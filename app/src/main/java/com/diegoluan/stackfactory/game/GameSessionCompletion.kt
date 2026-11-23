package com.diegoluan.stackfactory.game

/**
 * Controla o fechamento lógico de uma partida, separado do estado de GAME OVER.
 * GAME OVER pode ser revertido por anúncio premiado e não deve incrementar contadores.
 * Sem dependências de Android para permitir testes unitários.
 */
internal class GameSessionCompletion {
    private var completed = false

    fun completeOnce(onComplete: () -> Unit): Boolean {
        if (completed) return false
        completed = true
        onComplete()
        return true
    }

    fun reset() {
        completed = false
    }
}
