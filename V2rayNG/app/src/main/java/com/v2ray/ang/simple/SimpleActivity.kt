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

private val BgTop = Color(0xFF070B14)
private val BgMid = Color(0xFF0B1526)
private val BgBottom = Color(0xFF05090F)
private val CardBg = Color(0xFF101C2F)
private val CardBorder = Color(0xFF1D2C45)
private val TextPrimary = Color(0xFFF3F7FF)
private val TextSecondary = Color(0xFFB7C5DB)
private val TextMuted = Color(0xFF93A3BC)
private val AccentGreen = Color(0xFF3EE0A6)
private val AccentBlue = Color(0xFF6A97FF)
private val AccentRed = Color(0xFFFF8F84)

/**
 * Single screen client: a find button and a connect button, no profile names.
 *
 * Press "find connection point" -> probes profiles with a real Xray outbound until
 * [SimpleConfig.TARGET_WORKING] healthy ones are found. Press "connect" -> the fastest
 * healthy profile is used.
 */
class SimpleActivity : BaseComponentActivity() {

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
    private var showLanguageDialog by mutableStateOf(false)
    private var languageCode by mutableStateOf("auto")

    private var lastProgressAt = 0L
    private var connectAttempt = 0
    private var pendingConnect = false
    private var searchRunId = ""

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
            when (safe.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_START -> {
                    connected = false
                    connecting = true
                    statusLine = getString(R.string.simple_connecting)
                }
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> {
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
                    if (connectAttempt < winners.size) {
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
                syncSubscription(firstRun = true)
            } else {
                booting = false
                working = winners.size
                statusLine = if (winners.isNotEmpty()) getString(R.string.simple_ready)
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
        lifecycleScope.launch {
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
                statusLine = if (winners.isNotEmpty()) getString(R.string.simple_ready)
                else getString(R.string.simple_offline)
            } else {
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
    }

    // ------------------------------------------------------------- actions

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

    private fun toggleConnection() {
        if (connected || connecting) {
            pendingConnect = false
            connectAttempt = winners.size
            connected = false
            connecting = false
            LauncherManager.stopService(this)
            return
        }
        if (winners.isEmpty()) return
        errorLine = ""
        connectAttempt = 0
        launchConnect()
    }

    private fun launchConnect() {
        val winner = winners.getOrNull(connectAttempt)
        if (winner == null) {
            connecting = false
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
                Header(
                    onLanguage = { showLanguageDialog = true },
                    onReloadSubscription = { if (!testing && !downloading) syncSubscription(false) }
                )

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
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StatusRing(
                            connected = connected,
                            busy = connecting || testing || downloading,
                            title = statusLine.ifBlank { str(R.string.simple_ready) },
                            subtitle = if (connected && bestDelay > 0) "${num(bestDelay)} ms" else null,
                            onClick = if (!booting && !downloading &&
                                (connected || connecting || winners.isNotEmpty())
                            ) {
                                { toggleConnection() }
                            } else {
                                null
                            }
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = str(R.string.simple_find_best),
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
                                    .background(Color(0xFF2A1620))
                                    .border(1.dp, Color(0xFF432330), RoundedCornerShape(14.dp))
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

                        PointsCard(
                            value = working.coerceAtLeast(winners.size),
                            label = str(R.string.simple_points)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                ActionButton(
                    step = str(R.string.simple_step_one),
                    label = if (testing) str(R.string.simple_cancel_find) else str(R.string.simple_find),
                    primary = false,
                    danger = testing,
                    enabled = !booting && !downloading && !connecting,
                    loading = testing,
                    onClick = { if (testing) cancelTest() else startTest() }
                )

                Spacer(Modifier.height(12.dp))

                ActionButton(
                    step = str(R.string.simple_step_two),
                    label = when {
                        connecting -> str(R.string.simple_connecting)
                        connected -> str(R.string.simple_disconnect)
                        else -> str(R.string.simple_connect)
                    },
                    primary = true,
                    danger = connected,
                    enabled = !booting && !downloading && !connecting && (connected || winners.isNotEmpty()),
                    loading = connecting,
                    onClick = { toggleConnection() }
                )
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
    private fun Header(onLanguage: () -> Unit, onReloadSubscription: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF17364F), Color(0xFF0D2136))))
                    .border(1.dp, Color(0xFF244661), RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = {}, onLongClick = onReloadSubscription),
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
                    color = Color(0xFFA6B6CC),
                    fontSize = 12.sp,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF13233A))
                    .border(1.dp, Color(0xFF2A4062), RoundedCornerShape(12.dp))
                    .clickable(onClick = onLanguage),
                contentAlignment = Alignment.Center
            ) {
                LanguageGlyph(color = Color(0xFF9CD3EA))
            }
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

    @Composable
    private fun PointsCard(value: Int, label: String) {
        val animated by animateIntAsState(targetValue = value, animationSpec = tween(450), label = "points")
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val ink = AccentGreen
                val dot = 2.6.dp.toPx()
                drawCircle(ink.copy(alpha = 0.45f), dot, Offset(4.dp.toPx(), 5.dp.toPx()))
                drawCircle(ink.copy(alpha = 0.7f), dot, Offset(13.dp.toPx(), 4.dp.toPx()))
                drawCircle(ink, dot, Offset(9.dp.toPx(), 14.dp.toPx()))
                val st = 1.2.dp.toPx()
                drawLine(ink.copy(alpha = 0.45f), Offset(4.dp.toPx(), 5.dp.toPx()), Offset(9.dp.toPx(), 14.dp.toPx()), st)
                drawLine(ink.copy(alpha = 0.45f), Offset(13.dp.toPx(), 4.dp.toPx()), Offset(9.dp.toPx(), 14.dp.toPx()), st)
                drawLine(ink.copy(alpha = 0.45f), Offset(4.dp.toPx(), 5.dp.toPx()), Offset(13.dp.toPx(), 4.dp.toPx()), st)
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = num(animated),
                color = AccentGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Vazirmatn
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = label,
                color = TextSecondary,
                fontSize = 13.5.sp,
                fontFamily = Vazirmatn
            )
        }
    }

    /**
     * Rotation that is driven by the frame clock instead of Compose's animation APIs.
     *
     * Some phones report animator_duration_scale = 0 (developer options, battery saver or the
     * "remove animations" accessibility switch). Compose animations honour that flag and freeze,
     * which made the loading motion disappear; a frame loop keeps the feedback alive.
     */
    @Composable
    private fun rememberFrameAngle(active: Boolean, periodMs: Int = 1700): Float {
        var angle by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(active) {
            if (!active) return@LaunchedEffect
            var start = 0L
            while (true) {
                withFrameNanos { now ->
                    if (start == 0L) start = now
                    val elapsed = (now - start) / 1_000_000f
                    angle = (elapsed % periodMs) / periodMs * 360f
                }
            }
        }
        return angle
    }

    /** One-shot 0..1 ramp driven by frames, used for the connection ripple. */
    @Composable
    private fun rememberFrameRamp(trigger: Boolean, durationMs: Int = 1300): Float {
        var progress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(trigger) {
            if (!trigger) {
                progress = 0f
                return@LaunchedEffect
            }
            var start = 0L
            var finished = false
            while (!finished) {
                withFrameNanos { now ->
                    if (start == 0L) start = now
                    val t = ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
                    progress = t
                    finished = t >= 1f
                }
            }
        }
        return progress
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
            else -> Color(0xFF3B4C69)
        }
        val accent by animateColorAsState(target, tween(450), label = "accent")

        val rotation = rememberFrameAngle(active = busy)
        val pulse = 1f + 0.045f * kotlin.math.sin(Math.toRadians(rotation.toDouble())).toFloat()
        val ripple = rememberFrameRamp(trigger = connected, durationMs = 1400)
        val discScale by animateFloatAsState(
            targetValue = if (connected) 1.03f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "disc"
        )

        Box(
            modifier = Modifier.size(212.dp),
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 11.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                drawArc(
                    color = Color(0xFF16243A),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Expanding waves right after the connection is established.
                if (connected && ripple > 0f && ripple < 1f) {
                    val progress = ripple
                    val base = size.minDimension / 2f
                    for (wave in 0..1) {
                        val t = (progress - wave * 0.18f).coerceIn(0f, 1f)
                        if (t <= 0f) continue
                        drawCircle(
                            color = accent.copy(alpha = 0.28f * (1f - t)),
                            radius = base * (0.72f + 0.42f * t),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                if (connected) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(accent.copy(alpha = 0.55f), accent, accent.copy(alpha = 0.55f))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else if (busy) {
                    rotate(degrees = rotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, accent.copy(alpha = 0.15f), accent)
                            ),
                            startAngle = -90f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    rotate(degrees = rotation + 180f, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, accent.copy(alpha = 0.12f), accent.copy(alpha = 0.75f))
                            ),
                            startAngle = -90f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(158.dp)
                    .graphicsLayer {
                        val s = if (busy && !connected) pulse else discScale
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF111E33), Color(0xFF0A1424))))
                    .border(1.dp, Color(0xFF1E2E49), CircleShape)
                    .clickable(enabled = onClick != null) { onClick?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 24.sp,
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
    private fun ActionButton(
        step: String,
        label: String,
        primary: Boolean,
        danger: Boolean,
        enabled: Boolean,
        loading: Boolean,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(18.dp)
        val gradient = when {
            !primary -> null
            !enabled -> listOf(Color(0xFF121D2E), Color(0xFF101A2A))
            danger -> listOf(Color(0xFF77323F), Color(0xFF9A4152))
            else -> listOf(Color(0xFF12A97E), Color(0xFF0A6E56))
        }
        val surface = when {
            primary -> null
            danger -> Color(0xFF331D28)
            else -> Color(0xFF233651)
        }
        val borderColor = when {
            danger -> Color(0xFF63394A)
            else -> Color(0xFF3D5D87)
        }
        val ink = when {
            danger -> Color(0xFFFFC2B6)
            primary -> Color.White
            else -> Color(0xFFF2F7FF)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = if (primary && enabled) 16.dp else 0.dp,
                    shape = shape,
                    spotColor = if (danger) Color(0x6677020F) else Color(0x5512A97E)
                )
                .clip(shape)
                .background(
                    when {
                        gradient != null -> Brush.horizontalGradient(gradient)
                        else -> Brush.horizontalGradient(listOf(surface!!, surface))
                    }
                )
                .then(
                    if (primary) Modifier else Modifier.border(1.dp, borderColor, shape)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = label,
                    color = if (enabled) ink else Color(0xFF5A6A82),
                    fontSize = 16.5.sp,
                    fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = Vazirmatn,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 58.dp)
                )
                StepBadge(
                    step = step,
                    loading = loading,
                    ink = if (enabled) ink else Color(0xFF5A6A82),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 18.dp)
                )
            }
        }
    }

    @Composable
    private fun StepBadge(
        step: String,
        loading: Boolean,
        ink: Color,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                val angle = rememberFrameAngle(active = true, periodMs = 900)
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawArc(
                        color = ink,
                        startAngle = angle,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            } else {
                Text(
                    text = step,
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Vazirmatn
                )
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
