#!/usr/bin/env bash
################################################################################
# sm-workflow 시험 전에 대상 프로젝트(예: testApp)를 기준 상태로 되돌리는 스크립트 (WSL에서 실행)
#
# 왜 필요한가: 생성한 코드를 대상 소스에 넣어 보거나, 그 코드가 DB에 테이블·메뉴를 만든 채로 다시 돌리면
#   분석·설계 단계가 그것을 "이미 있는 기능"으로 읽어 결과가 흔들린다(2026-11-19 실행 4c163653).
#
# 사용법: reset-target.sh <요청 폴더 또는 소스 경로> [--db] [--apply] [--paths "src db"] [--db-name testApp]
#   - 요청 폴더(00.request.md가 있는 폴더)를 주면 "## 소스경로" 다음 줄에서 소스 경로를 읽는다.
#   - 기본은 미리 보기다. 무엇을 되돌릴지 보여 주기만 하고 아무것도 바꾸지 않는다. 실제로 하려면 --apply.
#
# 하는 일
#   1) 소스: git으로 관리되는 대상 소스에서 --paths(기본 "src db") 아래의 바뀐 파일과 새 파일을
#      git stash에 넣는다(지우지 않는다). 되살리려면 출력에 나오는 git stash pop 명령을 쓴다.
#   2) DB(--db를 줬을 때만): 대상 DB를 mysqldump로 백업한 뒤 모든 테이블을 지우고,
#      대상 소스의 db/schema.sql과 db/seed.sql(되돌린 뒤의 기준 파일)로 다시 만든다.
#      접속 정보는 환경 변수 MYSQL_HOST(기본 localhost), MYSQL_PORT(3306), MYSQL_USER(testapp), MYSQL_PWD(필수)로 준다.
#      비밀번호를 이 파일이나 명령줄에 적지 않는다: export MYSQL_PWD=... 후 실행한다.
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

TARGET=""
DO_DB=0
APPLY=0
PATHS="src db"
DB_NAME="testApp"
while [ $# -gt 0 ]; do
	case "$1" in
		--db) DO_DB=1 ;;
		--apply) APPLY=1 ;;
		--paths) PATHS="$2"; shift ;;
		--db-name) DB_NAME="$2"; shift ;;
		*) TARGET="$1" ;;
	esac
	shift
done
if [ -z "$TARGET" ]; then
	echo "사용법: reset-target.sh <요청 폴더 또는 소스 경로> [--db] [--apply] [--paths \"src db\"] [--db-name testApp]"
	exit 2
fi

TARGET="$(to_local_path "$TARGET")"
TARGET="${TARGET%/}"
SOURCE_DIR="$TARGET"
if [ -f "$TARGET/00.request.md" ]; then
	RAW="$(awk '/^##[[:space:]]*소스경로/{found=1; next} found && /^##/{exit} found && NF {gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$TARGET/00.request.md" | tr -d '\r')"
	if [ -z "$RAW" ]; then
		echo "00.request.md에서 '## 소스경로'를 찾지 못했습니다: $TARGET/00.request.md"
		exit 2
	fi
	SOURCE_DIR="$(to_local_path "$RAW")"
	SOURCE_DIR="${SOURCE_DIR%/}"
fi
if ! git -C "$SOURCE_DIR" rev-parse --is-inside-work-tree > /dev/null 2>&1; then
	echo "git 저장소가 아닙니다: $SOURCE_DIR (소스를 되돌릴 기준이 없습니다)"
	exit 2
fi

MODE="미리 보기(바꾸지 않음. 실제로 하려면 --apply)"
[ $APPLY -eq 1 ] && MODE="실행"
echo "== 대상 소스: $SOURCE_DIR"
echo "== 모드: $MODE"

# Windows에서 쓰는 저장소를 WSL git으로 볼 때는 Windows git과 같은 기준으로 본다.
#  - core.autocrlf: Git for Windows 기본값(true). 맞추지 않으면 줄바꿈 차이로 모든 파일이 "바뀜"으로 보이고,
#    stash가 그 파일을 전부 LF로 다시 써 버린다. 다른 값을 쓰면 SM_GIT_AUTOCRLF로 준다.
#  - core.filemode: 실행 권한 차이는 변경으로 보지 않는다.
GIT="git -C $SOURCE_DIR -c core.filemode=false -c core.autocrlf=${SM_GIT_AUTOCRLF:-true}"

