package com.diegoluan.stackfactory.referral

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit

/**
 * Registra atribuições em documentos imutáveis.
 * - Nenhum Lumis, saldo, medalha ou recompensa é criado aqui.
 * - O resultado é 'client_unverified': as regras verificam proprietário,
 *   existência da referência e não duplicação POR UID, não legitimidade da instalação.
 * - Operações de rede ficam fora da thread de interface.
 */
class ReferralAttributionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val tag = "SFReferral"

    override fun doWork(): Result {
        val app = applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) return Result.success()
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val uid = user.uid
        val database = FirebaseFirestore.getInstance()
        val ownCode = ReferralLinks.codeForUid(uid)
        try {
            // Cadastrar o código do indicador antes de tentar atribuir a instalação.
            // O documento é imutável e vinculado ao UID de quem o publicou.
            val ownRef = database.collection("referral_codes").document(ownCode)
            val own = Tasks.await(ownRef.get(Source.SERVER), 20, TimeUnit.SECONDS)
            if (!own.exists()) {
                Tasks.await(ownRef.set(mapOf(
                    "uid" to uid,
                    "code" to ownCode,
                    "schema" to 1,
                    "created_at" to FieldValue.serverTimestamp()
                )), 20, TimeUnit.SECONDS)
            } else if (own.getString("uid") != uid) {
                ReferralAttribution.saveError(app, "Código de convite indisponível")
                return Result.failure()
            }

            val pending = ReferralAttribution.pendingCode(app) ?: return Result.success()
            if (ReferralAttribution.attributedUid(app) != null) return Result.success()
            if (pending == ownCode) {
                ReferralAttribution.clearPending(app, "Convite próprio ignorado")
                return Result.success()
            }
            val installBegin = ReferralAttribution.installBeginMs(app)
            val now = System.currentTimeMillis()
            val created = user.metadata?.creationTimestamp ?: 0L
            if (created <= 0L) return Result.retry()
            if (!ReferralInstallParser.canAttributeToNewAccount(installBegin, created, now)) {
                ReferralAttribution.clearPending(app, "Conta existente ou instalação antiga")
                return Result.success()
            }

            if (!ReferralAttribution.bindToFirstUid(app, uid)) {
                ReferralAttribution.saveError(app, "Convite vinculado a outra conta nesta instalação")
                return Result.success()
            }

            val dest = database.collection("referral_attributions").document(uid)
            val already = Tasks.await(dest.get(Source.SERVER), 20, TimeUnit.SECONDS)
            if (already.exists()) {
                // O UID de destino só pode ter uma indicação, mesmo em outra instalação.
                ReferralAttribution.markAttributed(app, uid)
                return Result.success()
            }
            val inviter = Tasks.await(
                database.collection("referral_codes").document(pending).get(Source.SERVER),
                20, TimeUnit.SECONDS
            )
            if (!inviter.exists()) {
                // Pode ser um convite válido cujo emissor ainda não abriu a versão nova.
                ReferralAttribution.saveError(app, "Aguardando código do indicador")
                return Result.retry()
            }
            val referrerUid = inviter.getString("uid")
            if (referrerUid.isNullOrBlank() || referrerUid == uid) {
                ReferralAttribution.clearPending(app, "Código de indicação inválido")
                return Result.success()
            }
            // Regras Firestore fazem a verificação da relação entre código e UID.
            Tasks.await(dest.set(mapOf(
                "referred_uid" to uid,
                "referrer_uid" to referrerUid,
                "referral_code" to pending,
                "source" to if (ReferralAttribution.debugSimulated(app))
                    "debug_simulated_unverified" else "play_install_referrer_unverified",
                "schema" to 1,
                "created_at" to FieldValue.serverTimestamp()
            )), 20, TimeUnit.SECONDS)
            ReferralAttribution.markAttributed(app, uid)
            Log.i(tag, "Indicação registrada com sucesso (não verificada para recompensa)")
            return Result.success()
        } catch (error: Exception) {
            val cause = generateSequence(error as Throwable?) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            val status = cause?.code
            val retry = status == null || status in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                FirebaseFirestoreException.Code.CANCELLED
            )
            Log.w(tag, "Registro da indicação pendente: ${status ?: error.javaClass.simpleName}", error)
            ReferralAttribution.saveError(app, "Falha ao registrar: ${status ?: "rede"}")
            return if (retry) Result.retry() else Result.failure()
        }
    }
}
