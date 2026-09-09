package com.roberto.gestorpro.cliente.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.cliente.model.Servicio
import com.roberto.gestorpro.cliente.model.Sesion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SesionRepository
 * ----------------
 * Repositorio de lectura de SERVICIOS y SESIONES para el CLIENTE.
 *
 * El CLIENTE no puede listar servicios (las Rules solo permiten get de
 * servicios ACTIVOS de su negocio) ni hacer consultas globales de sesiones
 * (las Rules se evalúan por documento y una consulta que devuelva una sesión
 * no autorizada falla entera). Por eso el acceso se hace por servicio:
 *
 *   1. getDoc servicios/{idServicio}  -> null si inactivo/eliminado (permiso);
     *   2. getDocs sesiones.where("idServicio","==",id)
     *      .where("negocioId","==",negocioId) -> solo ese servicio y negocio.
 *
 * Un error de red o de otro tipo se propaga para que el ViewModel muestre un
 * estado de error; los errores de permiso (servicio inactivo/eliminado) se
 * traducen a "no disponible" sin romper la pantalla.
 */
@Singleton
class SesionRepository @Inject constructor(
    private val db: FirebaseFirestore
) {

    companion object {
        private const val COLECCION_SERVICIOS = "servicios"
        private const val COLECCION_SESIONES = "sesiones"

        /**
         * asistentesDe
         * ------------
         * Convierte el campo remoto `asistentes` (map { clienteId: nombre }) en
         * un mapa de Strings. Si el campo falta, no es un mapa o contiene valores
         * no textuales se devuelve un mapa vacío (fail-open para no romper la
         * lectura de sesiones).
         */
        internal fun asistentesDe(valor: Any?): Map<String, String> {
            val mapa = valor as? Map<*, *> ?: return emptyMap()
            return mapa.mapNotNull { (clave, nombre) ->
                val c = clave?.toString()
                val n = nombre?.toString()
                if (c != null && n != null) c to n else null
            }.toMap()
        }
    }

    /**
     * obtenerServicioActivo
     * ---------------------
     * Lee servicios/{idServicio}. Las Rules solo permiten al CLIENTE leer un
     * servicio ACTIVO de su negocio. El negocio se comprueba también en el
     * modelo para no mezclar datos de otro negocio.
     */
    suspend fun obtenerServicioActivo(idServicio: Int, negocioId: String): Servicio? {
        return try {
            val documento = db.collection(COLECCION_SERVICIOS)
                .document(idServicio.toString())
                .get()
                .esperar()
            if (!documento.exists()) return null
            val datos = documento.data ?: return null
            val servicio = Servicio(
                idServicio = (datos["idServicio"] as? Number)?.toInt() ?: idServicio,
                negocioId = datos["negocioId"] as? String ?: "",
                nombre = datos["nombre"] as? String ?: "",
                descripcion = datos["descripcion"] as? String ?: "",
                activo = datos["activo"] as? Boolean ?: false,
                permiteCombinarDia = datos["permiteCombinarDia"] as? Boolean ?: true
            )
            servicio.takeIf { it.negocioId == negocioId && it.activo }
        } catch (e: Exception) {
            if (e.message?.contains("permission", ignoreCase = true) == true) null else throw e
        }
    }

    /**
     * obtenerSesionesPorServicio
     * --------------------------
     * Consulta las sesiones de un servicio y negocio con filtros de igualdad
     * y las devuelve sin ordenar; el filtro del día actual y el orden por hora
     * los aplica el ViewModel en memoria.
     * Si la consulta se deniega (servicio desactivado entre comprobaciones)
     * se devuelve una lista vacía para omitir ese servicio.
     */
    suspend fun obtenerSesionesPorServicio(idServicio: Int, negocioId: String): List<Sesion> {
        return try {
            db.collection(COLECCION_SESIONES)
                .whereEqualTo("idServicio", idServicio)
                .whereEqualTo("negocioId", negocioId)
                .get()
                .esperar()
                .documents.mapNotNull { documento ->
                    sesionDeDocumento(documento, idServicio, negocioId)
                }
        } catch (e: Exception) {
            if (e.message?.contains("permission", ignoreCase = true) == true) emptyList() else throw e
        }
    }

    /**
     * obtenerSesionPorId
     * ------------------
     * Lee una sesión concreta por su documentId (sesiones/{idSesion}). El
     * CLIENTE solo puede leerla si cumple las Rules (servicio contratado y
     * activo del propio negocio y estado ACTIVO). Se usa para mostrar los
     * asistentes de una sesión que el cliente ya tiene reservada. Devuelve
     * null si no existe, no es legible o no pertenece al negocio indicado.
     */
    suspend fun obtenerSesionPorId(idSesion: Int, negocioId: String): Sesion? {
        return try {
            val documento = db.collection(COLECCION_SESIONES)
                .document(idSesion.toString())
                .get()
                .esperar()
            if (!documento.exists()) return null
            sesionDeDocumento(documento, null, negocioId)
        } catch (e: Exception) {
            if (e.message?.contains("permission", ignoreCase = true) == true) null else throw e
        }
    }

    /**
     * sesionDeDocumento
     * -----------------
     * Convierte un DocumentSnapshot de sesiones/{idSesion} en una Sesion.
     * idServicio se toma del documento; si el documento no lo trae se usa el
     * valor por defecto indicado. Devuelve null si la sesión no pertenece al
     * negocio esperado.
     */
    private fun sesionDeDocumento(
        documento: com.google.firebase.firestore.DocumentSnapshot,
        idServicioPorDefecto: Int?,
        negocioId: String
    ): Sesion? {
        val datos = documento.data ?: return null
        if (datos["negocioId"] != negocioId) return null
        val idSesion = (datos["idSesion"] as? Number)?.toInt()
            ?: documento.id.toIntOrNull()
            ?: return null
        val idServicio = (datos["idServicio"] as? Number)?.toInt()
            ?: idServicioPorDefecto
            ?: return null
        return Sesion(
            idSesion = idSesion,
            negocioId = datos["negocioId"] as? String ?: "",
            idServicio = idServicio,
            fecha = (datos["fecha"] as? Number)?.toLong() ?: 0L,
            hora = datos["hora"] as? String ?: "",
            duracionMinutos = (datos["duracionMinutos"] as? Number)?.toInt() ?: 0,
            capacidad = (datos["capacidad"] as? Number)?.toInt() ?: 0,
            plazasDisponibles = (datos["plazasDisponibles"] as? Number)?.toInt() ?: 0,
            horaDesdeReserva = datos["horaDesdeReserva"] as? String,
            asistentes = asistentesDe(datos["asistentes"])
        )
    }
}
