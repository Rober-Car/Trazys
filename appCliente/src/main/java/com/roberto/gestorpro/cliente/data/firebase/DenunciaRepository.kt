package com.roberto.gestorpro.cliente.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TiposContenidoDenunciable (CLIENTE)
 * -----------------------------------
 * Tipos de contenido UGC denunciable (mismos valores que las Rules).
 */
object TiposContenidoDenunciable {
    const val FOTO_CLIENTE = "FOTO_CLIENTE"
    const val NOTIFICACION = "NOTIFICACION"
    const val LOGO_NEGOCIO = "LOGO_NEGOCIO"

    fun validos(): List<String> =
        listOf(FOTO_CLIENTE, NOTIFICACION, LOGO_NEGOCIO)
}

/**
 * MotivosDenuncia (CLIENTE)
 * -------------------------
 * Motivos cortos para denunciar (mismos valores que las Rules).
 */
object MotivosDenuncia {
    const val INAPROPIADO = "INAPROPIADO"
    const val ACOSO = "ACOSO"
    const val OFENSIVO = "OFENSIVO"
    const val SUPLANTACION = "SUPLANTACION"
    const val OTRO = "OTRO"

    fun validos(): List<String> =
        listOf(INAPROPIADO, ACOSO, OFENSIVO, SUPLANTACION, OTRO)

    fun etiqueta(motivo: String): String = when (motivo) {
        INAPROPIADO -> "Contenido inapropiado"
        ACOSO -> "Acoso o amenazas"
        OFENSIVO -> "Contenido ofensivo"
        SUPLANTACION -> "Suplantación"
        else -> "Otro"
    }
}

/**
 * DenunciaRepository (CLIENTE)
 * ----------------------------
 * Crea denuncias UGC desde la app del CLIENTE (notificaciones del ADMIN y
 * logo/negocio). El denunciante siempre es el usuario autenticado; el negocio
 * se valida en el ViewModel desde la vinculación del usuario.
 */
@Singleton
class DenunciaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION = "denuncias"
        private const val TAG = "DenunciaRepository"
        const val MAX_DESCRIPCION = 500

        /**
         * validarDatosDenuncia
         * --------------------
         * Validación pura (sin Firebase) de tipo y motivo. Testeable sin SDK.
         */
        fun validarDatosDenuncia(tipo: String, motivo: String): String? = when {
            tipo !in TiposContenidoDenunciable.validos() ->
                "Este contenido no se puede denunciar"
            motivo !in MotivosDenuncia.validos() ->
                "Selecciona un motivo válido"
            else -> null
        }
    }

    /**
     * crearDenuncia
     * -------------
     * Registra una denuncia con el denunciante autenticado. `usuarioDenunciadoUid`
     * suele ser el ADMIN del negocio (negocioId) cuando se denuncia su contenido.
     */
    suspend fun crearDenuncia(
        negocioId: String,
        tipo: String,
        referencia: String,
        usuarioDenunciadoUid: String?,
        motivo: String,
        descripcion: String?
    ): ResultadoAutenticacion {
        val denunciante = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val error = validarDatosDenuncia(tipo, motivo)
        if (error != null) return ResultadoAutenticacion(false, error)

        val datos = mutableMapOf<String, Any>(
            "negocioId" to negocioId,
            "denuncianteUid" to denunciante,
            "tipo" to tipo,
            "referencia" to referencia,
            "motivo" to motivo,
            "fecha" to Timestamp.now(),
            "estado" to "PENDIENTE"
        )
        if (usuarioDenunciadoUid != null) {
            datos["usuarioDenunciadoUid"] = usuarioDenunciadoUid
        }
        val descripcionLimpia = descripcion?.trim().orEmpty()
        if (descripcionLimpia.isNotEmpty()) {
            datos["descripcion"] = descripcionLimpia.take(MAX_DESCRIPCION)
        }

        return try {
            db.collection(COLECCION)
                .document("denuncia_${System.currentTimeMillis()}_${(1000..9999).random()}")
                .set(datos)
                .esperar()
            ResultadoAutenticacion(true, "Denuncia enviada. Gracias por tu colaboración.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creando denuncia", e)
            ResultadoAutenticacion(
                false,
                if (e.message?.contains("permission", ignoreCase = true) == true) {
                    "No tienes permisos para realizar esta denuncia"
                } else {
                    "No se pudo enviar la denuncia. Inténtalo de nuevo"
                }
            )
        }
    }
}
