package com.roberto.gestorpro.ui.economia

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.entity.GastoEntity
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago
import com.roberto.gestorpro.ui.components.AccionSeleccionContextual
import com.roberto.gestorpro.ui.components.AppDialogConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogDangerConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppIconDangerButton
import com.roberto.gestorpro.ui.components.AppIconPrimaryButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.BarraSeleccionContextual
import com.roberto.gestorpro.ui.components.DialogoEdicionMovimiento
import com.roberto.gestorpro.ui.viewmodel.EconomiaViewModel
import com.roberto.gestorpro.util.MovimientoFiltro
import com.roberto.gestorpro.util.MovimientoPago
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ============================================================
 * ============ FILTRO DE ECONOMÍA ===========================
 * ============================================================ */
enum class FiltroEconomia {
    TODOS,
    INGRESOS,
    GASTOS
}

/* ============================================================
 * ============ ELEMENTO UNIFICADO DE LISTA ==================
 * ============================================================ */
sealed class ItemEconomia {
    data class Ingreso(val movimiento: MovimientoEntity) : ItemEconomia()
    data class Gasto(val gasto: GastoEntity) : ItemEconomia()

    fun fecha(): Long = when (this) {
        is Ingreso -> movimiento.fechaInicio
        is Gasto -> gasto.fecha
    }

    fun importe(): Double = when (this) {
        is Ingreso -> movimiento.precioFinal
        is Gasto -> -gasto.importe
    }
}

