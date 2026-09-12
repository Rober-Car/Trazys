package com.roberto.gestorpro.ui.configuracion

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.ui.components.AppNavigationBackButton

/**
 * PoliticaPrivacidadScreen (ADMIN)
 * -------------------------------
 * Política de privacidad de Trazys (app Admin) con scroll vertical.
 * Describe los tratamientos REALES implementados; no menciona funcionalidades
 * futuras no operativas (p. ej. el almacenamiento de fotografías en la nube).
 */
@Composable
fun PoliticaPrivacidadScreen(
    navController: NavHostController
) {
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
                    text = "Política de privacidad",
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
                    text = "Política de privacidad de Trazys",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                SeccionAdminPrivacidad("1. Responsable del tratamiento") {
                    Text(
                        "Trazys es una aplicación de gestión de un gimnasio.\n\n" +
                            "Responsable del tratamiento: Roberto Carlos Salvador Martin.\n" +
                            "NIF: 48910659D.\n" +
                            "Domicilio: Avenida de Huelva, 6, 21830 Bonares (Huelva), España.\n" +
                            "Contacto de privacidad y ejercicio de derechos: Pollinox@hotmail.com."
                    )
                }

                SeccionAdminPrivacidad("2. Qué datos tratamos") {
                    Text(
                        "Como administrador de Trazys, la aplicación trata los siguientes " +
                            "datos:\n\n" +
                            "• Datos de tu cuenta de acceso: correo electrónico y contraseña " +
                            "(gestionada por Firebase Authentication) y un identificador " +
                            "interno de usuario.\n" +
                            "• Datos del centro que configuras: nombre, código maestro de " +
                            "acceso para los clientes y logo (el logo se almacena de forma " +
                            "segura en la nube de Trazys, en Firebase Storage).\n" +
                            "• Datos de los clientes de tu centro que introduces o gestionas: " +
                            "identificación (nombre, apellidos, DNI), contacto (teléfono y " +
                            "correo), fecha de nacimiento, fotografía, servicios contratados, " +
                            "sesiones y reservas, estado de cada ficha y observaciones internas.\n" +
                            "• Datos económicos de tus clientes: cuotas y movimientos, " +
                            "importes, estado de pago, fecha y método de pago y deuda. No se " +
                            "almacenan números de tarjeta ni datos bancarios.\n" +
                            "• Solicitudes de baja de los clientes y su resolución.\n" +
                            "• Notificaciones enviadas y, en su caso, identificadores de " +
                            "dispositivo de los clientes para gestionar el envío de " +
                            "notificaciones.\n" +
                            "• Denuncias de contenido o de usuarios y su estado de revisión.\n" +
                            "• Gastos y datos de gestión que registres en la aplicación.\n\n" +
                            "En todo caso, los datos de tus clientes se tratan únicamente para " +
                            "gestionar tu centro y nunca se ceden para publicidad."
                    )
                }

                SeccionAdminPrivacidad("3. Para qué utilizamos los datos") {
                    Text(
                        "Utilizamos los datos para:\n\n" +
                            "• Permitirte gestionar tu centro: clientes, servicios, sesiones, " +
                            "reservas, solicitudes de baja y comunicaciones.\n" +
                            "• Registrar las cuotas y cobros de tus clientes y su estado de pago.\n" +
                            "• Enviar notificaciones a tus clientes desde la aplicación.\n" +
                            "• Mantener la seguridad de la cuenta y atender tus consultas.\n\n" +
                            "La aplicación no utiliza la ubicación: no solicita permisos de " +
                            "localización ni recoge tu posición."
                    )
                }

                SeccionAdminPrivacidad("4. Base jurídica") {
                    Text(
                        "Tratamos los datos necesarios para la prestación del servicio de " +
                            "gestión que ofrece Trazys y por el interés legítimo del " +
                            "responsable en administrar correctamente el centro y la " +
                            "relación con sus clientes. Las notificaciones a los clientes se " +
                            "envían según la relación que cada cliente mantiene con tu " +
                            "centro y, en su caso, con el consentimiento del cliente. Ciertos " +
                            "datos económicos pueden conservarse además por obligaciones " +
                            "legales aplicables.\n\n" +
                            "Como administrador, eres quien decide introducir los datos de tus " +
                            "clientes en la aplicación; te corresponde informar a tus clientes " +
                            "del tratamiento que realizas conforme a tu propia política."
                    )
                }

                SeccionAdminPrivacidad("5. Cómo se almacenan y protegen los datos") {
                    Text(
                        "Los datos se almacenan de forma segura en servicios en la nube de " +
                            "Google (Firebase) y, en parte, en tu propio dispositivo. Las " +
                            "comunicaciones con los servidores se realizan de forma cifrada y " +
                            "el acceso está limitado a tu cuenta y, en su caso, a la de cada " +
                            "cliente. No se ceden los datos a terceros para publicidad."
                    )
                }

                SeccionAdminPrivacidad("6. Proveedores de servicios") {
                    Text(
                        "La aplicación utiliza los servicios de Google:\n\n" +
                            "• Firebase Authentication: identificación con correo y contraseña.\n" +
                            "• Firebase (Cloud Firestore): almacenamiento de los datos de la " +
                            "aplicación.\n" +
                            "• Firebase Cloud Messaging (FCM): se utiliza para registrar los " +
                            "identificadores de dispositivo de los clientes y gestionar sus " +
                            "notificaciones.\n" +
                            "• Firebase Storage: almacenamiento seguro de las fotografías de " +
                            "perfil de los clientes y del logo del centro.\n\n" +
                            "Google puede tratar datos fuera del Espacio Económico Europeo. Cuando " +
                            "ello resulte necesario, se aplicarán las garantías previstas por la " +
                            "normativa de protección de datos, incluidas, en su caso, las " +
                            "cláusulas contractuales tipo u otros mecanismos válidos. No se " +
                            "utiliza ningún otro proveedor que trate los datos personales."
                    )
                }

                SeccionAdminPrivacidad("7. Conservación") {
                    Text(
                        "Los datos personales se conservarán durante el tiempo necesario para " +
                            "cumplir las finalidades para las que fueron recabados y mientras " +
                            "exista una relación con el usuario, y posteriormente durante los " +
                            "plazos necesarios para atender las obligaciones legales y posibles " +
                            "responsabilidades que puedan derivarse del tratamiento.\n\n" +
                            "Al eliminar la cuenta de un cliente, se suprimen su ficha " +
                            "operativa y los datos personales asociados. No obstante, el " +
                            "histórico económico del centro puede conservar determinados " +
                            "registros con datos identificativos (nombre, apellidos y DNI " +
                            "históricos del movimiento) durante el tiempo necesario para " +
                            "cumplir obligaciones legales, fiscales o contables o para el " +
                            "ejercicio o defensa de reclamaciones, según resulte aplicable.\n\n" +
                            "Al eliminar tu cuenta de administrador se elimina el centro y sus " +
                            "datos operativos, incluido el histórico económico del centro, " +
                            "salvo lo que deba conservarse por las obligaciones anteriores."
                    )
                }

                SeccionAdminPrivacidad("8. Derechos de los usuarios") {
                    Text(
                        "Puedes ejercer, cuando corresponda, los derechos de acceso, " +
                            "rectificación, supresión, oposición, limitación del tratamiento y " +
                            "portabilidad, así como retirar tu consentimiento cuando el " +
                            "tratamiento se base en él. También puedes reclamar ante la Agencia " +
                            "Española de Protección de Datos (www.aepd.es) cuando lo consideres " +
                            "oportuno.\n\n" +
                            "Los clientes de tu centro podrán ejercer sus derechos ante ti o " +
                            "ante el responsable conforme a esta política."
                    )
                }

                SeccionAdminPrivacidad("9. Cómo ejercer tus derechos") {
                    Text(
                        "Puedes ejercer tus derechos escribiendo a Pollinox@hotmail.com, " +
                            "indicando tu nombre y el derecho que quieres ejercer. Atenderemos tu " +
                            "solicitud en los plazos legalmente establecidos."
                    )
                }

                SeccionAdminPrivacidad("10. Menores") {
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

                SeccionAdminPrivacidad("11. Cambios de la política") {
                    Text(
                        "Podremos actualizar esta política para reflejar cambios en la " +
                            "aplicación o en la normativa. La versión vigente estará siempre " +
                            "disponible en esta sección."
                    )
                }

                SeccionAdminPrivacidad("12. Contacto") {
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
 * SeccionAdminPrivacidad
 * ----------------------
 * Título de sección + bloque de texto de la política.
 */
@Composable
private fun SeccionAdminPrivacidad(
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
