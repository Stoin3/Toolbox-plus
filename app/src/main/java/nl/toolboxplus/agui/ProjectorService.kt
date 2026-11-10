package nl.toolboxplus.agui

import android.os.IBinder

/**
 * Raw binder client for the Agui "ProjectorService" that drives the built-in projector and its
 * cooling fan. Transaction codes and parcel layouts mirror
 * com.agui.server.projector.IProjectorService in the device's framework.jar.
 *
 * Most projector settings are stored through [putSetting]; the system service polls them while
 * the projector runs and applies changes within about 1.5 seconds.
 */
class ProjectorService(val binder: IBinder) {

    /** 0 when off, otherwise the active aspect/orientation mode (1–7). */
    fun mode(): Int = transact(binder, DESCRIPTOR, TX_GET_PROJECTOR_MODE) { readInt() }

    /** Returns 0 or a negative [RESULT_IN_CALL], [RESULT_TOO_SOON] or [RESULT_USB_CONNECTED]. */
    fun setPower(on: Boolean): Int =
        transact(binder, DESCRIPTOR, TX_SET_PROJECTOR_MODE, {
            writeInt(if (on) 1 else 0)
            writeInt(TYPE_USER)
        }) { readInt() }

    fun isFanSupported(): Boolean =
        transact(binder, DESCRIPTOR, TX_IS_FAN_DEVICE_SUPPORT) { readInt() != 0 }

    /** Current fan duty: 0 when stopped. */
    fun fanLevel(): Int = transact(binder, DESCRIPTOR, TX_GET_FAN_LEVEL) { readInt() }

    fun setFanLevel(level: Int) =
        transact(binder, DESCRIPTOR, TX_SET_VALUES_FOR_ELEPHANT_DEVICE, {
            writeString(level.toString())
            writeInt(FAN_COOLING_MODE)
        }) {}

    fun setting(key: String, default: String): String =
        transact(binder, DESCRIPTOR, TX_GET_STRING_FROM_FILE, {
            writeString(key)
            writeString(default)
        }) { readString() } ?: default

    fun putSetting(key: String, value: String) =
        transact(binder, DESCRIPTOR, TX_PUT_STRING_TO_FILE, {
            writeString(key)
            writeString(value)
        }) {}

    companion object {
        const val SERVICE_NAME = "ProjectorService"

        const val RESULT_IN_CALL = -1
        const val RESULT_TOO_SOON = -2
        const val RESULT_USB_CONNECTED = -10

        const val KEY_BRIGHTNESS = "projector_brightness_values"
        const val BRIGHTNESS_MAX = 255
        const val BRIGHTNESS_DEFAULT = 205

        /** "0" lets the motion sensor straighten the image, "1" disables it. */
        const val KEY_AUTO_KEYSTONE = "projector_auto_keystone"

        /** "true" when the projector has the NDS03 sensor used for its focus calibration. */
        const val KEY_FOCUS_SUPPORT = "projector_focus_support"

        /** Last chosen fan speed; the stock fan app offers 10, 20, 30, 40 and 50. */
        const val KEY_FAN_LEVEL = "key_fan_level"
        val FAN_LEVELS = listOf(10, 20, 30, 40, 50)

        private const val TYPE_USER = 1
        private const val FAN_COOLING_MODE = 2

        private const val DESCRIPTOR = "com.agui.server.projector.IProjectorService"
        private const val TX_GET_PROJECTOR_MODE = 1
        private const val TX_SET_PROJECTOR_MODE = 2
        private const val TX_SET_VALUES_FOR_ELEPHANT_DEVICE = 5
        private const val TX_GET_FAN_LEVEL = 7
        private const val TX_GET_STRING_FROM_FILE = 9
        private const val TX_PUT_STRING_TO_FILE = 10
        private const val TX_IS_FAN_DEVICE_SUPPORT = 15
    }
}
