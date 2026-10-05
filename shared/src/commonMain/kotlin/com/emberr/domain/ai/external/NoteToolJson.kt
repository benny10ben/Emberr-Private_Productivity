package com.emberr.domain.ai.external

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonObject.toNoteToolArguments(): Map<String, String> = mapValues { (_, value) ->
    (value as? JsonPrimitive)?.content.orEmpty()
}
