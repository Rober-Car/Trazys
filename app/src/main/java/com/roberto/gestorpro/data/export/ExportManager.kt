package com.roberto.gestorpro.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.roberto.gestorpro.data.database.ClientesDatabase
import com.roberto.gestorpro.data.entity.ClienteEntity
import com.roberto.gestorpro.data.entity.GastoEntity
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.data.entity.ReservaEntity
import com.roberto.gestorpro.data.entity.ServicioEntity
import com.roberto.gestorpro.data.entity.SesionEntity
import com.roberto.gestorpro.data.repository.MovimientoRepository
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ExportManager (backup v1)
 * ------------------------
 * Motor de respaldo COMPLETO de UN negocio en formato ZIP:
 *   - manifest.json (versión, identidad negocioId, tablas funcionales)
 *   - media/clientes/<idCliente>.jpg (fotografías locales opcionales)
 *
 * Reglas de seguridad:
 *   - Importar/Restaurar exigen backup.negocioId == negocioId de la cuenta
 *     autenticada (leído de usuarios/{uid}); si no, se rechaza sin insertar nada.
 *   - Las filas se insertan SIEMPRE normalizadas al negocioId de la cuenta
 *     actual; nunca se confía en el negocioId de cada fila del backup.
 *   - DNI duplicado con distinto idCliente = conflicto duro -> aborto total.
 *   - El cambio se aplica en una transacción Room (o todo o nada).
 *   - Tras aplicar se recalcula la economía de los clientes afectados y se
 *     publica su resumen remoto con el mecanismo existente (sin perder el
 *     cambio local si la publicación falla).
 *
 * No se toca Firestore más allá del resumen económico del cliente afectado, no
 * se crea ningún documento remoto en otro negocio y nunca se modifica
 * uid_propietario_datos_locales.
 */
object ExportManager {

    const val BACKUP_VERSION = 1
    const val TIPO_BACKUP = "negocio"
    const val ORIGEN_APP = "Trazys Admin"

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val MEDIA_PREFIX = "media/clientes/"
    private const val DIR_FOTOS = "fotos"

    /**
     * Resultado
     * ---------
     * Resultado de una operación de exportación/importación/restauración.
     */
    data class Resultado(
        val exito: Boolean,
        val mensaje: String
    )

    /**
     * ContenidoBackup
     * ---------------
     * Backup ya leído, validado estructuralmente y listo para aplicar.
     * Las filas conservan sus IDs; los campos negocioId se ignoran en la
     * aplicación (se normalizan al negocio de la cuenta).
     */
    private data class ContenidoBackup(
        val backupVersion: Int,
        val negocioIdDeclarado: String,
        val servicios: List<ServicioEntity>,
        val clientes: List<ClienteEntity>,
        val sesiones: List<SesionEntity>,
        val movimientos: List<MovimientoEntity>,
        val gastos: List<GastoEntity>,
        val reservas: List<ReservaEntity>,
        val mediaFotos: Map<Int, ByteArray>
    )

    private data class MediaEntry(
        val clienteId: Int,
        val archivo: String
    )

    private data class DatosManifiesto(
        val servicios: List<ServicioEntity>? = emptyList(),
        val clientes: List<ClienteEntity>? = emptyList(),
        val sesiones: List<SesionEntity>? = emptyList(),
        val movimientos: List<MovimientoEntity>? = emptyList(),
        val gastos: List<GastoEntity>? = emptyList(),
        val reservas: List<ReservaEntity>? = emptyList()
    )

    private data class Manifiesto(
        val backupVersion: Int = 0,
        val tipo: String = "",
        val negocioId: String = "",
        val fechaExportacion: String = "",
        val origenApp: String = "",
        val datos: DatosManifiesto? = null,
        val media: List<MediaEntry>? = emptyList()
    )

    // =========================================================
    // 1) EXPORTAR
    // =========================================================

