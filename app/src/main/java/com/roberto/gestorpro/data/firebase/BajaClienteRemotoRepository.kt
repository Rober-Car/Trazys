package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.model.DestinatarioResuelto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BajaClienteRemotoRepository
 * ---------------------------
 * Consecuencias de negocio de una BAJA EFECTIVA de cliente en Firestore.
 *
 * Es la lógica COMPARTIDA por ambos caminos de baja (aceptar una solicitud y
 * dar de baja directamente): cancelar las reservas futuras del cliente (con el
 * ajuste atómico de plazas) y generar la notificación BAJA_CONFIRMADA si la
 * configuración del negocio lo permite. Así la baja directa y la aceptación de
 * una solicitud producen exactamente las mismas consecuencias.
 *
 * La notificación usa el ID determinista que Cloud Functions también usaría
 * (baja_confirmada_{clienteId}_{fechaBaja}) para no duplicarse cuando se
 * despliegue el backend.
 */
@Singleton
class BajaClienteRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val reservaRemotoRepository: ReservaRemotoRepository,
    private val notificacionRemotoRepository: NotificacionRemotoRepository
) {

    companion object {
        private const val COLECCION_CLIENTES = "clientes"
        private const val COLECCION_RESERVAS = "reservas"
        private const val COLECCION_SESIONES = "sesiones"
        private const val TAG = "BajaClienteRemotoRepository"

        /**
         * idNotificacionBajaConfirmada
         * ----------------------------
         * ID determinista de la notificación de baja confirmada (compartido con
         * Cloud Functions). Garantiza que para la misma ficha y fecha de baja
         * solo exista un documento, evitando duplicados.
         */
        fun idNotificacionBajaConfirmada(idCliente: Int, fechaBajaMillis: Long): String =
            "baja_confirmada_${idCliente}_$fechaBajaMillis"
    }

    /**
     * negocioIdActual
     * ---------------
     * El negocioId del ADMIN es su propio UID.
     */
    fun negocioIdActual(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    /**
     * bajaEfectiva
     * ------------
     * Ejecuta las consecuencias de la baja efectiva del cliente:
     *   1. lee la ficha remota para obtener negocioId y firebaseUid;
     *   2. cancela sus reservas futuras (delete reserva + plazas+1, atómico);
     *   3. genera la notificación BAJA_CONFIRMADA si la config lo permite.
     *
     * El estado y fechaBaja del cliente ya los ha aplicado el flujo que llama
     * (aceptar solicitud o baja directa). Se invoca DESPUÉS de que el cliente
     * esté en BAJA en Firestore.
     */
    suspend fun bajaEfectiva(
        idCliente: Int,
        fechaBajaMillis: Long
    ): ResultadoAutenticacion {
        return try {
            val cliente = db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .get()
                .esperar()
            if (!cliente.exists()) {
                return ResultadoAutenticacion(false, "El cliente no existe")
            }
            val negocioId = cliente.getString("negocioId")
            if (negocioId.isNullOrBlank()) {
                return ResultadoAutenticacion(false, "El cliente no tiene centro")
            }

            val canceladas = cancelarReservasFuturas(idCliente, negocioId)
            crearBajaConfirmada(negocioId, idCliente, cliente.getString("firebaseUid"), fechaBajaMillis)

            Log.i(
                TAG,
                "Baja efectiva del cliente $idCliente: $canceladas reservas futuras canceladas"
            )
            ResultadoAutenticacion(true, "Baja aplicada")
        } catch (e: Exception) {
            Log.e(TAG, "Error en la baja efectiva del cliente $idCliente", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * cancelarReservasFuturas
     * -----------------------
     * Cancela en Firestore las reservas del cliente en sesiones que todavía no
     * han terminado, reutilizando la Transaction atómica de cancelación
     * (elimina la reserva y libera la plaza). Las reservas de sesiones pasadas
     * se conservan como histórico. Es tolerante a reservas que ya no existan.
     */
    private suspend fun cancelarReservasFuturas(
        idCliente: Int,
        negocioId: String
    ): Int {
        var canceladas = 0
        try {
            val reservas = db.collection(COLECCION_RESERVAS)
                .whereEqualTo("clienteId", idCliente)
                .whereEqualTo("negocioId", negocioId)
                .get()
                .esperar()

            for (documento in reservas.documents) {
                val sesionId = documento.getLong("sesionId")?.toInt() ?: continue
                val sesion = db.collection(COLECCION_SESIONES)
                    .document(sesionId.toString())
                    .get()
                    .esperar()
                if (!sesion.exists()) continue

                val fecha = sesion.getLong("fecha") ?: continue
                val fin = fecha +
                    horaMinutos(sesion.getString("hora") ?: "") * 60_000L +
                    (sesion.getLong("duracionMinutos")?.toInt() ?: 0) * 60_000L
                if (fin <= System.currentTimeMillis()) continue

                val resultado = reservaRemotoRepository.cancelarReservaRemota(idCliente, sesionId)
                if (resultado.exito) canceladas++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando reservas futuras del cliente $idCliente", e)
        }
        return canceladas
    }

    /**
     * crearBajaConfirmada
     * -------------------
     * Si configuracion_notificaciones/{negocioId}.bajaConfirmada.activa está
     * activada, crea la notificación BAJA_CONFIRMADA con la infraestructura
     * existente y el ID determinista compartido con Cloud Functions.
     */
    private suspend fun crearBajaConfirmada(
        negocioId: String,
        idCliente: Int,
        firebaseUid: String?,
        fechaBajaMillis: Long
    ) {
        try {
            val config = notificacionRemotoRepository.obtenerConfiguracion(negocioId)
            val uidValido = firebaseUid?.takeIf { it.isNotBlank() }
            if (uidValido == null) return
            // Activa por defecto: solo se suprime con un false explícito del ADMIN.
            if (!NotificacionRemotoRepository.bajaConfirmadaActivaPorDefecto(
                    config?.bajaConfirmadaActiva
                )
            ) return

            val notificacionId = idNotificacionBajaConfirmada(idCliente, fechaBajaMillis)
            if (notificacionRemotoRepository.existeNotificacion(notificacionId)) return

            notificacionRemotoRepository.crearNotificacion(
                negocioId = negocioId,
                titulo = "Baja confirmada",
                mensaje = "Tu baja se ha realizado con fecha ${formatearFecha(fechaBajaMillis)}.",
                modoDestino = "INDIVIDUAL",
                clienteId = idCliente,
                destinatarios = listOf(DestinatarioResuelto(idCliente, uidValido)),
                idsObjetivo = listOf(idCliente),
                programada = false,
                fechaProgramada = null,
                tipo = "BAJA_CONFIRMADA",
                origen = "PRECONFIGURADA",
                notificacionId = notificacionId
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear la notificación de baja confirmada", e)
        }
    }

    private fun horaMinutos(hhmm: String): Long {
        val partes = hhmm.split(":")
        val h = partes.getOrNull(0)?.toLongOrNull() ?: 0L
        val m = partes.getOrNull(1)?.toLongOrNull() ?: 0L
        return h * 60 + m
    }

    private fun formatearFecha(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                "No tienes permisos para aplicar la baja"
            else -> e.message ?: "Error inesperado al aplicar la baja"
        }
    }
}
