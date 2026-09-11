package com.roberto.gestorpro

import com.google.firebase.Timestamp
import com.roberto.gestorpro.data.entity.MovimientoEntity
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago
import com.roberto.gestorpro.util.MovimientoFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MovimientoFirestoreTest
 * -----------------------
 * Tests unitarios de la FASE 6 (FASE de ECONOMÍA): transformación pura
 * MovimientoEntity -> documento Firestore y resumen económico -> campos del
 * cliente. No requiere emulador ni integración con Firestore.
 */
class MovimientoFirestoreTest {

    private val fechaIni: Long = fecha(1, 9)
    private val fechaFin: Long = fecha(30, 9)
    private val fechaPagoValor: Long = fecha(5, 9)

    private fun movimiento(
        idMovimiento: Int = 7,
        servicios: List<Int> = listOf(1, 2),
        estado: EstadoMovimiento = EstadoMovimiento.PENDIENTE,
        fechaPago: Long? = null,
        metodoPago: MetodoPago? = null,
        observaciones: String? = null,
        precioFinal: Double = 42.5,
        fechaRegistro: Long = fechaIni
    ): MovimientoEntity = MovimientoEntity(
        idMovimiento = idMovimiento,
        idCliente = 3,
        servicios = servicios,
        fechaInicio = fechaIni,
        fechaFin = fechaFin,
        precioFinal = precioFinal,
        estado = estado,
        fechaPago = fechaPago,
        metodoPago = metodoPago,
        observaciones = observaciones,
        fechaRegistro = fechaRegistro
    )

    private fun fecha(dia: Int, mes: Int, anio: Int = 2026): Long =
        java.time.LocalDate.of(anio, mes, dia)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun assertBaseMovimiento(
        mapa: Map<String, Any?>,
        movimiento: MovimientoEntity,
        negocioId: String = "negocio-admin-1"
    ) {
        assertEquals("claves del contrato", EXPECTED_KEYS, mapa.keys.sorted())
        assertEquals(movimiento.idMovimiento, mapa["idMovimiento"])
        assertEquals(negocioId, mapa["negocioId"])
        assertEquals(movimiento.idCliente, mapa["idCliente"])
        assertEquals(movimiento.servicios, mapa["servicios"])
        assertEquals(movimiento.precioFinal, mapa["precioFinal"])
        assertEquals(movimiento.estado.name, mapa["estado"])

        val inicio = mapa["fechaInicio"] as Timestamp
        val fin = mapa["fechaFin"] as Timestamp
        val registro = mapa["fechaRegistro"] as Timestamp
        assertEquals(fechaIni, inicio.toDate().time)
        assertEquals(fechaFin, fin.toDate().time)
        assertEquals(movimiento.fechaRegistro, registro.toDate().time)
    }

    private fun assertMillis(mapa: Map<String, Any?>, clave: String, esperado: Long?) {
        val valor = mapa[clave]
        if (esperado == null) {
            assertNull("$clave debe ser null", valor)
        } else {
            assertNotNull("$clave no debe ser null", valor)
            assertEquals(esperado, (valor as Timestamp).toDate().time)
        }
    }

    // 1. Movimiento PENDIENTE (sin pago).
    @Test
    fun pendiente_se_mapea_con_estado_y_sin_fecha_pago() {
        val mov = movimiento(estado = EstadoMovimiento.PENDIENTE)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertBaseMovimiento(mapa, mov)
        assertEquals("PENDIENTE", mapa["estado"])
        assertMillis(mapa, "fechaPago", null)
        assertNull(mapa["metodoPago"])
        assertNull(mapa["observaciones"])
    }

    // 2. Movimiento PAGADO con fechaPago y metodoPago.
    @Test
    fun pagado_mapea_estado_fecha_pago_y_metodo() {
        val mov = movimiento(
            estado = EstadoMovimiento.PAGADO,
            fechaPago = fechaPagoValor,
            metodoPago = MetodoPago.EFECTIVO
        )
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertBaseMovimiento(mapa, mov)
        assertEquals("PAGADO", mapa["estado"])
        assertMillis(mapa, "fechaPago", fechaPagoValor)
        assertEquals("EFECTIVO", mapa["metodoPago"])
    }

    // 3. Varios servicios se conservan como lista de IDs.
    @Test
    fun varios_servicios_se_conservan_como_ids() {
        val mov = movimiento(servicios = listOf(1, 2, 3))
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals(listOf(1, 2, 3), mapa["servicios"])
    }

    // 4. Movimiento histórico sin servicios: lista vacía.
    @Test
    fun movimiento_historico_sin_servicios_mapea_lista_vacia() {
        val mov = movimiento(servicios = emptyList())
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals(emptyList<Int>(), mapa["servicios"])
    }

