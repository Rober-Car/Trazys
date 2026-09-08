package com.roberto.gestorpro.cliente.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * ResultadoVinculacion
 * --------------------
 * ✔ TIPO: data class
 * Resultado del intento de vinculación: ficha encontrada y vinculada, ficha
 * creada, DNI ya vinculado, o error.
 * requiereCompletarPerfil = true indica que NO existe ficha y el usuario tampoco
 * tiene un perfil pendiente completo: la UI debe ofrecerle completar sus datos
 * (no es un error, es un paso del flujo guiado).
 */
data class ResultadoVinculacion(
    val exito: Boolean,
    val mensaje: String,
    val clienteId: Int? = null,
    val negocioId: String? = null,
    val requiereCompletarPerfil: Boolean = false
)

/**
 * ResultadoIndice
 * ---------------
 * ✔ TIPO: sealed class
 * Resultado de la consulta a indices_clientes/{negocioId}_{dni}:
 *   - Ficha(clienteId): el índice existe y apunta a una ficha.
 *   - NoExiste: el índice no existe (el gimnasio aún no creó una ficha para ese DNI).
 * Los errores (permisos, red) NO se devuelven aquí: se lanzan como excepción para
 * que el llamador los distinga de un índice inexistente.
 */
sealed class ResultadoIndice {
    data class Ficha(val clienteId: Int) : ResultadoIndice()
    object NoExiste : ResultadoIndice()
}

/**
 * VinculacionRepository
 * ---------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Implementa las DOS vías de alta del CLIENTE:
 *   VÍA 1 — el ADMIN creó la ficha: localizar por indices_clientes/{negocio}_{dni}
 *           y vincular el UID a la ficha existente (sin crear otra).
 *   VÍA 2 — el CLIENTE se registró primero: crear la ficha con los datos de
 *           perfiles_pendientes + su índice, siempre dentro de la misma Transaction.
 * La unicidad negocio+DNI está garantizada por el documentId del índice.
 */
