package com.roberto.gestorpro.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.R
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.ui.viewmodel.SolicitudesViewModel
import java.io.File

@Composable
fun HomeScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel(),
    solicitudesViewModel: SolicitudesViewModel = hiltViewModel()
) {
    val nombreNegocio by mainViewModel.nombreNegocio.collectAsStateWithLifecycle()
    val logoNegocio by mainViewModel.logoNegocio.collectAsStateWithLifecycle()
    val solicitudesPendientes by solicitudesViewModel.solicitudesPendientes.collectAsStateWithLifecycle()

    // Refresca el badge de solicitudes cada vez que la Home vuelve a mostrarse
    // (p. ej. al regresar tras aceptar/rechazar solicitudes).
    LaunchedEffect(Unit) {
        solicitudesViewModel.cargarPendientes()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // El inset inferior se conserva; el superior pasa al interior
                // del encabezado para que el azul llegue hasta el borde.
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E88E5))
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (logoNegocio.isNotBlank()) {
                    if (logoNegocio.startsWith("http")) {
                        LogoNegocioAutenticado(
                            url = logoNegocio,
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            tamano = 50.dp
                        )
                    } else {
                        AsyncImage(
                            model = File(logoNegocio),
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nombreNegocio.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Primeros 6: cards grandes con imagen de fondo (mismo lenguaje
                // visual que el Home CLIENTE).
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.centro_titulo),
                        imagenFondo = R.drawable.img_centro,
                        onClick = { navController.navigate(Routes.CENTRO) }
                    )
                }
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.home_clientes_titulo),
                        imagenFondo = R.drawable.img_clinetes,
                        onClick = { navController.navigate(Routes.CLIENTES) }
                    )
                }
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.home_actividades_titulo),
                        imagenFondo = R.drawable.img_actividades_admind,
                        onClick = { navController.navigate(Routes.SERVICIOS) }
                    )
                }
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.rutinas_titulo),
                        imagenFondo = R.drawable.img_rutinas_admind,
                        onClick = { navController.navigate(Routes.RUTINAS_ADMIN) }
                    )
                }
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.home_economia_titulo),
                        imagenFondo = R.drawable.img_econimia,
                        onClick = { navController.navigate(Routes.ECONOMIA) }
                    )
                }
                item {
                    HomeAdminImageCard(
                        titulo = stringResource(R.string.home_solicitudes_titulo),
                        imagenFondo = R.drawable.img_solicitudes_baja,
                        badge = solicitudesPendientes,
                        onClick = { navController.navigate(Routes.SOLICITUDES) }
                    )
                }
                // Últimos 2: cards compactos azules con icono (como el Home
                // CLIENTE).
                item {
                    HomeAdminCompactCard(
                        titulo = stringResource(R.string.home_notificaciones_titulo),
                        icono = Icons.Default.Notifications,
                        onClick = { navController.navigate(Routes.NOTIFICACIONES) }
                    )
                }
                item {
                    HomeAdminCompactCard(
                        titulo = stringResource(R.string.home_ajustes_titulo),
                        icono = Icons.Default.Settings,
                        onClick = { navController.navigate(Routes.CONFIGURACION) }
                    )
                }
            }
        }
    }
}

/**
 * HomeAdminImageCard
 * ------------------
 * Card de acceso del Home ADMIN con IMAGEN de fondo: mismo lenguaje visual que
 * los cards con imagen del Home CLIENTE (imagen a pantalla completa, degradado
 * inferior y título blanco abajo a la izquierda, sin icono). Conserva el badge
 * opcional (p. ej. solicitudes pendientes).
 */
@Composable
private fun HomeAdminImageCard(
    titulo: String,
    @DrawableRes imagenFondo: Int,
    badge: Int? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            // Altura de los 6 cards con imagen: subida moderada (150 -> 175 dp)
            // para aprovechar el espacio vertical libre sin ser exagerados.
            .height(175.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = imagenFondo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Degradado inferior para asegurar la legibilidad del título.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
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
            if (badge != null && badge > 0) {
                HomeAdminBadge(
                    cantidad = badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                )
            }
        }
    }
}

/**
 * HomeAdminCompactCard
 * --------------------
 * Card compacto horizontal del Home ADMIN (Notificaciones / Ajustes): fondo azul
 * corporativo, icono blanco a la izquierda y título blanco a la derecha. Mismo
 * estilo que los cards inferiores del Home CLIENTE.
 */
@Composable
private fun HomeAdminCompactCard(
    titulo: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
                    Icon(
                        imageVector = icono,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
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
}

/**
 * HomeAdminBadge
 * --------------
 * Badge numérico rojo de la esquina superior derecha (p. ej. solicitudes
 * pendientes), coherente con el badge del Home CLIENTE.
 */
@Composable
private fun HomeAdminBadge(cantidad: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color = Color(0xFFE53935), shape = CircleShape)
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
