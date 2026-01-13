#!/bin/bash
set -e

PID=.run/actionbase.pid

[ ! -f "$PID" ] && { echo "❌ failed: not running"; exit 1; }

kill "$(cat "$PID")"
rm "$PID"

echo "✅ Actionbase server stopped"
echo "pid: $PID"