@Singleton
class VinculacionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val perfilPendienteRepository: PerfilPendienteRepository,
    private val preferencesRepository: com.roberto.gestorpro.cliente.data.repository.PreferencesRepository,
    private val storage: FirebaseStorage
) {

    companion object {
        private const val COLECCION_CLIENTES = "clientes"
        private const val COLECCION_INDICES = "indices_clientes"
        private const val COLECCION_USUARIOS = "usuarios"
        private const val COLECCION_PERFILES_PENDIENTES = "perfiles_pendientes"
        private const val COLECCION_NEGOCIOS_PUBLICOS = "negocios_publicos"
        private const val COLECCION_CODIGOS = "codigos_maestros"
        private const val COLECCION_NOTIFICACIONES = "notificaciones"

        private const val MAX_INTENTOS_ID = 5
        private const val ID_CLIENTE_MINIMO = 1_000_000_000

        internal fun resultadoCuandoIndiceNoExiste(): ResultadoVinculacion =
            ResultadoVinculacion(
                false,
                "No existe ningún cliente registrado con ese DNI."
            )

        /**
         * resultadoNoHayFichaParaRegistro
         * -------------------------------
         * Resultado del flujo guiado cuando NO existe la ficha del DNI y el
         * usuario tampoco tiene un perfil pendiente completo. No es un error de
         * la operación: la UI debe invitar al usuario a completar sus datos y,
         * tras guardarlos, la vinculación continuará sola (VÍA 2).
         */
        internal fun resultadoNoHayFichaParaRegistro(): ResultadoVinculacion =
            ResultadoVinculacion(
                false,
                "No encontramos una ficha con este DNI. Puedes registrarte ahora " +
                    "y después te vincularemos automáticamente a este centro.",
                requiereCompletarPerfil = true
            )
    }

    /**
     * indiceId
     * --------
     * DocumentId del índice negocio+DNI.
     */
    fun indiceId(negocioId: String, dni: String): String =
        "${negocioId}_${dni.trim().uppercase()}"

    /**
     * localizarFicha
     * --------------
     * Devuelve el estado del índice negocio+DNI.
     *   - ResultadoIndice.Ficha(clienteId): el índice existe.
     *   - ResultadoIndice.NoExiste: el índice no existe.
     * NO traga las excepciones: un PERMISSION_DENIED o un error de red se
     * propagan al llamador para que no se confundan con "índice inexistente"
     * (un índice inexistente significa VÍA 2; un permiso denegado no).
     */
    suspend fun localizarFicha(negocioId: String, dni: String): ResultadoIndice {
        val documento = db.collection(COLECCION_INDICES)
            .document(indiceId(negocioId, dni))
            .get()
            .esperar()
        val clienteId = documento.getLong("clienteId")?.toInt()
        return if (clienteId != null) {
            ResultadoIndice.Ficha(clienteId)
        } else {
            ResultadoIndice.NoExiste
        }
    }

    /**
     * vincularConCodigoYDNI
     * ---------------------
     * Flujo principal de vinculación. Resuelve el negocio por código maestro,
     * declara temporalmente { dni, negocioId } en perfiles_pendientes/{uid}
     * (sin destruir el perfil completo) y, según el índice:
     *   - si la ficha existe y está libre (firebaseUid == null): la vincula (VÍA 1);
     *   - si la ficha ya está vinculada: rechaza;
     *   - si el índice NO existe pero hay un perfil pendiente COMPLETO: crea la
     *     ficha con los datos de ese perfil (VÍA 2, reutiliza crearFicha);
     *   - si el índice NO existe y no hay perfil completo: rechaza (no se crean
     *     fichas vacías a partir de la declaración temporal { dni, negocioId }).
     * El perfil pendiente SOLO se elimina cuando la vinculación se completa con
     * éxito. Ante cualquier error (falta de perfil, permisos, red, fallo
     * intermedio) se conserva.
     */
    suspend fun vincularConCodigoYDNI(
        codigoMaestro: String,
        dni: String
    ): ResultadoVinculacion {
        return try {
            val usuario = auth.currentUser
                ?: return ResultadoVinculacion(false, "No hay ningún usuario autenticado")
            val uid = usuario.uid

            // RESOLUCIÓN DEL NEGOCIO POR CÓDIGO MAESTRO (única y determinista).
            // codigos_maestros/{codigo} es la reserva global: UN código = UN
            // negocio. Nunca se usa whereEqualTo/limit(1). Si la reserva no
            // existe o apunta a un negocio incoherente, se falla explícitamente.
            val codigoNorm = codigoMaestro.trim()

            val codigoDoc = db.collection(COLECCION_CODIGOS)
                .document(codigoNorm)
                .get()
                .esperar()
            if (!codigoDoc.exists()) {
                return ResultadoVinculacion(false, "Código maestro no válido.")
            }
            val negocioId = codigoDoc.getString("negocioId")
            if (negocioId.isNullOrBlank()) {
                return ResultadoVinculacion(false, "Código maestro no válido.")
            }

            val negocioPublico = db.collection(COLECCION_NEGOCIOS_PUBLICOS)
                .document(negocioId)
                .get()
                .esperar()
            if (!negocioPublico.exists() ||
                negocioPublico.getString("codigoMaestro") != codigoNorm
            ) {
                return ResultadoVinculacion(
                    false,
                    "El código maestro no es válido o la información del centro " +
                        "es incoherente. Contacta con tu centro."
                )
            }

            val dniNorm = dni.trim().uppercase()

            // Declaración temporal de VÍA 1: permite a las Rules validar que el
            // índice consultado es exactamente { negocioId, dni } del propio uid.
            // Con merge no destruye el perfil completo guardado previamente.
            val declaracion = perfilPendienteRepository.guardarDeclaracion(uid, dniNorm, negocioId)
            if (!declaracion.exito) {
                return ResultadoVinculacion(false, declaracion.mensaje)
            }

            when (val indice = localizarFicha(negocioId, dniNorm)) {
                is ResultadoIndice.Ficha -> {
                    val resultado = vincularFichaExistente(
                        uid, indice.clienteId, negocioId, dniNorm
                    )
                    if (resultado.exito) {
                        perfilPendienteRepository.borrar(uid)
                    }
                    resultado
                }

                // El gimnasio no registró previamente a este DNI. VÍA 2: si el
                // cliente completó su perfil (No tengo vinculación) se crea la
                // ficha con esos datos; si no hay perfil completo se devuelve el
                // resultado "necesita completar perfil" para que la UI lo guíe.
                ResultadoIndice.NoExiste -> {
                    val perfil = leerPerfilPendiente(uid)
                    if (perfil != null &&
                        perfil.nombre.isNotBlank() &&
                        perfil.apellidos.isNotBlank() &&
                        perfil.telefono.isNotBlank()
                    ) {
                        val resultado = crearFicha(uid, negocioId, dniNorm, perfil)
                        if (resultado.exito) {
                            perfilPendienteRepository.borrar(uid)
                        }
                        resultado
                    } else {
                        resultadoNoHayFichaParaRegistro()
                    }
                }
            }
        } catch (e: Exception) {
            ResultadoVinculacion(false, mensajeDe(e))
        }
    }

    /**
     * leerPerfilPendiente
     * -------------------
     * Lee el perfil pendiente completo de Firestore. A diferencia de
     * PerfilPendienteRepository.leer(), NO traga las excepciones: un error de
     * permisos o de red se propaga para que no se interprete como "no hay perfil".
     */
    private suspend fun leerPerfilPendiente(uid: String): PerfilPendiente? {
        val documento = db.collection(COLECCION_PERFILES_PENDIENTES)
            .document(uid)
            .get()
            .esperar()
        if (!documento.exists()) return null
        return PerfilPendiente(
            nombre = documento.getString("nombre") ?: "",
            apellidos = documento.getString("apellidos") ?: "",
            dni = documento.getString("dni") ?: "",
            telefono = documento.getString("telefono") ?: "",
            email = documento.getString("email"),
            foto = documento.getString("foto") ?: "",
            fechaNacimiento = documento.getLong("fechaNacimiento")
        )
    }

    /**
     * vincularFichaExistente
     * ----------------------
     * VÍA 1: Transaction que escribe el UID en la ficha libre y actualiza
     * usuarios/{uid}. Si la ficha ya tiene UID, las Rules deniegan la escritura
     * y la Transaction falla.
     */
    private suspend fun vincularFichaExistente(
        uid: String,
        clienteId: Int,
        negocioId: String,
        dni: String
    ): ResultadoVinculacion {
        return try {
            val clienteRef = db.collection(COLECCION_CLIENTES).document(clienteId.toString())
            val usuarioRef = db.collection(COLECCION_USUARIOS).document(uid)

            db.runTransaction { transaction ->
                val ficha = transaction.get(clienteRef)
                if (!ficha.exists()) {
                    // El índice quedó huérfano: se ignora y se trata como VÍA 2.
                    throw FichaInexistenteException()
                }
                if (ficha.getString("firebaseUid") != null) {
                    throw DniYaVinculadoException()
                }
                transaction.update(clienteRef, mapOf("firebaseUid" to uid))
                transaction.update(
                    usuarioRef,
                    mapOf(
                        "clienteId" to clienteId,
                        "negocioId" to negocioId
                    )
                )
            }.esperar()

            // Regla definitiva de la foto en VÍA 1: si la ficha del ADMIN ya
            // tiene foto REMOTA, prevalece. Si NO tiene foto remota y el perfil
            // pendiente trae una foto local, se transfiere a Storage.
            transferirFotoPendienteSiProcede(uid, clienteId)

            // Aviso idempotente en la bandeja del ADMIN (notificaciones/{id}).
            notificarVinculacionAlAdmin(negocioId, clienteId)

            ResultadoVinculacion(
                true,
                "Te has vinculado a la ficha de tu centro",
                clienteId,
                negocioId
            )
        } catch (e: DniYaVinculadoException) {
            ResultadoVinculacion(false, "Ese DNI ya está vinculado a otra cuenta")
        } catch (e: FichaInexistenteException) {
            ResultadoVinculacion(false, "La ficha ya no existe. Inténtalo de nuevo")
        } catch (e: Exception) {
            ResultadoVinculacion(false, mensajeDe(e))
        }
    }

    /**
     * notificarVinculacionAlAdmin
     * ---------------------------
     * Crea (solo si no existe) una notificación en `notificaciones/{id}` para
     * la bandeja del ADMIN, con documentId determinista
     * `vinculacion_{negocioId}_{clienteId}` y tipo VINCULACION. El fallo de la
     * notificación nunca bloquea el éxito de la vinculación.
     */
    private suspend fun notificarVinculacionAlAdmin(
        negocioId: String,
        clienteId: Int
    ) {
        try {
            val ficha = db.collection(COLECCION_CLIENTES)
                .document(clienteId.toString())
                .get()
                .esperar()
            val nombre = listOf(ficha.getString("nombre"), ficha.getString("apellidos"))
                .filterNotNull()
                .joinToString(" ")
                .trim()
                .ifBlank { "Un cliente" }

            val notificacionId = "vinculacion_${negocioId}_$clienteId"
            val referencia = db.collection(COLECCION_NOTIFICACIONES)
                .document(notificacionId)
            if (referencia.get().esperar().exists()) return

            referencia.set(
                mapOf(
                    "negocioId" to negocioId,
                    "titulo" to "Cliente vinculado",
                    "mensaje" to "$nombre se ha vinculado al negocio.",
                    "tipo" to "VINCULACION",
                    "origen" to "AUTOMATICA",
                    "modoDestino" to "INDIVIDUAL",
                    "clienteId" to clienteId,
                    "fechaCreacion" to com.google.firebase.Timestamp.now(),
                    "estado" to "PENDIENTE"
                )
            ).esperar()
        } catch (_: Exception) {
            // No debe impedir que la vinculación se complete.
        }
    }

    /**
     * crearFicha
     * ----------
     * VÍA 2: Transaction que crea la ficha con los datos del perfil pendiente,
     * crea el índice negocio+DNI y actualiza usuarios/{uid}. La unicidad la
     * garantiza el documentId del índice: si otro cliente creó la ficha antes,
     * el set del índice colisiona y la Transaction se aborta.
     */
    private suspend fun crearFicha(
        uid: String,
        negocioId: String,
        dni: String,
        perfil: PerfilPendiente
    ): ResultadoVinculacion {
        val usuarioRef = db.collection(COLECCION_USUARIOS).document(uid)

        return try {
            repeat(MAX_INTENTOS_ID) { intento ->
                val idCliente = generarIdCliente()
                val fichaRef = db.collection(COLECCION_CLIENTES)
                    .document(idCliente.toString())
                val indiceRef = db.collection(COLECCION_INDICES)
                    .document(indiceId(negocioId, dni))

                try {
                    db.runTransaction { transaction ->
                        if (transaction.get(indiceRef).exists()) {
                            throw DniYaVinculadoException()
                        }
                        if (transaction.get(fichaRef).exists()) {
                            throw ColisionIdClienteException()
                        }
                        transaction.set(
                            fichaRef,
                            mapOf(
                                "idCliente" to idCliente,
                                "negocioId" to negocioId,
                                "firebaseUid" to uid,
                                "nombre" to perfil.nombre,
                                "apellidos" to perfil.apellidos,
                                "dni" to dni,
                                "telefono" to perfil.telefono,
                                "email" to perfil.email,
                                "foto" to "",
                                "fechaNacimiento" to perfil.fechaNacimiento,
                                "fechaRegistro" to com.google.firebase.Timestamp.now(),
                                "fechaAlta" to null,
                                "fechaBaja" to null,
                                "estado" to "REGISTRADO",
                                "serviciosContratados" to emptyList<Int>(),
                                "fechaInicioActual" to null,
                                "fechaFinActual" to null
                            )
                        )
                        transaction.set(
                            indiceRef,
                            mapOf(
                                "negocioId" to negocioId,
                                "dni" to dni,
                                "clienteId" to idCliente
                            )
                        )
                        transaction.update(
                            usuarioRef,
                            mapOf(
                                "clienteId" to idCliente,
                                "negocioId" to negocioId
                            )
                        )
                    }.esperar()

                    // VÍA 2: la ficha recién creada parte sin foto remota; si el
                    // perfil pendiente traía una foto local, se transfiere.
                    transferirFotoPendienteSiProcede(uid, idCliente)

                    // Aviso idempotente en la bandeja del ADMIN.
                    notificarVinculacionAlAdmin(negocioId, idCliente)

                    return ResultadoVinculacion(
                        true,
                        "Te has registrado en tu centro",
                        idCliente,
                        negocioId
                    )
                } catch (e: DniYaVinculadoException) {
                    return ResultadoVinculacion(
                        false,
                        "Ese DNI ya está vinculado a otra cuenta"
                    )
                } catch (e: ColisionIdClienteException) {
                    if (intento == MAX_INTENTOS_ID - 1) {
                        return ResultadoVinculacion(
                            false,
                            "No se pudo generar un identificador único. Inténtalo de nuevo"
                        )
                    }
                }
            }
            ResultadoVinculacion(false, "No se pudo crear la ficha. Inténtalo de nuevo")
        } catch (e: Exception) {
            ResultadoVinculacion(false, mensajeDe(e))
        }
    }

    /**
     * transferirFotoPendienteSiProcede
     * --------------------------------
     * Aplica la regla DEFINITIVA de la foto durante la vinculación:
     *  - Si la ficha (creada por el ADMIN o recién creada) ya tiene una foto
     *    REMOTA, esa prevalece: la foto local del perfil pendiente NO la
     *    sobrescribe.
     *  - Si la ficha NO tiene foto remota y el perfil pendiente tiene una foto
     *    local, se sube a `clientes/{clienteId}/foto.jpg` y se guarda la URL.
     *  - Si ninguno tiene foto, la ficha se queda sin foto.
     * Best-effort: un fallo (red, permisos) no bloquea la vinculación.
     */
    private suspend fun transferirFotoPendienteSiProcede(uid: String, clienteId: Int) {
        try {
            val ficha = db.collection(COLECCION_CLIENTES)
                .document(clienteId.toString())
                .get()
                .esperar()
            if (!ficha.exists()) return
            val fotoRemota = ficha.getString("foto")
            if (FotoClienteStorage.esUrlFoto(fotoRemota)) return
            val perfil = leerPerfilPendiente(uid) ?: return
            val fotoLocal = perfil.foto
            if (fotoLocal.isBlank()) return
            // GATE TÉRMINOS (VÍA 2): sin aceptación vigente NO se publica la foto.
            // No se borra el fichero local ni se rompe la vinculación: queda
            // pendiente para cuando los Términos estén aceptados.
            if (!preferencesRepository.terminosAceptados(uid)) return
            val url = FotoClienteStorage.subirFotoCliente(storage, clienteId, fotoLocal)
                ?: return
            db.collection(COLECCION_CLIENTES)
                .document(clienteId.toString())
                .update(mapOf("foto" to url))
                .esperar()
        } catch (_: Exception) {
            // Best-effort: no debe impedir que la vinculación se complete.
        }
    }

    private fun generarIdCliente(): Int {
        return Random.nextInt(ID_CLIENTE_MINIMO, Int.MAX_VALUE)
    }

    private class DniYaVinculadoException : Exception()
    private class FichaInexistenteException : Exception()
    private class ColisionIdClienteException : Exception()

    private fun mensajeDe(e: Exception): String {
        return when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "No tienes permisos para esta operación. Revisa el código y el DNI"

                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "No hay conexión con el servidor. Comprueba tu conexión a Internet"

                else -> e.message ?: "Error inesperado. Inténtalo de nuevo"
            }

            is FirebaseNetworkException ->
                "No hay conexión con el servidor. Comprueba tu conexión a Internet"

            else -> e.message ?: "Error inesperado. Inténtalo de nuevo"
        }
    }
}
