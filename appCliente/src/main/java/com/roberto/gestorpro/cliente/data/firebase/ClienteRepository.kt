package com.roberto.gestorpro.cliente.data.firebase

import android.content.Context
import androidx.annotation.StringRes
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.model.Cliente
import com.roberto.gestorpro.cliente.model.EstadoCliente
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClienteRepository
 * -----------------
 * ✔ TIPO: clase @Singleton inyectada por Hilt
 * Lee y actualiza la ficha del CLIENTE autenticado en clientes/{idCliente}.
 * La edición solo permite los campos personales (nombre, apellidos, telefono,
 * email, foto, fechaNacimiento); las Rules bloquean el resto.
 */
@Singleton
class ClienteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FirebaseFirestore
) {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(@StringRes recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    companion object {
        private const val COLECCION_CLIENTES = "clientes"
    }

    /**
     * leerFicha
     * ---------
     * Lee clientes/{idCliente} y la convierte en el modelo Cliente.
     */
    suspend fun leerFicha(idCliente: Int): Cliente? {
        val documento = db.collection(COLECCION_CLIENTES)
            .document(idCliente.toString())
            .get()
            .esperar()
        if (!documento.exists()) return null
        val datos = documento.data ?: return null

        return Cliente(
            idCliente = datos["idCliente"]?.let { (it as? Number)?.toInt() } ?: idCliente,
            negocioId = datos["negocioId"] as? String ?: "",
            firebaseUid = datos["firebaseUid"] as? String,
            nombre = datos["nombre"] as? String ?: "",
            apellidos = datos["apellidos"] as? String ?: "",
            dni = datos["dni"] as? String ?: "",
            telefono = datos["telefono"] as? String ?: "",
            email = datos["email"] as? String,
            foto = datos["foto"] as? String ?: "",
            fechaNacimiento = fechaEnMilisegundos(datos["fechaNacimiento"]),
            fechaRegistro = fechaEnMilisegundos(datos["fechaRegistro"]) ?: 0L,
            fechaAlta = fechaEnMilisegundos(datos["fechaAlta"]),
            fechaBaja = fechaEnMilisegundos(datos["fechaBaja"]),
            estado = when (datos["estado"]) {
                "ACTIVO" -> EstadoCliente.ACTIVO
                "MOROSO" -> EstadoCliente.MOROSO
                "BAJA" -> EstadoCliente.BAJA
                "ARCHIVADO" -> EstadoCliente.ARCHIVADO
                "REGISTRADO" -> EstadoCliente.REGISTRADO
                else -> EstadoCliente.REGISTRADO
            },
            serviciosContratados = (datos["serviciosContratados"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?: emptyList(),
            fechaInicioActual = fechaEnMilisegundos(datos["fechaInicioActual"]),
            fechaFinActual = fechaEnMilisegundos(datos["fechaFinActual"])
        )
    }

    private fun fechaEnMilisegundos(valor: Any?): Long? = when (valor) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> null
    }

    /**
     * actualizarDatosPersonales
     * -------------------------
     * Actualiza solo los campos personales de la propia ficha.
     */
    suspend fun actualizarDatosPersonales(
        idCliente: Int,
        nombre: String,
        apellidos: String,
        telefono: String,
        email: String?,
        foto: String,
        fechaNacimiento: Long?
    ): ResultadoAutenticacion {
        return try {
            db.collection(COLECCION_CLIENTES)
                .document(idCliente.toString())
                .update(
                    mapOf(
                        "nombre" to nombre,
                        "apellidos" to apellidos,
                        "telefono" to telefono,
                        "email" to email,
                        "foto" to foto,
                        "fechaNacimiento" to fechaNacimiento
                    )
                )
                .esperar()
            ResultadoAutenticacion(true, "Datos actualizados")
        } catch (e: Exception) {
            ResultadoAutenticacion(false, mensajeDe(e))
        }
    }

    private fun mensajeDe(e: Exception): String {
        return when {
            e.message?.contains("permission", ignoreCase = true) == true ->
                texto(R.string.perfil_error_permisos)
            else -> texto(R.string.auth_error_inesperado)
        }
    }
}
