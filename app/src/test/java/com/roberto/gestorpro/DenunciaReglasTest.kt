package com.roberto.gestorpro

import com.roberto.gestorpro.data.firebase.DenunciaRepository
import com.roberto.gestorpro.data.firebase.MotivosDenuncia
import com.roberto.gestorpro.data.firebase.TiposContenidoDenunciable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DenunciaReglasTest (ADMIN)
 * --------------------------
 * Tests de la lógica pura del sistema de denuncias UGC (sin Firebase):
 * tipos válidos, motivos válidos, etiquetas y la regla de que las
 * notificaciones automáticas no son contenido denunciable (L).
 */
class DenunciaReglasTest {

    @Test
    fun `tipos denunciables validos`() {
        assertTrue(TiposContenidoDenunciable.FOTO_CLIENTE in TiposContenidoDenunciable.validos())
        assertTrue(TiposContenidoDenunciable.NOTIFICACION in TiposContenidoDenunciable.validos())
        assertTrue(TiposContenidoDenunciable.LOGO_NEGOCIO in TiposContenidoDenunciable.validos())
        assertFalse("COMENTARIO" in TiposContenidoDenunciable.validos())
    }

    @Test
    fun `motivos validos y etiquetas en espanol`() {
        val motivos = MotivosDenuncia.validos()
        assertTrue(motivos.size >= 5)
        assertEquals("Contenido inapropiado", MotivosDenuncia.etiqueta(MotivosDenuncia.INAPROPIADO))
        assertEquals("Acoso o amenazas", MotivosDenuncia.etiqueta(MotivosDenuncia.ACOSO))
        assertEquals("Suplantación", MotivosDenuncia.etiqueta(MotivosDenuncia.SUPLANTACION))
    }

    @Test
    fun `datos validos - sin error`() {
        assertNull(DenunciaRepository.validarDatosDenuncia(
            TiposContenidoDenunciable.NOTIFICACION,
            MotivosDenuncia.OFENSIVO
        ))
    }

    @Test
    fun `tipo invalido - error`() {
        assertEquals(
            "Este contenido no se puede denunciar",
            DenunciaRepository.validarDatosDenuncia("AUTOMATICA", MotivosDenuncia.OTRO)
        )
    }

    @Test
    fun `motivo invalido - error`() {
        assertEquals(
            "Selecciona un motivo válido",
            DenunciaRepository.validarDatosDenuncia(TiposContenidoDenunciable.FOTO_CLIENTE, "SPAM")
        )
    }

    @Test
    fun `las notificaciones automaticas no son contenido denunciable`() {
        assertEquals(
            "Este contenido no se puede denunciar",
            DenunciaRepository.validarDatosDenuncia("BAJA_CONFIRMADA", MotivosDenuncia.INAPROPIADO)
        )
        assertEquals(
            "Este contenido no se puede denunciar",
            DenunciaRepository.validarDatosDenuncia("VINCULACION", MotivosDenuncia.INAPROPIADO)
        )
    }

    @Test
    fun `etiqueta de tipo denunciable`() {
        assertEquals(
            "Foto de cliente",
            DenunciaRepository.etiquetaTipo(TiposContenidoDenunciable.FOTO_CLIENTE)
        )
    }
}
