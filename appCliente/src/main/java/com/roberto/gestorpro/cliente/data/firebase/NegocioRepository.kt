package com.roberto.gestorpro.cliente.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.cliente.model.HorarioNegocio
import com.roberto.gestorpro.cliente.model.HorarioParseo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DatosPublicosNegocio
 * --------------------
 * ✔ TIPO: data class
 * Datos públicos del gimnasio que la app Cliente lee de negocios_publicos/{id}.
 */
data class DatosPublicosNegocio(
    val nombre: String,
    val logo: String
)

/**
 * NegocioRepository
 * -----------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Encapsula la lectura pública de negocios_publicos para resolver un negocio
 * a partir de su código maestro y para obtener sus datos públicos (nombre/logo).
 */
@Singleton
class NegocioRepository @Inject constructor(
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_NEGOCIOS_PUBLICOS = "negocios_publicos"
    }

    /**
     * resolverNegocioPorCodigoMaestro
     * -------------------------------
     * Busca el negocio público cuyo codigoMaestro coincide.
     * Devuelve el negocioId o null si no existe.
     */
    suspend fun resolverNegocioPorCodigoMaestro(codigoMaestro: String): String? {
        return try {
            val coincidencias = db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .whereEqualTo("codigoMaestro", codigoMaestro.trim())
                .limit(1)
                .get()
                .esperar()
            coincidencias.documents.firstOrNull()?.id
        } catch (_: Exception) {
            null
        }
    }

    /**
     * obtenerDatosPublicosNegocio
     * ---------------------------
     * Lee negocios_publicos/{negocioId} y devuelve nombre y logo (null si el
     * documento no existe o falla la lectura). Es la fuente de verdad de los
     * datos públicos del gimnasio para la app Cliente.
     */
    suspend fun obtenerDatosPublicosNegocio(negocioId: String): DatosPublicosNegocio? {
        return try {
            val documento = db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .get()
                .esperar()
            if (!documento.exists()) return null
            DatosPublicosNegocio(
                nombre = documento.getString("nombre") ?: "",
                logo = documento.getString("logo") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * obtenerHorarioNegocio
     * ---------------------
     * Lee el horario configurable (centro + actividades) de
     * negocios_publicos/{negocioId}. Devuelve un HorarioNegocio vacío si el
     * documento o los campos no existen (negocios sin horario configurado).
     */
    suspend fun obtenerHorarioNegocio(negocioId: String): HorarioNegocio {
        return try {
            val documento = db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .get()
                .esperar()
            if (!documento.exists()) return HorarioNegocio()
            HorarioNegocio(
                centro = HorarioParseo.mapaACentro(documento.get("horarioCentro")),
                actividades = HorarioParseo.mapaAActividades(documento.get("horarioActividades"))
            )
        } catch (_: Exception) {
            HorarioNegocio()
        }
    }

    /**
     * obtenerNombreNegocio
     * --------------------
     * Lee solo el nombre de negocios_publicos/{negocioId} (información pública).
     */
    suspend fun obtenerNombreNegocio(negocioId: String): String? {
        return try {
            db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .get()
                .esperar()
                .getString("nombre")
        } catch (_: Exception) {
            null
        }
    }
}
