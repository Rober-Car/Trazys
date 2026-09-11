package com.roberto.gestorpro.ui.clases

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.entity.ClaseEntity
import com.roberto.gestorpro.model.SesionConClase
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.viewmodel.ClaseViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ClasesScreen(
    navController: NavHostController,
    /**
     * esCliente
     * ---------
     * ✔ TIPO: parámetro (param) → Boolean
     * Indica si la pantalla se muestra para un perfil Cliente.
     * Sirve para ocultar toda la gestión (crear clases, configuración, eliminar):
     * el cliente solo podrá consultar; de momento, hasta integrar las
     * inscripciones de Firestore, verá un estado vacío con mensaje explicativo.
     */
    esCliente: Boolean = false,
    viewModel: ClaseViewModel = hiltViewModel()
) {
    var tabSeleccionada by remember { mutableIntStateOf(0) }
    val tabs = listOf("Configuración", "Sesiones")

    LaunchedEffect(Unit) {
        if (!esCliente) {
            viewModel.cargarClases()
            viewModel.cargarSesionesActivas()
        }
    }

    Scaffold(
        floatingActionButton = {
            // El cliente no puede crear ni configurar clases: solo el administrador.
            if (tabSeleccionada == 0 && !esCliente) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.CREAR_CLASE) },
                    containerColor = Color(0xFF1E88E5)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Crear clase",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Clases",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (esCliente) {
                /**
                 * Vista de cliente (solo lectura)
                 * -------------------------------
                 * ✔ TIPO: bloque condicional + Composable
                 * Es el estado que ve el cliente al entrar a Clases.
                 * Sirve para dejar claro que las inscripciones aún no están disponibles:
                 * cuando se integre la parte de Firestore (otra IA) aquí se listarán
                 * únicamente las sesiones de los servicios en los que esté inscrito.
                 */
                TabClientePlaceholder()
            } else {
                SecondaryTabRow(
                    selectedTabIndex = tabSeleccionada,
                    containerColor = Color.White,
                    contentColor = Color(0xFF1E88E5)
                ) {
                    tabs.forEachIndexed { index, titulo ->
                        Tab(
                            selected = tabSeleccionada == index,
                            onClick = { tabSeleccionada = index },
                            text = {
                                Text(
                                    text = titulo,
                                    fontWeight = if (tabSeleccionada == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selectedContentColor = Color(0xFF1E88E5),
                            unselectedContentColor = Color.Gray
                        )
                    }
                }

                when (tabSeleccionada) {
                    0 -> TabConfiguracion(viewModel, navController)
                    1 -> TabSesiones(viewModel, navController)
                }
            }
        }
    }
}

@Composable
fun TabClientePlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aún no estás inscrito en ninguna sesión",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        Text(
            text = "Cuando el centro active las inscripciones, aquí verás las sesiones de tus servicios",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TabConfiguracion(
    viewModel: ClaseViewModel,
    navController: NavHostController
) {
    val clases by viewModel.clases.collectAsStateWithLifecycle()
    var claseEliminar by remember { mutableStateOf<ClaseEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (clases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No hay clases creadas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                    Text(
                        text = "Pulsa + para crear una nueva clase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    items(clases) { clase ->
                        ClaseCard(
                            clase = clase,
                            onClick = {
                                navController.navigate(Routes.detalleClase(clase.idClase))
                            },
                            onDelete = { claseEliminar = clase }
                        )
                    }
                }
            }
        }

        if (claseEliminar != null) {
        AlertDialog(
            onDismissRequest = { claseEliminar = null },
            title = { Text("Eliminar clase") },
            text = { Text("¿Seguro que quieres eliminar \"${claseEliminar!!.nombre}\" y todas sus sesiones?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminarClase(claseEliminar!!)
                        claseEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { claseEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TabSesiones(
    viewModel: ClaseViewModel,
    navController: NavHostController
) {
    val sesionesActivas by viewModel.sesionesActivas.collectAsStateWithLifecycle()

    if (sesionesActivas.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Gray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay sesiones activas",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            Text(
                text = "Crea una clase para generar sesiones",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(sesionesActivas) { sesion ->
                SesionCard(
                    sesion = sesion,
                    onClick = {
                        navController.navigate(Routes.detalleSesionReservas(sesion.idSesion))
                    }
                )
            }
        }
    }
}

@Composable
fun ClaseCard(
    clase: ClaseEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val diasTexto = remember(clase.diasSemana) {
        val dias = ClaseViewModel.parseDiasSemana(clase.diasSemana)
        dias.sortedBy { it.value }.joinToString(", ") { dia ->
            when (dia) {
                DayOfWeek.MONDAY -> "Lun"
                DayOfWeek.TUESDAY -> "Mar"
                DayOfWeek.WEDNESDAY -> "Mié"
                DayOfWeek.THURSDAY -> "Jue"
                DayOfWeek.FRIDAY -> "Vie"
                DayOfWeek.SATURDAY -> "Sáb"
                DayOfWeek.SUNDAY -> "Dom"
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clase.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$diasTexto · ${clase.horaInicio} · ${clase.duracionMinutos}min",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${clase.capacidadMaxima}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (clase.activa) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Activa",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Inactiva",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SesionCard(
    sesion: SesionConClase,
    onClick: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val fechaStr = remember(sesion.fecha) {
        Instant.ofEpochMilli(sesion.fecha)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }
    val inscritos = sesion.capacidadMaxima - sesion.plazasDisponibles

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sesion.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$fechaStr · ${sesion.horaInicio} · ${sesion.duracionMinutos}min",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (inscritos >= sesion.capacidadMaxima)
                    Color(0xFFF44336).copy(alpha = 0.1f)
                else
                    Color(0xFF4CAF50).copy(alpha = 0.1f)
            ) {
                Text(
                    text = "$inscritos/${sesion.capacidadMaxima}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (inscritos >= sesion.capacidadMaxima)
                        Color(0xFFF44336)
                    else
                        Color(0xFF4CAF50)
                )
            }
        }
    }
}
