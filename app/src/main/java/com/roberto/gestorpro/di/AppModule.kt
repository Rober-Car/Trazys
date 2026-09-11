package com.roberto.gestorpro.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roberto.gestorpro.data.dao.ClaseDao
import com.roberto.gestorpro.data.dao.ClienteDao
import com.roberto.gestorpro.data.dao.EliminacionPendienteDao
import com.roberto.gestorpro.data.dao.GastoDao
import com.roberto.gestorpro.data.dao.MovimientoDao
import com.roberto.gestorpro.data.dao.ReservaDao
import com.roberto.gestorpro.data.dao.ServicioDao
import com.roberto.gestorpro.data.dao.ServicioDesactivacionPendienteDao
import com.roberto.gestorpro.data.dao.SesionClaseDao
import com.roberto.gestorpro.data.dao.SesionDao
import com.roberto.gestorpro.data.dao.SolicitudDao
import com.roberto.gestorpro.data.database.ClientesDatabase
import com.roberto.gestorpro.data.firebase.ClienteRemotoRepository
import com.roberto.gestorpro.data.firebase.MovimientoRemotoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.data.repository.ClaseRepository
import com.roberto.gestorpro.data.repository.ClienteRepository
import com.roberto.gestorpro.data.repository.GastoRepository
import com.roberto.gestorpro.data.repository.MovimientoRepository
import com.roberto.gestorpro.data.repository.ReservaRepository
import com.roberto.gestorpro.data.repository.ServicioRepository
import com.roberto.gestorpro.data.repository.SesionClaseRepository
import com.roberto.gestorpro.data.repository.SesionRepository
import com.roberto.gestorpro.data.repository.SolicitudRepository
import com.roberto.gestorpro.model.EstadoCliente
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule.kt
 * ------------
 * ✔ TIPO: archivo de código fuente Kotlin (inyección de dependencias)
 * Es el archivo que define el módulo de Hilt de la aplicación.
 * Sirve para que Hilt sepa cómo construir y proporcionar la base de datos, el DAO y el repositorio.
 */

/**
 * @Module
 * -------
 * ✔ TIPO: anotación (dagger.Module)
 * Es la anotación que marca este objeto como módulo de inyección de dependencias de Hilt.
 * Sirve para indicar a Hilt qué objetos puede proporcionar a la aplicación.
 */

/**
 * @InstallIn(SingletonComponent::class)
 * -------------------------------------
 * ✔ TIPO: anotación (dagger.hilt.InstallIn)
 * Es la anotación que indica en qué componente de Hilt se instala este módulo.
 * Sirve para que las dependencias proporcionadas estén disponibles en toda la aplicación
 * (alcance singleton, es decir, una única instancia mientras viva el proceso).
 */

