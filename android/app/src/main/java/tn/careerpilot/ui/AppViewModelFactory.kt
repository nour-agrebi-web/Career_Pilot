package tn.careerpilot.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tn.careerpilot.AppContainer
import tn.careerpilot.CareerPilotApp
import tn.careerpilot.ui.auth.AuthViewModel
import tn.careerpilot.ui.cv.CvViewModel
import tn.careerpilot.ui.matches.MatchesViewModel
import tn.careerpilot.ui.preferences.PreferencesViewModel
import tn.careerpilot.ui.profile.ProfileViewModel
import tn.careerpilot.ui.stats.StatsViewModel

private val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as CareerPilotApp).container

/**
 * ViewModelFactory unique : injecte dans chaque ViewModel les repositories dont il a besoin.
 * (MatchDetailViewModel a sa propre factory car il reçoit aussi l'identifiant de l'offre.)
 */
val AppViewModelFactory = viewModelFactory {
    initializer { AuthViewModel(container.authRepository, container.sessionExpired) }
    initializer { MatchesViewModel(container.matchRepository, container.preferencesRepository) }
    initializer { StatsViewModel(container.matchRepository) }
    initializer { ProfileViewModel(container.profileRepository) }
    initializer { CvViewModel(container.cvRepository) }
    initializer { PreferencesViewModel(container.preferencesRepository) }
}

fun matchDetailFactory(matchId: String) = viewModelFactory {
    initializer { tn.careerpilot.ui.matches.MatchDetailViewModel(container.matchRepository, matchId) }
}
