package dev.kharioki.nyumbani

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First launch: name, location, dock apps, done. Every step can be skipped, and everything can
 * be changed later in Nyumbani settings.
 */
@Composable
fun SetupFlow(
    settings: LauncherSettings,
    allApps: List<AppEntry>,
    repo: SettingsRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    val stepFocus = remember { FocusRequester() }
    val steps = 3

    fun next() {
        if (step < steps) step++
    }

    BackHandler { if (step > 0) step-- }

    Scrim(alpha = 0.55f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassPanel(
                modifier = Modifier.width(600.dp),
                contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 24.dp, bottom = 22.dp),
            ) {
                Column {
                    Text(
                        if (step < steps) "WELCOME TO NYUMBANI · STEP ${step + 1} OF $steps" else "WELCOME TO NYUMBANI",
                        style = style(11, FontWeight.Medium, alpha = 0.7f, tracking = 2f, color = Accent),
                    )
                    Spacer(Modifier.height(10.dp))
                    when (step) {
                        0 -> {
                            PaneHeader("What's your name?", "It goes in the greeting, like \"Good evening, Wanjiru\".")
                            TextEntry(
                                label = "Name",
                                value = settings.name,
                                placeholder = "Press OK to type your name",
                                onCommit = { scope.launch { repo.setName(it) } },
                                rowFocus = stepFocus,
                            )
                        }
                        1 -> {
                            PaneHeader("Where are you?", "Your city or town, shown under the greeting. Skip to leave it out.")
                            TextEntry(
                                label = "Location",
                                value = settings.location,
                                placeholder = "Press OK to type a location",
                                onCommit = { scope.launch { repo.setLocation(it) } },
                                rowFocus = stepFocus,
                            )
                        }
                        2 -> {
                            PaneHeader("Pick your dock apps", "Up to ${LauncherConfig.MAX_DOCK_APPS}, in the order you want them.")
                            DockEditor(
                                allApps = allApps,
                                dock = settings.dockPackages,
                                onChange = { scope.launch { repo.setDock(it) } },
                                firstFocus = stepFocus,
                                modifier = Modifier.height(220.dp),
                            )
                        }
                        else -> {
                            PaneHeader("You're all set", null)
                            Tip("Hold OK on a dock app to move it or take it off the dock.")
                            Tip("The gear at the end of the dock opens Nyumbani settings: wallpaper, greeting, clock and more.")
                            Tip("Focus an app in the dock to see what you can continue watching.")
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (step in 1 until steps) PillButton("Back", onClick = { step-- })
                        Spacer(Modifier.weight(1f))
                        if (step < steps) {
                            PillButton("Skip", onClick = ::next)
                            Spacer(Modifier.width(10.dp))
                            PillButton("Next", onClick = ::next)
                        } else {
                            PillButton(
                                "Start",
                                onClick = {
                                    scope.launch { repo.setSetupDone() }
                                    onDone()
                                },
                                modifier = Modifier.focusRequester(stepFocus),
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(step, allApps.isNotEmpty()) {
        delay(60)
        runCatching { stepFocus.requestFocus() }
    }
}

@Composable
private fun Tip(text: String) {
    Row(Modifier.padding(bottom = 8.dp)) {
        Text("•", style = style(14, FontWeight.Medium, color = Accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = style(14, FontWeight.Normal, alpha = 0.9f))
    }
}
