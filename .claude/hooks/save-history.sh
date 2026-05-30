#!/bin/bash

# stdin으로 들어오는 JSON 파싱
INPUT=$(cat)

# JSONL 파일 flush 대기 (Stop 훅 실행 시점에 JSONL 쓰기가 아직 진행 중일 수 있음)
sleep 2

# transcript_path 및 last_assistant_message 추출
TRANSCRIPT_PATH=$(echo "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('transcript_path',''))" 2>/dev/null)
LAST_MSG=$(echo "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('last_assistant_message',''))" 2>/dev/null)

if [ -z "$TRANSCRIPT_PATH" ] || [ ! -f "$TRANSCRIPT_PATH" ]; then
  echo "[History] transcript_path 없음 또는 파일 없음: ${TRANSCRIPT_PATH}" >&2
  exit 0
fi

# JSONL에서 대화 내용 추출
# last_assistant_message 를 함께 전달해 JSONL race condition 보완:
# JSONL의 마지막 assistant 텍스트가 비어있거나 LAST_MSG보다 짧으면 LAST_MSG로 대체
TRANSCRIPT=$(python3 - "$TRANSCRIPT_PATH" "$LAST_MSG" << 'PYEOF'
import sys, json

transcript_path = sys.argv[1]
last_msg = sys.argv[2] if len(sys.argv) > 2 else ''

lines = []
with open(transcript_path) as f:
    for line in f:
        obj = json.loads(line)
        t = obj.get('type', '')
        if t not in ('user', 'assistant'):
            continue
        msg = obj.get('message', {})
        role = msg.get('role', t)
        content = msg.get('content', '')

        if isinstance(content, list):
            content = ' '.join(
                c.get('text', '') for c in content
                if isinstance(c, dict) and c.get('type') == 'text'
            )

        content = content.strip()
        if not content:
            continue

        if role == 'user':
            lines.append(('user', content))
        elif role == 'assistant':
            lines.append(('assistant', content))

# JSONL의 마지막 assistant가 last_msg보다 짧으면 대체 (race condition 보완)
if last_msg and last_msg.strip():
    for i in range(len(lines) - 1, -1, -1):
        if lines[i][0] == 'assistant':
            if len(lines[i][1]) < len(last_msg.strip()):
                lines[i] = ('assistant', last_msg.strip())
            break
    else:
        lines.append(('assistant', last_msg.strip()))

out = []
for role, content in lines:
    if role == 'user':
        out.append(f'## 질문\n\n{content}')
    else:
        out.append(f'## 답변\n\n{content}')

print('\n\n---\n\n'.join(out))
PYEOF
)

# 첫 번째 질문에서 주제 추출 (20자 이내, 특수문자 제거)
TOPIC=$(python3 -c "
import sys, json, re

with open(sys.argv[1]) as f:
    for line in f:
        obj = json.loads(line)
        if obj.get('type') != 'user':
            continue
        msg = obj.get('message', {})
        content = msg.get('content', '')
        if isinstance(content, list):
            content = ' '.join(
                c.get('text', '') for c in content
                if isinstance(c, dict) and c.get('type') == 'text'
            )
        content = content.strip()
        if not content:
            continue
        topic = re.sub(r'[^\w\sㄱ-힣]', '', content).strip()
        topic = re.sub(r'\s+', '_', topic)[:20]
        print(topic)
        break
" "$TRANSCRIPT_PATH" 2>/dev/null)

[ -z "$TOPIC" ] && TOPIC="대화"

# 날짜/시간
DATE_DIR=$(date +%Y%m%d)
HHMM=$(date +%H%M)

# 저장 경로 생성
SAVE_DIR="$(dirname "$(dirname "$(dirname "$0")")")/History/${DATE_DIR}"
mkdir -p "$SAVE_DIR"

FILENAME="${HHMM}-${TOPIC}.md"
FILEPATH="${SAVE_DIR}/${FILENAME}"
DATETIME=$(date '+%Y-%m-%d %H:%M')

# 마크다운 파일 작성
# printf '%s' 로 출력 → heredoc 과 달리 $TRANSCRIPT 내 백틱·$변수를 shell이 재해석하지 않음
{
  printf '# %s\n\n> 생성일시: %s\n\n' "$TOPIC" "$DATETIME"
  printf '%s\n' "$TRANSCRIPT"
} > "$FILEPATH"

echo "[History] 저장 완료: ${FILEPATH}" >&2

# Git 자동 커밋 & 푸시 (백그라운드, 실패해도 훅은 성공으로 처리)
(
  cd "$(dirname "$(dirname "$(dirname "$0")")")" || exit 0
  git add "99.History/${DATE_DIR}/${FILENAME}" 2>/dev/null || exit 0
  git diff --cached --quiet && exit 0
  git commit -m "history: ${DATE_DIR} ${HHMM} - ${TOPIC}" \
    --no-gpg-sign 2>/dev/null || exit 0
  git push 2>/dev/null \
    && echo "[History] Git 푸시 완료" >&2 \
    || echo "[History] Git 푸시 실패 (로컬 저장은 완료)" >&2
) &
