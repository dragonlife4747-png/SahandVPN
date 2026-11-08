package com.v2ray.ang.simple

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.io.File

/**
 * Constants and state persistence for the simplified one-screen client.
 *
 * State lives in a plain JSON file (not MMKV) because the UI process and the ":tasks" test
 * process must see each other's writes immediately; a file read is always coherent.
 */
object SimpleConfig {

    /** Built-in subscription used the first time the app starts. */
    const val SUB_URL = "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/refs/heads/main/all_configs.txt"
    const val SUB_ID = "sahand-auto"
    const val SUB_NAME = "Sahand Auto"

    /** How many working profiles are enough before the connect button unlocks. */
    const val TARGET_WORKING = 5

    /** Cheap TCP pre-check timeout before a full core probe. */
    const val TCP_TIMEOUT_MS = 900

    /** Parallel TCP reachability scans over unique endpoints. */
    const val TCP_CONCURRENCY = 8

    /** Endpoints scanned per round before switching to real core probes. */
    const val SCAN_CHUNK = 120

    /** Stop the fast stage once this many candidates answered. */
    const val FAST_RESULTS_TARGET = 24

    /** How many of the fastest candidates get the TLS + throughput check. */
    const val FINALISTS = 6

    /** Fast answers needed before moving to the TLS/throughput stage. */
    const val MIN_FINALISTS = 3

    /** Cap the fast stage per scan round so finalists are tested early. */
    const val MAX_FAST_BATCHES_PER_ROUND = 4

    /** Upper bound of endpoints scanned per run, so a test always terminates. */
    const val MAX_ENDPOINTS_PER_RUN = 800

    /** Hard per-probe deadline so a wedged native call cannot stall the run. */
    const val PROBE_TIMEOUT_MS = 10_000L

    /**
     * Probe target. Plain HTTP on purpose: no TLS handshake, so a healthy proxy answers in
     * well under a second and dead ones fail fast instead of burning the TLS timeout.
     */
    const val PROBE_URL = "http://www.gstatic.com/generate_204"

    // Broadcast plumbing between SimpleTestService (":tasks" process) and SimpleActivity.
    const val ACTION_TEST = "com.sahand.vpn.simple.action.TEST"
    const val EXTRA_RUN_ID = "run_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_TESTED = "tested"
    const val EXTRA_TOTAL = "total"
    const val EXTRA_WORKING = "working"
    const val EXTRA_WINNERS = "winners"

    const val KIND_PROGRESS = 1
    const val KIND_FINISH = 2

    // Test phases, surfaced in the UI as progress text.
    const val PHASE_SCANNING = 1
    const val PHASE_PROBING = 2
    const val PHASE_DONE = 3

    data class Winner(val guid: String = "", val delay: Long = 0L)

    data class TestState(
        val winners: List<Winner> = emptyList(),
        val backup: List<Winner> = emptyList(),
        val tested: Int = 0,
        val total: Int = 0,
        val phase: String = "idle",
        val runId: String = "",
    )

    private const val STATE_FILE = "simple_state.json"

    private fun stateFile(context: Context) = File(context.filesDir, STATE_FILE)

    fun read(context: Context): TestState {
        return try {
            val file = stateFile(context)
            if (!file.exists()) return TestState()
            JsonUtil.fromJsonSafe(file.readText(), TestState::class.java) ?: TestState()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SimpleConfig: failed to read state", e)
            TestState()
        }
    }

    fun write(context: Context, state: TestState) {
        try {
            val file = stateFile(context)
            val temp = File(context.filesDir, "$STATE_FILE.tmp")
            temp.writeText(JsonUtil.toJson(state))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SimpleConfig: failed to write state", e)
        }
    }

    /** Best profiles found by the last test, fastest first. */
    fun loadWinners(context: Context): List<Winner> =
        read(context).winners.filter { it.guid.isNotBlank() && it.delay > 0 }.sortedBy { it.delay }

    fun saveWinners(context: Context, winners: List<Winner>) {
        val trimmed = winners.filter { it.guid.isNotBlank() && it.delay > 0 }
            .sortedBy { it.delay }
            .take(TARGET_WORKING)
        write(context, read(context).copy(winners = trimmed))
    }

    /** Drops every result from the previous session so the next search starts from scratch. */
    fun clearSession(context: Context) = locked(context) {
        write(context, TestState())
    }

    fun clearWinners(context: Context) {
        write(context, read(context).copy(winners = emptyList()))
    }

    /**
     * Starts a fresh search but keeps the previous points as a backup, so abandoning or killing
     * the search does not leave the user with nothing to connect to.
     */
    private fun <T> locked(context: Context, action: () -> T): T = synchronized(this) {
        java.io.RandomAccessFile(File(context.filesDir, "simple_state.lock"), "rw").use { file ->
            file.channel.use { channel -> channel.lock().use { action() } }
        }
    }

    fun beginSearch(context: Context): String = locked(context) {
        val state = read(context)
        val id = java.util.UUID.randomUUID().toString()
        write(context, state.copy(backup = state.winners.ifEmpty { state.backup },
            winners = emptyList(), phase = "running", runId = id))
        id
    }

    fun updateSearch(context: Context, runId: String, winners: List<Winner>, phase: String = "running"): Boolean = locked(context) {
        val state = read(context)
        if (state.runId != runId || state.phase != "running") return@locked false
        val result = if (phase == "running") winners else winners.ifEmpty { state.backup }
        write(context, state.copy(winners = result, phase = phase))
        true
    }

    fun cancelSearch(context: Context) = locked(context) {
        val state = read(context)
        write(context, state.copy(winners = state.winners.ifEmpty { state.backup },
            phase = "cancelled", runId = ""))
    }

    /** Restores the backup when a finished search produced nothing. */
    fun restoreIfEmpty(context: Context): List<Winner> {
        val state = read(context)
        if (state.winners.isNotEmpty() || state.backup.isEmpty()) return state.winners
        write(context, state.copy(winners = state.backup))
        return state.backup
    }

    /** Drops winners whose profile disappeared after a subscription refresh. */
    fun pruneWinners(context: Context, aliveGuids: Set<String>): List<Winner> {
        val current = loadWinners(context)
        val alive = current.filter { it.guid in aliveGuids }
        if (alive.size != current.size) saveWinners(context, alive)
        return alive
    }

    fun lastTested(context: Context): Int = read(context).tested

    fun saveLastTest(context: Context, tested: Int, total: Int) {
        write(context, read(context).copy(tested = tested, total = total))
    }

    /** "running" / "done" / "empty" / "idle", written by the test service and polled by the UI. */
    fun setTestState(context: Context, phase: String) {
        write(context, read(context).copy(phase = phase))
    }

    fun getTestState(context: Context): String = read(context).phase

    fun logTag(): String = AppConfig.TAG
}
