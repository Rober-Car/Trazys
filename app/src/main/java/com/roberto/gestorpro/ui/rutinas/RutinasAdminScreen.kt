package com.roberto.gestorpro.ui.rutinas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.ui.components.AppNavigationBackButton

/**
 * RutinasAdminScreen
 * ------------------
 * Pantalla informativa del ADMIN para la futura sección de Rutinas. De momento
 * no tiene funcionalidad: muestra un aviso de "próximamente" manteniendo el
 * estilo visual de Trazys (azul corporativo, card con borde suave).
 */
@Composable
fun RutinasAdminScreen(
    navController: NavHostController
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.Companion.width(12.dp))
                Text(
                    text = stringResource(R.string.rutinas_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Companion.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Companion.Center
            ) {
                Surface(
                    modifier = Modifier.Companion.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E88E5).copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.Companion.size(56.dp)
                        )
                        Spacer(modifier = Modifier.Companion.size(16.dp))
                        Text(
                            text = stringResource(R.string.rutinas_proximamente),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.Companion.size(8.dp))
                        Text(
                            text = stringResource(R.string.rutinas_mensaje),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Companion.Center
                        )
                    }
                }
            }
        }
    }
}