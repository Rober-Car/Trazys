package com.roberto.gestorpro.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.model.TipoUsuario
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppTextLinkButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun RegistroScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {

    /**
     * autenticando
     * ------------
     * ✔ TIPO: variable observable (val by collectAsStateWithLifecycle) → Boolean
     * Es el estado que indica si la creación de cuenta está en curso.
     * Sirve para desactivar el botón y mostrar carga mientras Firebase responde.
     */
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()

    /**
     * tipoGuardado / mensajeError / scope
     * -----------------------------------
     * ✔ TIPO: variables de estado y CoroutineScope
     * tipoGuardado muestra el perfil elegido en la selección inicial;
     * mensajeError guarda el error devuelto por el repositorio para mostrarlo;
     * scope lanza la corrutina de registro al pulsar el botón.
     */
    var tipoGuardado by rememberSaveable { mutableStateOf<String?>(null) }
    var mensajeError by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tipoGuardado = when (mainViewModel.obtenerTipoUsuario()) {
            TipoUsuario.ADMINISTRADOR -> "Administrador"
            TipoUsuario.CLIENTE -> "Cliente"
        }
    }

    var email by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var contrasenaRepetida by rememberSaveable { mutableStateOf("") }

    var contrasenaVisible by rememberSaveable { mutableStateOf(false) }
    var contrasenaRepetidaVisible by rememberSaveable { mutableStateOf(false) }

    /**
     * aceptaTerminos
     * --------------
     * Checkbox obligatorio: el registro queda bloqueado hasta aceptar los
     * Términos de uso (versión vigente). Nunca se marca automáticamente.
     */
    var aceptaTerminos by rememberSaveable { mutableStateOf(false) }

    val formularioValido = email.isNotBlank() &&
        contrasena.length >= 6 &&
        contrasena == contrasenaRepetida

    val azulPrincipal = Color(0xFF1E88E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "Crear una cuenta",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (tipoGuardado != null) {
                "Perfil: $tipoGuardado"
            } else {
                "Gestión de clientes y cuotas"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = azulPrincipal
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azulPrincipal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = azulPrincipal,
                        cursorColor = azulPrincipal
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text("Contraseña") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = azulPrincipal
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { contrasenaVisible = !contrasenaVisible }
                        ) {
                            Icon(
                                imageVector = if (contrasenaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (contrasenaVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = azulPrincipal
                            )
                        }
                    },
                    visualTransformation = if (contrasenaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azulPrincipal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = azulPrincipal,
                        cursorColor = azulPrincipal
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contrasenaRepetida,
                    onValueChange = { contrasenaRepetida = it },
                    label = { Text("Repetir contraseña") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = azulPrincipal
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { contrasenaRepetidaVisible = !contrasenaRepetidaVisible }
                        ) {
                            Icon(
                                imageVector = if (contrasenaRepetidaVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (contrasenaRepetidaVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = azulPrincipal
                            )
                        }
                    },
                    visualTransformation = if (contrasenaRepetidaVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azulPrincipal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = azulPrincipal,
                        cursorColor = azulPrincipal
                    )
                )

                if (mensajeError.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = mensajeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = aceptaTerminos,
                        onCheckedChange = { aceptaTerminos = it },
                        enabled = !autenticando
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    // Toda la zona de texto comparte la misma columna de inicio:
                    // el texto de aceptación y los dos enlaces empiezan igualados.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "He leído y acepto los Términos de uso",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ver Términos de uso",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { navController.navigate(Routes.TERMINOS_CONDICIONES) }
                        )
                        Text(
                            text = "Ver Política de privacidad",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { navController.navigate(Routes.POLITICA_PRIVACIDAD) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                AppPrimaryButton(
                    text = "Crear cuenta",
                    onClick = {
                        scope.launch {
                            if (!aceptaTerminos) {
                                mensajeError =
                                    "Debes aceptar los Términos de uso para crear la cuenta"
                                return@launch
                            }
                            val error = mainViewModel.registrarse(
                                email.trim(),
                                contrasena,
                                contrasenaRepetida
                            )
                            if (error == null) {
                                mensajeError = ""
                                // Registra la aceptación con el uid y la versión vigente.
                                mainViewModel.aceptarTerminos()
                                val destino = mainViewModel.destinoSegunTipo()
                                navController.navigate(destino) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            } else {
                                mensajeError = error
                            }
                        }
                    },
                    enabled = formularioValido && aceptaTerminos && !autenticando
                )

                if (autenticando) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = azulPrincipal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AppTextLinkButton(
            text = "Ya tengo cuenta. Iniciar sesión",
            onClick = {
                if (!autenticando) navController.popBackStack()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "© 2026 Trazys",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
