package com.roberto.gestorpro.cliente.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppSecondaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.AsistentesSesionViewModel

/**
 * AsistentesSesionScreen
 * ----------------------
 * Pantalla independiente con los asistentes CONFIRMADOS de una sesión que el
 * CLIENTE ya tiene reservada. Muestra una lista vertical compacta con scroll:
 * cada asistente ocupa una fila con un icono genérico de persona y su nombre en
 * texto destacado, separadas por divisores sutiles. Solo se muestran nombres
 * (sin apellidos, fotos, teléfonos ni emails). Accesible desde ClasesScreen
 * únicamente en el estado RESERVADA; el ViewModel vuelve a comprobar que la
 * sesión está reservada.
 */
@Composable
fun AsistentesSesionScreen(
    navController: NavHostController,
    idSesion: Int,
    viewModel: AsistentesSesionViewModel = hiltViewModel()
) {
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val asistentes by viewModel.asistentes.collectAsStateWithLifecycle()

    LaunchedEffect(idSesion) {
        viewModel.cargar(idSesion)
    }

    val volver = stringResource(R.string.accion_volver)
    val textoVacio = stringResource(R.string.asistentes_vacio)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.asistentes_titulo),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (asistentes.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.asistentes_total,
                                asistentes.size,
                                asistentes.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when {
                cargando -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    AppSecondaryButton(
                        text = volver,
                        onClick = { navController.popBackStack() },
                        fullWidth = false
                    )
                }

                asistentes.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textoVacio,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            asistentes.forEachIndexed { indice, nombre ->
                                FilaAsistente(nombre = nombre)
                                if (indice < asistentes.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * FilaAsistente
 * -------------
 * Fila de un asistente: icono genérico de persona a la izquierda y el nombre en
 * texto destacado. Altura compacta para que la lista sea densa con scroll.
 */
@Composable
private fun FilaAsistente(nombre: String) {
    val contenidoIcono = stringResource(R.string.asistentes_titulo)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = contenidoIcono,
            tint = colorAsistente(nombre),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = nombre,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Paleta de colores para el icono de cada asistente. El color se deriva de
 * forma determinista del nombre: es estable durante las recomposiciones y al
 * volver a abrir la pantalla (mismo nombre -> mismo color), pero varía entre
 * asistentes para dar un aspecto aleatorio.
 */
private val coloresAsistente = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFF8E24AA),
    Color(0xFFF4511E),
    Color(0xFF00897B),
    Color(0xFFD81B60),
    Color(0xFF6D4C41),
    Color(0xFF3949AB),
    Color(0xFF00838F),
    Color(0xFF7CB342)
)

private fun colorAsistente(nombre: String): Color {
    val tam = coloresAsistente.size
    val indice = ((nombre.hashCode() % tam) + tam) % tam
    return coloresAsistente[indice]
}
