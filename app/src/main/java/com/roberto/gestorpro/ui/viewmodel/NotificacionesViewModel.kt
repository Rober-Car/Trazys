package com.roberto.gestorpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.firebase.AutenticacionRepository
import com.roberto.gestorpro.data.firebase.LecturaNotificacion
import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.model.ConfiguracionNotificaciones
import com.roberto.gestorpro.model.DestinatarioResuelto
import com.roberto.gestorpro.model.NotificacionAdmin
import com.roberto.gestorpro.model.ResolucionDestinatarios
import com.roberto.gestorpro.util.GateUgcNotificaciones
import com.roberto.gestorpro.util.ResultadoGateUgc
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * NotificacionesViewModel
 * -----------------------
 * ViewModel de la gestión de notificaciones del ADMIN (Fase D).
 *
 * Coordina: lista de notificaciones del negocio, resolución de destinatarios
 * (quiénes tienen firebaseUid válido), creación inmediata/programada,
 * cancelación de programadas y la configuración de preconfiguradas.
 *
 * Sigue el patrón de error/sincronización de la app: si una creación falla,
 * el error no se oculta, queda una operación pendiente y se ofrece el
 * reintento manual.
 *
 * GATE DE TÉRMINOS (UGC): la publicación MANUAL (inmediata o programada) exige
 * la versión vigente de los Términos aceptada ANTES de tocar Firestore. Las
 * notificaciones automáticas/preconfiguradas no pasan por este ViewModel, por
 * lo que nunca se bloquean aquí.
 */
