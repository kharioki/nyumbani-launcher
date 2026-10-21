package dev.kharioki.nyumbani

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What's showing on top of the home screen. */
private sealed interface Overlay {
    data object None : Overlay
    data object AllApps : Overlay
    data class Settings(val section: SettingsSection) : Overlay
    data class DockMenu(val packageName: String) : Overlay
}

/** Focus target meaning "the gear button" rather than a dock app. */
private const val FOCUS_GEAR = "#gear"

@Composable
fun HomeScreen(resumeTick: Int, homeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AppRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    // Null until DataStore has been read once. Owner seeding runs first, so setup doesn't flash.
    val settings by produceState<LauncherSettings?>(initialValue = null) {
        settingsRepo.seedOwnerIfNeeded()
        settingsRepo.settings.collect { value = it }
    }

    var wallpaper by remember { mutableStateOf<Wallpaper.Images?>(null) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    var wallpaperSignature by remember { mutableStateOf<String?>(null) }
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var watchNext by remember { mutableStateOf<Map<String, WatchNextState>>(emptyMap()) }
    var activePackage by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    // Where focus goes when the home screen is back in front: a dock package, FOCUS_GEAR, or null for the first app.
    var focusTarget by remember { mutableStateOf<String?>(null) }

    val gearFocus = remember { FocusRequester() }
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    val dockPackages = settings?.dockPackages.orEmpty()
    val dock = remember(dockPackages, allApps) { repo.resolveDock(dockPackages, allApps) }
    val dockIds = dock.map { it.packageName }
    val showSetup = settings?.setupDone == false

    fun saveDock(packages: List<String>) {
        scope.launch { settingsRepo.setDock(packages) }
    }

    // Refresh on every resume: wallpaper (only if it changed), installed apps, Continue watching.
    LaunchedEffect(resumeTick) {
        val metrics = context.resources.displayMetrics
        val w = maxOf(metrics.widthPixels, metrics.heightPixels)
        val h = minOf(metrics.widthPixels, metrics.heightPixels)

        val signature = withContext(Dispatchers.IO) { Wallpaper.signature(context) }
        if (signature != wallpaperSignature) {
            wallpaper = withContext(Dispatchers.IO) { Wallpaper.load(context, w, h) }
            wallpaperSignature = signature
        }

        val apps = withContext(Dispatchers.IO) { repo.loadAllApps() }
        allApps = apps
        settingsRepo.seedDockIfNeeded(apps)
    }

    // Continue watching for the dock apps: on every resume, and whenever the dock changes.
    LaunchedEffect(resumeTick, dockIds) {
        watchNext = withContext(Dispatchers.IO) {
            dock.associate { app ->
                app.packageName to WatchNextRepository.load(context, app.packageName, LauncherConfig.WATCH_NEXT_LIMIT)
            }
        }
    }

    // Home pressed: close whatever is open and go back to the first app.
    LaunchedEffect(homeTick) {
        overlay = Overlay.None
        focusTarget = null
    }
    // First launch, Home pressed, or an overlay closed: put focus back in the dock.
    LaunchedEffect(homeTick, overlay, showSetup, dockIds) {
        if (overlay == Overlay.None && !showSetup && dock.isNotEmpty()) {
            delay(60)
            val target = when (val t = focusTarget) {
                FOCUS_GEAR -> gearFocus
                null -> null
                else -> tileFocus[t]
            } ?: tileFocus[dockIds.first()]
            runCatching { target?.requestFocus() }
        }
    }

    // A launcher has nowhere to go "back" to.
    BackHandler(enabled = true) {}

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalBackdrop provides Backdrop(wallpaper?.frosted, screen)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Night)
                    .onSizeChanged { screen = it }
            ) {
                wallpaper?.let {
                    Image(bitmap = it.sharp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }

                // Gentle shading behind the greeting and the dock so text stays readable on any wallpaper.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                                    center = Offset.Zero,
                                    radius = size.width * 0.55f,
                                )
                            )
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.35f),
                                )
                            )
                        }
                )

                // Settings has its own title in that corner.
                if (overlay !is Overlay.Settings) {
                    GlassPanel(
                        modifier = Modifier.align(Alignment.TopStart).padding(start = SafeH, top = SafeV),
                        contentPadding = PaddingValues(start = 26.dp, end = 34.dp, top = 18.dp, bottom = 20.dp),
                    ) { settings?.let { Greeting(it) } }
                }

                if (overlay == Overlay.None || overlay is Overlay.DockMenu) {
                    // Only reachable with the D-pad when nothing is on top of it.
                    val dockFocusable = overlay == Overlay.None && !showSetup
                    val tileWidth = dockTileWidth(LocalConfiguration.current.screenWidthDp.dp, dock.size)
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = SafeH, end = SafeH, bottom = SafeV)
                    ) {
                        ContinueWatching(
                            packageName = activePackage.takeIf { dockFocusable },
                            appLabel = dock.firstOrNull { it.packageName == activePackage }?.label,
                            state = activePackage?.let { watchNext[it] },
                            onOpen = { item ->
                                val deepLink = WatchNextRepository.intentFor(item)
                                if (deepLink != null) repo.start(deepLink) else repo.launch(item.packageName)
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        GlassPanel(
                            shape = RoundedCornerShape(30.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(DockGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                dock.forEach { app ->
                                    DockTile(
                                        app = app,
                                        width = tileWidth,
                                        focusable = dockFocusable,
                                        modifier = Modifier.focusRequester(tileFocus.getOrPut(app.packageName) { FocusRequester() }),
                                        onFocused = { activePackage = app.packageName },
                                        onClick = { repo.launch(app.packageName) },
                                        onLongClick = { overlay = Overlay.DockMenu(app.packageName) },
                                    )
                                }
                                Spacer(Modifier.width(2.dp))
                                RoundButton(
                                    label = "All apps",
                                    focusable = dockFocusable,
                                    onFocused = { activePackage = null },
                                    onClick = { overlay = Overlay.AllApps },
                                ) { DotsGrid() }
                                RoundButton(
                                    label = "Settings",
                                    focusable = dockFocusable,
                                    modifier = Modifier.focusRequester(gearFocus),
                                    onFocused = { activePackage = null },
                                    onClick = {
                                        focusTarget = FOCUS_GEAR
                                        overlay = Overlay.Settings(SettingsSection.NAME)
                                    },
                                ) { Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    }
                }

                when (val o = overlay) {
                    Overlay.None -> Unit
                    Overlay.AllApps -> AllAppsOverlay(
                        apps = allApps,
                        onLaunch = { app ->
                            overlay = Overlay.None
                            repo.launch(app.packageName)
                        },
                        onClose = { overlay = Overlay.None },
                    )
                    is Overlay.Settings -> settings?.let { s ->
                        SettingsScreen(
                            settings = s,
                            allApps = allApps,
                            repo = settingsRepo,
                            initialSection = o.section,
                            onOpenWallpaper = {},
                            onOpenSystemSettings = { repo.openSettings() },
                            onClose = { overlay = Overlay.None },
                        )
                    }
                    is Overlay.DockMenu -> {
                        val index = dockIds.indexOf(o.packageName)
                        fun moveBy(by: Int) = saveDock(dockIds.toMutableList().apply { add(index + by, removeAt(index)) })
                        MenuOverlay(
                            title = dock.getOrNull(index)?.label ?: "Dock app",
                            items = listOf(
                                MenuItem("Move left", enabled = index > 0) { moveBy(-1) },
                                MenuItem("Move right", enabled = index in 0 until dockIds.size - 1) { moveBy(+1) },
                                MenuItem("Remove from dock", enabled = index >= 0 && dockIds.size > 1) {
                                    focusTarget = dockIds.getOrNull(index + 1) ?: dockIds.getOrNull(index - 1)
                                    saveDock(dockIds - o.packageName)
                                },
                            ),
                            onDismiss = {
                                focusTarget = o.packageName
                                overlay = Overlay.None
                            },
                        )
                    }
                }

                if (showSetup) {
                    settings?.let { s ->
                        SetupFlow(settings = s, allApps = allApps, repo = settingsRepo, onDone = { focusTarget = null })
                    }
                }
            }
        }
    }
}

private val DockGap = 18.dp

/**
 * Dock tiles shrink when there are more apps, so the dock plus the two round buttons always fit
 * between the TV-safe margins (5 apps on a 960dp-wide screen come out at about 118dp).
 */
private fun dockTileWidth(screenWidth: Dp, count: Int): Dp {
    if (count <= 0) return 164.dp
    // Panel padding (32), spacer + two round buttons (2 + 48 + 48) and their gaps (3 x 18).
    val fixed = 32.dp + 98.dp + DockGap * 3
    val available = screenWidth - SafeH * 2 - fixed - DockGap * (count - 1)
    return (available / count).coerceIn(96.dp, 164.dp)
}

// ---------------------------------------------------------------- Greeting + clock

@Composable
private fun Greeting(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Ticks on the minute, so the clock is never stale and we don't recompose every second.
    val now by produceState(initialValue = ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val is24h = when (settings.use24Hour) {
        ClockMode.FOLLOW_SYSTEM -> DateFormat.is24HourFormat(context)
        ClockMode.ON -> true
        ClockMode.OFF -> false
    }
    val timeFormat = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm") }
    val amPmFormat = remember { DateTimeFormatter.ofPattern("a", Locale.getDefault()) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }

    Column(modifier) {
        Text(greetingText(settings, now.hour), style = style(26, FontWeight.Light))
        if (settings.location.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(settings.location.uppercase(), style = style(12, FontWeight.Medium, alpha = 0.8f, tracking = 2.4f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(now.format(timeFormat), style = style(56, FontWeight.Thin))
            if (!is24h) {
                Spacer(Modifier.width(8.dp))
                Text(
                    now.format(amPmFormat).uppercase(),
                    style = style(18, FontWeight.Light, alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 11.dp),
                )
            }
        }
        Text(now.format(dateFormat), style = style(17, FontWeight.Normal, alpha = 0.85f))
    }
}

/** "Good evening, Tony", "Hey Tony", or just the greeting when no name is set. */
fun greetingText(settings: LauncherSettings, hour: Int): String {
    val name = settings.name.trim()
    val timeOfDay = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val custom = settings.customGreeting.trim()
    return when {
        settings.greetingStyle == GreetingStyle.CUSTOM && custom.isNotEmpty() -> if (name.isEmpty()) custom else "$custom $name"
        name.isEmpty() -> timeOfDay
        else -> "$timeOfDay, $name"
    }
}

// ---------------------------------------------------------------- Continue watching

private val ContinueAreaHeight = 138.dp

@Composable
private fun ContinueWatching(
    packageName: String?,
    appLabel: String?,
    state: WatchNextState?,
    onOpen: (WatchNextItem) -> Unit,
) {
    // Fixed height, so the dock never jumps when the row appears or disappears.
    Box(
        Modifier
            .fillMaxWidth()
            .height(ContinueAreaHeight),
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedContent(
            targetState = Triple(packageName, appLabel, state),
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "continue-watching",
        ) { (pkg, label, st) ->
            val name = label ?: "this app"
            when {
                // Many TVs reserve the Watch Next list for system apps. When we can't read it,
                // leave the space empty rather than nagging: the wallpaper just shows through.
                pkg == null || st == null || st == WatchNextState.Loading ||
                    st == WatchNextState.NoPermission || st is WatchNextState.Failed -> Box(Modifier.fillMaxWidth())
                st is WatchNextState.Loaded -> {
                    if (st.items.isEmpty()) {
                        Hint("Nothing to continue on $name")
                    } else {
                        Column {
                            Text("CONTINUE ON ${name.uppercase()}", style = style(12, FontWeight.Medium, alpha = 0.75f, tracking = 2f))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                st.items.forEach { item -> WatchNextCard(item) { onOpen(item) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = style(14, FontWeight.Normal, alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun WatchNextCard(item: WatchNextItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(184.dp)
            .height(104.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
        ),
    ) {
        AsyncImage(
            model = item.imageUri,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(item.title, style = style(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, style = style(11, FontWeight.Normal, alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item.progress?.let { p ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(p)
                        .background(Accent)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Dock

@Composable
private fun DockTile(
    app: AppEntry,
    width: Dp,
    focusable: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
            .height(width * (92f / 164f))
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusProperties { canFocus = focusable },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White.copy(alpha = 0.95f)), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
        ),
    ) {
        AppArt(app = app, fallbackLabel = app.label)
    }
}

@Composable
private fun AppArt(app: AppEntry?, fallbackLabel: String) {
    when {
        app?.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        app?.icon != null -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(6.dp))
            Text(app.label, style = style(12, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        else -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(fallbackLabel, style = style(15, FontWeight.Medium))
            Text("not installed", style = style(11, FontWeight.Normal, alpha = 0.6f))
        }
    }
}

@Composable
private fun RoundButton(
    label: String,
    focusable: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Same height as the label below, so the circle lines up with the middle of the app tiles.
        Spacer(Modifier.height(LabelSlot))
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(48.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .focusProperties { canFocus = focusable },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Night,
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        // Label only appears on focus, keeping the dock clean.
        Box(Modifier.height(LabelSlot), contentAlignment = Alignment.BottomCenter) {
            if (focused) Text(label, style = style(11, FontWeight.Medium, alpha = 0.9f), maxLines = 1)
        }
    }
}

private val LabelSlot = 22.dp

/** A 3x3 dot grid: the usual "all apps" glyph, drawn so we don't need the big icon library. */
@Composable
private fun DotsGrid() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp)) {
        val step = size.width / 3f
        val r = step * 0.18f
        for (row in 0..2) for (col in 0..2) {
            drawCircle(color, radius = r, center = Offset(step * (col + 0.5f), step * (row + 0.5f)))
        }
    }
}

// ---------------------------------------------------------------- All apps

@Composable
private fun AllAppsOverlay(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Night.copy(alpha = 0.88f))
    ) {
        Column(Modifier.padding(start = SafeH, end = SafeH, top = SafeV)) {
            Text("All apps", style = style(28, FontWeight.Light))
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = SafeV),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    val shape = RoundedCornerShape(12.dp)
                    Column {
                        Surface(
                            onClick = { onLaunch(app) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                            shape = ClickableSurfaceDefaults.shape(shape = shape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            ),
                        ) {
                            AppArt(app = app, fallbackLabel = app.label)
                        }
                        Text(
                            app.label,
                            style = style(12, FontWeight.Normal, alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            delay(80)
            runCatching { first.requestFocus() }
        }
    }
}
