package com.roberto.gestorpro.ui.servicios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.entity.SesionEntity
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.components.AppSemanticButton
import com.roberto.gestorpro.ui.viewmodel.ServicioViewModel
import com.roberto.gestorpro.ui.viewmodel.SesionViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ProgramarSesionesScreen
 * -----------------------
 * Pantalla para programar las sesiones de un servicio.
 * Cada día seleccionado tiene SU PROPIA hora. Al generar se eliminan las
 * sesiones futuras (y sus reservas) y se crean las nuevas dentro del
 * intervalo [desde, hasta]. Solo accesible con el servicio activo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramarSesionesScreen(
    navController: NavHostController,
    idServicio: Int,
    servicioViewModel: ServicioViewModel = hiltViewModel(),
    sesionViewModel: SesionViewModel = hiltViewModel()
) {
    val servicio by servicioViewModel.servicioSeleccionado.collectAsStateWithLifecycle()
    val sesiones by sesionViewModel.sesiones.collectAsStateWithLifecycle()
    val operando by sesionViewModel.operando.collectAsStateWithLifecycle()
    val errorSincronizacion by sesionViewModel.errorSincronizacion.collectAsStateWithLifecycle()
    val sesionSinSincronizar by sesionViewModel.sesionSinSincronizar.collectAsStateWithLifecycle()
    val resultadoGeneracion by sesionViewModel.resultadoGeneracion.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * Muestra en una snackbar el resultado real de la última generación
     * (éxito o error). Si terminó bien, vuelve a la pantalla anterior;
     * si falló, la pantalla permanece para que el usuario vea el banner
     * de sincronización y pueda reintentar.
     */
    LaunchedEffect(resultadoGeneracion) {
        resultadoGeneracion?.let { resultado ->
            snackbarHostState.showSnackbar(resultado.mensaje)
            if (resultado.exito) {
                navController.popBackStack()
            }
        }
    }

    /**
     * Al abandonar la pantalla se limpia el resultado de generación para
     * que una reentrada posterior no reaccione a un resultado obsoleto.
     */
    DisposableEffect(Unit) {
        onDispose {
            sesionViewModel.limpiarResultadoGeneracion()
        }
    }

    var desde by rememberSaveable { mutableStateOf<Long?>(null) }
    var hasta by rememberSaveable { mutableStateOf<Long?>(null) }
    var diasSeleccionados by rememberSaveable(stateSaver = SaverDiasSeleccionados) {
        mutableStateOf(setOf<DayOfWeek>())
    }
    var horasPorDia by rememberSaveable(stateSaver = SaverHorasPorDia) {
        mutableStateOf(mapOf<DayOfWeek, String>())
    }
    var aperturaReservas by rememberSaveable { mutableStateOf<String?>(null) }
    var duracion by rememberSaveable { mutableStateOf("60") }
    var capacidad by rememberSaveable { mutableStateOf("20") }
    var horaGlobal by rememberSaveable { mutableStateOf<String?>(null) }

    var mostrarDatePickerInicio by remember { mutableStateOf(false) }
    var mostrarDatePickerFin by remember { mutableStateOf(false) }
    var diaConTimePicker by remember { mutableStateOf<DayOfWeek?>(null) }
    var mostrarSelectorApertura by remember { mutableStateOf(false) }
    var mostrarHoraGlobal by remember { mutableStateOf(false) }

    var errorDesde by remember { mutableStateOf(false) }
    var errorHasta by remember { mutableStateOf(false) }
    var errorDias by remember { mutableStateOf(false) }
    var errorDuracion by remember { mutableStateOf(false) }
    var errorCapacidad by remember { mutableStateOf(false) }

    LaunchedEffect(idServicio) {
        servicioViewModel.cargarServicio(idServicio)
        sesionViewModel.cargarSesionesPorServicio(idServicio)
    }

    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val desdeFormateada = desde?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
    } ?: ""
    val hastaFormateada = hasta?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
    } ?: ""

    val diasSemana = listOf(
        DayOfWeek.MONDAY to "L",
        DayOfWeek.TUESDAY to "M",
        DayOfWeek.WEDNESDAY to "X",
        DayOfWeek.THURSDAY to "J",
        DayOfWeek.FRIDAY to "V",
        DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "D"
    )

    val diasNombres = listOf(
        DayOfWeek.MONDAY to "Lun",
        DayOfWeek.TUESDAY to "Mar",
        DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue",
        DayOfWeek.FRIDAY to "Vie",
        DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
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
                        text = "Gestionar sesiones",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = servicio?.nombre ?: "Cargando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Text(
                text = "Fechas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            OutlinedTextField(
                value = desdeFormateada,
                onValueChange = { },
                readOnly = true,
                enabled = false,
                label = { Text("Fecha desde") },
                placeholder = { Text("dd/MM/aaaa") },
                isError = errorDesde,
                supportingText = {
                    if (errorDesde) Text("La fecha desde es obligatoria")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        mostrarDatePickerInicio = true
                        errorDesde = false
                    }
            )

            OutlinedTextField(
                value = hastaFormateada,
                onValueChange = { },
                readOnly = true,
                enabled = false,
                label = { Text("Fecha hasta") },
                placeholder = { Text("dd/MM/aaaa") },
                isError = errorHasta,
                supportingText = {
                    if (errorHasta) Text("La fecha hasta es obligatoria y debe ser >= desde")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        mostrarDatePickerFin = true
                        errorHasta = false
                    }
            )

            Text(
                text = "Días de la semana y hora",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Selecciona los días y asigna una hora a cada uno",
                style = MaterialTheme.typography.bodyMedium,
                color = if (errorDias) MaterialTheme.colorScheme.error else Color.Gray
            )

            OutlinedTextField(
                value = horaGlobal?.let { "Todos los días a las $it" } ?: "Sin hora común",
                onValueChange = { },
                readOnly = true,
                enabled = false,
                label = { Text("Hora para todos los días") },
                supportingText = {
                    Text("Aplica la misma hora a todos los días seleccionados")
                },
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
                    .clickable { mostrarHoraGlobal = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            diasSemana.forEachIndexed { index, (dia, letra) ->
                val seleccionado = diasSeleccionados.contains(dia)
                val hora = horasPorDia[dia]

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                diasSeleccionados = if (seleccionado) {
                                    diasSeleccionados - dia
                                } else {
                                    diasSeleccionados + dia
                                }
                                if (!seleccionado && hora == null) {
                                    // Si ya se definió una hora común, el día nuevo
                                    // nace con esa hora; si no, se mantiene el 18:00.
                                    horasPorDia = horasPorDia + (dia to (horaGlobal ?: "18:00"))
                                }
                                errorDias = false
                            },
                        shape = CircleShape,
                        color = if (seleccionado) Color(0xFF1E88E5) else Color.Transparent,
                        border = if (seleccionado) null else androidx.compose.foundation.BorderStroke(1.5.dp, Color.LightGray)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = letra,
                                color = if (seleccionado) Color.White else Color.DarkGray,
                                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Text(
                        text = diasNombres[index].second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (seleccionado) Color(0xFF1E88E5) else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )

                    if (seleccionado) {
                        AppSemanticButton(
                            text = hora ?: "18:00",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { diaConTimePicker = dia },
                            icon = Icons.Default.DateRange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Apertura de reservas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = aperturaReservas?.let { "Apertura: $it" } ?: "Desde el inicio",
                onValueChange = { },
                readOnly = true,
                enabled = false,
                label = { Text("Apertura de reservas") },
                supportingText = {
                    Text(
                        text = if (aperturaReservas == null) {
                            "Los clientes podrán reservar desde el inicio del día"
                        } else {
                            "Los clientes podrán reservar a partir de las $aperturaReservas"
                        }
                    )
                },
                trailingIcon = {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        mostrarSelectorApertura = true
                    }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = duracion,
                    onValueChange = {
                        duracion = it
                        errorDuracion = false
                    },
                    label = { Text("Duración (min)") },
                    isError = errorDuracion,
                    supportingText = {
                        if (errorDuracion) Text("Inválido")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = capacidad,
                    onValueChange = {
                        capacidad = it
                        errorCapacidad = false
                    },
                    label = { Text("Capacidad") },
                    isError = errorCapacidad,
                    supportingText = {
                        if (errorCapacidad) Text("Inválido")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPrimaryButton(
                text = if (operando) "Generando sesiones..." else "Generar sesiones",
                onClick = {
                    errorDesde = desde == null
                    errorHasta = hasta == null || (desde != null && hasta != null && hasta!! < desde!!)
                    errorDias = diasSeleccionados.isEmpty()
                    errorDuracion = duracion.toIntOrNull() == null || duracion.toInt() <= 0
                    errorCapacidad = capacidad.toIntOrNull() == null || capacidad.toInt() <= 0

                    if (!errorDesde && !errorHasta && !errorDias && !errorDuracion && !errorCapacidad) {
                        val servicioActual = servicio ?: return@AppPrimaryButton
                        sesionViewModel.generarSesiones(
                            servicio = servicioActual,
                            desde = desde!!,
                            hasta = hasta!!,
                            horariosPorDia = horasPorDia.filterKeys { it in diasSeleccionados },
                            aperturaReservas = aperturaReservas,
                            duracionMinutos = duracion.toInt(),
                            capacidad = capacidad.toInt()
                        )
                    }
                },
                enabled = servicio?.activo == true && !operando
            )

            if (errorSincronizacion != null || sesionSinSincronizar != null) {
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
                            text = "Reintentar sincronización",
                            onClick = { sesionViewModel.reintentarSincronizacion() },
                            enabled = sesionSinSincronizar != null,
                            fullWidth = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Sesiones del servicio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (sesiones.isEmpty()) {
                Text(
                    text = "No hay sesiones programadas para este servicio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                sesiones.sortedWith(
                    compareBy<SesionEntity> { it.fecha }.thenBy { it.hora }
                ).forEach { sesion ->
                    val fechaTexto = Instant.ofEpochMilli(sesion.fecha)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(formatter)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E88E5).copy(alpha = 0.06f)
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
                                    text = "$fechaTexto · ${sesion.hora}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Plazas: ${sesion.plazasDisponibles}/${sesion.capacidad}" +
                                        (sesion.horaDesdeReserva?.let { " · Apertura $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            AppSemanticButton(
                                text = "Ver / editar",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    navController.navigate(Routes.editarSesion(sesion.idSesion))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (mostrarDatePickerInicio) {
        val hoy = LocalDate.now()
        val min = hoy.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val selectable = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= min
                override fun isSelectableYear(year: Int): Boolean = year >= hoy.year
            }
        }
        val state = rememberDatePickerState(selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerInicio = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        desde = state.selectedDateMillis
                        mostrarDatePickerInicio = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerInicio = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (mostrarDatePickerFin) {
        val hoy = LocalDate.now()
        val desdeUtc = desde ?: hoy.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val selectable = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= desdeUtc
                override fun isSelectableYear(year: Int): Boolean = year >= hoy.year
            }
        }
        val state = rememberDatePickerState(selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerFin = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        hasta = state.selectedDateMillis
                        mostrarDatePickerFin = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerFin = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    val diaTime = diaConTimePicker
    if (diaTime != null) {
        val horaActual = horasPorDia[diaTime] ?: "18:00"
        val partes = horaActual.split(":")
        val initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 18
        val initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = TimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { diaConTimePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    horasPorDia = horasPorDia + (diaTime to "$h:$m")
                    diaConTimePicker = null
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { diaConTimePicker = null }) { Text("Cancelar") }
            },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hora para ${diasNombres.first { it.first == diaTime }.second}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (mostrarHoraGlobal) {
        val horaActual = horaGlobal ?: "18:00"
        val partes = horaActual.split(":")
        val initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 18
        val initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0
        // rememberTimePickerState conserva la hora elegida en el diálogo
        // (construir TimePickerState directamente la reinicia al recomponer).
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { mostrarHoraGlobal = false },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    val hora = "$h:$m"
                    horaGlobal = hora
                    if (diasSeleccionados.isNotEmpty()) {
                        horasPorDia = horasPorDia + diasSeleccionados.associateWith { hora }
                    }
                    mostrarHoraGlobal = false
                }) {
                    Text("Aplicar a todos")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarHoraGlobal = false }) { Text("Cancelar") }
            },
            title = {
                Text(
                    text = "Hora para todos los días",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Se aplicará a todos los días seleccionados. " +
                            "Después puedes ajustar cada día de forma individual.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (mostrarSelectorApertura) {
        val aperturaActual = aperturaReservas ?: "00:00"
        val partes = aperturaActual.split(":")
        val initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 0
        val initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = TimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { mostrarSelectorApertura = false },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    aperturaReservas = "$h:$m"
                    mostrarSelectorApertura = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSelectorApertura = false }) { Text("Cancelar") }
            },
            title = {
                Text(
                    text = "Apertura de reservas",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hora desde la que los clientes pueden reservar.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        aperturaReservas = null
                        mostrarSelectorApertura = false
                    }) {
                        Text("Abrir desde el inicio")
                    }
                }
            }
        )
    }
}

/**
 * Saver para conservar los días seleccionados tras un cambio de configuración
 * (por ejemplo, una rotación). Se guardan por nombre del enum, que es estable
 * y almacenable en el estado guardado.
 */
private val SaverDiasSeleccionados = listSaver<Set<DayOfWeek>, String>(
    save = { dias -> dias.map { it.name } },
    restore = { nombres ->
        nombres.mapNotNull { nombre ->
            runCatching { DayOfWeek.valueOf(nombre) }.getOrNull()
        }.toSet()
    }
)

/**
 * Saver para conservar la hora asignada a cada día tras un cambio de
 * configuración (por ejemplo, una rotación). Cada entrada se guarda como
 * "DIA=HH:MM".
 */
private val SaverHorasPorDia = listSaver<Map<DayOfWeek, String>, String>(
    save = { mapa -> mapa.map { (dia, hora) -> "${dia.name}=$hora" } },
    restore = { entradas ->
        entradas.mapNotNull { entrada ->
            val partes = entrada.split("=", limit = 2)
            if (partes.size == 2) {
                runCatching { DayOfWeek.valueOf(partes[0]) }.getOrNull()
                    ?.let { dia -> dia to partes[1] }
            } else {
                null
            }
        }.toMap()
    }
)
