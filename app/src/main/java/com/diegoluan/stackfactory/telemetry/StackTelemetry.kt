package com.diegoluan.stackfactory.telemetry

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Telemetria opt-in, separada dos dados necessários para jogar (Lumis/check-in).
 * Nenhum MAC, Android ID ou identificador publicitário é coletado por este módulo.
 * Uma nova instalação não é tratada como a mesma pessoa.
 *
 * Eventos são persistidos antes do envio. WorkManager tenta novamente ao haver rede.
 * Não depender de onDestroy() ou bloquear a saída do app.
 */
object StackTelemetry {
    private const val TAG = "StackTelemetry"
    private const val PREFS = "stackfactory_telemetry_settings"
    private const val KEY_ENABLED = "telemetry_opt_in"
    private const val KEY_OPEN_GAME = "unfinished_game"
    private const val SYNC_NAME = "stackfactory_telemetry_upload"
    private const val DELETE_NAME = "stackfactory_telemetry_delete"
    private const val KEY_DELETE_REMOTE = "delete_remote_pending"
    private const val CHECKPOINT_INTERVAL = 10

    private var store: TelemetryDatabase? = null
    private val game = TelemetryGameState()
    private var sessionId = ""
    private var foregroundAtMs = 0L
    private var inForeground = false

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return
        val app = context.applicationContext
        store = TelemetryDatabase(app)
        val enabled = isEnabled(app)
        applyAnalyticsCollection(app, enabled)
        applyCrashCollection(app, enabled)
        if (remoteDeletionPending(app)) scheduleRemoteDeletion(app)

