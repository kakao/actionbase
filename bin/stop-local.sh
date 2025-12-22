#!/usr/bin/env bash
set -e

PID_FILE="$(cd "$(dirname "$0")/.." && pwd)/.actionbase-local.pid"

[ ! -f "$PID_FILE" ] && echo "No running Actionbase instance found" && exit 0

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  echo "Stopping Actionbase (pid=$PID)..."
  kill "$PID"
else
  echo "Process not running (stale pid file)"
fi

rm -f "$PID_FILE"
echo "Actionbase stopped"
