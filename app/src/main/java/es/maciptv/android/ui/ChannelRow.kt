package es.maciptv.android.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.R
import es.maciptv.android.SafeNetwork

class ChannelRow(context: Context) : LinearLayout(context) {
    private val logo = ImageView(context)
    private val name = text(context, "", TextStyle.ChannelName)
    private val programme = text(context, "", TextStyle.Metadata)
    private val endLabel = text(context, "", TextStyle.Metadata)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val favorite = ImageButton(context)
    private val metadata = text(context, "", TextStyle.Metadata)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(AndroTheme.dp(context, 16), AndroTheme.dp(context, 4), AndroTheme.dp(context, 10), AndroTheme.dp(context, 4))
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(logo, LayoutParams(AndroTheme.dp(context, 52), AndroTheme.dp(context, 52)))
        val textColumn = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val titleRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(name, LayoutParams(0, AndroTheme.dp(context, 22), 1f))
        titleRow.addView(metadata, LayoutParams(-2, AndroTheme.dp(context, 22)))
        textColumn.addView(titleRow, LayoutParams(-1, AndroTheme.dp(context, 22)))
        textColumn.addView(programme, LayoutParams(-1, AndroTheme.dp(context, 18)))
        val progressRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progress.max = 100
        progress.progressDrawable = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(AndroTheme.NEUTRAL_300); cornerRadius = 0f },
            ClipDrawable(GradientDrawable().apply { setColor(AndroTheme.ACCENT); cornerRadius = 0f }, Gravity.START, ClipDrawable.HORIZONTAL)
        )).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        progressRow.addView(progress, LayoutParams(0, AndroTheme.dp(context, 2), 1f))
        progressRow.addView(endLabel, LayoutParams(-2, AndroTheme.dp(context, 16)).apply { marginStart = AndroTheme.dp(context, 6) })
        textColumn.addView(progressRow, LayoutParams(-1, AndroTheme.dp(context, 16)))
        addView(textColumn, LayoutParams(0, -2, 1f).apply { marginStart = AndroTheme.dp(context, 12) })
        favorite.contentDescription = context.getString(R.string.a11y_favorite)
        favorite.setImageResource(R.drawable.ic_heart)
        favorite.imageTintList = android.content.res.ColorStateList.valueOf(AndroTheme.NEUTRAL_600)
        favorite.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 0f }
        favorite.minimumWidth = AndroTheme.dp(context, 44)
        favorite.minimumHeight = AndroTheme.dp(context, 44)
        addView(favorite, LayoutParams(AndroTheme.dp(context, 44), AndroTheme.dp(context, 44)))
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 0f }
        isFocusable = true
        minimumHeight = AndroTheme.dp(context, 68)
    }

    fun bind(
        item: ChannelPresentation,
        isFavorite: Boolean,
        selected: Boolean,
        network: SafeNetwork?,
        landscape: Boolean,
        onSelect: () -> Unit,
        onFavorite: () -> Unit,
        onHttpBlocked: () -> Unit
    ) {
        val logoSize = if (landscape) 38 else 52
        val lp = logo.layoutParams
        lp.width = AndroTheme.dp(context, logoSize)
        lp.height = AndroTheme.dp(context, logoSize)
        logo.layoutParams = lp
        name.text = item.displayName
        val tags = listOfNotNull(item.country, item.quality).joinToString("  ")
        metadata.text = tags
        val current = item.currentProgramme
        programme.text = current?.title.orEmpty()
        progress.progress = item.progressPercent ?: 0
        progress.visibility = if (current == null) View.INVISIBLE else View.VISIBLE
        endLabel.text = item.programmeEndLabel.orEmpty()
        endLabel.visibility = if (current == null) View.INVISIBLE else View.VISIBLE
        favorite.setImageResource(R.drawable.ic_heart)
        favorite.imageTintList = android.content.res.ColorStateList.valueOf(if (isFavorite) AndroTheme.ACCENT else AndroTheme.NEUTRAL_600)
        setBackgroundColor(if (selected) AndroTheme.ACCENT_100 else Color.TRANSPARENT)
        setOnClickListener { onSelect() }
        favorite.setOnClickListener { onFavorite() }
        val fallback = InitialsDrawable(item.displayName)
        logo.setImageDrawable(fallback)
        val width = if (landscape) 38 else 52
        logo.layoutParams = logo.layoutParams.apply { this.width = AndroTheme.dp(context, width); height = AndroTheme.dp(context, width) }
        if (network != null && item.channel.logo.isNotBlank()) LogoLoader.load(item.channel.logo, network, logo, fallback, onHttpBlocked)
    }
}
