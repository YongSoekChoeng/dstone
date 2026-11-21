# pasted_content_ida70

> 생성일시: 2026-11-21 15:16

## 질문

<pasted_content id="a700">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
sm-developer-agent 가 개발에이전트야.
그런데 작업결과를 보면 겉보이에는 컴파일에러도 안나고 그럴듯 해보이지만 실제로 돌려보면 제대로 기능을 하지 않는 경우가 많아.
이런 경우 개발승인 APPROVAL에서 승인을 내리지 않고 개발에이전트로 재개발 요청을 진행하고 있는데 반복적으로 재개발해도 완전한 기능을 하지 않는 경우가 많아.
(Windows 에서 진행한 실행ID : 9dc51bb8-4649-4537-a5b8-c2d98f649f39)
이런 경우 탐색범위가 제한되서 그런건지 혹은 다른 이유인지 궁금해. 왜 개발에이전트의 성능이 기대이하이지?
</pasted_content id="a700">

---

## 답변

실행 기록과 Agent 정의부터 확인하겠습니다.

---

## 답변

이제 실행 기록을 찾아보겠습니다. Windows 쪽 실행도 같은 PostgreSQL을 쓰는지 확인합니다.

---

## 답변

Windows 실행도 같은 DB에 남아 있습니다. step 이력을 보겠습니다.

---

## 답변

개발 step이 실제로 어떤 파일을 읽고 썼는지 파일 기록을 보겠습니다.

---

## 답변

컴파일은 Java 4개만 확인했고 JSP·매퍼는 검사 대상이 아닙니다. 실제 결과물과 원본 관례를 비교해 보겠습니다(Windows 쪽 파일은 읽기만 합니다).

---

## 답변

서버 쪽은 원본 관례와 거의 같습니다. 이제 화면(JSP)의 업로드 부분과 원본 `memberRegi.jsp`의 방식을 비교합니다.

---

## 답변

Windows 쪽 로그를 찾았습니다. 재개발 때 개발 Agent가 실제로 어떤 Tool을 불렀는지 확인 중입니다.

---

## 답변

원인 대부분을 확인했습니다. 마지막으로 이 프로젝트의 Kendo 버전에서 `select` 이벤트의 파일 객체 구조를 확인합니다.

---

## 답변

탐색 범위가 좁아서 생긴 문제는 아닙니다. 개발 Agent는 원본의 업로드 화면(`memberRegi.jsp`)과 `FileService`, `MemberController`까지 읽었습니다. 기능이 안 되는 원인은 아래 다섯 가지이고, 1~3번이 가장 큽니다.

## 이번 실행에서 실제로 틀린 곳

**1. 파일이 업로드되지 않음 (세 번 반려되고 설계까지 고쳤는데도 그대로)**

`imageBoardView.jsp`의 `select` 이벤트에서 `e.files[0]`을 그대로 보내고 있습니다.
```js
selectedFile = e.files[0];          // Kendo가 감싼 객체 {name, extension, size, rawFile}
fd.append("file", selectedFile);    // 파일이 아닌 "[object Object]" 문자열이 전송됨
```
이 프로젝트의 Kendo(v2016.1.112) 소스에서 직접 확인했습니다. 실제 파일은 `e.files[0].rawFile`에 있고, Kendo도 내부에서 `t[a].rawFile`로 append합니다. 그래서 서버의 `request.getFileNames()`가 비어 `imageSave`가 호출되지 않습니다. 반려 의견에 적힌 증상과 정확히 같습니다.

설계 수정(step18)도 원인은 맞게 짚었습니다("Kendo 객체를 보내면 바이트가 안 간다"). 그런데 고친 코드는 똑같은 객체를 다른 경로로 받아 보내는 것이어서 버그가 그대로 남았습니다.

**2. 등록자 표시가 다시 깨졌을 가능성이 큼**

첫 반려 1번이 "등록 팝업에 로그인 사용자가 안 보인다"였습니다. 현재 결과물에서 이 값을 넣는 곳을 찾지 못했습니다.
- 등록 모드에서는 `ImageBoardVO`가 없어서 `writer_nm`이 빈칸입니다.
- `imageBoardView.go`도 로그인 사용자 이름을 모델에 넣지 않습니다.

## 왜 반복해도 고쳐지지 않는가

