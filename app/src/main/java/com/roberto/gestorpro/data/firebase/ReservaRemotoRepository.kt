package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roberto.gestorpro.data.entity.ReservaEntity
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.model.ReservaClienteDetalle
import com.roberto.gestorpro.util.BajaServicioReglas
import com.roberto.gestorpro.util.HidratacionMapeadores
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReservaRemotoRepository
 * -----------------------
 * Repositorio de reservas en Firestore orientado al nuevo modelo
 * Cliente -> Reserva -> SesionEntity -> ServicioEntity.
 *
 * El documentId es DETERMINISTA: reservas/{clienteId}_{sesionId}, lo que
 * garantiza una única reserva activa por cliente + sesión.
 *
 * - La creación y la cancelación son ATÓMICAS (Firestore Transaction): la
 *   reserva y el ajuste de plazasDisponibles de la sesión van siempre juntos.
 * - Las cascadas de borrado de reservas se agrupan en WriteBatch.
 *
 * No toca movimientos en ningún caso.
 */
@Singleton
class ReservaRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_RESERVAS = "reservas"
        private const val COLECCION_SESIONES = "sesiones"
        private const val COLECCION_SERVICIOS = "servicios"
        private const val COLECCION_CLIENTES = "clientes"
        private const val SUBCOLECCION_AGENDA = "agenda"
        private const val TAG = "ReservaRemotoRepository"

        /**
         * Máximo de reintentos de la cascada ante conflictos de transacción.
         * Cada reintento vuelve a consultar las reservas en FRESCO para capturar
         * reservas creadas entre consultas (máx. intentos = MAX_REINTENTOS + 1).
         */
        private const val MAX_REINTENTOS_CASCADA = 3

        /**
         * Máximo de reservas por sesión para poder eliminarlas de forma atómica:
         * 1 lectura de la sesión + N borrados de reserva + 1 borrado de sesión
         * no deben superar las 500 operaciones de la Transaction.
         */
        private const val MAX_RESERVAS_POR_SESION = 498

        /**
         * Máximo de escrituras por WriteBatch al limpiar la AGENDA derivada de
         * un cliente tras una cascada. Se usa ≤400 (por debajo del límite de 500
         * operaciones de un WriteBatch) para dejar margen y mantener la operación
         * dentro de los límites de Firestore.
         */
        private const val MAX_AGENDA_POR_BATCH = 400

        /**
         * documentId de una reserva: {clienteId}_{sesionId}.
         */
        fun reservaId(clienteId: Int, sesionId: Int): String =
            "${clienteId}_${sesionId}"
    }

    /**
     * rutaAgendaDelDia
     * ----------------
     * Ruta del documento AGENDA derivado del día de una sesión:
     * clientes/{clienteId}/agenda/{fecha}, donde fecha es el epoch millis de la
     * medianoche local del día (el mismo valor que guarda sesiones/{id}.fecha y
     * el que usa la Cloud Function de reservas como documentId).
     */
    private fun rutaAgendaDelDia(clienteId: Int, fecha: Long): String =
        "clientes/$clienteId/$SUBCOLECCION_AGENDA/$fecha"

    private fun refAgenda(clienteId: Int, fecha: Long) =
        db.document(rutaAgendaDelDia(clienteId, fecha))

    /**
     * limpiarAgendaDeSesionEliminada
     * ------------------------------
     * Tras eliminar una sesión (con sus reservas), retira la entrada de la
     * sesión del mapa `sesiones` de la agenda derivada de cada cliente afectado
     * en el día de esa sesión. Si el mapa queda vacío se borra el documento.
     * Idempotente: si la agenda no existe o ya no contiene la sesión, no-op.
     *
     * La agenda es un dato DERIVADO (no fuente de verdad) y esta limpieza es
     * BEST-EFFORT: nunca aborta la cascada principal si falla (se registra y el
     * día se reconstruye bajo demanda contra las reservas reales).
     */
    private suspend fun limpiarAgendaDeSesionEliminada(
        sesionId: Int,
        fecha: Long,
        clienteIds: List<Int>
    ) {
        if (fecha <= 0L || clienteIds.isEmpty()) return
        try {
            clienteIds.distinct().chunked(MAX_AGENDA_POR_BATCH).forEach { lote ->
                val batch = db.batch()
                var pendientes = 0
                for (clienteId in lote) {
                    val agendaRef = refAgenda(clienteId, fecha)
                    val agendaSnap = agendaRef.get().esperar()
                    if (!agendaSnap.exists()) continue
                    val sesiones = agendaSnap.get("sesiones") as? Map<*, *>
                    val sinSesion = sesiones
                        ?.toMutableMap()
                        ?.apply { remove(sesionId.toString()) }
                        ?: emptyMap<String, Any?>()
                    if (sinSesion.isEmpty()) {
                        batch.delete(agendaRef)
                    } else {
                        batch.update(agendaRef, mapOf("sesiones" to sinSesion))
                    }
                    pendientes++
                }
                if (pendientes > 0) batch.commit().esperar()
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "limpiarAgendaDeSesionEliminada: no se pudo limpiar la agenda " +
                    "de la sesión $sesionId (día $fecha): ${e.message}",
                e
            )
        }
    }

    /**
     * obtenerReservasRemotasDelNegocio
     * ---------------------------------
     * Recupera TODAS las reservas del negocio del ADMIN autenticado desde
     * `reservas/{clienteId}_{sesionId}` (query filtrada por negocioId). Solo
     * lectura. Se usa en la hidratación central: las reservas huérfanas (sin
     * su cliente o su sesión locales) NO se insertan en Room. NO traga los
     * errores (un fallo se propaga para reintentar).
     */
    suspend fun obtenerReservasRemotasDelNegocio(): List<ReservaEntity> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val negocioId = uid
        return db.collection(COLECCION_RESERVAS)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
            .documents
            .mapNotNull { documento ->
                HidratacionMapeadores.reservaDeDocumento(
                    documento.data ?: emptyMap(),
                    negocioId
                )
            }
    }

    /**
     * contarReservasDeSesionRemoto
     * ----------------------------
     * Número de reservas activas REALES de una sesión en Firestore (hay un
     * documento de reserva por reserva). Se usa al cambiar la CAPACIDAD de una
     * sesión desde el Admin para no basar el cálculo en el valor local de Room,
     * que puede estar desactualizado respecto a las reservas creadas por
     * appCliente. Devuelve null si no hay sesión autenticada o si la consulta
     * falla (el llamador usará su respaldo).
     */
    suspend fun contarReservasDeSesionRemoto(idSesion: Int): Int? {
        val negocioId = auth.currentUser?.uid ?: return null
        return try {
            db.collection(COLECCION_RESERVAS)
                .whereEqualTo("sesionId", idSesion)
                .whereEqualTo("negocioId", negocioId)
                .get()
                .esperar()
                .size()
        } catch (e: Exception) {
            val codigo = (e as? FirebaseFirestoreException)?.code?.name ?: "NO_FIRESTORE_CODE"
            Log.e(
                TAG,
                "contarReservasDeSesionRemoto: idSesion=$idSesion falló. códigoFirebase=$codigo",
                e
            )
            null
        }
    }

    /**
     * crearReservaRemota
     * ------------------
     * Crea una reserva de forma ATÓMICA dentro de una Transaction:
     * comprueba cliente, sesión, servicio (activo y del negocio), servicio
     * contratado, plazas disponibles y que no exista ya la reserva; crea la
     * reserva y decrementa plazasDisponibles.
     */
    suspend fun crearReservaRemota(
        clienteId: Int,
        sesionId: Int
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        return try {
            db.runTransaction { transaction ->
                val clienteRef = db.collection(COLECCION_CLIENTES)
                    .document(clienteId.toString())
                val sesionRef = db.collection(COLECCION_SESIONES)
                    .document(sesionId.toString())
                val reservaRef = db.collection(COLECCION_RESERVAS)
                    .document(reservaId(clienteId, sesionId))

                val cliente = transaction.get(clienteRef)
                if (!cliente.exists()) throw ReservaException("El cliente no existe")
                val negocioId = cliente.getString("negocioId")
                    ?: throw ReservaException("El cliente no tiene centro")
                val serviciosContratados = cliente.get("serviciosContratados") as? List<*>
                    ?: emptyList<Any>()

                val sesion = transaction.get(sesionRef)
                if (!sesion.exists()) throw ReservaException("La sesión no existe")
                if (sesion.getString("negocioId") != negocioId) {
                    throw ReservaException("La sesión no pertenece a tu centro")
                }
                val idServicio = sesion.getLong("idServicio")?.toInt()
                    ?: throw ReservaException("La sesión no tiene servicio")
                val plazas = sesion.getLong("plazasDisponibles")?.toInt() ?: 0
                if (plazas <= 0) throw ReservaException("No hay plazas disponibles")

                val servicioRef = db.collection(COLECCION_SERVICIOS)
                    .document(idServicio.toString())
                val servicio = transaction.get(servicioRef)
                if (!servicio.exists()) throw ReservaException("El servicio no existe")
                if (servicio.getString("negocioId") != negocioId) {
                    throw ReservaException("El servicio no pertenece a tu centro")
                }
                if (servicio.getBoolean("activo") != true) {
                    throw ReservaException("El servicio está inactivo")
                }
                if (idServicio !in serviciosContratados.filterIsInstance<Number>().map { it.toInt() }) {
                    throw ReservaException("No tienes contratado este servicio")
                }

                if (transaction.get(reservaRef).exists()) {
                    throw ReservaException("Ya tienes una reserva para esta sesión")
                }

                transaction.set(
                    reservaRef,
                    mapOf(
                        "idReserva" to reservaId(clienteId, sesionId),
                        "negocioId" to negocioId,
                        "sesionId" to sesionId,
                        "clienteId" to clienteId,
                        "fechaReserva" to Timestamp.now()
                    )
                )
                transaction.update(
                    sesionRef,
                    mapOf("plazasDisponibles" to (plazas - 1))
                )
            }.esperar()
            ResultadoAutenticacion(true, "Reserva realizada")
        } catch (e: ReservaException) {
            ResultadoAutenticacion(false, e.message ?: "No se pudo realizar la reserva")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * cancelarReservaRemota
     * ---------------------
     * Cancela una reserva de forma ATÓMICA dentro de una Transaction:
     * comprueba que la reserva existe y que la sesión sigue existiendo, elimina
     * la reserva e incrementa plazasDisponibles (sin superar la capacidad).
     *
     * FASE 3: la misma Transaction retira el ASISTENTE del cliente de la sesión
     * (sesiones/{id}.asistentes.{clienteId}) y limpia la entrada de la sesión en
     * la AGENDA derivada del cliente para ese día (clientes/{clienteId}/agenda/
     * {fecha}; borra el documento si el mapa queda vacío). Así una cancelación
     * administrativa (p. ej. la baja de un cliente) no deja asistentes ni
     * entradas de agenda huérfanas. Es idempotente: si la agenda no existe o no
     * contiene la sesión, no hace nada.
     */
    suspend fun cancelarReservaRemota(
        clienteId: Int,
        sesionId: Int
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val negocioId = uid

        return try {
            db.runTransaction { transaction ->
                val reservaRef = db.collection(COLECCION_RESERVAS)
                    .document(reservaId(clienteId, sesionId))
                val sesionRef = db.collection(COLECCION_SESIONES)
                    .document(sesionId.toString())

                if (!transaction.get(reservaRef).exists()) {
                    throw ReservaException("No existe la reserva")
                }
                val sesion = transaction.get(sesionRef)
                if (!sesion.exists()) throw ReservaException("La sesión no existe")

                val plazas = sesion.getLong("plazasDisponibles")?.toInt() ?: 0
                val capacidad = sesion.getLong("capacidad")?.toInt() ?: plazas
                if (plazas >= capacidad) {
                    throw ReservaException("La sesión ya está completa")
                }

                // Leer TODAS las lecturas antes de escribir (agenda derivada del
                // día). Si la agenda existe, se quita la entrada de la sesión o
                // se borra el documento cuando el día queda vacío.
                val fecha = sesion.getLong("fecha")
                var agendaParaBorrar = false
                var sesionesAgendaActualizado: Map<*, *>? = null
                var agendaExiste = false
                if (fecha != null) {
                    val agendaRef = refAgenda(clienteId, fecha)
                    val agenda = transaction.get(agendaRef)
                    agendaExiste = agenda.exists()
                    if (agendaExiste) {
                        val sesiones = agenda.get("sesiones") as? Map<*, *>
                        val sinSesion = sesiones
                            ?.toMutableMap()
                            ?.apply { remove(sesionId.toString()) }
                            ?: emptyMap<String, Any?>()
                        agendaParaBorrar = sinSesion.isEmpty()
                        sesionesAgendaActualizado = sinSesion
                    }
                }

                transaction.delete(reservaRef)
                transaction.update(
                    sesionRef,
                    mapOf(
                        "plazasDisponibles" to (plazas + 1),
                        // Retirar al cliente de los asistentes confirmados.
                        "asistentes.$clienteId" to FieldValue.delete()
                    )
                )

                if (fecha != null && agendaExiste) {
                    val agendaRef = refAgenda(clienteId, fecha)
                    if (agendaParaBorrar) {
                        transaction.delete(agendaRef)
                    } else {
                        transaction.set(
                            agendaRef,
                            mapOf(
                                "negocioId" to negocioId,
                                "fecha" to fecha,
                                "sesiones" to (sesionesAgendaActualizado ?: emptyMap<String, Any?>())
                            )
                        )
                    }
                }
            }.esperar()
            ResultadoAutenticacion(true, "Reserva cancelada")
        } catch (e: ReservaException) {
            ResultadoAutenticacion(false, e.message ?: "No se pudo cancelar la reserva")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * obtenerReservasDeSesionRemoto
     * -----------------------------
     * Obtiene las reservas de una sesión directamente desde Firestore
     * (reservas/{clienteId}_{sesionId}), que es la fuente de verdad de las
     * reservas creadas por appCliente. Consulta por sesionId + negocioId
     * (el índice compuesto correspondiente ya existe en producción) y
     * enriquece cada reserva con los datos del cliente (nombre, apellidos,
     * teléfono, foto y estado real) desde clientes/{idCliente}. Si la
     * operación falla, devuelve lista vacía para no romper la pantalla.
     */
    suspend fun obtenerReservasDeSesionRemoto(idSesion: Int): List<ReservaClienteDetalle> {
        val uid = auth.currentUser?.uid
            ?: return emptyList()
        val negocioId = uid

        return try {
            val reservas = db.collection(COLECCION_RESERVAS)
                .whereEqualTo("sesionId", idSesion)
                .whereEqualTo("negocioId", negocioId)
                .get()
                .esperar()
                .documents

            reservas.mapNotNull { documento ->
                val clienteId = documento.getLong("clienteId")?.toInt()
                    ?: return@mapNotNull null
                val cliente = db.collection(COLECCION_CLIENTES)
                    .document(clienteId.toString())
                    .get()
                    .esperar()
                if (!cliente.exists()) {
                    Log.w(TAG, "obtenerReservasDeSesionRemoto: cliente $clienteId no existe")
                    return@mapNotNull null
                }
                ReservaClienteDetalle(
                    idCliente = clienteId,
                    nombre = cliente.getString("nombre") ?: "",
                    apellidos = cliente.getString("apellidos") ?: "",
                    telefono = cliente.getString("telefono") ?: "",
                    foto = cliente.getString("foto") ?: "",
                    estado = estadoDe(cliente.getString("estado"))
                )
            }.sortedWith(compareBy({ it.nombre }, { it.apellidos }))
        } catch (e: Exception) {
            val codigo = (e as? FirebaseFirestoreException)?.code?.name ?: "NO_FIRESTORE_CODE"
            Log.e(
                TAG,
                "obtenerReservasDeSesionRemoto: idSesion=$idSesion falló. códigoFirebase=$codigo",
                e
            )
            emptyList()
        }
    }

    /**
     * estadoDe
     * --------
     * Convierte el valor remoto de clientes/{idCliente}.estado al enum
     * EstadoCliente. Ante un valor ausente o desconocido devuelve ACTIVO
     * (fail-closed, nunca lanza).
     */
    private fun estadoDe(valor: String?): EstadoCliente {
        if (valor == null) return EstadoCliente.ACTIVO
        return runCatching { EstadoCliente.valueOf(valor) }.getOrDefault(EstadoCliente.ACTIVO)
    }

    /**
     * eliminarSesionConReservasRemoto
     * -------------------------------
     * Elimina de forma ATÓMICA una sesión y TODAS sus reservas dentro de la
     * misma Transaction: lee primero la sesión (detección de conflictos con la
     * reserva concurrente de un CLIENTE) y borra sus reservas + la sesión.
     *
     * Ante un conflicto de transacción se REINTENTA con una consulta FRESCA de
     * reservas (máx. MAX_REINTENTOS_CASCADA reintentos) para capturar reservas
     * creadas entre consultas. La operación es idempotente: si la sesión ya no
     * existe y no conserva reservas, termina con éxito sin hacer nada. Si aún
     * conserva reservas, devuelve error para no ocultar datos huérfanos.
     *
     * Una sesión con más de MAX_RESERVAS_POR_SESION reservas no puede
     * eliminarse atómicamente sin superar el límite de 500 operaciones de la
     * Transaction; en ese caso se devuelve un error claro y NO se elimina nada.
     */
    suspend fun eliminarSesionConReservasRemoto(sesionId: Int): ResultadoAutenticacion {
        val negocioId = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val sesionRef = db.collection(COLECCION_SESIONES).document(sesionId.toString())

        var intentos = 0
        while (true) {
            intentos++

            val documentosReservas = try {
                db.collection(COLECCION_RESERVAS)
                    .whereEqualTo("sesionId", sesionId)
                    .whereEqualTo("negocioId", negocioId)
                    .get()
                    .esperar()
                    .documents
            } catch (e: Exception) {
                return resultadoDeError(
                    "Query de reservas para la sesión $sesionId",
                    e
                )
            }
            val refsReservas = documentosReservas.map { it.reference }
            val clienteIdsReservados = documentosReservas.mapNotNull {
                it.getLong("clienteId")?.toInt()
            }

            if (refsReservas.size > MAX_RESERVAS_POR_SESION) {
                return ResultadoAutenticacion(
                    false,
                    "La sesión tiene demasiadas reservas (${refsReservas.size}) " +
                        "para eliminarse de forma atómica"
                )
            }

            try {
                var fechaDeLaSesion = 0L
                db.runTransaction { transaction ->
                    val sesion = transaction.get(sesionRef)
                    if (!sesion.exists()) {
                        if (refsReservas.isNotEmpty()) {
                            throw SesionInexistenteConReservasException(refsReservas.size)
                        }
                        return@runTransaction
                    }
                    fechaDeLaSesion = sesion.getLong("fecha") ?: 0L
                    refsReservas.forEach { transaction.delete(it) }
                    transaction.delete(sesionRef)
                }.esperar()

                // Tras la transacción crítica: limpiar la AGENDA derivada del día
                // de la sesión en cada cliente reservado (batches ≤400).
                limpiarAgendaDeSesionEliminada(sesionId, fechaDeLaSesion, clienteIdsReservados)

                return ResultadoAutenticacion(true, "Sesión y reservas eliminadas")
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.ABORTED && intentos <= MAX_REINTENTOS_CASCADA) {
                    continue
                }
                return resultadoDeError(
                    "Transaction de eliminación de la sesión $sesionId",
                    e
                )
            } catch (e: SesionInexistenteConReservasException) {
                return ResultadoAutenticacion(false, e.message ?: "La sesión no existe")
            } catch (e: Exception) {
                return resultadoDeError(
                    "Transaction de eliminación de la sesión $sesionId",
                    e
                )
            }
        }
    }

    /**
     * eliminarSesionesFuturasConReservasRemoto
     * -----------------------------------------
     * Elimina las sesiones futuras (fecha >= desde) de un servicio junto con
     * todas sus reservas, de una en una y de forma atómica. Se usa al dar de
     * baja un servicio o regenerar su programación. Las sesiones pasadas se
     * conservan.
     */
    suspend fun eliminarSesionesFuturasConReservasRemoto(
        idServicio: Int,
        desde: Long
    ): ResultadoAutenticacion {
        val negocioId = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            val idsFuturas = obtenerIdsSesionesFuturasDelServicio(
                idServicio,
                desde,
                negocioId
            )
            val resultado = eliminarSesionesConReservas(idsFuturas)
            resultado
        } catch (e: Exception) {
            resultadoDeError(
                "Query de sesiones futuras del servicio $idServicio",
                e
            )
        }
    }

    /**
     * eliminarTodasLasSesionesConReservasRemoto
     * -----------------------------------------
     * Elimina TODAS las sesiones de un servicio junto con sus reservas, de una
     * en una y de forma atómica. Se usa al eliminar un servicio.
     */
    suspend fun eliminarTodasLasSesionesConReservasRemoto(
        idServicio: Int
    ): ResultadoAutenticacion {
        val negocioId = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            val ids = obtenerIdsSesionesDelServicio(idServicio, negocioId)
            eliminarSesionesConReservas(ids)
        } catch (e: Exception) {
            resultadoDeError(
                "Query de sesiones del servicio $idServicio",
                e
            )
        }
    }

    /**
     * eliminarSesionesConReservas
     * ---------------------------
     * Elimina las sesiones indicadas con sus reservas, de una en una. Cada
     * sesión se borra en su propia Transaction (nunca separada de sus reservas)
     * y dentro del límite de 500 operaciones. Si una sesión falla, se detiene
     * y se devuelve ese error para que el reintento exterior converja.
     */
    private suspend fun eliminarSesionesConReservas(sesionIds: List<Int>): ResultadoAutenticacion {
        if (sesionIds.isEmpty()) {
            return ResultadoAutenticacion(true, "No hay sesiones que eliminar")
        }
        for (id in sesionIds) {
            val resultado = eliminarSesionConReservasRemoto(id)
            if (!resultado.exito) return resultado
        }
        return ResultadoAutenticacion(true, "Sesiones y reservas eliminadas")
    }

    /**
     * obtenerIdsSesionesDelServicio
     * -----------------------------
     * Consulta las sesiones de un servicio y su negocio con filtros de igualdad
     * y devuelve sus ids.
     */
    private suspend fun obtenerIdsSesionesDelServicio(
        idServicio: Int,
        negocioId: String
    ): List<Int> {
        val snapshots = db.collection(COLECCION_SESIONES)
            .whereEqualTo("idServicio", idServicio)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
        return snapshots.documents.mapNotNull { it.getLong("idSesion")?.toInt() }
    }

    /**
     * obtenerIdsSesionesFuturasDelServicio
     * ------------------------------------
     * Sesiones de un servicio con fecha >= desde.
     */
    private suspend fun obtenerIdsSesionesFuturasDelServicio(
        idServicio: Int,
        desde: Long,
        negocioId: String
    ): List<Int> {
        val snapshots = db.collection(COLECCION_SESIONES)
            .whereEqualTo("idServicio", idServicio)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
        return snapshots.documents.mapNotNull { documento ->
            val id = documento.getLong("idSesion")?.toInt()
            val fecha = documento.getLong("fecha")
            if (id != null && fecha != null &&
                BajaServicioReglas.esSesionFuturaEnBaja(fecha, desde)
            ) {
                id
            } else {
                null
            }
        }
    }

    /**
     * mensajeDe
     * ---------
     * Traduce los errores típicos de Firestore a mensajes en español.
     */
    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                "No tienes permisos para esta operación"
            else -> e.message ?: "Error inesperado. Inténtalo de nuevo"
        }
    }

    /** Registra el código real de Firestore sin cambiar el mensaje de la UI. */
    private fun resultadoDeError(operacion: String, e: Exception): ResultadoAutenticacion {
        val codigo = (e as? FirebaseFirestoreException)?.code?.name ?: "NO_FIRESTORE_CODE"
        Log.e(TAG, "$operacion falló. códigoFirebase=$codigo", e)
        return ResultadoAutenticacion(false, mensajeDe(e))
    }
}

/**
 * ReservaException
 * ----------------
 * Excepción interna para errores de negocio de reservas (mensajes amigables).
 */
private class ReservaException(message: String) : Exception(message)

private class SesionInexistenteConReservasException(cantidadReservas: Int) :
    Exception("La sesión no existe y conserva $cantidadReservas reserva(s)")
