package com.roberto.gestorpro.ui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.roberto.gestorpro.MainActivity
import com.roberto.gestorpro.R
import com.roberto.gestorpro.data.firebase.DispositivoAdminRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FcmService (ADMIN)
 * ------------------
 * Servicio FCM del ADMIN. Es el canal de push exclusivo del administrador
 * (p. ej. avisos de solicitudes de baja). Registra el token del dispositivo y
 * muestra la notificación en primer plano; en segundo plano la muestra el
 * sistema. No envía nada al CLIENTE.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var dispositivoAdminRepository: DispositivoAdminRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                dispositivoAdminRepository.registrarToken(token)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo registrar el token FCM: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val titulo = data["titulo"]
            ?: message.notification?.title
            ?: getString(R.string.fcm_push_titulo_defecto)
        val cuerpo = data["mensaje"] ?: message.notification?.body ?: ""
        mostrarNotificacion(titulo, cuerpo)
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val canal = CANAL_NOTIFICACIONES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                canal,
                getString(R.string.fcm_canal_nombre),
                NotificationManager.IMPORTANCE_HIGH
            )
            val gestor = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestor.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(this, canal)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(ID_NOTIFICACION, notificacion)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para mostrar la notificación")
        }
    }

    companion object {
        private const val TAG = "FcmService"
        private const val CANAL_NOTIFICACIONES = "notificaciones"
        private const val ID_NOTIFICACION = 1001
    }
}
