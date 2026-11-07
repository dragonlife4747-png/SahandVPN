package com.v2ray.ang.simple

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Finds healthy profiles of the built-in subscription with a two stage probe:
 *
 * 1. Fast parallel TCP reachability scan over unique server endpoints (cheap, no core).
 * 2. Real outbound probes through the Xray core on the fastest endpoints, low concurrency
 *    and a hard per-probe deadline, stopping once [SimpleConfig.TARGET_WORKING] were found.
 *
 * Each core probe starts its own Xray instance, so a wedged native call is abandoned at the
 * deadline instead of stalling the whole run.
 */
class SimpleTestService : Service() {

    private var worker: Thread? = null
    private var runId = ""
    private val cancelled = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        runCatching { com.v2ray.ang.handler.SettingsManager.initAssets(this, assets) }
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.simple_testing_notification),
            null
        )

        if (intent?.getBooleanExtra(EXTRA_CANCEL, false) == true) {
            cancelled.set(true)
            return START_NOT_STICKY
        }
        if (worker != null) return START_NOT_STICKY

        cancelled.set(false)
        runId = intent?.getStringExtra(SimpleConfig.EXTRA_RUN_ID).orEmpty()
        if (runId.isBlank()) { finishService(); return START_NOT_STICKY }
        worker = Thread({ runTest() }, "SimpleTestRunner").apply { start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled.set(true)
        super.onDestroy()
    }

    private fun runTest() {
        val guids = MmkvManager.decodeServerList(SimpleConfig.SUB_ID)
        val total = guids.size
        if (total == 0) {
            publishFinish(0, 0, emptyList())
            finishService()
            return
        }

        val startedAt = System.currentTimeMillis()
        val tested = AtomicInteger(0)
        val working = CopyOnWriteArrayList<SimpleConfig.Winner>()
        val lastPublish = AtomicLong(0L)

        fun publish(phase: Int, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPublish.get() < 250L) return
            lastPublish.set(now)
            publishProgress(phase, tested.get(), total, working.size)
        }

        val cached = SimpleConfig.read(this).backup.map { it.guid }
            .filter { MmkvManager.decodeServerConfig(it) != null }
            .take(SimpleConfig.TARGET_WORKING)
        val engine = SimpleProbeEngine(this)
        if (cached.isNotEmpty() && !cancelled.get()) {
            publish(SimpleConfig.PHASE_PROBING, force = true)
            val results = engine.probe(cached, deep = true, timeoutMs = 6_000)
            tested.addAndGet(cached.size)
            if (!cancelled.get()) {
                working.addAll(results.filter { it.httpsOk && it.delayMs > 0 }
                    .map { SimpleConfig.Winner(it.guid, it.delayMs) })
                SimpleConfig.updateSearch(this, runId, working)
                publish(SimpleConfig.PHASE_PROBING, force = true)
            }
        }

        if (working.isNotEmpty()) {
            if (SimpleConfig.updateSearch(this, runId, working, "done")) {
                publishFinish(tested.get(), total, working.toList())
            }
            return
        }

        // Group every profile by its unique server endpoint; duplicates share one TCP probe.
        val endpoints = LinkedHashMap<String, MutableList<String>>()
        val endpointHostPort = HashMap<String, Pair<String, Int>>()
        for (guid in guids) {
            if (cancelled.get()) break
            val config = MmkvManager.decodeServerConfig(guid) ?: continue
            val host = config.server.orEmpty().trim()
            val port = config.serverPort?.toIntOrNull() ?: continue
            if (host.isBlank() || port !in 1..65535) continue
            val key = "$host:$port".lowercase()
            endpointHostPort[key] = host to port
            endpoints.getOrPut(key) { mutableListOf() }.add(guid)
        }

        val fast = LinkedHashMap<String, SimpleProbeEngine.ProbeResult>()
        val deepDone = HashSet<String>()

        val shuffled = endpoints.keys.shuffled(kotlin.random.Random(System.currentTimeMillis()))
        val scanner = Executors.newFixedThreadPool(SimpleConfig.TCP_CONCURRENCY)
        var cursor = 0
        var scannedEndpoints = 0
        var reachableCount = 0

        while (!cancelled.get()
            && working.size < SimpleConfig.TARGET_WORKING
            && cursor < shuffled.size
            && cursor < SimpleConfig.MAX_ENDPOINTS_PER_RUN
        ) {
            val chunk = shuffled.subList(
                cursor,
                minOf(cursor + SimpleConfig.SCAN_CHUNK, shuffled.size)
            ).toList()
            cursor += chunk.size

            publishPhase(SimpleConfig.PHASE_SCANNING, scannedEndpoints, shuffled.size)
            val reachable = CopyOnWriteArrayList<Pair<String, Long>>()
            val roundLatch = CountDownLatch(chunk.size)
            val scannedInRound = AtomicInteger(0)
            val lastScanPublish = AtomicLong(System.currentTimeMillis())
            for (key in chunk) {
                scanner.execute {
                    try {
                        if (!cancelled.get()) {
                            val hp = endpointHostPort[key]
                            if (hp != null) {
                                val latency = tcpProbe(hp.first, hp.second)
                                if (latency > 0) reachable.add(key to latency)
                            }
                        }
                    } finally {
                        val done = scannedInRound.incrementAndGet()
                        val now = System.currentTimeMillis()
                        if (now - lastScanPublish.get() > 600L) {
                            lastScanPublish.set(now)
                            publishPhase(
                                SimpleConfig.PHASE_SCANNING,
                                scannedEndpoints + done,
                                shuffled.size
                            )
                        }
                        roundLatch.countDown()
                    }
                }
            }
            runCatching { roundLatch.await(60, TimeUnit.SECONDS) }
            scannedEndpoints += chunk.size
            reachableCount += reachable.size
            if (cancelled.get()) break

            // ---- fast stage: latency for many candidates through one shared core ----
            val candidates = ArrayList<String>()
            reachable.sortedBy { it.second }.forEach { pair ->
                endpoints[pair.first]?.take(3)?.let { candidates.addAll(it) }
            }
            publish(SimpleConfig.PHASE_PROBING, force = true)

            var candidateCursor = 0
            var batchesThisRound = 0
            while (!cancelled.get()
                && batchesThisRound < SimpleConfig.MAX_FAST_BATCHES_PER_ROUND
                && working.size < SimpleConfig.TARGET_WORKING
                && fast.size < SimpleConfig.FAST_RESULTS_TARGET
                && candidateCursor < candidates.size
            ) {
                val batch = candidates.subList(
                    candidateCursor,
                    minOf(candidateCursor + SimpleProbeEngine.BATCH_SIZE, candidates.size)
                ).toList()
                candidateCursor += batch.size
                batchesThisRound++
                val results = engine.probe(batch, deep = false, timeoutMs = 6_000)
                tested.addAndGet(batch.size)
                results.forEach { fast[it.guid] = it }
                LogUtil.w(
                    SimpleConfig.logTag(),
                    "SimpleTest: fast batch=${batch.size} answered=${results.size} total=${fast.size}"
                )
                publish(SimpleConfig.PHASE_PROBING)
            }

            // ---- final stage: TLS + throughput for the fastest untested candidates ----
            val finalists = fast.values
                .filter { it.guid !in deepDone }
                .sortedBy { it.delayMs }
                .take(SimpleConfig.FINALISTS)
            if (finalists.isNotEmpty() && !cancelled.get()) {
                val ids = finalists.map { it.guid }
                deepDone.addAll(ids)
                val deep = engine.probe(ids, deep = true, timeoutMs = 8_000)
                LogUtil.w(
                    SimpleConfig.logTag(),
                    "SimpleTest: deep finalists=${ids.size} answered=${deep.size} " +
                        deep.joinToString { "${it.delayMs}ms/https=${it.httpsOk}/${it.bytesPerSecond / 1024}KBps" }
                )
                deep.filter { it.httpsOk && it.delayMs > 0 }
                    .sortedWith(
                        compareByDescending<SimpleProbeEngine.ProbeResult> { it.httpsOk }
                            .thenByDescending { it.bytesPerSecond }
                            .thenBy { it.delayMs }
                    )
                    .forEach { result ->
                        if (working.none { it.guid == result.guid }) {
                            working.add(SimpleConfig.Winner(result.guid, result.delayMs))
                        }
                    }
                if (working.isNotEmpty()) {
                    // Persist as we go: a killed process keeps whatever it already found.
                    if (!cancelled.get()) SimpleConfig.updateSearch(this, runId, working)
                }
                publish(SimpleConfig.PHASE_PROBING, force = true)
            }
        }

        scanner.shutdown()
        if (cancelled.get()) {
            finishService()
            return
        }

        val winners = working.sortedBy { it.delay }.take(SimpleConfig.TARGET_WORKING)
        if (!SimpleConfig.updateSearch(this, runId, winners, if (winners.isEmpty()) "empty" else "done")) {
            finishService(); return
        }
        LogUtil.w(
            SimpleConfig.logTag(),
            "SimpleTest: finished in ${System.currentTimeMillis() - startedAt}ms, " +
                "${winners.size} healthy out of ${tested.get()} probed, " +
                "$reachableCount/$scannedEndpoints endpoints reachable"
        )
        publishFinish(tested.get(), total, winners)
        finishService()
    }

    /** Raw TCP connect time in ms, or -1. */
    private fun tcpProbe(host: String, port: Int): Long {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), SimpleConfig.TCP_TIMEOUT_MS)
                System.currentTimeMillis() - start
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun publishProgress(phase: Int, tested: Int, total: Int, working: Int) {
        send(SimpleConfig.KIND_PROGRESS, phase, tested, total, working, emptyList())
    }

    private fun publishPhase(phase: Int, tested: Int, total: Int) {
        send(SimpleConfig.KIND_PROGRESS, phase, tested, total, 0, emptyList())
    }

    private fun publishFinish(tested: Int, total: Int, winners: List<SimpleConfig.Winner>) {
        send(SimpleConfig.KIND_FINISH, SimpleConfig.PHASE_DONE, tested, total, winners.size, winners)
    }

    private fun send(
        kind: Int,
        phase: Int,
        tested: Int,
        total: Int,
        working: Int,
        winners: List<SimpleConfig.Winner>
    ) {
        try {
            val intent = Intent(SimpleConfig.ACTION_TEST)
            intent.setPackage(packageName)
            intent.putExtra(SimpleConfig.EXTRA_RUN_ID, runId)
            intent.putExtra(SimpleConfig.EXTRA_KIND, kind)
            intent.putExtra(SimpleConfig.EXTRA_PHASE, phase)
            intent.putExtra(SimpleConfig.EXTRA_TESTED, tested)
            intent.putExtra(SimpleConfig.EXTRA_TOTAL, total)
            intent.putExtra(SimpleConfig.EXTRA_WORKING, working)
            if (winners.isNotEmpty()) {
                intent.putExtra(SimpleConfig.EXTRA_WINNERS, JsonUtil.toJson(winners))
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e(SimpleConfig.logTag(), "SimpleTest: failed to broadcast result", e)
        }
    }

    private fun finishService() {
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    companion object {
        const val EXTRA_CANCEL = "cancel"

        fun start(context: Context, runId: String) {
            val intent = Intent(context, SimpleTestService::class.java)
            intent.putExtra(SimpleConfig.EXTRA_RUN_ID, runId)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, SimpleTestService::class.java)
            intent.putExtra(EXTRA_CANCEL, true)
            context.stopService(intent)
        }
    }
}
