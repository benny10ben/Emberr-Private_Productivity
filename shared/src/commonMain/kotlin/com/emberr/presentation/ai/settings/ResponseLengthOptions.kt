package com.emberr.presentation.ai.settings

internal data class ResponseLengthOption(val label: String, val subtitle: String, val tokens: Int)

internal val responseLengthOptions = listOf(
    ResponseLengthOption("Short", "Quick, concise answers.", 512),
    ResponseLengthOption("Balanced", "A good mix of detail and brevity.", 1024),
    ResponseLengthOption("Long", "More thorough, detailed answers.", 2048),
    ResponseLengthOption("Max", "The longest answers this device/provider allows.", 4096)
)
