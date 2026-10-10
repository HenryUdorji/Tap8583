package dev.sarari.tap8583.listener

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Logger

/**
 * Utility for automatically resolving Android POS hardware serial numbers
 * without requiring manual user configuration.
 */
object DeviceUtils {

    private val log = Logger.getLogger("Tap8583DeviceUtils")

    @Volatile
    private var cachedSerial: String? = null

    /**
     * Resolves the hardware serial number of the POS device.
     *
     * Order of precedence:
     * 1. Explicit override if provided
     * 2. Android Build.getSerial() (Android 8+)
     * 3. Android Build.SERIAL (Android < 8)
     * 4. POS Vendor system properties (PAX, Telpo, Nexgo, Sunmi, etc.)
     * 5. Fallback persistent session ID
     */
    fun resolveDeviceSerial(explicitSerial: String? = null): String {
        if (!explicitSerial.isNullOrBlank() && explicitSerial != "unknown") {
            return explicitSerial.trim()
        }

        cachedSerial?.let { return it }

        val detected = detectAndroidSerial()
        val finalSerial = if (detected.isNotBlank() && detected != "unknown") {
            detected
        } else {
            "POS-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        }

        cachedSerial = finalSerial
        log.info("Resolved POS Device Serial: $finalSerial")
        return finalSerial
    }

    private fun detectAndroidSerial(): String {
        // 1. Try Build.getSerial() via reflection
        try {
            val buildClass = Class.forName("android.os.Build")
            val getSerialMethod: Method? = try {
                buildClass.getMethod("getSerial")
            } catch (_: NoSuchMethodException) { null }

            if (getSerialMethod != null) {
                val serial = getSerialMethod.invoke(null) as? String
                if (!serial.isNullOrBlank() && serial != "unknown") {
                    return serial.trim()
                }
            }
        } catch (_: Throwable) { }

        // 2. Try Build.SERIAL field
        try {
            val buildClass = Class.forName("android.os.Build")
            val serialField = buildClass.getField("SERIAL")
            val serial = serialField.get(null) as? String
            if (!serial.isNullOrBlank() && serial != "unknown") {
                return serial.trim()
            }
        } catch (_: Throwable) { }

        // 3. Try POS Manufacturer System Properties
        val posPropertyKeys = listOf(
            "ro.serialno",
            "ro.boot.serialno",
            "persist.sys.pax.sn",
            "ro.pax.sn",
            "ro.telpo.sn",
            "persist.sys.telpo.sn",
            "ro.nexgo.serialno",
            "ro.sunmi.serialno",
            "ro.sn"
        )

        for (prop in posPropertyKeys) {
            val value = getSystemProperty(prop)
            if (!value.isNullOrBlank() && value != "unknown") {
                return value.trim()
            }
        }

        return ""
    }

    private fun getSystemProperty(key: String): String? {
        // Method A: android.os.SystemProperties via reflection
        try {
            val sysPropClass = Class.forName("android.os.SystemProperties")
            val getMethod = sysPropClass.getMethod("get", String::class.java, String::class.java)
            val result = getMethod.invoke(null, key, "") as? String
            if (!result.isNullOrBlank()) return result
        } catch (_: Throwable) { }

        // Method B: shell command getprop
        try {
            val process = ProcessBuilder("getprop", key).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            process.destroy()
            if (!line.isNullOrBlank()) return line.trim()
        } catch (_: Throwable) { }

        return null
    }
}
