package com.roberto.gestorpro.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.roberto.gestorpro.data.database.ClientesDatabase
import com.roberto.gestorpro.data.firebase.ClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.EstadoNegocioDeCuenta
import com.roberto.gestorpro.data.firebase.MovimientoRemotoRepository
import com.roberto.gestorpro.data.firebase.NegocioRepository
import com.roberto.gestorpro.data.firebase.ReservaRemotoRepository
import com.roberto.gestorpro.data.firebase.ServicioRemotoRepository
import com.roberto.gestorpro.data.firebase.SesionRemotoRepository
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HidratadorCacheLocal
 * --------------------
 * Coordinador CENTRAL de la reconstrucción de la caché Room desde Firestore
 * después de un cambio de propietario (WIPE) o de una instalación nueva con
 * Room vacía. Mantiene la arquitectura: UN solo negocio por instalación,
 * Room = caché local y Firestore = fuente remota.
 *
 * Reglas:
 *  - Solo hidrata si la cuenta autenticada tiene un negocio CONFIRMADO
 *    (`estadoNegocioDeCuenta() == ConNegocio` y `negocioId == uid`). Si la
 *    cuenta no tiene negocio NO se hace ninguna consulta remota de datos.
 *  - Todas las lecturas remotas se filtran por el negocio de la cuenta actual
 *    (el UID autenticado). NUNCA se usan datos del propietario anterior ni de
 *    filas antiguas.
 *  - Inserciones SOLO si la fila no existe (idempotente, sin duplicados y sin
 *    sobrescribir filas válidas ya presentes).
 *  - Cada fase se ejecuta en su propia transacción Room para que una fase que
 *    falle no deje una base a medias de forma incoherente: las fases anteriores
 *    ya confirmadas son válidas y las siguientes se pueden reintentar.
 *  - Es BEST-EFFORT: un fallo de red/permisos NO marca la cuenta como inválida
 *    ni bloquea la navegación; NO se escribe el marcador de "caché hidratada",
 *    por lo que un login posterior reintenta automáticamente.
 *
 * Orden de hidratación (respetando dependencias):
 *   1. clientes -> 2. servicios -> 3. sesiones -> 4. reservas ->
 *   5. movimientos -> 6. recalcular morosidad/deuda por cliente afectado.
 */
