package com.roberto.gestorpro.cliente.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.model.ActividadHorario
import com.roberto.gestorpro.cliente.model.ExcepcionHorario
import com.roberto.gestorpro.cliente.model.HorarioNegocio
import com.roberto.gestorpro.cliente.model.TramoHorario
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppSecondaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.HorarioClienteViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Azul corporativo de Trazys. */
private val AzulTrazys = Color(0xFF1E88E5)

/**
 * HorarioCentroClienteScreen
 * --------------------------
 * HORARIO DEL CENTRO para el CLIENTE: horario semanal de apertura/cierre con
 * selector horizontal de días, varios tramos por día en tarjetas visuales y
 * excepciones de fechas concretas con PRIORIDAD sobre el horario semanal.
 */
@Composable
fun HorarioCentroClienteScreen(
    navController: NavHostController,
    viewModel: HorarioClienteViewModel = hiltViewModel()
) {
    PantallaHorario(
        navController = navController,
        tituloRecurso = R.string.horario_titulo_centro,
        viewModel = viewModel
    ) { horario, _ ->
        ContenidoCentro(horario)
    }
}

/**
 * ActividadesClienteScreen
 * ------------------------
 * HORARIO DE ACTIVIDADES para el CLIENTE: horario semanal independiente de las
 * sesiones, con selector horizontal de días y tarjetas por actividad
 * (nombre + hora), sin imágenes.
 */
@Composable
fun ActividadesClienteScreen(
    navController: NavHostController,
    viewModel: HorarioClienteViewModel = hiltViewModel()
) {
    PantallaHorario(
        navController = navController,
        tituloRecurso = R.string.horario_titulo_actividades,
        viewModel = viewModel
    ) { horario, nombres ->
        ContenidoActividades(horario, nombres)
    }
}

/**
 * PantallaHorario
 * ---------------
 * Estructura común (cabecera + estados + carga) de las dos pantallas de horario
 * del CLIENTE. Recibe el contenido concreto a pintar cuando hay datos.
 */
