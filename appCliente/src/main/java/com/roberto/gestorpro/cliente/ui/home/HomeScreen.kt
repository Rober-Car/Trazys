package com.roberto.gestorpro.cliente.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.model.EstadoIndicadorCliente
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.components.DialogoDenuncia
import com.roberto.gestorpro.cliente.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.cliente.ui.viewmodel.NotificacionesClienteViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    notificacionesViewModel: NotificacionesClienteViewModel = hiltViewModel()
) {
    val idCliente by mainViewModel.idCliente.collectAsStateWithLifecycle()
    val nombreNegocio by mainViewModel.nombreNegocio.collectAsStateWithLifecycle()
    val logoNegocio by mainViewModel.logoNegocio.collectAsStateWithLifecycle()
    val estadoHome by mainViewModel.estadoHome.collectAsStateWithLifecycle()
    val notificaciones by notificacionesViewModel.notificaciones.collectAsStateWithLifecycle()
    val noLeidas = notificaciones.count { !it.leida }
    val vinculado = idCliente != null

    var menuCentroAbierto by remember { mutableStateOf(false) }
    var mostrarDenunciaLogo by remember { mutableStateOf(false) }

    // Textos localizados del bloque Home.
    val textoDenunciarTitulo = stringResource(R.string.home_denunciar_titulo)
    val textoLogoCentro = stringResource(R.string.auth_logo_desc)
    val textoNombrePorDefecto = stringResource(R.string.auth_login_subtitulo_centro)
    val textoNombreApp = stringResource(R.string.app_name)
    val textoMasOpciones = stringResource(R.string.home_mas_opciones_centro)
    val textoDenunciarCentro = stringResource(R.string.home_denunciar_centro)
    val textoNoVinculado = stringResource(R.string.home_no_vinculado_titulo)
    val textoNoVinculadoDesc = stringResource(R.string.home_no_vinculado_descripcion)
    val textoVincularCentro = stringResource(R.string.home_vincular_centro)
    val textoCardActividades = stringResource(R.string.home_card_actividades)
    val textoCardActividadesDesc = stringResource(R.string.home_card_actividades_descripcion)
    val textoCardHorario = stringResource(R.string.home_card_horario)
    val textoCardHorarioDesc = stringResource(R.string.home_card_horario_descripcion)
    val textoCardRutinas = stringResource(R.string.home_card_rutinas)
    val textoCardRutinasDesc = stringResource(R.string.home_card_rutinas_descripcion)
    val textoCardAjustes = stringResource(R.string.home_card_ajustes)
    val textoCardAjustesDesc = stringResource(R.string.home_card_ajustes_descripcion)
    val textoCardNotificaciones = stringResource(R.string.home_card_notificaciones)
    val textoCardNotificacionesDesc =
        stringResource(R.string.home_card_notificaciones_descripcion)
    val textoAvisoRenovacion = stringResource(R.string.home_aviso_renovacion_contacto)
    val textoAvisoSolicitarBaja = stringResource(R.string.home_aviso_solicitar_baja)

    if (mostrarDenunciaLogo) {
        DialogoDenuncia(
            titulo = textoDenunciarTitulo,
            onDismiss = { mostrarDenunciaLogo = false },
            onEnviar = { motivo, descripcion ->
                mainViewModel.denunciarLogoNegocio(motivo, descripcion)
            }
        )
    }

    LifecycleResumeEffect(idCliente) {
        if (idCliente != null) {
            mainViewModel.refrescarEstadoHome()
            // Recarga el buzón para mantener el contador de no leídas del badge
            // al volver al Home (p. ej. tras leer notificaciones).
            notificacionesViewModel.cargar()
        }
        onPauseOrDispose { }
    }

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
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (vinculado) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (logoNegocio.isNotBlank()) {
                            if (logoNegocio.startsWith("http")) {
                                LogoNegocioAutenticado(
                                    url = logoNegocio,
                                    contentDescription = textoLogoCentro,
                                    tamano = 48.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                AsyncImage(
                                    model = logoNegocio,
                                    contentDescription = textoLogoCentro,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }

                        Text(
                            text = nombreNegocio.ifBlank { textoNombrePorDefecto },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            IconButton(onClick = { menuCentroAbierto = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = textoMasOpciones,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuCentroAbierto,
                                onDismissRequest = { menuCentroAbierto = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(textoDenunciarCentro) },
                                    onClick = {
                                        menuCentroAbierto = false
                                        mostrarDenunciaLogo = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = textoNombreApp,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!vinculado) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFFE57373)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFCDD2)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = textoNoVinculado,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = textoNoVinculadoDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppPrimaryButton(
                            text = textoVincularCentro,
                            onClick = { navController.navigate(Routes.INICIO) },
                            fullWidth = false
                        )
                    }
                }
            }

            val estadoVisual = when (estadoHome.estado) {
                EstadoIndicadorCliente.ACTIVO -> EstadoVisualCliente.ACTIVO
                EstadoIndicadorCliente.PAGO_VENCIDO -> EstadoVisualCliente.PAGO_VENCIDO
                EstadoIndicadorCliente.BAJA -> EstadoVisualCliente.BAJA
                EstadoIndicadorCliente.REGISTRADO -> EstadoVisualCliente.REGISTRADO
                EstadoIndicadorCliente.ARCHIVADO -> EstadoVisualCliente.ARCHIVADO
                null -> null
            }

            if (vinculado && estadoVisual != null) {
                HomeClientEstadoIndicator(
                    estado = estadoVisual,
                    // Solo se muestra "Hasta/Venció/Desde" cuando existe una fecha
                    // real. Si no hay período (fechaFinActual nula) no se inventa
                    // texto "Fecha no disponible": se muestra únicamente el estado.
                    fecha = estadoHome.fechaRelevante?.let(::formatearFecha),
                    // El aviso de renovación y el enlace de baja solo se pintan
                    // dentro del indicador cuando el estado es PAGO_VENCIDO.
                    textoRenovacion = textoAvisoRenovacion,
                    textoEnlaceBaja = textoAvisoSolicitarBaja,
                    onSolicitarBaja = { navController.navigate(Routes.CUENTA) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Solo un cliente ACTIVO accede a las clases/actividades. La
                // morosidad (PAGO_VENCIDO) es independiente: sigue siendo ACTIVO.
                val puedeVerClases =
                    estadoHome.estado == EstadoIndicadorCliente.ACTIVO ||
                        estadoHome.estado == EstadoIndicadorCliente.PAGO_VENCIDO
                if (puedeVerClases) {
                    item {
                        HomeClientMenuCard(
                            titulo = textoCardActividades,
                            descripcion = textoCardActividadesDesc,
                            icono = Icons.Default.FitnessCenter,
                            color = Color(0xFFFB8C00),
                            onClick = { navController.navigate(Routes.CLASES) }
                        )
                    }
                    item {
                        HomeClientMenuCard(
                            titulo = textoCardHorario,
                            descripcion = textoCardHorarioDesc,
                            icono = Icons.Default.Schedule,
                            color = Color(0xFF1E88E5),
                            onClick = { navController.navigate(Routes.HORARIO) }
                        )
                    }
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardRutinas,
                        descripcion = textoCardRutinasDesc,
                        icono = Icons.Default.FitnessCenter,
                        color = Color(0xFF26A69A),
                        onClick = { navController.navigate(Routes.RUTINAS) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardAjustes,
                        descripcion = textoCardAjustesDesc,
                        icono = Icons.Default.Settings,
                        color = Color(0xFF78909C),
                        onClick = { navController.navigate(Routes.CONFIGURACION) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardNotificaciones,
                        descripcion = textoCardNotificacionesDesc,
                        icono = Icons.Default.Notifications,
                        color = Color(0xFF7E57C2),
                        badge = noLeidas,
                        onClick = { navController.navigate(Routes.NOTIFICACIONES) }
                    )
                }
            }
        }
    }
}

/**
 * HomeClientMenuCard
 * ------------------
 * Tarjeta de navegación del Home de GestorPro Cliente.
 *
 * Comparte el mismo concepto visual que la MenuCard de GestorPro Admin:
 * composición vertical (icono arriba en contenedor coloreado, título y
 * descripción debajo), proporción compacta y bordes suaves. Es un componente
 * privado del Home para no alterar la MenuCard compartida (usada en otras
 * pantallas como Cuenta).
 */
@Composable
private fun HomeClientMenuCard(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    color: Color = Color(0xFF1E88E5),
    badge: Int? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icono,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (badge != null && badge > 0) {
                HomeMenuBadge(
                    cantidad = badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 14.dp)
                )
            }
        }
    }
}