@Singleton
class HidratadorCacheLocal @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: ClientesDatabase,
    private val preferences: PreferencesRepository,
    private val negocioRepository: NegocioRepository,
    private val clienteRemotoRepository: ClienteRemotoRepository,
    private val servicioRemotoRepository: ServicioRemotoRepository,
    private val sesionRemotoRepository: SesionRemotoRepository,
    private val reservaRemotoRepository: ReservaRemotoRepository,
    private val movimientoRemotoRepository: MovimientoRemotoRepository,
    private val movimientoRepository: MovimientoRepository
) {

    companion object {
        private const val TAG = "HidratadorCacheLocal"
    }

    private val hidratacionEnCurso = AtomicBoolean(false)

    /**
     * hidratarSiNecesario
     * -------------------
     * Punto de entrada. Comprueba: sesión, marcador de caché ya hidratada para
     * esta cuenta (evita repetir la reconstrucción en cada login), negocio
     * confirmado y ausencia de otra hidratación concurrente.
     */
    suspend fun hidratarSiNecesario() {
        val uid = auth.currentUser?.uid ?: return
        if (preferences.obtenerUidCacheHidratada() == uid) return
        if (!hidratacionEnCurso.compareAndSet(false, true)) return

        try {
            when (val estado = negocioRepository.estadoNegocioDeCuenta()) {
                EstadoNegocioDeCuenta.SinSesion,
                EstadoNegocioDeCuenta.Error -> {
                    // No confirmado: sin datos del negocio no se toca nada y no
                    // se marca como hidratado (se reintentará más adelante).
                }

                EstadoNegocioDeCuenta.SinNegocio -> {
                    // Cuenta sin negocio CONFIRMADO: Room permanece vacía y NO se
                    // consultan clientes/servicios/etc. Se marca para no repetir
                    // la comprobación en cada login (no hay nada que hidratar).
                    preferences.setUidCacheHidratada(uid)
                }

                is EstadoNegocioDeCuenta.ConNegocio -> {
                    if (estado.negocioId != uid) return
                    hidratarTodo(estado.negocioId)
                    preferences.setUidCacheHidratada(uid)
                }
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Hidratación incompleta (no se marca como completada; se reintentará) uid=$uid",
                e
            )
        } finally {
            hidratacionEnCurso.set(false)
        }
    }

    // ---------------------------------------------------------------------
    // Fases
    // ---------------------------------------------------------------------

    private suspend fun hidratarTodo(negocioId: String) {
        hidratarClientes()
        hidratarServicios()
        hidratarSesiones()
        hidratarReservas(negocioId)
        val clientesAfectados = hidratarMovimientos()
        recalcularMorosidad(clientesAfectados)
    }

    /**
     * hidratarClientes
     * ----------------
     * Incorpora los clientes remotos del negocio actual. Reutiliza la lógica
     * existente (ClienteRemotoRepository.obtenerClientesRemotosDelNegocio) y
     * conserva idCliente, negocioId, observaciones, foto y el resto de campos.
     */
    private suspend fun hidratarClientes() {
        val remotos = clienteRemotoRepository.obtenerClientesRemotosDelNegocio()
        if (remotos.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val dao = database.clienteDao()
                for (cliente in remotos) {
                    val existePorId = dao.obtenerClientePorIdDao(cliente.idCliente) != null
                    val existePorDni = cliente.dni.isNotBlank() &&
                        dao.obtenerClientePorDniDao(cliente.dni) != null
                    if (!existePorId && !existePorDni) {
                        dao.insertarClienteDao(cliente)
                    }
                }
            }
        }
    }

    /**
     * hidratarServicios
     * -----------------
     * Incorpora los servicios remotos del negocio actual conservando idServicio,
     * nombre, descripcion, precio, activo y negocioId.
     */
    private suspend fun hidratarServicios() {
        val remotos = servicioRemotoRepository.obtenerServiciosRemotosDelNegocio()
        if (remotos.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val dao = database.servicioDao()
                for (servicio in remotos) {
                    if (dao.obtenerServicioPorId(servicio.idServicio) == null) {
                        dao.insertarServicio(servicio)
                    }
                }
            }
        }
    }

    /**
     * hidratarSesiones
     * ----------------
     * Incorpora las sesiones remotas del negocio actual conservando todos los
     * campos (fecha, hora, duración, capacidad, plazas y horaDesdeReserva).
     */
    private suspend fun hidratarSesiones() {
        val remotos = sesionRemotoRepository.obtenerSesionesRemotasDelNegocio()
        if (remotos.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val dao = database.sesionDao()
                for (sesion in remotos) {
                    if (dao.obtenerSesionPorId(sesion.idSesion) == null) {
                        dao.insertarSesion(sesion)
                    }
                }
            }
        }
    }

    /**
     * hidratarReservas
     * ----------------
     * Incorpora las reservas remotas del negocio actual SOLO si su cliente y su
     * sesión existen en Room. Las reservas huérfanas se descartan (no se crean
     * clientes ni sesiones artificialmente). No crea duplicados: se comprueba
     * la pareja única (idSesion, idCliente) antes de insertar.
     */
    private suspend fun hidratarReservas(negocioId: String) {
        val remotos = reservaRemotoRepository.obtenerReservasRemotasDelNegocio()
        if (remotos.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val clienteDao = database.clienteDao()
                val sesionDao = database.sesionDao()
                val reservaDao = database.reservaDao()
                for (reserva in remotos) {
                    if (reserva.negocioId != negocioId) continue
                    if (clienteDao.obtenerClientePorIdDao(reserva.idCliente) == null) continue
                    if (sesionDao.obtenerSesionPorId(reserva.idSesion) == null) continue
                    if (reservaDao.obtenerReserva(reserva.idSesion, reserva.idCliente) == null) {
                        reservaDao.insertarReserva(reserva)
                    }
                }
            }
        }
    }

    /**
     * hidratarMovimientos
     * -------------------
     * Incorpora los movimientos remotos del negocio actual conservando el
     * idMovimiento original (NO se usan autoincrementos locales) y devuelve el
     * conjunto de clientes afectados (los que tienen movimientos en Room tras la
     * hidratación) para recalcular su morosidad/deuda.
     */
    private suspend fun hidratarMovimientos(): Set<Int> {
        val remotos = movimientoRemotoRepository.obtenerMovimientosRemotosDelNegocio()
        if (remotos.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val movimientoDao = database.movimientoDao()
                    for (movimiento in remotos) {
                        // Los movimientos son histórico económico AUTÓNOMO del
                        // centro: se conservan aunque su cliente ya no exista
                        // (p. ej. cuenta eliminada). No se descartan huérfanos.
                        if (movimientoDao.obtenerMovimientoPorId(movimiento.idMovimiento) == null) {
                            movimientoDao.insertarMovimiento(movimiento)
                        }
                    }
                }
            }
        }
        return withContext(Dispatchers.IO) {
            database.movimientoDao()
                .obtenerTodosLosMovimientosSync()
                .map { it.idCliente }
                .toSet()
        }
    }

    /**
     * recalcularMorosidad
     * -------------------
     * Recalcula y persiste la morosidad/deuda local de los clientes afectados
     * usando el motor existente (MovimientoMorosidad, con la regla de
     * fechaBaja/inicioEtapa) y publica el resumen económico remoto. Cada cliente
     * se procesa de forma aislada y best-effort: un fallo de publicación no
     * aborta el resto ni impide marcar la caché como hidratada.
     */
    private suspend fun recalcularMorosidad(clientesAfectados: Set<Int>) {
        for (idCliente in clientesAfectados) {
            // La morosidad es un dato del CLIENTE: solo se recalcula si la ficha
            // existe. Un movimiento histórico sin ficha (cuenta eliminada) no
            // debe provocar recálculo ni publicación de resumen.
            val existe = withContext(Dispatchers.IO) {
                database.clienteDao().obtenerClientePorIdDao(idCliente) != null
            }
            if (!existe) continue
            try {
                movimientoRepository.recalcularMorosidadDeCliente(idCliente)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo recalcular morosidad del cliente $idCliente", e)
            }
        }
    }
}
