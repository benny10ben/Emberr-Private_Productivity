package com.emberr.domain.ai

private val BUNDLED_PROCESSOR_NAMES = setOf("amd64", "x86_64", "x86-64")

private fun nativeEngineDirectoryFor(operatingSystemName: String): String? = when {
    operatingSystemName.contains("linux") -> "linux"
    operatingSystemName.contains("win") -> "windows"
    operatingSystemName.contains("mac") -> "macos"
    else -> null
}

private fun isNativeEngineBundledIn(directoryName: String): Boolean =
    LocalAiSupport::class.java.classLoader
        .getResource("native/$directoryName/native-libs.txt") != null

actual fun detectLocalAiSupport(): LocalAiSupport {
    val operatingSystemName = System.getProperty("os.name").orEmpty()
    val processorName = System.getProperty("os.arch").orEmpty().lowercase()
    val nativeEngineDirectory = nativeEngineDirectoryFor(operatingSystemName.lowercase())

    return when {
        nativeEngineDirectory == null || !isNativeEngineBundledIn(nativeEngineDirectory) ->
            LocalAiSupport.Unsupported(
                "This build of Emberr does not bundle the on-device AI engine for $operatingSystemName. Connect a cloud provider with your own API key instead."
            )

        processorName !in BUNDLED_PROCESSOR_NAMES -> LocalAiSupport.Unsupported(
            "The on-device AI engine needs a 64-bit Intel or AMD processor. This computer reports \"$processorName\", which has no bundled engine. Connect a cloud provider with your own API key instead."
        )

        else -> LocalAiSupport.Supported
    }
}
