package com.diegoluan.stackfactory.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** Pré-carrega o intersticial, mas só o exibe em transições naturais da partida. */
class AdManager(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var enabled = false
    private var showing = false
    private var finishedGames = 0
    private var due = false
    private var lastShowAt = 0L
    private var loadedAt = 0L
    private var retry: Runnable? = null

    // Evita mostrar anúncios em sequência se o jogador perder muito rápido.
    private val minimumIntervalMs = 90_000L
    private val reloadDelayMs = 45_000L
    private val expirationMs = 55 * 60_000L

    fun loadInterstitial() {
        // Este método é chamado pelo MainActivity somente após canRequestAds().
        enabled = true
        if (loading || showing) return
        // Criativos antigos são descartados antes de tentar reutilizá-los.
        if (interstitial != null && SystemClock.elapsedRealtime() - loadedAt < expirationMs) return
        interstitial = null
        retry?.let(handler::removeCallbacks)
        retry = null
        loading = true
        InterstitialAd.load(
            activity,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (!enabled) return
                    loading = false
                    interstitial = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    Log.d("StackFactoryAds", "Intersticial carregado")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w("StackFactoryAds", "Intersticial indisponível: ${error.code} / ${error.message}")
                    if (!enabled) return
                    val task = Runnable { loadInterstitial() }
                    retry = task
                    handler.postDelayed(task, reloadDelayMs)
                }
            }
        )
    }

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
        if (!enabled || !due || (lastShowAt != 0L && elapsed < minimumIntervalMs)) {
            proceed()
            return
        }
        if (interstitial != null && SystemClock.elapsedRealtime() - loadedAt >= expirationMs) {
            interstitial = null
        }
        val ad = interstitial
        if (ad == null) {
            // Não bloqueia o jogador nem perde a oportunidade: fica pendente
            // para a PRÓXIMA transição natural, caso um anúncio carregue.
            loadInterstitial()
            proceed()
            return
        }
        interstitial = null
        showing = true
        var completed = false
        fun finish() {
            if (completed) return
            completed = true
            showing = false
            loadInterstitial()
            proceed()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                due = false
                finishedGames = 0
                lastShowAt = SystemClock.elapsedRealtime()
                Log.d("StackFactoryAds", "Intersticial exibido")
            }
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w("StackFactoryAds", "Falha ao exibir intersticial: ${error.message}")
                finish()
            }
        }
        try { ad.show(activity) } catch (error: Exception) {
            Log.w("StackFactoryAds", "Falha inesperada ao mostrar intersticial", error)
            finish()
        }
    }

    fun clear() {
        enabled = false
        retry?.let(handler::removeCallbacks)
        retry = null
        interstitial = null
        due = false
        loading = false
    }
}
