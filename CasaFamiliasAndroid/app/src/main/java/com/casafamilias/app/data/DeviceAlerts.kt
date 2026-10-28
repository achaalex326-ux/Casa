package com.casafamilias.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.*
import com.casafamilias.app.MainActivity
import com.casafamilias.app.R
import com.casafamilias.app.domain.*
import com.casafamilias.app.ui.CasaViewModel
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

object DeviceAlertState {
    val lock = Any()

    fun prefs(c: Context) = c.getSharedPreferences("casa_device_alerts", 0)

    fun owner(c: Context) = prefs(c).getString("owner", null)

    fun history(c: Context, user: String) = c.getSharedPreferences("casa_alert_history_$user", 0)

    fun permitted(c: Context) =
        NotificationManagerCompat.from(c).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)

    fun disable(c: Context) {
        synchronized(lock) {
            prefs(c).edit().remove("owner").remove("enabled_at").commit()
            WorkManager.getInstance(c).cancelUniqueWork("casa-alert-periodic")
            WorkManager.getInstance(c).cancelUniqueWork("casa-alert-now")
            NotificationManagerCompat.from(c).cancelAll()
        }
    }

    fun accountChanged(c: Context, user: String?) {
        if (owner(c) != null && owner(c) != user) disable(c)
    }

    fun schedule(c: Context, user: String, immediate: Boolean = true) {
        if (owner(c) != user || !permitted(c)) return
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = workDataOf("user_id" to user)
        val periodic =
            PeriodicWorkRequestBuilder<DeviceAlertWorker>(15, TimeUnit.MINUTES)
                .setInputData(data)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(c)
            .enqueueUniquePeriodicWork(
                "casa-alert-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        if (immediate)
            WorkManager.getInstance(c)
                .enqueueUniqueWork(
                    "casa-alert-now",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<DeviceAlertWorker>()
                        .setInputData(data)
                        .setConstraints(constraints)
                        .build(),
                )
    }
}

object DeviceAlertDisplay {
    fun channels(c: Context) {
        val manager = c.getSystemService(NotificationManager::class.java)
        listOf(
                "cleaning" to "Turnos de limpieza",
                "payments" to "Deudas y servicios",
                "general" to "Avisos de Casa Familias",
            )
            .forEach { (id, name) ->
                manager.createNotificationChannel(
                    NotificationChannel("casa_$id", name, NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE }
                )
            }
    }

