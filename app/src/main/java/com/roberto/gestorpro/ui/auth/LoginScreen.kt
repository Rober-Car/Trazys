package com.roberto.gestorpro.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppTextLinkButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * textoErrorLogin
 * ---------------
 * Normaliza el error de credenciales devuelto por la autenticación a un
 * mensaje genérico. El resto de errores (cuenta desactivada, sin conexión,
 * perfil ilegible…) se muestran tal cual.
 */
private fun textoErrorLogin(
    error: String,
    erroresCredenciales: Set<String>,
    mensajeCredencialesIncorrectas: String
): String =
    if (error in erroresCredenciales) {
        mensajeCredencialesIncorrectas
    } else {
        error
    }

@Composable
fun LoginScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {

    /**
     * autenticando
     * ------------
     * ✔ TIPO: variable observable (val by collectAsStateWithLifecycle) → Boolean
     * Es el estado que indica si el inicio de sesión está en curso.
     * Sirve para desactivar el botón y mostrar carga mientras Firebase responde.
     */
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()

    /**
     * mensajeError
     * ------------
     * ✔ TIPO: variable de estado (var by rememberSaveable) → String
     * Guarda el error devuelto por la autenticación real (credenciales,
     * cuenta desactivada, sin conexión...).
     * Sirve para mostrarlo bajo el formulario sin romper la pantalla.
     */
    var mensajeError by rememberSaveable { mutableStateOf("") }

    /**
     * scope
     * -----
     * ✔ TIPO: variable inmutable (val) → CoroutineScope
     * Es el ámbito de corrutinas ligado a la composición de esta pantalla.
     * Sirve para leer el tipo de usuario guardado antes de navegar al Home correcto.
     */
    val scope = rememberCoroutineScope()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var contrasenaVisible by rememberSaveable { mutableStateOf(false) }

    val formularioValido = email.isNotBlank() && password.isNotBlank()
    val azulPrincipal = Color(0xFF1E88E5)

    // Textos localizados del bloque de autenticación.
    val textoMarcaTrazys = stringResource(R.string.app_name)
    val textoSubtituloGestion = stringResource(R.string.auth_login_subtitulo_gestion)
    val textoIniciarSesion = stringResource(R.string.auth_login_titulo)
    val textoEmail = stringResource(R.string.auth_email)
    val textoContrasena = stringResource(R.string.auth_contrasena)
    val textoOcultarContrasena = stringResource(R.string.auth_ocultar_contrasena)
    val textoMostrarContrasena = stringResource(R.string.auth_mostrar_contrasena)
    val textoEntrar = stringResource(R.string.auth_boton_entrar)
    val textoNoTienesCuenta = stringResource(R.string.auth_enlace_no_tienes_cuenta)
    val textoHasOlvidado = stringResource(R.string.auth_enlace_olvidaste_contrasena)
    val textoCopyright = stringResource(R.string.auth_copyright)
    val erroresCredenciales = setOf(
        stringResource(R.string.auth_error_credenciales),
        stringResource(R.string.auth_error_cuenta_no_existe)
    )
    val mensajeCredencialesIncorrectas =
        stringResource(R.string.auth_error_credenciales_generico)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        /**
         * Cabecera de marca
         * -----------------
         * ✔ TIPO: Image de Compose
         * Es el wordmark oficial de Trazys (aplicación), independiente del
         * centro. Debajo se mantiene el subtítulo funcional de la app.
         */
        Image(
            painter = painterResource(R.drawable.trazys_logo),
            contentDescription = textoMarcaTrazys,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .aspectRatio(3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = textoSubtituloGestion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

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
                Text(
                    text = textoIniciarSesion,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = azulPrincipal
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        mensajeError = ""
                    },
                    label = { Text(textoEmail) },
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
                    value = password,
                    onValueChange = {
                        password = it
                        mensajeError = ""
                    },
                    label = { Text(textoContrasena) },
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
                                    textoOcultarContrasena
                                } else {
                                    textoMostrarContrasena
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

                Spacer(modifier = Modifier.height(24.dp))

                AppPrimaryButton(
                    text = textoEntrar,
                    onClick = {
                        mensajeError = ""
                        scope.launch {
                            val error = mainViewModel.iniciarSesion(email.trim(), password)
                            if (error == null) {
                                val destino = mainViewModel.destinoSegunTipo()
                                navController.navigate(destino) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            } else {
                                mensajeError = textoErrorLogin(
                                    error,
                                    erroresCredenciales,
                                    mensajeCredencialesIncorrectas
                                )
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
                    text = textoNoTienesCuenta,
                    onClick = {
                        if (!autenticando) navController.navigate(Routes.REGISTRO)
                    }
                )

                AppTextLinkButton(
                    text = textoHasOlvidado,
                    onClick = {
                        if (!autenticando) navController.navigate(Routes.RECUPERAR_PASSWORD)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = textoCopyright,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
