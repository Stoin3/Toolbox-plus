package nl.toolboxplus

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.toolboxplus.agui.AguiService
import nl.toolboxplus.agui.CommonService
import nl.toolboxplus.agui.ProjectorService
import nl.toolboxplus.agui.ServiceManagerCompat
import rikka.shizuku.Shizuku

/**
 * App-wide access to the camping lamp, warning lights, projector, fan and distance sensor.
 *
 * Regular apps may not look up the Agui service (SELinux restricts it to system apps and the
 * shell), so the lookup is made once through Shizuku. After that the app talks to the system
 * service directly and Shizuku is no longer needed until the app restarts.
 */
object Lights {

    sealed interface Connection {
        data object Connecting : Connection
        data object ShizukuMissing : Connection
        data object ShizukuStopped : Connection
        data object PermissionNeeded : Connection
        data object PermissionDenied : Connection
        data class Failed(val message: String) : Connection
        data object Connected : Connection
    }

    data class LampState(val mode: Int = AguiService.LAMP_OFF) {
        val isOn get() = mode != AguiService.LAMP_OFF
    }

    data class WarningState(
        val playing: Boolean = false,
        val enabledLights: Int = AguiService.LIGHT_RED or AguiService.LIGHT_BLUE,
        val style: Int = AguiService.STYLE_FLASHING,
        val siren: String = AguiService.SIREN_NONE,
    ) {
        fun isEnabled(light: Int) = enabledLights and light != 0
    }

    data class ProjectorState(
        val available: Boolean = false,
        val on: Boolean = false,
        val brightness: Int = ProjectorService.BRIGHTNESS_DEFAULT,
        val autoKeystone: Boolean = true,
        val fanAvailable: Boolean = false,
        val fanRunning: Boolean = false,
        val fanLevel: Int = ProjectorService.FAN_LEVELS.last(),
        val focusAvailable: Boolean = false,
    )

    data class RangeState(
        val available: Boolean = false,
        val measuring: Boolean = false,
        val continuous: Boolean = false,
        val lastCm: Float? = null,
        val noReading: Boolean = false,
        val history: List<Float> = emptyList(),
        val fromBottomEdge: Boolean = false,
    )

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "Lights"
    private const val PERMISSION_REQUEST_CODE = 1
    private const val RANGE_INTERVAL_MS = 500L
    private const val RANGE_ATTEMPTS = 10
    private const val RANGE_HISTORY = 10
    private const val PREF_FROM_BOTTOM_EDGE = "range_from_bottom_edge"

    private val _connection = MutableStateFlow<Connection>(Connection.Connecting)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _lamp = MutableStateFlow(LampState())
    val lamp: StateFlow<LampState> = _lamp.asStateFlow()

    private val _warning = MutableStateFlow(WarningState())
    val warning: StateFlow<WarningState> = _warning.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Binder calls are serialised on a single background thread.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _projector = MutableStateFlow(ProjectorState())
    val projectorState: StateFlow<ProjectorState> = _projector.asStateFlow()

    private val _range = MutableStateFlow(RangeState())
    val range: StateFlow<RangeState> = _range.asStateFlow()

