package tn.careerpilot.ui.matches

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tn.careerpilot.data.repository.MatchRepository
import tn.careerpilot.domain.Match
import tn.careerpilot.domain.MatchStatus
import tn.careerpilot.ui.common.BaseViewModel

class MatchDetailViewModel(
    private val repository: MatchRepository,
    private val matchId: String,
) : BaseViewModel() {

    val match: LiveData<Match?> = repository.match(matchId).asLiveData()

    init {
        // Une offre nouvelle ouverte passe à l'état « consultée ».
        viewModelScope.launch {
            val current = repository.match(matchId).first()
            if (current?.status == MatchStatus.PENDING) repository.setStatus(matchId, MatchStatus.VIEWED)
        }
    }

    fun toggleSaved() {
        val current = match.value ?: return
        launchRequest(if (current.isSaved) "Offre retirée des sauvegardes" else "Offre sauvegardée") {
            repository.setStatus(matchId, if (current.isSaved) MatchStatus.VIEWED else MatchStatus.ACCEPTED)
        }
    }

    fun hide() = launchRequest("Offre masquée : elle ne vous sera plus proposée") { repository.hide(matchId) }
}
