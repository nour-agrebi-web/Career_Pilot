package tn.careerpilot.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val languages = listOf("fr" to "Français", "en" to "English", "ar" to "العربية")

private const val CONSENT_TEXT =
    "J'accepte que CareerPilot traite mon CV et mes données pour me proposer des offres, conformément à la " +
        "loi organique tunisienne n° 2004-63 et, pour les utilisateurs de l'Union européenne, au RGPD. Mes " +
        "coordonnées ne sont jamais envoyées aux services d'IA et je peux supprimer mes données à tout moment."

@Composable
fun RegisterScreen(
    form: FormState,
    onRegister: (email: String, password: String, language: String, consent: Boolean) -> Unit,
    onGoToLogin: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("fr") }
    var consent by rememberSaveable { mutableStateOf(false) }

    AuthScaffold(title = "Créer un compte", subtitle = "Déposez ensuite votre CV, l'agent s'occupe du reste.") {
        EmailField(email) { email = it }
        PasswordField(password, { password = it }, label = "Mot de passe (8 caractères minimum)")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { (code, label) ->
                FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) })
            }
        }

        // US-1.1.2 : consentement explicite, l'inscription reste bloquée tant qu'il n'est pas coché.
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it }),
        ) {
            Checkbox(checked = consent, onCheckedChange = null)
            Text(
                CONSENT_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }

        ErrorText(form.error)
        SubmitButton(
            "Créer mon compte",
            loading = form.loading,
            enabled = consent && email.isNotBlank() && password.length >= 8,
        ) { onRegister(email, password, language, consent) }
        TextButton(onClick = onGoToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Déjà un compte ? Se connecter")
        }
    }
}
