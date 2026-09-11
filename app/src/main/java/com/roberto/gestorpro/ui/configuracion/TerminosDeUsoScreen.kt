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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import com.roberto.gestorpro.util.TerminosDeUso

/**
 * TerminosDeUsoScreen (ADMIN)
 * ---------------------------
 * Documento real de "Términos y condiciones" de Trazys, separado de la política
 * de privacidad. Permite al usuario autenticado aceptar la versión vigente.
 */
@Composable
fun TerminosDeUsoScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    /**
     * aceptado
     * --------
     * null = comprobando; true = el usuario ya aceptó la versión VIGENTE;
     * false = debe aceptar (no aceptó o aceptó una versión anterior).
     * Se lee de la persistencia al entrar, para no volver a pedir la aceptación
     * cuando ya está registrada.
     */
    var aceptado by remember { mutableStateOf<Boolean?>(null) }

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
                text = "Términos y condiciones de Trazys",
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
                        text = "Términos aceptados — versión ${TerminosDeUso.VERSION}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ya has aceptado la versión vigente de los Términos de uso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // No aceptó la versión vigente (o aceptó una anterior): se ofrece aceptar.
                false -> {
                    AppPrimaryButton(
                        text = "Aceptar los términos de uso",
                        onClick = {
                            mainViewModel.aceptarTerminos()
                            aceptado = true
                        },
                        fullWidth = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Al aceptar registras tu aceptación de la versión " +
                            "${TerminosDeUso.VERSION} de estos términos para tu cuenta de Trazys.",
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
        "(ADMIN) y una app para clientes (CLIENTE). Estos términos regulan el uso de Trazys.\n\n" +
        "Responsable: Roberto Carlos Salvador Martin. NIF: 48910659D. " +
        "Domicilio: Avenida de Huelva, 6, 21830 Bonares (Huelva), España. " +
        "Contacto: Pollinox@hotmail.com.\n\n" +
        "2. Cuentas\n" +
        "El usuario es responsable de mantener seguras sus credenciales y de que los datos que " +
        "introduce sean correctos. Existen dos roles: ADMIN (gestiona el centro) y CLIENTE " +
        "(usuario del centro).\n\n" +
        "3. Contenido que puede aportar cada rol\n" +
        "El ADMIN puede aportar el nombre y el logo del centro, notificaciones y mensajes para sus " +
        "clientes, y gestionar las fichas de sus clientes. El CLIENTE puede aportar su fotografía de " +
        "perfil y sus datos personales, vinculados a su ficha en el centro.\n\n" +
        "4. Contenido prohibido\n" +
        "Queda prohibido introducir contenido ilegal, ofensivo, discriminatorio, de acoso, que infrinja " +
        "derechos de terceros o que no corresponda a la finalidad de Trazys. No se publica contenido de " +
        "forma abierta: el contenido solo es accesible por las cuentas relacionadas con el mismo centro.\n\n" +
        "5. Fotografías, logos y notificaciones\n" +
        "Las fotografías de perfil y los logos se almacenan de forma segura en Firebase Storage y solo " +
        "son visibles para el centro y, en su caso, sus clientes. Las notificaciones y mensajes del " +
        "ADMIN se envían a los clientes del centro correspondiente.\n\n" +
        "6. Retirada de contenido y moderación\n" +
        "El ADMIN puede retirar o corregir el contenido que gestiona (fichas, logos y notificaciones). " +
        "Cualquier usuario puede solicitar la retirada de contenido o el cese del uso indebido " +
        "escribiendo al contacto indicado. Las denuncias se atienden manualmente.\n\n" +
        "7. Uso indebido e incumplimiento\n" +
        "El uso indebido de Trazys puede conllevar la retirada de contenido, la suspensión o la " +
        "eliminación de la cuenta, además de las consecuencias legales que correspondan.\n\n" +
        "8. Eliminación de cuenta\n" +
        "Puedes eliminar tu cuenta desde la aplicación (Configuración > Eliminar mi cuenta / " +
        "Eliminar cuenta y centro). El CLIENTE elimina su cuenta y datos personales; el ADMIN " +
        "elimina su cuenta y todo su centro. El histórico económico del centro se conserva según " +
        "lo indicado en la aplicación.\n\n" +
        "9. Limitaciones y responsabilidad\n" +
        "Trazys se presta en el estado en que se encuentra. El responsable no responde de los daños " +
        "derivados del mal uso de la aplicación ni de los datos que el ADMIN introduce de sus clientes, " +
        "de los que es responsable cada ADMIN conforme a su propia política.\n\n" +
        "10. Cambios\n" +
        "Estos términos pueden actualizarse. Cuando cambie la versión vigente, el usuario deberá " +
        "aceptar la nueva versión para continuar usando Trazys.\n\n" +
        "Puedes consultar también la política de privacidad de Trazys, que es un documento " +
        "independiente sobre el tratamiento de datos personales."
