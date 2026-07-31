package com.zhousl.aether.data.pi

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SharedMcpManagerTest {
    @Test
    fun serverConfigurationRoundTrips() {
        val servers = listOf(
            SharedMcpServerConfig(
                id = "stdio-id",
                name = "Local Files",
                actionLabel = "Files",
                transport = SharedMcpTransport.Stdio,
                command = "/usr/bin/node",
                arguments = listOf("server.mjs", "--stdio"),
                workingDirectory = "/root/workspace",
                environment = mapOf("TOKEN" to "secret", "LOG_LEVEL" to "debug"),
                runtimeEnvironment = "alpine",
                connectTimeoutMillis = 12_000L,
                requestTimeoutMillis = 45_000L,
                enabled = false,
            ),
            SharedMcpServerConfig(
                id = "http-id",
                name = "Remote Search",
                transport = SharedMcpTransport.Http,
                url = "https://example.com/mcp",
                headers = mapOf("Authorization" to "Bearer token"),
            ),
        )

        assertEquals(servers, parseSharedMcpServers(serializeSharedMcpServers(servers)))
    }

    @Test
    fun exposedToolNameUsesAndroidServerIdAndRemoteName() {
        assertEquals(
            "mcp__server-id__search.docs",
            sharedMcpToolName("server-id", "search.docs"),
        )
    }

    @Test
    fun readsAndroidNestedServerConfiguration() {
        val parsed = parseSharedMcpServers(
            """[{"id":"android-id","displayName":"Docs","actionLabel":"Search Docs","transport":{"type":"streamable_http","url":"https://example.com/mcp","headers":[{"key":"Authorization","value":"Bearer token"}]},"isEnabled":false}]"""
        )

        assertEquals(
            SharedMcpServerConfig(
                id = "android-id",
                name = "Docs",
                actionLabel = "Search Docs",
                transport = SharedMcpTransport.Http,
                url = "https://example.com/mcp",
                headers = mapOf("Authorization" to "Bearer token"),
                enabled = false,
            ),
            parsed.single(),
        )
    }

    @Test
    fun nodeClientMatchesStreamableHttpSessionLifecycleAndInspectionMetadata() {
        assertContains(SharedMcpNodeClient, "protocolVersion:'2025-11-25'")
        assertContains(SharedMcpNodeClient, "'mcp-protocol-version':protocolVersion")
        assertContains(SharedMcpNodeClient, "method:'DELETE'")
        assertContains(SharedMcpNodeClient, "'mcp-session-id':session")
        assertContains(SharedMcpNodeClient, "protocol_version: initialization.protocolVersion")
        assertContains(SharedMcpNodeClient, "server_info:")
        assertContains(SharedMcpNodeClient, "request.includeMetadata")
    }
}
