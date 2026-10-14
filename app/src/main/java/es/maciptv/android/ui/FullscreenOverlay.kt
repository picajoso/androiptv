package es.maciptv.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import es.maciptv.android.R

data class FullscreenChannelItem(val id: String, val name: String)

data class FullscreenUiState(
    val channelName: String,
    val quality: String?,
    val programme: String?,
    val previousName: String?,
    val nextName: String?,
    val playing: Boolean,
    val progressPercent: Int,
    val startTime: String?,
    val endTime: String?,
    val favorite: Boolean,
    val timerMinutes: Int,
    val fillVideo: Boolean,
    val groupName: String,
    val selectedId: String?
)

data class FullscreenActions(
    val onExit: () -> Unit,
    val onToggle: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onFavorite: () -> Unit,
    val onTimer: () -> Unit,
    val onFit: () -> Unit,
    val onLast: () -> Unit,
    val onCast: () -> Unit,
    val onBrightness: (Float) -> Unit,
    val onVolume: (Float) -> Unit,
    val channels: () -> List<FullscreenChannelItem>,
    val programmeFor: (String) -> String?,
    val onSelectChannel: (String) -> Unit
)

/** Owns fullscreen UI only. Playback and channel state remain in MainActivity. */
class FullscreenOverlay(
    context: Context,
    playerView: PlayerView,
    private val actions: FullscreenActions,
    initialBrightness: Float,
    initialVolume: Float
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { if (state?.playing == true && !drawerOpen && !locked) controls.visibility = GONE }
    private val controls = FrameLayout(context)
    private val drawer = LinearLayout(context)
    private val drawerList = ListView(context)
    private val drawerItems = mutableListOf<FullscreenChannelItem>()
    private var drawerOpen = false
    private var locked = false
    private var state: FullscreenUiState? = null
    private val channelTitle = darkText("Selecciona un canal", 17f, 800)
    private val qualityLabel = darkText("", 11f, 600)
    private val programmeTitle = darkText("", 12.5f, 400)
    private val previousName = darkText("", 11f, 600)
    private val nextName = darkText("", 11f, 600)
    private val playButton = darkIcon(R.drawable.ic_pause, "Pausar reproducción") { actions.onToggle() }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val startTime = darkText("", 11f, 400)
    private val endTime = darkText("", 11f, 400)
    private val favoriteCell = actionCell(R.drawable.ic_heart, "Favorito", "Marcar canal favorito") { actions.onFavorite() }
    private val timerCell = actionCell(R.drawable.ic_moon, "Apagado", "Cambiar temporizador de apagado") { actions.onTimer() }
    private val fitCell = actionCell(R.drawable.ic_scan, "Rellenar", "Cambiar modo de imagen") { actions.onFit() }
    private val brightness = VerticalLevel(context, "Brillo", initialBrightness, actions.onBrightness)
    private val volume = VerticalLevel(context, "Volumen", initialVolume, actions.onVolume)
    private val unlockButton = darkIcon(R.drawable.ic_unlock, "Pantalla bloqueada, toca para desbloquear") { unlock() }
    private val drawerAdapter = object : BaseAdapter() {
        override fun getCount() = drawerItems.size
        override fun getItem(position: Int) = drawerItems[position]
        override fun getItemId(position: Int) = drawerItems[position].id.hashCode().toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? DrawerRow) ?: DrawerRow(context)
            val item = getItem(position)
            row.bind(item, actions.programmeFor(item.id), item.id == state?.selectedId)
            row.setOnClickListener { actions.onSelectChannel(item.id) }
            return row
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        (playerView.parent as? ViewGroup)?.removeView(playerView)
        playerView.useController = false
        addView(playerView, LayoutParams(-1, -1))
        addView(View(context).apply { setOnClickListener { toggleControls() } }, LayoutParams(-1, -1))
        addView(controls, LayoutParams(-1, -1))

        val topShade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB3000000.toInt(), 0x00000000))
        }
        controls.addView(topShade, LayoutParams(-1, dp(145), Gravity.TOP))
        val bottomShade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xCC000000.toInt()))
        }
        controls.addView(bottomShade, LayoutParams(-1, dp(165), Gravity.BOTTOM))

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        top.addView(darkIcon(R.drawable.ic_arrow_left, "Salir de pantalla completa") { actions.onExit() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            val firstLine = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            firstLine.addView(channelTitle, LinearLayout.LayoutParams(0, -1, 1f))
            qualityLabel.apply {
                setPadding(dp(5), 0, dp(5), 0)
                setBackgroundColor(0x30FFFFFF)
            }
            firstLine.addView(qualityLabel, LinearLayout.LayoutParams(-2, dp(20)).apply { marginStart = dp(8) })
            addView(firstLine, LinearLayout.LayoutParams(-1, dp(24)))
            addView(programmeTitle, LinearLayout.LayoutParams(-1, dp(18)))
        }
        top.addView(heading, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(darkIcon(R.drawable.ic_list, "Abrir lista de canales") { showDrawer() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(top, LayoutParams(-1, dp(58), Gravity.TOP).apply { topMargin = dp(10) })

        val center = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(86), 0, dp(86), 0)
        }
        center.addView(skipControl(R.drawable.ic_skip_back, "Canal anterior", previousName, actions.onPrevious),
            LinearLayout.LayoutParams(0, dp(80), 1f))
        playButton.apply {
            setBackgroundColor(AndroTheme.ACCENT)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        center.addView(playButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            marginStart = dp(24); marginEnd = dp(24)
        })
        center.addView(skipControl(R.drawable.ic_skip_forward, "Canal siguiente", nextName, actions.onNext),
            LinearLayout.LayoutParams(0, dp(80), 1f))
        controls.addView(center, LayoutParams(-1, dp(90), Gravity.CENTER))

        controls.addView(sideLevel(R.drawable.ic_sun, brightness),
            LayoutParams(dp(44), dp(194), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(30) })
        controls.addView(sideLevel(R.drawable.ic_volume_2, volume),
            LayoutParams(dp(44), dp(194), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(30) })

        val live = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            addView(darkText("DIRECTO", 11f, 800).apply { setTextColor(AndroTheme.ACCENT_400) })
            addView(startTime, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(12) })
            progress.max = 100
            progress.progressTintList = ColorStateList.valueOf(AndroTheme.ACCENT)
            progress.progressBackgroundTintList = ColorStateList.valueOf(0x66FFFFFF)
            addView(progress, LinearLayout.LayoutParams(0, dp(3), 1f).apply {
                marginStart = dp(12); marginEnd = dp(12)
            })
            addView(endTime)
        }
        controls.addView(live, LayoutParams(-1, dp(36), Gravity.BOTTOM).apply { bottomMargin = dp(48) })

        val actionsBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x99000000.toInt())
            addView(actionCell(R.drawable.ic_cast, "Enviar", "Buscar dispositivos para enviar") { actions.onCast() },
                LinearLayout.LayoutParams(0, -1, 1f))
            addView(favoriteCell, LinearLayout.LayoutParams(0, -1, 1f))
            addView(timerCell, LinearLayout.LayoutParams(0, -1, 1f))
            addView(fitCell, LinearLayout.LayoutParams(0, -1, 1f))
            addView(actionCell(R.drawable.ic_history, "Último", "Volver al último canal") { actions.onLast() },
                LinearLayout.LayoutParams(0, -1, 1f))
            addView(actionCell(R.drawable.ic_lock, "Bloquear", "Bloquear controles") { lock() },
                LinearLayout.LayoutParams(0, -1, 1f))
        }
        controls.addView(actionsBar, LayoutParams(-1, dp(44), Gravity.BOTTOM))

        buildDrawer()
        unlockButton.apply {
            visibility = GONE
            setBackgroundColor(0x99000000.toInt())
        }
        addView(unlockButton, LayoutParams(dp(48), dp(48), Gravity.START or Gravity.BOTTOM).apply {
            marginStart = dp(16); bottomMargin = dp(16)
        })
    }

    private fun buildDrawer() {
        drawer.orientation = LinearLayout.VERTICAL
        drawer.setBackgroundColor(AndroTheme.DARK_BG)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        val title = darkText("Todos los canales", 15f, 800)
        title.tag = "drawer-title"
        header.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        header.addView(darkIcon(R.drawable.ic_x, "Cerrar lista de canales") { closeDrawer() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        drawer.addView(header, LinearLayout.LayoutParams(-1, dp(52)))
        drawer.addView(View(context).apply { setBackgroundColor(0x55FFFFFF) }, LinearLayout.LayoutParams(-1, dp(1)))
        drawerList.adapter = drawerAdapter
        drawerList.dividerHeight = 0
        drawerList.setBackgroundColor(AndroTheme.DARK_BG)
        drawer.addView(drawerList, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.visibility = GONE
        addView(drawer, LayoutParams(dp(300), -1, Gravity.END))
    }

    fun update(value: FullscreenUiState) {
        state = value
        channelTitle.text = value.channelName
        qualityLabel.text = value.quality.orEmpty()
        qualityLabel.visibility = if (value.quality.isNullOrBlank()) GONE else VISIBLE
        programmeTitle.text = value.programme.orEmpty()
        playButton.setImageResource(if (value.playing) R.drawable.ic_pause else R.drawable.ic_play)
        playButton.contentDescription = if (value.playing) "Pausar reproducción" else "Reanudar reproducción"
        previousName.text = value.previousName ?: "Primer canal"
        nextName.text = value.nextName ?: "Último canal"
        startTime.text = value.startTime.orEmpty()
        endTime.text = value.endTime.orEmpty()
        progress.progress = value.progressPercent
        favoriteCell.setActive(value.favorite)
        timerCell.setLabel(if (value.timerMinutes == 0) "Apagado" else "${value.timerMinutes} min")
        timerCell.setActive(value.timerMinutes > 0)
        fitCell.setLabel(if (value.fillVideo) "Sin recortar" else "Rellenar")
        (drawer.findViewWithTag<TextView>("drawer-title"))?.text =
            if (value.groupName == "Todos") "Todos los canales" else value.groupName
        if (drawerOpen) drawerAdapter.notifyDataSetChanged()
        scheduleHide()
    }

    fun showDrawer() {
        if (locked) return
        drawerItems.clear()
        drawerItems.addAll(actions.channels())
        drawerAdapter.notifyDataSetChanged()
        drawer.visibility = VISIBLE
        drawerOpen = true
        controls.visibility = VISIBLE
        handler.removeCallbacks(hideControls)
        val index = drawerItems.indexOfFirst { it.id == state?.selectedId }
        if (index >= 0) drawerList.setSelection(index)
    }

    fun closeDrawer() {
        drawer.visibility = GONE
        drawerOpen = false
        scheduleHide()
    }

    private fun toggleControls() {
        if (locked) return
        if (drawerOpen) { closeDrawer(); return }
        controls.visibility = if (controls.visibility == VISIBLE) GONE else VISIBLE
        scheduleHide()
    }

    private fun lock() {
        locked = true
        drawer.visibility = GONE
        drawerOpen = false
        controls.visibility = GONE
        unlockButton.visibility = VISIBLE
        handler.removeCallbacks(hideControls)
    }

    private fun unlock() {
        locked = false
        unlockButton.visibility = GONE
        controls.visibility = VISIBLE
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (state?.playing == true && controls.visibility == VISIBLE && !drawerOpen && !locked)
            handler.postDelayed(hideControls, 4_000L)
    }

    fun dispose() { handler.removeCallbacks(hideControls) }

    private fun sideLevel(icon: Int, level: VerticalLevel) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        addView(level, LinearLayout.LayoutParams(dp(44), dp(160)).apply { topMargin = dp(8) })
    }

    private fun skipControl(icon: Int, description: String, name: TextView, click: () -> Unit) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = description
            setOnClickListener { click() }
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            name.gravity = Gravity.CENTER
            name.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(name, LinearLayout.LayoutParams(-1, dp(20)))
        }

    private fun darkIcon(icon: Int, description: String, click: () -> Unit) =
        iconButton(context, icon, description, click, Color.WHITE)

    private fun darkText(value: String, sp: Float, weight: Int) = text(context, value, TextStyle.Body).apply {
        textSize = sp
        fontVariationSettings = "'wght' $weight"
        setTextColor(AndroTheme.ON_DARK)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun actionCell(icon: Int, label: String, description: String, click: () -> Unit) =
        FullscreenActionCell(context, icon, label, description, click)

    private fun dp(value: Int) = AndroTheme.dp(context, value)
}

private class FullscreenActionCell(
    context: Context, icon: Int, label: String, description: String, click: () -> Unit
) : LinearLayout(context) {
    private val image = ImageView(context)
    private val caption = text(context, label, TextStyle.Button)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(AndroTheme.dp(context, 4), 0, AndroTheme.dp(context, 4), 0)
        contentDescription = description
        setOnClickListener { click() }
        image.setImageResource(icon)
        image.imageTintList = ColorStateList.valueOf(AndroTheme.ON_DARK)
        image.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(image, LayoutParams(AndroTheme.dp(context, 17), AndroTheme.dp(context, 17)))
        caption.textSize = 12f
        caption.setTextColor(AndroTheme.ON_DARK)
        caption.maxLines = 1
        caption.ellipsize = TextUtils.TruncateAt.END
        caption.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(caption, LayoutParams(-2, -1).apply { marginStart = AndroTheme.dp(context, 5) })
    }

    fun setLabel(label: String) { caption.text = label }
    fun setActive(active: Boolean) {
        val color = if (active) AndroTheme.ACCENT_400 else AndroTheme.ON_DARK
        image.imageTintList = ColorStateList.valueOf(color)
        caption.setTextColor(color)
    }
}

