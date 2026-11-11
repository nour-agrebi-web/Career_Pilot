package tn.careerpilot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tn.careerpilot.data.CurrentUser
import tn.careerpilot.ui.AppViewModelFactory
import tn.careerpilot.ui.common.LocalSnackbarHost
import tn.careerpilot.ui.matchDetailFactory
import tn.careerpilot.ui.matches.FeedScreen
import tn.careerpilot.ui.matches.MatchDetailScreen
import tn.careerpilot.ui.matches.MatchDetailViewModel
import tn.careerpilot.ui.matches.MatchesViewModel
import tn.careerpilot.ui.matches.SavedScreen
import tn.careerpilot.ui.preferences.PreferencesScreen
import tn.careerpilot.ui.profile.ProfileScreen
import tn.careerpilot.ui.stats.StatsScreen
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    FEED("feed", "Offres", Icons.Filled.Home),
    SAVED("saved", "Sauvegardées", Icons.Filled.FavoriteBorder),
    PROFILE("profile", "Profil et CV", Icons.Filled.Person),
    SETTINGS("settings", "Préférences", Icons.Filled.Settings),
}

/**
 * Application connectée : barre de navigation en bas à quatre onglets comme dans la maquette
 * (Offres, Sauvegardées, Profil et CV, Préférences) et un écran par onglet.
 */
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
            containerColor = CareerPilotTheme.colors.background,
            snackbarHost = {
                // Toast bleu nuit de la maquette (.toast).
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(10.dp),
                        containerColor = CareerPilotTheme.colors.primaryButton,
                        contentColor = CareerPilotTheme.colors.onPrimaryButton,
                    )
                }
            },
            bottomBar = {
                if (Tab.entries.any { it.route == currentRoute }) {
                    BottomNav(currentRoute, onSelect = { openTab(it.route) })
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
                    composable(Tab.PROFILE.route) {
                        ProfileScreen(
                            viewModel(factory = AppViewModelFactory),
                            cvViewModel = viewModel(factory = AppViewModelFactory),
                            onLogout = onLogout,
                        )
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

/** Barre de navigation du bas (.bottom-nav) : fond blanc, filet en haut, onglet actif en gras. */
@Composable
private fun BottomNav(currentRoute: String?, onSelect: (Tab) -> Unit) {
    val c = CareerPilotTheme.colors
    Column(Modifier.fillMaxWidth().background(c.navBackground).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val color = if (selected) c.navItemActive else c.navItem
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Text(
                        tab.label,
                        color = color,
                        fontFamily = DmSans,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
