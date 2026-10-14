package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color

object AndroTheme {
    const val BG = 0xFFF3F2F2.toInt()
    const val SURFACE = 0xFFEAE9E9.toInt()
    const val TEXT = 0xFF201E1D.toInt()
    const val ACCENT = 0xFFEC3013.toInt()
    const val ACCENT_100 = 0xFFFFF2EF.toInt()
    const val ACCENT_400 = 0xFFFF9783.toInt()
    const val ACCENT_600 = 0xFFDD2B0F.toInt()
    const val ACCENT_700 = 0xFFAE1800.toInt()
    const val DIVIDER = 0x66201E1D
    const val NEUTRAL_200 = 0xFFEAE7E7.toInt()
    const val NEUTRAL_300 = 0xFFD7D3D3.toInt()
    const val NEUTRAL_400 = 0xFFBAB6B6.toInt()
    const val NEUTRAL_600 = 0xFF7D7979.toInt()
    const val NEUTRAL_700 = 0xFF605D5D.toInt()
    const val NEUTRAL_800 = 0xFF444141.toInt()
    const val NEUTRAL_900 = 0xFF2D2B2B.toInt()
    const val DARK_BG = 0xFF1A1817.toInt()
    const val DARK_ROW_CURRENT = 0xFF3A1A14.toInt()
    const val ON_DARK = 0xFFF3F2F2.toInt()
    const val VIDEO = Color.BLACK

    val spacing = intArrayOf(4, 8, 12, 16, 24, 32)
    const val ROW_DIVIDER_DP = 1
    const val SECTION_DIVIDER_DP = 2
    const val MIN_TOUCH_DP = 44

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
