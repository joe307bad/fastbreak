import kotbase.Array
import kotbase.Collection
import kotbase.Database
import kotbase.Document
import kotbase.MutableDocument
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

@Serializable
data class SelectionsWrapper(val id: String, val selectionDtos: List<FastbreakSelection>)

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
        return collectionRef
            ?: throw IllegalStateException("Collection 'fastbreak_selections' not found")
    }

    /**
     * Save the current selections to the database
     */
    suspend fun saveSelections(id: String, selections: List<FastbreakSelection>) {

        val selectionMaps = selections.map { selection ->
            mapOf(
                "id" to selection.id,
                "userAnswer" to selection.userAnswer,
                "points" to selection.points,
                "description" to selection.description,
                "type" to selection.type,
            )
        }

        // Get today's date in YYYY-MM-DD format
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString() // Returns in format YYYY-MM-DD

        // Create document with the selections
        val document = MutableDocument(today)
        document.setValue("selections", selectionMaps)
        document.setString("id", id)

        // Save to database
        getCollectionSafe().save(document)
    }

    /**
     * Load selections for today
     */
    suspend fun loadTodaySelections(): SelectionsWrapper? {
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
        val id = document?.getString("id") ?: return null
        val selections = getSelectionsFromDocument(document);


        // Convert DTOs back to domain objects
        return SelectionsWrapper(id, selections.toList() as List<FastbreakSelection>)
    }

    private fun getSelectionsFromDocument(document: Document): List<FastbreakSelection> {
        // Get the value as Any?
        @Suppress("UNCHECKED_CAST")
        val selectionsValue = document.getValue("selections") as Array?

        // Check if it's not null and cast to List<Map<String, Any?>>
        if (selectionsValue != null) {
            try {
                return (selectionsValue.toList() as List<*>).mapNotNull { item ->
                    if (item is Map<*, *>) {
                        // Cast to Map<String, Any?>
                        @Suppress("UNCHECKED_CAST")
                        val map = item as Map<String, Any?>

                        // Create FastbreakSelection object from map
                        FastbreakSelection(
                            id = map["id"] as? String ?: "",
                            userAnswer = map["userAnswer"] as? String ?: "",
                            points = (map["points"] as? Number)?.toInt() ?: 0,
                            description = map["description"] as? String ?: "",
                            type = map["type"] as? String ?: ""
                        )
                    } else null
                }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        // If the value is null or not a List, return an empty list
        return emptyList()
    }
}