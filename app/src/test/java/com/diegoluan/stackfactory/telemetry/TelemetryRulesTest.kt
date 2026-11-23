package com.diegoluan.stackfactory.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRulesTest {
    @Test fun `metas cruzadas nao sao repetidas`() {
        assertEquals(listOf(40), TelemetryRules.newlyReachedMilestones(39, 40))
        assertEquals(listOf(75, 100), TelemetryRules.newlyReachedMilestones(65, 108))
        assertEquals(emptyList<Int>(), TelemetryRules.newlyReachedMilestones(100, 100))
    }

    @Test fun `checkpoint somente ao cruzar multiplos de dez`() {
        assertTrue(TelemetryRules.isCheckpoint(9, 10))
        assertFalse(TelemetryRules.isCheckpoint(10, 11))
        assertTrue(TelemetryRules.isCheckpoint(10, 22))
    }

    @Test fun `revive continua a mesma partida e nao duplica termino`() {
        val game = TelemetryGameState()
        game.start("game-a", 1000)
        game.score(39)
        assertTrue(game.canRevive())
        game.score(80)
        assertEquals("game-a", game.gameId)
        assertTrue(game.finish(87))
        assertFalse(game.finish(87))
        assertFalse(game.canRevive())
        game.start("game-b", 5000)
        assertEquals(0, game.lastScore)
        assertTrue(game.canRevive())
    }

    @Test fun `pontuacao nunca diminui`() {
        val game = TelemetryGameState()
        game.start("game-a", 0)
        assertEquals(0 to 30, game.score(30))
        assertEquals(null, game.score(15))
        assertEquals(30, game.lastScore)
    }
}
