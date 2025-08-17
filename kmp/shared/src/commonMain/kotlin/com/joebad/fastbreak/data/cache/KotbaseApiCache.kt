package com.joebad.fastbreak.data.cache

import kotbase.Collection
import kotbase.Database
import kotbase.DataSource
import kotbase.Expression
import kotbase.MutableDocument
import kotbase.Ordering
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.datetime.Clock

/**
 * Kotbase-backed implementation of ApiCache.
 * Stores API GET responses as documents where:
 * - Document ID is a hash of the URL
 * - Document contains: url, response, timestamp
 */
class KotbaseApiCache(
    private val database: Database,
    private val collectionName: String = "api_cache"
) : ApiCache {
    
    private val collection: Collection by lazy {
        database.getCollection(collectionName) ?: database.createCollection(collectionName)
    }
    
    override suspend fun get(url: String): String? {
        val docId = urlToDocId(url)
        return collection.getDocument(docId)?.getString("response")
    }
    
    override suspend fun put(url: String, jsonResponse: String) {
        val docId = urlToDocId(url)
        val document = MutableDocument(docId).apply {
            setString("url", url)
            setString("response", jsonResponse)
            setLong("timestamp", Clock.System.now().toEpochMilliseconds())
        }
        collection.save(document)
    }
    
    override suspend fun remove(url: String) {
        val docId = urlToDocId(url)
        collection.getDocument(docId)?.let { document ->
            collection.delete(document)
        }
    }
    
    override suspend fun clear() {
        val query = QueryBuilder
            .select(SelectResult.expression(kotbase.Meta.id))
            .from(DataSource.collection(collection))
        
        query.execute().allResults().forEach { result ->
            result.getString("id")?.let { docId ->
                collection.getDocument(docId)?.let { document ->
                    collection.delete(document)
                }
            }
        }
    }
    
    override suspend fun contains(url: String): Boolean {
        val docId = urlToDocId(url)
        return collection.getDocument(docId) != null
    }
    
    /**
     * Removes old cache entries to keep collection size manageable.
     * Keeps only the most recent N entries.
     */
    suspend fun cleanup(maxEntries: Int = 100) {
        val query = QueryBuilder
            .select(SelectResult.expression(kotbase.Meta.id))
            .from(DataSource.collection(collection))
            .orderBy(Ordering.property("timestamp").ascending()) // Oldest first
        
        val documents = query.execute().allResults()
        if (documents.size > maxEntries) {
            val excess = documents.size - maxEntries
            documents.take(excess).forEach { result ->
                result.getString("id")?.let { docId ->
                    collection.getDocument(docId)?.let { document ->
                        collection.delete(document)
                    }
                }
            }
        }
    }
    
    /**
     * Converts URL to a valid document ID by creating a hash.
     * Uses simple string hash to avoid special characters in document IDs.
     */
    private fun urlToDocId(url: String): String {
        return "cache_${url.hashCode().toString().replace("-", "neg")}"
    }
}