package com.roberto.gestorpro.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.roberto.gestorpro.data.local.PreparadorLocalCuenta
import com.roberto.gestorpro.ui.auth.LoginScreen
import com.roberto.gestorpro.ui.auth.RecuperarPasswordScreen
import com.roberto.gestorpro.ui.auth.RegistroScreen
import com.roberto.gestorpro.ui.clases.ClasesScreen
import com.roberto.gestorpro.ui.clases.CrearClaseScreen
import com.roberto.gestorpro.ui.clases.DetalleClaseScreen
import com.roberto.gestorpro.ui.clases.DetalleSesionReservasScreen
import com.roberto.gestorpro.ui.clientes.AñadirClienteScreen
import com.roberto.gestorpro.ui.clientes.ClientesScreen
import com.roberto.gestorpro.ui.clientes.PerfilClienteScreen
import com.roberto.gestorpro.ui.configuracion.ConfiguracionScreen
import com.roberto.gestorpro.ui.configuracion.CrearNegocioScreen
import com.roberto.gestorpro.ui.configuracion.CuentaScreen
import com.roberto.gestorpro.ui.configuracion.DatosScreen
import com.roberto.gestorpro.ui.configuracion.MiNegocioScreen
import com.roberto.gestorpro.ui.configuracion.PreferenciasScreen
import com.roberto.gestorpro.ui.configuracion.PoliticaPrivacidadScreen
import com.roberto.gestorpro.ui.configuracion.EliminarCuentaScreen
import com.roberto.gestorpro.ui.configuracion.TerminosDeUsoScreen
import com.roberto.gestorpro.ui.configuracion.GestionDenunciasScreen
import com.roberto.gestorpro.ui.configuracion.HorarioActividadesScreen
import com.roberto.gestorpro.ui.configuracion.HorarioCentroScreen
import com.roberto.gestorpro.ui.economia.EconomiaScreen
import com.roberto.gestorpro.ui.home.HomeScreen
import com.roberto.gestorpro.ui.notificaciones.ConfigNotificacionesScreen
import com.roberto.gestorpro.ui.notificaciones.CrearNotificacionScreen
import com.roberto.gestorpro.ui.notificaciones.GestionNotificacionesScreen
import com.roberto.gestorpro.ui.notificaciones.ModoSeleccion
import com.roberto.gestorpro.ui.notificaciones.SeleccionarClientesScreen
import com.roberto.gestorpro.ui.servicios.DetalleServicioScreen
import com.roberto.gestorpro.ui.servicios.EditarServicioScreen
import com.roberto.gestorpro.ui.servicios.EditarSesionScreen
import com.roberto.gestorpro.ui.servicios.ProgramarSesionesScreen
import com.roberto.gestorpro.ui.servicios.ServiciosScreen
import com.roberto.gestorpro.ui.servicios.SesionReservasScreen
import com.roberto.gestorpro.ui.solicitudes.SolicitudesScreen
import com.roberto.gestorpro.ui.viewmodel.EstadoPreparacion
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.ui.viewmodel.NotificacionesViewModel

/**
 * AppNavigation.kt
 * ----------------
 * ✔ TIPO: archivo de código fuente Kotlin (navegación)
 * Es el NavHost de GestorPro Admin. Centraliza rutas y pantallas del
 * administrador. (Las pantallas de CLIENTE y la Vía B quedaron fuera.)
 */

/**
 * AppNavigation
 * -------------
 * ✔ TIPO: función @Composable
 * Prepara la navegación de GestorPro Admin usando Jetpack Compose.
 * Arranca en Login (sin sesión) o en Home (sesión restaurada).
 */
