package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import es.maciptv.android.R
import es.maciptv.android.SavedSource
import es.maciptv.android.SourceKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val sources: List<SavedSource>,
    val activeSourceId: String?,
    val resumeLastChannel: Boolean,
    val fillVideo: Boolean,
    val sleepTimerMinutes: Int,
    val logoHttpRestrictedSourceIds: Set<String>
)

data class SettingsActions(
    val onSelect: (SavedSource) -> Unit,
    val onAdd: (SourceKind) -> Unit,
    val onEdit: (SavedSource) -> Unit,
    val onRefresh: (SavedSource) -> Unit,
    val onRemove: (SavedSource) -> Unit,
    val onResume: (Boolean) -> Unit,
    val onFillVideo: (Boolean) -> Unit,
    val onTimer: (Int) -> Unit
)

object SettingsScreen {
    fun render(context: Context, state: SettingsUiState, actions: SettingsActions): View =
        ScrollView(context).apply {
            setBackgroundColor(AndroTheme.BG)
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(AndroTheme.dp(context, 16), AndroTheme.dp(context, 20), AndroTheme.dp(context, 16), AndroTheme.dp(context, 20))
            }
            content.addView(text(context, "PREFERENCIAS", TextStyle.Kicker))
            content.addView(text(context, "Ajustes", TextStyle.ScreenTitle), LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 48)))
            content.addView(text(context, "FUENTES", TextStyle.Kicker), sectionHeader(context))
            if (state.sources.isEmpty()) content.addView(text(context, "Aún no hay fuentes guardadas", TextStyle.Body)
                .apply { setPadding(0, AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 12)) })
            state.sources.forEach { source ->
                val active = source.id == state.activeSourceId
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(AndroTheme.dp(context, 12), AndroTheme.dp(context, 10), AndroTheme.dp(context, 8), AndroTheme.dp(context, 8))
                    setBackgroundColor(if (active) AndroTheme.ACCENT_100 else AndroTheme.SURFACE)
                    setOnClickListener { actions.onSelect(source) }
                }
                val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(text(context, source.name, TextStyle.ChannelName), LinearLayout.LayoutParams(0, AndroTheme.dp(context, 28), 1f))
                if (active) top.addView(text(context, "ACTIVA", TextStyle.Kicker))
                card.addView(top)
                card.addView(text(context, "${kindName(source.config.kind)} · ${source.channelCount} canales", TextStyle.Metadata))
                val updated = if (source.updatedAt > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                    .format(Date(source.updatedAt)) else "Sin actualizar"
                card.addView(text(context, updated, TextStyle.Metadata))
                if (source.id in state.logoHttpRestrictedSourceIds) card.addView(text(context,
                    "Los logos HTTP están bloqueados para esta fuente", TextStyle.Metadata).apply { setTextColor(AndroTheme.ACCENT_700) })
                val actionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
                actionsRow.addView(flatButton(context, "Editar", { actions.onEdit(source) }, AndroTheme.SURFACE))
                actionsRow.addView(iconButton(context, R.drawable.ic_refresh_cw, context.getString(R.string.a11y_refresh),
                    { actions.onRefresh(source) }))
                actionsRow.addView(iconButton(context, R.drawable.ic_trash_2, context.getString(R.string.a11y_remove_source),
                    { actions.onRemove(source) }))
                card.addView(actionsRow)
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = AndroTheme.dp(context, 8) })
            }
            content.addView(text(context, "AÑADIR FUENTE", TextStyle.Kicker), sectionHeader(context))
            val addGrid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val kinds = listOf(
                SourceKind.M3U_URL to "URL M3U",
                SourceKind.M3U_FILE to "Archivo M3U",
                SourceKind.XTREAM to "Xtream Codes",
                SourceKind.DEMO to "Demostración"
            )
            kinds.chunked(2).forEach { rowKinds ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowKinds.forEach { (kind, label) ->
                    row.addView(flatButton(context, label, { actions.onAdd(kind) }, AndroTheme.SURFACE),
                        LinearLayout.LayoutParams(0, AndroTheme.dp(context, 52), 1f).apply { marginEnd = AndroTheme.dp(context, 4) })
                }
                if (rowKinds.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, AndroTheme.dp(context, 52), 1f))
                addGrid.addView(row)
            }
            content.addView(addGrid)
            content.addView(text(context, "REPRODUCCIÓN", TextStyle.Kicker), sectionHeader(context))
            val fitRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            fitRow.addView(text(context, "Ajuste de imagen", TextStyle.Body), LinearLayout.LayoutParams(0, AndroTheme.dp(context, 48), 1f))
            fitRow.addView(flatButton(context, if (state.fillVideo) "Rellenar" else "Sin recortar", {
                actions.onFillVideo(!state.fillVideo)
            }, AndroTheme.SURFACE))
            content.addView(fitRow)
            val resume = CheckBox(context).apply {
                text = "Reanudar el último canal al abrir"
                isChecked = state.resumeLastChannel
                setTextColor(AndroTheme.TEXT)
                minHeight = AndroTheme.dp(context, 48)
                setOnCheckedChangeListener { _, checked -> actions.onResume(checked) }
            }
            content.addView(resume)
            content.addView(text(context, "TEMPORIZADOR", TextStyle.Kicker), sectionHeader(context))
            val timers = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(0, 30, 60, 90).forEach { minutes ->
                val label = if (minutes == 0) "No" else "$minutes min"
                val active = minutes == state.sleepTimerMinutes
                timers.addView(flatButton(context, label, { actions.onTimer(minutes) },
                    if (active) AndroTheme.TEXT else AndroTheme.SURFACE, if (active) Color.WHITE else AndroTheme.TEXT),
                    LinearLayout.LayoutParams(0, AndroTheme.dp(context, 48), 1f).apply { marginEnd = AndroTheme.dp(context, 4) })
            }
            content.addView(timers)
            addView(content)
        }

    private fun sectionHeader(context: Context) = LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 36)).apply {
        topMargin = AndroTheme.dp(context, 20)
    }

    private fun kindName(kind: SourceKind) = when (kind) {
        SourceKind.M3U_URL -> "URL M3U"
        SourceKind.M3U_FILE -> "Archivo M3U"
        SourceKind.XTREAM -> "Xtream"
        SourceKind.DEMO -> "Demo"
    }
}

class SleepTimer(private val onExpire: () -> Unit) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val expiration = Runnable { minutes = 0; onExpire() }
    var minutes: Int = 0
        private set

    fun setMinutes(value: Int) {
        cancel()
        if (value <= 0) return
        minutes = value
        handler.postDelayed(expiration, value * 60_000L)
    }

    fun cancel() {
        handler.removeCallbacks(expiration)
        minutes = 0
    }
}
