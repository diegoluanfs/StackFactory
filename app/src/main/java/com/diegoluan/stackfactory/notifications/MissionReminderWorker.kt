package com.diegoluan.stackfactory.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.diegoluan.stackfactory.MainActivity
import com.diegoluan.stackfactory.R
import com.diegoluan.stackfactory.daily.DailyMissionManager
import java.time.LocalTime

class MissionReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val slot = inputData.getString("slot") ?: return Result.success()
        if (!MissionReminderScheduler.isEnabled(applicationContext)) return Result.success()
        val time = LocalTime.now()
        val withinWindow = if (slot == "mission_evening") !time.isBefore(LocalTime.of(19, 0))
            else !time.isBefore(LocalTime.of(12, 10)) && time.isBefore(LocalTime.of(19, 0))
        if (!withinWindow) {
            MissionReminderScheduler.rescheduleSlot(applicationContext, slot)
            return Result.success()
        }
        if (DailyMissionManager(applicationContext).today().completed) {
            MissionReminderScheduler.rescheduleSlot(applicationContext, slot)
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            MissionReminderScheduler.rescheduleSlot(applicationContext, slot)
            return Result.success()
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_missions"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Missões diárias", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val evening = slot == "mission_evening"
        val title = if (evening) "Sua missão ainda está esperando!" else "Hora de construir sua torre!"
        val body = if (evening) "Complete a missão de hoje e mantenha sua sequência." else
            "Aproveite o almoço para completar o desafio do StackFactory."
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(if (evening) 1900 else 1210, notification)
        MissionReminderScheduler.rescheduleSlot(applicationContext, slot)
        return Result.success()
    }
}