/* ============================================================
 * ============ PANTALLA ECONOMÍA ============================
 * ============================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EconomiaScreen(
    navController: NavHostController,
    viewModel: EconomiaViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.cargarDatos()
    }

    val movimientos by viewModel.movimientos.collectAsStateWithLifecycle()
    val gastos by viewModel.gastos.collectAsStateWithLifecycle()
    val clientesMap by viewModel.clientesMap.collectAsStateWithLifecycle()
    val serviciosMap by viewModel.serviciosMap.collectAsStateWithLifecycle()
    val serviciosActivos by viewModel.serviciosActivos.collectAsStateWithLifecycle()

    // Estado de selección múltiple (solo movimientos/ingresos)
    val modoSeleccion by viewModel.modoSeleccion.collectAsStateWithLifecycle()
    val seleccionadas by viewModel.seleccionadas.collectAsStateWithLifecycle()

    // Feedback de sincronización (StateFlow reutilizado del repositorio)
    val errorSincronizacion by viewModel.errorSincronizacion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorSincronizacion) {
        val mensaje = errorSincronizacion
        if (mensaje != null) {
            snackbarHostState.showSnackbar(mensaje)
        }
    }

    var filtroSeleccionado by rememberSaveable(
        stateSaver = Saver<FiltroEconomia, String>(
            save = { it.name },
            restore = { FiltroEconomia.valueOf(it) }
        )
    ) {
        mutableStateOf(FiltroEconomia.TODOS)
    }

    var textoBusqueda by rememberSaveable { mutableStateOf("") }
    var ordenarDescendente by rememberSaveable { mutableStateOf(true) }

    // Filtro de fechas (solo movimientos/ingresos, sobre fechaInicio).
    // Se distingue el BORRADOR (lo que muestra el campo) del filtro APLICADO
    // (lo que realmente filtra): Aplicar valida el rango y solo copia al estado
    // aplicado si es válido; un rango inválido (Desde > Hasta) NO se aplica.
    var desdeBorrador by rememberSaveable { mutableStateOf<Long?>(null) }
    var hastaBorrador by rememberSaveable { mutableStateOf<Long?>(null) }
    var desdeAplicado by rememberSaveable { mutableStateOf<Long?>(null) }
    var hastaAplicado by rememberSaveable { mutableStateOf<Long?>(null) }
    var errorRangoFechas by rememberSaveable { mutableStateOf(false) }
    var mostrarDatePickerDesde by rememberSaveable { mutableStateOf(false) }
    var mostrarDatePickerHasta by rememberSaveable { mutableStateOf(false) }

    fun formatearFiltroFecha(millis: Long?): String =
        millis?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } ?: ""

    fun aplicarRangoFechas() {
        val desde = desdeBorrador
        val hasta = hastaBorrador
        if (MovimientoFiltro.rangoValido(desde, hasta)) {
            desdeAplicado = desde
            hastaAplicado = hasta
            errorRangoFechas = false
        } else {
            errorRangoFechas = true
        }
    }

    fun limpiarRangoFechas() {
        desdeBorrador = null
        hastaBorrador = null
        desdeAplicado = null
        hastaAplicado = null
        errorRangoFechas = false
    }

    val totalIngresos = movimientos.sumOf { it.precioFinal }
    val totalGastos = gastos.sumOf { it.importe }
    val balance = totalIngresos - totalGastos

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }

    val items = remember(movimientos, gastos) {
        val lista = mutableListOf<ItemEconomia>()
        movimientos.forEach { lista.add(ItemEconomia.Ingreso(it)) }
        gastos.forEach { lista.add(ItemEconomia.Gasto(it)) }
        lista
    }

    val itemsFiltrados = remember(
        items, filtroSeleccionado, textoBusqueda, ordenarDescendente, clientesMap, serviciosMap,
        desdeAplicado, hastaAplicado
    ) {
        val filtrados = when (filtroSeleccionado) {
            FiltroEconomia.TODOS -> items
            FiltroEconomia.INGRESOS -> items.filterIsInstance<ItemEconomia.Ingreso>()
            FiltroEconomia.GASTOS -> items.filterIsInstance<ItemEconomia.Gasto>()
        }

        val busqueda = textoBusqueda.trim().lowercase()
        val resultados = if (busqueda.isBlank()) {
            filtrados
        } else {
            filtrados.filter { item ->
                when (item) {
                    is ItemEconomia.Ingreso -> {
                        val nombreCliente = item.movimiento.nombreHistorico()
                            .ifBlank { clientesMap[item.movimiento.idCliente].orEmpty() }
                            .lowercase()
                        val nombreServicios = item.movimiento.servicios
                            .mapNotNull { serviciosMap[it] }
                            .joinToString(" ")
                            .lowercase()
                        nombreCliente.contains(busqueda) ||
                                item.movimiento.dniCliente.lowercase().contains(busqueda) ||
                                nombreServicios.contains(busqueda) ||
                                item.movimiento.servicios.joinToString(" ").contains(busqueda)
                    }
                    is ItemEconomia.Gasto -> {
                        item.gasto.concepto.lowercase().contains(busqueda) ||
                                item.gasto.observaciones.orEmpty().lowercase().contains(busqueda)
                    }
                }
            }
        }

        // Filtro de fechas EN MEMORIA. Solo afecta a los movimientos (Ingreso),
        // nunca a los gastos. La deuda real y los totales se calculan aparte
        // sobre el conjunto completo (no sobre esta lista filtrada).
        val conRangoFechas = if (desdeAplicado == null && hastaAplicado == null) {
            resultados
        } else {
            resultados.filter { item ->
                when (item) {
                    is ItemEconomia.Ingreso ->
                        MovimientoFiltro.enRango(
                            item.movimiento.fechaInicio,
                            desdeAplicado,
                            hastaAplicado
                        )
                    is ItemEconomia.Gasto -> true
                }
            }
        }

        if (ordenarDescendente) {
            conRangoFechas.sortedByDescending { it.fecha() }
        } else {
            conRangoFechas.sortedBy { it.fecha() }
        }
    }

    var mostrarDialogNuevoGasto by remember { mutableStateOf(false) }
    var gastoSeleccionado by remember { mutableStateOf<GastoEntity?>(null) }
    var movimientoSeleccionado by remember { mutableStateOf<MovimientoEntity?>(null) }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }

    // Estado de acciones desde Economía (modo selección).
    var movimientoEnEdicion by remember { mutableStateOf<MovimientoEntity?>(null) }
    var mostrarSelectorPagoMasivo by remember { mutableStateOf(false) }
    var metodoPagoMasivoNombre by rememberSaveable { mutableStateOf<String?>(null) }
    var movimientosAEliminar by remember { mutableStateOf<List<MovimientoEntity>?>(null) }

    val movimientosSeleccionados = remember(movimientos, seleccionadas) {
        movimientos.filter { it.idMovimiento in seleccionadas }
    }
    val unicoSeleccionado =
        if (seleccionadas.size == 1) movimientosSeleccionados.singleOrNull() else null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!modoSeleccion) {
                FloatingActionButton(
                    onClick = { mostrarDialogNuevoGasto = true },
                    containerColor = Color(0xFF1E88E5)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Añadir gasto",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!modoSeleccion) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppNavigationBackButton(onClick = { navController.popBackStack() })
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Economía",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResumenEconomiaCard(
                    titulo = "Ingresos",
                    cantidad = formatter.format(totalIngresos),
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                ResumenEconomiaCard(
                    titulo = "Gastos",
                    cantidad = formatter.format(totalGastos),
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                ResumenEconomiaCard(
                    titulo = "Balance",
                    cantidad = formatter.format(balance),
                    color = if (balance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipEconomia(
                    texto = "Todos",
                    seleccionado = filtroSeleccionado == FiltroEconomia.TODOS,
                    color = Color(0xFF1E88E5),
                    onClick = { filtroSeleccionado = FiltroEconomia.TODOS },
                    modifier = Modifier.weight(1f)
                )
                FilterChipEconomia(
                    texto = "Ingresos",
                    seleccionado = filtroSeleccionado == FiltroEconomia.INGRESOS,
                    color = Color(0xFF4CAF50),
                    onClick = { filtroSeleccionado = FiltroEconomia.INGRESOS },
                    modifier = Modifier.weight(1f)
                )
                FilterChipEconomia(
                    texto = "Gastos",
                    seleccionado = filtroSeleccionado == FiltroEconomia.GASTOS,
                    color = Color(0xFFF44336),
                    onClick = { filtroSeleccionado = FiltroEconomia.GASTOS },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar por nombre, servicio...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1E88E5)
                    )
                )
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { ordenarDescendente = !ordenarDescendente },
                    color = Color(0xFF1E88E5).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (ordenarDescendente) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = if (ordenarDescendente) "Más recientes primero" else "Más antiguos primero",
                        tint = Color(0xFF1E88E5),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Filtro de fechas (Desde / Hasta): afecta solo a los movimientos
            // (filtrando por fechaInicio). Los gastos no se filtran nunca.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = formatearFiltroFecha(desdeBorrador),
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Desde") },
                    placeholder = { Text("dd/MM/aaaa") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color(0xFFF5F5F5),
                        disabledBorderColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha desde",
                            tint = Color(0xFF1E88E5)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { mostrarDatePickerDesde = true }
                )
                OutlinedTextField(
                    value = formatearFiltroFecha(hastaBorrador),
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Hasta") },
                    placeholder = { Text("dd/MM/aaaa") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color(0xFFF5F5F5),
                        disabledBorderColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha hasta",
                            tint = Color(0xFF1E88E5)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { mostrarDatePickerHasta = true }
                )
            }

            if (errorRangoFechas) {
                Text(
                    text = "La fecha 'Desde' no puede ser posterior a 'Hasta'. Revisa el rango.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { limpiarRangoFechas() }) {
                    Text("Limpiar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { aplicarRangoFechas() }) {
                    Text("Aplicar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(itemsFiltrados) { item ->
                    when (item) {
                        is ItemEconomia.Ingreso -> ItemMovimiento(
                            movimiento = item.movimiento,
                            nombreCliente = item.movimiento.nombreHistorico()
                                .ifBlank { clientesMap[item.movimiento.idCliente].orEmpty() },
                            nombreServicios = item.movimiento.servicios
                                .mapNotNull { serviciosMap[it] }
                                .joinToString(" + ")
                                .ifBlank { "Sin servicio asociado" },
                            enModoSeleccion = modoSeleccion,
                            seleccionado = item.movimiento.idMovimiento in seleccionadas,
                            onClick = {
                                if (modoSeleccion) {
                                    viewModel.alternarSeleccion(item.movimiento.idMovimiento)
                                } else {
                                    movimientoSeleccionado = item.movimiento
                                }
                            },
                            onLongClick = {
                                if (modoSeleccion) {
                                    viewModel.alternarSeleccion(item.movimiento.idMovimiento)
                                } else {
                                    viewModel.entrarEnSeleccion(item.movimiento.idMovimiento)
                                }
                            }
                        )
                        is ItemEconomia.Gasto -> ItemGasto(
                            gasto = item.gasto,
                            onClick = {
                                gastoSeleccionado = item.gasto
                            }
                        )
                    }
                }
            }

            if (modoSeleccion) {
                val acciones = mutableListOf<AccionSeleccionContextual>()
                val unico = unicoSeleccionado
                if (seleccionadas.size == 1 && unico != null) {
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Editar",
                        onClick = { movimientoEnEdicion = unico },
                        color = Color(0xFF1E88E5)
                    )
                    if (unico.estado == EstadoMovimiento.PENDIENTE) {
                        acciones += AccionSeleccionContextual(
                            etiqueta = "Marcar pagado",
                            onClick = {
                                viewModel.cambiarEstadoMovimientos(
                                    movimientos = listOf(unico),
                                    pagar = true,
                                    metodoPago = null
                                )
                            },
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        acciones += AccionSeleccionContextual(
                            etiqueta = "Marcar pendiente",
                            onClick = {
                                viewModel.cambiarEstadoMovimientos(
                                    movimientos = listOf(unico),
                                    pagar = false,
                                    metodoPago = null
                                )
                            },
                            color = Color(0xFFFF8F00)
                        )
                    }
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Eliminar",
                        onClick = { movimientosAEliminar = listOf(unico) },
                        color = Color(0xFFD32F2F)
                    )
                } else if (movimientosSeleccionados.size > 1) {
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Marcar pagados",
                        onClick = {
                            metodoPagoMasivoNombre = null
                            mostrarSelectorPagoMasivo = true
                        },
                        color = Color(0xFF2E7D32)
                    )
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Marcar pendientes",
                        onClick = {
                            viewModel.cambiarEstadoMovimientos(
                                movimientos = movimientosSeleccionados,
                                pagar = false,
                                metodoPago = null
                            )
                        },
                        color = Color(0xFFFF8F00)
                    )
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Eliminar",
                        onClick = { movimientosAEliminar = movimientosSeleccionados },
                        color = Color(0xFFD32F2F)
                    )
                }
                BarraSeleccionContextual(
                    numeroSeleccionados = movimientosSeleccionados.size,
                    onSalir = { viewModel.salirDeSeleccion() },
                    accionesPrincipales = acciones
                )
            }
        }
    }

    if (mostrarDialogNuevoGasto) {
        DialogNuevoGasto(
            onDismiss = { mostrarDialogNuevoGasto = false },
            onGuardar = { concepto, importe, fecha, observaciones ->
                viewModel.insertarGasto(
                    GastoEntity(
                        concepto = concepto,
                        importe = importe,
                        fecha = fecha,
                        observaciones = observaciones
                    )
                )
                mostrarDialogNuevoGasto = false
            }
        )
    }

    if (gastoSeleccionado != null) {
        DialogDetalleGasto(
            gasto = gastoSeleccionado!!,
            onDismiss = { gastoSeleccionado = null },
            onEditar = { gastoModificado ->
                viewModel.actualizarGasto(gastoModificado)
                gastoSeleccionado = null
            },
            onEliminar = {
                mostrarConfirmarEliminar = true
            }
        )
    }

    if (movimientoSeleccionado != null) {
        DialogDetalleMovimiento(
            movimiento = movimientoSeleccionado!!,
            nombreServicios = movimientoSeleccionado!!.servicios
                .mapNotNull { serviciosMap[it] }
                .joinToString(" + ")
                .ifBlank { "Sin servicio asociado" },
            onDismiss = { movimientoSeleccionado = null }
        )
    }

    if (mostrarConfirmarEliminar && gastoSeleccionado != null) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarEliminar = false },
            title = {
                Text(
                    text = "Eliminar gasto",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text("¿Seguro que quieres eliminar este gasto? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.eliminarGasto(gastoSeleccionado!!)
                        mostrarConfirmarEliminar = false
                        gastoSeleccionado = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { mostrarConfirmarEliminar = false }
                )
            }
        )
    }

    val movimientoEnEdicionActual = movimientoEnEdicion
    if (movimientoEnEdicionActual != null) {
        // Editor COMPARTIDO (única implementación): se reutiliza la misma
        // extracción que usa el perfil del cliente.
        DialogoEdicionMovimiento(
            movimiento = movimientoEnEdicionActual,
            serviciosActivos = serviciosActivos,
            serviciosMap = serviciosMap,
            onDismiss = { movimientoEnEdicion = null },
            onGuardar = { editado ->
                viewModel.editarMovimiento(editado)
                movimientoEnEdicion = null
            },
            onEliminar = { aEliminar ->
                viewModel.eliminarMovimientos(listOf(aEliminar))
                movimientoEnEdicion = null
            }
        )
    }

    if (mostrarSelectorPagoMasivo) {
        val listaAPagar = movimientosSeleccionados
        if (listaAPagar.isNotEmpty()) {
            Dialog(
                onDismissRequest = { mostrarSelectorPagoMasivo = false }
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
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Marcar ${listaAPagar.size} movimientos como pagados",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF1E88E5),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Selecciona el método de pago (opcional).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val opcionesMetodo = listOf(
                            null,
                            MetodoPago.EFECTIVO,
                            MetodoPago.BIZUM,
                            MetodoPago.TRANSFERENCIA
                        )
                        opcionesMetodo.forEach { opcion ->
                            val opcionNombre = opcion?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { metodoPagoMasivoNombre = opcionNombre }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = metodoPagoMasivoNombre == opcionNombre,
                                    onClick = { metodoPagoMasivoNombre = opcionNombre }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = MovimientoPago.metodoPagoLabel(opcion),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AppDialogTextButton(
                                text = "Cancelar",
                                onClick = { mostrarSelectorPagoMasivo = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppDialogConfirmButton(
                                text = "Confirmar",
                                onClick = {
                                    val metodo = MovimientoPago.metodoPagoDe(
                                        metodoPagoMasivoNombre
                                    )
                                    viewModel.cambiarEstadoMovimientos(
                                        movimientos = listaAPagar,
                                        pagar = true,
                                        metodoPago = metodo
                                    )
                                    mostrarSelectorPagoMasivo = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val pendientesAEliminar = movimientosAEliminar
    if (pendientesAEliminar != null) {
        AlertDialog(
            onDismissRequest = { movimientosAEliminar = null },
            title = {
                Text(
                    text = if (pendientesAEliminar.size == 1) {
                        "Eliminar movimiento"
                    } else {
                        "Eliminar movimientos"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    if (pendientesAEliminar.size == 1) {
                        "¿Seguro que quieres eliminar este movimiento? Esta acción no se puede deshacer."
                    } else {
                        "¿Eliminar ${pendientesAEliminar.size} movimientos?\n\nEsta acción no se puede deshacer."
                    }
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Eliminar",
                    onClick = {
                        viewModel.eliminarMovimientos(pendientesAEliminar)
                        movimientosAEliminar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { movimientosAEliminar = null }
                )
            }
        )
    }

    if (mostrarDatePickerDesde) {
        val selectableDatesDesde = remember {
            val hoy = LocalDate.now()
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }
        val datePickerStateDesde = rememberDatePickerState(
            selectableDates = selectableDatesDesde
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerDesde = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerStateDesde.selectedDateMillis != null,
                    onClick = {
                        desdeBorrador = datePickerStateDesde.selectedDateMillis
                        errorRangoFechas = false
                        mostrarDatePickerDesde = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerDesde = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerStateDesde)
        }
    }

    if (mostrarDatePickerHasta) {
        val selectableDatesHasta = remember {
            val hoy = LocalDate.now()
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }
        val datePickerStateHasta = rememberDatePickerState(
            selectableDates = selectableDatesHasta
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerHasta = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerStateHasta.selectedDateMillis != null,
                    onClick = {
                        hastaBorrador = datePickerStateHasta.selectedDateMillis
                        errorRangoFechas = false
                        mostrarDatePickerHasta = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerHasta = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerStateHasta)
        }
    }
}

/* ============================================================
 * ============ COMPONENTE: Tarjeta de resumen ===============
 * ============================================================ */
