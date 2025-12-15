@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Metadata about the locally cached registry.
 * Used to track when the registry was last downloaded and determine if a refresh is needed.
 */
@Serializable
data class RegistryMetadata(
    /**
     * When the registry was last downloaded from the API/mock
     * Used to implement the 12-hour refresh policy
     */
    val lastDownloadTime: Instant,

    /**
     * The version of the registry that was downloaded
     * Used to track registry schema changes
     */
    val registryVersion: String
)
