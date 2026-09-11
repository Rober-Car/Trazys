package com.roberto.gestorpro

import com.roberto.gestorpro.data.entity.ClienteEntity
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.ui.viewmodel.aplicarBaja
import com.roberto.gestorpro.ui.viewmodel.prepararReactivacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ClienteTransicionEstadoTest
 * ---------------------------
 * Tests de las transiciones de estado que afectan a `fechaBaja` (frontera de la
 * etapa económica) y a `fechaAlta`:
 *  - BAJA -> ACTIVO (reactivación): se conserva la fechaBaja (última baja).
 *  - ACTIVO -> BAJA (segunda baja): se fija SIEMPRE la fecha actual.
 */
class ClienteTransicionEstadoTest {

    private fun cliente(
        estado: EstadoCliente,
        fechaAlta: Long? = null,
        fechaBaja: Long? = null
    ): ClienteEntity = ClienteEntity(
        idCliente = 1,
        nombre = "Nombre",
        apellidos = "Apellidos",
        dni = "12345678X",
        telefono = "600000000",
        email = null,
        foto = "",
        fechaNacimiento = null,
        fechaRegistro = 1L,
        fechaAlta = fechaAlta,
        fechaBaja = fechaBaja,
        estado = estado,
        observaciones = null,
        negocioId = null,
        serviciosContratados = emptyList(),
        firebaseUid = null,
        moroso = false,
        fechaEntradaMorosidad = null,
        exentoMorosidad = false
    )

    // Reactivación BAJA -> ACTIVO: fechaBaja se CONSERVA y fechaAlta se renueva.
    @Test
    fun reactivacion_conserva_fecha_baja() {
        val fechaBajaAnterior = 1_000_000L
        val ahora = 2_000_000L
        val entidadActivo = cliente(estado = EstadoCliente.ACTIVO)
        val fichaPrevia = cliente(estado = EstadoCliente.BAJA, fechaBaja = fechaBajaAnterior)

        val resultado = prepararReactivacion(entidadActivo, fichaPrevia, ahora)

        assertEquals(EstadoCliente.ACTIVO, resultado.estado)
        assertEquals(fechaBajaAnterior, resultado.fechaBaja)
        assertEquals(ahora, resultado.fechaAlta)
    }

    // Sin transición de BAJA previa, la reactivación no altera nada.
    @Test
    fun sin_baja_previa_la_edicion_no_alterea_fechas() {
        val ahora = 2_000_000L
        val entidadActivo = cliente(estado = EstadoCliente.ACTIVO, fechaAlta = 500_000L)

        val resultado = prepararReactivacion(entidadActivo, null, ahora)

        assertEquals(EstadoCliente.ACTIVO, resultado.estado)
        assertEquals(500_000L, resultado.fechaAlta)
        assertNull(resultado.fechaBaja)
    }

    // Una nueva BAJA (segunda baja) usa SIEMPRE la fecha actual, no la anterior.
    @Test
    fun segunda_baja_actualiza_fecha_a_la_actual() {
        val fechaBajaAntigua = 1_000_000L
        val ahora = 2_000_000L
        val activoConBajaPrevia = cliente(
            estado = EstadoCliente.ACTIVO,
            fechaAlta = 900_000L,
            fechaBaja = fechaBajaAntigua
        )

        val resultado = aplicarBaja(activoConBajaPrevia, ahora)

        assertEquals(EstadoCliente.BAJA, resultado.estado)
        assertEquals(ahora, resultado.fechaBaja)
    }

    // Una BAJA normal desde ACTIVO sin historial también fija la fecha actual.
    @Test
    fun baja_desde_activo_fija_fecha_actual() {
        val ahora = 3_000_000L
        val activo = cliente(estado = EstadoCliente.ACTIVO)

        val resultado = aplicarBaja(activo, ahora)

        assertEquals(EstadoCliente.BAJA, resultado.estado)
        assertEquals(ahora, resultado.fechaBaja)
    }

    // Baja con una fecha PASADA elegida: se persiste EXACTAMENTE esa fecha
    // (ni la actual ni otra), sin tocar el resto de campos.
    @Test
    fun baja_con_fecha_elegida_persiste_exactamente_esa_fecha() {
        val fechaElegida = 1_600_000_000_000L
        val activo = cliente(estado = EstadoCliente.ACTIVO, fechaAlta = 900_000L)

        val resultado = aplicarBaja(activo, fechaElegida)

        assertEquals(EstadoCliente.BAJA, resultado.estado)
        assertEquals(fechaElegida, resultado.fechaBaja)
        // La fechaAlta no se modifica al dar de baja.
        assertEquals(900_000L, resultado.fechaAlta)
    }
}
