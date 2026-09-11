package com.roberto.gestorpro.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TiposContenidoDenunciable
 * -------------------------
 * Tipos de contenido UGC denunciable dentro de la app (sistema de denuncias).
 */
object TiposContenidoDenunciable {
    const val FOTO_CLIENTE = "FOTO_CLIENTE"
    const val NOTIFICACION = "NOTIFICACION"
    const val LOGO_NEGOCIO = "LOGO_NEGOCIO"

    fun validos(): List<String> =
        listOf(FOTO_CLIENTE, NOTIFICACION, LOGO_NEGOCIO)
}

/**
 * MotivosDenuncia
 * ---------------
 * Motivos cortos y razonables para denunciar contenido/usuario UGC.
 */
object MotivosDenuncia {
    const val INAPROPIADO = "INAPROPIADO"
    const val ACOSO = "ACOSO"
    const val OFENSIVO = "OFENSIVO"
    const val SUPLANTACION = "SUPLANTACION"
    const val OTRO = "OTRO"

    fun validos(): List<String> =
        listOf(INAPROPIADO, ACOSO, OFENSIVO, SUPLANTACION, OTRO)

    /** Etiqueta visible en español para un motivo. */
    fun etiqueta(motivo: String): String = when (motivo) {
        INAPROPIADO -> "Contenido inapropiado"
        ACOSO -> "Acoso o amenazas"
        OFENSIVO -> "Contenido ofensivo"
        SUPLANTACION -> "Suplantación"
        else -> "Otro"
    }
}

/**
 * EstadosDenuncia
 * ---------------
 * Estados de una denuncia: nace PENDIENTE y el ADMIN la marca REVISADA.
 */
object EstadosDenuncia {
    const val PENDIENTE = "PENDIENTE"
    const val REVISADA = "REVISADA"
}

/**
 * Denuncia
 * --------
 * Representación de una denuncia de contenido/usuario UGC (denuncias/{id}).
 */
data class Denuncia(
    val id: String,
    val negocioId: String,
    val denuncianteUid: String,
    val usuarioDenunciadoUid: String?,
    val tipo: String,
    val referencia: String,
    val motivo: String,
    val descripcion: String?,
    val fecha: Long,
    val estado: String
)

/**
 * ResultadoDenuncia
 * -----------------
 * Resultado unificado de las operaciones de denuncias.
 */
data class ResultadoDenuncia(
    val exito: Boolean,
    val mensaje: String
)

/**
 * DenunciaRepository
 * ------------------
 * Acceso a Firestore de la colección `denuncias`. Separa la UI de Firebase:
 *   UI -> ViewModel -> DenunciaRepository -> Firestore
 *
 * El denunciante siempre es el usuario autenticado (request.auth.uid); la app
 * nunca puede indicar otro denunciante. El `negocioId` lo resuelve el ViewModel
 * desde el negocio del usuario autenticado (uid del ADMIN o negocio del CLIENTE).
 */
@Singleton
class DenunciaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION = "denuncias"
        private const val TAG = "DenunciaRepository"

        /** Longitud máxima razonable de la descripción opcional. */
        const val MAX_DESCRIPCION = 500

        /** Etiqueta visible en español para el tipo de contenido. */
        fun etiquetaTipo(tipo: String): String = when (tipo) {
            TiposContenidoDenunciable.FOTO_CLIENTE -> "Foto de cliente"
            TiposContenidoDenunciable.NOTIFICACION -> "Notificación"
            TiposContenidoDenunciable.LOGO_NEGOCIO -> "Logo del centro"
            else -> tipo
        }

        /**
         * validarDatosDenuncia
         * --------------------
         * Validación pura (sin Firebase) del tipo y motivo de una denuncia.
         * Devuelve el error en español o null si son válidos. Testeable sin SDK.
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
     * Registra una denuncia. El denunciante es SIEMPRE el usuario autenticado.
     * Solo se escriben las claves permitidas por las Rules.
     */
    suspend fun crearDenuncia(
        negocioId: String,
        tipo: String,
        referencia: String,
        usuarioDenunciadoUid: String?,
        motivo: String,
        descripcion: String?
    ): ResultadoDenuncia {
        val denunciante = auth.currentUser?.uid
            ?: return ResultadoDenuncia(false, "No hay ningún usuario autenticado")
        val error = validarDatosDenuncia(tipo, motivo)
        if (error != null) return ResultadoDenuncia(false, error)

        val datos = mutableMapOf<String, Any>(
            "negocioId" to negocioId,
            "denuncianteUid" to denunciante,
            "tipo" to tipo,
            "referencia" to referencia,
            "motivo" to motivo,
            "fecha" to Timestamp.now(),
            "estado" to EstadosDenuncia.PENDIENTE
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
            ResultadoDenuncia(true, "Denuncia enviada. Gracias por tu colaboración.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creando denuncia", e)
            ResultadoDenuncia(false, mensajeDe(e))
        }
    }

    /**
     * obtenerDenuncias
     * ----------------
     * Denuncias del negocio indicado (el ADMIN de su propio negocio), ordenadas
     * de más reciente a más antigua. Query con negocioId (Rules).
     */
    suspend fun obtenerDenuncias(negocioId: String): List<Denuncia> {
        val snapshots = db.collection(COLECCION)
            .whereEqualTo("negocioId", negocioId)
            .get()
            .esperar()
        return snapshots.documents.mapNotNull { documento ->
            val datos = documento.data ?: return@mapNotNull null
            val negocio = datos["negocioId"] as? String ?: return@mapNotNull null
            val denunciante = datos["denuncianteUid"] as? String ?: return@mapNotNull null
            val tipo = datos["tipo"] as? String ?: return@mapNotNull null
            val referencia = datos["referencia"] as? String ?: return@mapNotNull null
            val motivo = datos["motivo"] as? String ?: return@mapNotNull null
            val fecha = fechaEnMilisegundos(datos["fecha"]) ?: return@mapNotNull null
            Denuncia(
                id = documento.id,
                negocioId = negocio,
                denuncianteUid = denunciante,
                usuarioDenunciadoUid = datos["usuarioDenunciadoUid"] as? String,
                tipo = tipo,
                referencia = referencia,
                motivo = motivo,
                descripcion = datos["descripcion"] as? String,
                fecha = fecha,
                estado = datos["estado"] as? String ?: EstadosDenuncia.PENDIENTE
            )
        }.sortedByDescending { it.fecha }
    }

    /**
     * marcarRevisada
     * --------------
     * Pone la denuncia en REVISADA (solo el ADMIN de su negocio por Rules).
     */
    suspend fun marcarRevisada(denunciaId: String): ResultadoDenuncia {
        return try {
            db.collection(COLECCION)
                .document(denunciaId)
                .update("estado", EstadosDenuncia.REVISADA)
                .esperar()
            ResultadoDenuncia(true, "Denuncia marcada como revisada")
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando denuncia como revisada", e)
            ResultadoDenuncia(false, mensajeDe(e))
        }
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    private fun mensajeDe(e: Exception): String =
        if (e.message?.contains("permission", ignoreCase = true) == true) {
            "No tienes permisos para realizar esta denuncia"
        } else {
            e.message ?: "No se pudo enviar la denuncia. Inténtalo de nuevo"
        }
}
