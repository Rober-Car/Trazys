package com.roberto.gestorpro

import com.roberto.gestorpro.model.NotificacionAdmin
import com.roberto.gestorpro.util.FiltroNotificaciones
import com.roberto.gestorpro.util.FiltroTipoNotificacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FiltroNotificacionesTest
 * ------------------------
 * Tests de la lógica PURA de clasificación (individual / grupal / automática) y
 * del filtro por fecha de la lista de notificaciones del ADMIN.
 */
class FiltroNotificacionesTest {

    private fun notif(
        id: String = "n",
        origen: String = "MANUAL",
        modoDestino: String = "INDIVIDUAL",
        tipo: String = "MANUAL",
        fechaCreacion: Long = 0L,
        fechaEnvio: Long? = null,
        fechaProgramada: Long? = null
    ): NotificacionAdmin = NotificacionAdmin(
        id = id,
        titulo = "t",
        mensaje = "m",
        tipo = tipo,
        origen = origen,
        modoDestino = modoDestino,
        idsClientes = emptyList(),
        clienteId = null,
        fechaCreacion = fechaCreacion,
        fechaEnvio = fechaEnvio,
        programada = fechaProgramada != null,
        fechaProgramada = fechaProgramada,
        estado = "ENVIADA"
    )

    // G) Clasificación.

    @Test
    fun manual_individual_es_individual() {
        val n = notif(origen = "MANUAL", modoDestino = "INDIVIDUAL")
        assertTrue(FiltroNotificaciones.esIndividual(n))
        assertFalse(FiltroNotificaciones.esGrupal(n))
        assertFalse(FiltroNotificaciones.esAutomatica(n))
    }

    @Test
    fun manual_grupal_es_grupal() {
        assertTrue(FiltroNotificaciones.esGrupal(notif(modoDestino = "GRUPO")))
        assertTrue(FiltroNotificaciones.esGrupal(notif(modoDestino = "TODOS")))
        assertFalse(FiltroNotificaciones.esIndividual(notif(modoDestino = "GRUPO")))
    }

    @Test
    fun automaticas_incluyen_todos_los_origenes_no_manuales() {
        assertTrue(FiltroNotificaciones.esAutomatica(notif(origen = "AUTOMATICA")))
        assertTrue(FiltroNotificaciones.esAutomatica(notif(origen = "PRECONFIGURADA")))
        assertFalse(FiltroNotificaciones.esAutomatica(notif(origen = "MANUAL")))
    }

    @Test
    fun apertura_reservas_es_automatica() {
        val n = notif(origen = "PRECONFIGURADA", modoDestino = "INDIVIDUAL", tipo = "APERTURA_RESERVAS")
        assertTrue(FiltroNotificaciones.esAutomatica(n))
        assertFalse(FiltroNotificaciones.esIndividual(n))
    }

    @Test
    fun filtro_todos_cumple_siempre() {
        assertTrue(FiltroNotificaciones.cumpleTipo(notif(), FiltroTipoNotificacion.TODOS))
        assertTrue(
            FiltroNotificaciones.cumpleTipo(
                notif(origen = "PRECONFIGURADA"),
                FiltroTipoNotificacion.TODOS
            )
        )
    }

    @Test
    fun cumpleTipo_clasifica_correctamente() {
        val individual = notif(origen = "MANUAL", modoDestino = "INDIVIDUAL")
        val grupal = notif(origen = "MANUAL", modoDestino = "GRUPO")
        val automatica = notif(origen = "PRECONFIGURADA")

        assertTrue(FiltroNotificaciones.cumpleTipo(individual, FiltroTipoNotificacion.INDIVIDUALES))
        assertFalse(FiltroNotificaciones.cumpleTipo(individual, FiltroTipoNotificacion.GRUPALES))
        assertFalse(FiltroNotificaciones.cumpleTipo(individual, FiltroTipoNotificacion.AUTOMATICAS))

        assertTrue(FiltroNotificaciones.cumpleTipo(grupal, FiltroTipoNotificacion.GRUPALES))
        assertTrue(FiltroNotificaciones.cumpleTipo(automatica, FiltroTipoNotificacion.AUTOMATICAS))
    }

    // H) Filtros de fecha.

    @Test
    fun fecha_relevante_prioriza_envio_luego_programada_luego_creacion() {
        assertEquals(
            30L,
            FiltroNotificaciones.fechaRelevante(notif(fechaCreacion = 10L, fechaEnvio = 30L))
        )
        assertEquals(
            20L,
            FiltroNotificaciones.fechaRelevante(notif(fechaCreacion = 10L, fechaProgramada = 20L))
        )
        assertEquals(
            10L,
            FiltroNotificaciones.fechaRelevante(notif(fechaCreacion = 10L))
        )
    }

    @Test
    fun cumpleFecha_respeta_los_limites_inclusive() {
        val n = notif(fechaCreacion = 100L)
        assertTrue(FiltroNotificaciones.cumpleFecha(n, desdeInclusive = 100L, hastaInclusive = 100L))
        assertTrue(FiltroNotificaciones.cumpleFecha(n, desdeInclusive = 50L, hastaInclusive = 150L))
        assertFalse(FiltroNotificaciones.cumpleFecha(n, desdeInclusive = 101L, hastaInclusive = null))
        assertFalse(FiltroNotificaciones.cumpleFecha(n, desdeInclusive = null, hastaInclusive = 99L))
        assertTrue(FiltroNotificaciones.cumpleFecha(n, desdeInclusive = null, hastaInclusive = null))
    }

    @Test
    fun filtrar_combina_tipo_y_fecha() {
        val lista = listOf(
            notif(id = "ind", origen = "MANUAL", modoDestino = "INDIVIDUAL", fechaEnvio = 100L),
            notif(id = "gru", origen = "MANUAL", modoDestino = "GRUPO", fechaEnvio = 200L),
            notif(id = "auto", origen = "PRECONFIGURADA", fechaEnvio = 300L)
        )

        // Solo automáticas.
        assertEquals(
            listOf("auto"),
            FiltroNotificaciones
                .filtrar(lista, FiltroTipoNotificacion.AUTOMATICAS, null, null)
                .map { it.id }
        )

        // Grupales dentro de [150, 250] -> solo la grupal.
        assertEquals(
            listOf("gru"),
            FiltroNotificaciones
                .filtrar(lista, FiltroTipoNotificacion.GRUPALES, 150L, 250L)
                .map { it.id }
        )

        // Automáticas + rango que excluye la automática -> vacío.
        assertTrue(
            FiltroNotificaciones
                .filtrar(lista, FiltroTipoNotificacion.AUTOMATICAS, null, 250L)
                .isEmpty()
        )

        // Todos + rango completo -> las tres, conservando el orden.
        assertEquals(
            listOf("ind", "gru", "auto"),
            FiltroNotificaciones
                .filtrar(lista, FiltroTipoNotificacion.TODOS, 0L, 1000L)
                .map { it.id }
        )
    }
}
