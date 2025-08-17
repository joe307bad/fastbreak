package com.joebad.fastbreak.model.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(
    val fastbreakCard: List<EmptyFastbreakCardItem>
)