package dev.kharioki.nyumbani

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface PickerMode {
    data object Row : PickerMode
    data object Phone : PickerMode
    data object Usb : PickerMode
    data class ConfirmDelete(val id: String) : PickerMode
}

/**
 * Wallpaper picker: a row of thumbnails along the bottom, so the wallpaper itself stays in view.
 * Choosing one applies it at once. Your own images can be deleted (hold OK); new ones come from a
 * phone (QR code) or a USB drive.
 */
@Composable
fun WallpaperPicker(settings: LauncherSettings, repo: SettingsRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<PickerMode>(PickerMode.Row) }
    // Bumped whenever an image is added or deleted, so the row re-reads the folder.
    var version by remember { mutableIntStateOf(0) }
    val imported by produceState(emptyList<File>(), version) {
        value = withContext(Dispatchers.IO) { Wallpaper.imported(context) }
    }
    val adbOverride by produceState(false) {
        value = withContext(Dispatchers.IO) { Wallpaper.customFile(context) != null }
    }

    fun select(id: String) {
        scope.launch { repo.setWallpaper(id) }
    }

    fun added(id: String) {
        version++
        select(id)
    }

    when (val m = mode) {
        PickerMode.Row -> PickerRow(
            settings = settings,
            imported = imported,
            adbOverride = adbOverride,
            onSelect = ::select,
            onDelete = { mode = PickerMode.ConfirmDelete(it) },
            onPhone = { mode = PickerMode.Phone },
            onUsb = { mode = PickerMode.Usb },
            onClose = onClose,
        )
        PickerMode.Phone -> SendFromPhone(onImported = ::added, onClose = { mode = PickerMode.Row })
        PickerMode.Usb -> UsbBrowser(
            onImported = {
                added(it)
                mode = PickerMode.Row
            },
            onClose = { mode = PickerMode.Row },
        )
        is PickerMode.ConfirmDelete -> MenuOverlay(
            title = "Delete this wallpaper?",
            items = listOf(
                MenuItem("Delete") {
                    scope.launch {
                        withContext(Dispatchers.IO) { Wallpaper.deleteImported(context, m.id) }
                        if (settings.wallpaperId == m.id) repo.setWallpaper(LauncherConfig.DEFAULT_WALLPAPER_ID)
                        version++
                    }
                },
                MenuItem("Keep it") {},
            ),
            onDismiss = { mode = PickerMode.Row },
        )
    }
}

@Composable
private fun PickerRow(
    settings: LauncherSettings,
    imported: List<File>,
    adbOverride: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPhone: () -> Unit,
    onUsb: () -> Unit,
    onClose: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = SafeH, end = SafeH, bottom = SafeV),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Wallpaper", style = style(22, FontWeight.Light))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Changes apply at once. Hold OK on your own images to delete them.",
                        style = style(12, FontWeight.Normal, alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                if (adbOverride) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A wallpaper pushed over ADB is overriding this choice. Delete files/wallpaper.jpg to use these.",
                        style = style(12, FontWeight.Medium, color = Accent),
                    )
                }
                Spacer(Modifier.height(12.dp))
                val selectedId = settings.wallpaperId.takeIf { id ->
                    Wallpaper.BUNDLED.any { it.id == id } || imported.any { it.name == id }
                } ?: LauncherConfig.DEFAULT_WALLPAPER_ID
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    items(Wallpaper.BUNDLED, key = { it.id }) { b ->
                        WallpaperTile(
                            id = b.id,
                            name = b.name,
                            selected = b.id == selectedId,
                            onClick = { onSelect(b.id) },
                            modifier = if (b.id == selectedId) Modifier.focusRequester(selectedFocus) else Modifier,
                        )
                    }
                    items(imported, key = { it.name }) { f ->
                        WallpaperTile(
                            id = f.name,
                            name = "Your image",
                            selected = f.name == selectedId,
                            onClick = { onSelect(f.name) },
                            onLongClick = { onDelete(f.name) },
                            modifier = if (f.name == selectedId) Modifier.focusRequester(selectedFocus) else Modifier,
                        )
                    }
                    item(key = "phone") { ActionTile("Send from phone", "Scan a QR code", Icons.Filled.Phone, onPhone) }
                    item(key = "usb") { ActionTile("From USB", "Or Downloads / Pictures", Icons.Filled.Add, onUsb) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { selectedFocus.requestFocus() }
    }
}

private val TileW = 176.dp
private val TileH = 99.dp

