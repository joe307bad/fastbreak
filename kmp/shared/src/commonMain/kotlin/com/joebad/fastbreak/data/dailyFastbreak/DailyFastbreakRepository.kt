package com.joebad.fastbreak.data.dailyFastbreak

import AuthRepository
import com.joebad.fastbreak.getPlatform
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.model.dtos.DailyResponse
import io.ktor.client.HttpClient
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
    private val db: Database,
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository?
) {
    private val persistence = FastbreakSelectionsPersistence(db, authRepository)

    private val lastFetchedCollection =
        db.getCollection("LastFetchedCollection") ?: db.createCollection("LastFetchedCollection")
    private val dailyStateCollection = db.getCollection("FastBreakDailyStateCollection")
        ?: db.createCollection("FastBreakDailyStateCollection")
    private val BASE_URL = if (getPlatform().name == "iOS") "localhost" else "10.0.2.2"
    private val LOCK_CARD = "http://${BASE_URL}:8085/api/lock"

    companion object {
        private const val LAST_FETCHED_KEY = "lastFetchedDate"
        private const val FETCH_THRESHOLD = 12 * 60 * 60
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
        val response = fetchDailyFastbreak(date)
        saveLastFetchedTime()
        val dailyFastbreak =
            response?.let {
                DailyFastbreak(
                    leaderboard = it.leaderboard,
                    fastbreakCard = response.fastbreakCard,
                    statSheet = it.statSheetForUser,
                    lastLockedCardResults = it.lastLockedCardResults,
                    lastFetchedDate = getLastFetchedTime()
                )
            }
        saveStateToDatabase(date, dailyFastbreak)
        enforceMaxDocumentsLimit()

        return dailyFastbreak
    }

    suspend fun lockCardApi(fastbreakSelectionState: FastbreakSelectionState): LockCardResponse? {
        val authedUser = authRepository?.getUser() ?: return null
        val apiResponse = lockDailyFastbreakCard(LOCK_CARD, fastbreakSelectionState, authedUser)
        return apiResponse
    }

    private suspend fun fetchDailyFastbreak(date: String): DailyResponse? {
        val getDailyFastbreakUrl = "http://${BASE_URL}:8085/api/day/${date}"
        val apiResponse = getDailyFastbreak(getDailyFastbreakUrl, authRepository?.getUser()?.userId)
        return apiResponse
    }

    private fun saveStateToDatabase(date: String, state: DailyFastbreak?) {
        val doc = MutableDocument(date)
            .setString("data", Json.encodeToString(state))

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
