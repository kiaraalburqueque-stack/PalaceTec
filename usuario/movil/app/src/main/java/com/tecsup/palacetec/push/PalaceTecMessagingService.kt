package com.tecsup.palacetec.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tecsup.palacetec.PalaceTecApp
import com.tecsup.palacetec.R

/**
 * Con la app en segundo plano el sistema muestra solo el aviso; con la app abierta llega aquí.
 * El backend no guarda tokens de FCM, por eso no se sobrescribe onNewToken: se envía desde la consola.
 */
class PalaceTecMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val content = pushContent(
            message.notification?.title, message.notification?.body, message.data, getString(R.string.app_name),
        ) ?: return
        (application as PalaceTecApp).container.pushNotifications.show(content)
    }
}