| # | 원인 | 분류 |
|---|---|---|
| 1 | **코드를 실제로 쓰는 건 설계 Agent입니다.** `03-1.design.md`(약 1000줄)에 신규 파일 8개의 전체 코드가 들어 있고, 개발 Agent는 그걸 옮겨 적습니다. 프롬프트에 "설계서와 다르게 만들 이유가 생기면 보류"라는 규칙이 있어서, 설계가 틀리면 개발은 틀린 그대로 만듭니다. | Workflow 구조 |
| 2 | **검증이 Java 컴파일뿐입니다.** step16 결과가 매번 "바뀐 파일 4개 컴파일 통과"였습니다. JSP·JS·매퍼 XML은 검사하지 않고 실행 확인도 없습니다. "겉보기엔 멀쩡한데 안 돈다"는 바로 이 빈틈입니다. 이번 버그도 JS 런타임 문제라 컴파일로는 절대 잡히지 않습니다. | Workflow/Tool |
| 3 | **재개발로 고친 내용이 설계 수정을 거치면 사라집니다.** 재개발은 `dev-resources`를 직접 고치고 설계서는 고치지 않습니다. 그 뒤 "설계 수정"으로 가면 step14가 `dev-resources`를 지우고, 그 수정이 없는 설계서로 다시 만듭니다. 두 번째 반려 의견에서 등록자 이야기가 빠졌으니 그때는 고쳐졌다가, 설계 수정 뒤 사라진 것으로 보입니다. Tool 인자가 로그에 남지 않아 확정은 못 했습니다. | Workflow 구조 |
| 4 | **기존 관례를 따르지 않고 새 방식을 지어냈습니다.** 원본 `memberRegi.jsp`는 `async.saveUrl` + `autoUpload: true`로 먼저 올리고 `success`에서 `file_no`를 받는 방식입니다. 설계는 이 프로젝트 어디에도 없는 FormData 직접 전송 방식을 만들었습니다. Skill `gnx-crud-screen`의 업로드 항목은 서버 쪽(`imageSave`)만 다루고 화면 쪽 관례는 없습니다. "선언을 찾아 확인하라"는 규칙도 Java에만 해당하고, Kendo 같은 JS 라이브러리 API에는 확인할 수단이 없습니다. | Skill |
| 5 | **모델 성능.** Windows 설정의 chat 모델은 OpenRouter `qwen/qwen3.8-27b`입니다. 1000줄짜리 설계 코드를 쓰고, 증상만 적힌 반려 의견에서 원인을 추론하는 일에 27B 모델은 버겁습니다. 원인을 맞게 말하고도 코드는 틀리게 고친 것이 전형적인 예입니다. 개발 Agent의 입력도 23k에서 68k 토큰까지 커졌습니다. | 모델 |

## 개선 방안 (아직 아무것도 고치지 않았습니다)

1. **이번 건 바로잡기:** 재개발 의견에 구체적으로 적으면 됩니다. 예: "`selectedFile = e.files[0].rawFile`로 고칠 것. 등록 모드에서도 `imageBoardView.go`가 로그인 사용자 이름을 내려 `writer_nm`에 넣을 것." 증상보다 이렇게 쓰는 편이 이 모델에게 훨씬 잘 듣습니다.
2. **step14 고치기 (원인 3):** 개발 폴더 초기화를 첫 진입 때만 하거나, 재개발 수정을 설계서에 되돌려 적게 합니다.
3. **Skill 보강 (원인 4):** `gnx-crud-screen`에 화면 쪽 업로드 관례를 코드 모양 그대로 추가합니다(kendoUpload `async.saveUrl` → `success`에서 `file_no`를 hidden 필드에 저장 → 본 저장). 새 방식을 만들지 말고 참고 파일을 복사하라는 규칙도 넣습니다.
4. **검증 범위 넓히기 (원인 2):** 범용 Tool을 하나 두는 방법이 있습니다. 예를 들면 테스트 인스턴스에 HTTP를 호출하는 스모크 테스트, 또는 JSP 안 JS가 참조하는 라이브러리 API를 원본에서 쓰는 예가 있는지 찾는 검사입니다. 비용이 크니 우선순위는 낮게 봅니다.
5. **모델 비교 (원인 5):** 설계·개발 Agent에만 Agent별 provider로 Claude를 지정하고, `SmAgentEvalTest`로 기준선과 비교해 보는 것을 권합니다.

가장 효과가 클 것으로 보는 건 2번과 3번입니다. 진행할 항목을 골라 주시면 그 부분부터 작업하겠습니다.

---

## 질문

1. 네 제안중에 2번은 진행해줘. 그런데 재설계가 진행될 때 앞선 설계에서 지정한 파일을 (필요없어서)삭제해야하는 경우가 나왔을 때 개발폴더를 그대로 두면 사용하지 않는 자원이 남게 되는 것 아닌가?
2. 나는 이 기본 workflow 를 기본으로 진행하고 만일 만족스럽지 않은 개발결과가 나왔을 때 문제해결을 의뢰 할 super 개발자 agent가 필요하지 않을까 싶은데 네 생각은 어때?
   말하자면 추론레벨도 high이고 설계자만큼 검색권한이 있고 개발능력도 탁월한 agent 말이야.

---

## 답변

2번부터 진행하겠습니다. 먼저 관련 정의와 테스트, 문서를 확인합니다.

---

## 답변

