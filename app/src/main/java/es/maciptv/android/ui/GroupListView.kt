package es.maciptv.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import es.maciptv.android.R

data class PortraitGroupItem(val id: String, val title: String, val count: Int, val subtitle: String, val active: Boolean)

/** The same virtualized list is used by Channels and Guide. */
class GroupListView(context: Context, groups: List<PortraitGroupItem>, onGroup: (String) -> Unit) : ListView(context) {
    init {
        dividerHeight = 0
        setBackgroundColor(AndroTheme.BG)
        adapter = object : BaseAdapter() {
            override fun getCount() = groups.size + if (groups.size > 3) 1 else 0
            override fun getItem(position: Int): Any = groups[position - if (position > 3) 1 else 0]
            override fun getItemId(position: Int) = if (position == 3) Long.MIN_VALUE else (getItem(position) as PortraitGroupItem).id.hashCode().toLong()
            override fun hasStableIds() = true
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) = if (position == 3 && groups.size > 3) 1 else 0
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (getItemViewType(position) == 1) return LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(context, "GRUPOS", TextStyle.Kicker).apply {
                        setPadding(dp(16), dp(16), 0, dp(6))
                    }, LayoutParams(-1, dp(39)))
                    addView(rule(), LayoutParams(-1, dp(1)))
                }
                val item = getItem(position) as PortraitGroupItem
                val row = (convertView as? GroupRow) ?: GroupRow(context)
                row.bind(item) { onGroup(item.id) }
                return row
            }
        }
    }

    private fun dp(value: Int) = AndroTheme.dp(context, value)
    private fun rule() = View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }
}

private class GroupRow(context: Context) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val title = text(context, "", TextStyle.ChannelName)
    private val subtitle = text(context, "", TextStyle.Metadata)
    private val count = text(context, "", TextStyle.Metadata)

    init {
        orientation = VERTICAL
        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(12), 0)
        }
        content.addView(icon, LayoutParams(dp(22), dp(22)))
        val label = LinearLayout(context).apply { orientation = VERTICAL }
        title.maxLines = 1; title.ellipsize = TextUtils.TruncateAt.END
        subtitle.maxLines = 1; subtitle.ellipsize = TextUtils.TruncateAt.END
        label.addView(title)
        label.addView(subtitle)
        content.addView(label, LayoutParams(0, -2, 1f).apply { marginStart = dp(12); marginEnd = dp(12) })
        content.addView(count)
        content.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(AndroTheme.NEUTRAL_700)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(18), dp(18)).apply { marginStart = dp(12) })
        addView(content, LayoutParams(-1, dp(61)))
        addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }, LayoutParams(-1, dp(1)))
        minimumHeight = dp(62)
    }

    fun bind(item: PortraitGroupItem, onClick: () -> Unit) {
        icon.setImageResource(when (item.id) {
            "Todos" -> R.drawable.ic_list
            "Favoritos" -> R.drawable.ic_heart
            "Recientes" -> R.drawable.ic_history
            else -> R.drawable.ic_folder_open
        })
        icon.imageTintList = ColorStateList.valueOf(if (item.id == "Favoritos") AndroTheme.ACCENT else AndroTheme.TEXT)
        title.text = item.title
        subtitle.text = item.subtitle
        subtitle.setTextColor(if (item.active) AndroTheme.ACCENT_700 else AndroTheme.NEUTRAL_700)
        count.text = item.count.toString()
        setBackgroundColor(if (item.active) AndroTheme.ACCENT_100 else AndroTheme.BG)
        contentDescription = "${item.title}, ${item.count} canales. ${item.subtitle}"
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = AndroTheme.dp(context, value)
}
