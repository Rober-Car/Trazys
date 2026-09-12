package com.roberto.gestorpro.data.repository

import android.util.Log
import com.roberto.gestorpro.data.dao.ClienteDao
import com.roberto.gestorpro.data.dao.EliminacionPendienteDao
import com.roberto.gestorpro.data.dao.MovimientoDao
import com.roberto.gestorpro.data.entity.EliminacionPendienteEntity
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.data.firebase.ClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.MovimientoRemotoRepository
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.util.IdMovimiento
import com.roberto.gestorpro.util.MovimientoMorosidad
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MovimientoRepository
 * --------------------
 * Capa de repositorio de movimientos (F2). Room sigue siendo la fuente de
 * verdad económica del ADMIN; Firestore es la réplica remota.
 *
 * Flujo común tras cada CRUD:
 *  1. persistir en Room (el movimiento nuevo usa un id GLOBAL preasignado
 *     por IdMovimiento para soportar varios dispositivos Admin);
 *  2. recalcular morosidad con MovimientoMorosidad y persistir el resultado
 *     (moroso/fechaEntradaMorosidad) en Room;
 *  3. replicar el movimiento a `movimientos/{idMovimiento}` (crear/editar)
 *     o registrarlo como eliminación pendiente y borrarlo (eliminar);
 *  4. publicar el resumen económico completo en `clientes/{id}`.
 *
 * Si Firestore falla: Room conserva el cambio, se informa al ADMIN y queda
 * pendiente de reintento (sin cola general). Las eliminaciones remotas
 * fallidas se persisten en la tabla `eliminacion_pendiente` para no perderse
 * al reiniciar la aplicación.
 */
