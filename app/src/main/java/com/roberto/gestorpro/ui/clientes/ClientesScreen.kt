package com.roberto.gestorpro.ui.clientes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.model.Cliente
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.model.FiltroClientes
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.util.FechaBajaEfectiva
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.roberto.gestorpro.ui.components.AccionSeleccionContextual
import com.roberto.gestorpro.ui.components.AppDialogDangerConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.BarraSeleccionContextual
import com.roberto.gestorpro.ui.components.ClienteItem
import com.roberto.gestorpro.ui.viewmodel.ClienteViewModel

/**
 * FiltroCuenta
 * ------------
 * Filtro de VINCULACIÓN de la lista de clientes (independiente del estado
 * administrativo). Es una propiedad derivada de `firebaseUid`; NO es un estado
 * de Firestore.
 */
enum class FiltroCuenta {
    TODOS,
    VINCULADOS,
    NO_VINCULADOS
}

private fun etiquetaFiltroCuenta(opcion: FiltroCuenta): String = when (opcion) {
    FiltroCuenta.TODOS -> "Todos"
    FiltroCuenta.VINCULADOS -> "Vinculados"
    FiltroCuenta.NO_VINCULADOS -> "No vinculados"
}

/**
 * cumpleFiltroClientes
 * --------------------
 * Predicado PURo que combina el filtro de VINCULACIÓN (derivado de firebaseUid)
 * con el de ESTADO administrativo. Solo determina la lista visible: no modifica
 * ningún dato del cliente ni crea estados nuevos.
 */
internal fun cumpleFiltroClientes(
    cliente: Cliente,
    cuenta: FiltroCuenta,
    estado: FiltroClientes,
    morososIds: Set<Int>
): Boolean {
    val cuentaOk = when (cuenta) {
        FiltroCuenta.TODOS -> true
        FiltroCuenta.VINCULADOS -> !cliente.firebaseUid.isNullOrBlank()
        FiltroCuenta.NO_VINCULADOS -> cliente.firebaseUid.isNullOrBlank()
    }
    val estadoOk = when (estado) {
        FiltroClientes.TODOS -> cliente.estado != EstadoCliente.ARCHIVADO
        FiltroClientes.ACTIVO -> cliente.estado == EstadoCliente.ACTIVO
        FiltroClientes.MOROSO -> cliente.idCliente in morososIds
        FiltroClientes.BAJA -> cliente.estado == EstadoCliente.BAJA
        FiltroClientes.ARCHIVADO -> cliente.estado == EstadoCliente.ARCHIVADO
    }
    return cuentaOk && estadoOk
}

