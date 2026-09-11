package com.roberto.gestorpro.ui.configuracion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.roberto.gestorpro.ui.components.AppDialogConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.viewmodel.DatosViewModel

@Composable
fun DatosScreen(
    navController: NavHostController,
    viewModel: DatosViewModel = hiltViewModel()
) {
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val esError by viewModel.esError.collectAsStateWithLifecycle()
    val mostrarDialogoImportar by viewModel.mostrarDialogoImportar.collectAsStateWithLifecycle()
    val mostrarDialogoRestaurar by viewModel.mostrarDialogoRestaurar.collectAsStateWithLifecycle()

    var incluirFotos by remember { mutableStateOf(true) }

    val exportarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportarDatos(uri, incluirFotos)
        }
    }

    val importarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.solicitarImportar(uri)
        }
    }

    val restaurarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.solicitarRestaurar(uri)
        }
    }

    if (mostrarDialogoImportar) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarImportar() },
            title = { Text("¿Importar datos?") },
            text = {
                Text("Se incorporarán o actualizarán los datos del backup del MISMO centro. No se borrarán los datos actuales.")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "IMPORTAR",
                    onClick = { viewModel.confirmarImportar() }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "CANCELAR",
                    onClick = { viewModel.cancelarImportar() }
                )
            }
        )
    }

    if (mostrarDialogoRestaurar) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelarRestaurar() },
            title = { Text("¿Restaurar copia de seguridad?") },
            text = {
                Text("Se reemplazarán TODOS los datos locales actuales por el contenido del backup del MISMO centro. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                AppDialogConfirmButton(
                    text = "RESTAURAR",
                    onClick = { viewModel.confirmarRestaurar() }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "CANCELAR",
                    onClick = { viewModel.cancelarRestaurar() }
                )
            }
        )
    }

    Scaffold { innerPadding ->
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
                    text = "Datos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Gestión de datos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Opción de fotografías del backup (solo afecta a Exportar).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = incluirFotos,
                    onCheckedChange = { incluirFotos = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Incluir fotografías en el backup",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            DatosItem(
                titulo = "Exportar datos",
                descripcion = "Guardar una copia completa de tu centro",
                icono = Icons.Default.CloudUpload,
                onClick = { exportarLauncher.launch("trazys_backup.zip") }
            )

            DatosItem(
                titulo = "Importar datos",
                descripcion = "Añadir/actualizar datos desde un backup del mismo centro",
                icono = Icons.Default.CloudDownload,
                onClick = { importarLauncher.launch(arrayOf("application/zip")) }
            )

            DatosItem(
                titulo = "Restaurar copia de seguridad",
                descripcion = "Reemplazar todos los datos por un backup del mismo centro",
                icono = Icons.Default.Restore,
                onClick = { restaurarLauncher.launch(arrayOf("application/zip")) }
            )

            if (mensaje != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = mensaje!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (esError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun DatosItem(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
