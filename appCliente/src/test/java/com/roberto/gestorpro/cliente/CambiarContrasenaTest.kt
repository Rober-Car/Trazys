package com.roberto.gestorpro.cliente

import com.roberto.gestorpro.cliente.data.firebase.validarCambioContrasena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CambiarContrasenaTest (CLIENTE)
 * -------------------------------
 * Tests de la validación pura del formulario de cambio de contraseña. No
 * requieren Firebase: solo prueban la lógica de campos (vacíos, corta,
 * repetición distinta y datos válidos).
 */
class CambiarContrasenaTest {

    @Test
    fun `contrasena actual vacia - error`() {
        assertEquals(
            "Introduce tu contraseña actual",
            validarCambioContrasena("", "Nueva123", "Nueva123")
        )
        assertEquals(
            "Introduce tu contraseña actual",
            validarCambioContrasena("   ", "Nueva123", "Nueva123")
        )
    }

    @Test
    fun `nueva contrasena demasiado corta - error`() {
        assertEquals(
            "La nueva contraseña debe tener al menos 6 caracteres",
            validarCambioContrasena("Actual123", "abc", "abc")
        )
        assertEquals(
            "La nueva contraseña debe tener al menos 6 caracteres",
            validarCambioContrasena("Actual123", "", "")
        )
    }

    @Test
    fun `repeticion distinta de la nueva - error`() {
        assertEquals(
            "Las contraseñas nuevas no coinciden",
            validarCambioContrasena("Actual123", "Nueva123", "Otra456")
        )
        assertEquals(
            "Las contraseñas nuevas no coinciden",
            validarCambioContrasena("Actual123", "Nueva123", "")
        )
    }

    @Test
    fun `datos validos - sin error`() {
        assertNull(validarCambioContrasena("Actual123", "NuevaSegura1", "NuevaSegura1"))
    }
}
