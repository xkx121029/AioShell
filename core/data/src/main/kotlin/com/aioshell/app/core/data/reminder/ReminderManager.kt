package com.aioshell.app.core.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aioshell.app.core.data.store.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** 定时提醒数据项。 */
data class Reminder(
    val id: String,
    val title: String,
    val content: String = "",
    val triggerAtMs: Long,
    val enabled: Boolean = true,
)

private const val ACTION_REMINDER = "com.aioshell.app.action.REMINDER"
const val NOTIFICATION_CHANNEL_REMINDERS = "reminders_channel"

/**
 * 定时提醒：基于 AlarmManager 的日程提醒。
 * 提醒列表以 JSON 存于偏好存储，每个提醒单独调度一个一次性闹钟。
 */
@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 读取全部提醒。 */
    suspend fun list(): List<Reminder> = parse(settings.remindersRaw.first())

    /** 新增一个提醒并调度。 */
    suspend fun add(title: String, content: String, triggerAtMs: Long): Reminder {
        val reminder = Reminder(id = UUID.randomUUID().toString(), title = title, content = content, triggerAtMs = triggerAtMs)
        val all = list().toMutableList().apply { add(reminder) }
        settings.saveReminders(serialize(all))
        schedule(reminder)
        return reminder
    }

    /** 删除提醒并取消其已排程闹钟。 */
    suspend fun remove(id: String) {
        val target = list().firstOrNull { it.id == id }
        val rest = list().filterNot { it.id == id }
        settings.saveReminders(serialize(rest))
        target?.let { cancel(it) }
    }

    /** 删除已触发的过期提醒（清理）。 */
    suspend fun removeExpired(now: Long = System.currentTimeMillis()) {
        val all = list()
        val alive = all.filter { !it.enabled || it.triggerAtMs >= now }
        if (alive.size != all.size) settings.saveReminders(serialize(alive))
    }

    // ---- 调度 / 取消 ----
    private fun schedule(r: Reminder) {
        val pending = pendingIntent(r)
        // Android 12+ 需要精确闹钟权限才有 setExact；若未授予则退化为 setWindow，保证可用。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, r.triggerAtMs, 5 * 60_000L, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMs, pending)
        }
    }

    private fun cancel(r: Reminder) {
        alarmManager.cancel(pendingIntent(r))
    }

    private fun pendingIntent(r: Reminder): PendingIntent {
        val intent = Intent(ACTION_REMINDER)
            .setPackage(context.packageName)
            .putExtra("id", r.id)
            .putExtra("title", r.title)
            .putExtra("content", r.content)
        return PendingIntent.getBroadcast(
            context, r.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---- JSON 序列化 / 解析 ----
    private fun serialize(list: List<Reminder>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("content", r.content)
                    .put("triggerAtMs", r.triggerAtMs)
                    .put("enabled", r.enabled),
            )
        }
        return arr.toString()
    }

    private fun parse(raw: String?): List<Reminder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    triggerAtMs = o.optLong("triggerAtMs"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 确保通知渠道存在（应用启动时调用一次）。 */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_REMINDERS, "定时提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "AioShell 的定时提醒通知" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/**
 * 接收提醒闹钟广播并显示通知。点击回到应用主界面。
 * 在 AndroidManifest 中注册为静态 receiver。
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val id = intent.getStringExtra("id") ?: return
        // 从管理器读取标题与内容展示
        val title = intent.getStringExtra("title") ?: "AioShell 提醒"
        val content = intent.getStringExtra("content").orEmpty()

        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content.ifBlank { "到时间了，别忘了哦" })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
    }
}