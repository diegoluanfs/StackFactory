package com.diegoluan.stackfactory.telemetry

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit

/** Exclusão remota solicitada quando o jogador desativa a telemetria. */
class TelemetryDeleteWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        if (!StackTelemetry.remoteDeletionPending(app)) return Result.success()
        if (FirebaseApp.getApps(app).isEmpty()) return Result.retry()
        try {
            // Não criar outra identidade apenas para excluir dados.
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.retry()
            // Nunca apagar dados do novo perfil por uma solicitação do perfil anterior.
            val owner = StackTelemetry.deletionOwnerUid(app)
            if (owner == null || owner != uid) return Result.retry()
            val collection = FirebaseFirestore.getInstance()
                .collection("players").document(uid).collection("telemetry")
            // Lotes pequenos limitam memória/tempo e permitem retentativa.
            repeat(25) {
                if (isStopped) return Result.retry()
                val snapshot = Tasks.await(collection.limit(100).get(Source.SERVER), 25, TimeUnit.SECONDS)
                if (snapshot.isEmpty) {
                    StackTelemetry.remoteDeletionFinished(app)
                    return Result.success()
                }
                snapshot.documents.forEach { document ->
                    Tasks.await(document.reference.delete(), 25, TimeUnit.SECONDS)
                }
            }
            return Result.retry()
        } catch (error: Exception) {
            Log.w("StackTelemetryDelete", "Exclusao remota adiada", error)
            return Result.retry()
        }
    }
}
