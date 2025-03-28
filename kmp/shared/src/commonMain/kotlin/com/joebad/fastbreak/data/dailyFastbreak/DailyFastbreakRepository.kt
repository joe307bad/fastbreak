
import io.ktor.client.HttpClient
import kotbase.DataSource
import kotbase.Database
import kotbase.Meta
import kotbase.MutableDocument
import kotbase.Ordering
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FastbreakStateRepository(private val db: Database, private val httpClient: HttpClient) {

    private val lastFetchedCollection = db.getCollection("LastFetchedCollection") ?: db.createCollection("LastFetchedCollection")
    private val dailyStateCollection = db.getCollection("FastBreakDailyStateCollection") ?: db.createCollection("FastBreakDailyStateCollection")

    companion object {
        private const val LAST_FETCHED_KEY = "lastFetchedDate"
        private const val API_URL = "http://10.0.2.2:1080/api/daily"
        private const val FETCH_THRESHOLD = 12 * 60 * 60
    }

    suspend fun getDailyFastbreakState(date: String): DailyFastbreak? {
        val now = Clock.System.now().epochSeconds
        val lastFetchedTime = getLastFetchedTime()
        delay(2000)

        return if (lastFetchedTime == null || (now - lastFetchedTime) > FETCH_THRESHOLD) {
            fetchAndStoreState(date)
        } else {
            getStateFromDatabase(date) ?: fetchAndStoreState(date)
        }
    }

    private fun getLastFetchedTime(): Long? {
        return lastFetchedCollection.getDocument(LAST_FETCHED_KEY)
            ?.getLong("timestamp")
    }

    private suspend fun fetchAndStoreState(date: String): DailyFastbreak? {
        val response = fetchFromApi()
        saveStateToDatabase(date, response)
        saveLastFetchedTime()
        enforceMaxDocumentsLimit()
        return response
    }

    private suspend fun fetchFromApi(): DailyFastbreak? {
        val apiResponse = getDailyFastbreakApi(API_URL)
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
