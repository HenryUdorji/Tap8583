# Deploying Tap8583 on Hetzner

One Hetzner Cloud server runs everything with Docker Compose:

```
                 ┌──────────────── Hetzner Cloud server ────────────────┐
POS terminals ─┐ │                                                      │
               ├─┼─▶ Caddy :443 ──┬─ /api/*, /ws/*, /health ─▶ server   │
Browsers ──────┘ │  (auto HTTPS)  └─ everything else ────────▶ web      │
                 └──────────────────────────────────────────────────────┘
```

- **server**: Ktor ingestion + WebSocket backend (`serverApp/Dockerfile`)
- **web**: Next.js dashboard (`webApp/Dockerfile`)
- **caddy**: TLS certificates from Let's Encrypt, reverse proxy, security headers

The dashboard and API share one hostname, so there is no CORS to configure and the API keys never appear in the web bundle.

## 1. Create the server

1. In the Hetzner Cloud console, create a server: **Ubuntu 24.04**, a **CX22** (2 vCPU, 4 GB) is plenty to start. Add your SSH key.
2. Create a **Firewall** and attach it to the server, allowing inbound only:
   - TCP 22 (SSH), ideally limited to your IP
   - TCP 80 and 443, UDP 443 (HTTP/3)
3. Point a DNS record (e.g. `tap8583.yourdomain.com`) at the server's IPv4 (A) and IPv6 (AAAA).

## 2. Prepare the server

```bash
ssh root@<server-ip>

# Updates, Docker, and a non-root deploy user
apt update && apt -y upgrade
apt -y install unattended-upgrades
curl -fsSL https://get.docker.com | sh
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh && cp ~/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh

# SSH: keys only, no root login
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/; s/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart ssh
```

## 3. Configure the app

As the `deploy` user:

```bash
mkdir -p ~/tap8583/deploy && cd ~/tap8583/deploy
# Copy docker-compose.yml, Caddyfile and .env.example from this repo's deploy/ folder
# (the deploy workflow keeps docker-compose.yml and Caddyfile up to date after this).
cp .env.example .env
nano .env
```

Fill in `.env`:

| Variable | Value |
|---|---|
| `TAP8583_DOMAIN` | The hostname from step 1 |
| `ACME_EMAIL` | Your email, for Let's Encrypt certificate notices (required) |
| `TAP8583_VIEWER_KEY` | `openssl rand -hex 24`. People enter this in the dashboard. It can read and clear transactions. |
| `TAP8583_INGEST_KEY` | `openssl rand -hex 24`, a different value. Goes into POS debug builds. It can only send events. |
| `TAP8583_MASK_CARD_DATA` | Keep `true`. PAN, track data, expiry and PIN block are masked before anything is stored or shown. |

The images are published to GitHub Container Registry. If the packages are private, log the server in once with a GitHub token that has `read:packages`:

```bash
echo <token> | docker login ghcr.io -u <github-username> --password-stdin
```

Start it:

```bash
docker compose up -d
docker compose logs -f server   # confirm "Card data masking: ON"
```

Open `https://<your-domain>`, click the settings icon, and enter the viewer key.

## 4. Point terminals at it

```kotlin
VisualizerLogListener.install(
    apiKey = "<TAP8583_INGEST_KEY>",
    endpoint = "https://<your-domain>/api/v1/events"
)
```

## 5. Automatic deploys from GitHub

`.github/workflows/deploy.yml` builds both images on every push to `main` and pushes them to GHCR. To have it also deploy, set in the repository's **Settings → Secrets and variables → Actions**:

- Variables: `DEPLOY_ENABLED` = `true`, `HETZNER_HOST` = server IP or hostname, `HETZNER_USER` = `deploy`
- Secret: `HETZNER_SSH_KEY` = a private key whose public half is in `/home/deploy/.ssh/authorized_keys` (create a dedicated key pair for CI)

Each deploy copies the compose file and Caddyfile, pulls the images tagged with the commit SHA and restarts the containers.

## Operations

- **Logs:** `docker compose logs -f server` (or `web`, `caddy`)
- **Roll back:** `SERVER_IMAGE=ghcr.io/henryudorji/tap8583-server:<older-sha> WEB_IMAGE=ghcr.io/henryudorji/tap8583-web:<older-sha> docker compose up -d`
- **Rotate a key:** edit `.env`, then `docker compose up -d server`. Rotating the ingest key means rebuilding the POS debug apps.
- **Backups:** transactions are held in memory for now, so a restart or deploy clears history. Persistence (Postgres, with nightly backups to a Hetzner Storage Box) is the next step.
