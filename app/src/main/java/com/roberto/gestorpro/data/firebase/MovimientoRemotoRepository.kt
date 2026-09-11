package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.util.HidratacionMapeadores
import com.roberto.gestorpro.util.MovimientoFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MovimientoRemotoRepository
 * --------------------------
 * Repositorio que replica los movimientos económicos del ADMIN a Firestore en
 * `movimientos/{idMovimiento}`, con el mismo idMovimiento de Room (documentId
 * DETERMINISTA: `idMovimiento.toString()`, nunca IDs aleatorios).
 *
 * Room es la fuente de verdad del ADMIN; Firestore es el ESPEJO de la economía
 * para futuras necesidades (appCliente, Functions, notificaciones). El CLIENTE
 * no tiene permisos de escritura sobre esta colección.
 *
 * El `negocioId` remoto es el UID del ADMIN autenticado (convención del proyecto:
 * negocioId del ADMIN = su UID), igual que en los demás repositorios remotos.
 *
 * La escritura es IDEMPOTENTE: `set()` sobre `movimientos/{idMovimiento}`.
 * Antes de sobrescribir se comprueba que el documento (si existe) pertenece al
 * mismo negocio para no pisar movimientos de otro negocio con el mismo id.
 */
@Singleton
class MovimientoRemotoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_MOVIMIENTOS = "movimientos"
        private const val TAG = "MovimientoRemotoRepository"
    }

    /**
     * negocioIdDelAdmin
     * -----------------
     * El negocioId del ADMIN es su propio UID, igual que en ClienteRemotoRepository.
     */
    private fun negocioIdDelAdmin(uid: String): String = uid

    /**
     * obtenerMovimientosRemotosDelNegocio
     * ------------------------------------
     * Recupera TODOS los movimientos del negocio del ADMIN autenticado desde
     * `movimientos/{idMovimiento}` (query filtrada por negocioId; las reglas de
     * list no funcionan como post-filtro). Solo lectura. Se usa en la
     * hidratación central de la caché local tras un cambio de propietario.
     * Conserva el idMovimiento original (no regenera IDs). NO traga los errores
     * (un fallo se propaga para reintentar).
     */
    suspend fun obtenerMovimientosRemotosDelNegocio(): List<MovimientoEntity> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val negocioId = negocioIdDelAdmin(uid)
        return db.collection(COLECCION_MOVIMIENTOS)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
            .documents
            .mapNotNull { documento ->
                HidratacionMapeadores.movimientoDeDocumento(
                    documento.data ?: emptyMap(),
                    negocioId
                )
            }
    }

    /**
     * crearMovimientoRemoto
     * ---------------------
     * Crea `movimientos/{idMovimiento}` replicando el movimiento completo de Room.
     * Si el documento ya existe y es del mismo negocio, lo sobrescribe con el
     * estado actual (idempotente: repetir la creaci��n no duplica documentos).
     */
    suspend fun crearMovimientoRemoto(movimiento: MovimientoEntity): ResultadoAutenticacion {
        return escribirMovimientoRemoto(movimiento, "crear")
    }

    /**
     * actualizarMovimientoRemoto
     * --------------------------
     * Sobrescribe `movimientos/{idMovimiento}` con el estado ACTUAL de Room.
     * No se reconstruye un documento parcial: se envía el movimiento completo,
     * conservando servicios, precioFinal, estado, fechaPago, metodoPago,
     * fechaInicio, fechaFin y observaciones.
     */
    suspend fun actualizarMovimientoRemoto(movimiento: MovimientoEntity): ResultadoAutenticacion {
        return escribirMovimientoRemoto(movimiento, "actualizar")
    }

    /**
     * eliminarMovimientoRemoto
     * ------------------------
     * Elimina `movimientos/{idMovimiento}` de Firestore. Si el documento no
     * existe la operación es un no-op (no se revierte la eliminación local).
     */
    suspend fun eliminarMovimientoRemoto(idMovimiento: Int): ResultadoAutenticacion {
        if (idMovimiento <= 0) {
            return ResultadoAutenticacion(false, "El movimiento no tiene un id válido")
        }
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        return try {
            db.collection(COLECCION_MOVIMIENTOS)
                .document(idMovimiento.toString())
                .delete()
                .esperar()
            Log.i(
                TAG,
                "Movimiento eliminado: idMovimiento=$idMovimiento resultado=OK"
            )
            ResultadoAutenticacion(true, "Movimiento eliminado")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error eliminando movimiento: idMovimiento=$idMovimiento codigo=${e.code}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando movimiento: idMovimiento=$idMovimiento", e)
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * escribirMovimientoRemoto
     * ------------------------
     * Núcleo común de crear/actualizar: comprueba la propiedad del documento
     * existente (para no sobrescribir movimientos de otro negocio) y hace un
     * `set()` completo del documento con su ID determinista.
     */
    private suspend fun escribirMovimientoRemoto(
        movimiento: MovimientoEntity,
        operacion: String
    ): ResultadoAutenticacion {
        if (movimiento.idMovimiento <= 0) {
            return ResultadoAutenticacion(false, "El movimiento no tiene un id válido")
        }
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val negocioId = negocioIdDelAdmin(uid)
        val referencia = db.collection(COLECCION_MOVIMIENTOS)
            .document(movimiento.idMovimiento.toString())

        // Comprobación de propiedad (no sobrescribir movimientos de otro negocio
        // con el mismo idMovimiento). En Firestore los documentos por negocio
        // comparten la colección, por eso se protege igual que sesiones/servicios.
        try {
            val existente = referencia.get().esperar()
            if (existente.exists()) {
                val negocioExistente = existente.getString("negocioId")
                if (negocioExistente != null && negocioExistente != negocioId) {
                    Log.w(
                        TAG,
                        "Movimiento de otro negocio: idMovimiento=${movimiento.idMovimiento} " +
                            "negocioExistente=$negocioExistente negocioPropio=$negocioId"
                    )
                    return ResultadoAutenticacion(
                        false,
                        "El idMovimiento ya está en uso por otro centro"
                    )
                }
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                // La lectura se usa solo para no pisar a otro negocio; si por
                // cualquier motivo no se puede comprobar, no se bloquea la réplica
                // (las Rules ya validan la escritura del propio negocio).
                Log.w(TAG, "Sin permisos para comprobar movimientos/${movimiento.idMovimiento}", e)
            } else {
                return ResultadoAutenticacion(false, mensajeDe(e))
            }
        } catch (e: Exception) {
            return ResultadoAutenticacion(false, mensajeDe(e))
        }

        return try {
            referencia.set(MovimientoFirestore.documentoDe(movimiento, negocioId)).esperar()
            Log.i(
                TAG,
                "Movimiento $operacion sincronizado: idMovimiento=${movimiento.idMovimiento} " +
                    "resultado=OK"
            )
            ResultadoAutenticacion(true, "Movimiento sincronizado")
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "Error en $operacion de movimiento: idMovimiento=${movimiento.idMovimiento} " +
                    "codigo=${e.code}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error en $operacion de movimiento: idMovimiento=${movimiento.idMovimiento}",
                e
            )
            ResultadoAutenticacion(false, mensajeDe(e))
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
                "No tienes permisos para sincronizar el movimiento"
            else -> e.message ?: "Error inesperado. Inténtalo de nuevo"
        }
    }
}
