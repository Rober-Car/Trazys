package com.roberto.gestorpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.firebase.AutenticacionRepository
import com.roberto.gestorpro.data.firebase.Denuncia
import com.roberto.gestorpro.data.firebase.DenunciaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DenunciasViewModel (ADMIN)
 * --------------------------
 * Coordina el sistema de denuncias UGC del ADMIN:
 *  - crear una denuncia (contenido/usuario) desde el perfil de un cliente;
 *  - listar las denuncias de su negocio y marcarlas como REVISADA.
 *
 * Arquitectura: UI -> ViewModel -> DenunciaRepository -> Firestore.
 */
@HiltViewModel
class DenunciasViewModel @Inject constructor(
    private val denunciaRepository: DenunciaRepository,
    private val autenticacionRepository: AutenticacionRepository
) : ViewModel() {

    private val _enviando = MutableStateFlow(false)
    val enviando = _enviando.asStateFlow()

    private val _denuncias = MutableStateFlow<List<Denuncia>>(emptyList())
    val denuncias = _denuncias.asStateFlow()

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /**
     * enviarDenuncia
     * --------------
     * Crea una denuncia como ADMIN del negocio autenticado (negocioId = uid).
     * Devuelve null si se envió o el mensaje de error real.
     */
    suspend fun enviarDenuncia(
        tipo: String,
        referencia: String,
        usuarioDenunciadoUid: String?,
        motivo: String,
        descripcion: String?
    ): String? {
        if (_enviando.value) return "Ya hay una denuncia en curso"
        val negocioId = autenticacionRepository.uidActual() ?: return "No hay ninguna sesión activa"
        _enviando.value = true
        return try {
            val resultado = denunciaRepository.crearDenuncia(
                negocioId = negocioId,
                tipo = tipo,
                referencia = referencia,
                usuarioDenunciadoUid = usuarioDenunciadoUid,
                motivo = motivo,
                descripcion = descripcion
            )
            if (resultado.exito) null else resultado.mensaje
        } finally {
            _enviando.value = false
        }
    }

    /**
     * cargarDenuncias
     * ---------------
     * Carga las denuncias del negocio del ADMIN autenticado.
     */
    fun cargarDenuncias() {
        viewModelScope.launch {
            val negocioId = autenticacionRepository.uidActual()
                ?: run {
                    _error.value = "No hay ninguna sesión activa"
                    return@launch
                }
            _cargando.value = true
            _error.value = null
            try {
                _denuncias.value = denunciaRepository.obtenerDenuncias(negocioId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudieron cargar las denuncias"
            } finally {
                _cargando.value = false
            }
        }
    }

    /**
     * marcarRevisada
     * --------------
     * Pone una denuncia en REVISADA y refresca la lista.
     */
    fun marcarRevisada(denunciaId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                denunciaRepository.marcarRevisada(denunciaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo actualizar la denuncia"
            }
            cargarDenuncias()
        }
    }
}
