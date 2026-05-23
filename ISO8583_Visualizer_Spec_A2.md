# ISO 8583 Packet Visualizer — Architecture & UI Specification
## Approach: jPOS Log Stream Interception

**Version:** 2.0
**Platform:** Kotlin (JVM), Desktop Application
**Replaces:** Approach A (Transparent TCP Proxy)
**Status:** Specification Draft

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Why this Approach — The jPOS Reality](#2-why-approach-a2--the-jpos-reality)
3. [How jPOS Logging Works](#3-how-jpos-logging-works)
4. [Approach — Architecture Overview](#4-approach-a2--architecture-overview)
5. [The Bridge: Custom jPOS LogListener](#5-the-bridge-custom-jpos-loglistener)
6. [Ingestion Modes](#6-ingestion-modes)
7. [ISO 8583 Semantic Decoding Engine](#7-iso-8583-semantic-decoding-engine)
8. [UI Specification — The Crux](#8-ui-specification--the-crux)
9. [Data Models](#9-data-models)
10. [Technology Stack](#10-technology-stack)
11. [Configuration & Setup](#11-configuration--setup)
12. [Error Handling & Edge Cases](#12-error-handling--edge-cases)
13. [Security Considerations](#13-security-considerations)
14. [Phased Delivery Plan](#14-phased-delivery-plan)

---

## 1. Executive Summary

The ISO 8583 Packet Visualizer is a desktop developer tool that captures live ISO 8583 financial messages logged by a jPOS-based POS application, then presents those messages in a structured, human-readable, and visually navigable interface.

The tool targets developers, testers, and integration engineers working with jPOS-based POS terminals communicating with payment switches such as NIBSS. Its goal is to eliminate the pain of reading raw jPOS XML log dumps — wall-of-text outputs like the one below — and replace that experience with a structured, real-time, field-by-field visual breakdown:

```xml
<!-- What developers currently read -->
<isomsg direction="outgoing">
  <field id="0" value="0200"/>
  <field id="2" value="5061240207416432753"/>
  <field id="3" value="000000"/>
  <field id="4" value="000000000001"/>
  <field id="52" value="26B7D3B168E69591" type="binary"/>
  <field id="55" value="820258008407A0000003710001950502800480009A03..." />
  ...
</isomsg>
```

Approach works by injecting a lightweight custom `LogListener` into the jPOS `Q2` configuration on the POS side. This listener forwards every `<isomsg>` log event to the visualizer in real time. Because jPOS has already decoded the raw bytes, bitmap, BCD encoding, and field lengths before logging, the visualizer receives clean, already-parsed field data — no raw byte parsing, no SSL fight, no TCP framing concerns.

---

## 2. Why this Approach — The jPOS Reality

### 2.1 What the jPOS Log Already Gives You

jPOS's built-in logging infrastructure is exceptional. When a `Channel` has a `LogListener` attached (which is default jPOS configuration), it logs every outgoing and incoming ISO message in fully-decoded XML format:

```xml
<log realm="channel/196.6.103.18:4018" at="2026-05-21T14:20:58.266" lifespan="19ms">
  <send>
    <isomsg direction="outgoing">
      <!-- com.basepos.pos.host.iso.transaction.IsoPackager -->
      <field id="0"  value="0200"/>
      <field id="2"  value="5061240207416432753"/>
      <field id="35" value="5061240207416432753D2702601013660902"/>
      <field id="52" value="26B7D3B168E69591" type="binary"/>
      <field id="55" value="820258008407A000000371000195..." />
    </isomsg>
  </send>
</log>
```

This means jPOS has already handled:
- Raw byte reading from the TCP stream
- SSL decryption
- Length prefix parsing (2-byte or 4-byte header)
- Bitmap expansion (primary, secondary, tertiary)
- BCD / ASCII / EBCDIC field decoding
- LLVAR / LLLVAR length extraction
- Binary field hex encoding (DE52, DE55, DE128)

The visualizer can consume this output directly. The heavy lifting is done.

### 2.3 Why the Log Format Is Reliable

jPOS's `ISOMsg.dump()` method produces deterministic XML. The format has not changed significantly across major jPOS versions. Field IDs are always numeric, values are always strings (with an optional `type="binary"` attribute for raw byte fields). This is a stable, well-defined contract.

### 2.4 The Three-Bitmap Implication

The attached log shows Field 128 is present (`type="binary"`, 32-byte hex value). Field 128 being present means this is a **tertiary bitmap** message — a 192-bit (24-byte) bitmap structure. jPOS handles this transparently in its packager; the logged output simply includes field 128 as a data field. The visualizer must understand that DE128 is the tertiary bitmap MAC, not a regular data field.

### 2.5 NIBSS-Specific Observations from the Sample Log

| Observation | Implication |
|---|---|
| Connection to `196.6.103.18:4018` | NIBSS NIP/POS switch endpoint |
| MTI `0200` | Financial Transaction Request (purchase) |
| DE2: `5061240207416432753` | Verve card (506124 BIN prefix) |
| DE49: `566` | NGN — Nigerian Naira |
| DE55: long hex string | EMV chip transaction, TLV data present |
| DE52: `26B7D3B168E69591` | Encrypted PIN block (8 bytes, 3DES) |
| DE128: 32-byte binary | NIBSS 3DES MAC (message authentication code) |
| DE123: `51010151134C101` | NIBSS-specific terminal capability field |
| DE59: `2214OTGF-P332600027425-614114086503` | Terminal/merchant reference composite |
| DE32: `506124` | Acquiring institution ID (Verve/Interswitch) |

---

## 3. How jPOS Logging Works

Understanding the jPOS logging pipeline is essential for designing the bridge correctly.

### 3.1 jPOS Log Architecture

```
jPOS Q2 Container
│
├── Channel (e.g. NACChannel, XMLChannel, ASCIIChannel over SSLSocketFactory)
│     │
│     ├── sends/receives ISOMsg objects
│     └── fires LogEvents to attached LogListeners
│
├── Logger
│     ├── LogListener 1: SimpleLogListener → writes to stdout/file
│     └── LogListener 2: [YOUR CUSTOM LISTENER] → forwards to visualizer
│
└── QBean (transaction participants, mux, etc.)
```

### 3.2 LogEvent Structure

Every jPOS `LogEvent` contains:
- `realm`: identifies the channel/component (e.g. `channel/196.6.103.18:4018`)
- `at`: ISO 8601 timestamp of the event
- `lifespan`: milliseconds since the event was created (useful for RTT calculation)
- `tag`: the event type (`send`, `receive`, `connect`, `disconnect`, `error`, etc.)
- `payload`: one or more objects — for ISO message events, an `ISOMsg` that serializes as `<isomsg>`

### 3.3 Log Output Modes

jPOS can write logs to multiple destinations:

| Mode | Description | Visualizer Integration |
|---|---|---|
| **stdout** (Android logcat) | Default for Android/POS apps | Logcat stream reader |
| **File** | `SimpleLogListener` to a file | File tail watcher |
| **Custom LogListener** | Injected at Q2 config level | Direct socket/pipe forward |
| **Remote logger** | Custom `LogListener` sending over network | TCP socket to visualizer |

---

## 4. this Approach — Architecture Overview

### 4.1 System Diagram

```
┌────────────────────────────────────────────────────────────────┐
│  Android POS Terminal (jPOS Q2)                                │
│                                                                │
│  ┌──────────┐    ┌──────────┐    ┌──────────────────────────┐ │
│  │ ISO Msg  │───▶│ NACChan  │───▶│ NIBSS Switch (SSL/TLS)   │ │
│  │ Builder  │    │ (SSLChan)│    └──────────────────────────┘ │
│  └──────────┘    └────┬─────┘                                 │
│                       │ LogEvent                               │
│                  ┌────▼─────┐                                 │
│                  │  Logger  │                                  │
│                  │          │                                  │
│           ┌──────┴──┐  ┌────┴──────────────────┐             │
│           │ Simple  │  │  VisualizerLogListener │             │
│           │ LogList │  │  (custom, injected)    │             │
│           │ (stdout)│  └────────────┬───────────┘             │
│           └─────────┘               │                         │
│                          localhost:9999 via ADB forward
└──────────────────────────────────── │ ─────────────────────── ┘
                                      │
                          ┌───────────▼────────────────────────┐
                          │   ISO 8583 Visualizer (Desktop)    │
                          │                                    │
                          │  ┌────────────┐  ┌─────────────┐  │
                          │  │ Ingestion  │  │ XML Parser  │  │
                          │  │ Server     │─▶│ (jPOS fmt)  │  │
                          │  └────────────┘  └──────┬──────┘  │
                          │                         │          │
                          │                  ┌──────▼──────┐  │
                          │                  │  Semantic   │  │
                          │                  │  Decoder    │  │
                          │                  └──────┬──────┘  │
                          │                         │          │
                          │                  ┌──────▼──────┐  │
                          │                  │  UI Layer   │  │
                          │                  │  (Compose)  │  │
                          │                  └─────────────┘  │
                          └────────────────────────────────────┘
```

### 4.2 Data Flow

```
POS builds ISOMsg
  → jPOS Channel sends to NIBSS (SSL encrypted — visualizer never touches this)
  → jPOS Logger fires LogEvent with direction="outgoing"
  → VisualizerLogListener.log(LogEvent) called
  → Listener serializes <isomsg> XML fragment to string
  → Sends over TCP socket to visualizer (localhost:9999 via ADB forward)

NIBSS sends response
  → jPOS Channel receives bytes, decodes ISOMsg
  → jPOS Logger fires LogEvent with direction="incoming"
  → VisualizerLogListener forwards <isomsg> XML
  → Visualizer receives, matches to outgoing by STAN (DE11)
  → Renders request/response pair in UI
```

### 4.3 Transport Options (POS to Visualizer)

| Option | Setup | Use Case |
|---|---|---|
| **ADB TCP Forward** | `adb forward tcp:9999 tcp:9999` | Android POS via USB cable to dev machine |

**Recommended for development:** ADB TCP Forward — zero network config, works with USB, no firewall issues.

---

## 5. The Bridge: Custom jPOS LogListener

This is the only code that needs to be added to the POS application. It is small, non-invasive, and purely additive.

### 5.1 VisualizerLogListener Implementation

This needs to be a jar lib that the pos app would add as a dependency, then we should not use any xml config this class should directly accept a port no only as we are using adb tcp forwarding

```kotlin
/**
 * jPOS LogListener that forwards ISO message log events to the
 * ISO 8583 Visualizer desktop application over a TCP socket.
 *
 * Add to jPOS Q2 logger configuration in 10_logger.xml:
 *   <logger name="Q2">
 *     <log-listener class="com.yourapp.VisualizerLogListener">
 *       <property name="host" value="localhost"/>
 *       <property name="port" value="9999"/>
 *     </log-listener>
 *   </logger>
 */
class VisualizerLogListener : LogListener, Configurable {

    private var host: String = "localhost"
    private var port: Int = 9999
    private var enabled: Boolean = true

    @Volatile private var writer: PrintWriter? = null
    @Volatile private var connected: Boolean = false

    override fun setConfiguration(cfg: Configuration) {
        host = cfg.get("host", "localhost")
        port = cfg.getInt("port", 9999)
        enabled = cfg.getBoolean("enabled", true)
        if (enabled) connectAsync()
    }

    override fun log(ev: LogEvent): LogEvent {
        if (!enabled || !connected) return ev

        // Only forward send/receive events that contain an ISOMsg
        if (ev.tag !in listOf("send", "receive")) return ev
        val isoMsg = ev.payLoad.filterIsInstance<ISOMsg>().firstOrNull() ?: return ev

        try {
            val sb = StringBuilder()
            sb.append("<visualizer-event")
            sb.append(" realm=\"${ev.realm}\"")
            sb.append(" at=\"${ev.createdAt.toInstant()}\"")
            sb.append(" lifespan=\"${ev.lifespan}ms\"")
            sb.append(" tag=\"${ev.tag}\"")
            sb.append(">\n")
            isoMsg.dump(sb, "  ")    // jPOS built-in XML dump
            sb.append("</visualizer-event>\n")
            sb.append("---END---\n") // delimiter: tells visualizer packet is complete

            writer?.println(sb.toString())
            writer?.flush()
        } catch (e: Exception) {
            connected = false
            connectAsync()  // reconnect silently; never block the pipeline
        }

        return ev  // ALWAYS return ev — never drop or block the log event
    }

    private fun connectAsync() {
        Thread {
            repeat(5) { attempt ->
                try {
                    val socket = Socket(host, port)
                    writer = PrintWriter(socket.outputStream.bufferedWriter())
                    connected = true
                    return@Thread
                } catch (e: Exception) {
                    Thread.sleep(2000L * (attempt + 1))  // exponential backoff
                }
            }
        }.also { it.isDaemon = true }.start()
    }
}
```

### 5.2 jPOS Q2 Configuration (XML)

In the POS's `cfg/10_logger.xml` (or equivalent Q2 deploy descriptor):

```xml
<logger name="Q2">
  <!-- Existing log listener — kept unchanged -->
  <log-listener class="org.jpos.util.SimpleLogListener">
    <property name="file" value="log/q2.log"/>
  </log-listener>

  <!-- Add this block — visualizer bridge -->
  <log-listener class="com.yourapp.iso.VisualizerLogListener">
    <property name="host" value="localhost"/>
    <property name="port" value="9999"/>
    <property name="enabled" value="true"/>
  </log-listener>
</logger>
```

**Key design decisions in the listener:**

- **Non-blocking:** The listener never throws — it swallows exceptions silently and attempts reconnect in a daemon thread. The ISO transaction pipeline is never affected.
- **Reconnect with backoff:** If the visualizer is not running, the listener retries up to 5 times with exponential backoff. When the visualizer starts, connection is established within ~10 seconds.
- **Delimiter framing:** Each event ends with `---END---\n` so the visualizer's stream reader knows where one event ends regardless of TCP buffering or fragmentation.
- **Daemon thread:** The reconnect thread is a daemon thread and does not prevent JVM/app shutdown.
- **Configurable:** Host and port are Q2 properties — no recompile needed to change targets.

---

## 6. ISO 8583 Semantic Decoding Engine

Since jPOS has already decoded the raw bytes into field ID / value pairs, the semantic decoder transforms those string values into human-readable meaning. This is pure domain logic — no byte-level parsing.

### 6.1 Input Contract

```kotlin
data class RawISOMessage(
    val direction: Direction,       // OUTGOING, INCOMING
    val realm: String,              // "channel/196.6.103.18:4018"
    val capturedAt: Instant,
    val lifespanMs: Long,           // jPOS lifespan = channel processing time
    val fields: Map<Int, RawField>  // field id → value
)

data class RawField(
    val id: Int,
    val value: String,              // always string as logged by jPOS
    val isBinary: Boolean           // true when type="binary" in XML
)
```

### 6.2 MTI Decoding

MTI is always DE0 in jPOS XML output.

| Digit | Position | Meaning |
|---|---|---|
| 1 | Version | 0=1987, 1=1993, 2=2003 |
| 2 | Class | 1=Authorization, 2=Financial, 4=Reversal, 8=Network Mgmt |
| 3 | Function | 0=Request, 1=Request Response, 2=Advice, 3=Advice Response, 4=Notification |
| 4 | Origin | 0=Acquirer, 1=Acquirer Repeat, 2=Issuer, 3=Issuer Repeat |

Common decoded labels:
- `0100` → Authorization Request
- `0110` → Authorization Request Response
- `0200` → Financial Transaction Request
- `0210` → Financial Transaction Response
- `0400` → Reversal Request
- `0410` → Reversal Request Response
- `0800` → Network Management Request
- `0810` → Network Management Response

### 6.3 Field Dictionary

All 128 standard fields are defined in the schema, plus NIBSS-specific private fields. Each definition includes:

```kotlin
data class FieldDefinition(
    val number: Int,
    val name: String,
    val shortName: String,
    val dataType: String,       // n, an, ans, b, z
    val isoType: String,        // FIXED, LLVAR, LLLVAR
    val maxLength: Int,
    val description: String,
    val sensitive: Boolean,
    val category: FieldCategory,
    val decoder: ((String) -> DecodedField)?
)

enum class FieldCategory {
    CARD_DATA,        // red    — DE2, DE14, DE35, DE45
    AMOUNT,           // green  — DE4, DE5, DE6, DE28, DE29, DE30
    DATETIME,         // blue   — DE7, DE12, DE13, DE15, DE17
    IDENTIFICATION,   // purple — DE11, DE37, DE38, DE41, DE42
    RESPONSE,         // amber  — DE39, DE44
    POS_TERMINAL,     // teal   — DE22, DE25, DE26
    EMV,              // orange — DE23, DE55, DE56
    INSTITUTION,      // indigo — DE32, DE33, DE100
    SECURITY,         // crimson — DE52, DE53, DE64, DE128
    PRIVATE_NIBSS,    // grey   — DE59, DE60, DE123
    NETWORK           // slate  — DE70, DE71, DE73
}
```

### 6.4 Field-Specific Decoders

#### DE3 — Processing Code
```
"000000"
  Txn Type:   00 → Purchase
  From Acct:  00 → Default/Unspecified
  To Acct:    00 → Default/Unspecified
```

#### DE4 — Amount, Transaction
```
"000000000001"
  Minor units: 1
  Currency from DE49 (566 = NGN, 2 decimal places)
  Formatted: ₦ 0.01
```

#### DE7 — Transmission Date/Time
```
"0521142037"
  MMDDhhmmss → May 21, 14:20:37
```

#### DE18 — Merchant Category Code
```
"6012" → Financial Institutions — Merchandise and Services
```
Full ISO 18245 MCC table (870+ codes) included.

#### DE22 — POS Entry Mode
```
"051"
  First 2 digits: 05 → Integrated Circuit Card (Chip)
  Last digit:      1 → PIN Entered
  Summary: Chip + PIN
```

| Code | Meaning |
|---|---|
| 01 | Manual (key-entered) |
| 05 | ICC / Chip |
| 07 | Contactless ICC (NFC/tap) |
| 10 | Credential on File |
| 90 | Magnetic Stripe |
| 91 | Contactless Magnetic Stripe |

#### DE32 — Acquiring Institution ID
```
"506124" → Verve International / Interswitch
```
IIN-based lookup table for Nigerian acquirers and international schemes.

#### DE35 — Track 2 Data
```
"5061240207416432753D2702601013660902"
  PAN:           5061240207416432753   [masked]
  Separator:     D (=)
  Expiry:        2702 → Feb 2027
  Service Code:  601
  Discretionary: 013660902
```

#### DE39 — Response Code

Full lookup table (60+ codes). Selected:

| Code | Meaning | Severity |
|---|---|---|
| 00 | Approved | ✅ Success |
| 01 | Refer to Card Issuer | ⚠️ Warning |
| 05 | Do Not Honour | ❌ Declined |
| 12 | Invalid Transaction | ❌ Declined |
| 14 | Invalid Card Number | ❌ Declined |
| 51 | Insufficient Funds | ❌ Declined |
| 54 | Expired Card | ❌ Declined |
| 55 | Incorrect PIN | ❌ Declined |
| 57 | Txn Not Permitted to Cardholder | ❌ Declined |
| 68 | Response Received Too Late | ⚠️ Timeout |
| 91 | Issuer or Switch Inoperative | ❌ Network |
| 96 | System Malfunction | ❌ Error |

#### DE49 — Currency Code
```
"566" → NGN — Nigerian Naira (₦)
```
Full ISO 4217 table included.

#### DE52 — PIN Data
```
"26B7D3B168E69591" (binary, 8 bytes)
  Encrypted PIN block (3DES, ANSI X9.8 format)
  Always masked: ████████████████
  [Show Raw Hex] toggle available for diagnostic use
```

#### DE59 — NIBSS Private Reference
```
"2214OTGF-P332600027425-614114086503"
  Part 1: 2214OTGF       → Terminal ID
  Part 2: P332600027425  → PTSP / Merchant reference
  Part 3: 614114086503   → RRN / Trace reference
```

#### DE123 — NIBSS Terminal Capabilities
```
"51010151134C101"
  Decoded per NIBSS PTSP implementation guide
  Terminal Type, capability flags, terminal class
```

#### DE128 — Message Authentication Code (MAC)
```
"6CFDE6E02F8F5C2203B06B5F8E1AA5C8B649D7A7456A2F721EC39A55B44BC871" (binary, 32 bytes)
  NIBSS 3DES MAC — message integrity check over the full ISO message
  Label: "Message Authentication Code (MAC)"
  Presence implies tertiary bitmap is active
  Shown as hex only; MAC verification is not performed (key not available)
```

### 6.5 EMV / TLV Decoder (Field 55)

Field 55 contains BER-TLV encoded EMV chip data. The decoder recursively parses the hex string.

**Parsing algorithm:**
1. Read tag bytes (1 or 2 bytes; 2-byte if first byte low nibble = `0x1F`)
2. Read length (1 byte if < 0x80; multi-byte otherwise)
3. Read value bytes
4. If tag's class bit indicates "constructed," recurse into value
5. Look up tag in EMV dictionary for name and decode logic

**EMV Tag Dictionary (selected):**

| Tag | Name | Decode |
|---|---|---|
| `82` | Application Interchange Profile | Bit-flag decode |
| `84` | Dedicated File Name | AID → card scheme lookup |
| `95` | Terminal Verification Results | 40-bit TVR, bit-by-bit decode |
| `9A` | Transaction Date | YYMMDD → human date |
| `9C` | Transaction Type | 00=Purchase, 01=Cash, 20=Refund |
| `9F02` | Amount, Authorised | Numeric (minor units) |
| `9F06` | AID — Terminal | AID → scheme lookup |
| `9F09` | Application Version Number | Hex |
| `9F10` | Issuer Application Data | Hex (issuer-proprietary) |
| `9F1A` | Terminal Country Code | ISO 3166 name |
| `9F26` | Application Cryptogram | Hex |
| `9F27` | Cryptogram Information Data | 80=ARQC, 40=TC, 00=AAC |
| `9F33` | Terminal Capabilities | 3-byte bit-flag decode |
| `9F34` | CVM Results | CVM method + condition + result |
| `9F35` | Terminal Type | Lookup table |
| `9F36` | Application Transaction Counter | Decimal |
| `9F37` | Unpredictable Number | Hex |
| `9F41` | Transaction Sequence Counter | Decimal |
| `5F2A` | Transaction Currency Code | ISO 4217 |
| `5F34` | PAN Sequence Number | Decimal |

**Cryptogram types (DE9F27):**
- `80` → ARQC — Authorisation Request Cryptogram (online auth requested)
- `40` → TC — Transaction Certificate (offline approved)
- `00` → AAC — Application Authentication Cryptogram (offline declined)

**TVR (DE95) bit decode** — each of the 40 bits maps to a named check:
- Bit 1: Offline data authentication not performed
- Bit 2: SDA failed
- Bit 3: ICC data missing
- Bit 4: Card on terminal exception file
- Bit 5: DDA failed
- Bit 8: Cardholder verification not performed
- ... (full table in schema)

**AID to card scheme (DE84 / DE9F06):**

| AID Prefix | Scheme |
|---|---|
| `A0000000031010` | Visa Credit/Debit |
| `A0000000032010` | Visa Electron |
| `A0000000041010` | Mastercard |
| `A0000000043060` | Mastercard Maestro |
| `A0000003710001` | Verve |
| `A0000000651010` | JCB |
| `A000000025` | American Express |

---

## 7. UI Specification — The Crux

The UI is the primary value-delivery mechanism. This section specifies every screen, panel, state, and interaction in sufficient detail for direct implementation.

Note: The visualizer should bundle a check at startup — run adb version and if it succeeds, proceed silently. If it fails, show a clear one-time message with a direct link to the Platform Tools download page rather than a cryptic error

### 7.1 Design Philosophy

The UI is built for developers and integration engineers who currently spend hours staring at jPOS XML dumps in logcat. The design goal is radical readability improvement — every decision reduces cognitive load when debugging a failed transaction.

**Visual language:**
- **Dark theme by default.** Hex, field IDs, and timestamps are far more readable on dark backgrounds. Test lab environments are the primary context.
- **Two font families only.** `JetBrains Mono` for all field values, hex, raw data, and MTI digits. A clean humanist sans-serif (IBM Plex Sans or equivalent) for all labels, names, descriptions, and UI chrome.
- **Color as information, never decoration.** Every color has a semantic purpose. No gradient backgrounds, no decorative color.
- **Density with breathing room.** Many fields must coexist. Compact field rows, generous card padding.

### 7.2 Application Window Layout

Three-panel layout with fixed top toolbar. Panels separated by draggable dividers; widths persist in user preferences.

```
┌────────────────────────────────────────────────────────────────────────┐
│  TOOLBAR (48px fixed)                                                  │
├──────────────────────┬──────────────────────────────┬──────────────────┤
│                      │                              │                  │
│  LEFT PANEL          │  CENTER PANEL                │  RIGHT PANEL     │
│  Transaction         │  Packet Inspector            │  Field Detail    │
│  Timeline            │                              │                  │
│  28% default         │  44% default                 │  28% default     │
│                      │                              │                  │
│                      │                              │                  │
├──────────────────────┴──────────────────────────────┴──────────────────┤
│  STATUS BAR (24px fixed)                                               │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 7.3 Toolbar

**Height:** 48px. Full-width.

**Left group:**
```
[▶ Start]  [■ Stop]  [ 🔌 Mode ▾ ]  [🗑 Clear]
```

**Center group:**
```
[ 🔍  Search fields, RRN, STAN, PAN...                    × ]
```

**Right group:**
```
[ All | Auth | Financial ▾ ]   ● Listening :9999   47 packets   [⚙]  [⬆ Export]
```

#### 7.3.1 Control Detail

| Control | Type | Behavior                                               |
|---|---|--------------------------------------------------------|
| **Start** | Primary button, green | Starts the adb tcp forwarding                          |
| **Stop** | Destructive button, red | Stops ingestion; packets remain visible                |
| **Clear** | Ghost button | Clears all sessions; confirms if > 0 packets           |
| **Search** | Text input | Filters timeline by any field value in real-time       |
| **Filter** | Dropdown | All / Auth / Financial / Reversal / Network / Declined |
| **Status pill** | Read-only | Grey=Idle, Green=Listening, Amber=Active, Red=Error    |
| **Packet counter** | Badge | Live count; brief pulse animation on increment         |
| **Settings** | Icon (gear) | Opens right config drawer without hiding packets       |
| **Export** | Icon (upload) | Exports sessions as JSON or plain text                 |

---

### 7.4 Left Panel — Transaction Timeline

#### 7.4.1 Transaction Card

Each card represents a matched request + response pair, or a pending request.

**Approved card:**
```
┌─────────────────────────────────────────────┐
│  ↑  FINANCIAL REQUEST        0200           │
│  ─────────────────────────────────────────  │
│  PAN      ••••  ••••  ••••  6432            │
│  Amount   ₦ 0.01                            │
│  STAN     086503                            │
│  14:20:58.266              Chip + PIN       │
│                                             │
│  ↓  FINANCIAL RESPONSE       0210   ✓ 00   │
│  ─────────────────────────────────────────  │
│  Approved                                   │
│  Auth Code  482901                          │
│  14:20:59.100                   +834ms      │
└─────────────────────────────────────────────┘
```

**Declined card:**
```
┌─────────────────────────────────────────────┐
│  ↑  FINANCIAL REQUEST        0200           │
│  ─────────────────────────────────────────  │
│  PAN      ••••  ••••  ••••  6432            │
│  Amount   ₦ 5,000.00                        │
│  STAN     086503                            │
│  14:20:58.266              Chip + PIN       │
│                                             │
│  ↓  FINANCIAL RESPONSE       0210   ✗ 51   │
│  ─────────────────────────────────────────  │
│  Insufficient Funds                         │
│  14:20:59.100                +834ms  🐢    │
└─────────────────────────────────────────────┘
```

**Pending card:**
```
┌─────────────────────────────────────────────┐
│  ↑  FINANCIAL REQUEST        0200           │
│  ─────────────────────────────────────────  │
│  PAN      ••••  ••••  ••••  6432            │
│  Amount   ₦ 5,000.00                        │
│  STAN     086503                            │
│  14:20:58.266              Chip + PIN       │
│                                             │
│  ⟳  Awaiting response...         +2.1s     │
└─────────────────────────────────────────────┘
```

#### 7.4.2 Card Visual States

| State | Visual |
|---|---|
| Default | Subtle border, standard background |
| Hovered | Slight elevation, brighter background |
| Selected | 3px left accent border, highlighted background |
| Approved | Response row: green left accent, ✓ badge, green response code |
| Declined | Response row: red left accent, ✗ badge, red response code + text |
| Timeout (>3s no response) | Pending row turns amber, ⏱ icon appears |
| Newly arrived | Slide-in from left + fade animation, 300ms |

#### 7.4.3 RTT Color Coding

- < 500ms: green
- 500ms – 2s: amber
- > 2s: red with 🐢 icon

#### 7.4.4 Card Pairing Logic

Request and response are matched by **STAN (DE11)**. Matching window is 60 seconds (configurable). If no response arrives within the window, the card transitions to TIMEOUT state. Orphan responses (no matching request) are shown as standalone cards labeled "Unmatched Response."

#### 7.4.5 Auto-scroll

When the user is scrolled to the bottom, new cards auto-scroll into view. When scrolled up, a **"↓ N new"** pill appears at the bottom. Clicking it scrolls to latest.

---

### 7.5 Center Panel — Packet Inspector

#### 7.5.1 Tab Bar

```
[ Request ↑ ]  [ Response ↓ ]  [ Diff ]  [ Raw ]
```

Request and Response tabs available once respective packets are captured. Diff tab appears when both exist. Raw shows original jPOS XML.

#### 7.5.2 Packet Header Strip

```
┌────────────────────────────────────────────────────────────────────┐
│  ↑ OUTGOING   [0200]  Financial Request          14:20:58.266      │
│  channel/196.6.103.18:4018  ·  lifespan: 19ms  ·  STAN: 086503    │
└────────────────────────────────────────────────────────────────────┘
```

The realm shows the NIBSS endpoint. Lifespan comes from jPOS's own attribute — it is jPOS's internal processing/sending time.

#### 7.5.3 MTI Card

```
┌──────────────────────────────────────────────────────────────────┐
│  MESSAGE TYPE INDICATOR                                          │
│                                                                  │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │    0    │  │    2    │  │    0    │  │    0    │            │
│  │  1987   │  │Financial│  │ Request │  │Acquirer │            │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘            │
│                                                                  │
│  Financial Transaction Request                                   │
└──────────────────────────────────────────────────────────────────┘
```

Each digit rendered as a raised tile with the digit prominent and its semantic meaning below in small text.

#### 7.5.4 Field List

Fields grouped into collapsible sections by category. Section headers are collapsible with animated chevron.

```
▼  CARD DATA
▼  AMOUNTS
▼  DATE & TIME
▼  IDENTIFICATION
▼  POS / TERMINAL
▼  EMV / ICC
▼  NIBSS PRIVATE
▼  SECURITY
```

**Collapsed field row:**
```
┌───────────────────────────────────────────────────────────────────┐
│ ▐  DE 3   PROCESSING CODE                      FIXED n6        ▶ │
│           000000                                                  │
│           Purchase  ·  From: Default  ·  To: Default             │
└───────────────────────────────────────────────────────────────────┘
```

- `▐` — 3px colored left bar (category color)
- `DE N` — field number chip, monospace
- Field name — ALL CAPS, proportional font
- Type descriptor — right-aligned, muted
- Raw value — monospace, full contrast
- Decoded value — italic, secondary color
- `▶` — indicates expandable; rotates to `▼` when expanded

**Expanded field row:**
```
┌───────────────────────────────────────────────────────────────────┐
│ ▐  DE 3   PROCESSING CODE                      FIXED n6        ▼ │
│           000000                                                  │
│           Purchase  ·  From: Default  ·  To: Default             │
│    ┌────────────────────────────────────────────────────────────┐ │
│    │  Sub-fields                                                │ │
│    │  Digits 1–2   00   Transaction Type: Purchase             │ │
│    │  Digits 3–4   00   From Account:     Default/Unspecified  │ │
│    │  Digits 5–6   00   To Account:       Default/Unspecified  │ │
│    │                                                            │ │
│    │  Hex: 30 30 30 30 30 30                      [Copy hex]   │ │
│    └────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**Sensitive field row:**
```
┌───────────────────────────────────────────────────────────────────┐
│ ▐  DE 2   PRIMARY ACCOUNT NUMBER               LLVAR n..19  🔒 ▶ │
│           ••••  ••••  ••••  6432                                  │
│           Verve  ·  19 digits  ·  Luhn: ✓                        │
└───────────────────────────────────────────────────────────────────┘
```

`[Reveal]` button appears on hover or in expanded state.

**EMV field row (collapsed):**
```
┌───────────────────────────────────────────────────────────────────┐
│ ▐  DE 55  ICC DATA (EMV)                      LLLVAR b..255    ▶ │
│           820258008407A00000037100019505...  (187 bytes)          │
│           ARQC  ·  Verve  ·  ATC: 1113  ·  Chip                  │
└───────────────────────────────────────────────────────────────────┘
```

Summary line for DE55 shows the highest-signal decoded values from the TLV tree.

#### 7.5.5 EMV / ICC TLV Tree (DE55 Expanded)

```
DE 55  ICC DATA (EMV)                              187 bytes
│
├─ 82    Application Interchange Profile           2 bytes
│        8200
│        SDA supported  ·  Terminal risk mgmt required
│
├─ 84    Dedicated File Name                       7 bytes
│        A0000003710001
│        Scheme: Verve
│
├─ 95    Terminal Verification Results             5 bytes
│        0502800480
│        ⚠  Offline data auth not performed
│        ⚠  Cardholder verification not performed
│        ✓  Terminal risk management performed
│        ✓  Issuer auth data verified
│
├─ 9A    Transaction Date                          3 bytes
│        260521  →  May 21, 2026
│
├─ 9F02  Amount, Authorised                        6 bytes
│        000000000001  →  ₦ 0.01
│
├─ 9F26  Application Cryptogram                   8 bytes
│        B08F298D5A26BECD
│        ARQC — Online authorisation requested
│
├─ 9F27  Cryptogram Information Data               1 byte
│        80  →  ARQC
│
├─ 9F36  Application Transaction Counter           2 bytes
│        0459  →  1113 (decimal)
│
├─ 9F37  Unpredictable Number                      4 bytes
│        6A04A143
│
└─ 5F34  PAN Sequence Number                       1 byte
         01  →  Sequence: 1
```

TVR (tag `95`) decoded bit-by-bit. Each set bit shown as ⚠ warning or ✓ pass. Unknown tags shown as raw hex with tag ID — they do not cause crashes.

#### 7.5.6 Diff Tab

```
┌─────────────────────────────────────────────────────────────────────┐
│     REQUEST (0200)               │     RESPONSE (0210)              │
├──────────────────────────────────┼─────────────────────────────────-┤
│  DE 0   0200 Financial Req       │  DE 0   0210 Financial Resp      │
│  DE 2   ••••••••••••6432         │  DE 2   ••••••••••••6432         │
│  DE 3   000000  Purchase         │  DE 3   000000  Purchase         │
│  DE 4   000000000001  ₦0.01      │  DE 4   000000000001  ₦0.01      │
│  DE 11  086503                   │  DE 11  086503                   │
│  DE 37  614114086503             │  DE 37  614114086503             │
│  DE 38  (absent)            ──▶  │  DE 38  482901           ✦ new  │
│  DE 39  (absent)            ──▶  │  DE 39  00  Approved     ✦ new  │
│  DE 44  (absent)            ──▶  │  DE 44  ND               ✦ new  │
└──────────────────────────────────┴─────────────────────────────────-┘
```

Fields only in request: blue `✦ new` on the left side.
Fields only in response: green `✦ new` on the right side.
Fields in both with different values: amber highlight on both cells.

#### 7.5.7 Raw Tab

Original jPOS XML with syntax highlighting. Tag names in accent color, attribute names in secondary, values in primary. `[Copy All]` and `[Copy as logcat]` buttons.

```xml
<visualizer-event realm="channel/196.6.103.18:4018"
                  at="2026-05-21T14:20:58.266"
                  lifespan="19ms"
                  tag="send">
  <isomsg direction="outgoing">
    <field id="0"  value="0200"/>
    <field id="2"  value="5061240207416432753"/>
    ...
    <field id="55" value="820258008407A000000371000195..."/>
    <field id="128" value="6CFDE6E02F8F5C22..." type="binary"/>
  </isomsg>
</visualizer-event>
```

---

### 7.6 Right Panel — Field Detail

Deep-dive view for a single selected field. Fully replaced on every field selection.

#### 7.6.1 Panel Header

```
┌────────────────────────────────────────────────┐
│  DE 39  ·  Response Code             [←]  [→]  │
└────────────────────────────────────────────────┘
```

`[←]` / `[→]` navigate to previous/next field in the packet.

#### 7.6.2 Content Layout

```
FIELD NUMBER         39
FULL NAME            Response Code
SHORT NAME           RESP CODE
─────────────────────────────────────────

VALUE                00
                                [Copy]  [Hex]  [JSON]

MEANING              ✅ Approved

DESCRIPTION
  Transaction approved by the issuer.
  No further action required.

─────────────────────────────────────────

FORMAT
  ISO Type:    FIXED
  Data Type:   AN (Alphanumeric)
  Length:      2

RAW BYTES            30 30        [Copy hex]

─────────────────────────────────────────

RESPONSE CODE REFERENCE
  00  Approved                   ← you are here
  01  Refer to Card Issuer
  05  Do Not Honour
  12  Invalid Transaction
  14  Invalid Card Number
  51  Insufficient Funds
  54  Expired Card
  55  Incorrect PIN
  57  Txn Not Permitted
  91  Issuer Unavailable
  96  System Malfunction
  [Show all 60 codes]

CATEGORY             ◆ Response
```

#### 7.6.3 Contextual Content Per Field

| Field | Supplementary Content |
|---|---|
| DE2 (PAN) | Card scheme, BIN range info, Luhn check result, digit count |
| DE3 (Processing Code) | Sub-field decode table |
| DE4 (Amount) | Formatted with currency, minor-unit explanation |
| DE7 (Date/Time) | Parsed timestamp, local timezone |
| DE18 (MCC) | Full category name from ISO 18245 |
| DE22 (Entry Mode) | Full entry mode reference table with current highlighted |
| DE39 (Response Code) | Full response code table with current highlighted + color severity |
| DE49 (Currency) | ISO 4217 name, symbol, decimal places |
| DE55 (ICC) | Summary: cryptogram type, ATC, scheme name |

---

### 7.7 Status Bar

```
● Listening :9999  ·  Sessions: 1  ·  Packets: 23  ·  Approved: 20  ·  Declined: 3  ·  Avg RTT: 312ms  ·  Last: 14:20:58
```

---

### 7.8 Toast Notifications

Bottom-right, stacking, auto-dismiss in 4s:

| Event | Message | Color |
|---|---|---|
| New packet when scrolled up | "↓ 3 new transactions" (clickable) | Blue |
| Parse error | "DE55 TLV parse error at offset 42. Raw hex shown." | Amber |
| High latency | "STAN 086503: RTT 4.2s — possible timeout?" | Red |
| ADB disconnected | "ADB connection lost. Reconnecting..." | Amber |
| POS listener connected | "POS connected from 127.0.0.1:52341" | Green |

---

### 7.9 Configuration Drawer

Side drawer from right. Does not obscure left/center panels.

**Sections: Ingestion / Display / Transaction Matching / Schema / Export**

Key settings:
- Listen port (default: 9999)
- Mask sensitive fields toggle + configurable field list
- RTT high-latency threshold (default: 3000ms)
- STAN matching window (default: 60s)
- Theme: Dark / Light / System
- Custom schema YAML path
- Export format

---

### 7.10 Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `↑` / `↓` | Navigate transaction cards |
| `Enter` | Select focused card |
| `Tab` | Cycle center panel tabs |
| `Ctrl+F` | Focus search bar |
| `Ctrl+L` | Clear all transactions |
| `Ctrl+E` | Export current selection |
| `Ctrl+,` | Open settings drawer |
| `Escape` | Close drawer / deselect field |
| `Space` | Expand / collapse selected field row |
| `←` / `→` | Navigate fields in right panel |
| `Ctrl+C` | Copy selected field value |
| `Ctrl+Shift+C` | Copy as hex |
| `Ctrl+R` | Switch to Raw tab |
| `Ctrl+D` | Switch to Diff tab |

---

## 8. Data Models

### 8.1 Ingestion

```kotlin
data class RawLogEvent(
    val id: String,
    val realm: String,
    val capturedAt: Instant,
    val lifespanMs: Long,
    val direction: Direction,
    val rawXml: String,
    val fields: Map<Int, RawField>
)

data class RawField(
    val id: Int,
    val value: String,
    val isBinary: Boolean
)

enum class Direction { OUTGOING, INCOMING }
```

### 8.2 Decoded Domain

```kotlin
data class Transaction(
    val id: String,
    val stan: String,
    val request: DecodedPacket?,
    val response: DecodedPacket?,
    val status: TransactionStatus,
    val rttMs: Long?
)

enum class TransactionStatus { PENDING, APPROVED, DECLINED, TIMEOUT, NETWORK_ERROR }

data class DecodedPacket(
    val rawEvent: RawLogEvent,
    val mti: MTI,
    val fields: List<DecodedField>,
    val parseErrors: List<FieldParseError>
)

data class MTI(
    val raw: String,
    val version: String,
    val messageClass: String,
    val messageFunction: String,
    val messageOrigin: String,
    val label: String
)

data class DecodedField(
    val number: Int,
    val definition: FieldDefinition,
    val rawValue: String,
    val isBinary: Boolean,
    val decodedValue: String?,
    val subFields: List<SubField>?,
    val tlvEntries: List<TLVEntry>?,
    val isSensitive: Boolean,
    val isCurrentlyMasked: Boolean
)

data class TLVEntry(
    val tag: String,
    val name: String?,
    val length: Int,
    val valueHex: String,
    val decoded: String?,
    val children: List<TLVEntry>?,
    val isConstructed: Boolean
)
```

### 8.3 UI State

```kotlin
data class AppState(
    val serverStatus: ServerStatus,
    val transactions: List<Transaction>,
    val selectedTransactionId: String?,
    val selectedPacketDirection: Direction,
    val centerTab: CenterTab,
    val selectedFieldNumber: Int?,
    val searchQuery: String,
    val messageFilter: MessageFilter,
    val settingsOpen: Boolean,
    val stats: SessionStats
)

enum class ServerStatus  { IDLE, LISTENING, CONNECTED, ERROR }
enum class CenterTab     { REQUEST, RESPONSE, DIFF, RAW }
enum class MessageFilter { ALL, AUTH, FINANCIAL, REVERSAL, NETWORK, DECLINED }
```

---

## 9. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | Kotlin (JVM 17+) | Coroutines, null safety |
| UI | Compose Multiplatform Desktop | Reactive, declarative, Kotlin-native |
| Async | Kotlin Coroutines + StateFlow/SharedFlow | Live ingestion → UI reactivity |
| XML Parsing | SAX via `javax.xml` | Fast, streaming; no third-party dep |
| TLV Parsing | Custom Kotlin implementation | Full BER-TLV, no external dependency |
| TCP Server | Java NIO via coroutines | Non-blocking accept loop |
| Schema | `kotlinx.serialization` + kaml (YAML) | Human-editable field configs |
| Build | Gradle (Kotlin DSL) | Standard |
| Packaging | `jpackage` | `.exe` / `.dmg` / `.deb` native installers |

```kotlin
// build.gradle.kts
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.charleskorn.kaml:kaml:0.61.0")
}
```

---

## 10. Configuration & Setup

### 10.1 First-Run Wizard

1. For Live Socket: listen port (default 9999)
2. Schema: built-in ISO 8583:1993 or custom YAML
3. Masking preferences

### 10.2 ADB Forward Command (USB mode)

```bash
adb forward tcp:9999 tcp:9999
```

The wizard displays this command with a `[Copy]` button and a live indicator showing whether the forward is active.

### 10.3 Config File

`~/.iso8583viz/config.json` — auto-created on first run.

---

## 11. Error Handling & Edge Cases

| Scenario | Handling |
|---|---|
| Malformed XML from listener | Skip event; amber toast with message |
| Missing DE11 (STAN) | Card shown without pairing; labeled "Unmatched Request" |
| Response with no matching request | Shown as "Orphan Response" card |
| DE55 TLV parse failure at offset N | Tree shown to failure point; remainder as raw hex |
| Unknown EMV tag | Shown as `[UNKNOWN TAG xxxx]` with raw hex — no crash |
| ADB device disconnects | Amber toast; auto-reconnect with backoff |
| LogListener fails to connect to visualizer | jPOS pipeline completely unaffected; listener retries silently |
| Port 9999 already in use | Status bar error; one-click port change |
| DE128 present | Recognized as MAC; labeled "Message Authentication Code"; not treated as data field |
| Very long DE55 (>500 bytes) | TLV tree uses virtual scroll; no UI freeze |
| Multiple POS connections | Each connection is a separate session group in the timeline |

---

## 12. Security Considerations

This is a **developer and test tool only**. Never deploy in production.

- **Masking on by default.** DE2, DE35, DE45, DE52 masked in UI. Raw values in memory for sub-field decoding only. Explicit `[Reveal]` action required.
- **Loopback-only by default.** Ingestion server binds to `127.0.0.1`. Binding to LAN requires explicit configuration change.
- **No persistence by default.** All data is in-memory. Export is a manual action.
- **Production host warning.** If realm contains a non-RFC1918 IP, a persistent amber banner shows: "⚠ Live data from a public host. Ensure this is a test environment."
- **PIN never decoded.** DE52 is hex-only. No PIN block decryption logic exists in the visualizer (no key knowledge).
- **MAC labeled, not verified.** DE128 shown as hex with label. No MAC verification (no MAC key).

---

## 13. Phased Delivery Plan

### Phase 1 — Ingestion Engine (Weeks 1–2)
- TCP server for VisualizerLogListener connections with `---END---` framing
- jPOS XML parser → `RawLogEvent`
- STAN-based request/response matcher with timeout

### Phase 2 — Semantic Decoder (Weeks 3–4)
- MTI decoder (all standard MTIs)
- Field schema — all 128 standard fields + full NIBSS private fields (DE59, DE123, DE128)
- Sub-field decoders: DE3, DE7, DE18, DE22, DE25, DE32, DE35, DE39, DE49
- Amount formatter (DE4 + DE49 currency, NGN aware)
- BER-TLV parser (recursive, handles constructed tags)
- EMV tag dictionary (50+ tags including TVR bit decode, AID scheme lookup)

### Phase 3 — UI Foundation (Weeks 5–7)
- Three-panel Compose Desktop layout with draggable dividers
- Toolbar with mode selector, status pill, search, filters
- Transaction cards (approved, declined, pending, timeout states)
- Center panel: MTI card + field list with category groups
- Raw XML tab with syntax highlighting
- Status bar

### Phase 4 — Rich UI (Weeks 8–10)
- Category color accents on field rows
- Inline field expansion with sub-field decode
- EMV TLV tree (recursive, collapsible, TVR bit decode)
- Right panel field detail with contextual reference tables
- Request/Response diff tab
- Masking + reveal toggle with settings control
- Keyboard shortcuts

### Phase 5 — Polish & Utilities (Weeks 11–12)
- Full-text search across all field values
- Message type filter
- Configuration drawer (all settings)
- Export: JSON, plain text, jPOS XML format
- Paste / manual input mode
- Auto-scroll + "N new" pill
- Toast notification system
- High-latency RTT alerts

### Phase 6 — Packaging & Schema (Week 13)
- Custom YAML schema loader + inline editor in settings
- NIBSS private field definitions finalized and documented
- jpackage native installers (Windows `.exe`, macOS `.dmg`, Linux `.deb`)
- First-run setup wizard
- Integration guide for adding VisualizerLogListener to jPOS Q2 config

---

*End of Specification — Approach: jPOS Log Stream Interception*
*Version 2.0 — Revised based on real POS log sample (NIBSS/Verve, jPOS/Android)*
*Review before each phase kick-off*
