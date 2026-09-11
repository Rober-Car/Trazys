package com.roberto.gestorpro

import com.roberto.gestorpro.data.firebase.BajaClienteRemotoRepository
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BajaConfirmadaFechaTest
 * -----------------------
 * La notificación BAJA_CONFIRMADA usa la FECHA EFECTIVA elegida para la baja
 * (ID determinista y texto con formato), no la fecha de resolución "de hoy".
 */
class BajaConfirmadaFechaTest {

    @Test
    fun el_id_de_baja_confirmada_usa_la_fecha_efectiva_elegida() {
        val idCliente = 42
        val fechaElegida = 1_600_000_000_000L
        val id = BajaClienteRemotoRepository.idNotificacionBajaConfirmada(
            idCliente,
            fechaElegida
        )
        assertTrue(id.contains(idCliente.toString()))
        assertTrue(id.contains(fechaElegida.toString()))
    }

    @Test
    fun dos_fechas_distintas_generan_ids_distintos() {
        val idCliente = 42
        val conFechaElegida = BajaClienteRemotoRepository.idNotificacionBajaConfirmada(
            idCliente,
            1_600_000_000_000L
        )
        val conHoy = BajaClienteRemotoRepository.idNotificacionBajaConfirmada(
            idCliente,
            System.currentTimeMillis()
        )
        assertNotEquals(conFechaElegida, conHoy)
    }
}
