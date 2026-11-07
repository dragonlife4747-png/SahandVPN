package com.v2ray.ang.simple

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
import com.v2ray.ang.ui.compose.ThemeManager
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val BgTop: Color @Composable get() = MaterialTheme.colorScheme.background
private val BgMid: Color @Composable get() = MaterialTheme.colorScheme.background
private val BgBottom: Color @Composable get() = MaterialTheme.colorScheme.background
private val CardBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val CardBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AccentGreen: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AccentBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AccentRed: Color @Composable get() = MaterialTheme.colorScheme.error

/**
 * One central action: retest cached points, search when needed, then connect.
 * Busy taps cancel the complete request; only verified tunnel health shows connected.
 */
class SimpleActivity : BaseComponentActivity() {
    private val updater by lazy { androidx.lifecycle.ViewModelProvider(this)[com.v2ray.ang.update.UpdateViewModel::class.java] }
    private val installPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updater.awaitingPermission = false
        if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) launchUpdateInstaller()
    }
    private fun launchUpdateInstaller() {
        if (!updater.verifyReady()) return
        try {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                updater.awaitingPermission = true
                installPermissionLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:$packageName")))
                return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", updater.apk)
            val installIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            installIntent.clipData = android.content.ClipData.newRawUri("Sahand VPN update", uri)
            startActivity(installIntent)
        } catch (_: Exception) { updater.installerFailed() }
    }

    @Composable
    private fun UpdateDialog() {
        val u = updater
        if (!u.visible) return
        LaunchedEffect(u.ready) {
            if (u.ready && u.autoInstallPending) {
                u.autoInstallPending = false
                launchUpdateInstaller()
            }
        }
        AlertDialog(
            onDismissRequest = { if (!u.busy) u.dismiss() },
            containerColor = CardBg,
            title = { Text(stringResource(R.string.update_title, u.release?.tag.orEmpty()), fontFamily = Vazirmatn, color = TextPrimary) },
            text = { Column {
                Text(stringResource(when { u.failed -> R.string.update_error; u.busy -> R.string.update_downloading; u.ready -> R.string.update_permission; else -> R.string.update_message }), fontFamily = Vazirmatn, color = TextSecondary)
                if (u.busy) {
                    LinearProgressIndicator(progress = { u.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    Text(java.text.NumberFormat.getPercentInstance().format(u.progress / 100.0), fontFamily = Vazirmatn, color = TextSecondary)
                }
            } },
            confirmButton = { if (!u.busy) TextButton(onClick = { if (u.ready) launchUpdateInstaller() else u.download() }) {
                Text(stringResource(if (u.ready) R.string.update_install else if (u.failed) R.string.update_retry else R.string.update_download), fontFamily = Vazirmatn)
            } },
            dismissButton = { TextButton(onClick = { u.dismiss() }) { Text(stringResource(if (u.busy) R.string.update_cancel else R.string.update_later), fontFamily = Vazirmatn) } }
        )
    }

    private val flow = SingleActionPolicy()
    private var flowToken = 0L
    private var userCancelled = false
    private var flowJob: kotlinx.coroutines.Job? = null
    private var showTheme by mutableStateOf(false)
    private var showDisconnectWarning by mutableStateOf(false)
    private var booting by mutableStateOf(true)
    private var downloading by mutableStateOf(false)
    private var testing by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var connecting by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var tested by mutableStateOf(0)
    private var total by mutableStateOf(0)
    private var working by mutableStateOf(0)
    private var statusLine by mutableStateOf("")
    private var errorLine by mutableStateOf("")
    private var bestDelay by mutableLongStateOf(0L)
    private var winners by mutableStateOf<List<SimpleConfig.Winner>>(emptyList())
    private var showKillSwitch by mutableStateOf(false)
    private var osAlwaysOn by mutableStateOf<Boolean?>(null)
    private var osLockdown by mutableStateOf<Boolean?>(null)

    private fun querySystemVpnStatus() {
        osAlwaysOn = null
        osLockdown = null
        // No BIND_AUTO_CREATE: reading settings must not start a VPN or imply consent.
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                val reply = android.os.Messenger(android.os.Handler(mainLooper) { message ->
                    osAlwaysOn = if (message.data.containsKey("alwaysOn")) message.data.getBoolean("alwaysOn") else null
                    osLockdown = if (message.data.containsKey("lockdown")) message.data.getBoolean("lockdown") else null
                    true
                })
                runCatching { android.os.Messenger(binder).send(android.os.Message.obtain().apply { replyTo = reply }) }
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                osAlwaysOn = null
                osLockdown = null
            }
        }
        val intent = Intent(this, com.v2ray.ang.service.CoreVpnService::class.java).setAction("com.sahand.vpn.SYSTEM_STATUS")
        if (runCatching { bindService(intent, connection, 0) }.getOrDefault(false)) {
            android.os.Handler(mainLooper).postDelayed({ runCatching { unbindService(connection) } }, 1500)
        }
    }

    private var verifiedAt = 0L
    private fun sessionElapsed(): Long = if (connected && verifiedAt > 0)
        (android.os.SystemClock.elapsedRealtime() - verifiedAt) / 1000 else 0

    private fun openSystemVpnSettings() {
        for (action in listOf(android.provider.Settings.ACTION_VPN_SETTINGS, android.provider.Settings.ACTION_SETTINGS)) {
            if (runCatching { startActivity(Intent(action)) }.isSuccess) return
        }
        android.widget.Toast.makeText(this, R.string.kill_switch_settings_unavailable, android.widget.Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun KillSwitchDialog() {
        if (!showKillSwitch) return
        LaunchedEffect(Unit) { querySystemVpnStatus() }
        fun label(value: Boolean?) = getString(when(value) { true -> R.string.kill_switch_on; false -> R.string.kill_switch_off; null -> R.string.kill_switch_unknown })
        AlertDialog(
            onDismissRequest = { showKillSwitch = false },
            containerColor = CardBg,
            title = { Text(str(R.string.kill_switch_title), fontFamily = Vazirmatn, color = TextPrimary) },
            text = { Column(Modifier.height(360.dp).then(Modifier.verticalScroll(rememberScrollState()))) {
                Text(String.format(str(R.string.kill_switch_status), label(osAlwaysOn), label(osLockdown)), fontFamily = Vazirmatn, color = AccentGreen)
                Spacer(Modifier.height(12.dp))
                Text(str(R.string.kill_switch_explanation), fontFamily = Vazirmatn, color = TextSecondary, fontSize = 14.sp)
            } },
            confirmButton = { TextButton(onClick = { showKillSwitch = false; openSystemVpnSettings() }) { Text(str(R.string.kill_switch_settings), fontFamily = Vazirmatn) } },
            dismissButton = { TextButton(onClick = { showKillSwitch = false }) { Text(str(R.string.update_later), fontFamily = Vazirmatn) } }
        )
    }

    private var showLanguageDialog by mutableStateOf(false)
    private var languageCode by mutableStateOf("auto")

    private var lastProgressAt = 0L
    private var connectAttempt = 0
    private var pendingConnect = false
    private var searchRunId = ""

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!pendingConnect || !flow.accepts(flowToken)) return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                pendingConnect = false
                launchConnect()
            } else {
                pendingConnect = false
                connecting = false
                errorLine = getString(R.string.simple_vpn_denied)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Late progress from a cancelled search must not overwrite the restored state.
            if (!testing) return
            val safe = intent ?: return
            if (safe.getStringExtra(SimpleConfig.EXTRA_RUN_ID) != searchRunId) return
            val kind = safe.getIntExtra(SimpleConfig.EXTRA_KIND, 0)
            val phase = safe.getIntExtra(SimpleConfig.EXTRA_PHASE, 0)
            lastProgressAt = System.currentTimeMillis()
            tested = safe.getIntExtra(SimpleConfig.EXTRA_TESTED, tested)
            total = safe.getIntExtra(SimpleConfig.EXTRA_TOTAL, total)
            working = safe.getIntExtra(SimpleConfig.EXTRA_WORKING, working)
            if (kind == SimpleConfig.KIND_FINISH) {
                val json = safe.getStringExtra(SimpleConfig.EXTRA_WINNERS)
                val parsed = json?.let {
                    JsonUtil.fromJsonSafe(it, Array<SimpleConfig.Winner>::class.java)
                }?.toList().orEmpty()
                applyTestOutcome(parsed.sortedBy { it.delay })
            } else {
                when (phase) {
                    SimpleConfig.PHASE_SCANNING -> {
                        scanning = true
                        statusLine = getString(R.string.simple_checking)
                    }

                    else -> {
                        scanning = false
                        statusLine = getString(R.string.simple_searching)
                    }
                }
            }
        }
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safe = intent ?: return
            if (userCancelled) return
            if (testing || downloading) return
            when (safe.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_START -> {
                    connected = false
                    connecting = true
                    statusLine = getString(R.string.simple_connecting)
                }
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> {
                    if (!connected) verifiedAt = android.os.SystemClock.elapsedRealtime()
                    connecting = false
                    connected = true
                    errorLine = ""
                    statusLine = getString(R.string.simple_connected)
                }

                AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    connected = false
                    connecting = false
                    bestDelay = 0L
                    statusLine = if (winners.isNotEmpty()) getString(R.string.simple_ready)
                    else getString(R.string.simple_offline)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    connecting = false
                    connected = false
                    if (flow.accepts(flowToken) && connectAttempt < winners.size) {
                        // Silent failover: try the next best healthy profile.
                        launchConnect()
                    } else {
                        errorLine = getString(R.string.simple_connect_failed)
                        statusLine = getString(R.string.simple_error)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract geosite/geoip assets right away; the core needs them before it can start.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { com.v2ray.ang.handler.SettingsManager.initAssets(application, assets) }
        }
        ContextCompat.registerReceiver(
            this, testReceiver, IntentFilter(SimpleConfig.ACTION_TEST), Utils.receiverFlags()
        )
        ContextCompat.registerReceiver(
            this, serviceReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), Utils.receiverFlags()
        )
        MessageHelper.sendMsg2Service(application, AppConfig.MSG_REGISTER_CLIENT, "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        languageCode = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE) ?: "auto"
        runCatching {
            val scale = android.provider.Settings.Global.getFloat(
                contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            LogUtil.w(AppConfig.TAG, "SimpleUI: animator_duration_scale=$scale")
        }
        bootstrap()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(testReceiver) }
        runCatching { unregisterReceiver(serviceReceiver) }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- data

    private fun bootstrap() {
        lifecycleScope.launch {
            val known = withContext(Dispatchers.IO) {
                runCatching { MmkvManager.decodeServerList(SimpleConfig.SUB_ID).size }.getOrDefault(0)
            }
            total = known
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val alive = MmkvManager.decodeServerList(SimpleConfig.SUB_ID).toHashSet()
                    SimpleConfig.pruneWinners(this@SimpleActivity, alive)
                }.getOrDefault(emptyList())
            }
            winners = if (saved.isEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching { SimpleConfig.restoreIfEmpty(this@SimpleActivity) }.getOrDefault(emptyList())
                }
            } else {
                saved
            }
            bestDelay = winners.firstOrNull()?.delay ?: 0L
            tested = withContext(Dispatchers.IO) { SimpleConfig.lastTested(this@SimpleActivity) }
            if (known == 0) {
                booting = false
            } else {
                booting = false
                working = winners.size
                if (!connected && !connecting) statusLine = if (winners.isNotEmpty()) getString(R.string.simple_ready)
                else getString(R.string.simple_offline)
            }
        }
    }

    private fun syncSubscription(firstRun: Boolean) {
        if (downloading) return
        downloading = true
        if (firstRun) {
            statusLine = getString(R.string.simple_loading)
        } else {
        }
        val syncToken = flowToken
        flowJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MmkvManager.encodeSubscription(
                        SimpleConfig.SUB_ID,
                        SubscriptionItem(
                            remarks = SimpleConfig.SUB_NAME,
                            url = SimpleConfig.SUB_URL,
                            autoUpdate = false
                        )
                    )
                    val content = HttpUtil.getUrlContent(
                        UrlContentRequest(url = SimpleConfig.SUB_URL, timeout = 120_000)
                    ) ?: return@runCatching -1
                    val (count, _) = AngConfigManager.importBatchConfig(
                        content, SimpleConfig.SUB_ID, append = false
                    )
                    count
                }.getOrElse {
                    LogUtil.e(AppConfig.TAG, "SimpleActivity: subscription sync failed", it)
                    -1
                }
            }
            if (!flow.accepts(syncToken)) return@launch
            downloading = false
            booting = false
            if (result > 0) {
                total = result
                winners = withContext(Dispatchers.IO) {
                    runCatching {
                        val alive = MmkvManager.decodeServerList(SimpleConfig.SUB_ID).toHashSet()
                        SimpleConfig.pruneWinners(this@SimpleActivity, alive)
                    }.getOrDefault(emptyList())
                }
                bestDelay = winners.firstOrNull()?.delay ?: 0L
                tested = 0
                working = winners.size
                errorLine = ""
                if (flow.accepts(syncToken)) startTest()
            } else {
                flow.cancel()
                errorLine = getString(R.string.simple_list_failed)
                statusLine = getString(R.string.simple_error)
            }
        }
    }

    // ------------------------------------------------------------ polling

    /**
     * The search service persists its outcome, so the screen stays correct even if the
     * finishing broadcast never reaches the activity (service shutdown races).
     */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val poller = object : Runnable {
        override fun run() {
            if (!testing) return
            val saved = runCatching { SimpleConfig.loadWinners(this@SimpleActivity) }
                .getOrDefault(emptyList())
            // No progress for 25s means the search process is gone or wedged: finish with
            // whatever healthy points were already persisted.
            val stalled = lastProgressAt > 0 &&
                System.currentTimeMillis() - lastProgressAt > 25_000L
            val state = SimpleConfig.read(this@SimpleActivity)
            if (state.runId == searchRunId && (state.phase != "running" || stalled)) {
                applyTestOutcome(saved)
                return
            }
            handler.postDelayed(this, 1200)
        }
    }

    private fun startPolling() {
        stopPolling()
        handler.postDelayed(poller, 1500)
    }

    private fun isTestServiceAlive(): Boolean {
        return runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getRunningServices(200).any {
                it.service.className == SimpleTestService::class.java.name
            }
        }.getOrDefault(true)
    }

    private fun stopPolling() {
        handler.removeCallbacks(poller)
    }

    private fun applyTestOutcome(saved: List<SimpleConfig.Winner>) {
        SimpleConfig.cancelSearch(this)
        testing = false
        scanning = false
        stopPolling()
        val resolved = if (saved.isEmpty()) {
            runCatching { SimpleConfig.restoreIfEmpty(this@SimpleActivity) }.getOrDefault(emptyList())
        } else {
            saved
        }
        winners = resolved.sortedBy { it.delay }
        bestDelay = winners.firstOrNull()?.delay ?: 0L
        working = winners.size
        statusLine = when {
            winners.isEmpty() -> getString(R.string.simple_no_points)
            else -> getString(R.string.simple_ready)
        }
        if (flow.searchFinished(flowToken, saved.isNotEmpty()) == Action.CONNECT) {
            connectAttempt = 0
            launchConnect()
        }
    }

    // ------------------------------------------------------------- actions

    private fun hasInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.let {
            it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } == true }
    }

    private fun primaryAction() {
        if (connected) {
            querySystemVpnStatus()
            showDisconnectWarning = true // Unknown is explicitly caveated, never presented as off.
            return
        }
        if (testing || connecting || downloading || flow.requested) { cancelFlow(); return }
        if (booting) return
        // The search service retests cached winners before doing a broad scan.
        if (flow.begin(hasInternet(), false) == Action.NONE) {
            errorLine = getString(R.string.simple_no_internet)
            return
        }
        userCancelled = false
        flowToken = flow.generation
        if (total == 0) syncSubscription(true) else startTest()
    }

    private fun cancelFlow() {
        userCancelled = true
        flow.cancel()
        pendingConnect = false
        flowJob?.cancel()
        downloading = false
        connecting = false
        connected = false
        cancelTest()
        LauncherManager.stopService(this)
        errorLine = ""
    }

    private fun startTest() {
        if (downloading || connecting) return
        if (total == 0) {
            syncSubscription(firstRun = true)
            return
        }
        errorLine = ""
        testing = true
        scanning = true
        tested = 0
        lastProgressAt = System.currentTimeMillis()
        working = 0
        winners = emptyList()
        statusLine = getString(R.string.simple_searching)
        searchRunId = SimpleConfig.beginSearch(this)
        SimpleTestService.start(this, searchRunId)
        startPolling()
    }

    private fun cancelTest() {
        testing = false
        stopPolling()
        SimpleConfig.cancelSearch(this)
        SimpleTestService.cancel(this)
        winners = runCatching { SimpleConfig.loadWinners(this@SimpleActivity) }.getOrDefault(emptyList())
        if (winners.isEmpty()) {
            // A cancelled search must not throw away the points found by the previous one.
            winners = runCatching { SimpleConfig.restoreIfEmpty(this@SimpleActivity) }
                .getOrDefault(emptyList())
        }
        bestDelay = winners.firstOrNull()?.delay ?: 0L
        working = winners.size
        statusLine = if (winners.isNotEmpty()) getString(R.string.simple_ready)
        else getString(R.string.simple_offline)
    }

    private fun launchConnect() {
        if (!flow.accepts(flowToken)) return
        val winner = winners.getOrNull(connectAttempt)
        if (winner == null) {
            connecting = false
            flow.cancel()
            errorLine = getString(R.string.simple_none_connected)
            return
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingConnect = true
            connecting = true
            vpnPermissionLauncher.launch(prepareIntent)
            return
        }
        startVpn(winner)
    }

    private fun startVpn(winner: SimpleConfig.Winner) {
        if (!flow.accepts(flowToken)) return
        connectAttempt++
        connecting = true
        statusLine = getString(R.string.simple_connecting)
        runCatching { LauncherManager.startService(this, winner.guid) }
            .onFailure {
                connecting = false
                errorLine = it.message ?: getString(R.string.simple_connect_failed)
                if (connectAttempt < winners.size) launchConnect()
            }
    }

    // --------------------------------------------------------------- text

    /** Numbers follow the active locale: Persian digits for fa, grouped Latin digits otherwise. */
    private fun num(value: Int): String =
        if (isPersian()) fa(value) else String.format(Locale.US, "%,d", value)

    private fun num(value: Long): String =
        if (isPersian()) fa(value) else String.format(Locale.US, "%,d", value)

    private fun isPersian(): Boolean =
        resources.configuration.locales[0].language == "fa"

    private fun fa(value: Int): String = fa(value.toString())

    private fun fa(value: Long): String = fa(value.toString())

    private fun fa(raw: String): String {
        val grouped = raw.reversed().chunked(3).joinToString("٬").reversed()
        val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return grouped.map { ch ->
            if (ch in '0'..'9') digits[ch - '0'] else ch
        }.joinToString("")
    }

    private fun str(@StringRes id: Int): String = getString(id)

    // ----------------------------------------------------------- composable

    @Composable
    override fun ScreenContent() {
        UpdateDialog()
        KillSwitchDialog()
        if (showTheme) AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text(str(R.string.simple_theme), fontFamily = Vazirmatn) },
            text = { Column {
                listOf("0" to R.string.simple_theme_system, "1" to R.string.simple_theme_light, "2" to R.string.simple_theme_dark).forEach { (mode, label) ->
                    TextButton(onClick = { ThemeManager.setThemeMode(mode); showTheme = false }) { Text(str(label), fontFamily = Vazirmatn) }
                }
            } }, confirmButton = {}
        )
        if (showDisconnectWarning) AlertDialog(
            onDismissRequest = { showDisconnectWarning = false },
            title = { Text(str(R.string.simple_disconnect), fontFamily = Vazirmatn) },
            text = { Text(str(R.string.simple_disconnect_warning), fontFamily = Vazirmatn) },
            confirmButton = { TextButton(onClick = { showDisconnectWarning = false; cancelFlow() }) { Text(str(R.string.simple_disconnect)) } },
            dismissButton = { TextButton(onClick = { showDisconnectWarning = false }) { Text(str(R.string.simple_ok)) } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBottom)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (connected) AccentGreen else AccentBlue).copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            radius = 760f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header()

                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentBlue,
                        trackColor = Color(0xFF16243A)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StatusRing(
                            connected = connected,
                            busy = connecting || testing || downloading,
                            title = if (connected) str(R.string.simple_connected) else if (connecting || testing || downloading) statusLine else str(R.string.simple_connect),
                            subtitle = if (connecting || testing || downloading) str(R.string.simple_cancel) else if (connected) str(R.string.simple_disconnect) else null,
                            onClick = { primaryAction() }
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = str(if (connected) R.string.simple_connected_hint else if (connecting || testing || downloading) R.string.simple_search_hint else R.string.simple_tap_hint),
                            color = TextSecondary,
                            fontSize = 15.5.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                        )

                        if (errorLine.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = errorLine,
                                    color = AccentRed,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = Vazirmatn,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                    }
                }

                Footer()
            }
        }

        if (showLanguageDialog) {
            LanguageDialog(
                current = languageCode,
                onSelect = { code ->
                    showLanguageDialog = false
                    languageCode = code
                    AppLocaleManager.setApplicationLanguage(code)
                },
                onDismiss = { showLanguageDialog = false }
            )
        }
    }

    @Composable
    private fun Header() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    ,
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_stat_sahand),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AccentGreen),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sahand VPN",
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str(R.string.simple_subtitle),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = Vazirmatn,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconAction(R.string.simple_theme, if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.drawable.ic_day else R.drawable.ic_night) { showTheme = true }
                IconAction(R.string.simple_language, R.drawable.ic_translate_24dp) { showLanguageDialog = true }
            }
        }
    }

    @Composable
    private fun Footer() {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconAction(R.string.simple_update_check, R.drawable.ic_check_update_24dp) { updater.checkManually() }
            IconAction(R.string.kill_switch_title, R.drawable.ic_privacy_24dp) { showKillSwitch = true }
        }
    }

    @Composable
    private fun IconAction(@StringRes label: Int, image: Int, onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(painter = painterResource(image), contentDescription = str(label), tint = TextSecondary, modifier = Modifier.size(24.dp))
        }
    }

    @Composable
    private fun LanguageGlyph(color: Color) {
        Canvas(modifier = Modifier.size(19.dp)) {
            val stroke = 1.5.dp.toPx()
            val r = size.minDimension / 2f - stroke / 2f
            drawCircle(color, radius = r, style = Stroke(stroke))
            drawOval(
                color = color,
                topLeft = Offset(size.width / 2f - r * 0.45f, size.height / 2f - r),
                size = Size(r * 0.9f, r * 2f),
                style = Stroke(stroke)
            )
            drawLine(
                color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
                strokeWidth = stroke
            )
        }
    }

    /** Only the search field moves. Idle is still; accessibility reduction wins. */
    @Composable
    private fun rememberSearchPhase(active: Boolean): Float {
        var phase by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(active) {
            phase = 0f
            if (!active) return@LaunchedEffect
            var start = 0L
            while (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()) {
                withFrameNanos { now ->
                    if (start == 0L) start = now
                    phase = ((now - start) / 1_000_000f % 2600f) / 2600f
                }
            }
            phase = 0f
        }
        return phase
    }

    @Composable
    private fun StatusRing(
        connected: Boolean,
        busy: Boolean,
        title: String,
        subtitle: String?,
        onClick: (() -> Unit)? = null
    ) {
        val target = when {
            connected -> AccentGreen
            busy -> AccentBlue
            else -> AccentGreen
        }
        val accent by animateColorAsState(target, tween(450), label = "accent")

        val motionEnabled = Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()
        val phase = rememberSearchPhase(active = busy && motionEnabled)
        val settle by animateFloatAsState(if (connected) 1f else 0f, tween(900), label = "secure-field")

        Box(
            modifier = Modifier.size((240 * LocalDensity.current.fontScale.coerceAtMost(1.5f)).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(212.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accent.copy(alpha = if (connected || busy) 0.18f else 0.05f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            val track = CardBorder
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.43f
                // A quiet contour field becomes outward travelling signal waves during search.
                // On connection the contours settle into a complete protective boundary.
                for (layer in 0..3) {
                    val travel = if (busy) (phase + layer / 4f) % 1f else layer / 4f
                    val r = radius * (0.76f + travel * 0.35f)
                    val opacity = if (busy) (1f - travel) * 0.48f else 0.12f + settle * 0.15f
                    val path = androidx.compose.ui.graphics.Path()
                    for (step in 0..180) {
                        val theta = step * kotlin.math.PI * 2 / 180
                        val contour = 1f + (0.035f * (1f - settle)) * kotlin.math.sin(theta * 6 + phase * kotlin.math.PI * 2).toFloat()
                        val point = Offset(center.x + r * contour * kotlin.math.cos(theta).toFloat(), center.y + r * contour * kotlin.math.sin(theta).toFloat())
                        if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    path.close()
                    drawPath(path, if (busy || connected) accent.copy(alpha = opacity) else track.copy(alpha = 0.65f), style = Stroke(1.5.dp.toPx()))
                }
                if (connected) {
                    drawCircle(accent.copy(alpha = 0.10f), radius * 0.83f)
                    drawCircle(accent, radius * 0.84f, style = Stroke(2.dp.toPx()))
                }
            }

            Box(
                modifier = Modifier
                    .size((158 * LocalDensity.current.fontScale.coerceAtMost(1.5f)).dp)
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(1.dp, CardBorder, CircleShape)
                    .semantics { stateDescription = title }
                    .clickable(enabled = onClick != null, role = Role.Button, onClickLabel = subtitle ?: title) { onClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Vazirmatn,
                        textAlign = TextAlign.Center
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            color = accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Vazirmatn
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LanguageDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = str(R.string.simple_language),
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    LanguageRow(
                        label = str(R.string.simple_language_system),
                        selected = current == "auto",
                        onClick = { onSelect("auto") }
                    )
                    LanguageRow(
                        label = "فارسی",
                        selected = current == "fa",
                        onClick = { onSelect("fa") }
                    )
                    LanguageRow(
                        label = "English",
                        selected = current == "en",
                        onClick = { onSelect("en") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = str(R.string.simple_ok), fontFamily = Vazirmatn)
                }
            }
        )
    }

    @Composable
    private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (selected) AccentGreen else Color(0xFF52657F),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                fontFamily = Vazirmatn,
                fontSize = 15.sp,
                color = if (selected) TextPrimary else TextSecondary
            )
        }
    }
}
