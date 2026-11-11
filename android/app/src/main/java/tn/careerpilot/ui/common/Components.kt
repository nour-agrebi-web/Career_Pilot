package tn.careerpilot.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.careerpilot.ui.theme.CareerPilotTheme
import tn.careerpilot.ui.theme.DmSans

/** Snackbar partagée par tous les écrans de l'application connectée. */
val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

/** Affiche le message ponctuel du ViewModel puis le signale comme lu. */
@Composable
fun UiMessageEffect(state: UiState?, onShown: () -> Unit) {
    val host = LocalSnackbarHost.current
    val message = state?.message
    LaunchedEffect(message) {
        if (message != null) {
            onShown()
            host.showSnackbar(message.text)
        }
    }
}

// --- Formes de la maquette ---

val CardShape = RoundedCornerShape(15.dp)
val ButtonShape = RoundedCornerShape(9.dp)
val FieldShape = RoundedCornerShape(8.dp)
private val IconBoxShape = RoundedCornerShape(10.dp)

/** Marges horizontales des écrans (.screen { padding: 22px 16px }). */
val ScreenPadding = PaddingValues(horizontal = 16.dp)

// --- En-têtes ---

/** En-tête d'écran (.top-line) : petit surtitre gris, grand titre, et boutons carrés à droite. */
@Composable
fun ScreenHeader(
    title: String,
    eyebrow: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = CareerPilotTheme.colors
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow, color = c.muted, fontFamily = DmSans, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                title,
                color = c.title,
                fontFamily = DmSans,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.6).sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Bouton carré bordé (.icon-box). */
@Composable
fun IconBox(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color? = null) {
    val c = CareerPilotTheme.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(IconBoxShape)
            .background(c.card)
            .border(1.dp, c.border, IconBoxShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.text, modifier = Modifier.size(18.dp))
    }
}

/** En-tête des écrans secondaires (.detail-header) : flèche de retour et libellé gris. */
@Composable
fun BackHeader(label: String, onBack: () -> Unit, backIcon: ImageVector, actions: @Composable RowScope.() -> Unit = {}) {
    val c = CareerPilotTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(backIcon, contentDescription = "Retour", tint = c.text, modifier = Modifier.size(20.dp))
        }
        Text(label, color = c.muted, fontFamily = DmSans, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Titre de section (.section-title). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 15.dp, bottom = 9.dp),
        color = CareerPilotTheme.colors.title,
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

/** Barre de chargement fine aux couleurs de la maquette. */
@Composable
fun LoadingBar(modifier: Modifier = Modifier) {
    val c = CareerPilotTheme.colors
    LinearProgressIndicator(modifier.fillMaxWidth().height(3.dp), color = c.accent, trackColor = c.track)
}

// --- Cartes ---

/** Carte bordée blanche (.offer-card, .detail-card, .profile-card, .preference-card). */
@Composable
fun CpCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(15.dp),
    background: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CareerPilotTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(background ?: c.card)
            .border(1.dp, borderColor ?: c.border, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Cadre en pointillés (.cv-empty). */
@Composable
fun DashedBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val color = CareerPilotTheme.colors.dashed
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(11.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** État vide (.empty) : pastille turquoise, titre et texte centrés. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = CareerPilotTheme.colors
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        Text(
            text,
            color = c.muted,
            fontFamily = DmSans,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp).padding(top = 8.dp),
        )
    }
}

// --- Score ---

/** Couleur du score : vert au-dessus de 80, turquoise de 70 à 79, orange en dessous. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF1E8E5A)
    score >= 70 -> Color(0xFF007F7D)
    else -> Color(0xFFB7791F)
}

/** Pastille de score cerclée de turquoise (.score). */
@Composable
fun ScoreBadge(score: Int, size: Dp = 40.dp) {
    val c = CareerPilotTheme.colors
    Box(
        Modifier.size(size).background(c.scoreBackground, CircleShape).border(2.dp, c.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$score", color = c.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = (size.value / 3.2f).sp)
    }
}

/** Barre « libellé ... valeur / max » (.meter). */
@Composable
fun ScoreBar(label: String, value: Int, max: Int, color: Color? = null) {
    val c = CareerPilotTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.body, fontFamily = DmSans, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("$value / $max", color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.track)) {
            val fraction = if (max == 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(color ?: c.accent))
        }
    }
}

// --- Étiquettes ---

enum class TagStyle { NEUTRAL, WARNING, ACCENT }

