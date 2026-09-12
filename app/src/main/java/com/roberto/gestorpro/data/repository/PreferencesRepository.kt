package com.roberto.gestorpro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roberto.gestorpro.model.TipoUsuario
import com.roberto.gestorpro.util.TerminosDeUso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

private val TERMINOS_UID_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_UID)
private val TERMINOS_VERSION_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_VERSION)
private val TERMINOS_FECHA_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_FECHA)

@Singleton
class PreferencesRepository @Inject constructor(
    private val context: Context
) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        const val THEME_CLARO = "claro"
        const val THEME_OSCURO = "oscuro"
        const val THEME_SISTEMA = "sistema"

        private val IDIOMA_KEY = stringPreferencesKey("idioma")
        const val IDIOMA_ES = "es"
        const val IDIOMA_EN = "en"
        const val IDIOMA_DEFECTO = IDIOMA_ES

        /**
         * TIPO_USUARIO_KEY
         * ----------------
         * ✔ TIPO: propiedad (private val) → Preferences.Key<String>
         * Es la clave bajo la que se guarda el tipo de usuario elegido en DataStore.
         * Sirve para recordar si la app se usa como Administrador o Cliente;
         * si la clave no existe, es que el usuario aún no ha hecho la elección.
         */
        private val TIPO_USUARIO_KEY = stringPreferencesKey("tipo_usuario")

        /**
         * NOMBRE_NEGOCIO_KEY
         * ------------------
         * ✔ TIPO: propiedad (private val) → Preferences.Key<String>
         * Es la clave bajo la que se guarda el nombre del negocio en DataStore.
         * Sirve para personalizar la app mostrando ese nombre en Home y Login;
         * si está vacío, se muestra "GestorPro" como valor por defecto.
         */
        private val NOMBRE_NEGOCIO_KEY = stringPreferencesKey("nombre_negocio")

        /**
         * LOGO_NEGOCIO_KEY
         * ------------------
         * ✔ TIPO: propiedad (private val) → Preferences.Key<String>
         * Es la clave bajo la que se guarda la ruta del logo del negocio en DataStore.
         * Sirve para mostrar el logo configurado en Home y Login; si está vacía,
         * se muestra el icono por defecto de GestorPro.
         */
        private val LOGO_NEGOCIO_KEY = stringPreferencesKey("logo_negocio")

        /**
         * ID_CLIENTE_SESION_KEY
         * ---------------------
         * ✔ TIPO: propiedad (private val) → Preferences.Key<Int>
         * Es la clave bajo la que se guarda el id del cliente registrado desde este dispositivo.
         * Sirve para vincular el registro hecho por un usuario con perfil CLIENTE
         * (pantalla Mi perfil) con su fila en la tabla cliente; si no existe la clave,
         * es que ese dispositivo aún no tiene registro de cliente y debe mostrarse
         * el formulario de alta.
         */
        private val ID_CLIENTE_SESION_KEY = intPreferencesKey("id_cliente_sesion")

        /**
         * UID_PROPIETARIO_KEY
         * --------------------
         * UID del ADMIN (o de su negocio, negocioId == uid) al que pertenece la
         * caché local (Room + ficheros + identidad de DataStore). Sirve para
         * detectar un cambio de cuenta en el mismo dispositivo y limpiar la
         * caché antes de que un segundo ADMIN vea datos ajenos. NUNCA se borra
         * en el logout; solo se actualiza al adoptar/limpiar la caché.
         */
        private val UID_PROPIETARIO_KEY = stringPreferencesKey("uid_propietario_datos_locales")

        /**
         * CACHE_HIDRATADA_UID_KEY
         * ------------------------
         * UID del ADMIN cuyo Room ya se reconstruyó desde Firestore (hidratación
         * central). Sirve para no repetir la reconstrucción en cada login de la
         * misma cuenta (MismaCuenta) y para reintentarla automáticamente si un
         * intento anterior falló (p. ej. sin conexión): solo se escribe tras una
         * hidratación COMPLETA y sin errores. NO es identidad del negocio, por lo
         * que no se borra en el logout ni en limpiarIdentidadNegocio.
         */
        private val CACHE_HIDRATADA_UID_KEY = stringPreferencesKey("cache_hidratada_uid")
    }

    /**
     * themeMode
     * ---------
     * ✔ TIPO: propiedad (val) → Flow<String>
     * Es el flujo que emite el modo de tema guardado.
     * Sirve para aplicar claro/oscuro/sistema en toda la app.
     */
    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: THEME_SISTEMA
    }

    /**
     * setThemeMode
     * ------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que guarda el modo de tema en DataStore.
     * Sirve para persistir la preferencia elegida en PreferenciasScreen.
     */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    /**
     * idioma
     * ------
     * Idioma elegido por el usuario ("es" o "en"). Por defecto "es".
     * Sigue el mismo patrón que themeMode (persistido en DataStore).
     */
    val idioma: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IDIOMA_KEY] ?: IDIOMA_DEFECTO
    }

    /**
     * setIdioma
     * ---------
     * Persiste el idioma elegido en DataStore.
     */
    suspend fun setIdioma(idioma: String) {
        context.dataStore.edit { preferences ->
            preferences[IDIOMA_KEY] = idioma
        }
    }

    /**
     * obtenerIdioma
     * -------------
     * Lectura única (sin observar) del idioma guardado. Se usa en el arranque
     * de la aplicación para aplicar el locale antes del primer frame.
     */
    suspend fun obtenerIdioma(): String {
        return context.dataStore.data.first()[IDIOMA_KEY] ?: IDIOMA_DEFECTO
    }

    /**
     * tipoUsuario
     * -----------
     * ✔ TIPO: propiedad (val) → Flow<TipoUsuario?> (nullable)
     * Es el flujo que emite el tipo de usuario guardado, o null si nunca se eligió.
     * Sirve para decidir al arrancar si mostrar la pantalla de selección
     * ("¿Cómo vas a utilizar GestorPro?") o ir directo al Login.
     * Si el texto guardado no corresponde a ningún valor del enum, devuelve null
     * para que el usuario vuelva a elegir (evita fallos por datos corruptos).
     */
    val tipoUsuario: Flow<TipoUsuario?> = context.dataStore.data.map { preferences ->
        preferences[TIPO_USUARIO_KEY]?.let { guardado ->
            try {
                TipoUsuario.valueOf(guardado)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * setTipoUsuario
     * --------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que guarda el tipo de usuario elegido en DataStore.
     * Sirve para persistir la elección hecha en SeleccionTipoUsuarioScreen
     * y no volver a preguntarla en futuras aperturas de la app.
     */
    suspend fun setTipoUsuario(tipo: TipoUsuario) {
        context.dataStore.edit { preferences ->
            preferences[TIPO_USUARIO_KEY] = tipo.name
        }
    }

    /**
     * restablecerTipoUsuario
     * ----------------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que borra la clave de tipo de usuario de DataStore.
     * Sirve para que, al cambiar el tipo desde Configuración > Cuenta,
     * la pantalla de selección vuelva a mostrarse en el próximo arranque.
     */
    suspend fun restablecerTipoUsuario() {
        context.dataStore.edit { preferences ->
            preferences.remove(TIPO_USUARIO_KEY)
        }
    }

    /**
     * idClienteSesion
     * ---------------
     * ✔ TIPO: propiedad (val) → Flow<Int?> (nullable)
     * Es el flujo que emite el id del cliente registrado en este dispositivo,
     * o null si todavía no se ha registrado ninguno.
     * Sirve para que las pantallas del perfil de cliente reaccionen en tiempo real:
     * si hay id muestran la ficha con sus datos y si no lo hay ofrecen registrarse.
     */
    val idClienteSesion: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[ID_CLIENTE_SESION_KEY]
    }

    /**
     * obtenerIdClienteSesion
     * ----------------------
     * ✔ TIPO: método (fun) suspend de Kotlin → Int?
     * Es la lectura única (sin observar) del id del cliente registrado en este dispositivo.
     * Sirve para comprobaciones puntuales dentro de los ViewModels
     * (por ejemplo al guardar el registro o al entrar en Mi perfil)
     * siguiendo el mismo patrón que obtenerTipoUsuario().
     */
    suspend fun obtenerIdClienteSesion(): Int? {
        return context.dataStore.data.map { preferences ->
            preferences[ID_CLIENTE_SESION_KEY]
        }.first()
    }

    /**
     * setIdClienteSesion
     * ------------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que guarda el id del cliente recién registrado en DataStore.
     * Sirve para que, tras completar el alta desde la pantalla Mi perfil,
     * el dispositivo recuerde qué fila de la tabla cliente pertenece a este usuario
     * y pueda abrirla directamente en los siguientes accesos.
     */
    suspend fun setIdClienteSesion(id: Int) {
        context.dataStore.edit { preferences ->
            preferences[ID_CLIENTE_SESION_KEY] = id
        }
    }

    /**
     * borrarIdClienteSesion
     * ---------------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que borra el id del cliente guardado en DataStore.
     * Sirve para volver al estado "sin registro" cuando el administrador elimina
     * al cliente de la base de datos, obligando a registrarse de nuevo
     * la próxima vez que entre en Mi perfil.
     */
    suspend fun borrarIdClienteSesion() {
        context.dataStore.edit { preferences ->
            preferences.remove(ID_CLIENTE_SESION_KEY)
        }
    }

    /**
     * nombreNegocio
     * -------------
     * ✔ TIPO: propiedad (val) → Flow<String>
     * Es el flujo que emite el nombre del negocio guardado (vacío si no se configuró).
     * Sirve para mostrar el nombre en las cabeceras de Home y Login;
     * cuando está vacío, las pantallas muestran "GestorPro" por defecto.
     */
    val nombreNegocio: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NOMBRE_NEGOCIO_KEY] ?: ""
    }

    /**
     * logoNegocio
     * -----------
     * ✔ TIPO: propiedad (val) → Flow<String>
     * Es el flujo que emite la ruta del archivo del logo del negocio (vacía si no hay logo).
     * Sirve para cargar el logo con Coil en Home y Login; cuando está vacía,
     * esas pantallas muestran el icono por defecto de GestorPro.
     */
    val logoNegocio: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LOGO_NEGOCIO_KEY] ?: ""
    }

    /**
     * setNombreNegocio
     * ----------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que guarda el nombre del negocio en DataStore.
     * Sirve para persistir lo escrito en MiNegocioScreen y verlo reflejado
     * en Home y Login inmediatamente.
     */
    suspend fun setNombreNegocio(nombre: String) {
        context.dataStore.edit { preferences ->
            preferences[NOMBRE_NEGOCIO_KEY] = nombre.trim()
        }
    }

    /**
     * setLogoNegocio
     * --------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Es la operación que guarda la ruta del logo del negocio en DataStore.
     * Sirve para persistir el logo elegido en MiNegocioScreen (o vaciarlo
     * al quitarlo) y verlo reflejado en Home y Login inmediatamente.
     */
    suspend fun setLogoNegocio(ruta: String) {
        context.dataStore.edit { preferences ->
            preferences[LOGO_NEGOCIO_KEY] = ruta
        }
    }

    /**
     * terminosAceptados
     * -----------------
     * ¿El usuario indicado aceptó la versión VIGENTE de los Términos de uso?
     */
    suspend fun terminosAceptados(uid: String): Boolean {
        val prefs = context.dataStore.data.first()
        return TerminosDeUso.aceptado(
            uidGuardado = prefs[TERMINOS_UID_KEY],
            versionGuardada = prefs[TERMINOS_VERSION_KEY],
            uidActual = uid
        )
    }

    /**
     * guardarAceptacionTerminos
     * -------------------------
     * Persiste la aceptación (uid + versión vigente + fecha) en DataStore.
     */
    suspend fun guardarAceptacionTerminos(uid: String) {
        context.dataStore.edit { prefs ->
            prefs[TERMINOS_UID_KEY] = uid
            prefs[TERMINOS_VERSION_KEY] = TerminosDeUso.VERSION
            prefs[TERMINOS_FECHA_KEY] = System.currentTimeMillis().toString()
        }
    }

    /**
     * uidPropietario
     * --------------
     * UID del ADMIN al que pertenece la caché local, o null si todavía no se ha
     * fijado. Sirve al guard de cambio de cuenta para decidir entre conservar,
     * adoptar o limpiar los datos locales.
     */
    val uidPropietario: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[UID_PROPIETARIO_KEY]
    }

    /**
     * obtenerUidPropietario
     * ---------------------
     * Lectura única (sin observar) del UID propietario de la caché local.
     */
    suspend fun obtenerUidPropietario(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[UID_PROPIETARIO_KEY]
        }.first()
    }

    /**
     * setUidPropietario
     * -----------------
     * Fija el UID del ADMIN propietario de la caché local. Se escribe al
     * adoptar la caché para una cuenta o tras limpiarla para la cuenta nueva.
     */
    suspend fun setUidPropietario(uid: String) {
        context.dataStore.edit { preferences ->
            preferences[UID_PROPIETARIO_KEY] = uid
        }
    }

    /**
     * obtenerUidCacheHidratada
     * ------------------------
     * UID del ADMIN que ya tiene su Room reconstruido desde Firestore (o null
     * si todavía no se ha completado ninguna hidratación).
     */
    suspend fun obtenerUidCacheHidratada(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[CACHE_HIDRATADA_UID_KEY]
        }.first()
    }

    /**
     * setUidCacheHidratada
     * --------------------
     * Marca que la caché Room del ADMIN indicado ya se reconstruyó desde
     * Firestore sin errores. Solo se llama al terminar la hidratación completa.
     */
    suspend fun setUidCacheHidratada(uid: String) {
        context.dataStore.edit { preferences ->
            preferences[CACHE_HIDRATADA_UID_KEY] = uid
        }
    }

    /**
     * borrarUidCacheHidratada
     * -----------------------
     * Elimina el marcador de caché hidratada. Se usa al crear el negocio (una
     * cuenta que antes no tenía negocio pudo marcar su caché como hidratada sin
     * datos; al crear el negocio debe poder volver a hidratarse si procede).
     */
    suspend fun borrarUidCacheHidratada() {
        context.dataStore.edit { preferences ->
            preferences.remove(CACHE_HIDRATADA_UID_KEY)
        }
    }

    /**
     * limpiarIdentidadNegocio
     * -----------------------
     * Borra las claves de DataStore ligadas a la IDENTIDAD del negocio de la
     * cuenta anterior: nombre, logo e id de cliente de sesión. NO borra
     * theme_mode (preferencia del dispositivo) ni el tipo de usuario.
     */
    suspend fun limpiarIdentidadNegocio() {
        context.dataStore.edit { preferences ->
            preferences.remove(NOMBRE_NEGOCIO_KEY)
            preferences.remove(LOGO_NEGOCIO_KEY)
            preferences.remove(ID_CLIENTE_SESION_KEY)
        }
    }

    /**
     * limpiarCuentaCompleta
     * ---------------------
     * Limpieza total de la identidad local tras eliminar la cuenta/negocio:
     * identidad del negocio, id de cliente de sesión, tipo de usuario,
     * aceptación de términos y marcadores de propietario/caché hidratada. NO
     * borra theme_mode ni idioma (preferencias del dispositivo).
     */
    suspend fun limpiarCuentaCompleta() {
        context.dataStore.edit { preferences ->
            preferences.remove(NOMBRE_NEGOCIO_KEY)
            preferences.remove(LOGO_NEGOCIO_KEY)
            preferences.remove(ID_CLIENTE_SESION_KEY)
            preferences.remove(TIPO_USUARIO_KEY)
            preferences.remove(TERMINOS_UID_KEY)
            preferences.remove(TERMINOS_VERSION_KEY)
            preferences.remove(TERMINOS_FECHA_KEY)
            preferences.remove(UID_PROPIETARIO_KEY)
            preferences.remove(CACHE_HIDRATADA_UID_KEY)
        }
    }
}