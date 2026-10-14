package es.maciptv.android.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import es.maciptv.android.R

object ConfirmationDialog {
    fun show(context: Context, title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroTheme.dp(context, 24), AndroTheme.dp(context, 20), AndroTheme.dp(context, 24), AndroTheme.dp(context, 16))
            setBackgroundColor(AndroTheme.BG)
            addView(text(context, title, TextStyle.ScreenTitle))
            addView(text(context, message, TextStyle.Body).apply { setPadding(0, AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 20)) })
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            actions.addView(flatButton(context, "Cancelar", { dialog.dismiss() }, AndroTheme.SURFACE))
            actions.addView(flatButton(context, confirmLabel, { dialog.dismiss(); onConfirm() }, AndroTheme.ACCENT, android.graphics.Color.WHITE))
            addView(actions)
        }
        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.CENTER)
            attributes = attributes.apply { dimAmount = 0.5f }
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