/**
 * HomeMenuBadge
 * -------------
 * Badge numérico rojo usado en la esquina superior derecha del card de
 * "Notificaciones" del Home cuando existen avisos sin leer. No se dibuja
 * si la cantidad es 0.
 */
@Composable
private fun HomeMenuBadge(cantidad: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFFE53935),
                shape = CircleShape
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cantidad.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * EstadoVisualCliente
 * -------------------
 * Modelo visual (sin datos) de los estados que puede mostrar el indicador de
 * estado del cliente en el Home. Solo se usa para la presentación.
 */
private enum class EstadoVisualCliente {
    ACTIVO, PAGO_VENCIDO, BAJA, REGISTRADO, ARCHIVADO
}

/**
 * HomeClientEstadoIndicator
 * -------------------------
 * Bloque visual, neutro y discreto, que muestra el estado del cliente en el
 * Home: una bola de color a la izquierda y el texto de estado (con protagonismo)
 * junto a la fecha (secundaria). El fondo es neutro; solo la bola y el título
 * adoptan el color semántico. El color y el texto dependen del ESTADO (enum),
 * nunca del texto traducido.
 *
 * Cuando el estado es PAGO_VENCIDO, dentro del propio indicador se añade el
 * aviso de renovación (texto no clicable) y el enlace independiente para
 * solicitar la baja (único elemento clicable). No existe ninguna acción para
 * renovar o pagar desde la app.
 */
