# Tap8583 — Render Deployment Guide

This guide walks you through deploying both the **Kotlin / Ktor Ingestion Server (`serverApp`)** and the **Next.js Web Visualizer (`webApp`)** to [Render](https://render.com).

---

## 🏗️ Architecture on Render

```
┌─────────────────────────────────────────────────────────────┐
│  Render Cloud Environment                                   │
│                                                             │
│   ┌──────────────────────────────────────────────────────┐  │
│   │ tap8583-server (Docker Web Service)                  │  │
│   │ - URL: https://tap8583-server.onrender.com           │  │
│   │ - HTTP Ingest: /api/v1/events                        │  │
│   │ - WebSockets:  /ws/stream                            │  │
│   │ - Health Check: /health                              │  │
│   └──────────────────────────┬───────────────────────────┘  │
│                              │ WSS (WebSockets)             │
│                              ▼                              │
│   ┌──────────────────────────────────────────────────────┐  │
│   │ tap8583-web (Node.js Web Service)                    │  │
│   │ - URL: https://tap8583-web.onrender.com              │  │
│   │ - Next.js 15 App Router Dark UI                      │  │
│   └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚡ Option 1: One-Click Blueprint Deployment (Recommended)

The repository includes a ready-to-use [`render.yaml`](render.yaml) blueprint that defines both services, their runtime settings, and environment variables.

### How it Works:
1. Push your repository to **GitHub** or **GitLab**.
2. Log in to your [Render Dashboard](https://dashboard.render.com).
3. Click **New +** in the top right and select **Blueprint**.
4. Connect your `Tap8583` repository.
5. Render will detect [`render.yaml`](render.yaml) and present a pre-configured plan for both services:
   - **`tap8583-server`** (Docker Web Service)
   - **`tap8583-web`** (Node Web Service)
6. Click **Apply**. Render will provision, build, and deploy both services automatically!

---

### 📋 Environment Variables Configured in `render.yaml`

When deploying via Blueprint, the following environment variables are automatically configured:

#### 1. For `tap8583-server` (Ktor Backend):
| Variable | Value in `render.yaml` | Purpose / Description |
|---|---|---|
| `TAP8583_API_KEY` | `tap_live_secret` *(change in production)* | The shared authentication key. Requests from POS devices must include this in the `x-api-key` header, and WebSocket clients must pass it in query parameters (`?apiKey=...`). |
| `HOST` | `0.0.0.0` | Binds the Ktor server to all network interfaces so Render's internal reverse proxy can route public traffic to it. |
| `PORT` | *(Auto-injected by Render)* | Render assigns a dynamic port (usually `10000`) and provides it in the `PORT` env var. Ktor automatically binds to this port. |

#### 2. For `tap8583-web` (Next.js Dashboard):
| Variable | Value in `render.yaml` | Purpose / Description |
|---|---|---|
| `NODE_VERSION` | `20` | Tells Render to use Node.js 20 LTS runtime. |
| `NEXT_PUBLIC_TAP8583_API_KEY` | `tap_live_secret` | The default API key pre-filled in the web UI settings to authenticate with the backend stream. |
| `NEXT_PUBLIC_SERVER_WS_URL` | *(Optional override)* | WebSocket endpoint for the live stream (e.g. `wss://tap8583-server.onrender.com/ws/stream`). If omitted, you can enter it in the Web UI Settings modal. |
| `NEXT_PUBLIC_SERVER_HTTP_URL` | *(Optional override)* | HTTP endpoint for transaction ingestion and simulation (e.g. `https://tap8583-server.onrender.com/api/v1/events`). |

> [!TIP]
> **Connecting the Web UI to your Live Server:**
> Once Render finishes deploying `tap8583-server`, copy its generated URL (e.g., `https://tap8583-server.onrender.com`).
> You can either:
> 1. Open the Web App, click **Settings (⚙️)** in the top bar, and paste `wss://tap8583-server.onrender.com/ws/stream`.
> 2. Or, in Render Dashboard under `tap8583-web` ➔ **Environment**, set `NEXT_PUBLIC_SERVER_WS_URL = wss://tap8583-server.onrender.com/ws/stream` and `NEXT_PUBLIC_SERVER_HTTP_URL = https://tap8583-server.onrender.com/api/v1/events` then trigger a redeploy.

---

## 🛠️ Option 2: Manual Deployment via Render Dashboard

If you prefer configuring the services individually in the Render UI, follow these steps:

### Step 1: Deploy the Ktor Ingestion Server (`tap8583-server`)

1. In Render Dashboard, click **New +** → **Web Service**.
2. Connect your Git repository.
3. Configure the settings:
   - **Name**: `tap8583-server`
   - **Region**: Choose closest to you (e.g. *Frankfurt* or *Oregon*).
   - **Runtime**: `Docker`
   - **Dockerfile Path**: `./Dockerfile` (or `./serverApp/Dockerfile`)
   - **Docker Build Context Directory**: `.` (Root directory)
   - **Instance Type**: `Free` (or `Starter`)
4. In **Advanced** settings:
   - **Health Check Path**: `/health`
5. In **Environment Variables**, add:
   | Key | Value | Notes |
   |---|---|---|
   | `TAP8583_API_KEY` | `tap_live_secret` | Or your custom secret key |
   | `HOST` | `0.0.0.0` | Binds to all interfaces |
6. Click **Create Web Service**.
7. Once deployed, note your server URL (e.g. `https://tap8583-server.onrender.com`).

---

### Step 2: Deploy the Next.js Web App (`tap8583-web`)

1. In Render Dashboard, click **New +** → **Web Service**.
2. Connect your Git repository.
3. Configure the settings:
   - **Name**: `tap8583-web`
   - **Region**: Same region as your server.
   - **Root Directory**: `webApp`
   - **Runtime**: `Node`
   - **Build Command**: `npm install && npm run build`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free` (or `Starter`)
4. In **Environment Variables**, add:
   | Key | Value | Example |
   |---|---|---|
   | `NODE_VERSION` | `20` | Standard LTS Node runtime |
   | `NEXT_PUBLIC_TAP8583_API_KEY` | `tap_live_secret` | Must match server API key |
   | `NEXT_PUBLIC_SERVER_WS_URL` | `wss://<YOUR-SERVER-URL>/ws/stream` | e.g. `wss://tap8583-server.onrender.com/ws/stream` |
   | `NEXT_PUBLIC_SERVER_HTTP_URL` | `https://<YOUR-SERVER-URL>/api/v1/events` | e.g. `https://tap8583-server.onrender.com/api/v1/events` |

5. Click **Create Web Service**.
6. Once deployed, open your web app URL (e.g. `https://tap8583-web.onrender.com`).

---

## 🧪 3 — Verifying Your Deployment

### 1. Test Server Health Check
Open in your browser:
```
https://tap8583-server.onrender.com/health
```
Expected response:
```json
{
  "service": "Tap8583 Cloud Ingestion",
  "status": "healthy",
  "version": "2.0.0"
}
```

### 2. Test Live Streaming on Web Visualizer
1. Open `https://tap8583-web.onrender.com` in your browser.
2. The top-right status pill should show **`● LIVE CLOUD`** in green.
3. Click **"Simulate Txn"** in the top navigation bar.
4. You will see a live paired transaction card with MTI `0200` ➔ `0210`, RTT latency calculation (+420ms), and Field 120 GPS location data (`06.5244° N, 003.3792° E`).

---

## 📱 4 — Connecting Android POS Devices

In your Android POS application (`Application.onCreate()`):

```kotlin
import com.tap8583.visualizer.VisualizerLogListener

class PosApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Point to your live Render server:
        VisualizerLogListener.install(
            apiKey = "tap_live_secret",
            endpoint = "https://tap8583-server.onrender.com/api/v1/events"
        )
    }
}
```

---

## 💡 Important Render Free Tier Tips

1. **Spin-down on Inactivity (Free Tier)**:
   - On Render's Free tier, web services spin down after 15 minutes of inactivity. When a new transaction arrives or a user opens the dashboard, the first request may take ~30-50 seconds to cold start.
   - For 24/7 instant response in testing environments, you can upgrade the service to the `$7/month Starter` tier or use a free uptime monitor (e.g. UptimeRobot) pinging `/health` every 10 minutes.
2. **WebSockets Support**:
   - Render natively supports WebSockets without additional configuration or reverse proxy setup.
3. **HTTPS / TLS**:
   - Render automatically provisions free SSL/TLS certificates for `*.onrender.com` domains and custom domains.
