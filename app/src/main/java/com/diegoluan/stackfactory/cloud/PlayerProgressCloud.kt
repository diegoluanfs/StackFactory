package com.diegoluan.stackfactory.cloud

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.diegoluan.stackfactory.checkin.FiveDayCheckInRepository
import com.diegoluan.stackfactory.lumis.LumisRepository
import com.diegoluan.stackfactory.daily.DailyMissionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Progresso independente do consentimento de telemetria.
 * Os registros remotos são BACKUP NÃO VERIFICADO e não dão direito a Lumis.
 * O banco/contador local continua sendo a origem da jogabilidade.
 */
class PlayerProgressCloud(private val context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sf_cloud_progress_v1", Context.MODE_PRIVATE)
    private val db = ProgressOutbox(app)

    val deviceId: String get() {
        val existing = prefs.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", id).commit()
        return id
    }

    /**
     * Proteção contra copiar silenciosamente o progresso local de um visitante
     * para um UID Google que já possuía conta. A recuperação/merge exige decisão
     * explícita e regras de negócio numa versão futura.
     */
    @Synchronized
    fun canSync(uid: String): Boolean {
        val owner = prefs.getString("local_owner_uid", null)
        if (!ProgressOwnership.mayUpload(owner, uid)) return false
        if (owner == null) {
            prefs.edit().putString("local_owner_uid", uid).commit()
            return true
        }
        return owner == uid
    }

    fun beginGame(uid: String?) {
        if (uid == null || !canSync(uid)) return
        prefs.edit()
            .putString("active_game_uid", uid)
            .putString("active_game_id", UUID.randomUUID().toString())
            .putLong("active_game_started_ms", System.currentTimeMillis())
            .commit()
    }

    @Synchronized
    fun finishGame(uid: String?, score: Int) {
        if (uid == null || !canSync(uid)) return
        if (prefs.getString("active_game_uid", null) != uid) return
        val id = prefs.getString("active_game_id", null) ?: return
        val started = prefs.getLong("active_game_started_ms", System.currentTimeMillis())
        val completed = System.currentTimeMillis()
        // UNIQUE(game_id) + INSERT OR IGNORE previnem duplicidade após callback/retry.
        val recorded = db.addCompletedGame(uid, id, score.coerceAtLeast(0), started, completed)
        if (recorded) {
            prefs.edit().remove("active_game_uid").remove("active_game_id")
                .remove("active_game_started_ms").commit()
        }
        schedule(uid)
    }

    fun schedule(uid: String?) {
        if (uid == null || !canSync(uid)) return
        try {
            val work = OneTimeWorkRequestBuilder<PlayerProgressUploadWorker>()
                .setInputData(workDataOf("uid" to uid))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                "sf_progress_upload_$uid", ExistingWorkPolicy.KEEP, work
            )
        } catch (error: Exception) {
            Log.w("StackProgress", "Falha ao agendar backup", error)
        }
    }

    /** Restaura somente o RECORD local; não importa carteira/medalhas não verificadas. */
    fun restoreHighScore(uid: String, callback: (String) -> Unit) {
        if (!canSync(uid) || FirebaseAuth.getInstance().currentUser?.uid != uid) {
            callback("Há progresso local de outro perfil. Nenhum dado foi mesclado.")
            return
        }
        FirebaseFirestore.getInstance().collection("players").document(uid)
            .collection("device_backups").get(Source.SERVER)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    callback("Sem conexão com o Firestore para consultar o backup.")
                    return@addOnCompleteListener
                }
                val fromCloud = task.result?.documents.orEmpty().maxOfOrNull {
                    it.getLong("best_score")?.toInt() ?: 0
                } ?: 0
                val settings = app.getSharedPreferences("stack_factory", Context.MODE_PRIVATE)
                val local = settings.getInt("high_score", 0)
                val best = maxOf(local, fromCloud)
                if (best > local) settings.edit().putInt("high_score", best).commit()
                callback("Recorde disponível: $best blocos. Lumis e medalhas não foram alterados.")
            }
    }

    companion object {
        internal fun captureSnapshot(context: Context, uid: String, deviceId: String): Map<String, Any> {
            val bestScore = context.getSharedPreferences("stack_factory", Context.MODE_PRIVATE)
                .getInt("high_score", 0).coerceIn(0, 1000000)
            val checkin = FiveDayCheckInRepository(context).current()
            val lumis = LumisRepository(context).snapshot(checkin.dayKey)
            val daily = DailyMissionManager(context).today()
            return mapOf(
                "uid" to uid,
                "device_id" to deviceId,
                "schema" to 1,
                "best_score" to bestScore,
                "daily_score" to checkin.bestScoreToday.coerceIn(0, 1000000),
                "daily_medal" to checkin.todayMedal.name,
                "daily_key" to checkin.dayKey,
                "mission_progress" to daily.progress.coerceIn(0, 1000000),
                "mission_target" to daily.target.coerceIn(0, 1000000),
                "streak_days" to checkin.consecutiveDays.coerceIn(0, 1000000),
                "week_bronze_days" to checkin.weekBronzeDays,
                "week_silver_days" to checkin.weekSilverDays,
                "week_gold_days" to checkin.weekGoldDays,
                "client_lumis_units_unverified" to lumis.balanceUnits.coerceIn(0, 100000000),
                "client_updated_at_ms" to System.currentTimeMillis()
            )
        }
    }
}

internal data class PendingGame(
    val uid: String, val id: String, val score: Int,
    val startedMs: Long, val finishedMs: Long
)

/** Banco local separado do SQLite da carteira e da telemetria opcional. */
internal class ProgressOutbox(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "stackfactory_cloud_progress_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE completed_games(" +
                "game_id TEXT PRIMARY KEY NOT NULL, owner_uid TEXT NOT NULL, " +
                "score INTEGER NOT NULL, started_ms INTEGER NOT NULL, " +
                "completed_ms INTEGER NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX ix_pending_games ON completed_games(owner_uid, uploaded)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nunca eliminar dados locais para atualizar o schema.
    }

    @Synchronized
    fun addCompletedGame(uid: String, id: String, score: Int, started: Long, finished: Long): Boolean {
        val values = ContentValues().apply {
            put("game_id", id)
            put("owner_uid", uid)
            put("score", score)
            put("started_ms", started)
            put("completed_ms", finished)
        }
        val result = writableDatabase.insertWithOnConflict(
            "completed_games", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return result != -1L || isRecorded(id)
    }

    private fun isRecorded(id: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM completed_games WHERE game_id = ? LIMIT 1", arrayOf(id)
    ).use { it.moveToFirst() }

    @Synchronized
    fun pending(uid: String, limit: Int = 25): List<PendingGame> {
        val data = mutableListOf<PendingGame>()
        readableDatabase.rawQuery(
            "SELECT owner_uid, game_id, score, started_ms, completed_ms FROM completed_games " +
                "WHERE owner_uid = ? AND uploaded = 0 ORDER BY completed_ms LIMIT ?",
            arrayOf(uid, limit.coerceIn(1, 25).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                data.add(PendingGame(cursor.getString(0), cursor.getString(1),
                    cursor.getInt(2), cursor.getLong(3), cursor.getLong(4)))
            }
        }
        return data
    }

    @Synchronized
    fun markUploaded(id: String) {
        val values = ContentValues().apply { put("uploaded", 1) }
        writableDatabase.update("completed_games", values, "game_id = ?", arrayOf(id))
    }
}
