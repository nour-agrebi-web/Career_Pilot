package tn.careerpilot.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import tn.careerpilot.data.repository.MatchRepository
import tn.careerpilot.domain.MatchStatistics

/** Statistiques calculées en direct à partir du cache des correspondances. */
class StatsViewModel(repository: MatchRepository) : ViewModel() {
    val statistics: LiveData<MatchStatistics> = repository.matches.map { MatchStatistics.from(it) }.asLiveData()
}
