package com.minidex.app.ui.touchpad

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minidex.app.data.UserPreferences
import com.minidex.app.ui.theme.LocalMiniDexColors
import kotlinx.coroutines.launch
import kotlin.math.hypot

data class TouchRipple(
    val x: Float,
    val y: Float,
    val startTime: Long,
    val color: Color
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadView(
    userPreferences: UserPreferences,
    onPointerMove: (Float, Float) -> Unit,
    onPointerDown: (Int) -> Unit,
    onPointerUp: (Int) -> Unit,
    onPointerClick: (Int) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onHapticClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMiniDexColors.current
    val scope = rememberCoroutineScope()

    var lastTouchX by remember { mutableFloatStateOf(0f) }
    var lastTouchY by remember { mutableFloatStateOf(0f) }
    var touchDownTime by remember { mutableLongStateOf(0L) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var totalMovedDistance by remember { mutableFloatStateOf(0f) }
    var pointerCount by remember { mutableIntStateOf(0) }
    var maxPointerCount by remember { mutableIntStateOf(0) }
    var multiTapHandled by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isEdgeScrolling by remember { mutableStateOf(false) }
    var rightClickPressed by remember { mutableStateOf(false) }
    var rightClickDownTime by remember { mutableLongStateOf(0L) }
    var rightClickDownX by remember { mutableFloatStateOf(0f) }
    var rightClickDownY by remember { mutableFloatStateOf(0f) }
    var rightClickHotspotSizePx by remember { mutableIntStateOf(0) }
    var touchpadWidthPx by remember { mutableIntStateOf(0) }
    val edgeWidthPx = with(LocalDensity.current) { 48.dp.toPx() }

    // Visual touch indicator ripples
    val ripples = remember { mutableStateListOf<TouchRipple>() }

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceElevated.copy(alpha = 0.42f),
                        colors.background.copy(alpha = 0.78f)
                    )
                ),
                shape
            )
            .border(1.dp, colors.border.copy(alpha = 0.4f), shape)
            .onSizeChanged { touchpadWidthPx = it.width }
            .pointerInteropFilter { motionEvent ->
                val now = SystemClock.uptimeMillis()
                pointerCount = motionEvent.pointerCount

                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = motionEvent.x
                        lastTouchY = motionEvent.y
                        touchDownTime = now
                        totalMovedDistance = 0f
                        maxPointerCount = 1
                        multiTapHandled = false
                        isEdgeScrolling = if (userPreferences.edgeScrollOnRight) {
                            motionEvent.x >= touchpadWidthPx - edgeWidthPx
                        } else {
                            motionEvent.x <= edgeWidthPx
                        }

                        // Check for Double-tap-and-drag
                        val timeSinceLastTap = now - lastTapTime
                        if (!isEdgeScrolling && timeSinceLastTap < 280L) {
                            isDragging = true
                            onPointerDown(1) // Hold Left Button for drag
                            onHapticClick()
                        }

                        ripples.add(TouchRipple(motionEvent.x, motionEvent.y, now, colors.accent))
                        if (ripples.size > 8) ripples.removeAt(0)
                        true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        maxPointerCount = maxOf(maxPointerCount, motionEvent.pointerCount)
                        // Multi-touch started (e.g. 2 fingers)
                        if (motionEvent.pointerCount == 2) {
                            isEdgeScrolling = false
                            lastTouchX = (motionEvent.getX(0) + motionEvent.getX(1)) / 2f
                            lastTouchY = (motionEvent.getY(0) + motionEvent.getY(1)) / 2f
                            touchDownTime = now
                            totalMovedDistance = 0f
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (motionEvent.pointerCount == 1) {
                            val currentX = motionEvent.x
                            val currentY = motionEvent.y
                            val rawDx = currentX - lastTouchX
                            val rawDy = currentY - lastTouchY

                            totalMovedDistance += hypot(rawDx, rawDy)

                            if (isEdgeScrolling) {
                                val (scrollX, scrollY) = TouchpadKinematics.calculateScrollDelta(
                                    rawDx = 0f,
                                    rawDy = rawDy,
                                    scrollSensitivity = userPreferences.scrollSensitivity,
                                    naturalScrolling = userPreferences.naturalScrolling
                                )
                                onScroll(scrollX, scrollY)
                            } else {
                                val (dx, dy) = TouchpadKinematics.calculatePointerDelta(
                                    rawDx = rawDx,
                                    rawDy = rawDy,
                                    sensitivity = userPreferences.pointerSensitivity,
                                    acceleration = userPreferences.pointerAcceleration
                                )
                                onPointerMove(dx, dy)
                            }
                            lastTouchX = currentX
                            lastTouchY = currentY
                        } else if (motionEvent.pointerCount == 2) {
                            // Two-finger scrolling
                            val avgX = (motionEvent.getX(0) + motionEvent.getX(1)) / 2f
                            val avgY = (motionEvent.getY(0) + motionEvent.getY(1)) / 2f

                            val rawDx = avgX - lastTouchX
                            val rawDy = avgY - lastTouchY
                            totalMovedDistance += hypot(rawDx, rawDy)

                            val (scrollX, scrollY) = TouchpadKinematics.calculateScrollDelta(
                                rawDx = rawDx,
                                rawDy = rawDy,
                                scrollSensitivity = userPreferences.scrollSensitivity,
                                naturalScrolling = userPreferences.naturalScrolling
                            )

                            onScroll(scrollX, scrollY)
                            lastTouchX = avgX
                            lastTouchY = avgY
                        }
                        true
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        if (!multiTapHandled && totalMovedDistance < 20f && (now - touchDownTime) < 350L) {
                            if (maxPointerCount >= 3) {
                                onPointerClick(4) // Three-finger tap: Back
                                multiTapHandled = true
                                onHapticClick()
                            } else if (maxPointerCount == 2 && motionEvent.pointerCount == 2) {
                                onPointerClick(2) // Two-finger tap: Right Click
                                multiTapHandled = true
                                onHapticClick()
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val duration = now - touchDownTime
                        if (isEdgeScrolling) {
                            isEdgeScrolling = false
                        } else if (isDragging) {
                            isDragging = false
                            onPointerUp(1) // Release drag
                            onHapticClick()
                        } else if (
                            maxPointerCount == 1 &&
                            !multiTapHandled &&
                            totalMovedDistance < 15f &&
                            duration < 250L &&
                            userPreferences.tapToClick
                        ) {
                            // Single Tap -> Left Click
                            val timeSinceLastTap = now - lastTapTime
                            if (timeSinceLastTap < 280L) {
                                // Double tap
                                onPointerClick(1)
                                onHapticClick()
                            } else {
                                onPointerClick(1)
                                onHapticClick()
                            }
                            lastTapTime = now
                        }

                        // Clean up old ripples
                        ripples.removeAll { now - it.startTime > 600L }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isEdgeScrolling = false
                        if (isDragging) {
                            isDragging = false
                            onPointerUp(1)
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        // Quiet geometry gives the full-screen pad depth without instructional clutter.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = SystemClock.uptimeMillis()

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.accent.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.68f, size.height * 0.42f),
                    radius = size.maxDimension * 0.62f
                ),
                radius = size.maxDimension * 0.62f,
                center = Offset(size.width * 0.68f, size.height * 0.42f)
            )

            // Subtle center crosshair guide
            drawLine(
                color = colors.border.copy(alpha = 0.2f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1f
            )
            drawLine(
                color = colors.border.copy(alpha = 0.2f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1f
            )

            // Active ripples
            ripples.forEach { ripple ->
                val progress = ((now - ripple.startTime) / 450f).coerceIn(0f, 1f)
                val radius = 10f + (progress * 50f)
                val alpha = (1f - progress) * 0.4f
                drawCircle(
                    color = ripple.color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(ripple.x, ripple.y),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // A dedicated edge strip behaves like the physical scroll zones on old touchpads.
        Canvas(
            modifier = Modifier
                .align(if (userPreferences.edgeScrollOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .width(48.dp)
        ) {
            drawRect(colors.accent.copy(alpha = if (isEdgeScrolling) 0.16f else 0.06f))
            val lineX = if (userPreferences.edgeScrollOnRight) size.width - 3.dp.toPx() else 3.dp.toPx()
            drawLine(
                color = colors.accent.copy(alpha = if (isEdgeScrolling) 0.9f else 0.4f),
                start = Offset(lineX, 12.dp.toPx()),
                end = Offset(lineX, size.height - 12.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            val centerX = size.width / 2f
            val tickHalfWidth = 7.dp.toPx()
            var y = 32.dp.toPx()
            while (y < size.height - 24.dp.toPx()) {
                drawLine(
                    color = colors.accent.copy(alpha = if (isEdgeScrolling) 0.8f else 0.3f),
                    start = Offset(centerX - tickHalfWidth, y),
                    end = Offset(centerX + tickHalfWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += 22.dp.toPx()
            }
        }

        // Simple, reliable right-click target: tap inside the top-right quarter circle.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(84.dp)
                .onSizeChanged { rightClickHotspotSizePx = it.width }
                .pointerInteropFilter { event ->
                    val distanceFromCorner = hypot(
                        rightClickHotspotSizePx - event.x,
                        event.y
                    )
                    val insideRadius = distanceFromCorner <= rightClickHotspotSizePx

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (!insideRadius) return@pointerInteropFilter false
                            rightClickPressed = true
                            rightClickDownTime = SystemClock.uptimeMillis()
                            rightClickDownX = event.x
                            rightClickDownY = event.y
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!rightClickPressed) return@pointerInteropFilter false
                            rightClickPressed = insideRadius
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            if (!rightClickPressed) return@pointerInteropFilter false
                            val movement = hypot(
                                event.x - rightClickDownX,
                                event.y - rightClickDownY
                            )
                            val isTap = insideRadius &&
                                movement < 20f &&
                                SystemClock.uptimeMillis() - rightClickDownTime < 500L
                            rightClickPressed = false
                            if (isTap) {
                                onPointerClick(2)
                                onHapticClick()
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            rightClickPressed = false
                            true
                        }

                        else -> rightClickPressed
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = colors.accent.copy(alpha = if (rightClickPressed) 0.28f else 0.10f),
                    radius = size.minDimension,
                    center = Offset(size.width, 0f)
                )
                drawCircle(
                    color = colors.accent.copy(alpha = if (rightClickPressed) 0.9f else 0.45f),
                    radius = size.minDimension,
                    center = Offset(size.width, 0f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

    }
}
