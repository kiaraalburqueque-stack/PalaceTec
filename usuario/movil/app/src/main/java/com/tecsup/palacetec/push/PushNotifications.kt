package com.tecsup.palacetec.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tecsup.palacetec.MainActivity
import com.tecsup.palacetec.R

data class PushContent(val title: String, val body: String)

/** La consola envía título y texto en notification; un mensaje solo de datos puede traerlos en data. */
fun pushContent(title: String?, body: String?, data: Map<String, String>, appName: String): PushContent? {
    val shownTitle = title?.ifBlank { null } ?: data["title"]?.ifBlank { null }
    val shownBody = body?.ifBlank { null } ?: data["body"]?.ifBlank { null }
    if (shownTitle == null && shownBody == null) return null
    return PushContent(shownTitle ?: appName, shownBody.orEmpty())
}

/** Canal y avisos del sistema. */
class PushNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val channelId = context.getString(R.string.push_channel_id)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.push_channel_name))
                .setDescription(context.getString(R.string.push_channel_description))
                .build(),
        )
    }

    fun show(content: PushContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.notification))
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Vuelve a la app si ya estaba abierta, con su pantalla actual; si no, la inicia. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
