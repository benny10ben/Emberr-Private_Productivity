package com.emberr.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CalloutBlockTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val calloutWithATypeFromANewerVersion =
        """{"type":"callout","id":"callout-1","text":"Read this","calloutTypeName":"IMPORTANT","updatedAt":100}"""

    @Test
    fun aCalloutTypeFromANewerVersionLoadsAndShowsAsANote() {
        val block = json.decodeFromString(NoteBlockSerializer, calloutWithATypeFromANewerVersion)

        val callout = assertIs<CalloutBlock>(block)
        assertEquals(CalloutType.NOTE, callout.calloutType)
        assertEquals("Read this", callout.text)
    }

    @Test
    fun aCalloutTypeFromANewerVersionIsSavedBackUnchanged() {
        val callout = json.decodeFromString(NoteBlockSerializer, calloutWithATypeFromANewerVersion) as CalloutBlock

        val editedAndSaved = json.encodeToString(NoteBlockSerializer, callout.copy(text = "Edited", updatedAt = 200L))

        assertEquals(
            "IMPORTANT",
            json.parseToJsonElement(editedAndSaved).jsonObject.getValue("calloutTypeName").jsonPrimitive.content
        )
    }

    @Test
    fun everyKnownCalloutTypeIsReadBackFromItsSavedName() {
        CalloutType.entries.forEach { calloutType ->
            assertEquals(calloutType, CalloutBlock(id = "callout-1", calloutTypeName = calloutType.name).calloutType)
        }
    }
}
