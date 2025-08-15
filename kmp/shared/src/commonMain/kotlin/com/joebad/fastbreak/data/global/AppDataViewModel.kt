package com.joebad.fastbreak.data.global

import AuthRepository
import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.data.cache.FastbreakCache
import com.joebad.fastbreak.data.dailyFastbreak.ScheduleResult
import com.joebad.fastbreak.data.dailyFastbreak.StatsResult
import com.joebad.fastbreak.model.dtos.ScheduleResponse
import com.joebad.fastbreak.model.dtos.StatsResponse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class CacheStatus(
    val isLoading: Boolean = false,
    val isCached: Boolean = false,
    val rawJson: String? = null,
    val error: String? = null
)

data class AppDataState(
    val dateCode: String,
    val cacheStatus: CacheStatus = CacheStatus(),
    val isInitialized: Boolean = false,
    val scheduleData: ScheduleResponse? = null,
    val statsData: StatsResponse? = null,
    val isLoading: Boolean = false,
    // TTL-specific states
    val scheduleIsRefreshing: Boolean = false,
    val statsIsRefreshing: Boolean = false,
    val scheduleIsStale: Boolean = false,
    val statsIsStale: Boolean = false,
    val scheduleExpiresAt: Instant? = null,
    val statsExpiresAt: Instant? = null,
    val scheduleIsFromCache: Boolean = false,
    val statsIsFromCache: Boolean = false,
    val scheduleLastFetchedAt: Instant? = null,
    val statsLastFetchedAt: Instant? = null,
    val scheduleCachedAt: Instant? = null,
    val statsCachedAt: Instant? = null
)

sealed class AppDataSideEffect {
    object NavigateToHome : AppDataSideEffect()
}

fun getCurrentDateET(): String {
    val etTimeZone = TimeZone.of("America/New_York")
    val nowET = Clock.System.now().toLocalDateTime(etTimeZone)
    val dateET = nowET.date

    return "${dateET.year}${dateET.monthNumber.toString().padStart(2, '0')}${dateET.dayOfMonth.toString().padStart(2, '0')}"
}

sealed class AppDataAction {
    data class LoadDailyData(val userId: String? = null) : AppDataAction()
    data object LoadSchedule : AppDataAction()
    data class LoadStats(val userId: String) : AppDataAction()
}

