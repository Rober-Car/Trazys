package com.roberto.gestorpro.ui.notificaciones

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.model.ConfiguracionNotificaciones
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.NotificacionesViewModel

/**
 * ConfigNotificacionesScreen
 * --------------------------
 * Configuración de las notificaciones preconfiguradas del negocio
 * (configuracion_notificaciones/{negocioId}):
 *  - Notificaciones de morosidad (al entrar en MOROSO).
 *  - Recordatorio de morosidad cada 24 horas (ON -> recordatorioHoras = 24).
 *  - Notificación de baja confirmada.
 *
 * Fase D: solo se guarda la configuración. La lógica automática de envío
 * (morosidad, recordatorios y bajas) es Fase E (Cloud Functions).
 */
@Composable
fun ConfigNotificacionesScreen(
    navController: NavHostController,
    viewModel: NotificacionesViewModel
) {
    val configuracion by viewModel.configuracion.collectAsStateWithLifecycle()
    val cargandoConfiguracion by viewModel.cargandoConfiguracion.collectAsStateWithLifecycle()
    val guardandoConfiguracion by viewModel.guardandoConfiguracion.collectAsStateWithLifecycle()
    val errorConfiguracion by viewModel.errorConfiguracion.collectAsStateWithLifecycle()
    val mensajeExito by viewModel.mensajeExito.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var morosidadActiva by rememberSaveable { mutableStateOf(false) }
    var recordatorioActivo by rememberSaveable { mutableStateOf(false) }
    var bajaConfirmadaActiva by rememberSaveable { mutableStateOf(false) }
    var cambioHorarioActiva by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cargarConfiguracion()
    }

    LaunchedEffect(configuracion) {
        configuracion?.let { config ->
            morosidadActiva = config.morosidadActiva
            recordatorioActivo = config.recordatorioHoras > 0
            bajaConfirmadaActiva = config.bajaConfirmadaActiva
            cambioHorarioActiva = config.cambioHorarioActiva
        }
    }

    LaunchedEffect(mensajeExito) {
        mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumirMensajeExito()
        }
    }

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
                        text = "Configuración de notificaciones",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Preconfiguradas del negocio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Text(
                text = "Estas opciones activan o desactivan los avisos automáticos " +
                    "que generará el sistema. El envío automático se implementará " +
                    "en una fase posterior.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                cargandoConfiguracion -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            OpcionConfiguracion(
                                titulo = "Notificaciones de morosidad",
                                descripcion = "Aviso al cliente cuando entra en MOROSO",
                                activa = morosidadActiva,
                                onCambio = { morosidadActiva = it },
                                colorActivado = Color(0xFFF44336)
                            )
                            OpcionConfiguracion(
                                titulo = "Recordatorio de morosidad",
                                descripcion = "Aviso cada 24 horas mientras siga en MOROSO",
                                activa = recordatorioActivo,
                                onCambio = { recordatorioActivo = it }
                            )
                            OpcionConfiguracion(
                                titulo = "Baja confirmada",
                                descripcion = "Aviso al cliente cuando se confirma su baja",
                                activa = bajaConfirmadaActiva,
                                onCambio = { bajaConfirmadaActiva = it },
                                colorActivado = Color(0xFFF44336)
                            )
                            OpcionConfiguracion(
                                titulo = "Cambio de horario",
                                descripcion = "Aviso al cliente cuando cambia el horario del centro",
                                activa = cambioHorarioActiva,
                                onCambio = { cambioHorarioActiva = it }
                            )
                        }
                    }

                    errorConfiguracion?.let { mensaje ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = mensaje,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AppPrimaryButton(
                        text = if (guardandoConfiguracion) {
                            "Guardando..."
                        } else {
                            "Guardar configuración"
                        },
                        onClick = {
                            viewModel.guardarConfiguracion(
                                ConfiguracionNotificaciones(
                                    morosidadActiva = morosidadActiva,
                                    recordatorioHoras = if (recordatorioActivo) 24 else 0,
                                    bajaConfirmadaActiva = bajaConfirmadaActiva,
                                    cambioHorarioActiva = cambioHorarioActiva
                                )
                            )
                        },
                        enabled = !guardandoConfiguracion
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * OpcionConfiguracion
 * -------------------
 * Fila con interruptor para una opción de preconfigurada.
 */
@Composable
private fun OpcionConfiguracion(
    titulo: String,
    descripcion: String,
    activa: Boolean,
    onCambio: (Boolean) -> Unit,
    colorActivado: Color = Color(0xFF1E88E5)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = activa,
            onCheckedChange = onCambio,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorActivado
            )
        )
    }
}
