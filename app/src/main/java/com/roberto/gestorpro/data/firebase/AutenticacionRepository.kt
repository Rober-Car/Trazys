package com.roberto.gestorpro.data.firebase

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.R
import com.roberto.gestorpro.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ResultadoAutenticacion
 * ---------------------
 * ✔ TIPO: data class de Kotlin
 * Es el resultado unificado de las operaciones de autenticación.
 * Sirve para que los ViewModels reciban siempre la misma estructura:
 * exito indica si la operación terminó bien, mensaje es el texto para la UI
 * y rol contiene el rol remoto leído del documento usuarios/{uid} cuando aplica.
 */
data class ResultadoAutenticacion(
    val exito: Boolean,
    val mensaje: String,
    val rol: String? = null
)

/**
 * AutenticacionRepository
 * -----------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt (data/firebase)
 * Es el repositorio que encapsula Firebase Authentication y la gestión remota
 * del documento usuarios/{uid}. Sirve para registrar cuentas nuevas, iniciar
 * sesión, cerrar sesión y comprobar si hay sesión activa, garantizando que el
 * documento remoto cumple las Security Rules vigentes (rol válido, activo true,
 * clienteId null y negocioId null al crear).
 */
@Singleton
class AutenticacionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
        const val ROL_ADMIN = "ADMIN"
        const val ROL_CLIENTE = "CLIENTE"

        private const val COLECCION_USUARIOS = "usuarios"
    }

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(@StringRes recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    private fun texto(@StringRes recurso: Int, vararg argumentos: Any): String =
        IdiomaAplicacion.textoDe(context, recurso, *argumentos)

    /**
     * haySesionActiva
     * ---------------
     * ✔ TIPO: método (fun) de Kotlin → Boolean
     * Indica si hay una sesión de Firebase persistida en el dispositivo.
     * Sirve a AppNavigation para saltarse el Login cuando el SDK restaura
     * automáticamente la sesión del último acceso.
     */
    fun haySesionActiva(): Boolean {
        return auth.currentUser != null
    }

    /**
     * uidActual
     * ---------
     * UID del usuario autenticado o null si no hay sesión. Sirve al guard de
     * cambio de propietario para comparar la cuenta autenticada con el
     * propietario de la caché local.
     */
    fun uidActual(): String? {
        return auth.currentUser?.uid
    }

    /**
     * reautenticar
     * ------------
     * Reautentica al usuario actual con su contraseña (EmailAuthProvider) para
     * permitir operaciones sensibles como la eliminación de la cuenta.
     */
    suspend fun reautenticar(password: String): Boolean {
        val usuario = auth.currentUser ?: return false
        val email = usuario.email ?: return false
        return try {
            usuario.reauthenticate(EmailAuthProvider.getCredential(email, password))
                .esperar()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * cambiarContrasena
     * -----------------
     * Cambia la contraseña del usuario autenticado en Firebase Authentication.
     *
     * Flujo seguro:
     *  1. Obtiene el usuario actual y su email (si no hay sesión, falla).
     *  2. Reautentica con la contraseña ACTUAL (EmailAuthProvider): valida la
     *     identidad y renueva la sesión para que `updatePassword` no falle por
     *     sesión demasiado antigua.
     *  3. SOLO si la reautenticación fue correcta llama a
     *     `FirebaseUser.updatePassword(nueva)`.
     *
     * La contraseña solo vive en memoria durante la operación: no se guarda en
     * Room, DataStore ni Firestore, y nunca se registra en logs. Ante cualquier
     * fallo de Firebase devuelve un resultado con exito = false (nunca se
     * presenta como éxito una operación que no llegó a completarse).
     */
    suspend fun cambiarContrasena(
        actual: String,
        nueva: String
    ): ResultadoAutenticacion {
        val usuario = auth.currentUser
            ?: return ResultadoAutenticacion(false, "No hay ningún usuario autenticado")
        val email = usuario.email
            ?: return ResultadoAutenticacion(
                false,
                "Esta cuenta no tiene un email asociado para reautenticar"
            )

        return try {
            // 1) Reautenticación con la contraseña actual.
            usuario.reauthenticate(EmailAuthProvider.getCredential(email, actual))
                .esperar()

            // 2) Solo tras una reautenticación correcta se cambia la contraseña.
            usuario.updatePassword(nueva).esperar()

            ResultadoAutenticacion(true, "Contraseña actualizada correctamente")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            ResultadoAutenticacion(false, "La contraseña actual no es correcta")
        } catch (e: FirebaseAuthInvalidUserException) {
            ResultadoAutenticacion(
                false,
                "El usuario ya no existe o su cuenta ya no está disponible"
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            ResultadoAutenticacion(
                false,
                "La nueva contraseña debe tener al menos 6 caracteres"
            )
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            ResultadoAutenticacion(
                false,
                "Tu sesión es demasiado antigua. Vuelve a iniciar sesión e inténtalo de nuevo"
            )
        } catch (e: FirebaseNetworkException) {
            ResultadoAutenticacion(
                false,
                "No hay conexión con Firebase. Comprueba tu conexión a Internet"
            )
        } catch (e: Exception) {
            ResultadoAutenticacion(false, "No se pudo cambiar la contraseña. Inténtalo de nuevo")
        }
    }

    /**
     * registrar
     * ---------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Crea la cuenta en Firebase Authentication y, a continuación, el documento
     * usuarios/{uid} con rol, activo true, clienteId null y negocioId null,
     * exactamente lo que exigen las Security Rules. Si la escritura remota
     * falla, elimina la cuenta recién creada para no dejar cuentas huérfanas.
     * Sirve como alta real tanto del perfil ADMINISTRADOR como del CLIENTE.
     */
    suspend fun registrar(
        email: String,
        contrasena: String,
        rol: String
    ): ResultadoAutenticacion {
        return try {
            val credencial = auth.createUserWithEmailAndPassword(email, contrasena).esperar()
            val usuario = credencial.user

            if (usuario == null) {
                ResultadoAutenticacion(false, texto(R.string.auth_error_no_crear_cuenta))
            } else {
                try {
                    db.collection(COLECCION_USUARIOS)
                        .document(usuario.uid)
                        .set(
                            mapOf(
                                "rol" to rol,
                                "activo" to true,
                                "clienteId" to null,
                                "negocioId" to null
                            )
                        )
                        .esperar()

                    ResultadoAutenticacion(true, texto(R.string.auth_cuenta_creada_ok), rol)
                } catch (e: Exception) {
                    try {
                        usuario.delete().esperar()
                    } catch (_: Exception) {
                    }
                    ResultadoAutenticacion(
                        false,
                        texto(R.string.auth_error_no_perfil_usuario, mensajeDe(e, false))
                    )
                }
            }
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e, false))
        }
    }

    /**
     * iniciarSesion
     * -------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Inicia sesión en Firebase Authentication y comprueba el documento
     * usuarios/{uid}: si no existe o está desactivado cierra la sesión y lo
     * comunica. Sirve como acceso real de la app respetando el campo activo
     * que controlan las Security Rules.
     */
    suspend fun iniciarSesion(
        email: String,
        contrasena: String
    ): ResultadoAutenticacion {
        return try {
            val credencial = auth.signInWithEmailAndPassword(email, contrasena).esperar()
            val usuario = credencial.user

            if (usuario == null) {
                cerrarSesion()
                return ResultadoAutenticacion(false, texto(R.string.auth_error_no_iniciar_sesion))
            }

            try {
                val documento = db.collection(COLECCION_USUARIOS)
                    .document(usuario.uid)
                    .get()
                    .esperar()

                if (!documento.exists()) {
                    cerrarSesion()
                    ResultadoAutenticacion(
                        false,
                        texto(R.string.auth_error_usuario_sin_perfil)
                    )
                } else if (documento.getBoolean("activo") != true) {
                    cerrarSesion()
                    ResultadoAutenticacion(false, texto(R.string.auth_error_cuenta_desactivada))
                } else {
                    ResultadoAutenticacion(
                        true,
                        texto(R.string.auth_sesion_iniciada_ok),
                        documento.getString("rol")
                    )
                }
            } catch (e: Exception) {
                cerrarSesion()
                ResultadoAutenticacion(
                    false,
                    texto(R.string.auth_error_no_leer_perfil, mensajeDe(e, true))
                )
            }
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e, true))
        }
    }

    /**
     * cerrarSesion
     * ------------
     * ✔ TIPO: método (fun) de Kotlin
     * Cierra la sesión de Firebase Authentication. No borra nada de DataStore:
     * el tipo de usuario y el registro local se conservan para el próximo acceso.
     * Sirve a las opciones "Cerrar sesión" de Cuenta y Preferencias.
     */
    fun cerrarSesion() {
        auth.signOut()
    }

    /**
     * enviarCorreoRecuperacion
     * ------------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → ResultadoAutenticacion
     * Envía el correo de restablecimiento de contraseña con
     * FirebaseAuth.sendPasswordResetEmail. Siguiendo la política de no revelar
     * si un email está registrado, ante cualquier error de autenticación se
     * responde con el mismo mensaje genérico de éxito; solo se comunica un
     * fallo real de la operación (p. ej. sin conexión a Internet).
     * Sirve a la pantalla de recuperación de contraseña accesible desde el Login.
     */
    suspend fun enviarCorreoRecuperacion(email: String): ResultadoAutenticacion {
        return try {
            auth.sendPasswordResetEmail(email).esperar()
            ResultadoAutenticacion(true, texto(R.string.auth_recuperar_exito))
        } catch (e: Exception) {
            when (e) {
                is FirebaseAuthInvalidUserException,
                is FirebaseAuthInvalidCredentialsException,
                is FirebaseAuthUserCollisionException,
                is FirebaseAuthWeakPasswordException ->
                    ResultadoAutenticacion(true, texto(R.string.auth_recuperar_exito))
                is FirebaseNetworkException ->
                    ResultadoAutenticacion(
                        false,
                        texto(R.string.auth_error_sin_conexion)
                    )
                else ->
                    ResultadoAutenticacion(
                        false,
                        texto(R.string.auth_error_no_enviar_correo)
                    )
            }
        }
    }

    /**
     * mensajeDe
     * ---------
     * ✔ TIPO: método (fun) privado de Kotlin → String
     * Traduce las excepciones de Firebase a mensajes comprensibles en el idioma
     * de la app, distinguiendo entre inicio de sesión y registro.
     * Sirve para que la UI nunca muestre textos técnicos en inglés.
     */
    private fun mensajeDe(e: Exception, alIniciarSesion: Boolean): String {
        return when (e) {
            is FirebaseAuthUserCollisionException ->
                texto(R.string.auth_error_email_ya_existe)
            is FirebaseAuthWeakPasswordException ->
                texto(R.string.auth_error_contrasena_debil)
            is FirebaseAuthInvalidUserException ->
                texto(R.string.auth_error_cuenta_no_existe)
            is FirebaseAuthInvalidCredentialsException ->
                if (alIniciarSesion) {
                    texto(R.string.auth_error_credenciales)
                } else {
                    texto(R.string.auth_error_email_formato)
                }
            else -> e.message ?: texto(R.string.auth_error_inesperado)
        }
    }
}