@Singleton
class MovimientoRepository @Inject constructor(
    private val movimientoDao: MovimientoDao,
    private val clienteDao: ClienteDao,
    private val eliminacionPendienteDao: EliminacionPendienteDao,
    private val clienteRemotoRepository: ClienteRemotoRepository,
    private val movimientoRemotoRepository: MovimientoRemotoRepository
) {

    private val sincronizacionMutex = Mutex()
    private val _periodosPendientes = MutableStateFlow<Set<Int>>(emptySet())
    val periodosPendientes: StateFlow<Set<Int>> = _periodosPendientes.asStateFlow()

    private val _errorSincronizacion = MutableStateFlow<String?>(null)
    val errorSincronizacion: StateFlow<String?> = _errorSincronizacion.asStateFlow()

    /**
     * pendientesEconomicosMemoria
     * ---------------------------
     * Clientes con alguna operación económica (crear/editar/borrar/resumen)
     * cuyo resultado remoto no se confirmó en esta sesión. Solo memoria; no
     * sobrevive al reinicio. Se usa en el guard de cambio de propietario para
     * detectar movimientos locales cuyo espejo remoto no está confirmado.
     */
    val pendientesEconomicosMemoria: Set<Int>
        get() = _periodosPendientes.value

    /**
     * resetEstadoEnMemoria
     * --------------------
     * Vacía los marcadores de sincronización económica en memoria. Lo invoca
     * el guard de cambio de propietario tras un WIPE (o al adoptar una caché
     * de propietario indeterminado) para que ninguna marca de la cuenta
     * anterior se reintente bajo la nueva cuenta.
     */
    fun resetEstadoEnMemoria() {
        _periodosPendientes.value = emptySet()
        _errorSincronizacion.value = null
    }

    companion object {
        private const val TAG = "MovimientoRepository"
    }

    /**
     * insertarMovimiento
     * ------------------
     * Crea un movimiento: Room + réplica `movimientos/{id}` + resumen remoto.
     */
    suspend fun insertarMovimiento(movimiento: MovimientoEntity) {
        ejecutarOperacionEconomica(movimiento.idCliente) {
            // Un movimiento nuevo se crea ahora: se marca su fechaRegistro como
            // frontera de la etapa económica (los movimientos anteriores a la
            // última baja no cuentan para la morosidad por fecha tras reactivar).
            val conId = movimiento.copy(
                idMovimiento = if (movimiento.idMovimiento > 0) {
                    movimiento.idMovimiento
                } else {
                    IdMovimiento.nuevo()
                },
                fechaRegistro = System.currentTimeMillis()
            )
            movimientoDao.insertarMovimiento(conId)
            val resumen = calcularYPersistirMorosidad(conId.idCliente)
            val resultadoMovimiento = movimientoRemotoRepository
                .crearMovimientoRemoto(conId)
            publicarResumenYResultado(conId.idCliente, resumen, resultadoMovimiento.exito)
        }
    }

    /**
     * actualizarMovimiento
     * --------------------
     * Edita un movimiento: Room + réplica `movimientos/{id}` + resumen remoto.
     */
    suspend fun actualizarMovimiento(movimiento: MovimientoEntity) {
        ejecutarOperacionEconomica(movimiento.idCliente) {
            actualizarMovimientoCore(movimiento)
        }
    }

    /**
     * actualizarMovimientos
     * ---------------------
     * Actualización MASIVA (varios movimientos). Reutiliza el mismo núcleo
     * individual de `actualizarMovimientoCore` para cada movimiento: Room →
     * recálculo de morosidad/deuda → réplica `movimientos/{id}` → resumen
     * remoto. Cada elemento pasa por el mismo `ejecutarOperacionEconomica`
     * (Mutex + IO + NonCancellable) que la operación individual; no se duplica
     * ninguna regla de la F2 y no existe ningún delete directo desde la UI.
     */
    suspend fun actualizarMovimientos(movimientos: List<MovimientoEntity>) {
        movimientos.forEach { movimiento ->
            ejecutarOperacionEconomica(movimiento.idCliente) {
                actualizarMovimientoCore(movimiento)
            }
        }
    }

    /**
     * eliminarMovimiento
     * ------------------
     * Elimina un movimiento: Room primero, se registra la eliminación pendiente
     * en `eliminacion_pendiente`, se recalcula la economía SOLO con los
     * movimientos restantes y se publica el resumen; después se intenta el
     * borrado remoto (si falla, el registro pendiente persiste para reintento).
     */
    suspend fun eliminarMovimiento(movimiento: MovimientoEntity) {
        if (movimiento.idMovimiento <= 0) return
        ejecutarOperacionEconomica(movimiento.idCliente) {
            eliminarMovimientoCore(movimiento)
        }
    }

    /**
     * eliminarMovimientos
     * -------------------
     * Eliminación MASIVA (varios movimientos). Reutiliza el mismo núcleo
     * individual de `eliminarMovimientoCore` por movimiento conservando la
     * secuencia y el comportamiento existentes (Room → `eliminacion_pendiente`
     * → recálculo de morosidad/deuda → borrado remoto → reintento con el
     * mecanismo actual). Pasa siempre por este repositorio; nunca hay un
     * delete directo desde la UI.
     */
    suspend fun eliminarMovimientos(movimientos: List<MovimientoEntity>) {
        movimientos.forEach { movimiento ->
            if (movimiento.idMovimiento <= 0) return@forEach
            ejecutarOperacionEconomica(movimiento.idCliente) {
                eliminarMovimientoCore(movimiento)
            }
        }
    }

    /**
     * recalcularMorosidadDeCliente
     * ----------------------------
     * Recalcula y persiste la morosidad usando la ÚNICA lógica MovimientoMorosidad
     * y, a continuación, publica el resumen económico remoto. Se invoca tras
     * cambios de estado administrativo (baja, restauración…).
     */
    suspend fun recalcularMorosidadDeCliente(idCliente: Int) {
        ejecutarOperacionEconomica(idCliente) {
            val resumen = calcularYPersistirMorosidad(idCliente)
            publicarResumenYResultado(idCliente, resumen, true)
        }
    }

    /**
     * sincronizarPeriodoActual
     * ------------------------
     * Reconciliación completa de la economía de un cliente (reintento manual del
     * perfil): reintenta sus eliminaciones pendientes, replica todos sus
     * movimientos desde Room (idempotente) y publica el resumen remoto.
     */
    suspend fun sincronizarPeriodoActual(idCliente: Int) {
        withContext(NonCancellable + Dispatchers.IO) {
            sincronizacionMutex.withLock {
                try {
                    var ok = true
                    val pendientes = eliminacionPendienteDao
                        .obtenerPendientesDeClienteSync(idCliente)
                    for (pendiente in pendientes) {
                        val resultado = movimientoRemotoRepository
                            .eliminarMovimientoRemoto(pendiente.idMovimiento)
                        if (resultado.exito) {
                            eliminacionPendienteDao.eliminarPendiente(pendiente.idMovimiento)
                        } else {
                            ok = false
                        }
                    }

                    val movimientos = movimientoDao
                        .obtenerMovimientosPorCliente(idCliente)
                        .first()
                    for (movimiento in movimientos) {
                        val resultado = movimientoRemotoRepository
                            .crearMovimientoRemoto(movimiento)
                        if (!resultado.exito) ok = false
                    }

                    val resumen = calcularYPersistirMorosidad(idCliente)
                    val resultadoResumen = clienteRemotoRepository
                        .actualizarResumenEconomicoRemoto(
                            idCliente = idCliente,
                            moroso = resumen.moroso,
                            fechaEntradaMorosidad = resumen.fechaEntradaMorosidad,
                            deuda = resumen.deuda,
                            fechaInicioActual = resumen.fechaInicioActual,
                            fechaFinActual = resumen.fechaFinActual,
                            exentoMorosidad = resumen.exentoMorosidad
                        )
                    if (!resultadoResumen.exito) ok = false

                    if (ok) {
                        _periodosPendientes.value = _periodosPendientes.value - idCliente
                        _errorSincronizacion.value = null
                    } else {
                        _periodosPendientes.value = _periodosPendientes.value + idCliente
                        _errorSincronizacion.value =
                            "Cliente $idCliente: No se pudo completar la sincronización económica"
                    }
                } catch (e: Exception) {
                    _periodosPendientes.value = _periodosPendientes.value + idCliente
                    _errorSincronizacion.value =
                        "Cliente $idCliente: ${e.message ?: "error inesperado"}"
                    Log.e(TAG, "Error reconciliando economía: idCliente=$idCliente", e)
                }
            }
        }
    }

    /**
     * sincronizarSiHayPendientes
     * --------------------------
     * Reconciliación ECONÓMICA SOLO cuando es necesaria (AJUSTE 2): si el cliente
     * tiene eliminaciones pendientes persistidas o está marcado con una operación
     * remota fallida en esta sesión (`_periodosPendientes`). Abrir un perfil NO
     * reescribe todos los movimientos por defecto.
     */
    suspend fun sincronizarSiHayPendientes(idCliente: Int) {
        val hayEliminaciones = eliminacionPendienteDao
            .obtenerPendientesDeClienteSync(idCliente)
            .isNotEmpty()
        val hayOperacionPendiente = idCliente in _periodosPendientes.value
        if (hayEliminaciones || hayOperacionPendiente) {
            sincronizarPeriodoActual(idCliente)
        }
    }

    /**
     * reintentarEliminacionesPendientesGlobal
     * ---------------------------------------
     * Al arrancar (o al entrar en gestión de clientes): lee TODAS las
     * eliminaciones pendientes persistidas y reintenta su borrado remoto.
     * Si el ADMIN no está autenticado, el intento es un no-op.
     */
    suspend fun reintentarEliminacionesPendientesGlobal() {
        withContext(NonCancellable + Dispatchers.IO) {
            val pendientes = eliminacionPendienteDao.obtenerPendientesSync()
            for (pendiente in pendientes) {
                try {
                    val resultado = movimientoRemotoRepository
                        .eliminarMovimientoRemoto(pendiente.idMovimiento)
                    if (resultado.exito) {
                        eliminacionPendienteDao.eliminarPendiente(pendiente.idMovimiento)
                        Log.i(TAG, "Eliminación pendiente confirmada: idMovimiento=${pendiente.idMovimiento}")
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "No se pudo reintentar eliminación pendiente: idMovimiento=${pendiente.idMovimiento}",
                        e
                    )
                }
            }
        }
    }

    // =========================================================
    // Lecturas
    // =========================================================

    fun obtenerMovimientosPorCliente(idCliente: Int): Flow<List<MovimientoEntity>> {
        return movimientoDao.obtenerMovimientosPorCliente(idCliente)
    }

    suspend fun obtenerMovimientoPorId(idMovimiento: Int): MovimientoEntity? {
        return movimientoDao.obtenerMovimientoPorId(idMovimiento)
    }

    fun obtenerMovimientosPorEstado(
        estado: EstadoMovimiento
    ): Flow<List<MovimientoEntity>> {
        return movimientoDao.obtenerMovimientosPorEstado(estado)
    }

    fun obtenerTodosLosMovimientos(): Flow<List<MovimientoEntity>> {
        return movimientoDao.obtenerTodosLosMovimientos()
    }

    // =========================================================
    // Internos
    // =========================================================

    /**
     * Núcleo individual de actualización de un movimiento (Room + recálculo de
     * morosidad/deuda + réplica remota + resumen remoto). Lo comparten
     * `actualizarMovimiento` (1) y `actualizarMovimientos` (N) para no duplicar
     * la lógica F2. No incluye el wrapper `ejecutarOperacionEconomica` (cada
     * llamador aporta su propio contexto de Mutex/IO/errores).
     */
    private suspend fun actualizarMovimientoCore(movimiento: MovimientoEntity) {
        // Editar NUNCA cambia la fecha de creación ni la identidad histórica:
        // si la entidad entrante no trae fechaRegistro (0) se conserva la
        // almacenada, y los campos históricos (nombre/apellidos/DNI) se
        // conservan SIEMPRE desde el movimiento existente (son una fotografía
        // del momento de creación, inmutables al editar).
        val existente = movimientoDao.obtenerMovimientoPorId(movimiento.idMovimiento)
        val aGuardar = if (existente != null) {
            movimiento.copy(
                fechaRegistro = if (movimiento.fechaRegistro == 0L) {
                    existente.fechaRegistro
                } else {
                    movimiento.fechaRegistro
                },
                nombreCliente = existente.nombreCliente,
                apellidosCliente = existente.apellidosCliente,
                dniCliente = existente.dniCliente
            )
        } else {
            movimiento
        }
        movimientoDao.actualizarMovimiento(aGuardar)
        val resumen = calcularYPersistirMorosidad(aGuardar.idCliente)
        val resultadoMovimiento = movimientoRemotoRepository
            .actualizarMovimientoRemoto(aGuardar)
        publicarResumenYResultado(aGuardar.idCliente, resumen, resultadoMovimiento.exito)
    }

    /**
     * Núcleo individual de eliminación de un movimiento (Room primero, registro
     * en `eliminacion_pendiente`, recálculo de morosidad/deuda con los restantes,
     * borrado remoto y limpieza del pendiente si el borrado remoto es correcto).
     * Lo comparten `eliminarMovimiento` (1) y `eliminarMovimientos` (N). No
     * incluye el wrapper `ejecutarOperacionEconomica`.
     */
    private suspend fun eliminarMovimientoCore(movimiento: MovimientoEntity) {
        movimientoDao.eliminarMovimiento(movimiento)
        eliminacionPendienteDao.registrarPendiente(
            EliminacionPendienteEntity(
                idMovimiento = movimiento.idMovimiento,
                idCliente = movimiento.idCliente
            )
        )
        val resumen = calcularYPersistirMorosidad(movimiento.idCliente)
        val resultadoBorrado = movimientoRemotoRepository
            .eliminarMovimientoRemoto(movimiento.idMovimiento)
        if (resultadoBorrado.exito) {
            eliminacionPendienteDao.eliminarPendiente(movimiento.idMovimiento)
        }
        publicarResumenYResultado(movimiento.idCliente, resumen, resultadoBorrado.exito)
    }

    /**
     * Resumen económico calculado de un cliente tras persistir morosidad.
     */
    private data class ResumenEconomia(
        val moroso: Boolean,
        val fechaEntradaMorosidad: Long?,
        val deuda: Double,
        val fechaInicioActual: Long?,
        val fechaFinActual: Long?,
        val exentoMorosidad: Boolean
    )

    /**
     * Recalcula con el motor (deuda + morosidad + fechaEntradaMorosidad),
     * persiste el flag en Room y devuelve el resumen para publicar.
     */
    private suspend fun calcularYPersistirMorosidad(idCliente: Int): ResumenEconomia {
        val cliente = clienteDao.obtenerClientePorIdDao(idCliente)
            ?: throw IllegalStateException("No existe el cliente $idCliente")
        val movimientos = movimientoDao
            .obtenerMovimientosPorCliente(idCliente)
            .first()
        val ahora = System.currentTimeMillis()

        val final = MovimientoMorosidad.resultadoFinal(
            estado = cliente.estado,
            movimientos = movimientos,
            morosoPrevio = cliente.moroso,
            fechaEntradaPrevia = cliente.fechaEntradaMorosidad,
            ahora = ahora,
            exentoMorosidad = cliente.exentoMorosidad,
            // Frontera de etapa: la última fecha de baja. Los movimientos creados
            // antes de esa baja no cuentan para la morosidad por fecha.
            inicioEtapa = cliente.fechaBaja
        )
        clienteDao.actualizarMorosidadDao(
            idCliente,
            final.moroso,
            final.fechaEntradaMorosidad
        )

        val deuda = MovimientoMorosidad.deudaDe(movimientos)
        // El período actual (fechaInicioActual/fechaFinActual remotos) también se
        // calcula SOLO con la etapa actual: tras reactivar no debe arrastrar el
        // período anterior.
        val actual = MovimientoMorosidad.periodoActualDe(movimientos, cliente.fechaBaja)
        return ResumenEconomia(
            moroso = final.moroso,
            fechaEntradaMorosidad = final.fechaEntradaMorosidad,
            deuda = deuda,
            fechaInicioActual = actual?.fechaInicio,
            fechaFinActual = actual?.fechaFin,
            exentoMorosidad = cliente.exentoMorosidad
        )
    }

    /**
     * Publica el resumen remoto y refleja el resultado global (movimiento +
     * resumen) en el estado de pendientes/error del perfil.
     */
    private suspend fun publicarResumenYResultado(
        idCliente: Int,
        resumen: ResumenEconomia,
        resultadoMovimientoExito: Boolean
    ) {
        val resultadoResumen = clienteRemotoRepository.actualizarResumenEconomicoRemoto(
            idCliente = idCliente,
            moroso = resumen.moroso,
            fechaEntradaMorosidad = resumen.fechaEntradaMorosidad,
            deuda = resumen.deuda,
            fechaInicioActual = resumen.fechaInicioActual,
            fechaFinActual = resumen.fechaFinActual,
            exentoMorosidad = resumen.exentoMorosidad
        )
        if (resultadoMovimientoExito && resultadoResumen.exito) {
            _periodosPendientes.value = _periodosPendientes.value - idCliente
            _errorSincronizacion.value = null
        } else {
            _periodosPendientes.value = _periodosPendientes.value + idCliente
            val mensaje = if (!resultadoResumen.exito) {
                resultadoResumen.mensaje
            } else {
                "Cliente $idCliente: no se pudo sincronizar el movimiento"
            }
            _errorSincronizacion.value = mensaje
        }
    }

    /**
     * Envuelve las operaciones de economía en IO + NonCancellable + Mutex y
     * deja el estado de error informado si algo falla a nivel local/remoto.
     */
    private suspend fun ejecutarOperacionEconomica(
        idCliente: Int,
        bloque: suspend () -> Unit
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            sincronizacionMutex.withLock {
                try {
                    bloque()
                } catch (e: Exception) {
                    _periodosPendientes.value = _periodosPendientes.value + idCliente
                    _errorSincronizacion.value =
                        "Cliente $idCliente: ${e.message ?: "error inesperado"}"
                    Log.e(
                        TAG,
                        "Error en operación económica: idCliente=$idCliente",
                        e
                    )
                }
            }
        }
    }
}
