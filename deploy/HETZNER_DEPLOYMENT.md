# Deploying Tap8583 on Hetzner

One Hetzner Cloud server runs everything with Docker Compose, behind Cloudflare's proxy:

```
                                 ┌──────────────── Hetzner Cloud server ─────────────────┐
POS terminals ─┐                 │                                                       │
               ├─▶ Cloudflare ───┼─▶ Caddy :8443 ──┬─ /api/*, /ws/*, /health ─▶ server   │
Browsers ──────┘   (:443, proxy) │ (origin cert)   └─ everything else ────────▶ web      │
                                 │                     server ──▶ postgres (volume)      │
                                 └───────────────────────────────────────────────────────┘
```

The same server also runs Termly (`~/termly-deploy`, Compose project `termly`), whose Caddy owns ports 80 and 443. Tap8583's Caddy therefore listens on **8443** only, and Cloudflare forwards `tap8583.sarari.dev` there with an Origin Rule. The compose file pins the project name to `tap8583`, so the two apps never share a Compose project.

- **server**: Ktor ingestion + WebSocket backend (`serverApp/Dockerfile`)
- **web**: Next.js dashboard (`webApp/Dockerfile`)
- **postgres**: accounts, workspaces, terminal keys and transaction history
- **caddy**: HTTPS on port 8443 with a Cloudflare Origin Certificate, reverse proxy, security headers

The dashboard and API share one hostname, so there is no CORS to configure. People sign in with a link emailed to them; each workspace gets its own terminal keys, so nobody shares a key.

## 1. Create the server

