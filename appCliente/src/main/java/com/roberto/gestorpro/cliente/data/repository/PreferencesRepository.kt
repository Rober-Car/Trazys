package com.roberto.gestorpro.cliente.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roberto.gestorpro.cliente.util.TerminosDeUso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias_cliente")

private val TERMINOS_UID_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_UID)
private val TERMINOS_VERSION_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_VERSION)
private val TERMINOS_FECHA_KEY = stringPreferencesKey(TerminosDeUso.CLAVE_TERMINOS_FECHA)

/**
 * PreferencesRepository
 * ---------------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Encapsula DataStore de GestorPro Cliente: tema, id de la ficha vinculada,
 * negocioId del negocio al que pertenece y dni del perfil pendiente.
 */
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

        private val ID_CLIENTE_KEY = intPreferencesKey("id_cliente")
        private val NEGOCIO_ID_KEY = stringPreferencesKey("negocio_id")
        private val DNI_PENDIENTE_KEY = stringPreferencesKey("dni_pendiente")
        private val NOMBRE_NEGOCIO_KEY = stringPreferencesKey("nombre_negocio")
        private val LOGO_NEGOCIO_KEY = stringPreferencesKey("logo_negocio")
        private val NOTIFICACIONES_ACTIVADAS_KEY = booleanPreferencesKey("notificaciones_activadas")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: THEME_SISTEMA
    }

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

    val idCliente: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[ID_CLIENTE_KEY]
    }

    suspend fun setIdCliente(id: Int) {
        context.dataStore.edit { preferences ->
            preferences[ID_CLIENTE_KEY] = id
        }
    }

    suspend fun borrarIdCliente() {
        context.dataStore.edit { preferences ->
            preferences.remove(ID_CLIENTE_KEY)
        }
    }

    val negocioId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[NEGOCIO_ID_KEY]
    }

    suspend fun setNegocioId(negocioId: String) {
        context.dataStore.edit { preferences ->
            preferences[NEGOCIO_ID_KEY] = negocioId
        }
    }

    /**
     * borrarNegocioId
     * ---------------
     * Limpia el negocioId local. Se usa al cerrar sesión y al reconciliar el
     * estado local para que un usuario no herede el negocio de otra cuenta.
     */
    suspend fun borrarNegocioId() {
        context.dataStore.edit { preferences ->
            preferences.remove(NEGOCIO_ID_KEY)
        }
    }

    val dniPendiente: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DNI_PENDIENTE_KEY]
    }

    suspend fun setDniPendiente(dni: String) {
        context.dataStore.edit { preferences ->
            preferences[DNI_PENDIENTE_KEY] = dni.uppercase()
        }
    }

    suspend fun borrarDniPendiente() {
        context.dataStore.edit { preferences ->
            preferences.remove(DNI_PENDIENTE_KEY)
        }
    }

    val nombreNegocio: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NOMBRE_NEGOCIO_KEY] ?: ""
    }

    suspend fun setNombreNegocio(nombre: String) {
        context.dataStore.edit { preferences ->
            preferences[NOMBRE_NEGOCIO_KEY] = nombre.trim()
        }
    }

    /**
     * logoNegocio
     * -----------
     * ✔ TIPO: propiedad (val) → Flow<String>
     * Es el flujo que emite la URL del logo del negocio guardada como caché local
     * (vacía si no hay logo). La fuente de verdad es negocios_publicos/{id}.logo.
     */
    val logoNegocio: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LOGO_NEGOCIO_KEY] ?: ""
    }

    /**
     * setLogoNegocio
     * --------------
     * ✔ TIPO: método (fun) suspend de Kotlin
     * Guarda la URL del logo del negocio en DataStore como caché local.
     */
    suspend fun setLogoNegocio(url: String) {
        context.dataStore.edit { preferences ->
            preferences[LOGO_NEGOCIO_KEY] = url.trim()
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
     * notificacionesActivadas
     * -----------------------
     * Preferencia local del CLIENTE para recibir (o no) avisos del gimnasio.
     * Se persiste en DataStore (por dispositivo); por defecto activadas.
     */
    val notificacionesActivadas: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICACIONES_ACTIVADAS_KEY] ?: true
    }

    suspend fun setNotificacionesActivadas(activas: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICACIONES_ACTIVADAS_KEY] = activas
        }
    }
}
