package com.tecsup.palacetec.data.repository

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.tecsup.palacetec.data.local.PushStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lo que se necesita del sistema y de FCM, separado para poder probar el repositorio. */
interface PushPlatform {
    /** Desde Android 13 hay que pedir POST_NOTIFICATIONS. */
    val needsPermission: Boolean

    fun notificationsEnabled(): Boolean

    /** Token de FCM de este celular, o null si no se pudo obtener. */
    suspend fun token(): String?
}

class AndroidPushPlatform(private val context: Context, private val firebaseReady: Boolean) : PushPlatform {

    override val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun notificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** El SDK propone pasar a FID, pero el mensaje de prueba de la consola se dirige con el token. */
    @Suppress("DEPRECATION")
    override suspend fun token(): String? {
        if (!firebaseReady) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}

class PushRepository(private val platform: PushPlatform, private val store: PushStore) {

    fun notificationsEnabled(): Boolean = platform.notificationsEnabled()

    suspend fun token(): String? = platform.token()

    /** El permiso se pide una sola vez; después se cambia desde Ajustes. */
    suspend fun shouldAskPermission(): Boolean =
        platform.needsPermission && !platform.notificationsEnabled() && !store.permissionAsked()

    suspend fun onPermissionAsked() = store.setPermissionAsked()
}
