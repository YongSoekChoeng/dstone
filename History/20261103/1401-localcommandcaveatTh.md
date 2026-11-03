# localcommandcaveatTh

> 생성일시: 2026-11-03 14:01

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

sm-workflow 설정에서
step05(영향도 분석) 혹은 step08(아키텍처 설계) 에
deepseek/deepseek-v4.1-flash 모델을 적용해 볼까 하는데 의견을 말해줘.

---

## 답변

결론부터 말하면 **step05(영향도 분석)에 먼저 적용해 보는 것**을 추천합니다. step08은 step05 결과를 본 다음에 결정하는 편이 좋겠습니다.

## OpenRouter에서 확인한 내용
- `deepseek/deepseek-v4.1-flash`라는 id가 실제로 있고, `tools`, `tool_choice`, `reasoning_effort`를 지원합니다. 우리 엔진이 쓰는 기능은 모두 들어 있습니다.
- 단가(100만 토큰당)는 입력 $0.045, 출력 $0.50입니다. 지금 쓰는 `qwen/qwen3.8-27b`가 입력 $0.15, 출력 $1.875이니 입력은 3분의 1, 출력은 4분의 1 정도입니다.
- 두 모델 모두 컨텍스트 창은 약 100만 토큰이라 이 점에서는 차이가 없습니다.

## step05가 먼저인 이유
- **Tool을 가장 많이 부르는 단계입니다.** 영향도 분석 Agent는 knowledge Tool 6개와 파일 Tool을 쓰고 `maxToolCalls: 30`이 걸려 있습니다. 지난번 실측에서 Tool 루프가 38라운드, 입력 토큰 83k까지 간 적이 있습니다. LLM 라운드 수가 많아서 한 라운드가 빨라지고 싸지는 효과를 가장 크게 봅니다.
- **실수가 나와도 사람이 거릅니다.** 결과물은 영향도분석서(02.impact.md) 하나이고, 바로 뒤 step06 APPROVAL에서 사람이 보고 "영향도 재분석"으로 되돌릴 수 있습니다.
- `model.reasoning`이 설정되어 있지 않아 모델을 바꿔도 다른 옵션과 얽히지 않습니다.

## step08을 미루는 이유
- `reasoning: medium`이 걸린, 판단이 필요한 단계입니다. flash급 모델은 이런 데서 차이가 가장 크게 납니다.
- 이 단계에서 나온 설계서(03-1)와 태스크 목록(03-2)을 step12 개발 Agent가 그대로 따라 소스를 고칩니다. step09에서 승인을 받긴 하지만, 설계가 조금만 어긋나도 소스 변경으로 바로 이어집니다.
- 긴 문서를 `writeFile`/`appendFile`로 나눠 저장하는 단계라서, 인자가 중간에 끊기거나 같은 내용을 다시 쓰는 현상이 모델 따라 달라집니다.

## 적용 방법과 확인할 것
- `sm-analyze-impact-agent.md`의 `model` 블록에 `name: deepseek/deepseek-v4.1-flash` 한 줄만 넣으면 됩니다. OpenRouter를 그대로 쓰므로 `provider`는 비워 둡니다. 이 Agent는 sm-workflow에서만 쓰여서 다른 Workflow에는 영향이 없습니다.
- 모델 이름이 틀려도 기동이나 [검사]에서는 걸리지 않고 첫 호출 때 실패합니다. 위 id는 확인했으니 그대로 쓰면 됩니다.
- 같은 요청으로 qwen과 한 번씩 돌려서 `execution.log`의 `dstone-ai-engine agent:` 합계 줄을 비교해 보세요. 라운드 수, Tool별 호출 수, 토큰, 시간이 찍힙니다. 특히 볼 것은 세 가지입니다.
  - 같은 Tool을 같은 인자로 다시 부르는지
  - 30회 한도에 부딪히는지
  - knowledge 결과의 HIGH/MEDIUM/LOW 구분을 프롬프트 규칙대로 옮겨 적는지
- 마지막으로 02.impact.md의 한국어 문장 품질은 사람이 직접 읽고 판단해야 합니다.

DeepSeek v4.1 flash가 이 Workflow에서 실제로 어떤 품질을 내는지는 저도 근거가 없습니다. 위 판단은 단계별 특성과 가격만 보고 내린 것이니, 한 번 돌려 본 결과로 확정하세요.

참고로 작업 트리에 아직 커밋하지 않은 변경이 있습니다. step09에 `중지: FAIL` 경로를 추가한 것과 layout.json 수정입니다. 이번 질문과는 상관없습니다.