/**
 * AppModule
 * ---------
 * ✔ TIPO: object (objeto singleton de Kotlin)
 * Es el objeto que agrupa los métodos que proporcionan las dependencias de datos.
 * Sirve para construir la base de datos, el DAO y el repositorio e inyectarlos donde se necesiten.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * MIGRACION_11_12
     * ---------------
     * Añade la columna nullable horaDesdeReserva a la tabla "sesion".
     * No toca el resto de tablas ni destruye datos locales.
     */
    private val MIGRACION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sesion ADD COLUMN horaDesdeReserva TEXT")
        }
    }

    /**
     * MIGRACION_12_13
     * ---------------
     * Elimina la columna "tieneLlave" de la tabla "cliente": la llave deja de
     * ser un atributo especial del cliente y pasa a gestionarse como un servicio
     * normal (nombre/estado) contratado. SQLite anterior a 3.35 no soporta
     * ALTER TABLE DROP COLUMN, por eso se recrea la tabla sin esa columna,
     * copiando el resto de datos para no perder la información local.
     */
    private val MIGRACION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cliente_nueva` (" +
                    "`idCliente` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`apellidos` TEXT NOT NULL, " +
                    "`dni` TEXT NOT NULL, " +
                    "`telefono` TEXT NOT NULL, " +
                    "`email` TEXT, " +
                    "`foto` TEXT NOT NULL, " +
                    "`fechaNacimiento` INTEGER NOT NULL, " +
                    "`fechaRegistro` INTEGER NOT NULL, " +
                    "`fechaAlta` INTEGER, " +
                    "`fechaBaja` INTEGER, " +
                    "`estado` TEXT NOT NULL, " +
                    "`observaciones` TEXT, " +
                    "`negocioId` TEXT, " +
                    "`serviciosContratados` TEXT NOT NULL, " +
                    "`firebaseUid` TEXT)"
            )
            db.execSQL(
                "INSERT INTO `cliente_nueva` " +
                    "(`idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`) " +
                    "SELECT `idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid` " +
                    "FROM `cliente`"
            )
            db.execSQL("DROP TABLE `cliente`")
            db.execSQL("ALTER TABLE `cliente_nueva` RENAME TO `cliente`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_dni` ON `cliente` (`dni`)"
            )
        }
    }

    /**
     * MIGRACION_13_14
     * ---------------
     * FASE 1 DE ECONOMÍA (modelos Room + migración NO destructiva).
     *
     * Tabla "servicio": añade la columna `precio` REAL NOT NULL. Los servicios
     * existentes NO tienen un precio histórico conocido, por lo que se inicializan
     * con 0.0 (valor temporal por defecto; la UI para editar precios se hará en
     * una fase posterior). SQLite < 3.35 no soporta ADD/DROP COLUMN, por eso se
     * recrea la tabla copiando todas las filas.
     *
     * Tabla "movimiento": migra el modelo económico antiguo
     *   (servicio: String + precio: Double) al nuevo
     *   (servicios: List<Int> guardado como TEXT separado por comas +
     *    precioFinal: Double + metodoPago: TEXT nullable).
     *   - precioFinal = precio antiguo (se conserva el importe histórico);
     *   - metodoPago = NULL (no se inventan métodos para históricos);
     *   - servicios: se rellena con el id del servicio cuyo NOMBRE coincide
     *     EXACTAMENTE con el texto antiguo y SOLO si dicha coincidencia es ÚNICA
     *     (COUNT(*) = 1). Si no hay una correspondencia segura (0 o varias filas),
     *     la lista queda vacía (''): NO se inventa ninguna relación. El resto de
     *     campos (fechas, estado, fechaPago, observaciones) se copia tal cual.
     */
    private val MIGRACION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- Servicio: nueva columna precio = 0.0 para filas existentes ---
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `servicio_nueva` (" +
                    "`idServicio` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`negocioId` TEXT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`descripcion` TEXT NOT NULL, " +
                    "`activo` INTEGER NOT NULL, " +
                    "`precio` REAL NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `servicio_nueva` " +
                    "(`idServicio`, `negocioId`, `nombre`, `descripcion`, `activo`, `precio`) " +
                    "SELECT `idServicio`, `negocioId`, `nombre`, `descripcion`, `activo`, 0.0 " +
                    "FROM `servicio`"
            )
            db.execSQL("DROP TABLE `servicio`")
            db.execSQL("ALTER TABLE `servicio_nueva` RENAME TO `servicio`")

            // --- Movimiento: servicios(List<Int>) + precioFinal + metodoPago ---
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movimiento_nueva` (" +
                    "`idMovimiento` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`idCliente` INTEGER NOT NULL, " +
                    "`servicios` TEXT NOT NULL, " +
                    "`fechaInicio` INTEGER NOT NULL, " +
                    "`fechaFin` INTEGER NOT NULL, " +
                    "`precioFinal` REAL NOT NULL, " +
                    "`estado` TEXT NOT NULL, " +
                    "`fechaPago` INTEGER, " +
                    "`metodoPago` TEXT, " +
                    "`observaciones` TEXT)"
            )
            db.execSQL(
                "INSERT INTO `movimiento_nueva` " +
                    "(`idMovimiento`, `idCliente`, `servicios`, `fechaInicio`, `fechaFin`, " +
                    "`precioFinal`, `estado`, `fechaPago`, `metodoPago`, `observaciones`) " +
                    "SELECT `idMovimiento`, `idCliente`, " +
                    "CASE " +
                    "  WHEN (SELECT COUNT(*) FROM `servicio` s WHERE s.`nombre` = m.`servicio`) = 1 " +
                    "  THEN (SELECT CAST(s.`idServicio` AS TEXT) FROM `servicio` s " +
                    "        WHERE s.`nombre` = m.`servicio` LIMIT 1) " +
                    "  ELSE '' " +
                    "END, " +
                    "`fechaInicio`, `fechaFin`, `precio`, `estado`, `fechaPago`, NULL, `observaciones` " +
                    "FROM `movimiento` m"
            )
            db.execSQL("DROP TABLE `movimiento`")
            db.execSQL("ALTER TABLE `movimiento_nueva` RENAME TO `movimiento`")
        }
    }

    /**
     * MIGRACION_14_15
     * ---------------
     * FASE 5 DE ECONOMÍA: morosidad. Añade a la tabla "cliente" dos columnas
     * para el indicador independiente de morosidad:
     *   - `moroso` INTEGER NOT NULL (0 por defecto);
     *   - `fechaEntradaMorosidad` INTEGER nullable.
     * Los clientes existentes se inicializan con moroso = false y
     * fechaEntradaMorosidad = null (NO se reconstruye morosidad histórica).
     * Se recrea la tabla (SQLite < 3.35 no soporta ADD COLUMN con NOT NULL
     * sin DEFAULT compatible con el esquema esperado por Room) conservando
     * todas las filas.
     */
    private val MIGRACION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cliente_nueva` (" +
                    "`idCliente` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`apellidos` TEXT NOT NULL, " +
                    "`dni` TEXT NOT NULL, " +
                    "`telefono` TEXT NOT NULL, " +
                    "`email` TEXT, " +
                    "`foto` TEXT NOT NULL, " +
                    "`fechaNacimiento` INTEGER NOT NULL, " +
                    "`fechaRegistro` INTEGER NOT NULL, " +
                    "`fechaAlta` INTEGER, " +
                    "`fechaBaja` INTEGER, " +
                    "`estado` TEXT NOT NULL, " +
                    "`observaciones` TEXT, " +
                    "`negocioId` TEXT, " +
                    "`serviciosContratados` TEXT NOT NULL, " +
                    "`firebaseUid` TEXT, " +
                    "`moroso` INTEGER NOT NULL, " +
                    "`fechaEntradaMorosidad` INTEGER)"
            )
            db.execSQL(
                "INSERT INTO `cliente_nueva` " +
                    "(`idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "`moroso`, `fechaEntradaMorosidad`) " +
                    "SELECT `idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "0, NULL " +
                    "FROM `cliente`"
            )
            db.execSQL("DROP TABLE `cliente`")
            db.execSQL("ALTER TABLE `cliente_nueva` RENAME TO `cliente`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_dni` ON `cliente` (`dni`)"
            )
        }
    }

    /**
     * MIGRACION_15_16
     * ---------------
     * Fecha de nacimiento OPCIONAL del cliente: la columna `fechaNacimiento`
     * de la tabla `cliente` pasa de NOT NULL a NULLABLE para permitir crear un
     * cliente sin fecha de nacimiento. Se recrea la tabla conservando todas las
     * filas (SQLite < 3.35 no soporta modificar la nullabilidad de una columna).
     * Las fechas ya guardadas se copian tal cual; no se inventa ningún valor.
     */
    private val MIGRACION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cliente_nueva` (" +
                    "`idCliente` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`apellidos` TEXT NOT NULL, " +
                    "`dni` TEXT NOT NULL, " +
                    "`telefono` TEXT NOT NULL, " +
                    "`email` TEXT, " +
                    "`foto` TEXT NOT NULL, " +
                    "`fechaNacimiento` INTEGER, " +
                    "`fechaRegistro` INTEGER NOT NULL, " +
                    "`fechaAlta` INTEGER, " +
                    "`fechaBaja` INTEGER, " +
                    "`estado` TEXT NOT NULL, " +
                    "`observaciones` TEXT, " +
                    "`negocioId` TEXT, " +
                    "`serviciosContratados` TEXT NOT NULL, " +
                    "`firebaseUid` TEXT, " +
                    "`moroso` INTEGER NOT NULL, " +
                    "`fechaEntradaMorosidad` INTEGER)"
            )
            db.execSQL(
                "INSERT INTO `cliente_nueva` " +
                    "(`idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "`moroso`, `fechaEntradaMorosidad`) " +
                    "SELECT `idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "`moroso`, `fechaEntradaMorosidad` " +
                    "FROM `cliente`"
            )
            db.execSQL("DROP TABLE `cliente`")
            db.execSQL("ALTER TABLE `cliente_nueva` RENAME TO `cliente`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_dni` ON `cliente` (`dni`)"
            )
        }
    }

    /**
     * MIGRACION_16_17
     * ---------------
     * F2 DE ECONOMÍA: (1) añade a la tabla "cliente" la columna
     * `exentoMorosidad` (INTEGER NOT NULL DEFAULT 0) y (2) crea la tabla
     * `eliminacion_pendiente` para persistir los borrados remotos de
     * movimientos pendientes de confirmar. Se recrea la tabla "cliente"
     * conservando todas las filas (patrón habitual del proyecto).
     */
    private val MIGRACION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cliente_nueva` (" +
                    "`idCliente` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`apellidos` TEXT NOT NULL, " +
                    "`dni` TEXT NOT NULL, " +
                    "`telefono` TEXT NOT NULL, " +
                    "`email` TEXT, " +
                    "`foto` TEXT NOT NULL, " +
                    "`fechaNacimiento` INTEGER, " +
                    "`fechaRegistro` INTEGER NOT NULL, " +
                    "`fechaAlta` INTEGER, " +
                    "`fechaBaja` INTEGER, " +
                    "`estado` TEXT NOT NULL, " +
                    "`observaciones` TEXT, " +
                    "`negocioId` TEXT, " +
                    "`serviciosContratados` TEXT NOT NULL, " +
                    "`firebaseUid` TEXT, " +
                    "`moroso` INTEGER NOT NULL, " +
                    "`fechaEntradaMorosidad` INTEGER, " +
                    "`exentoMorosidad` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `cliente_nueva` " +
                    "(`idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "`moroso`, `fechaEntradaMorosidad`, `exentoMorosidad`) " +
                    "SELECT `idCliente`, `nombre`, `apellidos`, `dni`, `telefono`, `email`, " +
                    "`foto`, `fechaNacimiento`, `fechaRegistro`, `fechaAlta`, `fechaBaja`, " +
                    "`estado`, `observaciones`, `negocioId`, `serviciosContratados`, `firebaseUid`, " +
                    "`moroso`, `fechaEntradaMorosidad`, 0 " +
                    "FROM `cliente`"
            )
            db.execSQL("DROP TABLE `cliente`")
            db.execSQL("ALTER TABLE `cliente_nueva` RENAME TO `cliente`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_dni` ON `cliente` (`dni`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `eliminacion_pendiente` (" +
                    "`idMovimiento` INTEGER NOT NULL, " +
                    "`idCliente` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`idMovimiento`))"
            )
        }
    }

    /**
     * MIGRACION_17_18
     * ---------------
     * Desactivación durable de servicios: crea la tabla
     * `servicio_desactivacion_pendiente` para persistir la baja de una actividad
     * cuyo estado remoto (eliminar sesiones futuras + reservas y dejar el
     * servicio inactivo) no se confirmó en Firestore.
     */
    private val MIGRACION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `servicio_desactivacion_pendiente` (" +
                    "`idServicio` INTEGER NOT NULL, " +
                    "`desde` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`idServicio`))"
            )
        }
    }

    /**
     * MIGRACION_18_19
     * ---------------
     * Servicio: nueva columna `permiteCombinarDia` (default true = 1).
     * Añade la columna con recreación de tabla (SQLite no permite ALTER TABLE
     * ADD COLUMN con NOT NULL sin DEFAULT constante de forma fiable en todas
     * las versiones usadas; se sigue el patrón de recreación ya empleado en
     * MIGRACION_13_14 para `precio`).
     */
    private val MIGRACION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `servicio_nueva` (" +
                    "`idServicio` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`negocioId` TEXT NOT NULL, " +
                    "`nombre` TEXT NOT NULL, " +
                    "`descripcion` TEXT NOT NULL, " +
                    "`activo` INTEGER NOT NULL, " +
                    "`precio` REAL NOT NULL, " +
                    "`permiteCombinarDia` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `servicio_nueva` " +
                    "(`idServicio`, `negocioId`, `nombre`, `descripcion`, `activo`, `precio`, `permiteCombinarDia`) " +
                    "SELECT `idServicio`, `negocioId`, `nombre`, `descripcion`, `activo`, `precio`, 1 " +
                    "FROM `servicio`"
            )
            db.execSQL("DROP TABLE `servicio`")
            db.execSQL("ALTER TABLE `servicio_nueva` RENAME TO `servicio`")
        }
    }

    /**
     * MIGRACION_19_20
     * ---------------
     * Movimiento: nueva columna `fechaRegistro` (marca de creación, frontera de
     * etapa para la morosidad por fecha). Las filas existentes toman 0 (se
     * tratan como anteriores a la etapa actual). ALTER simple con DEFAULT 0.
     */
    private val MIGRACION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `movimiento` ADD COLUMN `fechaRegistro` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * provideFirebaseAuth
     * -------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides y @Singleton → FirebaseAuth
     * Es la receta que proporciona la instancia única de Firebase Authentication.
     * Sirve para que el repositorio de autenticación no cree sus propias instancias
     * y toda la app comparta la misma sesión.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    /**
     * provideFirebaseFirestore
     * ------------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides y @Singleton → FirebaseFirestore
     * Es la receta que proporciona la instancia única de Firestore.
     * Sirve para que los repositorios remotos compartan la misma conexión a la nube.
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    /**
     * provideFirebaseStorage
     * ----------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides y @Singleton → FirebaseStorage
     * Es la receta que proporciona la instancia única de Firebase Storage.
     * Sirve para que el repositorio de negocio suba el logo del gimnasio.
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    /**
     * provideDatabase
     * ---------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides y @Singleton → ClientesDatabase
     * Es la función que proporciona la instancia de la base de datos Room de la aplicación.
     * Sirve para que Hilt cree una única ClientesDatabase y la inyecte en las clases que la pidan.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ClientesDatabase {

        var databaseBuilder = Room.databaseBuilder(
            context,
            ClientesDatabase::class.java,
            "gestorpro_database"
        )

        databaseBuilder = databaseBuilder
            .addMigrations(
                MIGRACION_11_12,
                MIGRACION_12_13,
                MIGRACION_13_14,
                MIGRACION_14_15,
                MIGRACION_15_16,
                MIGRACION_16_17,
                MIGRACION_17_18,
                MIGRACION_18_19,
                MIGRACION_19_20
            )

        return databaseBuilder.build()
    }

    /**
     * provideClienteDao
     * -----------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides → ClienteDao
     * Es la función que proporciona el DAO de clientes a partir de la base de datos.
     * Sirve para que Hilt inyecte ClienteDao en las clases que necesiten acceder a la tabla de clientes.
     */
    @Provides
    fun provideClienteDao(
        /**
         * database
         * --------
         * ✔ TIPO: parámetro (param) → ClientesDatabase
         * Es la base de datos Room de la aplicación.
         * Sirve para obtener el DAO de clientes a partir de ella.
         */
        database: ClientesDatabase
    ): ClienteDao {
        return database.clienteDao()
    }

    /**
     * provideClienteRepository
     * ------------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides → ClienteRepository
     * Es la función que proporciona el repositorio de clientes con su DAO.
     * Sirve para que Hilt inyecte ClienteRepository en las clases que necesiten operar con clientes.
     */
    @Provides
    fun provideClienteRepository(
        /**
         * clienteDao
         * ----------
         * ✔ TIPO: parámetro (param) → ClienteDao
         * Es el DAO de clientes de la base de datos.
         * Sirve para construir el repositorio de clientes a partir de él.
         */
        clienteDao: ClienteDao
    ): ClienteRepository {
        return ClienteRepository(clienteDao)
    }

    /**
     * provideMovimientoDao
     * --------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides → MovimientoDao
     * Es la función que proporciona el DAO de movimientos a partir de la base de datos.
     * Sirve para que Hilt inyecte MovimientoDao en las clases que necesiten acceder a la tabla de movimientos.
     */
    @Provides
    fun provideMovimientoDao(
        /**
         * database
         * --------
         * ✔ TIPO: parámetro (param) → ClientesDatabase
         * Es la base de datos Room de la aplicación.
         * Sirve para obtener el DAO de movimientos a partir de ella.
         */
        database: ClientesDatabase
    ): MovimientoDao {
        return database.movimientoDao()
    }

    /**
     * provideEliminacionPendienteDao
     * ------------------------------
     * Provee el DAO de eliminaciones pendientes de movimientos.
     */
    @Provides
    fun provideEliminacionPendienteDao(database: ClientesDatabase): EliminacionPendienteDao {
        return database.eliminacionPendienteDao()
    }

    /**
     * provideServicioDesactivacionPendienteDao
     * ----------------------------------------
     * Provee el DAO de desactivaciones pendientes de servicios (baja durable de
     * Actividades cuya cascada remota no se confirmó).
     */
    @Provides
    fun provideServicioDesactivacionPendienteDao(
        database: ClientesDatabase
    ): ServicioDesactivacionPendienteDao {
        return database.servicioDesactivacionPendienteDao()
    }

    /**
     * provideMovimientoRepository
     * ---------------------------
     * ✔ TIPO: método (fun) de Hilt con anotación @Provides → MovimientoRepository
     * Es la función que proporciona el repositorio de movimientos con su DAO.
     * Sirve para que Hilt inyecte MovimientoRepository en las clases que necesiten operar con movimientos.
     */
    @Provides
    fun provideMovimientoRepository(
        /**
         * movimientoDao
         * -------------
         * ✔ TIPO: parámetro (param) → MovimientoDao
         * Es el DAO de movimientos de la base de datos.
         * Sirve para construir el repositorio de movimientos a partir de él.
         */
        movimientoDao: MovimientoDao,
        clienteDao: ClienteDao,
        eliminacionPendienteDao: EliminacionPendienteDao,
        clienteRemotoRepository: ClienteRemotoRepository,
        movimientoRemotoRepository: MovimientoRemotoRepository
    ): MovimientoRepository {
        return MovimientoRepository(
            movimientoDao,
            clienteDao,
            eliminacionPendienteDao,
            clienteRemotoRepository,
            movimientoRemotoRepository
        )
    }

    @Provides
    fun provideGastoDao(database: ClientesDatabase): GastoDao {
        return database.gastoDao()
    }

    @Provides
    fun provideGastoRepository(gastoDao: GastoDao): GastoRepository {
        return GastoRepository(gastoDao)
    }

    @Provides
    fun provideClaseDao(database: ClientesDatabase): ClaseDao {
        return database.claseDao()
    }

    @Provides
    fun provideClaseRepository(claseDao: ClaseDao): ClaseRepository {
        return ClaseRepository(claseDao)
    }

    @Provides
    fun provideSesionClaseDao(database: ClientesDatabase): SesionClaseDao {
        return database.sesionClaseDao()
    }

    @Provides
    fun provideSesionClaseRepository(sesionClaseDao: SesionClaseDao): SesionClaseRepository {
        return SesionClaseRepository(sesionClaseDao)
    }

    @Provides
    fun provideReservaDao(database: ClientesDatabase): ReservaDao {
        return database.reservaDao()
    }

    @Provides
    fun provideReservaRepository(
        reservaDao: ReservaDao,
        sesionDao: SesionDao,
        servicioDao: ServicioDao,
        database: ClientesDatabase
    ): ReservaRepository {
        return ReservaRepository(reservaDao, sesionDao, servicioDao, database)
    }

    @Provides
    fun provideServicioDao(database: ClientesDatabase): ServicioDao {
        return database.servicioDao()
    }

    @Provides
    fun provideServicioRepository(servicioDao: ServicioDao): ServicioRepository {
        return ServicioRepository(servicioDao)
    }

    @Provides
    fun provideSesionDao(database: ClientesDatabase): SesionDao {
        return database.sesionDao()
    }

    @Provides
    fun provideSesionRepository(sesionDao: SesionDao): SesionRepository {
        return SesionRepository(sesionDao)
    }

    @Provides
    fun provideSolicitudDao(database: ClientesDatabase): SolicitudDao {
        return database.solicitudDao()
    }

    @Provides
    fun provideSolicitudRepository(solicitudDao: SolicitudDao): SolicitudRepository {
        return SolicitudRepository(solicitudDao)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context
    ): PreferencesRepository {
        return PreferencesRepository(context)
    }
}
