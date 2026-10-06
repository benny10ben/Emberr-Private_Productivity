package com.emberr.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

// One rule from now on: when reading or writing a single block, use NoteBlockSerializer, not NoteBlock.serializer().
// The old one fails on unknown types.
object NoteBlockSerializer : KSerializer<NoteBlock> {

    private const val BLOCK_TYPE_FIELD = "type"

    private val generatedSerializer = NoteBlock.serializer()

    private val blockTypesThisVersionKnows: Set<String> by lazy {
        generatedSerializer.descriptor.getElementDescriptor(1).elementNames.toSet() -
                UnknownBlockSerializer.descriptor.serialName
    }

    override val descriptor: SerialDescriptor =
        SerialDescriptor("com.emberr.domain.model.NoteBlockKeepingUnknownTypes", generatedSerializer.descriptor)

    override fun deserialize(decoder: Decoder): NoteBlock {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Note blocks can only be read from JSON")
        val blockJson = jsonDecoder.decodeJsonElement().jsonObject
        val blockType = (blockJson[BLOCK_TYPE_FIELD] as? JsonPrimitive)?.contentOrNull
        return if (blockType != null && blockType in blockTypesThisVersionKnows) {
            jsonDecoder.json.decodeFromJsonElement(generatedSerializer, blockJson)
        } else {
            UnknownBlockSerializer.unknownBlockFrom(blockJson)
        }
    }

    override fun serialize(encoder: Encoder, value: NoteBlock) {
        if (value is UnknownBlock) {
            UnknownBlockSerializer.serialize(encoder, value)
        } else {
            encoder.encodeSerializableValue(generatedSerializer, value)
        }
    }
}
