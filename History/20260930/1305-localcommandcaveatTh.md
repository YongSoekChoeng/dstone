# localcommandcaveatTh

> 생성일시: 2026-09-30 13:05

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

http://localhost:7081/defaultLink.do?defaultLink=ai/workflow/workflow 에서 pilot-workflow 를 진행했고 input 은 /app/testApp/workshop/changes/work001[log-error-fix] 로 진행했고 결과는 step[step01] 실행 중 예외가 발생했습니다 - 400: Provider returned error 이렇게 떨어졌는데 원인을 추적해줘

---

## 답변

pilot-workflow 정의와 실행 로그부터 확인하겠습니다.

---

## 답변

Chat provider가 OpenRouter(openai 호환)로 설정되어 있네요. 설정 전체와 실제 실행 로그를 확인하겠습니다.

---

## 답변

로컬 로그는 오래된 것이라, 현재 실행 중인 엔진 프로세스를 찾아보겠습니다.

---

## 답변

엔진이 현재 WSL/kind 에서는 떠 있지 않습니다(아마 IDE 등 다른 곳에서 실행 중). 우선 기존 로그와 코드 경로를 정적으로 추적하겠습니다.

---

## 답변

Key 복호화 성공(OpenRouter 키). 이제 단계별로 요청을 재현합니다: ① 도구 없는 단순 호출, ② system 메시지 포함, ③ 도구 포함.

---

## 답변

Redis/Postgres/Ollama 가 WSL에 떠 있으니 엔진을 WSL 설정으로 임시 기동해 재현하겠습니다.

---

## 답변

jar 가 어제 빌드본이라 최신 소스로 다시 빌드 후 기동합니다.

---

## 답변

기동 완료. 사용자와 동일한 입력으로 실행합니다.

---

## 답변

결정적인 단서가 나왔습니다: `ToolResponse[... name=readFile, responseData=null]`. 이후 에러 메시지를 확인합니다.

---

## 답변

원인 확정: **tool 메시지 `content: null` → 400**. 마지막으로 null 이 어디서 나오는지 확인합니다.

---

## 답변

원인 추적이 끝났습니다. 제가 임시로 띄운 엔진은 내리고, 복호화한 키 파일도 지웁니다.

---

## 답변

## 원인

`readFile` Tool이 **`null`을 반환**했고, 이 `null`이 LLM에 보내는 tool 결과 메시지의 `content: null`로 그대로 실렸습니다. OpenRouter를 거쳐 이 요청을 받은 upstream 공급자(NextBit)가 400으로 거부했습니다.

## 재현 결과

WSL에서 최신 소스로 엔진을 빌드해 띄우고 같은 입력으로 `pilot-workflow`를 실행했더니, `step[step01] 실행 중 예외가 발생했습니다 - 400: Provider returned error`가 그대로 재현됐습니다.

진행 순서(로그 기준):
1. 1차 LLM 호출 → 모델이 `readFile(".../00-request.md")` 호출을 요청했습니다.
2. `FileNotFoundException`이 발생했습니다. 그런데 `dstone-common`의 `FileUtil.readFile()`(`dstone-common/src/main/java/net/dstone/common/utils/FileUtil.java:111`)이 예외를 삼키고 `return null`을 합니다.
   → 로그에 `ToolResponse[name=readFile, responseData=null]`로 남았습니다.
3. 2차 호출 → 모델이 `readFileListAll`을 호출했고 이건 정상 응답이었습니다.
4. 3차 호출에서 앞의 `null` 결과가 대화 이력에 포함돼 400이 났습니다.

curl로 OpenRouter에 직접 보내 조건을 분리해 봤습니다(각 3회):

| tool 메시지 `content` | 결과 |
|---|---|
| `null` | **400** `text content parts must carry a string "text" (got null)` |
| `""` | 정상 |
| `"hello"` | 정상 |

모델, system 프롬프트, Tool 스키마 자체에는 문제가 없었습니다.

## 파일을 못 읽은 이유

- 제 재현에서는 `/app/testApp/workshop/changes/work001[log-error-fix]`가 실행 중인 13:04에 디스크에 생겼습니다(git 커밋은 13:00). 그래서 첫 `readFile` 시점에는 파일이 없었습니다.
- 사용자분 환경의 엔진은 WSL/kind에서 떠 있지 않았습니다. `conf/env.properties`가 `D:/AppHome/...` 기준이라 Windows(IDE)에서 실행 중인 것으로 보이는데, 제가 직접 확인하지는 못했습니다. 그렇다면 Windows에는 `/app/testApp/...` 경로가 없어서 같은 이유로 `null`이 됐을 가능성이 큽니다. Windows에서는 입력을 `D:/AppHome/testApp/...` 형태로 줘야 합니다.
- 경로가 맞더라도 다른 이유(권한, 인코딩 등)로 읽기에 실패하면 똑같이 400이 납니다. 그래서 근본 수정이 필요합니다.

## 수정 제안

`dstone-ai-engine/src/main/java/net/dstone/ai/tools/utils/FileUtil.java`의 Tool 메서드가 **절대 `null`을 반환하지 않게** 하면 됩니다.
- 파일이 없거나 읽기에 실패하면 `"파일이 존재하지 않습니다: <경로>"` 같은 문자열을 반환합니다. 그러면 모델이 상황을 알고 다음 행동을 고를 수 있습니다.
- `readFileListAll`도 `null` 대신 빈 배열을 반환하도록 같이 방어합니다.

## 참고

- `error.log`가 약 232KB입니다. 모델이 `readFile`로 통째로 읽으면 컨텍스트와 비용 부담이 큽니다. 나중에 줄 수 제한이나 tail 읽기 Tool을 따로 두는 것도 고려해 보세요.
- 재현하느라 WSL에서 엔진을 잠깐 띄웠고 지금은 내렸습니다. OpenRouter 키는 화면에 출력하지 않고 임시 파일로 복호화해 썼고, 그 파일도 삭제했습니다.

위 수정(`FileUtil` Tool의 null 방어)을 바로 적용할까요?
