package com.roberto.gestorpro.cliente.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.model.EstadoCliente
import com.roberto.gestorpro.cliente.model.EstadoSolicitud
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.MenuCard
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * CuentaScreen
 * ------------
 * Gestión de la cuenta del CLIENTE: cerrar sesión y solicitar la baja del
 * gimnasio (la solicitud queda PENDIENTE hasta que el ADMIN la resuelva; el
 * cliente permanece ACTIVO mientras tanto).
 */
@Composable
fun CuentaScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val solicitudesBaja by mainViewModel.solicitudesBaja.collectAsStateWithLifecycle()
    val cargandoSolicitudes by mainViewModel.cargandoSolicitudesBaja.collectAsStateWithLifecycle()
    val operandoSolicitud by mainViewModel.operandoSolicitudBaja.collectAsStateWithLifecycle()
    val errorSolicitud by mainViewModel.errorSolicitudBaja.collectAsStateWithLifecycle()
    val mensaje by mainViewModel.mensaje.collectAsStateWithLifecycle()
    val cliente by mainViewModel.cliente.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val cambiandoContrasena by mainViewModel.cambiandoContrasena.collectAsStateWithLifecycle()

    var mostrarDialogoContrasena by remember { mutableStateOf(false) }
    var mostrarDialogoCerrarSesion by remember { mutableStateOf(false) }
    var mostrarConfirmarBaja by remember { mutableStateOf(false) }
    var mostrarPendienteInfo by remember { mutableStateOf(false) }

    // Textos localizados del bloque de cuenta.
    val textoVolver = stringResource(R.string.accion_volver)
    val textoMiCuenta = stringResource(R.string.cuenta_titulo)
    val textoYaDadoDeBaja = stringResource(R.string.cuenta_desc_ya_dado_de_baja)
    val textoBajaPendiente = stringResource(R.string.cuenta_desc_baja_pendiente)
    val textoBajaRechazada = stringResource(R.string.cuenta_desc_baja_rechazada)
    val textoSolicitarLaBaja = stringResource(R.string.cuenta_desc_solicitar_baja)
    val textoSolicitarBaja = stringResource(R.string.cuenta_titulo_solicitar_baja)
    val textoCambiarContrasena = stringResource(R.string.cuenta_titulo_cambiar_contrasena)
    val textoDescCambiarContrasena = stringResource(R.string.cuenta_desc_cambiar_contrasena)
    val textoCerrarSesion = stringResource(R.string.cuenta_titulo_cerrar_sesion)
    val textoDescCerrarSesion = stringResource(R.string.cuenta_desc_cerrar_sesion)
    val textoComprobandoSolicitudes = stringResource(R.string.cuenta_comprobando_solicitudes)
    val textoCancelar = stringResource(R.string.accion_cancelar)
    val textoContrasenaActualizada =
        stringResource(R.string.cuenta_snackbar_contrasena_actualizada)

    LaunchedEffect(Unit) {
        mainViewModel.cargarSolicitudesBaja()
    }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.limpiarMensaje()
        }
    }

    val tienePendiente = solicitudesBaja.any { it.estado == EstadoSolicitud.PENDIENTE }
    val ultimaRechazada = solicitudesBaja.firstOrNull()?.let {
        it.estado == EstadoSolicitud.RECHAZADA
    } ?: false

    val estadoBaja = cliente?.estado == EstadoCliente.BAJA

    val descripcionBaja = when {
        estadoBaja -> textoYaDadoDeBaja
        tienePendiente -> textoBajaPendiente
        ultimaRechazada -> textoBajaRechazada
        else -> textoSolicitarLaBaja
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = textoVolver,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = textoMiCuenta,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MenuCard(
                titulo = textoSolicitarBaja,
                descripcion = descripcionBaja,
                icono = Icons.Default.Person,
                onClick = {
                    when {
                        estadoBaja -> {
                            // Sin acción: el cliente ya está de baja.
                        }
                        tienePendiente -> mostrarPendienteInfo = true
                        else -> mostrarConfirmarBaja = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            MenuCard(
                titulo = textoCambiarContrasena,
                descripcion = textoDescCambiarContrasena,
                icono = Icons.Default.Lock,
                onClick = { mostrarDialogoContrasena = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            MenuCard(
                titulo = textoCerrarSesion,
                descripcion = textoDescCerrarSesion,
                icono = Icons.Default.ExitToApp,
                onClick = { mostrarDialogoCerrarSesion = true }
            )

            if (cargandoSolicitudes) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = textoComprobandoSolicitudes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            errorSolicitud?.let { mensajeError ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = mensajeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (mostrarDialogoContrasena) {
        DialogoCambiarContrasena(
            cambiando = cambiandoContrasena,
            onDismiss = { mostrarDialogoContrasena = false },
            onConfirmar = { actual, nueva, repetida ->
                mainViewModel.cambiarContrasena(actual, nueva, repetida)
            },
            onExito = {
                mostrarDialogoContrasena = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(textoContrasenaActualizada)
                }
            }
        )
    }

    if (mostrarDialogoCerrarSesion) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoCerrarSesion = false },
            title = {
                Text(
                    text = textoCerrarSesion,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1E88E5)
                )
            },
            text = {
                Text(stringResource(R.string.cuenta_dialogo_cerrar_pregunta))
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogoCerrarSesion = false
                        mainViewModel.cerrarSesion()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text(textoCerrarSesion, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoCerrarSesion = false }) {
                    Text(textoCancelar)
                }
            }
        )
    }

    if (mostrarConfirmarBaja) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarBaja = false },
            title = {
                Text(
                    text = textoSolicitarBaja,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF44336)
                )
            },
            text = {
                Text(stringResource(R.string.cuenta_dialogo_baja_pregunta))
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarConfirmarBaja = false
                        coroutineScope.launch {
                            val error = mainViewModel.solicitarBaja(null)
                            if (error != null) {
                                snackbarHostState.showSnackbar(error)
                            }
                        }
                    },
                    enabled = !operandoSolicitud,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text(textoSolicitarBaja, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarBaja = false }) {
                    Text(textoCancelar)
                }
            }
        )
    }

    if (mostrarPendienteInfo) {
        AlertDialog(
            onDismissRequest = { mostrarPendienteInfo = false },
            title = {
                Text(
                    text = stringResource(R.string.cuenta_dialogo_pendiente_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFF9800)
                )
            },
            text = {
                Text(stringResource(R.string.cuenta_dialogo_pendiente_texto))
            },
            confirmButton = {
                TextButton(onClick = { mostrarPendienteInfo = false }) {
                    Text(stringResource(R.string.cuenta_accion_entendido))
                }
            }
        )
    }
}

