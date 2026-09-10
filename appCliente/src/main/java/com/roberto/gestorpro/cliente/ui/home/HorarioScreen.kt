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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.roberto.gestorpro.cliente.model.HorarioNegocio
import com.roberto.gestorpro.cliente.model.TramoHorario
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppSecondaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.HorarioClienteViewModel
import java.time.DayOfWeek

/**
 * HorarioScreen
 * -------------
 * Pantalla de horario del CLIENTE (solo vinculados y ACTIVOS):
 *  - HORARIO DEL CENTRO: horario semanal general.
 *  - HORARIO DE ACTIVIDADES: horario semanal independiente de las sesiones, con
 *    selector de día y tarjetas por actividad (nombre + hora habitual).
 */
@Composable
fun HorarioScreen(
    navController: NavHostController,
    viewModel: HorarioClienteViewModel = hiltViewModel()
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
                    text = stringResource(R.string.horario_titulo),
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

                is HorarioClienteViewModel.Estado.Datos -> ContenidoHorario(
                    horario = actual.horario,
                    nombresActividad = actual.nombresActividad
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

@Composable
private fun ContenidoHorario(
    horario: HorarioNegocio,
    nombresActividad: Map<Int, String>
) {
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
            text = stringResource(R.string.horario_centro_titulo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AzulTrazys
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (horario.centro.isEmpty()) {
            Text(
                text = stringResource(R.string.horario_sin_configurar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    diasSemanaCliente.forEachIndexed { indice, dia ->
                        val tramo = horario.centro[dia] ?: TramoHorario()
                        FilaHorarioCentro(dia = dia, tramo = tramo)
                        if (indice < diasSemanaCliente.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.horario_actividades_titulo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AzulTrazys
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            diasSemanaCliente.forEach { dia ->
                FilterChip(
                    selected = diaSeleccionado == dia,
                    onClick = { diaSeleccionado = dia },
                    label = { Text(stringResource(recursoDiaCorto(dia))) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AzulTrazys,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val lista = horario.actividades[diaSeleccionado] ?: emptyList()
        if (lista.isEmpty()) {
            Text(
                text = stringResource(R.string.horario_sin_actividades),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lista.forEach { entrada ->
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

@Composable
private fun FilaHorarioCentro(dia: DayOfWeek, tramo: TramoHorario) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(recursoDia(dia)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (tramo.cerrado) {
                stringResource(R.string.horario_cerrado)
            } else {
                "${tramo.apertura} - ${tramo.cierre}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (tramo.cerrado) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun TarjetaActividad(entrada: ActividadHorario, nombre: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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

/** Azul corporativo de Trazys. */
private val AzulTrazys = Color(0xFF1E88E5)

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

private fun recursoDia(dia: DayOfWeek): Int = when (dia) {
    DayOfWeek.MONDAY -> R.string.horario_dia_lunes
    DayOfWeek.TUESDAY -> R.string.horario_dia_martes
    DayOfWeek.WEDNESDAY -> R.string.horario_dia_miercoles
    DayOfWeek.THURSDAY -> R.string.horario_dia_jueves
    DayOfWeek.FRIDAY -> R.string.horario_dia_viernes
    DayOfWeek.SATURDAY -> R.string.horario_dia_sabado
    DayOfWeek.SUNDAY -> R.string.horario_dia_domingo
}

private fun recursoDiaCorto(dia: DayOfWeek): Int = when (dia) {
    DayOfWeek.MONDAY -> R.string.horario_dia_corto_lun
    DayOfWeek.TUESDAY -> R.string.horario_dia_corto_mar
    DayOfWeek.WEDNESDAY -> R.string.horario_dia_corto_mie
    DayOfWeek.THURSDAY -> R.string.horario_dia_corto_jue
    DayOfWeek.FRIDAY -> R.string.horario_dia_corto_vie
    DayOfWeek.SATURDAY -> R.string.horario_dia_corto_sab
    DayOfWeek.SUNDAY -> R.string.horario_dia_corto_dom
}
