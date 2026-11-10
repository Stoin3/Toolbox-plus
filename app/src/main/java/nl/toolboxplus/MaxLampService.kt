package nl.toolboxplus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.toolboxplus.agui.AguiService
import nl.toolboxplus.ui.MainActivity

/**
 * Keeps the camping lamp at full brightness without the system's one-minute limit and
 * ten-minute cool-down. Because that limit exists to protect against heat, this service
 * watches the battery temperature and drops the lamp to half when the phone gets too warm.
 */
class MaxLampService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var guard: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            Lights.init(this)
            Lights.setLampMode(AguiService.LAMP_OFF)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = notification(getString(R.string.max_lamp_running), ongoing = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_RUNNING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_RUNNING, notification)
        }
        if (guard?.isActive != true) guard = scope.launch { keepFullAndWatchHeat() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun keepFullAndWatchHeat() {
        Lights.init(this)
        if (!Lights.awaitConnected()) {
            stopSelf()
            return
        }
        Lights.setLampModeDirect(AguiService.LAMP_FULL)
        delay(CHECK_INTERVAL_MS)
        while (true) {
            val celsius = batteryCelsius()
            if (celsius != null && celsius >= MAX_CELSIUS) {
                Lights.setLampModeDirect(AguiService.LAMP_HALF)
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_HOT,
                    notification(getString(R.string.max_lamp_too_hot, celsius), ongoing = false),
                )
                break
            }
            // Stop guarding once the lamp was changed elsewhere (app, tile or hardware key).
            Lights.refresh()
            delay(CHECK_INTERVAL_MS)
            if (Lights.lamp.value.mode != AguiService.LAMP_FULL) break
        }
        stopSelf()
    }

    private fun batteryCelsius(): Float? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10f }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.camping_lamp), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lamp)
            .setContentTitle(getString(R.string.camping_lamp))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(ongoing)
        if (ongoing) {
            val off = PendingIntent.getService(
                this, 0, Intent(this, MaxLampService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, getString(R.string.turn_off), off).build())
        }
        return builder.build()
    }

    companion object {
        /** Battery temperature at which full brightness is dropped to half. */
        const val MAX_CELSIUS = 45f

        private const val CHECK_INTERVAL_MS = 5_000L
        private const val CHANNEL = "camping_lamp"
        private const val NOTIFICATION_RUNNING = 1
        private const val NOTIFICATION_HOT = 2
        private const val ACTION_OFF = "nl.toolboxplus.action.LAMP_OFF"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MaxLampService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MaxLampService::class.java))
        }
    }
}
