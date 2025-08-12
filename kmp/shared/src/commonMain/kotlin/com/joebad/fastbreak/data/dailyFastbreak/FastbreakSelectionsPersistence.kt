package com.joebad.fastbreak.data.dailyFastbreak

import AuthRepository
import kotbase.Array
import kotbase.Collection
import kotbase.DataSource
import kotbase.Database
import kotbase.Dictionary
import kotbase.Expression
import kotbase.MutableDocument
import kotbase.Ordering
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class SelectionsWrapper(val cardId: String, val selectionDtos: List<FastbreakSelection>, val locked: Boolean? = false)

class FastbreakSelectionsPersistence(private val db: Database, private val authRepository: AuthRepository?) {

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

    fun saveSelections(cardId: String, selections: List<FastbreakSelection>, locked: Boolean? = false, date: String) {

        val selectionMaps = selections.map { selection ->
            mapOf(
                "_id" to selection._id,
                "userAnswer" to selection.userAnswer,
                "points" to selection.points,
                "description" to selection.description,
                "type" to selection.type,
            )
        }

        val document = MutableDocument()
        document.setValue("selections", selectionMaps)
        document.setString("cardId", cardId)
        document.setString("userId", null)
        document.setString("date", date)
        document.setBoolean("locked", locked ?: false)
        document.setLong("timestamp", Clock.System.now().toEpochMilliseconds())

        getCollectionSafe().save(document)
    }

    fun loadSelections(day: String): SelectionsWrapper? {
        val document = QueryBuilder
            .select(SelectResult.all())
            .from(DataSource.collection(getCollectionSafe()))
            .where(
                Expression.property("date").equalTo(Expression.string(day))
                    .and(Expression.property("userId").equalTo(Expression.string(null)))
            )
            .orderBy(Ordering.property("timestamp").descending())
            .execute().firstOrNull()

        val fs = document?.getDictionary("fastbreak_selections");
        val cardId = fs?.getString("cardId");
        val selections = getSelectionsFromDocument(fs);
        val locked = fs?.getBoolean("locked");

        return cardId?.let { SelectionsWrapper(it, selections.toList(), locked) }
    }

    private fun getSelectionsFromDocument(document: Dictionary?): List<FastbreakSelection> {
        @Suppress("UNCHECKED_CAST")
        val selectionsValue = document?.getValue("selections") as Array?

        if (selectionsValue != null) {
            try {
                return (selectionsValue.toList() as List<*>).mapNotNull { item ->
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val map = item as Map<String, Any?>

                        FastbreakSelection(
                            _id = map["_id"] as? String ?: "",
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