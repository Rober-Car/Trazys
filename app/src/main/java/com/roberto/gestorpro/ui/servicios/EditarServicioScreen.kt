package com.roberto.gestorpro.ui.servicios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.viewmodel.ServicioViewModel

/**
 * EditarServicioScreen
 * --------------------
 * Pantalla de alta/edición de un servicio.
 * - idServicio == null  → crear (activo = true).
 * - idServicio != null  → editar nombre, descripción y estado activo.
 * El idServicio nunca se modifica.
 */
@Composable
fun EditarServicioScreen(
    navController: NavHostController,
    idServicio: Int? = null,
    viewModel: ServicioViewModel = hiltViewModel()
) {
    val servicioSeleccionado by viewModel.servicioSeleccionado.collectAsStateWithLifecycle()

    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var activo by remember { mutableStateOf(true) }
    var permiteCombinarDia by remember { mutableStateOf(true) }
    var errorNombre by remember { mutableStateOf(false) }
    var errorPrecio by remember { mutableStateOf(false) }
    var cargado by remember { mutableStateOf(idServicio == null) }

    LaunchedEffect(idServicio) {
        if (idServicio != null) {
            viewModel.cargarServicio(idServicio)
        }
    }

    LaunchedEffect(servicioSeleccionado) {
        if (idServicio != null && servicioSeleccionado != null) {
            nombre = servicioSeleccionado!!.nombre
            descripcion = servicioSeleccionado!!.descripcion
            precio = precioParaCampo(servicioSeleccionado!!.precio)
            activo = servicioSeleccionado!!.activo
            permiteCombinarDia = servicioSeleccionado!!.permiteCombinarDia
            cargado = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(onClick = { navController.popBackStack() })
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (idServicio == null) "Crear servicio" else "Editar servicio",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = "Datos del servicio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it
                    errorNombre = false
                },
                label = { Text("Nombre") },
                placeholder = { Text("Ej: CrossFit, Sala de máquinas...") },
                isError = errorNombre,
                supportingText = {
                    if (errorNombre) Text("El nombre es obligatorio")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                placeholder = { Text("Descripción opcional del servicio") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = precio,
                onValueChange = {
                    precio = it
                    errorPrecio = false
                },
                label = { Text("Precio (€)") },
                placeholder = { Text("Ej: 30") },
                isError = errorPrecio,
                supportingText = {
                    if (errorPrecio) Text("Introduce un precio válido (0 o mayor)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (idServicio != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Servicio activo",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = activo,
                        onCheckedChange = { activo = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF1E88E5)
                        )
                    )
                }
            }

            // Permite a un CLIENTE reservar más de una actividad distinta el mismo
            // día. Aparece SIEMPRE (alta y edición); default true en actividades
            // nuevas. Se guarda en Room y se replica a Firestore.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permitir combinar con otras actividades el mismo día",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = permiteCombinarDia,
                    onCheckedChange = { permiteCombinarDia = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF1E88E5)
                    )
                )
            }

            AppPrimaryButton(
                text = if (idServicio == null) "Crear servicio" else "Guardar cambios",
                onClick = {
                    val precioValido = precio
                        .trim()
                        .replace(",", ".")
                        .toDoubleOrNull()

                    errorNombre = nombre.isBlank()
                    errorPrecio = precioValido == null || precioValido < 0

                    if (errorNombre || errorPrecio) {
                        return@AppPrimaryButton
                    }

                    if (idServicio == null) {
                        viewModel.crearServicio(
                            nombre, descripcion, precioValido!!, permiteCombinarDia
                        )
                    } else {
                        val original = servicioSeleccionado ?: return@AppPrimaryButton
                        viewModel.actualizarServicio(
                            original.copy(
                                nombre = nombre.trim(),
                                descripcion = descripcion.trim(),
                                precio = precioValido!!,
                                activo = activo,
                                permiteCombinarDia = permiteCombinarDia
                            )
                        )
                    }
                    navController.popBackStack()
                },
                enabled = cargado
            )
        }
    }
}

/**
 * precioParaCampo
 * ---------------
 * Convierte el precio (Double) al texto que se muestra en el campo "Precio":
 * sin decimales cuando el importe es entero (30.0 → "30") y con decimales
 * cuando no lo es (12.5 → "12.5").
 */
private fun precioParaCampo(valor: Double): String {
    val texto = valor.toString()
    return if (texto.endsWith(".0")) texto.dropLast(2) else texto
}