# 1) 소스
# shellcheck disable=SC2086
CHANGES="$($GIT status --porcelain --untracked-files=all -- $PATHS)"
if [ -z "$CHANGES" ]; then
	echo "-- 소스: [$PATHS] 아래에 바뀐 파일이 없습니다."
else
	echo "-- 소스: [$PATHS] 아래에서 되돌릴 파일(?? = 새 파일, M = 바뀐 파일, D = 지운 파일):"
	echo "$CHANGES" | sed 's/^/   /'
	if [ $APPLY -eq 1 ]; then
		LABEL="sm-reset $(date +%Y%m%d-%H%M%S)"
		# shellcheck disable=SC2086
		if $GIT stash push --include-untracked -m "$LABEL" -- $PATHS > /dev/null; then
			echo "   -> git stash에 넣었습니다: \"$LABEL\""
			echo "      되살리기: git -C \"$SOURCE_DIR\" stash list 로 찾아서 git -C \"$SOURCE_DIR\" stash pop stash@{n}"
		else
			echo "   -> git stash에 실패했습니다. 아무것도 바꾸지 않았습니다."
			exit 1
		fi
	fi
fi

# 2) DB
if [ $DO_DB -eq 1 ]; then
	SCHEMA="$SOURCE_DIR/db/schema.sql"
	SEED="$SOURCE_DIR/db/seed.sql"
	if [ ! -f "$SCHEMA" ]; then
		echo "-- DB: 기준 스키마 파일이 없습니다: $SCHEMA"
		exit 2
	fi
	if [ -z "${MYSQL_PWD:-}" ]; then
		echo "-- DB: MYSQL_PWD 환경 변수가 없습니다. export MYSQL_PWD=... 후 다시 실행하십시오."
		exit 2
	fi
	export MYSQL_PWD
	MYSQL="mysql -h ${MYSQL_HOST:-localhost} -P ${MYSQL_PORT:-3306} -u ${MYSQL_USER:-testapp} --default-character-set=utf8mb4"
	TABLES="$($MYSQL -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE'")" || exit 1
	echo "-- DB: $DB_NAME 의 테이블 $(echo "$TABLES" | grep -c .)개를 지우고 schema.sql${SEED:+, seed.sql}로 다시 만듭니다."
	if [ $APPLY -eq 1 ]; then
		BACKUP_DIR="$HOME/sm-reset-backup"
		mkdir -p "$BACKUP_DIR"
		BACKUP="$BACKUP_DIR/$DB_NAME-$(date +%Y%m%d-%H%M%S).sql"
		mysqldump -h "${MYSQL_HOST:-localhost}" -P "${MYSQL_PORT:-3306}" -u "${MYSQL_USER:-testapp}" --no-tablespaces "$DB_NAME" > "$BACKUP" || { echo "   -> 백업에 실패해 멈춥니다."; exit 1; }
		echo "   -> 백업: $BACKUP"
		DROP="SET FOREIGN_KEY_CHECKS=0;"
		for t in $TABLES; do
			DROP="$DROP DROP TABLE \`$t\`;"
		done
		$MYSQL "$DB_NAME" -e "$DROP SET FOREIGN_KEY_CHECKS=1;" || exit 1
		# schema.sql의 CREATE DATABASE 문은 권한이 없을 수 있어 빼고 넣는다(DB는 이미 있다)
		grep -viE "^[[:space:]]*CREATE DATABASE|^[[:space:]]*DEFAULT CHARACTER SET" "$SCHEMA" | $MYSQL "$DB_NAME" || exit 1
		if [ -f "$SEED" ]; then
			$MYSQL "$DB_NAME" < "$SEED" || exit 1
		fi
		echo "   -> 다시 만들었습니다."
	fi
fi
echo "== 끝"
