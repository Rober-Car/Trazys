package com.roberto.gestorpro.cliente.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton

/**
 * PoliticaPrivacidadScreen
 * ------------------------
 * Política de privacidad de Trazys (app Cliente) con scroll vertical.
 * El texto describe los tratamientos REALES implementados en la aplicación;
 * no menciona funcionalidades futuras no operativas (p. ej. Firebase Storage).
 */
@Composable
fun PoliticaPrivacidadScreen(
    navController: NavHostController
) {
    val textoPrivacidad = stringResource(R.string.privacidad_titulo)
    val textoPrivacidadDocumento = stringResource(R.string.privacidad_titulo_documento)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
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
                    text = textoPrivacidad,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = textoPrivacidadDocumento,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                SeccionPrivacidad("1. Responsable del tratamiento") {
                    Text(
                        "Trazys es una aplicación de gestión de un centro.\n\n" +
                            "Responsable del tratamiento: Roberto Carlos Salvador Martin.\n" +
                            "NIF: 48910659D.\n" +
                            "Domicilio: Avenida de Huelva, 6, 21830 Bonares (Huelva), España.\n" +
                            "Contacto de privacidad y ejercicio de derechos: Pollinox@hotmail.com."
                    )
                }

                SeccionPrivacidad("2. Qué datos tratamos") {
                    Text(
                        "Cuando te das de alta o utilizas la aplicación como cliente del centro " +
                            "tratamos los siguientes datos:\n\n" +
                            "• Datos de identificación: nombre, apellidos y DNI.\n" +
                            "• Datos de contacto: teléfono y correo electrónico.\n" +
                            "• Fecha de nacimiento (si la indicas).\n" +
                            "• Fotografía: se guarda en tu ficha y se almacena de forma " +
                            "segura en la nube de Trazys (Firebase Storage) para que tu " +
                            "centro pueda verla.\n" +
                            "• Datos de tu relación con el centro: negocio al que estás vinculado, " +
                            "servicios contratados, sesiones y reservas, y estado de tu ficha.\n" +
                            "• Datos económicos de tu cuenta con el centro: cuotas y movimientos, " +
                            "importes, estado de pago, fecha y método de pago, y deuda pendiente. " +
                            "No se almacenan números de tarjeta ni datos bancarios.\n" +
                            "• Solicitudes de baja y su estado.\n" +
                            "• Notificaciones recibidas y tus preferencias de notificación.\n" +
                            "• Identificador del dispositivo: se registra para gestionar las " +
                            "notificaciones de tu cuenta.\n" +
                            "• Datos de la cuenta de acceso: correo y contraseña (gestionada por " +
                            "Firebase Authentication) y un identificador interno de usuario."
                    )
                }

                SeccionPrivacidad("3. Para qué utilizamos tus datos") {
                    Text(
                        "Utilizamos los datos para:\n\n" +
                            "• Gestionar tu relación con el centro: alta, servicios contratados, " +
                            "sesiones, reservas y estado de tu ficha.\n" +
                            "• Gestionar las cuotas y cobros del centro: movimientos, importes, " +
                            "estado, fecha y método de pago y deuda.\n" +
                            "• Gestionar las solicitudes de baja.\n" +
                            "• Enviarte notificaciones relacionadas con tu relación con el centro " +
                            "(por ejemplo, recordatorios o avisos), si las has autorizado en tu " +
                            "dispositivo.\n" +
                            "• Atender solicitudes y consultas y, en su caso, cumplir obligaciones " +
                            "legales.\n\n" +
                            "La aplicación no utiliza tu ubicación: no solicita permisos de " +
                            "localización ni recoge tu posición."
                    )
                }

                SeccionPrivacidad("4. Base jurídica") {
                    Text(
                        "Tratamos tus datos porque es necesario para la relación de servicios " +
                            "que mantienes con el centro (por ejemplo, gestionar tu alta, tus " +
                            "servicios, tus reservas y tus pagos) y por el interés legítimo del " +
                            "centro en gestionar y administrar correctamente esa relación.\n\n" +
                            "Cuando te enviamos notificaciones a tu dispositivo tratamos los datos " +
                            "con tu consentimiento, que puedes retirar en cualquier momento " +
                            "desactivando las notificaciones en tu dispositivo o en la aplicación.\n\n" +
                            "Ciertos datos económicos pueden conservarse además por obligaciones " +
                            "legales aplicables."
                    )
                }

                SeccionPrivacidad("5. Cómo se almacenan y protegen los datos") {
                    Text(
                        "Los datos se almacenan de forma segura en servicios en la nube de " +
                            "Google (Firebase) y, en parte, en tu propio dispositivo. Las " +
                            "comunicaciones con los servidores se realizan de forma cifrada y el " +
                            "acceso está limitado a quien gestiona el centro y a ti mismo según " +
                            "tu rol. No se comparten los datos con terceros para publicidad."
                    )
                }

                SeccionPrivacidad("6. Proveedores de servicios") {
                    Text(
                        "La aplicación utiliza los servicios de Google:\n\n" +
                            "• Firebase Authentication: identificación con correo y contraseña.\n" +
                            "• Firebase (Cloud Firestore): almacenamiento de los datos de la " +
                            "aplicación.\n" +
                            "• Firebase Cloud Messaging (FCM): se utiliza para registrar el " +
                            "identificador de tu dispositivo y gestionar tus notificaciones.\n\n" +
                            "Google puede tratar datos fuera del Espacio Económico Europeo. Cuando " +
                            "ello resulte necesario, se aplicarán las garantías previstas por la " +
                            "normativa de protección de datos, incluidas, en su caso, las " +
                            "cláusulas contractuales tipo u otros mecanismos válidos. No se " +
                            "utiliza ningún otro proveedor que trate los datos personales de los " +
                            "usuarios."
                    )
                }

                SeccionPrivacidad("7. Conservación") {
                    Text(
                        "Los datos personales se conservarán durante el tiempo necesario para " +
                            "cumplir las finalidades para las que fueron recabados y mientras " +
                            "exista una relación con el usuario, y posteriormente durante los " +
                            "plazos necesarios para atender las obligaciones legales y posibles " +
                            "responsabilidades que puedan derivarse del tratamiento."
                    )
                }

                SeccionPrivacidad("8. Derechos de los usuarios") {
                    Text(
                        "Puedes ejercer, cuando corresponda, los derechos de acceso, " +
                            "rectificación, supresión, oposición, limitación del tratamiento y " +
                            "portabilidad, así como retirar tu consentimiento cuando el " +
                            "tratamiento se base en él. También puedes reclamar ante la Agencia " +
                            "Española de Protección de Datos (www.aepd.es) cuando lo consideres " +
                            "oportuno."
                    )
                }

                SeccionPrivacidad("9. Cómo ejercer tus derechos") {
                    Text(
                        "Puedes ejercer tus derechos escribiendo a Pollinox@hotmail.com, " +
                            "indicando tu nombre y el derecho que quieres ejercer. Atenderemos tu " +
                            "solicitud en los plazos legalmente establecidos."
                    )
                }

                SeccionPrivacidad("10. Menores") {
                    Text(
                        "Trazys no está dirigida a un público determinado y no impide su uso " +
                            "por menores por el mero hecho de serlo. No obstante, cuando el " +
                            "tratamiento se base en el consentimiento, en España se considera que " +
                            "un mayor de 14 años puede prestar su consentimiento; para menores de " +
                            "esa edad será necesario el consentimiento del titular de la patria " +
                            "potestad o tutela. La aplicación no dispone de un mecanismo de " +
                            "verificación de la edad del usuario."
                    )
                }

                SeccionPrivacidad("11. Cambios de la política") {
                    Text(
                        "Podremos actualizar esta política para reflejar cambios en la " +
                            "aplicación o en la normativa. La versión vigente estará siempre " +
                            "disponible en esta sección."
                    )
                }

                SeccionPrivacidad("12. Contacto") {
                    Text(
                        "Para cualquier cuestión relacionada con esta política o con tus datos " +
                            "personales puedes escribir a: Pollinox@hotmail.com."
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * SeccionPrivacidad
 * -----------------
 * Título de sección + bloque de texto de la política.
 */
@Composable
private fun SeccionPrivacidad(
    titulo: String,
    contenido: @Composable () -> Unit
) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        contenido()
    }
}
