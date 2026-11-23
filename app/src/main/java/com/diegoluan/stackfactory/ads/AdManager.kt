package com.diegoluan.stackfactory.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** Intersticiais apenas em transições naturais, nunca no meio da partida. */
class AdManager(
    private val activity: Activity,
    private val onAdStarted: () -> Unit = {},
    private val onAdFinished: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var enabled = false
    private var showing = false
    private var finishedGames = 0
    private var due = false
    private var lastShowAt = 0L
    private var loadedAt = 0L
    private var retries = 0
    private var epoch = 0
    private var retry: Runnable? = null

    private val minimumIntervalMs = 90_000L
    private val expirationMs = 55 * 60_000L

    fun loadInterstitial() {
        enabled = true // Somente a MainActivity deve chamar após canRequestAds().
        if (loading || showing || activity.isFinishing || activity.isDestroyed) return
        if (interstitial != null && !isExpired()) return
        interstitial = null
        retry?.let(handler::removeCallbacks)
        retry = null
        loading = true
        val token = epoch
        AdDiagnostics.event("interstitial", "request")
        InterstitialAd.load(
            activity,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (!enabled || token != epoch) return
                    loading = false
                    retries = 0
                    interstitial = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    ad.setOnPaidEventListener { value ->
                        AdDiagnostics.paid("interstitial", value)
                    }
                    AdDiagnostics.event("interstitial", "loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (token != epoch) return
                    loading = false
                    AdDiagnostics.event("interstitial", "load_failed", "code=${error.code}")
                    scheduleRetry()
                }
            }
        )
    }

    private fun scheduleRetry() {
        if (!enabled || retry != null || activity.isDestroyed) return
        retries = (retries + 1).coerceAtMost(5)
        val delayMs = (15_000L shl (retries - 1)).coerceAtMost(180_000L)
        val task = Runnable { retry = null; if (enabled) loadInterstitial() }
        retry = task
        handler.postDelayed(task, delayMs)
    }

    private fun isExpired() =
        SystemClock.elapsedRealtime() - loadedAt >= expirationMs

    fun recordGameFinished() {
        finishedGames++
        if (finishedGames >= AdConfig.GAMES_BETWEEN_ADS) {
            due = true
            if (enabled) loadInterstitial()
        }
    }

    fun showIfDue(proceed: () -> Unit) {
        if (showing) return
        val elapsed = SystemClock.elapsedRealtime() - lastShowAt
        if (!enabled || !due ||
            (lastShowAt != 0L && elapsed < minimumIntervalMs)
        ) {
            proceed()
            return
        }
        if (interstitial != null && isExpired()) interstitial = null
        val ad = interstitial ?: run {
            loadInterstitial()
            proceed() // Nunca mantém o jogador bloqueado sem anúncio.
            return
        }
        interstitial = null
        showing = true
        var completed = false
        var started = false
        fun finish() {
            if (completed) return
            completed = true
            showing = false
            if (started) onAdFinished()
            if (enabled) loadInterstitial()
            proceed()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                started = true
                due = false
                finishedGames = 0
                lastShowAt = SystemClock.elapsedRealtime()
                onAdStarted()
                AdDiagnostics.event("interstitial", "shown")
            }
            override fun onAdImpression() {
                AdDiagnostics.event("interstitial", "impression")
            }
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdDiagnostics.event("interstitial", "show_failed", "code=${error.code}")
                finish()
            }
        }
        try {
            ad.show(activity)
        } catch (exception: Exception) {
            AdDiagnostics.event("interstitial", "show_exception")
            finish()
        }
    }

    fun clear() {
        epoch++
        enabled = false
        retry?.let(handler::removeCallbacks)
        retry = null
        interstitial = null
        showing = false
        loading = false
        due = false
        retries = 0
    }
}
