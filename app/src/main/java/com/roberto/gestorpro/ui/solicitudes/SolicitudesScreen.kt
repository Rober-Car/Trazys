package com.roberto.gestorpro.ui.solicitudes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.model.Cliente
import com.roberto.gestorpro.model.EstadoSolicitud
import com.roberto.gestorpro.model.SolicitudBaja
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppDialogConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogDangerConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.components.AppSemanticButton
import com.roberto.gestorpro.ui.components.SinNegocioContenido
import com.roberto.gestorpro.ui.viewmodel.ClienteViewModel
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.ui.viewmodel.SolicitudesViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SolicitudesScreen
 * -----------------
 * Gestión de solicitudes de baja del ADMIN: lista las solicitudes del negocio
 * (con foto, nombre y fecha del cliente) y permite ACEPTAR (cliente -> BAJA) o
 * RECHAZAR cada solicitud pendiente. Al pulsar una solicitud se abre el perfil
 * del cliente.
 */
@Composable
fun SolicitudesScreen(
    navController: NavHostController,
    viewModel: SolicitudesViewModel = hiltViewModel(),
    clienteViewModel: ClienteViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val solicitudes by viewModel.solicitudes.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val errorSincronizacion by viewModel.errorSincronizacion.collectAsStateWithLifecycle()
    val solicitudSinSincronizar by viewModel.solicitudSinSincronizar.collectAsStateWithLifecycle()
    val mensajeExito by viewModel.mensajeExito.collectAsStateWithLifecycle()
    val clientes by clienteViewModel.clientes.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var solicitudAAceptar by remember { mutableStateOf<SolicitudBaja?>(null) }
    var solicitudARechazar by remember { mutableStateOf<SolicitudBaja?>(null) }
    var solicitudAEliminar by remember { mutableStateOf<SolicitudBaja?>(null) }
    var textoBusqueda by rememberSaveable { mutableStateOf("") }

    /**
     * negocioOk
     * ---------
     * null = comprobando si el ADMIN tiene negocio; false = sin negocio;
     * true = con negocio. Sin negocio NO se consulta Firestore (las Rules de
     * solicitudes lo denegarían) y se muestra SinNegocioContenido.
     */
    var negocioOk by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val conNegocio = mainViewModel.existeNegocioPropio()
        negocioOk = conNegocio
        if (conNegocio) {
            viewModel.cargarSolicitudes()
        }
    }

    LaunchedEffect(mensajeExito) {
        mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajeExito()
        }
    }

    val clientesPorId = clientes.associateBy { it.idCliente }

    // Búsqueda por los datos REALES del cliente asociado a la solicitud: el
    // nombre del modelo ya incluye "nombre apellidos", más teléfono, DNI, email
    // e id de cliente. Se reutiliza el mismo criterio de ClientesScreen.
    val solicitudesFiltradas = if (textoBusqueda.isBlank()) {
        solicitudes
    } else {
        solicitudes.filter { solicitud ->
            val cliente = clientesPorId[solicitud.idCliente]
            cliente != null && listOf(
                cliente.nombre,
                cliente.telefono,
                cliente.dni,
                cliente.email.orEmpty(),
                solicitud.idCliente.toString()
            ).any { it.contains(textoBusqueda, ignoreCase = true) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Solicitudes de baja",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            when (negocioOk) {
                null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                false -> SinNegocioContenido(
                    titulo = "No puedes gestionar solicitudes todavía.",
                    mensaje = "Primero debes crear tu centro para poder gestionar las solicitudes de baja.",
                    onCrearNegocio = { navController.navigate(Routes.MINEGOCIO) }
                )

                true -> {
                    OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar por nombre, apellidos o DNI") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                },
                trailingIcon = {
                    if (textoBusqueda.isNotEmpty()) {
                        IconButton(onClick = { textoBusqueda = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar búsqueda"
                            )
                        }
                    }
                },
                singleLine = true
            )

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
                    onClick = { viewModel.cargarSolicitudes() },
                    fullWidth = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (errorSincronizacion != null || solicitudSinSincronizar != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorSincronizacion
                                ?: "Hay cambios pendientes de sincronizar con la nube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        AppSecondaryButton(
                            text = "Reintentar",
                            onClick = { viewModel.reintentarSincronizacion() },
                            enabled = solicitudSinSincronizar != null,
                            fullWidth = false
                        )
                    }
                }
            }

            when {
                cargando && solicitudes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                solicitudes.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            text = "No hay solicitudes de baja.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Cuando un cliente solicite la baja aparecerá aquí.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    if (solicitudesFiltradas.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.size(16.dp))
                            Text(
                                text = "No se encontraron solicitudes para la búsqueda.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(solicitudesFiltradas, key = { it.idSolicitud }) { solicitud ->
                                SolicitudCard(
                                    solicitud = solicitud,
                                    cliente = clientesPorId[solicitud.idCliente],
                                    onEntrar = {
                                        navController.navigate(Routes.perfilCliente(solicitud.idCliente))
                                    },
                                    onAceptar = { solicitudAAceptar = solicitud },
                                    onRechazar = { solicitudARechazar = solicitud },
                                    onEliminar = { solicitudAEliminar = solicitud }
                                )
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }

    solicitudAAceptar?.let { solicitud ->
        AlertDialog(
            onDismissRequest = { solicitudAAceptar = null },
            title = { Text("Aceptar solicitud de baja") },
            text = {
                Text(
                    "¿Confirmar la baja de ${clientesPorId[solicitud.idCliente]?.nombre ?: "este cliente"}? " +
                        "El cliente pasará a estado BAJA y se le notificará."
                )
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "Aceptar baja",
                    onClick = {
                        viewModel.aceptar(solicitud)
                        solicitudAAceptar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { solicitudAAceptar = null }
                )
            }
        )
    }

    solicitudARechazar?.let { solicitud ->
        AlertDialog(
            onDismissRequest = { solicitudARechazar = null },
            title = { Text("Rechazar solicitud de baja") },
            text = {
                Text(
                    "¿Seguro que quieres rechazar la solicitud de " +
                        "${clientesPorId[solicitud.idCliente]?.nombre ?: "este cliente"}? " +
                        "El cliente permanecerá ACTIVO."
                )
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "Rechazar",
                    onClick = {
                        viewModel.rechazar(solicitud)
                        solicitudARechazar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { solicitudARechazar = null }
                )
            }
        )
    }

    solicitudAEliminar?.let { solicitud ->
        AlertDialog(
            onDismissRequest = { solicitudAEliminar = null },
            title = { Text("¿Eliminar esta solicitud?") },
            text = {
                Text(
                    "Esta acción eliminará la solicitud del historial. " +
                        "No se modifica el estado del cliente ni se borran sus datos."
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.eliminarSolicitud(solicitud)
                        solicitudAEliminar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { solicitudAEliminar = null }
                )
            }
        )
    }
}

