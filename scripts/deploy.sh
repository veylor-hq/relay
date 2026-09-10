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

echo "--> Stopping running Relay container to free host port..."
kamal app stop || true

echo "--> Deploying updated Relay container..."
kamal deploy "$@"
