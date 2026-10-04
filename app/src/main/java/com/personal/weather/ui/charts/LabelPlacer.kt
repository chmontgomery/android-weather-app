package com.personal.weather.ui.charts

import kotlin.math.hypot

/**
 * Chooses where value labels go so none of them covers a line, a point dot, another label, or leaves the chart.
 * Pure geometry in pixels (y grows downward), kept out of Canvas code so it can be tested.
 */
object LabelPlacer {
    data class Pt(val x: Float, val y: Float)

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    /** A label of [width] x [height] that belongs to the point [anchor]. */
    data class Request(val anchor: Pt, val width: Float, val height: Float)

    /**
     * For each request, in order, the top-left corner of the first candidate spot that is clear, or null when no
     * spot is clear (the label is skipped rather than drawn over a line). [lines] are contiguous polylines; [pad]
     * keeps labels a little further from lines to allow for stroke width.
     */
    fun place(
        requests: List<Request>,
        lines: List<List<Pt>>,
        dots: List<Pt>,
        dotRadius: Float,
        gap: Float,
        bounds: Box,
        pad: Float = 0f,
    ): List<Pt?> {
        val segments = lines.flatMap { it.zipWithNext() }
        val placed = mutableListOf<Box>()
        return requests.map { r ->
            candidates(r, dotRadius + gap)
                .map { Pt(it.x.coerceAtMost(bounds.right - r.width).coerceAtLeast(bounds.left), it.y) }
                .firstOrNull { tl ->
                    val box = Box(tl.x, tl.y, tl.x + r.width, tl.y + r.height)
                    val padded = Box(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
                    box.left >= bounds.left && box.right <= bounds.right && box.top >= bounds.top && box.bottom <= bounds.bottom &&
                        segments.none { (p, q) -> segmentHits(p, q, padded) } &&
                        dots.none { dotHits(it, dotRadius, box) } &&
                        placed.none { it.intersects(box) }
                }
                ?.also { placed += Box(it.x, it.y, it.x + r.width, it.y + r.height) }
        }
    }

    /** Above, then below, the point (centred, then shifted either way), then further out, then beside it. */
    private fun candidates(r: Request, clearance: Float): List<Pt> {
        val (x, y) = r.anchor
        val w = r.width
        val h = r.height
        val centred = x - w / 2
        val xs = listOf(centred, centred - w * 0.75f, centred + w * 0.75f)
        val ys = listOf(y - clearance - h, y + clearance, y - clearance - 2 * h, y + clearance + h)
        return ys.flatMap { top -> xs.map { left -> Pt(left, top) } } +
            listOf(Pt(x + clearance, y - h / 2), Pt(x - clearance - w, y - h / 2))
    }

    /** Liang–Barsky clip: does segment p→q touch the box? */
    private fun segmentHits(p: Pt, q: Pt, b: Box): Boolean {
        var t0 = 0f
        var t1 = 1f
        val dx = q.x - p.x
        val dy = q.y - p.y
        val ps = floatArrayOf(-dx, dx, -dy, dy)
        val qs = floatArrayOf(p.x - b.left, b.right - p.x, p.y - b.top, b.bottom - p.y)
        for (i in 0..3) {
            if (ps[i] == 0f) {
                if (qs[i] < 0f) return false
            } else {
                val t = qs[i] / ps[i]
                if (ps[i] < 0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
            }
        }
        return true
    }

    private fun dotHits(c: Pt, r: Float, b: Box): Boolean =
        hypot(c.x - c.x.coerceIn(b.left, b.right), c.y - c.y.coerceIn(b.top, b.bottom)) < r
}
