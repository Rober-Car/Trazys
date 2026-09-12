/* ============================================================
 * ============ BLOQUE 1: IMPORTS =============================
 * ============================================================ */
package com.roberto.gestorpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.model.EstadoMovimiento
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ============================================================
 * ============ BLOQUE 2: DOCUMENTACIÓN DEL ARCHIVO ===========
 * ============================================================ */
/**
 * MovimientoViewModel.kt
 * ----------------------
 * ✔ TIPO: archivo de código fuente Kotlin (ViewModel)
 * Es el archivo que define el ViewModel encargado de la lógica de los movimientos (servicios).
 * Sirve para conectar la interfaz de usuario con el repositorio de movimientos,
 * gestionando las operaciones CRUD sobre los servicios contratados por cada cliente.
 */

/* ============================================================
 * ============ BLOQUE 3: VIEWMODEL DE MOVIMIENTOS ============
 * ============================================================ */
/**
 * @HiltViewModel
 * --------------
 * ✔ TIPO: anotación (dagger.hilt.android.lifecycle.HiltViewModel)
 * Es la anotación que marca esta clase como ViewModel inyectable de Hilt.
 * Sirve para que Hilt construya el ViewModel y le inyecte sus dependencias automáticamente.
 */

/**
 * MovimientoViewModel
 * -------------------
 * ✔ TIPO: clase (ViewModel de Android)
 * Es el ViewModel que gestiona los datos de los movimientos (servicios) en la pantalla.
 * Sirve para que la interfaz sobreviva a cambios de configuración
 * y obtenga los datos de movimientos a través del repositorio.
 */
