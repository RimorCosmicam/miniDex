package com.minidex.app.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.domain.model.Macro
import com.minidex.app.ui.theme.LocalMiniDexColors

@Composable
fun MacropadView(
    macros: List<Macro>,
    onExecuteMacro: (Macro) -> Unit,
    onAddMacroClick: () -> Unit,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current

    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(macros, key = { it.id }) { macro ->
                MacroGridItem(
                    macro = macro,
                    cornerRadius = cornerRadius,
                    onTap = { onExecuteMacro(macro) }
                )
            }

            // Add Custom Macro Button
            item {
                val shape = RoundedCornerShape(cornerRadius)
                Box(
                    modifier = Modifier
                        .height(54.dp)
                        .clip(shape)
                        .background(colors.surfaceElevated, shape)
                        .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
                        .clickable { onAddMacroClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Macro",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "New Macro",
                            color = colors.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MacroGridItem(
    macro: Macro,
    cornerRadius: Dp,
    onTap: () -> Unit
) {
    val colors = LocalMiniDexColors.current
    val shape = RoundedCornerShape(cornerRadius)

    val itemAccentColor = try {
        Color(android.graphics.Color.parseColor(macro.colorHex))
    } catch (e: Exception) {
        colors.accent
    }

    val iconVector = when (macro.iconName) {
        "content_copy" -> Icons.Default.ContentCopy
        "content_paste" -> Icons.Default.ContentPaste
        "undo" -> Icons.Default.Undo
        "monitor_heart" -> Icons.Default.MonitorHeart
        "terminal" -> Icons.Default.Terminal
        "screenshot" -> Icons.Default.Screenshot
        "desktop_windows" -> Icons.Default.DesktopWindows
        "close" -> Icons.Default.Close
        "tab" -> Icons.Default.Tab
        else -> Icons.Default.Keyboard
    }

    Box(
        modifier = Modifier
            .height(54.dp)
            .clip(shape)
            .background(colors.keyBackground, shape)
            .border(1.dp, itemAccentColor.copy(alpha = 0.5f), shape)
            .clickable { onTap() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = macro.name,
                    tint = itemAccentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = macro.name,
                    color = colors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (macro.description.isNotEmpty()) {
                Text(
                    text = macro.description,
                    color = colors.textSecondary,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    lineHeight = 8.sp
                )
            }
        }
    }
}
