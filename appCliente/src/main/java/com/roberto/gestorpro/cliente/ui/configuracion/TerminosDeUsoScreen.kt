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
                text = terminosDeUsoTexto(),
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

private fun terminosDeUsoTexto(): String =
    "1. Objeto\n" +
        "Trazys es una aplicación de gestión de gimnasios formada por una app de administración " +
        "(ADMIN) y una app para clientes (CLIENTE). Estos términos regulan tu uso de Trazys.\n\n" +
        "Responsable: Roberto Carlos Salvador Martin. NIF: 48910659D. " +
        "Domicilio: Avenida de Huelva, 6, 21830 Bonares (Huelva), España. " +
        "Contacto: Pollinox@hotmail.com.\n\n" +
        "2. Cuentas\n" +
        "Eres responsable de mantener seguras tus credenciales y de que los datos que introduces " +
        "sean correctos.\n\n" +
        "3. Contenido que puedes aportar\n" +
        "Como CLIENTE puedes aportar tus datos personales, tu fotografía de perfil y tu relación " +
        "con el centro. Tu contenido solo es accesible por tu centro y por ti; no se publica de " +
        "forma abierta.\n\n" +
        "4. Contenido prohibido\n" +
        "Queda prohibido introducir contenido ilegal, ofensivo, discriminatorio, de acoso, que " +
        "infrinja derechos de terceros o que no corresponda a la finalidad de Trazys.\n\n" +
        "5. Fotografías y datos\n" +
        "Tu fotografía se almacena de forma segura en Firebase Storage y solo es visible para tu " +
        "centro. Puedes corregir o retirar tus datos personales según lo indicado en la política " +
        "de privacidad.\n\n" +
        "6. Retirada de contenido y denuncias\n" +
        "Puedes solicitar la retirada de contenido o informar de un uso indebido escribiendo al " +
        "contacto indicado. Las solicitudes se atienden manualmente.\n\n" +
        "7. Uso indebido e incumplimiento\n" +
        "El uso indebido puede conllevar la retirada de contenido, la suspensión o la eliminación " +
        "de la cuenta, además de las consecuencias legales que correspondan.\n\n" +
        "8. Eliminación de cuenta\n" +
        "Puedes eliminar tu cuenta desde la aplicación (Configuración > Eliminar mi cuenta). Se " +
        "eliminan tu cuenta y tus datos personales; el histórico económico del negocio se " +
        "conserva según lo indicado en la aplicación.\n\n" +
        "9. Limitaciones y responsabilidad\n" +
        "Trazys se presta en el estado en que se encuentra. Tu centro es el responsable de la " +
        "relación contigo y del tratamiento de tus datos conforme a su propia política.\n\n" +
        "10. Cambios\n" +
        "Estos términos pueden actualizarse. Cuando cambie la versión vigente, deberás aceptar la " +
        "nueva versión para continuar usando Trazys.\n\n" +
        "Puedes consultar también la política de privacidad de Trazys, que es un documento " +
        "independiente sobre el tratamiento de datos personales."
