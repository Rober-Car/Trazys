package com.roberto.gestorpro.util

/**
 * ResultadoGateUgc
 * ----------------
 * Resultado del gate de Términos de uso para la publicación de una notificación.
 */
enum class ResultadoGateUgc {
    PERMITIDO,
    BLOQUEADO
}

/**
 * GateUgcNotificaciones
 * ---------------------
 * Regla de Términos de uso para la PUBLICACIÓN MANUAL de notificaciones del
 * ADMIN (contenido UGC). Se aplica SOLO al flujo manual
 * (`CrearNotificacionScreen` -> `NotificacionesViewModel`): tanto las
 * notificaciones INMEDIATAS como las PROGRAMADAS creadas por el ADMIN.
 *
 * Las notificaciones AUTOMÁTICAS o PRECONFIGURADAS (BAJA_CONFIRMADA,
 * SOLICITUD_BAJA, VINCULACION, etc.) se identifican por un `origen` distinto
 * de `"MANUAL"` y NUNCA pasan por este gate, aunque compartan el repositorio
 * de creación con las manuales.
 *
 * La comprobación de "versión vigente aceptada" no vive aquí: se delega en
 * [TerminosDeUso.aceptado] (vía `PreferencesRepository.terminosAceptados`).
 * Esta clase solo decide si la publicación está sujeta al gate y, si lo está,
 * si puede publicarse con la aceptación vigente dada.
 */
object GateUgcNotificaciones {

    /** Origen de las notificaciones creadas manualmente por el ADMIN. */
    const val ORIGEN_MANUAL = "MANUAL"

    /** Origen de la notificación automática de baja confirmada. */
    const val ORIGEN_PRECONFIGURADA = "PRECONFIGURADA"

    /** Origen de las notificaciones automáticas (solicitudes, vinculación...). */
    const val ORIGEN_AUTOMATICA = "AUTOMATICA"

    /**
     * esPublicacionManual
     * -------------------
     * ¿La notificación es una PUBLICACIÓN MANUAL del ADMIN (inmediata o
     * programada)? Las creadas a mano usan `origen = "MANUAL"` (por defecto
     * cuando el origen es null, igual que el repositorio). El resto
     * (automáticas y preconfiguradas) no son publicaciones manuales.
     */
    fun esPublicacionManual(origen: String?): Boolean =
        (origen ?: ORIGEN_MANUAL) == ORIGEN_MANUAL

    /**
     * decidir
     * -------
     * Aplica el gate de Términos de uso sobre la publicación de una notificación:
     *  - Publicación MANUAL sin la versión vigente aceptada -> [ResultadoGateUgc.BLOQUEADO].
     *  - Publicación MANUAL con la versión vigente aceptada -> [ResultadoGateUgc.PERMITIDO].
     *  - Publicación NO manual (automática/preconfigurada) -> siempre PERMITIDO
     *    (nunca se bloquea por Términos).
     */
    fun decidir(origen: String?, terminosVigentes: Boolean): ResultadoGateUgc =
        if (esPublicacionManual(origen) && !terminosVigentes) {
            ResultadoGateUgc.BLOQUEADO
        } else {
            ResultadoGateUgc.PERMITIDO
        }
}
