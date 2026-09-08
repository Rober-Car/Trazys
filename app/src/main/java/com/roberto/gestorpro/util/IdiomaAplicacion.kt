package com.roberto.gestorpro.util

import android.content.Context
import android.content.res.Configuration
import com.roberto.gestorpro.data.repository.PreferencesRepository
import java.util.Locale

/**
 * IdiomaAplicacion
 * ----------------
 * Helper de la infraestructura de idioma de Trazys Admin.
 *
 * Guarda en memoria el idioma que se está aplicando en el proceso (actual) y
 * permite envolver el contexto base de la Activity con una configuración cuyo
 * locale corresponde a ese idioma. El valor se precarga en la Application al
 * arrancar (desde DataStore) y se actualiza justo antes de recrear la Activity
 * cuando el usuario cambia el idioma en Preferencias.
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
}
