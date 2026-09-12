package com.roberto.gestorpro

import com.google.firebase.Timestamp
import com.roberto.gestorpro.model.EstadoMovimiento
import com.roberto.gestorpro.model.MetodoPago
import com.roberto.gestorpro.util.HidratacionMapeadores
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HidratacionMapeadoresTest
 * -------------------------
 * Tests unitarios PUROS (JVM) de los mapeadores remoto -> entidad de la
 * hidratación central de la caché Room. No requieren emulador ni integración.
 * Cubren: mapeo correcto por entidad, rechazo de documentos de OTRO negocio,
 * ids altos conservados, movimientos con servicios vacíos y fechas Timestamp.
 */
class HidratacionMapeadoresTest {

    private val negocio = "rdKODwReHrMN8KSpotMG6rI6qdi1"
    private val otroNegocio = "BW8av33rFoXr6gyG83sDPXLqreS2"

    private fun fecha(dia: Int, mes: Int, anio: Int = 2026): Long =
        java.time.LocalDate.of(anio, mes, dia)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    // =================================================================
    // SERVICIOS
    // =================================================================

    @Test
    fun servicio_mapaCorrecto_conservaCampos() {
        val datos = mapOf(
            "idServicio" to 1L,
            "negocioId" to negocio,
            "nombre" to "CrossFit",
            "descripcion" to "Entrenamiento",
            "activo" to true,
            "precio" to 30.0
        )
        val servicio = HidratacionMapeadores.servicioDeDocumento(datos, negocio)
        assertNotNull(servicio)
        servicio!!.let {
            assertEquals(1, it.idServicio)
            assertEquals(negocio, it.negocioId)
            assertEquals("CrossFit", it.nombre)
            assertEquals("Entrenamiento", it.descripcion)
            assertTrue(it.activo)
            assertEquals(30.0, it.precio, 0.0)
        }
    }

    @Test
    fun servicio_deOtroNegocio_rechazado() {
        val datos = mapOf(
            "idServicio" to 1L,
            "negocioId" to otroNegocio,
            "nombre" to "CrossFit",
            "activo" to true,
            "precio" to 30.0
        )
        assertNull(HidratacionMapeadores.servicioDeDocumento(datos, negocio))
    }

    @Test
    fun servicio_sinCamposEsenciales_rechazado() {
        val datos = mapOf("negocioId" to negocio, "nombre" to "Sin id")
        assertNull(HidratacionMapeadores.servicioDeDocumento(datos, negocio))
    }

    // =================================================================
    // SESIONES
    // =================================================================

    @Test
    fun sesion_mapaCorrecto_conservaHoraDesdeReserva() {
        val datos = mapOf(
            "idSesion" to 42L,
            "negocioId" to negocio,
            "idServicio" to 1L,
            "fecha" to fecha(5, 9),
            "hora" to "18:00",
            "duracionMinutos" to 60L,
            "capacidad" to 12L,
            "plazasDisponibles" to 7L,
            "horaDesdeReserva" to "17:30"
        )
        val sesion = HidratacionMapeadores.sesionDeDocumento(datos, negocio)
        assertNotNull(sesion)
        sesion!!.let {
            assertEquals(42, it.idSesion)
            assertEquals(negocio, it.negocioId)
            assertEquals(1, it.idServicio)
            assertEquals(fecha(5, 9), it.fecha)
            assertEquals("18:00", it.hora)
            assertEquals(60, it.duracionMinutos)
            assertEquals(12, it.capacidad)
            assertEquals(7, it.plazasDisponibles)
            assertEquals("17:30", it.horaDesdeReserva)
        }
    }

    @Test
    fun sesion_horaDesdeReservaAusente_esNull() {
        val datos = mapOf(
            "idSesion" to 42L,
            "negocioId" to negocio,
            "idServicio" to 1L,
            "fecha" to fecha(5, 9),
            "hora" to "18:00",
            "duracionMinutos" to 60L,
            "capacidad" to 12L,
            "plazasDisponibles" to 12L
        )
        assertNull(HidratacionMapeadores.sesionDeDocumento(datos, negocio)?.horaDesdeReserva)
    }

    @Test
    fun sesion_deOtroNegocio_rechazada() {
        val datos = mapOf(
            "idSesion" to 42L,
            "negocioId" to otroNegocio,
            "idServicio" to 1L,
            "fecha" to fecha(5, 9),
            "hora" to "18:00",
            "duracionMinutos" to 60L,
            "capacidad" to 12L,
            "plazasDisponibles" to 12L
        )
        assertNull(HidratacionMapeadores.sesionDeDocumento(datos, negocio))
    }

    // =================================================================
    // RESERVAS
    // =================================================================

    @Test
    fun reserva_mapaCorrecto() {
        val datos = mapOf(
            "negocioId" to negocio,
            "sesionId" to 42L,
            "clienteId" to 7L,
            "fechaReserva" to Timestamp(Date(fecha(1, 9)))
        )
        val reserva = HidratacionMapeadores.reservaDeDocumento(datos, negocio)
        assertNotNull(reserva)
        reserva!!.let {
            assertEquals(negocio, it.negocioId)
            assertEquals(42, it.idSesion)
            assertEquals(7, it.idCliente)
            assertEquals(fecha(1, 9), it.fechaReserva)
        }
    }

    @Test
    fun reserva_deOtroNegocio_rechazada() {
        val datos = mapOf(
            "negocioId" to otroNegocio,
            "sesionId" to 42L,
            "clienteId" to 7L,
            "fechaReserva" to Timestamp(Date(fecha(1, 9)))
        )
        assertNull(HidratacionMapeadores.reservaDeDocumento(datos, negocio))
    }

