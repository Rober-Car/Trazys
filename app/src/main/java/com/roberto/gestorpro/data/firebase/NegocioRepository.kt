package com.roberto.gestorpro.data.firebase

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.roberto.gestorpro.model.HorarioNegocio
import com.roberto.gestorpro.model.HorarioSerializacion
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResultadoLogo
 * -------------
 * ✔ TIPO: data class
 * Resultado de la subida del logo: éxito/mensaje y, en caso de éxito, la URL
 * remota de descarga que se guarda en Firestore y en DataStore.
 */
data class ResultadoLogo(
    val exito: Boolean,
    val mensaje: String,
    val url: String? = null
)

/**
 * DatosPublicosNegocio
 * --------------------
 * Datos públicos de identidad del negocio leídos de negocios_publicos/{id}.
 */
data class DatosPublicosNegocio(
    val nombre: String,
    val logo: String
)

/**
 * NegocioRepository
 * -----------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt (data/firebase)
 * Es el repositorio que encapsula la gestión remota del negocio del ADMIN:
 * creación atómica de negocios/{negocioId} + negocios_publicos/{negocioId}
 * junto a usuarios/{uid}, y modificación posterior del código maestro.
 * Sirve para que las pantallas no toquen Firestore directamente y para que
 * toda escritura respete las Security Rules vigentes (operaciones atómicas).
 */
@Singleton
class NegocioRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    companion object {
        private const val COLECCION_USUARIOS = "usuarios"
        private const val COLECCION_NEGOCIOS = "negocios"
        private const val COLECCION_NEGOCIOS_PUBLICOS = "negocios_publicos"
        private const val COLECCION_CODIGOS = "codigos_maestros"

        /**
         * El negocioId del ADMIN es su propio UID: determinista, único y
         * coherente con la regla que exige getAfter(usuarios).negocioId.
         */
        fun negocioIdDeAdmin(uid: String): String = uid

