package com.roberto.gestorpro.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.firebase.Denuncia
import com.roberto.gestorpro.data.firebase.DenunciaRepository
import com.roberto.gestorpro.data.firebase.EstadosDenuncia
import com.roberto.gestorpro.data.firebase.MotivosDenuncia
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.viewmodel.DenunciasViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * GestionDenunciasScreen (ADMIN)
 * ------------------------------
 * Pantalla sencilla de moderación de denuncias UGC de su negocio. Lista las
 * denuncias (fecha, motivo, tipo, contenido, usuario denunciado y estado) y
 * permite marcar como REVISADA las pendientes. El CLIENTE no accede aquí.
 */
@Composable
fun GestionDenunciasScreen(
    navController: NavHostController,
    viewModel: DenunciasViewModel = hiltViewModel()
) {
    val denuncias by viewModel.denuncias.collectAsStateWithLifecycle()
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.cargarDenuncias()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
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
                    text = "Denuncias",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (cargando) {
                    Text(
                        text = "Cargando denuncias...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                error?.let { mensaje ->
                    Text(
                        text = mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                if (!cargando && denuncias.isEmpty() && error == null) {
                    Text(
                        text = "No hay denuncias de tu centro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                denuncias.forEach { denuncia ->
                    ItemDenuncia(
                        denuncia = denuncia,
                        onMarcarRevisada = { viewModel.marcarRevisada(denuncia.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ItemDenuncia(
    denuncia: Denuncia,
    onMarcarRevisada: () -> Unit
) {
    val esPendiente = denuncia.estado == EstadosDenuncia.PENDIENTE
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = MotivosDenuncia.etiqueta(denuncia.motivo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DenunciaRepository.etiquetaTipo(denuncia.tipo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (esPendiente) "PENDIENTE" else "REVISADA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (esPendiente) Color(0xFFE65100) else Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Contenido: ${denuncia.referencia}",
                style = MaterialTheme.typography.bodySmall
            )
            denuncia.usuarioDenunciadoUid?.let { uid ->
                Text(
                    text = "Usuario denunciado: $uid",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            denuncia.descripcion?.takeIf { it.isNotBlank() }?.let { descripcion ->
                Text(
                    text = "Descripción: $descripcion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Fecha: ${formatoFecha(denuncia.fecha)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (esPendiente) {
                Spacer(modifier = Modifier.height(8.dp))
                AppSecondaryButton(
                    text = "Marcar como revisada",
                    onClick = onMarcarRevisada,
                    fullWidth = false
                )
            }
        }
    }
}

private fun formatoFecha(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
