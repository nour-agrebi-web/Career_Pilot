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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import tn.careerpilot.domain.JobTypes
import tn.careerpilot.domain.Preferences
import tn.careerpilot.domain.Validators
import tn.careerpilot.ui.common.BodyText
import tn.careerpilot.ui.common.CardDivider
import tn.careerpilot.ui.common.CheckRow
import tn.careerpilot.ui.common.CpCard
import tn.careerpilot.ui.common.CpTextField
import tn.careerpilot.ui.common.FieldLabel
import tn.careerpilot.ui.common.LoadingBar
import tn.careerpilot.ui.common.MutedText
import tn.careerpilot.ui.common.PrimaryButton
import tn.careerpilot.ui.common.ScreenHeader
import tn.careerpilot.ui.common.SecondaryButton
import tn.careerpilot.ui.common.SkillTag
import tn.careerpilot.ui.common.SoftButton
import tn.careerpilot.ui.common.SwitchRow
import tn.careerpilot.ui.common.UiMessageEffect
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Préférences de recherche et compte (maquette « Préférences » : une carte avec le formulaire). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(viewModel: PreferencesViewModel, email: String, onLogout: () -> Unit) {
    val prefs by viewModel.draft.observeAsState(Preferences())
    val dirty by viewModel.dirty.observeAsState(false)
    val state by viewModel.uiState.observeAsState()
    var city by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("Tunisie") }
    val c = CareerPilotTheme.colors
    UiMessageEffect(state, viewModel::messageShown)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Préférences", eyebrow = "Affinez votre matching")
        if (state?.loading == true) LoadingBar(Modifier.padding(top = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 22.dp)) {
            CpCard {
                FieldLabel("Types de contrat (au moins un)", Modifier.padding(top = 0.dp))
                JobTypes.all.forEach { (code, label) ->
                    CheckRow(label, checked = code in prefs.jobTypes, onCheckedChange = { viewModel.toggleJobType(code) })
                }

                FieldLabel("Localisations privilégiées")
                if (prefs.locations.isEmpty()) {
                    MutedText("Aucune ville pour l'instant.")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    prefs.locations.forEach { location ->
                        SkillTag("${location.city}, ${location.country}  ✕", onClick = { viewModel.removeLocation(location) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpTextField(city, { city = it }, label = "Ville", placeholder = "Tunis", modifier = Modifier.weight(1f))
                    CpTextField(country, { country = it }, label = "Pays", modifier = Modifier.weight(1f))
                }
                SoftButton("Ajouter la ville", onClick = {
                    viewModel.addLocation(city, country)
                    if (Validators.location(city, prefs.locations) == null) city = ""
                }, modifier = Modifier.padding(top = 10.dp), icon = Icons.Filled.Add)

                SwitchRow("Accepter le télétravail", "Les offres en télétravail ignorent la zone géographique.", prefs.remoteWork, viewModel::setRemoteWork)

                FieldLabel("Seuil de compatibilité")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slider(
                        value = prefs.threshold.toFloat(),
                        onValueChange = { viewModel.setThreshold(it.toInt()) },
                        valueRange = Validators.MIN_THRESHOLD.toFloat()..Validators.MAX_THRESHOLD.toFloat(),
                        steps = Validators.MAX_THRESHOLD - Validators.MIN_THRESHOLD - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = c.accent,
                            activeTrackColor = c.accent,
                            inactiveTrackColor = c.track,
                            activeTickColor = c.accent,
                            inactiveTickColor = c.track,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text("${prefs.threshold}%", color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                MutedText(
                    when {
                        prefs.threshold >= 80 -> "Peu d'offres, très proches de votre profil."
                        prefs.threshold >= 65 -> "Un bon équilibre entre volume et pertinence."
                        else -> "Beaucoup d'offres, y compris plus éloignées de votre profil."
                    },
                )

                SwitchRow("Notifications", "5 par jour au maximum, jamais entre 22 h et 8 h.", prefs.notificationsEnabled, viewModel::setNotifications)

                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Réinitialiser", viewModel::reset, Modifier.weight(1f))
                    PrimaryButton("Enregistrer les préférences", viewModel::save, Modifier.weight(1.4f), enabled = dirty)
                }
            }

            CpCard(Modifier.padding(top = 15.dp)) {
                Text("Compte", color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                CardDivider()
                BodyText("Connecté en tant que $email")
                SoftButton("Déconnexion", onLogout, Modifier.padding(top = 12.dp), icon = Icons.AutoMirrored.Filled.ExitToApp)
            }
        }
    }
}
