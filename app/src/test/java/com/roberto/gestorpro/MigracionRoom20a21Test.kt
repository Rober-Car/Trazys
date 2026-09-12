package com.roberto.gestorpro

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.roberto.gestorpro.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MigracionRoom20a21Test
 * ----------------------
 * Verificación REAL de la migración Room 20 → 21 (identidad histórica del
 * movimiento) ejecutada sobre SQLite con Robolectric (sin dispositivo):
 *  - se crea una BD v20 con la tabla `movimiento` (esquema v20) y una fila;
 *  - se ejecuta `AppModule.MIGRACION_20_21` (el SQL real de producción);
 *  - las columnas `nombreCliente`, `apellidosCliente` y `dniCliente` existen,
 *    son NOT NULL y tienen default '';
 *  - los datos existentes se conservan.
 *
 * Nota: `MigrationTestHelper` de Room 2.8 no es ejecutable bajo Robolectric en
 * este proyecto (incompatibilidad del nuevo driver con la ruta de BD); por eso
 * la migración se ejecuta directamente sobre `SupportSQLiteDatabase`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionRoom20a21Test {

    @Test
    fun migracion_20_21_anadeColumnasConDefaultYConservaDatos() {
        val context = RuntimeEnvironment.getApplication()
        val nombreDb = "migracion-room-20-21.db"
        context.deleteDatabase(nombreDb)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(nombreDb)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Esquema v20 de `movimiento` (idéntico a 20.json).
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `movimiento` (" +
                            "`idMovimiento` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`idCliente` INTEGER NOT NULL, " +
                            "`servicios` TEXT NOT NULL, " +
                            "`fechaInicio` INTEGER NOT NULL, " +
                            "`fechaFin` INTEGER NOT NULL, " +
                            "`precioFinal` REAL NOT NULL, " +
                            "`estado` TEXT NOT NULL, " +
                            "`fechaPago` INTEGER, " +
                            "`metodoPago` TEXT, " +
                            "`observaciones` TEXT, " +
                            "`fechaRegistro` INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO `movimiento` " +
                    "(`idMovimiento`,`idCliente`,`servicios`,`fechaInicio`,`fechaFin`," +
                    "`precioFinal`,`estado`,`fechaRegistro`) " +
                    "VALUES (1, 7, '', 1000, 2000, 42.5, 'PENDIENTE', 500)"
            )

            // Migración real de producción.
            AppModule.MIGRACION_20_21.migrate(db)

            // Columnas nuevas: NOT NULL + default ''.
            db.query("PRAGMA table_info(`movimiento`)").use { c ->
                val columnas = mutableMapOf<String, Pair<Int, String?>>()
                val idxNombre = c.getColumnIndexOrThrow("name")
                val idxNotNull = c.getColumnIndexOrThrow("notnull")
                val idxDefault = c.getColumnIndexOrThrow("dflt_value")
                while (c.moveToNext()) {
                    columnas[c.getString(idxNombre)] =
                        c.getInt(idxNotNull) to c.getString(idxDefault)
                }
                listOf("nombreCliente", "apellidosCliente", "dniCliente").forEach { col ->
                    val info = columnas[col]
                    assertNotNull("debe existir la columna $col", info)
                    assertEquals("$col debe ser NOT NULL", 1, info!!.first)
                    assertEquals("$col debe tener default ''", "''", info.second)
                }
            }

            // Datos existentes conservados.
            db.query(
                "SELECT `nombreCliente`,`apellidosCliente`,`dniCliente`," +
                    "`idCliente`,`precioFinal`,`fechaRegistro` " +
                    "FROM `movimiento` WHERE `idMovimiento`=1"
            ).use { c ->
                assertTrue("el movimiento debe conservarse", c.moveToFirst())
                assertEquals("", c.getString(0))
                assertEquals("", c.getString(1))
                assertEquals("", c.getString(2))
                assertEquals(7, c.getInt(3))
                assertEquals(42.5, c.getDouble(4), 0.0)
                assertEquals(500L, c.getLong(5))
            }
        }
    }
}