private class DrawerRow(context: Context) : LinearLayout(context) {
    private val tile = text(context, "", TextStyle.Button)
    private val name = text(context, "", TextStyle.Button)
    private val programme = text(context, "", TextStyle.Metadata)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 12), 0)
        tile.gravity = Gravity.CENTER
        tile.setTextColor(AndroTheme.ON_DARK)
        tile.setBackgroundColor(AndroTheme.NEUTRAL_900)
        addView(tile, LayoutParams(AndroTheme.dp(context, 36), AndroTheme.dp(context, 36)))
        val labels = LinearLayout(context).apply { orientation = VERTICAL; setPadding(AndroTheme.dp(context, 10), 0, 0, 0) }
        name.textSize = 13f
        name.fontVariationSettings = "'wght' 800"
        name.setTextColor(AndroTheme.ON_DARK)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        programme.textSize = 11f
        programme.setTextColor(0xFFBDB9B8.toInt())
        programme.maxLines = 1
        programme.ellipsize = TextUtils.TruncateAt.END
        labels.addView(name, LayoutParams(-1, AndroTheme.dp(context, 23)))
        labels.addView(programme, LayoutParams(-1, AndroTheme.dp(context, 18)))
        addView(labels, LayoutParams(0, -2, 1f))
    }

    fun bind(item: FullscreenChannelItem, subtitle: String?, selected: Boolean) {
        name.text = item.name
        programme.text = subtitle ?: "En directo"
        tile.text = item.name.trim().take(2).uppercase()
        setBackgroundColor(if (selected) AndroTheme.DARK_ROW_CURRENT else AndroTheme.DARK_BG)
        contentDescription = "${item.name}, ${programme.text}"
    }
}

private class VerticalLevel(
    context: Context, private val label: String, initial: Float, private val onChange: (Float) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = initial.coerceIn(0f, 1f)

    init {
        isFocusable = true
        updateDescription()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = width / 2f
        val top = AndroTheme.dp(context, 6).toFloat()
        val bottom = height - top
        val edge = AndroTheme.dp(context, 2).toFloat()
        paint.color = 0x66FFFFFF
        canvas.drawRect(center - edge, top, center + edge, bottom, paint)
        paint.color = Color.WHITE
        canvas.drawRect(center - edge, bottom - (bottom - top) * level, center + edge, bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val top = AndroTheme.dp(context, 6).toFloat()
                level = ((height - top - event.y) / (height - 2 * top)).coerceIn(0f, 1f)
                onChange(level)
                updateDescription()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            level = (level + if (keyCode == KeyEvent.KEYCODE_DPAD_UP) .05f else -.05f).coerceIn(0f, 1f)
            onChange(level)
            updateDescription()
            invalidate()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun updateDescription() { contentDescription = "$label, ${(level * 100).toInt()} por ciento" }
}
