# Tap8583 — Real-Time ISO 8583 Payment Message Visualizer & Semantic Decoder

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-JVM-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Next.js](https://img.shields.io/badge/Next.js-15-black.svg?style=flat&logo=next.js)](https://nextjs.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Tap8583** is a cloud-powered, real-time log visualizer, transaction matcher, and semantic decoder for **ISO 8583 payment messages** (standard payment gateway formats used by jPOS, POS terminals, and NIBSS switches).

It features a high-performance **Kotlin / Ktor Ingestion Server** (deployable on Hetzner VPS / Docker) and a sleek **Next.js Web Visualizer** (deployable on Vercel), backed by a zero-configuration Android client logger (`listenerLib`).

---

## 🚀 Key Features

* **☁️ Zero-Config Cloud Ingestion**: POS devices stream logs directly over HTTPS to the cloud backend. QA testers and developers no longer need to find local IP addresses or configure ADB port forwarding.
* **📱 Android Hardware Auto-Detection**: `listenerLib` automatically retrieves POS hardware serial numbers (`Build.getSerial()`, `Settings.Secure.ANDROID_ID`, and vendor system properties for PAX, Telpo, Nexgo, Sunmi) to tag transaction streams seamlessly.
* **🔗 Real-Time STAN Matching & RTT**: Matches outgoing requests and incoming responses by STAN (DE11) across active devices and calculates precise millisecond round-trip times (RTT).
* **🏷️ Interactive 4-Digit MTI Breakdown**: Visually decodes Version, Message Class, Function, and Origin in interactive tiles.
* **🌲 Hierarchical EMV / BER-TLV Tree**: Recursively parses Field 55 ICC payloads with byte offsets, AID scheme recognition (Verve, Visa, Mastercard), and bit-by-bit Terminal Verification Results (TVR Tag 95) security checks.
* **🔍 Request / Response Diff Viewer**: Side-by-side comparison highlighting new, matching, and modified fields.
* **👁️ Syntax-Highlighted Raw XML Stream**: Real-time syntax-highlighted packet inspection with one-click copy.
* **🔒 Team Workspace Security & Masking**: Scoped by Workspace API Key to prevent snooping across sequential POS serial numbers. Automatic masking of PANs, Track 2, and PIN blocks with role-based reveal controls.

---

## 📂 Project Structure

```
Tap8583/
├── listenerLib/     # Client jPOS Log Listener library (Android debug utility)
│   └── INTEGRATION.md # Integration documentation for Android POS apps
├── serverApp/       # Kotlin / Ktor Cloud Backend (HTTP Ingestion, WebSockets, Dockerfile)
├── shared/          # Core Kotlin ISO 8583 SAX parsers, STAN matcher & field decoders
├── webApp/          # Next.js 15 Web Application (App Router, Tailwind CSS, TypeScript)
└── build.gradle.kts # Multi-project Gradle build configuration
```

---

## 🛠️ How to Build & Run

### 1. Build the Client Log Listener (`listenerLib`)
To compile the `listenerLib` JAR for your Android / POS projects:
```bash
./gradlew :listenerLib:assemble
```
The output JAR will be generated at:
`listenerLib/build/libs/visualizer-log-listener-1.0.0.jar`

### 2. Run the Ingestion Server (`serverApp`)
```bash
# Run locally
./gradlew :serverApp:run

# Or run with Docker
docker build -f serverApp/Dockerfile -t tap8583-server .
docker run -p 8080:8080 -e TAP8583_API_KEY="tap_live_secret" tap8583-server
```

### 3. Run the Web Application (`webApp`)
```bash
cd webApp
npm install
npm run dev
```
Open `http://localhost:3000` in your browser.

---

## 🧪 Running Tests

To run the shared JUnit test suite validating ISO 8583 parsing and semantic decoding:
```bash
./gradlew :shared:jvmTest
```

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.