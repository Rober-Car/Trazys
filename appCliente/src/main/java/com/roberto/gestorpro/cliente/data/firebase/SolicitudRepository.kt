package com.roberto.gestorpro.cliente.data.firebase

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.model.EstadoSolicitud
import com.roberto.gestorpro.cliente.model.SolicitudBaja
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SolicitudRepository
 * -------------------
 * Acceso del CLIENTE a sus solicitudes de baja en Firestore (solicitudes).
 * Solo consulta las propias (filtros negocioId + idCliente, que exigen las
 * Rules) y crea su solicitud PENDIENTE con un ID determinista.
 */
@Singleton
class SolicitudRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(@StringRes recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    companion object {
        private const val COLECCION_SOLICITUDES = "solicitudes"
        private const val TAG = "SolicitudRepository"
    }

    /**
     * obtenerSolicitudes
     * ------------------
     * Consulta las solicitudes del cliente vinculado, ordenadas de más reciente
     * a más antigua.
     */
    suspend fun obtenerSolicitudes(
        clienteId: Int,
        negocioId: String
    ): List<SolicitudBaja> {
        val snapshots = db.collection(COLECCION_SOLICITUDES)
            .whereEqualTo("negocioId", negocioId)
            .whereEqualTo("idCliente", clienteId)
            .get()
            .esperar()

        return snapshots.documents
            .mapNotNull { documento ->
                val datos = documento.data ?: return@mapNotNull null
                val idSolicitud = datos["idSolicitud"] as? String ?: return@mapNotNull null
                val fechaSolicitud = fechaEnMilisegundos(datos["fechaSolicitud"])
                    ?: return@mapNotNull null
                SolicitudBaja(
                    idSolicitud = idSolicitud,
                    negocioId = datos["negocioId"] as? String ?: negocioId,
                    idCliente = enteroDe(datos["idCliente"]) ?: clienteId,
                    firebaseUid = datos["firebaseUid"] as? String,
                    fechaSolicitud = fechaSolicitud,
                    estado = when (datos["estado"]) {
                        "ACEPTADA" -> EstadoSolicitud.ACEPTADA
                        "RECHAZADA" -> EstadoSolicitud.RECHAZADA
                        else -> EstadoSolicitud.PENDIENTE
                    },
                    fechaResolucion = fechaEnMilisegundos(datos["fechaResolucion"]),
                    resueltaPor = datos["resueltaPor"] as? String,
                    motivo = datos["motivo"] as? String
                )
            }
            .sortedByDescending { it.fechaSolicitud }
    }

    /**
     * crearSolicitudBaja
     * ------------------
     * Crea una solicitud de baja PENDIENTE para el propio cliente con un ID
     * determinista (baja_{clienteId}_{fechaSolicitud}). Si ya existe una
     * solicitud PENDIENTE, se rechaza (no se duplica). Las Rules impiden además
     * solicitar la baja si el cliente ya está en BAJA.
     */
    suspend fun crearSolicitudBaja(
        clienteId: Int,
        negocioId: String,
        motivo: String?
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )

        return try {
            val existentes = obtenerSolicitudes(clienteId, negocioId)
            if (existentes.any { it.estado == EstadoSolicitud.PENDIENTE }) {
                return ResultadoAutenticacion(
                    false,
                    texto(R.string.cuenta_error_baja_pendiente)
                )
            }

            val fechaSolicitud = System.currentTimeMillis()
            val idSolicitud = "baja_${clienteId}_$fechaSolicitud"
            val mapa = mutableMapOf<String, Any>(
                "idSolicitud" to idSolicitud,
                "negocioId" to negocioId,
                "idCliente" to clienteId,
                "firebaseUid" to uid,
                "fechaSolicitud" to Timestamp(Date(fechaSolicitud)),
                "estado" to "PENDIENTE",
                "tipo" to "BAJA"
            )
            motivo?.takeIf { it.isNotBlank() }?.let { mapa["motivo"] = it }

            db.collection(COLECCION_SOLICITUDES)
                .document(idSolicitud)
                .set(mapa)
                .esperar()

            Log.i(TAG, "Solicitud de baja creada: $idSolicitud")
            ResultadoAutenticacion(true, texto(R.string.cuenta_baja_enviada_ok))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando la solicitud de baja", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    private fun enteroDe(valor: Any?): Int? = when (valor) {
        is Int -> valor
        is Long -> valor.toInt()
        is Number -> valor.toInt()
        else -> null
    }

    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                texto(R.string.cuenta_error_permisos_baja)
            else -> e.message ?: texto(R.string.cuenta_error_inesperado_baja)
        }
    }
}
