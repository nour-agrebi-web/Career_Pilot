package tn.careerpilot.ui.matches

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tn.careerpilot.data.repository.MatchRepository
import tn.careerpilot.data.repository.PreferencesRepository
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchFilter
import tn.careerpilot.domain.MatchSort
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.ui.common.BaseViewModel

/** Fil d'offres recommandées et offres sauvegardées, avec filtres dynamiques appliqués au cache Room. */
class MatchesViewModel(
    private val matchRepository: MatchRepository,
    preferencesRepository: PreferencesRepository,
) : BaseViewModel() {

    private val _filter = MutableStateFlow(MatchFilter())
    val filter: LiveData<MatchFilter> = _filter.asLiveData()

    val threshold: LiveData<Int> = preferencesRepository.preferences.map { it.threshold }.asLiveData()

    private val visible = combine(matchRepository.matches, preferencesRepository.preferences, _filter) { matches, prefs, filter ->
        filter.apply(matches, prefs.threshold)
    }

    /** Offres du fil (au-dessus du seuil, filtrées et triées). */
    val matches: LiveData<List<Match>> = visible.asLiveData()

    /** Offres sauvegardées (indépendantes des filtres de type et de recherche). */
    val saved: LiveData<List<Match>> = combine(matchRepository.matches, preferencesRepository.preferences) { matches, prefs ->
        MatchFilter(savedOnly = true).apply(matches, prefs.threshold)
    }.asLiveData()

    init {
        refresh()
    }

    /** Lance un cycle de matching puis recharge le cache. */
    fun refresh() = launchRequestWith<Int>({ count -> if (count > 0) "$count nouvelle(s) offre(s) pour vous" else null }) {
        matchRepository.refresh(runMatching = true)
    }

    fun setType(type: String?) = _filter.update { it.copy(type = type) }

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }

    fun setSort(sort: MatchSort) = _filter.update { it.copy(sort = sort) }

    fun toggleSaved(match: Match) = launchRequest(if (match.isSaved) "Offre retirée des sauvegardes" else "Offre sauvegardée") {
        matchRepository.setStatus(match.id, if (match.isSaved) MatchStatus.VIEWED else MatchStatus.ACCEPTED)
    }

    fun hide(match: Match) = launchRequest("Offre masquée : elle ne vous sera plus proposée") {
        matchRepository.hide(match.id)
    }
}