@Composable
private fun TileFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    caption: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .size(TileW, TileH)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                border = if (selected) Border(BorderStroke(2.dp, Accent), shape = shape) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.16f),
            ),
        ) {
            content()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            caption,
            style = style(12, if (focused) FontWeight.SemiBold else FontWeight.Normal, alpha = if (focused) 1f else 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TileW),
        )
    }
}

@Composable
private fun WallpaperTile(
    id: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { Wallpaper.thumbnail(context, id) }
    }
    TileFrame(onClick = onClick, onLongClick = onLongClick, selected = selected, caption = name, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            thumb?.let { Image(bitmap = it, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = "In use", tint = Night, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, caption: String, icon: ImageVector, onClick: () -> Unit) {
    TileFrame(onClick = onClick, caption = caption) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = style(14, FontWeight.Medium))
        }
    }
}

// ---------------------------------------------------------------- Send from phone

private const val PHONE_TIMEOUT_MS = 10 * 60 * 1000L

private sealed interface PhoneState {
    data object Starting : PhoneState
    data class Ready(val url: String) : PhoneState
    data object NoNetwork : PhoneState
    data object TimedOut : PhoneState
}

@Composable
private fun SendFromPhone(onImported: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PhoneState>(PhoneState.Starting) }
    var message by remember { mutableStateOf<String?>(null) }
    var received by remember { mutableIntStateOf(0) }
    val closeFocus = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    // The server lives exactly as long as this screen (or 10 minutes, whichever is shorter).
    DisposableEffect(Unit) {
        val server = PhoneUploadServer(File(context.cacheDir, "uploads")) { file ->
            val id = Wallpaper.import(context, file)
            scope.launch {
                received++
                message = "Got it! Your photo is now the wallpaper. Send another, or press Back."
                onImported(id)
            }
        }
        scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { server.start() }.getOrNull() }
            state = if (url != null) PhoneState.Ready(url) else PhoneState.NoNetwork
            if (url != null) {
                delay(PHONE_TIMEOUT_MS)
                server.stop()
                state = PhoneState.TimedOut
            }
        }
        onDispose { server.stop() }
    }

    Scrim(alpha = 0.55f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassPanel(
                modifier = Modifier.width(640.dp),
                contentPadding = PaddingValues(28.dp),
            ) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Send from phone", style = style(24, FontWeight.Light))
                        Spacer(Modifier.height(10.dp))
                        when (val s = state) {
                            PhoneState.Starting -> Text("Starting…", style = style(14, FontWeight.Normal, alpha = 0.8f))
                            is PhoneState.Ready -> {
                                Step("1", "Connect your phone to the same Wi-Fi or network as the TV.")
                                Step("2", "Scan the code with your phone's camera, or open this address:")
                                Text(
                                    s.url,
                                    style = style(14, FontWeight.Medium, color = Accent),
                                    modifier = Modifier.padding(start = 30.dp, bottom = 8.dp),
                                )
                                Step("3", "Pick a photo and tap Send to TV.")
                            }
                            PhoneState.NoNetwork -> Text(
                                "The TV doesn't seem to be on a network. Connect it to Wi-Fi or Ethernet and try again.",
                                style = style(14, FontWeight.Normal, alpha = 0.85f),
                            )
                            PhoneState.TimedOut -> Text(
                                "This closed after 10 minutes to keep your TV private. Press Back and choose Send from phone again.",
                                style = style(14, FontWeight.Normal, alpha = 0.85f),
                            )
                        }
                        message?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = style(14, FontWeight.Medium, color = Accent))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Only works while this screen is open. Photos are resized to 4K and stored on the TV.",
                            style = style(11, FontWeight.Normal, alpha = 0.6f),
                        )
                        Spacer(Modifier.height(14.dp))
                        PillButton(if (received > 0) "Done" else "Cancel", onClick = onClose, modifier = Modifier.focusRequester(closeFocus))
                    }
                    (state as? PhoneState.Ready)?.let {
                        Spacer(Modifier.width(22.dp))
                        QrCode(it.url, Modifier.size(196.dp))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { closeFocus.requestFocus() }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.padding(bottom = 8.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number, style = style(11, FontWeight.Medium)) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = style(14, FontWeight.Normal, alpha = 0.9f))
    }
}

// ---------------------------------------------------------------- From USB / local storage

