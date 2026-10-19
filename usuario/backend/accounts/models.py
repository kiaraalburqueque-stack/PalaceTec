from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.contrib.auth.models import PermissionsMixin
from django.core.validators import RegexValidator
from django.db import models
from django.db.models.functions import ExtractYear, ExtractMonth, ExtractDay, Lower


class UserManager(BaseUserManager):
    def create_user(self, email, dni, birth_date, password=None, **extra_fields):
        if not email:
            raise ValueError("El correo es obligatorio.")
        user = self.model(email=self.normalize_email(email), dni=dni,
                          birth_date=birth_date, **extra_fields)
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, email, dni, birth_date, password=None, **extra_fields):
        extra_fields.setdefault("is_staff", True)
        extra_fields.setdefault("is_superuser", True)
        extra_fields.setdefault("is_active", True)
        if not extra_fields["is_staff"] or not extra_fields["is_superuser"]:
            raise ValueError("El superusuario requiere is_staff e is_superuser.")
        return self.create_user(email, dni, birth_date, password, **extra_fields)


class User(AbstractBaseUser, PermissionsMixin):
    email = models.EmailField(unique=True)
    dni = models.CharField(max_length=8, unique=True, validators=[
        RegexValidator(r"\A[0-9]{8}\Z", "El DNI debe tener 8 dígitos.")])
    birth_date = models.DateField()
    nickname = models.CharField(max_length=30, unique=True, null=True, blank=True)
    avatar_url = models.CharField(max_length=100, default="/avatars/avatar-1.svg")
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    failed_attempts = models.PositiveIntegerField(default=0)
    locked_until = models.DateTimeField(null=True, blank=True)
    auth_version = models.PositiveIntegerField(default=0)

    objects = UserManager()
    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["dni", "birth_date"]

    class Meta:
        db_table = "users"
        constraints = [
            models.UniqueConstraint(Lower("nickname"), name="unique_nickname_case_insensitive"),
            models.CheckConstraint(
                condition=(models.Q(birth_date__year__lt=ExtractYear("created_at") - 18)
                    | (models.Q(birth_date__year=ExtractYear("created_at") - 18)
                       & (models.Q(birth_date__month__lt=ExtractMonth("created_at"))
                          | models.Q(birth_date__month=ExtractMonth("created_at"), birth_date__day__lte=ExtractDay("created_at"))))),
                name="user_adult_at_registration",
            ),
        ]

    def __str__(self):
        return self.email


class AuthSession(models.Model):
    """Una sesión por JWT; conservar revocaciones hasta su vencimiento."""
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="auth_sessions")
    jti = models.CharField(max_length=64, unique=True)
    started_at = models.DateTimeField()
    expires_at = models.DateTimeField(db_index=True)
    revoked_at = models.DateTimeField(null=True, blank=True)
