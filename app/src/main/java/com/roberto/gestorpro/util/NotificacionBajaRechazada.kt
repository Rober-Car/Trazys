package com.roberto.gestorpro.util

/**
 * NotificacionBajaRechazada
 * -------------------------
 * Reglas PURAS (sin Firebase) de la notificación al CLIENTE cuando el ADMIN
 * rechaza su solicitud de baja. Se aíslan aquí para poder testearlas sin
 * infraestructura y para no dispersar la lógica en el repositorio.
 *
 *  - El ID es DETERMINISTA por solicitud: una misma solicitud solo puede generar
 *    una notificación de rechazo (idempotencia).
 *  - El aviso respeta el switch "Baja rechazada", cuyo DEFECTO es ACTIVO (igual
 *    que la baja confirmada): null (sin configuración o campo ausente) se trata
 *    como activo; solo un false explícito del ADMIN lo desactiva.
 */
object NotificacionBajaRechazada {

    /**
     * idDeterminista
     * --------------
     * DocumentId de la notificación de rechazo de una solicitud concreta.
     */
    fun idDeterminista(idSolicitud: String): String = "solicitud_rechazada_$idSolicitud"

    /**
     * activaPorDefecto
     * ----------------
     * El switch está activo salvo que el ADMIN lo haya desactivado de forma
     * explícita (false).
     */
    fun activaPorDefecto(valor: Boolean?): Boolean = valor != false

    /**
     * debeCrear
     * ---------
     * Decide si hay que crear la notificación: el switch debe estar activo y la
     * notificación no debe existir todavía (idempotencia).
     */
    fun debeCrear(configActiva: Boolean?, yaExiste: Boolean): Boolean =
        activaPorDefecto(configActiva) && !yaExiste
}
