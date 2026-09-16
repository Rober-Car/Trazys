package com.roberto.gestorpro

import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.util.RetiradaNotificacionReglas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RetiradaNotificacionReglasTest
 * ------------------------------
 * Tests de la regla pura de ELIMINACIÓN de notificaciones: eliminables si están
 * publicadas o en entrega (PENDIENTE/ENVIADA), con independencia del origen
 * (manual, automática o preconfigurada). Las programadas sin publicar y los
 * estados no disponibles no se eliminan. Incluye el id determinista de buzón.
 */
class RetiradaNotificacionReglasTest {

    // --- Publicadas / en entrega: eliminables (cualquier origen) ---

    @Test
    fun `pendiente - eliminable`() {
        // Cubre también las automáticas/preconfiguradas (BAJA_CONFIRMADA,
        // MOROSIDAD...) cuando están en proceso de entrega.
        assertTrue(RetiradaNotificacionReglas.esRetirable("PENDIENTE"))
    }

    @Test
    fun `enviada - eliminable`() {
        assertTrue(RetiradaNotificacionReglas.esRetirable("ENVIADA"))
    }

    // --- Programadas aún no ejecutadas: NO se eliminan (usan cancelación) ---

    @Test
    fun `programada sin publicar - no eliminable`() {
        assertFalse(RetiradaNotificacionReglas.esRetirable("PROGRAMADA"))
    }

    // --- Otros estados no publicados: no eliminables ---

    @Test
    fun `cancelada - no eliminable`() {
        assertFalse(RetiradaNotificacionReglas.esRetirable("CANCELADA"))
    }

    @Test
    fun `error sin destinatarios - no eliminable`() {
        assertFalse(RetiradaNotificacionReglas.esRetirable("ERROR"))
    }

    @Test
    fun `estado ausente - no eliminable`() {
        assertFalse(RetiradaNotificacionReglas.esRetirable(null))
    }

    // --- Id determinista de buzón ---

    @Test
    fun `id de buzon sigue el patron clienteId_notificacionId`() {
        assertEquals(
            "890_n_1750000000000_1234",
            NotificacionRemotoRepository.idDeBuzon(890, "n_1750000000000_1234")
        )
    }
}
