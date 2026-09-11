package com.roberto.gestorpro.cliente.ui.configuracion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.cliente.util.TerminosDeUso

/**
 * TerminosDeUsoScreen (CLIENTE)
 * -----------------------------
 * Documento real de "Términos y condiciones" de Trazys (sustituye el placeholder).
 * Permite al usuario autenticado aceptar la versión vigente.
 */
@Composable
fun TerminosDeUsoScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    var aceptado by remember { mutableStateOf<Boolean?>(null) }

    val textoTerminosTitulo = stringResource(R.string.terminos_titulo)
    val textoAceptadosEstado =
        stringResource(R.string.terminos_aceptados_estado, TerminosDeUso.VERSION)
    val textoAceptadosInfo = stringResource(R.string.terminos_aceptados_info)
    val textoAccionAceptar = stringResource(R.string.terminos_accion_aceptar)
    val textoNotaAceptacion =
        stringResource(R.string.terminos_nota_aceptacion, TerminosDeUso.VERSION)

    LaunchedEffect(Unit) {
        aceptado = mainViewModel.terminosAceptados()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            AppNavigationBackButton(onClick = { navController.popBackStack() })
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = textoTerminosTitulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.legal_terminos_cuerpo),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (aceptado) {
                // Mientras se comprueba el estado no se muestra acción.
                null -> Unit

                // Ya aceptó la versión vigente: no se vuelve a pedir.
                true -> {
                    Text(
                        text = textoAceptadosEstado,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = textoAceptadosInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // No aceptó la versión vigente (o aceptó una anterior): se ofrece aceptar.
                false -> {
                    AppPrimaryButton(
                        text = textoAccionAceptar,
                        onClick = {
                            mainViewModel.aceptarTerminos()
                            aceptado = true
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = textoNotaAceptacion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