Workflow와 개발 Agent 정의를 고칩니다.

---

## 답변

이제 문서를 맞추고 부팅 검증 테스트를 돌립니다.

---

## 답변

두 가지 모두 답드립니다. 1번은 처리했고, 2번은 의견입니다.

## 1. 개발 폴더 초기화 (반영함)

말씀하신 문제가 맞습니다. 폴더를 그대로 두면 재설계로 빠진 파일이 `dev-resources`에 남아 결과물에 섞입니다. 그래서 제가 낸 두 안 중 "첫 진입 때만 비우기"는 버리고 반대로 했습니다. **설계서를 원본으로 삼고, 재개발로 고친 내용을 설계서에 옮겨 적게** 바꿨습니다.

- **step14(개발 폴더 비우기)는 그대로 둡니다.** 설계를 승인할 때마다 폴더를 비우고 설계서대로 새로 만들므로 안 쓰는 파일이 남지 않습니다.
- **재개발은 폴더를 비우지 않고 고칩니다.** 고친 내용은 `03-1.design.md`에도 같은 before/after로 적고, 설계서에 없는 곳을 고쳤으면 '변경 전/변경 후' 한 쌍을 더합니다. 새로 만들거나 지울 파일이 생기면 설계서와 목록에 추가하고, `## 수정 이력`에 한 줄 남깁니다(`sm-developer-agent` 2.1.0).
  - 이렇게 하면 나중에 설계 수정을 거쳐 폴더를 다시 비워도 재개발로 고친 것이 사라지지 않습니다. 이번 실행에서 등록자 수정이 사라진 것이 바로 이 경우였습니다.
  - 재개발에서는 사람의 지시가 설계서보다 앞선다는 규칙도 넣었습니다. 전에는 설계와 달라지는 수정을 '보류'로 두게 되어 있었습니다.
  - 설계서에 옮겨 적었는지는 답의 `designSynced`로 보입니다.
- **재개발 전에 설계서와 목록을 다시 읽습니다.** 재개발 경로를 step17 → 새 step21·22(설계서·목록 다시 읽기) → step15로 바꿨습니다. 전에는 처음 읽은 옛 내용을 넘겨서, Agent가 목록을 고칠 때 before가 맞지 않아 파일을 몇 번씩 다시 읽었습니다.

**수정한 파일:** `sm-workflow.yml`(2.1.0), `sm-developer-agent.md`(2.1.0), `docs/09` 11.1절, `.claude/docs/ai-engine-tuning.md`, `SmDecisionFlowTest`(재개발 경로 테스트 추가)

**테스트:** sm 관련 테스트 6종은 `llmcall=false`로 모두 통과했습니다(새 테스트 포함). 실제 LLM으로 재개발 → 설계 수정을 거치는 흐름은 아직 돌려 보지 않았습니다.

**참고:**
- `conf/application.yml`이 `llmcall: true`로 되어 있어서, 그냥 돌리면 미리 넣은 답으로 도는 테스트가 건너뛰어집니다.
- Windows 인스턴스는 D: 클론에서 돌기 때문에 git으로 받아야 반영됩니다. 커밋은 말씀하시면 하겠습니다.

## 2. 해결 전문 개발자 Agent — 찬성합니다. 조건이 있습니다

기본 흐름은 싸고 빠른 모델로 돌리고, 막힐 때만 비싼 Agent를 부르는 구조는 합리적입니다. 이번 `rawFile` 버그도 라이브러리를 아는 강한 모델이라면 한 번에 잡았을 종류입니다. 다만 "더 똑똑한 개발자"만으로는 부족하고, 아래가 함께 있어야 효과가 납니다.

1. **모델을 실제로 바꿔야 합니다.** reasoning만 high로 올리고 같은 qwen 27B를 쓰면 차이가 크지 않습니다. Agent별 provider로 이 Agent만 Claude(anthropic)를 쓰게 하는 것이 핵심입니다.
2. **역할을 "구현"이 아니라 "원인 찾기 → 고치기"로 정합니다.** 반려 의견의 증상에서 출발해 화면 JS → Controller → Service → 매퍼 → DDL을 끝까지 따라갑니다. 비교 대상은 잘 돌아가는 기존 기능(예: `memberRegi.jsp`)과 라이브러리 소스(예: `kendo.upload.min.js`)입니다. 답에는 원인과 근거(파일:줄)를 먼저 적게 합니다. 이번 설계 수정 Agent처럼 원인을 말로만 맞히고 코드는 틀리는 일을 줄이려는 것입니다.
3. **설계서를 벗어날 권한과 기록 의무를 같이 줍니다.** 설계 자체가 틀린 경우가 많으니 설계와 다르게 고칠 수 있어야 합니다. 대신 방금 넣은 방식대로 고친 것을 설계서에 되돌려 적게 해서 설계서가 원본이라는 원칙을 지킵니다.
4. **조사 도구는 설계자 수준으로 줍니다.** knowledge 검색 Tool, `searchInFiles`, DB 조회(`dbQuery`)를 주고, `maxToolCalls`는 넉넉하게 잡습니다.
5. **Workflow에는 사람이 고르는 경로로 붙입니다.** 개발 승인(step17)에 "전문가 해결" 같은 route를 하나 더 두고, 그 뒤에 컴파일 확인(step16)과 개발 승인으로 돌아오게 합니다. 자동으로 넘기면 비용이 통제되지 않으니 사람이 고르게 하는 편이 낫습니다.

