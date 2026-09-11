package com.roberto.gestorpro.ui.gestioncentro

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.model.ActividadHorario
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AyudaContextual
import com.roberto.gestorpro.ui.viewmodel.HorarioViewModel
import java.time.DayOfWeek

/** Azul corporativo de Trazys. */
private val AzulTrazys = Color(0xFF1E88E5)

/**
 * HorarioActividadesScreen
 * ------------------------
 * Horario semanal de ACTIVIDADES (independiente de las sesiones): se elige un
 * día, se selecciona una actividad existente y una hora, y se añade. Las
 * entradas pueden editarse y eliminarse. Se guarda `idServicio`. Todo el estado
 * del formulario se conserva ante rotación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorarioActividadesScreen(
    navController: NavHostController,
    viewModel: HorarioViewModel = hiltViewModel()
) {
    val horario by viewModel.horario.collectAsStateWithLifecycle()
    val cargado by viewModel.cargado.collectAsStateWithLifecycle()
    val servicios by viewModel.servicios.collectAsStateWithLifecycle()
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

    var precargado by rememberSaveable { mutableStateOf(false) }
    var actividades by rememberSaveable(stateSaver = SaverActividades) {
        mutableStateOf(mapOf<DayOfWeek, List<ActividadHorario>>())
    }
    var diaSeleccionado by rememberSaveable(stateSaver = SaverDia) {
        mutableStateOf(DayOfWeek.MONDAY)
    }
    var servicioSeleccionado by rememberSaveable { mutableStateOf<Int?>(null) }
    var dialogoAbierto by remember { mutableStateOf(false) }
    var indiceEditando by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(cargado) {
        if (cargado && !precargado) {
            actividades = horario.actividades
            precargado = true
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.horario_actividades_titulo),
                            style = MaterialTheme.typography.titleLarge
                        )
                        AyudaContextual(
                            titulo = stringResource(R.string.horario_actividades_titulo),
                            texto = stringResource(R.string.horario_act_ayuda)
                        )
                    }
                    Text(
                        text = stringResource(R.string.horario_act_subtitulo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // ===================== CONFIGURAR HORARIO =====================
            EncabezadoSeccion(
                titulo = stringResource(R.string.horario_seccion_configurar),
                ayuda = stringResource(R.string.horario_act_configurar_ayuda)
            )
            ContenedorSeccion {
                EtiquetaBloque(titulo = stringResource(R.string.horario_dia))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    diasSemana.forEach { dia ->
                        FilterChip(
                            selected = diaSeleccionado == dia,
                            onClick = { diaSeleccionado = dia },
                            label = { Text(nombreDiaCorto(dia)) },
                            colors = chipAzul()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                EtiquetaBloque(titulo = stringResource(R.string.horario_act_centro))
                Spacer(modifier = Modifier.height(8.dp))
                if (servicios.isEmpty()) {
                    Text(
                        text = stringResource(R.string.horario_act_sin_actividades),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        servicios.forEach { servicio ->
                            FilterChip(
                                selected = servicioSeleccionado == servicio.idServicio,
                                onClick = {
                                    servicioSeleccionado = if (
                                        servicioSeleccionado == servicio.idServicio
                                    ) {
                                        null
                                    } else {
                                        servicio.idServicio
                                    }
                                },
                                label = { Text(servicio.nombre) },
                                colors = chipAzul()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        indiceEditando = null
                        dialogoAbierto = true
                    },
                    enabled = servicios.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AzulTrazys
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.horario_act_anadir, nombreDia(diaSeleccionado)),
                        color = AzulTrazys
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===================== HORARIO DEL DÍA =====================
            EncabezadoSeccion(
                titulo = stringResource(R.string.horario_seccion_dia),
                ayuda = stringResource(R.string.horario_dia_ayuda)
            )
            ContenedorSeccion {
                EtiquetaBloque(
                    titulo = stringResource(
                        R.string.horario_act_dia_titulo,
                        nombreDia(diaSeleccionado)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                val lista = actividades[diaSeleccionado] ?: emptyList()
                if (lista.isEmpty()) {
                    Text(
                        text = stringResource(R.string.horario_act_sin_dia),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    lista.forEachIndexed { indice, entrada ->
                        val nombreActividad = servicios
                            .firstOrNull { it.idServicio == entrada.idServicio }
                            ?.nombre
                            ?: stringResource(R.string.horario_act_fallback, entrada.idServicio)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nombreActividad,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.horario_act_hora, entrada.hora),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                indiceEditando = indice
                                servicioSeleccionado = entrada.idServicio
                                dialogoAbierto = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.horario_act_editar),
                                    tint = AzulTrazys
                                )
                            }
                            IconButton(onClick = {
                                val nuevaLista = lista.toMutableList().also {
                                    it.removeAt(indice)
                                }
                                actividades = if (nuevaLista.isEmpty()) {
                                    actividades - diaSeleccionado
                                } else {
                                    actividades + (diaSeleccionado to nuevaLista)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.horario_act_eliminar),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (indice != lista.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
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
                text = if (guardando) {
                    stringResource(R.string.horario_guardando)
                } else {
                    stringResource(R.string.horario_guardar)
                },
                onClick = { viewModel.guardarActividades(actividades) },
                enabled = !guardando,
                fullWidth = false,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (dialogoAbierto) {
        val indice = indiceEditando
        val entradaActual = indice?.let { actividades[diaSeleccionado]?.getOrNull(it) }
        DialogoActividad(
            titulo = if (indice == null) {
                stringResource(R.string.horario_act_anadir_dialogo, nombreDia(diaSeleccionado))
            } else {
                stringResource(R.string.horario_act_editar_dialogo, nombreDia(diaSeleccionado))
            },
            servicios = servicios,
            servicioInicial = entradaActual?.idServicio ?: servicioSeleccionado,
            horaInicial = entradaActual?.hora ?: "18:00",
            onDismiss = { dialogoAbierto = false },
            onConfirm = { idServicio, hora ->
                val actual = actividades[diaSeleccionado] ?: emptyList()
                val nuevaLista = if (indice == null) {
                    actual + ActividadHorario(idServicio, hora)
                } else {
                    actual.toMutableList().also {
                        it[indice] = ActividadHorario(idServicio, hora)
                    }
                }
                actividades = actividades + (diaSeleccionado to nuevaLista)
                dialogoAbierto = false
            }
        )
    }
}

/** Colores de FilterChip con azul corporativo en el estado seleccionado. */
@Composable
private fun chipAzul() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AzulTrazys,
    selectedLabelColor = Color.White
)

