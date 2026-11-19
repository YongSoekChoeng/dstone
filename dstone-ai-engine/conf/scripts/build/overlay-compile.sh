#!/usr/bin/env bash
################################################################################
# 변경분을 원본 사본에 덮어씌워 컴파일해 보는 범용 스크립트 (리눅스/WSL. Windows는 overlay-compile.bat)
#
# 원본은 건드리지 않습니다. 작업 폴더에 원본 사본을 새로 만들고, 변경 폴더를 같은 경로로 덮어씌운 뒤,
# 삭제 목록의 파일을 사본에서 지우고 빌드 명령을 돌립니다. "만든 코드가 원본과 합쳐서 컴파일되는가"만 봅니다.
#
# 사용법: overlay-compile.sh <원본 폴더> <변경 폴더> <작업 폴더> [삭제 목록 파일] [빌드 명령]
#   원본 폴더   : 빌드 파일(pom.xml, build.gradle 등)이 있는 소스 루트
#   변경 폴더   : 원본 루트 기준 같은 경로로 바뀐 파일·새 파일이 들어 있는 폴더
#   작업 폴더   : 사본(<작업 폴더>/source)과 빌드 로그(<작업 폴더>/build.log)를 둘 곳. 실행할 때마다 사본을 새로 만든다
#   삭제 목록   : 사본에서 지울 경로(원본 루트 기준 상대경로, 한 줄에 하나, # 주석). 없으면 "" 또는 생략. 변경 폴더 안에 있어도 된다
#   빌드 명령   : 생략하면 빌드 파일을 보고 정한다. pom.xml → mvn -B -q compile -DskipTests,
#                 build.gradle(.kts) → ./gradlew(없으면 gradle) -q compileJava. 그 밖은 직접 줘야 한다
#   Windows 경로(D:/a/b)를 줘도 /mnt/d/a/b로 바꿔 쓴다.
#
# 결과: 성공이면 종료 코드 0. 실패면 0이 아니고 오류 줄만 추려 출력한다(경로는 원본 루트 기준).
#       엔진의 runShellScript는 종료 코드가 0이 아니면 "실패: ..."로 돌려준다.
################################################################################
set -u

to_local_path() {
	local p="${1//\\//}"
	if [[ "$p" =~ ^([A-Za-z]):/(.*)$ ]]; then
		echo "/mnt/${BASH_REMATCH[1],,}/${BASH_REMATCH[2]}"
	else
		echo "$p"
	fi
}

if [ $# -lt 3 ]; then
	echo "usage: overlay-compile.sh <source-dir> <overlay-dir> <work-dir> [delete-list-file] [build-command]"
	exit 2
fi
SOURCE_DIR="$(to_local_path "$1")"; SOURCE_DIR="${SOURCE_DIR%/}"
OVERLAY_DIR="$(to_local_path "$2")"; OVERLAY_DIR="${OVERLAY_DIR%/}"
WORK_DIR="$(to_local_path "$3")"; WORK_DIR="${WORK_DIR%/}"
DELETE_LIST=""
[ $# -ge 4 ] && [ -n "$4" ] && DELETE_LIST="$(to_local_path "$4")"
BUILD_CMD=""
[ $# -ge 5 ] && BUILD_CMD="$5"
COPY_DIR="$WORK_DIR/source"
LOG_FILE="$WORK_DIR/build.log"

[ -d "$SOURCE_DIR" ] || { echo "source dir not found: $SOURCE_DIR"; exit 2; }
[ -d "$OVERLAY_DIR" ] || { echo "overlay dir not found: $OVERLAY_DIR"; exit 2; }

# 빌드 명령이 없으면 빌드 파일을 보고 정한다
MAVEN=0
if [ -z "$BUILD_CMD" ]; then
	if [ -f "$SOURCE_DIR/pom.xml" ]; then
		BUILD_CMD="mvn -B -q compile -DskipTests"
		MAVEN=1
	elif [ -f "$SOURCE_DIR/build.gradle" ] || [ -f "$SOURCE_DIR/build.gradle.kts" ]; then
		if [ -f "$SOURCE_DIR/gradlew" ]; then
			BUILD_CMD="sh ./gradlew -q compileJava"
		else
			BUILD_CMD="gradle -q compileJava"
		fi
	else
		echo "no build file (pom.xml, build.gradle) in $SOURCE_DIR. give the build command as the 5th argument."
		exit 2
	fi
elif [[ "$BUILD_CMD" == mvn* ]]; then
	MAVEN=1
fi

# 1) 원본 사본을 새로 만든다. 빌드 산출물, 저장소, 의존성 폴더는 뺀다
rm -rf "$COPY_DIR"
mkdir -p "$COPY_DIR"
(cd "$SOURCE_DIR" && tar --exclude=./target --exclude=./build --exclude=./.gradle --exclude=./.git --exclude=./node_modules -cf - .) | (cd "$COPY_DIR" && tar -xf -)

# 2) 변경 폴더를 덮어씌운다(삭제 목록 파일이 변경 폴더 안에 있으면 그 파일은 빼고)
EXCLUDE_LIST=()
if [ -n "$DELETE_LIST" ] && [[ "$DELETE_LIST" == "$OVERLAY_DIR"/* ]]; then
	EXCLUDE_LIST=(--exclude="./${DELETE_LIST#"$OVERLAY_DIR"/}")
fi
(cd "$OVERLAY_DIR" && tar "${EXCLUDE_LIST[@]}" -cf - .) | (cd "$COPY_DIR" && tar -xf -)

# 3) 삭제 목록의 경로를 사본에서 지운다
if [ -n "$DELETE_LIST" ] && [ -f "$DELETE_LIST" ]; then
	while IFS= read -r line || [ -n "$line" ]; do
		rel="$(echo "$line" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's#\\#/#g' -e 's#^/##')"
		# 빈 줄, 주석, 원본 루트 밖을 가리키는 경로는 무시한다
		if [ -z "$rel" ] || [[ "$rel" == \#* ]] || [[ "$rel" == *..* ]]; then
			continue
		fi
		rm -rf "${COPY_DIR:?}/$rel"
		echo "deleted in copy: $rel"
	done < "$DELETE_LIST"
fi

# 4) 빌드한다. 전체 로그는 build.log에 남기고 오류 줄만 보여 준다
(cd "$COPY_DIR" && eval "$BUILD_CMD") > "$LOG_FILE" 2>&1
CODE=$?
if [ $CODE -eq 0 ]; then
	echo "compile OK: $SOURCE_DIR + $OVERLAY_DIR ($BUILD_CMD)"
	exit 0
fi
echo "compile FAILED (exit $CODE, $BUILD_CMD). errors (full log: $LOG_FILE):"
if [ $MAVEN -eq 1 ] && grep -q "Failed to execute goal" "$LOG_FILE"; then
	# Maven은 같은 오류를 요약과 상세(없는 심볼 이름 포함)로 두 번 찍는다. 상세 쪽만 보여 준다
	awk 'found && /^\[ERROR\]/ {print} /Failed to execute goal/ {found=1}' "$LOG_FILE" \
		| grep -vE "To see the full stack trace|Re-run Maven|For more information|-> \[Help|^\[ERROR\] *$|^\[Help"
elif grep -qiE "error" "$LOG_FILE"; then
	grep -iE -A2 "error" "$LOG_FILE"
else
	tail -n 60 "$LOG_FILE"
fi | sed -e "s#$COPY_DIR/##g" | head -80
exit $CODE