@HiltViewModel
class MovimientoViewModel @Inject constructor(

    /**
     * movimientoRepository
     * --------------------
     * ✔ TIPO: parámetro (param) → MovimientoRepository
     * Es el repositorio de movimientos que recibirá el ViewModel.
     * Sirve para que el ViewModel acceda a la base de datos a través del repositorio,
     * sin conocer los detalles de implementación del DAO.
     */
    private val movimientoRepository: MovimientoRepository
) : ViewModel() {

    /* ============================================================
     * ============ BLOQUE 4: ESTADO DEL VIEWMODEL ================
     * ============================================================ */
    /**
     * _movimientos
     * ------------
     * ✔ TIPO: propiedad (private val) → MutableStateFlow<List<MovimientoEntity>>
     * Es el flujo privado de movimientos del ViewModel, mutable solo dentro de la clase.
     * Sirve para guardar la lista actual de movimientos y actualizarla cuando la BD cambie.
     * Se inicializa con una lista vacía para evitar nulos.
     */
    private val _movimientos = MutableStateFlow<List<MovimientoEntity>>(emptyList())

    /**
     * movimientos
     * -----------
     * ✔ TIPO: propiedad (val) → StateFlow<List<MovimientoEntity>>
     * Es la versión de solo lectura del flujo _movimientos.
     * Sirve para que la interfaz observe la lista de movimientos en tiempo real
     * y se actualice automáticamente cuando se inserten, modifiquen o eliminen servicios.
     */
    val movimientos = _movimientos.asStateFlow()

    val errorSincronizacion: StateFlow<String?> = movimientoRepository.errorSincronizacion
    val periodosPendientes: StateFlow<Set<Int>> = movimientoRepository.periodosPendientes

    /* ============================================================
     * ============ BLOQUE 5: OPERACIONES DEL VIEWMODEL ===========
     * ============================================================ */
    /**
     * cargarMovimientosPorCliente
     * ---------------------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que carga todos los movimientos de un cliente concreto desde la base de datos.
     * Sirve para que la pantalla de perfil de cliente muestre la lista de servicios
     * que ese cliente tiene contratados.
     *
     * CÓMO FUNCIONA:
     * 1. Lanza una corrutina en el ámbito del ViewModel (viewModelScope).
     * 2. Llama al repositorio para obtener el Flow de movimientos del cliente.
     * 3. "collect" escucha cada cambio en la BD en tiempo real.
     * 4. Cada vez que la BD cambia, actualiza _movimientos con la nueva lista.
     */
    fun cargarMovimientosPorCliente(idCliente: Int) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la carga de movimientos en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI mientras se consulta la BD.
         */
        viewModelScope.launch {

            // Reconciliación SOLO si hay pendientes reales (eliminaciones
            // pendientes o una operación remota fallida en esta sesión). Abrir el
            // perfil NO reescribe todos los movimientos históricos por defecto.
            movimientoRepository.sincronizarSiHayPendientes(idCliente)

            /**
             * movimientoRepository.obtenerMovimientosPorCliente(idCliente)
             * ------------------------------------------------------------
             * ✔ TIPO: llamada al repositorio → Flow<List<MovimientoEntity>>
             * Es la consulta que obtiene todos los movimientos de un cliente concreto.
             * Devuelve un Flow que emite una nueva lista cada vez que la BD cambia.
             */
            movimientoRepository.obtenerMovimientosPorCliente(idCliente)

                /**
                 * collect { lista -> ... }
                 * ------------------------
                 * ✔ TIPO: operador de Flow (collect)
                 * Es el operador que escucha los valores que emite el Flow.
                 * Cada vez que el repositorio emite una nueva lista de movimientos,
                 * se ejecuta el bloque interno actualizando el estado del ViewModel.
                 */
                .collect { lista ->

                    /**
                     * _movimientos.value = lista
                     * --------------------------
                     * ✔ TIPO: asignación al MutableStateFlow
                     * Es la actualización del estado interno del ViewModel con la nueva lista.
                     * Al cambiar el valor, la interfaz que observe "movimientos" se repintará automáticamente.
                     */
                    _movimientos.value = lista
                }
        }
    }

    fun reintentarSincronizacionPeriodo(idCliente: Int) {
        viewModelScope.launch {
            movimientoRepository.sincronizarPeriodoActual(idCliente)
        }
    }

    /**
     * insertarMovimiento
     * ------------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que guarda un nuevo movimiento (servicio) en la base de datos.
     * Sirve para que la pantalla de añadir servicio inserte un MovimientoEntity
     * sin bloquear la interfaz de usuario.
     *
     * CÓMO FUNCIONA:
     * 1. Lanza una corrutina en el ámbito del ViewModel.
     * 2. Llama al repositorio para insertar el movimiento.
     * 3. Room guarda el movimiento y el Flow del DAO notifica el cambio.
     * 4. Si se está escuchando con collect, la lista se actualiza automáticamente.
     */
    fun insertarMovimiento(movimiento: MovimientoEntity) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la inserción del movimiento en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            /**
             * movimientoRepository.insertarMovimiento(movimiento)
             * ---------------------------------------------------
             * ✔ TIPO: llamada al repositorio (suspend fun)
             * Es la operación que delega al repositorio la inserción del movimiento en la BD.
             * El repositorio a su vez llama al DAO, que ejecuta la consulta SQL INSERT.
             */
            movimientoRepository.insertarMovimiento(movimiento)
        }
    }

    /**
     * actualizarMovimiento
     * --------------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que actualiza los datos de un movimiento ya existente.
     * Sirve para que la pantalla de editar servicio modifique un MovimientoEntity
     * sin bloquear la interfaz de usuario.
     *
     * CÓMO FUNCIONA:
     * 1. Lanza una corrutina en el ámbito del ViewModel.
     * 2. Llama al repositorio para actualizar el movimiento.
     * 3. Room actualiza el registro por su clave primaria.
     * 4. El Flow del DAO emite la lista actualizada.
     */
    fun actualizarMovimiento(movimiento: MovimientoEntity) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la actualización del movimiento en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            /**
             * movimientoRepository.actualizarMovimiento(movimiento)
             * -----------------------------------------------------
             * ✔ TIPO: llamada al repositorio (suspend fun)
             * Es la operación que delega al repositorio la actualización del movimiento en la BD.
             * El repositorio a su vez llama al DAO, que ejecuta la consulta SQL UPDATE.
             */
            movimientoRepository.actualizarMovimiento(movimiento)
        }
    }

    /**
     * eliminarMovimiento
     * ------------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que elimina un movimiento (servicio) de la base de datos.
     * Sirve para que la pantalla de perfil de cliente borre un servicio
     * sin bloquear la interfaz de usuario.
     *
     * CÓMO FUNCIONA:
     * 1. Lanza una corrutina en el ámbito del ViewModel.
     * 2. Llama al repositorio para eliminar el movimiento.
     * 3. Room elimina el registro por su clave primaria.
     * 4. El Flow del DAO emite la lista sin el movimiento eliminado.
     */
    fun eliminarMovimiento(movimiento: MovimientoEntity) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la eliminación del movimiento en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            /**
             * movimientoRepository.eliminarMovimiento(movimiento)
             * ---------------------------------------------------
             * ✔ TIPO: llamada al repositorio (suspend fun)
             * Es la operación que delega al repositorio la eliminación del movimiento de la BD.
             * El repositorio a su vez llama al DAO, que ejecuta la consulta SQL DELETE.
             */
            movimientoRepository.eliminarMovimiento(movimiento)
        }
    }

    /**
     * obtenerMovimientoPorId
     * ----------------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que recupera un único movimiento por su ID desde la base de datos.
     * Sirve para que la pantalla de perfil o detalle muestre los datos de un servicio concreto.
     *
     * CÓMO FUNCIONA:
     * 1. Lanza una corrutina en el ámbito del ViewModel.
     * 2. Llama al repositorio para buscar el movimiento por su ID.
     * 3. El repositorio devuelve el MovimientoEntity o null si no existe.
     * 4. Llama al callback "onResultado" con el resultado para que la pantalla lo use.
     *
     * DIFERENCIA CON LOS DEMÁS MÉTODOS:
     * Este método usa un callback (onResultado) en lugar de un StateFlow porque
     * solo necesita obtener un dato puntual, no una lista que cambie en tiempo real.
     *
     * @param idMovimiento Int → el ID del movimiento que se quiere buscar.
     * @param onResultado (MovimientoEntity?) -> Unit → callback que recibe el resultado:
     *        - MovimientoEntity si se encontró el movimiento.
     *        - null si no existe ningún movimiento con ese ID.
     */
    fun obtenerMovimientoPorId(
        idMovimiento: Int,
        onResultado: (MovimientoEntity?) -> Unit
    ) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta la búsqueda del movimiento en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            /**
             * val movimiento = movimientoRepository.obtenerMovimientoPorId(idMovimiento)
             * ---------------------------------------------------------------------------
             * ✔ TIPO: variable local (val) → MovimientoEntity?
             * Es la variable que almacena el resultado de la búsqueda en la BD.
             * Puede ser un MovimientoEntity si se encontró, o null si no existe.
             */
            val movimiento =
                movimientoRepository.obtenerMovimientoPorId(idMovimiento)

            /**
             * onResultado(movimiento)
             * -----------------------
             * ✔ TIPO: invocación del callback
             * Es la llamada al callback que devuelve el resultado a la pantalla que lo solicitó.
             * La pantalla recibirá el MovimientoEntity (o null) y podrá usarlo
             * para rellenar un formulario, mostrar datos, etc.
             */
            onResultado(movimiento)
        }
    }

    /* ============================================================
     * ============ BLOQUE 6: RENOVACIÓN DE MOVIMIENTO ============
     * ============================================================ */
    /**
     * renovarMovimiento
     * -----------------
     * ✔ TIPO: método (fun) de MovimientoViewModel
     * Es la función que crea un nuevo movimiento renovando el último periodo del cliente.
     * Sirve para copiar el servicio, precio y duración del último movimiento,
     * continuando el periodo donde este terminó (o empezando hoy si el usuario lo prefiere).
     *
     * CÓMO FUNCIONA:
     * 1. Calcula la duración del último periodo (fechaFin - fechaInicio).
     * 2. Si la duración no es válida (<= 0), aborta sin insertar nada.
     * 3. Calcula la nueva fecha de inicio: hoy o la fechaFin del último movimiento.
     * 4. Crea una copia del movimiento con idMovimiento = 0 (nuevo registro),
     *    las nuevas fechas, estado PAGADO, fechaPago = ahora y sin observaciones.
     * 5. Inserta el movimiento a través del repositorio.
     *
     * @param ultimo MovimientoEntity → el último movimiento del cliente que se quiere renovar.
     * @param empezarHoy Boolean → true si el nuevo periodo debe empezar hoy;
     *        false si debe empezar en la fechaFin del último movimiento.
     */
    fun renovarMovimiento(
        ultimo: MovimientoEntity,
        empezarHoy: Boolean,
        nombreCliente: String,
        apellidosCliente: String,
        dniCliente: String
    ) {

        /**
         * viewModelScope.launch
         * ---------------------
         * ✔ TIPO: corrutina lanzada en el ámbito del ViewModel
         * Es la corrutina que ejecuta el cálculo e inserción en segundo plano.
         * Sirve para no bloquear el hilo principal de la UI.
         */
        viewModelScope.launch {

            /**
             * duracion
             * --------
             * ✔ TIPO: variable local (val) → Long
             * Es la duración en milisegundos del periodo del último movimiento.
             * Sirve para replicar el mismo periodo en el movimiento renovado.
             */
            val duracion = ultimo.fechaFin - ultimo.fechaInicio

            /**
             * Protección de datos corruptos
             * -----------------------------
             * ✔ TIPO: condición de guarda (if) con return@launch
             * Si la duración calculada no es positiva, el movimiento tiene fechas incoherentes.
             * Sirve para abortar la renovación sin insertar un movimiento inválido.
             */
            if (duracion <= 0) return@launch

            /**
             * ahora
             * -----
             * ✔ TIPO: variable local (val) → Long
             * Es la marca de tiempo actual en milisegundos.
             * Sirve como posible fecha de inicio del nuevo periodo y como fecha de pago.
             */
            val ahora = System.currentTimeMillis()

            /**
             * nuevaFechaInicio
             * ----------------
             * ✔ TIPO: variable local (val) → Long
             * Es la fecha de inicio del periodo renovado.
             * Sirve para empezar el nuevo periodo hoy (si empezarHoy es true)
             * o justo donde terminó el último movimiento (si es false).
             */
            val nuevaFechaInicio = if (empezarHoy) ahora else ultimo.fechaFin

            /**
             * movimientoRenovado
             * ------------------
             * ✔ TIPO: variable local (val) → MovimientoEntity
             * Es el nuevo movimiento construido copiando el anterior con .copy().
             * Sirve para mantener servicio y precio originales, actualizando solo
             * el periodo, el estado (PAGADO), la fecha de pago y limpiando observaciones.
             */
            val movimientoRenovado = ultimo.copy(
                idMovimiento = 0,
                // Renovar crea un NUEVO período: se captura la identidad ACTUAL
                // del cliente en este instante (nueva fotografía histórica), no
                // la del movimiento anterior.
                nombreCliente = nombreCliente,
                apellidosCliente = apellidosCliente,
                dniCliente = dniCliente,
                fechaInicio = nuevaFechaInicio,
                fechaFin = nuevaFechaInicio + duracion,
                estado = EstadoMovimiento.PAGADO,
                fechaPago = ahora,
                observaciones = null
            )

            /**
             * movimientoRepository.insertarMovimiento(movimientoRenovado)
             * ------------------------------------------------------------
             * ✔ TIPO: llamada al repositorio (suspend fun)
             * Es la operación que guarda el movimiento renovado en la base de datos.
             * Sirve para persistir la renovación; el Flow del DAO actualizará la lista automáticamente.
             */
            movimientoRepository.insertarMovimiento(movimientoRenovado)
        }
    }
}
