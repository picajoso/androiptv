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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.Programme
import es.maciptv.android.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class LandscapePlayerActions(
    val onToggle: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onFullscreen: () -> Unit,
    val onFavorite: () -> Unit,
    val onTimer: () -> Unit,
    val onExternal: () -> Unit
)

class LandscapePlayerPanel(
    context: Context,
    playerView: PlayerView,
    actions: LandscapePlayerActions
) : LinearLayout(context) {
    private val nowHeading = text(context, "", TextStyle.Kicker)
    private val nowTitle = text(context, "", TextStyle.Button)
    private val nextHeading = text(context, "", TextStyle.Kicker)
    private val nextTitle = text(context, "", TextStyle.Button)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val toggle = iconButton(context, R.drawable.ic_pause, context.getString(R.string.a11y_pause), actions.onToggle, Color.WHITE)
    private val favoriteIcon: ImageView
    private val favoriteLabel: TextView
    private val timerIcon: ImageView
    private val timerLabel: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(AndroTheme.BG)
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        (playerView.parent as? ViewGroup)?.removeView(playerView)
        playerView.useController = false
        frame.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AndroTheme.dp(context, 4), 0, AndroTheme.dp(context, 4), 0)
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), Color.TRANSPARENT))
            addView(toggle, LayoutParams(AndroTheme.dp(context, 44), AndroTheme.dp(context, 44)))
            addView(text(context, "DIRECTO", TextStyle.Kicker).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(AndroTheme.ACCENT)
                setPadding(AndroTheme.dp(context, 6), 0, AndroTheme.dp(context, 6), 0)
            }, LayoutParams(-2, AndroTheme.dp(context, 24)))
            progress.max = 100
            progress.progressTintList = ColorStateList.valueOf(AndroTheme.ACCENT)
            progress.progressBackgroundTintList = ColorStateList.valueOf(0x80FFFFFF.toInt())
            addView(progress, LayoutParams(0, AndroTheme.dp(context, 3), 1f).apply {
                marginStart = AndroTheme.dp(context, 12)
                marginEnd = AndroTheme.dp(context, 8)
            })
            addView(iconButton(context, R.drawable.ic_maximize, context.getString(R.string.a11y_fullscreen),
                actions.onFullscreen, Color.WHITE), LayoutParams(AndroTheme.dp(context, 44), AndroTheme.dp(context, 44)))
        }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, AndroTheme.dp(context, 48), Gravity.BOTTOM))
        addView(frame, LayoutParams(-1, 0, 1f))

        val programmes = LinearLayout(context).apply { orientation = HORIZONTAL; setBackgroundColor(AndroTheme.BG) }
        programmes.addView(programmeCell(nowHeading, nowTitle), LayoutParams(0, -1, 1f))
        programmes.addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
            LayoutParams(AndroTheme.dp(context, 1), -1))
        programmes.addView(programmeCell(nextHeading, nextTitle), LayoutParams(0, -1, 1f))
        addView(programmes, LayoutParams(-1, AndroTheme.dp(context, 88)))
        addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }, LayoutParams(-1, AndroTheme.dp(context, 2)))

        val actionBar = LinearLayout(context).apply { orientation = HORIZONTAL; setBackgroundColor(AndroTheme.BG) }
        fun addAction(icon: Int, label: String, description: String, callback: () -> Unit): Pair<ImageView, TextView> {
            val cell = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(AndroTheme.dp(context, 4), 0, AndroTheme.dp(context, 4), 0)
                contentDescription = description
                setOnClickListener { callback() }
            }
            val image = ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(AndroTheme.TEXT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val caption = text(context, label, TextStyle.Button).apply {
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(image, LayoutParams(AndroTheme.dp(context, 18), AndroTheme.dp(context, 18)))
            cell.addView(caption, LayoutParams(0, -1, 1f).apply { marginStart = AndroTheme.dp(context, 5) })
            actionBar.addView(cell, LayoutParams(0, -1, 1f))
            return image to caption
        }
        addAction(R.drawable.ic_skip_back, "Anterior", context.getString(R.string.a11y_previous), actions.onPrevious)
        addAction(R.drawable.ic_skip_forward, "Siguiente", context.getString(R.string.a11y_next), actions.onNext)
        val favorite = addAction(R.drawable.ic_heart, "Favorito", context.getString(R.string.a11y_favorite), actions.onFavorite)
        favoriteIcon = favorite.first
        favoriteLabel = favorite.second
        val timer = addAction(R.drawable.ic_moon, "Apagado", "Temporizador de apagado", actions.onTimer)
        timerIcon = timer.first
        timerLabel = timer.second
        addAction(R.drawable.ic_external_link, "Abrir fuera", context.getString(R.string.a11y_open_external), actions.onExternal)
        addView(actionBar, LayoutParams(-1, AndroTheme.dp(context, 44)))
    }

    private fun programmeCell(heading: TextView, title: TextView): View = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(AndroTheme.dp(context, 12), AndroTheme.dp(context, 8), AndroTheme.dp(context, 8), 0)
        title.textSize = 13f
        title.maxLines = 3
        title.ellipsize = TextUtils.TruncateAt.END
        addView(heading, LayoutParams(-1, AndroTheme.dp(context, 20)))
        addView(title, LayoutParams(-1, 0, 1f))
    }

    fun updateProgrammes(current: ChannelPresentation?, next: Programme?) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        val programme = current?.currentProgramme
        nowHeading.text = programme?.let {
            "AHORA · ${time.format(Date(it.startMillis))}–${time.format(Date(it.endMillis))}"
        } ?: "AHORA"
        nowTitle.text = programme?.title ?: if (current == null) "Selecciona un canal" else "En directo"
        nextHeading.text = next?.let { "DESPUÉS · ${time.format(Date(it.startMillis))}" } ?: "DESPUÉS"
        nextTitle.text = next?.title ?: "Sin información siguiente"
        progress.progress = current?.progressPercent ?: 0
        progress.alpha = if (programme == null) 0.4f else 1f
    }

    fun updatePlaying(playing: Boolean) {
        toggle.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        toggle.contentDescription = context.getString(if (playing) R.string.a11y_pause else R.string.a11y_play)
    }

    fun updateFavorite(favorite: Boolean) {
        val color = if (favorite) AndroTheme.ACCENT else AndroTheme.TEXT
        favoriteIcon.imageTintList = ColorStateList.valueOf(color)
        favoriteLabel.setTextColor(color)
    }

    fun updateTimer(minutes: Int) {
        val color = if (minutes > 0) AndroTheme.ACCENT else AndroTheme.TEXT
        timerIcon.imageTintList = ColorStateList.valueOf(color)
        timerLabel.text = if (minutes > 0) "$minutes min" else "Apagado"
        timerLabel.setTextColor(color)
    }
}
