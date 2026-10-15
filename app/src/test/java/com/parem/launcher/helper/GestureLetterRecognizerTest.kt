package com.parem.launcher.helper

import com.parem.launcher.helper.GestureLetterRecognizer.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GestureLetterRecognizerTest {
    private fun stroke(vararg xy: Int) = xy.toList().chunked(2).map { Point(it[0].toDouble(), it[1].toDouble()) }
    // Independently drawn fixtures, deliberately unevenly sampled and imperfect.
    private val letters = mapOf(
        'A' to stroke(12, 190, 38, 136, 91, 10, 131, 79, 185, 190),
        'C' to stroke(173, 28, 136, 11, 66, 19, 22, 56, 10, 105, 22, 155, 65, 188, 126, 191, 181, 165),
        'L' to stroke(15, 11, 19, 78, 13, 177, 23, 189, 78, 192, 171, 186),
        'M' to stroke(10, 188, 13, 14, 91, 132, 175, 15, 181, 190),
        'N' to stroke(10, 190, 16, 12, 64, 68, 172, 188, 177, 12),
        'S' to stroke(178, 25, 147, 10, 58, 15, 14, 47, 21, 76, 67, 99, 143, 122, 178, 157, 145, 185, 51, 192, 12, 168),
        'V' to stroke(12, 10, 39, 69, 87, 184, 102, 187, 147, 76, 180, 12),
        'W' to stroke(10, 12, 52, 187, 93, 69, 138, 190, 179, 16),
        'Z' to stroke(13, 13, 89, 16, 183, 11, 107, 98, 13, 182, 89, 186, 180, 182),
    )

    @Test fun handwrittenFixturesIgnoreSizePositionAndStrokeDirection() {
        for ((letter, points) in letters) {
            for (scale in listOf(0.3, 1.0, 4.0)) {
                val transformed = points.map { Point(it.x * scale + 420, it.y * scale - 73) }
                assertEquals("$letter at $scale", letter, GestureLetterRecognizer.recognize(transformed))
                assertEquals("$letter reversed", letter, GestureLetterRecognizer.recognize(transformed.reversed()))
            }
        }
    }

    @Test fun slightlySlantedAndDenseStrokesMatch() {
        val angle = Math.toRadians(12.0)
        for ((letter, points) in letters) {
            val dense = points.zipWithNext().flatMap { (a, b) ->
                (0..24).map { i ->
                    val t = i / 24.0
                    val x = a.x + (b.x - a.x) * t
                    val y = a.y + (b.y - a.y) * t
                    Point(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))
                }
            }
            assertEquals("slanted $letter", letter, GestureLetterRecognizer.recognize(dense))
        }
    }

    @Test fun ovalCanStartAnywhereInEitherDirection() {
        for (start in listOf(0.0, 37.0, 91.0, 213.0)) {
            val oval = (0..80).map {
                val a = Math.toRadians(start + it * 4.5)
                Point(80 * cos(a), 100 * sin(a))
            }
            assertEquals('O', GestureLetterRecognizer.recognize(oval))
            assertEquals('O', GestureLetterRecognizer.recognize(oval.reversed()))
        }
    }

    @Test fun rejectsSwipesDotsBacktracksAndScribbles() {
        for (points in listOf(
            emptyList(), List(30) { Point(5.0, 5.0) },
            stroke(0, 0, 40, 41, 100, 100), stroke(0, 0, 2, 80, 0, 200),
            stroke(0, 0, 100, 100, 0, 0),
            stroke(0, 0, 0, 80, 20, 100, 80, 100, 100, 80, 100, 0),
            stroke(0, 0, 100, 100, 0, 100, 100, 0, 0, 0, 50, 100, 100, 50),
            listOf(Point(Double.NaN, 0.0), Point(1.0, 1.0), Point(2.0, 0.0))
        )) assertNull("Rejected $points", GestureLetterRecognizer.recognize(points))
    }
}
