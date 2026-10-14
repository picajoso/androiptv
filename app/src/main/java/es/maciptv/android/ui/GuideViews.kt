package es.maciptv.android.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.GuideDayFilter
import es.maciptv.android.Programme
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class GuideUiState(
    val channel: ChannelPresentation?,
    val channels: List<ChannelPresentation>,
    val programmes: List<Programme>,
    val dayOffset: Int
)

data class GuideActions(
    val onChannel: (ChannelPresentation) -> Unit,
    val onDay: (Int) -> Unit,
    val onWatch: () -> Unit
)

object GuideViews {
    fun render(context: Context, state: GuideUiState, actions: GuideActions): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroTheme.BG)
            addView(text(context, "GUÍA", TextStyle.Kicker), LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 28)))
            addView(text(context, state.channel?.displayName ?: "Selecciona un canal", TextStyle.ScreenTitle),
                LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 48)))
            val channelPicker = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val channelScroll = android.widget.HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
            val channelChoices = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            state.channels.forEach { channel ->
                channelChoices.addView(flatButton(context, channel.displayName, { actions.onChannel(channel) },
                    if (channel.channel.id == state.channel?.channel?.id) AndroTheme.TEXT else AndroTheme.SURFACE,
                    if (channel.channel.id == state.channel?.channel?.id) ColorCompat.WHITE else AndroTheme.TEXT))
            }
            channelScroll.addView(channelChoices)
            channelPicker.addView(channelScroll, LinearLayout.LayoutParams(0, AndroTheme.dp(context, 44), 1f))
            channelPicker.addView(flatButton(context, "Ver canal", actions.onWatch, AndroTheme.ACCENT, ColorCompat.WHITE))
            addView(channelPicker)
            val dayPicker = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            dayPicker.addView(flatButton(context, "Hoy", { actions.onDay(0) }, if (state.dayOffset == 0) AndroTheme.TEXT else AndroTheme.SURFACE,
                if (state.dayOffset == 0) ColorCompat.WHITE else AndroTheme.TEXT), LinearLayout.LayoutParams(0, AndroTheme.dp(context, 44), 1f))
            dayPicker.addView(flatButton(context, "Mañana", { actions.onDay(1) }, if (state.dayOffset == 1) AndroTheme.TEXT else AndroTheme.SURFACE,
                if (state.dayOffset == 1) ColorCompat.WHITE else AndroTheme.TEXT), LinearLayout.LayoutParams(0, AndroTheme.dp(context, 44), 1f))
            addView(dayPicker)
            val items = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            state.programmes.forEach { programme ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, AndroTheme.dp(context, 10), 0, AndroTheme.dp(context, 10)) }
                row.addView(text(context, "${timeFormat.format(Date(programme.startMillis))} – ${timeFormat.format(Date(programme.endMillis))}", TextStyle.Metadata))
                row.addView(text(context, programme.title, TextStyle.ChannelName))
                if (programme.details.isNotBlank()) row.addView(text(context, programme.details, TextStyle.Body))
                items.addView(row)
                items.addView(android.view.View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 1)))
            }
            if (state.programmes.isEmpty()) items.addView(text(context, "No hay programas para este día", TextStyle.Body))
            addView(ScrollView(context).apply { addView(items) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
}

private object ColorCompat { const val WHITE = android.graphics.Color.WHITE }
