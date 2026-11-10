package nl.toolboxplus.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.toolboxplus.Lights
import nl.toolboxplus.R
import nl.toolboxplus.agui.AguiService
import nl.toolboxplus.ui.MainActivity

/** Quick Settings tile that mirrors and toggles one light. */
abstract class LightsTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    /** Emits (active, subtitle) for the tile. */
    protected abstract fun state(): Flow<Pair<Boolean, String>>

    protected abstract fun toggle()

    override fun onStartListening() {
        Lights.init(this)
        listening = scope.launch {
            combine(Lights.connection, state()) { connection, state ->
                if (connection == Lights.Connection.Connected) state else null
            }.collect { state ->
                val tile = qsTile ?: return@collect
                tile.state = when {
                    state == null -> Tile.STATE_UNAVAILABLE
                    state.first -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                tile.subtitle = state?.second ?: getString(R.string.tile_not_connected)
                tile.updateTile()
            }
        }
        Lights.refresh()
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        scope.launch {
            if (Lights.awaitConnected()) toggle() else openApp()
        }
    }

    /** Opens Shizuku when it only needs starting (the usual case after a reboot), else the app. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val shizuku = packageManager.getLaunchIntentForPackage(Lights.SHIZUKU_PACKAGE)
            .takeIf { Lights.connection.value == Lights.Connection.ShizukuStopped }
        val intent = (shizuku ?: Intent(this, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class CampingLampTile : LightsTile() {
    override fun state() = Lights.lamp.map { lamp ->
        lamp.isOn to getString(lampModeLabel(lamp.mode))
    }

    override fun toggle() = Lights.toggleLamp()
}

class WarningLightsTile : LightsTile() {
    override fun state() = Lights.warning.map { warning ->
        warning.playing to getString(if (warning.playing) R.string.state_active else R.string.state_off)
    }

    override fun toggle() = Lights.toggleWarning()
}

fun lampModeLabel(mode: Int): Int = when (mode) {
    AguiService.LAMP_FULL -> R.string.lamp_full
    AguiService.LAMP_HALF -> R.string.lamp_half
    AguiService.LAMP_QUARTER -> R.string.lamp_quarter
    AguiService.LAMP_FLASH -> R.string.lamp_flash
    AguiService.LAMP_SOS -> R.string.lamp_sos
    else -> R.string.state_off
}
