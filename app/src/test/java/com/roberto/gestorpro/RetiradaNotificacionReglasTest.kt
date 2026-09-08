package com.roberto.gestorpro

import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.util.GateUgcNotificaciones
import com.roberto.gestorpro.util.RetiradaNotificacionReglas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RetiradaNotificacionReglasTest
 * ------------------------------
 * Tests de la regla pura de retirada de notificaciones MANUALES (FASE 2C-3).
 *
 * Cubre: solo manuales ya publicadas (PENDIENTE/ENVIADA) son retirables; las
 * automáticas/preconfiguradas nunca; las programadas aún no ejecutadas se
 * gestionan con la cancelación existente; y el id determinista de buzón.
 */
class RetiradaNotificacionReglasTest {

    // --- Manuales ya publicadas / en entrega: retirables ---

    @Test
    fun `manual inmediata pendiente - retirable`() {
        assertTrue(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_MANUAL,
                "PENDIENTE"
            )
        )
    }

    @Test
    fun `manual enviada - retirable`() {
        assertTrue(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_MANUAL,
                "ENVIADA"
            )
        )
    }

    @Test
    fun `origen ausente se trata como manual pendiente - retirable`() {
        assertTrue(RetiradaNotificacionReglas.esRetirable(null, "PENDIENTE"))
    }

    // --- Automáticas / preconfiguradas: nunca retirables ---

    @Test
    fun `automatica pendiente - bloqueada`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_AUTOMATICA,
                "PENDIENTE"
            )
        )
    }

    @Test
    fun `automatica enviada - bloqueada`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_AUTOMATICA,
                "ENVIADA"
            )
        )
    }

    @Test
    fun `preconfigurada enviada (baja confirmada) - bloqueada`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_PRECONFIGURADA,
                "ENVIADA"
            )
        )
    }

    @Test
    fun `notificaciones automaticas no retirables - tipos protegidos`() {
        // La retirada se decide por ORIGEN, no por tipo: una SOLICITUD_BAJA o
        // BAJA_CONFIRMADA con origen automático/preconfigurado nunca es retirable.
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_AUTOMATICA,
                "ENVIADA"
            )
        )
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_PRECONFIGURADA,
                "PENDIENTE"
            )
        )
    }

    // --- Programadas aún no ejecutadas: NO se tocan ---

    @Test
    fun `manual programada sin publicar - no retirable (usa cancelacion)`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_MANUAL,
                "PROGRAMADA"
            )
        )
    }

    // --- Otros estados no publicados: no retirables ---

    @Test
    fun `manual cancelada - no retirable`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_MANUAL,
                "CANCELADA"
            )
        )
    }

    @Test
    fun `manual en error sin destinatarios - no retirable`() {
        assertFalse(
            RetiradaNotificacionReglas.esRetirable(
                GateUgcNotificaciones.ORIGEN_MANUAL,
                "ERROR"
            )
        )
    }

    @Test
    fun `estado ausente - no retirable`() {
        assertFalse(RetiradaNotificacionReglas.esRetirable(GateUgcNotificaciones.ORIGEN_MANUAL, null))
        assertFalse(RetiradaNotificacionReglas.esRetirable(null, null))
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
