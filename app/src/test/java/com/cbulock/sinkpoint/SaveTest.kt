package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.NearbySave
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class SaveTest {
    @Test fun legacyProgressKeepsAlternatingButNewGamesDefaultToRepeatingHits() {
        var m = solo().match.copy(config = solo().match.config.copy(fireAgainOnHit = false))
        m = m.append(Move("hit", 0, "player", listOf(6), Outcome(listOf(6), emptyList())))
        m = m.append(Move("reply", 1, "ai", listOf(35), Outcome(emptyList(), emptyList())))
        val data = AppData(playerId = "player", solo = solo().copy(match = m), nearby = NearbySave(m.copy(id = "nearby"), true))
        fun legacy(text: String) = text.replace(Regex(",\"fireAgainOnHit\":(true|false)"), "")
        val restored = SaveFormat.decodeData(legacy(SaveFormat.json.encodeToString(data)))
        assertTrue(restored.preferences.config.fireAgainOnHit)
        assertFalse(restored.solo!!.match.config.fireAgainOnHit)
        assertFalse(restored.nearby!!.match.config.fireAgainOnHit)
        assertEquals(m.moves, restored.solo!!.match.moves)
        val backup = SaveFormat.decode(legacy(SaveFormat.encode(data)))
        assertFalse(backup.solo!!.match.config.fireAgainOnHit)
        assertTrue(backup.preferences.config.fireAgainOnHit)
        assertEquals(data, SaveFormat.decodeData(SaveFormat.json.encodeToString(data)))
    }
    @Test fun normalRetainsSaveAndStatisticsKeys() {
        val saved = solo().copy(difficulty = Difficulty.NORMAL)
        val data = AppData(playerId = "player", solo = saved.copy(match = saved.match.copy(resigned = "ai"))).withResults(123)
        val text = SaveFormat.encode(data)
        assertTrue(text.contains("\"difficulty\":\"NORMAL\""))
        val restored = SaveFormat.restore(AppData(playerId = "player"), SaveFormat.decode(text))
        assertEquals(data.solo, restored.solo)
        assertEquals(data.records, restored.records)
        assertEquals(data.totals, restored.totals)
        assertEquals(1, restored.totals.getValue("AI:NORMAL:CUSTOM").played)
    }

    private fun solo(): SoloSave {
        val c = GameConfig(6, listOf(2))
        return SoloSave(MatchState("solo", c, "player", "ai", "player", Fleet(listOf(Piece(0, 0, true))), true, true), Fleet(listOf(Piece(0, 6, true))), Difficulty.HARD, 1234)
    }
    @Test fun restartPreservesPrivateAndPublicStateAndRandomSequence() {
        val d = AppData(playerId = "player", solo = solo())
        val restored = SaveFormat.json.decodeFromString<AppData>(SaveFormat.json.encodeToString(d))
        restored.validate(); assertEquals(d, restored)
        assertEquals(GameRandom(d.solo!!.randomState).nextInt(36), GameRandom(restored.solo!!.randomState).nextInt(36))
    }
    @Test fun backupExcludesNearbyAndImportPreservesIt() {
        val m = solo().match.copy(id = "nearby", peerId = "friend")
        val data = AppData(playerId = "player", solo = solo(), nearby = NearbySave(m, true))
        val text = SaveFormat.encode(data)
        assertFalse(text.contains("friend")); assertFalse(text.contains("nearby"))
        val backup = SaveFormat.decode(text)
        val restored = SaveFormat.restore(data.copy(solo = null), backup)
        assertEquals(data.nearby, restored.nearby); assertEquals(data.solo, restored.solo)
    }
    @Test fun corruptUnknownAndInvalidBackupsFailWithoutMutation() {
        val original = AppData(playerId = "player", solo = solo())
        assertTrue(runCatching { SaveFormat.decode("broken") }.isFailure)
        val text = SaveFormat.encode(original)
        assertTrue(runCatching { SaveFormat.decode(text.replace("\"version\":1", "\"version\":99")) }.isFailure)
        assertEquals(solo(), original.solo)
    }
    @Test fun finishedMatchRecordsExactlyOnceAcrossRestarts() {
        val s = solo().let { it.copy(match = it.match.copy(resigned = "ai")) }
        val data = AppData(playerId = "player", solo = s).withResults(123)
        val restarted = SaveFormat.json.decodeFromString<AppData>(SaveFormat.json.encodeToString(data)).withResults(456)
        assertEquals(data, restarted); assertEquals(1, data.records.size); assertEquals(1, data.totals.values.single().played)
    }
}
