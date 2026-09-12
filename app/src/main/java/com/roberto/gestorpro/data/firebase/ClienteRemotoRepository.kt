package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roberto.gestorpro.data.entity.ClienteEntity
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.util.MovimientoFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClienteRemotoRepository
 * -----------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt (data/firebase)
 * Es el repositorio que replica a Firestore las fichas de cliente creadas y
 * gestionadas por el ADMIN en Room (mismo idCliente en ambas bases).
 * Sirve para mantener el espejo remoto que permite la vinculacion de clientes
 * sin duplicidades:
 *   - clientes/{idCliente}        -> ficha publica del cliente (sin observaciones)
 *   - indices_clientes/{negocio}_{dni} -> unicidad y localizacion por negocio+DNI
 *   - clientes_privados/{idCliente}    -> datos exclusivos del ADMIN (observaciones)
 */
@Singleton
class ClienteRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_CLIENTES = "clientes"
        private const val COLECCION_INDICES = "indices_clientes"
        private const val COLECCION_PRIVADOS = "clientes_privados"
        private const val TAG = "ClienteRemotoRepository"

        /**
         * negocioIdDelAdmin
         * -----------------
         * El negocioId del ADMIN es su propio UID, igual que en NegocioRepository.
         */
        fun negocioIdDelAdmin(uid: String): String = uid

        /**
         * indiceId
         * --------
         * Documento de indice negocio+DNI: {negocioId}_{dni}. DNI normalizado
         * en mayusculas para que coincida con el documentId de Firestore.
         */
        fun indiceId(negocioId: String, dni: String): String =
            "${negocioId}_${dni.trim().uppercase()}"
    }

    /**
     * existeClienteRemoto
     * -------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → Boolean
     * Indica si la ficha ya existe en Firestore. Sirve para decidir en la UI
     * si se puede gestionar el enlace o si la ficha no esta sincronizada.
     */
    suspend fun existeClienteRemoto(idCliente: Int): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (uid.isBlank()) return false

        return try {
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .get()
                .esperar()
                .exists()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * fotoRemotaDelCliente
     * --------------------
     * Lee SOLO el campo `foto` de `clientes/{idCliente}` en Firestore. Devuelve
     * null si la ficha no existe o no se puede leer.
     */
    suspend fun fotoRemotaDelCliente(idCliente: Int): String? {
        return try {
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .get()
                .esperar()
                .getString("foto")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * actualizarFotoClienteRemoto
     * ---------------------------
     * Actualiza SOLO `foto` de `clientes/{idCliente}` con la URL de Storage
     * (alta doc-first y reintentos de foto). No toca ningún otro campo.
     */
    suspend fun actualizarFotoClienteRemoto(idCliente: Int, url: String): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .update(mapOf("foto" to url))
                .esperar()
            ResultadoAutenticacion(true, "Foto actualizada")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar la foto del cliente $idCliente", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * obtenerClientesRemotosDelNegocio
     * --------------------------------
     * Recupera TODOS los clientes del negocio del ADMIN autenticado desde
     * Firestore y los convierte a ClienteEntity (para incorporarlos a Room).
     *
     * La consulta filtra por `negocioId` (la regla de list NO funciona como
     * post-filtro). Solo lectura: no modifica Firestore.
     *
     * Además lee las `observaciones` de cada `clientes_privados/{id}` (dato
     * exclusivo del ADMIN) para no perderlas al incorporar la ficha.
     */
    suspend fun obtenerClientesRemotosDelNegocio(): List<ClienteEntity> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        if (uid.isBlank()) return emptyList()

        val negocioId = negocioIdDelAdmin(uid)
        // El error de lista NO se traga: se propaga para que la hidratación
        // central pueda reintentar sin marcar la caché como hidratada.
        val snapshots = db.collection(COLECCION_CLIENTES)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()

        return snapshots.documents.mapNotNull { documento ->
            val datos = documento.data ?: return@mapNotNull null
            val idCliente = documento.id.toIntOrNull()
                ?: (datos["idCliente"] as? Number)?.toInt()
                ?: return@mapNotNull null
            val observaciones = try {
                db.collection(COLECCION_PRIVADOS)
                    .document(documento.id)
                    .get()
                    .esperar()
                    .getString("observaciones")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron leer observaciones de clientes_privados/$idCliente", e)
                null
            }
            entidadDeDocumentoRemoto(idCliente, datos, observaciones)
        }
    }

    /**
     * entidadDeDocumentoRemoto
     * ------------------------
     * Convierte un documento de `clientes/{idCliente}` (más sus observaciones
     * de `clientes_privados`) en una ClienteEntity local. Los campos de
     * morosidad local no se reconstruyen (quedan en false/null); la lógica
     * MovimientoMorosidad los recalculará con los movimientos locales.
     */
    private fun entidadDeDocumentoRemoto(
        idCliente: Int,
        datos: Map<String, Any?>,
        observaciones: String?
    ): ClienteEntity = ClienteEntity(
        idCliente = idCliente,
        nombre = datos["nombre"] as? String ?: "",
        apellidos = datos["apellidos"] as? String ?: "",
        dni = datos["dni"] as? String ?: "",
        telefono = datos["telefono"] as? String ?: "",
        email = datos["email"] as? String,
        foto = datos["foto"] as? String ?: "",
        fechaNacimiento = fechaEnMilisegundos(datos["fechaNacimiento"]),
        fechaRegistro = fechaEnMilisegundos(datos["fechaRegistro"])
            ?: System.currentTimeMillis(),
        fechaAlta = fechaEnMilisegundos(datos["fechaAlta"]),
        fechaBaja = fechaEnMilisegundos(datos["fechaBaja"]),
        estado = estadoDe(datos["estado"]),
        observaciones = observaciones,
        negocioId = datos["negocioId"] as? String,
        serviciosContratados = (datos["serviciosContratados"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?: emptyList(),
        firebaseUid = datos["firebaseUid"] as? String
    )

    private fun estadoDe(valor: Any?): EstadoCliente = when (valor) {
        "ACTIVO" -> EstadoCliente.ACTIVO
        "BAJA" -> EstadoCliente.BAJA
        "ARCHIVADO" -> EstadoCliente.ARCHIVADO
        "REGISTRADO" -> EstadoCliente.REGISTRADO
        else -> EstadoCliente.REGISTRADO
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    /**
     * crearClienteRemoto
     * ------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Replica el alta completa de la ficha con el mismo idCliente de Room.
     * En un unico Batch crea:
     *   - clientes/{idCliente} (firebaseUid = null, sin observaciones);
     *   - indices_clientes/{negocio}_{dni} (unicidad negocio+DNI);
     *   - clientes_privados/{idCliente} (observaciones, solo ADMIN).
     * Las Rules exigen que el indice nazca en el mismo Batch que la ficha.
     */
    suspend fun crearClienteRemoto(entidad: ClienteEntity): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        if (entidad.dni.isBlank()) {
            return ResultadoAutenticacion(false, "La ficha necesita un DNI para crear el índice")
        }

        val negocioId = negocioIdDelAdmin(uid)
        val idIndice = indiceId(negocioId, entidad.dni)

        val mapaClientes = mapaDeAlta(entidad, negocioId)

        return try {
            val batch = db.batch()
            batch.set(
                db.collection(COLECCION_CLIENTES).document(entidad.idCliente.toString()),
                mapaClientes
            )
            batch.set(
                db.collection(COLECCION_INDICES).document(idIndice),
                mapOf(
                    "negocioId" to negocioId,
                    "dni" to entidad.dni.trim().uppercase(),
                    "clienteId" to entidad.idCliente
                )
            )
            batch.set(
                db.collection(COLECCION_PRIVADOS).document(entidad.idCliente.toString()),
                mapOf(
                    "negocioId" to negocioId,
                    "observaciones" to entidad.observaciones
                )
            )
            batch.commit().esperar()
            Log.i(
                TAG,
                "Alta de cliente sincronizada: idCliente=${entidad.idCliente} resultado=OK"
            )
            ResultadoAutenticacion(true, "Ficha sincronizada")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error en alta de cliente: idCliente=${entidad.idCliente} " +
                    "codigo=${e.code} mensaje=${e.message}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error en alta de cliente: idCliente=${entidad.idCliente} mensaje=${e.message}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * actualizarClienteRemoto
     * -----------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Replica la edicion de la ficha. En un unico Batch:
     *   - actualiza clientes/{idCliente} con los campos de gestion;
     *   - actualiza clientes_privados/{idCliente} (observaciones);
     *   - si cambia el DNI, borra el indice viejo y crea el nuevo (atomico).
     * Nunca toca firebaseUid ni identificadores.
     */
    suspend fun actualizarClienteRemoto(entidad: ClienteEntity, dniAnterior: String? = null): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDelAdmin(uid)
        val dniNuevo = entidad.dni.trim().uppercase()
        val dniViejo = dniAnterior?.trim()?.uppercase()

        return try {
            val batch = db.batch()
            batch.update(
                db.collection(COLECCION_CLIENTES).document(entidad.idCliente.toString()),
                mapaDeEdicion(entidad)
            )
            // set (no update): funciona tanto si clientes_privados/{id} ya
            // existe como si NO (clientes VIA 2 sin privados). Con el doc
            // existente la regla de update permite escribir `observaciones`;
            // si no existe, la regla de create valida {negocioId, observaciones}.
            batch.set(
                db.collection(COLECCION_PRIVADOS).document(entidad.idCliente.toString()),
                mapOf(
                    "negocioId" to negocioId,
                    "observaciones" to entidad.observaciones
                )
            )
            if (dniViejo != null && dniViejo != dniNuevo) {
                batch.delete(
                    db.collection(COLECCION_INDICES).document(indiceId(negocioId, dniViejo))
                )
            }
            if (dniViejo == null || dniViejo != dniNuevo) {
                batch.set(
                    db.collection(COLECCION_INDICES).document(indiceId(negocioId, dniNuevo)),
                    mapOf(
                        "negocioId" to negocioId,
                        "dni" to dniNuevo,
                        "clienteId" to entidad.idCliente
                    )
                )
            }
            batch.commit().esperar()
            Log.i(
                TAG,
                "Edicion de cliente sincronizada: idCliente=${entidad.idCliente} resultado=OK"
            )
            ResultadoAutenticacion(true, "Cambios sincronizados")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error en edicion de cliente: idCliente=${entidad.idCliente} codigo=${e.code}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error en edicion de cliente: idCliente=${entidad.idCliente}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * actualizarServiciosContratadosRemoto
     * -------------------------------------
     * Replica SOLO la lista de servicios contratados de la ficha
     * (clientes/{idCliente}.serviciosContratados) con un update de un único
     * campo. No toca el resto de campos ni el indice negocio+DNI, por lo que
     * las Rules de indices_clientes (update: false) no se ven afectadas.
     */
    suspend fun actualizarServiciosContratadosRemoto(
        idCliente: Int,
        idsServicios: List<Int>
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        if (uid.isBlank()) {
            return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        }

        return try {
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .update("serviciosContratados", idsServicios)
                .esperar()
            Log.i(
                TAG,
                "Servicios de cliente sincronizados: idCliente=$idCliente resultado=OK"
            )
            ResultadoAutenticacion(true, "Servicios sincronizados")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error en servicios de cliente: idCliente=$idCliente codigo=${e.code}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error en servicios de cliente: idCliente=$idCliente", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * actualizarResumenEconomicoRemoto
     * --------------------------------
     * Publica el RESUMEN ECONÓMICO completo del cliente en su ficha pública
     * `clientes/{idCliente}` con un `update()` (merge): moroso, deuda,
     * fechaEntradaMorosidad, fechaInicioActual, fechaFinActual y exentoMorosidad.
     */
    suspend fun actualizarResumenEconomicoRemoto(
        idCliente: Int,
        moroso: Boolean,
        fechaEntradaMorosidad: Long?,
        deuda: Double,
        fechaInicioActual: Long?,
        fechaFinActual: Long?,
        exentoMorosidad: Boolean
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        if (uid.isBlank()) {
            return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        }

        return try {
            val resumen = MovimientoFirestore.resumenDeCliente(
                moroso = moroso,
                fechaEntradaMorosidad = fechaEntradaMorosidad,
                deuda = deuda,
                fechaInicioActual = fechaInicioActual,
                fechaFinActual = fechaFinActual,
                exentoMorosidad = exentoMorosidad
            )
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .update(resumen)
                .esperar()
            Log.i(
                TAG,
                "Resumen económico sincronizado: idCliente=$idCliente " +
                    "moroso=$moroso deuda=$deuda exentoMorosidad=$exentoMorosidad resultado=OK"
            )
            ResultadoAutenticacion(true, "Resumen económico sincronizado")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error en resumen económico: idCliente=$idCliente codigo=${e.code}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error en resumen económico: idCliente=$idCliente", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * mapaDeAlta
     * ----------
     * ✔ TIPO: método (fun) privado de Kotlin → Map<String, Any?>
     * Construye el documento publico del alta segun el contrato acordado:
     * estados con el nombre exacto del enum Room y fechas como Timestamp.
     * observaciones NO va aqui (es dato privado del ADMIN).
     */
    private fun mapaDeAlta(entidad: ClienteEntity, negocioId: String): Map<String, Any?> {
        return mapOf(
            "idCliente" to entidad.idCliente,
            "negocioId" to negocioId,
            "firebaseUid" to null,
            "nombre" to entidad.nombre,
            "apellidos" to entidad.apellidos,
            "dni" to entidad.dni.trim().uppercase(),
            "telefono" to entidad.telefono,
            "email" to entidad.email,
            "foto" to entidad.foto,
            "fechaNacimiento" to entidad.fechaNacimiento?.let { timestampDe(it) },
            "fechaRegistro" to timestampDe(entidad.fechaRegistro),
            "fechaAlta" to entidad.fechaAlta?.let { timestampDe(it) },
            "fechaBaja" to entidad.fechaBaja?.let { timestampDe(it) },
            "estado" to entidad.estado.name,
            "serviciosContratados" to entidad.serviciosContratados,
            "fechaInicioActual" to null,
            "fechaFinActual" to null
        )
    }

    /**
     * mapaDeEdicion
     * -------------
     * ✔ TIPO: método (fun) privado de Kotlin → Map<String, Any?>
     * Construye el update de edicion limitado a los campos de gestion
     * autorizados por las Security Rules.
     */
    private fun mapaDeEdicion(entidad: ClienteEntity): Map<String, Any?> {
        return mapOf(
            "nombre" to entidad.nombre,
            "apellidos" to entidad.apellidos,
            "dni" to entidad.dni.trim().uppercase(),
            "telefono" to entidad.telefono,
            "email" to entidad.email,
            "foto" to entidad.foto,
            "fechaNacimiento" to entidad.fechaNacimiento?.let { timestampDe(it) },
            "serviciosContratados" to entidad.serviciosContratados,
            "estado" to entidad.estado.name,
            "fechaAlta" to entidad.fechaAlta?.let { timestampDe(it) },
            "fechaBaja" to entidad.fechaBaja?.let { timestampDe(it) }
        )
    }

    /**
     * timestampDe
     * -----------
     * ✔ TIPO: método (fun) privado de Kotlin → Timestamp
     * Convierte milisegundos de Room en Timestamp de Firestore.
     */
    private fun timestampDe(millis: Long): Timestamp = Timestamp(java.util.Date(millis))

    /**
     * mensajeDe
     * ---------
     * ✔ TIPO: método (fun) privado de Kotlin → String
     * Traduce los errores típicos de Firestore a mensajes en español.
     */
    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                "No tienes permisos para sincronizar esta ficha"
            else -> e.message ?: "Error inesperado durante la sincronización"
        }
    }
}
