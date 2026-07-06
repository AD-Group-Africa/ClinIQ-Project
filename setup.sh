#!/bin/bash
# =============================================================
# ClinIQ — Server Setup & Deployment
# Tested on: Ubuntu 22.04 (Hetzner CX21+)
# Usage: curl -sSL https://raw.githubusercontent.com/YOUR_ORG/cliniq/main/setup.sh | bash
# Or:    chmod +x setup.sh && ./setup.sh
# =============================================================
set -e

CLINIQ_DIR="/opt/cliniq"
DOMAIN="${DOMAIN:-}"
EMAIL="${EMAIL:-admin@example.com}"
REPO="${REPO:-https://github.com/YOUR_ORG/cliniq.git}"
BRANCH="${BRANCH:-main}"

# ──────────────────────────────────────────
# Colours
# ──────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; NC='\033[0m'; BOLD='\033[1m'

banner()  { echo -e "\n${CYAN}${BOLD}▶ $1${NC}"; }
ok()      { echo -e "  ${GREEN}✅ $1${NC}"; }
warn()    { echo -e "  ${YELLOW}⚠  $1${NC}"; }
fail()    { echo -e "  ${RED}❌ $1${NC}"; exit 1; }

# ──────────────────────────────────────────
# 0. Root check
# ──────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then fail "Run as root: sudo bash setup.sh"; fi

echo -e "\n${CYAN}${BOLD}"
echo "  ╔══════════════════════════════════╗"
echo "  ║  ClinIQ — Server Setup           ║"
echo "  ╚══════════════════════════════════╝${NC}"

# ──────────────────────────────────────────
# 1. System update + essentials
# ──────────────────────────────────────────
banner "1. System update"
apt-get update -qq && apt-get upgrade -y -qq
apt-get install -y -qq \
    curl wget git unzip ufw fail2ban \
    certbot python3-certbot-nginx \
    nginx cron logrotate htop
ok "System updated"

# ──────────────────────────────────────────
# 2. Docker + Docker Compose
# ──────────────────────────────────────────
banner "2. Installing Docker"
if command -v docker &>/dev/null; then
    warn "Docker already installed — skipping"
else
    curl -fsSL https://get.docker.com | bash
    systemctl enable docker && systemctl start docker
    ok "Docker installed: $(docker --version)"
fi

if ! command -v docker-compose &>/dev/null; then
    COMPOSE_VERSION=$(curl -sI https://github.com/docker/compose/releases/latest \
        | grep -i location | sed 's|.*tag/v||' | tr -d '[:space:]')
    curl -SL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
        -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi
ok "Docker Compose: $(docker-compose --version)"

# ──────────────────────────────────────────
# 3. Create deploy user
# ──────────────────────────────────────────
banner "3. Creating deploy user"
if ! id "deploy" &>/dev/null; then
    useradd -m -s /bin/bash deploy
    usermod -aG docker deploy
    ok "User 'deploy' created and added to docker group"
else
    warn "User 'deploy' already exists"
fi

# ──────────────────────────────────────────
# 4. Project directory
# ──────────────────────────────────────────
banner "4. Setting up project directory"
mkdir -p "$CLINIQ_DIR"/{logs,backups,ssl}
chown -R deploy:deploy "$CLINIQ_DIR"

if [[ -n "$REPO" && "$REPO" != *"YOUR_ORG"* ]]; then
    su - deploy -c "git clone $REPO $CLINIQ_DIR/app 2>/dev/null || (cd $CLINIQ_DIR/app && git pull)"
    ok "Repository cloned"
else
    mkdir -p "$CLINIQ_DIR/app"
    warn "No repo URL set — create $CLINIQ_DIR/app manually"
fi

# ──────────────────────────────────────────
# 5. Generate .env
# ──────────────────────────────────────────
banner "5. Generating .env"
ENV_FILE="$CLINIQ_DIR/app/.env"

if [[ -f "$ENV_FILE" ]]; then
    warn ".env already exists — skipping generation"
else
    # Generate secrets
    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
    DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    REDIS_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=' | head -c 20)
    WA_VERIFY=$(openssl rand -hex 16)

    cat > "$ENV_FILE" << EOF
# ── Database ────────────────────────────
DB_NAME=cliniq
DB_USER=cliniq
DB_PASSWORD=${DB_PASSWORD}

# ── Redis ───────────────────────────────
REDIS_PASSWORD=${REDIS_PASSWORD}

