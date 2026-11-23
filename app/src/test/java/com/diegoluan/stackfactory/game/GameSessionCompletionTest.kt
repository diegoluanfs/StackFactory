package com.diegoluan.stackfactory.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionCompletionTest {
    @Test fun `rewarded revival does not close session and exit counts once`() {
        val session = GameSessionCompletion()
        var counted = 0

        // GAME OVER inicial, vídeo premiado e retorno à mesma partida.
        // Nenhum desses eventos deve concluir a sessão.
        assertEquals(0, counted)

        assertTrue(session.completeOnce { counted++ })
        assertFalse(session.completeOnce { counted++ })
        assertFalse(session.completeOnce { counted++ })
        assertEquals(1, counted)
    }

    @Test fun `restart permits a new counted game`() {
        val session = GameSessionCompletion()
        var counted = 0
        assertTrue(session.completeOnce { counted++ })
        session.reset()
        assertTrue(session.completeOnce { counted++ })
        assertEquals(2, counted)
    }
}
