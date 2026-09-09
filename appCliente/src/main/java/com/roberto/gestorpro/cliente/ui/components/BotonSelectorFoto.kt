package com.roberto.gestorpro.cliente.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roberto.gestorpro.cliente.R

/**
 * BotonSelectorFoto
 * -----------------
 * Botón "Seleccionar/Cambiar foto" que despliega el menú con las dos vías de
 * obtención de la foto: elegir de la galería o hacer una foto con la cámara.
 * Estilo coherente con GestorPro Admin.
 *
 * @param tieneFoto Indica si ya hay una foto seleccionada (cambia el texto del botón)
 * @param onElegirGaleria Acción al elegir "Elegir de galería"
 * @param onHacerFoto Acción al elegir "Hacer una foto"
 */
@Composable
fun BotonSelectorFoto(
    tieneFoto: Boolean,
    onElegirGaleria: () -> Unit,
    onHacerFoto: () -> Unit
) {
    var menuAbierto by remember { mutableStateOf(false) }

    val textoElegirFoto = stringResource(R.string.foto_elegir)
    val textoCambiarFoto = stringResource(R.string.foto_cambiar)
    val textoElegirGaleria = stringResource(R.string.foto_elegir_galeria)
    val textoHacerFoto = stringResource(R.string.foto_hacer_foto)

    Box {
        OutlinedButton(
            onClick = { menuAbierto = true }
        ) {
            Text(if (tieneFoto) textoCambiarFoto else textoElegirFoto)
        }

        DropdownMenu(
            expanded = menuAbierto,
            onDismissRequest = { menuAbierto = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = { Text(textoElegirGaleria) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color(0xFF1E88E5)
                    )
                },
                onClick = {
                    menuAbierto = false
                    onElegirGaleria()
                }
            )
            DropdownMenuItem(
                text = { Text(textoHacerFoto) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color(0xFF1E88E5)
                    )
                },
                onClick = {
                    menuAbierto = false
                    onHacerFoto()
                }
            )
        }
    }
}
