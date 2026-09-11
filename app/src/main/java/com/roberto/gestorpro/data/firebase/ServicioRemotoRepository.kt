package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.util.HidratacionMapeadores
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ServicioRemotoRepository
 * ------------------------
 * Repositorio que replica los servicios del ADMIN a Firestore en
 * servicios/{idServicio}, con el mismo idServicio de Room.
 *
 * El negocioId remoto es el UID del ADMIN autenticado (convención
 * ADMIN UID == negocioId), aunque en Room el campo negocioId siga vacío.
 *
 * La creación comprueba la existencia previa del documento para no
 * sobrescribir un servicio de otro negocio (colisión de idServicio Int).
 */
@Singleton
class ServicioRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_SERVICIOS = "servicios"
        private const val TAG = "ServicioRemotoRepository"

        /**
         * El negocioId del ADMIN es su propio UID, igual que en los demás
         * repositorios remotos del proyecto.
         */
        fun negocioIdDeAdmin(uid: String): String = uid
    }

    /**
     * obtenerServiciosRemotosDelNegocio
     * ----------------------------------
     * Recupera TODOS los servicios del negocio del ADMIN autenticado desde
     * `servicios/{idServicio}` (query filtrada por negocioId; las reglas de
     * list no funcionan como post-filtro). Solo lectura. Se usa en la
     * hidratación central de la caché local tras un cambio de propietario.
     * NO traga los errores: un fallo de red/permisos se propaga para que el
     * hidratador pueda reintentar más adelante sin marcar la caché como
     * hidratada.
     */
    suspend fun obtenerServiciosRemotosDelNegocio(): List<ServicioEntity> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val negocioId = negocioIdDeAdmin(uid)
        return db.collection(COLECCION_SERVICIOS)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
            .documents
            .mapNotNull { documento ->
                HidratacionMapeadores.servicioDeDocumento(
                    documento.data ?: emptyMap(),
                    negocioId
                )
            }
    }

    /**
     * crearServicioRemoto
     * -------------------
     * Crea servicios/{idServicio} con el negocioId real del ADMIN autenticado.
     * Antes comprueba (con lectura y Transaction) que el id no pertenece ya a
     * otro negocio; si hay colisión, NO sobrescribe y devuelve error.
     */
    suspend fun crearServicioRemoto(servicio: ServicioEntity): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDeAdmin(uid)
        val referencia = db.collection(COLECCION_SERVICIOS)
            .document(servicio.idServicio.toString())

        // 1) Comprobación previa de colisión (lectura directa).
        val existente = try {
            referencia.get().esperar()
        } catch (e: Exception) {
            registrarError("GET de servicios/${servicio.idServicio}", e)
            // Siendo ADMIN, una lectura denegada solo ocurre si el documento ya
            // existe en otro negocio (un doc inexistente o propio sí es legible).
            if (e.message?.contains("permission", ignoreCase = true) == true) {
                return ResultadoAutenticacion(
                    false,
                    "El idServicio ya está en uso por otro centro"
                )
            }
            return ResultadoAutenticacion(false, mensajeDe(e))
        }

        if (existente.exists()) {
            return if (existente.getString("negocioId") == negocioId) {
                ResultadoAutenticacion(true, "Servicio ya sincronizado")
            } else {
                ResultadoAutenticacion(
                    false,
                    "El idServicio ya está en uso por otro centro"
                )
            }
        }

        // 2) Creación atómica con Transaction para evitar carreras entre
        //    creaciones simultáneas de dos negocios con el mismo id.
        return try {
            db.runTransaction { transaction ->
                val enTransaccion = transaction.get(referencia)
                if (enTransaccion.exists()) {
                    if (enTransaccion.getString("negocioId") != negocioId) {
                        throw ColisionServicioException()
                    }
                } else {
                    transaction.set(referencia, mapaDeServicio(servicio, negocioId))
                }
            }.esperar()
            ResultadoAutenticacion(true, "Servicio sincronizado")
        } catch (e: ColisionServicioException) {
            ResultadoAutenticacion(false, "El idServicio ya está en uso por otro centro")
        } catch (e: Exception) {
            registrarError("CREATE de servicios/${servicio.idServicio}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * actualizarServicioRemoto
     * ------------------------
     * Actualiza en Firestore solo los campos editables del servicio
     * (nombre, descripcion, precio, activo, permiteCombinarDia). negocioId e
     * idServicio no cambian.
     */
    suspend fun actualizarServicioRemoto(servicio: ServicioEntity): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            db.collection(COLECCION_SERVICIOS)
                .document(servicio.idServicio.toString())
                .update(
                    mapOf(
                        "nombre" to servicio.nombre,
                        "descripcion" to servicio.descripcion,
                        "precio" to servicio.precio,
                        "activo" to servicio.activo,
                        "permiteCombinarDia" to servicio.permiteCombinarDia
                    )
                )
                .esperar()
            ResultadoAutenticacion(true, "Servicio actualizado")
        } catch (e: Exception) {
            registrarError("UPDATE de servicios/${servicio.idServicio}", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * activarServicioRemoto
     * ---------------------
     * Pone activo = true en Firestore.
     */
    suspend fun activarServicioRemoto(idServicio: Int): ResultadoAutenticacion {
        return actualizarActivoRemoto(idServicio, true)
    }

    /**
     * desactivarServicioRemoto
     * ------------------------
     * Pone activo = false en Firestore. (La cascada de sesiones futuras se
     * implementará en la fase de sesiones.)
     */
    suspend fun desactivarServicioRemoto(idServicio: Int): ResultadoAutenticacion {
        return actualizarActivoRemoto(idServicio, false)
    }

    /**
     * actualizarActivoRemoto
     * ----------------------
     * Actualiza únicamente el campo activo del servicio en Firestore.
     */
    private suspend fun actualizarActivoRemoto(
        idServicio: Int,
        activo: Boolean
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            db.collection(COLECCION_SERVICIOS)
                .document(idServicio.toString())
                .update(mapOf("activo" to activo))
                .esperar()
            ResultadoAutenticacion(true, "Servicio actualizado")
        } catch (e: Exception) {
            registrarError("DELETE de servicios/$idServicio", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * eliminarServicioRemoto
     * ----------------------
     * Elimina servicios/{idServicio}. La cascada de sesiones y reservas se
     * implementará cuando exista el modelo remoto de sesiones.
     */
    suspend fun eliminarServicioRemoto(idServicio: Int): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        return try {
            db.collection(COLECCION_SERVICIOS)
                .document(idServicio.toString())
                .delete()
                .esperar()
            ResultadoAutenticacion(true, "Servicio eliminado")
        } catch (e: Exception) {
            registrarError("UPDATE activo de servicios/$idServicio", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * mapaDeServicio
     * --------------
     * Construye el documento remoto del servicio según el contrato acordado.
     * El negocioId real es el del ADMIN autenticado (su UID).
     */
    private fun mapaDeServicio(servicio: ServicioEntity, negocioId: String): Map<String, Any?> {
        return mapOf(
            "idServicio" to servicio.idServicio,
            "negocioId" to negocioId,
            "nombre" to servicio.nombre,
            "descripcion" to servicio.descripcion,
            "precio" to servicio.precio,
            "activo" to servicio.activo,
            "permiteCombinarDia" to servicio.permiteCombinarDia
        )
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
    private fun registrarError(operacion: String, e: Exception) {
        val codigo = (e as? FirebaseFirestoreException)?.code?.name ?: "NO_FIRESTORE_CODE"
        Log.e(TAG, "$operacion falló. códigoFirebase=$codigo", e)
    }
}

/**
 * ColisionServicioException
 * -------------------------
 * Excepción interna para señalar que el idServicio ya pertenece a otro negocio.
 */
private class ColisionServicioException : Exception()
