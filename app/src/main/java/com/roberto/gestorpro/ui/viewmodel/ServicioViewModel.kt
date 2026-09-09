package com.roberto.gestorpro.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.data.firebase.ResultadoAutenticacion
import com.roberto.gestorpro.data.firebase.ReservaRemotoRepository
import com.roberto.gestorpro.data.firebase.ServicioRemotoRepository
import com.roberto.gestorpro.data.repository.ReservaRepository
import com.roberto.gestorpro.data.repository.ServicioRepository
import com.roberto.gestorpro.data.repository.SesionRepository
import com.roberto.gestorpro.data.firebase.SesionRemotoRepository
import com.roberto.gestorpro.data.repository.DesactivacionServicioSincronizador
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ServicioViewModel
 * -----------------
 * ViewModel de la gestión de servicios del ADMIN.
 * Separa activos e inactivos, y coordina las operaciones que afectan a las
 * sesiones del servicio (dar de baja, eliminar) usando SesionRepository y
 * ReservaRepository para no dejar sesiones ni reservas huérfanas.
 */
@HiltViewModel
class ServicioViewModel @Inject constructor(
    private val servicioRepository: ServicioRepository,
    private val reservaRepository: ReservaRepository,
    private val servicioRemotoRepository: ServicioRemotoRepository,
    private val reservaRemotoRepository: ReservaRemotoRepository,
    private val sesionRepository: SesionRepository,
    private val sesionRemotoRepository: SesionRemotoRepository,
    private val desactivacionServicioSincronizador: DesactivacionServicioSincronizador
) : ViewModel() {

    private val _activos = MutableStateFlow<List<ServicioEntity>>(emptyList())
    val activos: StateFlow<List<ServicioEntity>> = _activos.asStateFlow()

    private val _inactivos = MutableStateFlow<List<ServicioEntity>>(emptyList())
    val inactivos: StateFlow<List<ServicioEntity>> = _inactivos.asStateFlow()

    private val _servicioSeleccionado = MutableStateFlow<ServicioEntity?>(null)
    val servicioSeleccionado: StateFlow<ServicioEntity?> = _servicioSeleccionado.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operando = MutableStateFlow(false)
    val operando: StateFlow<Boolean> = _operando.asStateFlow()

    /**
     * _errorSincronizacion / errorSincronizacion
     * ------------------------------------------
     * Mensaje del último fallo de réplica Room -> Firestore de un servicio.
     * Sirve para informar al ADMIN sin revertir el cambio local y para saber
     * cuándo ofrecer el reintento manual de sincronización.
     */
    private val _errorSincronizacion = MutableStateFlow<String?>(null)
    val errorSincronizacion: StateFlow<String?> = _errorSincronizacion.asStateFlow()

    /**
     * _servicioSinSincronizar / servicioSinSincronizar
     * ------------------------------------------------
     * Operación remota pendiente de un servicio tras un fallo de sincronización.
     * Sirve para poder reintentar la réplica exacta (alta, edición, baja,
     * reactivación o eliminación).
     */
    private val _servicioSinSincronizar = MutableStateFlow<PendienteServicio?>(null)
    val servicioSinSincronizar: StateFlow<PendienteServicio?> = _servicioSinSincronizar.asStateFlow()

    /**
     * _plazasHoyPorServicio / plazasHoyPorServicio
     * --------------------------------------------
     * Resumen agregado de las sesiones de HOY de cada servicio
     * (idServicio -> PlazasHoyServicio). Sirve para mostrar en el card del
     * servicio cuántas plazas hay reservadas frente a la capacidad total de
     * hoy sin tocar la lógica de reservas existente.
     */
    private val _plazasHoyPorServicio = MutableStateFlow<Map<Int, PlazasHoyServicio>>(emptyMap())
    val plazasHoyPorServicio: StateFlow<Map<Int, PlazasHoyServicio>> =
        _plazasHoyPorServicio.asStateFlow()

    /**
     * cargarServicios
     * ---------------
     * Observa en tiempo real los servicios activos e inactivos.
     */
    fun cargarServicios() {
        // Al entrar a gestionar Actividades se reintentan las desactivaciones
        // pendientes que no llegaron a converger en Firestore (best-effort).
        viewModelScope.launch {
            desactivacionServicioSincronizador.reintentarPendientes()
        }
        viewModelScope.launch {
            servicioRepository.obtenerServiciosActivos().collect { _activos.value = it }
        }
        viewModelScope.launch {
            servicioRepository.obtenerServiciosInactivos().collect { _inactivos.value = it }
        }
    }

    /**
     * cargarPlazasDeHoy
     * -----------------
     * Calcula las plazas reservadas y la capacidad agregada de las sesiones de
     * HOY de cada servicio. Para reflejar las reservas creadas por appCliente
     * (que no actualizan la Room local del ADMIN), las plazas disponibles de
     * cada sesión se leen de Firestore (sesiones/{id}) y, si falla la lectura
     * o no hay conexión, se usa el valor local de Room como respaldo.
     */
    fun cargarPlazasDeHoy() {
        viewModelScope.launch {
            val hoy = LocalDate.now()
            val inicioHoy = hoy.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val finHoy = hoy.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            val acumulador = mutableMapOf<Int, PlazasHoyServicio>()
            try {
                val sesionesHoy = sesionRepository.obtenerSesionesEntre(inicioHoy, finHoy)
                sesionesHoy.groupBy { it.idServicio }
                    .forEach { (idServicio, sesiones) ->
                        var reservadas = 0
                        var capacidad = 0
                        sesiones.forEach { sesion ->
                            capacidad += sesion.capacidad
                            val plazasRemotas = sesionRemotoRepository
                                .obtenerPlazasDisponiblesRemoto(sesion.idSesion)
                            val disponibles = (plazasRemotas ?: sesion.plazasDisponibles)
                                .coerceIn(0, sesion.capacidad)
                            reservadas += sesion.capacidad - disponibles
                        }
                        acumulador[idServicio] = PlazasHoyServicio(reservadas, capacidad)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "cargarPlazasDeHoy: no se pudieron calcular las plazas de hoy", e)
            }
            _plazasHoyPorServicio.value = acumulador
        }
    }

    /**
     * cargarServicio
     * --------------
     * Carga un servicio por su id (para editar o para la pantalla de detalle).
     */
    fun cargarServicio(idServicio: Int) {
        viewModelScope.launch {
            _servicioSeleccionado.value = servicioRepository.obtenerServicioPorId(idServicio)
        }
    }

    /**
     * crearServicio
     * -------------
     * Crea un servicio nuevo con activo = true y el negocioId del ADMIN.
     * Valida que el nombre no esté vacío y que el precio no sea negativo, y
     * replica a Firestore (write-through).
     */
    fun crearServicio(
        nombre: String,
        descripcion: String,
        precio: Double,
        permiteCombinarDia: Boolean
    ) {
        val nombreLimpio = nombre.trim()
        if (nombreLimpio.isBlank()) {
            _error.value = "El nombre del servicio es obligatorio"
            return
        }
        if (precio < 0) {
            _error.value = "El precio no puede ser negativo"
            return
        }
        _error.value = null
        viewModelScope.launch {
            val id = servicioRepository.insertarServicio(
                ServicioEntity(
                    negocioId = NEGOCIO_ID_PENDIENTE,
                    nombre = nombreLimpio,
                    descripcion = descripcion.trim(),
                    activo = true,
                    precio = precio,
                    permiteCombinarDia = permiteCombinarDia
                )
            )
            val entidad = ServicioEntity(
                idServicio = id.toInt(),
                negocioId = NEGOCIO_ID_PENDIENTE,
                nombre = nombreLimpio,
                descripcion = descripcion.trim(),
                activo = true,
                precio = precio,
                permiteCombinarDia = permiteCombinarDia
            )
            replicar(entidad, OperacionServicio.CREAR)
        }
    }

    /**
     * actualizarServicio
     * ------------------
     * Guarda los cambios de nombre, descripción y estado activo/inactivo,
     * y replica a Firestore (write-through).
     */
    fun actualizarServicio(servicio: ServicioEntity) {
        if (servicio.nombre.isBlank()) {
            _error.value = "El nombre del servicio es obligatorio"
            return
        }
        _error.value = null
        viewModelScope.launch {
            servicioRepository.actualizarServicio(servicio)
            replicar(servicio, OperacionServicio.ACTUALIZAR)
        }
    }

    /**
     * darDeBaja
     * ---------
     * Pasa el servicio a inactivo y elimina sus sesiones futuras y las reservas
     * asociadas a esas sesiones. Las sesiones pasadas se conservan.
     * Replica la desactivación a Firestore.
     */
    fun darDeBaja(servicio: ServicioEntity) {
        viewModelScope.launch {
            _operando.value = true
            _error.value = null
            try {
                // Frontera temporal ORIGINAL de la baja (inicio del día). Se
                // reutiliza también en el reintento durable para no redefinir
                // retroactivamente qué sesiones eran "futuras".
                val desde = inicioDeHoy()
                reservaRepository.eliminarReservasYSesionesFuturasDelServicio(
                    servicio.idServicio,
                    desde
                )
                servicioRepository.actualizarServicio(servicio.copy(activo = false))

                // Réplica remota: elimina reservas + sesiones futuras y deja el
                // servicio inactivo. Ante un fallo queda un pendiente DURABLE
                // (con su frontera original) para reintentarlo más adelante.
                val resultadoRemoto = desactivacionServicioSincronizador
                    .convergerDesactivacion(servicio.idServicio, desde)
                if (resultadoRemoto.exito) {
                    _errorSincronizacion.value = null
                    _servicioSinSincronizar.value = null
                } else {
                    _errorSincronizacion.value =
                        "Cambio guardado en el dispositivo, pero no sincronizado con la nube: ${resultadoRemoto.mensaje}"
                    _servicioSinSincronizar.value = PendienteServicio(
                        OperacionServicio.DESACTIVAR,
                        servicio.copy(activo = false)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "darDeBaja: error al dar de baja el servicio ${servicio.idServicio}", e)
                _error.value =
                    "No se pudo dar de baja el servicio: ${e.message ?: "error inesperado"}"
            } finally {
                _operando.value = false
            }
        }
    }

    /**
     * reactivar
     * ---------
     * Pasa el servicio a activo. NO recupera las sesiones eliminadas.
     * Replica la reactivación a Firestore. Si existe un pendiente DURABLE de
     * desactivación para este servicio, se converge primero (con su frontera
     * ORIGINAL) para no dejar en la nube `activo=true` junto a sesiones futuras
     * que la baja debía haber eliminado.
     */
    fun reactivar(servicio: ServicioEntity) {
        viewModelScope.launch {
            _operando.value = true
            _error.value = null
            try {
                servicioRepository.actualizarServicio(servicio.copy(activo = true))

                val pendiente = desactivacionServicioSincronizador
                    .obtenerPendiente(servicio.idServicio)
                if (pendiente != null) {
                    val convergido = desactivacionServicioSincronizador
                        .convergerDesactivacion(pendiente.idServicio, pendiente.desde)
                    if (!convergido.exito) {
                        _errorSincronizacion.value =
                            "Cambio guardado en el dispositivo, pero no sincronizado con la nube: ${convergido.mensaje}"
                        _servicioSinSincronizar.value = PendienteServicio(
                            OperacionServicio.REACTIVAR,
                            servicio.copy(activo = true)
                        )
                        return@launch
                    }
                }
                replicar(servicio.copy(activo = true), OperacionServicio.REACTIVAR)
            } catch (e: Exception) {
                Log.e(TAG, "reactivar: error al reactivar el servicio ${servicio.idServicio}", e)
                _error.value =
                    "No se pudo reactivar el servicio: ${e.message ?: "error inesperado"}"
            } finally {
                _operando.value = false
            }
        }
    }

    /**
     * eliminar
     * --------
     * Elimina el servicio, todas sus sesiones y todas las reservas asociadas
     * (Room). Los movimientos no se tocan. Replica la eliminación a Firestore.
     */
    fun eliminar(servicio: ServicioEntity) {
        viewModelScope.launch {
            _operando.value = true
            _error.value = null
            try {
                reservaRepository.eliminarReservasYSesionesDelServicio(servicio.idServicio)
                servicioRepository.eliminarServicio(servicio)
                // La eliminación borra TODAS las sesiones/reservas y el servicio:
                // si quedaba una desactivación durable pendiente, ya no aplica.
                desactivacionServicioSincronizador.eliminarPendiente(servicio.idServicio)
                replicar(servicio, OperacionServicio.ELIMINAR)
            } catch (e: Exception) {
                Log.e(TAG, "eliminar: error al eliminar el servicio ${servicio.idServicio}", e)
                _error.value =
                    "No se pudo eliminar el servicio: ${e.message ?: "error inesperado"}"
            } finally {
                _operando.value = false
            }
        }
    }

    /**
     * reintentarSincronizacion
     * ------------------------
     * Repite la última operación remota pendiente de un servicio. Antes de
     * reintentar se converge cualquier desactivación DURABLE pendiente de ese
     * servicio (con su frontera original), de modo que nunca se active en la
     * nube un servicio con sesiones futuras que la baja debía haber eliminado.
     */
    fun reintentarSincronizacion() {
        val pendiente = _servicioSinSincronizar.value ?: return
        viewModelScope.launch {
            val durable = desactivacionServicioSincronizador
                .obtenerPendiente(pendiente.servicio.idServicio)
            if (durable != null) {
                val convergido = desactivacionServicioSincronizador
                    .convergerDesactivacion(durable.idServicio, durable.desde)
                if (!convergido.exito) {
                    _errorSincronizacion.value =
                        "Cambio guardado en el dispositivo, pero no sincronizado con la nube: ${convergido.mensaje}"
                    return@launch
                }
                // Si la operación pendiente era precisamente la desactivación,
                // ya ha convergido con el reintento durable.
                if (pendiente.operacion == OperacionServicio.DESACTIVAR) {
                    _errorSincronizacion.value = null
                    _servicioSinSincronizar.value = null
                    return@launch
                }
            }
            replicar(pendiente.servicio, pendiente.operacion)
        }
    }

    /**
     * replicar
     * --------
     * Réplica write-through de una operación de servicio hacia Firestore.
     * Si falla, no revierte el cambio local y deja la operación preparada
     * para el reintento manual.
     */
    private suspend fun replicar(servicio: ServicioEntity, operacion: OperacionServicio) {
        _errorSincronizacion.value = null
        _servicioSinSincronizar.value = null

        val resultado = when (operacion) {
            OperacionServicio.CREAR -> servicioRemotoRepository.crearServicioRemoto(servicio)
            OperacionServicio.ACTUALIZAR -> servicioRemotoRepository.actualizarServicioRemoto(servicio)
            OperacionServicio.REACTIVAR -> servicioRemotoRepository.activarServicioRemoto(servicio.idServicio)
            OperacionServicio.DESACTIVAR -> replicarDesactivacionRemota(servicio)
            OperacionServicio.ELIMINAR -> replicarEliminacionRemota(servicio)
        }

        if (!resultado.exito) {
            _errorSincronizacion.value =
                "Cambio guardado en el dispositivo, pero no sincronizado con la nube: ${resultado.mensaje}"
            _servicioSinSincronizar.value = PendienteServicio(operacion, servicio)
        }
    }

    /**
     * replicarDesactivacionRemota
     * ---------------------------
     * Al desactivar un servicio: elimina de forma atómica las sesiones futuras
     * con todas sus reservas y finalmente pone activo = false.
     */
    private suspend fun replicarDesactivacionRemota(
        servicio: ServicioEntity
    ): ResultadoAutenticacion {
        val desde = inicioDeHoy()
        val cascada = reservaRemotoRepository
            .eliminarSesionesFuturasConReservasRemoto(servicio.idServicio, desde)
        if (!cascada.exito) return cascada
        return servicioRemotoRepository.desactivarServicioRemoto(servicio.idServicio)
    }

    /**
     * replicarEliminacionRemota
     * -------------------------
     * Al eliminar un servicio: elimina de forma atómica todas sus sesiones con
     * todas sus reservas y finalmente el documento del servicio.
     */
    private suspend fun replicarEliminacionRemota(
        servicio: ServicioEntity
    ): ResultadoAutenticacion {
        val cascada = reservaRemotoRepository
            .eliminarTodasLasSesionesConReservasRemoto(servicio.idServicio)
        if (!cascada.exito) return cascada
        return servicioRemotoRepository.eliminarServicioRemoto(servicio.idServicio)
    }

    companion object {
        private const val TAG = "ServicioViewModel"

        /**
         * NEGOCIO_ID_PENDIENTE
         * --------------------
         * Mientras el modelo vive solo en Room, el negocioId se guarda vacío
         * (igual que hacía ClaseEntity). En la sincronización remota se usa el
         * negocioId real del ADMIN (su UID) sin tocar el modelo Room.
         */
        private const val NEGOCIO_ID_PENDIENTE = ""

        /**
         * inicioDeHoy
         * -----------
         * Inicio (00:00) del día actual en milisegundos. Define el límite entre
         * sesiones pasadas y futuras: "futura" = fecha >= inicioDeHoy.
         */
        fun inicioDeHoy(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * OperacionServicio
     * -----------------
     * Operación remota pendiente de un servicio para el reintento manual.
     */
    enum class OperacionServicio {
        CREAR, ACTUALIZAR, DESACTIVAR, REACTIVAR, ELIMINAR
    }

    /**
     * PendienteServicio
     * -----------------
     * Datos de la operación remota pendiente de sincronizar.
     */
    data class PendienteServicio(
        val operacion: OperacionServicio,
        val servicio: ServicioEntity
    )
}

/**
 * PlazasHoyServicio
 * -----------------
 * Resumen agregado de las sesiones de HOY de un servicio (varias sesiones del
 * mismo día se suman). reservadas = plazas ocupadas y capacidad = plazas
 * totales de las sesiones del día. Solo existe si el servicio tiene alguna
 * sesión hoy.
 */
data class PlazasHoyServicio(
    val reservadas: Int,
    val capacidad: Int
)