    // =================================================================
    // MOVIMIENTOS
    // =================================================================

    @Test
    fun movimiento_mapaCorrecto_conservaIdAltoMetodoYEstado() {
        val datos = mapOf(
            "idMovimiento" to 1_500_000_000L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "servicios" to listOf(1L, 2L),
            "fechaInicio" to Timestamp(Date(fecha(1, 9))),
            "fechaFin" to Timestamp(Date(fecha(30, 9))),
            "fechaRegistro" to Timestamp(Date(fecha(2, 9))),
            "precioFinal" to 42.5,
            "estado" to EstadoMovimiento.PAGADO.name,
            "fechaPago" to Timestamp(Date(fecha(5, 9))),
            "metodoPago" to MetodoPago.BIZUM.name,
            "observaciones" to "Cuota septiembre"
        )
        val movimiento = HidratacionMapeadores.movimientoDeDocumento(datos, negocio)
        assertNotNull(movimiento)
        movimiento!!.let {
            assertEquals(1_500_000_000, it.idMovimiento)
            assertEquals(9, it.idCliente)
            // Documento sin identidad histórica → campos vacíos (compatibilidad).
            assertEquals("", it.nombreCliente)
            assertEquals("", it.apellidosCliente)
            assertEquals("", it.dniCliente)
            assertEquals(listOf(1, 2), it.servicios)
            assertEquals(fecha(1, 9), it.fechaInicio)
            assertEquals(fecha(30, 9), it.fechaFin)
            assertEquals(fecha(2, 9), it.fechaRegistro)
            assertEquals(42.5, it.precioFinal, 0.0)
            assertEquals(EstadoMovimiento.PAGADO, it.estado)
            assertEquals(fecha(5, 9), it.fechaPago)
            assertEquals(MetodoPago.BIZUM, it.metodoPago)
            assertEquals("Cuota septiembre", it.observaciones)
        }
    }

    @Test
    fun movimiento_conIdentidadHistorica_la_conserva() {
        val datos = mapOf(
            "idMovimiento" to 8L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "nombreCliente" to "Marta",
            "apellidosCliente" to "Ruiz",
            "dniCliente" to "87654321X",
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 10.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        val movimiento = HidratacionMapeadores.movimientoDeDocumento(datos, negocio)
        assertNotNull(movimiento)
        assertEquals("Marta", movimiento!!.nombreCliente)
        assertEquals("Ruiz", movimiento.apellidosCliente)
        assertEquals("87654321X", movimiento.dniCliente)
    }

    @Test
    fun movimiento_conIdentidadHistoricaNull_la_normalizaAVacio() {
        val datos = mapOf(
            "idMovimiento" to 9L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "nombreCliente" to null,
            "apellidosCliente" to null,
            "dniCliente" to null,
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 10.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        val movimiento = HidratacionMapeadores.movimientoDeDocumento(datos, negocio)
        assertNotNull(movimiento)
        assertEquals("", movimiento!!.nombreCliente)
        assertEquals("", movimiento.apellidosCliente)
        assertEquals("", movimiento.dniCliente)
    }

    @Test
    fun movimiento_sinFechaRegistro_usaCero() {
        val datos = mapOf(
            "idMovimiento" to 4L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 10.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        val movimiento = HidratacionMapeadores.movimientoDeDocumento(datos, negocio)
        assertNotNull(movimiento)
        assertEquals(0L, movimiento!!.fechaRegistro)
    }

    @Test
    fun movimiento_conServiciosVacios_noRompe() {
        val datos = mapOf(
            "idMovimiento" to 3L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "servicios" to emptyList<Any>(),
            "fechaInicio" to Timestamp(Date(fecha(1, 9))),
            "fechaFin" to Timestamp(Date(fecha(30, 9))),
            "precioFinal" to 20.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        assertEquals(
            emptyList<Int>(),
            HidratacionMapeadores.movimientoDeDocumento(datos, negocio)?.servicios
        )
    }

    @Test
    fun movimiento_sinCampoServicios_usaListaVacia() {
        val datos = mapOf(
            "idMovimiento" to 3L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 20.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        val movimiento = HidratacionMapeadores.movimientoDeDocumento(datos, negocio)
        assertNotNull(movimiento)
        assertTrue(movimiento!!.servicios.isEmpty())
        // fechaInicio/fechaFin aceptan Number (int) además de Timestamp.
        assertEquals(fecha(1, 9), movimiento.fechaInicio)
    }

    @Test
    fun movimiento_deOtroNegocio_rechazado() {
        val datos = mapOf(
            "idMovimiento" to 3L,
            "negocioId" to otroNegocio,
            "idCliente" to 9L,
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 20.0,
            "estado" to EstadoMovimiento.PENDIENTE.name
        )
        assertNull(HidratacionMapeadores.movimientoDeDocumento(datos, negocio))
    }

    @Test
    fun movimiento_conEstadoInvalido_rechazado() {
        val datos = mapOf(
            "idMovimiento" to 3L,
            "negocioId" to negocio,
            "idCliente" to 9L,
            "fechaInicio" to fecha(1, 9),
            "fechaFin" to fecha(30, 9),
            "precioFinal" to 20.0,
            "estado" to "PAGADOOO"
        )
        assertNull(HidratacionMapeadores.movimientoDeDocumento(datos, negocio))
    }
}
