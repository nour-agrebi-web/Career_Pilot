package tn.careerpilot.ui.preferences

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tn.careerpilot.data.repository.PreferencesRepository
import tn.careerpilot.domain.Location
import tn.careerpilot.domain.Preferences
import tn.careerpilot.domain.Validators
import tn.careerpilot.ui.common.BaseViewModel

/**
 * Édition des préférences : la vue modifie un brouillon, validé puis envoyé à l'API au clic sur
 * « Enregistrer ». Le brouillon suit le cache tant que l'utilisateur ne l'a pas modifié.
 */
class PreferencesViewModel(private val repository: PreferencesRepository) : BaseViewModel() {

    private val _draft = MutableLiveData(Preferences())
    val draft: LiveData<Preferences> = _draft

    private val _dirty = MutableLiveData(false)
    val dirty: LiveData<Boolean> = _dirty

    init {
        viewModelScope.launch {
            repository.preferences.collectLatest { saved -> if (_dirty.value != true) _draft.value = saved }
        }
        launchRequest { repository.refresh() }
    }

    private fun edit(change: (Preferences) -> Preferences) {
        _draft.value = change(_draft.value ?: Preferences())
        _dirty.value = true
    }

    fun toggleJobType(code: String) = edit { prefs ->
        prefs.copy(jobTypes = if (code in prefs.jobTypes) prefs.jobTypes - code else prefs.jobTypes + code)
    }

    fun addLocation(city: String, country: String) {
        val current = _draft.value ?: Preferences()
        Validators.location(city, current.locations)?.let { return showError(it) }
        edit { it.copy(locations = it.locations + Location(city.trim(), country.trim().ifEmpty { "Tunisie" })) }
    }

    fun removeLocation(location: Location) = edit { it.copy(locations = it.locations - location) }

    fun setRemoteWork(enabled: Boolean) = edit { it.copy(remoteWork = enabled) }

    fun setThreshold(value: Int) = edit {
        it.copy(threshold = value.coerceIn(Validators.MIN_THRESHOLD, Validators.MAX_THRESHOLD))
    }

    fun setNotifications(enabled: Boolean) = edit { it.copy(notificationsEnabled = enabled) }

    fun save() {
        val prefs = _draft.value ?: return
        Validators.preferences(prefs).firstOrNull()?.let { return showError(it) }
        _dirty.value = false
        launchRequest("Préférences enregistrées") { repository.save(prefs) }
    }

    fun reset() {
        _dirty.value = false
        launchRequest("Préférences réinitialisées") { repository.reset() }
    }
}
