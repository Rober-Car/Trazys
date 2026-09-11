package com.roberto.gestorpro.ui.gestioncentro

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.model.ExcepcionHorario
import com.roberto.gestorpro.model.TramoHorario
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AyudaContextual
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
 * Configuración del horario del centro con VARIOS tramos por día:
 *  - aplicación global de tramos a toda la semana (días abiertos);
 *  - horario semanal habitual (0, 1 o varios tramos por día; sin tramos = cerrado);
 *  - excepciones para FECHAS concretas con prioridad sobre el semanal.
 * Valida que apertura < cierre y que no haya tramos solapados. Todo el estado
 * del formulario se conserva ante rotación.
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
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.cargar() }
    LaunchedEffect(mensajeExito) {
        mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajeExito()
        }
    }

    var precargado by rememberSaveable { mutableStateOf(false) }
    var centro by rememberSaveable(stateSaver = SaverCentro) {
        mutableStateOf(mapOf<DayOfWeek, List<TramoHorario>>())
    }
    var excepciones by rememberSaveable(stateSaver = SaverExcepciones) {
        mutableStateOf(listOf<ExcepcionHorario>())
    }
    var globalTramos by rememberSaveable(stateSaver = SaverTramos) {
        mutableStateOf(listOf(TramoHorario("09:00", "21:00")))
    }
    var errorValidacion by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(cargado) {
        if (cargado && !precargado) {
            centro = if (horario.centro.isEmpty()) {
                // Editor utilizable desde el primer momento: semana abierta por
                // defecto. Solo se persiste si el ADMIN guarda.
                diasSemana.associateWith { listOf(TramoHorario("09:00", "21:00")) }
            } else {
                horario.centro
            }
            excepciones = horario.excepciones
            precargado = true
        }
    }

    // Selección de hora: día + índice de tramo + si es apertura.
    var seleccion by remember { mutableStateOf<Triple<DayOfWeek, Int, Boolean>?>(null) }
    // Selección de hora del bloque global: índice + si es apertura.
    var seleccionGlobal by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    // Estado del diálogo de excepción.
    var mostrarDatePickerExcepcion by remember { mutableStateOf(false) }
    var dialogoExcepcionAbierto by remember { mutableStateOf(false) }
    var indiceExcepcionEditando by remember { mutableStateOf<Int?>(null) }
    var excFecha by remember { mutableStateOf<Long?>(null) }
    var excTramos by remember { mutableStateOf(listOf<TramoHorario>()) }
    var seleccionExcepcion by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

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
                        stringResource(R.string.horario_centro_titulo),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.horario_centro_subtitulo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // ===================== CONFIGURAR HORARIO =====================
            EncabezadoSeccion(
                titulo = stringResource(R.string.horario_seccion_configurar),
                ayuda = stringResource(R.string.horario_configurar_ayuda)
            )
            ContenedorSeccion {
                // Sub-bloque: aplicar los tramos a toda la semana
                EtiquetaBloque(
                    titulo = stringResource(R.string.horario_aplicar_semana_titulo),
                    ayuda = stringResource(R.string.horario_aplicar_semana_ayuda)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ListaTramosEditor(
                    tramos = globalTramos,
                    onEditar = { indice, esApertura -> seleccionGlobal = indice to esApertura },
                    onEliminar = { indice ->
                        globalTramos = globalTramos.toMutableList().also { it.removeAt(indice) }
                    }
                )
                TextButton(onClick = {
                    globalTramos = globalTramos + TramoHorario("09:00", "21:00")
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AzulTrazys)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.horario_anadir_tramo), color = AzulTrazys)
                }
                val errorGlobal = validarTramos(globalTramos, context)
                if (errorGlobal != null) {
                    Text(
                        errorGlobal,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AppPrimaryButton(
                    text = stringResource(R.string.horario_aplicar_semana_boton),
                    onClick = {
                        val error = validarTramos(globalTramos, context)
                        if (error != null) {
                            errorValidacion = error
                        } else {
                            errorValidacion = null
                            centro = centro.toMutableMap().also { mapa ->
                                diasSemana.forEach { dia ->
                                    val abierto = (mapa[dia]?.isNotEmpty() == true)
                                    if (abierto) {
                                        mapa[dia] = globalTramos.map { it.copy() }
                                    }
                                }
                            }
                        }
                    },
                    fullWidth = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Sub-bloque: horario semanal (edición individual por día)
                EtiquetaBloque(
                    titulo = stringResource(R.string.horario_habitual_titulo),
                    ayuda = stringResource(R.string.horario_habitual_ayuda)
                )
                Spacer(modifier = Modifier.height(4.dp))
                diasSemana.forEachIndexed { indiceDia, dia ->
                    val tramos = centro[dia] ?: emptyList()
                    val abierto = tramos.isNotEmpty()
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                nombreDia(dia),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (abierto) {
                                    stringResource(R.string.horario_abierto)
                                } else {
                                    stringResource(R.string.horario_cerrado)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = abierto,
                                onCheckedChange = { activar ->
                                    centro = centro + (dia to if (activar) {
                                        listOf(TramoHorario("09:00", "21:00"))
                                    } else {
                                        emptyList()
                                    })
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AzulTrazys
                                )
                            )
                        }
                        if (abierto) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ListaTramosEditor(
                                tramos = tramos,
                                onEditar = { indice, esApertura ->
                                    seleccion = Triple(dia, indice, esApertura)
                                },
                                onEliminar = { indice ->
                                    val nueva = tramos.toMutableList().also { it.removeAt(indice) }
                                    centro = centro + (dia to nueva)
                                }
                            )
                            TextButton(onClick = {
                                centro = centro + (dia to (tramos + TramoHorario("09:00", "21:00")))
                            }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = AzulTrazys
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.horario_anadir_tramo), color = AzulTrazys)
                            }
                            val errorDia = validarTramos(tramos, context)
                            if (errorDia != null) {
                                Text(
                                    errorDia,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (indiceDia != diasSemana.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===================== DÍAS ESPECIALES =====================
            EncabezadoSeccion(
                titulo = stringResource(R.string.horario_dias_especiales),
                ayuda = stringResource(R.string.horario_dias_especiales_ayuda)
            )
            ContenedorSeccion {
                if (excepciones.isEmpty()) {
                    Text(
                        stringResource(R.string.horario_sin_dias_especiales),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    excepciones.forEachIndexed { indice, excepcion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    formatearFecha(excepcion.fecha),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    resumenTramos(excepcion.tramos),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                indiceExcepcionEditando = indice
                                excFecha = excepcion.fecha
                                excTramos = excepcion.tramos
                                dialogoExcepcionAbierto = true
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.horario_editar_dia_especial),
                                    tint = AzulTrazys
                                )
                            }
                            IconButton(onClick = {
                                excepciones = excepciones.toMutableList().also {
                                    it.removeAt(indice)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.horario_eliminar_dia_especial),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (indice != excepciones.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    indiceExcepcionEditando = null
                    excFecha = null
                    excTramos = listOf(TramoHorario("09:00", "21:00"))
                    mostrarDatePickerExcepcion = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AzulTrazys)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.horario_anadir_dia_especial), color = AzulTrazys)
                }
            }

            (errorValidacion ?: error)?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
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
                onClick = {
                    val errorDia = centro.entries
                        .mapNotNull { (dia, tramos) -> validarTramos(tramos, context)?.let { "$dia" } }
                        .firstOrNull()
                    val errorExc = excepciones
                        .mapNotNull { validarTramos(it.tramos, context) }
                        .firstOrNull()
                    if (errorDia != null || errorExc != null) {
                        errorValidacion = context.getString(R.string.horario_error_tramos)
                    } else {
                        errorValidacion = null
                        viewModel.guardarCentro(
                            centro.filterValues { it.isNotEmpty() },
                            excepciones
                        )
                    }
                },
                enabled = !guardando,
                fullWidth = false,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Selector de hora de un tramo diario ---
    seleccion?.let { (dia, indice, esApertura) ->
        val tramos = centro[dia] ?: emptyList()
        val tramo = tramos.getOrNull(indice) ?: TramoHorario()
        DialogoHora(
            titulo = "${
                if (esApertura) {
                    stringResource(R.string.horario_apertura)
                } else {
                    stringResource(R.string.horario_cierre)
                }
            } · ${nombreDia(dia)}",
            valorInicial = if (esApertura) tramo.apertura else tramo.cierre,
            onDismiss = { seleccion = null },
            onConfirm = { hora ->
                val nueva = tramos.toMutableList()
                if (indice < nueva.size) {
                    nueva[indice] = if (esApertura) {
                        nueva[indice].copy(apertura = hora)
                    } else {
                        nueva[indice].copy(cierre = hora)
                    }
                    centro = centro + (dia to nueva)
                }
                seleccion = null
            }
        )
    }

    // --- Selector de hora del bloque global ---
    seleccionGlobal?.let { (indice, esApertura) ->
        val tramo = globalTramos.getOrNull(indice) ?: TramoHorario()
        DialogoHora(
            titulo = if (esApertura) {
                stringResource(R.string.horario_apertura_semana)
            } else {
                stringResource(R.string.horario_cierre_semana)
            },
            valorInicial = if (esApertura) tramo.apertura else tramo.cierre,
            onDismiss = { seleccionGlobal = null },
            onConfirm = { hora ->
                val nueva = globalTramos.toMutableList()
                if (indice < nueva.size) {
                    nueva[indice] = if (esApertura) {
                        nueva[indice].copy(apertura = hora)
                    } else {
                        nueva[indice].copy(cierre = hora)
                    }
                    globalTramos = nueva
                }
                seleccionGlobal = null
            }
        )
    }

    // --- Selector de hora de un tramo de excepción ---
    seleccionExcepcion?.let { (indice, esApertura) ->
        val tramo = excTramos.getOrNull(indice) ?: TramoHorario()
        DialogoHora(
            titulo = if (esApertura) {
                stringResource(R.string.horario_apertura)
            } else {
                stringResource(R.string.horario_cierre)
            },
            valorInicial = if (esApertura) tramo.apertura else tramo.cierre,
            onDismiss = { seleccionExcepcion = null },
            onConfirm = { hora ->
                val nueva = excTramos.toMutableList()
                if (indice < nueva.size) {
                    nueva[indice] = if (esApertura) {
                        nueva[indice].copy(apertura = hora)
                    } else {
                        nueva[indice].copy(cierre = hora)
                    }
                    excTramos = nueva
                }
                seleccionExcepcion = null
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
                        state.selectedDateMillis?.let { excFecha = normalizarFechaLocal(it) }
                        mostrarDatePickerExcepcion = false
                        dialogoExcepcionAbierto = true
                    }
                ) { Text(stringResource(R.string.accion_aceptar)) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerExcepcion = false }) {
                    Text(stringResource(R.string.accion_cancelar))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    // --- Detalle de la excepción ---
    if (dialogoExcepcionAbierto) {
        val cerrado = excTramos.isEmpty()
        AlertDialog(
            onDismissRequest = { dialogoExcepcionAbierto = false },
            confirmButton = {
                TextButton(
                    enabled = excFecha != null && validarTramos(excTramos, context) == null,
                    onClick = {
                        val fecha = excFecha ?: return@TextButton
                        val nueva = ExcepcionHorario(fecha = fecha, tramos = excTramos)
                        val indice = indiceExcepcionEditando
                        excepciones = if (indice == null) {
                            excepciones + nueva
                        } else {
                            excepciones.toMutableList().also { it[indice] = nueva }
                        }.sortedBy { it.fecha }
                        dialogoExcepcionAbierto = false
                    }
                ) { Text(stringResource(R.string.accion_guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogoExcepcionAbierto = false }) {
                    Text(stringResource(R.string.accion_cancelar))
                }
            },
            title = {
                Text(
                    if (indiceExcepcionEditando == null) {
                        stringResource(R.string.horario_nuevo_dia_especial)
                    } else {
                        stringResource(R.string.horario_editar_dia_especial)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = excFecha?.let { formatearFecha(it) } ?: "",
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.horario_fecha)) },
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
                            stringResource(R.string.horario_cerrado),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = cerrado,
                            onCheckedChange = { marcarCerrado ->
                                excTramos = if (marcarCerrado) {
                                    emptyList()
                                } else {
                                    listOf(TramoHorario("09:00", "21:00"))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AzulTrazys
                            )
                        )
                    }
                    if (!cerrado) {
                        ListaTramosEditor(
                            tramos = excTramos,
                            onEditar = { indice, esApertura ->
                                seleccionExcepcion = indice to esApertura
                            },
                            onEliminar = { indice ->
                                excTramos = excTramos.toMutableList().also { it.removeAt(indice) }
                            }
                        )
                        TextButton(onClick = {
                            excTramos = excTramos + TramoHorario("09:00", "21:00")
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AzulTrazys)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.horario_anadir_tramo), color = AzulTrazys)
                        }
                        val errorExc = validarTramos(excTramos, context)
                        if (errorExc != null) {
                            Text(
                                errorExc,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Encabezado de sección de horario: título en azul corporativo con ayuda
 * contextual opcional. Compartido por las pantallas de horario del centro y de
 * actividades para mantener la misma jerarquía visual.
 */
@Composable
internal fun EncabezadoSeccion(
    titulo: String,
    ayuda: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AzulTrazys
        )
        if (ayuda != null) {
            AyudaContextual(titulo = titulo, texto = ayuda)
        }
    }
}

/**
 * Contenedor de una sección: una única Card por bloque para agrupar el contenido
 * y evitar el exceso de recuadros independientes.
 */
@Composable
internal fun ContenedorSeccion(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/**
 * Etiqueta de un sub-bloque dentro de una sección (por ejemplo, "Aplicar a toda
 * la semana" o "Horario habitual"). Usa el color de superficie, no el azul de
 * sección, para diferenciar la jerarquía.
 */
@Composable
internal fun EtiquetaBloque(
    titulo: String,
    ayuda: String? = null,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (ayuda != null) {
            AyudaContextual(titulo = titulo, texto = ayuda)
        }
    }
}

/** Lista editable de tramos: cada fila muestra "apertura - cierre" con editar/eliminar. */
@Composable
private fun ListaTramosEditor(
    tramos: List<TramoHorario>,
    onEditar: (Int, Boolean) -> Unit,
    onEliminar: (Int) -> Unit
) {
    if (tramos.isEmpty()) {
        Text(
            stringResource(R.string.horario_cerrado),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }
    tramos.forEachIndexed { indice, tramo ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${tramo.apertura.ifBlank { "--:--" }} - ${tramo.cierre.ifBlank { "--:--" }}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onEditar(indice, true) }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.horario_editar_apertura),
                    tint = AzulTrazys
                )
            }
            IconButton(onClick = { onEditar(indice, false) }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.horario_editar_cierre),
                    tint = AzulTrazys
                )
            }
            IconButton(onClick = { onEliminar(indice) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.horario_eliminar_tramo),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Diálogo de selección de hora (TimePicker) reutilizable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(
    titulo: String,
    valorInicial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val partes = valorInicial.split(":")
    val state = rememberTimePickerState(
        initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val h = state.hour.toString().padStart(2, '0')
                val m = state.minute.toString().padStart(2, '0')
                onConfirm("$h:$m")
            }) { Text(stringResource(R.string.accion_aceptar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accion_cancelar)) }
        },
        title = { Text(titulo) },
        text = {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
            }
        }
    )
}

