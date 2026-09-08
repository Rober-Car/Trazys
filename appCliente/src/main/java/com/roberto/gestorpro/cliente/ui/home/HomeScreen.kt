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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.model.EstadoIndicadorCliente
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.components.DialogoDenuncia
import com.roberto.gestorpro.cliente.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel
) {
    val idCliente by mainViewModel.idCliente.collectAsStateWithLifecycle()
    val nombreNegocio by mainViewModel.nombreNegocio.collectAsStateWithLifecycle()
    val logoNegocio by mainViewModel.logoNegocio.collectAsStateWithLifecycle()
    val estadoHome by mainViewModel.estadoHome.collectAsStateWithLifecycle()
    val vinculado = idCliente != null

    var menuCentroAbierto by remember { mutableStateOf(false) }
    var mostrarDenunciaLogo by remember { mutableStateOf(false) }

    if (mostrarDenunciaLogo) {
        DialogoDenuncia(
            titulo = "Denunciar contenido del centro",
            onDismiss = { mostrarDenunciaLogo = false },
            onEnviar = { motivo, descripcion ->
                mainViewModel.denunciarLogoNegocio(motivo, descripcion)
            }
        )
    }

    LifecycleResumeEffect(idCliente) {
        if (idCliente != null) {
            mainViewModel.refrescarEstadoHome()
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
                                    contentDescription = "Logo del centro",
                                    tamano = 48.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                AsyncImage(
                                    model = logoNegocio,
                                    contentDescription = "Logo del centro",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }

                        Text(
                            text = nombreNegocio.ifBlank { "Tu centro" },
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
                                    contentDescription = "Más opciones del centro",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuCentroAbierto,
                                onDismissRequest = { menuCentroAbierto = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Denunciar el centro") },
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
                        text = "Trazys Cliente",
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
                            text = "No estás vinculado.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Vincula tu cuenta para acceder a las funciones de tu centro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AppPrimaryButton(
                            text = "Vincular centro",
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
                            titulo = "Actividades",
                            descripcion = "Consulta y reserva tus actividades",
                            icono = Icons.Default.FitnessCenter,
                            color = Color(0xFFFB8C00),
                            onClick = { navController.navigate(Routes.CLASES) }
                        )
                    }
                }
                item {
                    HomeClientMenuCard(
                        titulo = "Notificaciones",
                        descripcion = "Consulta tus avisos",
                        icono = Icons.Default.Notifications,
                        color = Color(0xFF7E57C2),
                        onClick = { navController.navigate(Routes.NOTIFICACIONES) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = "Ajustes",
                        descripcion = "Configuración general",
                        icono = Icons.Default.Settings,
                        color = Color(0xFF78909C),
                        onClick = { navController.navigate(Routes.CONFIGURACION) }
                    )
                }
                item {
                    HomeClientMenuCard(
                        titulo = "Rutinas",
                        descripcion = "Rutinas de entrenamiento",
                        icono = Icons.Default.FitnessCenter,
                        color = Color(0xFF26A69A),
                        onClick = { navController.navigate(Routes.RUTINAS) }
                    )
                }
            }

            // Aviso de morosidad: solo cuando el cliente sigue ACTIVO pero su
            // período está vencido (PAGO_VENCIDO). Debajo de todas las cards,
            // con poco espacio superior y un margen inferior razonable.
            if (vinculado && estadoHome.estado == EstadoIndicadorCliente.PAGO_VENCIDO) {
                AvisoMorosidad(
                    onRenovar = { navController.navigate(Routes.CUENTA) },
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 16.dp
                    )
                )
            }
        }
    }
}

/**
 * AvisoMorosidad
 * --------------
 * Aviso de texto integrado en el Home cuando el cliente sigue ACTIVO pero con
 * el período vencido (PAGO_VENCIDO). Es una advertencia contextual sin fondo
 * rojo: todo el texto va en el color de error del tema y la palabra "aquí" se
 * distingue como enlace (color primario, negrita y subrayada). Solo "aquí" es
 * clicable y navega a la cuenta del cliente para renovar o solicitar la baja.
 */
@Composable
private fun AvisoMorosidad(
    onRenovar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texto = "Renueva la mensualidad o solicita la baja aquí"
    val etiqueta = "aquí"
    val indice = texto.indexOf(etiqueta)
    val enlaceColor = MaterialTheme.colorScheme.primary

    val annotated = buildAnnotatedString {
        append(texto)
        if (indice >= 0) {
            addStyle(
                SpanStyle(
                    color = enlaceColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                ),
                indice,
                indice + etiqueta.length
            )
        }
    }

    ClickableText(
        text = annotated,
        onClick = { offset ->
            if (indice >= 0 && offset in indice until indice + etiqueta.length) {
                onRenovar()
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        ),
        modifier = modifier.fillMaxWidth()
    )
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
    }
}

/**
 * EstadoVisualCliente
 * -------------------
 * Modelo visual (sin datos) de los tres estados que puede mostrar el indicador
 * de estado del cliente en el Home de GestorPro Cliente. Solo se usa para la
 * presentación; la conexión con Firestore corresponde a la Fase 2.
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
 * adoptan el color semántico. Queda preparado para recibir estado y fecha reales
 * en la Fase 2 sin alterar su presentación.
 */
@Composable
private fun HomeClientEstadoIndicator(
    estado: EstadoVisualCliente,
    fecha: String?,
    modifier: Modifier = Modifier
) {
    val (color, titulo, prefijo) = when (estado) {
        EstadoVisualCliente.ACTIVO ->
            Triple(Color(0xFF43A047), "Activo", "Hasta el")
        EstadoVisualCliente.PAGO_VENCIDO ->
            Triple(Color(0xFFE53935), "Pago vencido", "Venció el")
        EstadoVisualCliente.BAJA ->
            Triple(Color(0xFF78909C), "Baja", "Desde el")
        EstadoVisualCliente.REGISTRADO ->
            Triple(Color(0xFF64B5F6), "Registrado", null)
        EstadoVisualCliente.ARCHIVADO ->
            Triple(Color(0xFF78909C), "Archivado", null)
    }

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
