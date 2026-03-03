#!/bin/bash
APP_HOME="/opt/fix-engine"
PID_FILE="$APP_HOME/fix-engine.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    echo "Stopping FIX Engine (PID: $PID)..."
    kill -SIGTERM "$PID"
    sleep 5
    if kill -0 "$PID" 2>/dev/null; then
        echo "Process still running, sending SIGKILL..."
        kill -9 "$PID"
    fi
    rm -f "$PID_FILE"
    echo "FIX Engine stopped."
else
    echo "PID file not found. Is the engine running?"
fi