# ── JWT ─────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Spring ──────────────────────────────
SPRING_PROFILES_ACTIVE=prod

# ── Africa's Talking SMS ────────────────
AT_API_KEY=CHANGE_ME
AT_USERNAME=CHANGE_ME

# ── M-Pesa Daraja ───────────────────────
MPESA_CONSUMER_KEY=CHANGE_ME
MPESA_CONSUMER_SECRET=CHANGE_ME
MPESA_SHORTCODE=174379
MPESA_PASSKEY=CHANGE_ME
MPESA_CALLBACK_URL=https://${DOMAIN:-your-domain.com}/api/v1/billing/mpesa/callback
MPESA_SANDBOX=false

# ── WhatsApp Business ───────────────────
WHATSAPP_PHONE_ID=CHANGE_ME
WHATSAPP_ACCESS_TOKEN=CHANGE_ME
WHATSAPP_VERIFY_TOKEN=${WA_VERIFY}

# ── App ─────────────────────────────────
FRONTEND_URL=https://${DOMAIN:-your-domain.com}
NEXT_PUBLIC_API_URL=https://${DOMAIN:-your-domain.com}
DEFAULT_CLINIC_SLUG=demo
NODE_ENV=production
LOG_LEVEL=info
EOF
    chmod 600 "$ENV_FILE"
    ok ".env generated (update API keys before deploying)"
fi

# ──────────────────────────────────────────
# 6. Nginx configuration
# ──────────────────────────────────────────
banner "6. Configuring Nginx"

if [[ -n "$DOMAIN" ]]; then
    cat > /etc/nginx/sites-available/cliniq << NGINX
upstream cliniq_backend  { server 127.0.0.1:8080; keepalive 32; }
upstream cliniq_frontend { server 127.0.0.1:3000; }
upstream cliniq_chatbot  { server 127.0.0.1:5000; }

limit_req_zone \$binary_remote_addr zone=api:10m rate=30r/m;

