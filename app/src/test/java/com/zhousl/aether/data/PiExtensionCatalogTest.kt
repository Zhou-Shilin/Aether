package com.zhousl.aether.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiExtensionCatalogTest {
    @Test
    fun parsesAllInstallablePiPackagesIncludingUntypedCards() {
        val catalog = parsePiPackageCatalog(
            """
            <html><body>
              <article data-package-card="true"
                       data-package-name="pi-example"
                       data-package-types="extension skill"
                       data-package-downloads="12345">
                <h3><a data-package-path="/packages/pi-example">pi-example</a></h3>
                <p class="packages-desc">Example extension.</p>
                <div class="packages-meta"><span>author</span><span>12.3K/mo</span></div>
                <div class="packages-links">
                  <a href="https://www.npmjs.com/package/pi-example">npm</a>
                  <a href="https://github.com/example/pi-example">repo</a>
                </div>
                <button data-copy-text="pi install npm:pi-example">Copy</button>
              </article>
              <article data-package-card="true"
                       data-package-name="pi-recent"
                       data-package-types=""
                       data-package-search="pi-recent package">
                <div class="packages-badges"><span data-type="package">package</span></div>
                <a data-package-path="/packages/pi-recent">pi-recent</a>
                <button data-copy-text="pi install npm:pi-recent">Copy</button>
              </article>
              <article data-package-card="true"
                       data-package-name="local-only">
                <button data-copy-text="pi install ./local-only">Copy</button>
              </article>
            </body></html>
            """.trimIndent()
        )

        assertEquals(2, catalog.size)
        val example = catalog.first { it.name == "pi-example" }
        assertEquals("npm:pi-example", example.source)
        assertEquals(12_345L, example.monthlyDownloads)
        assertEquals(listOf("extension", "skill"), example.types)
        assertEquals("https://pi.dev/packages/pi-example", example.packageUrl)
        assertEquals("https://github.com/example/pi-example", example.repositoryUrl)
        assertTrue(catalog.any { it.name == "pi-recent" })
    }

    @Test
    fun parsesPackageDetailsAndConvertsReadmeToMarkdown() {
        val details = parsePiPackageDetails(
            """
            <html><body>
              <h1 class="content-title">@example/pi-tools</h1>
              <p class="content-description">Tools and skills.</p>
              <div class="packages-badges">
                <span data-type="extension">extension</span>
                <span data-type="skill">skill</span>
              </div>
              <div class="packages-detail-links">
                <a href="https://www.npmjs.com/package/@example/pi-tools">npm</a>
                <a href="https://github.com/example/pi-tools">repo</a>
              </div>
              <button data-copy-text="pi install npm:@example/pi-tools">Copy</button>
              <dl class="detail-grid">
                <dt>Version</dt><dd>1.2.3</dd>
                <dt>Author</dt><dd>Example</dd>
                <dt>Dependencies</dt><dd>2 dependencies</dd>
              </dl>
              <pre class="raw-data-panel">{ "extensions": ["./index.ts"] }</pre>
               <div class="packages-readme">
                 <h1>Package README</h1>
                 <p>
                   <a href="https://example.com/project">
                     <img src="/assets/status.svg" alt="Status">
                   </a>
                 </p>
                 <p>Use <strong>carefully</strong>.</p>
                 <ul>
                   <li>First feature</li>
                   <li>Second feature</li>
                 </ul>
                 <hr>
               </div>
            </body></html>
            """.trimIndent(),
            packageUrl = "https://pi.dev/packages/@example/pi-tools",
        )

        assertEquals("npm:@example/pi-tools", details.source)
        assertEquals("1.2.3", details.version)
         assertEquals(listOf("extension", "skill"), details.types)
         assertTrue(details.readmeMarkdown.contains("Package README"))
         assertTrue(details.readmeMarkdown.contains("carefully"))
         assertTrue(details.readmeMarkdown.contains("https://pi.dev/assets/status.svg"))
         assertTrue(details.readmeMarkdown.contains("First feature"))
         assertTrue(details.readmeMarkdown.contains("Second feature"))
         assertTrue(details.readmeMarkdown.contains("*** ** * ** ***"))
     }

    @Test
    fun detectsLikelyInteractiveTuiIncompatibility() {
        assertEquals(
            PiPackageCompatibilityIssue.InteractiveUi,
            detectPiPackageCompatibility(
                name = "pi-todo",
                description = "Renders a live overlay in the interactive TUI.",
                types = listOf("extension"),
            ),
        )
        assertEquals(
            PiPackageCompatibilityIssue.Theme,
            detectPiPackageCompatibility(
                name = "pi-theme",
                description = "A theme.",
                types = listOf("theme"),
            ),
        )
        assertEquals(
            PiPackageCompatibilityIssue.Platform,
            detectPiPackageCompatibility(
                name = "pi-claude-bridge",
                description = "Pi extension that uses Claude Code as a model provider.",
                types = listOf("extension"),
            ),
        )
    }

    @Test
    fun matchesPreinstalledImportedExtensionsInDiscoverCatalog() {
        val catalogEntry = PiExtensionCatalogEntry(
            name = "pi-web-access",
            source = "npm:pi-web-access",
            description = "Web search and extraction.",
            author = "nicobailon",
            monthlyDownloads = 1_000_000L,
            packageUrl = "https://pi.dev/packages/pi-web-access",
            npmUrl = "",
            repositoryUrl = "",
            types = listOf("extension"),
        )
        val preinstalled = InstalledPiExtension(
            id = "import:aether:/root/.aether/extensions/pi-web-access",
            name = "pi-web-access",
            source = "Imported",
            installedPath = "/data/user/0/com.baimoqilin.aether/files/runtimes/alpine/rootfs/root/.aether/extensions/pi-web-access",
            kind = PiExtensionInstallKind.Imported,
        )
        assertTrue(isCatalogEntryInstalled(catalogEntry, listOf(preinstalled)))
    }
}
