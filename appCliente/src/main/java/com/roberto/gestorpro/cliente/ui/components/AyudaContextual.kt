package com.roberto.gestorpro.cliente.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Azul corporativo de Trazys para el icono de ayuda. */
private val AzulAyuda = Color(0xFF1E88E5)

/** A partir de esta longitud (o con saltos de línea) la ayuda se muestra en diálogo. */
private const val LONGITUD_TEXTO_CORTO = 140

/**
 * AyudaContextual
 * ---------------
 * Icono de información (ⓘ) reutilizable para explicaciones secundarias que, si
 * estuvieran siempre visibles, ocuparían espacio innecesario.
 *
 * - Textos cortos: se muestran como tooltip al pulsar el icono.
 * - Textos largos o con varios renglones: se muestran en un diálogo.
 *
 * No debe usarse para información esencial, advertencias, errores,
 * confirmaciones ni textos legales: eso permanece visible.
 *
 * @param titulo Título del diálogo y descripción de accesibilidad del icono.
 * @param texto Contenido de la ayuda.
 * @param modifier Modificador opcional para colocar el icono en línea.
 * @param textoCerrar Texto del botón para cerrar el diálogo (localizable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyudaContextual(
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    textoCerrar: String = "Entendido"
) {
    val tooltipState = rememberTooltipState()
    val alcance = rememberCoroutineScope()
    var dialogoAbierto by remember { mutableStateOf(false) }
    val esTextoCorto = !texto.contains('\n') && texto.length <= LONGITUD_TEXTO_CORTO

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = texto, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = tooltipState,
        enableUserInput = false
    ) {
        IconButton(
            onClick = {
                if (esTextoCorto) {
                    alcance.launch { tooltipState.show() }
                } else {
                    dialogoAbierto = true
                }
            },
            modifier = modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = titulo,
                tint = AzulAyuda,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (dialogoAbierto) {
        AlertDialog(
            onDismissRequest = { dialogoAbierto = false },
            confirmButton = {
                TextButton(onClick = { dialogoAbierto = false }) { Text(textoCerrar) }
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = AzulAyuda
                )
            },
            title = { Text(text = titulo, fontWeight = FontWeight.SemiBold) },
            text = { Text(text = texto, style = MaterialTheme.typography.bodyMedium) }
        )
    }
}
