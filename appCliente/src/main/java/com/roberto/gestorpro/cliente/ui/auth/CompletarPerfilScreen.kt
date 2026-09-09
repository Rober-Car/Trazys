package com.roberto.gestorpro.cliente.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.PerfilPendiente
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.components.BotonSelectorFoto
import com.roberto.gestorpro.cliente.ui.utils.crearFotoTemporal
import com.roberto.gestorpro.cliente.ui.utils.guardaFotoEnInterna
import com.roberto.gestorpro.cliente.ui.utils.guardarFotoDeCamara
import com.roberto.gestorpro.cliente.ui.utils.uriDeFotoTemporal
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * CompletarPerfilScreen
 * ---------------------
 * Formulario de datos personales del CLIENTE sin negocio (VÍA 2).
 * Guarda el perfil en perfiles_pendientes/{uid} y vuelve al Inicio para que el
 * cliente introduzca el código maestro + DNI.
 */
@Composable
fun CompletarPerfilScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val operandoRemoto by mainViewModel.operandoRemoto.collectAsStateWithLifecycle()
    val perfilPendiente by mainViewModel.perfilPendiente.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var nombre by rememberSaveable { mutableStateOf("") }
    var apellidos by rememberSaveable { mutableStateOf("") }
    var dni by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var fechaNacimientoMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrarSelectorFecha by rememberSaveable { mutableStateOf(false) }
    var foto by rememberSaveable { mutableStateOf("") }
    var mensajeError by rememberSaveable { mutableStateOf("") }
    var fotoTemporal by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    // Textos localizados del bloque de incorporación.
    val textoCompletaRegistro = stringResource(R.string.perfil_titulo_completar)
    val textoIntroTemporal = stringResource(R.string.perfil_intro_temporal)
    val textoCamposObligatorios = stringResource(R.string.perfil_aviso_campos_obligatorios)
    val textoFotoRostro = stringResource(R.string.perfil_etiqueta_foto)
    val textoFotoPerfil = stringResource(R.string.perfil_foto_descripcion)
    val textoNombre = stringResource(R.string.perfil_label_nombre)
    val textoApellidos = stringResource(R.string.perfil_label_apellidos)
    val textoDni = stringResource(R.string.perfil_label_dni)
    val textoTelefono = stringResource(R.string.perfil_label_telefono)
    val textoEmailOpcional = stringResource(R.string.perfil_label_email_opcional)
    val textoFechaNacimiento = stringResource(R.string.perfil_label_fecha_nacimiento)
    val textoAyudaFecha = stringResource(R.string.perfil_ayuda_fecha_opcional)
    val textoGuardarPerfil = stringResource(R.string.perfil_accion_guardar)
    val textoAceptar = stringResource(R.string.accion_aceptar)
    val textoCancelar = stringResource(R.string.accion_cancelar)

    // Carga el perfil pendiente existente (perfiles_pendientes/{uid}) para que
    // los campos aparezcan rellenados si el usuario ya los completó antes.
    LaunchedEffect(Unit) {
        mainViewModel.cargarPerfilPendiente()
    }

    // Prefill: solo rellena los campos que siguen vacíos (respeta lo que el
    // usuario esté tecleando en esta sesión).
    LaunchedEffect(perfilPendiente) {
        val p = perfilPendiente ?: return@LaunchedEffect
        if (nombre.isBlank()) nombre = p.nombre
        if (apellidos.isBlank()) apellidos = p.apellidos
        if (dni.isBlank()) dni = p.dni
        if (telefono.isBlank()) telefono = p.telefono
        if (email.isBlank()) email = p.email ?: ""
        if (foto.isBlank()) foto = p.foto
        if (fechaNacimientoMillis == null && p.fechaNacimiento != null && p.fechaNacimiento > 0L) {
            fechaNacimientoMillis = p.fechaNacimiento
        }
    }

    val launcherFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val ruta = guardaFotoEnInterna(context, uri)
            if (ruta != null) foto = ruta
        }
    }

    val launcherTomarFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { resultado ->
        if (resultado) {
            val ruta = guardarFotoDeCamara(context, fotoTemporal)
            if (ruta != null) foto = ruta
        }
        fotoTemporal = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppNavigationBackButton(onClick = { navController.popBackStack() })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = textoCompletaRegistro,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = textoIntroTemporal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = textoCamposObligatorios,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = textoFotoRostro,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (foto.isNotBlank()) {
                    AsyncImage(
                        model = File(foto),
                        contentDescription = textoFotoPerfil,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF1E88E5), CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                BotonSelectorFoto(
                    tieneFoto = foto.isNotBlank(),
                    onElegirGaleria = {
                        launcherFoto.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onHacerFoto = {
                        val temporal = crearFotoTemporal(context)
                        if (temporal != null) {
                            fotoTemporal = temporal
                            launcherTomarFoto.launch(uriDeFotoTemporal(context, temporal))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(textoNombre) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                label = { Text(textoApellidos) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = dni,
                onValueChange = { dni = it.uppercase() },
                label = { Text(textoDni) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text(textoTelefono) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(textoEmailOpcional) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = fechaNacimientoMillis?.let(::formatearFechaCompletarPerfil) ?: "",
                onValueChange = {},
                label = { Text(textoFechaNacimiento) },
                leadingIcon = {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                },
                supportingText = {
                    Text(textoAyudaFecha)
                },
                singleLine = true,
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mostrarSelectorFecha = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (mensajeError.isNotBlank()) {
                Text(
                    text = mensajeError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            AppPrimaryButton(
                text = textoGuardarPerfil,
                onClick = {
                    mensajeError = ""
                    scope.launch {
                        val perfil = PerfilPendiente(
                            nombre = nombre.trim(),
                            apellidos = apellidos.trim(),
                            dni = dni.trim(),
                            telefono = telefono.trim(),
                            email = email.trim().ifBlank { null },
                            foto = foto,
                            fechaNacimiento = fechaNacimientoMillis
                        )
                        val error = mainViewModel.guardarPerfilPendiente(perfil)
                        if (error != null) {
                            mensajeError = error
                        } else {
                            // El perfil queda guardado en perfiles_pendientes/{uid}.
                            // NO se crea la ficha ni se vincula automáticamente: se
                            // lleva al cliente a la pantalla de vinculación para que
                            // introduzca código maestro + DNI (VÍA 1 o VÍA 2).
                            navController.navigate(Routes.INICIO) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
                enabled = !operandoRemoto &&
                    nombre.isNotBlank() && apellidos.isNotBlank() && dni.isNotBlank() &&
                    telefono.isNotBlank() && foto.isNotBlank()
            )

            if (operandoRemoto) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }

    if (mostrarSelectorFecha) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fechaNacimientoMillis?.let(::fechaParaDatePicker),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= fechaMaximaParaDatePicker()
            }
        )

        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            fechaNacimientoMillis = fechaDesdeDatePicker(it)
                        }
                        mostrarSelectorFecha = false
                    }
                ) {
                    Text(textoAceptar)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSelectorFecha = false }) {
                    Text(textoCancelar)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * formatearFechaCompletarPerfil
 * -----------------------------
 * Convierte los milisegundos del perfil pendiente a texto dd/MM/aaaa para
 * rellenar el campo de fecha al reabrir la pantalla.
 */
private fun formatearFechaCompletarPerfil(millis: Long): String {
    return try {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        ""
    }
}

/** Convierte el epoch local almacenado al formato UTC que usa el DatePicker. */
private fun fechaParaDatePicker(millis: Long): Long =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** Convierte el día elegido por el DatePicker al epoch local del modelo. */
private fun fechaDesdeDatePicker(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

private fun fechaMaximaParaDatePicker(): Long =
    LocalDate.now(ZoneId.systemDefault())
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
