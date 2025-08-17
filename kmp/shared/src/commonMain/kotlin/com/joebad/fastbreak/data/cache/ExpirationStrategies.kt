package com.joebad.fastbreak.data.cache

import kotlinx.datetime.*

class ScheduleExpirationStrategy : CacheExpirationStrategy {
    override fun calculateExpirationTime(currentTime: Instant): Instant {
        // Next midnight ET
        val etZone = TimeZone.of("America/New_York")
        val currentET = currentTime.toLocalDateTime(etZone)
        val nextMidnight = currentET.date.plus(1, DateTimeUnit.DAY).atTime(0, 0)
        return nextMidnight.toInstant(etZone)
    }
}

class StatsExpirationStrategy : CacheExpirationStrategy {
    override fun calculateExpirationTime(currentTime: Instant): Instant {
        // Next 5:30 AM ET
        val etZone = TimeZone.of("America/New_York")
        val currentET = currentTime.toLocalDateTime(etZone)
        
        val today530AM = currentET.date.atTime(5, 30)
        val tomorrow530AM = currentET.date.plus(1, DateTimeUnit.DAY).atTime(5, 30)
        
        return if (currentET.time < LocalTime(5, 30)) {
            today530AM.toInstant(etZone)
        } else {
            tomorrow530AM.toInstant(etZone)
        }
    }
}