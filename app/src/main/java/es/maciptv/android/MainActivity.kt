package es.maciptv.android

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
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
import androidx.media3.common.util.UnstableApi
import es.maciptv.android.ui.AppTab
import es.maciptv.android.ui.AndroTheme
import es.maciptv.android.ui.ChannelRow
import es.maciptv.android.ui.ChannelsActions
import es.maciptv.android.ui.ChannelsUiState
import es.maciptv.android.ui.GuideActions
import es.maciptv.android.ui.GuideUiState
import es.maciptv.android.ui.GuideViews
import es.maciptv.android.ui.LogoLoader
import es.maciptv.android.ui.PlayerActions
import es.maciptv.android.ui.PlayerUiState
import es.maciptv.android.ui.PortraitScreens
import es.maciptv.android.ui.ConfirmationDialog
import es.maciptv.android.ui.ProviderSheet
import es.maciptv.android.ui.SettingsActions
import es.maciptv.android.ui.SettingsScreen
import es.maciptv.android.ui.SettingsUiState
import es.maciptv.android.ui.SleepTimer
import es.maciptv.android.ui.NoticeView
import es.maciptv.android.ui.LandscapeActions
import es.maciptv.android.ui.LandscapePanels
import es.maciptv.android.ui.LandscapeUiState
import es.maciptv.android.ui.GroupItem
import es.maciptv.android.ui.LandscapePlayerActions
import es.maciptv.android.ui.LandscapePlayerPanel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var playback: PlaybackController
    private lateinit var sleepTimer: SleepTimer
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
    private lateinit var portraitHost: LinearLayout
    private var displayedChannels: List<Channel> = emptyList()
    private val channelAdapter = object : BaseAdapter() {
        override fun getCount() = displayedChannels.size
        override fun getItem(position: Int) = displayedChannels[position]
        override fun getItemId(position: Int) = displayedChannels[position].id.hashCode().toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? ChannelRow) ?: ChannelRow(this@MainActivity)
            val channel = getItem(position)
            val presentation = presentationById[channel.id]
            if (presentation != null) row.bind(
                item = presentation,
                isFavorite = channel.id in store.favorites,
                selected = channel.id == selected?.id,
                network = activeNetwork,
                landscape = isWide(),
                onSelect = { selectChannel(channel) },
                onFavorite = {
                    val next = store.favorites.toMutableSet()
                    if (!next.add(channel.id)) next.remove(channel.id)
                    store.favorites = next
                    renderChannels()
                    if (isWide() && !fullscreen) buildLandscapeUi()
                },
                onHttpBlocked = { /* surfaced in source settings */ }
            )
            return row
        }
    }
    private lateinit var status: TextView
    private lateinit var now: TextView
    private lateinit var search: EditText
    private var landscapePlayerPanel: LandscapePlayerPanel? = null
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var source: SourceConfig? = null
    private var savedSources = SourceCollection(emptyList(), null)
    private var activeSavedSource: SavedSource? = null
    private var activeNetwork: SafeNetwork? = null
    private var activeNetworkConfig: SourceConfig? = null
    private var activeNetworkSourceId: String? = null
    private var channels: List<Channel> = emptyList()
    private var guide: Map<String, List<Programme>> = emptyMap()
    private var presentations: List<ChannelPresentation> = emptyList()
    private var presentationById: Map<String, ChannelPresentation> = emptyMap()
    private var presentationGeneration = 0
    private var selected: Channel? = null
    private var previous: String? = null
    private var group = "Todos"
    private var showGroups = true
    private var showChannels = true
    private var fullscreen = false
    private var fillVideo = true
    private var loading = false
    private var loadGeneration = 0
    private var logoHttpRestricted = false
    private var appTab = AppTab.CHANNELS
    private var portraitPlayer = false
    private var guideChannelId: String? = null
    private var guideDayOffset = 0
    private var channelListScrollPosition = 0
    private var channelListScrollOffset = 0
    private var searchOpen = false
    private var pendingProviderName: String? = null
    private var pendingProviderSource: SavedSource? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
            val xmltv = EditText(this).apply { hint = "URL XMLTV (opcional)"; setSingleLine(); form.addView(this) }
            val http = CheckBox(this).apply { text = "Permitir HTTP"; form.addView(this) }
            val local = CheckBox(this).apply { text = "Permitir servidores locales"; form.addView(this) }
            AlertDialog.Builder(this).setTitle("Archivo M3U").setView(form)
                .setPositiveButton("Importar") { _, _ ->
                    val config = SourceConfig(SourceKind.M3U_FILE, uri.toString(), xmltv = xmltv.text.toString().trim(),
                        allowHttp = http.isChecked, localServer = local.isChecked)
                    val pendingName = pendingProviderName
                    if (pendingName == null) setSource(config)
                    else saveProviderSource(pendingProviderSource?.copy(config = config, name = pendingName,
                        updatedAt = System.currentTimeMillis(), channelCount = 0,
                        lastChannelId = if (pendingProviderSource?.config == config) pendingProviderSource?.lastChannelId else null)
                        ?: SavedSource(UUID.randomUUID().toString(), pendingName, config, updatedAt = System.currentTimeMillis()))
                    pendingProviderName = null
                    pendingProviderSource = null
                }
                .setNegativeButton("Cancelar") { _, _ -> pendingProviderName = null; pendingProviderSource = null }
                .setOnCancelListener { pendingProviderName = null; pendingProviderSource = null }
                .show()
        } else { pendingProviderName = null; pendingProviderSource = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        fillVideo = store.fillVideo
        playerView = PlayerView(this).apply { setBackgroundColor(Color.BLACK); useController = false }
        playback = PlaybackController(this, playerView)
        sleepTimer = SleepTimer {
            playback.pause()
            runOnUiThread {
                landscapePlayerPanel?.updateTimer(0)
                landscapePlayerPanel?.updatePlaying(false)
                showError("El temporizador ha pausado la reproducción")
            }
        }
        playback.onError = { message -> runOnUiThread { showError(message) } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) toggleFullscreen() else if (portraitPlayer && !isWide()) {
                    portraitPlayer = false
                    renderPortraitContent()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        buildUi()
        worker.scheduleAtFixedRate({ refreshPresentations() }, 1, 1, TimeUnit.MINUTES)
        try {
            savedSources = store.loadSources()
            activeSavedSource = savedSources.activeSource
            source = activeSavedSource?.config
            source?.let(::useActiveNetwork)
            source?.let { load(it, store.resumeLastChannel) }
        } catch (e: Exception) { showError("No se pudo abrir la fuente guardada: ${e.message}") }
    }

    private fun buildUi() {
        if (!fullscreen) WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        if (!isWide() && !fullscreen) {
            buildPortraitUi()
            return
        }
        if (isWide() && !fullscreen) {
            buildLandscapeUi()
            return
        }
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
            store.fillVideo = fillVideo
            store.fillVideo = fillVideo
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

    private fun buildPortraitUi() {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        val oldSearch = if (::search.isInitialized) search.text.toString() else ""
        if (!::search.isInitialized) {
            search = EditText(this).apply {
                hint = "Buscar canales o grupos"; setSingleLine(); setTextColor(Color.BLACK)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderChannels() }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
        } else if (search.text.toString() != oldSearch) search.setText(oldSearch)
        if (!::channelList.isInitialized) channelList = ListView(this).apply {
            adapter = channelAdapter
            dividerHeight = dp(1)
            divider = android.graphics.drawable.ColorDrawable(0x66201E1D)
            setBackgroundColor(Color.TRANSPARENT)
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(AndroTheme.BG) }
        portraitHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(portraitHost, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label(if (channels.isEmpty()) "Conecta un proveedor o prueba la demo" else "${channels.size} canales")
        now = label(selected?.name ?: "Selecciona un canal")
        setContentView(root)
        renderPortraitContent()
        if (channelListScrollPosition > 0 || channelListScrollOffset != 0) channelList.post {
            channelList.setSelectionFromTop(channelListScrollPosition, channelListScrollOffset)
        }
        renderChannels()
    }

    private fun buildLandscapeUi() {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        if (::channelList.isInitialized && channelList.childCount > 0) {
            channelListScrollPosition = channelList.firstVisiblePosition
            channelListScrollOffset = channelList.getChildAt(0)?.top ?: 0
        }
        val oldSearch = if (::search.isInitialized) search.text.toString() else ""
        search = EditText(this).apply {
            hint = "Buscar canales o grupos"
            setSingleLine()
            setText(oldSearch)
            setTextColor(AndroTheme.TEXT)
            setHintTextColor(AndroTheme.NEUTRAL_600)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderChannels() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        if (!::channelList.isInitialized) channelList = ListView(this).apply {
            adapter = channelAdapter
            dividerHeight = dp(1)
            divider = android.graphics.drawable.ColorDrawable(AndroTheme.DIVIDER)
            setBackgroundColor(Color.TRANSPARENT)
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = LandscapeInsetBackground() }
        status = label(if (channels.isEmpty()) "Conecta un proveedor o prueba la demo" else "${channels.size} canales")
        now = label(selected?.name ?: "Selecciona un canal")
        playerView.resizeMode = if (fillVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        val playerHost = LandscapePlayerPanel(this, playerView, LandscapePlayerActions(
            onToggle = { playback.togglePlayPause(); landscapePlayerPanel?.updatePlaying(playback.isPlaying) },
            onPrevious = { move(-1) },
            onNext = { move(1) },
            onFullscreen = ::toggleFullscreen,
            onFavorite = {
                selected?.let { channel ->
                    val favorites = store.favorites.toMutableSet()
                    if (!favorites.add(channel.id)) favorites.remove(channel.id)
                    store.favorites = favorites
                    renderChannels()
                    buildLandscapeUi()
                }
            },
            onTimer = {
                val nextMinutes = when (sleepTimer.minutes) { 0 -> 30; 30 -> 60; 60 -> 90; else -> 0 }
                sleepTimer.setMinutes(nextMinutes)
                landscapePlayerPanel?.updateTimer(nextMinutes)
            },
            onExternal = ::openExternal
        ))
        landscapePlayerPanel = playerHost
        val selectedPresentation = selected?.let { presentationById[it.id] }
        playerHost.updateProgrammes(selectedPresentation, selectedPresentation?.let { nextProgramme(it.channel) })
        playerHost.updatePlaying(playback.isPlaying)
        playerHost.updateFavorite(selected?.id in store.favorites)
        playerHost.updateTimer(sleepTimer.minutes)
        val content = when (appTab) {
            AppTab.SETTINGS -> renderSettings()
            AppTab.GUIDE -> createGuideScreen()
            AppTab.CHANNELS -> null
        }
        val groups = createLandscapeGroups()
        val pageTitle = if (appTab == AppTab.SETTINGS) "Ajustes" else if (appTab == AppTab.GUIDE) "Guía · ${buildSourceHeader()}" else buildSourceHeader()
        root.addView(LandscapePanels.render(this,
            LandscapeUiState(pageTitle, groups, group, search, channelList, content),
            LandscapeActions(
                onGroup = { group = it; renderChannels(); buildLandscapeUi() },
                onRefresh = { source?.let { load(it, false) } },
                onSettings = {
                    appTab = if (appTab == AppTab.SETTINGS) AppTab.CHANNELS else AppTab.SETTINGS
                    buildLandscapeUi()
                },
                onGuide = { appTab = if (appTab == AppTab.GUIDE) AppTab.CHANNELS else AppTab.GUIDE; buildLandscapeUi() },
                onBack = if (appTab == AppTab.CHANNELS) null else ({ appTab = AppTab.CHANNELS; buildLandscapeUi() })
            ), playerHost), LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.background as? LandscapeInsetBackground)?.topInset = bars.top
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        if (channelListScrollPosition > 0 || channelListScrollOffset != 0) channelList.post {
            channelList.setSelectionFromTop(channelListScrollPosition, channelListScrollOffset)
        }
        renderChannels()
    }

    private fun createLandscapeGroups(): List<GroupItem> {
        val favorites = store.favorites
        val recents = store.recents
        val groups = listOf("Todos", "Favoritos", "Recientes") + channels.map { it.group }.distinct()
        return groups.distinct().map { groupName ->
            val count = when (groupName) {
                "Todos" -> channels.size
                "Favoritos" -> channels.count { it.id in favorites }
                "Recientes" -> channels.count { it.id in recents }
                else -> channels.count { it.group == groupName }
            }
            GroupItem(groupName, count)
        }
    }

    private fun nextProgramme(channel: Channel): Programme? = guide[channel.tvgId].orEmpty()
        .filter { it.startMillis > System.currentTimeMillis() }.minByOrNull { it.startMillis }

    private fun createGuideScreen(): View {
        val channelPresentations = channels.mapNotNull { presentationById[it.id] }
            .filter { guide[it.channel.tvgId].orEmpty().isNotEmpty() }
        val guideChannel = channelPresentations.firstOrNull { it.channel.id == guideChannelId }
            ?: selected?.let { channel -> channelPresentations.firstOrNull { it.channel.id == channel.id } }
            ?: channelPresentations.firstOrNull()
        guideChannelId = guideChannel?.channel?.id
        val day = LocalDate.now(ZoneId.systemDefault()).plusDays(guideDayOffset.toLong())
        val entries = guideChannel?.let { GuideDayFilter.forDate(guide[it.channel.tvgId].orEmpty(), day, ZoneId.systemDefault()) }.orEmpty()
        return GuideViews.render(this, GuideUiState(guideChannel, channelPresentations, entries, guideDayOffset),
            GuideActions(
                onChannel = { guideChannelId = it.channel.id; if (isWide()) buildLandscapeUi() else renderPortraitContent() },
                onDay = { guideDayOffset = it.coerceIn(0, 1); if (isWide()) buildLandscapeUi() else renderPortraitContent() },
                onWatch = { guideChannel?.let { selectChannel(it.channel, openPlayer = true) } }
            ))
    }

    private fun renderPortraitContent() {
        if (!::portraitHost.isInitialized) return
        portraitHost.removeAllViews()
        if (portraitPlayer) {
            playerView.useController = false
            val current = selected?.let { presentationById[it.id] }
            val screen = PortraitScreens.renderPlayer(this,
                PlayerUiState(current, playerView, playback.isPlaying),
                PlayerActions(
                    onBack = { portraitPlayer = false; renderPortraitContent() },
                    onToggle = { playback.togglePlayPause(); renderPortraitContent() },
                    onPrevious = { move(-1) }, onNext = { move(1) }, onFullscreen = ::toggleFullscreen
                ))
            portraitHost.addView(screen, LinearLayout.LayoutParams(-1, -1))
            syncPortraitTabs()
            return
        }
        when (appTab) {
            AppTab.CHANNELS -> {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                val screen = PortraitScreens.renderChannels(this,
                    ChannelsUiState(
                        preview = selected?.let { presentationById[it.id] },
                        sourceHeader = buildSourceHeader(),
                        groups = listOf("Todos", "Favoritos", "Recientes") + channels.map { it.group }.distinct(),
                        selectedGroup = group,
                        search = search.text.toString(),
                        searchOpen = searchOpen,
                        searchView = search,
                        list = channelList,
                        playerView = playerView,
                        loading = loading,
                        playing = playback.isPlaying
                    ),
                    ChannelsActions(
                        onSource = ::sourceDialog,
                        onGroup = { group = it; renderChannels(); renderPortraitContent() },
                        onSearch = { value, open -> searchOpen = open; if (search.text.toString() != value) search.setText(value) },
                        onSearchToggle = { searchOpen = !searchOpen; renderPortraitContent() },
                        onPreview = { portraitPlayer = true; renderPortraitContent() },
                        onToggle = { playback.togglePlayPause(); renderPortraitContent() },
                        onFullscreen = ::toggleFullscreen
                    ))
                portraitHost.addView(screen, LinearLayout.LayoutParams(-1, -1))
            }
            AppTab.GUIDE -> {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                val channelPresentations = channels.mapNotNull { presentationById[it.id] }
                    .filter { guide[it.channel.tvgId].orEmpty().isNotEmpty() }
                val guideChannel = channelPresentations.firstOrNull { it.channel.id == guideChannelId }
                    ?: selected?.let { channel -> channelPresentations.firstOrNull { it.channel.id == channel.id } }
                    ?: channelPresentations.firstOrNull()
                guideChannelId = guideChannel?.channel?.id
                val day = LocalDate.now(ZoneId.systemDefault()).plusDays(guideDayOffset.toLong())
                val entries = guideChannel?.let { GuideDayFilter.forDate(guide[it.channel.tvgId].orEmpty(), day, ZoneId.systemDefault()) }.orEmpty()
                val screen = GuideViews.render(this, GuideUiState(guideChannel, channelPresentations, entries, guideDayOffset),
                    GuideActions(
                        onChannel = { guideChannelId = it.channel.id; renderPortraitContent() },
                        onDay = { guideDayOffset = it.coerceIn(0, 1); renderPortraitContent() },
                        onWatch = { guideChannel?.let { selectChannel(it.channel, openPlayer = true) } }
                    ))
                portraitHost.addView(screen, LinearLayout.LayoutParams(-1, -1))
            }
            AppTab.SETTINGS -> portraitHost.addView(renderSettings(), LinearLayout.LayoutParams(-1, -1))
        }
        syncPortraitTabs()
    }

    private fun syncPortraitTabs() {
        if (!::root.isInitialized) return
        val existing = root.findViewWithTag<View>("portrait-tabs")
        if (portraitPlayer) {
            if (existing != null) root.removeView(existing)
        } else if (existing == null) {
            root.addView(PortraitScreens.bottomTabs(this, appTab, ::selectPortraitTab).apply { tag = "portrait-tabs" })
        } else {
            val index = root.indexOfChild(existing)
            root.removeView(existing)
            root.addView(PortraitScreens.bottomTabs(this, appTab, ::selectPortraitTab).apply { tag = "portrait-tabs" }, index)
        }
    }

    private fun buildSourceHeader(): String {
        val active = activeSavedSource ?: return "Sin fuente activa"
        val kind = when (active.config.kind) {
            SourceKind.M3U_URL -> "URL M3U"
            SourceKind.M3U_FILE -> "Archivo M3U"
            SourceKind.XTREAM -> "Xtream"
            SourceKind.DEMO -> "Demo"
        }
        return "${active.name} · $kind · ${channels.size}"
    }

    private fun selectPortraitTab(tab: AppTab) {
        if (::channelList.isInitialized && channelList.childCount > 0) {
            channelListScrollPosition = channelList.firstVisiblePosition
            channelListScrollOffset = channelList.getChildAt(0)?.top ?: 0
        }
        portraitPlayer = false
        appTab = tab
        renderPortraitContent()
    }

    private fun renderSettings(): View = SettingsScreen.render(this,
        SettingsUiState(
            sources = savedSources.sources,
            activeSourceId = savedSources.activeSourceId,
            resumeLastChannel = store.resumeLastChannel,
            fillVideo = fillVideo,
            sleepTimerMinutes = sleepTimer.minutes,
            logoHttpRestrictedSourceIds = savedSources.sources.filter { it.logoHttpRestricted }.map { it.id }.toSet()
        ),
        SettingsActions(
            onSelect = ::activateSavedSource,
            onAdd = { showProviderSheet(it, null) },
            onEdit = { showProviderSheet(it.config.kind, it) },
            onRefresh = ::refreshSavedSource,
            onRemove = ::confirmRemoveSavedSource,
            onResume = { store.resumeLastChannel = it; refreshSettingsScreen() },
            onFillVideo = {
                fillVideo = it
                store.fillVideo = it
                playerView.resizeMode = if (it) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                refreshSettingsScreen()
            },
            onTimer = { sleepTimer.setMinutes(it); refreshSettingsScreen() }
        ))

    private fun refreshSettingsScreen() {
        if (isWide() && !fullscreen) buildLandscapeUi() else renderPortraitContent()
    }

    private fun showProviderSheet(kind: SourceKind, existing: SavedSource?) {
        ProviderSheet.show(this, kind, existing,
            onSave = ::saveProviderSource,
            onPickFile = { name, sourceRecord ->
                pendingProviderName = name
                pendingProviderSource = sourceRecord
                filePicker.launch(arrayOf("*/*"))
            })
    }

    private fun saveProviderSource(saved: SavedSource) {
        try {
            val old = savedSources.sources.firstOrNull { it.id == saved.id }
            val configChanged = old != null && old.config != saved.config
            val normalized = saved.copy(
                channelCount = if (configChanged) 0 else saved.channelCount,
                updatedAt = System.currentTimeMillis(),
                lastChannelId = if (configChanged) null else saved.lastChannelId,
                logoHttpRestricted = if (configChanged) false else saved.logoHttpRestricted
            )
            val records = if (old == null) savedSources.sources + normalized
                else savedSources.sources.map { if (it.id == normalized.id) normalized else it }
            val collection = SourceCollection(records, normalized.id)
            store.saveSources(collection)
            if (normalized.lastChannelId == null) store.setLastChannel(normalized.id, null)
            savedSources = collection
            activeSavedSource = normalized
            source = normalized.config
            activeNetwork = null
            activeNetworkConfig = null
            activeNetworkSourceId = null
            selected = null
            previous = null
            playback.stop()
            sleepTimer.cancel()
            appTab = AppTab.SETTINGS
            portraitPlayer = false
            useActiveNetwork(normalized.config)
            load(normalized.config, false)
            if (::portraitHost.isInitialized && !isWide()) renderPortraitContent()
        } catch (e: Exception) {
            showError(e.message ?: "No se pudo guardar la fuente")
        }
    }

    private fun activateSavedSource(saved: SavedSource) {
        if (saved.id == activeSavedSource?.id) return
        try {
            val collection = SourceCollection(savedSources.sources, saved.id)
            store.saveSources(collection)
            savedSources = collection
            activeSavedSource = saved
            source = saved.config
            activeNetwork = null
            activeNetworkConfig = null
            activeNetworkSourceId = null
            LogoLoader.clear()
            playback.stop()
            selected = null
            previous = null
            channels = emptyList()
            guide = emptyMap()
            presentations = emptyList()
            presentationById = emptyMap()
            portraitPlayer = false
            load(saved.config, true)
            if (::portraitHost.isInitialized && !isWide()) renderPortraitContent()
        } catch (e: Exception) {
            showError("No se pudo activar la fuente guardada")
        }
    }

    private fun refreshSavedSource(saved: SavedSource) {
        if (saved.id != activeSavedSource?.id) activateSavedSource(saved)
        else source?.let { load(it, false) }
    }

    private fun confirmRemoveSavedSource(saved: SavedSource) {
        ConfirmationDialog.show(this, "Quitar fuente", "Se quitará ${saved.name} de la lista de fuentes.", "Quitar") {
            if (saved.id == activeSavedSource?.id) {
                sleepTimer.cancel()
                removeCurrentSource()
            } else {
                try {
                    savedSources = store.removeSavedSource(saved.id)
                    activeSavedSource = savedSources.activeSource
                    if (::portraitHost.isInitialized) renderPortraitContent()
                } catch (_: Exception) { showError("No se pudo quitar la fuente") }
            }
        }
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
        playerView.useController = fullscreen
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
        requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        buildUi()
    }
    private fun renderGroups() {
        if (!::groupList.isInitialized) return
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
                ChannelName.matches(c, presentationById[c.id]?.displayName ?: c.name, q)
        }.let { list -> if (group == "Recientes") list.sortedBy { recents.indexOf(it.id) } else list }
    }
    private fun renderChannels() {
        if (!::channelList.isInitialized) return
        displayedChannels = visibleChannels()
        channelAdapter.notifyDataSetChanged()
    }
    private fun selectChannel(c: Channel, openPlayer: Boolean = true) {
        if (openPlayer && !isWide() && ::channelList.isInitialized && channelList.childCount > 0) {
            channelListScrollPosition = channelList.firstVisiblePosition
            channelListScrollOffset = channelList.getChildAt(0)?.top ?: 0
        }
        val config = source ?: return
        if (selected?.id != c.id) previous = selected?.id
        selected = c
        activeSavedSource?.let { active ->
            store.setLastChannel(active.id, c.id)
            val updated = active.copy(lastChannelId = c.id)
            activeSavedSource = updated
            savedSources = savedSources.copy(sources = savedSources.sources.map { if (it.id == updated.id) updated else it })
        }
        store.recents = (listOf(c.id) + store.recents.filterNot { it == c.id }).take(30)
        now.text = c.name + (currentProgramme(c)?.let { " · ${it.title}" } ?: "")
        playback.play(c, config, useActiveNetwork(config))
        status.text = c.name
        renderChannels()
        if (isWide() && !fullscreen) buildLandscapeUi()
        if (!isWide() && !fullscreen && openPlayer) {
            appTab = AppTab.CHANNELS
            portraitPlayer = true
            renderPortraitContent()
        } else if (!isWide() && !fullscreen && ::portraitHost.isInitialized) renderPortraitContent()
    }
    private fun move(delta: Int) {
        val list = visibleChannels()
        if (list.isEmpty()) return
        val next = (list.indexOfFirst { it.id == selected?.id } + delta).coerceIn(0, list.lastIndex)
        selectChannel(list[next])
    }
    private fun currentProgramme(c: Channel): Programme? = presentationById[c.id]?.currentProgramme

    private fun refreshPresentations() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val channelSnapshot = channels
            val guideSnapshot = guide
            val generation = ++presentationGeneration
            worker.execute {
                val fresh = buildChannelPresentations(channelSnapshot, guideSnapshot, System.currentTimeMillis())
                runOnUiThread {
                    if (generation != presentationGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    presentations = fresh
                    presentationById = fresh.associateBy { it.channel.id }
                    selected?.let { selectedChannel ->
                        now.text = selectedChannel.name + (currentProgramme(selectedChannel)?.let { " · ${it.title}" } ?: "")
                    }
                    renderChannels()
                    selected?.let { channel ->
                        val current = presentationById[channel.id]
                        landscapePlayerPanel?.updateProgrammes(current, current?.let { nextProgramme(it.channel) })
                    }
                }
            }
        }
    }

    private fun sourceDialog() {
        val choices = arrayOf("URL M3U", "Archivo M3U", "Xtream Codes", "Demostración", "Quitar fuente")
        AlertDialog.Builder(this).setTitle("Fuente de canales").setItems(choices) { _, index ->
            when (index) {
                0 -> editSource(SourceKind.M3U_URL)
                1 -> filePicker.launch(arrayOf("*/*"))
                2 -> editSource(SourceKind.XTREAM)
                3 -> setSource(SourceConfig(SourceKind.DEMO))
                4 -> AlertDialog.Builder(this).setTitle("Quitar fuente").setMessage("Se eliminará la fuente guardada")
                    .setPositiveButton("Quitar") { _, _ -> removeCurrentSource() }
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
            val existing = activeSavedSource
            val saved = if (existing == null) {
                SavedSource(UUID.randomUUID().toString(), "Mi lista", config, updatedAt = System.currentTimeMillis())
            } else {
                existing.copy(
                    config = config,
                    channelCount = 0,
                    updatedAt = System.currentTimeMillis(),
                    lastChannelId = if (existing.config == config) existing.lastChannelId else null
                )
            }
            val records = if (existing == null) savedSources.sources + saved
                else savedSources.sources.map { if (it.id == existing.id) saved else it }
            val updated = SourceCollection(records, saved.id)
            store.saveSources(updated)
            if (saved.lastChannelId == null) store.setLastChannel(saved.id, null)
            savedSources = updated
            activeSavedSource = saved
            source = config
            useActiveNetwork(config)
            selected = null; previous = null; playback.stop(); now.text = "Selecciona un canal para reproducir"
            load(config, false)
        }
        catch (e: Exception) { showError(e.message ?: "No se pudo guardar la fuente") }
    }

    private fun useActiveNetwork(config: SourceConfig?): SafeNetwork {
        requireNotNull(config) { "No hay una fuente activa" }
        val sourceId = activeSavedSource?.id
        if (activeNetwork == null || activeNetworkConfig != config || activeNetworkSourceId != sourceId) {
            LogoLoader.clear()
            activeNetwork = SafeNetwork(config)
            activeNetworkConfig = config
            activeNetworkSourceId = sourceId
        }
        return activeNetwork!!
    }

    private fun removeCurrentSource() {
        val active = activeSavedSource ?: return
        try {
            sleepTimer.cancel()
            loadGeneration++
            playback.stop()
            savedSources = store.removeSavedSource(active.id)
            activeSavedSource = savedSources.activeSource
            source = activeSavedSource?.config
            activeNetwork = null
            activeNetworkConfig = null
            activeNetworkSourceId = null
            source?.let(::useActiveNetwork)
            channels = emptyList(); guide = emptyMap(); selected = null; previous = null
            now.text = "Selecciona un canal para reproducir"
            if (source == null) {
                status.text = "Conecta un proveedor o prueba la demo"
                renderGroups(); renderChannels()
                if (::portraitHost.isInitialized && !isWide()) renderPortraitContent()
            } else {
                load(source!!, true)
            }
        } catch (e: Exception) {
            showError(e.message ?: "No se pudo quitar la fuente")
        }
    }

    private fun load(config: SourceConfig, restore: Boolean) {
        val generation = ++loadGeneration
        val network = useActiveNetwork(config)
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
                    else -> PlaylistParsers.m3u(network.fetch(config.playlistUrl(), 8_000_000), config.playlistUrl())
                }
                val programmes = if (config.kind == SourceKind.DEMO) DemoContent.guide() else if (config.guideUrl().isNotBlank()) runCatching {
                    PlaylistParsers.xmltv(network.fetch(config.guideUrl(), 40_000_000))
                }.getOrDefault(emptyMap()) else emptyMap()
                val derivedPresentations = buildChannelPresentations(parsed, programmes, System.currentTimeMillis())
                val blockedLogos = !config.allowHttp && parsed.any { it.logo.startsWith("http://", true) }
                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    channels = parsed; guide = programmes; loading = false
                    logoHttpRestricted = blockedLogos
                    activeSavedSource?.takeIf { it.config == config }?.let { active ->
                        val refreshed = active.copy(channelCount = parsed.size, updatedAt = System.currentTimeMillis(),
                            logoHttpRestricted = blockedLogos)
                        activeSavedSource = refreshed
                        savedSources = savedSources.copy(sources = savedSources.sources.map { if (it.id == refreshed.id) refreshed else it })
                        runCatching { store.saveSources(savedSources) }
                    }
                    presentations = derivedPresentations
                    presentationById = presentations.associateBy { it.channel.id }
                    presentationGeneration++
                    group = "Todos"; renderGroups(); renderChannels()
                    status.text = "${channels.size} canales"
                    val resumeId = if (restore) activeSavedSource?.lastChannelId else selected?.id
                    resumeId?.let { id -> channels.firstOrNull { it.id == id }?.let { channel ->
                        if (selected?.id == channel.id && playback.currentChannelId == channel.id) {
                            selected = channel
                            now.text = channel.name + (currentProgramme(channel)?.let { " · ${it.title}" } ?: "")
                            status.text = channel.name
                            renderChannels()
                        } else selectChannel(channel, openPlayer = false)
                    } }
                    if (isWide() && !fullscreen) buildLandscapeUi()
                    else if (::portraitHost.isInitialized && appTab == AppTab.SETTINGS) renderPortraitContent()
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
        ConfirmationDialog.show(this, "Abrir en otra app",
            "La aplicación externa recibirá la dirección completa del canal.", "Abrir") {
            try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse(c.url), "video/*") }) }
            catch (_: Exception) { showError("No hay una app compatible") }
        }
    }
    private fun showError(message: String) {
        var safe = message
        listOfNotNull(source?.address, source?.username, source?.password).filter { it.isNotBlank() }.forEach { secret ->
            safe = safe.replace(secret, "•••", ignoreCase = false)
        }
        safe = safe.replace(Regex("(?i)(username|password)=([^&\\s]+)"), "${'$'}1=•••")
        if (::status.isInitialized) status.text = safe
        NoticeView.show(this, safe)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::channelList.isInitialized && channelList.childCount > 0) {
            channelListScrollPosition = channelList.firstVisiblePosition
            channelListScrollOffset = channelList.getChildAt(0)?.top ?: 0
        }
        buildUi()
    }
    override fun onDestroy() {
        presentationGeneration++
        if (::sleepTimer.isInitialized) sleepTimer.cancel()
        playback.release()
        worker.shutdownNow()
        super.onDestroy()
    }
}

private class LandscapeInsetBackground : Drawable() {
    private val paint = Paint()
    var topInset: Int = 0
        set(value) { field = value; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        paint.color = AndroTheme.BG
        canvas.drawRect(bounds, paint)
        paint.color = AndroTheme.ACCENT
        canvas.drawRect(0f, 0f, bounds.width().toFloat(), topInset.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
