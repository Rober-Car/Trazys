package com.roberto.gestorpro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.R
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.ui.components.MenuCard
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
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (logoNegocio.isNotBlank()) {
                    if (logoNegocio.startsWith("http")) {
                        LogoNegocioAutenticado(
                            url = logoNegocio,
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            tamano = 44.dp
                        )
                    } else {
                        AsyncImage(
                            model = File(logoNegocio),
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                        text = stringResource(R.string.home_panel_principal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nombreNegocio.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = stringResource(R.string.home_accesos_rapidos),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    MenuCard(
                        titulo = stringResource(R.string.centro_titulo),
                        descripcion = stringResource(R.string.home_centro_desc),
                        icono = Icons.Default.Storefront,
                        containerColor = Color(0xFF1E88E5).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF1E88E5),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.CENTRO) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_clientes_titulo),
                        descripcion = stringResource(R.string.home_clientes_desc),
                        icono = Icons.Default.Person,
                        containerColor = Color(0xFF2196F3).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF2196F3),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.CLIENTES) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_actividades_titulo),
                        descripcion = stringResource(R.string.home_actividades_desc),
                        icono = Icons.Default.FitnessCenter,
                        containerColor = Color(0xFF43A047).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF43A047),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.SERVICIOS) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.rutinas_titulo),
                        descripcion = stringResource(R.string.rutinas_proximamente),
                        icono = Icons.Default.FitnessCenter,
                        containerColor = Color(0xFF1E88E5).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF1E88E5),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.RUTINAS_ADMIN) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_economia_titulo),
                        descripcion = stringResource(R.string.home_economia_desc),
                        icono = Icons.Default.AccountBalance,
                        containerColor = Color(0xFFFB8C00).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFFFB8C00),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.ECONOMIA) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_ajustes_titulo),
                        descripcion = stringResource(R.string.home_ajustes_desc),
                        icono = Icons.Default.Settings,
                        containerColor = Color(0xFF78909C).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF78909C),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.CONFIGURACION) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_notificaciones_titulo),
                        descripcion = stringResource(R.string.home_notificaciones_desc),
                        icono = Icons.Default.Notifications,
                        containerColor = Color(0xFFE91E63).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFFE91E63),
                        iconTint = Color.White,
                        onClick = { navController.navigate(Routes.NOTIFICACIONES) }
                    )
                }
                item {
                    MenuCard(
                        titulo = stringResource(R.string.home_solicitudes_titulo),
                        descripcion = stringResource(R.string.home_solicitudes_desc),
                        icono = Icons.Default.Email,
                        containerColor = Color(0xFF00ACC1).copy(alpha = 0.12f),
                        iconContainerColor = Color(0xFF00ACC1),
                        iconTint = Color.White,
                        badge = solicitudesPendientes,
                        onClick = { navController.navigate(Routes.SOLICITUDES) }
                    )
                }
            }
        }
    }
}
