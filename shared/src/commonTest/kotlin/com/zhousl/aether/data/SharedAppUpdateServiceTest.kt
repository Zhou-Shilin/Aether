package com.zhousl.aether.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SharedAppUpdateServiceTest {
    @Test
    fun parsesPublishedUpdateAndProductUrl() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"resultCount":1,"results":[{"version":"1.4.0","trackViewUrl":"https://apps.apple.com/app/id1"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val status = SharedAppUpdateService(engine).check("1.3.9")
        assertTrue(status.isPublished)
        assertTrue(status.isUpdateAvailable)
        assertEquals("https://apps.apple.com/app/id1", status.storeUrl)
    }

    @Test
    fun handlesAnAppThatIsNotPublishedYet() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"resultCount":0,"results":[]}""",
                status = HttpStatusCode.OK,
            )
        }
        val status = SharedAppUpdateService(engine).check("1.0")
        assertFalse(status.isPublished)
        assertFalse(status.isUpdateAvailable)
    }

    @Test
    fun comparesNumericVersionSegments() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("2.0", "2.0.0"))
    }
}
