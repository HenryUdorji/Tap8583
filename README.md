# Tap8583 — Real-Time ISO 8583 Payment Message Visualizer & Semantic Decoder

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-purple.svg?style=flat&logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Tap8583** is a state-of-the-art Compose Multiplatform desktop application that serves as a real-time log visualizer, transaction matcher, and semantic decoder for **ISO 8583 payment messages** (standard payment gateway formats used by jPOS, POS terminals, and NIBSS switches). 

It features an embedded, non-blocking TCP server that ingests visualizer XML streams from POS devices or backend servers, matches requests to responses, decodes complex binary payloads into human-readable details, and inspects raw XML streams side-by-side in a syntax-highlighted packet viewer.

---

## 📸 Screenshots & Walkthrough

> [!WARNING]
> **No Public Screenshots or Walkthrough Media Available**
>
> In accordance with strict payment security guidelines (PCI-DSS) and confidentiality requirements, this repository does **not** host public screenshots or recordings of the user interface. Because **Tap8583** processes and displays live ISO 8583 payment messages, any functional UI capture would inevitably expose highly sensitive real-world payment credentials (such as Primary Account Numbers (PANs), Track 2 data, PIN blocks, and customer transaction history).
>
> To test the application's stunning, modern, and dark-mode styled Compose interface safely, please compile and launch the application locally or run the pre-built native installer on non-production test data.

---

## 🚀 Key Features

* **⚡ Real-Time TCP Ingestion**: Spawns a background thread TCP Server that accepts persistent, low-overhead visualizer XML socket streams from connected clients.
* **🔗 Dynamic Transaction Matching**: Tracks and matches outgoing requests and incoming responses by STAN (DE11) within a configurable sliding window, calculating precise RTTs.
* **🏷️ Full MTI Decoding**: Decodes the 4-digit Message Type Indicator (DE0) into version, class (Authorization, Financial, Reversal), function (Request, Response, Advice), and origin.
* **🎨 Color-Coded Semantic Categories**: Displays decoded fields using specialized, harmonious color accents mapped to card data, amounts, security, EMV, etc.
* **📦 Specialized Custom Field Decoders**:
  * **Processing Code (DE3)**: Digits parsed into transaction types, source, and destination accounts.
  * **Track 2 (DE35)**: Formatted, Luhn-verified, and expiration-extracted card details.
  * **Private Reference (DE59)**: Formatted Terminal ID, PTSP Reference, and RRN trace values.
  * **Additional Amounts (DE54)**: POS-compatible decoding separating account types, balance types, currency, credit/debit sign, and floating-point amount values.
  * **Terminal/Merchant TLV (DE62)**: Extracts custom POS merchant ID, merchant location, currency names, and Merchant Category Codes (MCCs) via integrated standard lookups.
* **👁️ Syntax-Highlighted Raw Viewer**: Mini-regex compiler in Compose that highlights tag names, attributes, values, and comments inside raw XML streams in real time.
* **🔌 Robust Client Log Listener (`listenerLib`)**: A lightweight, resilient logger plugin (`VisualizerLogListener`) for jPOS apps that operates in a background daemon thread, catching and retrying connections with dynamic reflection-based compatibility hacks for older jPOS environments.

---

## 📂 Project Structure

```
Tap8583/
├── desktopApp/      # Compose Multiplatform desktop UI (Kotlin, Jetpack Compose)
├── listenerLib/     # Client jPOS Log Listener library (Java/Android debug utility)
│   └── INTEGRATION.md # Integration documentation for integrating jPOS with the app
├── shared/          # Core Kotlin Multiplatform shared code
│   ├── jvmMain/     # Ingestion server, TCP sockets, SAX parsers, & Field Decoders
│   └── jvmTest/     # Broad JUnit test suite validating XML parsing & decoders
└── build.gradle.kts # Multi-project Gradle build configuration
```

---

## 🛠️ How to Build & Run

### Prerequisites
* **JDK 17 or higher** installed on your system.

### Build and Run the Desktop App

Use the following Gradle commands to build or launch the application:

* **Run the Desktop App**:
  ```bash
  ./gradlew :desktopApp:run
  ```

* **Hot-Reload App Development**:
  ```bash
  ./gradlew :desktopApp:hotRun --auto
  ```

### Build the Client Log Listener JAR
To compile the `listenerLib` dependency for your Android/POS projects, run:
```bash
./gradlew :listenerLib:assemble
```
The output JAR will be generated at:
`listenerLib/build/libs/visualizer-log-listener-1.0.0.jar`

For detailed client integration steps, see the [Integration Guide](file:///Users/Ifechukwu/Work/Kmp/Tap8583/listenerLib/INTEGRATION.md).

---

## 📦 Native Pre-built Installers

To test **Tap8583** without installing JDKs, Gradle, or setting up a local development environment, you can download the pre-compiled native installer for your operating system directly from the **[GitHub Releases](https://github.com/your-username/your-repo/releases)** page:

* **🍏 macOS (Apple Silicon & Intel)**: Download `Tap8583-1.0.0.dmg` / `Tap8583-1.0.0.pkg`
* **🪟 Windows (x64)**: Download `Tap8583-1.0.0.msi` / `Tap8583-1.0.0.exe`
* **🐧 Linux (Debian/Ubuntu)**: Download `Tap8583-1.0.0.deb`

### Packaging Installers Manually
If you want to compile and package the native distributable installer for your current host operating system yourself, run:

```bash
# Package the native installer (creates .dmg on macOS, .msi on Windows, .deb on Linux)
./gradlew :desktopApp:package

# Or generate a runnable native distributable folder (unpackaged executable)
./gradlew :desktopApp:createDistributable
```

The generated distributable binary or installer package will be located in:
`desktopApp/build/compose/binaries/main/`

---

## 🧪 Running Tests

A comprehensive JUnit test suite is provided to validate both ingestion parser reliability and semantic ISO 8583 decoding rules.

To run all shared JVM tests:
```bash
./gradlew :shared:jvmTest
```

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.