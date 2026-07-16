package com.bobai.studio.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * A single visual effect baked onto a fragment (glitch, shake, zoom, etc).
 * These are preserved when the underlying media of a fragment is swapped out
 * via "Change Edit" — only the pixels change, the effect stack stays.
 */
data class FragmentEffect(
    val type: String,        // e.g. "glitch", "rgb_split", "shake", "zoom_punch"
    val intensity: Float = 1f,
    val startMs: Long = 0,
    val durationMs: Long = 0
)

/** What kind of source media backs a timeline fragment. */
enum class FragmentSourceType { VIDEO, IMAGE }

/**
 * One block on the timeline. Originally sliced from the imported TikTok edit;
 * after "Change Edit" its sourcePath can point at a totally different file
 * while keeping id/order/effects/duration intact so beat-sync & effects survive.
 */
@Entity(tableName = "fragments")
data class TimelineFragment(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int,
    val sourceType: FragmentSourceType,
    val sourcePath: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val thumbnailPath: String? = null,
    val effects: List<FragmentEffect> = emptyList(),
    val aiSceneLabel: String? = null,   // Groq vision description, used in "Change Edit" grid
    val aiSceneTags: List<String> = emptyList(),
    val isWatermarkCleaned: Boolean = false
) {
    val durationMs: Long get() = trimEndMs - trimStartMs
}

/** A user-placed beat marker on the audio track, used for snap-to-beat editing. */
data class BeatMarker(
    val id: String,
    val timeMs: Long,
    val isAutoDetected: Boolean = false
)

/** A manually drawn watermark region (as on the reference screenshot's red box). */
data class WatermarkRegion(
    val xNorm: Float,      // 0..1 relative to frame width
    val yNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float
)

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    val originalVideoPath: String,
    val createdAtMs: Long,
    val durationMs: Long,
    val audioTrackPath: String? = null,
    val beatMarkers: List<BeatMarker> = emptyList(),
    val watermarkRegion: WatermarkRegion? = null,
    val watermarkRemoved: Boolean = false
)

class Converters {
    @TypeConverter
    fun fragmentSourceTypeToString(v: FragmentSourceType) = v.name

    @TypeConverter
    fun stringToFragmentSourceType(v: String) = FragmentSourceType.valueOf(v)

    // Simple pipe-delimited encodings keep this file dependency-free (no extra
    // JSON converter needed just for Room). Fine for the small structures here.
    @TypeConverter
    fun effectsToString(effects: List<FragmentEffect>): String =
        effects.joinToString(";") { "${it.type},${it.intensity},${it.startMs},${it.durationMs}" }

    @TypeConverter
    fun stringToEffects(raw: String): List<FragmentEffect> =
        if (raw.isBlank()) emptyList() else raw.split(";").map {
            val (type, intensity, start, duration) = it.split(",")
            FragmentEffect(type, intensity.toFloat(), start.toLong(), duration.toLong())
        }

    @TypeConverter
    fun beatsToString(beats: List<BeatMarker>): String =
        beats.joinToString(";") { "${it.id},${it.timeMs},${it.isAutoDetected}" }

    @TypeConverter
    fun stringToBeats(raw: String): List<BeatMarker> =
        if (raw.isBlank()) emptyList() else raw.split(";").map {
            val (id, time, auto) = it.split(",")
            BeatMarker(id, time.toLong(), auto.toBoolean())
        }

    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString("|")

    @TypeConverter
    fun stringToTags(raw: String): List<String> = if (raw.isBlank()) emptyList() else raw.split("|")

    @TypeConverter
    fun regionToString(r: WatermarkRegion?): String =
        r?.let { "${it.xNorm},${it.yNorm},${it.widthNorm},${it.heightNorm}" } ?: ""

    @TypeConverter
    fun stringToRegion(raw: String): WatermarkRegion? {
        if (raw.isBlank()) return null
        val (x, y, w, h) = raw.split(",")
        return WatermarkRegion(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
    }
}

private operator fun List<String>.component4() = this[3]
