package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roberto.gestorpro.model.ConfiguracionNotificaciones
import com.roberto.gestorpro.model.DestinatarioResuelto
import com.roberto.gestorpro.model.NotificacionAdmin
import com.roberto.gestorpro.model.ResolucionDestinatarios
import com.roberto.gestorpro.util.RetiradaNotificacionReglas
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LecturaNotificacion
 * -------------------
 * Resumen de lectura de una notificación calculado desde su buzón
 * (notificaciones_por_destinatario): número de destinatarios y de leídas.
 * Independiente del `estado` (envío) del registro global `notificaciones/{id}`.
 */
data class LecturaNotificacion(
    val leidas: Int,
    val total: Int
)

/**
 * NotificacionRemotoRepository
 * ----------------------------
 * Repositorio remoto de las notificaciones del ADMIN.
 *
 * Fase D: crea el registro global `notificaciones/{id}`, resuelve los
 * destinatarios desde Firestore (nunca confía en Room) y crea los buzones
 * `notificaciones_por_destinatario/{clienteId}_{notificacionId}` para los
 * clientes vinculados (firebaseUid válido). También gestiona la configuración
 * de notificaciones preconfiguradas `configuracion_notificaciones/{negocioId}`.
 *
 * El envío FCM real (Cloud Functions) es Fase E: aquí solo se preparan los
 * documentos que el backend consumirá posteriormente.
 */
