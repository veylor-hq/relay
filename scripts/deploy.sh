#!/usr/bin/env bash
# ==============================================================================
# Veylor Relay - Safe Deployment Script
#
# Because Relay binds directly to host ports without a reverse proxy (proxy: false),
# the running container must be stopped first to avoid port collision (8085).
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# If infisical CLI is installed and .kamal/secrets is missing or empty, fetch from Infisical
if command -v infisical &>/dev/null; then
    if [ ! -s .kamal/secrets ]; then
        echo "--> Pulling secrets from Infisical (/relay)..."
        mkdir -p .kamal
        infisical export --domain="https://eu.infisical.com" --env="prod" --path="/relay" --format="dotenv" > .kamal/secrets
    fi
fi

echo "--> Stopping running Relay container to free host port..."
kamal app stop || true

echo "--> Deploying updated Relay container..."
kamal deploy "$@"
