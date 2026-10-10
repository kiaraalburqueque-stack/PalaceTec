from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.contrib.auth.models import PermissionsMixin
from django.core.validators import RegexValidator
from django.db import models


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
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    objects = UserManager()
    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["dni", "birth_date"]

    class Meta:
        db_table = "users"

    def __str__(self):
        return self.email
