package com.roberto.gestorpro.ui.configuracion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.model.ExcepcionHorario
import com.roberto.gestorpro.model.TramoHorario
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.HorarioViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Azul corporativo de Trazys. */
private val AzulTrazys = Color(0xFF1E88E5)

/**
 * HorarioCentroScreen
 * -------------------
 * Configuración del horario del centro:
 *  - aplicación rápida de un horario a TODA la semana (días abiertos);
 *  - horario semanal habitual (un tramo apertura/cierre por día, o cerrado);
 *  - excepciones para FECHAS concretas con prioridad sobre el semanal.
 * Todo el estado del formulario se conserva ante rotación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorarioCentroScreen(
    navController: NavHostController,
    viewModel: HorarioViewModel = hiltViewModel()
) {
    val horario by viewModel.horario.collectAsStateWithLifecycle()
    val cargado by viewModel.cargado.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mensajeExito by viewModel.mensajeExito.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.cargar() }
    LaunchedEffect(mensajeExito) {
        mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajeExito()
        }
    }

    // Estado del formulario (persistente ante cambios de configuración).
    var precargado by rememberSaveable { mutableStateOf(false) }
    var centro by rememberSaveable(stateSaver = SaverCentro) {
        mutableStateOf(mapOf<DayOfWeek, TramoHorario>())
    }
    var excepciones by rememberSaveable(stateSaver = SaverExcepciones) {
        mutableStateOf(listOf<ExcepcionHorario>())
    }
    var aperturaGlobal by rememberSaveable { mutableStateOf("09:00") }
    var cierreGlobal by rememberSaveable { mutableStateOf("21:00") }

    LaunchedEffect(cargado) {
        if (cargado && !precargado) {
            centro = horario.centro
            excepciones = horario.excepciones
            precargado = true
        }
    }

    var seleccion by remember { mutableStateOf<Pair<DayOfWeek, Boolean>?>(null) }
    var timePickerGlobal by remember { mutableStateOf<Boolean?>(null) }
    var mostrarDatePickerExcepcion by remember { mutableStateOf(false) }
    var dialogoExcepcionAbierto by remember { mutableStateOf(false) }
    var indiceExcepcionEditando by remember { mutableStateOf<Int?>(null) }
    var excFecha by remember { mutableStateOf<Long?>(null) }
    var excCerrado by remember { mutableStateOf(false) }
    var excApertura by remember { mutableStateOf("09:00") }
    var excCierre by remember { mutableStateOf("21:00") }
    var timePickerExcepcion by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
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
                Column {
                    Text(
                        text = "Horario del centro",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Horario semanal y excepciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // --- Aplicar a toda la semana ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Aplicar horario a toda la semana",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Aplica la misma apertura y cierre a todos los días " +
                            "abiertos. Después puedes ajustar cada día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CampoHora(
                            etiqueta = "Apertura",
                            valor = aperturaGlobal,
                            onClick = { timePickerGlobal = true },
                            modifier = Modifier.weight(1f)
                        )
                        CampoHora(
                            etiqueta = "Cierre",
                            valor = cierreGlobal,
                            onClick = { timePickerGlobal = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(onClick = {
                        centro = centro.toMutableMap().also { mapa ->
                            diasSemana.forEach { dia ->
                                val tramo = mapa[dia] ?: TramoHorario()
                                if (!tramo.cerrado) {
                                    mapa[dia] = tramo.copy(
                                        apertura = aperturaGlobal,
                                        cierre = cierreGlobal
                                    )
                                }
                            }
                        }
                    }) {
                        Text("Aplicar a toda la semana")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Horario habitual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AzulTrazys
            )
            Text(
                text = "Configura para cada día si el centro está abierto y su hora " +
                    "de apertura y cierre.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            diasSemana.forEach { dia ->
                val tramo = centro[dia] ?: TramoHorario()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nombreDia(dia),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (tramo.cerrado) "Cerrado" else "Abierto",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = !tramo.cerrado,
                                onCheckedChange = { abierto ->
                                    centro = centro + (dia to tramo.copy(cerrado = !abierto))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AzulTrazys
                                )
                            )
                        }
                        if (!tramo.cerrado) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CampoHora(
                                    etiqueta = "Apertura",
                                    valor = tramo.apertura,
                                    onClick = { seleccion = dia to true },
                                    modifier = Modifier.weight(1f)
                                )
                                CampoHora(
                                    etiqueta = "Cierre",
                                    valor = tramo.cierre,
                                    onClick = { seleccion = dia to false },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Excepciones (días concretos)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AzulTrazys
            )
            Text(
                text = "Festivos, cierres u horarios especiales para una fecha concreta. " +
                    "Tienen prioridad sobre el horario habitual de ese día.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (excepciones.isEmpty()) {
                Text(
                    text = "No hay excepciones configuradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                excepciones.forEachIndexed { indice, excepcion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatearFecha(excepcion.fecha),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (excepcion.cerrado) {
                                        "Cerrado"
                                    } else {
                                        "${excepcion.apertura} - ${excepcion.cierre}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                indiceExcepcionEditando = indice
                                excFecha = excepcion.fecha
                                excCerrado = excepcion.cerrado
                                excApertura = excepcion.apertura.ifBlank { "09:00" }
                                excCierre = excepcion.cierre.ifBlank { "21:00" }
                                dialogoExcepcionAbierto = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar excepción",
                                    tint = AzulTrazys
                                )
                            }
                            IconButton(onClick = {
                                excepciones = excepciones.toMutableList().also {
                                    it.removeAt(indice)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar excepción",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = {
                indiceExcepcionEditando = null
                excFecha = null
                excCerrado = false
                excApertura = "09:00"
                excCierre = "21:00"
                mostrarDatePickerExcepcion = true
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = AzulTrazys)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Añadir excepción", color = AzulTrazys)
            }

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPrimaryButton(
                text = if (guardando) "Guardando..." else "Guardar horario",
                onClick = { viewModel.guardarCentro(centro, excepciones) },
                enabled = !guardando,
                fullWidth = false,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Diálogo de hora del horario semanal ---
    val actual = seleccion
    if (actual != null) {
        val (dia, esApertura) = actual
        val tramo = centro[dia] ?: TramoHorario()
        val valor = if (esApertura) tramo.apertura else tramo.cierre
        val partes = valor.split(":")
        val timePickerState = rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: if (esApertura) 9 else 21,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { seleccion = null },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    val hora = "$h:$m"
                    val base = centro[dia] ?: TramoHorario()
                    centro = centro + (dia to if (esApertura) {
                        base.copy(apertura = hora)
                    } else {
                        base.copy(cierre = hora)
                    })
                    seleccion = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { seleccion = null }) { Text("Cancelar") }
            },
            title = {
                Text("${if (esApertura) "Apertura" else "Cierre"} · ${nombreDia(dia)}")
            },
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

    // --- Diálogo de hora global ---
    val global = timePickerGlobal
    if (global != null) {
        val valor = if (global) aperturaGlobal else cierreGlobal
        val partes = valor.split(":")
        val timePickerState = rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: if (global) 9 else 21,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerGlobal = null },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    val hora = "$h:$m"
                    if (global) aperturaGlobal = hora else cierreGlobal = hora
                    timePickerGlobal = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerGlobal = null }) { Text("Cancelar") }
            },
            title = { Text(if (global) "Apertura (toda la semana)" else "Cierre (toda la semana)") },
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

    // --- Selector de fecha de la excepción ---
    if (mostrarDatePickerExcepcion) {
        val state = rememberDatePickerState(initialSelectedDateMillis = excFecha)
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerExcepcion = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            excFecha = normalizarFechaLocal(it)
                        }
                        mostrarDatePickerExcepcion = false
                        dialogoExcepcionAbierto = true
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerExcepcion = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    // --- Detalle de la excepción ---
    if (dialogoExcepcionAbierto) {
        AlertDialog(
            onDismissRequest = { dialogoExcepcionAbierto = false },
            confirmButton = {
                TextButton(
                    enabled = excFecha != null,
                    onClick = {
                        val fecha = excFecha ?: return@TextButton
                        val nueva = ExcepcionHorario(
                            fecha = fecha,
                            cerrado = excCerrado,
                            apertura = if (excCerrado) "" else excApertura,
                            cierre = if (excCerrado) "" else excCierre
                        )
                        val indice = indiceExcepcionEditando
                        val lista = if (indice == null) {
                            excepciones + nueva
                        } else {
                            excepciones.toMutableList().also { it[indice] = nueva }
                        }
                        excepciones = lista.sortedBy { it.fecha }
                        dialogoExcepcionAbierto = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { dialogoExcepcionAbierto = false }) {
                    Text("Cancelar")
                }
            },
            title = {
                Text(if (indiceExcepcionEditando == null) "Nueva excepción" else "Editar excepción")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = excFecha?.let { formatearFecha(it) } ?: "",
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text("Fecha") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mostrarDatePickerExcepcion = true }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cerrado",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = excCerrado,
                            onCheckedChange = { excCerrado = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AzulTrazys
                            )
                        )
                    }
                    if (!excCerrado) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CampoHora(
                                etiqueta = "Apertura",
                                valor = excApertura,
                                onClick = { timePickerExcepcion = true },
                                modifier = Modifier.weight(1f)
                            )
                            CampoHora(
                                etiqueta = "Cierre",
                                valor = excCierre,
                                onClick = { timePickerExcepcion = false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        )
    }

    // --- Hora de una excepción ---
    val excTime = timePickerExcepcion
    if (excTime != null) {
        val valor = if (excTime) excApertura else excCierre
        val partes = valor.split(":")
        val timePickerState = rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: if (excTime) 9 else 21,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerExcepcion = null },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    val hora = "$h:$m"
                    if (excTime) excApertura = hora else excCierre = hora
                    timePickerExcepcion = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerExcepcion = null }) { Text("Cancelar") }
            },
            title = { Text(if (excTime) "Apertura" else "Cierre") },
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

/** Saver del horario semanal (mapa por día) para cambios de configuración. */
private val SaverCentro = listSaver<Map<DayOfWeek, TramoHorario>, String>(
    save = { mapa -> mapa.map { (dia, t) -> "${dia.name}|${t.cerrado}|${t.apertura}|${t.cierre}" } },
    restore = { lista ->
        lista.mapNotNull { fila ->
            val partes = fila.split("|")
            if (partes.size == 4) {
                runCatching { DayOfWeek.valueOf(partes[0]) }.getOrNull()?.let { dia ->
                    dia to TramoHorario(
                        cerrado = partes[1].toBoolean(),
                        apertura = partes[2],
                        cierre = partes[3]
                    )
                }
            } else {
                null
            }
        }.toMap()
    }
)

