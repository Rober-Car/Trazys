package com.roberto.gestorpro

import com.roberto.gestorpro.data.firebase.NotificacionRemotoRepository
import com.roberto.gestorpro.util.NotificacionBajaRechazada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NotificacionBajaRechazadaTest
 * -----------------------------
 * Tests unitarios (sin Firebase) de la notificación de BAJA RECHAZADA:
 * - el switch está activo por defecto (igual que baja confirmada);
 * - rechazo activado -> se crea; desactivado -> no se crea;
 * - idempotencia: si la notificación ya existe, no se vuelve a crear;
 * - el ID determinista por solicitud evita duplicados.
 */
class NotificacionBajaRechazadaTest {

    @Test
    fun configuracionPorDefecto_bajaRechazadaActiva() {
        val config = NotificacionRemotoRepository.configuracionPorDefecto()
        assertTrue(config.bajaRechazadaActiva)
    }

    @Test
    fun sinValor_esActivaPorDefecto() {
        assertTrue(NotificacionBajaRechazada.activaPorDefecto(null))
        assertTrue(NotificacionRemotoRepository.bajaRechazadaActivaPorDefecto(null))
    }

    @Test
    fun activaExplicitaTrue_seGenera() {
        assertTrue(NotificacionBajaRechazada.debeCrear(configActiva = true, yaExiste = false))
    }

    @Test
    fun desactivada_noSeGenera() {
        assertFalse(NotificacionBajaRechazada.debeCrear(configActiva = false, yaExiste = false))
    }

    @Test
    fun idempotencia_yaExiste_noSeGenera() {
        assertFalse(NotificacionBajaRechazada.debeCrear(configActiva = true, yaExiste = true))
        assertFalse(NotificacionBajaRechazada.debeCrear(configActiva = null, yaExiste = true))
    }

    @Test
    fun idDeterminista_porSolicitud() {
        val id1 = NotificacionBajaRechazada.idDeterminista("baja_7_1700000000000")
        val id2 = NotificacionBajaRechazada.idDeterminista("baja_7_1700000000000")
        val idOtra = NotificacionBajaRechazada.idDeterminista("baja_8_1700000000000")
        assertEquals(id1, id2)
        assertNotEquals(id1, idOtra)
        assertEquals(id1, NotificacionRemotoRepository.idNotificacionBajaRechazada("baja_7_1700000000000"))
    }
}
