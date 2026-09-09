package com.roberto.gestorpro.cliente.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.ReservaRepository
import com.roberto.gestorpro.cliente.data.firebase.SesionRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AsistentesSesionViewModel
 * -------------------------
 * Carga los asistentes CONFIRMADOS de una sesión (solo nombres) que el CLIENTE
 * ya tiene reservada. La pantalla solo es accesible desde una sesión en estado
 * RESERVADA (ver ClasesScreen); por defensa, este ViewModel también comprueba
 * que la reserva propia exista antes de exponer los asistentes.
 */
@HiltViewModel
class AsistentesSesionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val reservaRepository: ReservaRepository,
    private val sesionRepository: SesionRepository
) : ViewModel() {

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _asistentes = MutableStateFlow<List<String>>(emptyList())
    val asistentes = _asistentes.asStateFlow()

    private val _nombreServicio = MutableStateFlow<String?>(null)
    val nombreServicio = _nombreServicio.asStateFlow()

    /** Carga los asistentes de la sesión indicada. */
    fun cargar(idSesion: Int) {
        viewModelScope.launch {
            _cargando.value = true
            _error.value = null
            _asistentes.value = emptyList()
            try {
                val clienteId = preferencesRepository.idCliente.first() ?: run {
                    _error.value = texto(R.string.clases_no_vinculado)
                    return@launch
                }
                val negocioId = preferencesRepository.negocioId.first()
                    ?.takeIf { it.isNotBlank() }
                    ?: run {
                        _error.value = texto(R.string.clases_error_identificar_centro)
                        return@launch
                    }

                // Solo se muestran asistentes si el CLIENTE tiene la sesión reservada.
                val reservada = reservaRepository
                    .obtenerReservasCliente(clienteId, negocioId)
                    .any { it.sesionId == idSesion }
                if (!reservada) {
                    _error.value = texto(R.string.asistentes_no_reservada)
                    return@launch
                }

                val sesion = sesionRepository.obtenerSesionPorId(idSesion, negocioId)
                if (sesion == null) {
                    _error.value = texto(R.string.clases_error_cargar)
                    return@launch
                }
                // Solo nombres, ordenados alfabéticamente (sin apellidos, fotos,
                // teléfonos ni emails). Lo mantiene la Cloud Function de reservas.
                _asistentes.value = sesion.asistentes.values.sorted()
                _nombreServicio.value = null
            } finally {
                _cargando.value = false
            }
        }
    }

    private fun texto(recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)
}
