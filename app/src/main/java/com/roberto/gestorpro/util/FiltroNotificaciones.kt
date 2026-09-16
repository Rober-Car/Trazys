package com.roberto.gestorpro.util

import com.roberto.gestorpro.model.NotificacionAdmin

/**
 * FiltroTipoNotificacion
 * ----------------------
 * Categorías del filtro de tipo de la lista de notificaciones del ADMIN.
 */
enum class FiltroTipoNotificacion {
    TODOS,
    INDIVIDUALES,
    GRUPALES,
    AUTOMATICAS
}

/**
 * FiltroNotificaciones
 * --------------------
 * Lógica PURA (sin UI) para clasificar y filtrar las notificaciones del ADMIN
 * usando los campos que YA existen en `notificaciones/{id}`:
 *
 *  - AUTOMÁTICA: `origen != "MANUAL"` (AUTOMATICA / PRECONFIGURADA). Incluye, por
 *    tanto, aperturas de reservas, morosidad, recordatorios, baja confirmada…
 *  - INDIVIDUAL: manual (`origen == "MANUAL"`) con `modoDestino == "INDIVIDUAL"`.
 *  - GRUPAL: manual con `modoDestino != "INDIVIDUAL"` (GRUPO o TODOS).
 *
 * Las tres categorías son mutuamente excluyentes. El filtro por fecha se aplica
 * sobre la fecha REAL de creación/envío (`fechaEnvio ?: fechaProgramada ?
 * fechaCreacion`), la misma que usa la lista para ordenar.
 */
object FiltroNotificaciones {

    const val ORIGEN_MANUAL = "MANUAL"
    const val DESTINO_INDIVIDUAL = "INDIVIDUAL"

    /** ¿Es una notificación automática? (`origen` distinto de MANUAL). */
    fun esAutomatica(notificacion: NotificacionAdmin): Boolean =
        notificacion.origen != ORIGEN_MANUAL

    /** ¿Es una notificación manual INDIVIDUAL? */
    fun esIndividual(notificacion: NotificacionAdmin): Boolean =
        !esAutomatica(notificacion) &&
            notificacion.modoDestino == DESTINO_INDIVIDUAL

    /** ¿Es una notificación manual GRUPAL (GRUPO o TODOS)? */
    fun esGrupal(notificacion: NotificacionAdmin): Boolean =
        !esAutomatica(notificacion) &&
            notificacion.modoDestino != DESTINO_INDIVIDUAL

    /** ¿Cumple la categoría seleccionada? (TODOS siempre cumple). */
    fun cumpleTipo(
        notificacion: NotificacionAdmin,
        filtro: FiltroTipoNotificacion
    ): Boolean = when (filtro) {
        FiltroTipoNotificacion.TODOS -> true
        FiltroTipoNotificacion.INDIVIDUALES -> esIndividual(notificacion)
        FiltroTipoNotificacion.GRUPALES -> esGrupal(notificacion)
        FiltroTipoNotificacion.AUTOMATICAS -> esAutomatica(notificacion)
    }

    /** Fecha real de la notificación (creación/envío), no la de la sesión. */
    fun fechaRelevante(notificacion: NotificacionAdmin): Long =
        notificacion.fechaEnvio
            ?: notificacion.fechaProgramada
            ?: notificacion.fechaCreacion

    /** ¿La fecha relevante cae dentro del rango [desdeInclusive, hastaInclusive]? */
    fun cumpleFecha(
        notificacion: NotificacionAdmin,
        desdeInclusive: Long?,
        hastaInclusive: Long?
    ): Boolean {
        val fecha = fechaRelevante(notificacion)
        if (desdeInclusive != null && fecha < desdeInclusive) return false
        if (hastaInclusive != null && fecha > hastaInclusive) return false
        return true
    }

    /**
     * Filtra la lista combinando tipo + rango de fechas. Mantiene el orden
     * original (la consulta ya llega de más reciente a más antigua).
     */
    fun filtrar(
        notificaciones: List<NotificacionAdmin>,
        filtro: FiltroTipoNotificacion,
        desdeInclusive: Long?,
        hastaInclusive: Long?
    ): List<NotificacionAdmin> =
        notificaciones.filter {
            cumpleTipo(it, filtro) && cumpleFecha(it, desdeInclusive, hastaInclusive)
        }
}