@Composable
fun ResumenEconomiaCard(
    titulo: String,
    cantidad: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cantidad,
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
        }
    }
}

/* ============================================================
 * ============ COMPONENTE: Chip de filtro ===================
 * ============================================================ */
@Composable
fun FilterChipEconomia(
    texto: String,
    seleccionado: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (seleccionado) color else color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = texto,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            color = if (seleccionado) Color.White else color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/* ============================================================
 * ============ COMPONENTE: Fila de movimiento ==============
 * ============================================================ */
/**
 * nombreHistorico
 * ---------------
 * Nombre del cliente almacenado en el PROPIO movimiento (fotografía histórica).
 * Es la fuente principal de Economía; la ficha `clientes/{id}` solo se usa como
 * respaldo para movimientos antiguos sin identidad histórica.
 */
private fun MovimientoEntity.nombreHistorico(): String =
    listOf(nombreCliente, apellidosCliente)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemMovimiento(
    movimiento: MovimientoEntity,
    nombreCliente: String,
    nombreServicios: String,
    enModoSeleccion: Boolean = false,
    seleccionado: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    val fechaFinFormateada = remember(movimiento.fechaFin) {
        Instant.ofEpochMilli(movimiento.fechaFin)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    val colorFondo = when (movimiento.estado) {
        EstadoMovimiento.PENDIENTE -> Color(0xFFF44336).copy(alpha = 0.08f)
        EstadoMovimiento.PAGADO -> Color(0xFF4CAF50).copy(alpha = 0.08f)
    }

    val colorEstado = when (movimiento.estado) {
        EstadoMovimiento.PENDIENTE -> Color(0xFFF44336)
        EstadoMovimiento.PAGADO -> Color(0xFF4CAF50)
    }

    val shape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                border = if (seleccionado) {
                    BorderStroke(2.dp, Color(0xFF1E88E5))
                } else {
                    BorderStroke(1.dp, Color.Transparent)
                },
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colorFondo)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (enModoSeleccion) {
                Checkbox(
                    checked = seleccionado,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1E88E5))
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Default.AttachMoney,
                contentDescription = null,
                tint = colorEstado,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nombreCliente,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$nombreServicios · $fechaFinFormateada",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "+${formatter.format(movimiento.precioFinal)}",
                style = MaterialTheme.typography.bodyLarge,
                color = colorEstado
            )
        }
    }
}

