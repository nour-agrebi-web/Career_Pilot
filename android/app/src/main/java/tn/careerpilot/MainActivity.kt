package tn.careerpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.ui.AppViewModelFactory
import tn.careerpilot.ui.CareerPilotRoot
import tn.careerpilot.ui.auth.AuthViewModel
import tn.careerpilot.ui.theme.CareerPilotTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels { AppViewModelFactory }

    // État lu par Compose, alimenté uniquement par les LiveData du ViewModel.
    private var restoring by mutableStateOf(true)
    private var user by mutableStateOf<CurrentUser?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // US-1.2.2 : l'utilisateur reste connecté ; l'affichage dépend de authenticatedUser.
        authViewModel.isRestoringSession.observe(this) { restoring = it }
        authViewModel.authenticatedUser.observe(this) { user = it }

        setContent {
            CareerPilotTheme {
                CareerPilotRoot(authViewModel, restoring, user)
            }
        }
    }
}
