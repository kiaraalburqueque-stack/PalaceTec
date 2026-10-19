from django.db import models


class ExcludedPerson(models.Model):
    dni_hash = models.CharField(max_length=64, unique=True)
    reason = models.CharField(max_length=255, blank=True)
    source = models.CharField(max_length=120)
    created_at = models.DateTimeField(auto_now_add=True)
