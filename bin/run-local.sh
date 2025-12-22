#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="$ROOT_DIR/logs/server-local.log"
PID_FILE="$ROOT_DIR/.actionbase-local.pid"

mkdir -p "$(dirname "$LOG_FILE")"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Actionbase already running (pid=$(cat "$PID_FILE"))"
  exit 0
fi

echo "Starting Actionbase (local)..."
cd "$ROOT_DIR"
./gradlew :server:bootRun >"$LOG_FILE" 2>&1 &
PID=$!
echo "$PID" >"$PID_FILE"

cleanup() {
  [ -n "${TAIL_PID:-}" ] && kill "$TAIL_PID" 2>/dev/null || true
}
trap cleanup EXIT

for _ in {1..50}; do [ -f "$LOG_FILE" ] && break; sleep 0.1; done
tail -n 30 -f "$LOG_FILE" &
TAIL_PID=$!

echo "Waiting for liveness: http://localhost:8080/graph/health/liveness"
for i in {1..30}; do
  curl -sf http://localhost:8080/graph/health/liveness >/dev/null && break
  ! kill -0 "$PID" 2>/dev/null && echo "Actionbase exited unexpectedly. Last logs:" && tail -n 80 "$LOG_FILE" && exit 1
  [ $i -eq 30 ] && echo "Timed out waiting for liveness. Last logs:" && tail -n 80 "$LOG_FILE" && exit 1
  sleep 2
done

echo "Actionbase is ready 🎉  (pid=$PID)"
echo "URL: http://localhost:8080"
echo "Logs: $LOG_FILE"