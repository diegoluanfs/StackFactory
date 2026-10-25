package com.diegoluan.stackfactory.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object MissionReminderScheduler {
    private const val LUNCH = "mission_lunch"
    private const val EVENING = "mission_evening"

    // Compatibilidade com o MissionReminderWorker existente.
    // Não existe mais preferência interna para desligar os lembretes.
    // O Android ainda pode bloquear a exibição das notificações.
    @Suppress("UNUSED_PARAMETER")
    fun isEnabled(context: Context): Boolean = true

    fun schedule(context: Context) {
        // KEEP evita substituir lembretes já agendados em toda abertura.
        scheduleSlot(context, LUNCH, 12, 10, ExistingWorkPolicy.KEEP)
        scheduleSlot(context, EVENING, 19, 0, ExistingWorkPolicy.KEEP)
    }

    internal fun rescheduleSlot(context: Context, slot: String) {
        when (slot) {
            LUNCH -> scheduleSlot(context, LUNCH, 12, 10)
            EVENING -> scheduleSlot(context, EVENING, 19, 0)
        }
    }

    private fun scheduleSlot(
        context: Context, name: String, hour: Int, minute: Int,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(1000L)
        val request = OneTimeWorkRequestBuilder<MissionReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.workDataOf("slot" to name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }

}
