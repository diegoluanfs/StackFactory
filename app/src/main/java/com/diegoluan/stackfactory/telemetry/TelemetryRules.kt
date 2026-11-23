package com.diegoluan.stackfactory.telemetry

/** Regras puras: não dependem do Android, do Firebase ou do estado de anúncios. */
object TelemetryRules {
    private val milestones = listOf(40, 75, 100)

    /** Só produz um evento por marco alcançado; partidas diferentes não se somam. */
    fun newlyReachedMilestones(previousScore: Int, newScore: Int): List<Int> =
        milestones.filter { it > previousScore && it <= newScore }

    /** Checkpoint periódico para não enviar um evento a cada bloco. */
    fun isCheckpoint(previousScore: Int, newScore: Int): Boolean =
        newScore / 10 > previousScore / 10 && newScore > 0

    fun safeScore(value: Int): Int = value.coerceAtLeast(0)
}

/** Uma única partida lógica atravessa a recuperação por anúncio. */
class TelemetryGameState {
    var gameId: String? = null
        private set
    var lastScore: Int = 0
        private set
    var startedAtMs: Long = 0L
        private set
    private var finished = false

    fun start(newId: String, timestampMs: Long) {
        require(newId.isNotBlank())
        gameId = newId
        startedAtMs = timestampMs
        lastScore = 0
        finished = false
    }

    fun score(value: Int): Pair<Int, Int>? {
        if (gameId == null || finished) return null
        val old = lastScore
        val next = maxOf(old, TelemetryRules.safeScore(value))
        if (next == old) return null
        lastScore = next
        return old to next
    }

    /** Retorna false após a primeira conclusão, inclusive quando o jogador revive. */
    fun finish(value: Int): Boolean {
        if (gameId == null || finished) return false
        lastScore = maxOf(lastScore, TelemetryRules.safeScore(value))
        finished = true
        return true
    }

    fun canRevive(): Boolean = gameId != null && !finished

    fun abandon(): Boolean {
        if (gameId == null || finished) return false
        finished = true
        return true
    }
}
