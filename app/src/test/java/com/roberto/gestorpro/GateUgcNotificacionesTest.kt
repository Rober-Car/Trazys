package com.roberto.gestorpro

import com.roberto.gestorpro.util.GateUgcNotificaciones
import com.roberto.gestorpro.util.ResultadoGateUgc
import com.roberto.gestorpro.util.TerminosDeUso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GateUgcNotificacionesTest
 * -------------------------
 * Tests de la regla pura del gate de Términos de uso para la publicación
 * MANUAL de notificaciones del ADMIN (FASE 2B-1).
 *
 * Cubre: manual con la versión vigente aceptada, sin aceptación, con una
 * versión anterior, tras aceptar, y la garantía de que las notificaciones
 * automáticas/preconfiguradas NUNCA se bloquean por este gate.
 */
class GateUgcNotificacionesTest {

    // --- Publicación manual (inmediata) ---

    @Test
    fun `manual con version vigente aceptada - permitida`() {
        val vigente = TerminosDeUso.aceptado("u1", TerminosDeUso.VERSION, "u1")
        assertEquals(
            ResultadoGateUgc.PERMITIDO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, vigente)
        )
    }

    @Test
    fun `manual sin aceptacion guardada - bloqueada`() {
        val sinAceptar = TerminosDeUso.aceptado(null, null, "u1")
        assertFalse(sinAceptar)
        assertEquals(
            ResultadoGateUgc.BLOQUEADO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, sinAceptar)
        )
    }

    @Test
    fun `manual con version anterior a la vigente - bloqueada`() {
        val versionAntigua = TerminosDeUso.aceptado("u1", "0.9", "u1")
        assertFalse(versionAntigua)
        assertEquals(
            ResultadoGateUgc.BLOQUEADO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, versionAntigua)
        )
    }

    @Test
    fun `manual con aceptacion de otro uid - bloqueada`() {
        val otroUid = TerminosDeUso.aceptado("otro", TerminosDeUso.VERSION, "u1")
        assertFalse(otroUid)
        assertEquals(
            ResultadoGateUgc.BLOQUEADO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, otroUid)
        )
    }

    @Test
    fun `manual despues de aceptar - permitida`() {
        val aceptado = TerminosDeUso.aceptado("u1", TerminosDeUso.VERSION, "u1")
        assertTrue(aceptado)
        assertEquals(
            ResultadoGateUgc.PERMITIDO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, aceptado)
        )
    }

    // --- Publicación manual programada ---

    @Test
    fun `manual programada sin terminos vigentes - bloqueada`() {
        // La programada también la publica el ADMIN con origen MANUAL: el gate
        // actúa igual en el momento de CREAR/PROGRAMAR (antes de Firestore).
        assertTrue(GateUgcNotificaciones.esPublicacionManual(GateUgcNotificaciones.ORIGEN_MANUAL))
        assertEquals(
            ResultadoGateUgc.BLOQUEADO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, false)
        )
    }

    @Test
    fun `manual programada con terminos vigentes - permitida`() {
        assertEquals(
            ResultadoGateUgc.PERMITIDO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_MANUAL, true)
        )
    }

    // --- Automáticas / preconfiguradas: nunca bloqueadas ---

    @Test
    fun `automatica sin terminos vigentes - no bloqueada`() {
        assertEquals(
            ResultadoGateUgc.PERMITIDO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_AUTOMATICA, false)
        )
    }

    @Test
    fun `preconfigurada baja confirmada sin terminos - no bloqueada`() {
        assertEquals(
            ResultadoGateUgc.PERMITIDO,
            GateUgcNotificaciones.decidir(GateUgcNotificaciones.ORIGEN_PRECONFIGURADA, false)
        )
    }

    // --- Clasificación manual vs automática ---

    @Test
    fun `solo el origen MANUAL se considera publicacion manual`() {
        assertTrue(GateUgcNotificaciones.esPublicacionManual(GateUgcNotificaciones.ORIGEN_MANUAL))
        assertTrue(GateUgcNotificaciones.esPublicacionManual(null))
        assertFalse(GateUgcNotificaciones.esPublicacionManual(GateUgcNotificaciones.ORIGEN_AUTOMATICA))
        assertFalse(
            GateUgcNotificaciones.esPublicacionManual(GateUgcNotificaciones.ORIGEN_PRECONFIGURADA)
        )
    }
}
