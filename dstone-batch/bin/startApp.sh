#!/bin/sh
# dstone-batch 시작 스크립트 (백그라운드 실행)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$(dirname "$SCRIPT_DIR")"
PROFILE="${DSTONE_PROFILE:-wsl}"
JAVA_OPTS="-Xms512m -Xmx1024m -Dspring.profiles.active=$PROFILE"
MAIN_CLASS="net.dstone.batch.common.DstoneBatchApplication"
PID_FILE="$SCRIPT_DIR/application.pid"
LOG_DIR="$APP_HOME/logs"
LOG_FILE="$LOG_DIR/dstone-batch.out"

JAR_FILE="$(ls "$APP_HOME"/target/dstone-batch-*.jar 2>/dev/null | head -n 1)"
if [ -z "$JAR_FILE" ]; then
    echo "실행할 jar 파일을 찾을 수 없습니다. 먼저 mvn clean package 를 실행하세요. (${APP_HOME}/target)"
    exit 1
fi

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "dstone-batch가 이미 실행 중입니다. (PID: $(cat "$PID_FILE"))"
    exit 1
fi

mkdir -p "$LOG_DIR"

echo "dstone-batch를 백그라운드로 시작합니다... ($JAR_FILE)"
nohup java $JAVA_OPTS -jar "$JAR_FILE" $MAIN_CLASS > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

sleep 2

if kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "dstone-batch가 시작되었습니다. (PID: $(cat "$PID_FILE"))"
    echo "로그: $LOG_FILE"
else
    echo "dstone-batch 시작에 실패했습니다. 로그를 확인하세요: $LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
fi
