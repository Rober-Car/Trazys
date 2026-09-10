package com.roberto.gestorpro.model

/**
 * NotificacionAdmin
 * -----------------
 * Notificación creada por el ADMIN leída desde Firestore
 * (notificaciones/{id}). Representa el registro global de una notificación
 * enviada o programada del negocio, tal y como lo valida el hasOnly de
 * `notificaciones/create` de las Security Rules.
 */
data class NotificacionAdmin(
    val id: String,
    val titulo: String,
    val mensaje: String,
    val tipo: String,
    val origen: String,
    val modoDestino: String,
    val idsClientes: List<Int>,
    val clienteId: Int?,
    val fechaCreacion: Long,
    val fechaEnvio: Long?,
    val programada: Boolean,
    val fechaProgramada: Long?,
    val estado: String
)

/**
 * ConfiguracionNotificaciones
 * ---------------------------
 * Configuración de notificaciones preconfiguradas del negocio
 * (configuracion_notificaciones/{negocioId}).
 *
 * El recordatorio de morosidad se modela con las horas: 0 = desactivado,
 * 24 = activado (cada 24 horas mientras continúe MOROSO).
 */
data class ConfiguracionNotificaciones(
    val morosidadActiva: Boolean,
    val recordatorioHoras: Int,
    val bajaConfirmadaActiva: Boolean,
    val cambioHorarioActiva: Boolean = false
)

/**
 * DestinatarioResuelto
 * --------------------
 * Destinatario real de un buzón: un cliente con `firebaseUid` válido en
 * Firestore (los clientes sin vínculo no pueden recibir buzón).
 */
data class DestinatarioResuelto(
    val idCliente: Int,
    val firebaseUid: String
)

/**
 * ResolucionDestinatarios
 * -----------------------
 * Resultado de comprobar qué destinatarios recibirán realmente una
 * notificación. `destinatarios` son los que tienen firebaseUid válido,
 * `omitidos` los del objetivo sin vínculo y `totalObjetivo` el total
 * considerado (para INDIVIDUAL/GRUPO, los seleccionados; para TODOS,
 * todos los clientes del negocio).
 */
data class ResolucionDestinatarios(
    val destinatarios: List<DestinatarioResuelto>,
    val omitidos: Int,
    val totalObjetivo: Int
)

/**
 * ModoDestino
 * -----------
 * Forma de elegir los destinatarios de una notificación. Coincide con los
 * valores de `modoDestino` de las Security Rules.
 */
enum class ModoDestino(val valor: String) {
    INDIVIDUAL("INDIVIDUAL"),
    GRUPO("GRUPO"),
    TODOS("TODOS")
}