/**
 * DialogoActividad
 * ----------------
 * Diálogo para añadir o editar una entrada del horario: selección de la
 * actividad (chips) + hora. Se guarda `idServicio`, no el nombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoActividad(
    titulo: String,
    servicios: List<ServicioEntity>,
    servicioInicial: Int?,
    horaInicial: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var seleccion by remember { mutableStateOf(servicioInicial) }
    val partes = horaInicial.split(":")
    val timePickerState = rememberTimePickerState(
        initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 18,
        initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = seleccion != null,
                onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    seleccion?.let { onConfirm(it, "$h:$m") }
                }
            ) { Text(stringResource(R.string.accion_guardar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accion_cancelar)) }
        },
        title = { Text(titulo) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.horario_act_actividad),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    servicios.forEach { servicio ->
                        FilterChip(
                            selected = seleccion == servicio.idServicio,
                            onClick = { seleccion = servicio.idServicio },
                            label = { Text(servicio.nombre) },
                            colors = chipAzul()
                        )
                    }
                }
                TimePicker(state = timePickerState)
            }
        }
    )
}

/** Saver del día seleccionado (enum) para cambios de configuración. */
private val SaverDia: Saver<DayOfWeek, String> = Saver(
    save = { it.name },
    restore = { runCatching { DayOfWeek.valueOf(it) }.getOrDefault(DayOfWeek.MONDAY) }
)

/** Saver del horario de actividades para cambios de configuración. */
private val SaverActividades =
    listSaver<Map<DayOfWeek, List<ActividadHorario>>, String>(
        save = { mapa ->
            mapa.flatMap { (dia, lista) ->
                lista.map { "${dia.name}|${it.idServicio}|${it.hora}" }
            }
        },
        restore = { filas ->
            filas.mapNotNull { fila ->
                val partes = fila.split("|")
                if (partes.size == 3) {
                    val dia = runCatching { DayOfWeek.valueOf(partes[0]) }.getOrNull()
                    val id = partes[1].toIntOrNull()
                    if (dia != null && id != null) dia to ActividadHorario(id, partes[2]) else null
                } else {
                    null
                }
            }.groupBy({ it.first }, { it.second })
        }
    )

/** Nombre corto del día para el selector de chips. */
@Composable
private fun nombreDiaCorto(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> stringResource(R.string.dia_corto_lun)
    DayOfWeek.TUESDAY -> stringResource(R.string.dia_corto_mar)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.dia_corto_mie)
    DayOfWeek.THURSDAY -> stringResource(R.string.dia_corto_jue)
    DayOfWeek.FRIDAY -> stringResource(R.string.dia_corto_vie)
    DayOfWeek.SATURDAY -> stringResource(R.string.dia_corto_sab)
    DayOfWeek.SUNDAY -> stringResource(R.string.dia_corto_dom)
}
