package com.roberto.gestorpro.ui.gestioncentro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.R
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AyudaContextual
import com.roberto.gestorpro.ui.components.LogoNegocioAutenticado
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * MiNegocioScreen.kt
 * ------------------
 * ✔ TIPO: archivo de código fuente Kotlin (pantalla de personalización del negocio)
 * Es el archivo que define la pantalla donde el administrador configura el nombre y el logo.
 * Sirve para que la identidad del negocio se muestre en Home y Login.
 */

/**
 * MiNegocioScreen
 * ---------------
 * ✔ TIPO: función @Composable
 * Es la pantalla de personalización con el campo de nombre y el selector de logo.
 * Sirve para editar y guardar en DataStore (a través de MainViewModel) los datos
 * de identidad del negocio que ven tanto administrador como clientes.
 */
@Composable
fun MiNegocioScreen(
    /**
     * navController
     * -------------
     * ✔ TIPO: parámetro (param) → NavHostController
     * Es el controlador de navegación que recibe la pantalla.
     * Sirve para volver atrás hacia Configuración al terminar.
     */
    navController: NavHostController,
    /**
     * mainViewModel
     * -------------
     * ✔ TIPO: parámetro (param) → MainViewModel (inyectado por Hilt)
     * Es el ViewModel de preferencias de la app.
     * Sirve para leer el nombre y logo actuales y guardar los cambios en DataStore.
     */
    mainViewModel: MainViewModel = hiltViewModel()
) {

    /**
     * nombreActual / logoActual
     * -------------------------
     * ✔ TIPO: variables observables (val by collectAsStateWithLifecycle) → String
     * Son el nombre y la ruta del logo guardados actualmente en DataStore.
     * Sirven para precargar el formulario la primera vez que se abre la pantalla.
     */
    val nombreActual by mainViewModel.nombreNegocio.collectAsStateWithLifecycle()
    val logoActual by mainViewModel.logoNegocio.collectAsStateWithLifecycle()
    val operandoRemoto by mainViewModel.operandoRemoto.collectAsStateWithLifecycle()

    /**
     * negocioEnNube / codigoMaestroRemoto / mensajeRemoto
     * ---------------------------------------------------
     * ✔ TIPO: variables con estado (var) → Boolean? / String / String
     * Es el estado de la sincronización remota del negocio: null mientras
     * carga, false si el ADMIN aún no creó su negocio (modo alta) y true si
     * ya existe (modo edición del código maestro). mensajeRecoge errores.
     * Sirven para el modo dual de la pantalla.
     */
    var negocioEnNube by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var codigoMaestro by rememberSaveable { mutableStateOf("") }
    /**
     * codigoMaestroOriginal
     * ---------------------
     * Código maestro que el negocio tenía al abrir MiNegocio (modo edición).
     * Se pasa al guardar para liberar/reservar la reserva global
     * codigos_maestros de forma atómica.
     */
    var codigoMaestroOriginal by rememberSaveable { mutableStateOf<String?>(null) }
    var mensajeRemoto by rememberSaveable { mutableStateOf("") }

    /**
     * LaunchedEffect(estado del negocio remoto)
     * -----------------------------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Consulta una sola vez si existe el negocio remoto y precarga su
     * código maestro. Sirve para pintar el modo dual correcto.
     */
    LaunchedEffect(Unit) {
        if (mainViewModel.existeNegocioPropio()) {
            negocioEnNube = true
            mainViewModel.obtenerCodigoMaestroRemoto()?.let { codigo ->
                codigoMaestro = codigo
                codigoMaestroOriginal = codigo
            }
        } else {
            negocioEnNube = false
            codigoMaestroOriginal = null
        }
    }

    /**
     * nombre / logo
     * -------------
     * ✔ TIPO: variables con estado (var) → String
     * Son el nombre y la ruta del logo que se están editando en la pantalla.
     * Sirven para no escribir directamente sobre DataStore con cada pulsación;
     * solo se guarda al pulsar el botón "Guardar cambios".
     */
    var nombre by rememberSaveable { mutableStateOf("") }
    var logo by rememberSaveable { mutableStateOf("") }

    /**
     * precargado
     * ----------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es la marca de que ya se rellenaron los campos con los datos guardados.
     * Sirve para que LaunchedEffect solo precargue una vez y no sobrescriba
     * lo que el usuario está tecleando si el Flow vuelve a emitir.
     */
    var precargado by rememberSaveable { mutableStateOf(false) }

    /**
     * LaunchedEffect(nombreActual, logoActual)
     * ---------------------------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Se ejecuta cuando llegan por primera vez los datos guardados.
     * Sirve para rellenar el formulario una sola vez mientras precargado sea false.
     */
    LaunchedEffect(nombreActual, logoActual) {
        if (!precargado) {
            nombre = nombreActual
            logo = logoActual
            if (nombreActual.isNotBlank() || logoActual.isNotBlank()) {
                precargado = true
            }
        }
    }

    /**
     * context
     * -------
     * ✔ TIPO: variable (val) → Context
     * Es el contexto de la actividad obtenido a través de LocalContext.
     * Sirve para copiar el logo elegido al almacenamiento interno de la app.
     */
    val context = LocalContext.current

    /**
     * alcance
     * -------
     * ✔ TIPO: variable (val) → CoroutineScope
     * Es el scope ligado a la composición para lanzar el guardado del
     * código maestro. Sirve para no bloquear el hilo principal.
     */
    val alcance = rememberCoroutineScope()

    /**
     * launcherGaleria
     * ---------------
     * ✔ TIPO: variable (val) → ActivityResultLauncher<PickVisualMediaRequest>
     * Es el lanzador que abre el selector de fotos del sistema (Photo Picker).
     * Sirve para elegir el logo; al volver lo copia a memoria interna y guarda la ruta.
     */
    val launcherGaleria = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val ruta = guardarImagenEnInterna(context, uri)
            if (ruta != null) {
                logo = ruta
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            /**
             * Row de la cabecera
             * ------------------
             * ✔ TIPO: función @Composable (Row)
             * Es la fila superior que junta la flecha de volver con el título.
             * Sirve para retroceder a Configuración e indicar en qué pantalla estamos.
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppNavigationBackButton(
                    onClick = {
                        navController.popBackStack()
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.centro_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            /**
             * OutlinedTextField del nombre
             * ----------------------------
             * ✔ TIPO: función @Composable (OutlinedTextField)
             * Es el campo de texto con el nombre comercial del negocio.
             * Sirve para personalizar la cabecera de Home y Login; vacío muestra "GestorPro".
             */
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(stringResource(R.string.centro_nombre_label)) },
                placeholder = { Text(stringResource(R.string.app_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            /**
             * Text del título de la sección del logo
             * --------------------------------------
             * ✔ TIPO: función @Composable (Text)
             * Es el encabezado de la sección de selección de logo.
             * Sirve para separar visualmente el nombre del logo.
             */
            Text(
                text = stringResource(R.string.centro_logo_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            /**
             * Column de selección del logo
             * ----------------------------
             * ✔ TIPO: función @Composable (Column)
             * Es la sección que centra la vista previa del logo y sus botones.
             * Sirve para mostrar el logo actual o el icono por defecto si aún no hay ninguno.
             */
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logo.isNotBlank()) {
                    if (esUrlLogo(logo)) {
                        // Logo remoto: se descarga con el SDK autenticado/caché
                        // (mismo patrón que Home/Login), nunca con GET HTTP anónimo.
                        LogoNegocioAutenticado(
                            url = logo,
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            tamano = 140.dp,
                            bordeColor = Color(0xFF1E88E5),
                            bordeAncho = 2.dp
                        )
                    } else {
                        AsyncImage(
                            model = File(logo),
                            contentDescription = stringResource(R.string.centro_logo_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF1E88E5), CircleShape)
                        )
                    }
                } else {

                    /**
                     * Box del placeholder del logo
                     * ----------------------------
                     * ✔ TIPO: bloque condicional (else) + Composable (Box)
                     * Es el círculo con el icono por defecto cuando no hay logo elegido.
                     * Sirve para mantener la sección visualmente completa.
                     */
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, Color(0xFF1E88E5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /**
                     * OutlinedButton de seleccionar/cambiar logo
                     * ------------------------------------------
                     * ✔ TIPO: función @Composable (OutlinedButton)
                     * Es el botón que abre el selector de fotos del sistema.
                     * Sirve para elegir el logo por primera vez o sustituirlo.
                     */
                    OutlinedButton(
                        onClick = {
                            launcherGaleria.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Text(
                            if (logo.isBlank()) {
                                stringResource(R.string.centro_logo_seleccionar)
                            } else {
                                stringResource(R.string.centro_logo_cambiar)
                            }
                        )
                    }

                    /**
                     * OutlinedButton de quitar logo
                     * -----------------------------
                     * ✔ TIPO: función @Composable (OutlinedButton)
                     * Es el botón que vacía el logo pendiente de guardar.
                     * Sirve para volver al icono por defecto; solo se aplica al guardar.
                     */
                    if (logo.isNotBlank()) {
                        OutlinedButton(
                            onClick = { logo = "" }
                        ) {
                            Text(stringResource(R.string.centro_logo_quitar))
                        }
                    }
                }
            }

            /**
             * Estado remoto del negocio (modo dual)
             * --------------------------------------
             * null = comprobando, false = todavía no existe (alta inicial),
             * true = ya existe (edición). Se usa para decidir si el botón de
             * guardar crea o actualiza el negocio, sin pantallas separadas.
             */
            when (negocioEnNube) {
                null -> Text(
                    text = stringResource(R.string.centro_estado_comprobando),
                    style = MaterialTheme.typography.bodySmall
                )
                false -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.centro_estado_sin_crear),
                        style = MaterialTheme.typography.bodySmall
                    )
                    AyudaContextual(
                        titulo = stringResource(R.string.centro_crear),
                        texto = stringResource(R.string.centro_ayuda_crear)
                    )
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.centro_estado_creado),
                        style = MaterialTheme.typography.bodySmall
                    )
                    AyudaContextual(
                        titulo = stringResource(R.string.centro_guardar_cambios),
                        texto = stringResource(R.string.centro_ayuda_guardar)
                    )
                }
            }

            /**
             * Código maestro (siempre visible)
             * --------------------------------
             * En el alta inicial es el código con el que se creará el negocio en
             * la nube; una vez creado, el mismo campo permite modificarlo.
             */
            OutlinedTextField(
                value = codigoMaestro,
                onValueChange = { codigoMaestro = it },
                label = { Text(stringResource(R.string.centro_codigo_maestro_label)) },
                trailingIcon = {
                    AyudaContextual(
                        titulo = stringResource(R.string.centro_codigo_maestro_label),
                        texto = stringResource(R.string.centro_ayuda_codigo)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mensajeRemoto.isNotBlank()) {
                Text(
                    text = mensajeRemoto,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (mensajeRemoto.startsWith("Debes aceptar los Términos")) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { navController.navigate(Routes.TERMINOS_CONDICIONES) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.centro_ver_terminos))
                }
            }

            /**
             * Button de Guardar (crear o actualizar)
             * ---------------------------------------
             * Si el negocio NO existe en la nube lo crea (Batch de negocios/{id},
             * negocios_publicos/{id} y usuarios/{uid}.negocioId) y guarda el
             * nombre en DataStore. Si ya existe, actualiza nombre, código maestro
             * y logo en los dos documentos remotos. Un logo local nuevo se sube a
             * Firebase Storage y guarda su URL; una URL existente solo se conserva.
             */
            AppPrimaryButton(
                text = when (negocioEnNube) {
                    true -> stringResource(R.string.centro_guardar_cambios)
                    else -> stringResource(R.string.centro_crear)
                },
                onClick = {
                    alcance.launch {
                        mensajeRemoto = ""
                        val estadoNegocio = negocioEnNube ?: return@launch
                        if (nombre.isBlank()) {
                            mensajeRemoto = context.getString(R.string.centro_error_nombre_vacio)
                            return@launch
                        }
                        if (codigoMaestro.isBlank()) {
                            mensajeRemoto = context.getString(R.string.centro_error_codigo_vacio)
                            return@launch
                        }

                        var error: String? = null
                        if (estadoNegocio) {
                            // Edición: sincroniza nombre y código maestro. Se pasa
                            // el código original para liberar/reservar la reserva
                            // global de forma atómica y detectar códigos en uso.
                            val eNombre = mainViewModel.sincronizarNombreNegocio(nombre)
                            error = if (eNombre != null) {
                                eNombre
                            } else {
                                mainViewModel.guardarCodigoMaestro(
                                    codigoMaestro = codigoMaestro,
                                    codigoAnterior = codigoMaestroOriginal
                                )
                            }
                            if (error == null) {
                                codigoMaestroOriginal = codigoMaestro.trim()
                            }
                        } else {
                            // Alta inicial: crea el negocio en la nube y deja el
                            // estado en "ya existe" para poder reintentar el logo.
                            val eCrear = mainViewModel.crearNegocio(nombre, codigoMaestro)
                            if (eCrear != null) {
                                error = eCrear
                            } else {
                                negocioEnNube = true
                            }
                        }

                        // El logo se procesa solo si el nombre/código terminaron bien.
                        if (error == null) {
                            if (logo.isBlank()) {
                                mainViewModel.guardarLogoNegocio("")
                            } else if (esUrlLogo(logo)) {
                                mainViewModel.guardarLogoNegocio(logo)
                            } else {
                                error = mainViewModel.sincronizarLogoNegocio(logo)
                            }
                        }

                        if (error != null) {
                            mensajeRemoto = error
                        } else {
                            navController.popBackStack()
                        }
                    }
                },
                enabled = negocioEnNube != null &&
                    !operandoRemoto &&
                    nombre.isNotBlank() &&
                    codigoMaestro.isNotBlank(),
                fullWidth = false,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 16.dp)
            )
        }
    }
}

