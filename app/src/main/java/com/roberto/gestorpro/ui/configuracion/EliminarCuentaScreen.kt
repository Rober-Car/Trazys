package com.roberto.gestorpro.ui.configuracion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel

/**
 * EliminarCuentaScreen (ADMIN)
 * ----------------------------
 * Permite eliminar PERMANENTEMENTE la cuenta ADMIN y todo su negocio mediante
 * la Cloud Function `eliminarMiCuenta`. Es irreversible.
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
            title = { Text("Eliminar cuenta y negocio") },
            text = {
                Text(
                    "Se eliminará PERMANENTEMENTE tu cuenta, tu negocio, tus " +
                        "clientes, servicios, sesiones, reservas, economía, " +
                        "notificaciones y el logo. Esta acción es irreversible. " +
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Se borrará tu cuenta de Firebase y todo tu negocio de " +
                    "Trazys (clientes, datos económicos, notificaciones, logo, " +
                    "etc.). No podrás recuperar esta información.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña actual") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
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
                text = if (eliminando) "Eliminando…" else "Eliminar cuenta y negocio",
                enabled = !eliminando && contrasena.isNotBlank(),
                onClick = {
                    error = null
                    mainViewModel.eliminarCuentaYNegocio(contrasena) { resultado ->
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