그래도 이 Agent는 코드를 실행해 볼 수 없으니 여전히 "읽고 추론"하는 한계는 남습니다. 그래서 반려 의견을 증상만 적는 대신 브라우저 콘솔이나 서버 로그 한 줄이라도 붙여 주시면 성공률이 크게 오릅니다.

진행하신다면 이름은 `sm-troubleshooter-agent`(정의 파일명은 영어 규칙)로 제안합니다. 1~5번 그대로 만들어도 될지, 특히 모델을 Claude로 지정해도 되는지 알려 주세요.

---

## 질문

모델은 통상 금융권에서 사용하듯이  폐쇄망에서 돌아야 하고 너무 고비용이 수반될 모델이면 안돼. 네가 제안해볼래?

---

## 답변

폐쇄망에서 돌릴 수 있고 비용도 감당할 만한 모델로 제안드립니다. 기준은 세 가지입니다. 오픈 웨이트일 것, 상업적으로 써도 되는 라이선스일 것, 전체 파라미터 대비 실제로 계산하는 파라미터가 작은 MoE 구조일 것입니다. MoE는 메모리는 많이 차지하지만 한 토큰을 만들 때 일부 파라미터만 쓰므로 응답이 빠르고 서버 대수가 적게 듭니다.

## 엔진 쪽은 고칠 것이 없습니다

엔진은 OpenAI 호환 접속(`openai` provider)을 provider당 하나만 둡니다. 대신 Agent마다 `model.name`을 따로 지정할 수 있습니다. 그래서 사내에 vLLM 같은 OpenAI 호환 서버 하나로 모델 두 개를 띄우면 됩니다.
- 기본 Agent들: 지금처럼 가벼운 모델
- 해결 Agent: `model.name`만 다른 모델로 지정

`base-url`만 사내 주소로 바꾸면 되고 코드는 그대로입니다.

## 제안

| 안 | 해결 Agent 모델 | 규모·라이선스 | 서버 (제 추정) |
|---|---|---|---|
| **1안 (권장)** | **GLM-5.3-Flash** 또는 **DeepSeek V4-Flash** | 320B 중 18B 사용 / 284B 중 13B 사용, 둘 다 MIT. V4-Flash는 SWE-bench Verified 79.0%(제조사 발표) | INT4 기준 가중치 약 150~180GB. 96GB GPU 4장 서버 1대 또는 H200 2장. 같은 서버에 기본 Agent용 Qwen3.8-27B도 함께 올릴 수 있음 |
| 2안 (저비용) | **Qwen3-Coder-Next** 또는 **gpt-oss-120b** | 80B 중 3B 사용, SWE-bench Verified 약 71% / 117B 중 5.1B 사용, Apache 2.0, 추론 강도를 high로 지정 가능 | GPU 1장 (96GB / 80GB) |
| 중국계 모델을 못 쓸 때 | **gpt-oss-120b**(OpenAI), **Devstral 2**(Mistral, 123B, Verified 72.2%) | gpt-oss는 Apache 2.0. Devstral 2는 수정 MIT라 조건 확인 필요 | GPU 1장 (96GB) |

- **1안을 권하는 이유:** 이번 `rawFile` 같은 버그는 라이브러리 지식과 긴 추론이 필요합니다. 2안 모델들은 지금 쓰는 qwen 27B보다 얼마나 나을지 확신하기 어렵습니다. 1안은 GPU 서버 한 대로 기본 모델과 해결 모델을 같이 돌릴 수 있습니다.
- **금융권이라면 먼저 확인할 것:** 가중치를 내부에서만 돌리면 데이터는 밖으로 나가지 않습니다. 그래도 보안성 심의에서 중국계 모델(GLM, DeepSeek, Qwen)을 문제 삼는 곳이 있습니다. 이 확인 결과에 따라 1안을 쓸지, 마지막 줄의 대안으로 갈지가 갈립니다.
- **숫자는 참고만 하세요:** 성능 수치는 대부분 제조사 발표라 서로 비교하기 어렵습니다. 서버 사양은 파라미터 수로 제가 어림한 값이고 측정값이 아닙니다.

## 고르는 방법: 사기 전에 이번 사례로 시험

