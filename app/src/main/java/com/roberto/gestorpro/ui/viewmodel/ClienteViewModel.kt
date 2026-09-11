/* ============================================================
 * ============ BLOQUE 1: IMPORTS =============================
 * ============================================================ */
package com.roberto.gestorpro.ui.viewmodel

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.entity.ClienteEntity
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.data.entity.toCliente
import com.roberto.gestorpro.data.firebase.AutenticacionRepository
import com.roberto.gestorpro.data.firebase.BajaClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.ClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.FotoClienteCache
import com.roberto.gestorpro.data.firebase.FotoClienteStorage
import com.google.firebase.storage.FirebaseStorage
import com.roberto.gestorpro.data.repository.ClienteRepository
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.data.repository.ReservaRepository
import com.roberto.gestorpro.data.repository.ServicioRepository
import com.roberto.gestorpro.model.Cliente
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.util.IdCliente
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ============================================================
 * ============ BLOQUE 2: DOCUMENTACIÓN DEL ARCHIVO ===========
 * ============================================================ */
/**
 * ClienteViewModel.kt
 * -------------------
 * ✔ TIPO: archivo de código fuente Kotlin (ViewModel)
 * Es el archivo que define el ViewModel encargado de la lógica de los clientes.
 * Sirve para conectar la interfaz de usuario con el repositorio de clientes.
 */

/* ============================================================
 * ============ BLOQUE 3: VIEWMODEL DE CLIENTES ===============
 * ============================================================ */
/**
 * @HiltViewModel
 * --------------
 * ✔ TIPO: anotación (dagger.hilt.android.lifecycle.HiltViewModel)
 * Es la anotación que marca esta clase como ViewModel inyectable de Hilt.
 * Sirve para que Hilt construya el ViewModel y le inyecte sus dependencias automáticamente.
 */

/**
 * ClienteViewModel
 * ----------------
 * ✔ TIPO: clase (ViewModel de Android)
 * Es el ViewModel que gestiona los datos de los clientes en la pantalla.
 * Sirve para que la interfaz sobreviva a cambios de configuración
 * y obtenga los datos de clientes a través del repositorio.
 */
