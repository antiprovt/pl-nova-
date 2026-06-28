package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val dateString = intent.getStringExtra("date") ?: ""
            val noteText = intent.getStringExtra("note") ?: ""
            val reminderTime = intent.getStringExtra("reminder_time") ?: ""

            createNotificationChannel(context)

            val soundUri = try {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } catch (e: Exception) {
                null
            }

            // Intent to launch MainActivity when clicking the notification
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "Úloha"
            val timeRegex = Regex("^(\\d{2}):(\\d{2})")
            val matchResult = timeRegex.find(reminderTime)
            val justTime = matchResult?.value ?: ""
            val taskText = reminderTime.replaceFirst(Regex("^\\d{2}:\\d{2}\\s*-\\s*"), "")

            val message = if (justTime.isNotEmpty()) {
                "Dnes o $justTime: $taskText"
            } else {
                "Máte naplánovanú úlohu: $reminderTime"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .apply {
                    if (soundUri != null) {
                        setSound(soundUri)
                    }
                }
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val notificationId = dateString.hashCode()
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "shift_scheduler_reminders_channel"

        fun createNotificationChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = "Úlohy"
                    val descriptionText = "Upozornenia na úlohy k službám"
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        val soundUri = try {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        } catch (e: Exception) {
                            null
                        }
                        if (soundUri != null) {
                            val audioAttributes = AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .build()
                            setSound(soundUri, audioAttributes)
                        }
                        enableVibration(true)
                    }
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.createNotificationChannel(channel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @SuppressLint("ScheduleExactAlarm")
        fun scheduleReminder(context: Context, date: LocalDate, timeText: String, note: String?) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                
                // Parse timeText: "HH:mm"
                val timeRegex = Regex("^(\\d{2}):(\\d{2})")
                val matchResult = timeRegex.find(timeText) ?: return
                val hour = matchResult.groupValues[1].toIntOrNull() ?: return
                val minute = matchResult.groupValues[2].toIntOrNull() ?: return

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.YEAR, date.year)
                    set(Calendar.MONTH, date.monthValue - 1)
                    set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Only schedule if the alarm time is in the future
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    return
                }

                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("date", date.toString())
                    putExtra("note", note ?: "")
                    putExtra("reminder_time", timeText)
                }

                val requestCode = (date.toEpochDay() % Int.MAX_VALUE).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val canExact = try {
                        alarmManager.canScheduleExactAlarms()
                    } catch (e: Exception) {
                        false
                    }
                    if (canExact) {
                        try {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        } catch (se: SecurityException) {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } catch (e: Exception) {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelReminder(context: Context, date: LocalDate) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, ReminderReceiver::class.java)
                val requestCode = (date.toEpochDay() % Int.MAX_VALUE).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun showNotification(context: Context, title: String, message: String, notificationId: Int = System.currentTimeMillis().hashCode()) {
            try {
                createNotificationChannel(context)
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(notificationId, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
