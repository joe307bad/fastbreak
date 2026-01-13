package com.joebad.fastbreak.data.serializers

import com.joebad.fastbreak.data.model.Tag
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for List<Tag>? that handles backward compatibility.
 * Can deserialize both:
 * - Old format: ["regular season", "team"]
 * - New format: [{"label": "team", "layout": "left", "color": "#4CAF50"}, ...]
 */
object TagListSerializer : KSerializer<List<Tag>?> {
    private val tagListSerializer = ListSerializer(Tag.serializer())

    override val descriptor: SerialDescriptor = tagListSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<Tag>?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            tagListSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<Tag>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        // Handle null case
        if (element is JsonPrimitive && element.isString && element.content == "null") {
            return null
        }

        if (element !is JsonArray) {
            return null
        }

        // Check if it's the old string format or new object format
        val firstElement = element.firstOrNull() ?: return emptyList()

        return if (firstElement is JsonPrimitive && firstElement.isString) {
            // Old format: array of strings
            // Convert to Tag objects with default layout and colors
            element.mapNotNull { jsonElement ->
                val label = (jsonElement as? JsonPrimitive)?.content ?: return@mapNotNull null
                convertLegacyTagToTag(label)
            }
        } else {
            // New format: array of Tag objects
            jsonDecoder.json.decodeFromJsonElement(tagListSerializer, element)
        }
    }

    /**
     * Convert legacy string tag to Tag object with appropriate layout and color
     */
    private fun convertLegacyTagToTag(label: String): Tag {
        return when (label.lowercase()) {
            "player" -> Tag(
                label = "player",
                layout = "left",
                color = "#2196F3" // blue
            )
            "team" -> Tag(
                label = "team",
                layout = "left",
                color = "#4CAF50" // green
            )
            "regular season" -> Tag(
                label = "regular season",
                layout = "right",
                color = "#9C27B0" // purple
            )
            "post season" -> Tag(
                label = "post season",
                layout = "right",
                color = "#FF9800" // orange
            )
            else -> Tag(
                label = label,
                layout = "left",
                color = "#9E9E9E" // gray for unknown tags
            )
        }
    }
}
