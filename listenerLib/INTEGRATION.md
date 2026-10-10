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

- All log transmissions require the **ingest key** (`x-api-key`). It can only send events, so it is safe to build into debug APKs.
- Viewing transactions requires the separate **viewer key**, entered in the dashboard.
- Card data (PAN, Track 2, expiry, PIN block, and the card data inside DE55) is masked on the server before it is stored or shown.

---

## 📱 4 — View Live Logs on the Web Dashboard

1. Open your Tap8583 Web Dashboard (your Hetzner domain, or `http://localhost:3000` locally).
2. Enter the **viewer key** in the settings. This is a different key from the ingest key built into the POS app, which can only send events.
3. Select your device from the **Device Selector Dropdown** (or select **All Devices**).
4. Perform transactions on your POS terminal — packets, MTI tiles, STAN pairings, RTT latency, and EMV TLV trees appear instantly on screen!

---

## ⚙️ 5 — Advanced / Self-Hosted Configuration (Optional)

Point the listener at your Hetzner deployment (see `deploy/HETZNER_DEPLOYMENT.md`):

```kotlin
VisualizerLogListener.install(
    apiKey = "YOUR_INGEST_KEY",
    endpoint = "https://your-domain/api/v1/events",
    deviceSerial = "CUSTOM-SERIAL-TAG" // Optional custom tag
)
```
