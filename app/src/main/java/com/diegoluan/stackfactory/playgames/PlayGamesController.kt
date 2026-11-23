package com.diegoluan.stackfactory.playgames

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.diegoluan.stackfactory.R
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk

/**
 * Play Games Services v2: autenticação opcional e recursos de plataforma.
 * Não é a identidade principal do jogador nem substitui progresso local.
 * Os identificadores ficam em res/values/stackfactory_games_ids.xml.
 */
class PlayGamesController(
    private val activity: ComponentActivity,
    private val openGoogleUi: (Intent) -> Unit
) {
    private val projectId = activity.getString(R.string.game_services_project_id).trim()
    private val leaderboardId = activity.getString(R.string.stackfactory_leaderboard_id).trim()
    private val achievementIds = listOf(
        150 to activity.getString(R.string.stackfactory_achievement_150).trim(),
        500 to activity.getString(R.string.stackfactory_achievement_500).trim(),
        1000 to activity.getString(R.string.stackfactory_achievement_1000).trim()
    )

    val configured: Boolean = projectId.isNotEmpty() && projectId != "0"
    val hasLeaderboard: Boolean = configured && leaderboardId.isNotEmpty() && leaderboardId != "0"
    val hasAchievements: Boolean = configured && achievementIds.any { (_, id) -> id.isNotEmpty() && id != "0" }

    var authenticated by mutableStateOf(false)
        private set

    private var initialized = false
    private var lastSyncedScore = 0

    fun initialize() {
        if (!configured || initialized) return
        try {
            PlayGamesSdk.initialize(activity)
            initialized = true
            refreshAuthentication()
        } catch (error: Exception) {
            Log.w(TAG, "Play Games não pôde ser inicializado", error)
        }
    }

    fun refreshAuthentication() {
        if (!initialized) return
        PlayGames.getGamesSignInClient(activity).isAuthenticated
            .addOnSuccessListener { result ->
                val wasAuthenticated = authenticated
                authenticated = result.isAuthenticated
                if (authenticated && !wasAuthenticated) {
                    // Recupera pontuações anteriores guardadas localmente.
                    val prefs = activity.getSharedPreferences("stack_factory", Context.MODE_PRIVATE)
                    submitScore(prefs.getInt("high_score", 0))
                }
                if (!authenticated) lastSyncedScore = 0
            }
            .addOnFailureListener { error ->
                authenticated = false
                Log.d(TAG, "Autenticação indisponível; jogo local preservado", error)
            }
    }

    /** A autenticação é automática na abertura; ação manual apenas como fallback. */
    fun requestSignIn() {
        if (!initialized) return
        PlayGames.getGamesSignInClient(activity).signIn()
            .addOnCompleteListener { refreshAuthentication() }
    }

    /** Chamar ao terminar uma partida; nunca em todo frame ou toque. */
    fun onGameFinished(score: Int) = submitScore(score)

    private fun submitScore(score: Int) {
        if (!authenticated || score <= 0 || score <= lastSyncedScore) return
        lastSyncedScore = score
        if (hasLeaderboard) {
            PlayGames.getLeaderboardsClient(activity).submitScore(leaderboardId, score.toLong())
        }
        if (hasAchievements) {
            val achievements = PlayGames.getAchievementsClient(activity)
            achievementIds.forEach { (threshold, id) ->
                if (score >= threshold && id.isNotBlank() && id != "0") {
                    achievements.unlock(id)
                }
            }
        }
    }

    fun showLeaderboard() {
        if (!authenticated || !hasLeaderboard) return
        PlayGames.getLeaderboardsClient(activity).getLeaderboardIntent(leaderboardId)
            .addOnSuccessListener { intent -> openGoogleUi(intent) }
            .addOnFailureListener { Log.w(TAG, "Ranking indisponível", it) }
    }

    fun showAchievements() {
        if (!authenticated || !hasAchievements) return
        PlayGames.getAchievementsClient(activity).getAchievementsIntent()
            .addOnSuccessListener { intent -> openGoogleUi(intent) }
            .addOnFailureListener { Log.w(TAG, "Conquistas indisponíveis", it) }
    }

    private companion object {
        const val TAG = "StackFactoryPlayGames"
    }
}
