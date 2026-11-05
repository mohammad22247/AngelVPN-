package com.angelvpn.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angelvpn.app.AngelApp
import com.angelvpn.app.domain.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as AngelApp).profileStore

    val profiles: StateFlow<List<Profile>> = store.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importText = MutableStateFlow("")
    val importText: StateFlow<String> = _importText.asStateFlow()

    private val _flash = MutableStateFlow<String?>(null)
    val flash: StateFlow<String?> = _flash.asStateFlow()

    fun setImportText(t: String) {
        _importText.value = t
    }

    fun upsert(profile: Profile) {
        viewModelScope.launch { store.upsert(profile) }
    }

    fun delete(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    fun flash(msg: String) {
        _flash.value = msg
    }

    fun clearFlash() {
        _flash.value = null
    }
}
