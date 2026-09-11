package com.roberto.gestorpro

import com.roberto.gestorpro.model.Cliente
import com.roberto.gestorpro.model.EstadoCliente
import com.roberto.gestorpro.model.FiltroClientes
import com.roberto.gestorpro.ui.clientes.FiltroCuenta
import com.roberto.gestorpro.ui.clientes.cumpleFiltroClientes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ClienteVinculacionFiltroTest
 * ----------------------------
 * Tests del predicado puro que combina el filtro de VINCULACIÓN (derivado de
 * firebaseUid) con el filtro de ESTADO de la lista de clientes. No toca datos.
 */
class ClienteVinculacionFiltroTest {

    private fun cliente(
        id: Int,
        estado: EstadoCliente,
        firebaseUid: String? = null
    ): Cliente = Cliente(
        idCliente = id,
        nombre = "Nombre $id",
        telefono = "600000000",
        email = null,
        dni = "12345678${'A' + (id % 26)}",
        foto = "",
        fechaNacimiento = null,
        estado = estado,
        observaciones = null,
        serviciosContratados = emptyList(),
        firebaseUid = firebaseUid
    )

    @Test
    fun firebaseUid_null_es_no_vinculado() {
        val sin = cliente(1, EstadoCliente.ACTIVO, firebaseUid = null)
        assertFalse(cumpleFiltroClientes(sin, FiltroCuenta.VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertTrue(cumpleFiltroClientes(sin, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertNull(sin.firebaseUid)
    }

    @Test
    fun firebaseUid_presente_es_vinculado() {
        val vinculado = cliente(2, EstadoCliente.ACTIVO, firebaseUid = "uid-cliente")
        assertTrue(cumpleFiltroClientes(vinculado, FiltroCuenta.VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertFalse(cumpleFiltroClientes(vinculado, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun filtro_todos_pasa_a_todos() {
        val activo = cliente(3, EstadoCliente.ACTIVO, firebaseUid = "u")
        val baja = cliente(4, EstadoCliente.BAJA)
        val archivado = cliente(5, EstadoCliente.ARCHIVADO)
        assertTrue(cumpleFiltroClientes(activo, FiltroCuenta.TODOS, FiltroClientes.TODOS, emptySet()))
        assertTrue(cumpleFiltroClientes(baja, FiltroCuenta.TODOS, FiltroClientes.TODOS, emptySet()))
        // "Todos" excluye archivados (comportamiento existente de la lista).
        assertFalse(cumpleFiltroClientes(archivado, FiltroCuenta.TODOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun filtro_vinculados_solo_vinculados() {
        val vinculado = cliente(6, EstadoCliente.BAJA, firebaseUid = "u")
        val noVinculado = cliente(7, EstadoCliente.BAJA)
        assertTrue(cumpleFiltroClientes(vinculado, FiltroCuenta.VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertFalse(cumpleFiltroClientes(noVinculado, FiltroCuenta.VINCULADOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun filtro_no_vinculados_solo_sin_cuenta() {
        val noVinculado = cliente(8, EstadoCliente.ACTIVO)
        val vinculado = cliente(9, EstadoCliente.ACTIVO, firebaseUid = "u")
        assertTrue(cumpleFiltroClientes(noVinculado, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertFalse(cumpleFiltroClientes(vinculado, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun filtro_activos() {
        val activo = cliente(10, EstadoCliente.ACTIVO)
        val baja = cliente(11, EstadoCliente.BAJA)
        assertTrue(cumpleFiltroClientes(activo, FiltroCuenta.TODOS, FiltroClientes.ACTIVO, emptySet()))
        assertFalse(cumpleFiltroClientes(baja, FiltroCuenta.TODOS, FiltroClientes.ACTIVO, emptySet()))
    }

    @Test
    fun filtro_bajas() {
        val baja = cliente(12, EstadoCliente.BAJA)
        val activo = cliente(13, EstadoCliente.ACTIVO)
        assertTrue(cumpleFiltroClientes(baja, FiltroCuenta.TODOS, FiltroClientes.BAJA, emptySet()))
        assertFalse(cumpleFiltroClientes(activo, FiltroCuenta.TODOS, FiltroClientes.BAJA, emptySet()))
    }

    @Test
    fun filtro_archivados() {
        val archivado = cliente(14, EstadoCliente.ARCHIVADO)
        val activo = cliente(15, EstadoCliente.ACTIVO)
        assertTrue(cumpleFiltroClientes(archivado, FiltroCuenta.TODOS, FiltroClientes.ARCHIVADO, emptySet()))
        assertFalse(cumpleFiltroClientes(activo, FiltroCuenta.TODOS, FiltroClientes.ARCHIVADO, emptySet()))
    }

    @Test
    fun no_vinculados_mas_activos() {
        val sinCuentaActivo = cliente(16, EstadoCliente.ACTIVO)
        val sinCuentaBaja = cliente(17, EstadoCliente.BAJA)
        val conCuentaActivo = cliente(18, EstadoCliente.ACTIVO, firebaseUid = "u")
        assertTrue(cumpleFiltroClientes(sinCuentaActivo, FiltroCuenta.NO_VINCULADOS, FiltroClientes.ACTIVO, emptySet()))
        assertFalse(cumpleFiltroClientes(sinCuentaBaja, FiltroCuenta.NO_VINCULADOS, FiltroClientes.ACTIVO, emptySet()))
        assertFalse(cumpleFiltroClientes(conCuentaActivo, FiltroCuenta.NO_VINCULADOS, FiltroClientes.ACTIVO, emptySet()))
    }

    @Test
    fun vinculados_mas_bajas() {
        val vinculadoBaja = cliente(19, EstadoCliente.BAJA, firebaseUid = "u")
        val vinculadoActivo = cliente(20, EstadoCliente.ACTIVO, firebaseUid = "u")
        val sinVinculoBaja = cliente(21, EstadoCliente.BAJA)
        assertTrue(cumpleFiltroClientes(vinculadoBaja, FiltroCuenta.VINCULADOS, FiltroClientes.BAJA, emptySet()))
        assertFalse(cumpleFiltroClientes(vinculadoActivo, FiltroCuenta.VINCULADOS, FiltroClientes.BAJA, emptySet()))
        assertFalse(cumpleFiltroClientes(sinVinculoBaja, FiltroCuenta.VINCULADOS, FiltroClientes.BAJA, emptySet()))
    }

    @Test
    fun limpiar_filtros_todos_mas_todos_devuelve_todo() {
        val activo = cliente(22, EstadoCliente.ACTIVO)
        val baja = cliente(23, EstadoCliente.BAJA)
        assertTrue(cumpleFiltroClientes(activo, FiltroCuenta.TODOS, FiltroClientes.TODOS, emptySet()))
        assertTrue(cumpleFiltroClientes(baja, FiltroCuenta.TODOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun cliente_que_deja_de_cumplir_el_filtro_desaparece() {
        // Antes de vincularse cumple "No vinculados"...
        val antes = cliente(24, EstadoCliente.ACTIVO, firebaseUid = null)
        assertTrue(cumpleFiltroClientes(antes, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
        // ...tras vincularse (misma ficha, firebaseUid ya no es null) deja de cumplirlo.
        val despues = antes.copy(firebaseUid = "nuevo-uid")
        assertFalse(cumpleFiltroClientes(despues, FiltroCuenta.NO_VINCULADOS, FiltroClientes.TODOS, emptySet()))
        assertTrue(cumpleFiltroClientes(despues, FiltroCuenta.VINCULADOS, FiltroClientes.TODOS, emptySet()))
    }

    @Test
    fun el_filtro_no_modifica_los_datos_originales() {
        val original = cliente(25, EstadoCliente.ACTIVO, firebaseUid = "uid")
        val snapshot = original
        cumpleFiltroClientes(original, FiltroCuenta.VINCULADOS, FiltroClientes.ACTIVO, emptySet())
        assertEquals(snapshot, original)
        assertEquals("uid", original.firebaseUid)
    }

}
