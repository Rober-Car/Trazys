package com.roberto.gestorpro.cliente.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.ClienteRepository
import com.roberto.gestorpro.cliente.data.firebase.NegocioRepository
import com.roberto.gestorpro.cliente.data.firebase.SesionRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.model.EstadoCliente
import com.roberto.gestorpro.cliente.model.HorarioNegocio
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * HorarioClienteViewModel
 * -----------------------
 * Carga el horario del centro y el horario de actividades desde
 * negocios_publicos/{negocioId}. Solo accesible para CLIENTES vinculados y en
 * estado ACTIVO (la pantalla lo comprueba aquí). El horario de actividades no
 * se deriva de las sesiones: es configuración independiente.
 */
@HiltViewModel
class HorarioClienteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val clienteRepository: ClienteRepository,
    private val negocioRepository: NegocioRepository,
    private val sesionRepository: SesionRepository
) : ViewModel() {

    /** Estado de la pantalla de horario. */
    sealed interface Estado {
        object Cargando : Estado
        object NoVinculado : Estado
        object NoActivo : Estado
        data class Error(val mensaje: String) : Estado
        data class Datos(
            val horario: HorarioNegocio,
            val nombresActividad: Map<Int, String>
        ) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Cargando)
    val estado = _estado.asStateFlow()

    fun cargar() {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            try {
                val idCliente = preferencesRepository.idCliente.first()
                if (idCliente == null) {
                    _estado.value = Estado.NoVinculado
                    return@launch
                }
                val ficha = clienteRepository.leerFicha(idCliente)
                if (ficha == null) {
                    _estado.value = Estado.Error(texto(R.string.horario_error_cargar))
                    return@launch
                }
                if (ficha.estado != EstadoCliente.ACTIVO) {
                    _estado.value = Estado.NoActivo
                    return@launch
                }
                val negocioId = preferencesRepository.negocioId.first()
                    ?: ficha.negocioId.takeIf { it.isNotBlank() }
                    ?: run {
                        _estado.value = Estado.Error(texto(R.string.horario_error_centro))
                        return@launch
                    }

                val horario = negocioRepository.obtenerHorarioNegocio(negocioId)

                // Resuelve el nombre ACTUAL de cada actividad referenciada en el
                // horario (solo servicios activos legibles por el CLIENTE).
                val ids = horario.actividades.values.flatten()
                    .map { it.idServicio }
                    .distinct()
                val nombres = mutableMapOf<Int, String>()
                ids.forEach { id ->
                    val servicio = sesionRepository.obtenerServicioActivo(id, negocioId)
                    if (servicio != null) nombres[id] = servicio.nombre
                }

                _estado.value = Estado.Datos(horario, nombres)
            } catch (e: Exception) {
                _estado.value = Estado.Error(texto(R.string.horario_error_cargar))
            }
        }
    }

    fun reintentar() = cargar()

    private fun texto(recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)
}
