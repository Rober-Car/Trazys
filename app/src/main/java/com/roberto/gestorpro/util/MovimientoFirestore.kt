package com.roberto.gestorpro.util

import com.google.firebase.Timestamp
import com.roberto.gestorpro.data.entity.MovimientoEntity
import java.util.Date

/**
 * MovimientoFirestore
 * -------------------
 * Transformaciones PURAS (sin Firebase ni Android) entre el modelo Room de la
 * economía y los documentos de Firestore de la FASE 6.
 *
 *  - `documentoDe` construye el documento completo de `movimientos/{idMovimiento}`:
 *    un espejo de MovimientoEntity con el `negocioId` real del ADMIN autenticado.
 *    Las fechas se publican como Timestamp (la misma convención que usa la ficha
 *    del cliente para `fechaInicioActual`/`fechaFinActual`) y los opcionales nulos
 *    se omiten en Firestore (el SDK borra los campos cuyo valor es null).
 *
 *  - `resumenDeCliente` construye el mapa del resumen económico que se publica en
 *    `clientes/{idCliente}` con `update()` (merge): moroso, fechaEntradaMorosidad,
 *    deuda, fechaInicioActual y fechaFinActual. Los valores salen del MISMO motor
 *    Room (MovimientoMorosidad) para no crear una segunda fuente de cálculo.
 */
object MovimientoFirestore {

    /**
     * documentoDe
     * -----------
     * Documento completo de un movimiento en `movimientos/{idMovimiento}`.
     * El `negocioId` NO vive en Room (es local): se inyecta el del ADMIN
     * autenticado, igual que hacen los demás repositorios remotos del proyecto.
     */
    fun documentoDe(movimiento: MovimientoEntity, negocioId: String): Map<String, Any?> {
        return mapOf(
            "idMovimiento" to movimiento.idMovimiento,
            "negocioId" to negocioId,
            "idCliente" to movimiento.idCliente,
            // Fotografía histórica de la identidad del cliente en el momento
            // del movimiento (no se actualiza al editar ni cuando cambia la ficha).
            "nombreCliente" to movimiento.nombreCliente,
            "apellidosCliente" to movimiento.apellidosCliente,
            "dniCliente" to movimiento.dniCliente,
            "servicios" to movimiento.servicios,
            "fechaInicio" to timestampDe(movimiento.fechaInicio),
            "fechaFin" to timestampDe(movimiento.fechaFin),
            "fechaRegistro" to timestampDe(movimiento.fechaRegistro),
            "precioFinal" to movimiento.precioFinal,
            "estado" to movimiento.estado.name,
            "fechaPago" to movimiento.fechaPago?.let { timestampDe(it) },
            "metodoPago" to movimiento.metodoPago?.name,
            "observaciones" to movimiento.observaciones
        )
    }

    /**
     * resumenDeCliente
     * ----------------
     * Resumen económico publicado en `clientes/{idCliente}` con `update()` (merge).
     * `moroso`/`fechaEntradaMorosidad` son los valores YA persistidos en Room por
     * MovimientoMorosidad; `deuda` es la suma de TODOS los PENDIENTES del mismo
     * motor; el periodo (fechaInicioActual/fechaFinActual) conserva su semántica
     * actual (movimiento con mayor fechaFin) y `exentoMorosidad` es la excepción
     * manual controlada por el ADMIN.
     */
    fun resumenDeCliente(
        moroso: Boolean,
        fechaEntradaMorosidad: Long?,
        deuda: Double,
        fechaInicioActual: Long?,
        fechaFinActual: Long?,
        exentoMorosidad: Boolean
    ): Map<String, Any?> {
        return mapOf(
            "moroso" to moroso,
            "fechaEntradaMorosidad" to fechaEntradaMorosidad?.let { timestampDe(it) },
            "deuda" to deuda,
            "fechaInicioActual" to fechaInicioActual?.let { timestampDe(it) },
            "fechaFinActual" to fechaFinActual?.let { timestampDe(it) },
            "exentoMorosidad" to exentoMorosidad
        )
    }

    /**
     * timestampDe
     * -----------
     * Convierte milisegundos de Room en Timestamp de Firestore (convención
     * existente del proyecto: `clientes/{id}.fechaInicioActual` son Timestamp).
     */
    private fun timestampDe(millis: Long): Timestamp = Timestamp(Date(millis))
}
