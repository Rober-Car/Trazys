package com.roberto.gestorpro.cliente.data.firebase

import android.content.Context
import androidx.annotation.StringRes
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PerfilPendiente
 * ---------------
 * ✔ TIPO: data class
 * Datos personales de un CLIENTE registrado que aún no pertenece a un negocio.
 * fechaNacimiento es OPCIONAL: null cuando el cliente no introdujo fecha.
 */
data class PerfilPendiente(
    val nombre: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val email: String?,
    val foto: String,
    val fechaNacimiento: Long? = null
)

/**
 * PerfilPendienteRepository
 * -------------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Gestiona el documento temporal perfiles_pendientes/{uid} del CLIENTE.
 */
@Singleton
class PerfilPendienteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
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
        private const val COLECCION_PERFILES = "perfiles_pendientes"
    }

    /**
     * guardar
     * -------
     * Crea o actualiza el perfil pendiente completo del usuario autenticado
     * (VÍA 2: "No tengo código").
     */
    suspend fun guardar(uid: String, perfil: PerfilPendiente): ResultadoAutenticacion {
        return try {
            db.collection(COLECCION_PERFILES)
                .document(uid)
                .set(
                    mapOf(
                        "nombre" to perfil.nombre,
                        "apellidos" to perfil.apellidos,
                        "dni" to perfil.dni.trim().uppercase(),
                        "telefono" to perfil.telefono,
                        "email" to perfil.email,
                        "foto" to perfil.foto,
                        "fechaNacimiento" to perfil.fechaNacimiento
                    )
                )
                .esperar()
            ResultadoAutenticacion(true, "Perfil guardado")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * guardarDeclaracion
     * ------------------
     * Crea o actualiza la declaración temporal de VÍA 1 en
     * perfiles_pendientes/{uid}: únicamente { dni, negocioId }. Sirve para que
     * las Rules validen que el índice consultado corresponde exactamente a la
     * combinación negocio+DNI que el CLIENTE introdujo. NO es un perfil ficticio:
     * es el dato declarado en el momento de la vinculación.
     *
     * Se usa SetOptions.merge() para NO destruir el perfil completo de VÍA 2
     * que el cliente pudo haber guardado antes (nombre, apellidos, teléfono…):
     * el perfil pendiente es la fuente de verdad y solo se elimina al completar
     * la vinculación correctamente.
     */
    suspend fun guardarDeclaracion(
        uid: String,
        dni: String,
        negocioId: String
    ): ResultadoAutenticacion {
        return try {
            db.collection(COLECCION_PERFILES)
                .document(uid)
                .set(
                    mapOf(
                        "dni" to dni.trim().uppercase(),
                        "negocioId" to negocioId
                    ),
                    SetOptions.merge()
                )
                .esperar()
            ResultadoAutenticacion(true, "Declaración guardada")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * leer
     * ----
     * Lee el perfil pendiente del usuario (null si no existe).
     */
    suspend fun leer(uid: String): PerfilPendiente? {
        return try {
            val documento = db.collection(COLECCION_PERFILES)
                .document(uid)
                .get()
                .esperar()
            if (!documento.exists()) return null
            PerfilPendiente(
                nombre = documento.getString("nombre") ?: "",
                apellidos = documento.getString("apellidos") ?: "",
                dni = documento.getString("dni") ?: "",
                telefono = documento.getString("telefono") ?: "",
                email = documento.getString("email"),
                foto = documento.getString("foto") ?: "",
                fechaNacimiento = documento.getLong("fechaNacimiento")
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * borrar
     * ------
     * Elimina el perfil pendiente tras completar la vinculación.
     */
    suspend fun borrar(uid: String) {
        try {
            db.collection(COLECCION_PERFILES)
                .document(uid)
                .delete()
                .esperar()
        } catch (_: Exception) {
        }
    }

    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                texto(R.string.perfil_error_permisos)
            else -> texto(R.string.auth_error_inesperado)
        }
    }
}
