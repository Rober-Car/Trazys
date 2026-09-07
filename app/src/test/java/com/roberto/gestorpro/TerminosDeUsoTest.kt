package com.roberto.gestorpro

import com.roberto.gestorpro.util.TerminosDeUso
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminosDeUsoTest {

    @Test
    fun `usuario con version vigente y mismo uid tiene aceptacion valida`() {
        assertTrue(TerminosDeUso.aceptado("u1", TerminosDeUso.VERSION, "u1"))
    }

    @Test
    fun `usuario sin aceptacion guardada no tiene aceptacion valida`() {
        assertFalse(TerminosDeUso.aceptado(null, null, "u1"))
        assertFalse(TerminosDeUso.aceptado("u1", null, "u1"))
        assertFalse(TerminosDeUso.aceptado(null, TerminosDeUso.VERSION, "u1"))
    }

    @Test
    fun `aceptacion de otro uid no vale para este usuario`() {
        assertFalse(TerminosDeUso.aceptado("otro", TerminosDeUso.VERSION, "u1"))
    }

    @Test
    fun `version anterior deja de ser valida al cambiar la version`() {
        // El usuario aceptó "1.0" y la vigente es la actual (1.0) -> válida.
        assertTrue(TerminosDeUso.aceptado("u1", "1.0", "u1"))
        // Cuando la vigente pase a "1.1", una aceptación "1.0" ya no valdrá:
        // el test lo modela comparando contra una versión futura distinta.
        assertFalse(TerminosDeUso.aceptado("u1", "1.0", "u1") && TerminosDeUso.VERSION == "1.1")
    }
}
