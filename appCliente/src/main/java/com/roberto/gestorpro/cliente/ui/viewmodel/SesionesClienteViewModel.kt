package com.roberto.gestorpro.cliente.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.ClienteRepository
import com.roberto.gestorpro.cliente.data.firebase.ReservaRepository
import com.roberto.gestorpro.cliente.data.firebase.SesionRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.model.EstadoCliente
import com.roberto.gestorpro.cliente.model.EstadoReserva
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SesionesClienteViewModel
 * ------------------------
 * Orquesta la consulta de las sesiones del DÍA ACTUAL del CLIENTE.
 *
 * Flujo (compatible con las Security Rules):
 *   1. idCliente desde DataStore (null -> cliente no vinculado);
 *   2. ficha clientes/{idCliente} (fuente de verdad de serviciosContratados);
 *   3. por cada servicio contratado: getDoc servicios/{id} para confirmar que
 *      está ACTIVO (si no, se omite);
     *   4. reservas propias con una única query;
     *   5. sesiones del servicio con filtros de servicio y negocio;
     *   6. filtrar en memoria sesión.fecha == inicio del día actual;
     *   7. ordenar por hora ascendente y cruzar reservadaPorMi.
 */
@HiltViewModel
class SesionesClienteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val clienteRepository: ClienteRepository,
    private val sesionRepository: SesionRepository,
    private val reservaRepository: ReservaRepository
) : ViewModel() {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    private val _cargando = MutableStateFlow(true)
    val cargando = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _noVinculado = MutableStateFlow(false)
    val noVinculado = _noVinculado.asStateFlow()

    private val _sinServicios = MutableStateFlow(false)
    val sinServicios = _sinServicios.asStateFlow()

    private val _sinSesionesHoy = MutableStateFlow(false)
    val sinSesionesHoy = _sinSesionesHoy.asStateFlow()

    private val _dadoDeBaja = MutableStateFlow(false)
    val dadoDeBaja = _dadoDeBaja.asStateFlow()

    private val _estadoNoActivo = MutableStateFlow(false)
    val estadoNoActivo = _estadoNoActivo.asStateFlow()

    private val _sesiones = MutableStateFlow<List<SesionVisible>>(emptyList())
    val sesiones = _sesiones.asStateFlow()

    /**
     * cargar
     * ------
     * (Re)lanza la consulta completa de las clases de hoy.
     */
    fun cargar() {
        viewModelScope.launch {
            _cargando.value = true
            _error.value = null
            _noVinculado.value = false
            _sinServicios.value = false
            _sinSesionesHoy.value = false
            _dadoDeBaja.value = false
            _estadoNoActivo.value = false
            _sesiones.value = emptyList()
            try {
                val idCliente = preferencesRepository.idCliente.first()
                if (idCliente == null) {
                    _noVinculado.value = true
                    return@launch
                }

                // La ficha es la fuente de verdad de los servicios contratados.
                val ficha = clienteRepository.leerFicha(idCliente)
                if (ficha == null) {
                    _error.value = texto(R.string.clases_error_cargar)
                    return@launch
                }

                // SOLO un cliente ACTIVO accede a las clases (y puede reservar).
                // Un cliente de BAJA, REGISTRADO u otro estado administrativo no
                // activo no puede ver ni reservar sesiones. La morosidad es
                // independiente: un ACTIVO con deuda (moroso) sigue siendo ACTIVO.
                if (ficha.estado != EstadoCliente.ACTIVO) {
                    if (ficha.estado == EstadoCliente.BAJA) {
                        _dadoDeBaja.value = true
                    } else {
                        _estadoNoActivo.value = true
                    }
                    return@launch
                }

                val contratados = ficha.serviciosContratados.distinct()
                if (contratados.isEmpty()) {
                    _sinServicios.value = true
                    return@launch
                }

                val negocioId = preferencesRepository.negocioId.first()
                    ?: ficha.negocioId.takeIf { it.isNotBlank() }
                    ?: run {
                        _error.value = texto(R.string.clases_error_identificar_centro)
                        return@launch
                    }
                val sesionesReservadas = reservaRepository
                    .obtenerReservasCliente(idCliente, negocioId)
                    .map { it.sesionId }
                    .toSet()

                val inicioHoy = inicioDeHoy()
                val visibles = mutableListOf<SesionVisible>()
                for (idServicio in contratados) {
                    // Solo servicios ACTIVOS (un servicio inactivo/eliminado no es legible).
                    val servicio = sesionRepository
                        .obtenerServicioActivo(idServicio, negocioId)
                    if (servicio == null) {
                        continue
                    }
                    val sesionesBrutas = sesionRepository
                        .obtenerSesionesPorServicio(idServicio, negocioId)
                    val delDia = sesionesBrutas
                        .filter { esDeHoy(it.fecha) }
                        .sortedBy { it.hora }
                    delDia.forEach { sesion ->
                        visibles.add(
                            SesionVisible(
                                idSesion = sesion.idSesion,
                                idServicio = sesion.idServicio,
                                nombreServicio = servicio.nombre,
                                fecha = sesion.fecha,
                                hora = sesion.hora,
                                duracionMinutos = sesion.duracionMinutos,
                                capacidad = sesion.capacidad,
                                plazasDisponibles = sesion.plazasDisponibles,
                                horaDesdeReserva = sesion.horaDesdeReserva,
                                reservadaPorMi = sesion.idSesion in sesionesReservadas
                            )
                        )
                    }
                }

                visibles.sortBy { it.hora }
                if (visibles.isEmpty()) {
                    _sinSesionesHoy.value = true
                } else {
                    _sesiones.value = visibles
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SesionesClienteViewModel", "cargar fallo: ${e.message}", e)
                _error.value = texto(R.string.clases_error_cargar)
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * reintentar
     * ----------
     * Reintenta la carga de las clases de hoy (p. ej. tras un error de red).
     */
    fun reintentar() {
        cargar()
    }

    companion object {
        /**
         * inicioDeHoy
         * -----------
         * Epoch millis de la medianoche del día actual en la zona local del
         * dispositivo. Usa el MISMO criterio que el Admin
         * (ServicioViewModel.inicioDeHoy) para comparar con sesiones.fecha.
         */
        fun inicioDeHoy(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        /**
         * esDeHoy
         * -------
         * Comprobación robusta de si una fecha (epoch millis) corresponde al día
         * actual en la zona local. Tolera tanto fechas almacenadas como medianoche
         * local (contrato oficial) como posibles desfases de UTC vs local (p. ej.
         * sesión editada con DatePicker que guardó UTC). Compara por LocalDate en
         * lugar de igualdad estricta de millis.
         */
        fun esDeHoy(fecha: Long): Boolean {
            val fechaLocal = Instant.ofEpochMilli(fecha)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            return fechaLocal == LocalDate.now()
        }
    }
}

/**
 * SesionVisible
 * -------------
 * Sesión del día actual lista para mostrarse en la pantalla del CLIENTE.
 * reservadaPorMi se obtiene cruzando las reservas propias con las sesiones.
 * reservable indica si la hora de apertura (horaDesdeReserva) ya ha llegado:
 *   - horaDesdeReserva == null -> reservable desde el inicio del día;
 *   - con hora "HH:mm" -> solo cuando la hora actual local >= apertura.
 */
data class SesionVisible(
    val idSesion: Int,
    val idServicio: Int,
    val nombreServicio: String,
    val fecha: Long,
    val hora: String,
    val duracionMinutos: Int,
    val capacidad: Int,
    val plazasDisponibles: Int,
    val horaDesdeReserva: String? = null,
    val reservadaPorMi: Boolean = false
) {
    val estadoReserva: EstadoReserva
        get() = EstadoReserva.de(reservadaPorMi, plazasDisponibles)

    val reservable: Boolean
        get() = SesionVisible.aperturaAlcanzada(fecha, horaDesdeReserva)

    companion object {
        /**
         * aperturaAlcanzada
         * -----------------
         * Indica si la hora de apertura de reservas ya ha llegado. Se compara en
         * instante absoluto: fecha (epoch millis de la medianoche local del día)
         * + offset de horaDesdeReserva frente al instante actual. Si
         * horaDesdeReserva es null, la apertura es el inicio del día.
         */
        fun aperturaAlcanzada(fecha: Long, horaDesdeReserva: String?): Boolean {
            val apertura = horaDesdeReserva?.let { hora ->
                val partes = hora.split(":")
                val h = partes.getOrNull(0)?.toIntOrNull() ?: return true
                val m = partes.getOrNull(1)?.toIntOrNull() ?: return true
                fecha + (h * 3_600_000L + m * 60_000L)
            } ?: return true
            return System.currentTimeMillis() >= apertura
        }
    }
}
