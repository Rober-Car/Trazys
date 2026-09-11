package com.roberto.gestorpro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * SinNegocioContenido
 * -------------------
 * Interfaz explicativa que bloquea una acción de gestión cuando el ADMIN
 * todavía no tiene creado su negocio en la nube. Muestra el motivo y ofrece el
 * botón "Crear mi negocio" para navegar a MiNegocioScreen (flujo único de
 * creación de negocio: nombre + logo + código maestro).
 *
 * La usa el alta de clientes (AñadirClienteScreen) y, con textos adaptados, la
 * pantalla de Servicios/Actividades para impedir crear un servicio sin negocio.
 * Por defecto mantiene los textos del flujo de Clientes.
 */
@Composable
fun SinNegocioContenido(
    titulo: String = "No puedes crear clientes todavía.",
    mensaje: String = "Primero debes crear tu centro para poder dar de alta clientes.",
    onCrearNegocio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(24.dp))
        AppPrimaryButton(
            text = "Crear mi centro",
            onClick = onCrearNegocio,
            fullWidth = false
        )
    }
}
