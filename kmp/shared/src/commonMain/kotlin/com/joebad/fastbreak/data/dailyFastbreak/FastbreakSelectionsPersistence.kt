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
data class SelectionsWrapper(val id: String, val selectionDtos: List<FastbreakSelection>, val locked: Boolean? = false)

class FastbreakSelectionsPersistence(private val db: Database) {

    private val collectionRef: Collection? by lazy {
        val existingCollection = db.getCollection("fastbreak_selections")
        if (existingCollection == null) {
            db.createCollection("fastbreak_selections")
        }
        db.getCollection("fastbreak_selections")
    }

    private fun getCollectionSafe(): Collection {
        return collectionRef
            ?: throw IllegalStateException("Collection 'fastbreak_selections' not found")
    }

    fun saveSelections(id: String, selections: List<FastbreakSelection>, locked: Boolean? = false) {

        val selectionMaps = selections.map { selection ->
            mapOf(
                "id" to selection.id,
                "userAnswer" to selection.userAnswer,
                "points" to selection.points,
                "description" to selection.description,
                "type" to selection.type,
            )
        }

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

        val document = MutableDocument(today)
        document.setValue("selections", selectionMaps)
        document.setString("id", id)
        document.setBoolean("locked", locked ?: false)

        getCollectionSafe().save(document)
    }

    fun loadTodaySelections(): SelectionsWrapper? {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val yesterday = (Clock.System.now() - 1.days)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val tomorrow = (Clock.System.now() + 1.days)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

        val document = getCollectionSafe().getDocument(today)
        val yestDocument = getCollectionSafe().getDocument(yesterday)

        val id = document?.getString("id") ?: return null
        val selections = getSelectionsFromDocument(document);
        val locked = document.getBoolean("locked");


        return SelectionsWrapper(id, selections.toList(), locked)
    }

    private fun getSelectionsFromDocument(document: Document): List<FastbreakSelection> {
        @Suppress("UNCHECKED_CAST")
        val selectionsValue = document.getValue("selections") as Array?

        if (selectionsValue != null) {
            try {
                return (selectionsValue.toList() as List<*>).mapNotNull { item ->
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val map = item as Map<String, Any?>

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

        return emptyList()
    }
}