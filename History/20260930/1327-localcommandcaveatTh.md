# localcommandcaveatTh

> 생성일시: 2026-09-30 13:27

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

---

## 질문

FileUtil.readFileListAll 이 null을 반환하는 것 이 문제가 되는 이유가 무었인가?

---

## 답변

좋은 질문입니다. 앞서 `readFileListAll`도 "같이 방어"하자고 한 건 검증 없이 넣은 말이라, 실제로 null을 반환할 수 있는지와 반환하면 어떻게 전달되는지를 코드로 확인하겠습니다.

---

## 답변

`null` 결과를 JSON으로 바꾸는 `JsonHelper.toJson(result, true)`의 두 번째 인자가 무엇을 하는지 확인합니다.

---

## 답변

`pkill -f`가 패턴을 포함한 제 셸 명령까지 종료시켰습니다. 단계를 나눠 다시 실행합니다.

---

## 답변

Makora는 16만 토큰도 처리했습니다. 공급자마다 컨텍스트 한도가 다를 수 있으니, 공급자별 한도를 조회하고 NextBit로 고정해 다시 보내봅니다.

---

## 답변

## 답: `readFileListAll`이 null을 반환해도 문제가 되지 않습니다. 앞 답변의 원인 분석이 틀렸습니다

질문하신 부분을 검증하다가 앞 답변의 결론 자체가 틀렸다는 걸 확인했습니다. 죄송합니다.

**1. `readFileListAll`은 null을 거의 반환하지 않습니다**
- 경로가 없으면 빈 배열 `[]`을 반환합니다(`dstone-common/.../FileUtil.java:547`에서 초기화된 값).
- null이 되는 경우는 두 가지뿐입니다: 권한 문제 등으로 `f.list()`가 실패할 때, 그리고 예외가 발생해 `catch`로 빠질 때입니다.

**2. Tool이 null을 반환해도 LLM에는 문자열 `"null"`로 전달됩니다**
- Spring AI 2.0.1의 `DefaultToolCallResultConverter`는 반환값을 Jackson으로 직렬화합니다. 그래서 null은 4글자 문자열 `"null"`이 됩니다.
- 가짜 OpenAI 서버로 엔진이 실제로 보내는 요청을 캡처해 확인했습니다. `readFile`(String)이든 `readFileListAll`(String[])이든 `{"role":"tool","content":"null"}`로 나갑니다.
- 앞에서 400이 났던 건 제가 curl로 **JSON `null`**을 직접 넣었기 때문입니다. 엔진은 JSON null을 보내지 않습니다. 로그의 `responseData=null`을 JSON null로 잘못 해석했습니다.

**3. `"null"` 문자열은 OpenRouter에서 정상 처리됩니다**
- 캡처한 요청(Tool 17개, 실제 system 프롬프트, tool 결과 `"null"` 2개)을 NextBit와 Makora에 그대로 보냈습니다. 둘 다 정상 응답이었습니다.

그래서 앞에서 제안한 "null 방어" 수정은 이 400을 고치지 못합니다. 해두면 모델이 실패 이유를 알 수 있어 좋기는 하지만, 원인에 대한 조치는 아닙니다.

## 그러면 실제 400의 원인은?

**아직 확정하지 못했습니다.** 지금까지 확인한 사실은 다음과 같습니다.
- 앞선 재현에서 실패한 요청은 3번째가 아니라 **7번째**였습니다. 이것도 제가 잘못 읽었습니다.
- 그 요청에는 모델이 `error.log`(232KB)를 두 번 읽은 결과가 들어 있어 약 16만 토큰이었습니다.
- 하지만 16만 토큰 요청을 공급자를 고정해 다시 보내봤더니 NextBit, Darkbloom(한도 131K), Makora 모두 정상이었습니다. 크기만으로는 400이 재현되지 않습니다.
- 엔진 로그에는 요청이 `toString` 형태로만 남아서, 실패한 요청의 원본 JSON과 공급자가 준 상세 에러 메시지는 알 수 없습니다.

**다음 단계 제안:** 엔진과 OpenRouter 사이에 기록용 프록시를 두고 워크플로를 다시 돌리겠습니다. 실패하는 요청의 원본 본문과 400 응답 전체(어느 공급자가 어떤 메시지로 거부했는지)를 캡처하면 원인을 확정할 수 있습니다. 진행할까요?

---

## 질문

응 진행해

---

## 답변

기록용 프록시를 만들어 엔진과 OpenRouter 사이에 끼우겠습니다. API 키는 엔진이 보내는 헤더를 프록시가 그대로 전달하므로 제가 따로 복호화하지 않습니다.

---

## 답변

프록시와 엔진이 준비됐습니다. 400이 나올 때까지 최대 5번 워크플로를 실행합니다.

