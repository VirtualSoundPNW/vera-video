package org.veraproject.veravideo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VideoEntity::class,
        VideoFtsEntity::class,
        SavedSearchEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VeraDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun savedSearchDao(): SavedSearchDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "vera-video.db"
    }
}
