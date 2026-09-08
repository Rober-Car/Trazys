package com.roberto.gestorpro.util

/**
 * RetiradaNotificacionReglas
 * ---------------------------
 * Regla pura de la moderación UGC (FASE 2C-3): ¿una notificación del ADMIN
 * puede RETIRARSE desde la gestión de notificaciones?
 *
 * La retirada elimina `notificaciones/{id}` y sus buzones derivados en
 * `notificaciones_por_destinatario`. Para no interferir con el resto del
 * sistema, SOLO es retirable una notificación MANUAL que ya está publicada o
 * en proceso de entrega:
 *
 *  - origen MANUAL (nunca AUTOMATICA/PRECONFIGURADA: BAJA_CONFIRMADA,
 *    SOLICITUD_BAJA, VINCULACION...);
 *  - estado PENDIENTE (inmediata ya con buzones, pendiente del push de la
 *    Cloud Function) o ENVIADA (ya entregada).
 *
 * Las programadas aún no ejecutadas (estado PROGRAMADA) NO se retiran con
 * esta acción: se cancelan con la cancelación de programación existente.
 * ERROR/CANCELADA tampoco: nunca llegaron a estar disponibles.
 */
object RetiradaNotificacionReglas {

    private const val ESTADO_PENDIENTE = "PENDIENTE"
    private const val ESTADO_ENVIADA = "ENVIADA"

    /**
     * esRetirable
     * -----------
     * ¿La notificación puede retirarse desde la moderación del ADMIN?
     * El origen ausente se trata como MANUAL (igual que el resto de la app);
     * el estado ausente no se considera publicado y por tanto no es retirable.
     */
    fun esRetirable(origen: String?, estado: String?): Boolean {
        val esManual = GateUgcNotificaciones.esPublicacionManual(origen)
        return esManual && (estado == ESTADO_PENDIENTE || estado == ESTADO_ENVIADA)
    }
}
