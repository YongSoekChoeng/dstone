# testworkflow_가_실패하는_

> 생성일시: 2026-10-31 05:13

## 질문

test-workflow 가 실패하는 이유가 무엇인가? step02 로부터 전달된 input 값이 agent 에게 제대로 전달된건가?

---

## 답변

실패 시각이 step02 시작 후 정확히 20초 뒤입니다. 실제로 LLM에 보낸 프롬프트와 모델 설정을 로그에서 확인하겠습니다.

---

## 답변

**실패 원인은 step02의 LLM 호출이 `timeoutSeconds: 20`을 넘긴 것이고, input은 Agent까지 제대로 전달됐습니다.** 다만 20초를 넘긴 이유가 모델의 추론(reasoning)이라는 점은 추정이며, 수정 후 다시 돌려 보지는 않았습니다. 확인한 실행은 `ba73ec2b-966e-4f81-a7da-76e830e53f81`(05:06:13 시작)입니다.

## input 전달
| 구간 | 로그에서 확인한 값 |
|---|---|
| step01 결과 | `{outArr=[우리나라수도는 서울이다, 우리나라 수도는 부산이다], os=Linux}` |
| step02 `with` 평가 결과 (`${state.step01_output.outArr[0]}`) | `우리나라수도는 서울이다` |
| `AgentExecutor.callForSchema()`가 받은 input | `우리나라수도는 서울이다` |

Agent의 `input: string`과도 맞아서, 입력 검사도 통과하고 LLM 호출까지 진행됐습니다. 코드상 이 값은 user 메시지에 들어가고, 프롬프트의 `${input}` 자리에도 채워집니다. 다만 LLM에 실제로 보낸 전문은 로그에 남지 않아서, 이 부분은 코드로만 확인했습니다.

## 실패 과정
- **타임아웃:** step02는 05:06:17.96에 시작해 05:06:37.98에 실패했습니다. 정확히 20초 뒤이고, `test-step02-agent.md`의 설정값과 같습니다.
- **오류 메시지:** `Error reading response (원인: InterruptedIOException: timeout <- StreamResetException: stream was reset: CANCEL)`
- **20초를 넘긴 이유(추정):** `test-supervisor-agent`에는 `model.reasoning`이 없어 모델 기본 추론이 켜져 있습니다. 같은 모델 `qwen/qwen3.8-27b`로 짧은 질문을 잰 2026-10-07 기록에서도 추론을 켜면 41.7초, `none`이면 0.5초였습니다. step01은 `reasoning: none`이라 Tool 호출까지 포함해 4~5초 만에 끝났습니다.

## 재시도와 `onFailure`가 동작하지 않은 이유
- **`maxAttempts: 3`, `onInvalidOutput: RETRY`:** AGENT step과 채팅 호출(`AgentExecutor.call()`)에만 적용되고, SUPERVISOR step은 쓰지 않습니다.
- **`onFailure: FAIL`:** 타임아웃은 계약 위반(`AgentContractException`)이 아니라 시스템 오류여서 `onFailure`로 가지 않습니다. 그래서 판정 불통과가 아니라 실행 예외로 Workflow 전체가 `FAILED`가 됐습니다.

## 수정 방법
`test-step02-agent.md`의 `model`과 `execution`을 아래처럼 바꾸면 됩니다. 파일은 아직 고치지 않았습니다.

```yaml
model:
  temperature: 0
  reasoning: none
execution:
  timeoutSeconds: 60
  maxToolCalls: 3
```

`maxAttempts`와 `onInvalidOutput`은 SUPERVISOR에 효과가 없어 뺐습니다.

## 타임아웃을 고쳐도 남는 문제
- **판정 범위:** step02는 `outArr[0]`만 판정하므로 "우리나라 수도는 부산이다"는 검사하지 않습니다. 둘 다 판정하려면 step에 `forEach`를 쓰거나 `with`에 배열 전체를 넘기고 Agent의 `input` 타입도 맞춰야 합니다.
- **남은 템플릿 문구:** 본문의 `(무엇 - 예: 앞 단계가 만든 답변)`과 `description`의 `(무엇을)`이 그대로 남아 있습니다. 본문 문구는 LLM에게 그대로 전달됩니다.
