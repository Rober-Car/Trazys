package com.roberto.gestorpro.util

import com.google.firebase.Timestamp
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.data.entity.ReservaEntity
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.data.entity.SesionEntity
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago

/**
 * HidratacionMapeadores
 * ---------------------
 * Transformaciones PURAS (JVM, sin Room ni FirebaseRuntime) entre los
 * documentos de Firestore y las entidades Room de la caché local. Se usan en
 * la hidratación central tras un cambio de propietario (WIPE) para reconstruir
 * Room desde la nube del negocio ACTUAL.
 *
 * Todas las funciones reciben el `negocioIdEsperado` (el UID del ADMIN
 * autenticado) y devuelven null si el documento pertenece a OTRO negocio, de
 * modo que nunca se introduce en Room una fila de un negocio distinto.
 *
 * Los mapeos NO regeneran identificadores: conservan los ids que ya existían
 * en Firestore (misma convención de toda la app: documentId == id del negocio).
 */
object HidratacionMapeadores {

    /**
     * servicioDeDocumento
     * -------------------
     * Convierte un documento de `servicios/{idServicio}` en una ServicioEntity.
     * Devuelve null si falta el negocioId correcto o los campos esenciales.
     */
    fun servicioDeDocumento(
        datos: Map<String, Any?>,
        negocioIdEsperado: String
    ): ServicioEntity? {
        if (datos["negocioId"] != negocioIdEsperado) return null
        val idServicio = comoInt(datos["idServicio"]) ?: return null
        val nombre = datos["nombre"] as? String ?: return null
        return ServicioEntity(
            idServicio = idServicio,
            negocioId = negocioIdEsperado,
            nombre = nombre,
            descripcion = datos["descripcion"] as? String ?: "",
            activo = datos["activo"] as? Boolean ?: true,
            precio = comoDouble(datos["precio"]) ?: 0.0,
            permiteCombinarDia = datos["permiteCombinarDia"] as? Boolean ?: true
        )
    }

    /**
     * sesionDeDocumento
     * -----------------
     * Convierte un documento de `sesiones/{idSesion}` en una SesionEntity.
     * Conserva todos los campos, incluido `horaDesdeReserva` (null = abierta
     * desde el inicio del día).
     */
    fun sesionDeDocumento(
        datos: Map<String, Any?>,
        negocioIdEsperado: String
    ): SesionEntity? {
        if (datos["negocioId"] != negocioIdEsperado) return null
        val idSesion = comoInt(datos["idSesion"]) ?: return null
        val idServicio = comoInt(datos["idServicio"]) ?: return null
        val fecha = comoLong(datos["fecha"]) ?: return null
        val hora = datos["hora"] as? String ?: return null
        val duracionMinutos = comoInt(datos["duracionMinutos"]) ?: return null
        val capacidad = comoInt(datos["capacidad"]) ?: return null
        val plazas = comoInt(datos["plazasDisponibles"]) ?: return null
        return SesionEntity(
            idSesion = idSesion,
            negocioId = negocioIdEsperado,
            idServicio = idServicio,
            fecha = fecha,
            hora = hora,
            duracionMinutos = duracionMinutos,
            capacidad = capacidad,
            plazasDisponibles = plazas,
            horaDesdeReserva = datos["horaDesdeReserva"] as? String
        )
    }

    /**
     * reservaDeDocumento
     * ------------------
     * Convierte un documento de `reservas/{clienteId}_{sesionId}` en una
     * ReservaEntity local. El `idReserva` de Room es local (autogenerado); la
     * unicidad la garantiza el índice (idSesion, idCliente). Solo se insertará
     * si su cliente y su sesión existen localmente (filtrado en el coordinador).
     */
    fun reservaDeDocumento(
        datos: Map<String, Any?>,
        negocioIdEsperado: String
    ): ReservaEntity? {
        if (datos["negocioId"] != negocioIdEsperado) return null
        val idSesion = comoInt(datos["sesionId"]) ?: return null
        val idCliente = comoInt(datos["clienteId"]) ?: return null
        val fechaReserva = comoLong(datos["fechaReserva"]) ?: System.currentTimeMillis()
        return ReservaEntity(
            idReserva = 0,
            negocioId = negocioIdEsperado,
            idSesion = idSesion,
            idCliente = idCliente,
            fechaReserva = fechaReserva
        )
    }

    /**
     * movimientoDeDocumento
     * ---------------------
     * Convierte un documento de `movimientos/{idMovimiento}` en una
     * MovimientoEntity local. Conserva el idMovimiento original (NO se usa
     * autoincremento local) y los servicios vacíos cuando el documento los
     * tiene vacíos o ausentes.
     */
    fun movimientoDeDocumento(
        datos: Map<String, Any?>,
        negocioIdEsperado: String
    ): MovimientoEntity? {
        if (datos["negocioId"] != negocioIdEsperado) return null
        val idMovimiento = comoInt(datos["idMovimiento"]) ?: return null
        val idCliente = comoInt(datos["idCliente"]) ?: return null
        val fechaInicio = comoLong(datos["fechaInicio"]) ?: return null
        val fechaFin = comoLong(datos["fechaFin"]) ?: return null
        val precioFinal = comoDouble(datos["precioFinal"]) ?: return null
        val estado = estadoDe(datos["estado"] as? String) ?: return null
        return MovimientoEntity(
            idMovimiento = idMovimiento,
            idCliente = idCliente,
            servicios = comoListaDeEnteros(datos["servicios"]),
            fechaInicio = fechaInicio,
            fechaFin = fechaFin,
            precioFinal = precioFinal,
            estado = estado,
            fechaPago = comoLong(datos["fechaPago"]),
            metodoPago = metodoDe(datos["metodoPago"] as? String),
            observaciones = datos["observaciones"] as? String
        )
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private fun estadoDe(valor: String?): EstadoMovimiento? = when (valor) {
        EstadoMovimiento.PENDIENTE.name -> EstadoMovimiento.PENDIENTE
        EstadoMovimiento.PAGADO.name -> EstadoMovimiento.PAGADO
        else -> null
    }

    private fun metodoDe(valor: String?): MetodoPago? = when (valor) {
        MetodoPago.EFECTIVO.name -> MetodoPago.EFECTIVO
        MetodoPago.BIZUM.name -> MetodoPago.BIZUM
        MetodoPago.TRANSFERENCIA.name -> MetodoPago.TRANSFERENCIA
        else -> null
    }

    private fun comoLong(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    private fun comoInt(valor: Any?): Int? = when (valor) {
        is Int -> valor
        is Long -> valor.toInt()
        is Number -> valor.toInt()
        else -> null
    }

    private fun comoDouble(valor: Any?): Double? = when (valor) {
        is Double -> valor
        is Float -> valor.toDouble()
        is Int -> valor.toDouble()
        is Long -> valor.toDouble()
        is Number -> valor.toDouble()
        else -> null
    }

    private fun comoListaDeEnteros(valor: Any?): List<Int> =
        (valor as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
}
