#!/bin/sh
# dstone-batchadmin 중지 스크립트

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/application.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID 파일이 없습니다. dstone-batchadmin이 실행 중이지 않은 것으로 보입니다."
    exit 0
fi

PID="$(cat "$PID_FILE")"

if ! kill -0 "$PID" 2>/dev/null; then
    echo "PID $PID 프로세스가 이미 종료된 상태입니다."
    rm -f "$PID_FILE"
    exit 0
fi

echo "dstone-batchadmin을 종료합니다... (PID: $PID)"
kill -15 "$PID"

COUNT=0
while kill -0 "$PID" 2>/dev/null; do
    sleep 1
    COUNT=$((COUNT + 1))
    if [ "$COUNT" -ge 30 ]; then
        echo "정상 종료가 지연되어 강제 종료합니다 (kill -9)."
        kill -9 "$PID"
        break
    fi
done

rm -f "$PID_FILE"
echo "dstone-batchadmin이 종료되었습니다."
