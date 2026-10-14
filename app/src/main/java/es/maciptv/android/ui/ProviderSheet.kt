package es.maciptv.android.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import es.maciptv.android.R
import es.maciptv.android.SavedSource
import es.maciptv.android.SourceConfig
import es.maciptv.android.SourceKind
import java.net.URI
import java.util.UUID

object ProviderSheet {
    fun show(
        context: Context,
        kind: SourceKind,
        existing: SavedSource? = null,
        onSave: (SavedSource) -> Unit,
        onPickFile: (String, SavedSource?) -> Unit
    ) {
        if (kind == SourceKind.DEMO) {
            onSave(existing?.copy(config = SourceConfig(SourceKind.DEMO), updatedAt = System.currentTimeMillis())
                ?: SavedSource(UUID.randomUUID().toString(), "Demostración", SourceConfig(SourceKind.DEMO), updatedAt = System.currentTimeMillis()))
            return
        }
        if (kind == SourceKind.M3U_FILE) {
            onPickFile(existing?.name ?: "Mi lista", existing)
            return
        }
        val config = existing?.config?.takeIf { it.kind == kind } ?: SourceConfig(kind)
        val dialog = Dialog(context)
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroTheme.BG)
            setPadding(AndroTheme.dp(context, 20), AndroTheme.dp(context, 18), AndroTheme.dp(context, 20), AndroTheme.dp(context, 12))
        }
        val heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(context, if (existing == null) "Añadir fuente" else "Editar fuente", TextStyle.ScreenTitle),
            LinearLayout.LayoutParams(0, AndroTheme.dp(context, 48), 1f))
        heading.addView(iconButton(context, R.drawable.ic_x, context.getString(R.string.a11y_close), { dialog.dismiss() }))
        form.addView(heading)
        val nameField = field(context, "Nombre de la fuente", existing?.name ?: if (kind == SourceKind.XTREAM) "Xtream" else "Mi lista")
        form.addView(nameField)
        val address = field(context, if (kind == SourceKind.XTREAM) "URL del servidor" else "URL M3U", config.address)
        address.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        form.addView(address)
        val username = if (kind == SourceKind.XTREAM) field(context, "Usuario", config.username).also(form::addView) else null
        val password = if (kind == SourceKind.XTREAM) field(context, "Contraseña", config.password).also {
            it.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            form.addView(it)
        } else null
        val xmltv = if (kind == SourceKind.M3U_URL) field(context, "URL XMLTV (opcional)", config.xmltv).also(form::addView) else null
        val allowHttp = CheckBox(context).apply { text = "Permitir HTTP para esta fuente"; isChecked = config.allowHttp }
        val localServer = CheckBox(context).apply { text = "Permitir servidores locales"; isChecked = config.localServer }
        form.addView(allowHttp)
        form.addView(localServer)
        val error = text(context, "", TextStyle.Body).apply { setTextColor(AndroTheme.ACCENT_700) }
        form.addView(error)
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        buttons.addView(flatButton(context, "Cancelar", { dialog.dismiss() }, AndroTheme.SURFACE))
        buttons.addView(flatButton(context, "Guardar", {
            val sourceName = nameField.text.toString().trim()
            val url = address.text.toString().trim()
            val uri = runCatching { URI(url) }.getOrNull()
            val problem = when {
                sourceName.isBlank() -> "Introduce un nombre para la fuente"
                url.isBlank() -> "Introduce la URL del proveedor"
                uri?.host.isNullOrBlank() || uri?.userInfo != null -> "La URL del proveedor no es válida"
                uri?.scheme !in listOf("http", "https") -> "La URL debe empezar por http:// o https://"
                uri.scheme.equals("http", true) && !allowHttp.isChecked -> "Activa Permitir HTTP para esta fuente"
                kind == SourceKind.XTREAM && (username?.text.isNullOrBlank() || password?.text.isNullOrBlank()) -> "Introduce el usuario y la contraseña"
                else -> null
            }
            if (problem != null) {
                error.text = problem
            } else {
                val updated = SourceConfig(kind, url, username?.text?.toString()?.trim().orEmpty(),
                    password?.text?.toString().orEmpty(), xmltv?.text?.toString()?.trim().orEmpty(),
                    allowHttp.isChecked, localServer.isChecked)
                val saved = existing?.copy(name = sourceName, config = updated, updatedAt = System.currentTimeMillis(),
                    channelCount = 0, lastChannelId = if (existing.config == updated) existing.lastChannelId else null)
                    ?: SavedSource(UUID.randomUUID().toString(), sourceName, updated, updatedAt = System.currentTimeMillis())
                dialog.dismiss()
                onSave(saved)
            }
        }, AndroTheme.ACCENT, android.graphics.Color.WHITE))
        form.addView(buttons)
        val scroll = ScrollView(context).apply { addView(form) }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun field(context: Context, hint: String, value: String): EditText = EditText(context).apply {
        this.hint = hint
        setText(value)
        setSingleLine()
        setTextColor(AndroTheme.TEXT)
        setHintTextColor(AndroTheme.NEUTRAL_600)
        textSize = 14f
        setPadding(AndroTheme.dp(context, 12), 0, AndroTheme.dp(context, 12), 0)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(AndroTheme.SURFACE); cornerRadius = 0f }
        layoutParams = LinearLayout.LayoutParams(-1, AndroTheme.dp(context, 48)).apply { bottomMargin = AndroTheme.dp(context, 8) }
    }
}
