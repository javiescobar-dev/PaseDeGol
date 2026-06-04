package com.escobar.pasedegol.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.escobar.pasedegol.MainActivity
import com.escobar.pasedegol.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio de Firebase Cloud Messaging para recibir notificaciones push.
 * Se encarga de procesar los mensajes recibidos desde el servidor de Firebase
 * y mostrar las notificaciones al usuario en la bandeja del sistema.
 */
class PaseDeGolMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "pasedegol_notifications"
        private const val CHANNEL_NAME = "Notificaciones PaseDeGol"
    }

    // se ejecuta al recibir un nuevo mensaje de Firebase Cloud Messaging,
    // extrae el titulo y el cuerpo del mensaje y muestra la notificacion
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje recibido de: ${remoteMessage.from}")

        // procesamos la notificacion si tiene datos de notificacion
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "PaseDeGol"
            val body = notification.body ?: ""
            showNotification(title, body)
        }

        // si el mensaje contiene datos adicionales, los procesamos igualmente
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${remoteMessage.data}")
            val title = remoteMessage.data["title"] ?: "PaseDeGol"
            val body = remoteMessage.data["body"] ?: ""
            if (body.isNotEmpty()) {
                showNotification(title, body)
            }
        }
    }

    // se ejecuta al generar un nuevo token de Firebase Cloud Messaging
    // ocurre al instalar la aplicacion o al invalidar el token anterior
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM generado: $token")
        // en una implementacion de produccion, aqui se enviaria el token al servidor
        // para asociarlo al usuario y poder enviar notificaciones personalizadas segun usuario (dirigidas)
    }

    // metodo para generar y mostrar la notificacion push en el dispositivo
    private fun showNotification(title: String, body: String) {
        // creamos el intent para abrir la app al pulsar la notificacion
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // obtenemos el servicio de notificaciones del sistema
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // creamos el canal de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones sobre nuevos partidos y promociones"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // construimos la notificacion con Material Design
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pasedegol_alter)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        // mostramos la notificacion con un ID basado en el timestamp para evitar sobreescrituras
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
