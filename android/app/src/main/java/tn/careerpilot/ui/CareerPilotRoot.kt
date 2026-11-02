package tn.careerpilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.ui.auth.AuthViewModel
import tn.careerpilot.ui.auth.FormState
import tn.careerpilot.ui.auth.LoginScreen
import tn.careerpilot.ui.auth.RegisterScreen
import tn.careerpilot.ui.main.MainScreen

/**
 * Choisit les écrans selon la session, observée par la MainActivity :
 * attente pendant la restauration, connexion/inscription, ou application connectée.
 */
@Composable
fun CareerPilotRoot(viewModel: AuthViewModel, restoring: Boolean, user: CurrentUser?) {
    when {
        restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        user == null -> AuthNavigation(viewModel)
        else -> MainScreen(user, onLogout = viewModel::logout)
    }
}

@Composable
private fun AuthNavigation(viewModel: AuthViewModel) {
    val form by viewModel.form.observeAsState(FormState())
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