/**
 * guardarImagenEnInterna
 * ----------------------
 * ✔ TIPO: función privada (private fun) → String?
 * Es la función que copia la imagen elegida al almacenamiento interno de la app.
 * Sirve para que el logo no dependa del permiso temporal del URI, decodificándolo
 * con un tamaño reducido y devolviendo la ruta absoluta del archivo guardado.
 */
private fun guardarImagenEnInterna(context: Context, uri: Uri): String? {
    return try {

        /**
         * bounds
         * ------
         * ✔ TIPO: variable (val) → BitmapFactory.Options
         * Es la configuración que solo lee las dimensiones de la imagen sin cargarla completa.
         * Sirve para conocer el tamaño real del logo antes de redimensionarlo.
         */
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        /**
         * sample
         * ------
         * ✔ TIPO: variable (var) → Int
         * Es el factor de reducción aplicado al decodificar la imagen.
         * Sirve para no cargar en memoria una imagen gigante antes de escalarla.
         */
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_LOGO_DIMENSION ||
            bounds.outHeight / (sample * 2) >= MAX_LOGO_DIMENSION
        ) {
            sample *= 2
        }

        /**
         * bitmap
         * ------
         * ✔ TIPO: variable (val) → Bitmap
         * Es la imagen decodificada desde el URI con el tamaño reducido.
         * Sirve como base para generar el archivo final del logo.
         */
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        /**
         * archivo
         * -------
         * ✔ TIPO: variable (val) → File
         * Es el archivo PNG/JPEG final dentro de la carpeta "logos" interna.
         * Sirve para guardar el logo de forma permanente con nombre único.
         */
        val dir = File(context.filesDir, "logos").apply { mkdirs() }
        val archivo = File(dir, "logo_${System.currentTimeMillis()}.jpg")

        FileOutputStream(archivo).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_LOGO, output)
        }
        bitmap.recycle()

        archivo.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * esUrlLogo
 * ---------
 * ✔ TIPO: función privada (private fun) → Boolean
 * Indica si el valor del logo es una URL remota (https/http) o una ruta de
 * archivo local. Sirve para que AsyncImage cargue con Coil la URL remota o el
 * archivo local recién seleccionado según corresponda.
 */
private fun esUrlLogo(valor: String): Boolean {
    return valor.startsWith("http://") || valor.startsWith("https://")
}

/**
 * MAX_LOGO_DIMENSION
 * ------------------
 * ✔ TIPO: constante (private const val) → Int
 * Es la dimensión máxima en píxeles que puede tener el logo guardado.
 * Sirve para limitar el tamaño del archivo y su consumo de memoria.
 */
private const val MAX_LOGO_DIMENSION = 512

/**
 * CALIDAD_LOGO
 * ------------
 * ✔ TIPO: constante (private const val) → Int
 * Es el nivel de compresión JPEG aplicado al logo.
 * Sirve para equilibrar calidad y peso del archivo guardado.
 */
private const val CALIDAD_LOGO = 90
