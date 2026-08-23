# Tap8583 Android Client (`listenerLib`) — Integration Guide

`visualizer-log-listener-1.0.0.jar` is a lightweight, zero-configuration jPOS logger that streams live ISO 8583 transaction events directly to the **Tap8583 Cloud Visualizer** over HTTPS.

---

## 🚀 1 — Add the Dependency to your Android Project

### Option A: Gradle Dependency (via JAR)
Copy `listenerLib/build/libs/visualizer-log-listener-1.0.0.jar` into your Android project's `app/libs/` directory.

In `app/build.gradle`:
```groovy
dependencies {
    // Recommended: Use debugImplementation so visualizer code never ships to production
    debugImplementation files('libs/visualizer-log-listener-1.0.0.jar')
}
```

---

## ⚡ 2 — One-Line Setup in Android Application

You do **not** need to find local IP addresses, run ADB port forwards, or modify network configurations.

In your `Application.onCreate()`:

```kotlin
import com.tap8583.visualizer.VisualizerLogListener

class PosApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // One-line setup — auto-detects hardware serial and connects to cloud visualizer
        VisualizerLogListener.install(
            apiKey = "tap_live_YOUR_WORKSPACE_KEY"
        )
    }
}
```

> **What happens automatically:**
> 1. `listenerLib` auto-detects your POS hardware serial number (`Build.getSerial()`, `Settings.Secure.ANDROID_ID`, or PAX/Telpo/Nexgo system properties).
> 2. Auto-registers as a listener with jPOS `Logger.getLogger("Q2")`.
> 3. Dispatches transaction events via a non-blocking background queue over HTTPS.

---

## 🔒 3 — Security & Multi-Terminal Isolation

- All log transmissions require your Team **API Key** (`x-api-key`).
- Even though POS serial numbers are often sequential, unauthorized users cannot view your terminal transactions without your specific workspace API key.
- Sensitive payment data (PANs, Track 2, PIN blocks) are masked by default.

---

## 📱 4 — View Live Logs on the Web Dashboard

1. Open your Tap8583 Web Dashboard: `https://tap8583.vercel.app` (or your local/VPS URL).
2. Enter your **Team API Key** in the settings.
3. Select your device from the **Device Selector Dropdown** (or select **All Devices**).
4. Perform transactions on your POS terminal — packets, MTI tiles, STAN pairings, RTT latency, and EMV TLV trees appear instantly on screen!

---

## ⚙️ 5 — Advanced / Self-Hosted Configuration (Optional)

If self-hosting the Kotlin Ktor backend on your own Hetzner VPS:

```kotlin
VisualizerLogListener.install(
    apiKey = "tap_live_YOUR_KEY",
    endpoint = "https://your-hetzner-vps.com/api/v1/events",
    deviceSerial = "CUSTOM-SERIAL-TAG" // Optional custom tag
)
```
