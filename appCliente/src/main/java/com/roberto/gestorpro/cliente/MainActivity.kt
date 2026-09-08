package com.roberto.gestorpro.cliente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.roberto.gestorpro.cliente.data.firebase.DispositivoRepository
import com.roberto.gestorpro.cliente.navigation.AppNavigation
import com.roberto.gestorpro.cliente.ui.theme.GestorProClienteTheme
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity
 * ------------
 * ✔ TIPO: Activity única de GestorPro Cliente (@AndroidEntryPoint)
 * Es el punto de entrada Compose de la aplicación.
 * Al arrancar: solicita el permiso de notificaciones (Android 13+) y registra
 * el token FCM del dispositivo si el cliente ya está vinculado.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dispositivoRepository: DispositivoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val launcherPermiso = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                // Fase A (infra): registra el token FCM si hay cliente vinculado.
                dispositivoRepository.registrarTokenActual()

                // Permiso de notificaciones en Android 13+.
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    launcherPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val idioma by viewModel.idioma.collectAsState()

            LaunchedEffect(idioma) {
                if (idioma != IdiomaAplicacion.actual) {
                    IdiomaAplicacion.marcarActual(idioma)
                    recreate()
                }
            }

            GestorProClienteTheme(themeMode = themeMode) {
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
