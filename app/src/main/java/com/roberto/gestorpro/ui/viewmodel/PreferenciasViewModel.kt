package com.roberto.gestorpro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roberto.gestorpro.data.repository.PreferencesRepository
import com.roberto.gestorpro.util.IdiomaAplicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferenciasViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val themeMode = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreferencesRepository.THEME_SISTEMA
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    val idioma = preferencesRepository.idioma.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IdiomaAplicacion.actual
    )

    fun setIdioma(idioma: String) {
        viewModelScope.launch {
            preferencesRepository.setIdioma(idioma)
        }
    }
}
