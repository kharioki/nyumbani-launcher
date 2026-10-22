package dev.kharioki.nyumbani

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

enum class SettingsSection(val title: String) {
    NAME("Your name"),
    GREETING("Greeting"),
    LOCATION("Location"),
    CLOCK("Clock"),
    DOCK("Dock apps"),
    WALLPAPER("Wallpaper"),
    HOME("Make Nyumbani your Home screen"),
    ANDROID("Android TV settings"),
    ABOUT("About"),
}

/**
 * Nyumbani settings: sections on the left, the chosen section on a glass pane on the right.
 * Moving through the list shows each section; Right or OK goes into it, Back closes settings.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    allApps: List<AppEntry>,
    repo: SettingsRepository,
    initialSection: SettingsSection,
    onOpenWallpaper: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var section by remember { mutableStateOf(initialSection) }
    val initialFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    Scrim(alpha = 0.72f) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = SafeH, vertical = SafeV)
        ) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Text("Nyumbani settings", style = style(24, FontWeight.Light))
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.focusRestorer().focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsSection.entries.forEach { s ->
                        SectionItem(
                            title = s.title,
                            selected = s == section,
                            onFocused = { section = s },
                            onClick = { focusManager.moveFocus(FocusDirection.Right) },
                            modifier = if (s == initialSection) Modifier.focusRequester(initialFocus) else Modifier,
                        )
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            GlassPanel(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp),
            ) {
                Column(Modifier.fillMaxSize().focusGroup()) {
                    fun save(block: suspend SettingsRepository.() -> Unit) {
                        scope.launch { repo.block() }
                    }
                    when (section) {
                        SettingsSection.NAME -> {
                            PaneHeader("Your name", "Shown in the greeting. Leave it empty for just the greeting.")
                            TextEntry(label = "Name", value = settings.name, placeholder = "Add your name", onCommit = { save { setName(it) } })
                        }
                        SettingsSection.GREETING -> GreetingPane(settings, save = ::save)
                        SettingsSection.LOCATION -> {
                            PaneHeader("Location", "Shown under the greeting, for example your city. Leave it empty to hide it.")
                            TextEntry(label = "Location", value = settings.location, placeholder = "Add a location", onCommit = { save { setLocation(it) } })
                        }
                        SettingsSection.CLOCK -> {
                            PaneHeader("Clock")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChoiceRow("Follow the TV", settings.use24Hour == ClockMode.FOLLOW_SYSTEM, { save { setClock(ClockMode.FOLLOW_SYSTEM) } },
                                    subtitle = "Uses the TV's Date & time setting")
                                ChoiceRow("24-hour", settings.use24Hour == ClockMode.ON, { save { setClock(ClockMode.ON) } }, subtitle = "18:30")
                                ChoiceRow("12-hour", settings.use24Hour == ClockMode.OFF, { save { setClock(ClockMode.OFF) } }, subtitle = "6:30 PM")
                            }
                        }
                        SettingsSection.DOCK -> {
                            PaneHeader("Dock apps", "Tip: on the home screen, hold OK on a dock app to move or remove it.")
                            DockEditor(allApps = allApps, dock = settings.dockPackages, onChange = { save { setDock(it) } })
                        }
                        SettingsSection.WALLPAPER -> WallpaperPane(settings, onOpenWallpaper)
                        SettingsSection.HOME -> HomeScreenPane()
                        SettingsSection.ANDROID -> {
                            PaneHeader("Android TV settings", "Network, display, sound, accounts and apps: the TV's own settings.")
                            PillButton("Open Android TV settings", onClick = onOpenSystemSettings)
                        }
                        SettingsSection.ABOUT -> AboutPane()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(40)
        runCatching { initialFocus.requestFocus() }
    }
}

@Composable
private fun SectionItem(title: String, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(if (selected && !focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Marks the section on show while focus is in the pane.
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .background(if (selected) Accent else Color.Transparent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = style(15, if (selected) FontWeight.Medium else FontWeight.Normal, color = ink(focused)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GreetingPane(settings: LauncherSettings, save: (suspend SettingsRepository.() -> Unit) -> Unit) {
    val hour = LocalTime.now().hour
    PaneHeader("Greeting", "Preview: ${greetingText(settings, hour)}")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceRow(
            title = "Time of day",
            subtitle = greetingText(settings.copy(greetingStyle = GreetingStyle.TIME_OF_DAY), hour),
            selected = settings.greetingStyle == GreetingStyle.TIME_OF_DAY,
            onClick = { save { setGreetingStyle(GreetingStyle.TIME_OF_DAY) } },
        )
        ChoiceRow(
            title = "Custom",
            subtitle = greetingText(settings.copy(greetingStyle = GreetingStyle.CUSTOM), hour),
            selected = settings.greetingStyle == GreetingStyle.CUSTOM,
            onClick = { save { setGreetingStyle(GreetingStyle.CUSTOM) } },
        )
        Spacer(Modifier.height(4.dp))
        TextEntry(
            label = "Custom greeting",
            value = settings.customGreeting,
            placeholder = "For example: Hey, Karibu, Welcome home",
            onCommit = { text ->
                save {
                    setCustomGreeting(text)
                    setGreetingStyle(GreetingStyle.CUSTOM)
                }
            },
        )
    }
}

@Composable
private fun WallpaperPane(settings: LauncherSettings, onOpenWallpaper: () -> Unit) {
    val current = Wallpaper.BUNDLED.firstOrNull { it.id == settings.wallpaperId }?.name
        ?: if (Wallpaper.isImported(settings.wallpaperId)) "Your own image" else Wallpaper.BUNDLED.first().name
    PaneHeader("Wallpaper", "Pick a bundled wallpaper, send a photo from your phone, or copy one from a USB drive.")
    Text("In use: $current", style = style(14, FontWeight.Normal, alpha = 0.85f))
    Spacer(Modifier.height(14.dp))
    PillButton("Choose wallpaper", onClick = onOpenWallpaper)
    Spacer(Modifier.height(20.dp))
    Text("Advanced: an image pushed over ADB overrides this choice.", style = style(12, FontWeight.Normal, alpha = 0.6f))
    Spacer(Modifier.height(6.dp))
    Command("adb push my-wallpaper.jpg /sdcard/Android/data/${BuildConfig.APPLICATION_ID}/files/wallpaper.jpg")
}

@Composable
private fun HomeScreenPane() {
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(HomeRole.isHeld(context)) }
    // The ADB route: shown when the TV can't (or won't) show its own prompt.
    var showFallback by remember { mutableStateOf(false) }
    val askForRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isHeld(context)
        if (!isHome) showFallback = true
    }

    PaneHeader(
        "Make Nyumbani your Home screen",
        "So the Home button on your remote opens Nyumbani instead of the TV's own launcher.",
    )
    if (isHome) {
        Text("Nyumbani is your Home screen.", style = style(15, FontWeight.Medium, color = Accent))
        Spacer(Modifier.height(12.dp))
        if (!showFallback) {
            PillButton("Home still opens the old launcher?", onClick = { showFallback = true })
        }
    } else {
        PillButton(
            "Make Nyumbani the Home screen",
            onClick = {
                val intent = HomeRole.requestIntent(context)
                if (intent == null || runCatching { askForRole.launch(intent) }.isFailure) showFallback = true
            },
        )
    }
    if (showFallback) {
        Spacer(Modifier.height(14.dp))
        if (!isHome) {
            Text("No prompt, or it didn't stick? Set it once from a computer:", style = style(13, FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
        }
        HomeSetupInstructions()
    }
}

/** The ADB fallback for TVs that don't offer a "choose Home app" prompt. */
@Composable
fun HomeSetupInstructions() {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "From a computer on the same network, with Network debugging (ADB) turned on in Developer options, run:",
            style = style(13, FontWeight.Normal, alpha = 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        Command("adb shell cmd package set-home-activity ${BuildConfig.APPLICATION_ID}/.MainActivity")
        Spacer(Modifier.height(8.dp))
        Text(
            "Then press Home. If the TV's own launcher still comes back, turn it off (undo with pm enable):",
            style = style(13, FontWeight.Normal, alpha = 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        Command("adb shell pm disable-user --user 0 com.google.android.tvlauncher")
    }
}

@Composable
private fun Command(text: String) {
    Text(
        text,
        style = style(12, FontWeight.Normal).copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun AboutPane() {
    PaneHeader("About")
    Row {
        Column(Modifier.weight(1f)) {
            Text("Nyumbani ${BuildConfig.VERSION_NAME}", style = style(18, FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text("A calm, ad-free home screen for Android TV.", style = style(13, FontWeight.Normal, alpha = 0.8f))
            Spacer(Modifier.height(12.dp))
            Text("Releases and source code:", style = style(12, FontWeight.Normal, alpha = 0.7f))
            Text(LauncherConfig.RELEASES_URL, style = style(13, FontWeight.Normal))
            Spacer(Modifier.height(12.dp))
            Text("Inter typeface: SIL Open Font License 1.1", style = style(11, FontWeight.Normal, alpha = 0.6f))
        }
        Spacer(Modifier.width(16.dp))
        QrCode(LauncherConfig.RELEASES_URL, Modifier.size(128.dp))
    }
}
