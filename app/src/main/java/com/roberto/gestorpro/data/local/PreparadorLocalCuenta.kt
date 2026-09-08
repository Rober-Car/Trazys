package com.roberto.gestorpro.data.local

import android.content.Context
import com.roberto.gestorpro.data.database.ClientesDatabase
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.data.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PreparadorLocalCuenta
 * ---------------------
 * Capa SOLO de datos (no conoce ViewModels ni UI) que decide el estado de la
 * caché local (Room + ficheros + identidad de DataStore) según el propietario
 * autenticado.
 *
 * Regla de negocio de la instalación Admin: la caché local pertenece a UN solo
 * propietario (su UID, que es su negocioId). Si el propietario cambia, la caché
 * se borra y se reconstruye desde la nube del nuevo negocio. Si no se puede
 * determinar el propietario y hay datos locales, NUNCA se adoptan
 * automáticamente: se devuelve INDETERMINADO para que la capa UI decida.
 *
 * NO ejecuta operaciones remotas: nunca sincroniza pendientes de una cuenta
 * bajo otra. Los pendientes solo los reintenta su propio propietario.
 */
@Singleton
class PreparadorLocalCuenta @Inject constructor(
    private val database: ClientesDatabase,
    private val preferences: PreferencesRepository,
    private val movimientoRepository: MovimientoRepository,
    @ApplicationContext private val context: Context
) {

    /**
     * ResultadoResolver
     * -----------------
     * Resultado de resolver(uid). Los casos que mutan (AdoptadoSilencioso y
     * CambioCompletado) ya dejan la caché coherente con el nuevo propietario.
     */
    sealed interface ResultadoResolver {
        /** No hay sesión: no se toca nada. */
        object SinSesion : ResultadoResolver

        /** owner == cuenta autenticada: misma cuenta, no se toca nada. */
        object MismaCuenta : ResultadoResolver

        /** owner == null y Room vacía: se adopta silenciosamente la cuenta. */
        object AdoptadoSilencioso : ResultadoResolver

        /**
         * owner == null con datos locales: propietario indeterminado. No se
         * adopta ni se borra; la UI debe decidir (recomendado: empezar de cero).
         */
        data class Indeterminado(val pendientes: InformePendientes) : ResultadoResolver

        /**
         * owner distinto con pendientes críticos (eliminaciones o movimientos
         * económicos sin confirmar): bloqueado. Nunca se reintentan bajo la
         * nueva cuenta; la UI ofrece volver a la cuenta anterior o descartar.
         */
        data class Bloqueado(val pendientes: InformePendientes) : ResultadoResolver

        /** owner distinto sin pendientes: WIPE completo + nuevo owner aplicado. */
        object CambioCompletado : ResultadoResolver
    }

    /**
     * InformePendientes
     * -----------------
     * Resumen de operaciones locales pendientes de confirmar en la nube que
     * podrían perderse con un WIPE. La detección usa los mecanismos existentes:
     * eliminaciones persistidas (tabla eliminacion_pendiente) y marcadores
     * económicos en memoria (MovimientoRepository). No crea cola nueva.
     */
    data class InformePendientes(
        val eliminacionesPendientes: Int,
        val clientesEconomicosPendientes: Int
    ) {
        val total: Int get() = eliminacionesPendientes + clientesEconomicosPendientes
        fun hayAlgo(): Boolean = total > 0
    }

    /**
     * resolver
     * --------
     * Decide qué hacer con la caché local según la cuenta autenticada.
     * Aplica únicamente la lógica segura y automática; los casos ambiguos o
     * bloqueados se devuelven sin mutar para que la capa UI decida.
     */
    suspend fun resolver(uid: String?): ResultadoResolver {
        if (uid == null) return ResultadoResolver.SinSesion

        val owner = preferences.obtenerUidPropietario()
        if (owner == uid) return ResultadoResolver.MismaCuenta

        val hayDatos = hayDatosLocales()
        if (owner == null) {
            return if (hayDatos) {
                ResultadoResolver.Indeterminado(reportarPendientes())
            } else {
                // Instalación nueva / Room vacía: no hay nada que aislar, pero
                // la identidad visual de DataStore (nombre/logo) podría ser de
                // una cuenta anterior sin propietario registrado. Se limpia
                // antes de adoptar para no heredar branding ajeno.
                preferences.limpiarIdentidadNegocio()
                preferences.setUidPropietario(uid)
                ResultadoResolver.AdoptadoSilencioso
            }
        }

        // owner != uid: cambio de cuenta en el mismo dispositivo.
        val pendientes = reportarPendientes()
        return if (pendientes.hayAlgo()) {
            // Nunca reintentar bajo la cuenta nueva; se bloquea el cambio.
            ResultadoResolver.Bloqueado(pendientes)
        } else {
            ejecutarWipe(uid)
            ResultadoResolver.CambioCompletado
        }
    }

    /**
     * adoptarDatos
     * ------------
     * Conserva los datos locales (Room, identidad y ficheros) y los asigna a la
     * cuenta indicada. Solo debe llamarse por decisión EXPLÍCITA del usuario en
     * el caso INDETERMINADO (asume que los datos son suyos). Limpia los
     * marcadores económicos en memoria para no reintentar nada de una cuenta
     * desconocida bajo la nueva.
     */
    suspend fun adoptarDatos(uid: String) {
        movimientoRepository.resetEstadoEnMemoria()
        preferences.setUidPropietario(uid)
    }

    /**
     * wipeYAdoptar
     * ------------
     * Borra por completo la caché local (Room, ficheros e identidad de
     * DataStore), resetea los estados en memoria y asigna la caché al nuevo
     * propietario. Es la vía segura por defecto para un cambio de cuenta o
     * para un propietario indeterminado ("empezar con los datos de mi cuenta").
     */
    suspend fun wipeYAdoptar(uid: String) {
        ejecutarWipe(uid)
    }

    /**
     * reportarPendientes
     * ------------------
     * Detecta operaciones locales pendientes de confirmar en la nube:
     * eliminaciones persistidas + clientes económicos con operación fallida en
     * memoria. Solo lectura, no muta nada.
     */
    suspend fun reportarPendientes(): InformePendientes {
        val eliminaciones = database.eliminacionPendienteDao().contarTodos()
        val clientesEconomia = movimientoRepository.pendientesEconomicosMemoria.size
        return InformePendientes(
            eliminacionesPendientes = eliminaciones,
            clientesEconomicosPendientes = clientesEconomia
        )
    }

    /**
     * hayDatosLocales
     * ---------------
     * Indica si Room contiene datos (de cualquier tabla de negocio). Si no hay
     * ninguna fila no hay caché que aislar ni que perder.
     */
    private suspend fun hayDatosLocales(): Boolean {
        return database.clienteDao().contarTodos() > 0 ||
            database.movimientoDao().contarTodos() > 0 ||
            database.gastoDao().contarTodos() > 0 ||
            database.servicioDao().contarTodos() > 0 ||
            database.sesionDao().contarTodos() > 0 ||
            database.reservaDao().contarTodos() > 0 ||
            database.eliminacionPendienteDao().contarTodos() > 0
    }

    /**
     * ejecutarWipe
     * ------------
     * WIPE COMPLETO de la caché local en el orden del plan:
     * 1. Room (clearAllTables en una transacción).
     * 2. Ficheros locales (fotos y logos del negocio anterior).
     * 3. Identidad de DataStore (sin tocar theme_mode).
     * 4. Reset de estados en memoria (MovimientoRepository).
     * 5. Nuevo propietario.
     * No toca Firestore ni hace limpieza remota.
     */
    private suspend fun ejecutarWipe(uid: String) {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            limpiarFicherosLocales()
        }
        preferences.limpiarIdentidadNegocio()
        movimientoRepository.resetEstadoEnMemoria()
        preferences.setUidPropietario(uid)
    }

    /**
     * limpiarFicherosLocales
     * ----------------------
     * Borra el contenido de las carpetas internas de fotos de clientes y de
     * logos. No borra la carpeta en sí. Se ejecuta en un hilo de IO.
     */
    private fun limpiarFicherosLocales() {
        val carpetas = listOf(
            File(context.filesDir, "fotos"),
            File(context.filesDir, "logos")
        )
        carpetas.forEach { carpeta ->
            if (carpeta.isDirectory) {
                carpeta.listFiles()?.forEach { archivo ->
                    runCatching { archivo.delete() }
                }
            }
        }
    }
}
