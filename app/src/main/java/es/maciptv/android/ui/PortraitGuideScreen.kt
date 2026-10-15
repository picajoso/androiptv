package es.maciptv.android.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import es.maciptv.android.ChannelPresentation
import es.maciptv.android.Programme
import es.maciptv.android.ProgrammeSchedule
import es.maciptv.android.R
import es.maciptv.android.SafeNetwork
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

enum class GuideStep { GROUPS, GRID }

data class PortraitGuideState(
    val step: GuideStep,
    val group: String,
    val groups: GroupListView?,
    val rows: List<GuideGridRow>?,
    val dayOffset: Int,
    val availableDays: Set<Int>,
    val selectedChannelId: String?,
    val sourceId: String?,
    val marks: Set<String>,
    val network: SafeNetwork?
)

data class PortraitGuideActions(
    val onBack: () -> Unit,
    val onNow: () -> Unit,
    val onDay: (Int) -> Unit,
    val onChannel: (ChannelPresentation) -> Unit,
    val onProgramme: (ChannelPresentation, Programme, EpgGridView) -> Unit,
    val onGridReady: (EpgGridView) -> Unit
)

object PortraitGuideScreen {
    fun render(context: Context, state: PortraitGuideState, actions: PortraitGuideActions): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AndroTheme.BG)
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, if (state.step == GuideStep.GRID) 4 else 16), dp(context, 10), dp(context, 12), dp(context, 8))
        }
        if (state.step == GuideStep.GRID) heading.addView(iconButton(context, R.drawable.ic_arrow_left,
            "Volver a las listas", actions.onBack), LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text(context, if (state.step == GuideStep.GROUPS) "Elige una lista" else
            "${state.rows?.count { it.programmes.isNotEmpty() } ?: 0} canales con guía", TextStyle.Kicker))
        titles.addView(text(context, if (state.step == GuideStep.GROUPS) "Guía" else
            if (state.group == "Todos") "Todos los canales" else state.group, TextStyle.ScreenTitle).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        if (state.step == GuideStep.GRID) heading.addView(flatButton(context, "◷ Ahora", actions.onNow).apply {
            contentDescription = "Ir a la programación actual"
            setBackgroundColor(AndroTheme.BG)
        }, LinearLayout.LayoutParams(-2, dp(context, 44)))
        addView(heading, LinearLayout.LayoutParams(-1, dp(context, 64)))
        addView(View(context).apply { setBackgroundColor(AndroTheme.TEXT) }, LinearLayout.LayoutParams(-1, dp(context, 2)))
        if (state.step == GuideStep.GROUPS) {
            state.groups?.let { list ->
                (list.parent as? ViewGroup)?.removeView(list)
                addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            return@apply
        }
        val today = LocalDate.now(ZoneId.systemDefault())
        val dayBar = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val dayRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        (-1..3).filter { it in state.availableDays }.forEach { day ->
            val date = today.plusDays(day.toLong())
            val label = when (day) {
                -1 -> "Ayer"
                0 -> "Hoy"
                1 -> "Mañana"
                else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
            val active = day == state.dayOffset
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 14), 0, dp(context, 14), 0)
                setBackgroundColor(if (active) AndroTheme.ACCENT else AndroTheme.BG)
                addView(text(context, label, TextStyle.Button).apply { setTextColor(if (active) Color.WHITE else AndroTheme.TEXT) })
                addView(text(context, date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())), TextStyle.Metadata).apply {
                    setTextColor(if (active) Color.WHITE else AndroTheme.NEUTRAL_700)
                })
                contentDescription = "$label, ${date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))}"
                setOnClickListener { actions.onDay(day) }
            }
            dayRow.addView(cell, LinearLayout.LayoutParams(dp(context, 96), dp(context, 48)))
            dayRow.addView(View(context).apply { setBackgroundColor(AndroTheme.DIVIDER) }, LinearLayout.LayoutParams(dp(context, 1), dp(context, 48)))
        }
        dayBar.addView(dayRow)
        addView(dayBar, LinearLayout.LayoutParams(-1, dp(context, 48)))
        addView(View(context).apply { setBackgroundColor(AndroTheme.TEXT) }, LinearLayout.LayoutParams(-1, dp(context, 2)))
        val rows = state.rows
        if (rows == null) {
            addView(text(context, "Cargando guía…", TextStyle.Body).apply { setPadding(dp(context, 24), dp(context, 40), 0, 0) })
        } else if (rows.isEmpty()) {
            addView(text(context, "Sin canales en este grupo\nElige otro grupo para ver su programación.", TextStyle.ChannelName).apply {
                setPadding(dp(context, 24), dp(context, 40), dp(context, 24), 0)
            })
        } else {
            val holder = arrayOfNulls<EpgGridView>(1)
            val grid = EpgGridView(context, rows, today.plusDays(state.dayOffset.toLong()), ZoneId.systemDefault(),
                state.selectedChannelId, state.sourceId, state.marks, state.network, actions.onChannel) { channel, programme ->
                holder[0]?.let { actions.onProgramme(channel, programme, it) }
            }
            holder[0] = grid
            actions.onGridReady(grid)
            addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
            val minute = if (state.dayOffset == 0) {
                val now = java.time.LocalTime.now(); (now.hour - 6) * 60 + now.minute
            } else (19 - 6) * 60 + 30
            grid.post { grid.scrollToMinute(minute, false) }
        }
    }

    private fun dp(context: Context, value: Int) = AndroTheme.dp(context, value)

    fun showProgramme(
        context: Context, channel: ChannelPresentation, programme: Programme, dayOffset: Int,
        sourceId: String?, marks: Set<String>, network: SafeNetwork?,
        onMark: () -> Unit, onWatch: () -> Unit, onDismiss: () -> Unit
    ) {
        val dialog = Dialog(context)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(AndroTheme.BG) }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(context, 16), dp(context, 10), dp(context, 4), dp(context, 10)) }
        val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val fallback = InitialsDrawable(channel.displayName)
        if (network != null) LogoLoader.load(channel.channel.logo, network, logo, fallback) else logo.setImageDrawable(fallback)
        header.addView(logo, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(context, 10), 0, 0, 0) }
        labels.addView(text(context, listOfNotNull(channel.displayName, channel.country, channel.quality).joinToString(" · "), TextStyle.ChannelName).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val day = LocalDate.now().plusDays(dayOffset.toLong())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        labels.addView(text(context, "${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).uppercase()} · ${time.format(Date(programme.startMillis))}–${time.format(Date(programme.endMillis))}", TextStyle.Kicker))
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(context, R.drawable.ic_x, "Cerrar ficha del programa", { dialog.dismiss() }), LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))
        body.addView(header, LinearLayout.LayoutParams(-1, dp(context, 64)))
        body.addView(View(context).apply { setBackgroundColor(AndroTheme.TEXT) }, LinearLayout.LayoutParams(-1, dp(context, 2)))
        val now = System.currentTimeMillis()
        val current = programme.startMillis <= now && programme.endMillis > now
        val future = programme.startMillis > now
        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 18)) }
        val badge = buildList {
            if (current) add("AHORA")
            if (ProgrammeSchedule.isLive(programme, now) && current) add("DIRECTO")
            val minutes = ((programme.endMillis - programme.startMillis) / 60_000).toInt()
            add(if (minutes >= 60) "${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else "" else "$minutes min")
        }.joinToString("  ·  ")
        info.addView(text(context, badge, TextStyle.Kicker).apply { setPadding(0, 0, 0, dp(context, 10)) })
        info.addView(text(context, ProgrammeSchedule.displayTitle(programme), TextStyle.ScreenTitle).apply { textSize = 21f })
        val handler = Handler(Looper.getMainLooper())
        var refresh: Runnable? = null
        if (current) {
            val progress = PlaybackControls.programmeProgress(context,
                (((now - programme.startMillis) * 100) / (programme.endMillis - programme.startMillis).coerceAtLeast(1)).toInt())
            info.addView(progress, LinearLayout.LayoutParams(-1, dp(context, 3)).apply { topMargin = dp(context, 12) })
            val remainingLabel = text(context, "", TextStyle.Metadata).apply { setPadding(0, dp(context, 6), 0, 0) }
            info.addView(remainingLabel)
            refresh = object : Runnable {
                override fun run() {
                    val instant = System.currentTimeMillis()
                    val remaining = ((programme.endMillis - instant) / 60_000).coerceAtLeast(0)
                    remainingLabel.text = "quedan ${remaining / 60} h ${remaining % 60} min"
                    progress.progress = (((instant - programme.startMillis) * 100) /
                        (programme.endMillis - programme.startMillis).coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    handler.postDelayed(this, 60_000)
                }
            }.also { it.run() }
        }
        if (programme.details.isNotBlank()) info.addView(text(context, programme.details, TextStyle.Body).apply { setPadding(0, dp(context, 10), 0, 0) })
        body.addView(info)
        body.addView(View(context).apply { setBackgroundColor(AndroTheme.TEXT) }, LinearLayout.LayoutParams(-1, dp(context, 2)))
        val key = sourceId?.let { ProgrammeSchedule.reminderKey(it, channel.channel.id, programme.startMillis) }
        val marked = key != null && key in marks
        val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(icon: Int, label: String, accented: Boolean, click: () -> Unit): View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 8), 0)
            setBackgroundColor(if (accented) AndroTheme.ACCENT else AndroTheme.BG)
            contentDescription = label
            setOnClickListener { click() }
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(if (accented) Color.WHITE else AndroTheme.TEXT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)))
            addView(text(context, label, TextStyle.Button).apply {
                setTextColor(if (accented) Color.WHITE else AndroTheme.TEXT)
                setPadding(dp(context, 7), 0, 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        footer.addView(action(if (!future) R.drawable.ic_x else if (marked) R.drawable.ic_bell_off else R.drawable.ic_bell,
            if (!future) "Cerrar" else if (marked) "Quitar aviso" else "Recordar", false) {
            if (future) onMark()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(context, 56), 1f))
        footer.addView(action(R.drawable.ic_play, if (current) "Ver ahora" else "Ver canal", true) {
            dialog.dismiss(); onWatch()
        }, LinearLayout.LayoutParams(0, dp(context, 56), 1f))
        body.addView(footer)
        dialog.setContentView(body)
        dialog.setOnDismissListener {
            refresh?.let(handler::removeCallbacks)
            onDismiss()
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
