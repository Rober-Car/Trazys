package com.roberto.gestorpro.util

/**
 * RetiradaNotificacionReglas
 * ---------------------------
 * Regla pura de ELIMINACIÓN de notificaciones desde la gestión de
 * notificaciones del ADMIN.
 *
 * La eliminación borra `notificaciones/{id}` y sus buzones derivados en
 * `notificaciones_por_destinatario`. Puede aplicarse tanto a notificaciones
 * MANUALES como AUTOMÁTICAS/PRECONFIGURADAS (BAJA_CONFIRMADA, MOROSIDAD,
 * recordatorio de morosidad, SOLICITUD_BAJA, VINCULACION...) que ya estén
 * publicadas o en proceso de entrega:
 *
 *  - estado PENDIENTE (inmediata ya con buzones, pendiente del push de la
 *    Cloud Function) o ENVIADA (ya entregada).
 *
 * Las programadas aún no ejecutadas (estado PROGRAMADA) NO se eliminan con
 * esta acción: se cancelan con la cancelación de programación existente.
 * ERROR/CANCELADA tampoco: nunca llegaron a estar disponibles.
 */
object RetiradaNotificacionReglas {

    private const val ESTADO_PENDIENTE = "PENDIENTE"
    private const val ESTADO_ENVIADA = "ENVIADA"

    /**
     * esRetirable
     * -----------
     * ¿La notificación puede eliminarse desde la gestión de notificaciones?
     * Depende SOLO de que esté publicada o en entrega (PENDIENTE/ENVIADA), con
     * independencia de su origen (manual, automática o preconfigurada).
     */
    fun esRetirable(estado: String?): Boolean =
        estado == ESTADO_PENDIENTE || estado == ESTADO_ENVIADA
}