server {
    listen 80;
    server_name ${DOMAIN};
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name ${DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    ssl_session_cache   shared:SSL:10m;

    add_header X-Frame-Options "DENY";
    add_header X-Content-Type-Options "nosniff";
    add_header Referrer-Policy "strict-origin-when-cross-origin";
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    gzip on;
    gzip_types text/plain application/json text/css application/javascript;
    gzip_min_length 1024;

    location /api/v1/public/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://cliniq_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    location /api/ {
        limit_req zone=api burst=30 nodelay;
        proxy_pass http://cliniq_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }

    location /actuator/health {
        proxy_pass http://cliniq_backend;
        access_log off;
    }

    location /ws/ {
        proxy_pass http://cliniq_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    location /webhook/ { proxy_pass http://cliniq_chatbot; }

    location / {
        proxy_pass http://cliniq_frontend;
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
    }
}
NGINX

    ln -sf /etc/nginx/sites-available/cliniq /etc/nginx/sites-enabled/cliniq
    rm -f /etc/nginx/sites-enabled/default
    nginx -t && systemctl reload nginx
    ok "Nginx configured for $DOMAIN"
else
    warn "No DOMAIN set — Nginx config skipped (run with DOMAIN=yourdomain.com)"
fi

# ──────────────────────────────────────────
# 7. SSL Certificate (Let's Encrypt)
# ──────────────────────────────────────────
banner "7. SSL Certificate"
if [[ -n "$DOMAIN" ]]; then
    if [[ -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]]; then
        warn "Certificate already exists for $DOMAIN"
    else
        certbot certonly --nginx \
            -d "$DOMAIN" \
            --email "$EMAIL" \
            --agree-tos \
            --non-interactive \
            --redirect
        ok "SSL certificate issued for $DOMAIN"
    fi
    # Auto-renewal cron
    echo "0 12 * * * /usr/bin/certbot renew --quiet" | crontab -
    ok "Auto-renewal configured"
else
    warn "No DOMAIN set — SSL skipped"
fi

# ──────────────────────────────────────────
# 8. Firewall
# ──────────────────────────────────────────
banner "8. Configuring firewall"
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
ok "UFW firewall active"

# ──────────────────────────────────────────
# 9. Automated backups
# ──────────────────────────────────────────
banner "9. Backup cron"
cat > /usr/local/bin/cliniq-backup.sh << 'BACKUP'
#!/bin/bash
BACKUP_DIR="/opt/cliniq/backups"
DATE=$(date +%Y%m%d_%H%M%S)
LOG="/var/log/cliniq/backup.log"

mkdir -p "$BACKUP_DIR"

# PostgreSQL dump
docker exec cliniq_db pg_dump \
    -U "${DB_USER:-cliniq}" "${DB_NAME:-cliniq}" \
    --format=custom --compress=9 \
    > "$BACKUP_DIR/db_${DATE}.dump" 2>>"$LOG"

# Encrypt with openssl
if [[ -n "$BACKUP_KEY" ]]; then
    openssl enc -aes-256-cbc -pbkdf2 \
        -pass "pass:$BACKUP_KEY" \
        -in "$BACKUP_DIR/db_${DATE}.dump" \
        -out "$BACKUP_DIR/db_${DATE}.dump.enc"
    rm "$BACKUP_DIR/db_${DATE}.dump"
fi

# Keep last 30 days
find "$BACKUP_DIR" -name "*.dump*" -mtime +30 -delete

echo "[${DATE}] Backup completed" >> "$LOG"
BACKUP

chmod +x /usr/local/bin/cliniq-backup.sh
mkdir -p /var/log/cliniq

# Run backup at 2 AM daily
(crontab -l 2>/dev/null; echo "0 2 * * * /usr/local/bin/cliniq-backup.sh") | crontab -
ok "Daily backup cron configured (2 AM)"

# ──────────────────────────────────────────
# 10. Log rotation
# ──────────────────────────────────────────
cat > /etc/logrotate.d/cliniq << 'LOGROTATE'
/var/log/cliniq/*.log {
    daily
    rotate 14
    compress
    delaycompress
    missingok
    notifempty
    create 0640 deploy deploy
}
LOGROTATE
ok "Log rotation configured"

# ──────────────────────────────────────────
# 11. Deploy script
# ──────────────────────────────────────────
cat > /usr/local/bin/cliniq-deploy.sh << 'DEPLOY'
#!/bin/bash
set -e
CLINIQ_DIR="/opt/cliniq/app"
echo "🚀 Deploying ClinIQ..."
cd "$CLINIQ_DIR"

# Pull latest
git pull origin "${BRANCH:-main}"

# Build and restart
docker-compose -f docker-compose.yml pull --ignore-pull-failures 2>/dev/null || true
docker-compose up -d --build --remove-orphans

# Health check (wait up to 3 minutes)
echo "⏳ Waiting for backend to be healthy..."
for i in {1..18}; do
    HEALTH=$(curl -sf http://localhost:8080/actuator/health 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null)
    if [[ "$HEALTH" == "UP" ]]; then
        echo "✅ Backend is healthy"
        break
    fi
    echo "   attempt $i/18..."
    sleep 10
done

# Cleanup old images
docker image prune -f
echo "✅ ClinIQ deployment complete"
DEPLOY
chmod +x /usr/local/bin/cliniq-deploy.sh
ok "Deploy script at /usr/local/bin/cliniq-deploy.sh"

# ──────────────────────────────────────────
# 12. Fail2ban
# ──────────────────────────────────────────
cat > /etc/fail2ban/jail.local << 'F2B'
[sshd]
enabled = true
maxretry = 5
bantime  = 3600
findtime = 600

[nginx-http-auth]
enabled = true
F2B
systemctl enable fail2ban && systemctl restart fail2ban
ok "Fail2ban configured"

# ──────────────────────────────────────────
# 13. Summary
# ──────────────────────────────────────────
echo ""
echo -e "${CYAN}${BOLD}════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}  ✅ ClinIQ server setup complete!${NC}"
echo -e "${CYAN}${BOLD}════════════════════════════════════${NC}"
echo ""
echo "  Next steps:"
echo ""
echo "  1. Edit .env file:"
echo "     nano $CLINIQ_DIR/app/.env"
echo "     (Fill in AT_API_KEY, MPESA_*, WHATSAPP_*)"
echo ""
echo "  2. Start services:"
echo "     cd $CLINIQ_DIR/app && docker-compose up -d"
echo ""
echo "  3. Test health:"
echo "     curl http://localhost:8080/actuator/health"
echo ""
echo "  4. Deploy future updates:"
echo "     cliniq-deploy.sh"
echo ""
echo "  5. View logs:"
echo "     docker-compose -f $CLINIQ_DIR/app/docker-compose.yml logs -f backend"
echo ""
if [[ -n "$DOMAIN" ]]; then
    echo "  🌐 Live at: https://$DOMAIN"
fi
echo ""