/** Saver de las excepciones para cambios de configuración. */
private val SaverExcepciones = listSaver<List<ExcepcionHorario>, String>(
    save = { lista -> lista.map { "${it.fecha}|${it.cerrado}|${it.apertura}|${it.cierre}" } },
    restore = { lista ->
        lista.mapNotNull { fila ->
            val partes = fila.split("|")
            if (partes.size == 4) {
                partes[0].toLongOrNull()?.let { fecha ->
                    ExcepcionHorario(
                        fecha = fecha,
                        cerrado = partes[1].toBoolean(),
                        apertura = partes[2],
                        cierre = partes[3]
                    )
                }
            } else {
                null
            }
        }
    }
)

/** Orden de visualización de los días (lunes primero). */
internal val diasSemana = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

/** Nombre visible en español de un día de la semana. */
internal fun nombreDia(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miércoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

/** Normaliza un instante UTC del DatePicker a la medianoche local del día. */
private fun normalizarFechaLocal(utcMillis: Long): Long {
    val fecha = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatearFecha(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@Composable
private fun CampoHora(
    etiqueta: String,
    valor: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valor.ifBlank { "--:--" },
        onValueChange = { },
        readOnly = true,
        enabled = false,
        label = { Text(etiqueta) },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.clickable { onClick() }
    )
}
