package com.roberto.gestorpro.cliente.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton

/**
 * EleccionInicioScreen
 * --------------------
 * Primera pantalla del CLIENTE autenticado sin ficha ni perfil pendiente.
 * Pregunta "¿Cómo quieres empezar?" y ofrece dos caminos claros:
 *  1. "Vincularme al centro": abre la pantalla de vinculación (código + DNI),
 *     que decide internamente la VÍA 1 o la VÍA 2 según corresponda.
 *  2. "Registrarme": abre el formulario de registro (CompletarPerfilScreen);
 *     tras guardar el perfil se le llevará a la pantalla de vinculación.
 * No muestra el formulario código + DNI en esta pantalla.
 */
@Composable
fun EleccionInicioScreen(
    navController: NavHostController
) {
    val textoComoEmpezar = stringResource(R.string.inicio_eleccion_titulo)
    val textoCentroYaTieneDatos = stringResource(R.string.inicio_eleccion_centro_titulo)
    val textoCentroYaTieneDatosDesc = stringResource(R.string.inicio_eleccion_centro_descripcion)
    val textoVincularme = stringResource(R.string.vinculacion_titulo_accion)
    val textoCentroNoRegistrado = stringResource(R.string.inicio_eleccion_registro_titulo)
    val textoCentroNoRegistradoDesc = stringResource(R.string.inicio_eleccion_registro_descripcion)
    val textoRegistrarme = stringResource(R.string.vinculacion_accion_registrarse)
    val textoNotaSinCodigo = stringResource(R.string.inicio_eleccion_nota)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = textoComoEmpezar,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(40.dp))

            OpcionInicioCard(
                titulo = textoCentroYaTieneDatos,
                descripcion = textoCentroYaTieneDatosDesc,
                textoBoton = textoVincularme,
                onClick = { navController.navigate(Routes.INICIO) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            OpcionInicioCard(
                titulo = textoCentroNoRegistrado,
                descripcion = textoCentroNoRegistradoDesc,
                textoBoton = textoRegistrarme,
                onClick = { navController.navigate(Routes.COMPLETAR_PERFIL) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = textoNotaSinCodigo,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * OpcionInicioCard
 * ----------------
 * Tarjeta con la explicación de una opción y su botón principal.
 */
@Composable
private fun OpcionInicioCard(
    titulo: String,
    descripcion: String,
    textoBoton: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppPrimaryButton(
                text = textoBoton,
                onClick = onClick,
                fullWidth = true
            )
        }
    }
}
