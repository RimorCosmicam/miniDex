package com.minidex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.ui.theme.MontSurface
import com.minidex.app.ui.theme.Mont

/**
 * The studio chrome, set the same way as the command bar in miniMate.
 *
 * Everything the old chrome used to say with decoration — rounded cards, gradients, hairlines,
 * shadows, icon buttons in circles — the type says on its own. A studio is black, Mont Black,
 * white, and the selected thing is simply the bright one.
 */
private val StudioWeight = FontWeight.Black

/**
 * How far a top-anchored Mont surface holds off the top edge. The Flip's cover display is awkward
 * to reach at the very top — a case lips over it, and Samsung reserves a few pixels along the edge
 * against mis-taps — so a row placed hard against it takes two or three attempts to hit.
 */
val MONT_TOP_INSET = 44.dp

@Composable
fun StudioPanel(
    modifier: Modifier = Modifier,
    /**
     * Hard ceiling on how tall a studio may grow. A studio sits over the thing it is editing, and
     * what it contains changes as options are chosen, so without a bound its own content decides
     * whether it still fits on the display. It scrolls inside this instead.
     */
    maxHeight: Dp = 150.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            // Full width. A studio that stops three quarters of the way across leaves a strip of
            // dead screen beside every row it contains.
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .background(MontSurface)
            .padding(start = 22.dp, top = MONT_TOP_INSET, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // No header. A panel that opens over the thing it edits does not need to announce which
        // panel it is, and the row that used to say so was costing the first line of the list.
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content
        )
    }
}

/** A word, full width, tappable. Bright if it acts now, dim if it is secondary. */
@Composable
fun StudioRow(
    label: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    trailing: String? = null,
    trailingColor: Color? = null,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp, horizontal = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StudioLabel(label, Modifier.weight(1f), dim)
            if (trailing != null) {
                Text(
                    trailing,
                    color = trailingColor ?: Color.White.copy(alpha = 0.58f),
                    fontFamily = Mont,
                    fontWeight = StudioWeight,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
        if (detail != null) StudioDetail(detail)
    }
}

/**
 * A choice. No pill, no border, no fill: selected is bright, unselected is dim — the same rule the
 * rows follow, because a choice is a row that happens to sit beside others.
 */
@Composable
fun StudioChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    StudioLabel(
        label,
        modifier.clickable(onClick = onClick).padding(vertical = 5.dp, horizontal = 2.dp),
        dim = !selected
    )
}

@Composable
fun StudioLabel(
    text: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    size: Int = 14
) {
    Text(
        text,
        modifier = modifier,
        color = if (dim) Color.White.copy(alpha = 0.58f) else Color.White,
        fontFamily = Mont,
        fontWeight = StudioWeight,
        fontSize = size.sp,
        maxLines = 1
    )
}

/** The explanatory line under a row. */
@Composable
fun StudioDetail(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Color.White.copy(alpha = 0.62f),
        fontFamily = Mont,
        fontWeight = StudioWeight,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
}
