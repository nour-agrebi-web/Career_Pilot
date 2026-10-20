package tn.careerpilot.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tn.careerpilot.data.CurrentUser

// Écran d'accueil provisoire : il sera remplacé par l'import du CV (Epic 2) puis le fil d'offres.
@Composable
fun HomeScreen(user: CurrentUser, onLogout: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bienvenue", style = MaterialTheme.typography.headlineLarge)
            Text("Connecté en tant que ${user.email}", style = MaterialTheme.typography.bodyLarge)
            Text("Prochaine étape : importer votre CV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Se déconnecter") }
        }
    }
}
