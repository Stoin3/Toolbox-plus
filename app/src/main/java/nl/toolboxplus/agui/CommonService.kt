package nl.toolboxplus.agui

import android.os.IBinder

/**
 * Raw binder client for the Agui "agui_common" service, used here only to read the distance
 * sensor, whose sysfs node regular apps and the shell may not read. Mirrors
 * com.agui.ext.common.IACommonService in the device's agui-framework.jar.
 */
class CommonService(val binder: IBinder) {

    /** Distance from the sensor in millimetres, or null when the sensor has no valid reading. */
    fun readRangefinderMm(): Float? =
        transact(binder, DESCRIPTOR, TX_READ_STRING_FROM_FILE, { writeString(RANGEFINDER_PATH) }) { readString() }
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it >= 0f && it < RANGEFINDER_NO_TARGET_MM }

    companion object {
        const val SERVICE_NAME = "agui_common"

        /** Distance from the sensor to the bottom edge of the phone, as the stock app adds it. */
        const val PHONE_LENGTH_MM = 180f

        // The stock app treats 65300 as "no target".
        private const val RANGEFINDER_NO_TARGET_MM = 65_000f

        private const val RANGEFINDER_PATH = "/sys/nds03_ctrl/nds03_read"
        private const val DESCRIPTOR = "com.agui.ext.common.IACommonService"
        private const val TX_READ_STRING_FROM_FILE = 2
    }
}
