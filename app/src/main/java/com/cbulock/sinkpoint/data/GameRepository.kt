package com.cbulock.sinkpoint.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.NearbySave
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable data class Preferences(val theme: ThemeMode = ThemeMode.SYSTEM, val haptics: Boolean = true, val name: String = "Player", val config: GameConfig = GameConfig())
@Serializable data class SoloSave(val match: MatchState, val aiFleet: Fleet, val difficulty: Difficulty, val randomState: Long) {
    fun validate() {
        match.validate(); require(Rules.validFleet(match.config, aiFleet))
        var previous = emptySet<Int>()
        match.moves.filter { it.attacker == match.localId }.forEach {
            require(it.outcome == Rules.resolve(match.config, aiFleet, previous, it.targets))
            previous = previous + it.targets
        }
    }
}
@Serializable data class Record(val id: String, val mode: String, val difficulty: Difficulty?, val custom: Boolean, val won: Boolean, val shots: Int, val hits: Int, val date: Long)
@Serializable data class Aggregate(val played: Int = 0, val wins: Int = 0, val shots: Int = 0, val hits: Int = 0)
@Serializable data class AppData(
    val version: Int = 1,
    val playerId: String = UUID.randomUUID().toString(),
    val preferences: Preferences = Preferences(),
    val solo: SoloSave? = null,
    val nearby: NearbySave? = null,
    val records: List<Record> = emptyList(),
    val totals: Map<String, Aggregate> = emptyMap(),
    val recordedSoloId: String? = null,
    val recordedNearbyId: String? = null,
) {
    fun validate() {
        require(version == 1 && playerId.length in 1..64 && preferences.name.length in 1..32)
        preferences.config.validate(); solo?.validate(); nearby?.validate()
        nearby?.let { require(it.match.localId == playerId) }
        require(records.size <= 100 && records.map { it.id }.distinct().size == records.size)
        records.forEach { require(it.mode in listOf("AI", "NEARBY") && it.shots >= it.hits && it.hits >= 0 && it.date >= 0) }
        totals.values.forEach { require(it.played >= it.wins && it.wins >= 0 && it.shots >= it.hits && it.hits >= 0) }
    }
    fun withResults(now: Long = System.currentTimeMillis()): AppData {
        var result = this
        fun record(match: MatchState, mode: String, difficulty: Difficulty?) {
            if (match.phase != Phase.FINISHED) return
            val recorded = if (mode == "AI") result.recordedSoloId else result.recordedNearbyId
            if (recorded == match.id) return
            val r = Record(match.id, mode, difficulty, !match.config.classic, match.winner == match.localId, match.outgoing.tried.size, match.outgoing.hits.size, now)
            val key = "$mode:${difficulty?.name ?: "LOCAL"}:${if (r.custom) "CUSTOM" else "CLASSIC"}"
            val old = result.totals[key] ?: Aggregate()
            result = result.copy(records = (listOf(r) + result.records.filterNot { it.id == r.id }).take(100),
                totals = result.totals + (key to Aggregate(old.played + 1, old.wins + if (r.won) 1 else 0, old.shots + r.shots, old.hits + r.hits)),
                recordedSoloId = if (mode == "AI") match.id else result.recordedSoloId,
                recordedNearbyId = if (mode == "NEARBY") match.id else result.recordedNearbyId)
        }
        result.solo?.let { record(it.match, "AI", it.difficulty) }
        result.nearby?.takeIf { !it.blocked }?.let { record(it.match, "NEARBY", null) }
        return result
    }
}
@Serializable data class Backup(val version: Int = 1, val preferences: Preferences, val solo: SoloSave?, val records: List<Record>, val totals: Map<String, Aggregate>, val recordedSoloId: String?) {
    fun validate() = AppData(version = version, preferences = preferences, solo = solo, records = records, totals = totals).validate()
}

interface GameRepository {
    val data: Flow<AppData>
    suspend fun update(transform: (AppData) -> AppData): AppData
}
object SaveFormat {
    val json = Json { encodeDefaults = true }
    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    // Old matches alternated every shot. Keep their replay and pending exchanges valid,
    // while missing preference fields pick up the new default for future games.
    private fun migrateMatches(text: String): JsonObject {
        val root = json.parseToJsonElement(text).jsonObject.toMutableMap()
        for (key in listOf("solo", "nearby")) {
            val save = root[key] as? JsonObject ?: continue
            val match = save["match"] as? JsonObject ?: continue
            val config = match["config"] as? JsonObject ?: continue
            if ("fireAgainOnHit" !in config) {
                root[key] = JsonObject(save + ("match" to JsonObject(match + ("config" to
                    JsonObject(config + ("fireAgainOnHit" to JsonPrimitive(false)))))))
            }
        }
        return JsonObject(root)
    }
    fun decodeData(text: String): AppData {
        require(text.toByteArray().size <= MAX_BACKUP_BYTES)
        return json.decodeFromJsonElement<AppData>(migrateMatches(text)).also { it.validate() }
    }
    fun encode(data: AppData): String = json.encodeToString(Backup(preferences = data.preferences, solo = data.solo, records = data.records, totals = data.totals, recordedSoloId = data.recordedSoloId))
    fun decode(text: String): Backup {
        require(text.toByteArray().size <= MAX_BACKUP_BYTES) { "Backup is too large" }
        return json.decodeFromJsonElement<Backup>(migrateMatches(text)).also { it.validate() }
    }
    fun restore(current: AppData, backup: Backup): AppData {
        backup.validate()
        return current.copy(preferences = backup.preferences, solo = backup.solo, records = backup.records, totals = backup.totals,
            recordedSoloId = backup.recordedSoloId).also { it.validate() }
    }
}
class DiskGameRepository(context: Context) : GameRepository {
    private val store: DataStore<AppData> = DataStoreFactory.create(serializer = object : Serializer<AppData> {
        override val defaultValue = AppData()
        override suspend fun readFrom(input: InputStream): AppData {
            // No corruption replacement handler: unreadable progress must remain on disk.
            val bytes = input.readBytes()
            require(bytes.size <= SaveFormat.MAX_BACKUP_BYTES)
            return SaveFormat.decodeData(bytes.toString(Charsets.UTF_8))
        }
        override suspend fun writeTo(t: AppData, output: OutputStream) {
            t.validate()
            output.write(SaveFormat.json.encodeToString(t).toByteArray())
        }
    }, produceFile = { context.filesDir.resolve("sinkpoint-v1.json") })
    override val data = store.data
    override suspend fun update(transform: (AppData) -> AppData) = store.updateData { transform(it).withResults().also { data -> data.validate() } }
}