    /** Returns false when blocked: do not mark an undelivered alert as seen. */
    fun show(c: Context, alert: DeviceAlert): Boolean {
        if (!DeviceAlertState.permitted(c)) return false
        channels(c)
        if (
            c.getSystemService(NotificationManager::class.java)
                .getNotificationChannel("casa_${alert.channel}")
                ?.importance == NotificationManager.IMPORTANCE_NONE
        )
            return false
        val intent =
            Intent(c, MainActivity::class.java)
                .setAction("com.casafamilias.OPEN_ALERT")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open =
            PendingIntent.getActivity(
                c,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val public =
            NotificationCompat.Builder(c, "casa_${alert.channel}")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Casa Familias")
                .setContentText("Tienes un aviso nuevo. Abre la aplicación para verlo.")
                .build()
        val notice =
            NotificationCompat.Builder(c, "casa_${alert.channel}")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .build()
        return try {
            NotificationManagerCompat.from(c).notify("casa:${alert.tag}", 0, notice)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

class DeviceAlertWorker
internal constructor(
    context: Context,
    params: WorkerParameters,
    private val fetch: suspend (Context, String, SessionStore) -> Snapshot,
) : CoroutineWorker(context, params) {
    constructor(
        context: Context,
        params: WorkerParameters,
    ) : this(
        context,
        params,
        { c, at, store -> CasaRepository(c, ApiConfig.load(c), store).notificationSnapshot(at) },
    )

    override suspend fun doWork(): Result {
        val c = applicationContext
        val expected = inputData.getString("user_id") ?: return Result.success()
        if (DeviceAlertState.owner(c) != expected || !DeviceAlertState.permitted(c))
            return Result.success()
        val store = SecureSessionStore(c)
        if (store.load()?.userId != expected) return Result.success()
        return try {
            withTimeout(100000) {
                val enabledAt =
                    DeviceAlertState.prefs(c).getString("enabled_at", null)?.let(Instant::parse)
                        ?: return@withTimeout Result.success()
                val snapshot = fetch(c, enabledAt.toString(), store)
                if (snapshot.profile.id != expected) return@withTimeout Result.success()
                deliver(c, expected, snapshot, enabledAt)
                Result.success()
            }
        } catch (e: SessionExpired) {
            if (store.load() == null && DeviceAlertState.owner(c) == expected)
                DeviceAlertState.disable(c)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        /** Shared with instrumentation, without introducing a debug bypass in the app. */
        internal fun deliver(c: Context, expected: String, snapshot: Snapshot, enabledAt: Instant) {
            synchronized(DeviceAlertState.lock) {
                if (
                    DeviceAlertState.owner(c) != expected ||
                        snapshot.profile.id != expected ||
                        SecureSessionStore(c).load()?.userId != expected
                )
                    return
                val h = DeviceAlertState.history(c, expected)
                fun strings(key: String) =
                    runCatching { JSONArray(h.getString(key, "[]")) }
                        .getOrDefault(JSONArray())
                        .let { a -> (0 until a.length()).map { a.getString(it) } }
                val previous = strings("seen")
                val plan =
                    DeviceAlertRules.plan(
                        snapshot,
                        previous.toSet(),
                        enabledAt,
                        LocalDate.parse(Dates.today()),
                    )
                val shown = strings("active")
                shown
                    .filter { it !in plan.activeTags }
                    .forEach { NotificationManagerCompat.from(c).cancel("casa:$it", 0) }
                val newKeys = mutableListOf<String>()
                plan.alerts.forEach { alert ->
                    if (DeviceAlertDisplay.show(c, alert)) newKeys += alert.remember
                }
                h.edit()
                    .putString(
                        "seen",
                        JSONArray((previous + newKeys).distinct().takeLast(2000)).toString(),
                    )
                    .putString("active", JSONArray(plan.activeTags.toList()).toString())
                    .commit()
            }
        }
    }
}

class DeviceAlerts(private val activity: FragmentActivity, private val vm: CasaViewModel) {
    private val permission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) activate()
            else {
                vm.inform(
                    "No se activaron las notificaciones. Puedes permitirlas en los ajustes del teléfono."
                )
                vm.deviceSettingsChanged()
            }
        }

    fun enabled() =
        DeviceAlertState.owner(activity) == vm.state.value.data?.profile?.id &&
            DeviceAlertState.owner(activity) != null

    fun allowed() = DeviceAlertState.permitted(activity)

    fun enable() {
        if (
            vm.state.value.locked ||
                vm.state.value.data == null ||
                vm.state.value.data?.preview == true
        )
            return
        DeviceAlertDisplay.channels(activity)
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!allowed()) {
            settings()
            vm.inform("Permite las notificaciones y vuelve a pulsar Activar notificaciones.")
        } else activate()
    }

    private fun activate() {
        val s = vm.state.value
        val user = s.data?.profile?.id ?: return
        if (s.locked || s.data.preview || !allowed()) return
        synchronized(DeviceAlertState.lock) {
            DeviceAlertState.prefs(activity)
                .edit()
                .putString("owner", user)
                .putString("enabled_at", Instant.now().toString())
                .commit()
        }
        DeviceAlertState.schedule(activity, user)
        vm.inform("Notificaciones activadas. Android revisará los avisos periódicamente.")
        vm.deviceSettingsChanged()
    }

    fun disable() {
        DeviceAlertState.disable(activity)
        vm.inform("Notificaciones desactivadas en este teléfono.")
        vm.deviceSettingsChanged()
    }

    fun test() {
        if (!enabled() || !allowed()) return
        DeviceAlertDisplay.show(
            activity,
            DeviceAlert(
                "test",
                "test",
                "general",
                "Las notificaciones están activas",
                "Esta es una prueba de Casa Familias. Los avisos reales se revisan en segundo plano.",
            ),
        )
        vm.inform("Revisa la barra de notificaciones de tu celular.")
    }

    fun settings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        )
    }
}