        // Partida perdida ao encerrar o processo: registra como interrompida,
        // sem inventar pontuação ou duplicar conclusão de partida.
        if (enabled) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val abandonedId = prefs.getString(KEY_OPEN_GAME, null)
            if (!abandonedId.isNullOrBlank()) {
                append(app, "game_interrupted", "", abandonedId, 0)
                prefs.edit().remove(KEY_OPEN_GAME).commit()
            }
            scheduleSync(app)
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        initialize(app)
        if (enabled == isEnabled(app)) return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_DELETE_REMOTE, !enabled || remoteDeletionPending(app))
            .putString("pending_delete_uid", if (!enabled) {
                runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            } else {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("pending_delete_uid", null)
            })
            .remove(KEY_OPEN_GAME)
            .commit()
        applyAnalyticsCollection(app, enabled)
        applyCrashCollection(app, enabled)
        if (enabled) {
            if (inForeground) {
                sessionId = newId()
                foregroundAtMs = System.currentTimeMillis()
                append(app, "sf_session_open", sessionId, "", 0, deviceDescription())
            }
            scheduleSync(app)
        } else {
            // Cancela envios futuros e elimina a fila local de análise.
            WorkManager.getInstance(app).cancelUniqueWork(SYNC_NAME)
            store?.purgeAll()
            scheduleRemoteDeletion(app)
            sessionId = ""
            game.abandon()
            FirebaseApp.getApps(app).firstOrNull()?.let {
                runCatching { FirebaseAnalytics.getInstance(app).resetAnalyticsData() }
            }
        }
    }

    @Synchronized
    fun onForeground(context: Context) {
        val app = context.applicationContext
        initialize(app)
        if (inForeground) return
        inForeground = true
        if (!isEnabled(app)) return
        sessionId = newId()
        foregroundAtMs = System.currentTimeMillis()
        append(app, "sf_session_open", sessionId, "", 0, deviceDescription())
        scheduleSync(app)
    }

    @Synchronized
    fun onBackground(context: Context) {
        val app = context.applicationContext
        if (!inForeground) return
        inForeground = false
        if (!isEnabled(app)) return
        val seconds = ((System.currentTimeMillis() - foregroundAtMs) / 1000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        append(app, "sf_session_close", sessionId, "", seconds)
        sessionId = ""
        store?.purgeUploaded()
        scheduleSync(app)
    }

    @Synchronized
    fun gameStarted(context: Context) {
        val app = context.applicationContext
        initialize(app)
        if (!isEnabled(app)) return
        // Um reinício manual no meio da partida encerra a tentativa anterior como abandono.
        if (game.abandon()) append(app, "sf_game_abandoned", sessionId, game.gameId.orEmpty(), game.lastScore)
        val newGameId = newId()
        game.start(newGameId, System.currentTimeMillis())
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OPEN_GAME, newGameId).commit()
        append(app, "sf_game_started", sessionId, newGameId, 0)
    }

    @Synchronized
    fun scoreChanged(context: Context, score: Int) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val changed = game.score(score) ?: return
        val (old, latest) = changed
        val gameId = game.gameId.orEmpty()
        if (TelemetryRules.isCheckpoint(old, latest)) {
            append(app, "sf_score_checkpoint", sessionId, gameId, latest)
        }
        TelemetryRules.newlyReachedMilestones(old, latest).forEach { threshold ->
            append(app, "sf_medal_reached", sessionId, gameId, threshold)
        }
    }

    @Synchronized
    fun gameFinished(context: Context, finalScore: Int) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        if (!game.finish(finalScore)) return
        val duration = (System.currentTimeMillis() - game.startedAtMs)
            .coerceAtLeast(0L) / 1000L
        append(
            app, "sf_game_finished", sessionId, game.gameId.orEmpty(), game.lastScore,
            "seconds:${duration.coerceAtMost(86400L)}"
        )
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_OPEN_GAME).commit()
        scheduleSync(app)
    }

    @Synchronized
    fun revived(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app) || !game.canRevive()) return
        append(app, "sf_game_revived", sessionId, game.gameId.orEmpty(), game.lastScore)
    }

    @Synchronized
    fun skillSelected(context: Context, skill: String) {
        val app = context.applicationContext
        if (!isEnabled(app) || game.gameId == null) return
        append(app, "sf_skill_selected", sessionId, game.gameId.orEmpty(), 0, skill)
    }

    @Synchronized
    fun lumisGranted(context: Context, awardedCentilumis: Int) {
        val app = context.applicationContext
        if (!isEnabled(app) || awardedCentilumis <= 0) return
        // Apenas análise: este evento nunca altera nem valida o saldo de Lumis.
        append(app, "sf_lumis_earned", sessionId, game.gameId.orEmpty(), awardedCentilumis)
    }

    private fun append(
        context: Context,
        type: String,
        session: String,
        game: String,
        value: Int,
        detail: String = ""
    ) {
        val event = TelemetryRecord(
            id = newId(),
            type = type,
            sessionId = session,
            gameId = game,
            createdAtMs = System.currentTimeMillis(),
            value = value,
            detail = detail
        )
        try {
            if (store?.append(event) == true) {
                logAnalytics(context, type, value, detail)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Falha local de telemetria; nao bloquear jogabilidade", error)
        }
    }

    /** Reenvio no inicio, a cada partida encerrada e quando o app vai ao fundo. */
    fun scheduleSync(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app) || remoteDeletionPending(app)) return
        try {
            val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                SYNC_NAME, ExistingWorkPolicy.KEEP, request
            )
        } catch (error: Exception) {
            Log.w(TAG, "Nao foi possivel agendar envio", error)
        }
    }

    fun remoteDeletionPending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DELETE_REMOTE, false)

    fun deletionOwnerUid(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("pending_delete_uid", null)

    /** Ao trocar UID, eventos analíticos não enviados não podem migrar de conta. */
    @Synchronized
    fun prepareForAccountChange(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(SYNC_NAME)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (remoteDeletionPending(app) && deletionOwnerUid(app) == null) {
            // Vínculo de exclusão pendente criada numa versão anterior.
            runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()?.let { uid ->
                prefs.edit().putString("pending_delete_uid", uid).commit()
            }
        }
        store?.purgeAll()
        inForeground = false
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_OPEN_GAME).commit()
        game.abandon()
        sessionId = ""
    }

    fun remoteDeletionFinished(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DELETE_REMOTE, false)
            .remove("pending_delete_uid").commit()
        scheduleSync(context)
    }

    private fun scheduleRemoteDeletion(context: Context) {
        if (!FirebaseApp.getApps(context).any()) return
        val request = OneTimeWorkRequestBuilder<TelemetryDeleteWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DELETE_NAME, ExistingWorkPolicy.KEEP, request
        )
    }

    private fun logAnalytics(context: Context, type: String, value: Int, detail: String) {
        if (!FirebaseApp.getApps(context).any() || !isEnabled(context)) return
        try {
            val params = Bundle().apply {
                putLong("numeric_value", value.toLong())
                if (type == "sf_skill_selected") putString("skill_type", detail.take(40))
            }
            FirebaseAnalytics.getInstance(context).logEvent(type, params)
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao registrar evento Analytics", error)
        }
    }

    private fun applyAnalyticsCollection(context: Context, enabled: Boolean) {
        if (!FirebaseApp.getApps(context).any()) return
        runCatching { FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled) }
            .onFailure { Log.w(TAG, "Analytics indisponivel", it) }
    }

    private fun applyCrashCollection(context: Context, enabled: Boolean) {
        if (!FirebaseApp.getApps(context).any()) return
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { Log.w(TAG, "Crashlytics indisponivel", it) }
    }

    private fun deviceDescription(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

    private fun newId(): String = UUID.randomUUID().toString()
}
