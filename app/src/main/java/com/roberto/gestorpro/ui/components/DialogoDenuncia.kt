package com.roberto.gestorpro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roberto.gestorpro.data.firebase.DenunciaRepository
import com.roberto.gestorpro.data.firebase.MotivosDenuncia
import kotlinx.coroutines.launch

/**
 * DialogoDenuncia (ADMIN)
 * -----------------------
 * Diálogo de denuncia de contenido/usuario UGC: motivo obligatorio,
 * descripción opcional (máx. 500) y confirmación tras el envío. No depende de
 * correo externo: la denuncia se registra dentro de la app (denuncias/{id}).
 *
 * Recibe una lambda suspend que devuelve null en éxito o el error en español.
 */
@Composable
fun DialogoDenuncia(
    titulo: String,
    onDismiss: () -> Unit,
    onEnviar: suspend (motivo: String, descripcion: String?) -> String?
) {
    var motivo by remember { mutableStateOf<String?>(null) }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var enviada by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (!enviando && !enviada) onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            if (enviada) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Denuncia enviada. Gracias por tu colaboración.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppPrimaryButton(
                        text = "Aceptar",
                        onClick = onDismiss,
                        fullWidth = true
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E88E5),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Selecciona el motivo de la denuncia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    MotivosDenuncia.validos().forEach { opcion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !enviando) { motivo = opcion },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = motivo == opcion,
                                onClick = { motivo = opcion },
                                enabled = !enviando
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = MotivosDenuncia.etiqueta(opcion),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = {
                            if (it.length <= DenunciaRepository.MAX_DESCRIPCION) {
                                descripcion = it
                            }
                            error = null
                        },
                        enabled = !enviando,
                        label = { Text("Descripción (opcional)") },
                        supportingText = {
                            Text("${descripcion.length}/${DenunciaRepository.MAX_DESCRIPCION}")
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let { mensaje ->
                        Text(
                            text = mensaje,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppDialogTextButton(
                            text = "Cancelar",
                            enabled = !enviando,
                            onClick = onDismiss
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AppPrimaryButton(
                            text = if (enviando) "Enviando..." else "Enviar denuncia",
                            onClick = {
                                val motivoElegido = motivo
                                if (motivoElegido == null) {
                                    error = "Selecciona un motivo"
                                    return@AppPrimaryButton
                                }
                                error = null
                                enviando = true
                                scope.launch {
                                    val resultado = onEnviar(
                                        motivoElegido,
                                        descripcion.trim().ifEmpty { null }
                                    )
                                    if (resultado == null) {
                                        enviada = true
                                    } else {
                                        error = resultado
                                    }
                                    enviando = false
                                }
                            },
                            enabled = motivo != null && !enviando,
                            fullWidth = false
                        )
                    }
                }
            }
        }
    }
}
