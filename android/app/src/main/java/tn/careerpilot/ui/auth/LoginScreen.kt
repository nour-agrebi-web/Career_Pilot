package tn.careerpilot.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun LoginScreen(
    form: FormState,
    onLogin: (email: String, password: String) -> Unit,
    onGoToRegister: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = "CareerPilot", subtitle = "Connectez-vous pour retrouver vos offres.") {
        EmailField(email) { email = it }
        PasswordField(password, { password = it })
        ErrorText(form.error) // US-1.2.1 : message unique, sans dire quel champ est faux
        SubmitButton("Se connecter", loading = form.loading, enabled = email.isNotBlank() && password.isNotEmpty()) {
            onLogin(email, password)
        }
        TextButton(onClick = onGoToRegister, modifier = Modifier.fillMaxWidth()) {
            Text("Pas encore de compte ? Créer un compte")
        }
    }
}
