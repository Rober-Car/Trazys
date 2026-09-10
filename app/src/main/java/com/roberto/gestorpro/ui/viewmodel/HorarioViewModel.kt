package com.roberto.gestorpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.data.firebase.NegocioRepository
import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.data.repository.ServicioRepository
import com.roberto.gestorpro.model.ActividadHorario
import com.roberto.gestorpro.model.ExcepcionHorario
import com.roberto.gestorpro.model.HorarioNegocio
import com.roberto.gestorpro.model.TramoHorario
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * HorarioViewModel
 * ----------------
 * ViewModel de la configuración del HORARIO del negocio (ADMIN):
 *  - horario del centro (semanal, un tramo por día);
 *  - horario de actividades (semanal, independiente de las sesiones).
 *
 * Al guardar el horario del CENTRO, si la configuración de notificaciones tiene
 * activo el aviso "Cambio de horario", se crea una notificación automática de
 * tipo CAMBIO_HORARIO para todos los clientes vinculados. El horario de
 * actividades NUNCA genera notificación.
 */
@HiltViewModel
class HorarioViewModel @Inject constructor(
    private val negocioRepository: NegocioRepository,
    private val notificacionRemotoRepository: NotificacionRemotoRepository,
    servicioRepository: ServicioRepository
) : ViewModel() {

    /** Actividades actuales del negocio (para elegir en el horario semanal). */
    val servicios: StateFlow<List<ServicioEntity>> =
        servicioRepository.obtenerServiciosActivos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _horario = MutableStateFlow(HorarioNegocio())
    val horario: StateFlow<HorarioNegocio> = _horario.asStateFlow()

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    /** true cuando el horario ya se ha cargado al menos una vez. */
    private val _cargado = MutableStateFlow(false)
    val cargado = _cargado.asStateFlow()

    private val _guardando = MutableStateFlow(false)
    val guardando = _guardando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _mensajeExito = MutableStateFlow<String?>(null)
    val mensajeExito = _mensajeExito.asStateFlow()

    /** Carga el horario configurado (vacío si no existe: negocios existentes). */
    fun cargar() {
        viewModelScope.launch {
            _cargando.value = true
            _error.value = null
            try {
                _horario.value = negocioRepository.leerHorario()
                _cargado.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo cargar el horario"
            } finally {
                _cargando.value = false
            }
        }
    }

    /** Guarda el horario del centro (semanal + excepciones) y, si procede, avisa. */
    fun guardarCentro(
        centro: Map<DayOfWeek, TramoHorario>,
        excepciones: List<ExcepcionHorario>
    ) {
        guardar(
            nuevo = _horario.value.copy(centro = centro, excepciones = excepciones),
            avisarCambioHorario = true
        )
    }

    /** Guarda el horario de actividades (sin notificación). */
    fun guardarActividades(actividades: Map<DayOfWeek, List<ActividadHorario>>) {
        guardar(
            nuevo = _horario.value.copy(actividades = actividades),
            avisarCambioHorario = false
        )
    }

    fun consumirMensajeExito() {
        _mensajeExito.value = null
    }

    private fun guardar(nuevo: HorarioNegocio, avisarCambioHorario: Boolean) {
        viewModelScope.launch {
            _guardando.value = true
            _error.value = null
            try {
                val resultado = negocioRepository.guardarHorario(nuevo)
                if (!resultado.exito) {
                    _error.value = resultado.mensaje
                    return@launch
                }
                _horario.value = nuevo
                if (avisarCambioHorario) {
                    enviarAvisoSiActivo()
                }
                _mensajeExito.value = resultado.mensaje
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo guardar el horario"
            } finally {
                _guardando.value = false
            }
        }
    }

    /**
     * enviarAvisoSiActivo
     * -------------------
     * Best-effort: crea la notificación CAMBIO_HORARIO para todos los clientes
     * vinculados SOLO si el switch de configuración está activo. Un fallo aquí
     * nunca revierte el horario ya guardado.
     */
    private suspend fun enviarAvisoSiActivo() {
        try {
            val negocioId = notificacionRemotoRepository.negocioIdActual() ?: return
            val config = notificacionRemotoRepository.obtenerConfiguracion(negocioId)
                ?: return
            if (!config.cambioHorarioActiva) return

            val resolucion = notificacionRemotoRepository.resolverDestinatarios(
                negocioId = negocioId,
                modoDestino = "TODOS",
                idsSeleccionados = emptyList()
            )
            if (resolucion.destinatarios.isEmpty()) return

            notificacionRemotoRepository.crearNotificacion(
                negocioId = negocioId,
                titulo = "Cambio de horario",
                mensaje = "El horario del centro ha cambiado. " +
                    "Consulta el nuevo horario en la app.",
                modoDestino = "TODOS",
                clienteId = null,
                destinatarios = resolucion.destinatarios,
                idsObjetivo = resolucion.destinatarios.map { it.idCliente },
                programada = false,
                fechaProgramada = null,
                tipo = NotificacionRemotoRepository.TIPO_CAMBIO_HORARIO,
                origen = "MANUAL"
            )
        } catch (_: Exception) {
            // El aviso es best-effort: nunca rompe el guardado del horario.
        }
    }
}
