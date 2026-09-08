package com.roberto.gestorpro.ui.configuracion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.data.firebase.validarCambioContrasena
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppDialogConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun CuentaScreen(
    navController: NavHostController
) {

    /**
     * mainViewModel
     * -------------
     * ✔ TIPO: variable inmutable (val) → MainViewModel
     * Es el ViewModel de preferencias de la app.
     * Sirve para restablecer el tipo de usuario guardado en DataStore.
     */
    val mainViewModel: MainViewModel = hiltViewModel()

    var mostrarDialogoContrasena by remember { mutableStateOf(false) }
    var mostrarDialogoCerrarSesion by remember { mutableStateOf(false) }
    var contrasenaCambiadaExito by remember { mutableStateOf(false) }

    val cambiandoContrasena by mainViewModel.cambiandoContrasena.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
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
                    text = "Cuenta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Seguridad y sesión",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            CuentaItem(
                titulo = "Cambiar contraseña",
                descripcion = "Actualizar contraseña de acceso",
                icono = Icons.Default.Lock,
                onClick = {
                    mostrarDialogoContrasena = true
                    contrasenaCambiadaExito = false
                }
            )

            CuentaItem(
                titulo = "Cerrar sesión",
                descripcion = "Salir de la aplicación",
                icono = Icons.Default.ExitToApp,
                onClick = { mostrarDialogoCerrarSesion = true }
            )

            if (contrasenaCambiadaExito) {
                Text(
                    text = "Contraseña actualizada correctamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (mostrarDialogoContrasena) {
        DialogoCambiarContrasena(
            cambiando = cambiandoContrasena,
            onDismiss = { mostrarDialogoContrasena = false },
            onConfirmar = { actual, nueva, repetida ->
                mainViewModel.cambiarContrasena(actual, nueva, repetida)
            },
            onExito = {
                mostrarDialogoContrasena = false
                contrasenaCambiadaExito = true
            }
        )
    }

    if (mostrarDialogoCerrarSesion) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoCerrarSesion = false },
            title = {
                Text(
                    text = "Cerrar sesión",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text("¿Seguro que quieres cerrar sesión?")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "Cerrar sesión",
                    onClick = {
                        mostrarDialogoCerrarSesion = false
                        mainViewModel.cerrarSesion()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { mostrarDialogoCerrarSesion = false }
                )
            }
        )
    }
}

@Composable
private fun DialogoCambiarContrasena(
    cambiando: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: suspend (actual: String, nueva: String, repetida: String) -> String?,
    onExito: () -> Unit
) {
    var contrasenaActual by remember { mutableStateOf("") }
    var nuevaContrasena by remember { mutableStateOf("") }
    var repetirContrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var contrasenaActualVisible by rememberSaveable { mutableStateOf(false) }
    var nuevaContrasenaVisible by rememberSaveable { mutableStateOf(false) }
    var repetirContrasenaVisible by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val camposValidos = contrasenaActual.isNotBlank() &&
        nuevaContrasena.isNotBlank() &&
        nuevaContrasena == repetirContrasena

    Dialog(
        onDismissRequest = {
            if (!cambiando) onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cambiar contraseña",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                if (cambiando) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF1E88E5),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cambiando la contraseña...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = contrasenaActual,
                    onValueChange = {
                        contrasenaActual = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text("Contraseña actual") },
                    trailingIcon = {
                        IconButton(
                            onClick = { contrasenaActualVisible = !contrasenaActualVisible }
                        ) {
                            Icon(
                                imageVector = if (contrasenaActualVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (contrasenaActualVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (contrasenaActualVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nuevaContrasena,
                    onValueChange = {
                        nuevaContrasena = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text("Nueva contraseña") },
                    trailingIcon = {
                        IconButton(
                            onClick = { nuevaContrasenaVisible = !nuevaContrasenaVisible }
                        ) {
                            Icon(
                                imageVector = if (nuevaContrasenaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (nuevaContrasenaVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (nuevaContrasenaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repetirContrasena,
                    onValueChange = {
                        repetirContrasena = it
                        error = null
                    },
                    enabled = !cambiando,
                    label = { Text("Repetir contraseña") },
                    trailingIcon = {
                        IconButton(
                            onClick = { repetirContrasenaVisible = !repetirContrasenaVisible }
                        ) {
                            Icon(
                                imageVector = if (repetirContrasenaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (repetirContrasenaVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = Color(0xFF1E88E5)
                            )
                        }
                    },
                    visualTransformation = if (repetirContrasenaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let { mensajeError ->
                    Text(
                        text = mensajeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppDialogTextButton(
                        text = "Cancelar",
                        enabled = !cambiando,
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppPrimaryButton(
                        text = "Guardar",
                        onClick = {
                            error = validarCambioContrasena(
                                contrasenaActual,
                                nuevaContrasena,
                                repetirContrasena
                            )
                            if (error == null) {
                                coroutineScope.launch {
                                    val resultado = onConfirmar(
                                        contrasenaActual,
                                        nuevaContrasena,
                                        repetirContrasena
                                    )
                                    if (resultado == null) {
                                        onExito()
                                    } else {
                                        error = resultado
                                    }
                                }
                            }
                        },
                        enabled = camposValidos && !cambiando,
                        fullWidth = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CuentaItem(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
