package dev.kharioki.nyumbani

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Theme

internal val Accent = Color(0xFFF2A866)
internal val Night = Color(0xFF0E0F1F)

internal val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val SoftShadow = Shadow(color = Color.Black.copy(alpha = 0.30f), offset = Offset(0f, 1f), blurRadius = 8f)

internal fun style(
    size: Int,
    weight: FontWeight,
    alpha: Float = 1f,
    tracking: Float = 0f,
    color: Color = Color.White,
) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    fontWeight = weight,
    color = color.copy(alpha = alpha),
    letterSpacing = tracking.sp,
    // A shadow helps white text on the wallpaper, but smudges dark text on a focused (white) control.
    shadow = if (color == Night) null else SoftShadow,
)

/** Text colour for a control: dark on the white focused state, white otherwise. */
internal fun ink(focused: Boolean) = if (focused) Night else Color.White

// TV-safe margins: keeps everything clear of overscan on older panels.
internal val SafeH = 56.dp
internal val SafeV = 32.dp

// ---------------------------------------------------------------- Controls

/**
 * The basic focusable control: translucent at rest, solid white with dark content when focused,
 * so the D-pad position is always obvious. [content] gets the focus state to pick its colours.
 *
 * Colours and scale follow our own focus tracking rather than the Surface's: when focus is moved
 * in code right after the focused control disappears, the Surface can miss the change.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.03f,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(150), label = "focus-scale")
    val container = when {
        focused -> Color.White
        enabled -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val border = if (focused) Border.None else Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            // Disabled controls are skipped by the D-pad instead of trapping it.
            .focusProperties { canFocus = enabled }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f, disabledScale = 1f, focusedDisabledScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = ink(focused),
            focusedContainerColor = container,
            focusedContentColor = ink(focused),
            pressedContainerColor = container,
            pressedContentColor = ink(focused),
            disabledContainerColor = container,
            disabledContentColor = ink(focused).copy(alpha = 0.4f),
        ),
        border = ClickableSurfaceDefaults.border(
            border = border,
            focusedBorder = border,
            pressedBorder = border,
            disabledBorder = border,
            focusedDisabledBorder = border,
        ),
    ) {
        content(focused)
    }
}

/** A pill-shaped button with an optional leading icon. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = CircleShape, enabled = enabled, focusedScale = 1.06f) { focused ->
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon(ink(focused))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = style(15, FontWeight.Medium, alpha = if (enabled) 1f else 0.45f, color = ink(focused)),
                maxLines = 1,
            )
        }
    }
}

/** A full-width row with a title, an optional line of detail, and optional leading/trailing parts. */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable (focused: Boolean) -> Unit)? = null,
    trailing: (@Composable RowScope.(focused: Boolean) -> Unit)? = null,
) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth(), enabled = enabled) { focused ->
        Row(
            Modifier.heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading(focused)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = style(15, FontWeight.Medium, alpha = if (enabled) 1f else 0.45f, color = ink(focused)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = style(12, FontWeight.Normal, alpha = 0.7f, color = ink(focused)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this, focused)
        }
    }
}

/** A round radio mark (single choice) or a rounded check box (multiple choice). */
@Composable
fun ChoiceMark(selected: Boolean, focused: Boolean, multiple: Boolean = false) {
    val ink = ink(focused)
    val shape = if (multiple) RoundedCornerShape(5.dp) else CircleShape
    Box(
        Modifier
            .size(20.dp)
            .then(
                if (selected && multiple) Modifier.background(Accent, shape)
                else Modifier.border(2.dp, if (selected) Accent else ink.copy(alpha = 0.6f), shape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (multiple) Icon(Icons.Filled.Check, contentDescription = null, tint = Night, modifier = Modifier.size(16.dp))
            else Box(Modifier.size(10.dp).background(Accent, CircleShape))
        }
    }
}

/** A labelled choice for radio-button style settings. */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        leading = { focused -> ChoiceMark(selected, focused) },
    )
}

/**
 * A text setting that works with a D-pad: the row shows the value, OK starts editing (the TV's
 * on-screen keyboard opens), and Done or Back saves it. Moving focus away also saves.
 */
@Composable
fun TextEntry(
    label: String,
    value: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowFocus: FocusRequester = remember { FocusRequester() },
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue(value)) }
    // Bumped when editing ends with Done/Back, so focus goes back to the row.
    var refocus by remember { mutableIntStateOf(0) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun finish(returnFocus: Boolean) {
        if (!editing) return
        editing = false
        keyboard?.hide()
        if (text.text.trim() != value) onCommit(text.text)
        if (returnFocus) refocus++
    }

    if (editing) {
        BackHandler { finish(returnFocus = true) }
        var hadFocus by remember { mutableStateOf(false) }
        Column(
            modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .border(2.dp, Accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(label, style = style(12, FontWeight.Normal, alpha = 0.7f))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = style(17, FontWeight.Normal),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish(returnFocus = true) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) finish(returnFocus = false)
                    },
            )
        }
        LaunchedEffect(Unit) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    } else {
        ListRow(
            title = value.ifBlank { placeholder },
            subtitle = label,
            onClick = {
                text = TextFieldValue(value, selection = TextRange(value.length))
                editing = true
            },
            modifier = modifier.focusRequester(rowFocus),
        )
    }

    LaunchedEffect(refocus) {
        if (refocus > 0) {
            delay(30)
            runCatching { rowFocus.requestFocus() }
        }
    }
}

/** Section heading plus an optional explanation, used at the top of settings panes. */
@Composable
fun PaneHeader(title: String, description: String? = null) {
    Text(title, style = style(22, FontWeight.Light))
    if (description != null) {
        Spacer(Modifier.height(6.dp))
        Text(description, style = style(13, FontWeight.Normal, alpha = 0.75f))
    }
    Spacer(Modifier.height(16.dp))
}

data class MenuItem(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * A small glass menu in the middle of the screen (long-press actions, confirmations).
 * Back or choosing an item closes it.
 */
@Composable
fun MenuOverlay(title: String, items: List<MenuItem>, onDismiss: () -> Unit, width: Dp = 320.dp) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        GlassPanel(
            modifier = Modifier.widthIn(min = width, max = width + 80.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = style(18, FontWeight.Light), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val firstEnabled = items.indexOfFirst { it.enabled }
                items.forEachIndexed { index, item ->
                    ListRow(
                        title = item.label,
                        enabled = item.enabled,
                        onClick = {
                            onDismiss()
                            item.onClick()
                        },
                        modifier = if (index == firstEnabled) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { first.requestFocus() }
    }
}

/** Full-screen dimmed layer for overlays that sit on top of the home screen. */
@Composable
fun Scrim(alpha: Float = 0.80f, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Night.copy(alpha = alpha))
    ) { content() }
}
