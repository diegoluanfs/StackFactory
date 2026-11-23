package com.diegoluan.stackfactory.referral

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Captura a origem da instalação, sem bloquear o início do jogo. */
object ReferralAttribution {
    private const val TAG = "SFReferral"
    private const val PREFS = "sf_install_referral_v1"
    const val PENDING_CODE = "pending_code"
    const val INSTALL_BEGIN_MS = "install_begin_ms"
    const val ACCOUNT_ATTRIBUTED = "attributed_uid"
    const val CAPTURE_COMPLETE = "capture_complete"
    const val DEBUG_SIMULATION = "debug_simulation"
    const val BOUND_FIRST_UID = "bound_first_uid"
    const val ERROR = "last_error"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Executar uma vez na inicialização do aplicativo. */
    fun captureFromGooglePlay(context: Context, launchIntent: Intent? = null) {
        val app = context.applicationContext
        // Caminho de teste: APENAS APKs debuggable. Nao modifica builds de release.
        val debugBuild = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debugBuild) {
            val debugCode = ReferralInstallParser.parseDebugCode(
                launchIntent?.getStringExtra("sf_debug_referral_code")
            )
            if (debugCode != null && prefs(app).getString(ACCOUNT_ATTRIBUTED, null) == null) {
                prefs(app).edit()
                    .putString(PENDING_CODE, debugCode)
                    .putLong(INSTALL_BEGIN_MS, System.currentTimeMillis())
                    .putBoolean(CAPTURE_COMPLETE, true)
                    .putBoolean(DEBUG_SIMULATION, true)
                    .apply()
                Log.i(TAG, "Código de teste armazenado; não é prova de instalação pela Play")
                onAuthenticated(app, if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAuth.getInstance().currentUser?.uid else null)
                return
            }
        }

        if (prefs(app).getBoolean(CAPTURE_COMPLETE, false)) return
        val client = InstallReferrerClient.newBuilder(app).build()
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        thread(name = "sf-install-referrer", isDaemon = true) {
                            try {
                                val details = client.installReferrer
                                val now = System.currentTimeMillis()
                                val installMs = details.installBeginTimestampSeconds * 1000L
                                val code = ReferralInstallParser.parsePlayReferrer(details.installReferrer)
                                if (!prefs(app).getBoolean(CAPTURE_COMPLETE, false)) {
                                    val editor = prefs(app).edit()
                                        .putBoolean(CAPTURE_COMPLETE, true)
                                        .putBoolean(DEBUG_SIMULATION, false)
                                    if (code != null && ReferralInstallParser.isRecentInstall(installMs, now)) {
                                        editor.putString(PENDING_CODE, code)
                                            .putLong(INSTALL_BEGIN_MS, installMs)
                                    }
                                    editor.apply()
                                }
                                onAuthenticated(app, if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseAuth.getInstance().currentUser?.uid else null)
                            } catch (error: Exception) {
                                Log.w(TAG, "Install Referrer indisponível; tentaremos novamente", error)
                            } finally {
                                runCatching { client.endConnection() }
                            }
                        }
                    } else {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED) {
                            prefs(app).edit().putBoolean(CAPTURE_COMPLETE, true).apply()
                        }
                        Log.i(TAG, "Serviço Play Install Referrer: resposta $responseCode")
                        runCatching { client.endConnection() }
                    }
                }
                override fun onInstallReferrerServiceDisconnected() {
                    Log.d(TAG, "Serviço de origem da instalação desconectado")
                }
            })
        } catch (error: Exception) {
            Log.w(TAG, "Play Install Referrer não disponível", error)
            runCatching { client.endConnection() }
        }
    }

    /** Chamado quando a identidade Firebase estiver disponível ou mudar. */
    fun onAuthenticated(context: Context, uid: String?) {
        if (uid.isNullOrBlank() || FirebaseApp.getApps(context).isEmpty()) return
        val app = context.applicationContext
        val request = OneTimeWorkRequestBuilder<ReferralAttributionWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "sf-referrals-$uid", ExistingWorkPolicy.KEEP, request
        )
    }

    fun pendingCode(context: Context): String? = prefs(context).getString(PENDING_CODE, null)
    fun installBeginMs(context: Context): Long = prefs(context).getLong(INSTALL_BEGIN_MS, 0L)
    fun attributedUid(context: Context): String? = prefs(context).getString(ACCOUNT_ATTRIBUTED, null)
    fun debugSimulated(context: Context): Boolean = prefs(context).getBoolean(DEBUG_SIMULATION, false)

    /** Evita transferir o convite a uma segunda conta criada na mesma instalação. */
    @Synchronized
    fun bindToFirstUid(context: Context, uid: String): Boolean {
        val stored = prefs(context).getString(BOUND_FIRST_UID, null)
        if (stored != null) return stored == uid
        prefs(context).edit().putString(BOUND_FIRST_UID, uid).apply()
        return true
    }

    fun markAttributed(context: Context, uid: String) {
        prefs(context).edit().putString(ACCOUNT_ATTRIBUTED, uid)
            .remove(PENDING_CODE).remove(ERROR).apply()
    }

    fun clearPending(context: Context, reason: String) {
        prefs(context).edit().remove(PENDING_CODE).putString(ERROR, reason).apply()
    }

    fun saveError(context: Context, reason: String) {
        prefs(context).edit().putString(ERROR, reason.take(120)).apply()
    }
}