@HiltViewModel
class ClienteViewModel @Inject constructor(
    private val clienteRepository: ClienteRepository,
    private val movimientoRepository: MovimientoRepository,
    private val clienteRemotoRepository: ClienteRemotoRepository,
    private val servicioRepository: ServicioRepository,
    private val reservaRepository: ReservaRepository,
    private val bajaClienteRemotoRepository: BajaClienteRemotoRepository,
    private val storage: FirebaseStorage,
    private val fotoClienteCache: FotoClienteCache,
    private val autenticacionRepository: AutenticacionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /**
     * init
     * ----
     * Al entrar en la gestión de clientes se reintentan los borrados REMOTOS de
     * movimientos que quedaron pendientes (persistidos en Room para sobrevivir
     * al reinicio de la app).
     */
    init {
        viewModelScope.launch {
            movimientoRepository.reintentarEliminacionesPendientesGlobal()
        }
        reintentarFotosPendientes()
    }

    /**
     * terminosVigentes
     * ----------------
     * ¿El ADMIN autenticado aceptó la versión vigente de los Términos de uso?
     * Es el gate de publicación de fotos de cliente (UGC). Lectura local.
     */
    private suspend fun terminosVigentes(): Boolean {
        val uid = autenticacionRepository.uidActual() ?: return false
        return preferencesRepository.terminosAceptados(uid)
    }

    companion object {
        private const val TAG = "ClienteViewModel"
    }

    /* ============================================================
     * ============ BLOQUE 4: ESTADO DEL VIEWMODEL ================
     * ============================================================ */
    /**
     * _errorSincronizacion / errorSincronizacion
     * ------------------------------------------
     * ✔ TIPO: propiedad (private val) → MutableStateFlow<String?> y (val) → StateFlow<String?>
     * Es el mensaje del último fallo de réplica Room → Firestore.
     * Sirve para informar al ADMIN sin revertir el cambio local y para saber
     * cuándo ofrecer el reintento manual de sincronización.
     */
    private val _errorSincronizacion = MutableStateFlow<String?>(null)
    val errorSincronizacion = _errorSincronizacion.asStateFlow()

    /**
     * _clienteSinSincronizar / clienteSinSincronizar
     * ----------------------------------------------
     * ✔ TIPO: propiedad (private val) → MutableStateFlow<ClienteEntity?> y (val) → StateFlow<ClienteEntity?>
     * Es la ficha local que quedó pendiente de replicar tras un fallo remoto.
     * Sirve para poder reintentar la operación exacta (alta o edición).
     */
    private val _clienteSinSincronizar = MutableStateFlow<ClienteEntity?>(null)
    val clienteSinSincronizar = _clienteSinSincronizar.asStateFlow()

    /**
     * _sincronizacionPendienteEsAlta
     * ------------------------------
     * ✔ TIPO: propiedad (private val) → MutableStateFlow<Boolean>
     * Indica si la réplica pendiente es un alta (true) o una edición (false).
     * Sirve al reintento para llamar a la operación remota correcta.
     */
    private val _sincronizacionPendienteEsAlta = MutableStateFlow(false)

    /**
     * _sincronizacionPendienteServicios
     * ----------------------------------
     * Indica si la réplica pendiente es la de SOLO los servicios contratados
     * (true) en lugar de un alta/edición completa (false). Sirve al reintento
     * para llamar a la operación remota correcta.
     */
    private val _sincronizacionPendienteServicios = MutableStateFlow(false)
    /**
     * _error
     * ------
     * ✔ TIPO: propiedad (private val) → MutableStateFlow<String?>
     * Es el flujo privado de errores del ViewModel, mutable solo dentro de la clase.
     * Sirve para guardar el mensaje de error actual y cambiarlo cuando ocurre una excepción.
     */
    private val _error = MutableStateFlow<String?>(null)

    /**
     * error
     * -----
     * ✔ TIPO: propiedad (val) → StateFlow<String?>
     * Es la versión de solo lectura del flujo _error.
     * Sirve para que la interfaz observe el mensaje de error en tiempo real y lo muestre al usuario.
     */
    val error = _error.asStateFlow()

    /**
     * _guardandoAlta / guardandoAlta
     * ------------------------------
     * Bloqueo REAL del alta de cliente en el ViewModel: mientras `insertarCliente`
     * está en curso (subida de foto + creación remota + Room) no se admite una
     * segunda llamada, evitando subidas duplicadas y altas dobles.
     */
    private val _guardandoAlta = MutableStateFlow(false)
    val guardandoAlta: StateFlow<Boolean> = _guardandoAlta.asStateFlow()

    private val _clienteSeleccionado = MutableStateFlow<Cliente?>(null)

    val clienteSeleccionado = _clienteSeleccionado.asStateFlow()

    /**
     * _fotoPerfil
     * -----------
     * Fichero local de la foto del cliente mostrado en el perfil (descargado con
     * el SDK de Storage autenticado y cacheado). null mientras no hay foto, es
     * local/legacy o falla la descarga sin caché disponible.
     */
    private val _fotoPerfil = MutableStateFlow<File?>(null)
    val fotoPerfil: StateFlow<File?> = _fotoPerfil.asStateFlow()

    /**
     * _clienteEditando
     * ----------------
     * ✔ TIPO: propiedad privada (val) → MutableStateFlow<ClienteEntity?>
     * Es el flujo que guarda el cliente que se está editando en el formulario.
     * Sirve para conservar en memoria los datos originales del cliente mientras se editan.
     */
    private val _clienteEditando = MutableStateFlow<ClienteEntity?>(null)

    /**
     * clienteEditando
     * ---------------
     * ✔ TIPO: propiedad (val) → StateFlow<ClienteEntity?>
     * Es la versión pública e inmutable de _clienteEditando.
     * Sirve para que la pantalla de modificar cliente observe y precargue los datos del cliente.
     */
    val clienteEditando = _clienteEditando.asStateFlow()

    /**
     * clientes
     * --------
     * ✔ TIPO: propiedad (val) → StateFlow<List<Cliente>>
     * Es el flujo de clientes convertido en StateFlow con stateIn(),
     * transformando cada ClienteEntity en Cliente con toCliente().
     * Sirve para que la interfaz observe en tiempo real la lista de clientes de la base de datos,
     * usando emptyList() como valor inicial mientras los datos cargan.
     */
    val clientes = clienteRepository.obtenerClientesRepo()
        .map { lista ->
            lista.map { it.toCliente() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * morososIds
     * ----------
     * ✔ TIPO: propiedad (val) → StateFlow<Set<Int>>
     * Es el flujo que contiene los IDs de los clientes que son morosos.
     * Sirve para que la lista de clientes pueda filtrar y marcar visualmente
     * a los clientes morosos calculando su estado desde los movimientos.
     */
    val morososIds = clienteRepository.obtenerIdsMorososRepo()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // ---- Selección múltiple de clientes (lista principal) ----
    private val _modoSeleccionClientes = MutableStateFlow(false)
    val modoSeleccionClientes = _modoSeleccionClientes.asStateFlow()

    private val _clientesSeleccionados = MutableStateFlow<Set<Int>>(emptySet())
    val clientesSeleccionados: StateFlow<Set<Int>> = _clientesSeleccionados.asStateFlow()

    fun entrarEnSeleccionCliente(idCliente: Int) {
        _modoSeleccionClientes.value = true
        _clientesSeleccionados.value = setOf(idCliente)
    }

    fun alternarSeleccionCliente(idCliente: Int) {
        _clientesSeleccionados.value = alternarIdEnSeleccion(_clientesSeleccionados.value, idCliente)
    }

    fun salirSeleccionClientes() {
        _modoSeleccionClientes.value = false
        _clientesSeleccionados.value = emptySet()
    }

    fun limpiarSeleccionClientes() {
        _clientesSeleccionados.value = emptySet()
    }

    /**
     * podarSeleccionClientes
     * ----------------------
     * Elimina de la selección los IDs que ya no existen en la lista actual
     * (por ejemplo tras borrar/archivar en otra pantalla o refrescar Room).
     * Evita clientes fantasma seleccionados y errores al operar.
     */
    fun podarSeleccionClientes(idsExistentes: Set<Int>) {
        _clientesSeleccionados.value =
            podarSeleccion(_clientesSeleccionados.value, idsExistentes)
    }

    /**
     * activarClientesSeleccionados
     * ----------------------------
     * "Activar / dar de alta" MASIVO. Reutiliza las operaciones individuales
     * existentes según el estado de cada cliente (sin inventar transiciones):
     *  - ARCHIVADO  → restaurarCliente (mismo comportamiento que el perfil);
     *  - BAJA / REGISTRADO → reactivarCliente (misma lógica que el perfil:
     *    prepararReactivacion renueva fechaAlta y conserva fechaBaja, recalcula
     *    morosidad y replica);
     *  - ACTIVO     → no se toca.
     */
    fun activarClientesSeleccionados(ids: List<Int>) {
        viewModelScope.launch {
            for (id in ids) {
                val entidad = clienteRepository.obtenerClientePorIdRepo(id) ?: continue
                when (entidad.estado) {
                    EstadoCliente.ACTIVO -> {}
                    EstadoCliente.ARCHIVADO -> restaurarCliente(entidad)
                    else -> reactivarCliente(entidad.toCliente())
                }
            }
            salirSeleccionClientes()
        }
    }

    /**
     * archivarClientesSeleccionados
     * -----------------------------
     * "Archivar" MASIVO reutilizando `archivarCliente` (mismo comportamiento que
     * la lista/perfil actuales). Los clientes ya archivados no se tocan.
     */
    fun archivarClientesSeleccionados(ids: List<Int>) {
        viewModelScope.launch {
            for (id in ids) {
                val entidad = clienteRepository.obtenerClientePorIdRepo(id) ?: continue
                if (entidad.estado != EstadoCliente.ARCHIVADO) {
                    archivarCliente(entidad)
                }
            }
            salirSeleccionClientes()
        }
    }

    /**
     * darDeBajaClientesSeleccionados
     * ------------------------------
     * "Dar de baja" MASIVO reutilizando `darDeBaja` (la MISMA operación que usa
     * el perfil/aceptación de solicitud): cliente → BAJA con fechaBaja actual,
     * cancelación de reservas futuras (Room + Firestore liberando plazas),
     * recálculo de morosidad, réplica y BAJA_CONFIRMADA según configuración.
     * Solo aplica a clientes ACTIVO/REGISTRADO; BAJA y ARCHIVADO no se tocan.
     */
    fun darDeBajaClientesSeleccionados(
        ids: List<Int>,
        fechaBajaMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            for (id in ids) {
                val entidad = clienteRepository.obtenerClientePorIdRepo(id) ?: continue
                if (entidad.estado == EstadoCliente.ACTIVO ||
                    entidad.estado == EstadoCliente.REGISTRADO
                ) {
                    // La MISMA fecha elegida se aplica a todas las bajas del lote.
                    darDeBaja(entidad, fechaBajaMillis)
                }
            }
            salirSeleccionClientes()
        }
    }

    /**
     * _serviciosMap / serviciosMap
     * ----------------------------
     * Mapa idServicio -> nombre con TODOS los servicios (activos e inactivos).
     * Sirve para resolver los nombres de los servicios contratados del cliente
     * en el perfil, incluso si alguno se dio de baja posteriormente.
     */
    private val _serviciosMap = MutableStateFlow<Map<Int, String>>(emptyMap())
    val serviciosMap = _serviciosMap.asStateFlow()

    /**
     * _serviciosActivos / serviciosActivos
     * ------------------------------------
     * Lista de servicios activos del negocio. Sirve al selector de servicios
     * contratados: solo los activos pueden asignarse como nuevos servicios.
     */
    private val _serviciosActivos = MutableStateFlow<List<ServicioEntity>>(emptyList())
    val serviciosActivos = _serviciosActivos.asStateFlow()

    /* ============================================================
     * ============ BLOQUE 5: OPERACIONES DEL VIEWMODEL ===========
     * ============================================================ */
    /**
     * limpiarError
     * ------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que borra el mensaje de error del ViewModel.
     * Sirve para que la interfaz elimine la tarjeta de aviso al entrar en el formulario
     * o cuando el usuario vuelve a editar un campo, evitando errores obsoletos.
     */
    fun limpiarError() {
        _error.value = null
    }

    /**
     * cargarServicios
     * ---------------
     * Observa los servicios (todos) y los servicios activos para el perfil
     * y el selector de servicios contratados del cliente.
     */
    fun cargarServicios() {
        viewModelScope.launch {
            servicioRepository.obtenerTodosLosServicios().collect { lista ->
                _serviciosMap.value = lista.associate { it.idServicio to it.nombre }
            }
        }
        viewModelScope.launch {
            servicioRepository.obtenerServiciosActivos().collect { _serviciosActivos.value = it }
        }
    }

    /**
     * guardarServiciosContratados
     * ---------------------------
     * Actualiza SOLO la lista de servicios contratados del cliente, tanto en
     * Room (fuente de verdad local) como en Firestore (write-through). La
     * nueva lista sustituye a la anterior por completo. Si la réplica remota
     * falla, el cambio local se conserva, se informa y queda preparado el
     * reintento manual con el mismo mecanismo que el alta/edición.
     */
    fun guardarServiciosContratados(idCliente: Int, idsServicios: List<Int>) {
        viewModelScope.launch {
            val actual = clienteRepository.obtenerClientePorIdRepo(idCliente) ?: return@launch
            val actualizado = actual.copy(serviciosContratados = idsServicios.distinct())
            clienteRepository.actualizarClienteRepo(actualizado)

            _errorSincronizacion.value = null
            _clienteSinSincronizar.value = null
            _sincronizacionPendienteServicios.value = false

            val resultado = clienteRemotoRepository.actualizarServiciosContratadosRemoto(
                idCliente,
                actualizado.serviciosContratados
            )
            if (resultado.exito) {
                _sincronizacionPendienteEsAlta.value = false
            } else {
                _errorSincronizacion.value =
                    "Guardado en el dispositivo, pero no sincronizado con la nube: ${resultado.mensaje}"
                _clienteSinSincronizar.value = actualizado
                _sincronizacionPendienteServicios.value = true
            }
        }
    }

    /**
     * insertarCliente
     * ---------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que guarda un nuevo cliente en la base de datos.
     * Sirve para que la interfaz inserte un cliente sin bloquear la UI, lanzando la
     * operación de Room en segundo plano; limpia el error anterior, comprueba que el DNI
     * no esté ya registrado y ejecuta onExito(idGenerado) al terminar correctamente,
     * entregando el id del cliente creado (por ejemplo para guardar la sesión en Mi perfil).
     */
    fun insertarCliente(cliente: ClienteEntity, onExito: (Int) -> Unit = {}) {
        // Bloqueo real en el ViewModel: no se admite un segundo alta mientras
        // el primero esté en curso (evita subidas duplicadas y altas dobles).
        if (_guardandoAlta.value) return
        _guardandoAlta.value = true
        viewModelScope.launch {
            try {
                _error.value = null

                val existe = clienteRepository.obtenerClientePorDniRepo(cliente.dni) != null
                if (existe) {
                    _error.value = "El DNI ya está registrado"
                    return@launch
                }

                // Id ESTABLE de ámbito alto (IdCliente): no depende del
                // autoincrement de Room (que en cada PC/instalación vuelve a
                // empezar en 1 y podría colisionar con clientes ya existentes
                // en Firestore). El MISMO id se guarda en Room y se replica a
                // Firestore.
                var entidad = cliente
                var intentos = 0
                do {
                    val idCandidato = IdCliente.nuevo()
                    val idEnUso = clienteRepository.obtenerClientePorIdRepo(idCandidato) != null
                    if (!idEnUso) {
                        entidad = cliente.copy(idCliente = idCandidato)
                        break
                    }
                    intentos++
                } while (intentos < 5)

                if (entidad.idCliente == 0) {
                    _error.value = "No se pudo generar un identificador para el cliente"
                    return@launch
                }

                // FOTO OBLIGATORIA en el ALTA (el formulario ya la exige).
                if (entidad.foto.isBlank()) {
                    _error.value = "La foto es obligatoria para crear el cliente"
                    return@launch
                }

                val idClienteAlta = entidad.idCliente

                // ============================================================
                // ALTA ADMIN — DOC-FIRST
                //   1) Room con la foto LOCAL (solo en el dispositivo; sirve
                //      como pendiente para reintentar si la subida falla);
                //   2) ficha Firestore con foto="" (NUNCA la ruta local);
                //   3) subir foto (la ficha ya existe -> esAdminDelCliente);
                //   4) obtener URL;
                //   5) actualizar clientes/{id}.foto con la URL;
                //   6) Room con la URL y limpieza del fichero local.
                // Si fallan 3/4/5, queda en Room un cliente con foto local que
                // reintentarFotosPendientes() completa al abrir la app/pantalla.
                // ============================================================

                // 1) Room con la foto local.
                try {
                    clienteRepository.insertarClienteRepo(entidad)
                } catch (e: SQLiteConstraintException) {
                    _error.value = "El DNI ya está registrado"
                    return@launch
                }

                // 2) Ficha remota con foto vacía.
                val altaRemota =
                    clienteRemotoRepository.crearClienteRemoto(entidad.copy(foto = ""))
                if (!altaRemota.exito) {
                    _error.value =
                        "El cliente se guardó en el dispositivo, pero no se pudo crear en la nube: ${altaRemota.mensaje}"
                    return@launch
                }

                // 3) + 4) Subida y URL.
                // GATE TÉRMINOS: sin aceptación vigente NO se publica la foto.
                // La foto local queda en Room y el reintento la subirá cuando
                // los Términos estén aceptados.
                if (!terminosVigentes()) {
                    _error.value =
                        "La foto quedará pendiente hasta que aceptes los Términos de uso vigentes."
                    return@launch
                }
                val url = FotoClienteStorage.subirFotoCliente(
                    storage,
                    idClienteAlta,
                    entidad.foto
                )
                if (url == null) {
                    // Falló la subida o la URL: la foto local se conserva y el
                    // reintento la subirá más tarde.
                    _error.value =
                        "El cliente se creó, pero no se pudo subir su foto. Se reintentará automáticamente."
                    return@launch
                }

                // 5) Actualizar clientes/{id}.foto con la URL.
                val fotoRemota =
                    clienteRemotoRepository.actualizarFotoClienteRemoto(idClienteAlta, url)
                if (!fotoRemota.exito) {
                    _error.value =
                        "El cliente se creó, pero no se pudo actualizar su foto en la nube. Se reintentará automáticamente."
                    return@launch
                }

                // 6) Room con la URL + limpieza del fichero local.
                val entidadConUrl = entidad.copy(foto = url)
                clienteRepository.actualizarClienteRepo(entidadConUrl)
                try {
                    java.io.File(entidad.foto).delete()
                } catch (_: Exception) {
                }

                onExito(idClienteAlta)
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado en el alta del cliente", e)
                _error.value = "No se pudo crear el cliente. Inténtalo de nuevo"
            } finally {
                _guardandoAlta.value = false
            }
        }
    }

    /**
     * reintentarFotosPendientes
     * -------------------------
     * Reintento automático de las fotos del alta que quedaron pendientes
     * (clientes en Room cuya `foto` sigue siendo una ruta local). Se lanza al
     * crear el ViewModel (al abrir la gestión de clientes) y converge de forma
     * idempotente:
     *   - si la ficha remota no existe -> se crea con foto="";
     *   - si la remota ya tiene una URL -> solo se sincroniza Room y se borra
     *     el fichero local;
     *   - si no -> se sube la foto, se obtiene la URL y se actualiza
     *     clientes/{id}.foto y Room.
     * NUNCA escribe una ruta local en Firestore.
     */
    fun reintentarFotosPendientes() {
        viewModelScope.launch {
            try {
                val pendientes = clienteRepository.obtenerClientesRepo()
                    .first()
                    .filter { c ->
                        c.foto.isNotBlank() && !FotoClienteStorage.esUrlFoto(c.foto)
                    }
                for (cliente in pendientes) {
                    try {
                        val idCliente = cliente.idCliente
                        if (!clienteRemotoRepository.existeClienteRemoto(idCliente)) {
                            val alta = clienteRemotoRepository.crearClienteRemoto(
                                cliente.copy(foto = "")
                            )
                            if (!alta.exito) continue
                        }

                        val fotoRemota = clienteRemotoRepository.fotoRemotaDelCliente(idCliente)
                        if (FotoClienteStorage.esUrlFoto(fotoRemota) && fotoRemota != null) {
                            clienteRepository.actualizarClienteRepo(
                                cliente.copy(foto = fotoRemota)
                            )
                            try {
                                java.io.File(cliente.foto).delete()
                            } catch (_: Exception) {
                            }
                            continue
                        }

                        // GATE TÉRMINOS (reintento automático): sin aceptación
                        // vigente no se sube ni se elimina la foto pendiente.
                        if (!terminosVigentes()) continue

                        val url = FotoClienteStorage.subirFotoCliente(
                            storage,
                            idCliente,
                            cliente.foto
                        ) ?: continue
                        val actualizada = clienteRemotoRepository
                            .actualizarFotoClienteRemoto(idCliente, url)
                        if (!actualizada.exito) continue

                        clienteRepository.actualizarClienteRepo(cliente.copy(foto = url))
                        try {
                            java.io.File(cliente.foto).delete()
                        } catch (_: Exception) {
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo reintentar la foto del cliente ${cliente.idCliente}", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron revisar las fotos pendientes", e)
            }
        }
    }

    /**
     * incorporarClientesRemotos
     * -------------------------
     * Recupera los clientes del negocio desde Firestore y los añade a Room
     * SOLO si no existen localmente (por idCliente o por DNI). Se usa al
     * abrir la lista de clientes para que un PC nuevo con Room vacía recupere
     * los clientes ya existentes en la nube sin duplicarlos ni modificarlos.
     * No escribe en Firestore (solo lectura remota + inserción local).
     */
    fun incorporarClientesRemotos() {
        viewModelScope.launch {
            try {
                val remotos = clienteRemotoRepository.obtenerClientesRemotosDelNegocio()
                if (remotos.isEmpty()) return@launch

                var incorporados = 0
                var vinculacionesActualizadas = 0
                for (remoto in remotos) {
                    val existePorId =
                        clienteRepository.obtenerClientePorIdRepo(remoto.idCliente) != null
                    val existePorDni =
                        clienteRepository.obtenerClientePorDniRepo(remoto.dni) != null
                    if (!existePorId && !existePorDni) {
                        clienteRepository.insertarClienteRepo(remoto)
                        incorporados++
                    } else {
                        // La ficha ya existe: se actualiza SOLO el estado de
                        // vinculación local (firebaseUid) a la verdad remota,
                        // para reflejar un cliente que se vinculó en la app
                        // Cliente sin duplicar la ficha ni tocar sus datos.
                        val local = if (existePorId) {
                            clienteRepository.obtenerClientePorIdRepo(remoto.idCliente)
                        } else {
                            clienteRepository.obtenerClientePorDniRepo(remoto.dni)
                        }
                        if (local != null && local.firebaseUid != remoto.firebaseUid) {
                            clienteRepository.actualizarFirebaseUidRepo(
                                local.idCliente,
                                remoto.firebaseUid
                            )
                            vinculacionesActualizadas++
                        }
                    }
                }
                Log.i(
                    TAG,
                    "Clientes remotos reconciliados: $incorporados incorporados, " +
                        "$vinculacionesActualizadas vinculaciones actualizadas"
                )
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron recuperar los clientes remotos", e)
                _error.value = "No se pudieron recuperar los clientes de la nube"
            }
        }
    }

    /**
     * replicar
     * --------
     * ✔ TIPO: método (fun) privado suspend de Kotlin
     * Es la réplica write-through de una ficha local hacia Firestore.
     * Sirve para mantener el espejo remoto sin revertir nunca el cambio
     * local: si falla, deja el estado preparado para el reintento manual.
     */
    private suspend fun replicar(
        entidad: ClienteEntity,
        esAlta: Boolean,
        dniAnterior: String? = null
    ): Boolean {
        _errorSincronizacion.value = null
        _clienteSinSincronizar.value = null
        _sincronizacionPendienteServicios.value = false

        val resultado = if (esAlta) {
            clienteRemotoRepository.crearClienteRemoto(entidad)
        } else {
            clienteRemotoRepository.actualizarClienteRemoto(entidad, dniAnterior)
        }

        if (resultado.exito) {
            _sincronizacionPendienteEsAlta.value = false
            return true
        } else {
            _errorSincronizacion.value =
                "Guardado en el dispositivo, pero no sincronizado con la nube: ${resultado.mensaje}"
            _clienteSinSincronizar.value = entidad
            _sincronizacionPendienteEsAlta.value = esAlta
            return false
        }
    }

    /**
     * reintentarSincronizacion
     * ------------------------
     * ✔ TIPO: método (fun) de Kotlin (lanza corrutina)
     * Repite la última operación remota pendiente (alta o edición).
     * Sirve al botón "Reintentar sincronización" de las pantallas.
     */
    fun reintentarSincronizacion() {
        val pendiente = _clienteSinSincronizar.value ?: return
        viewModelScope.launch {
            if (_sincronizacionPendienteServicios.value) {
                _errorSincronizacion.value = null
                val resultado = clienteRemotoRepository.actualizarServiciosContratadosRemoto(
                    pendiente.idCliente,
                    pendiente.serviciosContratados
                )
                if (resultado.exito) {
                    _clienteSinSincronizar.value = null
                    _sincronizacionPendienteServicios.value = false
                    _sincronizacionPendienteEsAlta.value = false
                } else {
                    _errorSincronizacion.value =
                        "Guardado en el dispositivo, pero no sincronizado con la nube: ${resultado.mensaje}"
                }
            } else {
                replicar(pendiente, _sincronizacionPendienteEsAlta.value)
            }
        }
    }

    /**
     * actualizarCliente
     * -----------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que actualiza los datos de un cliente ya existente.
     * Sirve para que la interfaz modifique un cliente sin bloquear la UI, lanzando la
     * operación de Room en segundo plano; limpia el error anterior, comprueba que el nuevo
     * DNI no pertenezca a otro cliente y ejecuta onExito() al terminar correctamente.
     */
    fun actualizarCliente(cliente: ClienteEntity, onExito: () -> Unit = {}) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la actualización del cliente en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            _error.value = null

            val existente = clienteRepository.obtenerClientePorDniRepo(cliente.dni)

            if (existente != null && existente.idCliente != cliente.idCliente) {
                _error.value = "El DNI ya está registrado"
                return@launch
            }

            // Ficha previa para comparar (DNI previo para el índice atómico y
            // detección de cambio de estado).
            val fichaPrevia = clienteRepository.obtenerClientePorIdRepo(cliente.idCliente)
            val dniAnterior = fichaPrevia?.dni
            val cambiaEstado = fichaPrevia?.estado != cliente.estado

            // Reactivación de BAJA a ACTIVO: se renueva fechaAlta al instante de
            // la reactivación y se CONSERVA la fechaBaja como "última fecha de
            // baja" (frontera de la nueva etapa). Los movimientos cerrados antes
            // de esa baja no cuentan para la morosidad por fecha
            // (ver MovimientoMorosidad y prepararReactivacion).
            var clienteAEscribir = prepararReactivacion(
                entidad = cliente,
                fichaPrevia = fichaPrevia,
                ahora = System.currentTimeMillis()
            )

            // FOTO: migración progresiva a Storage. Si se quita la foto de un
            // cliente que tenía foto remota, se elimina el objeto de Storage.
            val fotoRemotaPrevia = fichaPrevia?.foto?.takeIf { FotoClienteStorage.esUrlFoto(it) }
            var fotoLocalBloqueada = false
            if (cliente.foto.isBlank()) {
                if (FotoClienteStorage.esUrlFoto(fichaPrevia?.foto)) {
                    FotoClienteStorage.eliminarFotoCliente(storage, cliente.idCliente)
                }
            } else if (!FotoClienteStorage.esUrlFoto(cliente.foto)) {
                // GATE TÉRMINOS: sin aceptación vigente NO se sube la foto nueva;
                // se conserva la ruta local en Room y el reintento la publicará
                // más adelante cuando los Términos estén aceptados. El documento
                // remoto NO recibe la ruta local (se mantiene la foto previa).
                if (terminosVigentes()) {
                    FotoClienteStorage.subirFotoCliente(storage, cliente.idCliente, cliente.foto)
                        ?.let { url ->
                            clienteAEscribir = clienteAEscribir.copy(foto = url)
                        }
                } else {
                    fotoLocalBloqueada = true
                }
            }

            try {
                clienteRepository.actualizarClienteRepo(clienteAEscribir)
                // El resumen económico solo se recalcula/publica si cambia algo
                // económico o de estado relevante (AJUSTE 3): una edición de datos
                // personales (nombre, teléfono, email, foto…) NO genera
                // sincronización económica ni su banner de error.
                if (cambiaEstado) {
                    movimientoRepository.recalcularMorosidadDeCliente(clienteAEscribir.idCliente)
                }
                val clienteParaRemoto = if (fotoLocalBloqueada) {
                    // No publicar una ruta local como foto remota mientras el
                    // ADMIN no tenga los Términos aceptados.
                    clienteAEscribir.copy(foto = fotoRemotaPrevia ?: "")
                } else {
                    clienteAEscribir
                }
                if (replicar(clienteParaRemoto, esAlta = false, dniAnterior = dniAnterior)) {
                    onExito()
                }
            } catch (e: SQLiteConstraintException) {
                _error.value = "El DNI ya está registrado"
            }
        }
    }

    /**
     * eliminarCliente
     * ---------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que elimina un cliente de la base de datos.
     * Sirve para que la interfaz borre un cliente sin bloquear la UI,
     * lanzando la operación de Room en segundo plano.
     */
    fun eliminarCliente(cliente: ClienteEntity) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la eliminación del cliente en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {
            clienteRepository.eliminarClienteRepo(cliente)
        }
    }

    /**
     * archivarCliente
     * ---------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que cambia el estado de un cliente a ARCHIVADO.
     * Sirve para ocultar un cliente de la lista principal conservando todos sus datos.
     */
    fun archivarCliente(cliente: ClienteEntity) {
        viewModelScope.launch {
            val actualizado = cliente.copy(estado = EstadoCliente.ARCHIVADO)
            clienteRepository.actualizarClienteRepo(actualizado)
            replicar(actualizado, esAlta = false)
        }
    }

    fun archivarCliente(cliente: Cliente) {
        viewModelScope.launch {
            val entity = clienteRepository.obtenerClientePorIdRepo(cliente.idCliente) ?: return@launch
            val actualizado = entity.copy(estado = EstadoCliente.ARCHIVADO)
            clienteRepository.actualizarClienteRepo(actualizado)
            replicar(actualizado, esAlta = false)
        }
    }

    /**
     * restaurarCliente
     * ----------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que restaura un cliente archivado cambiando su estado a ACTIVO.
     * Sirve para devolver un cliente archivado a la lista principal.
     */
    fun restaurarCliente(cliente: ClienteEntity) {
        viewModelScope.launch {
            val actualizado = cliente.copy(estado = EstadoCliente.ACTIVO)
            clienteRepository.actualizarClienteRepo(actualizado)
            movimientoRepository.recalcularMorosidadDeCliente(actualizado.idCliente)
            replicar(actualizado, esAlta = false)
        }
    }

    fun restaurarCliente(cliente: Cliente) {
        viewModelScope.launch {
            val entity = clienteRepository.obtenerClientePorIdRepo(cliente.idCliente) ?: return@launch
            val actualizado = entity.copy(estado = EstadoCliente.ACTIVO)
            clienteRepository.actualizarClienteRepo(actualizado)
            movimientoRepository.recalcularMorosidadDeCliente(actualizado.idCliente)
            replicar(actualizado, esAlta = false)
        }
    }

    /**
     * darDeBaja
     * ---------
     * BAJA DIRECTA de un cliente (sin solicitud previa). Produce las MISMAS
     * consecuencias de negocio que aceptar una solicitud de baja:
     *   1. Room: cliente -> BAJA + fechaBaja EFECTIVA elegida (por defecto hoy);
     *   2. Room: cancela las reservas futuras del cliente (libera plazas);
     *   3. Firestore: replica el cliente en BAJA;
     *   4. Firestore: cancela reservas futuras y genera BAJA_CONFIRMADA si la
     *      configuración lo permite (BajaClienteRemotoRepository).
     * Los servicios contratados se conservan.
     */
    fun darDeBaja(
        cliente: ClienteEntity,
        fechaBajaMillis: Long = System.currentTimeMillis(),
        onExito: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _error.value = null
            // Una BAJA nueva fija la fecha EFECTIVA elegida (por defecto HOY),
            // nunca reutiliza una fechaBaja anterior conservada de una baja previa.
            val fechaBaja = fechaBajaMillis
            val entidad = aplicarBaja(cliente, fechaBaja)

            try {
                clienteRepository.actualizarClienteRepo(entidad)
                movimientoRepository.recalcularMorosidadDeCliente(entidad.idCliente)
            } catch (e: Exception) {
                _error.value = "No se pudo guardar el cliente"
                return@launch
            }

            try {
                reservaRepository.cancelarReservasFuturasDeCliente(
                    entidad.idCliente,
                    System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron cancelar las reservas futuras en Room", e)
            }

            val dniAnterior = clienteRepository.obtenerClientePorIdRepo(entidad.idCliente)?.dni
            if (replicar(entidad, esAlta = false, dniAnterior = dniAnterior)) {
                bajaClienteRemotoRepository.bajaEfectiva(entidad.idCliente, fechaBaja)
                onExito()
            }
        }
    }

    /**
     * darDeBaja (sobrecarga para el perfil)
     * -------------------------------------
     * Adaptador que recibe el modelo [Cliente] (como el que observa el perfil)
     * y delega en [darDeBaja] con la entidad recién leída de Room. No añade
     * lógica: la baja efectiva (fechaBaja, reservas futuras, morosidad,
     * notificación y sincronización) es exactamente la ya existente.
     * Al completarse refresca el cliente seleccionado para actualizar la UI.
     */
    fun darDeBaja(
        cliente: Cliente,
        fechaBajaMillis: Long = System.currentTimeMillis(),
        onExito: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val entity = clienteRepository.obtenerClientePorIdRepo(cliente.idCliente) ?: return@launch
            darDeBaja(entity, fechaBajaMillis) {
                onExito()
                viewModelScope.launch {
                    _clienteSeleccionado.value = clienteRepository
                        .obtenerClientePorIdRepo(entity.idCliente)
                        ?.toCliente()
                }
            }
        }
    }

    /**
     * reactivarCliente
     * ----------------
     * Adaptador para el perfil que reactiva (BAJA → ACTIVO) o activa
     * (REGISTRADO → ACTIVO) un cliente. Delega en [actualizarCliente] con la
     * entidad en estado ACTIVO, que es quien ya aplica la reactivación real
     * (prepararReactivacion renueva fechaAlta y conserva fechaBaja, recalcula
     * la morosidad y replica). No añade reglas nuevas de estado.
     * Al completarse refresca el cliente seleccionado para actualizar la UI.
     */
    fun reactivarCliente(cliente: Cliente, onExito: () -> Unit = {}) {
        viewModelScope.launch {
            val entity = clienteRepository.obtenerClientePorIdRepo(cliente.idCliente) ?: return@launch
            actualizarCliente(entity.copy(estado = EstadoCliente.ACTIVO)) {
                onExito()
                viewModelScope.launch {
                    _clienteSeleccionado.value = clienteRepository
                        .obtenerClientePorIdRepo(entity.idCliente)
                        ?.toCliente()
                }
            }
        }
    }

    fun obtenerClientePorId(id: Int) {
        viewModelScope.launch {
            val clienteEntity = clienteRepository.obtenerClientePorIdRepo(id)

            _clienteSeleccionado.value = clienteEntity?.toCliente()
        }
    }

    /**
     * cargarFotoPerfil
     * ----------------
     * Carga (o devuelve de caché) la foto del cliente seleccionado para
     * mostrarla con Coil desde un fichero local (nunca con GET HTTP anónimo a
     * la URL). Las rutas locales/legacy no pasan por aquí (las muestran las
     * pantallas con File directo).
     */
    suspend fun cargarFotoPerfil(cliente: Cliente) {
        _fotoPerfil.value =
            if (FotoClienteStorage.esUrlFoto(cliente.foto)) {
                fotoClienteCache.obtener(cliente.idCliente, cliente.foto)
            } else {
                null
            }
    }

    /** Limpia el fichero de foto en memoria (p. ej. al salir del perfil). */
    fun limpiarFotoPerfil() {
        _fotoPerfil.value = null
    }

    /**
     * cargarFotoLocal
     * ---------------
     * Devuelve el fichero cacheado/descargado (SDK autenticado) de la foto
     * remota de un cliente concreto. Se usa desde listas (ClienteItem) sin
     * tocar el estado del perfil.
     */
    suspend fun cargarFotoLocal(clienteId: Int, foto: String): File? =
        fotoClienteCache.obtener(clienteId, foto)

    /**
     * obtenerClienteParaEditar
     * ------------------------
     * ✔ TIPO: método (fun) de ClienteViewModel
     * Es la función que carga de la base de datos el cliente que se va a editar.
     * Sirve para que la pantalla de modificar cliente obtenga los datos originales
     * y pueda rellenar el formulario antes de guardar los cambios.
     */
    fun obtenerClienteParaEditar(id: Int) {
        viewModelScope.launch {
            _clienteEditando.value = clienteRepository.obtenerClientePorIdRepo(id)
        }
    }

    /**
     * cambiarExentoMorosidad
     * ----------------------
     * Excepción manual controlada SOLO por el ADMIN: activa/desactiva
     * `exentoMorosidad` en Room y recalcula + publica el resumen económico
     * remoto (con la exención activa el cliente no se considera moroso, pero la
     * deuda real se mantiene).
     */
    fun cambiarExentoMorosidad(idCliente: Int, exento: Boolean) {
        viewModelScope.launch {
            clienteRepository.actualizarExentoMorosidad(idCliente, exento)
            movimientoRepository.recalcularMorosidadDeCliente(idCliente)
            _clienteSeleccionado.value = clienteRepository
                .obtenerClientePorIdRepo(idCliente)
                ?.toCliente()
        }
    }

}

