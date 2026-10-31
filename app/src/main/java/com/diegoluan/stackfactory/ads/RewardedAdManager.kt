package com.diegoluan.stackfactory.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Nunca concede recompensa se o usuário não ganhar o anúncio. */
class RewardedAdManager(private val activity: Activity) {
    private var rewarded: RewardedAd? = null
    private var loading = false
    fun load() {
        if (loading || rewarded != null) return
        loading = true
        RewardedAd.load(activity, AdConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { loading = false; rewarded = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w("StackFactoryAds", "Premiado indisponível: ${error.message}")
                }
            })
    }
    fun show(onEarned: () -> Unit) {
        val ad = rewarded ?: run { load(); return }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { load() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { load() }
        }
        ad.show(activity) {
            if (!earned) { earned = true; onEarned() }
        }
    }
    fun clear() { rewarded = null; loading = false }
}
