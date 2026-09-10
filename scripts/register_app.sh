#!/usr/bin/env bash
# ==============================================================================
# Veylor Relay - Application Registration Tool
#
# Usage:
#   ./register_app.sh <application_name> [relay_url] [admin_secret_token]
#
# Examples:
#   ./register_app.sh "eGarage"
#   ./register_app.sh "eGarage" "http://100.x.y.z:8080" "adm_relay_secret_token"
#   ./register_app.sh "Veylor SSO" "http://127.0.0.1:8080"
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELAY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load .env if present in relay root
if [ -f "$RELAY_DIR/.env" ]; then
    # Export vars without comments
    export $(grep -v '^#' "$RELAY_DIR/.env" | xargs)
fi

# Read from .kamal/secrets if not already set
if [ -f "$RELAY_DIR/.kamal/secrets" ]; then
    [ -z "$ADMIN_SECRET_TOKEN" ] && ADMIN_SECRET_TOKEN=$(grep '^ADMIN_SECRET_TOKEN=' "$RELAY_DIR/.kamal/secrets" | cut -d'=' -f2)
    if [ -z "$RELAY_URL" ]; then
        TS_IP=$(grep '^RELAY_BIND_IP=' "$RELAY_DIR/.kamal/secrets" | cut -d'=' -f2)
        TS_PORT=$(grep '^RELAY_PORT=' "$RELAY_DIR/.kamal/secrets" | cut -d'=' -f2)
        if [ -n "$TS_IP" ]; then
            RELAY_URL="http://${TS_IP}:${TS_PORT:-8085}"
        fi
    fi
fi

APP_NAME="${1:-${APP_NAME}}"
RELAY_URL="${2:-${RELAY_URL:-http://localhost:8085}}"
ADMIN_SECRET_TOKEN="${3:-${ADMIN_SECRET_TOKEN}}"

# Strip trailing slash from RELAY_URL
RELAY_URL="${RELAY_URL%/}"

if [ -z "$APP_NAME" ]; then
    echo "Usage: $0 <application_name> [relay_url] [admin_secret_token]"
    echo "Example: $0 'eGarage' 'http://100.115.92.10:8080' 'adm_secret_token_123'"
    exit 1
fi

if [ -z "$ADMIN_SECRET_TOKEN" ]; then
    echo "Error: ADMIN_SECRET_TOKEN is not defined."
    echo "Provide it as argument #3 or set ADMIN_SECRET_TOKEN in your environment / relay/.env."
    exit 1
fi

echo "------------------------------------------------------------"
echo "Registering application '$APP_NAME' with Relay..."
echo "Relay Endpoint: $RELAY_URL/api/v1/admin/applications"
echo "------------------------------------------------------------"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$RELAY_URL/api/v1/admin/applications" \
  -H "X-Admin-Token: $ADMIN_SECRET_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$APP_NAME\"}")

HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_STATUS" -ne 201 ]; then
    echo "Error: Application registration failed (HTTP $HTTP_STATUS)!"
    echo "Server response: $BODY"
    exit 1
fi

API_KEY=$(echo "$BODY" | grep -o '"apiKey":"[^"]*' | cut -d'"' -f4)
APP_ID=$(echo "$BODY" | grep -o '"id":"[^"]*' | cut -d'"' -f4)

echo ""
echo "============================================================"
echo " Application Registered Successfully!"
echo "============================================================"
echo " Application Name : $APP_NAME"
echo " Application ID   : $APP_ID"
echo " API Secret Key   : $API_KEY"
echo "============================================================"
echo ""
echo ">>> COPY THESE CONFIGURATION VALUES TO YOUR TARGET SERVICE <<<"
echo ""
echo "# Add to your target service's .env / .kamal/secrets file:"
echo "RELAY_URL=$RELAY_URL"
echo "RELAY_API_KEY=$API_KEY"
echo ""
echo "------------------------------------------------------------"