/** "HH:mm" → minutos desde medianoche, o null si no es válido. */
private fun horaAMinutos(hora: String): Int? {
    val partes = hora.split(":")
    val h = partes.getOrNull(0)?.toIntOrNull() ?: return null
    val m = partes.getOrNull(1)?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * Valida una lista de tramos: cada apertura < cierre y sin solapes. Devuelve el
 * mensaje de error o null si es correcta.
 */
private fun validarTramos(tramos: List<TramoHorario>, context: Context): String? {
    val rangos = mutableListOf<Pair<Int, Int>>()
    tramos.forEach { tramo ->
        val apertura = horaAMinutos(tramo.apertura)
        val cierre = horaAMinutos(tramo.cierre)
        if (apertura == null || cierre == null) {
            return context.getString(R.string.horario_error_hora)
        }
        if (apertura >= cierre) {
            return context.getString(R.string.horario_error_orden)
        }
        rangos += apertura to cierre
    }
    val ordenados = rangos.sortedBy { it.first }
    for (i in 0 until ordenados.size - 1) {
        if (ordenados[i].second > ordenados[i + 1].first) {
            return context.getString(R.string.horario_error_solape)
        }
    }
    return null
}

/** Resumen legible de los tramos de una excepción. */
@Composable
private fun resumenTramos(tramos: List<TramoHorario>): String {
    if (tramos.isEmpty()) return stringResource(R.string.horario_cerrado)
    return tramos.joinToString(" · ") { "${it.apertura} - ${it.cierre}" }
}

/** Saver del horario semanal (varios tramos por día) para cambios de configuración. */
private val SaverCentro = listSaver<Map<DayOfWeek, List<TramoHorario>>, String>(
    save = { mapa ->
        mapa.flatMap { (dia, tramos) ->
            tramos.map { "${dia.name}|${it.apertura}|${it.cierre}" }
        }
    },
    restore = { lista ->
        lista.mapNotNull { fila ->
            val partes = fila.split("|")
            if (partes.size == 3) {
                runCatching { DayOfWeek.valueOf(partes[0]) }.getOrNull()?.let { dia ->
                    dia to TramoHorario(partes[1], partes[2])
                }
            } else {
                null
            }
        }.groupBy({ it.first }, { it.second })
    }
)

/** Saver de la lista global de tramos. */
private val SaverTramos = listSaver<List<TramoHorario>, String>(
    save = { tramos -> tramos.map { "${it.apertura}|${it.cierre}" } },
    restore = { filas ->
        filas.mapNotNull { fila ->
            val partes = fila.split("|")
            if (partes.size == 2) TramoHorario(partes[0], partes[1]) else null
        }
    }
)

/** Saver de las excepciones (fecha + varios tramos) para cambios de configuración. */
private val SaverExcepciones = listSaver<List<ExcepcionHorario>, String>(
    save = { lista ->
        lista.map { excepcion ->
            val tramos = excepcion.tramos.joinToString(",") { "${it.apertura}|${it.cierre}" }
            "${excepcion.fecha};$tramos"
        }
    },
    restore = { filas ->
        filas.mapNotNull { fila ->
            val partes = fila.split(";")
            val fecha = partes.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val tramos = partes.getOrNull(1)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { tramo ->
                    val p = tramo.split("|")
                    if (p.size == 2) TramoHorario(p[0], p[1]) else null
                } ?: emptyList()
            ExcepcionHorario(fecha, tramos)
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

/** Nombre visible de un día de la semana en el idioma de la app. */
@Composable
internal fun nombreDia(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> stringResource(R.string.dia_lunes)
    DayOfWeek.TUESDAY -> stringResource(R.string.dia_martes)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.dia_miercoles)
    DayOfWeek.THURSDAY -> stringResource(R.string.dia_jueves)
    DayOfWeek.FRIDAY -> stringResource(R.string.dia_viernes)
    DayOfWeek.SATURDAY -> stringResource(R.string.dia_sabado)
    DayOfWeek.SUNDAY -> stringResource(R.string.dia_domingo)
}

/** Normaliza un instante UTC del DatePicker a la medianoche local del día. */
private fun normalizarFechaLocal(utcMillis: Long): Long {
    val fecha = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatearFecha(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
