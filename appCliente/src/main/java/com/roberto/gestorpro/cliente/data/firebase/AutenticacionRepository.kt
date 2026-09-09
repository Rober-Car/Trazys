package com.roberto.gestorpro.cliente.data.firebase

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
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ResultadoAutenticacion
 * ---------------------
 * ✔ TIPO: data class
 * Resultado unificado de las operaciones de autenticación.
 */
data class ResultadoAutenticacion(
    val exito: Boolean,
    val mensaje: String,
    val rol: String? = null
)

/**
 * AutenticacionRepository
 * -----------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Encapsula Firebase Authentication y el documento usuarios/{uid} del CLIENTE.
 */
@Singleton
class AutenticacionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    companion object {
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

    fun haySesionActiva(): Boolean {
        return auth.currentUser != null
    }

    /**
     * reautenticar
     * ------------
     * Reautentica al usuario actual con su contraseña antes de operaciones
     * sensibles (eliminación de cuenta).
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
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.vinculacion_error_sin_sesion)
            )
        val email = usuario.email
            ?: return ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_sin_email_reautenticar)
            )

        return try {
            // 1) Reautenticación con la contraseña actual.
            usuario.reauthenticate(EmailAuthProvider.getCredential(email, actual))
                .esperar()

            // 2) Solo tras una reautenticación correcta se cambia la contraseña.
            usuario.updatePassword(nueva).esperar()

            ResultadoAutenticacion(
                true,
                texto(R.string.cuenta_snackbar_contrasena_actualizada)
                    .removeSuffix(".")
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_contrasena_actual_incorrecta)
            )
        } catch (e: FirebaseAuthInvalidUserException) {
            ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_usuario_no_disponible)
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_nueva_corta)
            )
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_sesion_antigua)
            )
        } catch (e: FirebaseNetworkException) {
            ResultadoAutenticacion(
                false,
                texto(R.string.auth_error_sin_conexion)
            )
        } catch (e: Exception) {
            ResultadoAutenticacion(
                false,
                texto(R.string.cuenta_error_cambio_fallido)
            )
        }
    }

    /**
     * registrar
     * ---------
     * Crea la cuenta en Firebase Authentication y el documento usuarios/{uid}
     * con rol CLIENTE, activo true, clienteId null y negocioId null.
     */
    suspend fun registrar(
        email: String,
        contrasena: String
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
                                "rol" to ROL_CLIENTE,
                                "activo" to true,
                                "clienteId" to null,
                                "negocioId" to null
                            )
                        )
                        .esperar()
                    ResultadoAutenticacion(
                        true,
                        texto(R.string.auth_cuenta_creada_ok),
                        ROL_CLIENTE
                    )
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
     * Inicia sesión y comprueba el documento usuarios/{uid}.
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
                } else if (documento.getString("rol") != ROL_CLIENTE) {
                    cerrarSesion()
                    ResultadoAutenticacion(
                        false,
                        texto(R.string.auth_error_no_pertenece_a_app)
                    )
                } else {
                    ResultadoAutenticacion(
                        true,
                        texto(R.string.auth_sesion_iniciada_ok),
                        ROL_CLIENTE
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

    fun cerrarSesion() {
        auth.signOut()
    }

    /**
     * enviarCorreoRecuperacion
     * ------------------------
     * Envía el correo de recuperación con sendPasswordResetEmail. Ante errores
     * de autenticación responde el mismo mensaje genérico (no revela existencia).
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
 * ✔ TIPO: función de extensión internal (suspend) sobre Task<T>
 * Convierte un Task de Firebase en una llamada suspendible.
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
