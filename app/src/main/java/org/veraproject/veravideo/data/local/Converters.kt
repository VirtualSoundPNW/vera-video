package org.veraproject.veravideo.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * minSdk 26 means `java.time` is available natively, so instants are stored as
 * epoch millis without needing core-library desugaring.
 */
class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /**
     * Tags are a small list of short strings; a delimiter-joined column keeps
     * them searchable by FTS as plain text. Newline cannot appear in a YouTube
     * tag, which makes it a safe separator.
     */
    @TypeConverter
    fun tagsToString(tags: List<String>?): String = tags.orEmpty().joinToString(TAG_SEPARATOR)

    @TypeConverter
    fun stringToTags(value: String?): List<String> =
        value?.split(TAG_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val TAG_SEPARATOR = "\n"
    }
}
