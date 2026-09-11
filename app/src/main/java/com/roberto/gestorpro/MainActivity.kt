package com.roberto.gestorpro

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
import com.roberto.gestorpro.data.firebase.DispositivoAdminRepository
import com.roberto.gestorpro.navigation.AppNavigation
import com.roberto.gestorpro.ui.theme.GestorProTheme
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.util.IdiomaAplicacion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity
 * ------------
 * ✔ TIPO: Activity única de la aplicación (@AndroidEntryPoint)
 * Es el punto de entrada Compose de la app GestorPro Admin.
 * Sirve para arrancar el NavHost con el tema elegido por el administrador y para
 * registrar el token FCM del ADMIN (canal de push exclusivo del administrador).
 * (La Vía B / deep link de vinculación individual quedó descartada.)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dispositivoAdminRepository: DispositivoAdminRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val launcherPermiso = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            val viewModel: MainViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val idioma by viewModel.idioma.collectAsState()

            LaunchedEffect(Unit) {
                // Registra el token FCM del ADMIN si hay sesión (canal de push
                // exclusivo del administrador; nunca al CLIENTE).
                dispositivoAdminRepository.registrarTokenActual()

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
