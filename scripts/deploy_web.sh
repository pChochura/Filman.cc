#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Helper function to read property from file
get_prop() {
    local file="$1"
    local key="$2"
    if [ -f "$file" ]; then
        grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file" | head -n 1 | cut -d'=' -f2- | tr -d '\r"' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' || true
    fi
}

LOCAL_PROPS="${ROOT_DIR}/local.properties"
WEB_ENV="${ROOT_DIR}/web/.env"

# 1. Resolve DEPLOY_HOST
SERVER_HOST="${DEPLOY_HOST:-${SERVER_HOST:-}}"
if [ -z "$SERVER_HOST" ]; then
    SERVER_HOST="$(get_prop "$LOCAL_PROPS" "DEPLOY_HOST")"
fi
if [ -z "$SERVER_HOST" ]; then
    SERVER_HOST="$(get_prop "$WEB_ENV" "DEPLOY_HOST")"
fi
if [ -z "$SERVER_HOST" ]; then
    # Fall back to host from STREAM_API_BASE_URL if configured
    STREAM_URL="$(get_prop "$LOCAL_PROPS" "STREAM_API_BASE_URL")"
    if [ -z "$STREAM_URL" ]; then
        STREAM_URL="$(get_prop "$WEB_ENV" "STREAM_API_BASE_URL")"
    fi
    if [ -n "$STREAM_URL" ]; then
        SERVER_HOST="$(echo "$STREAM_URL" | sed -E 's|^https?://([^/:]+).*|\1|')"
    fi
fi

if [ -z "$SERVER_HOST" ]; then
    echo "❌ Error: Deployment host is not set." >&2
    echo "Please define DEPLOY_HOST in local.properties or web/.env, or export DEPLOY_HOST=<server_ip>." >&2
    exit 1
fi

# 2. Resolve DEPLOY_USER
SERVER_USER="${DEPLOY_USER:-${SERVER_USER:-}}"
if [ -z "$SERVER_USER" ]; then
    SERVER_USER="$(get_prop "$LOCAL_PROPS" "DEPLOY_USER")"
fi
if [ -z "$SERVER_USER" ]; then
    SERVER_USER="$(get_prop "$WEB_ENV" "DEPLOY_USER")"
fi
if [ -z "$SERVER_USER" ]; then
    SERVER_USER="ubuntu"
fi

REMOTE_REPO_DIR="${REMOTE_REPO_DIR:-/home/${SERVER_USER}/filman}"

echo "=================================================="
echo "🚀 Deploying Tiwi Web App to $SERVER_USER@$SERVER_HOST"
echo "=================================================="

ssh -o BatchMode=yes "${SERVER_USER}@${SERVER_HOST}" << EOF
set -euo pipefail
echo "📥 1/3 Pulling latest changes from master in ${REMOTE_REPO_DIR}..."
cd "${REMOTE_REPO_DIR}"
git pull origin master

echo "🐳 2/3 Rebuilding and restarting tiwi-web container..."
cd "${REMOTE_REPO_DIR}/web"
docker compose up -d --build

echo "🩺 3/3 Verifying container status..."
docker ps --filter "name=tiwi-web" --format "table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "✅ Web deployment completed successfully!"
EOF
