package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicUpdate;

/**
 * accounts.User de Django. La base no tiene valores por defecto: aquí se llenan todas las columnas NOT NULL.
 * Solo se actualizan las columnas que cambiaron, para no pisar lo que escriba Django.
 */
@Entity
@DynamicUpdate
@Table(name = "users")
public class User {

    public static final String DEFAULT_AVATAR = "/avatars/avatar-1.svg";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "is_superuser", nullable = false, updatable = false)
    private boolean superuser;

    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Column(nullable = false, length = 8, updatable = false)
    private String dni;

    @Column(name = "birth_date", nullable = false, updatable = false)
    private LocalDate birthDate;

    @Column(name = "is_active", nullable = false, updatable = false)
    private boolean active;

    @Column(name = "is_staff", nullable = false, updatable = false)
    private boolean staff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "avatar_url", nullable = false, length = 100)
    private String avatarUrl;

    @Column(length = 30)
    private String nickname;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "pause_until", updatable = false)
    private Instant pauseUntil;

    @Column(name = "self_excluded_until", updatable = false)
    private Instant selfExcludedUntil;

    protected User() {
    }

    public User(String email, String dni, LocalDate birthDate, String password, Instant createdAt) {
        this.email = email;
        this.dni = dni;
        this.birthDate = birthDate;
        this.password = password;
        this.createdAt = createdAt;
        this.active = true;
        this.avatarUrl = DEFAULT_AVATAR;
        this.firstName = "";
        this.lastName = "";
        this.phone = "";
    }

    public void registerLoginResult(int failedAttempts, Instant lockedUntil) {
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    /** Mismo password con un hash del algoritmo actual. */
    public void upgradeHash(String hash) {
        this.password = hash;
    }

    /** Los JWT emitidos antes dejan de valer. */
    public void changePassword(String hash) {
        this.password = hash;
        this.authVersion++;
    }

    /** "No fui yo": no acorta un bloqueo más largo y los JWT emitidos antes dejan de valer. */
    public void lockAfterReport(Instant until) {
        lockedUntil = lockedUntil == null || lockedUntil.isBefore(until) ? until : lockedUntil;
        authVersion++;
    }

    /** Los campos null se conservan. */
    public void updateProfile(String newNickname, String newAvatarUrl, String newFirstName, String newLastName,
                              String newPhone) {
        nickname = newNickname == null ? nickname : newNickname;
        avatarUrl = newAvatarUrl == null ? avatarUrl : newAvatarUrl;
        firstName = newFirstName == null ? firstName : newFirstName;
        lastName = newLastName == null ? lastName : newLastName;
        phone = newPhone == null ? phone : newPhone;
    }

    public boolean isAdmin() {
        return staff || superuser;
    }

    /** Nombre con el que se saluda en los correos. */
    public String getDisplayName() {
        return nickname == null || nickname.isEmpty() ? email : nickname;
    }

    public Long getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getDni() {
        return dni;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getNickname() {
        return nickname;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getPauseUntil() {
        return pauseUntil;
    }

    public Instant getSelfExcludedUntil() {
        return selfExcludedUntil;
    }
}
