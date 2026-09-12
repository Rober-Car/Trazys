package com.roberto.gestorpro.ui.notificaciones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.model.ModoDestino
import com.roberto.gestorpro.model.ResolucionDestinatarios
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.components.AyudaContextual
import com.roberto.gestorpro.ui.viewmodel.ClienteViewModel
import com.roberto.gestorpro.ui.viewmodel.NotificacionesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * CrearNotificacionScreen
 * -----------------------
 * Formulario de creación de una notificación del ADMIN (Fase D).
 *
 * Flujo: destino (Individual/Grupo/Todos) -> contenido -> programación
 * opcional -> resumen de destinatarios vinculados -> confirmar.
 *
 * Solo prepara los documentos Firestore (notificaciones/{id} + buzones de
 * los vinculados). El envío FCM real lo hará Cloud Functions en Fase E.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearNotificacionScreen(
    navController: NavHostController,
    viewModel: NotificacionesViewModel,
    clienteViewModel: ClienteViewModel = hiltViewModel()
) {
    val clientes by clienteViewModel.clientes.collectAsStateWithLifecycle()
    val seleccionGrupo by viewModel.seleccionGrupo.collectAsStateWithLifecycle()
    val clienteSeleccionadoId by viewModel.seleccionIndividual.collectAsStateWithLifecycle()
    val resolviendo by viewModel.resolviendo.collectAsStateWithLifecycle()
    val errorResolucion by viewModel.errorResolucion.collectAsStateWithLifecycle()
    val resolucion by viewModel.resolucion.collectAsStateWithLifecycle()
    val creando by viewModel.creando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val errorSincronizacion by viewModel.errorSincronizacion.collectAsStateWithLifecycle()
    val creacionPendiente by viewModel.creacionPendiente.collectAsStateWithLifecycle()
    val requiereAceptarTerminos by viewModel.requiereAceptarTerminos.collectAsStateWithLifecycle()

    var titulo by rememberSaveable { mutableStateOf("") }
    var mensaje by rememberSaveable { mutableStateOf("") }
    var modoDestino by rememberSaveable { mutableStateOf(ModoDestino.TODOS.valor) }
    var programar by rememberSaveable { mutableStateOf(false) }
    var fechaProgramada by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var mostrarTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.consumirMensajeExito()
    }

    val idsObjetivo = when (modoDestino) {
        ModoDestino.INDIVIDUAL.valor ->
            clienteSeleccionadoId?.let { listOf(it) } ?: emptyList()
        ModoDestino.GRUPO.valor -> seleccionGrupo.toList()
        else -> emptyList()
    }

    LaunchedEffect(modoDestino, clienteSeleccionadoId, seleccionGrupo, programar) {
        if (!programar) {
            viewModel.resolverDestinatarios(modoDestino, idsObjetivo)
        }
    }

    val clienteSeleccionado = clientes.firstOrNull { it.idCliente == clienteSeleccionadoId }

    val puedeEnviar = titulo.isNotBlank() &&
        mensaje.isNotBlank() &&
        !creando &&
        when (modoDestino) {
            ModoDestino.INDIVIDUAL.valor -> clienteSeleccionadoId != null
            ModoDestino.GRUPO.valor -> seleccionGrupo.isNotEmpty()
            else -> true
        } &&
        (!programar || fechaProgramada != null) &&
        (programar || (resolucion != null && resolucion?.destinatarios?.isNotEmpty() == true))

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Nueva notificación",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = "Destino",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpcionDestino(
                    titulo = "Individual",
                    seleccionado = modoDestino == ModoDestino.INDIVIDUAL.valor,
                    onClick = {
                        modoDestino = ModoDestino.INDIVIDUAL.valor
                        viewModel.limpiarErrorCreacion()
                    }
                )
                OpcionDestino(
                    titulo = "Grupo",
                    seleccionado = modoDestino == ModoDestino.GRUPO.valor,
                    onClick = {
                        modoDestino = ModoDestino.GRUPO.valor
                        viewModel.limpiarErrorCreacion()
                    }
                )
                OpcionDestino(
                    titulo = "Todos",
                    seleccionado = modoDestino == ModoDestino.TODOS.valor,
                    onClick = {
                        modoDestino = ModoDestino.TODOS.valor
                        viewModel.limpiarErrorCreacion()
                    }
                )
            }

            when (modoDestino) {
                ModoDestino.INDIVIDUAL.valor -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                navController.navigate(
                                    Routes.seleccionarClientes("individual")
                                )
                                viewModel.limpiarErrorCreacion()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE3F2FD),
                        border = BorderStroke(1.dp, Color(0xFF90CAF9))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF1E88E5)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cliente",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = clienteSeleccionado?.nombre
                                        ?: "Selecciona el cliente",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (clienteSeleccionado == null) {
                                        FontWeight.Normal
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = if (clienteSeleccionado == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Abrir selección de cliente",
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    }
                }

                ModoDestino.GRUPO.valor -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.iniciarSeleccionGrupo(seleccionGrupo)
                                navController.navigate(Routes.seleccionarClientes("grupo"))
                                viewModel.limpiarErrorCreacion()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE3F2FD),
                        border = BorderStroke(1.dp, Color(0xFF90CAF9))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Color(0xFF1E88E5)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clientes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (seleccionGrupo.isEmpty()) {
                                        "Seleccionar clientes"
                                    } else {
                                        "${seleccionGrupo.size} clientes seleccionados"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (seleccionGrupo.isEmpty()) {
                                        FontWeight.Normal
                                    } else {
                                        FontWeight.Medium
                                    },
                                    color = if (seleccionGrupo.isEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Abrir selección de clientes",
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    }
                }

                else -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "La notificación se enviará a todos los clientes vinculados del centro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Text(
                text = "Contenido",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = mensaje,
                onValueChange = { mensaje = it },
                label = { Text("Mensaje") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Programación",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Programar envío", style = MaterialTheme.typography.bodyLarge)
                    AyudaContextual(
                        titulo = "Programar envío",
                        texto = "La notificación se guardará como PROGRAMADA y no se " +
                            "enviará hasta la fecha indicada."
                    )
                }
                Switch(
                    checked = programar,
                    onCheckedChange = {
                        programar = it
                        viewModel.limpiarErrorCreacion()
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF1E88E5)
                    )
                )
            }

            if (programar) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fechaProgramada?.let { formatoFechaProgramada(it) } ?: "",
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha y hora de envío") },
                    placeholder = { Text("dd/MM/aaaa HH:mm") },
                    trailingIcon = {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            mostrarDatePicker = true
                            viewModel.limpiarErrorCreacion()
                        }
                )
            }

            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            val textoResumen = resumenDeDestino(
                modoDestino = modoDestino,
                programar = programar,
                idsObjetivo = idsObjetivo,
                resolviendo = resolviendo,
                errorResolucion = errorResolucion,
                resolucionDestinatarios = resolucion
            )
            val esAvisoDestino = errorResolucion != null ||
                (resolucion != null && resolucion?.destinatarios?.isEmpty() == true && !programar)
            if (esAvisoDestino) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Aviso",
                            tint = Color(0xFFB26A00),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = textoResumen,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6D4C00)
                        )
                    }
                }
            } else {
                Text(
                    text = textoResumen,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }

            error?.let { mensajeError ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = mensajeError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (requiereAceptarTerminos) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                            text = "Debes aceptar los Términos de uso antes de publicar " +
                                "notificaciones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Al enviar notificaciones aportas contenido visible para tus " +
                                "clientes. Puedes aceptarlos sin perder lo que ya has escrito.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(
                            onClick = { navController.navigate(Routes.TERMINOS_CONDICIONES) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ver y aceptar los Términos de uso")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPrimaryButton(
                text = if (creando) {
                    "Guardando..."
                } else if (programar) {
                    "Programar notificación"
                } else {
                    "Enviar notificación"
                },
                onClick = {
                    viewModel.crearNotificacion(
                        titulo = titulo.trim(),
                        mensaje = mensaje.trim(),
                        modoDestino = modoDestino,
                        clienteId = clienteSeleccionadoId,
                        idsObjetivo = idsObjetivo,
                        programada = programar,
                        fechaProgramada = fechaProgramada,
                        onExito = { navController.popBackStack() }
                    )
                },
                enabled = puedeEnviar
            )

            if (errorSincronizacion != null || creacionPendiente) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                            onClick = {
                                viewModel.reintentarCreacion(onExito = { navController.popBackStack() })
                            },
                            enabled = creacionPendiente && !creando,
                            fullWidth = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (mostrarDatePicker) {
        val hoy = LocalDate.now()
        val hoyInicioUtc = hoy.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selectable = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= hoyInicioUtc
                override fun isSelectableYear(year: Int): Boolean = year >= hoy.year
            }
        }
        val state = rememberDatePickerState(selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { fechaDiaUtc ->
                            fechaProgramada = fechaDiaUtc
                        }
                        mostrarDatePicker = false
                        if (fechaProgramada != null) {
                            mostrarTimePicker = true
                        }
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (mostrarTimePicker) {
        val actual = fechaProgramada ?: System.currentTimeMillis()
        val zona = ZoneId.systemDefault()
        val horaLocal = Instant.ofEpochMilli(actual).atZone(zona).toLocalTime()
        val timePickerState = TimePickerState(
            initialHour = horaLocal.hour,
            initialMinute = horaLocal.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { mostrarTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fechaProgramada?.let { fechaDiaUtc ->
                        val dia = Instant.ofEpochMilli(fechaDiaUtc)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        fechaProgramada = dia
                            .atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(zona)
                            .toInstant()
                            .toEpochMilli()
                    }
                    mostrarTimePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Hora de envío") },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}

/**
 * OpcionDestino
 * -------------
 * Chip seleccionable de destino (Individual / Grupo / Todos).
 * Se define como extensión de RowScope para poder usar Modifier.weight.
 */
@Composable
private fun RowScope.OpcionDestino(
    titulo: String,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (seleccionado) Color(0xFF1E88E5) else Color.Transparent,
        border = if (seleccionado) null else BorderStroke(1.dp, Color.LightGray)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text(
                text = titulo,
                color = if (seleccionado) Color.White else Color.DarkGray,
                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun resumenDeDestino(
    modoDestino: String,
    programar: Boolean,
    idsObjetivo: List<Int>,
    resolviendo: Boolean,
    errorResolucion: String?,
    resolucionDestinatarios: ResolucionDestinatarios?
): String {
    if (programar) {
        return when (modoDestino) {
            ModoDestino.INDIVIDUAL.valor ->
                "Se programará para el cliente seleccionado."
            ModoDestino.GRUPO.valor ->
                "Se programará para ${idsObjetivo.size} clientes."
            else ->
                "Se programará para todos los clientes vinculados del centro."
        }
    }
    if (resolviendo) return "Comprobando destinatarios..."
    if (errorResolucion != null) return errorResolucion
    val resolucion = resolucionDestinatarios
    if (resolucion == null) {
        return when (modoDestino) {
            ModoDestino.INDIVIDUAL.valor -> "Selecciona el cliente destinatario."
            ModoDestino.GRUPO.valor -> "Selecciona los clientes destinatarios."
            else -> "Comprobando todos los clientes del centro..."
        }
    }
    if (resolucion.totalObjetivo == 0) {
        return when (modoDestino) {
            ModoDestino.INDIVIDUAL.valor -> "Selecciona el cliente destinatario."
            ModoDestino.GRUPO.valor -> "Selecciona los clientes destinatarios."
            else -> "No hay clientes en el centro."
        }
    }
    if (resolucion.destinatarios.isEmpty()) {
        return "No hay clientes vinculados para recibir la notificación."
    }
    val base = "Se enviará a ${resolucion.destinatarios.size} de " +
        "${resolucion.totalObjetivo} clientes vinculados."
    return if (resolucion.omitidos > 0) {
        "$base Se omitirán ${resolucion.omitidos} clientes no vinculados."
    } else {
        base
    }
}

internal fun formatoFechaProgramada(millis: Long): String {
    // El patrón debe usar `yyyy` (año). `aaaa` NO es válido en DateTimeFormatter
    // ("Too many pattern letters: a") y provocaba el cierre de la app.
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
