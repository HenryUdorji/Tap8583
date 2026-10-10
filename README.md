# Tap8583 — Real-Time ISO 8583 Payment Message Visualizer & Semantic Decoder

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-JVM-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Next.js](https://img.shields.io/badge/Next.js-15-black.svg?style=flat&logo=next.js)](https://nextjs.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Tap8583** is a cloud-powered, real-time log visualizer, transaction matcher, and semantic decoder for **ISO 8583 payment messages** (standard payment gateway formats used by jPOS, POS terminals, and NIBSS switches).

It features a high-performance **Kotlin / Ktor Ingestion Server** and a sleek **Next.js Web Visualizer**, deployed together on a Hetzner server with Docker Compose, backed by a zero-configuration Android client logger (`listenerLib`).

---

## 🚀 Key Features

* **☁️ Zero-Config Cloud Ingestion**: POS devices stream logs directly over HTTPS to the cloud backend. QA testers and developers no longer need to find local IP addresses or configure ADB port forwarding.
* **📱 Android Hardware Auto-Detection**: `listenerLib` automatically retrieves POS hardware serial numbers (`Build.getSerial()`, `Settings.Secure.ANDROID_ID`, and vendor system properties for PAX, Telpo, Nexgo, Sunmi) to tag transaction streams seamlessly.
* **🔗 Real-Time STAN Matching & RTT**: Matches outgoing requests and incoming responses by STAN (DE11) across active devices and calculates precise millisecond round-trip times (RTT).
* **🏷️ Interactive 4-Digit MTI Breakdown**: Visually decodes Version, Message Class, Function, and Origin in interactive tiles.
* **🌲 Hierarchical EMV / BER-TLV Tree**: Recursively parses Field 55 ICC payloads with byte offsets, AID scheme recognition (Verve, Visa, Mastercard), and bit-by-bit Terminal Verification Results (TVR Tag 95) security checks.
* **🔍 Request / Response Diff Viewer**: Side-by-side comparison highlighting new, matching, and modified fields.
* **👁️ Syntax-Highlighted Raw XML Stream**: Real-time syntax-highlighted packet inspection with one-click copy.
* **📋 Paste or Upload a Log**: Decode jPOS logger output, Logcat lines or listener events without a terminal. Requests and responses are paired, card data is masked, and nothing is stored.
* **🔌 Channel Errors Next to the Transaction**: Read timeouts, host disconnects and failed connects reported by jPOS show on the transaction they affected.
* **↩️ Reversal Linking**: 0420/0400 reversals link to the transaction they reverse (via DE90 or the STAN), in both directions.
* **📊 Stats & Alerts**: Approval rate, average and p95 round trip, and pop-up alerts for timeouts, host errors, slow responses and integration-type declines.
* **👥 Accounts & Workspaces**: Sign in with an emailed link, create a workspace, invite your team, and give each POS app its own revocable terminal key. Transaction history is kept in Postgres.
* **🔒 Server-Side Masking**: PAN, Track 2, expiry, PIN block and the card data inside DE55 are masked on the server before anything is stored or shown.

---

## 📂 Project Structure

```
Tap8583/
├── listenerLib/     # Client jPOS Log Listener library (Android debug utility)
│   └── INTEGRATION.md # Integration documentation for Android POS apps
├── serverApp/       # Kotlin / Ktor Cloud Backend (HTTP Ingestion, WebSockets, Dockerfile)
├── deploy/          # Hetzner deployment: Docker Compose, Caddyfile, .env.example, guide
├── shared/          # Core Kotlin ISO 8583 SAX parsers, STAN matcher & field decoders
├── webApp/          # Next.js 15 Web Application (App Router, Tailwind CSS, TypeScript)
└── build.gradle.kts # Multi-project Gradle build configuration
```

---

## 🛠️ How to Build & Run

### 1. Use or Build the Client Log Listener (`listenerLib`)
POS apps add it from Maven Central:
```kotlin
debugImplementation("dev.sarari:tap8583-listener:1.0.0")
```
See [`listenerLib/INTEGRATION.md`](listenerLib/INTEGRATION.md) for setup and for publishing new versions. To build the JAR yourself:
```bash
./gradlew :listenerLib:assemble
```
The output JAR will be generated at:
`listenerLib/build/libs/tap8583-listener-1.0.0-SNAPSHOT.jar`

### 2. Run the Ingestion Server (`serverApp`)

The server has two modes. With `DATABASE_URL` set it runs with accounts: people sign in with an emailed link and each workspace has its own terminal keys. Without it, it falls back to one shared viewer key and one ingest key, with history in memory.

```bash
# Accounts mode against a local Postgres; sign-in links are printed to the log instead of emailed
DATABASE_URL=jdbc:postgresql://localhost:5432/tap8583 DATABASE_USER=postgres DATABASE_PASSWORD=postgres \
TAP8583_LOG_EMAIL_LINKS=true TAP8583_APP_URL=http://localhost:3000 ./gradlew :serverApp:run

# Shared-key mode (if no key is set, a temporary viewer key is generated and logged)
TAP8583_VIEWER_KEY=dev-viewer TAP8583_INGEST_KEY=dev-ingest ./gradlew :serverApp:run

# Or run with Docker
docker build -f serverApp/Dockerfile -t tap8583-server .
docker run -p 8080:8080 -e TAP8583_VIEWER_KEY=dev-viewer -e TAP8583_INGEST_KEY=dev-ingest tap8583-server
```

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Postgres connection. Turns on accounts mode; the schema is migrated on startup. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM` | Outgoing email for sign-in links and invites (accounts mode). |
| `TAP8583_LOG_EMAIL_LINKS` | `true` logs sign-in and invite links instead of emailing them. Local development only. |
| `TAP8583_PUBLIC_URL` | The server's public URL. `https` turns on secure cookies. |
| `TAP8583_APP_URL` | Where the dashboard lives, for links in emails. Defaults to the public URL. |
| `TAP8583_RETENTION_DAYS` | Days of transaction history to keep (default 30). |
| `TAP8583_VIEWER_KEY` | Shared-key mode: dashboard key for read, clear and simulate. |
| `TAP8583_INGEST_KEY` | Shared-key mode: terminal key that can only POST events. Defaults to the viewer key. |
| `TAP8583_API_KEY` | Legacy single key, used for both when the two above are unset. |
| `TAP8583_ALLOWED_ORIGINS` | Comma-separated browser origins allowed by CORS. Defaults to `http://localhost:3000`. |
| `TAP8583_MASK_CARD_DATA` | `true` (default) masks card data server-side. |

### 3. Run the Web Application (`webApp`)
```bash
cd webApp
npm install
NEXT_PUBLIC_SERVER_URL=http://localhost:8080 npm run dev
```
Open `http://localhost:3000`. In accounts mode, enter your email and open the link from the server log; in shared-key mode, enter the viewer key.

### 4. Deploy to Hetzner
See [`deploy/HETZNER_DEPLOYMENT.md`](deploy/HETZNER_DEPLOYMENT.md): one server running the backend, the dashboard and Caddy (automatic HTTPS) with Docker Compose, deployed from GitHub Actions.

---

## 🧪 Running Tests

To run the shared JUnit test suite validating ISO 8583 parsing and semantic decoding:
```bash
./gradlew :shared:jvmTest
```

Server tests. The accounts tests need an empty Postgres database and are skipped without one:
```bash
TAP8583_TEST_DATABASE_URL="jdbc:postgresql://localhost:5432/tap8583_test?user=postgres" ./gradlew :serverApp:test
```

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.