# VisualizerLogListener — Integration Guide

The `visualizer-log-listener-1.0.0.jar` lets any jPOS application stream live transaction events to the **Tap8583 desktop visualizer** in real time over a plain TCP socket on your local network.

---

## 1 — Add the JAR to your POS project

### Android / Gradle
Copy the JAR into your project's `libs/` folder, then in `build.gradle`:
```groovy
dependencies {
    implementation files('libs/visualizer-log-listener-1.0.0.jar')
}
```

### Maven
```xml
<dependency>
    <groupId>com.tap8583.visualizer</groupId>
    <artifactId>visualizer-log-listener</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/visualizer-log-listener-1.0.0.jar</systemPath>
</dependency>
```

---

## 2 — Find the desktop machine's IP

1. Open **Tap8583** on your Mac
2. Click **Start** in the toolbar
3. Look at the bottom status bar — it shows **"Connect to: 192.168.x.x:9999"** for each active network interface
4. Note the IP that is on the same network as your POS device

> Both the desktop and the POS device must be on the **same LAN or WiFi network** (or connected via USB tethering).

---

## 3 — Register the listener in your jPOS app

You can register the listener either programmatically or via jPOS Q2 XML logger configuration.

### 3.1 — Programmatic Setup (Android / JVM)

Ideal for Android apps or direct code configuration:

```kotlin
import com.tap8583.visualizer.VisualizerLogListener
import org.jpos.util.Logger

// In Application.onCreate() or Q2.start():
val visualizer = VisualizerLogListener(
    host = "192.168.1.42",   // ← desktop IP from the status bar
    port = 9999              // ← must match the desktop app's port
)

// Attach to your jPOS Logger(s):
Logger.getLogger("Q2").addListener(visualizer)
// or attach to a specific channel logger:
myChannel.logger.addListener(visualizer)
```

> The listener connects in the background — it will not block your app startup.

### 3.2 — Q2 XML Logger Setup (JVM / Backend)

Ideal for standard jPOS `Q2` systems. Add this block under your main XML logger (usually `deploy/10_logger.xml`):

```xml
<logger name="Q2">
  <!-- Existing log listener — kept unchanged -->
  <log-listener class="org.jpos.util.SimpleLogListener">
    <property name="file" value="log/q2.log"/>
  </log-listener>

  <!-- Add this block — Tap8583 visualizer bridge -->
  <log-listener class="com.tap8583.visualizer.VisualizerLogListener">
    <property name="host" value="192.168.1.42"/>
    <property name="port" value="9999"/>
    <property name="enabled" value="true"/>
  </log-listener>
</logger>
```

---

## 4 — View logs from the POS side

The lib logs all activity through `java.util.logging` under the tag `VisualizerLogListener`.
On Android this routes automatically to **Logcat**. Filter by tag to see everything:

```
# adb logcat
Tag: VisualizerLogListener

I  Initialising — will connect to 192.168.1.42:9999
I  Starting connection loop to 192.168.1.42:9999 (max 10 attempts)
I  Connecting to 192.168.1.42:9999 — attempt 1/10
I  ✓ Connected to 192.168.1.42:9999
D  Forwarded send event to 192.168.1.42:9999
D  Forwarded receive event to 192.168.1.42:9999
W  Write error detected — connection lost, reconnecting…
```

Log levels:
| Level | Events |
|---|---|
| `INFO` | Connect / disconnect / reconnect lifecycle |
| `FINE` / `DEBUG` | Per-message send traces, skipped events |
| `WARNING` | Write errors, failed connection attempts |
| `SEVERE` | All retry attempts exhausted |

---

## 5 — Configuration reference

| Parameter | Default | Description |
|---|---|---|
| `host` | *(required)* | Desktop machine's LAN/WiFi IPv4 address |
| `port` | `9999` | TCP port (must match the desktop app) |
| `maxReconnectAttempts` | `10` | Retries before giving up |
| `reconnectDelayMs` | `2000` | Base delay between retries (doubles each attempt, max 30 s) |

---

## 6 — Runtime behaviour

- **Non-blocking** — `log()` returns immediately; TCP writes happen on the event caller thread but are synchronized; reconnects run on a daemon thread
- **Auto-reconnect** — if the desktop app restarts, the listener reconnects automatically
- **Daemon thread** — the connection thread won't prevent your app from shutting down
- **No extra dependencies** — only requires `kotlin-stdlib` and `jpos` which your app already has

---

## 7 — Manual control API

```kotlin
visualizer.reconnect()  // force reconnect (e.g. after switching networks)
visualizer.close()      // permanently shut down — call on app exit
```

---

## 8 — Wire format

Each forwarded message looks like:

```xml
<visualizer-event realm="channel/..." at="2026-05-22T08:20:11.266Z" lifespan="19ms" tag="send">
  <isomsg direction="outgoing">
    <field id="0" value="0200"/>
    <field id="2" value="4111111111111111"/>
    <!-- ... all ISO 8583 fields ... -->
  </isomsg>
</visualizer-event>
---END---
```

The `---END---` delimiter tells the desktop server where each event ends so it can parse multiple events over a persistent connection.
