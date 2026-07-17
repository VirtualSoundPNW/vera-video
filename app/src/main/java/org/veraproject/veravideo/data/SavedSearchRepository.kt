package org.veraproject.veravideo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.veraproject.veravideo.data.local.SavedSearchDao
import org.veraproject.veravideo.domain.SavedSearch
import org.veraproject.veravideo.domain.SearchQuery
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedSearchRepository @Inject constructor(
    private val savedSearchDao: SavedSearchDao,
) {

    fun observeAll(): Flow<List<SavedSearch>> =
        savedSearchDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun get(id: Long): SavedSearch? = savedSearchDao.getById(id)?.toDomain()

    suspend fun save(name: String, query: SearchQuery): Long =
        savedSearchDao.upsert(
            SavedSearch(name = name.trim(), query = query, createdAt = Instant.now()).toEntity(),
        )

    suspend fun delete(id: Long) = savedSearchDao.delete(id)
}
