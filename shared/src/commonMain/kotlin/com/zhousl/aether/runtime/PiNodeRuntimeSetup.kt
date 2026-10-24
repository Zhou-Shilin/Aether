package com.zhousl.aether.runtime

/** The same executable health check is used by both Alpine hosts. */
object PiNodeRuntimeSetup {
    const val minimumVersion = "22.19.0"

    val verificationCommand: String =
        "node -e 'const v = process.versions.node.split(\".\").map(Number); " +
            "console.log(process.version); " +
            "if (v[0] < 22 || (v[0] === 22 && v[1] < 19)) " +
            "{ console.error(\"Pi requires Node >= $minimumVersion\"); process.exit(1); }' " +
            "&& npm --version"

    fun installCommand(noChown: Boolean): String {
        val flags = if (noChown) "--no-cache --no-chown" else "--no-cache"
        return """
            apk add $flags 'nodejs>=$minimumVersion' npm || exit ${'$'}?
            if $verificationCommand; then exit 0; fi
            # A restored or interrupted installation can have a valid APK database
            # but missing executables/libraries. Repair files as well as metadata.
            apk fix --no-cache --reinstall --depends nodejs npm || exit ${'$'}?
            $verificationCommand
        """.trimIndent()
    }
}
