package com.roberto.gestorpro.cliente.ui.notificaciones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.roberto.gestorpro.cliente.model.Notificacion
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppSecondaryButton
import com.roberto.gestorpro.cliente.ui.components.DialogoDenuncia
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.cliente.ui.viewmodel.NotificacionesClienteViewModel
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ListaNotificacionesScreen
 * -------------------------
 * Buzón de notificaciones del CLIENTE con datos reales de Firestore
 * (notificaciones_por_destinatario). Ordenadas de más reciente a más antigua.
 * Al abrir una notificación queda marcada como leída (leida=true/fechaLeida).
 */
@Composable
fun ListaNotificacionesScreen(
    navController: NavHostController,
    viewModel: NotificacionesClienteViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val noVinculado by viewModel.noVinculado.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notificaciones by viewModel.notificaciones.collectAsStateWithLifecycle()

    var notificacionADenunciar by remember { mutableStateOf<Notificacion?>(null) }

    // Textos localizados del bloque de notificaciones.
    val textoNotificaciones = stringResource(R.string.home_card_notificaciones)
    val textoDenunciarTitulo = stringResource(R.string.notif_denunciar_titulo)
    val textoNoVinculadoTitulo = stringResource(R.string.notif_no_vinculado_titulo)
    val textoNoVinculadoDetalle = stringResource(R.string.notif_no_vinculado_detalle)
    val textoVaciaTitulo = stringResource(R.string.notif_vacia_titulo)
    val textoVaciaDetalle = stringResource(R.string.notif_vacia_detalle)
    val textoReintentar = stringResource(R.string.notif_reintentar)
    val textoMasOpciones = stringResource(R.string.notif_mas_opciones)
    val textoDenunciar = stringResource(R.string.notif_accion_denunciar)
    val textoEliminar = stringResource(R.string.notif_accion_eliminar)

    LaunchedEffect(Unit) {
        viewModel.cargar()
    }

    notificacionADenunciar?.let { notificacion ->
        DialogoDenuncia(
            titulo = textoDenunciarTitulo,
            onDismiss = { notificacionADenunciar = null },
            onEnviar = { motivo, descripcion ->
                mainViewModel.denunciarNotificacion(
                    notificacion.notificacionId,
                    motivo,
                    descripcion
                )
            }
        )
    }

    val morado = Color(0xFF7E57C2)
    val formateador = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppNavigationBackButton(onClick = { navController.popBackStack() })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = textoNotificaciones,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when {
                cargando -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                noVinculado -> MensajeNotificaciones(
                    titulo = textoNoVinculadoTitulo,
                    detalle = textoNoVinculadoDetalle
                )

                error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppSecondaryButton(
                        text = textoReintentar,
                        onClick = { viewModel.cargar() },
                        fullWidth = false
                    )
                }

                notificaciones.isEmpty() -> MensajeNotificaciones(
                    titulo = textoVaciaTitulo,
                    detalle = textoVaciaDetalle
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notificaciones, key = { it.id }) { notificacion ->
                        NotificacionCard(
                            notificacion = notificacion,
                            morado = morado,
                            formateador = formateador,
                            esManual = notificacion.origen == "MANUAL",
                            onDenunciar = { notificacionADenunciar = notificacion },
                            onEliminar = { viewModel.eliminar(notificacion.id) },
                            onClick = { viewModel.marcarLeida(notificacion.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * NotificacionCard
 * ----------------
 * Tarjeta de una notificación con diferenciación visual leída/no leída:
 * punto morado e icono/título destacados para las no leídas, y tono más
 * tenue para las ya leídas.
 */
@Composable
private fun NotificacionCard(
    notificacion: Notificacion,
    morado: Color,
    formateador: DateTimeFormatter,
    esManual: Boolean,
    onDenunciar: () -> Unit,
    onEliminar: () -> Unit,
    onClick: () -> Unit
) {
    val leida = notificacion.leida
    val colorIcono = if (leida) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        morado
    }
    var menuAbierto by remember { mutableStateOf(false) }

    val textoMasOpciones = stringResource(R.string.notif_mas_opciones)
    val textoDenunciar = stringResource(R.string.notif_accion_denunciar)
    val textoEliminar = stringResource(R.string.notif_accion_eliminar)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (leida) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(
                        color = if (leida) Color.Transparent else morado,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = colorIcono,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = IdiomaAplicacion.textoLocalizado(
                        notificacion.titulo,
                        notificacion.tituloEn
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (leida) FontWeight.SemiBold else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = IdiomaAplicacion.textoLocalizado(
                        notificacion.mensaje,
                        notificacion.mensajeEn
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatoFecha(notificacion.fechaEnvio, formateador),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorIcono
                )
            }

            Box {
                IconButton(onClick = { menuAbierto = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = textoMasOpciones,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuAbierto,
                    onDismissRequest = { menuAbierto = false }
                ) {
                    // "Denunciar" solo para las notificaciones de origen MANUAL;
                    // "Eliminar" (borra SOLO el buzón propio) siempre disponible.
                    if (esManual) {
                        DropdownMenuItem(
                            text = { Text(textoDenunciar) },
                            onClick = {
                                menuAbierto = false
                                onDenunciar()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(textoEliminar) },
                        onClick = {
                            menuAbierto = false
                            onEliminar()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MensajeNotificaciones(titulo: String, detalle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = Color(0xFF7E57C2),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatoFecha(millis: Long, formateador: DateTimeFormatter): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formateador)
