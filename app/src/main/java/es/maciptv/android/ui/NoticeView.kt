package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

object NoticeView {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        val view = TextView(context).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AndroTheme.dp(context, 16), AndroTheme.dp(context, 12), AndroTheme.dp(context, 16), AndroTheme.dp(context, 12))
            background = GradientDrawable().apply { setColor(AndroTheme.NEUTRAL_900); cornerRadius = 0f }
            elevation = AndroTheme.dp(context, 4).toFloat()
        }
        Toast(context).apply { this.duration = duration; this.view = view; show() }
    }
}
