package tn.careerpilot.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tn.careerpilot.ui.theme.Aqua
import tn.careerpilot.ui.theme.DmSans
import tn.careerpilot.ui.theme.Ink
import tn.careerpilot.ui.theme.Line
import tn.careerpilot.ui.theme.Muted

/** Couleurs de la carte d'authentification, claires ou sombres comme dans la maquette (.dark-mode .auth-*). */
@Immutable
data class AuthColors(
    val card: Color,
    val cardBorder: Color,
    val title: Color,
    val text: Color,
    val muted: Color,
    val inputBackground: Color,
    val inputText: Color,
    val inputBorder: Color,
    val accent: Color,
    val buttonBackground: Color,
    val buttonText: Color,
)

private val LightAuthColors = AuthColors(
    card = Color.White, cardBorder = Line, title = Ink, text = Ink, muted = Muted,
    inputBackground = Color(0xFFFBFCFD), inputText = Ink, inputBorder = Line, accent = Aqua,
    buttonBackground = Ink, buttonText = Color.White,
)

private val DarkAuthColors = AuthColors(
    card = Color(0xFF172538), cardBorder = Color(0xFF34445A), title = Color(0xFFF3F6FB), text = Color(0xFFF3F6FB),
    muted = Color(0xFFAAB7CA), inputBackground = Color(0xFF111E30), inputText = Color(0xFFEDF3FB),
    inputBorder = Color(0xFF3B4C62), accent = Color(0xFF6FDBD3),
    buttonBackground = Color(0xFFEDF3FB), buttonText = Ink,
)

val LocalAuthColors = staticCompositionLocalOf { LightAuthColors }

private val ErrorRed = Color(0xFFB42318)
private val FieldShape = RoundedCornerShape(9.dp)
private val CardShape = RoundedCornerShape(15.dp)

/**
 * Mise en page commune aux écrans de connexion et d'inscription (#auth-screen de la maquette) :
 * fond en dégradé bleu nuit vers turquoise, nom de la marque, puis une carte blanche avec le formulaire.
 */
@Composable
fun AuthScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkAuthColors else LightAuthColors
    CompositionLocalProvider(LocalAuthColors provides colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to Color(0xFF10213D),
                        0.52f to Color(0xFF163B59),
                        1f to Color(0xFF087F7D),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
                .drawBehind {
                    // Halo turquoise en haut à gauche.
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0x5929B4AB), Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.15f),
                            radius = size.maxDimension * 0.42f,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 22.dp)
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(
                    "CareerPilot",
                    color = Color.White,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-1).sp,
                    style = TextStyle(shadow = Shadow(Color(0x3806162A), Offset(0f, 4f), blurRadius = 36f)),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(24.dp, CardShape, ambientColor = Color(0x3306162A), spotColor = Color(0x3306162A))
                        .background(colors.card, CardShape)
                        .border(1.dp, colors.cardBorder, CardShape)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Text(
                        title,
                        color = colors.title,
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                    )
                    Text(subtitle, color = colors.muted, fontFamily = DmSans, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(7.dp)) // 22dp sous le sous-titre, dont 15dp portés par le premier champ
                    content()
                }
            }
        }
    }
}

/** Champ avec son libellé au-dessus (.auth-field). */
@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = LocalAuthColors.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 15.dp)) {
        Text(
            label,
            color = colors.text,
            fontFamily = DmSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.inputText, fontFamily = DmSans, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .background(colors.inputBackground, FieldShape)
                        .border(1.dp, if (focused) colors.accent else colors.inputBorder, FieldShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.muted, fontFamily = DmSans, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun EmailField(value: String, onValueChange: (String) -> Unit) {
    AuthField(
        label = "Adresse e-mail",
        value = value,
        onValueChange = onValueChange,
        placeholder = "vous@exemple.com",
        keyboardType = KeyboardType.Email,
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Mot de passe",
    placeholder: String = "Votre mot de passe",
) {
    AuthField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
    )
}

/** Message d'erreur en rouge sous les champs (.auth-error), avec une hauteur réservée pour éviter les sauts. */
@Composable
fun ErrorText(error: String?) {
    Text(
        error.orEmpty(),
        color = ErrorRed,
        fontFamily = DmSans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).defaultMinSize(minHeight = 16.dp),
    )
}

/** Bouton plein (.auth-submit). */
@Composable
fun SubmitButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAuthColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.buttonBackground,
            contentColor = colors.buttonText,
            disabledContainerColor = colors.buttonBackground.copy(alpha = 0.45f),
            disabledContentColor = colors.buttonText.copy(alpha = 0.8f),
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp).defaultMinSize(minHeight = 44.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.buttonText)
        } else {
            Text(text, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Lien secondaire sous le bouton (passer de la connexion à l'inscription et inversement). */
@Composable
fun AuthSwitchLink(prompt: String, action: String, onClick: () -> Unit) {
    val colors = LocalAuthColors.current
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(prompt + " ", color = colors.muted, fontFamily = DmSans, fontSize = 12.sp)
        Text(action, color = colors.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Secondes de blocage restantes, mises à jour chaque seconde tant que le blocage dure. */
@Composable
fun rememberLockSecondsLeft(form: FormState): Int {
    val secondsLeft by produceState(form.lockSecondsLeft(), form.lockedUntil) {
        while (value > 0) {
            delay(250)
            value = form.lockSecondsLeft()
        }
    }
    return secondsLeft
}

/** Message affiché sous les champs : compte à rebours pendant un blocage, sinon l'erreur éventuelle. */
fun authMessage(form: FormState, lockSecondsLeft: Int): String? =
    if (lockSecondsLeft > 0) {
        "Trop de tentatives. Réessayez dans %d:%02d.".format(lockSecondsLeft / 60, lockSecondsLeft % 60)
    } else {
        form.error
    }
