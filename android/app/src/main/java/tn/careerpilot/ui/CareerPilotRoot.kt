package tn.careerpilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tn.careerpilot.ui.auth.AuthViewModel
import tn.careerpilot.ui.auth.FormState
import tn.careerpilot.ui.auth.LoginScreen
import tn.careerpilot.ui.auth.RegisterScreen
import tn.careerpilot.ui.auth.SessionState
import tn.careerpilot.ui.home.HomeScreen

/** Affiche les écrans d'authentification ou l'application selon l'état de la session. */
@Composable
fun CareerPilotRoot(viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    when (val state = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SessionState.LoggedOut -> AuthNavigation(viewModel, form)
        is SessionState.LoggedIn -> HomeScreen(state.user, onLogout = viewModel::logout)
    }
}

@Composable
private fun AuthNavigation(viewModel: AuthViewModel, form: FormState) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                form = form,
                onLogin = viewModel::login,
                onGoToRegister = {
                    viewModel.clearError()
                    navController.navigate("register")
                },
            )
        }
        composable("register") {
            RegisterScreen(
                form = form,
                onRegister = viewModel::register,
                onGoToLogin = {
                    viewModel.clearError()
                    navController.popBackStack()
                },
            )
        }
    }
}
