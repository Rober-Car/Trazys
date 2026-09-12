package com.roberto.gestorpro

import com.roberto.gestorpro.ui.notificaciones.formatoFechaProgramada
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FormatoFechaProgramadaTest
 * --------------------------
 * Regresión del crash de "Crear notificación PROGRAMADA": el formateador usaba
 * el patrón inválido `dd/MM/aaaa HH:mm` (lanzaba
 * `IllegalArgumentException: Too many pattern letters: a` al formatear la fecha).
 * Ahora usa `yyyy` y debe formatear sin lanzar.
 */
class FormatoFechaProgramadaTest {

    private val patron = Regex("^\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}$")

    @Test
    fun formateaFechaValidaSinExcepcion() {
        // 2026-09-12T12:41:00Z aprox.
        val texto = formatoFechaProgramada(1757680860000L)
        assertTrue("formato inesperado: $texto", patron.matches(texto))
    }

    @Test
    fun formateaFechaFuturaSinExcepcion() {
        val futuro = System.currentTimeMillis() + 86_400_000L
        val texto = formatoFechaProgramada(futuro)
        assertTrue("formato inesperado: $texto", patron.matches(texto))
    }

    @Test
    fun elPatronNoUsaLetrasInvalidas() {
        // Si se reintrodujera `aaaa`, `formatoFechaProgramada` lanzaría y este
        // test fallaría al invocarla.
        val texto = formatoFechaProgramada(0L)
        assertTrue("formato inesperado: $texto", patron.matches(texto))
    }
}
