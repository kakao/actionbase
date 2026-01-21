#!/bin/bash
set -e

SERVER_HOST="${AB_SERVER_HOST:-localhost}"
SERVER_PORT="${AB_SERVER_PORT:-8080}"
SERVER_URL="http://${SERVER_HOST}:${SERVER_PORT}"
HEALTH_URL="${SERVER_URL}/actuator/health"
MAX_WAIT_SECONDS=60

# Start server in background
java -jar /app/server.jar &

SERVER_PID=$!

# Function to cleanup on exit
cleanup() {
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true
    exit 0
}
trap cleanup EXIT INT TERM

# Wait for server to be ready
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; do
    if curl -s -f "${HEALTH_URL}" > /dev/null 2>&1; then
        break
    fi

    # Check if server process is still running
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "Server process exited unexpectedly."
        exit 1
    fi

    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

if [ $ELAPSED -ge $MAX_WAIT_SECONDS ]; then
    echo "Server did not start within ${MAX_WAIT_SECONDS} seconds."
    exit 1
fi

# Start CLI in foreground with proxy and debug mode
actionbase --host "${SERVER_URL}" --proxy --debug "$@"