/**
 * DialogoCambiarContrasena (CLIENTE)
 * ----------------------------------
 * Diálogo de cambio de contraseña con el mismo comportamiento de seguridad que
 * el de ADMIN: contraseña actual + nueva + repetición, reautenticación y
 * updatePassword reales en Firebase, error visible y cierre solo tras éxito.
 *
 * La validación previa la resuelve el ViewModel (con mensajes localizados); la
 * función pura `validarCambioContrasena` se conserva en el repositorio para sus
 * tests y no se usa aquí para mostrar texto al usuario.
 */
@Composable
private fun DialogoCambiarContrasena(
    cambiando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: suspend (actual: String, nueva: String, repetida: String) -> String?,
    onExito: () -> Unit
) {
    var contrasenaActual by remember { mutableStateOf("") }
    var nuevaContrasena by remember { mutableStateOf("") }
    var repetirContrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var contrasenaActualVisible by rememberSaveable { mutableStateOf(false) }
    var nuevaContrasenaVisible by rememberSaveable { mutableStateOf(false) }
    var repetirContrasenaVisible by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val camposValidos = contrasenaActual.isNotBlank() &&
        nuevaContrasena.isNotBlank() &&
        nuevaContrasena == repetirContrasena

    // Textos localizados del diálogo.
    val textoCambiarContrasena = stringResource(R.string.cuenta_titulo_cambiar_contrasena)
    val textoCambiandoContrasena = stringResource(R.string.cuenta_cambiando_contrasena)
    val textoContrasenaActual = stringResource(R.string.cuenta_label_contrasena_actual)
    val textoNuevaContrasena = stringResource(R.string.cuenta_label_nueva_contrasena)
    val textoRepetirContrasena = stringResource(R.string.auth_repetir_contrasena)
    val textoOcultarContrasena = stringResource(R.string.auth_ocultar_contrasena)
    val textoMostrarContrasena = stringResource(R.string.auth_mostrar_contrasena)
    val textoCancelar = stringResource(R.string.accion_cancelar)
    val textoGuardar = stringResource(R.string.accion_guardar)
    val textoCambiando = stringResource(R.string.cuenta_accion_cambiando)

    Dialog(
        onDismissRequest = {
            if (!cambiando) onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = textoCambiarContrasena,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                if (cambiando) {
                    Text(
                        text = textoCambiandoContrasena,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = contrasenaActual,
                    onValueChange = {
                        contrasenaActual = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text(textoContrasenaActual) },
                    trailingIcon = {
                        IconButton(
                            onClick = { contrasenaActualVisible = !contrasenaActualVisible }
                        ) {
                            Icon(
                                imageVector = if (contrasenaActualVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (contrasenaActualVisible) {
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (contrasenaActualVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nuevaContrasena,
                    onValueChange = {
                        nuevaContrasena = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text(textoNuevaContrasena) },
                    trailingIcon = {
                        IconButton(
                            onClick = { nuevaContrasenaVisible = !nuevaContrasenaVisible }
                        ) {
                            Icon(
                                imageVector = if (nuevaContrasenaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (nuevaContrasenaVisible) {
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (nuevaContrasenaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repetirContrasena,
                    onValueChange = {
                        repetirContrasena = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text(textoRepetirContrasena) },
                    trailingIcon = {
                        IconButton(
                            onClick = { repetirContrasenaVisible = !repetirContrasenaVisible }
                        ) {
                            Icon(
                                imageVector = if (repetirContrasenaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (repetirContrasenaVisible) {
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (repetirContrasenaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let { mensajeError ->
                    Text(
                        text = mensajeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        enabled = !cambiando,
                        onClick = onDismiss
                    ) {
                        Text(textoCancelar)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            error = null
                            coroutineScope.launch {
                                val resultado = onConfirmar(
                                    contrasenaActual,
                                    nuevaContrasena,
                                    repetirContrasena
                                )
                                if (resultado == null) {
                                    onExito()
                                } else {
                                    error = resultado
                                }
                            }
                        },
                        enabled = camposValidos && !cambiando
                    ) {
                        Text(if (cambiando) textoCambiando else textoGuardar)
                    }
                }
            }
        }
    }
}
