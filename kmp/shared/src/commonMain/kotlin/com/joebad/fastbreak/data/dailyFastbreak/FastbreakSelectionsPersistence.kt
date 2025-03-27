
import kotbase.Collection
import kotbase.Database
import kotbase.MutableDocument
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

@Serializable
data class FastbreakSelectionDto(
    val id: String,
    val userAnswer: String,
    val points: Int,
    val description: String,
    val type: String
)

class FastbreakSelectionsPersistence(private val db: Database) {

    // Use a different naming approach to avoid JVM signature clash
    private val collectionRef: Collection? by lazy {
        // Check if collection exists, create it if it doesn't
        val existingCollection = db.getCollection("fastbreak_selections")
        if (existingCollection == null) {
            // Create the collection if it doesn't exist
            db.createCollection("fastbreak_selections")
        }
        // Now get the collection (which should now exist)
        db.getCollection("fastbreak_selections")
    }

    private fun getCollectionSafe(): Collection {
        return collectionRef ?: throw IllegalStateException("Collection 'fastbreak_selections' not found")
    }

    private val json = Json { prettyPrint = true }

    /**
     * Save the current selections to the database
     */
    suspend fun saveSelections(selections: List<FastbreakSelection>) {
        // Convert domain objects to serializable DTOs
        val selectionDtos = selections.map { selection ->
            FastbreakSelectionDto(
                id = selection.id,
                userAnswer = selection.userAnswer,
                points = selection.points,
                description = selection.description,
                type = selection.type
            )
        }

        // Serialize to JSON
        val selectionsJson = json.encodeToString(selectionDtos)

        // Get today's date in YYYY-MM-DD format
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString() // Returns in format YYYY-MM-DD

        // Create document with the selections
        val document = MutableDocument(today)
        document.setString("selections", selectionsJson)

        // Save to database
        getCollectionSafe().save(document)
    }

    /**
     * Load selections for today
     */
    suspend fun loadTodaySelections(): List<FastbreakSelection>? {
        // Get today's date in YYYY-MM-DD format
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val yesterday = (Clock.System.now() - 1.days)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

        // Try to get document for today
        val document = getCollectionSafe().getDocument(today)
        val yestDocument = getCollectionSafe().getDocument(yesterday)

        // Parse JSON string from the document
        val selectionsJson = document?.getString("selections") ?: return null

        // Deserialize JSON to DTOs
        val selectionDtos = json.decodeFromString<List<FastbreakSelectionDto>>(selectionsJson)

        // Convert DTOs back to domain objects
        return selectionDtos.map { dto ->
            FastbreakSelection(
                id = dto.id,
                userAnswer = dto.userAnswer,
                points = dto.points,
                description = dto.description,
                type = dto.type
            )
        }
    }
}