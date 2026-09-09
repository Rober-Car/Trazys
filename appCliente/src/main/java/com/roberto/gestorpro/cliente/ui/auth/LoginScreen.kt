package com.roberto.gestorpro.cliente.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.components.AppTextLinkButton
import com.roberto.gestorpro.cliente.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
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
    val autenticando by mainViewModel.autenticando.collectAsStateWithLifecycle()
    val nombreNegocio by mainViewModel.nombreNegocio.collectAsStateWithLifecycle()
    val logoNegocio by mainViewModel.logoNegocio.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var mensajeError by rememberSaveable { mutableStateOf("") }
    var contrasenaVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val azul = Color(0xFF1E88E5)

    // Textos localizados del bloque de autenticación.
    val textoLogoCentro = stringResource(R.string.auth_logo_desc)
    val textoNombreApp = stringResource(R.string.app_name)
    val textoTuCentro = stringResource(R.string.auth_login_subtitulo_centro)
    val textoIniciarSesion = stringResource(R.string.auth_login_titulo)
    val textoEmail = stringResource(R.string.auth_email)
    val textoContrasena = stringResource(R.string.auth_contrasena)
    val textoOcultarContrasena = stringResource(R.string.auth_ocultar_contrasena)
    val textoMostrarContrasena = stringResource(R.string.auth_mostrar_contrasena)
    val textoEntrar = stringResource(R.string.auth_boton_entrar)
    val textoNoTienesCuenta = stringResource(R.string.auth_enlace_no_tienes_cuenta)
    val textoHasOlvidado = stringResource(R.string.auth_enlace_olvidaste_contrasena)
    val textoPoliticaPrivacidad = stringResource(R.string.auth_enlace_politica_privacidad)
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
         * Logo del centro o icono de cabecera
         * -----------------------------------
         * Misma presentación que el Login de ADMIN: si hay logo configurado
         * (URL remota o ruta local) se muestra circular; si no, el icono.
         */
        if (logoNegocio.isNotBlank()) {
            if (logoNegocio.startsWith("http")) {
                LogoNegocioAutenticado(
                    url = logoNegocio,
                    contentDescription = textoLogoCentro,
                    tamano = 80.dp
                )
            } else {
                AsyncImage(
                    model = logoNegocio,
                    contentDescription = textoLogoCentro,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = azul,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = textoNombreApp,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = nombreNegocio.ifBlank { textoTuCentro },
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
                    color = azul
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
                        Icon(Icons.Default.Person, contentDescription = null, tint = azul)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = azul,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = azul,
                        cursorColor = azul
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                        mensajeError = ""
                    },
                    label = { Text(textoContrasena) },
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = azul,
                        cursorColor = azul
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                AppPrimaryButton(
                    text = textoEntrar,
                    onClick = {
                        mensajeError = ""
                        scope.launch {
                            val error = mainViewModel.iniciarSesion(email.trim(), contrasena)
                            if (error != null) {
                                mensajeError = textoErrorLogin(
                                    error,
                                    erroresCredenciales,
                                    mensajeCredencialesIncorrectas
                                )
                            } else {
                                val destino = mainViewModel.destinoTrasAutenticar()
                                navController.navigate(destino) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            }
                        }
                    },
                    enabled = !autenticando && email.isNotBlank() && contrasena.isNotBlank()
                )

                if (autenticando) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = azul
                    )
                }

                if (mensajeError.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = mensajeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AppTextLinkButton(
                    text = textoNoTienesCuenta,
                    onClick = {
                        if (!autenticando) {
                            navController.navigate(Routes.REGISTRO) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    }
                )

                AppTextLinkButton(
                    text = textoHasOlvidado,
                    onClick = {
                        if (!autenticando) navController.navigate(Routes.RECUPERAR_PASSWORD)
                    }
                )

                AppTextLinkButton(
                    text = textoPoliticaPrivacidad,
                    onClick = {
                        if (!autenticando) navController.navigate(Routes.POLITICA_PRIVACIDAD)
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