/**
 * SolicitudCard
 * -------------
 * Tarjeta de una solicitud de baja con la representación del cliente (foto,
 * nombre) y el estado de la solicitud. Solo las PENDIENTE ofrecen Aceptar /
 * Rechazar.
 */
@Composable
private fun SolicitudCard(
    solicitud: SolicitudBaja,
    cliente: Cliente?,
    onEntrar: () -> Unit,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
    onEliminar: () -> Unit
) {
    val colorEstado = when (solicitud.estado) {
        EstadoSolicitud.ACEPTADA -> Color(0xFF4CAF50)
        EstadoSolicitud.RECHAZADA -> Color.Red
        else -> Color(0xFFFF9800)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEntrar() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorEstado.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cliente != null && cliente.foto.isNotBlank()) {
                    val modeloFoto: Any? = if (
                        com.roberto.gestorpro.data.firebase.FotoClienteStorage
                            .esUrlFoto(cliente.foto)
                    ) {
                        cliente.foto
                    } else {
                        File(cliente.foto)
                    }
                    AsyncImage(
                        model = modeloFoto,
                        contentDescription = "Foto del cliente",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorEstado.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colorEstado,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cliente?.nombre ?: "Cliente ${solicitud.idCliente}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Solicitada el ${formatoFecha(solicitud.fechaSolicitud)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorEstado.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = nombreDeEstado(solicitud.estado),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorEstado
                    )
                }
            }

            if (solicitud.estado == EstadoSolicitud.PENDIENTE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppSemanticButton(
                        text = "Rechazar",
                        color = Color.Red,
                        onClick = onRechazar
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AppSemanticButton(
                        text = "Aceptar baja",
                        color = Color(0xFF4CAF50),
                        onClick = onAceptar
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppSemanticButton(
                        text = "Eliminar del historial",
                        color = Color.Gray,
                        onClick = onEliminar,
                        icon = Icons.Default.Delete
                    )
                }
            }
        }
    }
}

private fun nombreDeEstado(estado: EstadoSolicitud): String {
    return when (estado) {
        EstadoSolicitud.ACEPTADA -> "ACEPTADA"
        EstadoSolicitud.RECHAZADA -> "RECHAZADA"
        else -> "PENDIENTE"
    }
}

private fun formatoFecha(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
