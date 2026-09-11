package com.roberto.gestorpro.cliente.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton

/**
 * PoliticaPrivacidadScreen
 * ------------------------
 * Política de privacidad de Trazys (app Cliente) con scroll vertical.
 * El texto describe los tratamientos REALES implementados en la aplicación;
 * no menciona funcionalidades futuras no operativas (p. ej. Firebase Storage).
 */
@Composable
fun PoliticaPrivacidadScreen(
    navController: NavHostController
) {
    val textoPrivacidad = stringResource(R.string.privacidad_titulo)
    val textoPrivacidadDocumento = stringResource(R.string.privacidad_titulo_documento)

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
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = textoPrivacidad,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = textoPrivacidadDocumento,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion1_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion1_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion2_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion2_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion3_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion3_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion4_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion4_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion5_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion5_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion6_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion6_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion7_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion7_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion8_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion8_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion9_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion9_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion10_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion10_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion11_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion11_cuerpo))
                }

                SeccionPrivacidad(stringResource(R.string.legal_privacidad_seccion12_titulo)) {
                    Text(stringResource(R.string.legal_privacidad_seccion12_cuerpo))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * SeccionPrivacidad
 * -----------------
 * Título de sección + bloque de texto de la política.
 */
@Composable
private fun SeccionPrivacidad(
    titulo: String,
    contenido: @Composable () -> Unit
) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        contenido()
    }
}
