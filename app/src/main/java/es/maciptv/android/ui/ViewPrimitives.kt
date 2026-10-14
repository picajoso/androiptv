package es.maciptv.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import es.maciptv.android.R

enum class TextStyle(val sp: Float, val weight: Int, val color: Int) {
    ScreenTitle(24f, 800, AndroTheme.TEXT),
    ChannelName(15f, 800, AndroTheme.TEXT),
    Body(13f, 400, AndroTheme.TEXT),
    Button(13f, 600, AndroTheme.TEXT),
    Kicker(10f, 400, AndroTheme.ACCENT_700),
    Metadata(11f, 400, AndroTheme.NEUTRAL_700)
}

fun text(context: Context, value: CharSequence, style: TextStyle): TextView = TextView(context).apply {
    text = value
    textSize = style.sp
    setTextColor(style.color)
    typeface = ResourcesCompat.getFont(context, R.font.archivo_variable) ?: Typeface.DEFAULT
    fontVariationSettings = "'wght' ${style.weight}"
    includeFontPadding = false
    gravity = Gravity.CENTER_VERTICAL or Gravity.START
}

fun flatButton(
    context: Context,
    value: CharSequence,
    onClick: () -> Unit,
    backgroundColor: Int = AndroTheme.BG,
    foregroundColor: Int = AndroTheme.TEXT
): Button = Button(context).apply {
    text = value
    setTextColor(foregroundColor)
    textSize = TextStyle.Button.sp
    typeface = ResourcesCompat.getFont(context, R.font.archivo_variable) ?: Typeface.DEFAULT
    fontVariationSettings = "'wght' ${TextStyle.Button.weight}"
    gravity = Gravity.CENTER_VERTICAL or Gravity.START
    setAllCaps(false)
    minWidth = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
    minimumWidth = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
    minHeight = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
    minimumHeight = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
    setPadding(AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 12), 0)
    background = GradientDrawable().apply { setColor(backgroundColor); cornerRadius = 0f }
    backgroundTintList = ColorStateList.valueOf(backgroundColor)
    setOnClickListener { onClick() }
}

fun iconButton(context: Context, drawableId: Int, description: String, onClick: () -> Unit,
    tintColor: Int = AndroTheme.TEXT): ImageButton =
    ImageButton(context).apply {
        contentDescription = description
        setImageResource(drawableId)
        imageTintList = ColorStateList.valueOf(tintColor)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 0f }
        minimumWidth = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
        minimumHeight = AndroTheme.dp(context, AndroTheme.MIN_TOUCH_DP)
        setOnClickListener { onClick() }
    }
