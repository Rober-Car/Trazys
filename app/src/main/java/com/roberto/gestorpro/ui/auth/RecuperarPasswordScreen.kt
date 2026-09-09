package com.roberto.gestorpro.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppTextLinkButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * RecuperarPasswordScreen
 * -----------------------
 * ✔ TIPO: función @Composable
 * Es la pantalla de recuperación de contraseña accesible desde el Login.
 * Sirve para que el usuario introduzca su email y reciba por Firebase
 * Authentication un correo con el enlace para restablecer su contraseña.
 * El mensaje de éxito es genérico para no revelar si el email está registrado.
 */
@Composable
fun RecuperarPasswordScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {

    /**
     * autenticando
     * ------------
     * ✔ TIPO: variable observable (val by collectAsStateWithLifecycle) → Boolean
     * Es el estado que indica si el envío del correo está en curso.
     * Sirve para desactivar el botón y mostrar carga mientras Firebase responde.
     */
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()

    /**
     * email
     * -----
     * ✔ TIPO: variable de estado (var by rememberSaveable) → String
     * Es el email introducido por el usuario.
     * Sirve para enviarlo a Firebase y validarlo antes de llamar al repositorio.
     */
    var email by rememberSaveable { mutableStateOf("") }

    /**
     * mensajeError
     * ------------
     * ✔ TIPO: variable de estado (var by rememberSaveable) → String
     * Es el error devuelto por la validación o por el repositorio.
     * Sirve para mostrarlo bajo el formulario en color de error.
     */
    var mensajeError by rememberSaveable { mutableStateOf("") }

    /**
     * correoEnviado
     * -------------
     * ✔ TIPO: variable de estado (var by rememberSaveable) → Boolean
     * Indica si el correo de recuperación se envió correctamente.
     * Sirve para mostrar el mensaje genérico de éxito en lugar del formulario.
     */
    var correoEnviado by rememberSaveable { mutableStateOf(false) }

    /**
     * scope
     * -----
     * ✔ TIPO: variable inmutable (val) → CoroutineScope
     * Es el ámbito de corrutinas ligado a la composición de esta pantalla.
     * Sirve para lanzar el envío del correo sin bloquear la interfaz.
     */
    val scope = rememberCoroutineScope()

    val formularioValido = email.isNotBlank()
    val azulPrincipal = Color(0xFF1E88E5)

    // Textos localizados del bloque de autenticación.
    val textoRecuperarTitulo = stringResource(R.string.auth_recuperar_titulo)
    val textoHasOlvidado = stringResource(R.string.auth_enlace_olvidaste_contrasena)
    val textoInstrucciones = stringResource(R.string.auth_recuperar_instrucciones)
    val textoEmail = stringResource(R.string.auth_email)
    val textoEnviarCorreo = stringResource(R.string.auth_recuperar_enviar)
    val textoExito = stringResource(R.string.auth_recuperar_exito)
    val textoVolver = stringResource(R.string.auth_recuperar_volver)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppNavigationBackButton(
                onClick = {
                    if (!autenticando) navController.popBackStack()
                },
                tint = azulPrincipal
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = textoRecuperarTitulo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = azulPrincipal
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = azulPrincipal,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = textoHasOlvidado,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = textoInstrucciones,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

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
                if (correoEnviado) {
                    /**
                     * Mensaje genérico de éxito
                     * --------------------------
                     * ✔ TIPO: bloque condicional (if) con Composables
                     * Se muestra tras enviar el correo. Es genérico a propósito:
                     * Firebase no debe revelar si el email está registrado.
                     */
                    Text(
                        text = textoExito,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF43A047),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextLinkButton(
                        text = textoVolver,
                        onClick = {
                            if (!autenticando) navController.popBackStack()
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(textoEmail) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
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

                    Spacer(modifier = Modifier.height(24.dp))

                    AppPrimaryButton(
                        text = textoEnviarCorreo,
                        onClick = {
                            scope.launch {
                                val error = mainViewModel.enviarCorreoRecuperacion(email.trim())
                                if (error == null) {
                                    mensajeError = ""
                                    correoEnviado = true
                                } else {
                                    mensajeError = error
                                }
                            }
                        },
                        enabled = formularioValido && !autenticando
                    )

                    if (autenticando) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = azulPrincipal
                        )
                    }

                    if (mensajeError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = mensajeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextLinkButton(
                        text = textoVolver,
                        onClick = {
                            if (!autenticando) navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