---

## 답변

2번째 실행에서 400을 캡처했습니다(15번 요청, 약 509KB). 응답 전문과 요청 구성을 분석합니다.

---

## 답변

원인이 확정됐습니다. 엔진과 프록시를 정리합니다.

---

## 답변

## 결론: 컨텍스트 길이 초과입니다

프록시로 잡은 400 응답 원문(요약):

```
provider_name: NextBit
This model's maximum context length is 262144 tokens. However, you requested 111468 output tokens
and your prompt contains at least 150677 input tokens, for a total of at least 262145 tokens.
```

엔진은 이 상세 내용(`metadata.raw`)을 버리고 겉의 `"Provider returned error"`만 보여줍니다. 그래서 화면에 원인이 드러나지 않았습니다.

## 어떻게 이렇게 됐나

실패한 요청(15번째 호출)의 대화 이력입니다.

| # | 모델이 호출한 Tool | 결과 크기 |
|---|---|---|
| 2 | `readFile(00-request.md)` | 196자 |
| 4 | `directory_tree` (MCP) | 229자 |
| 6 | `readFileListAll` | 135자 |
| 8 | `readFile(error.log)` | **237,583자** |
| 10 | `readFile(error.log)` **또 읽음** | **237,583자** |

두 가지가 겹쳐서 실패했습니다.

1. **입력이 너무 큽니다.** 모델이 232KB짜리 `error.log`를 통째로 **두 번** 읽었고, 입력이 약 15만 토큰이 됐습니다.
2. **출력 토큰 상한이 없습니다.** 요청에 `max_tokens`가 없습니다(`temperature`만 있음). 그래서 NextBit가 "남은 컨텍스트 전부(약 11만 토큰)"를 출력 한도로 잡았습니다. 입력과 합치니 262,145가 되어 한도 262,144를 1 넘었습니다.

같은 입력으로 실행해도 성공할 때가 있습니다. 모델이 로그를 한 번만 읽거나(1회차 실행은 `DONE`), OpenRouter가 다른 공급자로 보낼 때입니다. 결과가 매번 달라지는 이유가 이것입니다. 앞에서 제가 16만 토큰 요청을 보냈을 때 통과한 것도 `max_tokens=20`을 넣었기 때문입니다.

사용자분 환경의 실패도 메시지가 같아서 같은 원인일 가능성이 높습니다. 다만 그쪽 요청을 직접 캡처한 건 아닙니다.

## 수정 제안 (효과가 큰 순서)

1. **`spring.ai.openai.chat.options.max-tokens` 지정** (예: 4096). `conf/application.yml`의 openai 설정에 한 줄만 추가하면 됩니다. anthropic 쪽에는 이미 `max-tokens: 4096`이 있는데 openai 쪽에는 없습니다. 이번 오류는 이것만으로 바로 해결됩니다.
2. **큰 파일 읽기 제한.** `readFile` Tool이 일정 크기를 넘으면 잘라서 "(이하 N자 생략)"을 붙이게 합니다. 로그용으로 끝부분만 읽는 Tool을 따로 두는 방법도 있습니다. 파일이 더 커지면 1번만으로는 입력 자체가 한도를 넘습니다.
3. **공급자 에러 상세를 노출.** 스텝 실패 메시지에 `metadata.raw`까지 담으면, 다음부터는 이런 추적 없이 원인이 바로 보입니다.

(참고로 앞에서 이야기한 Tool의 null 반환은 이번 오류와 무관합니다.)

어디까지 적용할까요? 1번만 바로 넣을 수도 있고, 1~3번을 모두 적용할 수도 있습니다.

---

## 질문

모두 적용해줘

---

## 답변

세 가지 모두 적용하겠습니다. 먼저 관련 코드와 설정 위치를 확인합니다.

---

## 답변

k8s ConfigMap에도 application.yml 사본이 있어 확인하고, openai-java SDK 예외 클래스의 구조도 봅니다.

---

## 답변

예외 메시지를 개선할 위치는 `WorkFlowExecutor`의 145행(스텝 실패)과 292행(forEach 이력)입니다. 프로젝트에서 JSON 파싱에 쓰는 관례를 확인합니다.

---

## 답변

이제 3가지 수정을 적용합니다. 먼저 **①** `conf/application.yml`의 openai 설정에 `max-tokens`를 추가합니다.

---

## 답변

**②** `readFile` Tool에 크기 제한을 넣습니다. 상한값을 설정으로 뺄 수 있도록 application.yml의 tool 섹션 구조를 확인합니다.

---

## 답변

Constants에 설정 키를 추가하고 `FileUtil` Tool을 수정합니다.

---

## 답변

