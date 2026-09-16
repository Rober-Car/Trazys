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
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Textos localizados del bloque Home.
    val textoLogoCentro = stringResource(R.string.auth_logo_desc)
    val textoNombrePorDefecto = stringResource(R.string.auth_login_subtitulo_centro)
    val textoNombreApp = stringResource(R.string.app_name)
    val textoNoVinculado = stringResource(R.string.home_no_vinculado_titulo)
    val textoNoVinculadoDesc = stringResource(R.string.home_no_vinculado_descripcion)
    val textoVincularCentro = stringResource(R.string.home_vincular_centro)
    val textoCardActividades = stringResource(R.string.home_card_actividades)
    val textoCardHorario = stringResource(R.string.home_card_horario)
    val textoCardRutinas = stringResource(R.string.home_card_rutinas)
    val textoCardActividadesHorario = stringResource(R.string.home_card_horario_actividades)
    val textoCardAjustes = stringResource(R.string.home_card_ajustes)
    val textoCardNotificaciones = stringResource(R.string.home_card_notificaciones)
    val textoAvisoRenovacion = stringResource(R.string.home_aviso_renovacion_contacto)
    val textoAvisoSolicitarBaja = stringResource(R.string.home_aviso_solicitar_baja)

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
                // El inset inferior se conserva; el superior pasa al interior
                // del encabezado azul para que llegue hasta el borde superior.
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E88E5))
                    .padding(top = innerPadding.calculateTopPadding())
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
                                    tamano = 54.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                AsyncImage(
                                    model = logoNegocio,
                                    contentDescription = textoLogoCentro,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }

                        Text(
                            text = nombreNegocio.ifBlank { textoNombrePorDefecto },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = textoNombreApp,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Home con exactamente 6 cards en 3 filas de 2:
                // Reservas · Rutinas / Horario del centro · Actividades /
                // Ajustes · Notificaciones.
                item {
                    HomeClientMenuCard(
                        titulo = textoCardActividades,
                        imagenFondo = R.drawable.img_reservas2,
                        onClick = { navController.navigate(Routes.CLASES) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardRutinas,
                        imagenFondo = R.drawable.img_rutinas,
                        onClick = { navController.navigate(Routes.RUTINAS) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardHorario,
                        imagenFondo = R.drawable.img_horario,
                        onClick = { navController.navigate(Routes.HORARIO_CENTRO) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardActividadesHorario,
                        imagenFondo = R.drawable.img_actividades,
                        onClick = { navController.navigate(Routes.HORARIO_ACTIVIDADES) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardAjustes,
                        icono = Icons.Default.Settings,
                        color = Color(0xFF1E88E5),
                        onClick = { navController.navigate(Routes.CONFIGURACION) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = textoCardNotificaciones,
                        icono = Icons.Default.Notifications,
                        color = Color(0xFF1E88E5),
                        badge = noLeidas,
                        onClick = { navController.navigate(Routes.NOTIFICACIONES) }
                    )
                }
                // Estado del cliente al FINAL del Home (información secundaria),
                // sin tarjeta: solo círculo + texto. Ocupa el ancho completo.
                if (vinculado && estadoVisual != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomeClientEstadoIndicator(
                            estado = estadoVisual,
                            // Solo se muestra la fecha cuando existe un período
                            // real; si no lo hay, no se inventa un texto.
                            fecha = estadoHome.fechaRelevante?.let(::formatearFecha),
                            // Aviso de renovación y enlace de baja (PAGO_VENCIDO).
                            textoRenovacion = textoAvisoRenovacion,
                            textoEnlaceBaja = textoAvisoSolicitarBaja,
                            onSolicitarBaja = { navController.navigate(Routes.CUENTA) },
                            // El grid ya aporta 16 dp arriba (spacedBy) y 12 dp
                            // abajo (contentPadding); se iguala a ~20 dp ambos.
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
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
 * composición vertical (icono arriba en contenedor coloreado y título debajo),
 * sin descripción, proporción compacta y bordes suaves. Es un componente
 * privado del Home para no alterar la MenuCard compartida (usada en otras
 * pantallas como Cuenta).
 */
@Composable
private fun HomeClientMenuCard(
    titulo: String,
    icono: ImageVector? = null,
    color: Color = Color(0xFF1E88E5),
    badge: Int? = null,
    @DrawableRes imagenFondo: Int? = null,
    onClick: () -> Unit
) {
    // Con imagen: la fotografía ocupa toda la tarjeta, sin icono ni color de
    // fondo sólido, con el título en blanco sobre un degradado inferior. Sin
    // imagen: tarjeta compacta horizontal (icono a la izquierda, título a la
    // derecha).
    val conImagen = imagenFondo != null
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (conImagen) 180.dp else 56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (conImagen) Color.Transparent else color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (conImagen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (conImagen) {
                Image(
                    painter = painterResource(id = imagenFondo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay/degradado oscuro en la parte inferior para asegurar
                // la legibilidad del título sin alterar la imagen original.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                // Tarjeta compacta horizontal: icono a la izquierda, título a la
                // derecha, ambos centrados verticalmente.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (icono != null) {
                                Icon(
                                    imageVector = icono,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
 * Indicador visual del estado del cliente en el Home: sin tarjeta ni fondo,
 * solo una pequeña bola de color a la izquierda y el texto de estado junto a la
 * fecha (secundaria). La bola y el texto adoptan el color semántico del estado.
 * El color y el texto dependen del ESTADO (enum), nunca del texto traducido.
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

    // Sin tarjeta ni fondo: solo el círculo indicador y el texto del estado,
    // ambos con el color semántico del estado.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
                if (estado == EstadoVisualCliente.ACTIVO && fecha != null) {
                    // "Activo hasta el [fecha]" en una sola línea, todo en verde.
                    Text(
                        text = stringResource(R.string.home_activo_hasta_fecha, fecha),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                } else if (estado == EstadoVisualCliente.PAGO_VENCIDO && fecha != null) {
                    // "Pago vencido el [fecha]" en una sola línea (misma fecha).
                    Text(
                        text = stringResource(R.string.home_pago_vencido_fecha, fecha),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                } else {
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
