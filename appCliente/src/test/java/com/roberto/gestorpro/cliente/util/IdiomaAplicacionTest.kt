package com.roberto.gestorpro.cliente.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * IdiomaAplicacionTest
 * --------------------
 * Verifica la localización de contenido remoto (títulos de notificaciones):
 * español por defecto, inglés cuando el idioma aplicado es "en" y existe
 * traducción, y compatibilidad con contenido antiguo sin traducción.
 */
class IdiomaAplicacionTest {

    @Test
    fun textoLocalizado_devuelve_ingles_cuando_idioma_en_y_hay_traduccion() {
        assertEquals(
            "Payment overdue",
            IdiomaAplicacion.textoLocalizado("Alerta de pago vencido", "Payment overdue", "en")
        )
        assertEquals(
            "Overdue payment reminder",
            IdiomaAplicacion.textoLocalizado(
                "Recordatorio de pago vencido",
                "Overdue payment reminder",
                "en"
            )
        )
    }

    @Test
    fun textoLocalizado_devuelve_espanol_cuando_idioma_es() {
        assertEquals(
            "Alerta de pago vencido",
            IdiomaAplicacion.textoLocalizado("Alerta de pago vencido", "Payment overdue", "es")
        )
    }

    @Test
    fun textoLocalizado_compatible_sin_traduccion() {
        assertEquals(
            "Alerta de pago vencido",
            IdiomaAplicacion.textoLocalizado("Alerta de pago vencido", null, "en")
        )
        assertEquals(
            "Alerta de pago vencido",
            IdiomaAplicacion.textoLocalizado("Alerta de pago vencido", "", "en")
        )
    }
}
