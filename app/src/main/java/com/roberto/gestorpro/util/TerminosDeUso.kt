package com.roberto.gestorpro.util

/**
 * TerminosDeUso
 * -------------
 * Versión vigente y claves del consentimiento de los Términos de uso de Trazys.
 * No es un booleano simple: guardamos versión aceptada + fecha + uid, para poder
 * exigir una nueva aceptación cuando cambie la versión del documento.
 */
object TerminosDeUso {

    /** Versión vigente de los Términos de uso. */
    const val VERSION = "1.0"

    const val CLAVE_TERMINOS_UID = "terminos_uid"
    const val CLAVE_TERMINOS_VERSION = "terminos_version"
    const val CLAVE_TERMINOS_FECHA = "terminos_fecha"

    /** ¿El usuario actual aceptó la versión vigente? */
    fun aceptado(uidGuardado: String?, versionGuardada: String?, uidActual: String?): Boolean =
        uidActual != null &&
            uidGuardado == uidActual &&
            versionGuardada == VERSION
}
