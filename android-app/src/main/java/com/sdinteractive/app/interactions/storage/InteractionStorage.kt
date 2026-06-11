package com.sdinteractive.app.interactions.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sdinteractive.app.interactions.model.ClipEnd
import java.math.BigDecimal

data class QuizAnswerRecord(
    val quizId: String,
    val selectedOptionId: String,
    val selectedText: String,
    val episodeNumber: Int,
    val submitTimeMs: Long
) {
    init {
        require(quizId.isNotBlank())
        require(selectedOptionId.isNotBlank())
        require(episodeNumber > 0)
        require(submitTimeMs >= 0)
    }
}

data class RatingRecord(
    val ratingId: String,
    val value: Float,
    val episodeNumber: Int,
    val submitTimeMs: Long
) {
    init {
        require(ratingId.isNotBlank())
        require(value.isFinite())
        require(episodeNumber > 0)
        require(submitTimeMs >= 0)
    }
}

data class HighlightRecord(
    val episodeNumber: Int,
    val highlightId: String,
    val title: String,
    val clipStartSec: Double,
    val clipEnd: ClipEnd,
    val coverTimeSec: Double?,
    val createdAtMs: Long
) {
    init {
        require(episodeNumber > 0)
        require(highlightId.isNotBlank())
        require(clipStartSec.isFinite() && clipStartSec >= 0.0)
        require(coverTimeSec == null || coverTimeSec.isFinite() && coverTimeSec >= 0.0)
        require(createdAtMs >= 0)
    }
}

data class PersistentValueRecord(
    val key: String,
    val value: Int,
    val updatedAtMs: Long
) {
    init {
        require(key.isNotBlank())
        require(updatedAtMs >= 0)
    }
}

object InteractionStorageKeys {
    const val highlights = "user_highlights"

    fun quizAnswer(quizId: String): String = "quiz_answer_${requiredId(quizId)}"

    fun rating(ratingId: String): String = "rating_${requiredId(ratingId)}"

    fun persistentValue(persistKey: String): String = "value_${requiredId(persistKey)}"

    private fun requiredId(value: String): String {
        require(value.isNotBlank()) { "Storage key identifier must not be blank" }
        return value
    }
}

object InteractionStorageCodec {
    private const val SCHEMA_VERSION = 1

