package com.roberto.gestorpro.cliente.model

/**
 * Notificacion
 * ------------
 * Notificación del buzón del CLIENTE leída desde Firestore
 * (notificaciones_por_destinatario/{clienteId}_{notificacionId}).
 * `id` es el documentId determinista del documento.
 */
data class Notificacion(
    val id: String,
    val notificacionId: String,
    val titulo: String,
    val mensaje: String,
    val tipo: String,
    val origen: String,
    val fechaEnvio: Long,
    val leida: Boolean,
    val fechaLeida: Long? = null,
    val tituloEn: String? = null,
    val mensajeEn: String? = null,
    val subtipo: String? = null
)
