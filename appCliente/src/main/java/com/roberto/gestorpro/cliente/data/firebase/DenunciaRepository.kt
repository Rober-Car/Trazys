package com.roberto.gestorpro.cliente.data.firebase

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * La lógica pura (códigos, `validos` y `etiqueta`) se conserva para las Rules y
 * los tests; la UI resuelve las etiquetas visibles desde recursos.
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

    companion object {
        private const val COLECCION = "denuncias"
        private const val TAG = "DenunciaRepository"
        const val MAX_DESCRIPCION = 500

        /**
         * validarDatosDenuncia
         * --------------------
         * Validación pura (sin Firebase) de tipo y motivo. Testeable sin SDK.
         * Se conserva tal cual para sus tests; los mensajes que muestra la UI se
         * resuelven en el idioma activo dentro de [crearDenuncia].
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
     * Los errores de validación y de red se devuelven localizados.
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
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )
        // Misma semántica que validarDatosDenuncia, con mensaje localizado.
        val error = when {
            tipo !in TiposContenidoDenunciable.validos() ->
                texto(R.string.denuncia_error_tipo_invalido)
            motivo !in MotivosDenuncia.validos() ->
                texto(R.string.denuncia_error_motivo_invalido)
            else -> null
        }
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
            ResultadoAutenticacion(true, texto(R.string.denuncia_enviada_exito))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando denuncia", e)
            ResultadoAutenticacion(
                false,
                if (e.message?.contains("permission", ignoreCase = true) == true) {
                    texto(R.string.denuncia_error_permisos)
                } else {
                    texto(R.string.denuncia_error_envio)
                }
            )
        }
    }
}