    fun encodeQuizAnswer(record: QuizAnswerRecord): String = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("quizId", record.quizId)
        addProperty("selectedOptionId", record.selectedOptionId)
        addProperty("selectedText", record.selectedText)
        addProperty("episodeNumber", record.episodeNumber)
        addProperty("submitTimeMs", record.submitTimeMs)
    }.toString()

    fun decodeQuizAnswer(json: String?): QuizAnswerRecord? = decode(json) { root ->
        QuizAnswerRecord(
            quizId = root.requiredString("quizId"),
            selectedOptionId = root.requiredString("selectedOptionId"),
            selectedText = root.requiredString("selectedText"),
            episodeNumber = root.requiredInt("episodeNumber"),
            submitTimeMs = root.requiredLong("submitTimeMs")
        )
    }

    fun encodeRating(record: RatingRecord): String = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("ratingId", record.ratingId)
        addProperty("value", record.value)
        addProperty("episodeNumber", record.episodeNumber)
        addProperty("submitTimeMs", record.submitTimeMs)
    }.toString()

    fun decodeRating(json: String?): RatingRecord? = decode(json) { root ->
        RatingRecord(
            ratingId = root.requiredString("ratingId"),
            value = root.requiredFloat("value"),
            episodeNumber = root.requiredInt("episodeNumber"),
            submitTimeMs = root.requiredLong("submitTimeMs")
        )
    }

    fun encodeHighlight(record: HighlightRecord): String = highlightToJson(record)
        .apply { addProperty("schemaVersion", SCHEMA_VERSION) }
        .toString()

    fun decodeHighlight(json: String?): HighlightRecord? = decode(json, ::highlightFromJson)

    fun encodeHighlights(records: List<HighlightRecord>): String = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        add("items", JsonArray().apply {
            records.forEach { add(highlightToJson(it)) }
        })
    }.toString()

    fun decodeHighlights(json: String?): List<HighlightRecord> = runCatching {
        val root = parseRoot(json)
        val items = root.requiredArray("items")
        items.mapNotNull { element ->
            runCatching { highlightFromJson(element.asJsonObject) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun encodePersistentValue(record: PersistentValueRecord): String = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("key", record.key)
        addProperty("value", record.value)
        addProperty("updatedAtMs", record.updatedAtMs)
    }.toString()

    fun decodePersistentValue(json: String?): PersistentValueRecord? = decode(json) { root ->
        PersistentValueRecord(
            key = root.requiredString("key"),
            value = root.requiredInt("value"),
            updatedAtMs = root.requiredLong("updatedAtMs")
        )
    }

    fun mergeHighlights(
        existing: List<HighlightRecord>,
        incoming: List<HighlightRecord>
    ): List<HighlightRecord> {
        val mergedById = linkedMapOf<String, HighlightRecord>()
        existing.forEach { record -> mergedById[record.highlightId] = record }
        incoming.forEach { record -> mergedById[record.highlightId] = record }
        return mergedById.values.toList()
    }

    fun clampValue(value: Int, min: Int, max: Int): Int {
        require(min <= max) { "min must not be greater than max" }
        return value.coerceIn(min, max)
    }

    private fun highlightToJson(record: HighlightRecord): JsonObject = JsonObject().apply {
        addProperty("episodeNumber", record.episodeNumber)
        addProperty("highlightId", record.highlightId)
        addProperty("title", record.title)
        addProperty("clipStartSec", record.clipStartSec)
        add("clipEnd", clipEndToJson(record.clipEnd))
        if (record.coverTimeSec == null) {
            add("coverTimeSec", null)
        } else {
            addProperty("coverTimeSec", record.coverTimeSec)
        }
        addProperty("createdAtMs", record.createdAtMs)
    }

    private fun highlightFromJson(root: JsonObject): HighlightRecord = HighlightRecord(
        episodeNumber = root.requiredInt("episodeNumber"),
        highlightId = root.requiredString("highlightId"),
        title = root.requiredString("title"),
        clipStartSec = root.requiredDouble("clipStartSec"),
        clipEnd = clipEndFromJson(root.requiredObject("clipEnd")),
        coverTimeSec = root.optionalDouble("coverTimeSec"),
        createdAtMs = root.requiredLong("createdAtMs")
    )

    private fun clipEndToJson(clipEnd: ClipEnd): JsonObject = JsonObject().apply {
        when (clipEnd) {
            is ClipEnd.Fixed -> {
                addProperty("type", "fixed")
                addProperty("seconds", clipEnd.seconds)
            }
            ClipEnd.EpisodeEnd -> addProperty("type", "episode_end")
        }
    }

    private fun clipEndFromJson(root: JsonObject): ClipEnd = when (root.requiredString("type")) {
        "fixed" -> ClipEnd.Fixed(root.requiredDouble("seconds"))
        "episode_end" -> ClipEnd.EpisodeEnd
        else -> error("Unsupported clip end type")
    }

    private fun <T> decode(json: String?, factory: (JsonObject) -> T): T? = runCatching {
        factory(parseRoot(json))
    }.getOrNull()

    private fun parseRoot(json: String?): JsonObject {
        require(!json.isNullOrBlank()) { "JSON must not be blank" }
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "JSON root must be an object" }
        return root.asJsonObject.also {
            require(it.requiredInt("schemaVersion") == SCHEMA_VERSION) {
                "Unsupported schema version"
            }
        }
    }

    private fun JsonObject.requiredString(name: String): String {
        val element = get(name)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString)
        return element.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = requiredNumber(name).toDouble()
        require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
        return value.toInt()
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val value = BigDecimal(requiredNumber(name).toString())
        return value.longValueExact()
    }

    private fun JsonObject.requiredFloat(name: String): Float {
        val value = requiredNumber(name).toFloat()
        require(value.isFinite())
        return value
    }

    private fun JsonObject.requiredDouble(name: String): Double {
        val value = requiredNumber(name).toDouble()
        require(value.isFinite())
        return value
    }

    private fun JsonObject.optionalDouble(name: String): Double? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        return requiredDouble(name)
    }

    private fun JsonObject.requiredNumber(name: String): Number {
        val element = get(name)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isNumber)
        return element.asNumber
    }

    private fun JsonObject.requiredObject(name: String): JsonObject {
        val element = get(name)
        require(element != null && element.isJsonObject)
        return element.asJsonObject
    }

    private fun JsonObject.requiredArray(name: String): JsonArray {
        val element = get(name)
        require(element != null && element.isJsonArray)
        return element.asJsonArray
    }
}

