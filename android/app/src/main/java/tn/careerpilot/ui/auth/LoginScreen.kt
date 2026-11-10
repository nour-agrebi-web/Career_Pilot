package tn.careerpilot.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun LoginScreen(
    form: FormState,
    onLogin: (email: String, password: String) -> Unit,
    onGoToRegister: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val lockSecondsLeft = rememberLockSecondsLeft(form)

    AuthScaffold(
        title = "Bienvenue",
        subtitle = "Connectez-vous pour retrouver vos offres, votre profil et vos CV.",
    ) {
        EmailField(email) { email = it }
        PasswordField(password, { password = it })
        ErrorText(authMessage(form, lockSecondsLeft)) // US-1.2.1 : message unique, sans dire quel champ est faux
        SubmitButton(
            "Se connecter",
            loading = form.loading,
            enabled = email.isNotBlank() && password.isNotEmpty() && lockSecondsLeft == 0,
        ) { onLogin(email, password) }
        AuthSwitchLink("Pas encore de compte ?", "Créer un compte", onGoToRegister)
    }
}
