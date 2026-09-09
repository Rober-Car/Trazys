package com.roberto.gestorpro.cliente.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roberto.gestorpro.cliente.R

/**
 * Sistema de botones reutilizables de GestorPro Cliente.
 *
 * Variantes principales:
 * - [AppPrimaryButton]: Acción principal de pantalla (Guardar, Crear, Continuar)
 * - [AppSecondaryButton]: Acciones secundarias (Editar, Renovar)
 * - [AppDangerButton]: Acciones destructivas (Eliminar, Dar de baja)
 * - [AppCompactButton]: Acciones pequeñas dentro de Cards
 * - [AppIconPrimaryButton]: Botón solo icono con color primario
 * - [AppIconDangerButton]: Botón solo icono destructivo
 * - [AppNavigationBackButton]: Flecha de navegación atrás
 * - [AppTextLinkButton]: Enlaces de navegación (texto color primario)
 *
 * Características comunes:
 * - Misma familia de formas (RoundedCornerShape 12.dp)
 * - Misma tipografía coherente
 * - Colores del tema Material 3 (nunca hardcodeados)
 * - Estados pressed/disabled coherentes
 */

// ============================================================================
// CONSTANTES DEL SISTEMA
// ============================================================================

/** Radio de esquinas estándar para botones */
object ButtonShapes {
    val standard = RoundedCornerShape(12.dp)
    val compact = RoundedCornerShape(8.dp)
}

/** Alturas estándar por categoría */
object ButtonHeights {
    val large = 52.dp
    val medium = 44.dp
    val compact = 36.dp
}

/** Tamaños de iconos */
object IconSizes {
    val standard = 20.dp
    val navigation = 24.dp
}

/** Padding estándar */
object ButtonPadding {
    val horizontalLarge = 24.dp
    val horizontalMedium = 16.dp
    val horizontalCompact = 12.dp
}

/**
 * Azul corporativo de GestPro (#1E88E5), el mismo que utiliza `:app`.
 * El colorScheme.primary puede ser dinámico (Material You), por eso el botón
 * principal usa este azul fijo de la aplicación.
 */
private val AzulPrimarioGestPro = Color(0xFF1E88E5)

// ============================================================================
// 1. PRIMARY BUTTON
// ============================================================================

/**
 * Botón principal de acción.
 * Se usa para la acción más importante de una pantalla:
 * Guardar, Crear, Continuar, Confirmar.
 *
 * @param text Texto del botón
 * @param onClick Acción al pulsar
 * @param modifier Modificador personalizado
 * @param enabled Si el botón está habilitado (por defecto true)
 * @param icon Icono opcional a la izquierda del texto
 * @param fullWidth Si ocupa todo el ancho (por defecto true)
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            if (fullWidth) Modifier.fillMaxWidth() else Modifier
        ),
        enabled = enabled,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.buttonColors(
            containerColor = AzulPrimarioGestPro,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalLarge,
            vertical = 14.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.standard)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// 2. SECONDARY BUTTON (Outlined)
// ============================================================================

/**
 * Botón secundario con borde.
 * Se usa para acciones importantes pero no principales:
 * Editar, Renovar, Ver detalles, Reintentar.
 *
 * @param text Texto del botón
 * @param onClick Acción al pulsar
 * @param modifier Modificador personalizado
 * @param enabled Si el botón está habilitado
 * @param icon Icono opcional a la izquierda del texto
 * @param fullWidth Si ocupa todo el ancho
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(
            if (fullWidth) Modifier.fillMaxWidth() else Modifier
        ),
        enabled = enabled,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalMedium,
            vertical = 12.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.standard)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// 3. TONAL BUTTON (Secundaria con fondo sutil)
// ============================================================================

/**
 * Botón con tono sutil (FilledTonalButton).
 * Se usa para acciones secundarias que necesitan más peso visual
 * que OutlinedButton pero menos que Primary.
 *
 * @param text Texto del botón
 * @param onClick Acción al pulsar
 * @param modifier Modificador personalizado
 * @param enabled Si el botón está habilitado
 * @param icon Icono opcional
 * @param fullWidth Si ocupa todo el ancho
 */