/** Petite étiquette (.badge, .badge.warning). */
@Composable
fun Tag(text: String, style: TagStyle = TagStyle.NEUTRAL) {
    val c = CareerPilotTheme.colors
    val (bg, fg) = when (style) {
        TagStyle.NEUTRAL -> c.badgeBackground to c.badgeText
        TagStyle.WARNING -> c.warningBackground to c.warningText
        TagStyle.ACCENT -> c.skillBackground to c.skillText
    }
    Text(
        text,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        color = fg,
        fontFamily = DmSans,
        fontSize = 10.sp,
        lineHeight = 11.sp,
    )
}

/** Compétence (.skill). */
@Composable
fun SkillTag(text: String, onClick: (() -> Unit)? = null, warning: Boolean = false) {
    val c = CareerPilotTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Text(
        text,
        modifier = Modifier
            .clip(shape)
            .background(if (warning) c.warningBackground else c.skillBackground)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        color = if (warning) c.warningText else c.skillText,
        fontFamily = DmSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    )
}

/** Filtre en forme de pilule (.chip, .chip.active). */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = CareerPilotTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Text(
        text,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) c.primaryButton else c.card)
            .border(1.dp, if (selected) c.primaryButton else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        color = if (selected) c.onPrimaryButton else c.chipText,
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
    )
}

// --- Boutons ---

/** Bouton plein bleu nuit (.apply-btn, .auth-submit). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CareerPilotTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.primaryButton,
            contentColor = c.onPrimaryButton,
            disabledContainerColor = c.primaryButton.copy(alpha = 0.4f),
            disabledContentColor = c.onPrimaryButton.copy(alpha = 0.85f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
    ) {
        Text(text, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Bouton bordé blanc (.action-btn). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null) {
    val c = CareerPilotTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = color ?: c.text),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
    ) {
        Text(text, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Petit bouton turquoise pâle (.small-btn). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = CareerPilotTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.accentSoft.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
        Text(text, color = c.accent.copy(alpha = if (enabled) 1f else 0.6f), fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

/** Grand bouton d'import bordé de turquoise (.cv-import). */
@Composable
fun AccentOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = CareerPilotTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(c.accentSoft)
            .border(1.dp, c.accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Lien turquoise (.link-btn). */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, color: Color? = null) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
        Text(text, color = color ?: CareerPilotTheme.colors.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// --- Champs ---

/** Champ de recherche arrondi avec loupe (.search-wrap). */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = CareerPilotTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = c.text, fontFamily = DmSans, fontSize = 14.sp),
        cursorBrush = SolidColor(c.accent),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(c.inputBackground, shape)
                    .border(1.dp, if (focused) c.accent else c.inputBorder, shape)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = c.muted, fontFamily = DmSans, fontSize = 14.sp, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** Champ avec son libellé au-dessus (.field). */
@Composable
fun CpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = CareerPilotTheme.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (label != null) {
            Text(label, color = c.body, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = c.text, fontFamily = DmSans, fontSize = 13.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .background(c.inputBackground, FieldShape)
                        .border(1.dp, if (focused) c.accent else c.inputBorder, FieldShape)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = c.muted, fontFamily = DmSans, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
    }
}

/** Libellé de champ seul (.field label). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = CareerPilotTheme.colors.body,
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** Case à cocher et son libellé (.check-row). */
@Composable
fun CheckRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = CareerPilotTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(FieldShape).clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.muted, checkmarkColor = c.card),
        )
        Text(text, color = c.body, fontFamily = DmSans, fontSize = 13.sp)
    }
}

/** Interrupteur avec titre et explication. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = CareerPilotTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = c.muted, fontFamily = DmSans, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.card,
                uncheckedTrackColor = c.track,
                uncheckedThumbColor = c.muted,
                uncheckedBorderColor = c.border,
            ),
        )
    }
}

/** Séparateur fin entre deux éléments d'une carte (.experience { border-top }). */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(CareerPilotTheme.colors.divider))
}

/** Boîte de dialogue aux couleurs de la maquette. */
@Composable
fun CpDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "Annuler",
    destructive: Boolean = false,
    extraAction: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CareerPilotTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = CardShape,
        title = { Text(title, color = c.title, fontFamily = DmSans, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Column(content = content) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) c.error else c.accent, fontFamily = DmSans, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (extraAction != null) {
                    TextButton(onClick = extraAction.second) {
                        Text(extraAction.first, color = c.error, fontFamily = DmSans, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) { Text(dismissText, color = c.muted, fontFamily = DmSans) }
            }
        },
    )
}

/** Texte courant d'une carte. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color? = null, size: Int = 12) {
    Text(text, modifier = modifier, color = color ?: CareerPilotTheme.colors.body, fontFamily = DmSans, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
}

/** Texte secondaire gris (.note, .company, .source). */
@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, size: Int = 11) {
    Text(text, modifier = modifier, color = CareerPilotTheme.colors.muted, fontFamily = DmSans, fontSize = size.sp, lineHeight = (size * 1.45f).sp)
}
