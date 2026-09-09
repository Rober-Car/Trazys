package com.roberto.gestorpro.cliente.ui.auth

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.components.AppTextLinkButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun RegistroScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var contrasenaRepetida by rememberSaveable { mutableStateOf("") }
    var mensajeError by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var contrasenaVisible by rememberSaveable { mutableStateOf(false) }
    var contrasenaRepetidaVisible by rememberSaveable { mutableStateOf(false) }

    /**
     * aceptaTerminos
     * --------------
     * Checkbox obligatorio: el registro queda bloqueado hasta aceptar los
     * Términos de uso (versión vigente). Nunca se marca automáticamente.
     */
    var aceptaTerminos by rememberSaveable { mutableStateOf(false) }

    val azul = Color(0xFF1E88E5)
    val formularioValido = email.isNotBlank() &&
        contrasena.length >= 6 &&
        contrasena == contrasenaRepetida

    // Textos localizados del bloque de autenticación.
    val textoCrearCuenta = stringResource(R.string.auth_registro_titulo)
    val textoPerfilCliente = stringResource(R.string.auth_registro_perfil_cliente)
    val textoEmail = stringResource(R.string.auth_email)
    val textoContrasenaMin = stringResource(R.string.auth_contrasena_min_caracteres)
    val textoRepetirContrasena = stringResource(R.string.auth_repetir_contrasena)
    val textoOcultarContrasena = stringResource(R.string.auth_ocultar_contrasena)
    val textoMostrarContrasena = stringResource(R.string.auth_mostrar_contrasena)
    val textoHeLeidoYAcepto = stringResource(R.string.auth_registro_acepta_terminos)
    val textoVerTerminos = stringResource(R.string.auth_registro_ver_terminos)
    val textoVerPrivacidad = stringResource(R.string.auth_registro_ver_privacidad)
    val textoErrorTerminos = stringResource(R.string.auth_registro_error_terminos)
    val textoBotonCrear = stringResource(R.string.auth_registro_boton)
    val textoYaTengoCuenta = stringResource(R.string.auth_registro_ya_tengo_cuenta)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = textoCrearCuenta,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = textoPerfilCliente,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(textoEmail) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = azul)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azul,
                        focusedLabelColor = azul,
                        cursorColor = azul
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    label = { Text(textoContrasenaMin) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = azul)
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
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
                                },
                                tint = azul
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
                        focusedBorderColor = azul,
                        focusedLabelColor = azul,
                        cursorColor = azul
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contrasenaRepetida,
                    onValueChange = { contrasenaRepetida = it },
                    label = { Text(textoRepetirContrasena) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = azul)
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
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
                                },
                                tint = azul
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
                        focusedBorderColor = azul,
                        focusedLabelColor = azul,
                        cursorColor = azul
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (mensajeError.isNotBlank()) {
                    Text(
                        text = mensajeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                            text = textoHeLeidoYAcepto,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = textoVerTerminos,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { navController.navigate(Routes.TERMINOS_CONDICIONES) }
                        )
                        Text(
                            text = textoVerPrivacidad,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { navController.navigate(Routes.POLITICA_PRIVACIDAD) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AppPrimaryButton(
                    text = textoBotonCrear,
                    onClick = {
                        mensajeError = ""
                        scope.launch {
                            if (!aceptaTerminos) {
                                mensajeError = textoErrorTerminos
                                return@launch
                            }
                            val error = mainViewModel.registrarse(
                                email.trim(), contrasena, contrasenaRepetida
                            )
                            if (error != null) {
                                mensajeError = error
                            } else {
                                // Registra la aceptación con el uid y la versión vigente.
                                mainViewModel.aceptarTerminos()
                                val destino = mainViewModel.destinoTrasAutenticar()
                                navController.navigate(destino) {
                                    popUpTo(Routes.REGISTRO) { inclusive = true }
                                }
                            }
                        }
                    },
                    enabled = !autenticando && formularioValido && aceptaTerminos
                )

                if (autenticando) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                AppTextLinkButton(
                    text = textoYaTengoCuenta,
                    onClick = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.REGISTRO) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