**③** 공급자 에러 상세를 꺼내는 유틸을 `runtime.agent`에 추가합니다.

---

## 답변

메시지 속 작은따옴표 때문에 grep 결과가 중간에 잘렸습니다. 전체 메시지를 확인합니다.

---

## 답변

③도 실제 엔진 경로에서 검증됐습니다. 이제 CLAUDE.md 지침대로 문서에 반영할 내용이 있는지 확인합니다.

---

## 답변

문서 세 군데를 갱신합니다: Tool 표, 설정 레퍼런스, 실패 메시지 트러블슈팅.

---

## 답변

CLAUDE.md의 `runtime.agent` 설명에도 새 클래스를 한 줄 추가합니다.

---

## 답변

세 가지를 모두 적용하고, 실제 엔진을 띄워 각각 동작을 확인했습니다. 커밋은 하지 않았습니다.

## 바뀐 내용

**① 출력 토큰 상한** (`dstone-ai-engine/conf/application.yml`)
- openai(OpenRouter) 설정에 `max-tokens: 4096`을 추가했습니다. 이번 400을 직접 막는 수정입니다.
- 확인: 프록시로 캡처한 요청 66건 모두에 `max_tokens: 4096`이 들어갔습니다.

**② 큰 파일 읽기 제한** (`tools/utils/FileUtil.java`)
- `readFile`은 앞 30,000자까지만 돌려주고, 잘렸다는 안내를 붙입니다.
- 로그용으로 끝부분만 읽는 `readFileTail` Tool을 새로 추가했습니다.
- 상한은 `dstone.ai.tool.file.max-read-chars`(기본 30000)로 바꿀 수 있습니다. 설정 키는 `Constants`에 추가했습니다.
- 파일을 못 읽으면 `null` 대신 "파일을 읽을 수 없습니다: 경로"를 돌려줍니다. `readFileListAll`은 null 대신 빈 배열을 돌려줍니다.
- pilot Agent 프롬프트의 Tool 목록에 `readFileTail`을 추가했습니다.
- 확인: 가장 큰 Tool 결과가 약 31K자(이전 237K자)였고, `pilot-workflow` 3회 실행이 모두 `DONE`으로 끝났습니다.

**③ 공급자 에러 상세 노출** (새 파일 `runtime/agent/ProviderErrorMessage.java`, `WorkFlowExecutor.java`)
- 실패 메시지 뒤에 공급자 이름과 진짜 사유를 붙입니다. 사유는 `error.metadata.raw` 안에 겹겹이 싸인 JSON에서 꺼냅니다.
- 확인: 가짜 서버가 캡처해 둔 실제 400 본문을 돌려주게 하고 실행했더니, 저장된 메시지가 이렇게 나왔습니다.
  ```
  step[step01] 실행 중 예외가 발생했습니다 - 400: Provider returned error [공급자: NextBit]
  This model's maximum context length is 262144 tokens. However, you requested 111468 output tokens ...
  ```
- `raw`가 없는 에러는 원래 메시지를 그대로 둡니다. OpenRouter 자체가 거부한 경우로 확인했습니다.
- 현재 사용 중인 OpenAI 호환(OpenRouter) 호출에만 적용됩니다. Anthropic 에러는 대상이 아닙니다.

**문서**: `docs/09.dstone-ai-engine.md`(Tool 표, 설정 레퍼런스, 실패 메시지 표)와 `CLAUDE.md`에 반영했습니다.

## 따로 확인이 필요한 것

- **검증 중에 파일이 하나 생겼습니다.** 검증 실행에서 Agent가 `/app/testApp/workshop/changes/work001[log-error-fix]/01-requirements.md`를 만들었습니다(testApp에서 추적되지 않는 새 파일). 정상 산출물이긴 하지만, 제 테스트로 생긴 파일이라 지울지 말씀해 주세요.
- **모델이 같은 Tool을 반복 호출하는 문제가 있습니다.** 2회차 실행에서 Gemma가 `readFileListAll`을 40번 반복해 Spring AI의 Tool 호출 한도에 걸렸습니다. 그런데 상태는 `DONE`이고, 결과로 "Tool call limit (40) exceeded..."라는 문장이 나왔습니다. 이번 400과는 별개인 모델 품질 문제입니다. 다만 실패인데 성공으로 보이는 점은 따로 손볼 가치가 있습니다.
- **쓰기/삭제 Tool에 경로 제한이 없습니다.** `writeFile`과 `deleteFile`은 Shell/Http Tool과 달리 화이트리스트가 없어서, `toolsEnabled: true`인 Agent라면 어떤 경로든 쓰고 지울 수 있습니다. 문서의 Tool 표에 "항상(경로 제한 없음)"으로 적어 두었습니다.
