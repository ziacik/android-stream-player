package sk.ziacik.androidstreamplayer.torrent

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun buildTorrServerCommand(
    binaryPath: String,
    dataPath: String,
): List<String> = listOf(
    binaryPath,
    "--ip",
    "127.0.0.1",
    "--port",
    "18090",
    "--path",
    dataPath,
)

internal interface TorrServerChildProcess {
    val inputStream: InputStream
    val isAlive: Boolean
    fun waitFor(timeoutMs: Long): Boolean
    fun destroy()
    fun destroyForcibly()
}

internal fun interface TorrServerProcessLauncher {
    fun launch(
        command: List<String>,
        environment: Map<String, String>,
    ): TorrServerChildProcess
}

private class JavaTorrServerChildProcess(
    private val process: Process,
) : TorrServerChildProcess {
    override val inputStream: InputStream
        get() = process.inputStream

    override val isAlive: Boolean
        get() = process.isAlive

    override fun waitFor(timeoutMs: Long): Boolean =
        process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }
}

internal object JavaTorrServerProcessLauncher : TorrServerProcessLauncher {
    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
    ): TorrServerChildProcess {
        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
        builder.environment().putAll(environment)
        return JavaTorrServerChildProcess(builder.start())
    }
}

internal class TorrServerProcess(
    private val binaryPath: String,
    private val dataPath: String,
    private val launcher: TorrServerProcessLauncher = JavaTorrServerProcessLauncher,
    private val client: TorrServerControlClient,
    private val scope: CoroutineScope,
    private val validateBinary: Boolean = true,
    private val logLine: (String) -> Unit = { line -> Log.i(LOG_TAG, line) },
) {
    private val lifecycleMutex = Mutex()
    private var child: TorrServerChildProcess? = null
    private var logJob: Job? = null

    suspend fun ensureStarted() = lifecycleMutex.withLock {
        if (child?.isAlive == true) return@withLock

        val started = withContext(Dispatchers.IO) {
            if (validateBinary) {
                val binary = File(binaryPath)
                if (!binary.isFile) {
                    throw IOException("TorrServer binary not found: $binaryPath")
                }
            }

            val dataDir = File(dataPath)
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                throw IOException("Unable to create TorrServer data directory: $dataPath")
            }

            launcher.launch(
                command = buildTorrServerCommand(binaryPath, dataPath),
                environment = mapOf("GODEBUG" to "madvdontneed=1"),
            )
        }
        child = started
        logJob = scope.launch(Dispatchers.IO) {
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach(logLine)
                }
            }
        }

        try {
            client.awaitReady()
            client.configureStreamingSettings()
            client.assertRamCache()
        } catch (error: Throwable) {
            terminate(started)
            child = null
            logJob?.cancel()
            logJob = null
            if (error is CancellationException) throw error
            throw error
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        val active = child
        child = null

        if (active != null) {
            runCatching { client.shutdown() }
            terminate(active)
        }

        logJob?.cancelAndJoin()
        logJob = null
    }

    private suspend fun terminate(process: TorrServerChildProcess) = withContext(Dispatchers.IO) {
        if (!process.isAlive) return@withContext

        if (process.waitFor(GRACEFUL_WAIT_MS)) return@withContext
        process.destroy()
        if (process.waitFor(DESTROY_WAIT_MS)) return@withContext
        process.destroyForcibly()
        process.waitFor(FORCE_WAIT_MS)
    }

    private companion object {
        const val LOG_TAG = "TorrServer"
        const val GRACEFUL_WAIT_MS = 1_500L
        const val DESTROY_WAIT_MS = 500L
        const val FORCE_WAIT_MS = 500L
    }
}
