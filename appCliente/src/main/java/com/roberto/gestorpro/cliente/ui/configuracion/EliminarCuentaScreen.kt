package com.roberto.gestorpro.cliente.ui.configuracion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel

/**
 * EliminarCuentaScreen (CLIENTE)
 * ------------------------------
 * Elimina PERMANENTEMENTE la cuenta del CLIENTE (Firebase Auth y todos sus
 * datos asociados) mediante la Cloud Function `eliminarMiCuenta`. Irreversible.
 */
@Composable
fun EliminarCuentaScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    var mostrarConfirmacion by remember { mutableStateOf(true) }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val eliminando by mainViewModel.eliminandoCuenta.collectAsStateWithLifecycle()

    if (mostrarConfirmacion) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Eliminar mi cuenta") },
            text = {
                Text(
                    "Se eliminará PERMANENTEMENTE tu cuenta de Firebase y tus " +
                        "datos asociados. Esta acción es irreversible. " +
                        "Introduce tu contraseña para continuar."
                )
            },
            confirmButton = {
                TextButton(onClick = { mostrarConfirmacion = false }) {
                    Text("Continuar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppNavigationBackButton(onClick = { navController.popBackStack() })
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Eliminación permanente",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Se borrará tu cuenta de Firebase, tu perfil y tus datos " +
                    "asociados. No podrás recuperarlos.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña actual") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !eliminando,
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPrimaryButton(
                text = if (eliminando) "Eliminando…" else "Eliminar mi cuenta",
                enabled = !eliminando && contrasena.isNotBlank(),
                onClick = {
                    error = null
                    mainViewModel.eliminarMiCuenta(contrasena) { resultado ->
                        if (resultado != null) {
                            error = resultado
                        } else {
                            // Éxito: sesión cerrada -> volver a Login limpiando el stack.
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }
    }
}