    // 5. fechaPago null (PENDIENTE sin cobro).
    @Test
    fun fecha_pago_null_se_mapea_null() {
        val mov = movimiento(estado = EstadoMovimiento.PENDIENTE, fechaPago = null)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertMillis(mapa, "fechaPago", null)
    }

    // 6. fechaPago existente (Timestamp).
    @Test
    fun fecha_pago_existente_se_mapea_timestamp() {
        val mov = movimiento(fechaPago = fechaPagoValor)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertMillis(mapa, "fechaPago", fechaPagoValor)
    }

    // 7. metodoPago null.
    @Test
    fun metodo_pago_null_se_mapea_null() {
        val mov = movimiento(metodoPago = null)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertNull(mapa["metodoPago"])
    }

    // 8. EFECTIVO.
    @Test
    fun metodo_pago_efectivo() {
        val mov = movimiento(metodoPago = MetodoPago.EFECTIVO)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals("EFECTIVO", mapa["metodoPago"])
    }

    // 9. BIZUM.
    @Test
    fun metodo_pago_bizum() {
        val mov = movimiento(metodoPago = MetodoPago.BIZUM)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals("BIZUM", mapa["metodoPago"])
    }

    // 10. TRANSFERENCIA.
    @Test
    fun metodo_pago_transferencia() {
        val mov = movimiento(metodoPago = MetodoPago.TRANSFERENCIA)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals("TRANSFERENCIA", mapa["metodoPago"])
    }

    // 11. observaciones null.
    @Test
    fun observaciones_null_se_mapean_null() {
        val mov = movimiento(observaciones = null)
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertNull(mapa["observaciones"])
    }

    // 12. observaciones con contenido.
    @Test
    fun observaciones_con_contenido_se_conservan() {
        val mov = movimiento(observaciones = "Cuota de septiembre revisada")
        val mapa = MovimientoFirestore.documentoDe(mov, "negocio-admin-1")

        assertEquals("Cuota de septiembre revisada", mapa["observaciones"])
    }

    // Resumen económico -> clientes/{id}: moroso, fechaEntradaMorosidad, deuda,
    // periodo actual y exentoMorosidad.
    @Test
    fun resumen_con_morosidad_se_mapea_completo() {
        val mapa = MovimientoFirestore.resumenDeCliente(
            moroso = true,
            fechaEntradaMorosidad = fechaFin,
            deuda = 42.5,
            fechaInicioActual = fechaIni,
            fechaFinActual = fechaFin,
            exentoMorosidad = false
        )

        assertEquals(true, mapa["moroso"])
        assertEquals(42.5, mapa["deuda"])
        assertEquals(false, mapa["exentoMorosidad"])
        assertMillis(mapa, "fechaEntradaMorosidad", fechaFin)
        assertMillis(mapa, "fechaInicioActual", fechaIni)
        assertMillis(mapa, "fechaFinActual", fechaFin)
    }

    // Resumen con exención manual activa: moroso false pero deuda real.
    @Test
    fun resumen_con_exencion_mapea_moroso_false_y_deuda_real() {
        val mapa = MovimientoFirestore.resumenDeCliente(
            moroso = false,
            fechaEntradaMorosidad = null,
            deuda = 90.0,
            fechaInicioActual = fechaIni,
            fechaFinActual = fechaFin,
            exentoMorosidad = true
        )

        assertEquals(false, mapa["moroso"])
        assertEquals(90.0, mapa["deuda"])
        assertEquals(true, mapa["exentoMorosidad"])
        assertNull(mapa["fechaEntradaMorosidad"])
    }

    // Resumen sin morosidad: fechaEntradaMorosidad null y periodo null.
    @Test
    fun resumen_sin_morosidad_mapea_fecha_entrada_null() {
        val mapa = MovimientoFirestore.resumenDeCliente(
            moroso = false,
            fechaEntradaMorosidad = null,
            deuda = 0.0,
            fechaInicioActual = null,
            fechaFinActual = null,
            exentoMorosidad = false
        )

        assertEquals(false, mapa["moroso"])
        assertEquals(0.0, mapa["deuda"])
        assertEquals(false, mapa["exentoMorosidad"])
        assertNull(mapa["fechaEntradaMorosidad"])
        assertNull(mapa["fechaInicioActual"])
        assertNull(mapa["fechaFinActual"])
    }

    companion object {
        private val EXPECTED_KEYS = listOf(
            "idMovimiento",
            "negocioId",
            "idCliente",
            "servicios",
            "fechaInicio",
            "fechaFin",
            "fechaRegistro",
            "precioFinal",
            "estado",
            "fechaPago",
            "metodoPago",
            "observaciones"
        ).sorted()
    }
}
