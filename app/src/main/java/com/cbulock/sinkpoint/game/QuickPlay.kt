package com.cbulock.sinkpoint.game

import kotlinx.serialization.Serializable

@Serializable enum class QuickPreset {
    SKIRMISH, CLASSIC, SALVO, CUSTOM;
    fun config(custom: GameConfig): GameConfig = when (this) {
        SKIRMISH -> GameConfig(6, listOf(3, 2, 2))
        CLASSIC -> GameConfig()
        SALVO -> GameConfig(rule = TurnRule.SALVO)
        CUSTOM -> custom
    }
}
