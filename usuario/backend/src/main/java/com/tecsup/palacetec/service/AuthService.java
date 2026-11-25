package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.dto.Emails;
import com.tecsup.palacetec.dto.LoginRequest;
import com.tecsup.palacetec.dto.LoginResponse;
import com.tecsup.palacetec.dto.RegisterRequest;
import com.tecsup.palacetec.dto.RegisterResponse;
import com.tecsup.palacetec.entity.AuthSession;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.exception.LoginRejectedException;
import com.tecsup.palacetec.repository.AuthSessionRepository;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.JwtService;
import com.tecsup.palacetec.security.WebTokenService;
import com.tecsup.palacetec.security.WebTokenService.WebToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final RegistrationService registration;
    private final ExclusionService exclusions;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final WebTokenService webTokens;
    private final Clock clock;

    public AuthService(UserRepository users, AuthSessionRepository sessions, RegistrationService registration,
                       ExclusionService exclusions, PasswordEncoder passwords, JwtService jwt,
                       WebTokenService webTokens, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.registration = registration;
        this.exclusions = exclusions;
        this.passwords = passwords;
        this.jwt = jwt;
        this.webTokens = webTokens;
        this.clock = clock;
    }

    public record WebLogin(Long userId, String email, WebToken access, WebToken refresh) {
    }

    public RegisterResponse register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        exclusions.ensureDniAllowed(request.dni());
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        if (users.existsByEmail(email)) {
            conflicts.put("email", List.of("Este correo ya está registrado."));
        }
        if (users.existsByDni(request.dni())) {
            conflicts.put("dni", List.of("Este DNI ya está registrado."));
        }
        if (!conflicts.isEmpty()) {
            throw new InvalidFieldsException(HttpStatus.CONFLICT, conflicts);
        }
        try {
            User user = registration.create(email, request.dni(), request.birthDate(),
                    passwords.encode(request.password()));
            return new RegisterResponse(user.getId(), user.getEmail());
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                // Otra solicitud ganó la carrera; la transacción ya se revirtió completa.
                throw new ApiException(HttpStatus.CONFLICT, null, "El correo o el DNI ya están registrados.");
            }
            throw exception;
        }
    }

    /** Login de la app móvil. */
    @Transactional(noRollbackFor = LoginRejectedException.class)
    public LoginResponse login(LoginRequest request) {
        User user = authenticate(request);
        JwtService.IssuedToken token = jwt.issue(user);
        sessions.save(new AuthSession(user.getId(), token.jti(), token.issuedAt(), token.expiresAt(), null));
        return new LoginResponse(user.getId(), user.getEmail(), token.value(), "Bearer", token.expiresIn());
    }

    /** Login de la web: acceso de 15 minutos renovable durante 8 horas. */
    @Transactional(noRollbackFor = LoginRejectedException.class)
    public WebLogin loginWeb(LoginRequest request) {
        User user = authenticate(request);
        WebToken access = webTokens.startSession(user);
        WebToken refresh = webTokens.issueRefresh(user, access.sid());
        sessions.save(new AuthSession(user.getId(), access.sid(), TimeConfig.now(clock), access.expiresAt(),
                refresh.expiresAt()));
        return new WebLogin(user.getId(), user.getEmail(), access, refresh);
    }

    /** Bloquea la fila del usuario para no perder incrementos del contador entre intentos simultáneos. */
    private User authenticate(LoginRequest request) {
        User user = users.lockByEmail(request.email()).orElse(null);
        if (user == null) {
            // Calcula igual un hash para reducir la diferencia de tiempo con una cuenta existente.
            passwords.encode(request.password());
            throw LoginRejectedException.invalidCredentials();
        }
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw locked(user.getLockedUntil(), now);
        }
        int failedAttempts = user.getLockedUntil() == null ? user.getFailedAttempts() : 0;
        if (!passwords.matches(request.password(), user.getPassword())) {
            registerFailure(user, failedAttempts + 1, now);
            throw LoginRejectedException.invalidCredentials();
        }
        if (passwords.upgradeEncoding(user.getPassword())) {
            user.upgradeHash(passwords.encode(request.password()));
        }
        if (!user.isActive()) {
            throw LoginRejectedException.suspended();
        }
        exclusions.ensureUserAllowed(user);
        user.registerLoginResult(0, null);
        return user;
    }

    private static void registerFailure(User user, int failedAttempts, Instant now) {
        boolean lock = failedAttempts >= MAX_FAILED_ATTEMPTS;
        Instant lockedUntil = lock ? now.plus(LOCK_DURATION) : null;
        user.registerLoginResult(failedAttempts, lockedUntil);
        if (lock) {
            throw locked(lockedUntil, now);
        }
    }

    private static LoginRejectedException locked(Instant until, Instant now) {
        long seconds = (long) Math.ceil(Duration.between(now, until).toMillis() / 1000.0);
        return LoginRejectedException.locked(Math.max(1, seconds));
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sql
                && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
