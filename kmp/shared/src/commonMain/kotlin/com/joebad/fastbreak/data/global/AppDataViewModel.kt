package com.joebad.fastbreak.data.global

import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.data.cache.CachedHttpClient
import com.joebad.fastbreak.data.cache.KotbaseApiCache
import com.joebad.fastbreak.data.dailyFastbreak.DailyFastbreakResult
import com.joebad.fastbreak.data.dailyFastbreak.getDailyFastbreakCached
import com.joebad.fastbreak.ui.screens.CacheStatus
import com.joebad.fastbreak.ui.screens.getCurrentFastbreakDate
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class AppDataState(
    val cacheStatus: CacheStatus = CacheStatus(),
    val isInitialized: Boolean = false
)

sealed class AppDataSideEffect {
    object NavigateToHome : AppDataSideEffect()
}

sealed class AppDataAction {
    object InitializeAppWithData : AppDataAction()
}

class AppDataViewModel(
    private val database: Database,
    private val httpClient: HttpClient
) : ContainerHost<AppDataState, AppDataSideEffect> {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override val container: Container<AppDataState, AppDataSideEffect> = viewModelScope.container(AppDataState())

    init {
        initializeAppWithData()
    }

    fun handleAction(action: AppDataAction) {
        when (action) {
            AppDataAction.InitializeAppWithData -> initializeAppWithData()
        }
    }

    private fun initializeAppWithData() = intent {
        try {
            reduce {
                state.copy(
                    cacheStatus = state.cacheStatus.copy(
                        isLoading = true,
                        isCached = false,
                        error = null
                    )
                )
            }

            val dateString = getCurrentFastbreakDate()
            val apiCache = KotbaseApiCache(database)
            val cachedHttpClient = CachedHttpClient(httpClient, apiCache)
            val apiUrl = "${BuildKonfig.API_BASE_URL}/api/day/${dateString}"

            when (val result = getDailyFastbreakCached(cachedHttpClient, apiUrl, null)) {
                is DailyFastbreakResult.Success -> {
                    reduce {
                        state.copy(
                            cacheStatus = CacheStatus(
                                isLoading = false,
                                isCached = result.isFromCache,
                                rawJson = result.rawJson,
                                error = null
                            ),
                            isInitialized = true
                        )
                    }
//                    postSideEffect(AppDataSideEffect.NavigateToHome)
                }

                is DailyFastbreakResult.Error -> {
                    reduce {
                        state.copy(
                            cacheStatus = CacheStatus(
                                isLoading = false,
                                isCached = false,
                                rawJson = null,
                                error = result.message
                            ),
                            isInitialized = true
                        )
                    }
//                    postSideEffect(AppDataSideEffect.NavigateToHome)
                }
            }
        } catch (e: Exception) {
            reduce {
                state.copy(
                    cacheStatus = CacheStatus(
                        isLoading = false,
                        isCached = false,
                        rawJson = null,
                        error = "Error: ${e.message}"
                    ),
                    isInitialized = true
                )
            }
//            postSideEffect(AppDataSideEffect.NavigateToHome)
        }
    }
}