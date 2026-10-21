package dev.kharioki.nyumbani

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * Pick up to [LauncherConfig.MAX_DOCK_APPS] dock apps and put them in order. The dock apps come
 * first, in dock order, each with move up / move down; every other app follows alphabetically.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DockEditor(
    allApps: List<AppEntry>,
    dock: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    var message by remember { mutableStateOf<String?>(null) }
    val byPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    // Apps that were uninstalled since they were added just drop out.
    val current = dock.filter { it in byPackage }
    val ordered = current.mapNotNull { byPackage[it] } + allApps.filter { it.packageName !in current }
    val max = LauncherConfig.MAX_DOCK_APPS

    fun toggle(pkg: String) {
        message = null
        when {
            pkg in current && current.size == 1 -> message = "Keep at least one app in the dock."
            pkg in current -> onChange(current - pkg)
            current.size >= max -> message = "The dock holds up to $max apps. Remove one first."
            else -> onChange(current + pkg)
        }
    }

    fun move(pkg: String, by: Int) {
        val from = current.indexOf(pkg)
        val to = from + by
        if (from < 0 || to !in current.indices) return
        message = null
        onChange(current.toMutableList().apply { add(to, removeAt(from)) })
    }

    Column(modifier) {
        Text(
            message ?: "${current.size} of $max selected. Use the arrows to change the order.",
            style = style(13, FontWeight.Normal, alpha = 0.75f, color = if (message != null) Accent else Color.White),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(ordered, key = { _, app -> app.packageName }) { index, app ->
                val position = current.indexOf(app.packageName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ListRow(
                        title = app.label,
                        subtitle = if (position >= 0) "Dock position ${position + 1}" else null,
                        onClick = { toggle(app.packageName) },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                            // Without arrows of its own, Right would land on a neighbouring row's arrow.
                            .focusProperties { if (position < 0) right = FocusRequester.Cancel },
                        leading = { focused ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChoiceMark(selected = position >= 0, focused = focused, multiple = true)
                                Spacer(Modifier.width(12.dp))
                                AppIcon(app)
                            }
                        },
                    )
                    if (position >= 0) {
                        Spacer(Modifier.width(8.dp))
                        ArrowButton(Icons.Filled.KeyboardArrowUp, "Move up", enabled = position > 0) { move(app.packageName, -1) }
                        Spacer(Modifier.width(6.dp))
                        ArrowButton(Icons.Filled.KeyboardArrowDown, "Move down", enabled = position < current.size - 1) {
                            move(app.packageName, +1)
                        }
                    } else {
                        // Keeps every row the same width, so the list doesn't look ragged.
                        Spacer(Modifier.width(8.dp + 40.dp + 6.dp + 40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val image = app.icon ?: app.banner
    Box(Modifier.size(width = 30.dp, height = 30.dp).clip(RoundedCornerShape(7.dp))) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, enabled = enabled, shape = CircleShape, focusedScale = 1.1f, modifier = Modifier.size(40.dp)) { focused ->
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = ink(focused).copy(alpha = if (enabled) 1f else 0.3f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
