#!/bin/bash

# Find directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/../.."

# Load root .env to read the ADMIN_SECRET_TOKEN
if [ -f "$ROOT_DIR/.env" ]; then
    export $(grep -v '^#' "$ROOT_DIR/.env" | xargs)
fi

if [ -z "$ADMIN_SECRET_TOKEN" ]; then
    echo "Error: ADMIN_SECRET_TOKEN is not defined in root .env file."
    exit 1
fi

echo "Registering FastAPI Demo App with Veylor Relay..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/admin/applications \
  -H "X-Admin-Token: $ADMIN_SECRET_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "FastAPI Demo App"}')

API_KEY=$(echo "$RESPONSE" | grep -o '"apiKey":"[^"]*' | grep -o '[^"]*$')

if [ -z "$API_KEY" ]; then
    echo "Error: Failed to register application. Server response: $RESPONSE"
    exit 1
fi

echo "Application registered successfully!"
echo "API Key: $API_KEY"

# Write credentials to examples/fastapi_demo/.env
ENV_FILE="$DIR/.env"
echo "RELAY_URL=http://localhost:8080" > "$ENV_FILE"
echo "RELAY_API_KEY=$API_KEY" >> "$ENV_FILE"
echo "Wrote credentials to: $ENV_FILE"