@Composable
private fun PantallaHorario(
    navController: NavHostController,
    tituloRecurso: Int,
    viewModel: HorarioClienteViewModel,
    contenido: @Composable (HorarioNegocio, Map<Int, String>) -> Unit
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(tituloRecurso),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            when (val actual = estado) {
                is HorarioClienteViewModel.Estado.Cargando -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is HorarioClienteViewModel.Estado.NoVinculado -> MensajeHorario(
                    texto = stringResource(R.string.horario_no_vinculado)
                )

                is HorarioClienteViewModel.Estado.NoActivo -> MensajeHorario(
                    texto = stringResource(R.string.horario_no_activo)
                )

                is HorarioClienteViewModel.Estado.Error -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = actual.mensaje,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppSecondaryButton(
                        text = stringResource(R.string.notif_reintentar),
                        onClick = { viewModel.reintentar() },
                        fullWidth = false
                    )
                }

                is HorarioClienteViewModel.Estado.Datos -> contenido(
                    actual.horario,
                    actual.nombresActividad
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MensajeHorario(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/** Contenido del horario del centro: selector de día, tramos y excepciones. */
@Composable
private fun ContenidoCentro(horario: HorarioNegocio) {
    var diaSeleccionado by rememberSaveable(stateSaver = SaverDiaCliente) {
        mutableStateOf(DayOfWeek.MONDAY)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SelectorDias(
            diaSeleccionado = diaSeleccionado,
            onSeleccionar = { diaSeleccionado = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (horario.centro.isEmpty()) {
            Text(
                text = stringResource(R.string.horario_sin_configurar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Prioridad de la fecha especial: si el próximo día seleccionado
            // tiene configuración propia, se muestra directamente su resultado
            // (Cerrado o sus tramos), sin explicar que es una excepción.
            val proximaFecha = proximaFechaDeDia(diaSeleccionado)
            val excepcion = horario.excepciones.firstOrNull { it.fecha == proximaFecha }

            if (excepcion != null) {
                TarjetaTramos(excepcion.tramos)
            } else {
                val tramos = horario.centro[diaSeleccionado] ?: emptyList()
                TarjetaTramos(tramos)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.horario_proximas_excepciones),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AzulTrazys
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (horario.excepciones.isEmpty()) {
            Text(
                text = stringResource(R.string.horario_sin_excepciones),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            horario.excepciones.sortedBy { it.fecha }.forEach { excepcion ->
                TarjetaExcepcion(excepcion)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Contenido del horario de actividades: selector de día y tarjetas por actividad. */
@Composable
private fun ContenidoActividades(horario: HorarioNegocio, nombresActividad: Map<Int, String>) {
    var diaSeleccionado by rememberSaveable(stateSaver = SaverDiaCliente) {
        mutableStateOf(DayOfWeek.MONDAY)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.horario_subtitulo_actividades),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        SelectorDias(
            diaSeleccionado = diaSeleccionado,
            onSeleccionar = { diaSeleccionado = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val lista = horario.actividades[diaSeleccionado] ?: emptyList()
        if (lista.isEmpty()) {
            Text(
                text = stringResource(R.string.horario_sin_actividades),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lista.sortedBy { it.hora }.forEach { entrada ->
                TarjetaActividad(
                    entrada = entrada,
                    nombre = nombresActividad[entrada.idServicio]
                        ?: stringResource(R.string.horario_actividad_generica)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Selector horizontal de días con el seleccionado resaltado en azul corporativo. */
@Composable
private fun SelectorDias(
    diaSeleccionado: DayOfWeek,
    onSeleccionar: (DayOfWeek) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        diasSemanaCliente.forEach { dia ->
            FilterChip(
                selected = diaSeleccionado == dia,
                onClick = { onSeleccionar(dia) },
                label = { Text(stringResource(recursoDiaCorto(dia))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AzulTrazys,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

/** Tarjeta con los tramos del día (o "Cerrado" si no hay ninguno). */
@Composable
private fun TarjetaTramos(tramos: List<TramoHorario>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (tramos.isEmpty()) {
                Text(
                    text = stringResource(R.string.horario_cerrado),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tramos.sortedBy { it.apertura }.forEach { tramo ->
                    Text(
                        text = "${tramo.apertura} - ${tramo.cierre}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/** Tarjeta resumen de una excepción (fecha + tramos o "Cerrado"). */
@Composable
private fun TarjetaExcepcion(excepcion: ExcepcionHorario) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = formatearFechaCorta(excepcion.fecha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (excepcion.cerrado) {
                    stringResource(R.string.horario_cerrado)
                } else {
                    excepcion.tramos.sortedBy { it.apertura }
                        .joinToString(" · ") { "${it.apertura} - ${it.cierre}" }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tarjeta individual de una actividad: nombre + hora (sin imágenes). */
@Composable
private fun TarjetaActividad(entrada: ActividadHorario, nombre: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = entrada.hora,
                style = MaterialTheme.typography.bodyLarge,
                color = AzulTrazys,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Próxima fecha (hoy o siguiente) que cae en el día de la semana indicado. */
private fun proximaFechaDeDia(dia: DayOfWeek): Long {
    var fecha = LocalDate.now()
    while (fecha.dayOfWeek != dia) {
        fecha = fecha.plusDays(1)
    }
    return fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatearFechaCorta(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

/** Saver del día seleccionado (enum) para cambios de configuración. */
private val SaverDiaCliente: Saver<DayOfWeek, String> = Saver(
    save = { it.name },
    restore = { runCatching { DayOfWeek.valueOf(it) }.getOrDefault(DayOfWeek.MONDAY) }
)

private val diasSemanaCliente = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

private fun recursoDiaCorto(dia: DayOfWeek): Int = when (dia) {
    DayOfWeek.MONDAY -> R.string.horario_dia_corto_lun
    DayOfWeek.TUESDAY -> R.string.horario_dia_corto_mar
    DayOfWeek.WEDNESDAY -> R.string.horario_dia_corto_mie
    DayOfWeek.THURSDAY -> R.string.horario_dia_corto_jue
    DayOfWeek.FRIDAY -> R.string.horario_dia_corto_vie
    DayOfWeek.SATURDAY -> R.string.horario_dia_corto_sab
    DayOfWeek.SUNDAY -> R.string.horario_dia_corto_dom
}
