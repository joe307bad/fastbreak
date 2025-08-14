package com.joebad.fastbreak.data.dailyFastbreak

import AuthRepository
import com.joebad.fastbreak.BuildKonfig.API_BASE_URL
import com.joebad.fastbreak.data.cache.CachedHttpClient
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import kotbase.DataSource
import kotbase.Database
import kotbase.Meta
import kotbase.MutableDocument
import kotbase.Ordering
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json


class FastbreakStateRepository(
    db: Database,
    private val authRepository: AuthRepository?,
    private val cachedHttpClient: CachedHttpClient? = null
) {

    private val lastFetchedCollection =
        db.getCollection("LastFetchedCollection") ?: db.createCollection("LastFetchedCollection")
    private val dailyStateCollection = db.getCollection("FastBreakDailyStateCollection")
        ?: db.createCollection("FastBreakDailyStateCollection")
    private val BASE_URL = API_BASE_URL
    private val LOCK_CARD = "${BASE_URL}/lock"

    companion object {
        private const val LAST_FETCHED_KEY = "lastFetchedDate"
    }

    suspend fun getDailyFastbreakState(date: String, forceUpdate: Boolean = false): DailyFastbreak? {
        val now = Clock.System.now()
        val lastFetchedTime = getLastFetchedTime()

        // If forceUpdate is true, always fetch fresh data
        if (forceUpdate) {
            return fetchAndStoreState(date)
        }

        // Get current time and 4am ET today in UTC
        val nowUtc = now.epochSeconds
        val todayAt4amET = getTodayAt4amETInUtc()

        return if (lastFetchedTime == null) {
            // No previous fetch, always fetch
            fetchAndStoreState(date)
        } else if (todayAt4amET in (lastFetchedTime + 1)..nowUtc) {
            // Current time is past 4am ET and last fetch was before 4am ET today
            fetchAndStoreState(date)
        } else {
            // Last fetch was after 4am ET, use database
            getStateFromDatabase(date) ?: fetchAndStoreState(date)
        }
    }

    private fun getLastFetchedTime(): Long? {
        return lastFetchedCollection.getDocument(LAST_FETCHED_KEY)?.getLong("timestamp")
    }

    private fun getTodayAt4amETInUtc(): Long {
        val etTimeZone = TimeZone.of("America/New_York")
        val today = Clock.System.todayIn(etTimeZone)
        val fourAmET = LocalTime(4, 0)
        val todayAt4amET = today.atTime(fourAmET)
        return todayAt4amET.toInstant(etTimeZone).epochSeconds
    }

    private suspend fun fetchAndStoreState(date: String): DailyFastbreak? {
        val result = fetchDailyFastbreak(date)
        saveLastFetchedTime()
        
        val dailyFastbreak = when (result) {
            is DailyFastbreakResult.Success -> {
                val response = result.response
                DailyFastbreak(
                    leaderboard = response.leaderboard,
                    fastbreakCard = response.fastbreakCard,
                    statSheet = response.statSheetForUser,
                    lastLockedCardResults = response.lastLockedCardResults,
                    lastFetchedDate = getLastFetchedTime(),
                    isFromCache = result.isFromCache,
                    rawJson = result.rawJson
                )
            }
            is DailyFastbreakResult.Error -> {
                println("Failed to fetch daily fastbreak: ${result.message}")
                null
            }
        }
        
        saveStateToDatabase(date, dailyFastbreak)
        enforceMaxDocumentsLimit()

        return dailyFastbreak
    }

    suspend fun lockCardApi(fastbreakSelectionState: FastbreakSelectionState): LockCardResult {
        val authedUser = authRepository?.getUser() ?: return LockCardResult.AuthenticationRequired
        return lockDailyFastbreakCard(LOCK_CARD, fastbreakSelectionState, authedUser)
    }

    private suspend fun fetchDailyFastbreak(date: String): DailyFastbreakResult {
        val getDailyFastbreakUrl = "${BASE_URL}/day/${date}"
        
        return if (cachedHttpClient != null) {
            // Use cached version when available
            getDailyFastbreakCached(cachedHttpClient, getDailyFastbreakUrl, null)
        } else {
            // Fallback to non-cached version
            getDailyFastbreak(getDailyFastbreakUrl, null)
        }
    }

    private fun saveStateToDatabase(date: String, state: DailyFastbreak?) {
        val doc = MutableDocument(date)
            .setString("data", if (state != null) Json.encodeToString(DailyFastbreak.serializer(), state) else "null")

        dailyStateCollection.save(doc)
    }

    private fun saveLastFetchedTime() {
        val doc = MutableDocument(LAST_FETCHED_KEY)
            .setLong("timestamp", Clock.System.now().epochSeconds)

        lastFetchedCollection.save(doc)
    }

    private fun getStateFromDatabase(date: String): DailyFastbreak? {
        return dailyStateCollection.getDocument(date)
            ?.getString("data")
            ?.let { Json.decodeFromString(it) }
    }

    private fun enforceMaxDocumentsLimit() {
        val query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(dailyStateCollection))
            .orderBy(Ordering.property("timestamp").ascending()) // Oldest first

        val documents = query.execute().allResults()
        if (documents.size > 10) {
            val excess = documents.size - 10
            documents.take(excess).forEach { result ->
                result.getString("id")?.let { dailyStateCollection.getDocument(it) }
                    ?.let { dailyStateCollection.delete(it) }
            }
        }
    }
}
