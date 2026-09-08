package com.roberto.gestorpro.cliente.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.data.firebase.PerfilPendiente
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.data.firebase.FotoClienteStorage
import com.roberto.gestorpro.cliente.ui.components.BotonSelectorFoto
import com.roberto.gestorpro.cliente.ui.utils.crearFotoTemporal
import com.roberto.gestorpro.cliente.ui.utils.guardaFotoEnInterna
import com.roberto.gestorpro.cliente.ui.utils.guardarFotoDeCamara
import com.roberto.gestorpro.cliente.ui.utils.uriDeFotoTemporal
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.launch

/**
 * EditarPerfilScreen
 * ------------------
 * Formulario de edición del propio perfil:
 *   - vinculado: solo datos personales (el DNI y los datos administrativos no
 *     se pueden modificar);
 *   - sin vincular: edita el perfil pendiente perfiles_pendientes/{uid} y el
 *     DNI SÍ es editable (todavía no está vinculado a una ficha).
 */
@Composable
fun EditarPerfilScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val operandoRemoto by mainViewModel.operandoRemoto.collectAsStateWithLifecycle()
    val idCliente by mainViewModel.idCliente.collectAsStateWithLifecycle()
    val cliente by mainViewModel.cliente.collectAsStateWithLifecycle()
    val perfilPendiente by mainViewModel.perfilPendiente.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vinculado = idCliente != null

    var nombre by rememberSaveable { mutableStateOf("") }
    var apellidos by rememberSaveable { mutableStateOf("") }
    var dni by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var foto by rememberSaveable { mutableStateOf("") }
    var fechaNacimiento by rememberSaveable { mutableStateOf("") }
    var mensajeError by rememberSaveable { mutableStateOf("") }
    var fotoTemporal by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mainViewModel.cargarPerfilVista()
    }

    // Prefill: rellena los campos vacíos con el perfil correspondiente al estado.
    LaunchedEffect(vinculado, cliente, perfilPendiente) {
        if (vinculado) {
            val c = cliente ?: return@LaunchedEffect
            if (nombre.isBlank()) nombre = c.nombre
            if (apellidos.isBlank()) apellidos = c.apellidos
            if (telefono.isBlank()) telefono = c.telefono
            if (email.isBlank()) email = c.email ?: ""
            if (foto.isBlank()) foto = c.foto
            if (fechaNacimiento.isBlank() && c.fechaNacimiento != null && c.fechaNacimiento > 0L) {
                fechaNacimiento = formatearFechaEditar(c.fechaNacimiento)
            }
        } else {
            val p = perfilPendiente ?: return@LaunchedEffect
            if (nombre.isBlank()) nombre = p.nombre
            if (apellidos.isBlank()) apellidos = p.apellidos
            if (dni.isBlank()) dni = p.dni
            if (telefono.isBlank()) telefono = p.telefono
            if (email.isBlank()) email = p.email ?: ""
            if (foto.isBlank()) foto = p.foto
            if (fechaNacimiento.isBlank() && p.fechaNacimiento != null && p.fechaNacimiento > 0L) {
                fechaNacimiento = formatearFechaEditar(p.fechaNacimiento)
            }
        }
    }

    // Foto remota vigente (antes de cambiarla): se muestra desde la caché local
    // descargada con el SDK autenticado, no con la URL HTTP directa.
    var fotoRemotaPreview by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(vinculado, idCliente, foto) {
        val id = idCliente
        fotoRemotaPreview =
            if (vinculado && FotoClienteStorage.esUrlFoto(foto) && id != null) {
                mainViewModel.cargarFotoRemotaPara(id, foto)
            } else {
                null
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
                        text = "Modificar mis datos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = if (vinculado) {
                    "Solo puedes modificar tus datos personales. El DNI y los " +
                        "datos de tu centro no se pueden cambiar desde aquí."
                } else {
                    "Todavía no estás vinculado a un centro. Puedes modificar " +
                        "tus datos, incluido el DNI; al vincularlo, el DNI quedará bloqueado."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val modeloFotoEditar: Any? = if (FotoClienteStorage.esUrlFoto(foto)) {
                // Foto remota: fichero de la caché autenticada (File local).
                fotoRemotaPreview
            } else if (foto.isNotBlank()) {
                File(foto)
            } else {
                null
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (modeloFotoEditar != null) {
                    AsyncImage(
                        model = modeloFotoEditar,
                        contentDescription = "Foto de perfil",
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
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                label = { Text("Apellidos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (!vinculado) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dni,
                    onValueChange = { dni = it.uppercase() },
                    label = { Text("DNI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = fechaNacimiento,
                onValueChange = { fechaNacimiento = it },
                label = { Text("Fecha de nacimiento (dd/MM/aaaa)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (mensajeError.isNotBlank()) {
                if (mensajeError.startsWith("Debes aceptar los Términos")) {
                    val anotado = buildAnnotatedString {
                        append(mensajeError)
                        append(" ")
                        pushStringAnnotation("terminos", "terminos")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Pulsa aquí para aceptarlos.")
                        }
                        pop()
                    }
                    ClickableText(
                        text = anotado,
                        style = MaterialTheme.typography.bodySmall
                            .copy(color = MaterialTheme.colorScheme.error),
                        onClick = { offset ->
                            anotado.getStringAnnotations("terminos", offset, offset)
                                .firstOrNull()?.let {
                                    navController.navigate(Routes.TERMINOS_CONDICIONES)
                                }
                        }
                    )
                } else {
                    Text(
                        text = mensajeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            AppPrimaryButton(
                text = "Guardar cambios",
                onClick = {
                    mensajeError = ""
                    scope.launch {
                        // Fecha opcional: si el campo está vacío se guarda null
                        // (sin fechas ficticias). El texto "dd/MM/aaaa" se convierte
                        // a epoch; un valor no válido se trata como sin fecha.
                        val fechaMillis: Long? =
                            fechaNacimientoEditarMillis(fechaNacimiento).takeIf { it > 0L }
                        val error = if (vinculado) {
                            mainViewModel.actualizarMisDatosPersonales(
                                nombre = nombre.trim(),
                                apellidos = apellidos.trim(),
                                telefono = telefono.trim(),
                                email = email.trim().ifBlank { null },
                                foto = foto,
                                fechaNacimiento = fechaMillis
                            )
                        } else {
                            val perfil = PerfilPendiente(
                                nombre = nombre.trim(),
                                apellidos = apellidos.trim(),
                                dni = dni.trim(),
                                telefono = telefono.trim(),
                                email = email.trim().ifBlank { null },
                                foto = foto,
                                fechaNacimiento = fechaMillis
                            )
                            mainViewModel.guardarPerfilPendiente(perfil)
                        }
                        if (error != null) {
                            mensajeError = error
                        } else {
                            navController.popBackStack()
                        }
                    }
                },
                enabled = !operandoRemoto &&
                    nombre.isNotBlank() && apellidos.isNotBlank() &&
                    telefono.isNotBlank() &&
                    (!vinculado && dni.isNotBlank() || vinculado)
            )

            if (operandoRemoto) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun formatearFechaEditar(millis: Long): String {
    return try {
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        ""
    }
}

private fun fechaNacimientoEditarMillis(fechaNacimiento: String): Long {
    val fecha = fechaNacimiento
        .split("/")
        .filter { it.isNotBlank() }
        .mapNotNull { it.toLongOrNull() }
    return if (fecha.size == 3) {
        try {
            java.time.LocalDate.of(
                fecha[2].toInt(),
                fecha[1].toInt(),
                fecha[0].toInt()
            )
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    } else {
        0L
    }
}