@Singleton
class NotificacionRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_NOTIFICACIONES = "notificaciones"
        private const val COLECCION_BUZON = "notificaciones_por_destinatario"
        private const val COLECCION_CONFIG = "configuracion_notificaciones"
        private const val COLECCION_CLIENTES = "clientes"
        private const val MAX_ESCRITURAS_POR_BATCH = 500
        private const val TAG = "NotificacionRemotoRepository"

        const val TIPO_MANUAL = "MANUAL"
        const val TIPO_PROGRAMADA = "PROGRAMADA"
        const val TIPO_CAMBIO_HORARIO = "CAMBIO_HORARIO"
        const val ORIGEN_MANUAL = "MANUAL"
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_ENVIADA = "ENVIADA"
        const val ESTADO_PROGRAMADA = "PROGRAMADA"
        const val ESTADO_CANCELADA = "CANCELADA"
        const val ESTADO_ERROR = "ERROR"

        /**
         * configuracionPorDefecto
         * -----------------------
         * Configuración aplicada cuando el documento de configuración todavía
         * no existe: la notificación de BAJA CONFIRMADA está ACTIVA por defecto
         * (el CLIENTE debe recibir su aviso de baja), mientras que el resto de
         * preconfiguradas siguen desactivadas.
         */
        fun configuracionPorDefecto(): ConfiguracionNotificaciones =
            ConfiguracionNotificaciones(
                morosidadActiva = false,
                recordatorioHoras = 0,
                bajaConfirmadaActiva = true,
                cambioHorarioActiva = false
            )

        /**
         * bajaConfirmadaActivaPorDefecto
         * ------------------------------
         * Regla de negocio: la notificación de baja confirmada está activa por
         * defecto. null (sin configuración o campo ausente) se trata como
         * activa; solo un false explícito guardado por el ADMIN la desactiva.
         */
        fun bajaConfirmadaActivaPorDefecto(valor: Boolean?): Boolean = valor != false

        /**
         * idDeBuzon
         * ---------
         * DocumentId determinista de un buzón de la colección
         * `notificaciones_por_destinatario`: `{clienteId}_{notificacionId}`.
         * Lo exigen las Rules y coincide con el helper de Cloud Functions
         * (`functions/lib/ids.js:idBuzon`).
         */
        fun idDeBuzon(clienteId: Int, notificacionId: String): String =
            "${clienteId}_$notificacionId"
    }

    /**
     * negocioIdActual
     * ---------------
     * Devuelve el negocioId del ADMIN autenticado (su propio UID) o null si
     * no hay sesión válida.
     */
    fun negocioIdActual(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    /**
     * obtenerNotificaciones
     * ---------------------
     * Consulta `notificaciones` filtrando por negocioId (la query exige el
     * filtro porque la regla de list no funciona como post-filtro) y ordena
     * en memoria de más reciente a más antigua usando fechaEnvio y, si no
     * existe (programadas no enviadas), fechaProgramada o fechaCreacion.
     */
    suspend fun obtenerNotificaciones(negocioId: String): List<NotificacionAdmin> {
        val snapshots = db.collection(COLECCION_NOTIFICACIONES)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()

        return snapshots.documents
            .mapNotNull { documento ->
                val datos = documento.data ?: return@mapNotNull null
                val titulo = datos["titulo"] as? String ?: return@mapNotNull null
                val mensaje = datos["mensaje"] as? String ?: return@mapNotNull null
                val fechaCreacion = fechaEnMilisegundos(datos["fechaCreacion"])
                    ?: return@mapNotNull null
                NotificacionAdmin(
                    id = documento.id,
                    titulo = titulo,
                    mensaje = mensaje,
                    tipo = datos["tipo"] as? String ?: TIPO_MANUAL,
                    origen = datos["origen"] as? String ?: ORIGEN_MANUAL,
                    modoDestino = datos["modoDestino"] as? String ?: "TODOS",
                    idsClientes = (datos["idsClientes"] as? List<*>)
                        ?.mapNotNull { enteroDe(it) } ?: emptyList(),
                    clienteId = enteroDe(datos["clienteId"]),
                    fechaCreacion = fechaCreacion,
                    fechaEnvio = fechaEnMilisegundos(datos["fechaEnvio"]),
                    programada = (datos["programada"] as? Boolean) ?: false,
                    fechaProgramada = fechaEnMilisegundos(datos["fechaProgramada"]),
                    estado = datos["estado"] as? String ?: ESTADO_PENDIENTE
                )
            }
            .sortedByDescending { it.fechaEnvio ?: it.fechaProgramada ?: it.fechaCreacion }
    }

    /**
     * obtenerLecturaBuzones
     * ---------------------
     * Consulta UNA sola vez todos los buzones `notificaciones_por_destinatario`
     * del negocio (filtro `negocioId`, exigido por las Rules) y los agrupa por
     * `notificacionId`. Devuelve para cada notificación el total de buzones
     * (destinatarios reales) y cuántos están leídos. La lectura es independiente
     * del estado de envío del registro global. Sin consultas por notificación.
     */
    suspend fun obtenerLecturaBuzones(negocioId: String): Map<String, LecturaNotificacion> {
        val snapshots = db.collection(COLECCION_BUZON)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()

        val acumulado = mutableMapOf<String, MutableList<Boolean>>()
        snapshots.documents.forEach { documento ->
            val datos = documento.data ?: return@forEach
            val notificacionId = datos["notificacionId"] as? String ?: return@forEach
            val leida = (datos["leida"] as? Boolean) ?: false
            acumulado.getOrPut(notificacionId) { mutableListOf() }.add(leida)
        }
        return acumulado.mapValues { (_, leidas) ->
            LecturaNotificacion(
                leidas = leidas.count { it },
                total = leidas.size
            )
        }
    }

    /**
     * resolverDestinatarios
     * ---------------------
     * Comprueba qué clientes del objetivo tienen firebaseUid válido en
     * Firestore (pueden recibir buzón) y cuáles quedan fuera por no estar
     * vinculados. Usado por la UI para mostrar "Se enviará a X de Y clientes
     * vinculados" antes de confirmar.
     */
    suspend fun resolverDestinatarios(
        negocioId: String,
        modoDestino: String,
        idsSeleccionados: List<Int>
    ): ResolucionDestinatarios {
        val clientes = obtenerClientesDelNegocio(negocioId)
        val idsObjetivo = when (modoDestino) {
            "INDIVIDUAL", "GRUPO" -> idsSeleccionados.toSet()
            else -> null
        }
        val objetivo = if (idsObjetivo == null) {
            clientes
        } else {
            clientes.filter { it.first in idsObjetivo }
        }
        val destinatarios = objetivo.mapNotNull { (idCliente, firebaseUid) ->
            firebaseUid?.let { DestinatarioResuelto(idCliente, it) }
        }
        return ResolucionDestinatarios(
            destinatarios = destinatarios,
            omitidos = objetivo.size - destinatarios.size,
            totalObjetivo = objetivo.size
        )
    }

    /**
     * crearNotificacion
     * -----------------
     * Prepara los documentos de una notificación del ADMIN.
     *
     *  - Inmediata (programada = false):
     *      notificaciones/{id} en PENDIENTE + buzones de los destinatarios
     *      (lotes de <= 500 escrituras). El estado final (ENVIADA/ERROR) y el
     *      push FCM real los resuelve la Cloud Function (Fase E): la app NO
     *      marca ENVIADA ni asume que el push se ha enviado.
     *  - Programada (programada = true):
     *      notificaciones/{id} en PROGRAMADA con fechaProgramada. NO crea
     *      buzones: los generará Cloud Functions cuando llegue la fecha.
     *
     * `destinatarios` deben ser los ya resueltos (firebaseUid válido) para
     * una notificación inmediata; para programadas puede ir vacía y se usan
     * `idsObjetivo` como destinatarios previstos.
     */
    suspend fun crearNotificacion(
        negocioId: String,
        titulo: String,
        mensaje: String,
        modoDestino: String,
        clienteId: Int?,
        destinatarios: List<DestinatarioResuelto>,
        idsObjetivo: List<Int>,
        programada: Boolean,
        fechaProgramada: Long?,
        tipo: String = TIPO_MANUAL,
        origen: String = ORIGEN_MANUAL,
        notificacionId: String? = null
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val idNotificacion = notificacionId ?: generarIdNotificacion()
        val tipoFinal = if (programada && tipo == TIPO_MANUAL) TIPO_PROGRAMADA else tipo
        val idsParaRegistro = if (programada) {
            idsObjetivo.distinct()
        } else {
            destinatarios.map { it.idCliente }.distinct()
        }

        val docPrincipal = mapaDeNotificacion(
            negocioId = negocioId,
            titulo = titulo,
            mensaje = mensaje,
            tipo = tipoFinal,
            origen = origen,
            modoDestino = modoDestino,
            clienteId = clienteId,
            idsClientes = idsParaRegistro,
            programada = programada,
            fechaProgramada = fechaProgramada,
            estado = if (programada) ESTADO_PROGRAMADA else ESTADO_PENDIENTE
        )

        return try {
            db.collection(COLECCION_NOTIFICACIONES)
                .document(idNotificacion)
                .set(docPrincipal)
                .esperar()

            if (!programada) {
                if (destinatarios.isEmpty()) {
                    // Sin destinatarios vinculados: no se puede enviar.
                    // Se deja el registro en ERROR para no arrastrar un PENDIENTE eterno.
                    db.collection(COLECCION_NOTIFICACIONES)
                        .document(idNotificacion)
                        .update("estado", ESTADO_ERROR)
                        .esperar()
                    return ResultadoAutenticacion(
                        false,
                        "No hay clientes vinculados para recibir la notificación"
                    )
                }
                crearBuzones(
                    negocioId = negocioId,
                    notificacionId = idNotificacion,
                    titulo = titulo,
                    mensaje = mensaje,
                    tipo = tipoFinal,
                    origen = origen,
                    destinatarios = destinatarios
                )
                // NOTA (Fase E): el envío inmediato se deja en PENDIENTE a
                // propósito. La Cloud Function onDocumentCreated reclamará el
                // documento (PENDIENTE -> ENVIADA) y hará el push FCM real.
                // La app NO asume que el push se ha enviado correctamente.
            }

            Log.i(
                TAG,
                "Notificación creada: $idNotificacion " +
                    "programada=$programada destinatarios=${destinatarios.size}"
            )
            ResultadoAutenticacion(
                true,
                if (programada) "Notificación programada" else "Notificación creada"
            )
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Error creando notificación $idNotificacion código=${e.code}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando notificación $idNotificacion", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * existeNotificacionFinalizada
     * ----------------------------
     * Indica si ya existe un documento de notificación con ese id y su estado
     * ya no es PENDIENTE (p. ej. ya ENVIADA por Cloud Functions). Sirve para
     * no sobrescribir una notificación automática que el backend ya procesó.
     */
    suspend fun existeNotificacionFinalizada(notificacionId: String): Boolean {
        return try {
            val documento = db.collection(COLECCION_NOTIFICACIONES)
                .document(notificacionId)
                .get()
                .esperar()
            documento.exists() && documento.getString("estado") != ESTADO_PENDIENTE
        } catch (e: Exception) {
            false
        }
    }

    /**
     * existeNotificacion
     * ------------------
     * Indica si ya existe un documento de notificación con ese id, con
     * independencia de su estado. Sirve para no duplicar avisos idempotentes
     * como el de SOLICITUD_BAJA.
     */
    suspend fun existeNotificacion(notificacionId: String): Boolean {
        return try {
            db.collection(COLECCION_NOTIFICACIONES)
                .document(notificacionId)
                .get()
                .esperar()
                .exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * crearNotificacionSolicitudBaja
     * ------------------------------
     * Crea en notificaciones/{id} el aviso "Solicitud de baja" que ve el ADMIN
     * en su bandeja (la colección notificaciones). La crea el ADMIN (la app
     * ADMIN, al cargar sus solicitudes) porque es el único escritor permitido
     * por las Rules. Usa un ID determinista (solicitud_baja_{clienteId}_{fecha})
     * e idempotente: si el documento ya existe, no hace nada.
     */
    suspend fun crearNotificacionSolicitudBaja(
        negocioId: String,
        clienteId: Int,
        nombreCliente: String,
        fechaSolicitud: Long
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val idNotificacion = "solicitud_baja_${clienteId}_$fechaSolicitud"
        return try {
            if (existeNotificacion(idNotificacion)) {
                return ResultadoAutenticacion(true, "Aviso de solicitud de baja ya creado")
            }
            db.collection(COLECCION_NOTIFICACIONES)
                .document(idNotificacion)
                .set(
                    mapOf(
                        "negocioId" to negocioId,
                        "titulo" to "Solicitud de baja",
                        "mensaje" to "$nombreCliente ha solicitado la baja.",
                        "tipo" to "SOLICITUD_BAJA",
                        "origen" to "AUTOMATICA",
                        "modoDestino" to "INDIVIDUAL",
                        "clienteId" to clienteId,
                        "fechaCreacion" to Timestamp(java.util.Date(fechaSolicitud)),
                        "programada" to false,
                        "estado" to ESTADO_PENDIENTE
                    )
                )
                .esperar()
            Log.i(TAG, "Aviso SOLICITUD_BAJA creado: $idNotificacion")
            ResultadoAutenticacion(true, "Aviso de solicitud de baja creado")
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Error creando aviso SOLICITUD_BAJA $idNotificacion código=${e.code}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando aviso SOLICITUD_BAJA $idNotificacion", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * cancelarNotificacion
     * --------------------
     * Pone el estado de una notificación programada a CANCELADA. Las Rules
     * solo permiten cambiar estado/fechaEnvio/idsClientes, por lo que el
     * contenido nunca se puede editar.
     */
    suspend fun cancelarNotificacion(notificacionId: String): ResultadoAutenticacion {
        return try {
            db.collection(COLECCION_NOTIFICACIONES)
                .document(notificacionId)
                .update("estado", ESTADO_CANCELADA)
                .esperar()
            ResultadoAutenticacion(true, "Notificación cancelada")
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Error cancelando notificación $notificacionId código=${e.code}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando notificación $notificacionId", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * retirarNotificacionManual
     * -------------------------
     * Moderación UGC (FASE 2C-3): retira una notificación MANUAL ya publicada
     * (o en entrega). Elimina el registro `notificaciones/{id}` y los buzones
     * derivados `notificaciones_por_destinatario/{clienteId}_{notificacionId}`
     * que se crearon para los clientes de `idsClientes`.
     *
     * Solo actúa sobre notificaciones manuales ya publicadas (origen MANUAL y
     * estado PENDIENTE/ENVIADA, ver [RetiradaNotificacionReglas]). Las
     * automáticas/preconfiguradas nunca se retiran con esta acción y las
     * programadas aún no enviadas siguen gestionándose con la cancelación
     * existente. Es idempotente: si el registro ya no existe devuelve éxito y
     * si algún buzón ya no existe su borrado es un no-op.
     *
     * No borra las denuncias asociadas: una denuncia puede seguir existiendo
     * aunque la notificación denunciada haya sido retirada (el ADMIN la
     * revisa y la marca como revisada).
     */
    suspend fun retirarNotificacionManual(notificacionId: String): ResultadoAutenticacion {
        return try {
            val documento = db.collection(COLECCION_NOTIFICACIONES)
                .document(notificacionId)
                .get()
                .esperar()
            if (!documento.exists()) {
                // Idempotente: el registro ya no existe, no hay nada que retirar.
                return ResultadoAutenticacion(true, "La notificación ya no existe")
            }
            val origen = documento.getString("origen")
            val estado = documento.getString("estado")
            if (!RetiradaNotificacionReglas.esRetirable(origen, estado)) {
                return ResultadoAutenticacion(
                    false,
                    "Solo se pueden retirar notificaciones manuales ya enviadas"
                )
            }
            val idsClientes = (documento.get("idsClientes") as? List<*>)
                ?.mapNotNull { enteroDe(it) }
                ?.distinct()
                ?: emptyList()

            // Elimina el registro principal junto con el primer lote de
            // buzones (<= 499) y el resto en lotes siguientes, respetando el
            // límite de 500 escrituras por WriteBatch. Borrar buzones
            // inexistentes es un no-op (idempotente).
            val lotes = idsClientes.chunked(MAX_ESCRITURAS_POR_BATCH - 1)
            if (lotes.isEmpty()) {
                db.collection(COLECCION_NOTIFICACIONES)
                    .document(notificacionId)
                    .delete()
                    .esperar()
            } else {
                lotes.forEachIndexed { indice, lote ->
                    val batch = db.batch()
                    if (indice == 0) {
                        batch.delete(
                            db.collection(COLECCION_NOTIFICACIONES)
                                .document(notificacionId)
                        )
                    }
                    lote.forEach { clienteId ->
                        batch.delete(
                            db.collection(COLECCION_BUZON)
                                .document(idDeBuzon(clienteId, notificacionId))
                        )
                    }
                    batch.commit().esperar()
                }
            }

            Log.i(TAG, "Notificación manual retirada: $notificacionId")
            ResultadoAutenticacion(true, "Notificación retirada")
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Error retirando notificación $notificacionId código=${e.code}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error retirando notificación $notificacionId", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * obtenerConfiguracion
     * --------------------
     * Lee configuracion_notificaciones/{negocioId}. Si el documento no existe
     * devuelve la configuración POR DEFECTO (bajaConfirmada.activa = true), de
     * modo que el CLIENTE reciba su aviso de baja sin necesidad de configuración
     * previa. Devuelve null solo ante un error de lectura.
     */
    suspend fun obtenerConfiguracion(negocioId: String): ConfiguracionNotificaciones? {
        return try {
            val documento = db.collection(COLECCION_CONFIG)
                .document(negocioId)
                .get()
                .esperar()
            if (!documento.exists()) return configuracionPorDefecto()
            val morosidad = documento.get("morosidad") as? Map<*, *>
            val bajaConfirmada = documento.get("bajaConfirmada") as? Map<*, *>
            val cambioHorario = documento.get("cambioHorario") as? Map<*, *>
            ConfiguracionNotificaciones(
                morosidadActiva = (morosidad?.get("activa") as? Boolean) ?: false,
                recordatorioHoras = enteroDe(morosidad?.get("recordatorioHoras")) ?: 0,
                bajaConfirmadaActiva = bajaConfirmadaActivaPorDefecto(
                    bajaConfirmada?.get("activa") as? Boolean
                ),
                cambioHorarioActiva = (cambioHorario?.get("activa") as? Boolean) ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo configuración de notificaciones", e)
            null
        }
    }

    /**
     * guardarConfiguracion
     * --------------------
     * Crea o actualiza configuracion_notificaciones/{negocioId}. Para el
     * recordatorio de morosidad: 0 = desactivado, 24 = activado (cada 24h).
     */
    suspend fun guardarConfiguracion(
        negocioId: String,
        config: ConfiguracionNotificaciones
    ): ResultadoAutenticacion {
        return try {
            val datos = mapOf(
                "morosidad" to mapOf(
                    "activa" to config.morosidadActiva,
                    "recordatorioHoras" to config.recordatorioHoras
                ),
                "bajaConfirmada" to mapOf(
                    "activa" to config.bajaConfirmadaActiva
                ),
                "cambioHorario" to mapOf(
                    "activa" to config.cambioHorarioActiva
                )
            )
            val referencia = db.collection(COLECCION_CONFIG).document(negocioId)
            val existente = referencia.get().esperar()
            if (existente.exists()) {
                referencia.update(datos).esperar()
            } else {
                referencia.set(mapOf("negocioId" to negocioId) + datos).esperar()
            }
            ResultadoAutenticacion(true, "Configuración guardada")
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Error guardando configuración de notificaciones código=${e.code}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando configuración de notificaciones", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    // ------------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------------

    /**
     * crearBuzones
     * ------------
     * Crea notificaciones_por_destinatario/{clienteId}_{notificacionId} para
     * cada destinatario respetando el límite de 500 escrituras por WriteBatch
     * (chunking). El documentId determinista lo exigen las Rules.
     */
    private suspend fun crearBuzones(
        negocioId: String,
        notificacionId: String,
        titulo: String,
        mensaje: String,
        tipo: String,
        origen: String,
        destinatarios: List<DestinatarioResuelto>
    ) {
        destinatarios.chunked(MAX_ESCRITURAS_POR_BATCH).forEach { lote ->
            val batch = db.batch()
            lote.forEach { destino ->
                batch.set(
                    db.collection(COLECCION_BUZON)
                        .document(idDeBuzon(destino.idCliente, notificacionId)),
                    mapOf(
                        "negocioId" to negocioId,
                        "notificacionId" to notificacionId,
                        "clienteId" to destino.idCliente,
                        "firebaseUid" to destino.firebaseUid,
                        "titulo" to titulo,
                        "mensaje" to mensaje,
                        "tipo" to tipo,
                        "origen" to origen,
                        "fechaEnvio" to Timestamp.now(),
                        "leida" to false
                    )
                )
            }
            batch.commit().esperar()
        }
    }

    /**
     * obtenerClientesDelNegocio
     * -------------------------
     * Consulta `clientes` del negocio (query con negocioId, requerida por la
     * regla de list) y devuelve (idCliente, firebaseUid). La fuente de verdad
     * del vínculo es Firestore, no Room.
     */
    private suspend fun obtenerClientesDelNegocio(negocioId: String): List<Pair<Int, String?>> {
        val snapshots = db.collection(COLECCION_CLIENTES)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
        return snapshots.documents.mapNotNull { documento ->
            val idCliente = enteroDe(documento.get("idCliente")) ?: return@mapNotNull null
            idCliente to documento.getString("firebaseUid")
        }
    }

    /**
     * mapaDeNotificacion
     * ------------------
     * Construye el documento de notificaciones/{id} con exactamente las
     * claves permitidas por el hasOnly de las Rules.
     */
    private fun mapaDeNotificacion(
        negocioId: String,
        titulo: String,
        mensaje: String,
        tipo: String,
        origen: String,
        modoDestino: String,
        clienteId: Int?,
        idsClientes: List<Int>,
        programada: Boolean,
        fechaProgramada: Long?,
        estado: String
    ): Map<String, Any?> {
        val mapa = mutableMapOf<String, Any?>(
            "negocioId" to negocioId,
            "titulo" to titulo,
            "mensaje" to mensaje,
            "tipo" to tipo,
            "origen" to origen,
            "modoDestino" to modoDestino,
            "idsClientes" to idsClientes,
            "fechaCreacion" to Timestamp.now(),
            "programada" to programada,
            "estado" to estado
        )
        if (clienteId != null) {
            mapa["clienteId"] = clienteId
        }
        if (fechaProgramada != null) {
            mapa["fechaProgramada"] = Timestamp(java.util.Date(fechaProgramada))
        }
        return mapa
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    private fun enteroDe(valor: Any?): Int? = when (valor) {
        is Int -> valor
        is Long -> valor.toInt()
        is Number -> valor.toInt()
        else -> null
    }

    private fun generarIdNotificacion(): String =
        "n_${System.currentTimeMillis()}_${(1000..9999).random()}"

    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                "No tienes permisos para gestionar notificaciones"
            else -> e.message ?: "Error inesperado al gestionar la notificación"
        }
    }
}
