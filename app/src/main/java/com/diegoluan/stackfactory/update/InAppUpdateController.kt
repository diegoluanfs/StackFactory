
package com.diegoluan.stackfactory.update

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class InAppUpdateController(
    private val activity: ComponentActivity
) {
    private val manager = AppUpdateManagerFactory.create(activity)

    private val updateLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            // Cancelamento/falha: o aplicativo permanece utilizável.
            // Uma atualização crítica poderá ter tratamento adicional.
        }

    fun checkForUpdates() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability()
                == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(
                        AppUpdateType.IMMEDIATE
                    ).build()
                )
            }
        }
    }

    fun resumeUpdateIfNeeded() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability()
                == UpdateAvailability
                    .DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(
                        AppUpdateType.IMMEDIATE
                    ).build()
                )
            }
        }
    }
}
