package com.roberto.gestorpro

import android.app.Application
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.util.IdiomaAplicacion
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * GestorProApplication.kt
 * ----------------------------
 * ✔ TIPO: archivo de código fuente Kotlin (aplicación)
 * Es el archivo que define la clase Application de la aplicación.
 * Sirve para inicializar Hilt y dar el punto de entrada de la app a Android.
 */

/**
 * @HiltAndroidApp
 * ---------------
 * ✔ TIPO: anotación (dagger.hilt.android.HiltAndroidApp)
 * Es la anotación que marca esta clase como la Application principal con Hilt.
 * Sirve para que Hilt genere el componente y pueda inyectar dependencias en toda la app.
 */
@HiltAndroidApp
class GestorProApplication : Application() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        // Precarga el idioma guardado en DataStore ANTES de que se cree la
        // primera Activity: así attachBaseContext ya conoce el locale correcto
        // y no se produce un "flash" del idioma por defecto al arrancar.
        val idioma = try {
            runBlocking { preferencesRepository.obtenerIdioma() }
        } catch (_: Exception) {
            PreferencesRepository.IDIOMA_DEFECTO
        }
        IdiomaAplicacion.marcarActual(idioma)
    }
}
