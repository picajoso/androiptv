package es.maciptv.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Stores display metadata separately; reminderMarks remains the source of truth. */
class ProgrammeReminders(private val context: Context, private val store: SecureStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("androiptv.reminders", Context.MODE_PRIVATE)

    fun add(key: String, channel: String, programme: Programme) {
        val data = JSONObject().put("channel", channel).put("title", programme.title)
            .put("start", programme.startMillis).put("end", programme.endMillis).toString()
        if (!prefs.edit().putString(key, data).commit()) throw IllegalStateException("No se pudo guardar el aviso")
        schedule(key, programme.startMillis)
    }

    fun remove(key: String) {
        alarms.cancel(alarmIntent(key))
        prefs.edit().remove(key).apply()
    }

    fun removeSource(sourceId: String) {
        val prefix = "$sourceId|"
        store.reminderMarks.filter { it.startsWith(prefix) }.forEach(::remove)
        store.reminderMarks = store.reminderMarks.filterNotTo(mutableSetOf()) { it.startsWith(prefix) }
    }

    fun reconcile() {
        val now = System.currentTimeMillis()
        val marks = store.reminderMarks
        prefs.all.keys.filter { it !in marks }.forEach(::remove)
        marks.forEach { key ->
            val data = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val start = data?.optLong("start") ?: key.substringAfterLast('|').toLongOrNull()
            if (start != null && start > now) schedule(key, start)
        }
    }

    fun syncGuide(sourceId: String, channels: List<Channel>, guide: Map<String, List<Programme>>) {
        val byId = channels.associateBy { it.id }
        val now = System.currentTimeMillis()
        store.reminderMarks.filter { it.startsWith("$sourceId|") }.forEach { key ->
            val channelId = key.substringAfter('|').substringBeforeLast('|')
            val start = key.substringAfterLast('|').toLongOrNull() ?: return@forEach
            if (start <= now) return@forEach
            val channel = byId[channelId] ?: return@forEach
            val programme = guide[channel.tvgId].orEmpty().firstOrNull { it.startMillis == start } ?: return@forEach
            if (!prefs.contains(key)) add(key, channel.name, programme)
        }
        reconcile()
    }

    fun isExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun schedule(key: String, start: Long) {
        if (start <= System.currentTimeMillis()) return
        val intent = alarmIntent(key)
        try {
            if (isExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, intent)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, intent)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, intent)
        }
    }

    private fun alarmIntent(key: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("androiptv://reminder/${Uri.encode(key)}")
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_REMINDER = "es.maciptv.android.REMINDER"
        const val EXTRA_SOURCE = "reminderSource"
        const val EXTRA_CHANNEL = "reminderChannel"
    }

    internal fun deliver(key: String) {
        if (key !in store.reminderMarks) return
        val data = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        if (data.optLong("end") <= System.currentTimeMillis()) return
        if (!canNotify()) return
        val channelId = key.substringAfter('|').substringBeforeLast('|')
        val sourceId = key.substringBefore('|')
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(NotificationChannel("programme_reminders", "Avisos de la guía", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = Uri.parse("androiptv://open/${Uri.encode(key)}")
            putExtra(EXTRA_SOURCE, sourceId)
            putExtra(EXTRA_CHANNEL, channelId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notificationManager.notify(key.hashCode(), NotificationCompat.Builder(context, "programme_reminders")
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(data.optString("title", "Empieza un programa marcado"))
            .setContentText(data.optString("channel"))
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = ProgrammeReminders(context, SecureStore(context))
        when (intent.action) {
            ProgrammeReminders.ACTION_REMINDER -> intent.data?.lastPathSegment?.let(reminders::deliver)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> reminders.reconcile()
        }
    }
}
