package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import es.maciptv.android.R

object PlaybackControls {
    fun transport(
        context: Context,
        playing: Boolean,
        onToggle: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        onFullscreen: () -> Unit,
        onBack: (() -> Unit)? = null
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(AndroTheme.dp(context, 8), 0, AndroTheme.dp(context, 8), 0)
        onBack?.let { addView(iconButton(context, R.drawable.ic_arrow_left, context.getString(R.string.a11y_back), it)) }
        addView(iconButton(context, R.drawable.ic_skip_back, context.getString(R.string.a11y_previous), onPrevious))
        val toggle = iconButton(context, if (playing) R.drawable.ic_pause else R.drawable.ic_play,
            context.getString(if (playing) R.string.a11y_pause else R.string.a11y_play), onToggle)
        addView(toggle)
        addView(iconButton(context, R.drawable.ic_skip_forward, context.getString(R.string.a11y_next), onNext))
        addView(text(context, "DIRECTO", TextStyle.Kicker), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = AndroTheme.dp(context, 12)
        })
        addView(iconButton(context, R.drawable.ic_maximize, context.getString(R.string.a11y_fullscreen), onFullscreen))
    }

    fun programmeProgress(context: Context, percent: Int?): ProgressBar = ProgressBar(
        context, null, android.R.attr.progressBarStyleHorizontal
    ).apply {
        max = 100
        progress = percent ?: 0
        progressTintList = android.content.res.ColorStateList.valueOf(AndroTheme.ACCENT)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(AndroTheme.NEUTRAL_300)
        alpha = if (percent == null) 0.35f else 1f
    }
}
