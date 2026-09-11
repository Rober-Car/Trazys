package com.roberto.gestorpro

import com.roberto.gestorpro.util.FechaBajaEfectiva
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * FechaBajaEfectivaTest
 * ---------------------
 * Tests de la lógica PURA de la fecha efectiva de baja usada por las tres vías
 * directas (perfil, edición y baja masiva): HOY -> ahora; fecha pasada ->
 * medianoche local; null -> hoy; nunca futura.
 */
class FechaBajaEfectivaTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    // Instante de referencia: 15/09/2026 a las 12:00 local.
    private val ahora: Long = LocalDate.of(2026, 9, 15)
        .atTime(12, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun epochDay(dia: Int, mes: Int, anio: Int = 2026): Long =
        LocalDate.of(anio, mes, dia).toEpochDay()

    // Baja individual con HOY: se guarda el instante actual.
    @Test
    fun hoy_o_null_devuelve_el_instante_actual() {
        assertEquals(ahora, FechaBajaEfectiva.millis(null, ahora))
        assertEquals(
            ahora,
            FechaBajaEfectiva.millis(FechaBajaEfectiva.hoyEpochDay(ahora), ahora)
        )
    }

    // Baja individual con fecha PASADA: medianoche local, anterior a `ahora`.
    @Test
    fun fecha_pasada_devuelve_medianoche_local() {
        val dia = epochDay(10, 9)
        val esperado = LocalDate.ofEpochDay(dia)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(esperado, FechaBajaEfectiva.millis(dia, ahora))
        assertTrue(FechaBajaEfectiva.millis(dia, ahora) < ahora)
    }

    // No permitir fecha futura: hoy y pasadas sí; mañana y años posteriores no.
    @Test
    fun hoy_es_dia_permitido_y_manana_no() {
        assertTrue(FechaBajaEfectiva.esDiaPermitido(epochDay(15, 9), ahora))
        assertTrue(FechaBajaEfectiva.esDiaPermitido(epochDay(1, 1, 2020), ahora))
        assertFalse(FechaBajaEfectiva.esDiaPermitido(epochDay(16, 9), ahora))
        assertFalse(FechaBajaEfectiva.esDiaPermitido(epochDay(15, 9, 2027), ahora))
    }

    @Test
    fun dia_local_de_ahora_es_el_dia_de_hoy() {
        assertEquals(epochDay(15, 9), FechaBajaEfectiva.diaLocalDe(ahora))
    }

    // La fecha efectiva elegida NO sustituye al momento actual: las reservas
    // futuras se siguen cancelando con `ahora` (ver ClienteViewModel /
    // BajaClienteRemotoRepository), no con esta fecha histórica.
    @Test
    fun la_fecha_historica_elegida_no_es_el_momento_actual() {
        val fechaElegida = FechaBajaEfectiva.millis(epochDay(1, 1), ahora)
        assertTrue(fechaElegida < ahora)
    }
}