/**
 * prepararReactivacion
 * --------------------
 * Función PURA de transición de estado para poder testearse: si el cliente pasa
 * de BAJA a ACTIVO se renueva `fechaAlta` (nueva inscripción) y se CONSERVA la
 * `fechaBaja` como última fecha de baja (frontera de la etapa, ver
 * MovimientoMorosidad). En cualquier otro caso devuelve la entidad sin cambios.
 */
internal fun prepararReactivacion(
    entidad: ClienteEntity,
    fichaPrevia: ClienteEntity?,
    ahora: Long
): ClienteEntity =
    if (fichaPrevia?.estado == EstadoCliente.BAJA &&
        entidad.estado == EstadoCliente.ACTIVO
    ) {
        entidad.copy(
            fechaAlta = ahora,
            fechaBaja = fichaPrevia.fechaBaja
        )
    } else {
        entidad
    }

/**
 * aplicarBaja
 * -----------
 * Función PURA de transición a BAJA para poder testearse: una BAJA nueva
 * SIEMPRE fija `fechaBaja = ahora`, sin reutilizar una fecha anterior.
 */
internal fun aplicarBaja(
    entidad: ClienteEntity,
    ahora: Long
): ClienteEntity =
    entidad.copy(estado = EstadoCliente.BAJA, fechaBaja = ahora)

/**
 * alternarIdEnSeleccion
 * ---------------------
 * Alterna (seleccionar/deseleccionar) un id de cliente dentro de la selección
 * actual. Función PURA para poder testearse sin UI ni ViewModel.
 */
internal fun alternarIdEnSeleccion(seleccion: Set<Int>, id: Int): Set<Int> =
    if (id in seleccion) seleccion - id else seleccion + id

/**
 * podarSeleccion
 * --------------
 * Conserva solo los ids que siguen existiendo en la lista actual (evita
 * clientes fantasma seleccionados). Función PURA para poder testearse.
 */
internal fun podarSeleccion(
    seleccion: Set<Int>,
    idsExistentes: Set<Int>
): Set<Int> =
    seleccion intersect idsExistentes