@Composable
private fun HomeClientEstadoIndicator(
    estado: EstadoVisualCliente,
    fecha: String?,
    textoRenovacion: String? = null,
    textoEnlaceBaja: String? = null,
    onSolicitarBaja: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (color, tituloRecurso, prefijoRecurso) = when (estado) {
        EstadoVisualCliente.ACTIVO ->
            Triple(Color(0xFF43A047), R.string.estado_activo, R.string.home_prefijo_hasta)
        EstadoVisualCliente.PAGO_VENCIDO ->
            Triple(Color(0xFFE53935), R.string.home_estado_pago_vencido, R.string.home_prefijo_vencio)
        EstadoVisualCliente.BAJA ->
            Triple(Color(0xFF78909C), R.string.estado_baja, R.string.home_prefijo_desde)
        EstadoVisualCliente.REGISTRADO ->
            Triple(Color(0xFF64B5F6), R.string.estado_registrado, null)
        EstadoVisualCliente.ARCHIVADO ->
            Triple(Color(0xFF78909C), R.string.estado_archivado, null)
    }
    val titulo = stringResource(tituloRecurso)
    val prefijo = prefijoRecurso?.let { stringResource(it) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            if (estado == EstadoVisualCliente.PAGO_VENCIDO) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (prefijo != null && fecha != null) {
                    Text(
                        text = "$prefijo $fecha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (
                    estado == EstadoVisualCliente.PAGO_VENCIDO &&
                    textoRenovacion != null &&
                    textoEnlaceBaja != null
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = textoRenovacion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = textoEnlaceBaja,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onSolicitarBaja() }
                    )
                }
            }
        }
    }
}

private fun formatearFecha(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@Preview(showBackground = true)
@Composable
fun HomeClientEstadoIndicatorPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            HomeClientEstadoIndicator(EstadoVisualCliente.ACTIVO, "31/08/2026")
            Spacer(modifier = Modifier.height(12.dp))
            HomeClientEstadoIndicator(EstadoVisualCliente.PAGO_VENCIDO, "10/07/2026")
            Spacer(modifier = Modifier.height(12.dp))
            HomeClientEstadoIndicator(EstadoVisualCliente.BAJA, "05/03/2026")
        }
    }
}
