package sk.ziacik.androidstreamplayer.torrent

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerProcessTest {
    @Test
    fun commandBindsLoopbackAndUsesPrivateDataDirectory() {
        val command = buildTorrServerCommand(
            binaryPath = "/data/app/lib/arm64/libtorrserver.so",
            dataPath = "/data/user/0/app/no_backup/torrserver",
        )

        assertEquals(
            listOf(
                "/data/app/lib/arm64/libtorrserver.so",
                "--ip",
                "127.0.0.1",
                "--port",
                "18090",
                "--path",
                "/data/user/0/app/no_backup/torrserver",
            ),
            command,
        )
    }

    @Test
    fun ensureStartedDoesNotLaunchSecondLiveProcess() = runTest {
        val child = FakeChildProcess()
        val launcher = FakeLauncher(child)
        val client = FakeControlClient()
        val process = TorrServerProcess(
            binaryPath = "/tmp/libtorrserver.so",
            dataPath = "/tmp/torrserver",
            launcher = launcher,
            client = client,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            validateBinary = false,
        )

        process.ensureStarted()
        process.ensureStarted()

        assertEquals(1, launcher.launchCount)
        assertEquals(1, client.awaitReadyCount)
        assertEquals(1, client.assertRamCacheCount)
        assertEquals("madvdontneed=1", launcher.lastEnvironment["GODEBUG"])
    }

    @Test
    fun startupFailureTerminatesChild() = runTest {
        val child = FakeChildProcess()
        val launcher = FakeLauncher(child)
        val client = FakeControlClient(failReadiness = true)
        val process = TorrServerProcess(
            binaryPath = "/tmp/libtorrserver.so",
            dataPath = "/tmp/torrserver",
            launcher = launcher,
            client = client,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            validateBinary = false,
        )

        runCatching { process.ensureStarted() }

        assertTrue(child.destroyCalled || child.destroyForciblyCalled)
    }

    private class FakeControlClient(
        private val failReadiness: Boolean = false,
    ) : TorrServerControlClient {
        var awaitReadyCount = 0
        var assertRamCacheCount = 0

        override suspend fun awaitReady(timeoutMs: Long) {
            awaitReadyCount++
            if (failReadiness) error("not ready")
        }

        override suspend fun assertRamCache() {
            assertRamCacheCount++
        }

        override suspend fun shutdown() = Unit
    }

    private class FakeLauncher(
        private val child: FakeChildProcess,
    ) : TorrServerProcessLauncher {
        var launchCount = 0
        var lastEnvironment: Map<String, String> = emptyMap()

        override fun launch(
            command: List<String>,
            environment: Map<String, String>,
        ): TorrServerChildProcess {
            launchCount++
            lastEnvironment = environment
            return child
        }
    }

    private class FakeChildProcess : TorrServerChildProcess {
        override val inputStream: InputStream = ByteArrayInputStream(ByteArray(0))
        override var isAlive: Boolean = true
        var destroyCalled = false
        var destroyForciblyCalled = false

        override fun waitFor(timeoutMs: Long): Boolean = !isAlive

        override fun destroy() {
            destroyCalled = true
            isAlive = false
        }

        override fun destroyForcibly() {
            destroyForciblyCalled = true
            isAlive = false
        }
    }
}
