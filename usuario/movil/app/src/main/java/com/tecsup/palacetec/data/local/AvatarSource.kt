package com.tecsup.palacetec.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.tecsup.palacetec.data.remote.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class AvatarFile(val mime: String, val bytes: ByteArray)

/** Lee la imagen que el jugador eligió en su galería. */
interface AvatarSource {
    suspend fun read(uri: String): AvatarFile
}

class AndroidAvatarSource(private val context: Context) : AvatarSource {

    override suspend fun read(uri: String): AvatarFile = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(uri)
            val mime = context.contentResolver.getType(source)
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: throw IOException("Sin contenido")
            if (mime in ACCEPTED && bytes.size <= MAX_BYTES) AvatarFile(checkNotNull(mime), bytes)
            else AvatarFile("image/jpeg", shrink(bytes))
        } catch (error: IOException) {
            throw unreadable(error)
        } catch (error: SecurityException) {
            throw unreadable(error)
        }
    }

    /** Las fotos de la cámara superan los 2 MB del backend, o vienen en un formato que no acepta (HEIC). */
    private fun shrink(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= MIN_SIDE) inSampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("No es una imagen")
        val out = ByteArrayOutputStream()
        upright(decoded, bytes).compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return out.toByteArray()
    }

    /** Al volver a comprimir se pierde la orientación EXIF: se aplica a los píxeles. */
    private fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val rotation = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotation, true)
    }

    private fun unreadable(cause: Exception) =
        ApiException(null, null, "No se pudo leer la imagen. Elige otra.", cause = cause)

    private companion object {
        val ACCEPTED = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_BYTES = 2 * 1024 * 1024
        /** El backend la recorta a 300 x 300. */
        const val MIN_SIDE = 600
        const val QUALITY = 85
    }
}
