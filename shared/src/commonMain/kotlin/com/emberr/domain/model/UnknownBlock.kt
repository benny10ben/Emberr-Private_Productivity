package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

@Immutable
@Serializable(with = UnknownBlockSerializer::class)
data class UnknownBlock(
    val storedJson: JsonObject,
    override val id: String,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

object UnknownBlockSerializer : KSerializer<UnknownBlock> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.emberr.domain.model.UnknownBlock")

    override fun deserialize(decoder: Decoder): UnknownBlock {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Unknown blocks can only be read from JSON")
        return unknownBlockFrom(jsonDecoder.decodeJsonElement().jsonObject)
    }

    override fun serialize(encoder: Encoder, value: UnknownBlock) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Unknown blocks can only be written as JSON")
        jsonEncoder.encodeJsonElement(storedJsonWithCurrentSharedFields(value))
    }

    fun unknownBlockFrom(storedJson: JsonObject): UnknownBlock {
        fun primitive(fieldName: String) = storedJson[fieldName] as? JsonPrimitive
        val id = primitive("id")?.contentOrNull
            ?: throw SerializationException("A block without an id cannot be kept")
        return UnknownBlock(
            storedJson = storedJson,
            id = id,
            indentationLevel = primitive("indentationLevel")?.intOrNull ?: 0,
            isBold = primitive("isBold")?.booleanOrNull ?: false,
            isItalic = primitive("isItalic")?.booleanOrNull ?: false,
            isStrikeThrough = primitive("isStrikeThrough")?.booleanOrNull ?: false,
            isUnderlined = primitive("isUnderlined")?.booleanOrNull ?: false,
            isHighlighted = primitive("isHighlighted")?.booleanOrNull ?: false,
            isDeleted = primitive("isDeleted")?.booleanOrNull ?: false,
            isPinned = primitive("isPinned")?.booleanOrNull ?: false,
            updatedAt = primitive("updatedAt")?.longOrNull ?: 0L
        )
    }

    private fun storedJsonWithCurrentSharedFields(block: UnknownBlock): JsonObject {
        val defaultSharedFields = sharedFieldsOf(UnknownBlock(storedJson = block.storedJson, id = block.id))
        val sharedFieldsToWrite = sharedFieldsOf(block).filter { (fieldName, value) ->
            fieldName in block.storedJson || value != defaultSharedFields[fieldName]
        }
        return JsonObject(block.storedJson + sharedFieldsToWrite)
    }

    private fun sharedFieldsOf(block: UnknownBlock): Map<String, JsonPrimitive> = mapOf(
        "id" to JsonPrimitive(block.id),
        "indentationLevel" to JsonPrimitive(block.indentationLevel),
        "isBold" to JsonPrimitive(block.isBold),
        "isItalic" to JsonPrimitive(block.isItalic),
        "isStrikeThrough" to JsonPrimitive(block.isStrikeThrough),
        "isUnderlined" to JsonPrimitive(block.isUnderlined),
        "isHighlighted" to JsonPrimitive(block.isHighlighted),
        "isDeleted" to JsonPrimitive(block.isDeleted),
        "isPinned" to JsonPrimitive(block.isPinned),
        "updatedAt" to JsonPrimitive(block.updatedAt)
    )
}
