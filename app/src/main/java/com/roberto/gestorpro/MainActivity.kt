package com.roberto.gestorpro

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.roberto.gestorpro.navigation.AppNavigation
import com.roberto.gestorpro.ui.theme.GestorProTheme
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.util.IdiomaAplicacion
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 * ------------
 * ✔ TIPO: Activity única de la aplicación (@AndroidEntryPoint)
 * Es el punto de entrada Compose de la app GestorPro Admin.
 * Sirve para arrancar el NavHost con el tema elegido por el administrador.
 * (La Vía B / deep link de vinculación individual quedó descartada.)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val idioma by viewModel.idioma.collectAsState()

            LaunchedEffect(idioma) {
                if (idioma != IdiomaAplicacion.actual) {
                    IdiomaAplicacion.marcarActual(idioma)
                    recreate()
                }
            }

            GestorProTheme(themeMode = themeMode) {
                AppNavigation()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Aplica el idioma elegido a los recursos de la Activity. IdiomaAplicacion
        // ya contiene el valor precargado en la Application (o el actualizado antes
        // de recrear) cuando se ejecuta attachBaseContext.
        super.attachBaseContext(IdiomaAplicacion.baseConIdioma(newBase))
    }
}