/**
 * Persistent interaction records.
 *
 * A `true` result from a save method means JSON encoding completed and the update was submitted
 * to the SharedPreferences in-memory update queue. It does not guarantee asynchronous disk
 * persistence. Callers should only show a save failure when a method returns `false`.
 */
interface InteractionRecords {
    fun quizAnswer(quizId: String): QuizAnswerRecord?
    fun saveQuizAnswer(record: QuizAnswerRecord): Boolean
    fun rating(ratingId: String): RatingRecord?
    fun saveRating(record: RatingRecord): Boolean
    fun highlights(): List<HighlightRecord>
    fun saveHighlight(record: HighlightRecord): Boolean
    fun persistentValue(key: String): Int?
    fun savePersistentValue(key: String, value: Int): Boolean
    fun clearAll()
}

interface InteractionKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun clear()
}

class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences
) : InteractionKeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

class SharedPreferencesInteractionStorage(
    private val store: InteractionKeyValueStore
) : InteractionRecords {
    constructor(preferences: SharedPreferences) : this(
        SharedPreferencesKeyValueStore(preferences)
    )

    override fun quizAnswer(quizId: String): QuizAnswerRecord? = runCatching {
        InteractionStorageCodec.decodeQuizAnswer(
            store.getString(InteractionStorageKeys.quizAnswer(quizId))
        )
    }.getOrNull()

    override fun saveQuizAnswer(record: QuizAnswerRecord): Boolean = runCatching {
        write(
            InteractionStorageKeys.quizAnswer(record.quizId),
            InteractionStorageCodec.encodeQuizAnswer(record)
        )
    }.getOrDefault(false)

    override fun rating(ratingId: String): RatingRecord? = runCatching {
        InteractionStorageCodec.decodeRating(
            store.getString(InteractionStorageKeys.rating(ratingId))
        )
    }.getOrNull()

    override fun saveRating(record: RatingRecord): Boolean = runCatching {
        write(
            InteractionStorageKeys.rating(record.ratingId),
            InteractionStorageCodec.encodeRating(record)
        )
    }.getOrDefault(false)

    override fun highlights(): List<HighlightRecord> = runCatching {
        InteractionStorageCodec.decodeHighlights(
            store.getString(InteractionStorageKeys.highlights)
        )
    }.getOrDefault(emptyList())

    override fun saveHighlight(record: HighlightRecord): Boolean =
        synchronized(storageLock) {
            runCatching {
                val merged = InteractionStorageCodec.mergeHighlights(
                    highlights(),
                    listOf(record)
                )
                write(
                    InteractionStorageKeys.highlights,
                    InteractionStorageCodec.encodeHighlights(merged)
                )
            }.getOrDefault(false)
        }

    override fun persistentValue(key: String): Int? = runCatching {
        InteractionStorageCodec.decodePersistentValue(
            store.getString(InteractionStorageKeys.persistentValue(key))
        )?.value
    }.getOrNull()

    override fun savePersistentValue(key: String, value: Int): Boolean = runCatching {
        write(
            InteractionStorageKeys.persistentValue(key),
            InteractionStorageCodec.encodePersistentValue(
                PersistentValueRecord(
                    key = key,
                    value = value,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        )
    }.getOrDefault(false)

    override fun clearAll() {
        synchronized(storageLock) {
            runCatching { store.clear() }
        }
    }

    private fun write(key: String, value: String): Boolean {
        store.putString(key, value)
        return true
    }

    companion object {
        const val PREFERENCES_NAME = "interaction-records"
        private val storageLock = Any()

        fun from(context: Context): SharedPreferencesInteractionStorage =
            SharedPreferencesInteractionStorage(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
    }
}
