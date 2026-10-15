package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.EditText
import android.widget.TextView
import androidx.media3.ui.PlayerView
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.R

data class ChannelsUiState(
    val preview: ChannelPresentation?,
    val sourceHeader: String,
    val groups: List<String>,
    val selectedGroup: String,
    val search: String,
    val searchOpen: Boolean,
    val searchView: EditText,
    val list: ListView,
    val playerView: PlayerView,
    val loading: Boolean,
    val playing: Boolean,
    val step: PortraitStep,
    val groupsList: View?,
    val visibleCount: Int
)

enum class PortraitStep { GROUPS, CHANNELS }

data class ChannelsActions(
    val onSource: () -> Unit,
    val onGroup: (String) -> Unit,
    val onSearch: (String, Boolean) -> Unit,
    val onSearchToggle: () -> Unit,
    val onPreview: () -> Unit,
    val onToggle: () -> Unit,
    val onFullscreen: () -> Unit,
    val onBackToGroups: () -> Unit
)

data class PlayerUiState(val channel: ChannelPresentation?, val playerView: PlayerView, val playing: Boolean)
data class PlayerActions(
    val onBack: () -> Unit,
    val onToggle: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onFullscreen: () -> Unit
)

enum class AppTab { CHANNELS, GUIDE, SETTINGS }

object PortraitScreens {
    fun renderChannels(context: Context, state: ChannelsUiState, actions: ChannelsActions): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroTheme.BG)
            state.preview?.let { presentation ->
                val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
                (state.playerView.parent as? android.view.ViewGroup)?.removeView(state.playerView)
                frame.addView(state.playerView, FrameLayout.LayoutParams(-1, -1))
                state.playerView.setOnClickListener { actions.onPreview() }
                frame.addView(iconButton(context, R.drawable.ic_maximize, context.getString(R.string.a11y_fullscreen), actions.onFullscreen,
                    Color.WHITE),
                    FrameLayout.LayoutParams(AndroTheme.dp(context, 48), AndroTheme.dp(context, 48), Gravity.TOP or Gravity.END))
                frame.addView(iconButton(context, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    context.getString(if (state.playing) R.string.a11y_pause else R.string.a11y_play), actions.onToggle, Color.WHITE),
                    FrameLayout.LayoutParams(AndroTheme.dp(context, 52), AndroTheme.dp(context, 52), Gravity.BOTTOM or Gravity.END))
                addView(frame, LinearLayout.LayoutParams(-1, context.resources.displayMetrics.widthPixels * 9 / 16))
                addView(PlaybackControls.programmeProgress(context, presentation.progressPercent), LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 2)))
            } ?: run {
                addView(text(context, "Selecciona un canal para reproducir", TextStyle.Body), LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 72)))
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(AndroTheme.dp(context, 16), AndroTheme.dp(context, 10), AndroTheme.dp(context, 16), AndroTheme.dp(context, 8))
            }
            val heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            if (state.step == PortraitStep.CHANNELS) heading.addView(iconButton(context, R.drawable.ic_arrow_left,
                "Volver a las listas", actions.onBackToGroups), LinearLayout.LayoutParams(AndroTheme.dp(context, 44), AndroTheme.dp(context, 44)))
            val headingText = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setOnClickListener { actions.onSource() } }
            val searchingAll = state.step == PortraitStep.GROUPS && state.search.isNotBlank()
            headingText.addView(text(context, when {
                searchingAll -> "Resultados en todas las listas"
                state.step == PortraitStep.CHANNELS -> "${state.visibleCount} canales"
                else -> state.sourceHeader
            }, TextStyle.Kicker).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            headingText.addView(text(context, if (state.step == PortraitStep.GROUPS) "Listas" else
                if (state.selectedGroup == "Todos") "Todos los canales" else state.selectedGroup, TextStyle.ScreenTitle).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
            heading.addView(headingText, LinearLayout.LayoutParams(0, -1, 1f))
            heading.addView(iconButton(context, R.drawable.ic_search, context.getString(R.string.a11y_search), actions.onSearchToggle))
            header.addView(heading, LinearLayout.LayoutParams(-1, -1))
            addView(header, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 64)))
            addView(View(context).apply { setBackgroundColor(AndroTheme.TEXT) }, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 2)))
            if (state.searchOpen) {
            (state.searchView.parent as? android.view.ViewGroup)?.removeView(state.searchView)
            addView(state.searchView, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 48)))
            }
            if (state.loading) addView(text(context, "Cargando canales…", TextStyle.Body))
            val body = if (state.step == PortraitStep.GROUPS && state.search.isBlank()) state.groupsList else state.list
            body?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                addView(it, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }

    fun renderPlayer(context: Context, state: PlayerUiState, actions: PlayerActions): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroTheme.BG)
            val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            (state.playerView.parent as? android.view.ViewGroup)?.removeView(state.playerView)
            state.playerView.setOnClickListener(null)
            frame.addView(state.playerView, FrameLayout.LayoutParams(-1, -1))
            addView(frame, LinearLayout.LayoutParams(-1, context.resources.displayMetrics.widthPixels * 9 / 16))
            state.channel?.let { channel ->
                addView(text(context, channel.displayName, TextStyle.ScreenTitle).apply { setPadding(AndroTheme.dp(context, 16), AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 4)) })
                addView(text(context, channel.currentProgramme?.title ?: "En directo", TextStyle.Body).apply { setPadding(AndroTheme.dp(context, 16), 0, 0, AndroTheme.dp(context, 8)) })
                addView(PlaybackControls.programmeProgress(context, channel.progressPercent), LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 3)))
            }
            val controls = PlaybackControls.transport(context, state.playing, actions.onToggle, actions.onPrevious, actions.onNext, actions.onFullscreen, actions.onBack)
            addView(controls, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 56)))
            val filler = View(context)
            addView(filler, LinearLayout.LayoutParams(-1, 0, 1f))
        }

    fun bottomTabs(context: Context, active: AppTab, onTab: (AppTab) -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(AndroTheme.DIVIDER)
        val options = listOf(
            Triple(AppTab.CHANNELS, R.drawable.ic_tv, "Canales"),
            Triple(AppTab.GUIDE, R.drawable.ic_calendar_days, "Guía"),
            Triple(AppTab.SETTINGS, R.drawable.ic_settings, "Ajustes")
        )
        options.forEachIndexed { index, (tab, icon, label) ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(AndroTheme.dp(context, 14), 0, AndroTheme.dp(context, 8), 0)
                setBackgroundColor(if (tab == active) AndroTheme.ACCENT else AndroTheme.BG)
                val iconView = android.widget.ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = android.content.res.ColorStateList.valueOf(if (tab == active) Color.WHITE else AndroTheme.TEXT)
                    contentDescription = null
                }
                addView(iconView, LinearLayout.LayoutParams(AndroTheme.dp(context, 18), AndroTheme.dp(context, 18)))
                addView(text(context, label, TextStyle.Button).apply {
                    setTextColor(if (tab == active) Color.WHITE else AndroTheme.TEXT)
                    setPadding(AndroTheme.dp(context, 8), 0, 0, 0)
                }, LinearLayout.LayoutParams(-2, -1))
                setOnClickListener { onTab(tab) }
            }
            addView(item, LinearLayout.LayoutParams(0, AndroTheme.dp(context, 56), 1f))
            if (index < options.lastIndex) addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                LinearLayout.LayoutParams(AndroTheme.dp(context, 1), AndroTheme.dp(context, 56)))
        }
    }
}
