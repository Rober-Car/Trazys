package com.roberto.gestorpro.cliente.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import java.util.Locale

/**
 * IdiomaAplicacion
 * ----------------
 * Helper de la infraestructura de idioma de Trazys Cliente.
 *
 * Guarda en memoria el idioma que se está aplicando en el proceso (actual) y
 * permite envolver el contexto base de la Activity con una configuración cuyo
 * locale corresponde a ese idioma. El valor se precarga en la Application al
 * arrancar (desde DataStore) y se actualiza justo antes de recrear la Activity
 * cuando el usuario cambia el idioma en Configuración.
 */
object IdiomaAplicacion {

    /**
     * Idiomas soportados (es/en). "es" es el idioma por defecto.
     */
    fun esValido(idioma: String): Boolean =
        idioma == PreferencesRepository.IDIOMA_ES || idioma == PreferencesRepository.IDIOMA_EN

    /**
     * actual
     * ------
     * Idioma que se está aplicando realmente en este proceso. Arranca con el
     * idioma por defecto y se fija al precargarlo en la Application y al
     * cambiar de idioma antes de recrear la Activity.
     */
    @Volatile
    var actual: String = PreferencesRepository.IDIOMA_DEFECTO
        private set

    /**
     * marcarActual
     * ------------
     * Registra el idioma que pasa a estar aplicado. Normaliza cualquier valor
     * desconocido al idioma por defecto ("es").
     */
    fun marcarActual(idioma: String) {
        actual = if (esValido(idioma)) idioma else PreferencesRepository.IDIOMA_DEFECTO
    }

    /**
     * localeActual
     * ------------
     * Locale correspondiente al idioma aplicado en este proceso.
     */
    fun localeActual(): Locale {
        return         if (actual == PreferencesRepository.IDIOMA_EN) {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag("es")
        }
    }

    /**
     * baseConIdioma
     * -------------
     * Devuelve un Context (basado en el recibido) cuyos recursos usan el locale
     * del idioma actual. Se utiliza en attachBaseContext de la Activity para
     * que toda la app (recursos incluidos) se resuelva en el idioma elegido.
     */
    fun baseConIdioma(base: Context): Context {
        val configuracion = Configuration(base.resources.configuration)
        configuracion.setLocale(localeActual())
        return base.createConfigurationContext(configuracion)
    }

    /**
     * textoDe
     * -------
     * Devuelve el texto del recurso en el idioma elegido por el usuario. Se usa
     * desde capas sin acceso a composición (repositorios, ViewModels) para que
     * los mensajes se resuelvan en el idioma de la app y no en el del sistema.
     */
    fun textoDe(base: Context, @StringRes recurso: Int): String =
        baseConIdioma(base).getString(recurso)

    fun textoDe(base: Context, @StringRes recurso: Int, vararg argumentos: Any): String =
        baseConIdioma(base).getString(recurso, *argumentos)

    /**
     * textoLocalizado
     * ---------------
     * Devuelve el texto en inglés cuando el idioma aplicado es inglés y existe
     * traducción; en caso contrario devuelve el texto en español. Sirve para
     * localizar contenido remoto (p. ej. el título de una notificación) sin
     * depender de recursos. Compatible con contenido antiguo sin traducción.
     */
    fun textoLocalizado(es: String, en: String?, idioma: String = actual): String =
        if (idioma == PreferencesRepository.IDIOMA_EN && !en.isNullOrBlank()) en else es
}
