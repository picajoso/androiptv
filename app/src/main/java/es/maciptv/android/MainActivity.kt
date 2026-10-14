package es.maciptv.android

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var playback: PlaybackController
    private lateinit var root: LinearLayout
    private lateinit var topBar: View
    private lateinit var bottomControls: View
    private lateinit var exitFullscreenButton: View
    private lateinit var fullscreenTools: View
    private lateinit var fitButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var groupsView: LinearLayout
    private lateinit var channelsView: LinearLayout
    private lateinit var channelList: ListView
    private lateinit var groupList: LinearLayout
    private var displayedChannels: List<Channel> = emptyList()
    private val channelAdapter = object : BaseAdapter() {
        override fun getCount() = displayedChannels.size
        override fun getItem(position: Int) = displayedChannels[position]
        override fun getItemId(position: Int) = displayedChannels[position].id.hashCode().toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("").apply {
                    setPadding(dp(10), 0, 0, 0)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(button("☆") {}, LinearLayout.LayoutParams(dp(48), dp(48)))
            }
            val channel = getItem(position)
            (row.getChildAt(0) as TextView).apply {
                text = channel.name
                setOnClickListener { selectChannel(channel) }
            }
            (row.getChildAt(1) as Button).apply {
                text = if (channel.id in store.favorites) "★" else "☆"
                setOnClickListener {
                    val next = store.favorites.toMutableSet()
                    if (!next.add(channel.id)) next.remove(channel.id)
                    store.favorites = next
                    if (group == "Favoritos") renderChannels() else notifyDataSetChanged()
                }
            }
            return row
        }
    }
    private lateinit var status: TextView
    private lateinit var now: TextView
    private lateinit var search: EditText
    private val worker = Executors.newSingleThreadExecutor()
    private var source: SourceConfig? = null
    private var channels: List<Channel> = emptyList()
    private var guide: Map<String, List<Programme>> = emptyMap()
    private var selected: Channel? = null
    private var previous: String? = null
    private var group = "Todos"
    private var showGroups = true
    private var showChannels = true
    private var fullscreen = false
    private var fillVideo = true
    private var loading = false
    private var loadGeneration = 0

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
            val xmltv = EditText(this).apply { hint = "URL XMLTV (opcional)"; setSingleLine(); form.addView(this) }
            val http = CheckBox(this).apply { text = "Permitir HTTP"; form.addView(this) }
            val local = CheckBox(this).apply { text = "Permitir servidores locales"; form.addView(this) }
            AlertDialog.Builder(this).setTitle("Archivo M3U").setView(form)
                .setPositiveButton("Importar") { _, _ -> setSource(SourceConfig(SourceKind.M3U_FILE,
                    uri.toString(), xmltv = xmltv.text.toString().trim(), allowHttp = http.isChecked, localServer = local.isChecked)) }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        playerView = PlayerView(this).apply { setBackgroundColor(Color.BLACK); useController = true }
        playback = PlaybackController(this, playerView)
        playback.onError = { message -> runOnUiThread { showError(message) } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) toggleFullscreen() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        buildUi()
        try {
            source = store.loadSource()
            source?.let { load(it, true) }
        } catch (e: Exception) { showError("No se pudo abrir la fuente guardada: ${e.message}") }
    }

    private fun buildUi() {
        val oldSearch = if (::search.isInitialized) search.text.toString() else ""
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 24, 26)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(button("☰") { showGroups = !showGroups; renderPanels() })
        bar.addView(button("Lista") { showChannels = !showChannels; renderPanels() })
        bar.addView(button("←") { previous?.let { id -> channels.firstOrNull { it.id == id }?.let(::selectChannel) } })
        bar.addView(button("Guía") { showGuide() })
        bar.addView(button("⟳") { source?.let { load(it, false) } })
        bar.addView(button("Fuente") { sourceDialog() })
        bar.addView(button("⛶") { toggleFullscreen() })
        topBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(bar) }
        root.addView(topBar, LinearLayout.LayoutParams(-1, dp(52)))

        val body = LinearLayout(this).apply { orientation = if (isWide()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        groupsView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(43, 32, 35)) }
        channelsView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(50, 35, 39)) }
        search = EditText(this).apply {
            hint = "Buscar canales"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderChannels() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        channelsView.addView(search)
        groupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        channelList = ListView(this).apply {
            adapter = channelAdapter
            dividerHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
        }
        groupsView.addView(ScrollView(this).apply { addView(groupList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        channelsView.addView(channelList, LinearLayout.LayoutParams(-1, 0, 1f))

        val videoPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        (playerView.parent as? ViewGroup)?.removeView(playerView)
        val videoFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoFrame.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        fitButton = button(if (fillVideo) "Ver sin recortar" else "Rellenar pantalla") {
            fillVideo = !fillVideo
            fitButton.text = if (fillVideo) "Ver sin recortar" else "Rellenar pantalla"
            playerView.resizeMode = if (fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        exitFullscreenButton = button("Salir") { toggleFullscreen() }
        fullscreenTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            alpha = 0.85f
            addView(fitButton)
            addView(exitFullscreenButton)
        }
        videoFrame.addView(fullscreenTools, FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
        videoPane.addView(videoFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        now = label(selected?.let { c -> c.name + (currentProgramme(c)?.let { " · ${it.title}" } ?: "") }
            ?: "Selecciona un canal para reproducir").apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        videoPane.addView(now)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        nav.addView(button("Anterior") { move(-1) }); nav.addView(button("Siguiente") { move(1) })
        nav.addView(button("Abrir fuera") { openExternal() })
        videoPane.addView(nav)
        bottomControls = nav
        if (isWide()) {
            body.addView(groupsView, LinearLayout.LayoutParams(dp(190), -1))
            body.addView(channelsView, LinearLayout.LayoutParams(dp(270), -1))
            body.addView(videoPane, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            body.addView(videoPane, LinearLayout.LayoutParams(-1, 0, 1.3f))
            body.addView(groupsView, LinearLayout.LayoutParams(-1, 0, 0.55f))
            body.addView(channelsView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        status = label(selected?.name ?: if (channels.isEmpty()) "Conecta un proveedor o prueba la demo" else "${channels.size} canales")
            .apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
        root.addView(status)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, if (fullscreen) 0 else bars.top, 0, if (fullscreen) 0 else bars.bottom)
            insets
        }
        setContentView(root)
        search.setText(oldSearch)
        renderPanels(); renderGroups(); renderChannels()
    }

    private fun isWide() = resources.configuration.screenWidthDp >= 700 ||
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(s: String) = TextView(this).apply { text = s; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER_VERTICAL }
    private fun button(s: String, click: () -> Unit) = Button(this).apply {
        text = s; textSize = 12f; setAllCaps(false); minWidth = 0; minimumWidth = 0
        setOnClickListener { click() }
    }
    private fun renderPanels() {
        groupsView.visibility = if (showGroups && !fullscreen) View.VISIBLE else View.GONE
        channelsView.visibility = if (showChannels && !fullscreen) View.VISIBLE else View.GONE
        topBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        bottomControls.visibility = if (fullscreen) View.GONE else View.VISIBLE
        status.visibility = if (fullscreen) View.GONE else View.VISIBLE
        fullscreenTools.visibility = if (fullscreen) View.VISIBLE else View.GONE
        now.visibility = if (fullscreen) View.GONE else View.VISIBLE
        playerView.resizeMode = if (fullscreen && fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        val bars = WindowInsetsCompat.Type.systemBars()
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(bars) else show(bars)
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        renderPanels()
        requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    private fun renderGroups() {
        groupList.removeAllViews()
        (listOf("Todos", "Favoritos", "Recientes") + channels.map { it.group }.distinct()).forEach { name ->
            groupList.addView(button(name) { group = name; renderChannels() }, LinearLayout.LayoutParams(-1, dp(46)))
        }
    }
    private fun visibleChannels(): List<Channel> {
        val q = search.text.toString().trim()
        val favorites = store.favorites
        val recents = store.recents
        return channels.filter { c ->
            (group == "Todos" || group == "Favoritos" && c.id in favorites ||
                group == "Recientes" && c.id in recents || c.group == group) &&
                (q.isEmpty() || c.name.contains(q, true) || c.group.contains(q, true))
        }.let { list -> if (group == "Recientes") list.sortedBy { recents.indexOf(it.id) } else list }
    }
    private fun renderChannels() {
        if (!::channelList.isInitialized) return
        displayedChannels = visibleChannels()
        channelAdapter.notifyDataSetChanged()
    }
    private fun selectChannel(c: Channel) {
        val config = source ?: return
        if (selected?.id != c.id) previous = selected?.id
        selected = c
        store.lastChannel = c.id
        store.recents = (listOf(c.id) + store.recents.filterNot { it == c.id }).take(30)
        now.text = c.name + (currentProgramme(c)?.let { " · ${it.title}" } ?: "")
        playback.play(c, config)
        status.text = c.name
        renderChannels()
    }
    private fun move(delta: Int) {
        val list = visibleChannels()
        if (list.isEmpty()) return
        val next = (list.indexOfFirst { it.id == selected?.id } + delta).coerceIn(0, list.lastIndex)
        selectChannel(list[next])
    }
    private fun currentProgramme(c: Channel): Programme? = guide[c.tvgId].orEmpty()
        .firstOrNull { System.currentTimeMillis() in it.startMillis until it.endMillis }

    private fun sourceDialog() {
        val choices = arrayOf("URL M3U", "Archivo M3U", "Xtream Codes", "Demostración", "Quitar fuente")
        AlertDialog.Builder(this).setTitle("Fuente de canales").setItems(choices) { _, index ->
            when (index) {
                0 -> editSource(SourceKind.M3U_URL)
                1 -> filePicker.launch(arrayOf("*/*"))
                2 -> editSource(SourceKind.XTREAM)
                3 -> setSource(SourceConfig(SourceKind.DEMO))
                4 -> AlertDialog.Builder(this).setTitle("Quitar fuente").setMessage("Se eliminará la fuente guardada")
                    .setPositiveButton("Quitar") { _, _ ->
                        loadGeneration++; store.removeSource(); source = null; channels = emptyList(); guide = emptyMap()
                        selected = null; previous = null; playback.stop(); now.text = "Selecciona un canal para reproducir"
                        status.text = "Conecta un proveedor o prueba la demo"; renderGroups(); renderChannels()
                    }
                    .setNegativeButton("Cancelar", null).show()
            }
        }.show()
    }
    private fun editSource(kind: SourceKind) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hintText: String, value: String = "") = EditText(this).also {
            it.hint = hintText; it.setSingleLine(); it.setText(value); form.addView(it)
        }
        val address = field(if (kind == SourceKind.XTREAM) "URL del servidor" else "URL M3U", source?.address.orEmpty())
        val user = if (kind == SourceKind.XTREAM) field("Usuario", source?.username.orEmpty()) else null
        val pass = if (kind == SourceKind.XTREAM) field("Contraseña", source?.password.orEmpty()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else null
        val xmltv = if (kind == SourceKind.M3U_URL) field("URL XMLTV (opcional)", source?.xmltv.orEmpty()) else null
        val http = CheckBox(this).apply { text = "Permitir HTTP para esta fuente"; isChecked = source?.allowHttp == true; form.addView(this) }
        val local = CheckBox(this).apply { text = "Servidor local"; isChecked = source?.localServer == true; form.addView(this) }
        AlertDialog.Builder(this).setTitle("Configurar proveedor").setView(form)
            .setPositiveButton("Guardar") { _, _ ->
                val config = SourceConfig(kind, address.text.toString().trim(), user?.text?.toString().orEmpty(),
                    pass?.text?.toString().orEmpty(), xmltv?.text?.toString().orEmpty(), http.isChecked, local.isChecked)
                if (config.address.isBlank()) showError("Introduce la URL del proveedor") else setSource(config)
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun setSource(config: SourceConfig) {
        try {
            store.saveSource(config); source = config; store.lastChannel = null
            selected = null; previous = null; playback.stop(); now.text = "Selecciona un canal para reproducir"
            load(config, false)
        }
        catch (e: Exception) { showError(e.message ?: "No se pudo guardar la fuente") }
    }
    private fun load(config: SourceConfig, restore: Boolean) {
        val generation = ++loadGeneration
        loading = true; status.text = "Cargando canales…"
        worker.execute {
            try {
                val parsed = when (config.kind) {
                    SourceKind.DEMO -> DemoContent.channels
                    SourceKind.M3U_FILE -> contentResolver.openInputStream(android.net.Uri.parse(config.address)).use { input ->
                        val stream = input ?: throw IllegalStateException("No se pudo abrir el archivo")
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            if (output.size() + n > 30_000_000) throw IllegalStateException("Lista demasiado grande")
                            output.write(buffer, 0, n)
                        }
                        PlaylistParsers.m3u(output.toByteArray())
                    }
                    else -> PlaylistParsers.m3u(SafeNetwork(config).fetch(config.playlistUrl(), 8_000_000), config.playlistUrl())
                }
                val programmes = if (config.kind == SourceKind.DEMO) DemoContent.guide() else if (config.guideUrl().isNotBlank()) runCatching {
                    PlaylistParsers.xmltv(SafeNetwork(config).fetch(config.guideUrl(), 40_000_000))
                }.getOrDefault(emptyMap()) else emptyMap()
                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    channels = parsed; guide = programmes; loading = false
                    group = "Todos"; renderGroups(); renderChannels()
                    status.text = "${channels.size} canales"
                    val resumeId = if (restore) store.lastChannel else selected?.id
                    resumeId?.let { id -> channels.firstOrNull { it.id == id }?.let(::selectChannel) }
                }
            } catch (e: Exception) { runOnUiThread {
                if (generation == loadGeneration) { loading = false; showError(e.message ?: "Error al cargar canales") }
            } }
        }
    }
    private fun showGuide() {
        if (guide.isEmpty()) return showError("No hay programación disponible")
        val list = visibleChannels().filter { guide[it.tvgId].orEmpty().isNotEmpty() }
        if (list.isEmpty()) return showError("No hay programación para los canales visibles")
        AlertDialog.Builder(this).setTitle("Guía de programación")
            .setItems(list.map { c -> c.name + (currentProgramme(c)?.let { " · ${it.title}" } ?: "") }.toTypedArray()) { _, i ->
                showChannelGuide(list[i])
            }.show()
    }
    private fun showChannelGuide(c: Channel) {
        val entries = guide[c.tvgId].orEmpty().filter { it.endMillis >= System.currentTimeMillis() }.take(30)
        if (entries.isEmpty()) return showError("No hay programación próxima para este canal")
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        AlertDialog.Builder(this).setTitle("Guía: ${c.name}").setItems(entries.map {
            "${format.format(java.util.Date(it.startMillis))} ${it.title}"
        }.toTypedArray()) { _, i ->
            AlertDialog.Builder(this).setTitle(entries[i].title).setMessage(entries[i].details)
                .setPositiveButton("Ver canal") { _, _ -> selectChannel(c) }
                .setNegativeButton("Cerrar", null).show()
        }.show()
    }
    private fun openExternal() {
        val c = selected ?: return showError("Selecciona un canal")
        AlertDialog.Builder(this).setTitle("Abrir en otra app")
            .setMessage("La URL del canal puede incluir credenciales. La otra app recibirá la URL completa.")
            .setPositiveButton("Abrir") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse(c.url), "video/*") }) }
                catch (e: Exception) { showError("No hay una app compatible") }
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun showError(message: String) { status.text = message; Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); buildUi() }
    override fun onDestroy() { playback.release(); worker.shutdownNow(); super.onDestroy() }
}
