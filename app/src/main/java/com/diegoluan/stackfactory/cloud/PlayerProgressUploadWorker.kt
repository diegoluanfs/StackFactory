package com.diegoluan.stackfactory.cloud

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/** Sync de dados não financeiros. Nunca credita ou restaura Lumis na nuvem. */
class PlayerProgressUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        if (FirebaseApp.getApps(applicationContext).isEmpty()) return Result.retry()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.retry()
        val progress = PlayerProgressCloud(applicationContext)
        if (!progress.canSync(uid)) return Result.success() // outro perfil: não mesclar
        val db = ProgressOutbox(applicationContext)
        try {
            val firestore = FirebaseFirestore.getInstance()
            val player = firestore.collection("players").document(uid)
            val games = db.pending(uid)
            games.forEach { game ->
                if (isStopped || FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.retry()
                val reference = player.collection("games").document(game.id)
                val data = mapOf<String, Any>(
                    "uid" to uid,
                    "game_id" to game.id,
                    "score" to game.score,
                    "started_ms" to game.startedMs,
                    "completed_ms" to game.finishedMs,
                    "source" to "client_unverified"
                )
                // O ID de partida é fixo; uma tentativa repetida não cria outro documento.
                Tasks.await(firestore.runTransaction { transaction ->
                    if (!transaction.get(reference).exists()) transaction.set(reference, data)
                    true
                }, 30, TimeUnit.SECONDS)
                db.markUploaded(game.id)
            }
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.retry()
            val backup = PlayerProgressCloud.captureSnapshot(applicationContext, uid, progress.deviceId)
            Tasks.await(
                player.collection("device_backups").document(progress.deviceId).set(backup),
                30, TimeUnit.SECONDS
            )
            return if (games.size == 25 && db.pending(uid, 1).isNotEmpty()) Result.retry()
                else Result.success()
        } catch (error: Exception) {
            Log.w("StackProgress", "Backup adiado: dados locais preservados", error)
            return Result.retry()
        } finally {
            db.close()
        }
    }
}
