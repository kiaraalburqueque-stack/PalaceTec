"""HU65: foto de avatar subida por el jugador, recortada a 300 x 300 px y guardada como WEBP."""
import io
import secrets

from django.conf import settings
from django.core.files.base import ContentFile
from django.core.files.storage import default_storage
from PIL import Image, ImageOps, UnidentifiedImageError
from rest_framework import serializers

from admin_api.serializers import validate_image_upload

AVATAR_SIZE = (300, 300)
AVATAR_FOLDER = "avatars"
ALLOWED_MIME = {"image/jpeg", "image/png", "image/webp"}
MAX_PIXELS = 25_000_000  # evita "bombas de descompresión" dentro de un archivo pequeño


def uploaded_prefix():
    return f"{settings.MEDIA_URL}{AVATAR_FOLDER}/"


def is_uploaded(avatar_url):
    return bool(avatar_url) and avatar_url.startswith(uploaded_prefix())


def validate_avatar(upload):
    """Revisa tipo MIME declarado, extensión, tamaño y firma real; luego que Pillow pueda leer la imagen."""
    if (upload.content_type or "").lower() not in ALLOWED_MIME:
        raise serializers.ValidationError("La foto debe ser JPG, PNG o WEBP.")
    kind = validate_image_upload(upload, settings.AVATAR_MAX_BYTES, "La foto")
    try:
        with Image.open(upload) as probe:
            fmt = probe.format
            probe.verify()
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError, Image.DecompressionBombError) as exc:
        raise serializers.ValidationError("El contenido del archivo no corresponde a una imagen válida.") from exc
    finally:
        upload.seek(0)
    if {"JPEG": "image/jpeg", "PNG": "image/png", "WEBP": "image/webp"}.get(fmt) != kind:
        raise serializers.ValidationError("La extensión del archivo no coincide con su contenido.")
    return upload


def to_webp(upload):
    """Recorta al centro, redimensiona a 300 x 300 y comprime en WEBP."""
    try:
        with Image.open(upload) as image:
            if image.width * image.height > MAX_PIXELS:
                raise serializers.ValidationError("La imagen tiene demasiados píxeles.")
            image = ImageOps.exif_transpose(image)
            image = image.convert("RGBA" if "A" in image.getbands() else "RGB")
            square = ImageOps.fit(image, AVATAR_SIZE, method=Image.Resampling.LANCZOS)
            buffer = io.BytesIO()
            square.save(buffer, format="WEBP", quality=85, method=4)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError) as exc:
        raise serializers.ValidationError("No se pudo procesar la imagen.") from exc
    return buffer.getvalue()


def delete_uploaded(avatar_url):
    """Borra del disco una foto subida antes (nunca los avatares predeterminados)."""
    if is_uploaded(avatar_url):
        default_storage.delete(avatar_url[len(settings.MEDIA_URL):])


def save_avatar(user, upload):
    """Guarda la nueva foto, actualiza avatar_url y devuelve la ruta anterior para borrarla al confirmar."""
    content = to_webp(validate_avatar(upload))
    name = default_storage.save(f"{AVATAR_FOLDER}/user_{user.pk}_{secrets.token_hex(6)}.webp", ContentFile(content))
    previous = user.avatar_url
    user.avatar_url = f"{settings.MEDIA_URL}{name}"
    user.save(update_fields=["avatar_url"])
    return previous