private fun storagePermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Image files on USB drives / SD cards, in Download and in Pictures; newest first. Call off the main thread. */
private fun findImages(context: Context): List<File> {
    val roots = LinkedHashSet<File>()
    // Removable drives are mounted as /storage/<id>; "emulated" and "self" are the TV's own storage.
    File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let(roots::addAll)
    if (Build.VERSION.SDK_INT >= 30) {
        context.getSystemService(StorageManager::class.java)?.storageVolumes
            ?.filter { it.isRemovable }
            ?.mapNotNull { it.directory }
            ?.let(roots::addAll)
    }
    roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    val found = ArrayList<File>()
    fun walk(dir: File, depth: Int) {
        if (found.size >= 300 || depth > 4) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (found.size >= 300) return
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (f.name != "Android" && f.name != "LOST.DIR") walk(f, depth + 1)
            } else if (f.extension.lowercase() in IMAGE_EXTENSIONS && f.length() > 0) {
                found += f
            }
        }
    }
    roots.filter { it.isDirectory }.forEach { walk(it, 0) }
    return found.distinctBy { it.canonicalPath }.sortedByDescending { it.lastModified() }
}

private sealed interface UsbState {
    data object NeedPermission : UsbState
    data object Loading : UsbState
    data class Loaded(val files: List<File>) : UsbState
    data object Copying : UsbState
    data class Failed(val message: String) : UsbState
}

@Composable
private fun UsbBrowser(onImported: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = remember { storagePermission() }
    fun granted() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var state by remember { mutableStateOf(if (granted()) UsbState.Loading else UsbState.NeedPermission) }
    val firstFocus = remember { FocusRequester() }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state = if (ok) UsbState.Loading else UsbState.Failed("Nyumbani needs permission to see your photos. You can allow it in Android TV settings → Apps → Nyumbani → Permissions.")
    }
    BackHandler(onBack = onClose)

    LaunchedEffect(state == UsbState.Loading) {
        if (state == UsbState.Loading) {
            state = UsbState.Loaded(withContext(Dispatchers.IO) { runCatching { findImages(context) }.getOrDefault(emptyList()) })
        }
    }

    fun pick(file: File) {
        state = UsbState.Copying
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Wallpaper.import(context, file) } }
            result.fold(
                onSuccess = onImported,
                onFailure = { state = UsbState.Failed(it.message ?: "That image couldn't be used.") },
            )
        }
    }

    // Nearly opaque: this is a full screen of its own, with its title where the greeting sits.
    Scrim(alpha = 0.95f) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = SafeH, vertical = SafeV)
        ) {
            Text("From USB or this TV", style = style(24, FontWeight.Light))
            Spacer(Modifier.height(4.dp))
            Text("Images on USB drives, in Download and in Pictures.", style = style(13, FontWeight.Normal, alpha = 0.75f))
            Spacer(Modifier.height(18.dp))
            when (val s = state) {
                UsbState.NeedPermission -> {
                    Text("To list your images, Nyumbani needs permission to read photos.", style = style(14, FontWeight.Normal))
                    Spacer(Modifier.height(12.dp))
                    PillButton("Allow", onClick = { askPermission.launch(permission) }, modifier = Modifier.focusRequester(firstFocus))
                }
                UsbState.Loading -> Text("Looking for images…", style = style(14, FontWeight.Normal, alpha = 0.8f))
                UsbState.Copying -> Text("Copying…", style = style(14, FontWeight.Normal, alpha = 0.8f))
                is UsbState.Failed -> {
                    Text(s.message, style = style(14, FontWeight.Normal, color = Accent))
                    Spacer(Modifier.height(12.dp))
                    PillButton("Back", onClick = onClose, modifier = Modifier.focusRequester(firstFocus))
                }
                is UsbState.Loaded -> if (s.files.isEmpty()) {
                    Text(
                        "No JPEG, PNG or WebP images found. Plug in a USB drive with photos, or use Send from phone.",
                        style = style(14, FontWeight.Normal),
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton("Back", onClick = onClose, modifier = Modifier.focusRequester(firstFocus))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = SafeV, start = 4.dp, end = 4.dp),
                    ) {
                        itemsIndexed(s.files, key = { _, f -> f.path }) { index, file ->
                            FileTile(file, onClick = { pick(file) }, modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(state::class) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun FileTile(file: File, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val thumb by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { Wallpaper.thumbnail(file) }
    }
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Column {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.16f),
            ),
        ) {
            thumb?.let { Image(bitmap = it, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(shape)) }
        }
        Text(
            file.name,
            style = style(11, if (focused) FontWeight.SemiBold else FontWeight.Normal, alpha = if (focused) 1f else 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}
