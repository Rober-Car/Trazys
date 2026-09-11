package com.roberto.gestorpro.cliente.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.cliente.R
import com.roberto.gestorpro.cliente.data.firebase.DispositivoRepository
import com.roberto.gestorpro.cliente.data.firebase.NotificacionRepository
import com.roberto.gestorpro.cliente.data.repository.PreferencesRepository
import com.roberto.gestorpro.cliente.model.Notificacion
import com.roberto.gestorpro.cliente.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * NotificacionesClienteViewModel
 * ------------------------------
 * Gestiona el buzón de notificaciones del CLIENTE (leídas de Firestore,
 * ordenadas de más reciente a más antigua, marcado como leídas) y la
 * preferencia local de "recibir avisos".
 */
@HiltViewModel
class NotificacionesClienteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val notificacionRepository: NotificacionRepository,
    private val dispositivoRepository: DispositivoRepository
) : ViewModel() {

    /**
     * texto
     * -----
     * Resuelve un recurso string en el idioma elegido por el usuario.
     */
    private fun texto(recurso: Int): String =
        IdiomaAplicacion.textoDe(context, recurso)

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _noVinculado = MutableStateFlow(false)
    val noVinculado = _noVinculado.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _notificaciones = MutableStateFlow<List<Notificacion>>(emptyList())
    val notificaciones = _notificaciones.asStateFlow()

    /** Preferencia local persistente (DataStore). */
    val notificacionesActivadas = preferencesRepository.notificacionesActivadas

    fun setNotificacionesActivadas(activas: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificacionesActivadas(activas)
            // Refleja el switch en el documento del dispositivo para que Cloud
            // Functions omita este token cuando el aviso esté desactivado.
            dispositivoRepository.actualizarNotificacionesActivadas(activas)
        }
    }

    /** Carga las notificaciones propias del cliente vinculado. */
    fun cargar() {
        viewModelScope.launch {
            _cargando.value = true
            _error.value = null
            _noVinculado.value = false
            try {
                val idCliente = preferencesRepository.idCliente.first()
                    ?: run { _noVinculado.value = true; return@launch }
                val negocioId = preferencesRepository.negocioId.first()
                    ?.takeIf { it.isNotBlank() }
                    ?: run { _noVinculado.value = true; return@launch }
                _notificaciones.value =
                    notificacionRepository.obtenerNotificaciones(idCliente, negocioId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = texto(R.string.notif_error_cargar)
            } finally {
                _cargando.value = false
            }
        }
    }

    /** Marca una notificación propia como leída en Firestore. */
    fun marcarLeida(id: String) {
        viewModelScope.launch {
            if (notificacionRepository.marcarComoLeida(id)) {
                _notificaciones.value = _notificaciones.value.map { notificacion ->
                    if (notificacion.id == id) {
                        notificacion.copy(
                            leida = true,
                            fechaLeida = System.currentTimeMillis()
                        )
                    } else {
                        notificacion
                    }
                }
            }
        }
    }
}
