package com.roberto.gestorpro.ui.notificaciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.model.NotificacionAdmin
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppDialogDangerConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppIconPrimaryButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.components.AppSemanticButton
import com.roberto.gestorpro.ui.viewmodel.NotificacionesViewModel
import com.roberto.gestorpro.util.RetiradaNotificacionReglas
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * GestionNotificacionesScreen
 * ---------------------------
 * Pantalla principal de notificaciones del ADMIN.
 * Muestra las notificaciones del negocio (notificaciones/{id}) de más
 * reciente a más antigua, con el botón de crear (FAB), acceso a la
 * configuración de preconfiguradas y cancelación de programadas.
 */
@Composable
fun GestionNotificacionesScreen(
    navController: NavHostController,
    viewModel: NotificacionesViewModel
) {
    val notificaciones by viewModel.notificaciones.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val errorSincronizacion by viewModel.errorSincronizacion.collectAsStateWithLifecycle()
    val mensajeExito by viewModel.mensajeExito.collectAsStateWithLifecycle()
    val retirandoNotificacion by viewModel.retirandoNotificacion.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var notificacionACancelar by remember { mutableStateOf<NotificacionAdmin?>(null) }
    var notificacionARetirar by remember { mutableStateOf<NotificacionAdmin?>(null) }

    LaunchedEffect(Unit) {
        viewModel.cargarNotificaciones()
    }

    LaunchedEffect(mensajeExito) {
        mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajeExito()
            viewModel.cargarNotificaciones()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.CREAR_NOTIFICACION) },
                containerColor = Color(0xFF1E88E5)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nueva notificación",
                    tint = Color.White
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Notificaciones",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                AppIconPrimaryButton(
                    icon = Icons.Default.Settings,
                    onClick = { navController.navigate(Routes.CONFIG_NOTIFICACIONES) },
                    contentDescription = "Configuración de notificaciones"
                )
            }

            error?.let { mensaje ->
                Text(
                    text = mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                AppSecondaryButton(
                    text = "Reintentar",
                    onClick = { viewModel.cargarNotificaciones() },
                    fullWidth = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            errorSincronizacion?.let { mensaje ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when {
                cargando && notificaciones.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                notificaciones.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            text = "No hay notificaciones enviadas.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Pulsa + para crear la primera notificación",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(notificaciones, key = { it.id }) { notificacion ->
                            NotificacionAdminCard(
                                notificacion = notificacion,
                                retirando = retirandoNotificacion == notificacion.id,
                                onCancelar = {
                                    notificacionACancelar = notificacion
                                },
                                onRetirar = {
                                    notificacionARetirar = notificacion
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    notificacionACancelar?.let { notificacion ->
        AlertDialog(
            onDismissRequest = { notificacionACancelar = null },
            title = { Text("Cancelar notificación") },
            text = {
                Text(
                    "¿Seguro que quieres cancelar \"${notificacion.titulo}\"? " +
                        "No se enviará cuando llegue la fecha programada."
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Cancelar",
                    onClick = {
                        viewModel.cancelarNotificacion(notificacion.id)
                        notificacionACancelar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Atrás",
                    onClick = { notificacionACancelar = null }
                )
            }
        )
    }

    notificacionARetirar?.let { notificacion ->
        AlertDialog(
            onDismissRequest = { notificacionARetirar = null },
            title = { Text("Retirar notificación") },
            text = {
                Text(
                    "¿Retirar esta notificación?\n\n" +
                        "Dejará de estar disponible para los destinatarios y " +
                        "la acción es permanente."
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Retirar",
                    onClick = {
                        viewModel.retirarNotificacion(notificacion.id)
                        notificacionARetirar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Atrás",
                    onClick = { notificacionARetirar = null }
                )
            }
        )
    }
}

/**
 * NotificacionAdminCard
 * ---------------------
 * Tarjeta de una notificación del ADMIN: título, mensaje (resumen), tipo,
 * destino, fecha y estado.
 *
 * Acciones:
 *  - "Cancelar": solo PROGRAMADA activas (aún no enviadas).
 *  - "Retirar": solo notificaciones MANUALES ya publicadas/en entrega
 *    (moderación UGC). Nunca automáticas/preconfiguradas ni programadas
 *    aún no enviadas.
 */
@Composable
private fun NotificacionAdminCard(
    notificacion: NotificacionAdmin,
    retirando: Boolean,
    onCancelar: () -> Unit,
    onRetirar: () -> Unit
) {
    val colorEstado = colorDeEstado(notificacion.estado)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorEstado.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notificacion.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorEstado.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = nombreDeEstado(notificacion.estado),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorEstado
                    )
                }
            }

            if (notificacion.mensaje.isNotBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = notificacion.mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${nombreDeTipo(notificacion.tipo)} · ${descripcionDeDestino(notificacion)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatoFecha(notificacion.fechaEnvio
                        ?: notificacion.fechaProgramada
                        ?: notificacion.fechaCreacion),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (notificacion.programada && notificacion.estado == "PROGRAMADA") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppSemanticButton(
                        text = "Cancelar",
                        color = Color.Red,
                        onClick = onCancelar
                    )
                }
            } else if (RetiradaNotificacionReglas.esRetirable(
                    notificacion.origen,
                    notificacion.estado
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (retirando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    AppSemanticButton(
                        text = "Retirar",
                        color = Color.Red,
                        onClick = onRetirar
                    )
                }
            }
        }
    }
}

private fun colorDeEstado(estado: String): Color {
    return when (estado) {
        "ENVIADA" -> Color(0xFF4CAF50)
        "PROGRAMADA" -> Color(0xFF1E88E5)
        "CANCELADA" -> Color.Gray
        "ERROR" -> Color.Red
        else -> Color(0xFFFF9800)
    }
}

private fun nombreDeEstado(estado: String): String {
    return when (estado) {
        "ENVIADA" -> "ENVIADA"
        "PROGRAMADA" -> "PROGRAMADA"
        "CANCELADA" -> "CANCELADA"
        "ERROR" -> "ERROR"
        else -> "PENDIENTE"
    }
}

private fun nombreDeTipo(tipo: String): String {
    return when (tipo) {
        "MOROSIDAD" -> "Morosidad"
        "BAJA_CONFIRMADA" -> "Baja confirmada"
        "PROGRAMADA" -> "Programada"
        "SOLICITUD_BAJA" -> "Solicitud de baja"
        "VINCULACION" -> "Vinculación"
        else -> "Manual"
    }
}

private fun descripcionDeDestino(notificacion: NotificacionAdmin): String {
    return when (notificacion.modoDestino) {
        "INDIVIDUAL" -> "Individual"
        "GRUPO" -> "Grupo (${notificacion.idsClientes.size})"
        else -> "Todos (${notificacion.idsClientes.size})"
    }
}

private fun formatoFecha(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
