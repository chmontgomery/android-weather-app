package com.personal.weather.ui.charts

import com.personal.weather.ui.charts.LabelPlacer.Box
import com.personal.weather.ui.charts.LabelPlacer.Pt
import com.personal.weather.ui.charts.LabelPlacer.Request
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPlacerTest {
    private val bounds = Box(0f, 0f, 400f, 200f)
    private val w = 20f
    private val h = 12f
    private val dotR = 3f
    private val gap = 2f

    private fun place(requests: List<Request>, lines: List<List<Pt>>, dots: List<Pt> = lines.flatten()) =
        LabelPlacer.place(requests, lines, dots, dotR, gap, bounds)

    @Test fun flatLine_labelGoesDirectlyAbove() {
        val line = listOf(Pt(0f, 100f), Pt(100f, 100f), Pt(200f, 100f))
        val tl = place(listOf(Request(Pt(100f, 100f), w, h)), listOf(line)).single()!!
        assertEquals(90f, tl.x, 0.01f) // centred on the point
        assertTrue("above the line", tl.y + h <= 100f)
    }

    @Test fun lineRisingThroughAbovePosition_labelMovesElsewhereAndClearsIt() {
        // Steep line from below-left to above-right passes through the spot directly above (100,100).
        val line = listOf(Pt(90f, 160f), Pt(100f, 100f), Pt(110f, 40f))
        val tl = place(listOf(Request(Pt(100f, 100f), w, h)), listOf(line)).single()
        assertNotNull(tl)
        assertClear(Box(tl!!.x, tl.y, tl.x + w, tl.y + h), listOf(line), line)
    }

    @Test fun secondLabelAvoidsTheFirst() {
        val a = listOf(Pt(0f, 100f), Pt(400f, 100f))
        val b = listOf(Pt(0f, 101f), Pt(400f, 101f))
        val placed = place(listOf(Request(Pt(100f, 100f), w, h), Request(Pt(100f, 101f), w, h)), listOf(a, b))
        val boxes = placed.filterNotNull().map { Box(it.x, it.y, it.x + w, it.y + h) }
        assertEquals(2, boxes.size)
        assertFalse(boxes[0].intersects(boxes[1]))
    }

    @Test fun noRoomAnywhere_labelIsSkipped() {
        // A dense zigzag fills the whole chart, so every candidate spot crosses a line.
        val zigzag = (0..80).map { i -> Pt(i * 5f, if (i % 2 == 0) 0f else 200f) }
        assertNull(place(listOf(Request(Pt(200f, 100f), w, h)), listOf(zigzag)).single())
    }

    @Test fun randomCharts_placedLabelsNeverTouchLinesDotsOrEachOther() {
        val rnd = Random(42)
        repeat(300) {
            val lines = (1..4).map {
                val base = rnd.nextFloat() * 200f
                (0..24).map { x -> Pt(x * 16f, (base + rnd.nextFloat() * 80f - 40f).coerceIn(0f, 200f)) }
            }
            val requests = lines.flatMap { line -> line.filterIndexed { i, _ -> i % 3 == 0 }.map { Request(it, w, h) } }
            val placed = place(requests, lines)
            val boxes = placed.filterNotNull().map { Box(it.x, it.y, it.x + w, it.y + h) }
            boxes.forEach { assertClear(it, lines, lines.flatten()) }
            boxes.forEachIndexed { i, a -> boxes.drop(i + 1).forEach { b -> assertFalse("labels overlap", a.intersects(b)) } }
        }
    }

    /** Independent check: sample each segment densely and make sure no sample lands in the box. */
    private fun assertClear(box: Box, lines: List<List<Pt>>, dots: List<Pt>) {
        assertTrue("inside chart", box.left >= bounds.left && box.right <= bounds.right && box.top >= bounds.top && box.bottom <= bounds.bottom)
        lines.forEach { line ->
            line.zipWithNext().forEach { (p, q) ->
                for (k in 0..200) {
                    val t = k / 200f
                    val x = p.x + (q.x - p.x) * t
                    val y = p.y + (q.y - p.y) * t
                    assertFalse("line passes through label at ($x,$y) box=$box", x > box.left && x < box.right && y > box.top && y < box.bottom)
                }
            }
        }
        dots.forEach { d ->
            val cx = d.x.coerceIn(box.left, box.right)
            val cy = d.y.coerceIn(box.top, box.bottom)
            assertTrue("dot under label", hypot(d.x - cx, d.y - cy) >= dotR)
        }
    }
}
