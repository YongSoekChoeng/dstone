# localcommandcaveatTh

> 생성일시: 2026-10-02 14:49

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

로컬에서 dstone-ai-engine 의 pilot-workflow > step03 > pilot-impact-analyzer-review-agent 진행중 자꾸 LLM 리턴값이 비어서 리턴되. 원인을 추적해줘.

---

## 답변

pilot-workflow와 해당 Agent 정의, 그리고 로컬 로그부터 확인하겠습니다.

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

로컬 D드라이브에 깔려있는 dstone-ai-engine 의 pilot-workflow > step03 > pilot-impact-analyzer-review-agent 진행중 자꾸 LLM 리턴값이 비어서 리턴되. 원인을 추적해줘.

---

## 답변

D 드라이브(Windows 쪽)에서 실행 중인 인스턴스였군요. 그쪽은 읽기만 하고, 로그와 설정부터 확인하겠습니다.

---

## 답변

There is an `llm` log directory. Let me look there.

---

## 답변

원인이 보입니다. 다른 실패 건도 같은 패턴인지, 그리고 `readFile`이 줄 번호를 주는지 확인합니다.

---

## 답변

원인을 찾았습니다. LLM이 빈 답을 "보낸" 것이 아니라, **답을 쓰기 전에 출력 토큰 한도(`max-tokens: 4096`)를 추론(reasoning)에 전부 써서 본문이 잘린 것**입니다.

## 근거

D 드라이브 인스턴스의 LLM 호출 로그(`LOGS/dstone-ai-engine/llm/20261002/pilot-workflow/<실행ID>/`)에서 step03의 마지막 응답을 보면, 오늘 로그가 남은 두 실행이 모두 같은 모양입니다.

| 실행 | 마지막 응답 | finishReason | completionTokens | reasoning_tokens | text |
|---|---|---|---|---|---|
| `23095744…` | 14:37:59 | `LENGTH` | 4096 | 4096 | `""` |
| `40d76f0f…` | 14:25:29 | `LENGTH` | 4096 | 4096 | `""` |

- 모델은 `deepseek/deepseek-v4.1-flash`(OpenRouter)이고, 추론 토큰도 `max-tokens`에 포함됩니다.
- 4096 토큰이 전부 추론에 쓰여 `text`가 빈 글자, `toolCalls`도 비어 있습니다.
- 그래서 `AgentExecutor.convert()`가 빈 글자를 JSON으로 읽지 못해 `AgentContractException`을 던지고, step03이 실패 처리됩니다.

12:04, 12:29 실패 건은 LLM 로그 폴더가 13:46부터 생겨서 직접 확인하지 못했습니다. 실패 메시지는 같습니다.

## 왜 추론이 그렇게 길어지나

`reasoningContent`를 보면 모델이 `schema.sql`과 `logs-mapper.xml`의 줄을 1번부터 하나씩 세고 있습니다("223: CREATE TABLE … Yes! 223 matches").

- 리뷰 Agent 프롬프트가 "어느 파일 몇 번째 줄에 무엇이 있다"를 직접 열어 확인하라고 시킵니다(`03.impact-analyzer-review-agent.yml:25`, `:48`).
- 그런데 `readFile`은 줄 번호 없이 본문만 돌려주는 것으로 보입니다. 모델이 줄 번호를 검증하려면 머릿속으로 세는 수밖에 없습니다.
- 직전 응답(14:37:48)도 이미 3867 토큰 중 3712가 추론이었고, 그다음 호출에서 한도를 넘었습니다.

## 로그 메시지가 오해를 부른 부분

`answer[null]`은 실제 LLM 응답이 아닙니다. `SupervisorStepExecutor.java:58`의 catch 블록에서 `answer`는 예외 때문에 대입되기 전이라 항상 null이고, 진짜 사유(`e.getMessage()`)는 `printStackTrace()`로 콘솔에만 나갑니다. `RouterStepExecutor.java:56`도 같은 구조입니다.

## 고칠 수 있는 방향

1. **`readFile` 결과에 줄 번호를 붙이기** (또는 줄 범위를 받는 Tool 추가): 근본 원인을 없애는 방법이고, 줄 번호를 요구하는 step01/02 Agent에도 도움이 됩니다. 다만 모든 Agent가 받는 파일 내용 형식이 바뀝니다.
2. **`max-tokens` 올리기 또는 추론 토큰 제한**: 설정만 바꾸면 되지만, 줄 세기가 더 길어지면 다시 터질 수 있습니다.
3. **실패 메시지에 `e.getMessage()`를 넣고, `finishReason=LENGTH`면 "출력 한도 초과"로 알리기**: 원인을 고치지는 않지만 다음부터 바로 알아볼 수 있습니다.

1번과 3번을 함께 하는 것을 권합니다. 진행할까요? 수정은 `/app/dstone`에서 하고 D 드라이브에는 git으로 반영하시면 됩니다.
