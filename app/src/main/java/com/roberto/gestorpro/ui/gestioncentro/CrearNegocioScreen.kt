package com.roberto.gestorpro.ui.gestioncentro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.R
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AyudaContextual
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * CrearNegocioScreen.kt
 * ---------------------
 * ✔ TIPO: archivo de código fuente Kotlin (pantalla de alta del negocio)
 * Es el archivo que define la pantalla donde el administrador crea su negocio
 * remoto con su código maestro. Sirve para ejecutar el Batch atómico que
 * vincula negocios/{id}, negocios_publicos/{id} y usuarios/{uid}.
 */

/**
 * CrearNegocioScreen
 * ------------------
 * ✔ TIPO: función @Composable
 * Es la pantalla de alta del negocio con nombre y código maestro.
 * Sirve para que un ADMIN sin negocio en la nube lo cree; al terminar vuelve
 * a Mi negocio, que pasará a modo edición.
 */
@Composable
fun CrearNegocioScreen(
    /**
     * navController
     * -------------
     * ✔ TIPO: parámetro (param) → NavHostController
     * Es el controlador de navegación que recibe la pantalla.
     * Sirve para volver atrás hacia Mi negocio al crear el negocio.
     */
    navController: NavHostController,
    /**
     * mainViewModel
     * -------------
     * ✔ TIPO: parámetro (param) → MainViewModel (inyectado por Hilt)
     * Es el ViewModel principal de la app.
     * Sirve para lanzar la creación remota y conocer su estado de carga.
     */
    mainViewModel: MainViewModel = hiltViewModel()
) {

    /**
     * nombre / codigoMaestro / mensajeError
     * -------------------------------------
     * ✔ TIPO: variables con estado (var) → String
     * Son los campos del formulario y el error a mostrar bajo el botón.
     * Sirven para validar la entrada antes de llamar al repositorio.
     */
    var nombre by rememberSaveable { mutableStateOf("") }
    var codigoMaestro by rememberSaveable { mutableStateOf("") }
    var mensajeError by rememberSaveable { mutableStateOf("") }

    /**
     * operandoRemoto
     * --------------
     * ✔ TIPO: variable observable (val by collectAsStateWithLifecycle) → Boolean
     * Es el estado de carga de la operación remota.
     * Sirve para deshabilitar el botón y mostrar el spinner durante el Batch.
     */
    val operandoRemoto by mainViewModel.operandoRemoto.collectAsStateWithLifecycle()

    /**
     * alcance
     * -------
     * ✔ TIPO: variable (val) → CoroutineScope
     * Es el scope ligado a la composición para lanzar la creación.
     * Sirve para no bloquear el hilo principal durante la operación.
     */
    val alcance = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = stringResource(R.string.centro_crear),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                AyudaContextual(
                    titulo = stringResource(R.string.centro_crear),
                    texto = stringResource(R.string.centro_ayuda_crear_negocio)
                )
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(stringResource(R.string.centro_nombre_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = codigoMaestro,
                onValueChange = { codigoMaestro = it },
                label = { Text(stringResource(R.string.centro_codigo_maestro_label)) },
                trailingIcon = {
                    AyudaContextual(
                        titulo = stringResource(R.string.centro_codigo_maestro_label),
                        texto = stringResource(R.string.centro_ayuda_codigo_unico)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mensajeError.isNotBlank()) {
                Text(
                    text = mensajeError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AppPrimaryButton(
                text = stringResource(R.string.centro_crear),
                onClick = {
                    alcance.launch {
                        val error = mainViewModel.crearNegocio(nombre, codigoMaestro)
                        if (error == null) {
                            navController.popBackStack()
                        } else {
                            mensajeError = error
                        }
                    }
                },
                enabled = !operandoRemoto &&
                    nombre.isNotBlank() && codigoMaestro.isNotBlank(),
                fullWidth = false,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            if (operandoRemoto) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
