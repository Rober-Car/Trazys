/* ============================================================
 * ============ BLOQUE 1: IMPORTS =============================
 * ============================================================ */
package com.roberto.gestorpro.ui.clientes

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.roberto.gestorpro.data.entity.ClienteEntity
import com.roberto.gestorpro.data.firebase.FotoClienteStorage
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.navigation.Routes
import com.roberto.gestorpro.ui.components.AppDialogDangerConfirmButton
import com.roberto.gestorpro.ui.components.AppDialogTextButton
import com.roberto.gestorpro.ui.components.AppNavigationBackButton
import com.roberto.gestorpro.ui.components.AppPrimaryButton
import com.roberto.gestorpro.ui.components.AppSecondaryButton
import com.roberto.gestorpro.ui.components.BotonSelectorFoto
import com.roberto.gestorpro.ui.components.SinNegocioContenido
import com.roberto.gestorpro.ui.utils.crearFotoTemporal
import com.roberto.gestorpro.ui.utils.guardaFotoEnInterna
import com.roberto.gestorpro.ui.utils.guardarFotoDeCamara
import com.roberto.gestorpro.ui.utils.uriDeFotoTemporal
import com.roberto.gestorpro.ui.viewmodel.ClienteViewModel
import com.roberto.gestorpro.ui.viewmodel.MainViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/* ============================================================
 * ============ BLOQUE 2: DOCUMENTACIÓN DEL ARCHIVO ===========
 * ============================================================ */
/**
 * AñadirClienteScreen.kt
 * ----------------------
 * ✔ TIPO: archivo de código fuente Kotlin (pantalla de alta de clientes)
 * Es el archivo que define la pantalla para añadir un nuevo cliente.
 * Sirve para capturar los datos del cliente antes de guardarlos en la base de datos.
 */

/* ============================================================
 * ============ BLOQUE 3: PANTALLA AÑADIR CLIENTE =============
 * ============================================================ */
/**
 * AñadirClienteScreen
 * -------------------
 * ✔ TIPO: función @Composable
 * Es la pantalla de alta de un cliente con los campos del formulario.
 * Sirve para que el administrador introduzca los datos de un nuevo cliente
 * y los prepare para ser guardados a través del ClienteViewModel.
 */
