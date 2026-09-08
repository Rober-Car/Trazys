package com.roberto.gestorpro.ui.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.firebase.AutenticacionRepository
import com.roberto.gestorpro.data.firebase.EstadoNegocioDeCuenta
import com.roberto.gestorpro.data.firebase.NegocioRepository
import com.roberto.gestorpro.data.firebase.esperar
import com.roberto.gestorpro.data.firebase.validarCambioContrasena
import com.google.firebase.functions.FirebaseFunctions
import com.roberto.gestorpro.data.local.PreparadorLocalCuenta
import com.roberto.gestorpro.data.repository.DesactivacionServicioSincronizador
import com.roberto.gestorpro.data.repository.HidratadorCacheLocal
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.model.TipoUsuario
import com.roberto.gestorpro.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val autenticacionRepository: AutenticacionRepository,
    private val negocioRepository: NegocioRepository,
    private val movimientoRepository: MovimientoRepository,
    private val preparadorLocalCuenta: PreparadorLocalCuenta,
    private val hidratadorCacheLocal: HidratadorCacheLocal,
    private val desactivacionServicioSincronizador: DesactivacionServicioSincronizador
) : ViewModel() {

    /**
     * _autenticando / autenticando
     * ----------------------------
     * Estado que indica si hay una operaci��n de autenticaci��n en curso.
     */
    private val _autenticando = MutableStateFlow(false)
    val autenticando: StateFlow<Boolean> = _autenticando.asStateFlow()

    /**
     * _operandoRemoto / operandoRemoto
     * --------------------------------
     * Estado que indica si hay una operación remota de negocio en curso.
     */
    private val _operandoRemoto = MutableStateFlow(false)
    val operandoRemoto: StateFlow<Boolean> = _operandoRemoto.asStateFlow()

    /**
     * _estadoPreparacion / estadoPreparacion
     * --------------------------------------
     * Estado del guard de propietario de la caché local. Determina si la UI
     * puede mostrar pantallas de datos (Listo) o si debe mostrar un aviso de
     * propietario indeterminado / bloqueo por pendientes antes de continuar.
     */
    private val _estadoPreparacion = MutableStateFlow<EstadoPreparacion>(EstadoPreparacion.Preparando)
    val estadoPreparacion: StateFlow<EstadoPreparacion> = _estadoPreparacion.asStateFlow()

    /**
     * _cambioPropietarioToken / cambioPropietarioToken
     * ------------------------------------------------
     * Contador que se incrementa cada vez que se produce un WIPE por cambio de
     * propietario (o el borrado de datos de un propietario indeterminado). La
     * capa UI (AppNavigation) lo observa para pedir a los ViewModels que
     * conservan estado propio (p. ej. NotificacionesViewModel) que lo reseteen.
     */
    private val _cambioPropietarioToken = MutableStateFlow(0)
    val cambioPropietarioToken: StateFlow<Int> = _cambioPropietarioToken.asStateFlow()

    /**
     * themeMode
     * ---------
     * Modo de tema guardado (claro/oscuro/sistema).
     */
    val themeMode = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreferencesRepository.THEME_SISTEMA
    )

    /**
     * setThemeMode
     * ------------
     * Persiste el modo de tema elegido.
     */
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    /**
     * obtenerTipoUsuario
     * ------------------
     * GestorPro Admin es exclusivamente para administradores: devuelve siempre
     * ADMINISTRADOR, sin depender de la elección previa de DataStore.
     */
    suspend fun obtenerTipoUsuario(): TipoUsuario {
        return TipoUsuario.ADMINISTRADOR
    }

    /**
     * destinoSegunTipo
     * ----------------
     * GestorPro Admin siempre navega al Home del administrador.
     */
    suspend fun destinoSegunTipo(): String {
        return Routes.HOME
    }

    /**
     * destinoInicialSegunSesion
     * -------------------------
     * Pantalla inicial combinando DataStore y Firebase: sin sesión → Login;
     * con sesión restaurada → Home del administrador.
     */
    suspend fun destinoInicialSegunSesion(): String {
        return if (autenticacionRepository.haySesionActiva()) {
            prepararParaCuentaActual()
            Routes.HOME
        } else {
            _estadoPreparacion.value = EstadoPreparacion.SinSesion
            Routes.LOGIN
        }
    }

    /**
     * prepararParaCuentaActual
     * ------------------------
     * Ejecuta el guard de propietario con la cuenta autenticada (resolver del
     * PreparadorLocalCuenta) y mapea el resultado al estado de la UI. Un WIPE
     * por cambio de propietario ya deja la caché limpia; en ese caso NO se
     * lanzan reintentos de la cuenta anterior.
     */
    suspend fun prepararParaCuentaActual() {
        val uid = autenticacionRepository.uidActual()
        if (uid == null) {
            _estadoPreparacion.value = EstadoPreparacion.SinSesion
            return
        }

        when (val resultado = preparadorLocalCuenta.resolver(uid)) {
            is PreparadorLocalCuenta.ResultadoResolver.SinSesion ->
                _estadoPreparacion.value = EstadoPreparacion.SinSesion

            is PreparadorLocalCuenta.ResultadoResolver.MismaCuenta,
            is PreparadorLocalCuenta.ResultadoResolver.AdoptadoSilencioso -> {
                refrescarIdentidadLocal()
                refrescarIdentidadRemota()
                lanzarHidratacionSiProcede()
                _estadoPreparacion.value = EstadoPreparacion.Listo
                lanzarReintentoDeEliminacionesPendientes()
            }

            is PreparadorLocalCuenta.ResultadoResolver.Indeterminado ->
                _estadoPreparacion.value =
                    EstadoPreparacion.Indeterminado(resultado.pendientes)

            is PreparadorLocalCuenta.ResultadoResolver.Bloqueado ->
                _estadoPreparacion.value = EstadoPreparacion.Bloqueado(resultado.pendientes)

            is PreparadorLocalCuenta.ResultadoResolver.CambioCompletado -> {
                // WIPE ya aplicado por el preparador. No se reintenta nada del
                // propietario anterior bajo la nueva cuenta.
                refrescarIdentidadLocal()
                refrescarIdentidadRemota()
                lanzarHidratacionSiProcede()
                _cambioPropietarioToken.value += 1
                _estadoPreparacion.value = EstadoPreparacion.Listo
            }
        }
    }

    /**
     * decidirPropietarioIndeterminado
     * -------------------------------
     * Resuelve el caso INDETERMINADO (owner == null con datos locales) por
     * decisión del usuario:
     *  - conservar = true  -> adopta la caché para la cuenta actual (bajo su
     *    responsabilidad, sin limpiar).
     *  - conservar = false -> WIPE y reconstruye desde Firebase (opción segura
     *    por defecto).
     */
    fun decidirPropietarioIndeterminado(conservar: Boolean) {
        val uid = autenticacionRepository.uidActual() ?: return
        viewModelScope.launch {
            if (conservar) {
                preparadorLocalCuenta.adoptarDatos(uid)
                refrescarIdentidadLocal()
                // Aplica la verdad remota tras adoptar: si la cuenta no tiene
                // negocio (confirmado) se vacía la identidad; si lo tiene se
                // carga la suya. Si no se puede confirmar, se conserva la caché.
                refrescarIdentidadRemota()
            } else {
                preparadorLocalCuenta.wipeYAdoptar(uid)
                refrescarIdentidadLocal()
                refrescarIdentidadRemota()
                lanzarHidratacionSiProcede()
                _cambioPropietarioToken.value += 1
            }
            _estadoPreparacion.value = EstadoPreparacion.Listo
        }
    }

    /**
     * descartarPendientesYContinuar
     * -----------------------------
     * Ante un cambio de propietario BLOQUEADO por pendientes, descarta
     * explícitamente la caché local de la cuenta anterior (tras confirmación
     * del usuario) y continúa con la cuenta nueva. Nunca reintenta los
     * pendientes de la cuenta anterior bajo la nueva.
     */
    fun descartarPendientesYContinuar() {
        val uid = autenticacionRepository.uidActual() ?: return
        viewModelScope.launch {
            preparadorLocalCuenta.wipeYAdoptar(uid)
            refrescarIdentidadLocal()
            refrescarIdentidadRemota()
            lanzarHidratacionSiProcede()
            _cambioPropietarioToken.value += 1
            _estadoPreparacion.value = EstadoPreparacion.Listo
        }
    }

    /**
     * lanzarHidratacionSiProcede
     * --------------------------
     * Lanza (best-effort, no bloquea la UI) la reconstrucción central de la
     * caché Room desde Firestore cuando procede: solo si la cuenta actual tiene
     * un negocio confirmado, si esta instalación aún no ha hidratado esa cuenta
     * y si no hay otra hidratación en curso. Si falla (p. ej. sin red), no se
     * marca como completada y se reintenta en el próximo arranque/login.
     */
    private fun lanzarHidratacionSiProcede() {
        viewModelScope.launch {
            hidratadorCacheLocal.hidratarSiNecesario()
        }
    }

    /**
     * cancelarCambioDeCuenta
     * ----------------------
     * Abandona el cambio de cuenta (caso bloqueado o indeterminado): cierra la
     * sesión actual y vuelve al estado SinSesion. La caché y el propietario
     * anterior se conservan intactos.
     */
    fun cancelarCambioDeCuenta() {
        viewModelScope.launch {
            autenticacionRepository.cerrarSesion()
            _estadoPreparacion.value = EstadoPreparacion.SinSesion
        }
    }

    /**
     * lanzarReintentoDeEliminacionesPendientes
     * ----------------------------------------
     * Reintenta al ARRANQUE (o tras autenticarse) los borrados remotos de
     * movimientos que quedaron pendientes persistidos en Room, con
     * independencia de qué pantalla abra después el ADMIN (AJUSTE 1).
     * Es un no-op si no hay sesión o si no hay pendientes. Solo se invoca
     * cuando el propietario de la caché es la cuenta actual (nunca tras un
     * WIPE por cambio de propietario).
     */
    private fun lanzarReintentoDeEliminacionesPendientes() {
        viewModelScope.launch {
            movimientoRepository.reintentarEliminacionesPendientesGlobal()
            desactivacionServicioSincronizador.reintentarPendientes()
        }
    }

    /**
     * haySesionActiva
     * ---------------
     * Indica si hay sesión de Firebase restaurada en el dispositivo.
     */
    fun haySesionActiva(): Boolean {
        return autenticacionRepository.haySesionActiva()
    }

    /**
     * iniciarSesion
     * -------------
     * Inicia sesión real con email y contraseña.
     */
    suspend fun iniciarSesion(email: String, contrasena: String): String? {
        _autenticando.value = true
        try {
            val resultado = autenticacionRepository.iniciarSesion(email, contrasena)
            return if (resultado.exito) {
                prepararParaCuentaActual()
                null
            } else {
                resultado.mensaje
            }
        } finally {
            _autenticando.value = false
        }
    }

    /**
     * enviarCorreoRecuperacion
     * ------------------------
     * Valida el email antes de llamar a Firebase y delega el envío del correo
     * de restablecimiento.
     */
    suspend fun enviarCorreoRecuperacion(email: String): String? {
        if (email.isBlank()) {
            return "Introduce tu email"
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "El email no tiene un formato válido"
        }

        _autenticando.value = true
        try {
            val resultado = autenticacionRepository.enviarCorreoRecuperacion(email.trim())
            return if (resultado.exito) null else resultado.mensaje
        } finally {
            _autenticando.value = false
        }
    }

    /**
     * registrarse
     * -----------
     * Crea la cuenta real en Firebase Authentication y el documento usuarios/{uid}
     * con el rol ADMIN (GestorPro Admin es exclusivo de administradores).
     */
    suspend fun registrarse(
        email: String,
        contrasena: String,
        contrasenaRepetida: String
    ): String? {
        if (contrasena.length < 6) {
            return "La contraseña debe tener al menos 6 caracteres"
        }
        if (contrasena != contrasenaRepetida) {
            return "Las contraseñas no coinciden"
        }

        _autenticando.value = true
        try {
            val resultado = autenticacionRepository.registrar(
                email,
                contrasena,
                AutenticacionRepository.ROL_ADMIN
            )
            return if (resultado.exito) {
                prepararParaCuentaActual()
                null
            } else {
                resultado.mensaje
            }
        } finally {
            _autenticando.value = false
        }
    }

    /**
     * cerrarSesion
     * ------------
     * Cierra la sesión de Firebase Authentication sin borrar DataStore (la
     * caché y el owner se conservan para que el mismo usuario la reencuentre).
     * Antes de salir hace un reintento BEST-EFFORT de las eliminaciones
     * pendientes PERSISTIDAS bajo la cuenta actual (su token es el correcto);
     * si falla, quedan persistidas y el owner no cambia, por lo que no se
     * pierden ni se ejecutan bajo otra cuenta.
     */
    fun cerrarSesion() {
        viewModelScope.launch {
            movimientoRepository.reintentarEliminacionesPendientesGlobal()
            autenticacionRepository.cerrarSesion()
            // Logout: se limpia la identidad de la cuenta saliente (memoria y
            // DataStore) para que la siguiente cuenta no vea branding ajeno
            // mientras se prepara su sesión. Room, owner y ficheros se
            // conservan (el mismo usuario los reencuentra al volver).
            preferencesRepository.limpiarIdentidadNegocio()
            _nombreNegocio.value = ""
            _logoNegocio.value = ""
            _idClienteSesion.value = null
            _estadoPreparacion.value = EstadoPreparacion.SinSesion
        }
    }

    /**
     * eliminandoCuenta
     * ----------------
     * Evita que una doble pulsación lance dos eliminaciones simultáneas.
     */
    private val _eliminandoCuenta = MutableStateFlow(false)
    val eliminandoCuenta: StateFlow<Boolean> = _eliminandoCuenta.asStateFlow()

    /**
     * cambiandoContrasena
     * -------------------
     * true mientras se está cambiando la contraseña en Firebase. Sirve para
     * mostrar carga en el diálogo y evitar una doble pulsación de "Guardar".
     */
    private val _cambiandoContrasena = MutableStateFlow(false)
    val cambiandoContrasena: StateFlow<Boolean> = _cambiandoContrasena.asStateFlow()

    /**
     * cambiarContrasena
     * -----------------
     * Cambia la contraseña REAL del ADMIN en Firebase Authentication
     * (reautenticación con la actual + updatePassword en el repositorio).
     * Devuelve null si terminó bien o el mensaje de error real si falló.
     */
    suspend fun cambiarContrasena(actual: String, nueva: String, repetida: String): String? {
        if (_cambiandoContrasena.value) {
            return "Ya hay un cambio de contraseña en curso"
        }
        val errorValidacion = validarCambioContrasena(actual, nueva, repetida)
        if (errorValidacion != null) return errorValidacion
        _cambiandoContrasena.value = true
        return try {
            val resultado = autenticacionRepository.cambiarContrasena(actual, nueva)
            if (resultado.exito) null else resultado.mensaje
        } finally {
            _cambiandoContrasena.value = false
        }
    }

    /**
     * eliminarCuentaYNegocio
     * ----------------------
     * Elimina COMPLETAMENTE la cuenta ADMIN y su negocio llamando a la Cloud
     * Function callable `eliminarMiCuenta` (borrado por Admin SDK). Requiere
     * reautenticación con la contraseña actual. Al terminar cierra la sesión.
     */
    fun eliminarCuentaYNegocio(contrasena: String, onTerminado: (String?) -> Unit) {
        if (_eliminandoCuenta.value) return
        _eliminandoCuenta.value = true
        viewModelScope.launch {
            try {
                if (!autenticacionRepository.reautenticar(contrasena)) {
                    onTerminado("La contraseña no es correcta")
                    return@launch
                }
                FirebaseFunctions.getInstance("europe-west1")
                    .getHttpsCallable("eliminarMiCuenta")
                    .call(null)
                    .esperar()
                // signOut síncrono y limpieza de identidad en la misma corrutina.
                autenticacionRepository.cerrarSesion()
                preferencesRepository.limpiarIdentidadNegocio()
                _nombreNegocio.value = ""
                _logoNegocio.value = ""
                _idClienteSesion.value = null
                _estadoPreparacion.value = EstadoPreparacion.SinSesion
                onTerminado(null)
            } catch (e: Exception) {
                onTerminado("No se pudo eliminar la cuenta: ${e.message ?: "error desconocido"}")
            } finally {
                _eliminandoCuenta.value = false
            }
        }
    }

    /**
     * terminosAceptados
     * -----------------
     * ¿El ADMIN actual aceptó la versión vigente de los Términos de uso?
     */
    suspend fun terminosAceptados(): Boolean {
        val uid = autenticacionRepository.uidActual() ?: return false
        return preferencesRepository.terminosAceptados(uid)
    }

    /**
     * aceptarTerminos
     * ---------------
     * Registra la aceptación de la versión vigente para el ADMIN actual.
     */
    fun aceptarTerminos() {
        viewModelScope.launch {
            val uid = autenticacionRepository.uidActual() ?: return@launch
            preferencesRepository.guardarAceptacionTerminos(uid)
        }
    }

    /**
     * existeNegocioPropio
     * -------------------
     * Indica si el ADMIN autenticado ya creó su negocio remoto.
     */
    suspend fun existeNegocioPropio(): Boolean {
        return negocioRepository.existeNegocioPropio()
    }

    /**
     * obtenerCodigoMaestroRemoto
     * --------------------------
     * Lee el código maestro actual del negocio propio en Firestore.
     */
    suspend fun obtenerCodigoMaestroRemoto(): String? {
        return negocioRepository.obtenerCodigoMaestro()
    }

    /**
     * crearNegocio
     * ------------
     * Crea el negocio remoto (negocios + negocios_publicos + usuarios/{uid})
     * y guarda además el nombre en DataStore para la identidad local.
     */
    suspend fun crearNegocio(nombre: String, codigoMaestro: String): String? {
        if (nombre.isBlank()) return "El nombre del negocio no puede estar vacío"
        if (codigoMaestro.isBlank()) return "El código maestro no puede estar vacío"

        _operandoRemoto.value = true
        try {
            val resultado = negocioRepository.crearNegocio(nombre.trim(), codigoMaestro.trim())
            if (resultado.exito) {
                preferencesRepository.setNombreNegocio(nombre.trim())
                _nombreNegocio.value = nombre.trim()
                // La cuenta pasa de "sin negocio" a "con negocio": se elimina el
                // marcador de caché hidratada para que un futuro login pueda
                // reconstruir Room si procede.
                preferencesRepository.borrarUidCacheHidratada()
                return null
            }
            return resultado.mensaje
        } finally {
            _operandoRemoto.value = false
        }
    }

    /**
     * guardarCodigoMaestro
     * --------------------
     * Actualiza (o confirma) el código maestro del negocio de forma atómica,
     * liberando el código anterior si es distinto. Devuelve null en éxito o el
     * mensaje de error (p. ej. "código en uso") para la UI.
     */
    suspend fun guardarCodigoMaestro(
        codigoMaestro: String,
        codigoAnterior: String?
    ): String? {
        if (codigoMaestro.isBlank()) return "El código maestro no puede estar vacío"

        _operandoRemoto.value = true
        try {
            val resultado = negocioRepository.guardarCodigoMaestro(
                codigoNuevo = codigoMaestro.trim(),
                codigoAnterior = codigoAnterior?.trim()
            )
            return if (resultado.exito) null else resultado.mensaje
        } finally {
            _operandoRemoto.value = false
        }
    }

    /**
     * _nombreNegocio / nombreNegocio
     * ------------------------------
     * Nombre del negocio guardado en DataStore, cacheado en el ViewModel para
     * mostrarlo en Home/Login y para poder refrescarlo al cambiar de propietario.
     */
    private val _nombreNegocio = MutableStateFlow("")
    val nombreNegocio: StateFlow<String> = _nombreNegocio.asStateFlow()

    /**
     * _logoNegocio / logoNegocio
     * --------------------------
     * Ruta del logo del negocio guardado en DataStore (caché local del VM).
     */
    private val _logoNegocio = MutableStateFlow("")
    val logoNegocio: StateFlow<String> = _logoNegocio.asStateFlow()

    /**
     * _idClienteSesion / idClienteSesion
     * ----------------------------------
     * Identificador del cliente registrado en este dispositivo (uso local).
     */
    private val _idClienteSesion = MutableStateFlow<Int?>(null)
    val idClienteSesion: StateFlow<Int?> = _idClienteSesion.asStateFlow()

    init {
        viewModelScope.launch {
            refrescarIdentidadLocal()
        }
    }

    /**
     * refrescarIdentidadLocal
     * -----------------------
     * Recarga en el ViewModel los valores de identidad guardados en DataStore
     * (nombre/logo/id de cliente). Se invoca en init y después de un WIPE o de
     * adoptar/limpiar la identidad al cambiar de propietario, para no mostrar
     * en memoria la identidad de la cuenta anterior.
     * Es suspend y se espera antes de marcar la preparación como terminada,
     * para que la UI no llegue a mostrar el valor de la cuenta anterior.
     */
    private suspend fun refrescarIdentidadLocal() {
        val uid = autenticacionRepository.uidActual()
        val propietario = preferencesRepository.obtenerUidPropietario()
        if (uid != null && propietario == uid) {
            // Misma cuenta: se puede usar la caché del negocio actual (offline OK).
            _nombreNegocio.value = preferencesRepository.nombreNegocio.first()
            _logoNegocio.value = preferencesRepository.logoNegocio.first()
            _idClienteSesion.value = preferencesRepository.obtenerIdClienteSesion()
        } else {
            // UID distinto, nulo o propietario aún sin fijar: nunca se expone la
            // identidad visual de otra cuenta. Queda vacía/placeholder hasta que
            // la fuente remota confirme el negocio actual.
            _nombreNegocio.value = ""
            _logoNegocio.value = ""
            _idClienteSesion.value = null
        }
    }

    /**
     * refrescarIdentidadRemota
     * ------------------------
     * Hace coherente la identidad visual (nombre + logo) con la cuenta
     * autenticada usando como fuente de verdad el estado remoto:
     *  - SinNegocio CONFIRMADO (usuarios/{uid}.negocioId == null): vacía la
     *    identidad en DataStore (caché) y en memoria. Nunca se hereda la
     *    identidad de un propietario anterior.
     *  - ConNegocio: refresca nombre/logo desde negocios_publicos/{negocioId}.
     *  - Error/SinSesion: NO toca nada (no hay confirmación); se conserva la
     *    caché de un negocio válido (modo offline).
     */
    private suspend fun refrescarIdentidadRemota() {
        when (val estado = negocioRepository.estadoNegocioDeCuenta()) {
            EstadoNegocioDeCuenta.SinSesion,
            EstadoNegocioDeCuenta.Error -> Unit

            EstadoNegocioDeCuenta.SinNegocio -> {
                preferencesRepository.setNombreNegocio("")
                preferencesRepository.setLogoNegocio("")
                _nombreNegocio.value = ""
                _logoNegocio.value = ""
            }

            is EstadoNegocioDeCuenta.ConNegocio -> {
                val datos = negocioRepository.leerDatosPublicos(estado.negocioId) ?: return
                val nombre = datos.nombre.trim()
                val logo = datos.logo.trim()
                preferencesRepository.setNombreNegocio(nombre)
                preferencesRepository.setLogoNegocio(logo)
                _nombreNegocio.value = nombre
                _logoNegocio.value = logo
            }
        }
    }

    /**
     * guardarNombreNegocio
     * --------------------
     * Guarda el nombre del negocio en DataStore.
     */
    fun guardarNombreNegocio(nombre: String) {
        viewModelScope.launch {
            preferencesRepository.setNombreNegocio(nombre)
            _nombreNegocio.value = nombre.trim()
        }
    }

    /**
     * sincronizarNombreNegocio
     * ------------------------
     * Guarda el nombre del negocio en DataStore (identidad local/offline) y lo
     * sincroniza en Firestore (negocios/{id} y negocios_publicos/{id} en el
     * mismo Batch). Devuelve el error o null si se sincronizó correctamente.
     * Es el método que usa MiNegocioScreen cuando el negocio ya existe en la
     * nube, para que la app Cliente vea el nuevo nombre.
     */
    suspend fun sincronizarNombreNegocio(nombre: String): String? {
        if (nombre.isBlank()) return "El nombre del negocio no puede estar vacío"

        _operandoRemoto.value = true
        try {
            preferencesRepository.setNombreNegocio(nombre.trim())
            _nombreNegocio.value = nombre.trim()
            val resultado = negocioRepository.guardarNombreNegocio(nombre.trim())
            return if (resultado.exito) null else resultado.mensaje
        } finally {
            _operandoRemoto.value = false
        }
    }

    /**
     * sincronizarLogoNegocio
     * ----------------------
     * Sube el logo local a Firebase Storage, guarda la URL remota en Firestore
     * (negocios/{id} y negocios_publicos/{id}) y actualiza DataStore como caché.
     * Si la subida o el Batch fallan, NO se borra el logo local y se devuelve el
     * error para que la UI lo muestre sin dar la operación por exitosa.
     */
    suspend fun sincronizarLogoNegocio(rutaLocal: String): String? {
        if (rutaLocal.isBlank()) return "No hay logo para subir"

        // GATE TÉRMINOS (UGC): sin la versión vigente aceptada NO se publica el
        // logo. Se devuelve el error ANTES de llamar a guardarLogoRemoto (no hay
        // putFile ni actualización de negocios/negocios_publicos).
        if (!terminosAceptados()) {
            return "Debes aceptar los Términos de uso para cambiar el logo"
        }

        _operandoRemoto.value = true
        try {
            val resultado = negocioRepository.guardarLogoRemoto(rutaLocal)
            if (!resultado.exito) return resultado.mensaje
            val rutaGuardada = resultado.url ?: rutaLocal
            preferencesRepository.setLogoNegocio(rutaGuardada)
            _logoNegocio.value = rutaGuardada
            return null
        } finally {
            _operandoRemoto.value = false
        }
    }

    /**
     * guardarLogoNegocio
     * ------------------
     * Guarda la ruta del logo del negocio en DataStore.
     */
    fun guardarLogoNegocio(ruta: String) {
        viewModelScope.launch {
            preferencesRepository.setLogoNegocio(ruta)
            _logoNegocio.value = ruta
        }
    }

    /**
     * guardarIdClienteSesion
     * ----------------------
     * Guarda el id del cliente recién registrado.
     */
    fun guardarIdClienteSesion(id: Int) {
        viewModelScope.launch {
            preferencesRepository.setIdClienteSesion(id)
            _idClienteSesion.value = id
        }
    }

    /**
     * borrarIdClienteSesion
     * ---------------------
     * Borra el id del cliente guardado en DataStore.
     */
    fun borrarIdClienteSesion() {
        viewModelScope.launch {
            preferencesRepository.borrarIdClienteSesion()
            _idClienteSesion.value = null
        }
    }
}

/**
 * EstadoPreparacion
 * -----------------
 * Estado del guard de propietario de la caché local que ve la capa UI.
 * - Preparando: resolviendo el propietario (spinner).
 * - SinSesion: sin usuario autenticado (Login).
 * - Listo: la caché pertenece a la cuenta actual y se pueden mostrar datos.
 * - Indeterminado: owner == null con datos locales; exige decisión (no se
 *   adopta automáticamente). Opción segura por defecto: empezar desde cero.
 * - Bloqueado: cambio de propietario con pendientes críticos de la cuenta
 *   anterior; hay que volver a esa cuenta o descartar explícitamente.
 */
sealed interface EstadoPreparacion {
    object Preparando : EstadoPreparacion
    object SinSesion : EstadoPreparacion
    object Listo : EstadoPreparacion
    data class Indeterminado(
        val pendientes: PreparadorLocalCuenta.InformePendientes
    ) : EstadoPreparacion
    data class Bloqueado(
        val pendientes: PreparadorLocalCuenta.InformePendientes
    ) : EstadoPreparacion
}
