package tn.careerpilot.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Preferences
import tn.careerpilot.domain.Validators
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.SectionTitle
import tn.careerpilot.ui.common.UiMessageEffect

/** Préférences de recherche et compte (types d'offres, zones, télétravail, seuil, notifications). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(viewModel: PreferencesViewModel, email: String, onLogout: () -> Unit) {
    val prefs by viewModel.draft.observeAsState(Preferences())
    val dirty by viewModel.dirty.observeAsState(false)
    val state by viewModel.uiState.observeAsState()
    var city by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("Tunisie") }
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Préférences", "Ce que l'agent doit chercher pour vous")
        if (state?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Types d'offres (au moins un)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JobTypes.all.forEach { (code, label) ->
                    FilterChip(selected = code in prefs.jobTypes, onClick = { viewModel.toggleJobType(code) }, label = { Text(label) })
                }
            }

            SectionTitle("Localisations")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prefs.locations.forEach { location ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeLocation(location) },
                        label = { Text("${location.city}, ${location.country}") },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Retirer") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city, { city = it }, label = { Text("Ville") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(country, { country = it }, label = { Text("Pays") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = {
                viewModel.addLocation(city, country)
                if (Validators.location(city, prefs.locations) == null) city = ""
            }) { Text("Ajouter la ville") }

            SettingSwitch("Accepter le télétravail", "Les offres en télétravail ignorent la zone géographique.", prefs.remoteWork, viewModel::setRemoteWork)

            SectionTitle("Seuil de pertinence")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Score minimal", modifier = Modifier.weight(1f))
                Text("${prefs.threshold} / 100", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = prefs.threshold.toFloat(),
                onValueChange = { viewModel.setThreshold(it.toInt()) },
                valueRange = Validators.MIN_THRESHOLD.toFloat()..Validators.MAX_THRESHOLD.toFloat(),
                steps = Validators.MAX_THRESHOLD - Validators.MIN_THRESHOLD - 1,
            )
            Text(
                when {
                    prefs.threshold >= 80 -> "Peu d'offres, très proches de votre profil."
                    prefs.threshold >= 65 -> "Un bon équilibre entre volume et pertinence."
                    else -> "Beaucoup d'offres, y compris plus éloignées de votre profil."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingSwitch("Notifications", "5 par jour au maximum, jamais entre 22 h et 8 h.", prefs.notificationsEnabled, viewModel::setNotifications)

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f)) { Text("Réinitialiser") }
                Button(onClick = viewModel::save, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
            }

            HorizontalDivider()
            SectionTitle("Compte")
            Text("Connecté en tant que $email", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Se déconnecter") }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
