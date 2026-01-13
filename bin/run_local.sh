#!/bin/bash
set -e

PID=.run/actionbase.pid
LOG=.run/logs/actionbase.log
PORT=8080

[ -f "$PID" ] && { echo "❌ failed: already running"; exit 1; }

mkdir -p .run/logs

./gradlew :server:bootRun > "$LOG" 2>&1 &
echo $! > "$PID"

until nc -z localhost $PORT; do sleep 1; done

echo "✅ Actionbase server started on $PORT 🚀"
echo "log: $LOG"
echo "pid: $PID"
