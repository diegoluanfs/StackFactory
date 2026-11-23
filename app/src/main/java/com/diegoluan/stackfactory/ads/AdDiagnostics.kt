package com.diegoluan.stackfactory.ads

import android.util.Log
import com.google.android.gms.ads.AdValue

/** Telemetria local e não identificável. Receita consolidada: painel do AdMob. */
internal object AdDiagnostics {
    fun event(format: String, action: String, details: String = "") {
        Log.d("StackFactoryAds", "$format | $action $details")
    }

    fun paid(format: String, value: AdValue) {
        // Valor por evento em micros da moeda informada pelo próprio SDK.
        // Nunca tratar esse valor como saldo disponível nem somar moedas diferentes.
        Log.d(
            "StackFactoryAds",
            "$format | paid | micros=${value.valueMicros} " +
                "currency=${value.currencyCode} precision=${value.precisionType}"
        )
    }
}
