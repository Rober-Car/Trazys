package com.roberto.gestorpro.cliente.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.ui.res.stringResource
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.navigation.Routes
import com.roberto.gestorpro.cliente.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.cliente.ui.viewmodel.MainViewModel

/**
 * ConfiguracionScreen
 * -------------------
 * Pantalla de ajustes de GestorPro Cliente.
 * Permite cambiar el tema de la aplicación (claro, oscuro o sistema),
 * reutilizando el tema y el estilo visual de GestorPro Admin.
 */
@Composable
fun ConfiguracionScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val idioma by mainViewModel.idioma.collectAsStateWithLifecycle()

    // Textos localizados del bloque de configuración.
    val textoAjustes = stringResource(R.string.config_titulo)
    val textoSeccionCuenta = stringResource(R.string.config_seccion_cuenta)
    val textoSeccionPreferencias = stringResource(R.string.config_seccion_preferencias)
    val textoSeccionApariencia = stringResource(R.string.config_seccion_apariencia)
    val textoSeccionIdioma = stringResource(R.string.config_seccion_idioma)
    val textoSeccionInformacion = stringResource(R.string.config_seccion_informacion)
    val textoMiPerfil = stringResource(R.string.perfil_mi_perfil)
    val textoMiPerfilDesc = stringResource(R.string.config_mi_perfil_descripcion)
    val textoMiCuenta = stringResource(R.string.cuenta_titulo)
    val textoMiCuentaDesc = stringResource(R.string.config_mi_cuenta_descripcion)
    val textoNotificaciones = stringResource(R.string.home_card_notificaciones)
    val textoNotificacionesDesc = stringResource(R.string.config_notificaciones_descripcion)
    val textoClaro = stringResource(R.string.config_tema_claro)
    val textoClaroDesc = stringResource(R.string.config_tema_claro_descripcion)
    val textoOscuro = stringResource(R.string.config_tema_oscuro)
    val textoOscuroDesc = stringResource(R.string.config_tema_oscuro_descripcion)
    val textoSistema = stringResource(R.string.config_tema_sistema)
    val textoSistemaDesc = stringResource(R.string.config_tema_sistema_descripcion)
    val textoEspanol = stringResource(R.string.config_idioma_es)
    val textoEnglish = stringResource(R.string.config_idioma_en)
    val textoPoliticaPrivacidad = stringResource(R.string.auth_enlace_politica_privacidad)
    val textoTerminosCondiciones = stringResource(R.string.config_terminos_condiciones)
    val textoEliminarMiCuenta = stringResource(R.string.eliminar_titulo)
    val textoEliminarDesc = stringResource(R.string.config_eliminar_cuenta_descripcion)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                        text = textoAjustes,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = textoSeccionCuenta,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                AjusteNavigationOption(
                    icon = Icons.Filled.Person,
                    label = textoMiPerfil,
                    description = textoMiPerfilDesc,
                    tint = Color(0xFF2196F3),
                    onClick = { navController.navigate(Routes.MI_PERFIL) }
                )
                HorizontalDivider()
                AjusteNavigationOption(
                    icon = Icons.Filled.AccountCircle,
                    label = textoMiCuenta,
                    description = textoMiCuentaDesc,
                    tint = Color(0xFF1E88E5),
                    onClick = { navController.navigate(Routes.CUENTA) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = textoSeccionPreferencias,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                AjusteNavigationOption(
                    icon = Icons.Filled.Notifications,
                    label = textoNotificaciones,
                    description = textoNotificacionesDesc,
                    tint = Color(0xFF7E57C2),
                    onClick = {
                        navController.navigate(Routes.CONFIGURACION_NOTIFICACIONES)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = textoSeccionApariencia,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    TemaOption(
                        icon = Icons.Filled.LightMode,
                        label = textoClaro,
                        description = textoClaroDesc,
                        selected = themeMode == PreferencesRepository.THEME_CLARO,
                        onClick = { mainViewModel.setThemeMode(PreferencesRepository.THEME_CLARO) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    TemaOption(
                        icon = Icons.Filled.DarkMode,
                        label = textoOscuro,
                        description = textoOscuroDesc,
                        selected = themeMode == PreferencesRepository.THEME_OSCURO,
                        onClick = { mainViewModel.setThemeMode(PreferencesRepository.THEME_OSCURO) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    TemaOption(
                        icon = Icons.Filled.SettingsBrightness,
                        label = textoSistema,
                        description = textoSistemaDesc,
                        selected = themeMode == PreferencesRepository.THEME_SISTEMA,
                        onClick = { mainViewModel.setThemeMode(PreferencesRepository.THEME_SISTEMA) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = textoSeccionIdioma,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    IdiomaOption(
                        nombre = textoEspanol,
                        selected = idioma == PreferencesRepository.IDIOMA_ES,
                        onClick = { mainViewModel.setIdioma(PreferencesRepository.IDIOMA_ES) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    IdiomaOption(
                        nombre = textoEnglish,
                        selected = idioma == PreferencesRepository.IDIOMA_EN,
                        onClick = { mainViewModel.setIdioma(PreferencesRepository.IDIOMA_EN) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = textoSeccionInformacion,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                AjusteInformacionOption(
                    icon = Icons.Filled.PrivacyTip,
                    label = textoPoliticaPrivacidad,
                    onClick = { navController.navigate(Routes.POLITICA_PRIVACIDAD) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AjusteInformacionOption(
                    icon = Icons.Filled.Description,
                    label = textoTerminosCondiciones,
                    onClick = { navController.navigate(Routes.TERMINOS_CONDICIONES) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.ELIMINAR_CUENTA) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = textoEliminarMiCuenta,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = textoEliminarDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AjusteNavigationOption(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun AjusteInformacionOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TemaOption(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val azul = Color(0xFF1E88E5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) azul.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) azul else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) azul else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = azul)
        )
    }
}

@Composable
private fun IdiomaOption(
    nombre: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val azul = Color(0xFF1E88E5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) azul.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = nombre,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) azul else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = azul)
        )
    }
}
