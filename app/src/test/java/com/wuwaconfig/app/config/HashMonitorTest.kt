package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.ConfigHashInfo
import com.wuwaconfig.app.model.GamePaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class HashMonitorTest {
    private suspend fun makeMonitor(content: String): HashMonitor =
        runBlocking {
            val backend = Mockito.mock(AccessBackend::class.java)
            Mockito.`when`(backend.readFile(Mockito.anyString())).thenReturn(Result.success(content))
            val context = Mockito.mock(Context::class.java)
            HashMonitor(context, backend)
        }

    @Test
    fun `readConfigModifyCounts parses monitored sections in order`() =
        runBlocking {
            val names = GamePaths.MONITORED_FILES
            val content =
                buildString {
                    append("[${names[0]}]\nModifyCount=3\n")
                    append("[${names[1]}]\nModifyCount=0\n")
                    append("[${names[2]}]\nModifyCount=7\n")
                    append("[Unmonitored.ini]\nModifyCount=99\n")
                }
            val result = makeMonitor(content).readConfigModifyCounts()
            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    ConfigHashInfo(names[0], 3),
                    ConfigHashInfo(names[1], 0),
                    ConfigHashInfo(names[2], 7),
                ),
                result.getOrThrow(),
            )
        }

    @Test
    fun `readConfigModifyCounts fails on blank content`() =
        runBlocking {
            assertTrue(makeMonitor("").readConfigModifyCounts().isFailure)
        }

    @Test
    fun `snapshotHashFile returns content and timestamp`() =
        runBlocking {
            val result = makeMonitor("abc").snapshotHashFile()
            assertTrue(result.isSuccess)
            val snap = result.getOrThrow()
            assertEquals("abc", snap.content)
            assertTrue(snap.timestamp > 0)
        }
}