    /**
     * exportarBackup
     * --------------
     * Genera el ZIP de backup completo del negocio actual (Room) en `uri`.
     * Si `incluirFotos` es true y una foto existe localmente, se empaqueta.
     */
    suspend fun exportarBackup(
        context: Context,
        uri: Uri,
        negocioId: String,
        incluirFotos: Boolean,
        db: ClientesDatabase
    ): Resultado = withContext(Dispatchers.IO) {
        if (negocioId.isBlank()) {
            return@withContext Resultado(false, "No tienes un centro creado para exportar")
        }
        try {
            val servicios = db.servicioDao().obtenerTodosLosServiciosSync()
            val clientes = db.clienteDao().obtenerTodosLosClientesSync()
            val sesiones = db.sesionDao().obtenerTodasLasSesionesSync()
            val movimientos = db.movimientoDao().obtenerTodosLosMovimientosSync()
            val gastos = db.gastoDao().obtenerTodosLosGastosSync()
            val reservas = db.reservaDao().obtenerTodasLasReservasSync()

            val manifest = Manifiesto(
                backupVersion = BACKUP_VERSION,
                tipo = TIPO_BACKUP,
                negocioId = negocioId,
                fechaExportacion = Instant.now().toString(),
                origenApp = ORIGEN_APP,
                datos = DatosManifiesto(
                    servicios = servicios,
                    clientes = clientes,
                    sesiones = sesiones,
                    movimientos = movimientos,
                    gastos = gastos,
                    reservas = reservas
                ),
                media = if (incluirFotos) {
                    clientes.mapNotNull { cliente ->
                        val ruta = cliente.foto
                        val esUrl = ruta.startsWith("http://") || ruta.startsWith("https://")
                        if (!esUrl && ruta.isNotBlank() && File(ruta).isFile) {
                            MediaEntry(cliente.idCliente, "$MEDIA_PREFIX${cliente.idCliente}.jpg")
                        } else {
                            null
                        }
                    }
                } else {
                    emptyList()
                }
            )

            context.contentResolver.openOutputStream(uri)?.use { salida ->
                ZipOutputStream(salida).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zip.write(Gson().toJson(manifest).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    if (incluirFotos) {
                        clientes.forEach { cliente ->
                            val ruta = cliente.foto
                            val esUrl = ruta.startsWith("http://") || ruta.startsWith("https://")
                            if (!esUrl && ruta.isNotBlank()) {
                                val archivo = File(ruta)
                                if (archivo.isFile) {
                                    zip.putNextEntry(
                                        ZipEntry("$MEDIA_PREFIX${cliente.idCliente}.jpg")
                                    )
                                    archivo.inputStream().use { entrada ->
                                        entrada.copyTo(zip)
                                    }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                }
            } ?: return@withContext Resultado(false, "No se pudo crear el archivo de backup")

            Resultado(true, "Backup exportado correctamente")
        } catch (e: Exception) {
            Resultado(false, "Error al exportar: ${e.message ?: "error inesperado"}")
        }
    }

    // =========================================================
    // 2) IMPORTAR
    // =========================================================

    /**
     * importarBackup
     * --------------
     * Incorpora (merge/upsert) el contenido de un backup compatible del MISMO
     * negocio. Nunca borra datos existentes y es atómico: si la validación o la
     * transacción fallan, Room queda exactamente como estaba.
     */
    suspend fun importarBackup(
        context: Context,
        uri: Uri,
        negocioIdCuenta: String?,
        db: ClientesDatabase,
        movimientoRepository: MovimientoRepository
    ): Resultado {
        val contenido = leerYValidar(context, uri, negocioIdCuenta, validarReferencias = true)
        if (!contenido.exito) return Resultado(false, contenido.mensaje)

        val backup = contenido.contenido
            ?: return Resultado(false, "El backup no tiene contenido")

        // Conflictos duros contra la BD actual (DNI con distinto idCliente).
        val clientesExistentes = db.clienteDao().obtenerTodosLosClientesSync()
        val porDni = clientesExistentes
            .filter { it.dni.isNotBlank() }
            .associateBy { it.dni.trim().uppercase() }
        for (cliente in backup.clientes) {
            val dni = cliente.dni?.trim()?.uppercase()
            if (dni.isNullOrBlank()) {
                return Resultado(false, "El backup contiene un cliente sin DNI válido")
            }
            porDni[dni]?.let { existente ->
                if (existente.idCliente != cliente.idCliente) {
                    return Resultado(
                        false,
                        "El DNI $dni ya existe con otro idCliente. Importación cancelada " +
                            "para no mezclar datos."
                    )
                }
            }
        }

        try {
            db.withTransaction {
                aplicarMerge(backup, db)
            }
        } catch (e: Exception) {
            return Resultado(
                false,
                "Error al importar: la operación se canceló y no se modificó nada " +
                    "(${e.message ?: "error inesperado"})"
            )
        }

        restaurarMedia(context, backup)
        recalcularEconomia(
            backup.clientes.map { it.idCliente }.toSet(),
            backup,
            movimientoRepository
        )
        return Resultado(true, "Datos importados correctamente")
    }

    // =========================================================
    // 3) RESTAURAR
    // =========================================================

    /**
     * restaurarBackup
     * ---------------
     * Reemplaza por completo la caché local (TODAS las tablas) por el contenido
     * del backup compatible del MISMO negocio. Es atómico: borrado + inserción
     * en una única transacción Room; nunca deja medio backup aplicado.
     */
    suspend fun restaurarBackup(
        context: Context,
        uri: Uri,
        negocioIdCuenta: String?,
        db: ClientesDatabase,
        movimientoRepository: MovimientoRepository
    ): Resultado {
        val contenido = leerYValidar(context, uri, negocioIdCuenta, validarReferencias = true)
        if (!contenido.exito) return Resultado(false, contenido.mensaje)

        val backup = contenido.contenido
            ?: return Resultado(false, "El backup no tiene contenido")

        try {
            db.withTransaction {
                db.clearAllTables()
                aplicarInsercionCompleta(backup, db)
            }
        } catch (e: Exception) {
            return Resultado(
                false,
                "Error al restaurar: la operación se canceló y no se modificó nada " +
                    "(${e.message ?: "error inesperado"})"
            )
        }

        restaurarMedia(context, backup)
        recalcularEconomia(
            backup.clientes.map { it.idCliente }.toSet(),
            backup,
            movimientoRepository
        )
        return Resultado(true, "Copia restaurada correctamente")
    }

    // =========================================================
    // Lectura y validación
    // =========================================================

    private data class Lectura(
        val exito: Boolean,
        val mensaje: String = "",
        val contenido: ContenidoBackup? = null
    )

    private suspend fun leerYValidar(
        context: Context,
        uri: Uri,
        negocioIdCuenta: String?,
        validarReferencias: Boolean
    ): Lectura = withContext(Dispatchers.IO) {
        if (negocioIdCuenta.isNullOrBlank()) {
            return@withContext Lectura(
                false,
                "No tienes un centro creado. No se puede importar/restaurar un backup."
            )
        }

        val (leido, errorLectura) = leerZip(context, uri)
        if (leido == null) {
            val mensaje = if (errorLectura == "JSON_ANTIGUO") {
                "Este archivo es un backup antiguo (sin manifest). " +
                    "Vuelve a exportar el centro con la versión actual."
            } else {
                "El archivo no es un backup válido o está corrupto."
            }
            return@withContext Lectura(false, mensaje)
        }
        val contenido = leido

        if (contenido.backupVersion != BACKUP_VERSION) {
            return@withContext Lectura(
                false,
                "Versión de backup no soportada (${contenido.backupVersion}). " +
                    "Necesitas la versión actual de la app."
            )
        }
        if (contenido.negocioIdDeclarado.isBlank() ||
            contenido.negocioIdDeclarado != negocioIdCuenta
        ) {
            return@withContext Lectura(
                false,
                "Este backup pertenece a otro centro y no puede restaurarse aquí."
            )
        }

        val errores = erroresDeIntegridad(contenido, validarReferencias)
        if (errores.isNotEmpty()) {
            return@withContext Lectura(
                false,
                "El backup no es coherente: ${errores.joinToString("; ")}"
            )
        }
        Lectura(true, "", contenido)
    }

    /**
     * leerZip
     * -------
     * Lee el ZIP y devuelve (ContenidoBackup, error). `error == "JSON_ANTIGUO"`
     * indica un backup plano JSON legacy (sin ZIP).
     */
    private fun leerZip(context: Context, uri: Uri): Pair<ContenidoBackup?, String?> {
        try {
            var manifestJson: String? = null
            val media = mutableMapOf<Int, ByteArray>()
            val hayEntradas = context.contentResolver.openInputStream(uri)?.use { entrada ->
                ZipInputStream(entrada).use { zip ->
                    var algunaEntrada = false
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        algunaEntrada = true
                        val nombre = entry.name
                        if (nombre == MANIFEST_ENTRY) {
                            manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                        } else if (nombre.startsWith(MEDIA_PREFIX) && nombre.endsWith(".jpg")) {
                            val idTexto = nombre.removePrefix(MEDIA_PREFIX)
                                .removeSuffix(".jpg")
                            val id = idTexto.toIntOrNull()
                            if (id != null) {
                                media[id] = zip.readBytes()
                            }
                        }
                        zip.closeEntry()
                    }
                    algunaEntrada
                }
            } ?: return null to "NO_LEIBLE"

            if (!hayEntradas || manifestJson == null) {
                // Un backup plano JSON legacy no tiene entradas ZIP ni manifest.
                return null to "JSON_ANTIGUO"
            }

            val gson = Gson()
            val manifest = gson.fromJson(manifestJson, Manifiesto::class.java)
                ?: return null to "CORRUPTO"
            val datos = manifest.datos ?: return null to "CORRUPTO"

            return ContenidoBackup(
                backupVersion = manifest.backupVersion,
                negocioIdDeclarado = manifest.negocioId,
                servicios = datos.servicios ?: emptyList(),
                clientes = datos.clientes ?: emptyList(),
                sesiones = datos.sesiones ?: emptyList(),
                // Backups antiguos sin los campos de identidad histórica: Gson
                // puede dejarlos null; se normalizan a "".
                movimientos = (datos.movimientos ?: emptyList())
                    .map { it.normalizarIdentidadHistorica() },
                gastos = datos.gastos ?: emptyList(),
                reservas = datos.reservas ?: emptyList(),
                mediaFotos = media
            ) to null
        } catch (_: Exception) {
            return null to "CORRUPTO"
        }
    }

    /**
     * erroresDeIntegridad
     * -------------------
     * Comprueba unicidad interna e integridad referencial del backup. Si
     * `validarReferencias` es true exige autocontención (sin huérfanos).
     */
    private fun erroresDeIntegridad(
        contenido: ContenidoBackup,
        validarReferencias: Boolean
    ): List<String> {
        val errores = mutableListOf<String>()

        val idServicios = contenido.servicios.map { it.idServicio }.toSet()
        val idClientes = contenido.clientes.map { it.idCliente }.toSet()
        val idSesiones = contenido.sesiones.map { it.idSesion }.toSet()

        if (tieneDuplicados(contenido.servicios.map { it.idServicio })) errores += "servicios con id repetido"
        if (tieneDuplicados(contenido.clientes.map { it.idCliente })) errores += "clientes con id repetido"
        if (tieneDuplicados(contenido.sesiones.map { it.idSesion })) errores += "sesiones con id repetido"
        if (tieneDuplicados(contenido.movimientos.map { it.idMovimiento })) errores += "movimientos con id repetido"
        if (tieneDuplicados(contenido.gastos.map { it.idGasto })) errores += "gastos con id repetido"
        if (tieneDuplicados(contenido.reservas.map { it.idReserva })) errores += "reservas con id repetido"

        val dnis = contenido.clientes.mapNotNull { it.dni?.trim()?.uppercase() }
        if (tieneDuplicados(dnis)) errores += "clientes con DNI repetido"

        if (validarReferencias) {
            contenido.sesiones.forEach {
                if (it.idServicio !in idServicios) errores += "sesión ${it.idSesion} sin su servicio"
            }
            contenido.movimientos.forEach {
                // Los movimientos son histórico económico AUTÓNOMO del centro:
                // NO se exige que exista su ficha `clientes/{idCliente}` (puede
                // haberse eliminado la cuenta conservando el histórico).
                it.servicios.forEach { idServ ->
                    if (idServ !in idServicios) errores += "movimiento ${it.idMovimiento} referencia a servicio inexistente"
                }
            }
            contenido.reservas.forEach {
                if (it.idCliente !in idClientes) errores += "reserva sin su cliente"
                if (it.idSesion !in idSesiones) errores += "reserva sin su sesión"
            }
        }
        return errores
    }

    private fun <T> tieneDuplicados(lista: List<T>): Boolean =
        lista.size != lista.toSet().size

    /**
     * Normaliza los campos de identidad histórica del movimiento. Los backups
     * anteriores a esta versión no los traen y Gson puede deserializarlos como
     * null aunque el tipo sea String: se convierten a "".
     */
    private fun MovimientoEntity.normalizarIdentidadHistorica(): MovimientoEntity = copy(
        nombreCliente = nombreCliente.orEmpty(),
        apellidosCliente = apellidosCliente.orEmpty(),
        dniCliente = dniCliente.orEmpty()
    )

    // =========================================================
    // Aplicación en Room (normalizando negocioId)
    // =========================================================

    private suspend fun aplicarInsercionCompleta(contenido: ContenidoBackup, db: ClientesDatabase) {
        val negocio = contenido.negocioIdDeclarado
        contenido.servicios.forEach {
            db.servicioDao().insertarServicio(it.copy(negocioId = negocio))
        }
        contenido.clientes.forEach {
            db.clienteDao().insertarClienteDao(it.copy(negocioId = negocio))
        }
        contenido.sesiones.forEach {
            db.sesionDao().insertarSesion(it.copy(negocioId = negocio))
        }
        contenido.movimientos.forEach { db.movimientoDao().insertarMovimiento(it) }
        contenido.gastos.forEach { db.gastoDao().insertarGasto(it) }
        contenido.reservas.forEach {
            db.reservaDao().insertarReserva(it.copy(negocioId = negocio))
        }
    }

    private suspend fun aplicarMerge(contenido: ContenidoBackup, db: ClientesDatabase) {
        val negocio = contenido.negocioIdDeclarado

        contenido.servicios.forEach { entidad ->
            val existente = db.servicioDao().obtenerServicioPorId(entidad.idServicio)
            val fila = entidad.copy(negocioId = negocio)
            if (existente == null) db.servicioDao().insertarServicio(fila)
            else db.servicioDao().actualizarServicio(fila)
        }

        contenido.clientes.forEach { entidad ->
            val existente = db.clienteDao().obtenerClientePorIdDao(entidad.idCliente)
            val fila = entidad.copy(negocioId = negocio)
            if (existente == null) db.clienteDao().insertarClienteDao(fila)
            else db.clienteDao().actualizarClienteDao(fila)
        }

        contenido.sesiones.forEach { entidad ->
            val existente = db.sesionDao().obtenerSesionPorId(entidad.idSesion)
            val fila = entidad.copy(negocioId = negocio)
            if (existente == null) db.sesionDao().insertarSesion(fila)
            else db.sesionDao().actualizarSesion(fila)
        }

        contenido.movimientos.forEach { entidad ->
            val existente = db.movimientoDao().obtenerMovimientoPorId(entidad.idMovimiento)
            if (existente == null) db.movimientoDao().insertarMovimiento(entidad)
            else db.movimientoDao().actualizarMovimiento(entidad)
        }

        contenido.gastos.forEach { entidad ->
            val existente = db.gastoDao().obtenerGastoPorId(entidad.idGasto)
            if (existente == null) db.gastoDao().insertarGasto(entidad)
            else db.gastoDao().actualizarGasto(entidad)
        }

        contenido.reservas.forEach { entidad ->
            val fila = entidad.copy(negocioId = negocio)
            db.reservaDao().insertarReserva(fila)
        }
    }

    // =========================================================
    // Media y economía
    // =========================================================

    private fun restaurarMedia(context: Context, contenido: ContenidoBackup) {
        if (contenido.mediaFotos.isEmpty()) return
        val carpeta = File(context.filesDir, DIR_FOTOS).apply { mkdirs() }
        contenido.clientes.forEach { cliente ->
            val bytes = contenido.mediaFotos[cliente.idCliente] ?: return@forEach
            val ruta = cliente.foto
            val esUrl = ruta.startsWith("http://") || ruta.startsWith("https://")
            if (!esUrl && ruta.isNotBlank()) {
                val nombre = ruta.substringAfterLast('/')
                if (nombre.isNotBlank()) {
                    runCatching {
                        File(carpeta, nombre).writeBytes(bytes)
                    }
                }
            }
        }
    }

    /**
     * recalcularEconomia
     * ------------------
     * Recalcula y persiste en Room la economía de cada cliente con movimientos
     * y publica su resumen remoto con el mecanismo existente. Si la publicación
     * remota falla, el cambio local se conserva y queda preparado el reintento
     * (banner/pendientes del mecanismo actual), sin deshacer nada.
     */
    private suspend fun recalcularEconomia(
        idsClientes: Set<Int>,
        contenido: ContenidoBackup,
        movimientoRepository: MovimientoRepository
    ) {
        val idsConMovimientos = contenido.movimientos.map { it.idCliente }.toSet()
        idsClientes.intersect(idsConMovimientos).forEach { idCliente ->
            try {
                movimientoRepository.recalcularMorosidadDeCliente(idCliente)
            } catch (_: Exception) {
                // No se deshace el cambio local; el mecanismo de pendientes y el
                // banner del perfil informan del fallo y permiten reintentar.
            }
        }
    }
}
