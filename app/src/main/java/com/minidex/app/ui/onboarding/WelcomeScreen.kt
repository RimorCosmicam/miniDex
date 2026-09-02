package com.minidex.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.ui.components.DiagonalStripes
import com.minidex.app.ui.theme.MONT_SURFACE_ALPHA
import com.minidex.app.ui.theme.MontMustard
import com.minidex.app.ui.theme.Mont

/** One thing the app needs, and whether it has it yet. */
data class PermissionItem(
    val label: String,
    val detail: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
    /**
     * A second way in, shown beside the first. Wireless ADB uses it once this device has paired
     * before: the key is still on disk, so reconnecting needs no code at all.
     */
    val shortcutLabel: String? = null,
    val onShortcut: (() -> Unit)? = null,
    /** The shortcut was tried and did not work. Shown in red, because nothing else would say so. */
    val shortcutFailed: Boolean = false
)

/** The welcome's ground: mustard on black. Onboarding is the poster colour's one job. */
@Composable
private fun MustardDiagonals(
    travel: Float,
    split: Float,
    modifier: Modifier = Modifier
) {
    DiagonalStripes(
        travel = travel,
        first = MontMustard,
        second = Color.Black,
        split = split,
        modifier = modifier
    )
}

/**
 * The first run, as one card.
 *
 * Drivers, then the switcher, then the ground parting to reveal the app already running behind it.
 * The card is a single surface throughout: it changes what it holds and resizes to fit, rather
 * than one screen vanishing and another taking its place — the box is the thing being followed, so
 * it should never be the thing that blinks.
 */
@Composable
fun Welcome(
    permissions: List<PermissionItem>,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "welcome")
    val travel by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "stripes"
    )

    var showingSwitcher by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // Quick. The app behind this is already up and running, so the transition is the only thing
    // standing between the reader and it.
    val journey by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "journey",
        finishedListener = { if (it == 1f) onFinished() }
    )

    Box(modifier.fillMaxSize()) {
        MustardDiagonals(travel, journey, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-46).dp)
                .padding(horizontal = 18.dp)
                // The card goes first, and faster than the ground it is standing on.
                .alpha(1f - (journey / 0.22f).coerceAtMost(1f))
                .background(Color.Black.copy(alpha = MONT_SURFACE_ALPHA))
                .padding(start = 22.dp, top = 22.dp, end = 18.dp, bottom = 16.dp)
        ) {
            // mini is Thin and Dex is Black at the same size. That contrast is the logo.
            Text(
                "mini",
                color = Color.White,
                fontFamily = Mont,
                fontWeight = FontWeight.Thin,
                fontSize = 36.sp
            )
            Text(
                "Dex",
                color = Color.White,
                fontFamily = Mont,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp
            )

            Spacer(Modifier.height(14.dp))

            // The card keeps its place and its heading; only what sits under them is exchanged,
            // and the box grows or shrinks to whatever that needs.
            AnimatedContent(
                targetState = showingSwitcher,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 120)) togetherWith fadeOut(tween(140)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(360, easing = FastOutSlowInEasing) })
                },
                label = "welcomeStep"
            ) { switcher ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (switcher) {
                        SwitcherStep(onOkay = { leaving = true })
                    } else {
                        DriversStep(permissions = permissions, onDone = { showingSwitcher = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DriversStep(
    permissions: List<PermissionItem>,
    onDone: () -> Unit
) {
    permissions.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !item.granted) { item.onGrant() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Label(item.label.uppercase(), if (item.granted) 1f else 0.55f, 12)
                Detail(item.detail)
            }
            if (!item.granted && item.shortcutLabel != null && item.onShortcut != null) {
                Text(
                    item.shortcutLabel.uppercase(),
                    modifier = Modifier
                        .clickable(onClick = item.onShortcut)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = if (item.shortcutFailed) Color(0xFFC0392B) else Color.White,
                    fontFamily = Mont,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
            Label(if (item.granted) "ON" else "SET UP", if (item.granted) 0.55f else 1f, 11)
        }
    }

    // Text alone, like every other commitment in this app. Dim until there is nothing left to
    // grant, so it reads as the end of the list rather than a way past it.
    val everythingGranted = permissions.all { it.granted }
    Text(
        "ALL DONE",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = everythingGranted, onClick = onDone)
            .padding(vertical = 6.dp),
        color = Color.White.copy(alpha = if (everythingGranted) 1f else 0.30f),
        fontFamily = Mont,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp
    )
}

@Composable
private fun ColumnScope.SwitcherStep(onOkay: () -> Unit) {
    Label("THE PILL", 0.55f, 11)
    FakePill()
    TourLine("ONE TAP", "Keyboard or touchpad")
    TourLine("TWO TAPS", "AMOLED black")
    TourLine("HOLD", "Open settings")
    Spacer(Modifier.height(4.dp))
    Text(
        "OKAY",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOkay).padding(vertical = 6.dp),
        color = Color.White,
        fontFamily = Mont,
        fontWeight = FontWeight.Black,
        fontSize = 15.sp
    )
}

/**
 * A stand-in for the pill, drawn rather than borrowed. The real one is already in place behind all
 * of this, so the card only ever needed to show what one looks like.
 */
@Composable
private fun FakePill() {
    Row(
        Modifier
            .background(Color.Black.copy(alpha = MONT_SURFACE_ALPHA))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "9:41",
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "PM",
            color = Color(0xFFFF69B4),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "84%",
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(5.dp).background(Color(0xFF2E9E5B)))
    }
}

@Composable
private fun TourLine(gesture: String, meaning: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            gesture,
            modifier = Modifier.weight(0.42f),
            color = Color.White,
            fontFamily = Mont,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp
        )
        Text(
            meaning,
            modifier = Modifier.weight(0.58f),
            color = Color.White.copy(alpha = 0.62f),
            fontFamily = Mont,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun Label(text: String, alpha: Float, size: Int) {
    Text(
        text,
        color = Color.White.copy(alpha = alpha),
        fontFamily = Mont,
        fontWeight = FontWeight.Black,
        fontSize = size.sp
    )
}

@Composable
private fun Detail(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.42f),
        fontFamily = Mont,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp
    )
}
