# localcommandcaveatTh

> 생성일시: 2026-09-30 16:10

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone/dstone-ai-engine/src/main/resources/workflows/pilot/pilot-workflow.yml 에서 
step03 진행중 에러가 나는 이유가 무엇인가?

---

## 답변

워크플로 파일부터 확인하겠습니다.

---

## 답변

YAML 자체에는 눈에 띄는 차이가 없으니, 실제 로그에서 step03 에러를 찾아보겠습니다.

---

## 답변

로컬 로그에는 기록이 없습니다. 다른 로그 위치(k8s Pod, conf 설정)와 실행 이력 저장소를 확인하겠습니다.

---

## 답변

이미 max-tokens/readFile 상한은 반영돼 있습니다. step03 실제 에러 메시지를 실행 이력 DB에서 확인해 보겠습니다.

---

## 답변

DB 접근은 비밀번호가 필요하니, 대신 작업 디렉토리 산출물 상태를 확인하겠습니다.

---

## 답변

14:44에 step01/02 산출물이 생성됐으니 그 실행 로그가 다른 곳에 있을 것입니다. 찾아보겠습니다.

---

## 답변

로그 원문은 찾지 못했습니다(14:44 실행은 이 WSL의 `execution.log`에 기록되지 않았습니다). 대신 step03만 다른 점을 코드 기준으로 확인하겠습니다.

---

## 답변

에러 원문을 확보하지 못해서, 아래 원인은 **추정**입니다. 설정과 산출물로 보면 가능성이 가장 높은 원인은 하나입니다.

## 확인된 사실
- step03은 `04-15`와 같은 다른 경로가 아니라, 워크플로 YAML과 Agent YAML 모두 step01/02와 구조가 같습니다. 부팅 검증에 걸릴 부분이 없고, 프롬프트에도 `{workDir}` 말고는 `{}` 자리표시자가 없습니다.
- `work001[log-error-fix]/` 폴더에는 14:44에 `01-requirements.md`(2.2KB)와 `02-impact.md`(3KB)가 생성됐습니다. step01과 step02는 성공했고, `03-design.md`와 `03-tasks.md`는 만들어지지 않았습니다.
- 이 WSL의 `/app/dstone/LOGS/dstone-ai-engine/execution/execution.log`는 13:26에 마지막으로 기록됐습니다. 14:44 실행 로그는 찾지 못했습니다. IDE나 Windows 쪽에서 실행하신 것으로 보입니다.

## 추정 원인: 출력 토큰 상한(4096)에 걸려 응답이 잘림
`conf/application.yml`의 OpenAI(OpenRouter) 설정은 `max-tokens: 4096`입니다. 13:26의 컨텍스트 초과 에러 때문에 넣으신 값입니다.

그런데 이 Agent 구조에서는 **파일 내용 전체가 LLM의 출력 토큰**입니다. `writeFile(filePath, fileName, fileContents)`를 부르는 tool call의 인자 JSON도 출력 토큰으로 계산됩니다.

| step | 만들어야 하는 산출물 | 실제 크기 |
|---|---|---|
| step01 | 요구사항 1개 | 2.2KB → 4096 안에 들어감 |
| step02 | 영향도 1개 | 3KB → 4096 안에 들어감 |
| step03 | **`03-design.md`(클래스·API 스펙·DDL·시퀀스·예외 방침) + `03-tasks.md`** | 훨씬 큼 → 4096 초과 가능성 높음 |

한글은 글자당 토큰 소모가 커서 설계서 하나만으로도 4096을 넘기 쉽습니다. 넘으면 응답이 `finish_reason=length`로 중간에 잘립니다. 그러면 둘 중 하나로 실패합니다.
1. `writeFile` 인자 JSON이 중간에 끊겨 Spring AI가 인자를 파싱하지 못하고 예외가 납니다. 이때 step 전체가 예외로 끝나고 run이 FAILED가 됩니다.
2. 최종 응답이 비어 `AgentExecutor`가 `"LLM 응답이 비어 있습니다."`(`runtime/agent/AgentExecutor.java:227`)를 던집니다. 이때는 `onFailure: FAIL`로 끝납니다.

## 해결 방향 (택1 또는 병행)
- **`max-tokens`를 올립니다.** 16384 정도면 입력+출력이 262k 한도를 넘지 않는 선에서 여유가 있습니다.
- **step03을 둘로 나눕니다.** 예를 들어 `step03`은 `03-design.md`만, `step04`는 `03-tasks.md`만 쓰게 하면 한 번에 쓰는 양이 줄어듭니다.
- 프롬프트에 "각 산출물은 간결하게, 파일당 N자 이내로" 같은 분량 제한을 추가합니다.

## 정확히 확인하려면
실패 메시지 하나면 원인을 확정할 수 있습니다. 다음 중 하나를 알려주시면 됩니다.
- `/execute` 응답의 에러 메시지
- `AI_WORKFLOW_EXECUTION.ERROR_MESSAGE` 값
- 실제 실행한 쪽의 `execution.log`에서 `step[step03]` 부분

DB 조회를 시도했지만 비밀번호가 `ENC(...)`로 되어 있어서 제가 직접 조회하지는 않았습니다.
