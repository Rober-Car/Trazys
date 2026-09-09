package com.roberto.gestorpro.cliente.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.PerfilPendiente
import com.roberto.gestorpro.cliente.data.firebase.FotoClienteStorage
import com.roberto.gestorpro.cliente.model.Cliente
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.components.AppPrimaryButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel
import java.io.File

/**
 * MiPerfilScreen
 * --------------
 * Muestra el perfil del CLIENTE:
 *   - vinculado: la ficha clientes/{idCliente} (sin observaciones ni datos admin);
 *   - sin vincular: el perfil pendiente de perfiles_pendientes/{uid}.
 */
@Composable
fun MiPerfilScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val idCliente by mainViewModel.idCliente.collectAsStateWithLifecycle()
    val cliente by mainViewModel.cliente.collectAsStateWithLifecycle()
    val perfilPendiente by mainViewModel.perfilPendiente.collectAsStateWithLifecycle()
    val fotoPerfil by mainViewModel.fotoPerfil.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.cargarPerfilVista()
    }

    LaunchedEffect(cliente?.idCliente, cliente?.foto) {
        val c = cliente
        if (c != null) {
            if (FotoClienteStorage.esUrlFoto(c.foto)) {
                mainViewModel.cargarFotoPerfil(c)
            } else {
                mainViewModel.limpiarFotoPerfil()
            }
        } else {
            mainViewModel.limpiarFotoPerfil()
        }
    }

    val datos = if (idCliente != null) {
        cliente?.let { DatosPerfilMostrar.from(it) }
    } else {
        perfilPendiente?.let { DatosPerfilMostrar.from(it) }
    }

    // Textos localizados del bloque de perfil/cuenta.
    val textoMiPerfil = stringResource(R.string.perfil_mi_perfil)
    val textoDatosPersonales = stringResource(R.string.perfil_datos_personales)
    val textoDni = stringResource(R.string.vinculacion_label_dni)
    val textoTelefono = stringResource(R.string.perfil_campo_telefono)
    val textoEmail = stringResource(R.string.auth_email)
    val textoFechaNacimiento = stringResource(R.string.perfil_label_fecha_nacimiento)
    val textoSinEmail = stringResource(R.string.perfil_sin_email)
    val textoSinEspecificar = stringResource(R.string.perfil_sin_especificar)
    val textoFotoPerfil = stringResource(R.string.perfil_foto_descripcion)
    val textoModificarDatos = stringResource(R.string.perfil_accion_modificar_datos)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        text = textoMiPerfil,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (datos == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val modeloFotoPerfil: Any? = if (FotoClienteStorage.esUrlFoto(datos.foto)) {
                    // Foto remota: se muestra el fichero local descargado con el SDK
                    // autenticado (nunca la URL HTTP directa).
                    fotoPerfil
                } else if (datos.foto.isNotBlank()) {
                    File(datos.foto)
                } else {
                    null
                }
                if (modeloFotoPerfil != null) {
                    AsyncImage(
                        model = modeloFotoPerfil,
                        contentDescription = textoFotoPerfil,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF2196F3), CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${datos.nombre} ${datos.apellidos}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                val estadoRemoto = datos.estadoRemoto
                if (estadoRemoto != null) {
                    Spacer(modifier = Modifier.height(4.dp))

                    val colorEstado = estadoColor(estadoRemoto)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colorEstado.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = stringResource(estadoRecurso(estadoRemoto)),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorEstado,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = textoDatosPersonales,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF2196F3)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilaDatoPerfilCliente(
                        icono = Icons.Default.Badge,
                        etiqueta = textoDni,
                        valor = datos.dni,
                        color = Color(0xFF2196F3)
                    )
                    FilaDatoPerfilCliente(
                        icono = Icons.Default.Phone,
                        etiqueta = textoTelefono,
                        valor = datos.telefono,
                        color = Color(0xFF26A69A)
                    )
                    FilaDatoPerfilCliente(
                        icono = Icons.Default.Email,
                        etiqueta = textoEmail,
                        valor = datos.email ?: textoSinEmail,
                        color = Color(0xFF8E24AA)
                    )
                    FilaDatoPerfilCliente(
                        icono = Icons.Default.DateRange,
                        etiqueta = textoFechaNacimiento,
                        valor = formatearFecha(datos.fechaNacimiento, textoSinEspecificar),
                        color = Color(0xFFFB8C00)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AppPrimaryButton(
                text = textoModificarDatos,
                onClick = { navController.navigate(Routes.EDITAR_PERFIL) }
            )
        }
    }
}

/**
 * DatosPerfilMostrar
 * ------------------
 * Datos mínimos del perfil para mostrar tanto desde clientes/{idCliente}
 * (vinculado) como desde perfiles_pendientes/{uid} (sin vincular).
 * `estadoRemoto` es el código remoto del estado (p. ej. "ACTIVO"); el texto
 * visible y el color se resuelven en la UI desde ese código, NO desde texto.
 */
private data class DatosPerfilMostrar(
    val nombre: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val email: String?,
    val foto: String,
    val fechaNacimiento: Long?,
    val estadoRemoto: String?
) {
    companion object {
        fun from(c: Cliente) = DatosPerfilMostrar(
            nombre = c.nombre,
            apellidos = c.apellidos,
            dni = c.dni,
            telefono = c.telefono,
            email = c.email,
            foto = c.foto,
            fechaNacimiento = c.fechaNacimiento,
            estadoRemoto = c.estado.name
        )

        fun from(p: PerfilPendiente) = DatosPerfilMostrar(
            nombre = p.nombre,
            apellidos = p.apellidos,
            dni = p.dni,
            telefono = p.telefono,
            email = p.email,
            foto = p.foto,
            fechaNacimiento = p.fechaNacimiento,
            estadoRemoto = null
        )
    }
}

/** Recurso de texto correspondiente al estado remoto. */
@StringRes
private fun estadoRecurso(estadoRemoto: String): Int = when (estadoRemoto) {
    "ACTIVO" -> R.string.estado_activo
    "MOROSO" -> R.string.estado_moroso
    "BAJA" -> R.string.estado_baja
    "ARCHIVADO" -> R.string.estado_archivado
    else -> R.string.estado_registrado
}

/** Color del estado derivado del código remoto (no del texto traducido). */
private fun estadoColor(estadoRemoto: String): Color {
    return when (estadoRemoto) {
        "ACTIVO" -> Color(0xFF43A047)
        "MOROSO" -> Color(0xFFE53935)
        "BAJA" -> Color(0xFF78909C)
        "ARCHIVADO" -> Color(0xFF78909C)
        else -> Color(0xFF1E88E5)
    }
}

@Composable
private fun FilaDatoPerfilCliente(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    etiqueta: String,
    valor: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun formatearFecha(millis: Long?, textoVacio: String): String {
    if (millis == null || millis <= 0L) return textoVacio
    return try {
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) {
        textoVacio
    }
}
