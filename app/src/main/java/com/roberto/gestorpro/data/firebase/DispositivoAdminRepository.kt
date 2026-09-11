package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DispositivoAdminRepository
 * --------------------------
 * Registra el token FCM del dispositivo del ADMIN en Firestore, bajo su propio
 * documento de usuario: `usuarios/{uid}/dispositivos/{token}` (uid = negocioId).
 *
 * Es el canal de push EXCLUSIVO del ADMIN (p. ej. avisos de solicitudes de baja).
 * El envío real lo hace Cloud Functions (Admin SDK). Un ADMIN no puede registrar
 * ni leer el dispositivo de otro. Los fallos se registran pero no rompen la app.
 */
@Singleton
class DispositivoAdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val TAG = "DispositivoAdminRepository"
        private const val COLECCION_USUARIOS = "usuarios"
        private const val COLECCION_DISPOSITIVOS = "dispositivos"
    }

    /**
     * registrarTokenActual
     * --------------------
     * Lee el token FCM actual del dispositivo y lo registra si hay sesión de
     * ADMIN. Sin sesión no hace nada.
     */
    suspend fun registrarTokenActual() {
        val uid = auth.currentUser?.uid ?: return
        val token = try {
            FirebaseMessaging.getInstance().token.esperar()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener el token FCM: ${e.message}")
            return
        }
        registrarToken(token)
    }

    /**
     * registrarToken
     * --------------
     * Guarda o actualiza el token del dispositivo bajo `usuarios/{uid}`. Almacena
     * `notificacionesActivadas` (por defecto true) para que Cloud Functions omita
     * el token si el ADMIN desactiva los avisos en el futuro.
     */
    suspend fun registrarToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection(COLECCION_USUARIOS)
                .document(uid)
                .collection(COLECCION_DISPOSITIVOS)
                .document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "plataforma" to "android",
                        "notificacionesActivadas" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .esperar()
            Log.d(TAG, "Token FCM del ADMIN registrado: $uid")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar el token FCM del ADMIN: ${e.message}", e)
        }
    }
}
