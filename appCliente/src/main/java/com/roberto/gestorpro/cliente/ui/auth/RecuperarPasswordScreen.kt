package com.roberto.gestorpro.cliente.ui.auth

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun RecuperarPasswordScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var mensaje by rememberSaveable { mutableStateOf("") }
    var esError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Textos localizados del bloque de autenticación.
    val textoRecuperarTitulo = stringResource(R.string.auth_recuperar_titulo)
    val textoInstrucciones = stringResource(R.string.auth_recuperar_instrucciones)
    val textoEmail = stringResource(R.string.auth_email)
    val textoExito = stringResource(R.string.auth_recuperar_exito)
    val textoEnviarCorreo = stringResource(R.string.auth_recuperar_enviar)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppNavigationBackButton(onClick = { navController.popBackStack() })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = textoRecuperarTitulo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = textoInstrucciones,
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    mensaje = ""
                },
                label = { Text(textoEmail) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mensaje.isNotBlank()) {
                Text(
                    text = mensaje,
                    color = if (esError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AppPrimaryButton(
                text = textoEnviarCorreo,
                onClick = {
                    scope.launch {
                        mensaje = ""
                        val error = mainViewModel.enviarCorreoRecuperacion(email.trim())
                        if (error == null) {
                            esError = false
                            mensaje = textoExito
                        } else {
                            esError = true
                            mensaje = error
                        }
                    }
                },
                enabled = !autenticando && email.isNotBlank()
            )

            if (autenticando) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}