@HiltViewModel
class NotificacionesViewModel @Inject constructor(
    private val notificacionRemotoRepository: NotificacionRemotoRepository,
    private val autenticacionRepository: AutenticacionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /**
     * Datos de la última creación que falló, para poder reintentarla.
     */
    private data class PendienteCreacion(
        val titulo: String,
        val mensaje: String,
        val modoDestino: String,
        val clienteId: Int?,
        val destinatarios: List<DestinatarioResuelto>,
        val idsObjetivo: List<Int>,
        val programada: Boolean,
        val fechaProgramada: Long?
    )

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _notificaciones = MutableStateFlow<List<NotificacionAdmin>>(emptyList())
    val notificaciones = _notificaciones.asStateFlow()

    /**
     * lecturaPorNotificacion
     * ----------------------
     * Resumen de lectura (leídas/total) por notificacionId, calculado desde el
     * buzón. Independiente del estado de envío. Si la consulta falla queda
     * vacío y la lista se muestra igual, sin indicador de lectura.
     */
    private val _lecturaPorNotificacion =
        MutableStateFlow<Map<String, LecturaNotificacion>>(emptyMap())
    val lecturaPorNotificacion = _lecturaPorNotificacion.asStateFlow()

    private val _resolviendo = MutableStateFlow(false)
    val resolviendo = _resolviendo.asStateFlow()

    private val _errorResolucion = MutableStateFlow<String?>(null)
    val errorResolucion = _errorResolucion.asStateFlow()

    private val _resolucion = MutableStateFlow<ResolucionDestinatarios?>(null)
    val resolucion = _resolucion.asStateFlow()

    private val _creando = MutableStateFlow(false)
    val creando = _creando.asStateFlow()

    private val _errorSincronizacion = MutableStateFlow<String?>(null)
    val errorSincronizacion = _errorSincronizacion.asStateFlow()

    private val _creacionPendiente = MutableStateFlow(false)
    val creacionPendiente = _creacionPendiente.asStateFlow()

    private var pendienteCreacion: PendienteCreacion? = null

    /**
     * requiereAceptarTerminos
     * -----------------------
     * true cuando el ADMIN intentó publicar manualmente una notificación sin
     * tener aceptada la versión VIGENTE de los Términos de uso. La pantalla
     * muestra entonces el aviso con acceso a los Términos SIN cerrar el
     * formulario ni perder lo escrito. No se crea nada en Firestore.
     */
    private val _requiereAceptarTerminos = MutableStateFlow(false)
    val requiereAceptarTerminos = _requiereAceptarTerminos.asStateFlow()

    private val _mensajeExito = MutableStateFlow<String?>(null)
    val mensajeExito = _mensajeExito.asStateFlow()

    /**
     * retirandoNotificacion
     * ---------------------
     * Id de la notificación manual que se está retirando (o null si no hay
     * ninguna operación de retirada en curso). Permite evitar la doble
     * pulsación y mostrar el estado de carga en la tarjeta correspondiente.
     */
    private val _retirandoNotificacion = MutableStateFlow<String?>(null)
    val retirandoNotificacion = _retirandoNotificacion.asStateFlow()

    private val _configuracion = MutableStateFlow<ConfiguracionNotificaciones?>(null)
    val configuracion = _configuracion.asStateFlow()

    private val _cargandoConfiguracion = MutableStateFlow(false)
    val cargandoConfiguracion = _cargandoConfiguracion.asStateFlow()

    private val _guardandoConfiguracion = MutableStateFlow(false)
    val guardandoConfiguracion = _guardandoConfiguracion.asStateFlow()

    private val _errorConfiguracion = MutableStateFlow<String?>(null)
    val errorConfiguracion = _errorConfiguracion.asStateFlow()

    /**
     * seleccionGrupo
     * --------------
     * Clientes seleccionados para una notificación GRUPAL. Se mantiene en el
     * ViewModel compartido para que la selección sobreviva a los cambios de
     * filtro/búsqueda dentro de la pantalla de selección y al volver al
     * formulario de creación.
     */
    private val _seleccionGrupo = MutableStateFlow<Set<Int>>(emptySet())
    val seleccionGrupo = _seleccionGrupo.asStateFlow()

    /**
     * iniciarSeleccionGrupo
     * ---------------------
     * Prepara la selección grupal con la selección previa (o vacía) antes de
     * abrir la pantalla de selección de clientes.
     */
    fun iniciarSeleccionGrupo(actuales: Set<Int>) {
        _seleccionGrupo.value = actuales
    }

    /**
     * actualizarSeleccionGrupo
     * ------------------------
     * Actualiza la selección grupal a medida que el ADMIN marca/deselecciona
     * clientes en la pantalla de selección.
     */
    fun actualizarSeleccionGrupo(ids: Set<Int>) {
        _seleccionGrupo.value = ids
    }

    /**
     * seleccionIndividual
     * -------------------
     * Cliente elegido para una notificación INDIVIDUAL (máximo uno). Se guarda
     * en el ViewModel compartido para que la elección sobreviva al paso por la
     * pantalla de selección y se refleje en el formulario al volver con
     * "Continuar".
     */
    private val _seleccionIndividual = MutableStateFlow<Int?>(null)
    val seleccionIndividual = _seleccionIndividual.asStateFlow()

    /**
     * fijarSeleccionIndividual
     * ------------------------
     * Confirma el cliente elegido al pulsar "Continuar" en el selector
     * individual. Solo se llama en la confirmación (volver atrás no lo altera).
     */
    fun fijarSeleccionIndividual(idCliente: Int?) {
        _seleccionIndividual.value = idCliente
    }

    /**
     * terminosVigentes
     * ----------------
     * ¿El ADMIN autenticado aceptó la versión vigente de los Términos de uso?
     * Reutiliza el sistema existente (DataStore + TerminosDeUso.aceptado).
     */
    private suspend fun terminosVigentes(): Boolean {
        val uid = autenticacionRepository.uidActual() ?: return false
        return preferencesRepository.terminosAceptados(uid)
    }

    /**
     * publicacionManualBloqueadaPorTerminos
     * -------------------------------------
     * Aplica el gate de Términos de uso a una publicación MANUAL (inmediata o
     * programada). true = bloqueada (no se crea nada en Firestore). Las
     * automáticas/preconfiguradas nunca llegan aquí (no usan este ViewModel).
     */
    private suspend fun publicacionManualBloqueadaPorTerminos(): Boolean =
        GateUgcNotificaciones.decidir(
            origen = GateUgcNotificaciones.ORIGEN_MANUAL,
            terminosVigentes = terminosVigentes()
        ) == ResultadoGateUgc.BLOQUEADO

    /**
     * cargarNotificaciones
     * --------------------
     * Carga la lista de notificaciones del negocio del ADMIN autenticado y, en
     * paralelo y best-effort, el resumen de lectura del buzón (si esta segunda
     * consulta falla, la lista se muestra igualmente sin indicador de lectura).
     */
    fun cargarNotificaciones() {
        viewModelScope.launch {
            val negocioId = notificacionRemotoRepository.negocioIdActual()
                ?: run {
                    _error.value = "No hay ninguna sesión activa"
                    return@launch
                }
            _cargando.value = true
            _error.value = null
            try {
                // La lista de gestión muestra SOLO los envíos del negocio a los
                // clientes. Los avisos automáticos dirigidos al ADMIN
                // (SOLICITUD_BAJA/VINCULACION) no se ocultan aquí en Firestore,
                // simplemente se excluyen de esta lista.
                _notificaciones.value =
                    notificacionRemotoRepository.obtenerNotificaciones(negocioId)
                        .filter { esEnvioAClientes(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudieron cargar las notificaciones"
            } finally {
                _cargando.value = false
            }
            cargarLecturaBuzones(negocioId)
        }
    }

    /**
     * cargarLecturaBuzones
     * --------------------
     * Actualiza el mapa de lectura por notificación. Best-effort: un fallo de
     * esta consulta NO afecta a la lista de notificaciones; simplemente deja
     * el mapa vacío (sin indicador de lectura).
     */
    private suspend fun cargarLecturaBuzones(negocioId: String) {
        _lecturaPorNotificacion.value = emptyMap()
        try {
            _lecturaPorNotificacion.value =
                notificacionRemotoRepository.obtenerLecturaBuzones(negocioId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: sin indicador de lectura, la lista sigue visible.
            _lecturaPorNotificacion.value = emptyMap()
        }
    }

    /**
     * resolverDestinatarios
     * ---------------------
     * Comprueba qué clientes del destino tienen firebaseUid válido. Alimenta
     * el aviso "Se enviará a X de Y clientes vinculados" de la creación.
     */
    fun resolverDestinatarios(modoDestino: String, idsSeleccionados: List<Int>) {
        viewModelScope.launch {
            val negocioId = notificacionRemotoRepository.negocioIdActual() ?: return@launch
            _resolviendo.value = true
            _errorResolucion.value = null
            try {
                _resolucion.value = notificacionRemotoRepository.resolverDestinatarios(
                    negocioId,
                    modoDestino,
                    idsSeleccionados
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorResolucion.value =
                    e.message ?: "No se pudo comprobar los destinatarios"
            } finally {
                _resolviendo.value = false
            }
        }
    }

    /**
     * crearNotificacion
     * -----------------
     * Crea una notificación inmediata o programada. En éxito notifica con
     * `mensajeExito` (para la snackbar de la lista) y ejecuta onExito (volver
     * a la lista). En fallo deja `errorSincronizacion` y la operación
     * pendiente para el reintento.
     */
    fun crearNotificacion(
        titulo: String,
        mensaje: String,
        modoDestino: String,
        clienteId: Int?,
        idsObjetivo: List<Int>,
        programada: Boolean,
        fechaProgramada: Long?,
        onExito: () -> Unit
    ) {
        viewModelScope.launch {
            val negocioId = notificacionRemotoRepository.negocioIdActual()
                ?: run {
                    _error.value = "No hay ninguna sesión activa"
                    return@launch
                }

            // GATE TÉRMINOS (UGC): la publicación MANUAL requiere la versión
            // vigente aceptada. Si falta, NO se crea notificaciones/{id}, NO se
            // crean buzones y NO se publica nada: solo se muestra el aviso con
            // acceso a los Términos (el formulario se conserva en la pantalla).
            _requiereAceptarTerminos.value = false
            if (publicacionManualBloqueadaPorTerminos()) {
                _error.value = null
                _requiereAceptarTerminos.value = true
                return@launch
            }

            if (titulo.isBlank()) {
                _error.value = "El título es obligatorio"
                return@launch
            }
            if (mensaje.isBlank()) {
                _error.value = "El mensaje es obligatorio"
                return@launch
            }
            if (programada && fechaProgramada == null) {
                _error.value = "Indica la fecha de envío programado"
                return@launch
            }

            val destinatarios = if (programada) {
                emptyList()
            } else {
                val resolucion = _resolucion.value
                if (resolucion == null || resolucion.destinatarios.isEmpty()) {
                    _error.value = if (modoDestino == "INDIVIDUAL") {
                        "Este cliente no tiene su cuenta vinculada.\n" +
                            "Debe vincular su cuenta para poder recibir notificaciones."
                    } else {
                        "No hay clientes vinculados para recibir la notificación"
                    }
                    return@launch
                }
                resolucion.destinatarios
            }

            val pendiente = PendienteCreacion(
                titulo = titulo.trim(),
                mensaje = mensaje.trim(),
                modoDestino = modoDestino,
                clienteId = clienteId,
                destinatarios = destinatarios,
                idsObjetivo = idsObjetivo.distinct(),
                programada = programada,
                fechaProgramada = fechaProgramada
            )
            ejecutarCreacion(negocioId, pendiente, onExito)
        }
    }

    /**
     * reintentarCreacion
     * ------------------
     * Reintenta la última creación que falló con los mismos datos.
     */
    fun reintentarCreacion(onExito: () -> Unit) {
        val pendiente = pendienteCreacion ?: return
        val negocioId = notificacionRemotoRepository.negocioIdActual() ?: return
        viewModelScope.launch {
            // El reintento también es una publicación MANUAL: respeta el mismo gate.
            _requiereAceptarTerminos.value = false
            if (publicacionManualBloqueadaPorTerminos()) {
                _requiereAceptarTerminos.value = true
                return@launch
            }
            ejecutarCreacion(negocioId, pendiente, onExito)
        }
    }

    /**
     * cancelarNotificacion
     * --------------------
     * Pone una notificación programada en CANCELADA.
     */
    fun cancelarNotificacion(notificacionId: String) {
        viewModelScope.launch {
            val resultado =
                notificacionRemotoRepository.cancelarNotificacion(notificacionId)
            if (resultado.exito) {
                _mensajeExito.value = resultado.mensaje
            } else {
                _errorSincronizacion.value = resultado.mensaje
            }
        }
    }

    /**
     * retirarNotificacion
     * -------------------
     * Retira una notificación MANUAL ya publicada (moderación UGC): elimina el
     * registro y sus buzones. El resultado positivo se publica en
     * `mensajeExito` (provoca la snackbar y la recarga de la lista); el fallo
     * se publica en `errorSincronizacion`. Evita la doble pulsación: si ya hay
     * una retirada en curso, ignora la llamada.
     */
    fun retirarNotificacion(notificacionId: String) {
        if (_retirandoNotificacion.value != null) return
        viewModelScope.launch {
            _retirandoNotificacion.value = notificacionId
            _errorSincronizacion.value = null
            try {
                val resultado =
                    notificacionRemotoRepository.retirarNotificacionManual(notificacionId)
                if (resultado.exito) {
                    _mensajeExito.value = resultado.mensaje
                } else {
                    _errorSincronizacion.value = resultado.mensaje
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorSincronizacion.value =
                    e.message ?: "No se pudo retirar la notificación"
            } finally {
                _retirandoNotificacion.value = null
            }
        }
    }

    /**
     * cargarConfiguracion
     * -------------------
     * Carga la configuración de preconfiguradas del negocio.
     */
    fun cargarConfiguracion() {
        viewModelScope.launch {
            val negocioId = notificacionRemotoRepository.negocioIdActual()
                ?: run {
                    _errorConfiguracion.value = "No hay ninguna sesión activa"
                    return@launch
                }
            _cargandoConfiguracion.value = true
            _errorConfiguracion.value = null
            try {
                _configuracion.value =
                    notificacionRemotoRepository.obtenerConfiguracion(negocioId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorConfiguracion.value =
                    e.message ?: "No se pudo cargar la configuración"
            } finally {
                _cargandoConfiguracion.value = false
            }
        }
    }

    /**
     * guardarConfiguracion
     * --------------------
     * Guarda la configuración de preconfiguradas. En éxito publica
     * `mensajeExito` para la snackbar.
     */
    fun guardarConfiguracion(config: ConfiguracionNotificaciones) {
        viewModelScope.launch {
            val negocioId = notificacionRemotoRepository.negocioIdActual()
                ?: run {
                    _errorConfiguracion.value = "No hay ninguna sesión activa"
                    return@launch
                }
            _guardandoConfiguracion.value = true
            _errorConfiguracion.value = null
            try {
                val resultado =
                    notificacionRemotoRepository.guardarConfiguracion(negocioId, config)
                if (resultado.exito) {
                    _mensajeExito.value = resultado.mensaje
                } else {
                    _errorConfiguracion.value = resultado.mensaje
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorConfiguracion.value =
                    e.message ?: "No se pudo guardar la configuración"
            } finally {
                _guardandoConfiguracion.value = false
            }
        }
    }

    /**
     * consumirMensajeExito
     * --------------------
     * Limpia el mensaje de éxito tras mostrarlo en la snackbar.
     */
    fun consumirMensajeExito() {
        _mensajeExito.value = null
    }

    /**
     * limpiarErrorCreacion
     * --------------------
     * Limpia el error de validación/creación al cambiar el formulario.
     */
    fun limpiarErrorCreacion() {
        _error.value = null
        _errorSincronizacion.value = null
    }

    /**
     * resetTrasCambioCuenta
     * ---------------------
     * Limpia TODO el estado propio del ViewModel cuando la capa UI detecta un
     * cambio de propietario (WIPE de la caché local). Se llama desde
     * AppNavigation al observar el token de cambio de propietario; el propio
     * ViewModel se resetea a sí mismo (nadie externo conoce sus campos).
     */
    fun resetTrasCambioCuenta() {
        _cargando.value = false
        _error.value = null
        _notificaciones.value = emptyList()
        _lecturaPorNotificacion.value = emptyMap()
        _resolviendo.value = false
        _errorResolucion.value = null
        _resolucion.value = null
        _creando.value = false
        _errorSincronizacion.value = null
        _creacionPendiente.value = false
        pendienteCreacion = null
        _requiereAceptarTerminos.value = false
        _mensajeExito.value = null
        _retirandoNotificacion.value = null
        _configuracion.value = null
        _cargandoConfiguracion.value = false
        _guardandoConfiguracion.value = false
        _errorConfiguracion.value = null
        _seleccionGrupo.value = emptySet()
        _seleccionIndividual.value = null
    }

    /**
     * ejecutarCreacion
     * ----------------
     * Núcleo de la creación (compartido entre creación y reintento).
     */
    private suspend fun ejecutarCreacion(
        negocioId: String,
        pendiente: PendienteCreacion,
        onExito: () -> Unit
    ) {
        _creando.value = true
        _errorSincronizacion.value = null
        try {
            val resultado = notificacionRemotoRepository.crearNotificacion(
                negocioId = negocioId,
                titulo = pendiente.titulo,
                mensaje = pendiente.mensaje,
                modoDestino = pendiente.modoDestino,
                clienteId = pendiente.clienteId,
                destinatarios = pendiente.destinatarios,
                idsObjetivo = pendiente.idsObjetivo,
                programada = pendiente.programada,
                fechaProgramada = pendiente.fechaProgramada
            )
            if (resultado.exito) {
                pendienteCreacion = null
                _creacionPendiente.value = false
                _mensajeExito.value = resultado.mensaje
                onExito()
            } else {
                pendienteCreacion = pendiente
                _creacionPendiente.value = true
                _errorSincronizacion.value = resultado.mensaje
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendienteCreacion = pendiente
            _creacionPendiente.value = true
            _errorSincronizacion.value =
                e.message ?: "Error inesperado al crear la notificación"
        } finally {
            _creando.value = false
        }
    }
}

/**
 * esEnvioAClientes
 * ----------------
 * Determina si un registro de `notificaciones/{id}` corresponde a un ENVÍO del
 * negocio hacia los clientes (y no a un aviso automático dirigido al ADMIN).
 *
 * Los únicos escritores de avisos hacia el ADMIN en esa colección usan
 * `tipo == SOLICITUD_BAJA` (generado por la gestión de solicitudes de baja) o
 * `tipo == VINCULACION` (generado al vincularse un cliente). El resto de tipos
 * que produce el sistema (MANUAL, PROGRAMADA, BAJA_CONFIRMADA, MOROSIDAD) son
 * envíos destinados a clientes, por lo que se consideran "enviadas".
 */
private fun esEnvioAClientes(notificacion: com.roberto.gestorpro.model.NotificacionAdmin): Boolean =
    notificacion.tipo != "SOLICITUD_BAJA" && notificacion.tipo != "VINCULACION"