class AppDataViewModel(
    private val cache: FastbreakCache,
    authRepository: AuthRepository
) : ContainerHost<AppDataState, AppDataSideEffect> {

    private val dateCode = { getCurrentDateET() }
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override val container: Container<AppDataState, AppDataSideEffect> = viewModelScope.container(AppDataState(dateCode()))

    init {
        loadDailyData(dateCode(), authRepository.getUser()?.userId)
    }

    fun handleAction(action: AppDataAction) {
        when (action) {
            is AppDataAction.LoadDailyData -> loadDailyData(dateCode(), action.userId)
            is AppDataAction.LoadSchedule -> loadSchedule(dateCode())
            is AppDataAction.LoadStats -> loadStats(dateCode(), action.userId)
        }
    }

    private fun loadDailyData(dateString: String, userId: String? = null) = intent {
        try {
            reduce {
                state.copy(
                    isLoading = true,
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = true,
                        error = null
                    )
                )
            }

            // Cache is now initialized once as a class property
            val scheduleUrl = "${BuildKonfig.API_BASE_URL}/day/${dateString}/schedule"
            val statsUrl = if (userId != null) "${BuildKonfig.API_BASE_URL}/day/${dateString}/stats/${userId}" else null

            // Launch both requests in parallel if userId is available, otherwise just schedule
            val scheduleDeferred = viewModelScope.async { 
                try {
                    cache.getSchedule(scheduleUrl)
                } catch (e: Exception) {
                    ScheduleResult.Error("Failed to load schedule: ${e.message}")
                }
            }
            val statsDeferred = if (statsUrl != null) {
                viewModelScope.async { 
                    try {
                        cache.getStats(statsUrl)
                    } catch (e: Exception) {
                        StatsResult.Error("Failed to load stats: ${e.message}")
                    }
                }
            } else null

            // Await both results independently - if one fails, the other can still succeed
            val scheduleResult = scheduleDeferred.await()
            val statsResult = statsDeferred?.await()

            var hasError = false
            var errorMessage = ""
            var isFromCache = false
            var rawJson: String? = null

            when (scheduleResult) {
                is ScheduleResult.Success -> {
                    isFromCache = scheduleResult.isFromCache
                    rawJson = scheduleResult.rawJson
                    reduce {
                        state.copy(
                            scheduleData = scheduleResult.response,
                            scheduleIsRefreshing = scheduleResult.isRefreshing,
                            scheduleIsStale = scheduleResult.isExpired,
                            scheduleExpiresAt = scheduleResult.expiresAt,
                            scheduleIsFromCache = scheduleResult.isFromCache,
                            scheduleLastFetchedAt = if (!scheduleResult.isFromCache) Clock.System.now() else state.scheduleLastFetchedAt,
                            scheduleCachedAt = scheduleResult.cachedAt
                        )
                    }
                }
                is ScheduleResult.Error -> {
                    hasError = true
                    errorMessage = "Schedule: ${scheduleResult.message}"
                }
            }

            statsResult?.let { result ->
                when (result) {
                    is StatsResult.Success -> {
                        reduce {
                            state.copy(
                                statsData = result.response,
                                statsIsRefreshing = result.isRefreshing,
                                statsIsStale = result.isExpired,
                                statsExpiresAt = result.expiresAt,
                                statsIsFromCache = result.isFromCache,
                                statsLastFetchedAt = if (!result.isFromCache) Clock.System.now() else state.statsLastFetchedAt,
                                statsCachedAt = result.cachedAt
                            )
                        }
                    }
                    is StatsResult.Error -> {
                        hasError = true
                        errorMessage += if (errorMessage.isNotEmpty()) "; Stats: ${result.message}" else "Stats: ${result.message}"
                    }
                }
            }

            reduce {
                state.copy(
                    isLoading = false,
                    isInitialized = true,
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = false,
                        isCached = isFromCache,
                        rawJson = rawJson,
                        error = if (hasError) errorMessage else null
                    )
                )
            }

        } catch (e: Exception) {
            reduce {
                state.copy(
                    isLoading = false,
                    isInitialized = true,
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    private fun loadSchedule(dateString: String) = intent {
        try {
            reduce {
                state.copy(
                    isLoading = true,
                    cacheStatus = state.cacheStatus.copy(isLoading = true, error = null)
                )
            }

            val scheduleUrl = "${BuildKonfig.API_BASE_URL}/day/${dateString}/schedule"

            when (val result = cache.getSchedule(scheduleUrl)) {
                is ScheduleResult.Success -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            scheduleData = result.response,
                            scheduleIsRefreshing = result.isRefreshing,
                            scheduleIsStale = result.isExpired,
                            scheduleExpiresAt = result.expiresAt,
                            scheduleIsFromCache = result.isFromCache,
                            scheduleLastFetchedAt = if (!result.isFromCache) Clock.System.now() else state.scheduleLastFetchedAt,
                            cacheStatus = state.cacheStatus.copy(
                                isLoading = false,
                                isCached = result.isFromCache,
                                rawJson = result.rawJson,
                                error = null
                            )
                        )
                    }
                }
                is ScheduleResult.Error -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            cacheStatus = state.cacheStatus.copy(
                                isLoading = false,
                                error = result.message
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            reduce {
                state.copy(
                    isLoading = false,
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    private fun loadStats(dateString: String, userId: String) = intent {
        try {
            reduce {
                state.copy(
                    isLoading = true,
                    cacheStatus = state.cacheStatus.copy(isLoading = true, error = null)
                )
            }

            val statsUrl = "${BuildKonfig.API_BASE_URL}/day/${dateString}/stats/${userId}"

            when (val result = cache.getStats(statsUrl)) {
                is StatsResult.Success -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            statsCachedAt = result.cachedAt,
                            statsData = result.response,
                            statsIsRefreshing = result.isRefreshing,
                            statsIsStale = result.isExpired,
                            statsExpiresAt = result.expiresAt,
                            statsIsFromCache = result.isFromCache,
                            statsLastFetchedAt = if (!result.isFromCache) Clock.System.now() else state.statsLastFetchedAt,
                            cacheStatus = state.cacheStatus.copy(
                                isLoading = false,
                                isCached = result.isFromCache,
                                rawJson = result.rawJson,
                                error = null
                            )
                        )
                    }
                }
                is StatsResult.Error -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            cacheStatus = state.cacheStatus.copy(
                                isLoading = false,
                                error = result.message
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            reduce {
                state.copy(
                    isLoading = false,
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                )
            }
        }
    }

}