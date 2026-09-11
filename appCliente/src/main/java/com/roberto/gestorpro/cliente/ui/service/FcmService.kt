package com.roberto.gestorpro.cliente.ui.service

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
import com.roberto.gestorpro.cliente.MainActivity
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.DispositivoRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * FcmService
 * ----------
 * Servicio de mensajería Firebase (FCM) de GestorPro Cliente.
 * - onNewToken: registra el token nuevo en Firestore si el cliente está vinculado.
 * - onMessageReceived: muestra una notificación local (si el CLIENTE tiene las
 *   notificaciones activadas); en segundo plano el sistema muestra la de FCM.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var dispositivoRepository: DispositivoRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                dispositivoRepository.registrarToken(token)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo registrar el token FCM: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val titulo = resolverTitulo(message)
        val cuerpo = resolverCuerpo(message)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val activadas = try {
                preferencesRepository.notificacionesActivadas.first()
            } catch (e: Exception) {
                true
            }
            if (activadas) {
                mostrarNotificacion(titulo, cuerpo)
            }
        }
    }

    /**
     * resolverTitulo
     * --------------
     * Elige el título de la notificación. En los mensajes DATA-ONLY (p. ej. los
     * avisos de morosidad) el texto viaja en `data`; se usa `tituloEn` cuando el
     * idioma de la app es inglés, con fallback a `titulo`. El resto de
     * notificaciones (con payload `notification`) conserva el comportamiento
     * previo.
     */
    private fun resolverTitulo(message: RemoteMessage): String {
        val data = message.data
        val tituloData = data["titulo"]
        if (message.notification == null && tituloData != null) {
            return IdiomaAplicacion.textoLocalizado(tituloData, data["tituloEn"])
        }
        return tituloData
            ?: message.notification?.title
            ?: getString(R.string.notif_push_titulo_defecto)
    }

    /**
     * resolverCuerpo
     * --------------
     * Igual que [resolverTitulo] pero para el cuerpo: en mensajes DATA-ONLY usa
     * `mensajeEn` cuando el idioma de la app es inglés, con fallback a
     * `mensaje`. El resto conserva el comportamiento previo.
     */
    private fun resolverCuerpo(message: RemoteMessage): String {
        val data = message.data
        val mensajeData = data["mensaje"]
        if (message.notification == null && mensajeData != null) {
            return IdiomaAplicacion.textoLocalizado(mensajeData, data["mensajeEn"])
        }
        return mensajeData ?: message.notification?.body ?: ""
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val canal = CANAL_NOTIFICACIONES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                canal,
                getString(R.string.notif_canal_nombre),
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
            Log.w(TAG, "Sin permiso para mostrar la notificación local")
        }
    }

    companion object {
        private const val TAG = "FcmService"
        private const val CANAL_NOTIFICACIONES = "notificaciones"
        private const val ID_NOTIFICACION = 1000
    }
}
