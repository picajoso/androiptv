package es.maciptv.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.Programme
import es.maciptv.android.ProgrammeSchedule
import es.maciptv.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PortraitPlayerState(
    val channel: ChannelPresentation?,
    val playerView: PlayerView,
    val playing: Boolean,
    val favorite: Boolean,
    val groupName: String,
    val programmes: List<Programme>,
    val reminderMarks: Set<String>,
    val sourceId: String?
)

data class PortraitPlayerActions(
    val onBack: () -> Unit,
    val onToggle: () -> Unit,
    val onFullscreen: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCast: () -> Unit,
    val onExternal: () -> Unit,
    val onFavorite: () -> Unit,
    val onReminder: (Programme) -> Unit
)

class PortraitPlayerScreen(
    context: Context, private val state: PortraitPlayerState, private val actions: PortraitPlayerActions
) : LinearLayout(context) {
    private val scroll = ScrollView(context)
    private val pausedIcon = ImageView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(AndroTheme.BG)
        addVideo()
        scroll.isFillViewport = true
        scroll.addView(buildScrollableBody())
        addView(scroll, LayoutParams(-1, 0, 1f))
    }

    fun scrollYPosition(): Int = scroll.scrollY
    fun restoreScroll(y: Int) { scroll.post { scroll.scrollTo(0, y) } }

    fun updatePlaying(playing: Boolean) {
        pausedIcon.visibility = if (playing) GONE else VISIBLE
    }

    private fun addVideo() {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        (state.playerView.parent as? ViewGroup)?.removeView(state.playerView)
        state.playerView.useController = false
        frame.addView(state.playerView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(context).apply {
            contentDescription = if (state.playing) "Pausar vídeo" else "Reanudar vídeo"
            setOnClickListener { actions.onToggle() }
        }, FrameLayout.LayoutParams(-1, -1))

        fun overlayButton(icon: Int, description: String, action: () -> Unit): View =
            iconButton(context, icon, description, action, Color.WHITE).apply {
                setBackgroundColor(0x59000000)
            }
        frame.addView(overlayButton(R.drawable.ic_arrow_left, "Volver a los canales", actions.onBack),
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START).apply { marginStart = dp(12); topMargin = dp(8) })
        frame.addView(overlayButton(R.drawable.ic_maximize, "Pantalla completa", actions.onFullscreen),
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply { marginEnd = dp(12); topMargin = dp(8) })

        pausedIcon.setImageResource(R.drawable.ic_play)
        pausedIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        pausedIcon.setPadding(dp(13), dp(13), dp(13), dp(13))
        pausedIcon.setBackgroundColor(AndroTheme.ACCENT)
        pausedIcon.contentDescription = "Reanudar vídeo"
        pausedIcon.setOnClickListener { actions.onToggle() }
        frame.addView(pausedIcon, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        updatePlaying(state.playing)

        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xD9000000.toInt(), Color.TRANSPARENT))
        }
        val current = state.channel?.currentProgramme
        bottom.addView(text(context, "DIRECTO", TextStyle.Kicker).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(AndroTheme.ACCENT)
            setPadding(dp(5), 0, dp(5), 0)
        }, LayoutParams(-2, dp(23)))
        bottom.addView(PlaybackControls.programmeProgress(context, state.channel?.progressPercent),
            LayoutParams(0, dp(2), 1f).apply { marginStart = dp(9); marginEnd = dp(9) })
        bottom.addView(text(context, current?.let { time().format(Date(it.endMillis)) }.orEmpty(), TextStyle.Metadata).apply {
            setTextColor(Color.WHITE)
        })
        frame.addView(bottom, FrameLayout.LayoutParams(-1, dp(45), Gravity.BOTTOM))
        addView(frame, LayoutParams(-1, context.resources.displayMetrics.widthPixels * 9 / 16))
    }

    private fun buildScrollableBody() = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(buildInfo())
        addView(rule(2), LayoutParams(-1, dp(2)))
        addView(buildActions(), LayoutParams(-1, dp(64)))
        addView(rule(2), LayoutParams(-1, dp(2)))
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
        val guideHeader = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
            addView(text(context, "Guía · hoy", TextStyle.ChannelName).apply { textSize = 16f }, LayoutParams(0, -1, 1f))
            addView(text(context, date, TextStyle.Metadata))
        }
        addView(guideHeader, LayoutParams(-1, dp(47)))
        addView(rule(1), LayoutParams(-1, dp(1)))
        val rows = visibleProgrammeRows()
        if (rows.isEmpty()) addView(text(context, "Sin programación para hoy", TextStyle.Body).apply {
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }) else rows.forEach { programme ->
            addView(programmeRow(programme), LayoutParams(-1, -2))
            addView(rule(1), LayoutParams(-1, dp(1)))
        }
    }

    private fun buildInfo() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.TOP
        setPadding(dp(16), dp(14), dp(4), dp(12))
        val labels = LinearLayout(context).apply { orientation = VERTICAL }
        val titleRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text(context, state.channel?.displayName ?: "Selecciona un canal", TextStyle.ScreenTitle).apply {
            textSize = 22f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, -2, 1f))
        state.channel?.quality?.let { quality ->
            titleRow.addView(text(context, quality, TextStyle.Metadata).apply {
                setPadding(dp(4), 0, dp(4), 0)
                setBackgroundColor(AndroTheme.SURFACE)
            }, LayoutParams(-2, dp(22)).apply { marginStart = dp(6) })
        }
        labels.addView(titleRow, LayoutParams(-1, -2))
        val current = state.channel?.currentProgramme
        labels.addView(text(context, current?.let(ProgrammeSchedule::displayTitle) ?: "En directo", TextStyle.Body).apply {
            maxLines = 2
            setPadding(0, dp(5), 0, dp(4))
        })
        val metadata = current?.let {
            val remaining = ((it.endMillis - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
            "${time().format(Date(it.startMillis))}–${time().format(Date(it.endMillis))} · quedan ${remaining / 60} h ${remaining % 60} min · ${state.groupName}"
        } ?: state.groupName
        labels.addView(text(context, metadata, TextStyle.Metadata).apply { maxLines = 2 })
        addView(labels, LayoutParams(0, -2, 1f))
        val favorite = iconButton(context, R.drawable.ic_heart,
            if (state.favorite) "Quitar de favoritos" else "Añadir a favoritos", actions.onFavorite,
            if (state.favorite) AndroTheme.ACCENT else AndroTheme.NEUTRAL_700)
        favorite.background = GradientDrawable().apply {
            setColor(AndroTheme.BG)
            setStroke(dp(1), AndroTheme.DIVIDER)
        }
        addView(favorite, LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) })
    }

    private fun buildActions() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        val items = listOf(
            Triple(R.drawable.ic_skip_back, "Anterior", actions.onPrevious),
            Triple(R.drawable.ic_skip_forward, "Siguiente", actions.onNext),
            Triple(R.drawable.ic_cast, "Enviar", actions.onCast),
            Triple(R.drawable.ic_external_link, "Abrir fuera", actions.onExternal)
        )
        items.forEachIndexed { index, (icon, label, action) ->
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(dp(12), 0, dp(4), 0)
                contentDescription = label
                setOnClickListener { action() }
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(AndroTheme.TEXT)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(dp(18), dp(18)))
                addView(text(context, label, TextStyle.Button).apply {
                    textSize = 11.5f
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(-1, dp(25)))
            }
            addView(cell, LayoutParams(0, -1, 1f))
            if (index < items.lastIndex) addView(rule(1), LayoutParams(dp(1), -1))
        }
    }

    private fun visibleProgrammeRows(): List<Programme> {
        val entries = state.programmes
        val now = System.currentTimeMillis()
        val current = entries.indexOfFirst { it.startMillis <= now && it.endMillis > now }
        val firstUpcoming = entries.indexOfFirst { it.startMillis > now }
        val anchor = if (current >= 0) current else if (firstUpcoming >= 0) firstUpcoming else entries.size
        return entries.drop((anchor - 2).coerceAtLeast(0))
    }

    private fun programmeRow(programme: Programme): View {
        val now = System.currentTimeMillis()
        val past = programme.endMillis <= now
        val current = programme.startMillis <= now && !past
        val future = programme.startMillis > now
        val key = state.sourceId?.let { ProgrammeSchedule.reminderKey(it, state.channel?.channel?.id.orEmpty(), programme.startMillis) }
        val marked = key != null && key in state.reminderMarks
        val color = if (current) Color.WHITE else if (past) AndroTheme.NEUTRAL_600 else AndroTheme.TEXT
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (current) AndroTheme.TEXT else AndroTheme.BG)
            val hour = text(context, time().format(Date(programme.startMillis)), TextStyle.Button).apply {
                textSize = 12.5f
                setTextColor(color)
                setPadding(dp(16), 0, dp(4), 0)
            }
            addView(hour, LayoutParams(dp(60), dp(62)))
            addView(rule(1), LayoutParams(dp(1), -1))
            val titleColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(10), dp(8), dp(4), dp(8))
            }
            if (ProgrammeSchedule.isLive(programme, now)) titleColumn.addView(text(context, "DIRECTO", TextStyle.Kicker).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(AndroTheme.ACCENT)
                setPadding(dp(4), 0, dp(4), 0)
            }, LayoutParams(-2, dp(18)))
            titleColumn.addView(text(context, ProgrammeSchedule.displayTitle(programme), TextStyle.Body).apply {
                textSize = 13.5f
                setTextColor(color)
                maxLines = 3
            })
            addView(titleColumn, LayoutParams(0, -2, 1f))
            if (future) addView(iconButton(context, if (marked) R.drawable.ic_bell_ring else R.drawable.ic_bell,
                if (marked) "Quitar marca de la guía" else "Marcar en la guía", { actions.onReminder(programme) },
                if (marked) AndroTheme.ACCENT else AndroTheme.NEUTRAL_600), LayoutParams(dp(44), dp(62)))
            else addView(View(context), LayoutParams(dp(44), dp(62)))
        }
    }

    private fun rule(height: Int) = View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }
    private fun dp(value: Int) = AndroTheme.dp(context, value)
    private fun time() = SimpleDateFormat("HH:mm", Locale.getDefault())
}