/* ============================================================
 * ============ COMPONENTE: Fila de gasto ====================
 * ============================================================ */
@Composable
fun ItemGasto(
    gasto: GastoEntity,
    onClick: () -> Unit
) {
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    val fechaFormateada = remember(gasto.fecha) {
        Instant.ofEpochMilli(gasto.fecha)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = Color(0xFFF44336),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gasto.concepto,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fechaFormateada,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = "-${formatter.format(gasto.importe)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFF44336)
            )
        }
    }
}

/* ============================================================
 * ============ DIALOG: Nuevo gasto ==========================
 * ============================================================ */
@Composable
fun DialogNuevoGasto(
    onDismiss: () -> Unit,
    onGuardar: (concepto: String, importe: Double, fecha: Long, observaciones: String?) -> Unit
) {
    var concepto by remember { mutableStateOf("") }
    var importe by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf<Long?>(null) }
    var observaciones by remember { mutableStateOf("") }
    var errorConcepto by remember { mutableStateOf(false) }
    var errorImporte by remember { mutableStateOf(false) }
    var errorFecha by remember { mutableStateOf(false) }
    var mostrarDatePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss
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
                    text = "Nuevo gasto",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = concepto,
                    onValueChange = {
                        concepto = it
                        errorConcepto = false
                    },
                    label = { Text("Concepto") },
                    isError = errorConcepto,
                    supportingText = {
                        if (errorConcepto) Text("El concepto es obligatorio")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = importe,
                    onValueChange = {
                        importe = it
                        errorImporte = false
                    },
                    label = { Text("Importe") },
                    isError = errorImporte,
                    supportingText = {
                        if (errorImporte) Text("Introduce un importe válido")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val fechaFormateada = fecha?.let {
                    Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                } ?: ""

                OutlinedTextField(
                    value = fechaFormateada,
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha") },
                    placeholder = { Text("dd/MM/aaaa") },
                    isError = errorFecha,
                    supportingText = {
                        if (errorFecha) Text("La fecha es obligatoria")
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
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Seleccionar fecha"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mostrarDatePicker = true }
                )

                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    label = { Text("Observaciones (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppDialogTextButton(
                        text = "Cancelar",
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppDialogConfirmButton(
                        text = "Guardar",
                        onClick = {
                            errorConcepto = concepto.isBlank()
                            errorImporte = importe.toDoubleOrNull() == null
                            errorFecha = fecha == null

                            if (!errorConcepto && !errorImporte && !errorFecha) {
                                onGuardar(
                                    concepto.trim(),
                                    importe.toDouble(),
                                    fecha!!,
                                    observaciones.trim().ifBlank { null }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (mostrarDatePicker) {
        val hoy = LocalDate.now()
        val selectableDates = remember {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }
        val datePickerState = rememberDatePickerState(selectableDates = selectableDates)

        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        fecha = datePickerState.selectedDateMillis
                        mostrarDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/* ============================================================
 * ============ DIALOG: Detalle gasto ========================
 * ============================================================ */
@Composable
fun DialogDetalleGasto(
    gasto: GastoEntity,
    onDismiss: () -> Unit,
    onEditar: (GastoEntity) -> Unit,
    onEliminar: () -> Unit
) {
    var editando by remember { mutableStateOf(false) }
    var conceptoEditado by remember(gasto) { mutableStateOf(gasto.concepto) }
    var importeEditado by remember(gasto) { mutableStateOf(gasto.importe.toString()) }
    var fechaEditada by remember(gasto) { mutableStateOf(gasto.fecha) }
    var observacionesEditadas by remember(gasto) { mutableStateOf(gasto.observaciones.orEmpty()) }
    var errorConcepto by remember { mutableStateOf(false) }
    var errorImporte by remember { mutableStateOf(false) }
    var mostrarDatePickerDetalle by remember { mutableStateOf(false) }

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    val fechaFormateada = remember(fechaEditada) {
        Instant.ofEpochMilli(fechaEditada)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    Dialog(
        onDismissRequest = {
            onDismiss()
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
                    text = if (editando) "Editar gasto" else "Detalle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                if (editando) {
                    OutlinedTextField(
                        value = conceptoEditado,
                        onValueChange = {
                            conceptoEditado = it
                            errorConcepto = false
                        },
                        label = { Text("Concepto") },
                        isError = errorConcepto,
                        supportingText = {
                            if (errorConcepto) Text("El concepto es obligatorio")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = importeEditado,
                        onValueChange = {
                            importeEditado = it
                            errorImporte = false
                        },
                        label = { Text("Importe") },
                        isError = errorImporte,
                        supportingText = {
                            if (errorImporte) Text("Introduce un importe válido")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = fechaFormateada,
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text("Fecha") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Seleccionar fecha"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mostrarDatePickerDetalle = true }
                    )

                    OutlinedTextField(
                        value = observacionesEditadas,
                        onValueChange = { observacionesEditadas = it },
                        label = { Text("Observaciones (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    DetalleCampo("Concepto", gasto.concepto)
                    DetalleCampo("Importe", formatter.format(gasto.importe))
                    DetalleCampo("Fecha", fechaFormateada)
                    if (!gasto.observaciones.isNullOrBlank()) {
                        DetalleCampo("Observaciones", gasto.observaciones)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (editando) {
                        AppDialogTextButton(
                            text = "Cancelar",
                            onClick = {
                                editando = false
                                conceptoEditado = gasto.concepto
                                importeEditado = gasto.importe.toString()
                                fechaEditada = gasto.fecha
                                observacionesEditadas = gasto.observaciones.orEmpty()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AppDialogConfirmButton(
                            text = "Guardar",
                            onClick = {
                                errorConcepto = conceptoEditado.isBlank()
                                errorImporte = importeEditado.toDoubleOrNull() == null

                                if (!errorConcepto && !errorImporte) {
                                    onEditar(
                                        gasto.copy(
                                            concepto = conceptoEditado.trim(),
                                            importe = importeEditado.toDouble(),
                                            fecha = fechaEditada,
                                            observaciones = observacionesEditadas.trim()
                                                .ifBlank { null }
                                        )
                                    )
                                }
                            }
                        )
                    } else {
                        AppIconPrimaryButton(
                            icon = Icons.Default.Edit,
                            onClick = { editando = true },
                            contentDescription = "Editar"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AppIconDangerButton(
                            icon = Icons.Default.Delete,
                            onClick = onEliminar,
                            contentDescription = "Eliminar"
                        )
                    }
                }
            }
        }
    }

    if (mostrarDatePickerDetalle) {
        val hoy = LocalDate.now()
        val selectableDates = remember {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
                override fun isSelectableYear(year: Int): Boolean =
                    year >= hoy.minusYears(120).year
            }
        }
        val datePickerState = rememberDatePickerState(selectableDates = selectableDates)

        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerDetalle = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        fechaEditada = datePickerState.selectedDateMillis!!
                        mostrarDatePickerDetalle = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerDetalle = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/* ============================================================
 * ============ COMPONENTE: Campo de detalle =================
 * ============================================================ */
@Composable
fun DetalleCampo(etiqueta: String, valor: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/* ============================================================
 * ============ DIALOG: Detalle movimiento ===================
 * ============================================================ */
@Composable
fun DialogDetalleMovimiento(
    movimiento: MovimientoEntity,
    nombreServicios: String,
    onDismiss: () -> Unit
) {
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    val fechaInicioFormateada = remember(movimiento.fechaInicio) {
        Instant.ofEpochMilli(movimiento.fechaInicio)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    val fechaFinFormateada = remember(movimiento.fechaFin) {
        Instant.ofEpochMilli(movimiento.fechaFin)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    val fechaPagoFormateada = remember(movimiento.fechaPago) {
        movimiento.fechaPago?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    Dialog(
        onDismissRequest = onDismiss
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
                    text = "Detalle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                DetalleCampo("Servicio", nombreServicios)
                DetalleCampo("Precio", formatter.format(movimiento.precioFinal))
                DetalleCampo("Fecha inicio", fechaInicioFormateada)
                DetalleCampo("Fecha fin", fechaFinFormateada)
                DetalleCampo("Estado", movimiento.estado.name)
                if (movimiento.estado == EstadoMovimiento.PAGADO) {
                    if (fechaPagoFormateada != null) {
                        DetalleCampo("Fecha de pago", fechaPagoFormateada)
                    }
                    DetalleCampo(
                        "Método de pago",
                        com.roberto.gestorpro.util.MovimientoPago.metodoPagoLabel(
                            movimiento.metodoPago
                        )
                    )
                }
                if (!movimiento.observaciones.isNullOrBlank()) {
                    DetalleCampo("Observaciones", movimiento.observaciones)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppDialogTextButton(
                        text = "Cerrar",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