@Composable
fun ClientesScreen(
    navController: NavHostController
) {

    var filtroSeleccionado by rememberSaveable(
        stateSaver = Saver<FiltroClientes, String>(
            save = { it.name },
            restore = { FiltroClientes.valueOf(it) }
        )
    ) {
        mutableStateOf(FiltroClientes.TODOS)
    }

    var textoBusqueda by rememberSaveable { mutableStateOf("") }

    // Ordenación alfabética de la lista: A→Z (ascendente, por defecto) o
    // Z→A (descendente). Se aplica DESPUÉS de filtros y búsqueda, sin alterarlos.
    var ordenDescendente by rememberSaveable { mutableStateOf(false) }

    // Filtro de VINCULACIÓN (independiente del estado) + visibilidad del menú.
    var filtroCuentaNombre by rememberSaveable { mutableStateOf(FiltroCuenta.TODOS.name) }
    var mostrarMenuFiltros by rememberSaveable { mutableStateOf(false) }
    val filtroCuenta = FiltroCuenta.valueOf(filtroCuentaNombre)
    // El indicador del ⋮ representa SOLO el filtro de cuenta (el estado ya es
    // visible en los botones superiores de la pantalla).
    val hayFiltroCuenta = filtroCuenta != FiltroCuenta.TODOS

    val viewModel: ClienteViewModel = hiltViewModel()

    // Al volver a la pantalla se reconcilia la lista con la nube: se incorporan
    // clientes nuevos y se actualiza SOLO el firebaseUid local a la verdad
    // remota (para reflejar un cliente que se vinculó en la app Cliente). Sin
    // polling: solo al entrar/reanudar.
    LifecycleResumeEffect(Unit) {
        viewModel.incorporarClientesRemotos()
        onPauseOrDispose { }
    }

    val clientes by viewModel.clientes.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val morososIds by viewModel.morososIds.collectAsStateWithLifecycle()

    // Modo selección múltiple (lista de clientes)
    val modoSeleccion by viewModel.modoSeleccionClientes.collectAsStateWithLifecycle()
    val seleccionadas by viewModel.clientesSeleccionados.collectAsStateWithLifecycle()

    // Si un cliente seleccionado desaparece de la lista, se poda de la selección.
    val idsClientesActuales = remember(clientes) { clientes.map { it.idCliente }.toSet() }
    LaunchedEffect(idsClientesActuales, seleccionadas) {
        viewModel.podarSeleccionClientes(idsClientesActuales)
    }

    val clientesSeleccionadosActuales = remember(clientes, seleccionadas) {
        clientes.filter { it.idCliente in seleccionadas }
    }
    val unicoSeleccionado =
        if (seleccionadas.size == 1) clientesSeleccionadosActuales.singleOrNull() else null
    val puedeActivar = clientesSeleccionadosActuales.any { it.estado != EstadoCliente.ACTIVO }
    val puedeArchivar = clientesSeleccionadosActuales.any { it.estado != EstadoCliente.ARCHIVADO }
    val puedeDarDeBaja = clientesSeleccionadosActuales.any {
        it.estado == EstadoCliente.ACTIVO || it.estado == EstadoCliente.REGISTRADO
    }

    var listaArchivarConfirmar by remember { mutableStateOf<List<Cliente>?>(null) }
    var listaBajaConfirmar by remember { mutableStateOf<List<Cliente>?>(null) }

    // Fecha EFECTIVA elegida para la baja MASIVA (una sola para todo el lote).
    // null = HOY.
    var mostrarDatePickerBaja by rememberSaveable { mutableStateOf(false) }
    var fechaBajaElegidaEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val clientesFiltrados = clientes
        .filter { cliente ->
            cumpleFiltroClientes(
                cliente = cliente,
                cuenta = filtroCuenta,
                estado = filtroSeleccionado,
                morososIds = morososIds
            )
        }
        .filter { cliente ->
            textoBusqueda.isBlank() || listOf(
                cliente.nombre,
                cliente.telefono,
                cliente.dni,
                cliente.email.orEmpty()
            ).any { it.contains(textoBusqueda, ignoreCase = true) }
        }
        // Orden alfabético por apellido (principal) y nombre (secundario),
        // robusto ante mayúsculas/minúsculas. Se aplica DESPUÉS de los filtros y
        // la búsqueda, sin alterarlos. El control A→Z / Z→A solo cambia la
        // dirección (ASC/DESC); no filtra ni oculta clientes.
        .sortedWith(
            if (ordenDescendente) {
                compareByDescending<Cliente> { it.apellidos.trim().lowercase() }
                    .thenByDescending { it.nombre.trim().lowercase() }
            } else {
                compareBy<Cliente> { it.apellidos.trim().lowercase() }
                    .thenBy { it.nombre.trim().lowercase() }
            }
        )

    // En modo selección, al cambiar/limpiar el filtro se podan los ids que ya no
    // son visibles (no quedan clientes fantasma seleccionados).
    val idsVisibles = remember(clientesFiltrados) {
        clientesFiltrados.map { it.idCliente }.toSet()
    }
    LaunchedEffect(idsVisibles, modoSeleccion) {
        if (modoSeleccion) viewModel.podarSeleccionClientes(idsVisibles)
    }

    val totalClientes = clientes.count { it.estado != EstadoCliente.ARCHIVADO }
    val totalActivos = clientes.count { it.estado == EstadoCliente.ACTIVO }
    val totalMorosos = morososIds.size
    val totalBajas = clientes.count { it.estado == EstadoCliente.BAJA }
    val totalArchivados = clientes.count { it.estado == EstadoCliente.ARCHIVADO }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!modoSeleccion) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppNavigationBackButton(onClick = { navController.popBackStack() })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Clientes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppPrimaryButton(
                        text = "Añadir cliente",
                        onClick = { navController.navigate(Routes.AÑADIRCLIENTE) },
                        fullWidth = false
                    )
                    Box {
                        IconButton(onClick = { mostrarMenuFiltros = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Filtrar clientes",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (hayFiltroCuenta) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(8.dp)
                                    .background(Color(0xFF1E88E5), CircleShape)
                            )
                        }
                    }
                }
            }

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
                    placeholder = { Text("Buscar por nombre, DNI, teléfono...") },
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
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5)
                    )
                )
                // Ordenación alfabética A→Z / Z→A (mismo patrón visual y
                // conceptual que el orden por fecha de Economía). Cambia SOLO la
                // dirección de la ordenación; no filtra ni oculta clientes.
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { ordenDescendente = !ordenDescendente },
                    color = Color(0xFF1E88E5).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (ordenDescendente) {
                            Icons.Default.ArrowDownward
                        } else {
                            Icons.Default.ArrowUpward
                        },
                        contentDescription = if (ordenDescendente) "Z a A" else "A a Z",
                        tint = Color(0xFF1E88E5),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    titulo = "Todos",
                    cantidad = totalClientes,
                    color = Color(0xFF1E88E5),
                    seleccionado = filtroSeleccionado == FiltroClientes.TODOS,
                    onClick = { filtroSeleccionado = FiltroClientes.TODOS }
                )
                FilterChipItem(
                    titulo = "Activos",
                    cantidad = totalActivos,
                    color = Color(0xFF4CAF50),
                    seleccionado = filtroSeleccionado == FiltroClientes.ACTIVO,
                    onClick = { filtroSeleccionado = FiltroClientes.ACTIVO }
                )
                FilterChipItem(
                    titulo = "Bajas",
                    cantidad = totalBajas,
                    color = Color.Gray,
                    seleccionado = filtroSeleccionado == FiltroClientes.BAJA,
                    onClick = { filtroSeleccionado = FiltroClientes.BAJA }
                )
                FilterChipItem(
                    titulo = "Morosos",
                    cantidad = totalMorosos,
                    color = Color.Red,
                    seleccionado = filtroSeleccionado == FiltroClientes.MOROSO,
                    onClick = { filtroSeleccionado = FiltroClientes.MOROSO }
                )
                FilterChipItem(
                    titulo = "Archivados",
                    cantidad = totalArchivados,
                    color = Color(0xFFFF9800),
                    seleccionado = filtroSeleccionado == FiltroClientes.ARCHIVADO,
                    onClick = { filtroSeleccionado = FiltroClientes.ARCHIVADO }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (clientesFiltrados.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (textoBusqueda.isNotBlank()) "No se encontraron resultados" else "No hay clientes en esta categoría",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(clientesFiltrados, key = { it.idCliente }) { cliente ->
                        val esArchivado = cliente.estado == EstadoCliente.ARCHIVADO
                        ClienteItem(
                            nombre = cliente.nombre,
                            telefono = cliente.telefono,
                            estado = cliente.estado,
                            foto = cliente.foto,
                            idCliente = cliente.idCliente,
                            obtenerFotoCacheada = viewModel::cargarFotoLocal,
                            esMoroso = cliente.idCliente in morososIds,
                            seleccionable = modoSeleccion,
                            seleccionado = cliente.idCliente in seleccionadas,
                            onClick = {
                                if (modoSeleccion) {
                                    viewModel.alternarSeleccionCliente(cliente.idCliente)
                                } else {
                                    navController.navigate(
                                        Routes.perfilCliente(cliente.idCliente)
                                    )
                                }
                            },
                            onLongClick = {
                                if (modoSeleccion) {
                                    viewModel.alternarSeleccionCliente(cliente.idCliente)
                                } else {
                                    viewModel.entrarEnSeleccionCliente(cliente.idCliente)
                                }
                            },
                            onArchivar = if (!modoSeleccion && !esArchivado) {
                                { viewModel.archivarCliente(cliente) }
                            } else null,
                            onRestaurar = if (!modoSeleccion && esArchivado) {
                                { viewModel.restaurarCliente(cliente) }
                            } else null
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
                        onClick = {
                            viewModel.salirSeleccionClientes()
                            navController.navigate(Routes.modificarCliente(unico.idCliente))
                        },
                        color = Color(0xFF1E88E5)
                    )
                }
                if (puedeActivar) {
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Activar / dar de alta",
                        onClick = {
                            viewModel.activarClientesSeleccionados(
                                clientesSeleccionadosActuales.map { it.idCliente }
                            )
                        },
                        color = Color(0xFF2E7D32)
                    )
                }
                if (puedeArchivar) {
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Archivar",
                        onClick = { listaArchivarConfirmar = clientesSeleccionadosActuales },
                        color = Color(0xFFFF8F00)
                    )
                }
                if (puedeDarDeBaja) {
                    acciones += AccionSeleccionContextual(
                        etiqueta = "Dar de baja",
                        onClick = {
                            fechaBajaElegidaEpochDay = null
                            listaBajaConfirmar = clientesSeleccionadosActuales
                        },
                        color = Color(0xFFD32F2F)
                    )
                }
                BarraSeleccionContextual(
                    numeroSeleccionados = clientesSeleccionadosActuales.size,
                    onSalir = { viewModel.salirSeleccionClientes() },
                    accionesPrincipales = acciones
                )
            }
        }
    }

    val pendienteArchivar = listaArchivarConfirmar
    if (pendienteArchivar != null) {
        AlertDialog(
            onDismissRequest = { listaArchivarConfirmar = null },
            title = {
                Text(
                    text = if (pendienteArchivar.size == 1) "Archivar cliente" else "Archivar clientes",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    if (pendienteArchivar.size == 1) {
                        "¿Seguro que quieres archivar este cliente? No aparecerá en la lista principal, pero podrás restaurarlo más adelante."
                    } else {
                        "¿Archivar ${pendienteArchivar.size} clientes? No aparecerán en la lista principal, pero podrás restaurarlos más adelante."
                    }
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Archivar",
                    onClick = {
                        viewModel.archivarClientesSeleccionados(
                            pendienteArchivar.map { it.idCliente }
                        )
                        listaArchivarConfirmar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { listaArchivarConfirmar = null }
                )
            }
        )
    }

    val pendienteBaja = listaBajaConfirmar
    if (pendienteBaja != null) {
        AlertDialog(
            onDismissRequest = { listaBajaConfirmar = null },
            title = {
                Text(
                    text = if (pendienteBaja.size == 1) "Dar de baja" else "Dar de baja clientes",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                val diaElegido = fechaBajaElegidaEpochDay?.let { LocalDate.ofEpochDay(it) }
                    ?: LocalDate.now()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (pendienteBaja.size == 1) {
                            "¿Confirmar la baja de este cliente? Se cancelarán sus reservas futuras y se le notificará si está activada la configuración de avisos. Los servicios contratados se conservan."
                        } else {
                            "¿Dar de baja ${pendienteBaja.size} clientes?\n\nSe cancelarán sus reservas futuras y se avisará según la configuración. Los servicios contratados se conservan."
                        }
                    )
                    // UNA sola fecha efectiva para TODO el lote.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { mostrarDatePickerBaja = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.cliente_baja_fecha),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = diaElegido.format(
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Dar de baja",
                    onClick = {
                        viewModel.darDeBajaClientesSeleccionados(
                            pendienteBaja.map { it.idCliente },
                            FechaBajaEfectiva.millis(
                                fechaBajaElegidaEpochDay,
                                System.currentTimeMillis()
                            )
                        )
                        listaBajaConfirmar = null
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { listaBajaConfirmar = null }
                )
            }
        )
    }

    if (mostrarDatePickerBaja) {
        // Fecha EFECTIVA de la baja masiva: HOY por defecto; solo HOY o anterior.
        val selectableDatesBaja = remember {
            val hoy = LocalDate.now()
            val hoyUtc = hoy.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val fechaMinimaUtc = hoy.minusYears(120)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in fechaMinimaUtc..hoyUtc

                override fun isSelectableYear(year: Int): Boolean =
                    year in (hoy.minusYears(120).year..hoy.year)
            }
        }
        val seleccionInicialBaja = fechaBajaElegidaEpochDay?.let {
            LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerBajaState = rememberDatePickerState(
            initialSelectedDateMillis = seleccionInicialBaja,
            selectableDates = selectableDatesBaja
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerBaja = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerBajaState.selectedDateMillis != null,
                    onClick = {
                        datePickerBajaState.selectedDateMillis?.let { utc ->
                            fechaBajaElegidaEpochDay = Instant.ofEpochMilli(utc)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        }
                        mostrarDatePickerBaja = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerBaja = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerBajaState)
        }
    }

    if (mostrarMenuFiltros) {
        AlertDialog(
            onDismissRequest = { mostrarMenuFiltros = false },
            title = {
                Text(
                    text = "Filtrar clientes",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Cuenta",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FiltroCuenta.entries.forEach { opcion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filtroCuenta == opcion,
                                onClick = { filtroCuentaNombre = opcion.name }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = etiquetaFiltroCuenta(opcion),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hayFiltroCuenta) {
                        AppDialogTextButton(
                            text = "Limpiar filtros",
                            onClick = {
                                // Solo limpia el filtro de CUENTA; el estado se
                                // controla desde los botones superiores y no se toca.
                                filtroCuentaNombre = FiltroCuenta.TODOS.name
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    AppDialogTextButton(
                        text = "Cerrar",
                        onClick = { mostrarMenuFiltros = false }
                    )
                }
            }
        )
    }
}

@Composable
fun FilterChipItem(
    titulo: String,
    cantidad: Int,
    color: Color,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = {
            Text(
                text = "$titulo ($cantidad)",
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White
        )
    )
}
