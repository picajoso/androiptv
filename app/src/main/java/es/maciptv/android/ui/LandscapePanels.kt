package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import es.maciptv.android.R

data class GroupItem(val name: String, val count: Int)

data class LandscapeUiState(
    val header: String,
    val groups: List<GroupItem>,
    val selectedGroup: String,
    val searchField: EditText,
    val channelList: ListView,
    val screenContent: View? = null
)

data class LandscapeActions(
    val onGroup: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onSettings: () -> Unit,
    val onGuide: () -> Unit,
    val onBack: (() -> Unit)? = null
)

object LandscapePanels {
    fun render(context: Context, state: LandscapeUiState, actions: LandscapeActions, playerHost: View): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroTheme.BG)
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(AndroTheme.ACCENT)
            }
            val brand = text(context, "AndroIPTV", TextStyle.ScreenTitle).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(AndroTheme.dp(context, 16), 0, 0, 0)
            }
            header.addView(brand, LinearLayout.LayoutParams(AndroTheme.dp(context, 170), -1))
            header.addView(View(context).apply { setBackgroundColor(0x99FFFFFF.toInt()) },
                LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
            val searchHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(AndroTheme.dp(context, 6), 0, AndroTheme.dp(context, 6), 0)
                addView(iconButton(context, R.drawable.ic_search, context.getString(R.string.a11y_search),
                    { state.searchField.requestFocus() }, Color.WHITE))
                (state.searchField.parent as? android.view.ViewGroup)?.removeView(state.searchField)
                state.searchField.setTextColor(Color.WHITE)
                state.searchField.setHintTextColor(0xCCFFFFFF.toInt())
                state.searchField.textSize = 13f
                state.searchField.background = null
                addView(state.searchField, LinearLayout.LayoutParams(0, -1, 1f))
            }
            header.addView(searchHeader, LinearLayout.LayoutParams(AndroTheme.dp(context, 250), -1))
            header.addView(View(context).apply { setBackgroundColor(0x99FFFFFF.toInt()) },
                LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
            val sourceHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 4), 0)
            }
            actions.onBack?.let {
                sourceHeader.addView(iconButton(context, R.drawable.ic_arrow_left, context.getString(R.string.a11y_back), it, Color.WHITE))
            }
            sourceHeader.addView(text(context, state.header, TextStyle.Button).apply {
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -1, 1f))
            sourceHeader.addView(iconButton(context, R.drawable.ic_refresh_cw, context.getString(R.string.a11y_refresh), actions.onRefresh, Color.WHITE))
            sourceHeader.addView(iconButton(context, R.drawable.ic_settings, context.getString(R.string.a11y_settings), actions.onSettings, Color.WHITE))
            sourceHeader.addView(iconButton(context, R.drawable.ic_calendar_days, context.getString(R.string.a11y_guide), actions.onGuide, Color.WHITE))
            header.addView(sourceHeader, LinearLayout.LayoutParams(0, -1, 1f))
            addView(header, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 44)))

            if (state.screenContent != null) {
                (state.screenContent.parent as? android.view.ViewGroup)?.removeView(state.screenContent)
                val contentAndPlayer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(state.screenContent, LinearLayout.LayoutParams(0, -1, 1f))
                    addView(View(context).apply { setBackgroundColor(AndroTheme.ACCENT) },
                        LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
                    addView(playerHost, LinearLayout.LayoutParams(0, -1, 1f))
                }
                addView(contentAndPlayer, LinearLayout.LayoutParams(-1, 0, 1f))
            } else {
                val columns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                val groupPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(AndroTheme.BG)
                }
                val groupScroll = ScrollView(context)
                val groupItems = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                state.groups.forEach { item ->
                    val active = item.name == state.selectedGroup
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(AndroTheme.dp(context, 14), 0, AndroTheme.dp(context, 12), 0)
                        setBackgroundColor(if (active) AndroTheme.TEXT else AndroTheme.BG)
                        setOnClickListener { actions.onGroup(item.name) }
                        contentDescription = "${item.name}, ${item.count} canales"
                        addView(text(context, item.name, TextStyle.Button).apply {
                            setTextColor(if (active) Color.WHITE else AndroTheme.TEXT)
                            maxLines = 2
                        }, LinearLayout.LayoutParams(0, -1, 1f))
                        addView(text(context, item.count.toString(), TextStyle.Metadata).apply {
                            setTextColor(if (active) Color.WHITE else AndroTheme.NEUTRAL_700)
                        })
                    }
                    groupItems.addView(row, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 40)))
                    groupItems.addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                        LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 1)))
                }
                groupScroll.addView(groupItems)
                groupPanel.addView(groupScroll, LinearLayout.LayoutParams(-1, 0, 1f))
                columns.addView(groupPanel, LinearLayout.LayoutParams(AndroTheme.dp(context, 170), -1))
                columns.addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                    LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))

                val channelPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(AndroTheme.BG)
                }
                (state.channelList.parent as? android.view.ViewGroup)?.removeView(state.channelList)
                channelPanel.addView(state.channelList, LinearLayout.LayoutParams(-1, 0, 1f))
                columns.addView(channelPanel, LinearLayout.LayoutParams(AndroTheme.dp(context, 250), -1))
                columns.addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                    LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
                columns.addView(playerHost, LinearLayout.LayoutParams(0, -1, 1f))
                addView(columns, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }
}
