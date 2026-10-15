package es.maciptv.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.OverScroller
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.Programme
import es.maciptv.android.ProgrammeSchedule
import es.maciptv.android.SafeNetwork
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class GuideGridRow(val channel: ChannelPresentation, val programmes: List<Programme>) {
    val maxEnds: LongArray = LongArray(programmes.size).also { values ->
        var maximum = Long.MIN_VALUE
        programmes.forEachIndexed { index, programme ->
            maximum = max(maximum, programme.endMillis)
            values[index] = maximum
        }
    }
}

/** Single Canvas for both scroll axes; only visible rows and programme blocks are drawn. */
class EpgGridView(
    context: Context,
    private val rows: List<GuideGridRow>,
    private val date: LocalDate,
    private val zone: ZoneId,
    private val selectedChannelId: String?,
    private val sourceId: String?,
    private var marks: Set<String>,
    private val network: SafeNetwork?,
    private val onChannel: (ChannelPresentation) -> Unit,
    private val onProgramme: (ChannelPresentation, Programme) -> Unit
) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
    }
    private val scale = resources.displayMetrics.density
    private fun d(value: Float) = value * scale
    private val corner = d(92f)
    private val header = d(30f)
    private val rowHeight = d(64f)
    private val minute = d(3f)
    private val dayStart = date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    private val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val contentWidth = d(3240f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = d(12.5f); typeface = android.graphics.Typeface.create("sans-serif-medium", 0) }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = d(10.5f) }
    private val logos = mutableMapOf<String, Bitmap?>()
    private val requested = mutableSetOf<String>()
    private val layouts = android.util.LruCache<String, StaticLayout>(512)
    private val labels = mutableMapOf<Long, String>()
    private val clockFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
    private val bell = context.getDrawable(es.maciptv.android.R.drawable.ic_bell_ring)?.mutate()?.apply { setTint(AndroTheme.ACCENT) }
    private val scroller = OverScroller(context)
    private var offsetX = 0f
    private var offsetY = 0f
    private var selected: Pair<String, Long>? = null
    private var accessibilityFocus = View.NO_ID
    private var hoverId = View.NO_ID
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX = (offsetX + distanceX).coerceIn(0f, maxX())
            offsetY = (offsetY + distanceY).coerceIn(0f, maxY())
            invalidate(); return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(offsetX.toInt(), offsetY.toInt(), -velocityX.toInt(), -velocityY.toInt(),
                0, maxX().toInt(), 0, maxY().toInt()); postInvalidateOnAnimation(); return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val rowIndex = ((e.y - header + offsetY) / rowHeight).toInt()
            if (rowIndex !in rows.indices || e.y < header) return true
            val row = rows[rowIndex]
            if (e.x < corner) onChannel(row.channel)
            else {
                val instant = dayStart + (((e.x - corner + offsetX) / minute) * 60_000).toLong()
                row.programmes.firstOrNull { it.startMillis <= instant && it.endMillis > instant }?.let {
                    selected = row.channel.channel.id to it.startMillis
                    invalidate()
                    onProgramme(row.channel, it)
                }
            }
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibilityProvider

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!manager.isTouchExplorationEnabled) return super.onHoverEvent(event)
        val id = if (event.action == MotionEvent.ACTION_HOVER_EXIT) View.NO_ID else virtualIdAt(event.x, event.y)
        if (id != hoverId) {
            if (hoverId != View.NO_ID) sendVirtualEvent(hoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            hoverId = id
            if (id != View.NO_ID) sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        }
        return true
    }

    private val accessibilityProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) return AccessibilityNodeInfo.obtain(this@EpgGridView).apply {
                className = View::class.java.name
                packageName = context.packageName
                for (id in visibleVirtualIds()) addChild(this@EpgGridView, id)
            }
            val rowIndex = virtualViewId / 10_000
            val programmeIndex = virtualViewId % 10_000 - 1
            val row = rows.getOrNull(rowIndex) ?: return null
            val y = header + rowIndex * rowHeight - offsetY
            val node = AccessibilityNodeInfo.obtain().apply {
                setSource(this@EpgGridView, virtualViewId)
                setParent(this@EpgGridView)
                packageName = context.packageName
                className = View::class.java.name
                isClickable = true
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                if (accessibilityFocus == virtualViewId) addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                else addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            }
            val bounds = if (programmeIndex < 0) {
                node.contentDescription = "${row.channel.displayName}, ver canal"
                android.graphics.Rect(0, y.toInt(), corner.toInt(), (y + rowHeight).toInt())
            } else {
                val p = row.programmes.getOrNull(programmeIndex) ?: return null
                val start = corner + ((p.startMillis - dayStart) / 60_000f) * minute - offsetX
                val end = corner + ((p.endMillis - dayStart) / 60_000f) * minute - offsetX
                val now = System.currentTimeMillis()
                node.contentDescription = "${row.channel.displayName}, ${clockFormat.format(java.util.Date(p.startMillis))} a ${clockFormat.format(java.util.Date(p.endMillis))}, ${ProgrammeSchedule.displayTitle(p)}" +
                    if (p.startMillis <= now && p.endMillis > now) ", en emisión" else ""
                android.graphics.Rect(max(corner.toInt(), start.toInt()), y.toInt(), min(width, end.toInt()), (y + rowHeight).toInt())
            }
            node.setBoundsInParent(bounds)
            val screen = IntArray(2); getLocationOnScreen(screen)
            bounds.offset(screen[0], screen[1]); node.setBoundsInScreen(bounds)
            return node
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
            if (virtualViewId == View.NO_ID) return performAccessibilityAction(action, arguments)
            val rowIndex = virtualViewId / 10_000
            val programmeIndex = virtualViewId % 10_000 - 1
            val row = rows.getOrNull(rowIndex) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    if (programmeIndex < 0) onChannel(row.channel)
                    else row.programmes.getOrNull(programmeIndex)?.let { p ->
                        selected = row.channel.channel.id to p.startMillis; invalidate(); onProgramme(row.channel, p)
                    } ?: return false
                    true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    accessibilityFocus = virtualViewId
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    accessibilityFocus = View.NO_ID
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    true
                }
                else -> false
            }
        }
    }

    private fun visibleVirtualIds(): List<Int> {
        val first = max(0, (offsetY / rowHeight).toInt())
        val last = min(rows.lastIndex, ((offsetY + height - header) / rowHeight).toInt() + 1)
        val instantStart = dayStart + (offsetX / minute * 60_000).toLong()
        val instantEnd = dayStart + ((offsetX + width - corner) / minute * 60_000).toLong()
        return buildList {
            for (r in first..last) {
                val row = rows.getOrNull(r) ?: continue
                add(r * 10_000)
                var p = firstVisible(row, instantStart)
                while (p < row.programmes.size && row.programmes[p].startMillis < instantEnd) {
                    add(r * 10_000 + p + 1); p++
                }
            }
        }
    }

    private fun virtualIdAt(x: Float, y: Float): Int {
        if (y < header) return View.NO_ID
        val r = ((y - header + offsetY) / rowHeight).toInt()
        val row = rows.getOrNull(r) ?: return View.NO_ID
        if (x < corner) return r * 10_000
        val instant = dayStart + (((x - corner + offsetX) / minute) * 60_000).toLong()
        val p = row.programmes.indexOfFirst { it.startMillis <= instant && it.endMillis > instant }
        return if (p < 0) View.NO_ID else r * 10_000 + p + 1
    }

    private fun sendVirtualEvent(id: Int, type: Int) {
        val event = AccessibilityEvent.obtain(type)
        event.setSource(this, id)
        event.packageName = context.packageName
        event.className = View::class.java.name
        event.contentDescription = accessibilityProvider.createAccessibilityNodeInfo(id)?.contentDescription
        parent?.requestSendAccessibilityEvent(this, event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat(); offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    fun scrollToMinute(target: Int, animate: Boolean) {
        val x = (target * minute - d(40f * 3f)).coerceIn(0f, maxX())
        if (animate) {
            scroller.startScroll(offsetX.toInt(), offsetY.toInt(), (x - offsetX).toInt(), 0, 320)
            postInvalidateOnAnimation()
        } else { offsetX = x; invalidate() }
    }

    fun clearSelection() { selected = null; invalidate() }
    fun updateMarks(value: Set<String>) { marks = value; invalidate() }
    private fun maxX() = max(0f, contentWidth - (width - corner))
    private fun maxY() = max(0f, rows.size * rowHeight - (height - header))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = AndroTheme.BG; canvas.drawColor(AndroTheme.BG)
        val firstRow = max(0, (offsetY / rowHeight).toInt())
        val lastRow = min(rows.lastIndex, ((offsetY + height - header) / rowHeight).toInt() + 1)
        val visibleStart = dayStart + (offsetX / minute * 60_000).toLong()
        val visibleEnd = dayStart + ((offsetX + width - corner) / minute * 60_000).toLong()
        canvas.save(); canvas.clipRect(corner, header, width.toFloat(), height.toFloat())
        for (r in firstRow..lastRow) {
            if (r !in rows.indices) continue
            val y = header + r * rowHeight - offsetY
            val row = rows[r]
            paint.color = AndroTheme.DIVIDER; canvas.drawRect(corner, y + rowHeight - d(1f), width.toFloat(), y + rowHeight, paint)
            val entries = row.programmes
            var left = firstVisible(row, visibleStart)
            if (entries.isEmpty()) {
                paint.color = AndroTheme.NEUTRAL_200; canvas.drawRect(corner, y, width.toFloat(), y + rowHeight - d(1f), paint)
                drawText(canvas, "Sin información de guía", corner + d(9f), y + d(34f), d(12.5f), AndroTheme.NEUTRAL_700)
            }
            while (left < entries.size && entries[left].startMillis < visibleEnd) {
                val p = entries[left++]
                val start = max(dayStart, p.startMillis)
                val end = min(dayEnd, p.endMillis)
                if (end <= start) continue
                val x1 = corner + ((start - dayStart) / 60_000f) * minute - offsetX
                val x2 = corner + ((end - dayStart) / 60_000f) * minute - offsetX
                drawProgramme(canvas, row, p, x1, x2, y)
            }
        }
        if (date == LocalDate.now(zone)) {
            val now = System.currentTimeMillis()
            val nowX = corner + ((now - dayStart) / 60_000f) * minute - offsetX
            paint.color = AndroTheme.ACCENT
            canvas.drawRect(nowX, header, nowX + d(2f), height.toFloat(), paint)
        }
        canvas.restore()
        drawHourHeader(canvas)
        drawChannelColumn(canvas, firstRow, lastRow)
    }

    private fun firstVisible(row: GuideGridRow, instant: Long): Int {
        var low = 0; var high = row.programmes.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (row.maxEnds[mid] <= instant) low = mid + 1 else high = mid
        }
        return low
    }

    private fun drawProgramme(canvas: Canvas, row: GuideGridRow, p: Programme, x1: Float, x2: Float, y: Float) {
        val now = System.currentTimeMillis()
        val current = date == LocalDate.now(zone) && p.startMillis <= now && p.endMillis > now
        val past = p.endMillis <= now
        val active = selected == (row.channel.channel.id to p.startMillis)
        paint.color = when { active -> AndroTheme.TEXT; current -> AndroTheme.ACCENT_100; else -> AndroTheme.BG }
        canvas.drawRect(x1, y, x2 - d(1f), y + rowHeight - d(1f), paint)
        paint.color = AndroTheme.DIVIDER; canvas.drawRect(x2 - d(1f), y, x2, y + rowHeight, paint)
        val available = x2 - x1 - d(16f)
        val visibleRight = min(x2, width.toFloat())
        if (available > d(70f)) {
            val startLabel = labels.getOrPut(p.startMillis) { clockFormat.format(java.util.Date(p.startMillis)) }
            val endLabel = labels.getOrPut(p.endMillis) { clockFormat.format(java.util.Date(p.endMillis)) }
            drawText(canvas, "$startLabel–$endLabel", x1 + d(8f), y + d(18f), d(10.5f), if (active) AndroTheme.NEUTRAL_300 else AndroTheme.NEUTRAL_700)
            if (ProgrammeSchedule.isLive(p, now) && current) {
                paint.color = AndroTheme.ACCENT; canvas.drawRect(visibleRight - d(39f), y + d(5f), visibleRight - d(4f), y + d(18f), paint)
                drawText(canvas, "DIRECTO", visibleRight - d(37f), y + d(15f), d(7f), android.graphics.Color.WHITE)
            }
            if (sourceId?.let { ProgrammeSchedule.reminderKey(it, row.channel.channel.id, p.startMillis) } in marks) {
                bell?.let { icon ->
                    icon.setBounds((visibleRight - d(17f)).toInt(), (y + d(5f)).toInt(), (visibleRight - d(5f)).toInt(), (y + d(17f)).toInt())
                    icon.draw(canvas)
                }
            }
        }
        if (available > d(18f)) {
            titlePaint.color = if (active) AndroTheme.BG else if (past) AndroTheme.NEUTRAL_600 else AndroTheme.TEXT
            titlePaint.typeface = android.graphics.Typeface.create(if (current || active) "sans-serif-black" else "sans-serif-medium", 0)
            val layoutKey = "${row.channel.channel.id}|${p.startMillis}|${available.toInt()}|$active|$current|$past"
            val layout = layouts.get(layoutKey) ?: run {
                val title = ProgrammeSchedule.displayTitle(p)
                StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, available.toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2).setIncludePad(false).build().also { layouts.put(layoutKey, it) }
            }
            canvas.save(); canvas.clipRect(x1 + d(8f), y + d(22f), x2 - d(8f), y + d(50f))
            canvas.translate(x1 + d(8f), y + d(24f)); layout.draw(canvas); canvas.restore()
        }
        if (current) {
            paint.color = AndroTheme.ACCENT
            val pct = ((now - p.startMillis).toFloat() / (p.endMillis - p.startMillis).coerceAtLeast(1)).coerceIn(0f, 1f)
            canvas.drawRect(x1, y + rowHeight - d(2f), x1 + (x2 - x1) * pct, y + rowHeight, paint)
        }
    }

    private fun drawHourHeader(canvas: Canvas) {
        paint.color = AndroTheme.BG; canvas.drawRect(0f, 0f, width.toFloat(), header, paint)
        canvas.save(); canvas.clipRect(corner, 0f, width.toFloat(), header)
        for (halfHour in 0..36) {
            val x = corner + halfHour * d(90f) - offsetX
            if (x < corner - d(90f) || x > width) continue
            paint.color = AndroTheme.DIVIDER; canvas.drawRect(x, 0f, x + d(1f), header, paint)
            val hour = 6 + halfHour / 2
            drawText(canvas, String.format(Locale.getDefault(), "%02d:%02d", hour, if (halfHour % 2 == 0) 0 else 30),
                x + d(6f), d(19f), d(10.5f), AndroTheme.NEUTRAL_700)
        }
        if (date == LocalDate.now(zone)) {
            val now = System.currentTimeMillis()
            val x = corner + ((now - dayStart) / 60_000f) * minute - offsetX
            if (x in corner..width.toFloat()) {
                paint.color = AndroTheme.ACCENT; canvas.drawRect(x, 0f, x + d(36f), d(17f), paint)
                drawText(canvas, java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(now)),
                    x + d(3f), d(12f), d(10f), android.graphics.Color.WHITE)
            }
        }
        canvas.restore()
        paint.color = AndroTheme.TEXT; canvas.drawRect(0f, header - d(2f), width.toFloat(), header, paint)
    }

    private fun drawChannelColumn(canvas: Canvas, first: Int, last: Int) {
        paint.color = AndroTheme.BG; canvas.drawRect(0f, 0f, corner, height.toFloat(), paint)
        for (r in first..last) {
            if (r !in rows.indices) continue
            val row = rows[r]
            val y = header + r * rowHeight - offsetY
            paint.color = if (row.channel.channel.id == selectedChannelId) AndroTheme.ACCENT_100 else AndroTheme.BG
            canvas.drawRect(0f, y, corner, y + rowHeight, paint)
            val logo = logoFor(row, r)
            paint.color = android.graphics.Color.WHITE; canvas.drawRect(d(12f), y + d(6f), d(48f), y + d(32f), paint)
            if (logo != null) canvas.drawBitmap(logo, null, RectF(d(13f), y + d(7f), d(47f), y + d(31f)), paint)
            else drawText(canvas, row.channel.displayName.take(3).uppercase(), d(15f), y + d(25f), d(10.5f), AndroTheme.TEXT)
            val name = row.channel.displayName
            drawText(canvas, if (name.length > 14) name.take(13) + "…" else name, d(12f), y + d(50f), d(11.5f), AndroTheme.TEXT)
            paint.color = AndroTheme.DIVIDER; canvas.drawRect(0f, y + rowHeight - d(1f), corner, y + rowHeight, paint)
        }
        paint.color = AndroTheme.TEXT; canvas.drawRect(corner - d(2f), 0f, corner, height.toFloat(), paint)
        paint.color = AndroTheme.BG; canvas.drawRect(0f, 0f, corner, header, paint)
        drawText(canvas, "CANAL", d(12f), d(19f), d(10f), AndroTheme.NEUTRAL_700)
    }

    private fun logoFor(row: GuideGridRow, index: Int): Bitmap? {
        val id = row.channel.channel.id
        if (id !in requested && network != null && row.channel.channel.logo.isNotBlank()) {
            requested.add(id)
            LogoLoader.loadBitmap(row.channel.channel.logo, network, d(34f).toInt(), d(24f).toInt()) { bitmap ->
                logos[id] = bitmap
                val top = header + index * rowHeight - offsetY
                invalidate(0, top.toInt().coerceAtLeast(0), corner.toInt(), (top + rowHeight).toInt().coerceAtMost(height))
            }
        }
        return logos[id]
    }

    private fun drawText(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int) {
        smallPaint.textSize = size; smallPaint.color = color
        canvas.drawText(value, x, baseline, smallPaint)
    }
}
