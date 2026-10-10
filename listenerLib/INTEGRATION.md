# Tap8583 Android Client (`listenerLib`) — Integration Guide

`dev.sarari:tap8583-listener` is a lightweight jPOS logger that streams live ISO 8583 transaction events directly to the **Tap8583 Cloud Visualizer** over HTTPS.

---

## 🚀 1 — Add the Dependency to your Android Project

The library is on Maven Central. In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.sarari:tap8583-listener:1.0.0")
}
```

It expects jPOS and the Kotlin standard library to be on the app's classpath already (both are `compileOnly`), and brings in OkHttp.

---

## ⚡ 2 — Connect it to your jPOS channel

You do **not** need to find local IP addresses, run ADB port forwards, or modify network configurations.

jPOS only hands a channel's events to the listeners of the logger that channel was given (`channel.setLogger(...)`). A channel without a logger logs nothing, so the listener has to sit on the channel's logger.

Create the listener once, for example in `Application.onCreate()`:

```kotlin
import dev.sarari.tap8583.listener.VisualizerLogListener

// Debug builds only, so production traffic never leaves the terminal
val tap8583 = if (BuildConfig.DEBUG) VisualizerLogListener(
    apiKey = "YOUR_INGEST_KEY",
    endpoint = "https://your-domain/api/v1/events"
) else null
```

Then, where you create your channel:

```kotlin
tap8583?.let { listener ->
    channel.setLogger(org.jpos.util.Logger().apply { addListener(listener) }, "channel")
}
```

If your channel already has a logger, call `addListener(tap8583)` on that logger once instead. Create the listener only once: each instance runs its own background sender.

From the next library release you can write `tap8583.attach(channel)`, which does the above (it reuses the channel's logger or gives it one, and never adds the listener twice).

> **About `VisualizerLogListener.install(...)`:** it only helps apps that run jPOS through Q2 with a logger named `"Q2"`. In 1.0.0 it logs "Auto-registered" even when no such logger exists, because jPOS creates an empty `"Q2"` logger that no channel writes to, so nothing is sent. The next release only uses an existing `"Q2"` logger and warns otherwise.

> **What the listener does:**
> 1. Auto-detects your POS hardware serial number (`Build.getSerial()`, `Settings.Secure.ANDROID_ID`, or PAX/Telpo/Nexgo system properties).
> 2. Dispatches transaction events via a non-blocking background queue over HTTPS.

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
VisualizerLogListener(
    apiKey = "YOUR_INGEST_KEY",
    endpoint = "https://your-domain/api/v1/events",
    deviceSerial = "CUSTOM-SERIAL-TAG" // Optional custom tag
)
```

---

## 📦 6 — Publishing a new version (maintainers)

Push a tag named `listener-v<version>`:

```bash
git tag listener-v1.0.1 && git push origin listener-v1.0.1
```

`.github/workflows/publish-listener.yml` builds, signs and releases `dev.sarari:tap8583-listener:<version>` to Maven Central. The secrets it needs are listed at the top of that workflow. To try the artifacts locally first, run `./gradlew :listenerLib:publishToMavenLocal -PlistenerVersion=1.0.1`.
