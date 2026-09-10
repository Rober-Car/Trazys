package com.roberto.gestorpro.cliente.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.roberto.gestorpro.cliente.ui.auth.CuentaScreen
import com.roberto.gestorpro.cliente.ui.auth.CompletarPerfilScreen
import com.roberto.gestorpro.cliente.ui.auth.EleccionInicioScreen
import com.roberto.gestorpro.cliente.ui.configuracion.ConfiguracionScreen
import com.roberto.gestorpro.cliente.ui.configuracion.EliminarCuentaScreen
import com.roberto.gestorpro.cliente.ui.configuracion.InformacionLegalScreen
import com.roberto.gestorpro.cliente.ui.configuracion.PoliticaPrivacidadScreen
import com.roberto.gestorpro.cliente.ui.configuracion.TerminosDeUsoScreen
import com.roberto.gestorpro.cliente.ui.configuracion.NotificacionesScreen
import com.roberto.gestorpro.cliente.ui.auth.EditarPerfilScreen
import com.roberto.gestorpro.cliente.ui.auth.InicioScreen
import com.roberto.gestorpro.cliente.ui.auth.LoginScreen
import com.roberto.gestorpro.cliente.ui.auth.MiPerfilScreen
import com.roberto.gestorpro.cliente.ui.auth.RecuperarPasswordScreen
import com.roberto.gestorpro.cliente.ui.auth.RegistroScreen
import com.roberto.gestorpro.cliente.ui.home.AsistentesSesionScreen
import com.roberto.gestorpro.cliente.ui.home.ClasesScreen
import com.roberto.gestorpro.cliente.ui.home.HomeScreen
import com.roberto.gestorpro.cliente.ui.home.HorarioScreen
import com.roberto.gestorpro.cliente.ui.notificaciones.ListaNotificacionesScreen
import com.roberto.gestorpro.cliente.ui.rutinas.RutinasScreen
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel

/**
 * AppNavigation
 * -------------
 * ✔ TIPO: función @Composable
 * NavHost de GestorPro Cliente.
 */
@Composable
fun AppNavigation() {

    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()

    var destinoInicial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        destinoInicial = mainViewModel.destinoInicial()
    }

    val destino = destinoInicial
    if (destino == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = destino
    ) {

        composable(Routes.LOGIN) {
            LoginScreen(navController)
        }

        composable(Routes.REGISTRO) {
            RegistroScreen(navController)
        }

        composable(Routes.RECUPERAR_PASSWORD) {
            RecuperarPasswordScreen(navController)
        }

        composable(Routes.ELECCION) {
            EleccionInicioScreen(navController)
        }

        composable(Routes.INICIO) {
            InicioScreen(navController, mainViewModel)
        }

        composable(Routes.COMPLETAR_PERFIL) {
            CompletarPerfilScreen(navController, mainViewModel)
        }

        composable(Routes.HOME) {
            HomeScreen(navController, mainViewModel)
        }

        composable(Routes.CLASES) {
            ClasesScreen(navController)
        }

        composable(Routes.ASISTENTES_SESION) { backStackEntry ->
            val idSesion = backStackEntry.arguments
                ?.getString("idSesion")
                ?.toIntOrNull()
            if (idSesion != null) {
                AsistentesSesionScreen(
                    navController = navController,
                    idSesion = idSesion
                )
            }
        }

        composable(Routes.MI_PERFIL) {
            MiPerfilScreen(navController)
        }

        composable(Routes.EDITAR_PERFIL) {
            EditarPerfilScreen(navController)
        }

        composable(Routes.CUENTA) {
            CuentaScreen(navController)
        }

        composable(Routes.CONFIGURACION) {
            ConfiguracionScreen(navController)
        }

        composable(Routes.NOTIFICACIONES) {
            ListaNotificacionesScreen(navController)
        }

        composable(Routes.CONFIGURACION_NOTIFICACIONES) {
            NotificacionesScreen(navController)
        }

        composable(Routes.RUTINAS) {
            RutinasScreen(navController)
        }

        composable(Routes.HORARIO) {
            HorarioScreen(navController)
        }

        composable(Routes.POLITICA_PRIVACIDAD) {
            PoliticaPrivacidadScreen(
                navController = navController
            )
        }

        composable(Routes.ELIMINAR_CUENTA) {
            EliminarCuentaScreen(navController = navController)
        }

        composable(Routes.TERMINOS_CONDICIONES) {
            TerminosDeUsoScreen(navController = navController)
        }
    }
}