        /**
         * Normalización canónica del código maestro. Por ahora solo recorta
         * espacios al inicio y al final; se usa de forma idéntica en crear,
         * modificar, reservar y buscar (Admin y Cliente).
         */
        fun normalizarCodigo(codigo: String): String = codigo.trim()
    }

    /**
     * existeNegocioPropio
     * -------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → Boolean
     * Indica si el ADMIN autenticado ya tiene creado su documento de negocio.
     * Sirve a MiNegocioScreen para decidir su modo dual (sin negocio → alta;
     * con negocio → edición del código maestro).
     */
    suspend fun existeNegocioPropio(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection(COLECCION_NEGOCIOS)
                .document(negocioIdDeAdmin(uid))
                .get()
                .esperar()
                .exists()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * obtenerNegocioIdCuenta
     * ----------------------
     * Lee el `negocioId` de la cuenta autenticada desde su documento
     * `usuarios/{uid}` (fuente de verdad remota del contexto de negocio).
     * Devuelve null si no hay sesión, si el documento no existe o si el ADMIN
     * todavía no tiene negocio asignado. Sirve para validar la identidad de un
     * backup antes de importar/restaurar.
     */
    suspend fun obtenerNegocioIdCuenta(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val documento = db.collection(COLECCION_USUARIOS)
                .document(uid)
                .get()
                .esperar()
            if (documento.exists()) documento.getString("negocioId") else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * estadoNegocioDeCuenta
     * ---------------------
     * Determina el estado remoto del negocio de la cuenta autenticada leyendo
     * `usuarios/{uid}.negocioId` (fuente de verdad de la pertenencia).
     * Distingue tres casos que antes colapsaban en null:
     *  - SinSesion: no hay usuario autenticado.
     *  - Error: no se pudo CONFIRMAR (red, permisos, lectura fallida). En este
     *    caso NO debe concluirse que la cuenta no tiene negocio: la caché local
     *    de un negocio válido debe conservarse (modo offline).
     *  - SinNegocio: confirmado que `usuarios/{uid}.negocioId == null`.
     *  - ConNegocio(negocioId): la cuenta pertenece a un negocio.
     * Sirve al MainViewModel para limpiar la identidad visual solo cuando se
     * sabe con seguridad que la cuenta actual no tiene negocio.
     */
    suspend fun estadoNegocioDeCuenta(): EstadoNegocioDeCuenta {
        val uid = auth.currentUser?.uid ?: return EstadoNegocioDeCuenta.SinSesion
        return try {
            val documento = db.collection(COLECCION_USUARIOS)
                .document(uid)
                .get()
                .esperar()
            val negocioId = documento.getString("negocioId")
            if (negocioId.isNullOrBlank()) {
                EstadoNegocioDeCuenta.SinNegocio
            } else {
                EstadoNegocioDeCuenta.ConNegocio(negocioId)
            }
        } catch (_: Exception) {
            // No confirmado: no se trata como "sin negocio" (evita borrar la
            // caché de un negocio válido cuando no hay conexión).
            EstadoNegocioDeCuenta.Error
        }
    }

    /**
     * obtenerDatosPublicosCuenta
     * --------------------------
     * Lee la identidad pública (nombre + logo) del negocio de la cuenta actual
     * desde `negocios_publicos/{negocioId}` (fuente de verdad común con la app
     * Cliente). Devuelve null si no hay negocio, no existe el documento público
     * o falla la lectura (en ese caso la UI conserva su caché).
     */
    suspend fun obtenerDatosPublicosCuenta(): DatosPublicosNegocio? {
        val negocioId = obtenerNegocioIdCuenta() ?: return null
        return leerDatosPublicos(negocioId)
    }

    /**
     * leerDatosPublicos
     * -----------------
     * Lee `negocios_publicos/{negocioId}` y devuelve nombre y logo. null si el
     * documento no existe o la lectura falla (caché como fallback).
     */
    suspend fun leerDatosPublicos(negocioId: String): DatosPublicosNegocio? {
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
     * obtenerCodigoMaestro
     * --------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → String?
     * Lee el código maestro actual del negocio propio.
     * Devuelve null cuando no hay sesión, no hay negocio o falla la lectura.
     * Sirve para precargar MiNegocioScreen en modo edición.
     */
    suspend fun obtenerCodigoMaestro(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val documento = db.collection(COLECCION_NEGOCIOS)
                .document(negocioIdDeAdmin(uid))
                .get()
                .esperar()
            if (documento.exists()) documento.getString("codigoMaestro") else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * crearNegocio
     * ------------
     * Crea el negocio de forma ATÓMICA en una Transaction: reserva el código
     * maestro en codigos_maestros/{codigo} (si ya existe → rechaza "código ya
     * en uso"), crea negocios/{negocioId} y negocios_publicos/{negocioId}, y
     * asigna usuarios/{uid}.negocioId. Firestore serializa las transacciones
     * sobre codigos_maestros/{codigo}: ante concurrencia solo un ADMIN consigue
     * el código y el otro recibe el error.
     */
    suspend fun crearNegocio(
        nombre: String,
        codigoMaestro: String
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDeAdmin(uid)
        val codigo = normalizarCodigo(codigoMaestro)
        if (codigo.isBlank()) {
            return ResultadoAutenticacion(false, "El código maestro no puede estar vacío")
        }

        val codigosRef = db.collection(COLECCION_CODIGOS).document(codigo)
        val negociosRef = db.collection(COLECCION_NEGOCIOS).document(negocioId)
        val negociosPublicosRef = db.collection(COLECCION_NEGOCIOS_PUBLICOS).document(negocioId)
        val usuariosRef = db.collection(COLECCION_USUARIOS).document(uid)

        return try {
            db.runTransaction { transaction ->
                if (transaction.get(codigosRef).exists()) {
                    throw CodigoEnUsoException()
                }
                transaction.set(
                    negociosRef,
                    mapOf(
                        "adminUid" to uid,
                        "nombre" to nombre,
                        "codigoMaestro" to codigo
                    )
                )
                transaction.set(
                    negociosPublicosRef,
                    mapOf(
                        "nombre" to nombre,
                        "codigoMaestro" to codigo
                    )
                )
                transaction.update(usuariosRef, mapOf("negocioId" to negocioId))
                transaction.set(codigosRef, mapOf("negocioId" to negocioId))
            }.esperar()
            ResultadoAutenticacion(true, "Negocio creado correctamente")
        } catch (e: CodigoEnUsoException) {
            ResultadoAutenticacion(false, "El código maestro ya está en uso por otro centro")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * guardarCodigoMaestro
     * --------------------
     * Cambia (o confirma) el código maestro de forma ATÓMICA:
     *  - si `nuevo == anterior`: solo actualiza negocios/negocios_publicos;
     *  - si cambia: reserva el código nuevo (rechaza si está ocupado por otro
     *    negocio), libera el anterior (solo si pertenece a este negocio) y
     *    actualiza ambos documentos. Todo en una Transaction, sin dejar el
     *    código viejo reservado ni el nuevo reservado incorrectamente.
     */
    suspend fun guardarCodigoMaestro(
        codigoNuevo: String,
        codigoAnterior: String?
    ): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDeAdmin(uid)
        val nuevo = normalizarCodigo(codigoNuevo)
        if (nuevo.isBlank()) {
            return ResultadoAutenticacion(false, "El código maestro no puede estar vacío")
        }
        val anterior = codigoAnterior?.let { normalizarCodigo(it) }

        val negociosRef = db.collection(COLECCION_NEGOCIOS).document(negocioId)
        val negociosPublicosRef = db.collection(COLECCION_NEGOCIOS_PUBLICOS).document(negocioId)

        if (nuevo == anterior) {
            // Mismo código: no tocar codigos_maestros.
            return try {
                val batch = db.batch()
                batch.update(negociosRef, mapOf("codigoMaestro" to nuevo))
                batch.update(negociosPublicosRef, mapOf("codigoMaestro" to nuevo))
                batch.commit().esperar()
                ResultadoAutenticacion(true, "Código maestro actualizado")
            } catch (e: Exception) {
                ResultadoAutenticacion(false, mensajeDe(e))
            }
        }

        val codigoNuevoRef = db.collection(COLECCION_CODIGOS).document(nuevo)
        val codigoAnteriorRef = anterior?.let {
            db.collection(COLECCION_CODIGOS).document(it)
        }

        return try {
            db.runTransaction { transaction ->
                val reservadoNuevo = transaction.get(codigoNuevoRef)
                if (reservadoNuevo.exists() &&
                    reservadoNuevo.getString("negocioId") != negocioId
                ) {
                    throw CodigoEnUsoException()
                }

                if (codigoAnteriorRef != null) {
                    val reservadoAnterior = transaction.get(codigoAnteriorRef)
                    if (reservadoAnterior.exists()) {
                        if (reservadoAnterior.getString("negocioId") != negocioId) {
                            // El código anterior no pertenece a este negocio:
                            // estado incoherente, no se debe liberar nada.
                            throw IntegridadCodigoException()
                        }
                        transaction.delete(codigoAnteriorRef)
                    }
                }

                transaction.set(codigoNuevoRef, mapOf("negocioId" to negocioId))
                transaction.update(negociosRef, mapOf("codigoMaestro" to nuevo))
                transaction.update(negociosPublicosRef, mapOf("codigoMaestro" to nuevo))
            }.esperar()
            ResultadoAutenticacion(true, "Código maestro actualizado")
        } catch (e: CodigoEnUsoException) {
            ResultadoAutenticacion(false, "El código maestro ya está en uso por otro centro")
        } catch (e: IntegridadCodigoException) {
            ResultadoAutenticacion(false, "El código maestro anterior no es coherente. Contacta con soporte")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * guardarNombreNegocio
     * --------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Actualiza el nombre del negocio en negocios/{id} y negocios_publicos/{id}
     * dentro del mismo WriteBatch (mismo mecanismo que guardarCodigoMaestro),
     * para que ambos documentos queden siempre coherentes. La app Cliente lee
     * el nombre desde negocios_publicos/{id}, por lo que este método es el que
     * propaga el cambio que el ADMIN hace en MiNegocioScreen.
     * Sirve al modo edición de MiNegocioScreen.
     */
    suspend fun guardarNombreNegocio(nombre: String): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDeAdmin(uid)
        val batch = db.batch()

        batch.update(
            db.collection(COLECCION_NEGOCIOS).document(negocioId),
            mapOf("nombre" to nombre)
        )

        batch.update(
            db.collection(COLECCION_NEGOCIOS_PUBLICOS).document(negocioId),
            mapOf("nombre" to nombre)
        )

        return try {
            batch.commit().esperar()
            ResultadoAutenticacion(true, "Nombre actualizado")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * leerHorario
     * -----------
     * Lee el horario configurable (centro + actividades) de
     * negocios_publicos/{negocioId}. Devuelve un HorarioNegocio vacío si el
     * documento o los campos no existen (negocios sin horario configurado).
     */
    suspend fun leerHorario(): HorarioNegocio {
        val negocioId = obtenerNegocioIdCuenta() ?: return HorarioNegocio()
        return try {
            val documento = db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .get()
                .esperar()
            if (!documento.exists()) return HorarioNegocio()
            HorarioNegocio(
                centro = HorarioSerializacion.mapaACentro(documento.get("horarioCentro")),
                actividades = HorarioSerializacion.mapaAActividades(
                    documento.get("horarioActividades")
                ),
                excepciones = HorarioSerializacion.listaAExcepciones(
                    documento.get("horarioExcepciones")
                )
            )
        } catch (_: Exception) {
            HorarioNegocio()
        }
    }

    /**
     * guardarHorario
     * --------------
     * Guarda el horario (centro + actividades + excepciones) en
     * `negocios_publicos/{negocioId}`, que es la fuente pública que lee el
     * CLIENTE (y también `leerHorario`). NO se escribe en `negocios/{id}`: el
     * horario es información pública y así no depende de la regla de update del
     * documento privado (que exige `adminUid`). Las Rules de `negocios_publicos`
     * ya permiten al ADMIN propietario escribir estos campos.
     */
    suspend fun guardarHorario(horario: HorarioNegocio): ResultadoAutenticacion {
        val uid = auth.currentUser?.uid
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val negocioId = negocioIdDeAdmin(uid)
        val datos = mapOf(
            "horarioCentro" to HorarioSerializacion.centroAMapa(horario.centro),
            "horarioActividades" to HorarioSerializacion.actividadesAMapa(horario.actividades),
            "horarioExcepciones" to HorarioSerializacion.excepcionesALista(horario.excepciones)
        )
        return try {
            db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .update(datos)
                .esperar()
            ResultadoAutenticacion(true, "Horario guardado")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    /**
     * guardarLogoRemoto
     * -----------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoLogo
     * Sube el logo del gimnasio a Firebase Storage en negocios/{negocioId}/logo.jpg
     * (sobrescribiendo el anterior), obtiene la URL de descarga y actualiza el
     * campo logo de negocios/{id} y negocios_publicos/{id} en un único WriteBatch.
     * No se guarda la imagen en Firestore, solo la URL. El archivo local solo se
     * usa como origen de la subida. Si falla la subida o el Batch se devuelve el
     * error sin marcar la operación como exitosa (el logo local queda para reintentar).
     *
     * Cache-busting: la URL guardada incorpora `?rev=<epoch>`. El caché del logo
     * (LogoNegocioCache) se identifica por la URL completa, así que al reemplazar
     * el logo la URL cambia y el siguiente acceso descarga la imagen nueva.
     */
    suspend fun guardarLogoRemoto(rutaLocal: String): ResultadoLogo {
        val uid = auth.currentUser?.uid
            ?: return ResultadoLogo(false, "No hay ningún usuario autenticado")

        val negocioId = negocioIdDeAdmin(uid)

        return try {
            val referencia = storage.reference.child("negocios/$negocioId/logo.jpg")
            referencia.putFile(Uri.fromFile(File(rutaLocal))).esperar()
            val urlBase = referencia.downloadUrl.esperar().toString()
            val separador = if (urlBase.contains("?")) "&" else "?"
            val url = "$urlBase${separador}rev=${System.currentTimeMillis()}"

            val batch = db.batch()
            batch.update(
                db.collection(COLECCION_NEGOCIOS).document(negocioId),
                mapOf("logo" to url)
            )
            batch.update(
                db.collection(COLECCION_NEGOCIOS_PUBLICOS).document(negocioId),
                mapOf("logo" to url)
            )
            batch.commit().esperar()

            ResultadoLogo(true, "Logo actualizado", url)
        } catch (e: Exception) {
            ResultadoLogo(false, mensajeDe(e))
        }
    }

    /**
     * mensajeDe
     * ---------
     * ✔ TIPO: método (fun) privado de Kotlin → String
     * Traduce los errores típicos de Firestore a mensajes en español.
     * Sirve para que la UI nunca muestre textos técnicos en inglés.
     */
    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                "No tienes permisos para esta operación"
            else -> e.message ?: "Error inesperado. Inténtalo de nuevo"
        }
    }
}

/**
 * EstadoNegocioDeCuenta
 * ---------------------
 * Estado remoto del negocio de la cuenta autenticada. Distingue "sin negocio
 * CONFIRMADO" (usuarios/{uid}.negocioId == null, lectura correcta) de "no se
 * pudo comprobar" (Error), porque solo el primer caso autoriza a vaciar la
 * identidad visual local de un negocio anterior.
 */
sealed interface EstadoNegocioDeCuenta {
    /** No hay usuario autenticado. */
    object SinSesion : EstadoNegocioDeCuenta

    /** No se pudo confirmar el estado (red/permisos/fallo de lectura). */
    object Error : EstadoNegocioDeCuenta

    /** Confirmado: usuarios/{uid}.negocioId es null (la cuenta no tiene negocio). */
    object SinNegocio : EstadoNegocioDeCuenta

    /** La cuenta pertenece al negocio indicado. */
    data class ConNegocio(val negocioId: String) : EstadoNegocioDeCuenta
}

/**
 * CodigoEnUsoException
 * --------------------
 * Señala que el código maestro ya está reservado por otro negocio.
 */
private class CodigoEnUsoException : Exception()

/**
 * IntegridadCodigoException
 * -------------------------
 * Señala un estado incoherente de la reserva del código maestro.
 */
private class IntegridadCodigoException : Exception()
