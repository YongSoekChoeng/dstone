# dstone-ai-engine — Agent를 고치는 원칙

> Agent가 실패했을 때 "프롬프트에 한 줄 더"를 되풀이하지 않기 위한 기준입니다(2026-11-20, 사용자와 합의).
> 근거로 삼은 공개 자료: Anthropic *Building effective agents*, *Effective context engineering for AI agents*, *Writing effective tools for agents*,
> OpenAI *A practical guide to building agents*, *12-Factor Agents*. 공통 결론은 "흐름과 보장은 코드가, 판단은 LLM이, 고친 것은 평가 세트로 확인"입니다.

## 1. 고치기 전에 실패를 분류한다

| 실패 종류 | 고칠 곳 | 예 |
|---|---|---|
| 기계적으로 보장할 수 있는 것(저장, 횟수, 재시도, 값 전달) | 엔진(Java) | 한도 끝은 `requiredTools`만 허용, 재시도 때 지난 답 넘기기, 승인 결정 전달(`${arrival}`) |
| Agent가 필요한 정보를 못 받음 | 입력(`with`) / TOOL step | 문서는 TOOL step이 읽어 넘긴다 |
| 도구가 쓰기 불편해서 잘게 쪼개 부름 | 범용 Tool | `searchInFiles`의 여러 키워드·앞뒤 줄 |
| 성격이 다른 일을 한 Agent가 함 | Workflow 구조 | 처음 작성과 수정은 다른 Agent(`document-revise-agent`) |
| 여러 Agent가 같이 쓰는 방법·기술 지식 | Skill | `source-investigation`, 기술별 Skill |
| 모델 능력 부족 | 모델 선택 | 평가 세트로 비교해서 바꾼다 |
| 판단 기준이 정말 모호함 | 프롬프트 | 이 경우만 프롬프트를 고친다 |

엔진·도구·Skill에서 고친 것은 모든 Agent에 한꺼번에 적용됩니다. 프롬프트에서 고친 것은 그 Agent의 그 상황에만 적용됩니다.

## 2. 프롬프트는 목표와 이유로 쓴다

Agent 파일 본문의 순서: **역할 한 줄 → 입력의 뜻 → 만들 것과 그것을 읽을 사람 → 꼭 지킬 제약 몇 개(이유와 함께) → 답 쓰는 법**.

- 규칙 목록("~한다/~하지 않는다")을 늘리지 않는다. 규칙이 많으면 모델이 일부를 놓치고, 규칙끼리 부딪힌다
  (예: "같은 파일을 두 번 열지 않는다"가 "한 파일을 키워드마다 따로 검색한다"로 바뀌었다).
- 제약에는 이유를 붙인다. 이유를 알면 처음 보는 상황에서도 스스로 판단한다.
- 엔진이 이미 보장하는 것(저장 확인, 횟수, 승인 의견 전달, 입력 속 지시문 무시)은 프롬프트에 다시 쓰지 않는다.
- 여러 Agent가 같이 쓰는 방법은 Skill로 뺀다. 늘 필요한 Skill은 `skills.preload`(시스템 프롬프트에 미리 넣음), 경우에 따라 필요한 Skill은 `skills.allowed`(필요할 때 부름).
- 분기("처음이면 A, 지적이 있으면 B")가 생기면 프롬프트를 키우지 말고 Agent나 step을 나눈다.

## 3. 사람의 결정은 엔진이 전달한다

- APPROVAL·SUPERVISOR 결정은 엔진이 실행마다 기록한다(`decisions`). Workflow는 `${arrival}`(이 step으로 오게 한 결정)과 `${decisions}`(지금까지의 결정 전체)를 읽어 Agent에 넘기기만 한다.
- Agent는 `instruction`(=`${arrival}`)을 이번 일의 지시로, `history`(=`${decisions}`)를 참고 기록으로 읽는다.
- 지시 가운데 이 Agent의 산출물로 반영할 수 없는 것(다른 단계의 일, 권한 밖의 일)은 반영하지 않고 답의 "반영하지 않은 지시"에 이유와 함께 적는다. 다음 승인에서 사람이 보고 다시 판단한다.

## 4. 고친 뒤에는 평가 세트로 확인한다

- `src/test/java/net/dstone/ai/eval/SmAgentEvalTest`: 실제 실행에서 뽑은 사례를 실제 LLM으로 돌린다(`-Ddstone.ai.test.llmcall=true -Ddstone.ai.eval=true`).
- 결과는 `target/eval/report.md`에 쌓인다. 저장 여부, 시간, LLM 회차, Tool 호출 수, 꼭 들어가야 할 내용을 본다.
- 프롬프트·모델·엔진을 바꾸면 세트를 다시 돌려 기준선과 비교한다. 새 실패 사례를 만나면 프롬프트를 고치기 전에 그 사례를 세트에 먼저 넣는다.
- 측정 결과는 `ai-engine-tuning.md`에 남긴다.
