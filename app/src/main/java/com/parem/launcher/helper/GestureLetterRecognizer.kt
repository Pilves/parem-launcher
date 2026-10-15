package com.parem.launcher.helper

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Single-stroke shape matching, independent of Android, touch density and drawing speed. */
object GestureLetterRecognizer {
    data class Point(val x: Double, val y: Double)

    private const val SAMPLE_COUNT = 64
    // Distances are fractions of the longest side of the drawing's bounding box.
    private const val MAX_DISTANCE = 0.14
    private const val MIN_MARGIN = 0.025

    private fun path(vararg coordinates: Double) = coordinates.toList().chunked(2).map { Point(it[0], it[1]) }
    private fun arc(from: Double, to: Double) = (0..32).map {
        val angle = Math.toRadians(from + (to - from) * it / 32)
        Point(0.5 + 0.5 * cos(angle), 0.5 + 0.5 * sin(angle))
    }

    private val templates = listOf(
        'A' to path(0.0, 1.0, 0.5, 0.0, 1.0, 1.0), // existing one-stroke caret A
        'A' to path(0.0, 1.0, 0.5, 0.0, 1.0, 1.0, 0.8, 0.6, 0.2, 0.6),
        'C' to arc(-45.0, -315.0),
        'C' to path(1.0, 0.0, 0.25, 0.0, 0.0, 0.25, 0.0, 0.75, 0.25, 1.0, 1.0, 1.0),
        'L' to path(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
        'M' to path(0.0, 1.0, 0.0, 0.0, 0.5, 0.65, 1.0, 0.0, 1.0, 1.0),
        'M' to path(0.0, 1.0, 0.25, 0.0, 0.5, 0.7, 0.75, 0.0, 1.0, 1.0),
        'N' to path(0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0),
        'O' to arc(0.0, 360.0),
        'S' to path(1.0, 0.1, 0.75, 0.0, 0.25, 0.0, 0.0, 0.2, 0.1, 0.4,
            0.4, 0.5, 0.8, 0.6, 1.0, 0.8, 0.75, 1.0, 0.25, 1.0, 0.0, 0.9),
        'V' to path(0.0, 0.0, 0.5, 1.0, 1.0, 0.0),
        'W' to path(0.0, 0.0, 0.25, 1.0, 0.5, 0.3, 0.75, 1.0, 1.0, 0.0),
        'Z' to path(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0),
    ).map { (letter, points) -> letter to normalize(points)!! }

    /** Rejects swipes, degenerate strokes and ambiguous matches rather than guessing an app. */
    fun recognize(points: List<Point>): Char? {
        if (points.size < 3 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        val size = maxOf(width, height)
        if (size <= 0.0 || min(width, height) / size < 0.18) return null
        val length = points.zipWithNext().sumOf { (a, b) -> distance(a, b) }
        // A diagonal swipe spans both axes too. Its path barely exceeds endpoint distance.
        if (length < distance(points.first(), points.last()) * 1.15) return null
        val normalized = normalize(points) ?: return null
        val scores = mutableMapOf<Char, Double>()
        for (degrees in listOf(-15.0, -7.5, 0.0, 7.5, 15.0)) {
            val angle = Math.toRadians(degrees)
            val rotated = normalized.map { Point(it.x * cos(angle) - it.y * sin(angle), it.x * sin(angle) + it.y * cos(angle)) }
            for ((letter, template) in templates) {
                if (letter == 'O' && distance(points.first(), points.last()) > size * 0.3) continue
                val score = if (letter == 'O') {
                    // A loop can start anywhere; other letters retain their endpoints.
                    (0 until SAMPLE_COUNT step 4).minOf { offset ->
                        min(pathDistance(rotated, template, offset), pathDistance(rotated.asReversed(), template, offset))
                    }
                } else min(pathDistance(rotated, template), pathDistance(rotated.asReversed(), template))
                scores[letter] = min(scores[letter] ?: Double.POSITIVE_INFINITY, score)
            }
        }
        val ranked = scores.entries.sortedBy { it.value }
        val best = ranked.firstOrNull() ?: return null
        if (best.value > MAX_DISTANCE || ranked[1].value - best.value < MIN_MARGIN) return null
        return best.key
    }

    private fun pathDistance(a: List<Point>, b: List<Point>, offset: Int = 0): Double =
        a.indices.sumOf { distance(a[it], b[(it + offset) % SAMPLE_COUNT]) } / SAMPLE_COUNT

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    // Equal arc-length samples, interpolated rather than dropping the leftover segment.
    // Reference: https://depts.washington.edu/acelab/proj/dollar/
    // Shape matching follows the $1 family's resample/normalize approach, but keeps the
    // letter upright (only +/-15 degrees) so M/W and N/Z don't become interchangeable.
    private fun normalize(points: List<Point>): List<Point>? {
        val lengths = points.zipWithNext().map { (a, b) -> distance(a, b) }
        val total = lengths.sum()
        if (total <= 0.0 || !total.isFinite()) return null
        val sampled = mutableListOf<Point>()
        var segment = 0
        var traversed = 0.0
        for (i in 0 until SAMPLE_COUNT) {
            val target = total * i / (SAMPLE_COUNT - 1)
            while (segment < lengths.lastIndex && traversed + lengths[segment] < target) {
                traversed += lengths[segment++]
            }
            val ratio = if (lengths[segment] == 0.0) 0.0 else ((target - traversed) / lengths[segment]).coerceIn(0.0, 1.0)
            val a = points[segment]
            val b = points[segment + 1]
            sampled += Point(a.x + (b.x - a.x) * ratio, a.y + (b.y - a.y) * ratio)
        }
        val scale = maxOf(sampled.maxOf { it.x } - sampled.minOf { it.x }, sampled.maxOf { it.y } - sampled.minOf { it.y })
        if (scale <= 0.0) return null
        val cx = sampled.sumOf { it.x } / SAMPLE_COUNT
        val cy = sampled.sumOf { it.y } / SAMPLE_COUNT
        return sampled.map { Point((it.x - cx) / scale, (it.y - cy) / scale) }
    }
}
