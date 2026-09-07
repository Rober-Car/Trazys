package com.roberto.gestorpro.cliente.util

/**
 * TerminosDeUso
 * -------------
 * Versión vigente y claves del consentimiento de los Términos de uso de Trazys
 * (mismo contrato que `:app`). No es un booleano simple: guardamos versión
 * aceptada + fecha + uid.
 */
object TerminosDeUso {

    const val VERSION = "1.0"

    const val CLAVE_TERMINOS_UID = "terminos_uid"
    const val CLAVE_TERMINOS_VERSION = "terminos_version"
    const val CLAVE_TERMINOS_FECHA = "terminos_fecha"

    fun aceptado(uidGuardado: String?, versionGuardada: String?, uidActual: String?): Boolean =
        uidActual != null &&
            uidGuardado == uidActual &&
            versionGuardada == VERSION
}
