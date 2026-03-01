package com.joebad.fastbreak.data.serializers

import com.joebad.fastbreak.data.model.NHLPlayerStatValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for NHLPlayerStatValue that handles backward compatibility.
 * Can deserialize both:
 * - Old format: plain number (e.g., 65 for gamesPlayed, 1.23 for pointsPerGame)
 * - New format: object with value, rank, rankDisplay (e.g., {"value": 65, "rank": 1, "rankDisplay": "1st"})
 */
object NHLPlayerStatValueSerializer : KSerializer<NHLPlayerStatValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NHLPlayerStatValue")

    override fun serialize(encoder: Encoder, value: NHLPlayerStatValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer only works with JSON")

        val jsonObject = buildMap {
            value.value?.let { put("value", JsonPrimitive(it)) }
            value.rank?.let { put("rank", JsonPrimitive(it)) }
            value.rankDisplay?.let { put("rankDisplay", JsonPrimitive(it)) }
        }
        jsonEncoder.encodeJsonElement(JsonObject(jsonObject))
    }

    override fun deserialize(decoder: Decoder): NHLPlayerStatValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer only works with JSON")

        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonPrimitive -> {
                // Old format: plain number
                val value = element.doubleOrNull ?: element.intOrNull?.toDouble()
                NHLPlayerStatValue(value = value, rank = null, rankDisplay = null)
            }
            is JsonObject -> {
                // New format: object with value, rank, rankDisplay
                val value = (element["value"] as? JsonPrimitive)?.doubleOrNull
                    ?: (element["value"] as? JsonPrimitive)?.intOrNull?.toDouble()
                val rank = (element["rank"] as? JsonPrimitive)?.intOrNull
                val rankDisplay = (element["rankDisplay"] as? JsonPrimitive)?.content
                NHLPlayerStatValue(value = value, rank = rank, rankDisplay = rankDisplay)
            }
            else -> NHLPlayerStatValue()
        }
    }
}