/**
 * esperar
 * -------
 * ✔ TIPO: función de extensión privada (suspend) sobre Task<T>
 * Convierte cualquier Task de Firebase en una llamada suspendible sin añadir
 * dependencias externas (equivalente ligero de Task.await()). Devuelve el
 * resultado T cuando la tarea termina bien y lanza la excepción si falla.
 * Sirve para poder usar el SDK de Firebase desde corrutinas de forma limpia.
 * Es internal para reutilizarse desde los demás repositorios de data/firebase.
 */
internal suspend fun <T> Task<T>.esperar(): T =
    suspendCancellableCoroutine { continuacion ->
        addOnSuccessListener { resultado ->
            continuacion.resume(resultado)
        }
        addOnFailureListener { error ->
            continuacion.resumeWithException(error)
        }
    }

/**
 * validarCambioContrasena
 * -----------------------
 * Validación pura (sin Firebase) de los datos del formulario de cambio de
 * contraseña. Devuelve el error en español o null si los datos son válidos.
 * Se usa en el ViewModel y en el diálogo, y se testea sin Firebase real.
 */
fun validarCambioContrasena(actual: String, nueva: String, repetida: String): String? = when {
    actual.isBlank() -> "Introduce tu contraseña actual"
    nueva.length < 6 -> "La nueva contraseña debe tener al menos 6 caracteres"
    nueva != repetida -> "Las contraseñas nuevas no coinciden"
    else -> null
}
