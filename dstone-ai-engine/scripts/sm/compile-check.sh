#!/usr/bin/env bash
################################################################################
# sm-workflow 컴파일 확인 스크립트 (WSL/리눅스용. Windows는 compile-check.bat)
#
# 개발 단계가 만든 dev-resources를 원본 소스 사본에 덮어씌워 컴파일해 봅니다.
# 원본 소스는 건드리지 않습니다. 사본은 작업 폴더 아래 compile-check/source 에 만듭니다.
#
# 사용법: compile-check.sh <작업 폴더>
#   작업 폴더 = 요청 폴더/실행 시각 (예: D:/WorkShop/00.SM/work002/20261119-1441)
#   - 원본 소스 경로는 작업 폴더의 부모 폴더에 있는 00.request.md 의 "## 소스경로" 다음 줄에서 읽습니다.
#   - dev-resources/_delete-files.txt 에 적힌 경로(소스 루트 기준, 한 줄에 하나)는 사본에서 지웁니다.
#
# 결과: 통과하면 종료 코드 0. 실패하면 0이 아니고, 컴파일 오류 줄([ERROR])만 추려서 출력합니다.
#       엔진의 runShellScript는 종료 코드가 0이 아니면 "실패: ..."로 돌려주므로 TOOL step이 실패로 판정합니다.
# Maven 빌드(pom.xml)만 지원합니다.
################################################################################
set -u

# D:/a/b 같은 Windows 경로를 WSL 경로(/mnt/d/a/b)로 바꾼다
to_local_path() {
	local p="${1//\\//}"
	if [[ "$p" =~ ^([A-Za-z]):/(.*)$ ]]; then
		echo "/mnt/${BASH_REMATCH[1],,}/${BASH_REMATCH[2]}"
	else
		echo "$p"
	fi
}

if [ $# -lt 1 ]; then
	echo "usage: compile-check.sh <work-dir>"
	exit 2
fi

WORK_DIR="$(to_local_path "$1")"
WORK_DIR="${WORK_DIR%/}"
REQUEST_FILE="$(dirname "$WORK_DIR")/00.request.md"
DEV_DIR="$WORK_DIR/dev-resources"
CHECK_DIR="$WORK_DIR/compile-check"
COPY_DIR="$CHECK_DIR/source"

if [ ! -f "$REQUEST_FILE" ]; then
	echo "request file not found: $REQUEST_FILE"
	exit 2
fi

# "## 소스경로" 제목 다음의 첫 번째 빈 줄이 아닌 줄이 원본 소스 경로다
SOURCE_RAW="$(awk '/^##[[:space:]]*소스경로/{found=1; next} found && /^##/{exit} found && NF {gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$REQUEST_FILE" | tr -d '\r')"
if [ -z "$SOURCE_RAW" ]; then
	echo "source path not found under '## 소스경로' in $REQUEST_FILE"
	exit 2
fi
SOURCE_DIR="$(to_local_path "$SOURCE_RAW")"
SOURCE_DIR="${SOURCE_DIR%/}"
if [ ! -f "$SOURCE_DIR/pom.xml" ]; then
	echo "pom.xml not found in source: $SOURCE_DIR (only Maven projects are supported)"
	exit 2
fi
if [ ! -d "$DEV_DIR" ]; then
	echo "dev-resources not found: $DEV_DIR"
	exit 2
fi

# 1) 원본 사본을 새로 만든다(지난 확인의 흔적이 남지 않게). 빌드 산출물과 .git 같은 큰 폴더는 뺀다
rm -rf "$COPY_DIR"
mkdir -p "$COPY_DIR"
(cd "$SOURCE_DIR" && tar --exclude=./target --exclude=./.git --exclude=./uploadData --exclude=./node_modules -cf - .) | (cd "$COPY_DIR" && tar -xf -)

# 2) dev-resources를 덮어씌운다(삭제 목록 파일은 빼고)
(cd "$DEV_DIR" && tar --exclude=./_delete-files.txt -cf - .) | (cd "$COPY_DIR" && tar -xf -)

# 3) 삭제 목록에 적힌 파일을 사본에서 지운다
DELETE_LIST="$DEV_DIR/_delete-files.txt"
if [ -f "$DELETE_LIST" ]; then
	while IFS= read -r line || [ -n "$line" ]; do
		rel="$(echo "$line" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's#\\#/#g' -e 's#^/##')"
		# 빈 줄, 주석, 소스 루트 밖을 가리키는 경로는 무시한다
		if [ -z "$rel" ] || [[ "$rel" == \#* ]] || [[ "$rel" == *..* ]]; then
			continue
		fi
		rm -rf "$COPY_DIR/$rel"
		echo "deleted in copy: $rel"
	done < "$DELETE_LIST"
fi

# 4) 컴파일한다. 전체 로그는 compile-check/mvn.log 에 남기고, 오류 줄만 출력한다
LOG_FILE="$CHECK_DIR/mvn.log"
(cd "$COPY_DIR" && mvn -B -q ${SM_MVN_OPTS:-} compile -DskipTests) > "$LOG_FILE" 2>&1
CODE=$?
if [ $CODE -eq 0 ]; then
	echo "compile OK: $SOURCE_DIR + $DEV_DIR"
	exit 0
fi
echo "compile FAILED (exit $CODE). errors (full log: $LOG_FILE):"
# Maven은 같은 오류를 요약("COMPILATION ERROR")과 상세("Failed to execute goal" 아래, 없는 심볼 이름 포함)로 두 번 찍는다.
# 상세 쪽이 있으면 그것만, 없으면(의존성 오류 등) [ERROR] 줄 전체를 보여 준다.
# 파일 경로는 사본 경로를 떼어 소스 루트 기준으로 보여 준다(dev-resources 안의 같은 경로를 고치면 된다).
if grep -q "Failed to execute goal" "$LOG_FILE"; then
	FILTER='found && /^\[ERROR\]/ {print} /Failed to execute goal/ {found=1}'
else
	FILTER='/^\[ERROR\]/ {print}'
fi
awk "$FILTER" "$LOG_FILE" \
	| grep -vE "To see the full stack trace|Re-run Maven|For more information|-> \[Help|^\[ERROR\] *$|^\[Help" \
	| sed -E 's#^\[ERROR\] .*compile-check/source/#[ERROR] #' \
	| head -60
exit $CODE