@Composable
fun AñadirClienteScreen(


    navController: NavHostController,
    /**
     * idCliente
     * ---------
     * ✔ TIPO: parámetro (param) → Int?
     * Es el identificador del cliente que se está editando.
     * Sirve para que esta pantalla funcione en dos modos: si vale null se añade un
     * cliente nuevo, y si trae un id se precargan los datos y se actualiza ese cliente.
     */
    idCliente: Int? = null,
    /**
     * viewModel
     * ---------
     * ✔ TIPO: parámetro (param) → ClienteViewModel (inyectado por Hilt)
     * Es el ViewModel de clientes que recibe la pantalla.
     * Sirve para guardar el nuevo cliente en la base de datos cuando se complete el formulario.
     */
    viewModel: ClienteViewModel = hiltViewModel(),
    /**
     * mainViewModel
     * -------------
     * ✔ TIPO: parámetro (param) → MainViewModel (inyectado por Hilt)
     * Es el ViewModel general de la app.
     * Sirve para comprobar que el administrador tiene un negocio creado antes de
     * permitir el alta de un cliente nuevo.
     */
    mainViewModel: MainViewModel = hiltViewModel()

) {

    /* ============================================================
     * ============ BLOQUE 4: ESTADO DEL FORMULARIO ===============
     * ============================================================ */
    /**
     * nombre
     * ------
     * ✔ TIPO: variable con estado (var) → String
     * Es el nombre del cliente que se escribe en el campo.
     * Sirve para guardar el texto del campo "Nombre" mientras se rellena el formulario.
     * Usa rememberSaveable para que el texto no se pierda al girar la pantalla
     * ni al pasar la app a segundo plano (se guarda en el Bundle del sistema).
     */
    var nombre by rememberSaveable { mutableStateOf("") }

    /**
     * apellidos
     * ---------
     * ✔ TIPO: variable con estado (var) → String
     * Es el apellido o apellidos del cliente que se escriben en el campo.
     * Sirve para guardar el texto del campo "Apellidos" mientras se rellena el formulario.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var apellidos by rememberSaveable { mutableStateOf("") }

    /**
     * dni
     * ---
     * ✔ TIPO: variable con estado (var) → String
     * Es el DNI del cliente que se escribe en el campo.
     * Sirve para guardar el texto del campo "DNI" mientras se rellena el formulario.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var dni by rememberSaveable { mutableStateOf("") }

    /**
     * telefono
     * --------
     * ✔ TIPO: variable con estado (var) → String
     * Es el teléfono del cliente que se escribe en el campo.
     * Sirve para guardar el texto del campo "Teléfono" mientras se rellena el formulario.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var telefono by rememberSaveable { mutableStateOf("") }

    /**
     * email
     * -----
     * ✔ TIPO: variable con estado (var) → String
     * Es el email del cliente que se escribe en el campo.
     * Sirve para guardar el texto del campo "Email" mientras se rellena el formulario.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var email by rememberSaveable { mutableStateOf("") }

    /**
     * fechaNacimiento
     * ---------------
     * ✔ TIPO: variable con estado (var) → Long?
     * Es la fecha de nacimiento del cliente convertida a timestamp.
     * Sirve para guardar la fecha en milisegundos; es null mientras no se escriba una fecha válida.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla (Long? admite el valor null).
     */
    var fechaNacimiento by rememberSaveable { mutableStateOf<Long?>(null) }

    /**
     * mostrarDatePicker
     * -----------------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es la variable que controla si el selector de fecha está visible.
     * Sirve para abrir y cerrar el DatePickerDialog al tocar el campo de fecha.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var mostrarDatePicker by rememberSaveable { mutableStateOf(false) }

    /**
     * mostrarConfirmarBaja
     * --------------------
     * Controla el diálogo de confirmación al pasar un cliente ACTIVO a BAJA.
     */
    var mostrarConfirmarBaja by rememberSaveable { mutableStateOf(false) }

    /**
     * fechaNacimientoFormateada
     * -------------------------
     * ✔ TIPO: variable con estado (val) → String
     * Es la fecha de nacimiento ya convertida en texto legible.
     * Sirve para mostrar la fecha seleccionada dentro del campo de texto de solo lectura.
     */
    val fechaNacimientoFormateada = remember(fechaNacimiento) {
        fechaNacimiento?.let { formatearFecha(it) } ?: ""
    }

    /**
     * esActivo
     * --------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el interruptor que indica si el cliente está activo o de baja.
     * Sirve para elegir el estado del cliente al darlo de alta o al modificarlo.
     * true = ACTIVO, false = BAJA.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var esActivo by rememberSaveable { mutableStateOf(true) }

    /**
     * observaciones
     * -------------
     * ✔ TIPO: variable con estado (var) → String
     * Es el texto de observaciones que se escribe en el campo.
     * Sirve para guardar las notas opcionales sobre el cliente.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var observaciones by rememberSaveable { mutableStateOf("") }

    /**
     * errorNombre
     * -----------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo Nombre.
     * Sirve para resaltar el campo y mostrar "El nombre es obligatorio" si está vacío al pulsar Guardar.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorNombre by rememberSaveable { mutableStateOf(false) }

    /**
     * errorApellidos
     * --------------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo Apellidos.
     * Sirve para resaltar el campo y mostrar "Los apellidos son obligatorios" si está vacío al pulsar Guardar.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorApellidos by rememberSaveable { mutableStateOf(false) }

    /**
     * errorDni
     * --------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo DNI.
     * Sirve para resaltar el campo y mostrar "Introduce un DNI válido" si el DNI no es correcto.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorDni by rememberSaveable { mutableStateOf(false) }

    /**
     * errorTelefono
     * -------------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo Teléfono.
     * Sirve para resaltar el campo y mostrar "Introduce un teléfono válido" si el teléfono no es correcto.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorTelefono by rememberSaveable { mutableStateOf(false) }

    /**
     * errorEmail
     * ----------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo Email.
     * Sirve para resaltar el campo y mostrar "Introduce un email válido" si el email no es correcto.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorEmail by rememberSaveable { mutableStateOf(false) }

    /**
     * errorFechaNacimiento
     * --------------------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error del campo Fecha de nacimiento.
     * Sirve para resaltar el campo y mostrar "La fecha de nacimiento es obligatoria" si no se elige ninguna.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorFechaNacimiento by rememberSaveable { mutableStateOf(false) }

    /**
     * errorFoto
     * ---------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de error de la foto del cliente.
     * Sirve para avisar de que la foto es obligatoria si no se selecciona ninguna al pulsar Guardar.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorFoto by rememberSaveable { mutableStateOf(false) }

    /**
     * errorCargaEdicion
     * -----------------
     * ✔ TIPO: variable con estado (var) → Boolean
     * Es el indicador de que en modo edición aún no se ha cargado el cliente.
     * Sirve para bloquear el guardado si el formulario no ha podido precargar los datos
     * y avisar al usuario en lugar de sobrescribir el cliente con valores vacíos.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var errorCargaEdicion by rememberSaveable { mutableStateOf(false) }

    /**
     * mensajeErrorDni
     * ---------------
     * ✔ TIPO: variable con estado (var) → String?
     * Es el mensaje de error mostrado debajo del campo DNI (p.ej. "El DNI ya está registrado").
     * Sirve para avisar al usuario justo donde está el problema, además de la snackbar.
     * Usa rememberSaveable para sobrevivir a la rotación de pantalla.
     */
    var mensajeErrorDni by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * error
     * -----
     * ✔ TIPO: variable con estado (val) → String?
     * Es el mensaje de error que llega desde el ClienteViewModel (p.ej. DNI duplicado).
     * Sirve para mostrarlo al usuario cuando el guardado no se puede completar.
     */
    val error by viewModel.error.collectAsState()
    val guardandoAlta by viewModel.guardandoAlta.collectAsState()

    /**
     * snackbarHostState
     * -----------------
     * ✔ TIPO: variable con estado (val) → SnackbarHostState
     * Es el estado del host de snackbars de la pantalla.
     * Sirve para mostrar los avisos flotantes sin que queden ocultos bajo el scroll.
     */
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    /**
     * LaunchedEffect(error)
     * ---------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Se lanza cada vez que el mensaje de error del ViewModel cambia.
     * Sirve para mostrar el aviso en una snackbar flotante y, si el error es de DNI,
     * marcarlo además bajo el propio campo DNI para que siempre sea visible.
     */
    LaunchedEffect(error) {
        error?.let { mensaje ->
            snackbarHostState.showSnackbar(mensaje)
            if (mensaje == "El DNI ya está registrado") {
                errorDni = true
                mensajeErrorDni = mensaje
            }
        }
    }

    /**
     * LaunchedEffect(errorCargaEdicion)
     * ---------------------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Se lanza cuando se intenta guardar en edición sin haber cargado los datos.
     * Sirve para avisar con una snackbar en lugar de una tarjeta oculta al final del scroll.
     */
    LaunchedEffect(errorCargaEdicion) {
        if (errorCargaEdicion) {
            snackbarHostState.showSnackbar(
                "No se han cargado los datos del cliente. Vuelve atrás y entra de nuevo."
            )
        }
    }

    /**
     * clienteEditando
     * ---------------
     * ✔ TIPO: variable con estado (val) → ClienteEntity?
     * Es el cliente que se está editando, cargado desde la base de datos.
     * Sirve para precargar el formulario con sus datos en modo edición.
     */
    val clienteEditando by viewModel.clienteEditando.collectAsState()

    /**
     * LaunchedEffect(idCliente)
     * -------------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Se lanza cuando la pantalla se muestra por primera vez.
     * Sirve para cargar en modo edición el cliente cuyo id llega en el argumento de navegación.
     * Además limpia el mensaje de error al entrar, para que no aparezca un aviso obsoleto
     * de una visita anterior a esta pantalla.
     */
    LaunchedEffect(idCliente) {
        viewModel.limpiarError()
        if (idCliente != null) {
            viewModel.obtenerClienteParaEditar(idCliente)
        }
    }

    /**
     * negocioOk
     * ---------
     * ✔ TIPO: variable con estado (var) → Boolean?
     * Indica si el administrador tiene un negocio creado antes de permitir el
     * alta. null = comprobando; false = sin negocio (se bloquea el alta);
     * true = negocio válido. En modo edición no se comprueba (si hay clientes
     * que editar es porque ya existe negocio).
     */
    var negocioOk by remember {
        mutableStateOf<Boolean?>(if (idCliente != null) true else null)
    }

    /**
     * LaunchedEffect(negocioOk)
     * -------------------------
     * Comprueba, al entrar en el alta, que el administrador tiene un negocio
     * creado en la nube. Si no lo tiene, la pantalla muestra el aviso y no
     * permite llegar al botón de guardar.
     */
    LaunchedEffect(Unit) {
        if (idCliente == null) {
            negocioOk = mainViewModel.existeNegocioPropio()
        }
    }


    /* ============================================================
     * ============ BLOQUE 5: LÓGICA DE FECHA Y GALERÍA ===========
     * ============================================================ */
    /**
     * selectableDates
     * ---------------
     * ✔ TIPO: variable con estado (val) → SelectableDates
     * Es la regla que decide qué días y años puede elegir el usuario en el calendario.
     * Sirve para impedir fechas futuras y fechas anteriores a 120 años.
     */
    val selectableDates = remember {
        val hoy = LocalDate.now()
        val hoyUtc = hoy.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val fechaMinimaUtc = hoy.minusYears(120).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in fechaMinimaUtc..hoyUtc

            override fun isSelectableYear(year: Int): Boolean =
                year in (hoy.minusYears(120).year..hoy.year)
        }
    }

    /**
     * context
     * -------
     * ✔ TIPO: variable (val) → Context
     * Es el contexto de la actividad obtenido a través de LocalContext.
     * Sirve para acceder al almacenamiento interno de la app y guardar la foto copiada.
     */
    val context = LocalContext.current

    /**
     * foto
     * ----
     * ✔ TIPO: variable con estado (var) → String
     * Es la ruta del archivo de la foto elegida por el usuario.
     * Sirve para guardar en el formulario la foto seleccionada y mostrarla en la vista previa.
     * Usa rememberSaveable para conservar la ruta al girar la pantalla (el archivo ya está
     * copiado en el almacenamiento interno, así que solo se guarda la ruta, no la imagen).
     */
    var foto by rememberSaveable { mutableStateOf("") }

    /**
     * fotoRemotaPreview
     * -----------------
     * ✔ TIPO: variable con estado (var) → File?
     * Es el fichero local (descargado con el SDK autenticado y cacheado) que
     * representa la foto REMOTA del cliente al editar. Solo se usa para la
     * previsualización: el estado `foto` conserva la URL para el guardado.
     */
    var fotoRemotaPreview by remember(idCliente) { mutableStateOf<File?>(null) }

    /**
     * fotoTemporal
     * ------------
     * ✔ TIPO: variable con estado (var) → File?
     * Es el archivo temporal que rellena la app de cámara al hacer una foto.
     * Sirve para guardar la referencia hasta que el resultado del lanzador
     * de cámara devuelve el control y procesar la foto capturada.
     */
    var fotoTemporal by remember { mutableStateOf<File?>(null) }

    /**
     * launcherGaleria
     * ---------------
     * ✔ TIPO: variable (val) → ActivityResultLauncher<PickVisualMediaRequest>
     * Es el lanzador que abre el selector de fotos del sistema (Photo Picker).
     * Sirve para que el usuario elija una imagen; al volver, guarda la foto en
     * el almacenamiento interno y muestra la ruta en la variable foto.
     */
    val launcherGaleria = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val ruta = guardaFotoEnInterna(context, uri)
            if (ruta != null) {
                foto = ruta
            }
        }
    }

    /**
     * launcherTomarFoto
     * -----------------
     * ✔ TIPO: variable (val) → ActivityResultLauncher<Uri>
     * Es el lanzador que abre la app de cámara para hacer una foto nueva.
     * Sirve para capturar la imagen en el archivo temporal y, si la foto se
     * tomó correctamente, guardarla en el almacenamiento interno.
     */
    val launcherTomarFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { resultado ->
        if (resultado) {
            val ruta = guardarFotoDeCamara(context, fotoTemporal)
            if (ruta != null) {
                foto = ruta
            }
        } else {
            fotoTemporal?.delete()
        }
        fotoTemporal = null
    }

    /**
     * LaunchedEffect(clienteEditando)
     * -------------------------------
     * ✔ TIPO: efecto de composición (LaunchedEffect)
     * Se ejecuta cada vez que clienteEditando cambia (cuando se carga el cliente).
     * Sirve para rellenar los campos del formulario con los datos del cliente en modo edición.
     * Además reinicia errorCargaEdicion: si la carga ya se completó, el guardado vuelve a
     * estar permitido y desaparece el aviso de que faltaban los datos.
     */
    LaunchedEffect(clienteEditando) {
        errorCargaEdicion = false
        clienteEditando?.let { clienteCargado ->
            nombre = clienteCargado.nombre
            apellidos = clienteCargado.apellidos
            dni = clienteCargado.dni
            telefono = clienteCargado.telefono
            email = clienteCargado.email ?: ""
            foto = clienteCargado.foto
            fechaNacimiento = clienteCargado.fechaNacimiento
            esActivo = clienteCargado.estado != EstadoCliente.BAJA
            observaciones = clienteCargado.observaciones ?: ""

            // Foto remota (URL de Storage): se resuelve a un fichero local de la
            // caché autenticada SOLO para la previsualización; el estado `foto`
            // conserva la URL para el guardado (actualizarCliente no re-subirá).
            fotoRemotaPreview = if (FotoClienteStorage.esUrlFoto(clienteCargado.foto)) {
                viewModel.cargarFotoLocal(clienteCargado.idCliente, clienteCargado.foto)
            } else {
                null
            }
        }
    }


    /* ============================================================
     * ============ BLOQUE 6: UI - FORMULARIO DE DATOS ============
     * ============================================================ */

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        /**
         * snackbarHost del Scaffold
         * -------------------------
         * ✔ TIPO: parámetro del Scaffold (snackbarHost)
         * Es el contenedor donde aparecen las snackbars de la pantalla.
         * Sirve para mostrar los avisos flotantes por encima del contenido y sin
         * necesidad de hacer scroll; el Box con imePadding coloca la snackbar por
         * encima del teclado para que el aviso nunca quede oculto.
         */
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    ) { innerPadding ->

    if (idCliente == null && negocioOk == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return@Scaffold
    }

    /**
     * GUARD DE NEGOCIO: en modo alta, si el administrador todavía no tiene
     * negocio creado (o aún se está comprobando y ha dado false), no se muestra
     * el formulario: se bloquea el alta y se ofrece la navegación a la creación
     * del negocio. Así el usuario no llega nunca a un error de Firestore al guardar.
     */
    if (idCliente == null && negocioOk == false) {
        // El alta del negocio ahora se hace desde Mi negocio (flujo único);
        // la pantalla independiente "Crear negocio en la nube" ya no se ofrece.
        SinNegocioContenido(
            onCrearNegocio = { navController.navigate(Routes.MINEGOCIO) }
        )
        return@Scaffold
    }

    /**
     * Column del formulario
     * ---------------------
     * ✔ TIPO: función @Composable (androidx.compose.foundation.layout.Column)
     * Es el contenedor vertical del formulario de alta.
     * Sirve para apilar todos los campos uno debajo de otro con un margen lateral,
     * permitir hacer scroll si no caben y subir el contenido cuando se abre el teclado.
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            /**
              * IconButton de volver
              * --------------------
              * ✔ TIPO: función @Composable (androidx.compose.material3.IconButton)
              * Es el botón con forma de icono que permite retroceder.
              * Sirve para volver a la pantalla anterior pulsando la flecha.
              */
            AppNavigationBackButton(
                onClick = {
                    navController.popBackStack()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            /**
              * Text del título
              * ---------------
              * ✔ TIPO: función @Composable (androidx.compose.material3.Text)
              * Es el título de la pantalla de clientes.
              * Sirve para indicar al usuario en qué sección se encuentra;
              * en modo registro de cliente se usan títulos propios ("Completa tu registro"
              * y "Modificar mis datos") en lugar de los del administrador.
              */
            Text(
                text = if (idCliente != null) "Modificar cliente" else "Añadir cliente",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }


        Text(
            text = "Datos personales",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it
                    errorNombre = false

                    viewModel.limpiarError()
                },
                label = { Text("Nombre") },
                isError = errorNombre,
                supportingText = {
                    if (errorNombre) {
                        Text("El nombre es obligatorio")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = apellidos,
                onValueChange = {
                    apellidos = it
                    errorApellidos = false
                    viewModel.limpiarError()
                },
                label = { Text("Apellidos") },
                isError = errorApellidos,
                supportingText = {
                    if (errorApellidos) {
                        Text("Los apellidos son obligatorios")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dni,
                onValueChange = {
                    dni = it
                    errorDni = false
                    mensajeErrorDni = null
                    viewModel.limpiarError()
                },
                label = { Text("DNI") },
                isError = errorDni,
                supportingText = {
                    /**
                     * supportingText del DNI
                     * ----------------------
                     * ✔ TIPO: bloque condicional (if) + Text de ayuda del campo
                     * Es el texto de ayuda que aparece bajo el campo DNI cuando hay error.
                     * Sirve para mostrar "El DNI ya está registrado" (error devuelto por la base
                     * de datos) justo en el campo, o "Introduce un DNI válido" si el formato es
                     * incorrecto; así el aviso nunca queda oculto al final del formulario.
                     */
                    if (errorDni) {
                        Text(mensajeErrorDni ?: "Introduce un DNI válido")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = telefono,
                onValueChange = {
                    telefono = it
                    errorTelefono = false
                    viewModel.limpiarError()
                },
                label = { Text("Teléfono") },
                isError = errorTelefono,
                supportingText = {
                    if (errorTelefono) {
                        Text("Introduce un teléfono válido")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorEmail = false
                viewModel.limpiarError()
            },
            label = { Text("Email") },
            isError = errorEmail,
            supportingText = {
                if (errorEmail) {
                    Text("Introduce un email válido")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        /**
         * OutlinedTextField de Fecha de nacimiento
         * ------------------------------------------
         * ✔ TIPO: función @Composable (androidx.compose.material3.OutlinedTextField)
         * Es el campo de texto de solo lectura donde se muestra la fecha de nacimiento.
         * Sirve para mostrar la fecha ya formateada y abrir el selector de fecha al tocarlo.
         */
        OutlinedTextField(
            value = fechaNacimientoFormateada,
            onValueChange = { },
            readOnly = true,
            enabled = false,
            label = { Text("Fecha de nacimiento") },
            placeholder = { Text("dd/MM/aaaa") },
            isError = errorFechaNacimiento,
            supportingText = {
                if (errorFechaNacimiento) {
                    Text("La fecha de nacimiento es obligatoria")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Seleccionar fecha de nacimiento"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    mostrarDatePicker = true
                    errorFechaNacimiento = false
                    viewModel.limpiarError()
                }
        )

        /* ============================================================
         * ============ BLOQUE 7: UI - SELECCIÓN DE FOTO ==============
         * ============================================================ */
        /**
         * Column de selección de foto
         * ---------------------------
         * ✔ TIPO: función @Composable (androidx.compose.foundation.layout.Column)
         * Es la sección que muestra la foto del cliente y el botón para elegirla.
         * Sirve para centrar la vista previa de la foto y abrir el selector de imágenes.
         */
        Text(
            text = "Foto",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /**
             * AsyncImage de la foto
             * ---------------------
             * ✔ TIPO: bloque condicional (if) + función @Composable (coil3.compose.AsyncImage)
             * Es la vista previa circular de la foto elegida por el usuario.
             * Sirve para mostrar en pantalla la foto guardada y confirmar visualmente la selección.
             */
            val modeloFotoFormulario: Any? = when {
                // Foto remota (URL de Storage): fichero de la caché autenticada.
                FotoClienteStorage.esUrlFoto(foto) -> fotoRemotaPreview
                foto.isNotEmpty() -> File(foto)
                else -> null
            }
            if (modeloFotoFormulario != null) {
                AsyncImage(
                    model = modeloFotoFormulario,
                    contentDescription = "Foto del cliente",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF64B5F6), CircleShape)
                )
            } else {
                /**
                 * Box del placeholder de la foto
                 * ------------------------------
                 * ✔ TIPO: bloque condicional (else) + función @Composable (androidx.compose.foundation.layout.Box)
                 * Es el círculo con el icono de persona que se muestra mientras no hay foto elegida.
                 * Sirve para mantener la sección visualmente completa aunque el cliente aún no tenga foto.
                 */
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, Color(0xFF64B5F6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            /**
             * BotonSelectorFoto
             * -----------------
             * ✔ TIPO: componente @Composable (BotonSelectorFoto)
             * Es el botón que despliega el menú "Elegir de galería" / "Hacer una foto".
             * Sirve para elegir la foto por primera vez o cambiarla si ya hay una seleccionada.
             */
            BotonSelectorFoto(
                tieneFoto = foto.isNotEmpty(),
                onElegirGaleria = {
                    launcherGaleria.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                    errorFoto = false
                },
                onHacerFoto = {
                    val temporal = crearFotoTemporal(context)
                    if (temporal != null) {
                        fotoTemporal = temporal
                        launcherTomarFoto.launch(uriDeFotoTemporal(context, temporal))
                        errorFoto = false
                    }
                }
            )

            if (errorFoto) {
                Text(
                    text = "La foto es obligatoria",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        /* ============================================================
         * ============ BLOQUE 8: UI - OPCIONES Y GUARDAR =============
         * ============================================================ */
        /**
         * Sección "Otros datos" (solo administrador)
         * ------------------------------------------
         * ✔ TIPO: bloque de la UI que agrupa estado y observaciones.
         * Son decisiones del administrador; sus valores se conservan sin cambios
         * al editar (se precargan del cliente original).
         */

            Text(
                text = "Otros datos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )

            OutlinedTextField(
                value = observaciones,
                onValueChange = {
                    observaciones = it
                    viewModel.limpiarError()
                },
                label = { Text("Observaciones") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

        /**
         * Button de Guardar cliente
         * -------------------------
         * ✔ TIPO: función @Composable (androidx.compose.material3.Button)
         * Es el botón que valida el formulario y guarda el cliente.
         * Sirve para comprobar todos los campos obligatorios a la vez, marcar los incorrectos
         * y, si no hay errores, crear el ClienteEntity y volver a la lista de clientes.
         */
        AppPrimaryButton(
            onClick = {
                viewModel.limpiarError()

                // SEGUNDA BARRERA: aunque el formulario se muestre solo con
                // negocioOk == true, se vuelve a comprobar antes de guardar un
                // cliente nuevo para no llegar a un error de Firestore.
                if (idCliente == null && negocioOk != true) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            "No puedes crear clientes todavía. Primero debes crear tu centro."
                        )
                    }
                    return@AppPrimaryButton
                }

                errorNombre = nombre.isBlank()
                errorApellidos = apellidos.isBlank()
                errorDni = !esDniValido(dni)
                errorTelefono = !esTelefonoValido(telefono)
                errorEmail = email.isNotBlank() && !esEmailValido(email)
                // La fecha de nacimiento es OPCIONAL: no produce error si no se elige.
                errorFechaNacimiento = false
                errorFoto = foto.isBlank()

                val hayErrores =
                    errorNombre ||
                        errorApellidos ||
                        errorDni ||
                        errorTelefono ||
                        errorEmail ||
                        errorFoto

                // GUARD DE CARGA EN EDICIÓN: si estamos modificando un cliente pero los datos
                // originales aún no se han cargado (clienteEditando == null), se bloquea el
                // guardado para no sobrescribir el cliente con campos vacíos o valores por defecto.
                if (idCliente != null && clienteEditando == null) {
                    errorCargaEdicion = true
                } else {
                    errorCargaEdicion = false

                    if (!hayErrores) {
                        if (idCliente != null) {
                            // MODO EDICIÓN: se conservan los datos que no se pueden cambiar
                            // en este formulario (fecha de registro y el uid de Firebase)
                            // tomándolos del cliente original.
                            // El estado se conserva: ya no se edita desde este
                            // formulario (se cambia desde el card del perfil).
                            val original = clienteEditando!!
                            val cliente = ClienteEntity(
                                idCliente = idCliente,
                                nombre = nombre,
                                apellidos = apellidos,
                                // El DNI se normaliza a mayúsculas para que la comprobación
                                // de duplicados (WHERE dni = :dni) sea fiable en la base de datos.
                                dni = dni.uppercase(),
                                telefono = telefono,
                                email = email,
                                foto = foto,
                                fechaNacimiento = fechaNacimiento,
                                fechaRegistro = original.fechaRegistro,
                                fechaAlta = original.fechaAlta,
                                fechaBaja = if (esActivo) {
                                    // ACTIVO (reactivado o no): se CONSERVA la
                                    // fechaBaja previa (última fecha de baja). El
                                    // ViewModel además la restaura en BAJA -> ACTIVO.
                                    original.fechaBaja
                                } else {
                                    // Nueva BAJA: fecha ACTUAL siempre. Si ya estaba
                                    // en BAJA se conserva su fechaBaja original.
                                    if (original.estado == EstadoCliente.BAJA) {
                                        original.fechaBaja
                                    } else {
                                        System.currentTimeMillis()
                                    }
                                },
                                estado = if (esActivo) EstadoCliente.ACTIVO else EstadoCliente.BAJA,
                                observaciones = observaciones.ifBlank { null },
                                // Se conservan los servicios ya contratados al editar;
                                // PENDIENTE: pantalla para gestionarlos
                                serviciosContratados = original.serviciosContratados,
                                firebaseUid = original.firebaseUid
                            )

                            // Acción común al guardar con éxito: limpiar la foto
                            // antigua si cambió y volver a la lista.
                            val alGuardar: () -> Unit = {
                                if (foto.isNotBlank() && original.foto.isNotBlank() && foto != original.foto) {
                                    // Solo se elimina la foto local antigua si es un archivo local
                                    // (las fotos ya migradas a Storage se reemplazan en el bucket).
                                    val archivoAnterior = File(original.foto)
                                    if (archivoAnterior.exists()) {
                                        archivoAnterior.delete()
                                    }
                                }
                                navController.popBackStack()
                            }

                            if (cliente.estado == EstadoCliente.BAJA && original.estado != EstadoCliente.BAJA) {
                                // Transición a BAJA: baja efectiva (mismas consecuencias que
                                // aceptar una solicitud: cancela reservas futuras y notifica).
                                viewModel.darDeBaja(cliente, onExito = alGuardar)
                            } else {
                                viewModel.actualizarCliente(cliente, onExito = alGuardar)
                            }
                        } else {
                            // MODO ALTA: el cliente nuevo nace ACTIVO por defecto.
                            // El cambio de estado (ACTIVO/BAJA) se gestiona ahora desde
                            // el card de estado del perfil del cliente. Si el alta la
                            // hace el propio cliente desde Mi perfil, nace REGISTRADO a
                            // la espera de que el administrador lo revise.
                            val cliente = ClienteEntity(
                                nombre = nombre,
                                apellidos = apellidos,
                                // El DNI también se guarda en mayúsculas en el alta por coherencia.
                                dni = dni.uppercase(),
                                telefono = telefono,
                                email = email,
                                foto = foto,
                                fechaNacimiento = fechaNacimiento,
                                fechaAlta = if (esActivo) System.currentTimeMillis() else null,
                                fechaBaja = if (esActivo) null else System.currentTimeMillis(),
                                estado = if (esActivo) EstadoCliente.ACTIVO else EstadoCliente.BAJA,
                                observaciones = observaciones.ifBlank { null },
                                // Un cliente nuevo empieza sin servicios contratados;
                                // PENDIENTE: pantalla para gestionarlos
                                serviciosContratados = emptyList()
                            )

                            viewModel.insertarCliente(cliente) { _ ->
                                navController.popBackStack()
                            }
                        }
                    }
                }
            },
            text = if (idCliente != null) "Guardar cambios" else "Guardar cliente",
            enabled = !guardandoAlta,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 16.dp)
        )

        /**
         * Aviso de sincronización con la nube
         * -----------------------------------
         * ✔ TIPO: bloque condicional + Composable (Column)
         * Es el aviso que aparece cuando el guardado local tuvo éxito pero la
         * réplica a Firestore falló. Sirve para informar al administrador sin
         * revertir nada y para ofrecer el reintento manual de sincronización;
         * mientras siga pendiente, no se puede generar enlace de vinculación.
         */
        val errorSincronizacion by viewModel.errorSincronizacion
            .collectAsStateWithLifecycle()
        val clienteSinSincronizar by viewModel.clienteSinSincronizar
            .collectAsStateWithLifecycle()

        if (errorSincronizacion != null || clienteSinSincronizar != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = errorSincronizacion
                        ?: "Hay cambios pendientes de sincronizar con la nube.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                AppSecondaryButton(
                    text = "Reintentar sincronización",
                    onClick = { viewModel.reintentarSincronizacion() },
                    enabled = clienteSinSincronizar != null
                )
            }
        }

        /**
         * Botón de Guardar terminado: los avisos de error se muestran ahora con la snackbar
         * flotante (SnackbarHost) y, si el error es del DNI, también bajo el propio campo DNI.
         */
    }
    }
    /* ============================================================
     * ============ BLOQUE 9: UI - SELECTOR DE FECHA ==============
     * ============================================================ */
    /**
     * DatePickerDialog
     * ----------------
     * ✔ TIPO: bloque condicional (if) + función @Composable (androidx.compose.material3.DatePickerDialog)
     * Es el diálogo con calendario que se muestra al tocar el campo de fecha.
     * Sirve para que el usuario elija el día, mes y año de nacimiento del cliente.
     */
    if (mostrarDatePicker) {

        /**
         * datePickerState
         * ---------------
         * ✔ TIPO: variable con estado (val) → DatePickerState
         * Es el estado del selector de fecha con las fechas restringidas.
         * Sirve para conocer la fecha elegida por el usuario y confirmarla al pulsar Aceptar.
         */
        val datePickerState = rememberDatePickerState(selectableDates = selectableDates)

        DatePickerDialog(
            onDismissRequest = {
                mostrarDatePicker = false
            },
            confirmButton = {
                /**
                 * TextButton de Aceptar
                 * ---------------------
                 * ✔ TIPO: función @Composable (androidx.compose.material3.TextButton)
                 * Es el botón que confirma la fecha elegida en el calendario.
                 * Sirve para guardar la fecha en la variable fechaNacimiento y cerrar el diálogo;
                 * se mantiene deshabilitado mientras no haya una fecha seleccionada.
                 */
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        fechaNacimiento = datePickerState.selectedDateMillis?.let { millisUtcAMedianocheLocal(it) }
                        mostrarDatePicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                /**
                 * TextButton de Cancelar
                 * ----------------------
                 * ✔ TIPO: función @Composable (androidx.compose.material3.TextButton)
                 * Es el botón que cierra el selector de fecha sin guardar nada.
                 * Sirve para cancelar la elección de la fecha y ocultar el diálogo.
                 */
                TextButton(
                    onClick = {
                        mostrarDatePicker = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            /**
             * DatePicker
             * ----------
             * ✔ TIPO: función @Composable (androidx.compose.material3.DatePicker)
             * Es el calendario en sí que muestra los días del mes.
             * Sirve para que el usuario seleccione con un toque el día de nacimiento.
             */
            DatePicker(
                state = datePickerState
            )
        }
    }

    if (mostrarConfirmarBaja) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarBaja = false },
            title = { Text("Confirmar baja") },
            text = {
                Text(
                    "¿Confirmar la baja de este cliente? Se cancelarán sus " +
                        "reservas futuras y se le notificará si está activada " +
                        "la configuración de avisos. Los servicios contratados se conservan."
                )
            },
            confirmButton = {
                AppDialogDangerConfirmButton(
                    text = "Dar de baja",
                    onClick = {
                        esActivo = false
                        viewModel.limpiarError()
                        mostrarConfirmarBaja = false
                    }
                )
            },
            dismissButton = {
                AppDialogTextButton(
                    text = "Cancelar",
                    onClick = { mostrarConfirmarBaja = false }
                )
            }
        )
    }
}

/* ============================================================
 * ============ BLOQUE 10: FUNCIONES AUXILIARES ===============
 * ============================================================ */
/**
 * formatearFecha
 * --------------
 * ✔ TIPO: función privada (private fun) → String
 * Es la función que convierte un timestamp en milisegundos a texto legible.
 * Sirve para mostrar la fecha de nacimiento en el campo de solo lectura con formato dd/MM/aaaa.
 */
private fun formatearFecha(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

/**
 * millisUtcAMedianocheLocal
 * -------------------------
 * ✔ TIPO: función privada (private fun) → Long
 * Es la función que convierte la medianoche UTC del selector de fecha a medianoche local.
 * Sirve para guardar la fecha en la base de datos sin desfases por la zona horaria del dispositivo.
 */
private fun millisUtcAMedianocheLocal(utcMillis: Long): Long {
    val fecha = Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return fecha.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/**
 * esDniValido
 * -----------
 * ✔ TIPO: función privada (private fun) → Boolean
 * Es la función que valida el formato de un DNI español.
 * Sirve para comprobar que el DNI tenga 8 dígitos y una letra (sin verificar la letra
 * de control) antes de guardarlo, permitiendo DNIs de prueba inventados.
 */
private fun esDniValido(dni: String): Boolean {
    return dni.matches(Regex("\\d{8}[A-Za-z]"))
}

/**
 * esTelefonoValido
 * ----------------
 * ✔ TIPO: función privada (private fun) → Boolean
 * Es la función que valida el formato de un teléfono móvil español.
 * Sirve para comprobar que el teléfono empiece por 6, 7, 8 o 9 y tenga 9 dígitos.
 */
private fun esTelefonoValido(telefono: String): Boolean {
    return telefono.matches(Regex("[6789]\\d{8}"))
}

/**
 * esEmailValido
 * -------------
 * ✔ TIPO: función privada (private fun) → Boolean
 * Es la función que valida el formato de un correo electrónico.
 * Sirve para comprobar que el email tenga un formato válido antes de guardarlo.
 */
private fun esEmailValido(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS
        .matcher(email)
        .matches()
}
