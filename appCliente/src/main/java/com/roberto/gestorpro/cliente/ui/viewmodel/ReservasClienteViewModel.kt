package com.roberto.gestorpro.cliente.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.ClienteRepository
import com.roberto.gestorpro.cliente.data.firebase.ReservaRepository
import com.roberto.gestorpro.cliente.data.firebase.ResultadoAutenticacion
import com.roberto.gestorpro.cliente.data.firebase.SesionRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.model.Reserva
import com.roberto.gestorpro.cliente.model.Sesion
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** ViewModel de la infraestructura de reservas del CLIENTE. */
@HiltViewModel
class ReservasClienteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val reservaRepository: ReservaRepository,
    private val clienteRepository: ClienteRepository,
    private val sesionRepository: SesionRepository
) : ViewModel() {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _operando = MutableStateFlow(false)
    val operando = _operando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _noVinculado = MutableStateFlow(false)
    val noVinculado = _noVinculado.asStateFlow()

    private val _reservas = MutableStateFlow<List<Reserva>>(emptyList())
    val reservas = _reservas.asStateFlow()

    private val _reservasVisibles = MutableStateFlow<List<ReservaVisible>>(emptyList())
    val reservasVisibles = _reservasVisibles.asStateFlow()

    private val _actualizacion = MutableStateFlow(0)
    val actualizacion = _actualizacion.asStateFlow()

    /** Carga las reservas propias del cliente vinculado. */
    fun cargar() {
        viewModelScope.launch {
            _cargando.value = true
            _error.value = null
            _noVinculado.value = false
            try {
                val identidad = identidad()
                if (identidad == null) {
                    _noVinculado.value = true
                    return@launch
                }
                cargarReservasVisibles(identidad.first, identidad.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = mensajeDe(e)
            } finally {
                _cargando.value = false
            }
        }
    }

    /** Solicita una reserva a través de la Cloud Function callable `reservar`. */
    fun reservar(sesionId: Int) {
        operar {
            reservaRepository.reservarConFuncion(sesionId)
        }
    }

    /** Cancela una reserva a través de la Cloud Function callable `cancelarReserva`. */
    fun cancelar(sesionId: Int) {
        operar {
            reservaRepository.cancelarReservaConFuncion(sesionId)
        }
    }

    /** Indica si el cliente tiene reservada una sesión en el estado actual. */
    fun estaReservada(sesionId: Int): Boolean =
        _reservas.value.any { it.sesionId == sesionId }

    /**
     * limpiarError
     * ------------
     * Descarta un error de una operación de reserva ANTERIOR. Se invoca cuando
     * la pantalla se recarga (p. ej. al volver a ella tras un cambio de la
     * sesión por parte del centro), para que un error obsoleto (p. ej.
     * "La sesión ya está completa") no permanezca cuando el estado actual de la
     * sesión ya ha cambiado. Los errores de operaciones nuevas siguen
     * mostrándose con normalidad.
     */
    fun limpiarError() {
        _error.value = null
    }

    private fun operar(
        accion: suspend () -> ResultadoAutenticacion
    ) {
        viewModelScope.launch {
            _error.value = null
            val identidad = identidad()
            if (identidad == null) {
                _noVinculado.value = true
                return@launch
            }

            _operando.value = true
            try {
                val resultado = accion()
                if (!resultado.exito) {
                    _error.value = resultado.mensaje
                    // Aunque falle (p. ej. la callable rechaza por plazas agotadas
                    // o por combinación), se refresca el estado real de la sesión
                    // para que la pantalla deje de mostrar plazas obsoletas.
                    refrescarTrasOperacion(identidad.first, identidad.second)
                } else {
                    refrescarTrasOperacion(identidad.first, identidad.second)
                    _actualizacion.value++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = mensajeDe(e)
            } finally {
                _operando.value = false
            }
        }
    }

    /**
     * refrescarTrasOperacion
     * ----------------------
     * Recarga las reservas visibles y notifica a la pantalla (actualizacion)
     * para que recargue también las sesiones. Best-effort: si la lectura falla
     * no aborta la operación.
     */
    private suspend fun refrescarTrasOperacion(clienteId: Int, negocioId: String) {
        try {
            cargarReservasVisibles(clienteId, negocioId)
        } catch (_: Exception) {
            // Se ignora: el estado se recalculará en la próxima carga.
        }
        _actualizacion.value++
    }

    private suspend fun identidad(): Pair<Int, String>? {
        val clienteId = preferencesRepository.idCliente.first() ?: return null
        val negocioId = preferencesRepository.negocioId.first()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return clienteId to negocioId
    }

    /**
     * Carga las reservas y sus sesiones agrupando las lecturas por servicio.
     * Si una sesión fue eliminada o dejó de ser visible, no se muestra como
     * reserva activa.
     */
    private suspend fun cargarReservasVisibles(clienteId: Int, negocioId: String) {
        val reservas = reservaRepository.obtenerReservasCliente(clienteId, negocioId)
        _reservas.value = reservas

        val ficha = clienteRepository.leerFicha(clienteId) ?: run {
            _reservasVisibles.value = emptyList()
            return
        }
        val sesiones = mutableMapOf<Int, ReservaVisibleSesion>()
        ficha.serviciosContratados.distinct().forEach { idServicio ->
            val servicio = sesionRepository.obtenerServicioActivo(idServicio, negocioId)
                ?: return@forEach
            sesionRepository.obtenerSesionesPorServicio(idServicio, negocioId).forEach { sesion ->
                sesiones[sesion.idSesion] = ReservaVisibleSesion(sesion, servicio.nombre)
            }
        }

        _reservasVisibles.value = reservas.mapNotNull { reserva ->
            val sesion = sesiones[reserva.sesionId] ?: return@mapNotNull null
            if (!sesion.esProxima()) return@mapNotNull null
            ReservaVisible(
                reserva = reserva,
                sesion = sesion.sesion,
                nombreServicio = sesion.nombreServicio
            )
        }.sortedWith(compareBy({ it.sesion.fecha }, { it.sesion.hora }))
    }

    private fun mensajeDe(e: Exception): String =
        e.message ?: texto(R.string.reserva_error_cargar)
}

/** Reserva enriquecida con los datos de la sesión para la pantalla de reservas. */
data class ReservaVisible(
    val reserva: Reserva,
    val sesion: Sesion,
    val nombreServicio: String
)

private data class ReservaVisibleSesion(
    val sesion: Sesion,
    val nombreServicio: String
) {
    fun esProxima(): Boolean {
        return try {
            val fecha = java.time.Instant.ofEpochMilli(sesion.fecha)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val hora = java.time.LocalTime.parse(sesion.hora)
            !java.time.LocalDateTime.of(fecha, hora)
                .isBefore(java.time.LocalDateTime.now())
        } catch (_: Exception) {
            false
        }
    }
}
