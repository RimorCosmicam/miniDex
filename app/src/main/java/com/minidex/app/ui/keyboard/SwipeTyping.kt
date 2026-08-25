package com.minidex.app.ui.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

private val swipeDictionary = """
the be to of and a in that have i it for not on with he as you do at this but his by from
they we say her she or an will my one all would there their what so up out if about who get
which go me when make can like time no just him know take people into year your good some
could them see other than then now look only come its over think also back after use two how
our work first well way even new want because these give day most us is are was were been
being am has had did does done should would could may might must shall very much many more
here where why while home hello hi thanks thank please yes okay ok sorry love need help open
close start stop save send move click right left down next previous again app chrome gallery
keyboard mouse screen display window settings theme color background photo image gif crop
coffee today tomorrow tonight morning night phone cover dex samsung air type typing swipe
word words message text email browser search enter space delete return escape control shift
alt tab copy paste cut undo redo select find play pause volume mute page file folder download
upload connect connected wireless quick easy auto default standard floating small large high
low fast slow smooth perfect really still works working test try trying fix fixed change
make made keep remove add show hide visible invisible inside outside before after under above
through around every each any another same different first last little long press hold tap
finger fingers scroll scrolling button buttons cursor pointer window windows
""".trimIndent().split(Regex("\\s+")).distinct()

private fun signature(value: String): String = buildString {
    value.lowercase().forEach { char ->
        if (char in 'a'..'z' && (isEmpty() || last() != char)) append(char)
    }
}

private fun editDistance(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1
            )
        }
        previous = current
    }
    return previous[b.length]
}

private fun decodeSwipe(path: String): String {
    val shape = signature(path)
    if (shape.length < 2) return shape
    return swipeDictionary
        .asSequence()
        .filter { it.first() == shape.first() && it.last() == shape.last() }
        .minByOrNull { word ->
            val wordShape = signature(word)
            editDistance(shape, wordShape) * 4 + abs(word.length - shape.length)
        }
        ?: shape
}

private fun keyAt(position: Offset, width: Float, height: Float): Char? {
    if (width <= 0f || height <= 0f) return null
    val rowHeight = height / 4f
    return when (floor(position.y / rowHeight).toInt()) {
        0 -> "qwertyuiop"[(position.x / width * 10f).toInt().coerceIn(0, 9)]
        1 -> {
            val index = floor(position.x / width * 10f - 0.5f).toInt()
            "asdfghjkl".getOrNull(index)
        }
        2 -> {
            val index = floor(position.x / width * 10f - 1.5f).toInt()
            "zxcvbnm".getOrNull(index)
        }
        else -> null
    }
}

fun Modifier.swipeTyping(onWord: (String) -> Unit): Modifier = pointerInput(onWord) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var lastPosition = down.position
        var distance = 0f
        var swiping = false
        val path = StringBuilder()
        keyAt(down.position, size.width.toFloat(), size.height.toFloat())?.let(path::append)

        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val position = change.position
            distance += hypot(position.x - lastPosition.x, position.y - lastPosition.y)
            if (distance > 28f) swiping = true
            if (swiping) {
                change.consume()
                keyAt(position, size.width.toFloat(), size.height.toFloat())?.let { key ->
                    if (path.isEmpty() || path.last() != key) path.append(key)
                }
            }
            lastPosition = position
            pressed = event.changes.any { it.pressed }
        }

        if (swiping && path.length >= 2) onWord(decodeSwipe(path.toString()))
    }
}
