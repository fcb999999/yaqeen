package app.yaqeen.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.yaqeen.MainActivity
import app.yaqeen.R

/** قنوات الإشعارات. كل نوع قابل للتحكم المستقل من إعدادات النظام ومن إعدادات التطبيق. */
object Channels {
    const val ADHAN = "adhan"
    const val PRAYER_REMINDER = "prayer_reminder"
    const val WIRD = "wird"
    const val ADHKAR = "adhkar"
    const val TASKS = "tasks"
    const val DAY_END = "day_end"
    const val EVENTS = "events"
    const val UPDATES = "updates"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, nameRes: Int, importance: Int, descRes: Int? = null) {
            val c = NotificationChannel(id, context.getString(nameRes), importance)
            if (descRes != null) c.description = context.getString(descRes)
            nm.createNotificationChannel(c)
        }
        ch(ADHAN, R.string.channel_adhan, NotificationManager.IMPORTANCE_HIGH, R.string.channel_adhan_desc)
        ch(PRAYER_REMINDER, R.string.channel_prayer_reminder, NotificationManager.IMPORTANCE_HIGH)
        ch(WIRD, R.string.channel_wird, NotificationManager.IMPORTANCE_DEFAULT)
        ch(ADHKAR, R.string.channel_adhkar, NotificationManager.IMPORTANCE_DEFAULT)
        ch(TASKS, R.string.channel_tasks, NotificationManager.IMPORTANCE_DEFAULT)
        ch(DAY_END, R.string.channel_day_end, NotificationManager.IMPORTANCE_DEFAULT)
        ch(EVENTS, R.string.channel_events, NotificationManager.IMPORTANCE_DEFAULT)
        ch(UPDATES, R.string.channel_updates, NotificationManager.IMPORTANCE_LOW)
    }
}

/**
 * تذكير واحد مجدول. [key] فريد ومستقر (مثل "prayer:2026-11-04:FAJR:adhan") فإعادة الجدولة بالمفتاح نفسه تستبدل القديم ولا تكرر.
 * [route] مسار داخل التطبيق يُفتح عند النقر على الإشعار (انظر ui/navigation/Routes).
 */
data class Reminder(
    val key: String,
    val triggerAtMillis: Long,
    val channelId: String,
    val title: String,
    val text: String,
    val route: String? = null,
    val extras: Map<String, String> = emptyMap(),
)

/** جدولة التذكيرات عبر AlarmManager (دقيقة عند توفر إذن المنبهات الدقيقة، وتقريبية بدونه). */
class ReminderScheduler(private val context: Context) {
    private val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("yaqeen_reminders", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()

    fun schedule(r: Reminder) {
        if (r.triggerAtMillis <= System.currentTimeMillis()) return
        val pi = pendingIntent(r.key, r)
        if (canScheduleExact()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMillis, pi)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMillis, pi)
        }
        prefs.edit().putStringSet(INDEX, (prefs.getStringSet(INDEX, emptySet()).orEmpty() + r.key)).apply()
    }

    fun cancel(key: String) {
        alarm.cancel(pendingIntent(key, null))
        prefs.edit().putStringSet(INDEX, prefs.getStringSet(INDEX, emptySet()).orEmpty() - key).apply()
    }

    /** يلغي كل التذكيرات التي يبدأ مفتاحها بالبادئة (مثل "prayer:" قبل إعادة جدولة المواقيت). */
    fun cancelByPrefix(prefix: String) {
        val keys = prefs.getStringSet(INDEX, emptySet()).orEmpty()
        keys.filter { it.startsWith(prefix) }.forEach { alarm.cancel(pendingIntent(it, null)) }
        prefs.edit().putStringSet(INDEX, keys.filterNot { it.startsWith(prefix) }.toSet()).apply()
    }

    private fun pendingIntent(key: String, r: Reminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("app.yaqeen.REMINDER").setData(android.net.Uri.parse("yaqeen://reminder/$key"))
        if (r != null) {
            intent.putExtra(ReminderReceiver.EXTRA_KEY, r.key)
                .putExtra(ReminderReceiver.EXTRA_CHANNEL, r.channelId)
                .putExtra(ReminderReceiver.EXTRA_TITLE, r.title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, r.text)
                .putExtra(ReminderReceiver.EXTRA_ROUTE, r.route)
            r.extras.forEach { (k, v) -> intent.putExtra("x_$k", v) }
            intent.putExtra(ReminderReceiver.EXTRA_EXTRA_KEYS, r.extras.keys.toTypedArray())
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object { const val INDEX = "keys" }
}

/** يستقبل التنبيه المجدول ويعرض الإشعار. قناة الأذان تُمرَّر أولًا لمشغّل الأذان (الصوت المخصص). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: Channels.TASKS
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val route = intent.getStringExtra(EXTRA_ROUTE)
        val extraKeys = intent.getStringArrayExtra(EXTRA_EXTRA_KEYS).orEmpty()
        val extras = extraKeys.associateWith { intent.getStringExtra("x_$it").orEmpty() }
        val payload = ReminderPayload(key, channel, title, text, route, extras)
        // قناة الأذان قد تحتاج تشغيل صوت مخصص؛ إن عالجها المشغّل لا نعرض الإشعار الافتراضي.
        if (channel == Channels.ADHAN && app.yaqeen.feature.prayer.adhan.AdhanPlayer.handle(context, payload)) return
        NotificationPoster.post(context, payload)
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_EXTRA_KEYS = "extra_keys"
    }
}

data class ReminderPayload(
    val key: String,
    val channelId: String,
    val title: String,
    val text: String,
    val route: String?,
    val extras: Map<String, String>,
)

object NotificationPoster {
    fun post(context: Context, p: ReminderPayload) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        Channels.createAll(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, p.route)
        val pi = PendingIntent.getActivity(context, p.key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, p.channelId)
            .setSmallIcon(R.drawable.ic_logo_foreground)
            .setContentTitle(p.title)
            .setContentText(p.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(p.text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(p.key.hashCode(), n) }
    }
}
