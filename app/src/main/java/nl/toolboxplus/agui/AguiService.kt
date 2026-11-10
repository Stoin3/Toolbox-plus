package nl.toolboxplus.agui

import android.os.IBinder
import android.os.Parcel

/**
 * Raw binder client for the Agui system service ("AService") that drives the camping lamp and
 * warning lights. Transaction codes and parcel layouts mirror com.agui.server.IAService and
 * com.agui.server.warninglights.IAguiWarningLightsController in the device's framework.jar.
 */
class AguiService(val binder: IBinder) {

    private val warningLights: IBinder by lazy {
        transact(binder, DESCRIPTOR, TX_GET_WARNING_LIGHTS_CONTROLLER) { readStrongBinder() }
            ?: error("Warning lights controller unavailable")
    }

    fun campingLampMode(): Int =
        transact(binder, DESCRIPTOR, TX_GET_CAMPING_LAMP_MODE) { readInt() }

    /**
     * Sets [mode] for all lamp types. [LAMP_FULL] set this way has no system time limit, so
     * [nl.toolboxplus.MaxLampService] watches the temperature instead.
     */
    fun setCampingLampMode(mode: Int) =
        transact(binder, DESCRIPTOR, TX_SET_CAMPING_LAMP_MODE, {
            writeInt(mode)
            writeInt(LAMP_TYPE_ALL)
        }) {}


    /** Powers the NDS03 time-of-flight distance sensor; read it with [CommonService.readRangefinder]. */
    fun setRangefinderPower(on: Boolean) =
        transact(binder, DESCRIPTOR, TX_SEND_COMMAND_TO_SERIAL, { writeInt(if (on) 101 else 100) }) {}

    fun isWarningPlaying(): Boolean =
        transact(warningLights, WL_DESCRIPTOR, WL_IS_PLAYING) { readInt() != 0 }

    fun playWarning() = transact(warningLights, WL_DESCRIPTOR, WL_PLAY) {}

    fun stopWarning() = transact(warningLights, WL_DESCRIPTOR, WL_STOP) {}

    fun sirenName(): String =
        transact(warningLights, WL_DESCRIPTOR, WL_GET_SIREN_NAME) { readString() } ?: SIREN_NONE

    fun setSiren(name: String) =
        transact(warningLights, WL_DESCRIPTOR, WL_SET_SIREN, { writeString(name) }) {}

    fun isLightEnabled(light: Int): Boolean =
        transact(warningLights, WL_DESCRIPTOR, WL_IS_LIGHT_ENABLED, { writeInt(light) }) { readInt() != 0 }

    fun setLightEnabled(light: Int, enabled: Boolean) =
        transact(warningLights, WL_DESCRIPTOR, WL_SET_LIGHT_ENABLED, {
            writeInt(light)
            writeInt(if (enabled) 1 else 0)
        }) {}

    fun warningStyle(): Int = transact(warningLights, WL_DESCRIPTOR, WL_GET_STYLE) { readInt() }

    fun setWarningStyle(style: Int) =
        transact(warningLights, WL_DESCRIPTOR, WL_SET_STYLE, { writeInt(style) }) {}

    companion object {
        const val SERVICE_NAME = "AService"

        const val LAMP_OFF = 0
        const val LAMP_FULL = 1
        const val LAMP_HALF = 2
        const val LAMP_QUARTER = 3
        const val LAMP_FLASH = 4
        const val LAMP_SOS = 5

        const val LIGHT_RED = 1
        const val LIGHT_BLUE = 16
        const val LIGHT_GREEN = 256

        const val STYLE_ALWAYS_ON = 1
        const val STYLE_FLASHING = 2

        const val SIREN_NONE = "siren_none"
        const val SIREN_COUNT = 31

        private const val LAMP_TYPE_ALL = 0

        private const val DESCRIPTOR = "com.agui.server.IAService"
        private const val TX_GET_WARNING_LIGHTS_CONTROLLER = 7
        private const val TX_SET_CAMPING_LAMP_MODE = 8
        private const val TX_GET_CAMPING_LAMP_MODE = 10
        private const val TX_SEND_COMMAND_TO_SERIAL = 11

        private const val WL_DESCRIPTOR = "com.agui.server.warninglights.IAguiWarningLightsController"
        private const val WL_PLAY = 3
        private const val WL_STOP = 4
        private const val WL_SET_SIREN = 5
        private const val WL_GET_SIREN_NAME = 6
        private const val WL_IS_PLAYING = 7
        private const val WL_IS_LIGHT_ENABLED = 8
        private const val WL_SET_LIGHT_ENABLED = 9
        private const val WL_GET_STYLE = 10
        private const val WL_SET_STYLE = 11
    }
}

internal inline fun <T> transact(
    binder: IBinder,
    descriptor: String,
    code: Int,
    write: Parcel.() -> Unit = {},
    read: Parcel.() -> T,
): T {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken(descriptor)
        data.write()
        binder.transact(code, data, reply, 0)
        reply.readException()
        return reply.read()
    } finally {
        reply.recycle()
        data.recycle()
    }
}
