package com.roberto.gestorpro.cliente

import com.roberto.gestorpro.cliente.util.TerminosDeUso
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminosDeUsoTest {

    @Test
    fun `aceptacion valida solo con version vigente y mismo uid`() {
        assertTrue(TerminosDeUso.aceptado("c1", TerminosDeUso.VERSION, "c1"))
        assertFalse(TerminosDeUso.aceptado(null, null, "c1"))
        assertFalse(TerminosDeUso.aceptado("c1", null, "c1"))
        assertFalse(TerminosDeUso.aceptado("otro", TerminosDeUso.VERSION, "c1"))
        assertFalse(TerminosDeUso.aceptado(null, TerminosDeUso.VERSION, "c1"))
    }

    @Test
    fun `version anterior obliga a nueva aceptacion`() {
        assertTrue(TerminosDeUso.aceptado("c1", "1.0", "c1"))
        assertFalse(TerminosDeUso.aceptado("c1", "1.0", "c1") && TerminosDeUso.VERSION == "1.1")
        assertFalse(TerminosDeUso.aceptado("c1", "0.9", "c1"))
    }

    @Test
    fun `sin uid actual ninguna aceptacion guardada vale`() {
        assertFalse(TerminosDeUso.aceptado("c1", TerminosDeUso.VERSION, null))
    }
}