    private lateinit var appContext: Context
    @Volatile private var service: AguiService? = null
    @Volatile private var projector: ProjectorService? = null
    @Volatile private var common: CommonService? = null
    private var rangeJob: Job? = null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _range.update { it.copy(fromBottomEdge = prefs.getBoolean(PREF_FROM_BOTTOM_EDGE, false)) }
        Shizuku.addBinderReceivedListenerSticky { connect() }
        Shizuku.addRequestPermissionResultListener { _, _ -> connect() }
        connect()
    }

    fun connect() {
        scope.launch { doConnect() }
    }

    private fun doConnect() {
        if (service?.binder?.isBinderAlive == true) {
            _connection.value = Connection.Connected
            return
        }
        // Works if the app ever runs with system privileges; normally SELinux denies it.
        ServiceManagerCompat.getService(AguiService.SERVICE_NAME)?.let {
            attach(it, ServiceManagerCompat::getService)
            return
        }
        _connection.value = when {
            !isShizukuInstalled() -> Connection.ShizukuMissing
            !Shizuku.pingBinder() -> Connection.ShizukuStopped
            Shizuku.isPreV11() -> Connection.Failed(appContext.getString(R.string.shizuku_too_old))
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                try {
                    val lights = ServiceManagerCompat.getServiceViaShizuku(AguiService.SERVICE_NAME)
                    if (lights == null) {
                        Connection.Failed(appContext.getString(R.string.service_not_found))
                    } else {
                        attach(lights, ServiceManagerCompat::getServiceViaShizuku)
                        return
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Lookup through Shizuku failed", e)
                    val cause = (e.cause ?: e)
                    Connection.Failed(cause.message ?: cause.javaClass.simpleName)
                }
            }
            Shizuku.shouldShowRequestPermissionRationale() -> Connection.PermissionDenied
            else -> Connection.PermissionNeeded
        }
    }

    /** [lookup] finds the optional services the same way the lights service was found. */
    private fun attach(lights: IBinder, lookup: (String) -> IBinder?) {
        // All services live in system_server, so one death notification covers them all.
        runCatching {
            lights.linkToDeath({
                service = null
                projector = null
                common = null
                _connection.value = Connection.Connecting
                connect()
            }, 0)
        }
        service = AguiService(lights)
        projector = runCatching { lookup(ProjectorService.SERVICE_NAME) }.getOrNull()?.let(::ProjectorService)
        common = runCatching { lookup(CommonService.SERVICE_NAME) }.getOrNull()?.let(::CommonService)
        _range.value = _range.value.copy(available = common != null)
        _connection.value = Connection.Connected
        readState()
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { connect() }
    }

    fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun awaitConnected(timeoutMs: Long = 3000): Boolean {
        connect()
        return withTimeoutOrNull(timeoutMs) {
            connection.first { it == Connection.Connected }
        } != null
    }

    fun refresh() {
        scope.launch { if (service != null) readState() }
    }

    /** [AguiService.LAMP_FULL] runs continuously under [MaxLampService]'s temperature guard. */
    fun setLampMode(mode: Int) {
        if (mode == AguiService.LAMP_FULL) {
            MaxLampService.start(appContext)
        } else {
            MaxLampService.stop(appContext)
            act { it.setCampingLampMode(mode) }
        }
    }

    fun toggleLamp() {
        if (_lamp.value.isOn) MaxLampService.stop(appContext)
        act {
            it.setCampingLampMode(if (it.campingLampMode() == AguiService.LAMP_OFF) AguiService.LAMP_HALF else AguiService.LAMP_OFF)
        }
    }

    /** Only for [MaxLampService]; everything else goes through [setLampMode]. */
    internal fun setLampModeDirect(mode: Int) = act { it.setCampingLampMode(mode) }

    /** Takes one reading, or keeps reading until [stopMeasuring] when [continuous]. */
    fun measure(continuous: Boolean) {
        rangeJob?.cancel()
        rangeJob = scope.launch {
            val agui = service ?: return@launch
            val sensor = common ?: return@launch
            _range.update { it.copy(measuring = true, continuous = continuous, noReading = false) }
            try {
                agui.setRangefinderPower(true)
                delay(RANGE_INTERVAL_MS)
                var attempts = 0
                while (true) {
                    val cm = sensor.readRangefinderMm()?.let { mm ->
                        (if (_range.value.fromBottomEdge) mm + CommonService.PHONE_LENGTH_MM else mm) / 10f
                    }
                    if (cm != null) {
                        _range.update {
                            it.copy(
                                lastCm = cm,
                                noReading = false,
                                history = if (continuous) it.history else (listOf(cm) + it.history).take(RANGE_HISTORY),
                            )
                        }
                        if (!continuous) break
                    } else if (!continuous && ++attempts >= RANGE_ATTEMPTS) {
                        _range.update { it.copy(noReading = true) }
                        break
                    }
                    delay(RANGE_INTERVAL_MS)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Rangefinder failed", e)
                    handleFailure(e)
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { agui.setRangefinderPower(false) }
                    _range.update { it.copy(measuring = false, continuous = false) }
                }
            }
        }
    }

    fun stopMeasuring() {
        rangeJob?.cancel()
    }

    /** Adds the current continuous reading to the history. */
    fun keepReading() {
        _range.update { state ->
            state.lastCm?.let { state.copy(history = (listOf(it) + state.history).take(RANGE_HISTORY)) } ?: state
        }
    }

    fun clearHistory() {
        _range.update { it.copy(history = emptyList()) }
    }

    /** Measure from the bottom edge of the phone instead of from the sensor. */
    fun setMeasureFromBottomEdge(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_FROM_BOTTOM_EDGE, enabled).apply()
        _range.update { it.copy(fromBottomEdge = enabled) }
    }

    private val prefs by lazy { appContext.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    fun toggleWarning() = act {
        if (it.isWarningPlaying()) it.stopWarning() else it.playWarning()
    }

    fun setLightEnabled(light: Int, enabled: Boolean) = act { it.setLightEnabled(light, enabled) }

    fun setWarningStyle(style: Int) = act { it.setWarningStyle(style) }

    fun setSiren(name: String) = act { it.setSiren(name) }

    fun setProjectorPower(on: Boolean) = actProjector {
        when (it.setPower(on)) {
            ProjectorService.RESULT_USB_CONNECTED -> message(R.string.projector_usb_connected)
            ProjectorService.RESULT_TOO_SOON -> message(R.string.projector_too_soon)
        }
    }

    fun setProjectorBrightness(value: Int) = actProjector {
        it.putSetting(ProjectorService.KEY_BRIGHTNESS, value.coerceIn(0, ProjectorService.BRIGHTNESS_MAX).toString())
    }

    fun setAutoKeystone(enabled: Boolean) = actProjector {
        it.putSetting(ProjectorService.KEY_AUTO_KEYSTONE, if (enabled) "0" else "1")
    }

    fun setFanRunning(running: Boolean) = actProjector {
        it.setFanLevel(if (running) _projector.value.fanLevel else 0)
    }

    fun setFanLevel(level: Int) = actProjector {
        it.putSetting(ProjectorService.KEY_FAN_LEVEL, level.toString())
        it.setFanLevel(level)
    }

    private fun act(block: (AguiService) -> Unit) = call { service?.let(block) }

    private fun actProjector(block: (ProjectorService) -> Unit) = call { projector?.let(block) }

    private fun call(block: () -> Unit) {
        scope.launch {
            if (service == null) return@launch doConnect()
            try {
                block()
                // Some calls are handled asynchronously by the system service.
                delay(150)
                readState()
            } catch (e: Exception) {
                Log.e(TAG, "Call failed", e)
                handleFailure(e)
            }
        }
    }

    private fun readState() {
        try {
            service?.let { agui ->
                _lamp.value = LampState(agui.campingLampMode())
                _warning.value = WarningState(
                    playing = agui.isWarningPlaying(),
                    enabledLights = listOf(AguiService.LIGHT_RED, AguiService.LIGHT_BLUE, AguiService.LIGHT_GREEN)
                        .filter { agui.isLightEnabled(it) }
                        .fold(0) { acc, light -> acc or light },
                    style = agui.warningStyle(),
                    siren = agui.sirenName(),
                )
            }
            projector?.let { p ->
                _projector.value = ProjectorState(
                    available = true,
                    on = p.mode() > 0,
                    brightness = p.setting(ProjectorService.KEY_BRIGHTNESS, "")
                        .toIntOrNull() ?: ProjectorService.BRIGHTNESS_DEFAULT,
                    autoKeystone = p.setting(ProjectorService.KEY_AUTO_KEYSTONE, "0") == "0",
                    focusAvailable = p.setting(ProjectorService.KEY_FOCUS_SUPPORT, "false") == "true",
                    fanAvailable = p.isFanSupported(),
                    fanRunning = p.fanLevel() > 0,
                    fanLevel = p.setting(ProjectorService.KEY_FAN_LEVEL, "")
                        .toIntOrNull()?.takeIf { it in ProjectorService.FAN_LEVELS } ?: ProjectorService.FAN_LEVELS.last(),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reading state failed", e)
            handleFailure(e)
        }
    }

    private fun message(@StringRes id: Int) {
        _errors.tryEmit(appContext.getString(id))
    }

    private fun handleFailure(e: Exception) {
        if (service?.binder?.isBinderAlive != true) {
            service = null
            projector = null
            common = null
            _connection.value = Connection.Connecting
            connect()
        } else {
            _errors.tryEmit(appContext.getString(R.string.error_call, e.message ?: e.javaClass.simpleName))
        }
    }
}
