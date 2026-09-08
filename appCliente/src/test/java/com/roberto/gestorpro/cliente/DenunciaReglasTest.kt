package com.roberto.gestorpro.cliente

import com.roberto.gestorpro.cliente.data.firebase.DenunciaRepository
import com.roberto.gestorpro.cliente.data.firebase.MotivosDenuncia
import com.roberto.gestorpro.cliente.data.firebase.TiposContenidoDenunciable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DenunciaReglasTest (CLIENTE)
 * ----------------------------
 * Tests de la lógica pura del sistema de denuncias UGC del CLIENTE (sin Firebase).
 */
class DenunciaReglasTest {

    @Test
    fun `tipos denunciables validos`() {
        assertTrue(TiposContenidoDenunciable.NOTIFICACION in TiposContenidoDenunciable.validos())
        assertTrue(TiposContenidoDenunciable.LOGO_NEGOCIO in TiposContenidoDenunciable.validos())
        assertFalse("MOVIMIENTO" in TiposContenidoDenunciable.validos())
    }

    @Test
    fun `motivos validos y etiquetas en espanol`() {
        val motivos = MotivosDenuncia.validos()
        assertTrue(motivos.size >= 5)
        assertEquals("Contenido ofensivo", MotivosDenuncia.etiqueta(MotivosDenuncia.OFENSIVO))
        assertEquals("Otro", MotivosDenuncia.etiqueta("DESCONOCIDO"))
    }

    @Test
    fun `datos validos - sin error`() {
        assertNull(DenunciaRepository.validarDatosDenuncia(
            TiposContenidoDenunciable.LOGO_NEGOCIO,
            MotivosDenuncia.INAPROPIADO
        ))
    }

    @Test
    fun `tipo invalido - error`() {
        assertEquals(
            "Este contenido no se puede denunciar",
            DenunciaRepository.validarDatosDenuncia("BAJA_CONFIRMADA", MotivosDenuncia.OTRO)
        )
    }

    @Test
    fun `motivo invalido - error`() {
        assertEquals(
            "Selecciona un motivo válido",
            DenunciaRepository.validarDatosDenuncia(TiposContenidoDenunciable.NOTIFICACION, "SPAM")
        )
    }
}
