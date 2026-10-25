package com.diegoluan.stackfactory.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** Só mostra anúncios depois que o jogador decide sair da tela de derrota. */
class AdManager(private val activity: Activity) {
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var finishedGames = 0
    private var due = false
    private var showing = false

    fun loadInterstitial() {
        if (loading || interstitial != null || showing) return
        loading = true
        InterstitialAd.load(
            activity,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w("StackFactoryAds", "Interstitial indisponível: ${error.message}")
                }
            }
        )
    }

    fun recordGameFinished() {
        finishedGames++
        if (finishedGames % AdConfig.GAMES_BETWEEN_ADS == 0) due = true
    }

    fun showIfDue(proceed: () -> Unit) {
        if (!due || showing) {
            if (!showing) proceed()
            return
        }
        due = false
        val ad = interstitial
        interstitial = null
        if (ad == null) {
            proceed()
            loadInterstitial()
            return
        }
        showing = true
        var didProceed = false
        fun complete() {
            if (didProceed) return
            didProceed = true
            showing = false
            proceed()
            loadInterstitial()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = complete()
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = complete()
        }
        try { ad.show(activity) } catch (exception: Exception) {
            Log.w("StackFactoryAds", "Erro ao exibir interstitial", exception)
            complete()
        }
    }

    fun clear() {
        interstitial = null
        due = false
    }
}
