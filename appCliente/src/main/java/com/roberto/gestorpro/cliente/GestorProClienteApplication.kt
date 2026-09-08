package com.roberto.gestorpro.cliente

import android.app.Application
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * GestorProClienteApplication
 * ---------------------------
 * ✔ TIPO: Application de Hilt
 * Es el punto de entrada de la app GestorPro Cliente.
 * Sirve para que Hilt construya el grafo de dependencias de la aplicación.
 */
@HiltAndroidApp
class GestorProClienteApplication : Application() {

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
