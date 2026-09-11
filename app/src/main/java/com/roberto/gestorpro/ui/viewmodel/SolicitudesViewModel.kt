package com.roberto.gestorpro.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.firebase.BajaClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.data.firebase.SolicitudRemotoRepository
import com.roberto.gestorpro.data.repository.ClienteRepository
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.data.repository.ReservaRepository
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.model.EstadoSolicitud
import com.roberto.gestorpro.model.SolicitudBaja
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SolicitudesViewModel
 * --------------------
 * ViewModel de las solicitudes de baja del ADMIN.
 *
 * Lista las solicitudes del negocio, acepta (solicitud -> ACEPTADA y cliente
 * -> BAJA de forma atómica en Firestore + actualización local en Room) y
 * rechaza (solicitud -> RECHAZADA, cliente intacto). Al aceptar, la lógica
 * compartida de BAJA EFECTIVA (BajaClienteRemotoRepository) cancela las
 * reservas futuras del cliente y genera la notificación BAJA_CONFIRMADA si la
 * configuración lo permite (mismas consecuencias que la baja directa).
 */
@HiltViewModel
class SolicitudesViewModel @Inject constructor(
    private val solicitudRemotoRepository: SolicitudRemotoRepository,
    private val clienteRepository: ClienteRepository,
    private val movimientoRepository: MovimientoRepository,
    private val reservaRepository: ReservaRepository,
    private val bajaClienteRemotoRepository: BajaClienteRemotoRepository,
    private val notificacionRemotoRepository: NotificacionRemotoRepository
) : ViewModel() {

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _solicitudes = MutableStateFlow<List<SolicitudBaja>>(emptyList())
    val solicitudes = _solicitudes.asStateFlow()

    /**
     * _solicitudesPendientes / solicitudesPendientes
     * ----------------------------------------------
     * Número de solicitudes de baja con estado PENDIENTE. Lo consume el badge
     * de la Home del ADMIN. Se calcula con una lectura SOLA (sin generar los
     * avisos SOLICITUD_BAJA, que solo debe provocar la lista de solicitudes).
     */
    private val _solicitudesPendientes = MutableStateFlow(0)
    val solicitudesPendientes = _solicitudesPendientes.asStateFlow()

    private val _errorSincronizacion = MutableStateFlow<String?>(null)
    val errorSincronizacion = _errorSincronizacion.asStateFlow()

    private val _solicitudSinSincronizar = MutableStateFlow<SolicitudBaja?>(null)
    val solicitudSinSincronizar = _solicitudSinSincronizar.asStateFlow()

    private val _mensajeExito = MutableStateFlow<String?>(null)
    val mensajeExito = _mensajeExito.asStateFlow()

    private var pendienteEsAceptar = false

    companion object {
        private const val TAG = "SolicitudesViewModel"
    }

    /**
     * cargarSolicitudes
     * -----------------
     * Carga las solicitudes del negocio del ADMIN autenticado.
     */
    fun cargarSolicitudes() {
        viewModelScope.launch {
            val negocioId = solicitudRemotoRepository.negocioIdActual()
                ?: run {
                    _error.value = "No hay ninguna sesión activa"
                    return@launch
                }
            _cargando.value = true
            _error.value = null
            try {
                _solicitudes.value = solicitudRemotoRepository.obtenerSolicitudes(negocioId)
                generarAvisosDeSolicitudesPendientes(negocioId, _solicitudes.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudieron cargar las solicitudes"
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * cargarPendientes
     * ----------------
     * Actualiza el contador de solicitudes PENDIENTES para el badge de la Home.
     * Es una lectura SOLA (obtenerSolicitudes) y NO genera los avisos
     * SOLICITUD_BAJA: eso solo debe ocurrir al abrir la lista de solicitudes.
     * Ante un fallo conserva el último valor conocido.
     */
    fun cargarPendientes() {
        viewModelScope.launch {
            val negocioId = solicitudRemotoRepository.negocioIdActual() ?: return@launch
            try {
                _solicitudesPendientes.value = solicitudRemotoRepository
                    .obtenerSolicitudes(negocioId)
                    .count { it.estado == EstadoSolicitud.PENDIENTE }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El badge conserva el último valor conocido.
            }
        }
    }

    /**
     * generarAvisosDeSolicitudesPendientes
     * ------------------------------------
     * Por cada solicitud PENDIENTE que aún no tenga su aviso en la bandeja del
     * ADMIN (notificaciones/{id} con tipo SOLICITUD_BAJA), lo crea de forma
     * idempotente (ID determinista). Así el ADMIN recibe el aviso "X ha
     * solicitado la baja" con la infraestructura existente y sin depender de
     * Cloud Functions. Un fallo de creación no rompe la carga de la lista.
     */
    private suspend fun generarAvisosDeSolicitudesPendientes(
        negocioId: String,
        solicitudes: List<SolicitudBaja>
    ) {
        solicitudes
            .filter { it.estado == EstadoSolicitud.PENDIENTE }
            .forEach { solicitud ->
                try {
                    val cliente = clienteRepository.obtenerClientePorIdRepo(solicitud.idCliente)
                    val nombre = cliente?.let {
                        "${it.nombre.trim()} ${it.apellidos.trim()}".trim()
                    }.orEmpty()
                    notificacionRemotoRepository.crearNotificacionSolicitudBaja(
                        negocioId = negocioId,
                        clienteId = solicitud.idCliente,
                        nombreCliente = nombre.ifBlank { "El cliente" },
                        fechaSolicitud = solicitud.fechaSolicitud
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "No se pudo generar el aviso de la solicitud ${solicitud.idSolicitud}",
                        e
                    )
                }
            }
    }

    /**
     * aceptar
     * -------
     * Acepta la solicitud: en Firestore la solicitud pasa a ACEPTADA y el
     * cliente a BAJA (Transaction atómica); después actualiza la ficha local
     * (Room) para mantener la lista coherente y, si la configuración lo
     * permite, crea la notificación BAJA_CONFIRMADA.
     */
    fun aceptar(solicitud: SolicitudBaja) {
        viewModelScope.launch {
            _errorSincronizacion.value = null
            _solicitudSinSincronizar.value = null
            pendienteEsAceptar = true

            val fechaBaja = System.currentTimeMillis()
            val resultado = solicitudRemotoRepository.aceptarBaja(solicitud, fechaBaja)
            if (resultado.exito) {
                actualizarFichaLocal(solicitud, fechaBaja)
                cancelarReservasFuturasLocales(solicitud.idCliente)
                // Consecuencias compartidas con la baja directa: reservas futuras
                // en Firestore + notificación BAJA_CONFIRMADA (si config).
                bajaClienteRemotoRepository.bajaEfectiva(solicitud.idCliente, fechaBaja)
                _mensajeExito.value = resultado.mensaje
                cargarSolicitudes()
            } else {
                _errorSincronizacion.value = resultado.mensaje
                _solicitudSinSincronizar.value = solicitud
            }
        }
    }

    /**
     * rechazar
     * --------
     * Rechaza la solicitud (solicitud -> RECHAZADA). El cliente permanece como
     * estaba (ACTIVO).
     */
    fun rechazar(solicitud: SolicitudBaja) {
        viewModelScope.launch {
            _errorSincronizacion.value = null
            _solicitudSinSincronizar.value = null
            pendienteEsAceptar = false

            val resultado = solicitudRemotoRepository.rechazarSolicitud(solicitud)
            if (resultado.exito) {
                // Aviso al CLIENTE (idempotente y según config). Un fallo no
                // revierte el rechazo, que ya quedó aplicado en Firestore.
                notificarBajaRechazada(solicitud)
                _mensajeExito.value = resultado.mensaje
                cargarSolicitudes()
            } else {
                _errorSincronizacion.value = resultado.mensaje
                _solicitudSinSincronizar.value = solicitud
            }
        }
    }

    /**
     * notificarBajaRechazada
     * ----------------------
     * Crea la notificación al CLIENTE (con su buzón) cuando se rechaza una
     * solicitud de baja. Idempotente (ID determinista por solicitud) y respeta
     * el switch "Baja rechazada". NO modifica el cliente ni las reservas. Un
     * fallo de la notificación solo se registra: no revierte el rechazo.
     */
    private suspend fun notificarBajaRechazada(solicitud: SolicitudBaja) {
        try {
            notificacionRemotoRepository.crearNotificacionBajaRechazada(
                negocioId = solicitud.negocioId,
                idCliente = solicitud.idCliente,
                idSolicitud = solicitud.idSolicitud
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear la notificación de baja rechazada", e)
        }
    }

    /**
     * eliminarSolicitud
     * ------------------
     * Elimina del historial una solicitud ya resuelta (ACEPTADA/RECHAZADA).
     * No se ofrece para PENDIENTE (las Rules también lo rechazan) y no altera
     * el estado del cliente ni ningún otro dato.
     */
    fun eliminarSolicitud(solicitud: SolicitudBaja) {
        viewModelScope.launch {
            _errorSincronizacion.value = null
            _solicitudSinSincronizar.value = null
            val resultado = solicitudRemotoRepository.eliminarSolicitud(solicitud.idSolicitud)
            if (resultado.exito) {
                _mensajeExito.value = resultado.mensaje
                cargarSolicitudes()
            } else {
                _errorSincronizacion.value = resultado.mensaje
            }
        }
    }

    /**
     * reintentarSincronizacion
     * ------------------------
     * Repite la última resolución (aceptar o rechazar) que falló.
     */
    fun reintentarSincronizacion() {
        val pendiente = _solicitudSinSincronizar.value ?: return
        if (pendienteEsAceptar) {
            aceptar(pendiente)
        } else {
            rechazar(pendiente)
        }
    }

    /**
     * consumirMensajeExito
     * --------------------
     * Limpia el mensaje de éxito tras mostrarlo.
     */
    fun consumirMensajeExito() {
        _mensajeExito.value = null
    }

    /**
     * actualizarFichaLocal
     * --------------------
     * Refleja en Room el cambio de estado del cliente al aceptar la baja.
     */
    private suspend fun actualizarFichaLocal(solicitud: SolicitudBaja, fechaBaja: Long) {
        try {
            clienteRepository.obtenerClientePorIdRepo(solicitud.idCliente)?.let { entidad ->
                clienteRepository.actualizarClienteRepo(
                    entidad.copy(
                        estado = EstadoCliente.BAJA,
                        fechaBaja = fechaBaja
                    )
                )
                // Recalcula morosidad con la lógica única (BAJA solo por deuda).
                movimientoRepository.recalcularMorosidadDeCliente(entidad.idCliente)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar la ficha local del cliente ${solicitud.idCliente}", e)
        }
    }

    /**
     * cancelarReservasFuturasLocales
     * -------------------------------
     * Cancela en Room las reservas futuras del cliente al aceptar la baja,
     * liberando las plazas. Las reservas de sesiones pasadas se conservan.
     */
    private suspend fun cancelarReservasFuturasLocales(idCliente: Int) {
        try {
            reservaRepository.cancelarReservasFuturasDeCliente(idCliente, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron cancelar las reservas futuras del cliente $idCliente", e)
        }
    }
}
