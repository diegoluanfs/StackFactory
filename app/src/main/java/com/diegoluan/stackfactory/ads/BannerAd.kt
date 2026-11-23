package com.diegoluan.stackfactory.ads

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

/** Altura reservada conforme o tamanho real do banner adaptativo do Google. */
fun bannerHeightDp(context: Context, widthDp: Int): Int =
    AdSize.getLargeAnchoredAdaptiveBannerAdSize(
        context, widthDp.coerceAtLeast(1)
    ).height.coerceAtLeast(50)

/** Banner único no rodapé do menu, com descarte ao sair da tela. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
    var failedLoads by remember { mutableIntStateOf(0) }
    val adView = remember(context, widthDp) {
        AdView(context).apply {
            adUnitId = AdConfig.BANNER_UNIT_ID
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    failedLoads = 0
                    AdDiagnostics.event("banner", "loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failedLoads++
                    AdDiagnostics.event("banner", "load_failed", "code=${error.code}")
                }
                override fun onAdImpression() {
                    AdDiagnostics.event("banner", "impression")
                }
            }
            setOnPaidEventListener { value -> AdDiagnostics.paid("banner", value) }
        }
    }

    DisposableEffect(adView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        adView.loadAd(AdRequest.Builder().build())
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }
    LaunchedEffect(adView, failedLoads) {
        if (failedLoads > 0) {
            delay(90_000L)
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
