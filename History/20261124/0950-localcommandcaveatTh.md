# localcommandcaveatTh

> 생성일시: 2026-11-24 09:50

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/login</command-name>
            <command-message>login</command-message>
            <command-args></command-args>

---

## 질문

<local-command-stdout>Login successful</local-command-stdout>

---

## 질문

dstone-ai-engine 의 설정내용들에 대해서 온라인 AI에게 리뷰를 요청했어. 아래의 디렉토리에 모아놓았고.
이 내용들을 참고해서 우리가 수정할 내용이 있을까?
/temp/review

---

## 답변

세 리뷰(클로드·구글·GPT)를 다 읽었습니다. 지금은 리뷰가 짚은 내용이 실제 정의 파일과 엔진 코드에서도 맞는지 하나씩 확인하고 있습니다.

---

## 답변

엔진이 `${state.x}`에 값이 없을 때 어떻게 처리하는지 확인하고 있습니다.

---

## 답변

세 리뷰(클로드·구글·GPT)를 실제 `sm-workflow.yml`, Agent 정의, 엔진 코드와 맞춰 봤습니다. 아직 아무것도 고치지 않았습니다. 리뷰 지적은 사실과 맞는 것, 틀린 것, 우리 규칙상 받지 않을 것으로 나뉩니다.

## 1. 바로 고칠 만한 것 (코드로 확인함)

**① 리뷰 Agent가 검토 대상 문서를 직접 고칩니다** (클로드 ④, 맞음)
- `sm-requirement-analyze-reviewer-agent`는 `01.customer_requirement.md`의 '상태' 칸에서 '대기'를 찾아 '통과'나 '점검요청'으로 바꿉니다.
- [다시 리뷰]나 [요구사항 수정]으로 리뷰를 한 번 더 하면 그 칸에 '대기'가 없습니다. 그래서 `editFileTexts`의 before가 맞지 않습니다.
- 끝에 붙인 '점검 사유' 절은 매번 쌓이고, 그대로 영향도·설계 Agent의 `requirement` 입력으로 들어갑니다.
- 그 '상태' 칸은 다른 곳에서 읽지 않습니다. 판정 사유는 이미 엔진이 `${arrival}`과 `state.review.reason`으로 넘기고 있습니다.
- **수정안:** 리뷰 Agent의 `editFileTexts`와 기록 절차를 빼서 판정만 돌려주게 합니다. 요구사항 Agent의 표에서도 `상태(대기)` 칸을 뺍니다.

**② 동작 검증이 고치기 전 설계서를 받습니다** (GPT P0-5, 맞음)
- 자동 수정 경로에서는 step32가 설계서를 읽고, step34가 설계서를 고칩니다. 그 뒤 step18 → 28 → 40 → 29 → step35로 가는데, step35는 step32에서 읽은 옛 `${state.design}`을 받습니다.
- 전문가 해결 경로(step22에서 읽고 step24에서 고침)도 같습니다.
- **수정안:** step28과 step40 사이에 설계서를 다시 읽는 step41을 넣습니다. step29와 step35 사이는 서버를 넘겨받아야 하므로 건드리지 않습니다.

**③ state 주석이 틀렸습니다** (클로드 ⑦, 맞음)
- `revision`의 주석이 "step25~25, 27, 29, 30"으로 되어 있습니다. 실제로 저장하는 step은 25, 26, 27, 37, 38, 39입니다.

## 2. 고려해 볼 만한 것 (판단이 필요함)

- **처음 개발할 때 고친 컴파일 오류가 설계서에 남지 않습니다** (클로드 ③). 맞습니다. 개발 Agent에 "재개발이 아니면 설계서를 고치지 않는다"(84줄)는 규칙이 있습니다.
  - 다만 설계 수정 뒤 다시 개발하면 Agent가 컴파일을 최대 3번 돌려 다시 고치므로, 피해는 "가끔 다시 깨짐" 정도입니다.
  - 설계서에도 옮겨 적게 하면 동기화할 일이 늘어납니다. 구글 리뷰는 오히려 그 동기화를 줄이자고 합니다.
- **'보류'가 남아도 컴파일만 통과하면 자동 검증으로 갑니다** (구글 ③). 맞습니다. 이때 6~7분짜리 검증이 돌고, 자동 수정이 설계와 다른 방향으로 고칠 수 있습니다.
  - 개발 step에 `save`를 붙이고, 범용 Tool로 `pending > 0`일 때 개발 승인(step19)으로 바로 보내는 분기를 넣을 수 있습니다.
- **design-lint** (클로드 D, GPT 3.7). 설계서의 '변경 전'이 원본 파일에서 정확히 한 곳에만 맞는지 기계로 검사하는 범용 Tool입니다. 설계 승인 앞에 둡니다.
  - 개발 단계의 "before 불일치" 실패를 그 앞 단계에서 막습니다. 우리 규칙(새 범용 Tool)에도 맞습니다.
  - 큰 구조 변경 중에서는 들이는 비용에 비해 효과가 가장 좋다고 봅니다.

## 3. 틀렸거나 받지 않을 것

- **step29 실패 원인을 step30이 못 읽는다** (GPT): 틀렸습니다. `ToolStepExecutor`는 실패해도 Tool이 돌려준 값을 결과로 저장합니다. 그래서 `sandboxCheck.cause`가 채워집니다(`TestServerTool.java:232`).
- **step16 `saveError: compileError`를 분리하라** (세 리뷰 모두): 일부러 이렇게 둔 것입니다. 성공하면 null을 저장해 지난 컴파일 오류를 비우는 용도입니다. 이름을 바꾸면 컴파일 오류가 비워지지 않습니다.
- **step34의 `${state.verification}`에 기본값이 없다**: 값이 없으면 null이고, Agent 입력에서도 선택 항목(`verification?`)이라 문제가 없습니다.
- **step id와 name 번호가 서로 안 맞는다**: 우리 규칙상 의도된 것입니다. id는 한 번 정하면 바꾸지 않고, 흐름 순서는 name 앞의 NN-으로 나타냅니다.
- **Ollama `num_ctx` 잘림** (클로드 ⑧): 해당하지 않습니다. 지금 모델은 OpenRouter를 거친 `qwen/qwen3.8-27b`이고 Ollama가 아닙니다.
- **TASK 단위 개발, JSON 계약, Artifact 계층, Workflow 분리** (GPT): 방향은 옳지만 엔진을 크게 바꾸는 일입니다. 먼저 평가 세트(`conf/eval`)로 step별 편차부터 재 보고 정하는 편이 맞다고 봅니다. 이 측정은 클로드 리뷰 A안과 같은 이야기입니다.

1의 세 가지(리뷰 Agent 정리, 검증 전 설계서 다시 읽기, 주석)는 바로 고치고, 2는 하나씩 정하면 좋겠습니다. 1부터 진행할까요?
