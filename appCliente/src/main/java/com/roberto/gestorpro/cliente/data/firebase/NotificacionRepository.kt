package com.roberto.gestorpro.cliente.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roberto.gestorpro.cliente.model.Notificacion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificacionRepository
 * ----------------------
 * Acceso del CLIENTE a su buzón de notificaciones en Firestore
 * (notificaciones_por_destinatario). Solo consulta las propias (filtros
 * clienteId + negocioId, que son los que exigen las Rules) y permite marcar
 * una notificación como leída.
 */
@Singleton
class NotificacionRepository @Inject constructor(
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION = "notificaciones_por_destinatario"
        private const val TAG = "NotificacionRepository"
    }

    /**
     * obtenerNotificaciones
     * ---------------------
     * Consulta las notificaciones del cliente vinculado, ordenadas por
     * fechaEnvio de más reciente a más antigua. Requiere el índice compuesto
     * notificaciones_por_destinatario(clienteId, negocioId, fechaEnvio desc).
     */
    suspend fun obtenerNotificaciones(
        clienteId: Int,
        negocioId: String
    ): List<Notificacion> {
        val snapshots = db.collection(COLECCION)
            .whereEqualTo("clienteId", clienteId)
            .whereEqualTo("negocioId", negocioId)
            .orderBy("fechaEnvio", Query.Direction.DESCENDING)
            .get()
            .esperar()

        return snapshots.documents.mapNotNull { documento ->
            val datos = documento.data ?: return@mapNotNull null
            val notificacionId = datos["notificacionId"] as? String
                ?: return@mapNotNull null
            val titulo = datos["titulo"] as? String ?: return@mapNotNull null
            val mensaje = datos["mensaje"] as? String ?: return@mapNotNull null
            val fechaEnvio = fechaEnMilisegundos(datos["fechaEnvio"])
                ?: return@mapNotNull null
            Notificacion(
                id = documento.id,
                notificacionId = notificacionId,
                titulo = titulo,
                mensaje = mensaje,
                tipo = datos["tipo"] as? String ?: "MANUAL",
                origen = datos["origen"] as? String ?: "MANUAL",
                fechaEnvio = fechaEnvio,
                leida = (datos["leida"] as? Boolean) ?: false,
                fechaLeida = fechaEnMilisegundos(datos["fechaLeida"]),
                tituloEn = datos["tituloEn"] as? String,
                mensajeEn = datos["mensajeEn"] as? String,
                subtipo = datos["subtipo"] as? String
            )
        }
    }

    /**
     * marcarComoLeida
     * ---------------
     * Marca una notificación propia como leída en Firestore (leida=true y
     * fechaLeida=now). Devuelve true si la escritura tuvo éxito.
     */
    suspend fun marcarComoLeida(docId: String): Boolean {
        return try {
            db.collection(COLECCION)
                .document(docId)
                .update(
                    mapOf(
                        "leida" to true,
                        "fechaLeida" to Timestamp.now()
                    )
                )
                .esperar()
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo marcar como leída $docId: ${e.message}", e)
            false
        }
    }

    /**
     * eliminarNotificacion
     * --------------------
     * Elimina una notificación DEL PROPIO buzón del CLIENTE
     * (`notificaciones_por_destinatario/{clienteId}_{notificacionId}`). Nunca
     * borra el documento global `notificaciones/{notificacionId}` (otra
     * colección, gestionada por el ADMIN). Devuelve true si la operación tuvo
     * éxito; es tolerante a documentos ya inexistentes.
     */
    suspend fun eliminarNotificacion(docId: String): Boolean {
        return try {
            db.collection(COLECCION)
                .document(docId)
                .delete()
                .esperar()
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo eliminar la notificación $docId: ${e.message}", e)
            false
        }
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }
}