@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val mainViewModel: MainViewModel = hiltViewModel()

    // ViewModel compartido por las pantallas de notificaciones (lista, crear,
    // configuración): al obtenerlo a nivel de AppNavigation se mantiene una
    // única instancia durante toda la navegación de notificaciones.
    val notificacionesViewModel: NotificacionesViewModel = hiltViewModel()

    // Estado del guard de propietario de la caché local. La UI se bloquea
    // (diálogos) mientras sea Indeterminado o Bloqueado.
    val estadoPreparacion by mainViewModel.estadoPreparacion.collectAsStateWithLifecycle()
    val cambioPropietarioToken by mainViewModel.cambioPropietarioToken.collectAsStateWithLifecycle()

    // Cuando se produce un WIPE por cambio de propietario (token incrementado),
    // los ViewModels que conservan estado propio se resetean a sí mismos.
    // NotificacionesViewModel no lo conoce PreparadorLocalCuenta ni MainViewModel;
    // la capa UI le pide aquí que resetee su estado.
    LaunchedEffect(cambioPropietarioToken) {
        if (cambioPropietarioToken > 0) {
            notificacionesViewModel.resetTrasCambioCuenta()
        }
    }

    var destinoInicial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        destinoInicial = mainViewModel.destinoInicialSegunSesion()
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
            LoginScreen(navController, mainViewModel)
        }

        composable(Routes.REGISTRO) {
            RegistroScreen(navController)
        }

        composable(Routes.RECUPERAR_PASSWORD) {
            RecuperarPasswordScreen(navController)
        }

        composable(Routes.HOME) {
            HomeScreen(navController, mainViewModel)
        }

        composable(Routes.CLIENTES) {
            ClientesScreen(navController)
        }

        composable(
            route = "${Routes.PERFILCLIENTE}/{idCliente}"
        ) { backStackEntry ->

            val idCliente = backStackEntry.arguments
                ?.getString("idCliente")
                ?.toIntOrNull()

            if (idCliente != null) {
                PerfilClienteScreen(
                    navController = navController,
                    idCliente = idCliente
                )
            }
        }

        composable(Routes.AÑADIRCLIENTE) {

            AñadirClienteScreen(navController)
        }

        composable(
            route = "${Routes.MODIFICARCLIENTE}/{idCliente}"
        ) { backStackEntry ->

            val idCliente = backStackEntry.arguments
                ?.getString("idCliente")
                ?.toIntOrNull()

            if (idCliente != null) {
                AñadirClienteScreen(
                    navController = navController,
                    idCliente = idCliente
                )
            }
        }

        composable(Routes.ECONOMIA) {
            EconomiaScreen(navController)
        }

        composable(Routes.CONFIGURACION) {
            ConfiguracionScreen(navController)
        }

        composable(Routes.MINEGOCIO) {
            MiNegocioScreen(navController, mainViewModel)
        }

        composable(Routes.CREAR_NEGOCIO) {
            CrearNegocioScreen(navController)
        }

        composable(Routes.PREFERENCIAS) {
            PreferenciasScreen(navController)
        }

        composable(Routes.DATOS) {
            DatosScreen(navController)
        }

        composable(Routes.CUENTA) {
            CuentaScreen(navController)
        }

        composable(Routes.POLITICA_PRIVACIDAD) {
            PoliticaPrivacidadScreen(navController)
        }

        composable(Routes.ELIMINAR_CUENTA) {
            EliminarCuentaScreen(navController)
        }

        composable(Routes.TERMINOS_CONDICIONES) {
            TerminosDeUsoScreen(navController)
        }

        composable(Routes.DENUNCIAS) {
            GestionDenunciasScreen(navController)
        }

        composable(Routes.CLASES) {
            ClasesScreen(navController)
        }

        composable(Routes.CREAR_CLASE) {
            CrearClaseScreen(navController)
        }

        composable(
            route = "${Routes.DETALLE_CLASE}/{idClase}"
        ) { backStackEntry ->
            val idClase = backStackEntry.arguments
                ?.getString("idClase")
                ?.toIntOrNull()
            if (idClase != null) {
                DetalleClaseScreen(
                    navController = navController,
                    idClase = idClase
                )
            }
        }

        composable(
            route = "${Routes.DETALLE_SESION_RESERVAS}/{idSesion}"
        ) { backStackEntry ->
            val idSesion = backStackEntry.arguments
                ?.getString("idSesion")
                ?.toIntOrNull()
            if (idSesion != null) {
                DetalleSesionReservasScreen(
                    navController = navController,
                    idSesion = idSesion
                )
            }
        }

        composable(Routes.SERVICIOS) {
            ServiciosScreen(navController)
        }

        composable(Routes.CREAR_SERVICIO) {
            EditarServicioScreen(navController)
        }

        composable(
            route = "${Routes.EDITAR_SERVICIO}/{idServicio}"
        ) { backStackEntry ->
            val idServicio = backStackEntry.arguments
                ?.getString("idServicio")
                ?.toIntOrNull()
            if (idServicio != null) {
                EditarServicioScreen(
                    navController = navController,
                    idServicio = idServicio
                )
            }
        }

        composable(
            route = "${Routes.DETALLE_SERVICIO}/{idServicio}"
        ) { backStackEntry ->
            val idServicio = backStackEntry.arguments
                ?.getString("idServicio")
                ?.toIntOrNull()
            if (idServicio != null) {
                DetalleServicioScreen(
                    navController = navController,
                    idServicio = idServicio
                )
            }
        }

        composable(
            route = "${Routes.PROGRAMAR_SESIONES}/{idServicio}"
        ) { backStackEntry ->
            val idServicio = backStackEntry.arguments
                ?.getString("idServicio")
                ?.toIntOrNull()
            if (idServicio != null) {
                ProgramarSesionesScreen(
                    navController = navController,
                    idServicio = idServicio
                )
            }
        }

        composable(
            route = "${Routes.SESION_RESERVAS}/{idSesion}"
        ) { backStackEntry ->
            val idSesion = backStackEntry.arguments
                ?.getString("idSesion")
                ?.toIntOrNull()
            if (idSesion != null) {
                SesionReservasScreen(
                    navController = navController,
                    idSesion = idSesion
                )
            }
        }

        composable(
            route = "${Routes.EDITAR_SESION}/{idSesion}"
        ) { backStackEntry ->
            val idSesion = backStackEntry.arguments
                ?.getString("idSesion")
                ?.toIntOrNull()
            if (idSesion != null) {
                EditarSesionScreen(
                    navController = navController,
                    idSesion = idSesion
                )
            }
        }

        composable(Routes.NOTIFICACIONES) {
            GestionNotificacionesScreen(
                navController = navController,
                viewModel = notificacionesViewModel
            )
        }

        composable(Routes.CREAR_NOTIFICACION) {
            CrearNotificacionScreen(
                navController = navController,
                viewModel = notificacionesViewModel
            )
        }

        composable(Routes.CONFIG_NOTIFICACIONES) {
            ConfigNotificacionesScreen(
                navController = navController,
                viewModel = notificacionesViewModel
            )
        }

        composable(Routes.SELECCIONAR_CLIENTES + "?modo={modo}") { backStackEntry ->
            val modo = backStackEntry.arguments?.getString("modo") ?: "grupo"
            SeleccionarClientesScreen(
                navController = navController,
                viewModel = notificacionesViewModel,
                modoSeleccion = if (modo == "individual") {
                    ModoSeleccion.UNO
                } else {
                    ModoSeleccion.MUCHOS
                }
            )
        }

        composable(Routes.SOLICITUDES) {
            SolicitudesScreen(navController)
        }

        composable(Routes.HORARIO_CENTRO) {
            HorarioCentroScreen(navController)
        }

        composable(Routes.HORARIO_ACTIVIDADES) {
            HorarioActividadesScreen(navController)
        }

    }

    // Overlay del guard de propietario: bloquea el acceso a las pantallas de
    // datos hasta que se resuelva el propietario de la caché local.
    when (val estado = estadoPreparacion) {
        is EstadoPreparacion.Indeterminado -> DialogoPropietarioIndeterminado(
            pendientes = estado.pendientes,
            onEmpezarDeCero = { mainViewModel.decidirPropietarioIndeterminado(conservar = false) },
            onConservar = { mainViewModel.decidirPropietarioIndeterminado(conservar = true) }
        )

        is EstadoPreparacion.Bloqueado -> DialogoCambioBloqueado(
            pendientes = estado.pendientes,
            onCancelar = {
                mainViewModel.cancelarCambioDeCuenta()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onDescartar = { mainViewModel.descartarPendientesYContinuar() }
        )

        else -> Unit
    }
}

