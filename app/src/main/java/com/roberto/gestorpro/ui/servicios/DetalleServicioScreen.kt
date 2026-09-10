package com.roberto.gestorpro.ui.servicios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.entity.SesionEntity
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppIconPrimaryButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.ServicioViewModel
import com.roberto.gestorpro.ui.viewmodel.SesionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * DetalleServicioScreen
 * ---------------------
 * Pantalla de detalle de un servicio. Muestra la información del servicio y
 * únicamente la sesión correspondiente al DÍA ACTUAL. Desde aquí se accede a
 * "Gestionar sesiones" (programación) y a "Ver / editar sesión".
 */
@Composable
fun DetalleServicioScreen(
    navController: NavHostController,
    idServicio: Int,
    servicioViewModel: ServicioViewModel = hiltViewModel(),
    sesionViewModel: SesionViewModel = hiltViewModel()
) {
    val servicio by servicioViewModel.servicioSeleccionado.collectAsStateWithLifecycle()
    val sesiones by sesionViewModel.sesiones.collectAsStateWithLifecycle()
    val plazasRemotas by sesionViewModel.plazasDisponiblesRemoto.collectAsStateWithLifecycle()

    LaunchedEffect(idServicio) {
        servicioViewModel.cargarServicio(idServicio)
        sesionViewModel.cargarSesionesPorServicio(idServicio)
    }

    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val hoyLocal = remember {
        java.time.LocalDate.now()
    }
    val sesionHoy = sesiones.firstOrNull {
        Instant.ofEpochMilli(it.fecha).atZone(ZoneId.systemDefault()).toLocalDate() == hoyLocal
    }

    // Refresca las plazas reales desde Firestore al entrar y al reanudar
    // (volver de reservas/edición), para reflejar reservas hechas por appCliente.
    LifecycleResumeEffect(sesionHoy?.idSesion) {
        val id = sesionHoy?.idSesion
        if (id != null) {
            sesionViewModel.refrescarPlazasSesion(id)
        }
        onPauseOrDispose { }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = servicio?.nombre ?: "Servicio",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                AppIconPrimaryButton(
                    icon = Icons.Default.Edit,
                    onClick = { navController.navigate(Routes.editarServicio(idServicio)) },
                    contentDescription = "Editar servicio"
                )
            }

            if (servicio == null) {
                Text(
                    text = "Cargando...",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            } else {
                val s = servicio!!

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (s.activo) {
                            Color(0xFF1E88E5).copy(alpha = 0.08f)
                        } else {
                            Color.Gray.copy(alpha = 0.08f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = if (s.activo) Color(0xFF1E88E5) else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = s.nombre,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilaDatoDetalle("Descripción", s.descripcion.ifBlank { "Sin descripción" })
                    }
                }

                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = "SESIÓN DE HOY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (sesionHoy == null) {
                    Text(
                        text = "No hay sesión programada para hoy.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                } else {
                    CardSesionHoy(
                        sesion = sesionHoy,
                        formatter = formatter,
                        plazasDisponibles = plazasRemotas ?: sesionHoy.plazasDisponibles,
                        onVerReservas = {
                            navController.navigate(Routes.sesionReservas(sesionHoy.idSesion))
                        },
                        onVerEditar = {
                            navController.navigate(Routes.editarSesion(sesionHoy.idSesion))
                        }
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                AppPrimaryButton(
                    text = "Gestionar sesiones",
                    icon = Icons.Default.CalendarMonth,
                    onClick = {
                        navController.navigate(Routes.programarSesiones(idServicio))
                    },
                    fullWidth = false,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                )

                if (!s.activo) {
                    Text(
                        text = "El servicio está inactivo: no se pueden programar sesiones nuevas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

/**
 * FilaDatoDetalle
 * ---------------
 * Fila de dato (etiqueta + valor) del detalle del servicio.
 */
@Composable
private fun FilaDatoDetalle(etiqueta: String, valor: String) {
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

/**
 * CardSesionHoy
 * -------------
 * Tarjeta resumida de la sesión del día actual: fecha, hora, plazas
 * disponibles (reales, desde Firestore) y las acciones "Ver reservas de la
 * sesión" y "Ver / editar sesión". La apertura de reservas y otros datos se
 * consultan/modifican desde la edición individual.
 */
@Composable
private fun CardSesionHoy(
    sesion: SesionEntity,
    formatter: DateTimeFormatter,
    plazasDisponibles: Int,
    onVerReservas: () -> Unit,
    onVerEditar: () -> Unit
) {
    val fechaStr = Instant.ofEpochMilli(sesion.fecha)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
    val textoPlazas = if (plazasDisponibles == 1) {
        "1 plaza disponible de ${sesion.capacidad}"
    } else {
        "$plazasDisponibles plazas disponibles de ${sesion.capacidad}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF00897B).copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fechaStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            FilaDatoDetalle("Hora", sesion.hora)
            FilaDatoDetalle("Plazas", textoPlazas)

            TextButton(onClick = onVerReservas) {
                Icon(
                    imageVector = Icons.Default.EventSeat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ver reservas de la sesión")
            }

            TextButton(onClick = onVerEditar) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ver / editar sesión")
            }
        }
    }
}
