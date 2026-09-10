#!/usr/bin/env bash
set -e

# Bot Credentials from environment
BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
CHAT_ID="${TELEGRAM_DEPLOY_CHAT_ID:-${TELEGRAM_CHAT_ID:-}}"
EXPLICIT_THREAD="${TELEGRAM_DEPLOY_THREAD_ID:-${TELEGRAM_MESSAGE_THREAD_ID:-}}"
THREAD_ID="$EXPLICIT_THREAD"
PARSED_THREAD=""

# Parse URL (https://t.me/c/<chat_id>/<thread_id> or https://t.me/<chat_id>/<thread_id>)
if [[ "$CHAT_ID" =~ t\.me/(c/)?([0-9]+)/([0-9]+) ]]; then
  CHAT_ID="-100${BASH_REMATCH[2]}"
  PARSED_THREAD="${BASH_REMATCH[3]}"
elif [[ "$CHAT_ID" == *"/"* ]]; then
  PARSED_THREAD="${CHAT_ID#*/}"
  CHAT_ID="${CHAT_ID%%/*}"
fi

# Explicit deploy thread ID takes precedence; otherwise use parsed from chat_id
if [ -n "$EXPLICIT_THREAD" ]; then
  THREAD_ID="$EXPLICIT_THREAD"
elif [ -n "$PARSED_THREAD" ]; then
  THREAD_ID="$PARSED_THREAD"
elif [[ "$CHAT_ID" =~ ^-100 ]]; then
  # Default CI/CD deployment notifications to topic 3
  THREAD_ID="3"
fi

# In Telegram Bot API, supergroups require the -100 prefix
if [ -n "$THREAD_ID" ] && [[ "$CHAT_ID" =~ ^[0-9]+$ ]]; then
  CHAT_ID="-100${CHAT_ID}"
fi

if [ -z "$BOT_TOKEN" ] || [ -z "$CHAT_ID" ]; then
  echo "Telegram bot token or chat ID not provided. Skipping notification."
  exit 0
fi

STATE="${1:-processing}"
SERVICE_NAME="${2:-Veylor SSO}"
EXTRA_DETAILS="${3:-}"

# Details from Git/GitHub if available
COMMIT_SHA="${GITHUB_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')}"
SHORT_SHA="${COMMIT_SHA:0:7}"
BRANCH="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'main')}"
ACTOR="${GITHUB_ACTOR:-$(whoami)}"
SERVER_IP="${TARGET_SERVER_IP:-server}"

case "$STATE" in
  processing)
    EMOJI="🛠"
    TITLE="Deployment Task Started"
    STATUS_TEXT="Build & deployment task is being processed."
    ;;
  queued)
    EMOJI="📦"
    TITLE="Image Built & Queued"
    STATUS_TEXT="Docker image pushed to registry successfully. Queued for server deploy."
    ;;
  partial)
    EMOJI="⏳"
    TITLE="Partially Deployed"
    STATUS_TEXT="New container booted on server. Performing health checks & proxy routing."
    ;;
  success)
    EMOJI="✅"
    TITLE="Deployed Fully"
    STATUS_TEXT="Deployment complete! Health check passed & live traffic updated."
    ;;
  failure)
    EMOJI="🚨"
    TITLE="Deployment Failed"
    STATUS_TEXT="Build or deployment encountered an error."
    ;;
  *)
    EMOJI="ℹ️"
    TITLE="Deployment Alert"
    STATUS_TEXT="$STATE"
    ;;
esac

MESSAGE="<b>${EMOJI} [${SERVICE_NAME}] ${TITLE}</b>

<b>Status:</b> ${STATUS_TEXT}
<b>Service:</b> <code>${SERVICE_NAME}</code>
<b>Server:</b> <code>${SERVER_IP}</code>
<b>Branch:</b> <code>${BRANCH}</code>
<b>Commit:</b> <code>${SHORT_SHA}</code>
<b>Triggered by:</b> ${ACTOR}"

if [ -n "$EXTRA_DETAILS" ]; then
  MESSAGE="${MESSAGE}

<b>Details:</b> ${EXTRA_DETAILS}"
fi

if [ -n "$GITHUB_RUN_ID" ] && [ -n "$GITHUB_REPOSITORY" ]; then
  MESSAGE="${MESSAGE}

<a href=\"https://github.com/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}\">View GitHub Workflow Run</a>"
fi

CURL_ARGS=(
  -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage"
  -d "chat_id=${CHAT_ID}"
  -d "parse_mode=HTML"
  -d "disable_web_page_preview=true"
  -d "text=${MESSAGE}"
)

if [ -n "$THREAD_ID" ]; then
  CURL_ARGS+=(-d "message_thread_id=${THREAD_ID}")
fi

RESPONSE=$(curl "${CURL_ARGS[@]}" 2>&1 || true)
if echo "$RESPONSE" | grep -q '"ok":false'; then
  echo "Telegram notification warning: $RESPONSE" >&2
fi