@Composable
fun AppTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.then(
            if (fullWidth) Modifier.fillMaxWidth() else Modifier
        ),
        enabled = enabled,
        shape = ButtonShapes.standard,
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalMedium,
            vertical = 12.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.standard)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// 4. DANGER BUTTON
// ============================================================================

/**
 * Botón destructivo.
 * Se usa para acciones irreversibles o de baja:
 * Eliminar, Dar de baja, Borrar.
 *
 * Utiliza los colores de error del tema Material 3.
 *
 * @param text Texto del botón
 * @param onClick Acción al pulsar
 * @param modifier Modificador personalizado
 * @param enabled Si el botón está habilitado
 * @param icon Icono opcional
 * @param fullWidth Si ocupa todo el ancho
 */
@Composable
fun AppDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            if (fullWidth) Modifier.fillMaxWidth() else Modifier
        ),
        enabled = enabled,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalMedium,
            vertical = 12.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.standard)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Botón destructivo outlined (menos peso visual que AppDangerButton).
 */
@Composable
fun AppDangerOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fullWidth: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(
            if (fullWidth) Modifier.fillMaxWidth() else Modifier
        ),
        enabled = enabled,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalMedium,
            vertical = 12.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.standard)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ============================================================================
// 5. COMPACT BUTTON
// ============================================================================

/**
 * Botón compacto para acciones pequeñas dentro de Cards.
 */
@Composable
fun AppCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShapes.compact,
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalCompact,
            vertical = 6.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp
        )
    }
}

/**
 * Botón compacto tonal (con fondo sutil).
 */
@Composable
fun AppCompactTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShapes.compact,
        contentPadding = PaddingValues(
            horizontal = ButtonPadding.horizontalCompact,
            vertical = 6.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp
        )
    }
}

// ============================================================================
// 6. ICON BUTTONS
// ============================================================================

/**
 * Botón de navegación hacia atrás (flecha).
 */
@Composable
fun AppNavigationBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.accion_volver),
            modifier = Modifier.size(IconSizes.navigation)
        )
    }
}

/**
 * Botón solo icono con color primario.
 */
@Composable
fun AppIconPrimaryButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSizes.standard)
        )
    }
}

/**
 * Botón solo icono destructivo.
 */
@Composable
fun AppIconDangerButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSizes.standard)
        )
    }
}

/**
 * Botón solo icono genérico (sin color especial).
 */
@Composable
fun AppIconNeutralButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSizes.standard)
        )
    }
}

// ============================================================================
// 7. TEXT LINK BUTTON
// ============================================================================

/**
 * Enlace de texto con color primario.
 */
@Composable
fun AppTextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ============================================================================
// 8. TEXT BUTTON (para diálogos)
// ============================================================================

/**
 * Botón de texto para diálogos.
 */
@Composable
fun AppDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(text = text)
    }
}

// ============================================================================
// 9. BOTÓN DE CONFIRMACIÓN EN DIÁLOGOS (con color)
// ============================================================================

/**
 * Botón de confirmación en diálogos destructivos.
 */
@Composable
fun AppDialogDangerConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Botón de confirmación en diálogos normales.
 */
@Composable
fun AppDialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonShapes.standard,
        colors = ButtonDefaults.buttonColors(
            containerColor = AzulPrimarioGestPro,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// 10. BOTÓN DE ACCIÓN EN LISTAS (con color semántico)
// ============================================================================

/**
 * Botón de acción con color semántico para listas.
 */
@Composable
fun AppSemanticButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// ============================================================================
// COMPONENTES AUXILIARES
// ============================================================================

/**
 * Fila de botones de acción (secundarios alineados horizontalmente).
 */
@Composable
fun AppActionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.End,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Botón de texto con icono para acciones secundarias en listas.
 */
@Composable
fun AppTextIconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}