후보들은 대부분 OpenRouter에도 올라와 있습니다. 그래서 장비를 사기 전에 지금 설정 그대로 `model.name`만 바꿔 시험할 수 있습니다. 시험에는 실제 업무 코드가 아닌 testApp만 써야 합니다.

- 시험 문제: 이번 실행(`9dc51bb8`)의 반려 상황을 그대로 줍니다.
- 합격 기준: 업로드 버그(`rawFile`)와 등록자 누락을 원인과 근거까지 맞게 찾고 고치는가.
- 방법: 후보마다 3번씩 돌려 비교합니다. 벤치마크 점수보다 이 결과가 고르는 데 훨씬 믿을 만합니다.

진행하시면 이렇게 하겠습니다.
1. `sm-troubleshooter-agent`를 만들고 step17에 "전문가 해결" 경로를 붙입니다. 모델은 `model.name`으로 쉽게 바꿀 수 있게 둡니다.
2. 이번 사례로 평가 케이스를 만들어 후보 3~4개를 비교한 결과를 보고드립니다.

참고로 앞에서 고친 1번(개발 폴더·설계서 동기화)은 아직 커밋하지 않았습니다.

Sources:
- [Best Open-Weight Coding Models to Self-Host in 2026 (Digital Applied)](https://www.digitalapplied.com/blog/best-open-weight-coding-models-self-host-hardware-match-2026)
- [Best Open-Source Coding Model 2026 (Morph)](https://www.morphllm.com/best-open-source-coding-model-2026)
- [Best open-weight models for coding (Faros)](https://www.faros.ai/blog/open-weight-models)
- [The Best Open-Source LLMs for Agentic Coding in 2026 (MindStudio)](https://www.mindstudio.ai/blog/best-open-source-llms-agentic-coding-2026)

---

## 질문

우리 회사는 현재로서는 큰 비용을 들이지 않고 POC만이라도 진행해서 고객사에 영업을 하려고 해.
수주를 하게되면 비용은 우리가 걱정하지 않겠지만 적어도 POC를 진행하려면 유사 성능을 보일만한 모델을 골라야 할텐데...
어떤 모델을 선택해야 할 지 모르겠어.

---

## 답변

POC 비용을 가늠하려고, 이번 실행 한 건에 토큰이 얼마나 들었는지 로그에서 세어 보겠습니다.

---

## 질문

[Request interrupted by user]

---

## 질문

내말은 POC진행까지는 우리회사에서 비용을 들여야 한다는 말이야. 그리고 POC를 진행하는 환경은 동일한 폐쇄망이어야 하고. 그래서 POC를 진행할 수 있을만한 수준의 모델을 비용 고려해서 선택을 해야 한다는 말이야.

---

## 답변

POC도 폐쇄망 안에서 회사 비용으로 돌려야 한다는 뜻으로 이해했습니다. 그렇다면 고르는 기준은 **"GPU 한 장짜리 서버 한 대에서 돌면서, 시연에서 지금보다 확실히 나은 결과를 내는 모델"**입니다. 처음부터 다시 정리하겠습니다.

## 필요한 사양은 실제 사용량에서 나옵니다

Windows 로그에서 이번 실행(`9dc51bb8`, 재개발 3번 포함) 한 건을 세어 보니 다음과 같았습니다.
- LLM 호출 148번, 입력 약 465만 토큰, 출력 약 20만 토큰
- 개발 Agent 한 번 호출의 입력이 최대 약 68k 토큰

여기서 두 가지 조건이 나옵니다.
1. **문맥 길이 128k 이상, 그리고 그만큼의 메모리 여유.** 24~32GB 소비자용 GPU에서는 모델을 올리고 나면 이 길이의 문맥을 담을 자리가 모자랍니다.
2. **속도.** 출력 20만 토큰을 초당 30토큰으로 만들면 생성만 2시간 가까이 걸립니다. 시연하려면 한 토큰에 쓰는 파라미터가 작은 MoE 모델이 유리합니다(제 추정치입니다).

## 추천: 96GB GPU 1장 서버 + gpt-oss-120b

| | 1순위 **gpt-oss-120b** | 2순위 **Qwen3-Coder-Next** |
|---|---|---|
| 규모 | 117B, 그중 5.1B만 사용 (MoE) | 80B, 그중 3B만 사용 (MoE) |
| 메모리 | 80GB GPU 1장 | Q4 기준 약 46GB |
| 문맥 | 128K | 256K |
| 라이선스 | Apache 2.0 | 출처마다 다름(Apache 2.0 / Qwen 라이선스), 확인 필요 |
| 추론 강도 조절 | low/medium/high 지원 → 엔진의 `model.reasoning`과 맞음 | 없음 |
| 금융권 심의 | 미국(OpenAI) 모델이라 부담 적음 | 중국계 모델이라 문제 될 수 있음 |
| 코딩 성능 | 공식 SWE-bench 점수 없음 | SWE-bench Verified 약 71% (제조사 발표) |

**gpt-oss-120b를 1순위로 두는 이유**
- 한 모델로 해결 Agent 구상까지 그대로 보여 줄 수 있습니다. 기본 Agent는 reasoning medium, 해결 Agent는 high로 두면 됩니다.
- 금융권 고객에게 보안성 심의 부담이 적습니다.

**빼는 것:** 24~32GB GPU급 모델(Devstral Small 2, Qwen3-Coder-30B 등)은 더 싸지만 지금 쓰는 qwen 27B와 체급이 비슷합니다. 사용자께서 겪으신 "그럴듯하지만 안 도는" 결과를 시연에서 다시 보일 가능성이 커서 영업용으로는 권하지 않습니다.

## "비슷한 성능"에 대한 솔직한 평가

GPU 한 장짜리 POC가 수주 뒤 쓸 큰 모델(GLM-5.3-Flash, DeepSeek V4-Flash급)과 같은 성능을 내지는 못합니다. 대신 영업 메시지를 이렇게 잡을 수 있습니다.
- 지금 구조는 서버와 `model.name`만 바꾸면 큰 모델로 올라갑니다.
- 품질을 크게 좌우하는 것은 모델만이 아닙니다. 설계서와 개발 결과의 동기화, 화면 관례를 담은 Skill, 해결 Agent 같은 Workflow 장치도 큽니다. 이 부분은 POC에서 그대로 보여 줄 수 있습니다.

## 장비를 사기 전에 할 일

두 후보 모두 OpenRouter에도 있습니다. 실제 업무 코드가 아닌 testApp으로 이번 사례(업로드 `rawFile` 버그, 등록자 누락)를 몇 번 돌려 보면 어느 모델이 나은지 미리 가릴 수 있습니다.
- 이건 POC가 아니라 모델을 고르는 단계입니다.
- 이번 실행 정도의 사용량이면 gpt-oss-120b 기준 한 번에 1달러가 안 듭니다. 가격은 출처마다 차이가 있습니다.
- 사내 서버에서는 양자화 방식에 따라 결과가 조금 달라질 수 있으니, 최종 확인은 장비에서 다시 합니다.

진행하시면 이 순서로 하겠습니다.
1. 이번 사례로 평가 케이스를 만들어 두 후보와 지금 쓰는 qwen 27B를 비교합니다.
2. 결과를 보고 해결 Agent(`sm-troubleshooter-agent`)를 만듭니다.

GPU 가격은 제가 확인하지 못했으니 견적은 따로 받아 보셔야 합니다.

Sources:
- [Best Open-Weight Coding Models to Self-Host in 2026 (Digital Applied)](https://www.digitalapplied.com/blog/best-open-weight-coding-models-self-host-hardware-match-2026)
- [Best Open-Source Coding Model 2026 (Morph)](https://www.morphllm.com/best-open-source-coding-model-2026)
- [OpenRouter API Pricing, November 2026 (BenchLM)](https://benchlm.ai/hosts/openrouter)
- [OpenRouter Pricing (pricepertoken)](https://pricepertoken.com/endpoints/openrouter)
- [OpenRouter LLM API Pricing (computeprices)](https://computeprices.com/providers/openrouter)

---

## 질문

그렇다면 DeepSeek V4-Flash 모델로 해볼께.

---

## 답변

DeepSeek V4-Flash로 바꿀 수 있게 지금 설정부터 확인하겠습니다.

---

## 답변

기본 모델을 DeepSeek V4-Flash로 바꿨습니다. `model.name`을 따로 적은 sm Agent가 없어서 sm-workflow의 모든 Agent가 이 모델로 바뀝니다. 아직 이 모델로 실제 실행은 돌려 보지 않았습니다.

**바꾼 것**
- `dstone-ai-engine/conf/application.yml`: 기본 모델을 `qwen/qwen3.8-27b`에서 `deepseek/deepseek-v4-flash`로 바꿨습니다.
  - 이 ID는 Hugging Face의 `deepseek-ai/DeepSeek-V4-Flash`(284B 중 13B 사용, MIT)와 같은 가중치입니다. 지금 OpenRouter로 시험한 결과가 폐쇄망에 설치했을 때와 같은 모델의 결과가 됩니다.
  - `~deepseek/deepseek-v4-flash-latest`는 쓰지 않았습니다. 최신 버전(V4.1, 552B로 더 큼)으로 저절로 넘어갈 수 있어서 고정 ID로 묶었습니다.
- `docs/09` 4부 설정표의 기본 모델 값도 같이 고쳤습니다.

**시험 비용 (OpenRouter 목록에서 조회한 가격)**
- 입력 100만 토큰당 $0.09, 출력 $0.18
- 이번 실행(`9dc51bb8`) 규모(입력 약 465만, 출력 약 20만 토큰)로 한 번 돌리면 약 $0.45입니다.
- 무료 모델이 아니므로 OpenRouter 계정에 크레딧이 있어야 합니다.

**알아 두실 점**
- **POC 장비가 커집니다.** 이 모델은 INT4로 줄여도 가중치만 약 150GB 이상이라(제 추정) 앞서 말씀드린 96GB GPU 1장으로는 안 됩니다. 96GB 2장 이상이나 H200 2장 정도를 잡으셔야 합니다.
- **금융권 보안성 심의.** DeepSeek는 중국계 모델이라 심의에서 질문받을 수 있습니다. 영업 전에 고객사 기준을 확인해 두시는 게 좋습니다.
- **Windows 인스턴스에는 아직 반영되지 않았습니다.** D: 클론에서 돌고 있어서 git으로 받은 뒤 재시작해야 합니다. 이번 변경과 앞의 개발 폴더·설계서 동기화 변경 모두 아직 커밋하지 않았습니다. 커밋할까요?
- **동시 요청 수.** `llm.gate.providers.openai.max-concurrent: 2`는 무료 모델에 맞춰 낮게 잡은 값입니다. 유료 모델이라 올려도 되지만 지금은 그대로 뒀습니다.

다음으로는 이번 사례(업로드 `rawFile` 버그, 등록자 누락)를 V4-Flash로 다시 돌려 qwen 27B와 비교해 보시길 권합니다. 그 결과를 보고 해결 Agent(`sm-troubleshooter-agent`)를 만들지 정하면 됩니다.

---

## 질문

아니 다시 정리할께. 기본 : qwen/qwen3.8-27b, troubleshooter : gpt-oss-120b

---

## 답변

기본은 qwen 27B로 되돌리고, 해결 Agent만 gpt-oss-120b를 쓰게 하겠습니다. 먼저 모델 ID와 지원 기능을 확인합니다.

---

## 답변

Agent 정의를 작성합니다.

---

## 답변

이제 Workflow에 경로를 붙입니다.

---

## 답변

테스트도 추가합니다. 경로 확인과, 미리 넣은 답으로 step25를 실제로 돌려 `requiredWrites`·답 모양이 통과하는지 봅니다.

---

## 답변

Testing is running in the background. Meanwhile I'm updating docs.

---

## 답변

Update memory to match the decision.

---

## 답변

Tests are still running. I'll report the results when they finish.

---

## 질문

<task-notification>
<task-id>by2vpjk8e</task-id>
<tool-use-id>toolu_01UZVj9knh8zEEsJBqPH3mwK</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/4fe99213-ce23-4d3d-9fcf-2c5fe70430a4/tasks/by2vpjk8e.output</output-file>
<status>completed</status>
<summary>Background command "mvn -q test -DskipTests=false -Ddstone.ai.test.llmcall=false -Dtest='DefinitionBootTest,SmDecisionFlowTest,WorkFlowTest,RequiredToolsTest,SampleAgentProviderOverrideTest' -Dsurefire.failIfNoSpecifiedTests=false &gt; /tmp/claude-1000/-app-dstone/4fe99213-ce23-4d3d-9fcf-2c5fe70430a4/scratchpad/t2.log 2&gt;&amp;1; echo rc=$?; grep -h 'Tests run' target/surefire-reports/*{DefinitionBoot,SmDecisionFlow,sm.WorkFlowTest,RequiredTools,ProviderOverride}*.txt; grep -h -B2 -A25 '&lt;&lt;&lt; FAIL\|&lt;&lt;&lt; ERR' target/surefire-reports/*SmDecisionFlow*.txt target/surefire-reports/*DefinitionBoot*.txt | head -50" completed (exit code 0)</summary>
</task-notification>

---

## 답변

해결 Agent를 만들고 개발 승인(step17)에 "전문가 해결" 경로를 붙였습니다. 기본 모델은 qwen 27B로 되돌렸고, gpt-oss-120b는 해결 Agent만 씁니다. 미리 넣은 답으로 도는 테스트는 통과했지만, 실제 gpt-oss-120b로는 아직 돌려 보지 않았습니다.

**모델 배정**
- **기본 Agent들:** `qwen/qwen3.8-27b`입니다. `application.yml`은 원래대로 되돌렸습니다.
- **해결 Agent:** 새로 만든 `sm-troubleshooter-agent`만 `model.name: openai/gpt-oss-120b`, `reasoning: high`를 씁니다.
  - 이 OpenRouter ID는 Hugging Face의 `openai/gpt-oss-120b`와 같은 가중치이고 Apache 2.0 라이선스입니다.
  - 사내 서버로 옮길 때는 `base-url`과 모델 이름만 바꾸면 됩니다.

**사용 흐름**
개발 승인에서 **[전문가 해결]**을 고르고 의견에 본 증상을 적습니다(예: "업로드한 파일이 테이블에 저장되지 않음"). 그러면 설계서·목록을 다시 읽고(step23~24) → 해결 Agent(step25) → 컴파일 확인(step16) → 개발 승인으로 돌아옵니다. 해결 Agent가 실패하면 실행 전체가 멈추지 않고 개발 승인으로 돌아갑니다.

**해결 Agent가 하는 일** (기본 개발 Agent와 다른 점)
- 증상마다 화면 스크립트 → Controller → Service → SQL → 테이블까지 따라가서 값이 끊기는 곳을 찾습니다.
- 원본에서 이미 동작하는 기능과 한 줄씩 비교합니다. 라이브러리 사용이 의심되면 Kendo JS 같은 라이브러리 소스까지 확인합니다. 이번 `rawFile` 버그는 이 단계에서 잡히는 종류입니다.
- 원인마다 근거를 파일:줄로 적고, 근거가 없으면 고치지 않고 "사람이 확인할 것"으로 남깁니다.
- 지난 재개발에서 이미 써 본 방식은 되풀이하지 않습니다.
- 설계서와 다르게 고칠 수 있습니다. 대신 고친 내용은 반드시 설계서에 남겨야 하고, 설계서를 저장하지 않으면 엔진이 실패로 처리합니다(`requiredWrites`).
- 답에 원인·근거, 고친 것, 설계서와 달라진 점, 못 푼 것이 나와서 승인 화면에서 보입니다.

**테스트**
- `SmDecisionFlowTest`(새 테스트 포함 5개), 정의 부팅 검증, `WorkFlowTest`, `RequiredToolsTest` 모두 통과했습니다.
- 새 테스트는 전문가 해결을 골랐을 때 증상이 Agent에 전달되는지, 설계서가 고쳐지는지, 그다음 step16으로 가는지를 미리 넣은 답으로 확인합니다.
- `SampleAgentProviderOverrideTest` 1건은 실패했습니다. 이 테스트는 로컬 Ollama(11434 포트)를 실제로 부르는데, 지금 Ollama가 꺼져 있어 연결 시간 초과가 났습니다. 이번 변경과는 관계없습니다.

**바뀐 파일 (아직 커밋하지 않음)**
- 새 파일: `sm-troubleshooter-agent.md`
- 수정: `sm-workflow.yml`(2.2.0), `sm-developer-agent.md`(2.1.0, 앞의 설계서 동기화 작업), `SmDecisionFlowTest`, `docs/09` 11.1절, `.claude/docs/ai-engine-tuning.md`

**다음 단계 제안**
1. Windows의 `9dc51bb8` 실행은 지금 개발 승인 대기 상태입니다. 커밋 → Windows에서 pull → 재시작한 뒤 **[전문가 해결]**을 골라 보시면 바로 시험할 수 있습니다. 진행 중이던 실행에 새 경로가 보이는지는 확인하지 않았습니다. 안 보이면 새로 실행하셔야 합니다.
2. gpt-oss-120b는 OpenRouter에서 유료입니다(입력 100만 토큰당 $0.09, 출력 $0.36). 크레딧이 있어야 호출됩니다.

커밋할까요?

---

## 질문

응 커밋하고 올려줘.

---

## 답변

커밋(`3acb6f7c`)해서 `main`에 올렸습니다. 지금까지 이 대화에서 바꾼 것이 모두 들어 있습니다.

- **sm-workflow 2.2.0:** 재개발 전에 설계서와 목록을 다시 읽고(step21~22), 개발 승인에 [전문가 해결] 경로를 추가했습니다(step23~25).
- **sm-developer-agent 2.1.0:** 재개발로 고친 내용을 설계서에도 옮겨 적습니다.
- **sm-troubleshooter-agent 1.0.0:** 새로 추가했습니다. gpt-oss-120b, reasoning high입니다.
- **그 밖:** 테스트 추가, `docs/09` 11.1절과 `.claude/docs/ai-engine-tuning.md` 갱신

Windows에서 시험하시려면 이렇게 하시면 됩니다.
1. `D:/AppHome/framework/dstone`에서 `git pull`을 받고 dstone-ai-engine을 재시작합니다.
2. 실행 `9dc51bb8`의 개발 승인 화면에서 **[전문가 해결]**을 고르고, 의견에 증상을 적습니다(예: "업로드한 파일이 테이블에 저장되지 않음").
3. 승인 화면에 [전문가 해결]이 보이지 않으면 새 실행으로 시험해 주세요. 진행 중이던 실행에 새 경로가 보이는지는 확인하지 못했습니다.
4. gpt-oss-120b는 OpenRouter 유료 모델이라 계정에 크레딧이 있어야 호출됩니다.

결과가 나오면 원인을 제대로 짚었는지(`rawFile`, 등록자 누락) 같이 보겠습니다.
