package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
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
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AndroTheme.BG)
            val brandHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(AndroTheme.ACCENT)
                setPadding(AndroTheme.dp(context, 12), 0, 0, 0)
            }
            brandHeader.addView(text(context, "AndroIPTV", TextStyle.ScreenTitle).apply {
                textSize = 16f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -1, 1f))
            val menuButton = iconButton(context, R.drawable.ic_list,
                "Opciones: Guía, Ajustes y actualizar", {}, Color.WHITE)
            menuButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menu.add(0, 1, 0, "Guía")
                    menu.add(0, 2, 1, "Ajustes")
                    menu.add(0, 3, 2, "Actualizar lista")
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> actions.onGuide()
                            2 -> actions.onSettings()
                            3 -> actions.onRefresh()
                        }
                        true
                    }
                    show()
                }
            }
            brandHeader.addView(menuButton, LinearLayout.LayoutParams(AndroTheme.dp(context, 44), -1))

            if (state.screenContent != null) {
                (state.screenContent.parent as? android.view.ViewGroup)?.removeView(state.screenContent)
                val contentColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val screenHeader = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(AndroTheme.ACCENT)
                        actions.onBack?.let { back ->
                            addView(iconButton(context, R.drawable.ic_arrow_left,
                                context.getString(R.string.a11y_back), back, Color.WHITE))
                        }
                        addView(text(context, state.header, TextStyle.Button).apply {
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }, LinearLayout.LayoutParams(0, -1, 1f))
                    }
                    addView(screenHeader, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 44)))
                    addView(state.screenContent, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                addView(contentColumn, LinearLayout.LayoutParams(0, -1, 1f))
                addView(View(context).apply { setBackgroundColor(AndroTheme.ACCENT) },
                    LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
                addView(playerHost, LinearLayout.LayoutParams(0, -1, 1f))
            } else {
                val groupPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(AndroTheme.BG)
                }
                groupPanel.addView(brandHeader, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 44)))
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
                addView(groupPanel, LinearLayout.LayoutParams(AndroTheme.dp(context, 170), -1))
                addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                    LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))

                val channelPanel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(AndroTheme.BG)
                }
                val searchHeader = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(AndroTheme.ACCENT)
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
                channelPanel.addView(searchHeader, LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 44)))
                (state.channelList.parent as? android.view.ViewGroup)?.removeView(state.channelList)
                channelPanel.addView(state.channelList, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(channelPanel, LinearLayout.LayoutParams(AndroTheme.dp(context, 250), -1))
                addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) },
                    LinearLayout.LayoutParams(AndroTheme.dp(context, 2), -1))
                addView(playerHost, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
}
