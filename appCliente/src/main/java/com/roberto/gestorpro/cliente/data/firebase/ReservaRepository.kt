package com.roberto.gestorpro.cliente.data.firebase

import android.content.Context
import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.model.Reserva
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReservaRepository
 * -----------------
 * Gestiona las reservas del CLIENTE. Crear y cancelar se ejecutan en la nube
 * mediante las Cloud Functions callable `reservar` y `cancelarReserva` (FASE 2):
 * la lógica de negocio (plazas, estado ACTIVO, servicio contratado, apertura,
 * permiteCombinarDia y agenda del día) vive en el backend, no en una Transaction
 * del CLIENTE. Este repositorio conserva las LECTURAS locales y traduce los
 * errores de la callable a mensajes localizados.
 *
 * Las funciones `crearReserva`/`cancelarReserva` antiguas (Transaction CLIENTE)
 * se conservan SIN consumidores para la transición hasta confirmar que no son
 * necesarias (ver instrucción FASE 2). NO usarlas en flujos nuevos.
 */
@Singleton
class ReservaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(@StringRes recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    private fun texto(@StringRes recurso: Int, vararg argumentos: Any): String =
        IdiomaAplicacion.textoDe(context, recurso, *argumentos)

    companion object {
        private const val COLECCION_CLIENTES = "clientes"
        private const val COLECCION_SERVICIOS = "servicios"
        private const val COLECCION_SESIONES = "sesiones"
        private const val COLECCION_RESERVAS = "reservas"

        private const val REGION_FUNCIONES = "europe-west1"
        private const val FUNCION_RESERVAR = "reservar"
        private const val FUNCION_CANCELAR = "cancelarReserva"

        /** Identificador remoto determinista de cliente + sesión. */
        fun reservaId(clienteId: Int, sesionId: Int): String =
            "${clienteId}_${sesionId}"

        /**
         * aperturaAlcanzada
         * -----------------
         * Indica si la hora de apertura de reservas ya ha llegado. Comparación
         * en instante absoluto: fecha (epoch millis de la medianoche local del
         * día de la sesión) + offset de horaDesdeReserva frente al instante
         * actual. Si horaDesdeReserva es null, la apertura es el inicio del día.
         * Mismo criterio que el resto del proyecto (ZoneId.systemDefault ya
         * quedó aplicado en el valor de `fecha` generado por el Admin).
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

        /**
         * mensajeSinPlazasSiProcede
         * -------------------------
         * Función PURA: si la sesión ya no tiene plazas (0 o menos) devuelve el
         * mensaje de "no quedan plazas"; en otro caso null (no se debe transformar
         * el error). Permite distinguir la carrera por la última plaza de un
         * PERMISSION_DENIED real sin debilitar ninguna Rule.
         */
        fun mensajeSinPlazasSiProcede(plazasActuales: Int?): String? =
            if (plazasActuales != null && plazasActuales <= 0) {
                "No quedan plazas disponibles."
            } else {
                null
            }
    }

    /**
     * reservarConFuncion
     * ------------------
     * Reserva una sesión invocando la Cloud Function callable `reservar`
     * (europe-west1). La identidad (clienteId/negocioId) la resuelve el backend
     * desde Firebase Auth; aquí solo se envía `sesionId`. Los errores de negocio
     * de la callable se traducen a mensajes localizados.
     */
    suspend fun reservarConFuncion(sesionId: Int): ResultadoAutenticacion =
        invocarCallable(FUNCION_RESERVAR, sesionId, esCancelar = false)

    /**
     * cancelarReservaConFuncion
     * -------------------------
     * Cancela una reserva invocando la Cloud Function callable `cancelarReserva`
     * (europe-west1). Idempotente en el backend (cancelar algo inexistente es un
     * éxito sin efectos).
     */
    suspend fun cancelarReservaConFuncion(sesionId: Int): ResultadoAutenticacion =
        invocarCallable(FUNCION_CANCELAR, sesionId, esCancelar = true)

    private suspend fun invocarCallable(
        nombre: String,
        sesionId: Int,
        esCancelar: Boolean
    ): ResultadoAutenticacion {
        if (auth.currentUser == null) {
            return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )
        }
        return try {
            FirebaseFunctions.getInstance(REGION_FUNCIONES)
                .getHttpsCallable(nombre)
                .call(mapOf("sesionId" to sesionId))
                .esperar()
            ResultadoAutenticacion(true, "")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDeFuncion(e, esCancelar))
        }
    }

    /**
     * mensajeDeFuncion
     * ----------------
     * Traduce un error de una Cloud Function callable a un mensaje localizado.
     * El backend responde con HttpsError (código + mensaje en español); aquí se
     * mapea el contenido del mensaje a los recursos ES/EN de la app para no
     * mostrar texto del servidor sin localizar.
     */
    private fun mensajeDeFuncion(e: Exception, esCancelar: Boolean): String {
        if (e is FirebaseNetworkException) {
            return texto(R.string.vinculacion_error_sin_conexion)
        }
        if (e is FirebaseFunctionsException) {
            val mensaje = e.message ?: ""
            traducirMensajeFuncion(mensaje)?.let { return it }
            return when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    texto(R.string.vinculacion_error_sin_sesion)
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.ABORTED ->
                    texto(R.string.vinculacion_error_sin_conexion)
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    texto(if (esCancelar) R.string.reserva_error_no_cancelar else R.string.reserva_error_no_realizar)
                FirebaseFunctionsException.Code.INTERNAL,
                FirebaseFunctionsException.Code.UNKNOWN ->
                    texto(R.string.auth_error_inesperado)
                else ->
                    texto(
                        if (esCancelar) R.string.reserva_error_no_cancelar
                        else R.string.reserva_error_no_realizar
                    )
            }
        }
        return mensajeDe(e)
    }

    /**
     * traducirMensajeFuncion
     * ----------------------
     * Reconoce los mensajes de negocio que emite el backend (plan_reservas) por
     * su contenido en español y devuelve el mensaje localizado correspondiente.
     * Devuelve null si el mensaje no es reconocible (se usará el fallback).
     */
    private fun traducirMensajeFuncion(mensajeServidor: String): String? {
        val m = mensajeServidor.trim()
        val recurso = when {
            m.contains("dado de baja") -> R.string.reserva_error_dado_de_baja
            m.contains("no está activa") -> R.string.reserva_error_no_activa
            m.contains("La sesión no existe") || m.contains("la sesión no existe") ->
                R.string.reserva_error_sesion_no_existe
            m.contains("La sesión no pertenece") || m.contains("la sesión no pertenece") ->
                R.string.reserva_error_sesion_no_negocio
            m.contains("La sesión no tiene servicio") || m.contains("la sesión no tiene servicio") ->
                R.string.reserva_error_sesion_sin_servicio
            m.contains("La sesión no tiene fecha") || m.contains("la sesión no tiene fecha") ->
                R.string.reserva_error_sesion_sin_fecha
            m.contains("El servicio no existe") || m.contains("el servicio no existe") ->
                R.string.reserva_error_servicio_no_existe
            m.contains("El servicio no pertenece") || m.contains("el servicio no pertenece") ->
                R.string.reserva_error_servicio_no_negocio
            m.contains("El servicio está inactivo") || m.contains("el servicio está inactivo") ->
                R.string.reserva_error_servicio_inactivo
            m.contains("El cliente no existe") || m.contains("el cliente no existe") ->
                R.string.reserva_error_cliente_no_existe
            m.contains("ficha no pertenece") -> R.string.reserva_error_cliente_cuenta
            m.contains("no tiene negocio") || m.contains("cliente no pertenece") ->
                R.string.reserva_error_cliente_no_negocio
            m.contains("No tienes contratado") || m.contains("no tienes contratado") ->
                R.string.reserva_error_no_contratado
            m.contains("no hay plazas") || m.contains("sin plazas") ->
                R.string.reserva_error_sin_plazas
            m.contains("se abren a las") || m.contains("abren a las") ->
                R.string.reserva_error_reservas_abren_a
            m.contains("combinarse") || m.contains("no permite combinar") ->
                R.string.reserva_error_no_combinable
            m.contains("no pertenece a tu cuenta") || m.contains("reserva no pertenece") ->
                R.string.reserva_error_reserva_cuenta
            m.contains("sesión ha cambiado") || m.contains("sesion ha cambiado") ->
                R.string.reserva_error_sesion_cambio
            else -> null
        } ?: return null

        return if (recurso == R.string.reserva_error_reservas_abren_a) {
            val hora = Regex("\\d{1,2}:\\d{2}").find(m)?.value ?: ""
            texto(recurso, hora)
        } else {
            texto(recurso)
        }
    }

    /**
     * Crea una reserva validando cliente, negocio, servicio, autorización,
     * duplicado y plazas antes de escribir. TRANSACTION CLIENTE ANTIGUA.
     * Conservada SIN consumidores para la transición (FASE 2); no usarla en
     * flujos nuevos: usa reservarConFuncion.
     */
    @Deprecated("Sustituida por la Cloud Function callable reservar (reservarConFuncion)")
    suspend fun crearReserva(
        clienteId: Int,
        sesionId: Int,
        negocioId: String
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )

        return try {
            db.runTransaction { transaction ->
                val clienteRef = db.collection(COLECCION_CLIENTES)
                    .document(clienteId.toString())
                val sesionRef = db.collection(COLECCION_SESIONES)
                    .document(sesionId.toString())
                val reservaRef = db.collection(COLECCION_RESERVAS)
                    .document(reservaId(clienteId, sesionId))

                // Todas las lecturas se completan antes de cualquier escritura.
                val cliente = transaction.get(clienteRef)
                val sesion = transaction.get(sesionRef)
                val reserva = transaction.get(reservaRef)
                val idServicio = sesion.getLong("idServicio")?.toInt()
                val servicio = idServicio?.let {
                    transaction.get(
                        db.collection(COLECCION_SERVICIOS).document(it.toString())
                    )
                }

                if (!cliente.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_cliente_no_existe))
                }
                if (cliente.getString("negocioId") != negocioId) {
                    throw ReservaException(texto(R.string.reserva_error_cliente_no_negocio))
                }
                if (cliente.getString("firebaseUid") != uid) {
                    throw ReservaException(texto(R.string.reserva_error_cliente_cuenta))
                }
                // SOLO un cliente ACTIVO puede reservar. BAJA, REGISTRADO u otro
                // estado no activo quedan excluidos. La morosidad es
                // independiente: un ACTIVO con deuda sigue siendo ACTIVO.
                val estadoCliente = cliente.getString("estado")
                if (estadoCliente != "ACTIVO") {
                    val motivo = if (estadoCliente == "BAJA") {
                        texto(R.string.reserva_error_dado_de_baja)
                    } else {
                        texto(R.string.reserva_error_no_activa)
                    }
                    throw ReservaException(motivo)
                }
                if (!sesion.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_sesion_no_existe))
                }
                if (sesion.getString("negocioId") != negocioId) {
                    throw ReservaException(texto(R.string.reserva_error_sesion_no_negocio))
                }
                if (idServicio == null) {
                    throw ReservaException(texto(R.string.reserva_error_sesion_sin_servicio))
                }
                if (servicio == null || !servicio.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_servicio_no_existe))
                }
                if (servicio.getString("negocioId") != negocioId) {
                    throw ReservaException(texto(R.string.reserva_error_servicio_no_negocio))
                }
                if (servicio.getBoolean("activo") != true) {
                    throw ReservaException(texto(R.string.reserva_error_servicio_inactivo))
                }

                val contratados = (cliente.get("serviciosContratados") as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt() }
                    ?: emptyList()
                if (idServicio !in contratados) {
                    throw ReservaException(texto(R.string.reserva_error_no_contratado))
                }
                if (reserva.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_ya_reservada))
                }

                val plazas = sesion.getLong("plazasDisponibles")?.toInt()
                    ?: throw ReservaException(texto(R.string.reserva_error_sesion_sin_plazas))
                if (plazas <= 0) {
                    throw ReservaException(texto(R.string.reserva_error_sin_plazas))
                }

                // La apertura de reservas: antes de horaDesdeReserva no se puede
                // reservar (null = abierta desde el inicio del día).
                val fechaSesion = sesion.getLong("fecha")
                    ?: throw ReservaException(texto(R.string.reserva_error_sesion_sin_fecha))
                val horaDesdeReserva = sesion.getString("horaDesdeReserva")
                if (!aperturaAlcanzada(fechaSesion, horaDesdeReserva)) {
                    throw ReservaException(
                        texto(R.string.reserva_error_reservas_abren_a, horaDesdeReserva ?: "")
                    )
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
                    mapOf("plazasDisponibles" to plazas - 1)
                )
            }.esperar()
            ResultadoAutenticacion(true, "Reserva realizada")
        } catch (e: ReservaException) {
            ResultadoAutenticacion(
                false,
                e.message ?: texto(R.string.reserva_error_no_realizar)
            )
        } catch (e: Exception) {
            // En la carrera por la última plaza, la Transaction puede ser
            // rechazada por las Rules con PERMISSION_DENIED (el decremento ya
            // no encaja porque la plaza se agotó). Ese NO es un problema de
            // permisos: se relee el estado REAL de la sesión para distinguirlo.
            val plazasActuales = plazasDisponiblesActuales(sesionId)
            val sinPlazas = mensajeSinPlazasSiProcede(plazasActuales) != null
            if (e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                sinPlazas
            ) {
                ResultadoAutenticacion(false, texto(R.string.reserva_error_no_quedan_plazas))
            } else {
                ResultadoAutenticacion(false, mensajeDe(e))
            }
        }
    }

    /**
     * plazasDisponiblesActuales
     * -------------------------
     * Lee el valor ACTUAL de `plazasDisponibles` de la sesión en Firestore.
     * Devuelve null si la lectura falla (para no transformar un error de
     * permisos real cuando no se puede comprobar el estado).
     */
    private suspend fun plazasDisponiblesActuales(sesionId: Int): Int? {
        return try {
            db.collection(COLECCION_SESIONES)
                .document(sesionId.toString())
                .get()
                .esperar()
                .getLong("plazasDisponibles")
                ?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    /** Cancela una reserva y devuelve su plaza dentro de una Transaction.
     * TRANSACTION CLIENTE ANTIGUA. Conservada SIN consumidores para la
     * transición (FASE 2); no usarla en flujos nuevos: usa
     * cancelarReservaConFuncion. */
    @Deprecated("Sustituida por la Cloud Function callable cancelarReserva (cancelarReservaConFuncion)")
    suspend fun cancelarReserva(
        clienteId: Int,
        sesionId: Int,
        negocioId: String
    ): ResultadoAutenticacion {
        auth.currentUser
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )

        return try {
            db.runTransaction { transaction ->
                val reservaRef = db.collection(COLECCION_RESERVAS)
                    .document(reservaId(clienteId, sesionId))
                val sesionRef = db.collection(COLECCION_SESIONES)
                    .document(sesionId.toString())

                // Las dos lecturas preceden a la eliminación y actualización.
                val reserva = transaction.get(reservaRef)
                val sesion = transaction.get(sesionRef)

                if (!reserva.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_no_existe))
                }
                if (reserva.getString("negocioId") != negocioId ||
                    reserva.getLong("clienteId")?.toInt() != clienteId
                ) {
                    throw ReservaException(texto(R.string.reserva_error_reserva_cuenta))
                }
                if (!sesion.exists()) {
                    throw ReservaException(texto(R.string.reserva_error_sesion_no_existe))
                }
                if (sesion.getString("negocioId") != negocioId) {
                    throw ReservaException(texto(R.string.reserva_error_sesion_no_negocio))
                }

                // Un cliente que YA tiene reservada la sesión puede cancelarla
                // SIEMPRE, aunque la sesión esté completa (plazas == capacidad o
                // disponibles == 0). La disponibilidad solo limita NUEVAS
                // reservas, nunca la cancelación de una reserva propia.
                val plazas = sesion.getLong("plazasDisponibles")?.toInt()
                    ?: throw ReservaException(texto(R.string.reserva_error_sesion_sin_plazas))

                transaction.delete(reservaRef)
                transaction.update(
                    sesionRef,
                    mapOf("plazasDisponibles" to plazas + 1)
                )
            }.esperar()
            ResultadoAutenticacion(true, "Reserva cancelada")
        } catch (e: ReservaException) {
            ResultadoAutenticacion(
                false,
                e.message ?: texto(R.string.reserva_error_no_cancelar)
            )
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /** Obtiene todas las reservas propias usando los filtros exigidos por Rules. */
    suspend fun obtenerReservasCliente(
        clienteId: Int,
        negocioId: String
    ): List<Reserva> {
        return db.collection(COLECCION_RESERVAS)
            .whereEqualTo("clienteId", clienteId)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
            .documents.mapNotNull { documento ->
                val datos = documento.data ?: return@mapNotNull null
                val sesionId = (datos["sesionId"] as? Number)?.toInt()
                    ?: return@mapNotNull null
                val cliente = (datos["clienteId"] as? Number)?.toInt()
                    ?: return@mapNotNull null
                Reserva(
                    idReserva = datos["idReserva"] as? String ?: documento.id,
                    negocioId = datos["negocioId"] as? String ?: negocioId,
                    sesionId = sesionId,
                    clienteId = cliente,
                    fechaReserva = fechaEnMilisegundos(datos["fechaReserva"]) ?: 0L
                )
            }
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    private fun mensajeDe(e: Exception): String = when (e) {
        is FirebaseNetworkException ->
            texto(R.string.vinculacion_error_sin_conexion)
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                texto(R.string.perfil_error_permisos)
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                texto(R.string.vinculacion_error_sin_conexion)
            FirebaseFirestoreException.Code.ABORTED ->
                texto(R.string.reserva_error_sesion_cambio)
            else -> texto(R.string.auth_error_inesperado)
        }
        else -> texto(R.string.auth_error_inesperado)
    }
}

private class ReservaException(message: String) : Exception(message)
