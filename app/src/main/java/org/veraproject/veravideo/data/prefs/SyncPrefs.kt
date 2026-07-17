package org.veraproject.veravideo.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small bits of sync bookkeeping. DataStore rather than Room: these are
 * single-value settings, not queryable records.
 */
@Singleton
class SyncPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Backend cursor (`updatedAt` of the newest row we hold) for delta sync. */
    val cursor: Flow<String?> = dataStore.data.map { it[KEY_CURSOR] }

    /** Last ETag, sent as If-None-Match so an unchanged catalog costs a 304. */
    val etag: Flow<String?> = dataStore.data.map { it[KEY_ETAG] }

    val lastSyncedAt: Flow<Instant?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNCED_AT]?.let(Instant::ofEpochMilli)
    }

    suspend fun currentCursor(): String? = cursor.first()

    suspend fun currentEtag(): String? = etag.first()

    suspend fun recordSync(cursor: String?, etag: String?, at: Instant) {
        dataStore.edit { prefs ->
            // A null from the server means "no change" — keep what we have
            // rather than resetting to a full re-download next time.
            cursor?.let { prefs[KEY_CURSOR] = it }
            if (etag != null) prefs[KEY_ETAG] = etag else prefs.remove(KEY_ETAG)
            prefs[KEY_LAST_SYNCED_AT] = at.toEpochMilli()
        }
    }

    /** Forces the next sync to fetch everything. Used when the local cache is cleared. */
    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_CURSOR)
            prefs.remove(KEY_ETAG)
        }
    }

    private companion object {
        val KEY_CURSOR = stringPreferencesKey("catalog_cursor")
        val KEY_ETAG = stringPreferencesKey("catalog_etag")
        val KEY_LAST_SYNCED_AT = longPreferencesKey("catalog_last_synced_at")
    }
}
