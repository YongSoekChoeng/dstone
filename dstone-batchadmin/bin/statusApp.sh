#!/bin/sh
# dstone-batchadmin 상태 확인 스크립트

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/application.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "dstone-batchadmin: RUNNING (PID: $(cat "$PID_FILE"))"
else
    echo "dstone-batchadmin: STOPPED"
fi
