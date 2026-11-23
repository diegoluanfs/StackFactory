package com.diegoluan.stackfactory.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Recompensa apenas no callback onUserEarnedReward; anúncio sempre voluntário. */
class RewardedAdManager(
    private val activity: Activity,
    private val onAdStarted: () -> Unit = {},
    private val onAdFinished: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var rewarded: RewardedAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var enabled = false
    private var showing = false
    private var retry: Runnable? = null
    private var retries = 0
    private var epoch = 0

    fun load() {
        enabled = true // MainActivity só chama após aprovação do UMP.
        if (loading || showing || activity.isFinishing || activity.isDestroyed) return
        if (rewarded != null && !expired()) return
        rewarded = null
        retry?.let(handler::removeCallbacks)
        retry = null
        loading = true
        val token = epoch
        AdDiagnostics.event("rewarded", "request")
        RewardedAd.load(
            activity,
            AdConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (!enabled || token != epoch) return
                    loading = false
                    retries = 0
                    loadedAt = SystemClock.elapsedRealtime()
                    rewarded = ad
                    ad.setOnPaidEventListener { value ->
                        AdDiagnostics.paid("rewarded", value)
                    }
                    AdDiagnostics.event("rewarded", "loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (token != epoch) return
                    loading = false
                    AdDiagnostics.event("rewarded", "load_failed", "code=${error.code}")
                    scheduleRetry()
                }
            }
        )
    }

    private fun expired() =
        SystemClock.elapsedRealtime() - loadedAt >= 55 * 60_000L

    private fun scheduleRetry() {
        if (!enabled || retry != null || activity.isDestroyed) return
        retries = (retries + 1).coerceAtMost(5)
        val delayMs = (15_000L shl (retries - 1)).coerceAtMost(180_000L)
        val task = Runnable { retry = null; if (enabled) load() }
        retry = task
        handler.postDelayed(task, delayMs)
    }

    fun show(onEarned: () -> Unit, onUnavailable: () -> Unit = {}) {
        if (showing) return
        if (rewarded != null && expired()) rewarded = null
        val ad = rewarded ?: run {
            if (enabled) load()
            onUnavailable()
            return
        }
        rewarded = null
        showing = true
        var rewardedOnce = false
        var started = false
        var completed = false
        fun finish() {
            if (completed) return
            completed = true
            showing = false
            if (started) onAdFinished()
            if (enabled) load()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                started = true
                onAdStarted()
                AdDiagnostics.event("rewarded", "shown")
            }
            override fun onAdImpression() {
                AdDiagnostics.event("rewarded", "impression")
            }
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdDiagnostics.event("rewarded", "show_failed", "code=${error.code}")
                finish()
                onUnavailable()
            }
        }
        try {
            ad.show(activity) {
                if (!rewardedOnce) {
                    rewardedOnce = true
                    AdDiagnostics.event("rewarded", "earned")
                    onEarned()
                }
            }
        } catch (exception: Exception) {
            AdDiagnostics.event("rewarded", "show_exception")
            finish()
            onUnavailable()
        }
    }

    fun clear() {
        epoch++
        enabled = false
        retry?.let(handler::removeCallbacks)
        retry = null
        rewarded = null
        loading = false
        showing = false
        retries = 0
    }
}
