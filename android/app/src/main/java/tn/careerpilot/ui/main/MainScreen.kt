package tn.careerpilot.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.ui.AppViewModelFactory
import tn.careerpilot.ui.common.LocalSnackbarHost
import tn.careerpilot.ui.cv.CvScreen
import tn.careerpilot.ui.matchDetailFactory
import tn.careerpilot.ui.matches.FeedScreen
import tn.careerpilot.ui.matches.MatchDetailScreen
import tn.careerpilot.ui.matches.MatchDetailViewModel
import tn.careerpilot.ui.matches.MatchesViewModel
import tn.careerpilot.ui.matches.SavedScreen
import tn.careerpilot.ui.preferences.PreferencesScreen
import tn.careerpilot.ui.profile.ProfileScreen
import tn.careerpilot.ui.stats.StatsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    FEED("feed", "Offres", Icons.Filled.Home),
    SAVED("saved", "Sauvegardées", Icons.Filled.Favorite),
    PROFILE("profile", "Profil", Icons.Filled.Person),
    CV("cv", "CV", Icons.AutoMirrored.Filled.List),
    SETTINGS("settings", "Préférences", Icons.Filled.Settings),
}

/** Application connectée : barre de navigation en bas et un écran par onglet. */
@Composable
fun MainScreen(user: CurrentUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Le fil et les sauvegardes partagent le même ViewModel (même cache, mêmes actions).
    val matchesViewModel: MatchesViewModel = viewModel(factory = AppViewModelFactory)

    fun openTab(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (Tab.entries.any { it.route == currentRoute }) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { openTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(navController, startDestination = Tab.FEED.route) {
                    composable(Tab.FEED.route) {
                        FeedScreen(
                            matchesViewModel,
                            onOpen = { navController.navigate("match/${it.id}") },
                            onOpenStats = { navController.navigate("stats") },
                            onEditThreshold = { openTab(Tab.SETTINGS.route) },
                        )
                    }
                    composable(Tab.SAVED.route) {
                        SavedScreen(matchesViewModel, onOpen = { navController.navigate("match/${it.id}") })
                    }
                    composable(Tab.PROFILE.route) { ProfileScreen(viewModel(factory = AppViewModelFactory)) }
                    composable(Tab.CV.route) {
                        CvScreen(viewModel(factory = AppViewModelFactory), onOpenProfile = { openTab(Tab.PROFILE.route) })
                    }
                    composable(Tab.SETTINGS.route) {
                        PreferencesScreen(viewModel(factory = AppViewModelFactory), email = user.email, onLogout = onLogout)
                    }
                    composable("match/{id}") { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        val detailViewModel: MatchDetailViewModel = viewModel(factory = matchDetailFactory(id))
                        MatchDetailScreen(detailViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("stats") {
                        StatsScreen(viewModel(factory = AppViewModelFactory), onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