/**
 * DialogoPropietarioIndeterminado
 * -------------------------------
 * Muestra el aviso de propietario indeterminado (owner == null con datos
 * locales). NUNCA se adoptan automáticamente: la opción segura por defecto es
 * "Empezar con los datos de mi cuenta" (borra caché local y reconstruye desde
 * Firebase); conservar es una decisión explícita bajo responsabilidad del ADMIN.
 */
@Composable
private fun DialogoPropietarioIndeterminado(
    pendientes: PreparadorLocalCuenta.InformePendientes,
    onEmpezarDeCero: () -> Unit,
    onConservar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Datos locales de otra cuenta") },
        text = {
            Text(
                text = "Esta instalación contiene datos locales que no pueden " +
                    "atribuirse con seguridad a ninguna cuenta. Trazys Admin guarda " +
                    "en este dispositivo los datos de UN solo negocio.\n\n" +
                    if (pendientes.hayAlgo()) {
                        "Además hay ${pendientes.total} operaciones pendientes de " +
                            "sincronizar de la sesión anterior.\n\n"
                    } else {
                        ""
                    } +
                    "La opción recomendada es empezar con los datos de TU cuenta: " +
                    "se borrará la caché local y se reconstruirá desde la nube de tu negocio.",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            Button(onClick = onEmpezarDeCero) {
                Text("Empezar con mis datos (borrar caché local)")
            }
        },
        dismissButton = {
            TextButton(onClick = onConservar) {
                Text("Conservar los datos locales (asumo que son míos)")
            }
        }
    )
}

/**
 * DialogoCambioBloqueado
 * ----------------------
 * Bloquea el cambio de cuenta cuando la cuenta anterior dejó operaciones
 * locales pendientes (eliminaciones o movimientos económicos sin confirmar).
 * NUNCA se ejecutan esos pendientes bajo la nueva cuenta: solo se permite
 * volver a la cuenta anterior o descartar explícitamente la caché local.
 */
@Composable
private fun DialogoCambioBloqueado(
    pendientes: PreparadorLocalCuenta.InformePendientes,
    onCancelar: () -> Unit,
    onDescartar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Cambio de cuenta bloqueado") },
        text = {
            Text(
                text = "La cuenta anterior dejó en este dispositivo " +
                    "${pendientes.total} operación(es) local(es) sin sincronizar " +
                    "(eliminaciones y/o movimientos económicos).\n\n" +
                    "Para no perderlas, estas operaciones no pueden ejecutarse bajo la " +
                    "cuenta nueva. Inicia sesión con la cuenta anterior y sincroniza, " +
                    "o descarta la caché local (se reconstruirá desde la nube de tu negocio).",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            Button(onClick = onCancelar) {
                Text("Volver a iniciar sesión con la cuenta anterior")
            }
        },
        dismissButton = {
            TextButton(onClick = onDescartar) {
                Text("Descartar caché local y continuar")
            }
        }
    )
}