1. In the Hetzner Cloud console, create a server: **Ubuntu 24.04**, a **CX22** (2 vCPU, 4 GB) is plenty to start. Add your SSH key.
2. Create a **Firewall** and attach it to the server, allowing inbound only:
   - TCP 22 (SSH), ideally limited to your IP
   - TCP 80 and 443, UDP 443 (Termly's Caddy)
   - TCP 8443 (Tap8583's Caddy, reached through Cloudflare)
3. In Cloudflare, point a DNS record (e.g. `tap8583.sarari.dev`) at the server's IPv4 (A) and IPv6 (AAAA), **proxied** (orange cloud). Then set up Cloudflare as in [Cloudflare and the origin certificate](#cloudflare-and-the-origin-certificate).

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
mkdir -p certs   # origin.pem and origin-key.pem go here, see below
cp .env.example .env
nano .env
```

Fill in `.env`:

| Variable | Value |
|---|---|
| `TAP8583_DOMAIN` | The hostname from step 1, e.g. `tap8583.sarari.dev` |
| `POSTGRES_PASSWORD` | `openssl rand -hex 24`. Only the server container uses it. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | SMTP credentials from any email provider (Resend, Postmark, Mailgun, Amazon SES...). Sign-in links and invites are sent this way, so nobody can sign in until it works. |
| `SMTP_FROM` | The sender, e.g. `Tap8583 <no-reply@yourdomain.com>`, on a domain you've verified with the provider |
| `TAP8583_MASK_CARD_DATA` | Keep `true`. PAN, track data, expiry and PIN block are masked before anything is stored or shown. |
| `TAP8583_RETENTION_DAYS` | Days of transaction history to keep (default 30) |

The images are published to GitHub Container Registry. If the packages are private, log the server in once with a GitHub token that has `read:packages`:

```bash
echo <token> | docker login ghcr.io -u <github-username> --password-stdin
```

Put the Cloudflare Origin Certificate in `certs/` (next section), then start it:

```bash
docker compose up -d
docker compose logs -f server   # confirm "Card data masking: ON" and "Accounts mode: Postgres connected"
```

Open `https://<your-domain>`, enter your email and open the link it sends. The first sign-in walks you through creating a workspace and connecting a terminal.

## Cloudflare and the origin certificate

Caddy can't get a Let's Encrypt certificate on port 8443, so it serves a **Cloudflare Origin Certificate** instead. Browsers only ever see Cloudflare's own certificate; the origin certificate secures the hop from Cloudflare to the server.

1. **Create the certificate.** In the Cloudflare dashboard for `sarari.dev`, open **SSL/TLS → Origin Server → Create Certificate**. Keep "Generate private key and CSR with Cloudflare" (RSA or ECC both work), list the hostname `tap8583.sarari.dev`, and pick a validity (up to 15 years). Save the two text boxes on the server:

   ```bash
   mkdir -p ~/tap8583/deploy/certs && cd ~/tap8583/deploy/certs
   nano origin.pem       # paste "Origin Certificate"
   nano origin-key.pem   # paste "Private Key" (shown only once)
   chmod 600 origin-key.pem
   ```

   `deploy/certs/` is git-ignored: these files live only on the server. They are mounted read-only into the Caddy container at `/certs`.

   The Caddy container must be able to read `origin-key.pem`. The image runs as root, so `chmod 600` owned by `deploy` is fine.

2. **Encryption mode.** Under **SSL/TLS → Overview**, set the encryption mode to **Full (strict)**. This setting applies to the whole `sarari.dev` zone. `termly.sarari.dev` keeps working under it, because Termly's Caddy has a real Let's Encrypt certificate, which Full (strict) accepts.

3. **Origin Rule for port 8443.** Under **Rules → Origin Rules → Create rule**: when *Hostname* *equals* `tap8583.sarari.dev`, set **Destination Port** to *Rewrite to* `8443`. Deploy it. Without this rule Cloudflare connects on 443 and reaches Termly's Caddy instead.

4. **Firewall.** If the server has a Hetzner Cloud Firewall, add an inbound rule for **TCP 8443** (Hetzner console → Firewalls → your firewall → Inbound rules). If you also use `ufw` on the server, `sudo ufw allow 8443/tcp`.

When the certificate expires, create a new one the same way, replace the two files and run `docker compose restart caddy`.

## Switching over from the old "deploy" project

Before the project name was pinned, Compose named this stack after its folder, `deploy`, and its Caddy also asked for ports 80 and 443, so it couldn't start next to Termly. To move an existing server to the `tap8583` project without losing data:

```bash
cd ~/tap8583/deploy

# 1. Stop and remove the old project's containers. NEVER add -v: that deletes the database volume.
docker compose -p deploy down

# 2. The database volume must still be there.
docker volume ls | grep deploy_postgres_data

# 3. Start again as project "tap8583", using the same deploy_postgres_data volume.
docker compose up -d

# 4. Check.
docker compose ls                      # shows "tap8583" (and "termly"), no "deploy"
docker compose ps                      # caddy, server, web, postgres all up
curl -I https://tap8583.sarari.dev     # HTTP/2 200 (or a redirect) from Cloudflare
```

The certificate files, the Origin Rule, Full (strict) and the firewall rule must be in place first. Caddy starts with fresh `tap8583_caddy_data` and `tap8583_caddy_config` volumes; the old `deploy_caddy_data` and `deploy_caddy_config` volumes are no longer used and can be removed later with `docker volume rm deploy_caddy_data deploy_caddy_config`. Never remove `deploy_postgres_data`.

## 4. Point terminals at it

In the dashboard, open **Settings → Terminals → New key**. It shows the key once, with the snippet to paste into the POS app's debug build:

```kotlin
VisualizerLogListener.install(
    apiKey = "t8k_...",
    endpoint = "https://<your-domain>/api/v1/events"
)
```

Invite teammates from **Settings → People**. They get an email with a link to join the workspace.

## 5. Automatic deploys from GitHub

`.github/workflows/deploy.yml` builds both images on every push to `main` and pushes them to GHCR. To have it also deploy, set in the repository's **Settings → Secrets and variables → Actions**:

- Variables: `DEPLOY_ENABLED` = `true`, `HETZNER_HOST` = server IP or hostname, `HETZNER_USER` = `deploy`
- Secret: `HETZNER_SSH_KEY` = a private key whose public half is in `/home/deploy/.ssh/authorized_keys` (create a dedicated key pair for CI)

Each deploy copies the compose file and Caddyfile, pulls the images tagged with the commit SHA and restarts the containers. It does not touch `certs/` or `.env`.

## Operations

- **Logs:** `docker compose logs -f server` (or `web`, `caddy`)
- **Roll back:** `SERVER_IMAGE=ghcr.io/henryudorji/tap8583-server:<older-sha> WEB_IMAGE=ghcr.io/henryudorji/tap8583-web:<older-sha> docker compose up -d`
- **Rotate a terminal key:** create a new key in **Settings → Terminals**, ship it in the debug build, then revoke the old one. Revoking takes effect immediately.
- **Database:** `docker compose exec postgres psql -U tap8583 tap8583`. To browse it in a GUI (TablePlus, DBeaver, pgAdmin), turn on its SSH tunnel option: SSH `deploy@<server-ip>` with your key, then database host `127.0.0.1`, port `5432`, database and user `tap8583`, password `POSTGRES_PASSWORD` from `.env`. Postgres listens on the server's localhost only, so it is never reachable from the internet. Data lives on the `deploy_postgres_data` volume (named explicitly in the compose file, from when the project was called `deploy`) and survives restarts and deploys. There are no automatic backups; `docker compose down -v` deletes everything.
- **Email not arriving:** `docker compose logs server | grep -i mail`. Check the provider's dashboard and that `SMTP_FROM` is on a verified domain.

### Without accounts

If `DATABASE_URL` is not set, the server falls back to the original shared keys (`TAP8583_VIEWER_KEY` for the dashboard, `TAP8583_INGEST_KEY` for terminals) and keeps history in memory only. That is handy for running it locally; the compose file above always uses accounts.
