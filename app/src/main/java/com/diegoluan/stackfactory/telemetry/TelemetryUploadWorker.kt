package com.diegoluan.stackfactory.telemetry

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/**
 * Gravação remota por evento imutável: players/{anonymousUid}/telemetry/{eventId}.
 * Uma transação não substitui documentos existentes, e a fila só dá baixa depois
 * de confirmação do Firestore. As Security Rules devem ser publicadas antes do uso.
 */
class TelemetryUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        if (!StackTelemetry.isEnabled(context)) return Result.success()
        if (StackTelemetry.remoteDeletionPending(context)) return Result.retry()
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "Firebase nao configurado; fila mantida no aparelho")
            return Result.success()
        }
        val db = TelemetryDatabase(context)
        try {
            val auth = FirebaseAuth.getInstance()
            // Login é escolha do jogador. O Worker nunca cria visitante silenciosamente.
            val uid = auth.currentUser?.uid ?: return Result.retry()
            val firestore = FirebaseFirestore.getInstance()
            val events = db.pending(limit = 30)
            if (events.isEmpty()) {
                db.purgeUploaded()
                return Result.success()
            }

            events.forEach { event ->
                if (isStopped || !StackTelemetry.isEnabled(context) ||
                    auth.currentUser?.uid != uid
                ) return Result.retry()
                val reference = firestore.collection("players").document(uid)
                    .collection("telemetry").document(event.id)
                val payload = mapOf<String, Any>(
                    "event_id" to event.id,
                    "type" to event.type,
                    "session_id" to event.sessionId,
                    "game_id" to event.gameId,
                    "client_at_ms" to event.createdAtMs,
                    "numeric_value" to event.value.toLong(),
                    "detail" to event.detail
                )
                // Transação atômica: no retry com o mesmo ID, não duplica a escrita.
                Tasks.await(firestore.runTransaction { transaction ->
                    if (!transaction.get(reference).exists()) {
                        transaction.set(reference, payload)
                    }
                    true
                }, 30, TimeUnit.SECONDS)
                db.uploaded(event.id)
            }
            db.purgeUploaded()
            return if (events.size == 30 && db.pending(1).isNotEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Envio adiado; registros preservados", error)
            return Result.retry()
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TAG = "StackTelemetryUpload"
    }
}
