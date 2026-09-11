package com.roberto.gestorpro.ui.gestioncentro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppNavigationBackButton

/**
 * CentroScreen
 * ------------
 * Pantalla "Centro" del ADMIN: agrupa la identidad y la configuración del
 * centro en un único sitio.
 *   - Centro: nombre, logo y código maestro.
 *   - Horario del centro.
 *   - Horario de actividades.
 * Mantiene el estilo visual de Trazys (azul corporativo, cards y divisores
 * suaves). No contiene lógica propia: solo navega a las pantallas existentes.
 */
@Composable
fun CentroScreen(
    navController: NavHostController
) {
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
                    text = stringResource(R.string.centro_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                CentroItem(
                    titulo = stringResource(R.string.centro_titulo),
                    descripcion = stringResource(R.string.centro_item_identidad_desc),
                    icono = Icons.Default.AccountBox,
                    onClick = { navController.navigate(Routes.MINEGOCIO) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CentroItem(
                    titulo = stringResource(R.string.horario_centro_titulo),
                    descripcion = stringResource(R.string.centro_horario_centro_desc),
                    icono = Icons.Default.Schedule,
                    onClick = { navController.navigate(Routes.HORARIO_CENTRO) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CentroItem(
                    titulo = stringResource(R.string.horario_actividades_titulo),
                    descripcion = stringResource(R.string.centro_horario_actividades_desc),
                    icono = Icons.Default.FitnessCenter,
                    onClick = { navController.navigate(Routes.HORARIO_ACTIVIDADES) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * CentroItem
 * ----------
 * Entrada de lista del Centro con el mismo estilo que los ajustes del ADMIN:
 * icono en contenedor con acento azul corporativo, título, descripción y
 * chevron.
 */
@Composable
private fun CentroItem(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    val colorAcento = Color(0xFF1E88E5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorAcento.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = colorAcento,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
