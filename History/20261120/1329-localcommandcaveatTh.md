# localcommandcaveatTh

> 생성일시: 2026-11-20 13:29

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="1c9f">
dstone-common/src/main/resources 가 잘못 위치하고 있는 것 같아.
dstone-common 는 서버용 라이브러리들을 모아놓은 공통모듈인데 웹자원들이 들어오면 안될 것 같아.
또 dstone-console 이라는 명칭도 난 생소해. 아마도 dstone-boot 에서 사용하는 것 같은데...
모두 제자리로 돌려줘. dstone-common 는 적어도 지금까지는 resources가 필요 없을 것 같아.
</pasted_content id="1c9f">

---

## 답변

먼저 `dstone-common/src/main/resources`에 무엇이 있는지, 어디서 쓰는지 확인하겠습니다.

---

## 답변

ai-engine 쪽 `css` 폴더가 없어서 CSS 두 개만 옮겨지지 않았습니다. 다시 옮깁니다.

---

## 답변

이제 파일 머리 주석을 각 앱 기준으로 고칩니다.

---

## 답변

문서를 고치기 전에, 자원 주소가 `/dstone-console/...`에서 `/ai/assets/...`로 바뀌면서 로그인 필터에 걸리지 않는지 확인합니다.

---

## 답변

필터는 `<uiRoot>/assets/`를 이미 빼 주므로 문제없습니다. 이제 dstone-common pom과 문서를 고칩니다.

---

## 답변

문서 갱신을 마쳤습니다. 리소스 복사 단계만 돌려서 빌드 결과에 파일이 제대로 들어가는지 확인합니다 (실행 중인 jar는 건드리지 않습니다).

---

## 답변

웹 자원은 dstone-common에서 모두 빼서 제자리로 돌려놓았습니다. 커밋은 아직 하지 않았습니다.

**`dstone-console`은 무엇이었나**
dstone-boot에서 쓰던 것이 아니었습니다. 원래 dstone-boot의 `webapp/ai/assets/css/console.css`, `main.css`, `fonts/`에 있던 AI·코드 분석 화면의 모양 파일입니다. 11-18에 두 화면을 dstone-ai-engine과 dstone-knowledge로 옮기면서, 두 앱이 같이 쓰도록 dstone-common의 `META-INF/resources/dstone-console/`로 넣고 그때 이름을 새로 붙였습니다. 지금 dstone-boot는 이 파일을 쓰지 않습니다.

**바꾼 내용**
- 화면을 실제로 쓰는 두 앱에 하나씩 넣었습니다. `console-parts.css`는 원래 이름인 `main.css`로 되돌렸습니다.
  - dstone-ai-engine: `static/ai/assets/{css/console.css, css/main.css, js/console.js, fonts/}`
  - dstone-knowledge: `static/knowledge/assets/` 아래에 같은 구성
- AI 화면 5개와 Knowledge 화면 9개의 `/dstone-console/...` 경로를 각 앱의 `assets` 경로로 바꿨습니다.
- dstone-common `pom.xml`에서 `META-INF/resources` 복사 설정을 지웠습니다.
- 파일 머리 주석, `ConsoleSessionFilter` 주석, `CLAUDE.md`, `docs/09`, `docs/11`, `.claude/docs/knowledge-ui.md`를 고쳤습니다.

**확인한 것**
- 리소스 복사 단계만 실행해 두 앱의 빌드 결과에 파일이 들어가는지 봤고, 글꼴 파일이 깨지지 않은 것도 확인했습니다.
- 로그인 필터는 `/ai/assets/`, `/knowledge/assets/` 경로를 원래 건너뛰므로, 로그인을 켜도 CSS나 JS 요청이 SSO로 넘어가지 않습니다.
- 앱을 띄워 화면을 직접 열어 보지는 않았습니다.

같은 파일이 두 앱에 하나씩 생겼으니 모양을 고칠 때는 둘 다 맞춰야 합니다. 이 내용은 문서와 파일 주석에 적어 두었습니다.

**dstone-common `resources`에 아직 남은 것 (웹 자원이 아니라 그대로 둠)**

| 파일 | 하는 일 |
|---|---|
| `META-INF/spring.factories` | `ENC(...)` 설정값을 자동으로 복호화하는 처리기를 등록합니다. 지우면 다섯 모듈 모두 DB 비밀번호를 복호화하지 못해 기동에 실패합니다. 꼭 필요합니다. |
| `log4jdbc.log4j2.properties` | 모듈마다 자기 사본이 있어서 dstone-common 쪽은 사실상 쓰이지 않습니다. 지워도 될 것으로 보입니다. |
| `schema/01-create-table-mysql-dstone-messaging.sql`, `sqlmap-template/messaging/*.xml.sample` | dstone-common에 있는 Saga/Outbox 메시징 코드용 테이블 DDL과 매퍼 견본입니다. 서버 쪽 자원이라 웹 자원과는 다릅니다. |

`spring.factories`만큼은 dstone-common에 남겨야 합니다. `log4jdbc.log4j2.properties`는 지우고, 메시징 DDL과 견본은 `dstone-common/docs` 같은 비리소스 폴더로 옮기는 정리도 할 수 있습니다. 원하시면 말씀해 주세요.

참고로 API 호출자 이름 기본값 `caller: dstone-console`(`ApiKeyAuthFilter`)에도 같은 이름이 남아 있습니다. 파일 위치와는 관계없는 값이라 손대지 않았지만, 이름을 바꾸길 원하시면 함께 정리하겠습니다.

---

## 질문

<pasted_content id="1c9f">
  spring.factories만큼은 dstone-common에 남겨야 합니다. 
  => OK.
  log4jdbc.log4j2.properties는
  => 남겨.
  schema/01-create-table-mysql-dstone-messaging.sql, sqlmap-template/messaging/*.xml.sample
  => dstone-boot 로 옮겨줘.  
  참고로 API 호출자 이름 기본값 caller: dstone-console(ApiKeyAuthFilter)에도 같은 이름이 남아 있습니다. 파일 위치와는 관계없는
  값이라 손대지 않았지만, 이름을 바꾸길 원하시면 함께 정리하겠습니다.
  => 바꿔줘.
</pasted_content id="1c9f">

---

## 답변

먼저 옮길 위치와 호출자 이름이 쓰이는 곳을 확인하겠습니다. 호출자 이름은 dstone-knowledge에서 올린 문서의 주인(tenant)으로도 쓰이므로 DB에 남은 값도 확인해야 합니다.

---

## 답변

dstone-boot에는 이미 실제 매퍼 `sqlmap/common/messaging/SagaDao.xml`, `OutboxDao.xml`이 있습니다. 견본과 같은 내용인지, 문서에서 어떻게 안내하는지 확인합니다.

---

## 답변

남은 이전 이름은 모두 바꿨습니다. 마지막으로 dstone-boot와 dstone-common 문서에 파일 목록이 있는지 확인합니다.

---

## 답변

요청하신 정리를 끝냈습니다. 커밋은 아직 하지 않았습니다.

**1. 메시징 파일을 dstone-boot로 옮김**
- `dstone-boot/src/main/resources/schema/messaging/01-create-table-mysql-dstone-messaging.sql`
- `dstone-boot/src/main/resources/sqlmap-template/messaging/SagaDao.xml.sample`, `OutboxDao.xml.sample`

`git mv`로 옮겨서 변경 이력이 이어집니다. 이 파일들을 경로로 가리키는 코드나 문서는 없었습니다.

같은 테이블 3개(`TB_SAGA_INSTANCE`, `TB_SAGA_STEP_HISTORY`, `TB_OUTBOX_MESSAGE`)는 dstone-boot의 `02-create-table-mysql-dstone-boot.sql`에 이미 들어 있습니다. 견본 매퍼도 dstone-boot의 실제 매퍼보다 예전 내용입니다. 견본에는 `findPending`이 있고, 실제 매퍼는 그 대신 `claim`을 씁니다. 남겨 둘지는 나중에 정하셔도 됩니다.

**2. 호출자 이름 변경**
앱마다 구분되도록 이름을 나눴습니다.

| 앱 | 이전 | 이후 |
|---|---|---|
| dstone-ai-engine | `dstone-console` | `dstone-ai-screen` |
| dstone-knowledge | `dstone-console` | `dstone-knowledge-screen` |

두 앱의 `conf/application.yml`, `ApiKeyAuthFilter`의 기본값과 주석, `docs/09`, `docs/11`, `.claude/docs/ai-engine-features.md`를 고쳤습니다. 이제 저장소에 `dstone-console`이라는 이름은 남아 있지 않습니다.

이 값은 dstone-knowledge에서 화면으로 올린 문서의 주인 구분에도 쓰여서 먼저 DB를 확인했습니다. 기존 문서의 주인 값은 모두 비어 있고, AI 실행 기록의 caller 3건도 모두 null이었습니다. 그래서 이름을 바꿔도 기존 데이터가 안 보이게 되는 일은 없습니다.

**3. 그대로 둔 것**
dstone-common `resources`에는 이제 `META-INF/spring.factories`와 `log4jdbc.log4j2.properties`만 남았습니다.

이번 변경은 빌드하거나 앱을 띄워 확인하지 않았습니다. 코드 변경은 Java 문자열 값 두 곳뿐입니다. 실행 중인 dstone-ai-engine과 dstone-knowledge에 새 호출자 이름을 적용하려면 다시 빌드하고 재기동해야 합니다.

---

## 질문

커밋 푸시해줘.

---

## 답변

파일 이동과 새 파일은 자동 history 커밋(`2081c038`, `c816f855`)에 이미 함께 들어갔습니다. 나머지 수정분을 커밋하고 푸시합니다.

---

## 답변

커밋해서 `origin/main`에 푸시했습니다(`23890f1d`). 이제 로컬과 원격이 같습니다.

이번 변경은 두 커밋에 나뉘어 들어갔습니다.
- **자동 history 커밋** (`2081c038`, `c816f855`): 제가 스테이징해 둔 파일 이동과 새로 만든 파일이 여기에 먼저 포함됐습니다. 화면 공통 자원을 dstone-ai-engine과 dstone-knowledge로 옮긴 것, 메시징 파일을 dstone-boot로 옮긴 것이 여기에 있습니다.
- **`23890f1d`**: 나머지 수정을 모두 담았습니다. 화면 HTML 경로와 주석, dstone-common `pom.xml`, 호출자 이름 변경(`dstone-ai-screen`, `dstone-knowledge-screen`), 문서 갱신입니다.

빌드와 재기동은 하지 않았습니다. 실행 중인 dstone-ai-engine과 dstone-knowledge에 반영하려면 두 앱을 다시 빌드하고 재기동해야 합니다.

---

## 질문

sm-workflow 에서 
step06(리뷰 불통과 처리 결정) 의 결정사항이 reviewDecision 으로 남도록 되어있어.
그럼 step06에서 의 결정사항을 step07에서 참조하려면 어떻게 해야하지?

---

## 답변

`step07`의 `with`에서 `${state.reviewDecision.<필드>}`로 읽으면 됩니다. step06에 `save: reviewDecision`이 있고 Workflow `state:`에도 `reviewDecision`이 선언돼 있어서(268줄), 지금 정의 그대로 참조할 수 있습니다.

APPROVAL step의 결과는 엔진이 정한 `{decision, approver, comment}` 모양입니다.
- `${state.reviewDecision.decision}`: 사람이 고른 route 이름(`그대로 진행` 등)
- `${state.reviewDecision.comment}`: 승인자가 남긴 의견
- `${state.reviewDecision.approver}`: 승인한 사람

step07에는 이미 `feedback` 칸이 있으니, step03과 같은 줄을 넣으면 됩니다.

```yaml
  - id: step07
    name: 영향도 분석
    type: AGENT
    agent: sm-analyze-impact-agent
    with:
      base_dir: ${input}/${state.now}
      request: ${state.request}
      requirement: ${state.requirement}
      feedback: |-
        - 리뷰 불통과 처리 결정(step06): ${state.reviewDecision.decision:} / ${state.reviewDecision.comment:}
        - 영향도 분석 승인(step08): ${state.impactApproval.decision:} / ${state.impactApproval.comment:}
        - 설계 승인(step11): ${state.designApproval.decision:} / ${state.designApproval.comment:}
        - 개발 승인(step17): ${state.developApproval.decision:} / ${state.developApproval.comment:}
```

넣을 때 알아 둘 점이 세 가지 있습니다.

1. **끝의 `:`(기본값)는 꼭 붙여야 합니다.** step05 리뷰를 통과하면 step06을 거치지 않고 바로 step07로 갑니다. 그때는 `reviewDecision`이 비어 있습니다. `:`를 붙이면 값이 없을 때 빈 문자열로 채워집니다.
2. **step07에 쓸모 있는 것은 사실상 `comment`입니다.** step06에서 step07로 가는 route는 `그대로 진행` 하나뿐입니다. 그래서 step07에서 `decision`을 읽으면 항상 `그대로 진행`입니다. 승인자가 "이 부분은 감안하고 진행" 같은 의견을 남겼을 때 그 의견이 영향도 분석 Agent에 전달되는 것이 실제 효과입니다.
3. **예전 결정이 남아 있을 수 있습니다.** `state`는 실행 중에 지워지지 않습니다. 예를 들어 첫 번째 리뷰에서 불통과로 step06을 거쳐 step03 재분석으로 돌아갔다고 합시다. 두 번째 리뷰는 통과해서 바로 step07로 갑니다. 그래도 `reviewDecision`에는 첫 번째 결정(`고객요청사항 재분석` + 의견)이 그대로 남아 step07에 전달됩니다. step03의 `feedback`도 같은 방식이라 지금 구조와 일관되기는 합니다. 다만 Agent가 지난 피드백으로 읽을 수 있도록 Agent 프롬프트에 그 점을 밝혀 두는 편이 좋습니다.

원하시면 위 내용을 `sm-workflow.yml`에 바로 반영하겠습니다.

---

## 질문

sm-workflow 내에서 존재하는 모든 APPROVAL 과 연결되는 Agent스탭들에 동일하게 적용이 되었으면 좋겠어. 왜냐하면 승인자의 의견이 다음 스텝에 전달되어야 하는 것은 당연한 것 같아.

---

## 답변

먼저 sm-workflow 전체에서 APPROVAL step과 그 route가 가는 step을 확인하겠습니다.

---

## 답변

이제 테스트에 승인하고 앞으로 가는 세 경로를 확인하는 경우를 추가합니다.

---

## 답변

SmFeedbackTest 4개가 모두 통과했습니다. 문서에 변경 기록을 남깁니다.

---

## 답변

sm-workflow의 APPROVAL step 4개에서, 승인자 의견이 다음 Agent step으로 가는 길을 모두 이었습니다. `SmFeedbackTest`(4개, 새로 추가한 1개 포함)와 `RequiredToolsTest`는 통과했습니다. 실제 LLM으로 sm-workflow를 돌려 보지는 않았습니다. 아직 커밋하지 않았습니다.

**빠져 있던 곳**
되돌아가는 경로(재분석, 재설계 등)에는 이미 의견이 전달되고 있었습니다. 빠진 것은 **승인하고 앞으로 진행하는 경로 3곳**이었고, 각 Agent step의 feedback 맨 앞에 줄을 넣었습니다.

| 승인 | → Agent step | 추가한 줄 |
|---|---|---|
| step06 그대로 진행 | step07 영향도 분석 | `- [이 단계로 보냄] 리뷰 불통과 처리 결정(step06): 결정 / 의견` |
| step08 승인(설계진행) | step10 아키텍처 설계 | `- [이 단계로 보냄] 영향도 분석 승인(step08): …` |
| step11 승인(개발진행) | step15 개발 | `- [이 단계로 보냄] 설계 승인(step11): …` |

step17의 '승인'은 END로 가므로 받을 Agent가 없습니다.

**Agent 프롬프트도 함께 고쳐야 했습니다**
지금까지 세 Agent(영향도, 설계, 개발)의 `[feedback 읽는 법]`에는 "결정이 '승인'인 줄은 진행하라는 뜻이고 고칠 지적이 아니다"라고 적혀 있었습니다. 줄만 넣었다면 의견이 전달되기만 하고 반영되지 않았을 것입니다. 그래서 다음 규칙을 추가했습니다.
- `[이 단계로 보냄]`이 붙은 줄의 의견은 이번 작업에 주는 당부이므로 반영한다. '진행해'처럼 진행만 뜻하는 말은 빼고 본다.
- 표시가 없는 승인 줄의 의견은 다른 단계에 준 말이므로 반영하지 않는다.

표시를 붙인 이유는 step10이 step11 줄도 받기 때문입니다. 그 줄에는 지난번 '승인(개발진행)' 때 개발자에게 남긴 말이 들어 있을 수 있습니다. 표시가 없으면 설계 Agent가 그 말까지 설계에 반영하게 됩니다.

**고친 파일**
- `conf/definitions/workflows/sm/sm-workflow.yml`: 줄 3개 추가, 머리 주석에 규칙 추가, version 1.6.0 → 1.7.0
- `conf/definitions/agents/sm/` 아래 `sm-analyze-impact-agent.md`, `sm-architect-agent.md`, `sm-developer-agent.md`: `[feedback 읽는 법]`과 `feedback` 입력 설명
- `SmFeedbackTest`: 세 경로 확인 테스트 추가
- `docs/09`, `.claude/docs/ai-engine-tuning.md`: 변경 기록

**알아 둘 점**
`state`는 실행 중에 지워지지 않습니다. 그래서 되돌아갔다가 다시 오면 `[이 단계로 보냄]` 줄에는 마지막 승인 때의 의견이 남아 있습니다. 예를 들어 step11에서 재설계로 step10에 다시 오면, 예전 step08 승인 의견이 그대로 들어 있습니다. 원래 그 단계에 준 당부라서 다시 반영해도 맞는 내용이라고 보고 그대로 두었습니다.

지금 떠 있는 dstone-ai-engine에 적용하려면 디자이너에서 정의를 다시 읽거나 앱을 재기동해야 합니다. 커밋과 푸시가 필요하시면 말씀해 주세요.

---

## 질문

커밋, 푸시해줘

---

## 답변

변경 파일 7개를 `73fe4b46`으로 커밋해서 `origin/main`에 푸시했습니다. 이제 로컬과 원격이 같습니다.

지금 떠 있는 dstone-ai-engine에는 아직 반영되지 않았습니다. 바뀐 정의를 쓰려면 디자이너에서 정의를 다시 읽거나 앱을 재기동해야 합니다.

---

## 질문

/app/dstone/skills-lock.json 의 정체가 뭐야?

---

## 답변

`skills-lock.json`은 Agent Skill 설치 도구가 남긴 **잠금 파일**입니다. npm의 `package-lock.json`이 받은 패키지 버전을 적어 두듯, 이 파일은 외부에서 받아 온 Skill의 출처와 해시를 적어 둡니다. 앱 빌드나 실행과는 관계없고, Claude Code가 이 파일을 읽어서 Skill을 고르지도 않습니다.

**누가 언제 만들었나**
2026-11-17 커밋 `8eec1f84`("frontend-design skill을 dstone 프로젝트 범위로 추가")에서 들어왔습니다. 그때 `anthropics/skills` 저장소의 `frontend-design` Skill을 설치했습니다. Skill 파일은 `.claude/skills/frontend-design/`에 복사했고, 설치 기록이 이 파일에 남았습니다. 형식으로 보면 `npx skills add …` 같은 Skill 설치 CLI가 쓰는 파일로 보입니다. 설치에 쓴 명령은 커밋에 남아 있지 않아서 확실하지는 않습니다.

**적힌 내용**
지금은 Skill 하나만 적혀 있습니다.
- `source: anthropics/skills`, `sourceType: github`: GitHub의 어느 저장소에서 받았는지
- `skillPath`: 그 저장소 안의 위치
- `computedHash`: 받았을 때의 내용 해시. 나중에 원본이 바뀌었는지 알아보거나, 같은 버전으로 다시 설치할 때 씁니다.

`humanizer`, `respond-in-korean` 같은 나머지 `.claude/skills/` Skill은 직접 만들었거나 다른 방법으로 넣은 것이라 이 파일에 없습니다.

**지워도 되나**
지워도 `frontend-design` Skill은 그대로 동작합니다. 실제로 쓰는 것은 `.claude/skills/frontend-design/SKILL.md`이기 때문입니다. 다만 나중에 같은 설치 도구로 업데이트하거나 출처를 확인할 단서가 사라집니다. 작은 파일이라 남겨 두는 편을 권합니다.

---

## 질문

그렇다면 .claude/아래 적당한 위치에  옮겨줘.

---

## 답변

`skills-lock.json`을 `git mv`로 `.claude/skills-lock.json`에 옮겼습니다. 옮긴 Skill 폴더 `.claude/skills/` 바로 옆입니다. 저장소 안에서 이 파일 경로를 가리키는 곳은 없어서 다른 파일은 고칠 것이 없었습니다.

나중에 Skill 설치 도구(`npx skills add` 등)로 Skill을 추가하거나 업데이트하면 도구가 저장소 맨 위에서 이 파일을 찾을 것으로 보입니다. 그러면 맨 위에 새 `skills-lock.json`이 생길 수 있습니다. 그때는 두 파일의 내용을 합쳐서 다시 `.claude/`로 옮기면 됩니다.

아직 커밋하지 않았습니다. 커밋과 푸시가 필요하시면 말씀해 주세요.

---

## 질문

올려줘.

---

## 답변

`skills-lock.json`을 `.claude/`로 옮긴 변경은 이미 원격에 올라가 있습니다. 제가 커밋하기 전에 자동 history 커밋 `afa31061`이 스테이징해 둔 이 이동을 함께 담아 푸시했습니다. 그래서 제가 따로 커밋할 것은 남아 있지 않았습니다. 확인해 보니 원격 `main`이 `afa31061`이고 로컬과 같습니다.

이동 내용은 `afa31061` 하나에 들어 있고, 커밋 메시지는 자동 history 메시지입니다. 별도 메시지로 남기려면 그 커밋을 고쳐 강제 푸시해야 해서 그대로 두었습니다.

---

## 질문

<pasted_content id="1c9f">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
Windows 에서 진행.
실행 ID : 9294e45d-6c3c-4911-af87-3600454170c7
내용 :
지금 WAS 가 죽었어. 내용을 추적하고 원인을 분석해줘.
</pasted_content id="1c9f">

---

## 답변

Windows 쪽 실행이라 그쪽 로그와 실행 기록을 찾아보겠습니다. 먼저 DB에서 실행 기록을 찾고 Windows 로그 위치를 확인합니다.

---

## 답변

실행은 11:21:05에 step10(아키텍처 설계)에서 FAILED로 끝났습니다. 이것과 WAS가 죽은 것이 같은 일인지 확인하려면 step 이력과 Windows 로그를 봐야 합니다.

---

## 답변

확인을 마쳤습니다. **WAS는 오류로 죽은 것이 아니라 정상 종료 요청을 받고 꺼졌습니다.** 같은 시각 Workflow도 실패했는데, 둘은 서로 원인이 아닙니다.

## 1. WAS가 꺼진 원인

Windows 로그(`D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log`) 마지막 부분입니다.

```
11:21:05  실행 9294e45d … FAILED (step10)
11:21:09  화면이 실행 목록·파일을 다시 조회 (정상 응답)
11:21:23  SpringApplicationAdminMXBeanRegistrar … Application shutdown requested.
11:21:23  Commencing graceful shutdown … Graceful shutdown complete
11:21:26  HikariPool … Shutdown completed.   ← 로그 끝
```

- `Application shutdown requested.`는 Spring Boot의 JMX 관리 기능으로 들어온 **종료 요청**입니다. IDE(Eclipse/STS의 Stop·Relaunch 버튼, Boot Dashboard 등)가 앱을 끌 때 이 방법을 씁니다. 기동 로그의 실행 경로가 `target\classes`라서 IDE에서 띄운 것이 맞습니다.
- 그 직전에 `ERROR`, `Exception`, `OutOfMemoryError`가 하나도 없습니다. 종료도 graceful shutdown으로 차례대로 끝났습니다.
- 오늘 앱은 여섯 번 꺼졌고(08:44, 09:32, 10:32, 10:50, 11:09, 11:21), **모두 같은 방식**이었습니다. 앞의 다섯 번은 몇 분 뒤 다시 켜졌고, 11:21 종료 뒤에만 다시 켜지지 않았습니다. 지금 Windows에는 java 프로세스가 없습니다.

종료 요청은 IDE에서 들어왔습니다. 정확히 무엇을 눌렀는지는 로그만으로 알 수 없습니다. Workflow가 실패한 지 18초 뒤라서, 실패를 보고 Stop이나 Relaunch를 눌렀는데 다시 켜지지 않았을 가능성이 커 보입니다. **IDE에서 다시 실행하면 됩니다.**

## 2. 같은 시각 Workflow가 실패한 원인 (step10 아키텍처 설계)

| 시각 | 내용 |
|---|---|
| 10:06 | step10 첫 설계 완료 → 03-1/03-2/03-3 저장 |
| 11:18:05 | step11 설계 승인에서 **[재설계]** 선택 (의견: 메뉴 URL 변경 등) |
| 11:18~11:21 | step10을 두 번 시도했지만 둘 다 **저장 없이 Tool 50번 한도를 모두 씀** → FAILED |

- 두 번에 걸쳐 Tool을 99번 불렀습니다(`readFileLines` 56, `searchInFiles` 42, `dbDescribeTable` 1). `writeFile`과 `editFileTexts`는 **한 번도 부르지 않았습니다**. 그래서 꼭 성공해야 하는 Tool(`requiredTools`) 검사에 걸렸습니다.
- 재설계라서 이미 있는 설계서를 의견대로 고치기만 하면 됐습니다. 그런데 모델(`z-ai/glm-5.3-flash`)은 설계서를 다시 읽은 뒤 testApp 소스를 처음부터 다시 조사했습니다(FileService, SampleBoard*, JSP 등). 그러다 한도를 다 써 버렸습니다. 마지막 답에도 본인이 "Tool 호출 한도(50번)를 모두 사용하여 … 저장하지 못했습니다"라고 적었습니다.
- 11:18:05 로그의 "1.6.0으로 시작했는데 지금 정의는 1.7.0" 경고는 아까 고친 정의를 11:12 재기동 때 읽어서 생긴 것입니다. 실패와는 관계없습니다. 1.7.0에서 step10에 추가된 줄은 step08 의견 한 줄뿐입니다.

## 다음에 할 일

1. IDE에서 dstone-ai-engine을 다시 켭니다.
2. 이 실행은 FAILED라서 그대로는 이어지지 않습니다. 실행 관리 화면의 되감기로 step10부터 다시 돌리면, 앞 단계 결과와 step11 의견을 그대로 쓸 수 있습니다.
3. 같은 실패가 되풀이되지 않게 하려면 설계 Agent(`sm-architect-agent.md`)를 고쳐야 합니다. "feedback에 지적이 있으면 소스를 다시 조사하지 말고 지적된 부분만 `editFileTexts`로 먼저 고친다"는 규칙을 넣는 방법이 있습니다. 원하시면 고치겠습니다.

---

## 질문

<pasted_content id="1c9f">
먼저 IDE에서 종료요청이 들어왔다는게 이해되지 않고...(내가 종료하지 않았으니까)
둘째, 아래의 내용에 대한 대응책이 무엇일까? 왜 효율적으로 움직이지 못했을까?
재설계라서 이미 있는 설계서를 의견대로 고치기만 하면 됐습니다. 그런데 모델(z-ai/glm-5.3-flash)은 설계서를 다시 읽은 뒤 testApp
소스를 처음부터 다시 조사했습니다(FileService, SampleBoard*, JSP 등). 그러다 한도를 다 써 버렸습니다. 마지막 답에도 본인이
"Tool 호출 한도(50번)를 모두 사용하여 … 저장하지 못했습니다"라고 적었습니다.
</pasted_content id="1c9f">

---

## 답변

두 가지를 나눠서 보겠습니다. 먼저 종료 요청이 어디서 왔는지 Windows 쪽 실행 환경(IDE 설정, JMX 설정)을 확인합니다.

---

## 답변

첫 번째 원인을 찾았습니다. 두 번째 질문을 보기 위해 설계 Agent 프롬프트와 그때 받은 의견을 확인합니다.

---

## 답변

근본 원인을 보려면 무엇을 검색했는지가 중요합니다. `searchInFiles` 42번의 검색어를 확인합니다.

---

## 답변

두 가지 모두 원인을 확인했습니다. 지난 답에서 제가 틀리게 말한 부분이 있어 먼저 바로잡겠습니다.

## 1. WAS 종료: Eclipse가 먼저 문제를 일으켰습니다

Eclipse 로그(`D:/AppHome/framework/.metadata/.log`)와 앱 로그를 시각 순서로 맞춰 보면 다음과 같습니다.

```
11:21:20.937  Eclipse: SWTError: No more handles   ← Windows 그래픽 핸들 고갈, 화면 그리기 실패 (9번 연속)
11:21:21.076  Eclipse: Stopping Boot LS            ← Eclipse가 내려가기 시작
11:21:23      ai-engine: Application shutdown requested → graceful shutdown
11:21:37      Eclipse: 새 !SESSION                  ← Eclipse가 다시 켜짐
```

- ai-engine의 Eclipse 실행 설정(`dstone-ai-engine - DstoneAiEngineApplication.launch`)에는 `spring.boot.lifecycle.enable=true`가 켜져 있습니다. 이 설정이 켜져 있으면 **Eclipse가 내려갈 때 자기가 띄운 앱에 JMX로 종료 요청을 보냅니다**. 11:21:23의 종료 요청이 바로 이것입니다.
- 정리하면 사용자가 WAS를 끈 것이 아닙니다. Eclipse가 핸들 부족으로 고장 나서 꺼지거나 재시작되면서 WAS도 함께 끈 것입니다. Eclipse가 스스로 내려갔는지, 화면이 깨져서 Eclipse만 닫으셨는지는 로그로 구분할 수 없습니다. 어느 쪽이든 WAS를 끈 것은 Eclipse입니다.
- `No more handles`는 Eclipse 프로세스 하나가 Windows GDI/USER 핸들 한도(기본 10,000개)를 다 쓴 것입니다. 편집기 창을 많이 열어 두거나 Eclipse를 오래 켜 두면 생깁니다.

**대응책**
- Workflow처럼 오래 도는 작업을 할 때는 ai-engine을 Eclipse 밖에서 띄웁니다(`dstone-ai-engine/bin/startApp.bat`). 그러면 Eclipse가 죽어도 WAS는 영향을 받지 않습니다.
- Eclipse는 쓰지 않는 편집기를 닫아 두고, 오래 켜 두었으면 한 번씩 재시작합니다.

## 2. step10이 비효율적으로 움직인 이유

먼저 바로잡을 점입니다. 지난번에 "의견대로 고치기만 하면 됐다"고 말씀드렸는데, 정확하지 않았습니다. step11 의견 7개 중 #2 "별도 썸네일 파일을 만드는 방식으로 설계"와 #3 "최대 50MB"는 설계 내용 자체가 바뀌는 지적입니다. 그래서 `ImageUtil`, `FileService`, 업로드 설정을 새로 조사한 것은 필요한 일이었습니다. 문제는 **조사하는 방식과, 실패한 뒤 처리하는 방식**이었습니다.

| # | 원인 | 근거 |
|---|---|---|
| ① | **조사를 다 끝낸 뒤에 한꺼번에 쓰려고 함** | 99번 호출이 모두 읽기와 검색이었고, `editFileTexts`·`writeFile`은 0번이었습니다. 지적 7개 중 소스 조사가 필요 없는 #1, #4~#7도 하나도 반영하지 않았습니다. |
| ② | **한 번에 Tool 하나, 아주 잘게 검색함** | 한 회차가 0.7~0.9초였고, 매번 Tool 하나만 불렀습니다. `FileService.java` 한 파일 안에서 `imageSave`, `thumbnail`, `public FileVO`, `getFileDAO`를 따로따로 검색했습니다. 범위를 한 번 읽으면 끝날 일을 4~5번 나눠 했습니다. 프롬프트의 "같은 파일을 두 번 열지 않는다"가 오히려 이런 잘게 쪼갠 검색을 부추겼을 수 있습니다. |
| ③ | **남은 횟수 안내를 무시함** | 엔진은 한도가 다가오면 "앞으로 N번 남았다, 지금 저장하라"는 안내를 붙입니다(`ToolCallBudgetCallback`). 모델(`z-ai/glm-5.3-flash`)은 그래도 계속 조사했습니다. |
| ④ | **재시도가 처음부터 똑같이 다시 함** | 첫 시도의 마지막 답에는 "이번 단계에서 확정한 지적 반영 내용"이 정리돼 있었습니다. 그런데 재시도는 그 답을 버리고 같은 질문으로 다시 시작했습니다. 그래서 `ImageUtil`, `UPIMAGE`, `imageSave`, `FileService` 검색을 그대로 되풀이했습니다(`AgentExecutor.call()` 재시도 루프). |

## 대응책 (효과가 큰 순서)

1. **엔진: 재시도할 때 지난 시도의 답을 넘기기** (범용 Tool·엔진 수정)
   `requiredTools`를 못 지켜서 다시 부를 때, 지난 답을 함께 넘겨 "이 내용으로 바로 저장하라"고 합니다. 이번 경우라면 두 번째 시도는 조사 없이 저장만 하면 됐습니다. ④를 직접 해결합니다.
2. **엔진: 한도 끝 몇 번은 꼭 성공해야 하는 Tool만 허용하기**
   `requiredTools`가 아직 성공하지 않았는데 남은 횟수가 적으면, 저장 Tool(`writeFile|editFileTexts`) 말고는 거절합니다. 안내를 무시하는 모델(③)에도 강제로 적용됩니다. 기존 `requiredTools` 정의를 그대로 쓰므로 Workflow 전용 로직이 아닙니다.
3. **Agent 프롬프트: 재설계는 "지적 하나 → 바로 반영"**
   `sm-architect-agent.md` 1-3절을 고칩니다. 지적마다 최소한으로 조사하고 곧바로 `editFileTexts`로 반영한 뒤 다음 지적으로 넘어가게 합니다. 소스 조사가 필요 없는 지적부터 먼저 반영하게 합니다. 한 파일에서 여러 곳을 봐야 하면 검색을 여러 번 하지 말고 범위를 한 번 읽게 합니다. 그러면 중간에 한도가 끝나도 반영한 부분은 남습니다(①, ②).
4. (선택) 설계 Agent 모델을 바꾸거나 `maxToolCalls`를 늘립니다. 한도만 늘리는 것은 1~3 없이는 같은 일을 더 오래 하게 될 뿐이라 권하지 않습니다.

1~3을 모두 적용하기를 권합니다. 1과 2는 엔진 코드라 테스트(`ScriptedChatModel`로 재시도·한도 상황 재현)와 함께 만들고, 3은 정의 파일 수정입니다. 진행할까요?

---

## 질문

<pasted_content id="1c9f">
물어볼께.
개발을 위한 AI에이전트라는게 이렇게 먼저 만들어 놓고 헛점이 보일때마다 하나 하나 프롬프트를 더하고 하는 방식으로 개발되는가?
너무 비효율적이고 설령 특정 업무에 대해서 프롬프트를 완성해놨다고 하더라도 조금만 다른 업무를 마주치게 되면 또다시 뭔가 덧붙이고 하는 방식이라면...
결국에는 프롬프트지옥이 될 것 같아.
이 부분에 대한 표준적인 가이드(혹은 외부 모범적인 사례 등)가 없을까? 방향성을 찾기가 힘들어. 
</pasted_content id="1c9f">

---

## 답변

표준 하나로 정해진 것은 아직 없습니다. 분야가 2~3년밖에 안 됐기 때문입니다. 다만 "문제가 보일 때마다 프롬프트에 한 줄씩 더하는 방식은 나쁜 패턴"이라는 점은 공개된 가이드들이 대체로 같은 의견입니다. 말씀하신 "프롬프트 지옥"이 실제로 많이 겪는 실패 모습입니다. 대표적인 자료와 거기서 공통으로 말하는 원칙을 정리하고, 지금 dstone에 대어 보겠습니다.

## 참고할 만한 공개 자료

| 자료 | 핵심 |
|---|---|
| Anthropic, *Building effective agents* (2024.12) | 단순하고 조합 가능한 패턴부터 시작한다. 정해진 흐름은 코드(Workflow)가 맡고, LLM에는 판단만 맡긴다. 도구 설계(ACI, Agent-Computer Interface)에 프롬프트만큼 공을 들인다. |
| Anthropic, *Effective context engineering for AI agents* (2025), *Writing effective tools for agents* (2025) | 프롬프트 문구보다 "Agent가 무엇을 보고 있는가(컨텍스트)"가 결과를 더 좌우한다. 도구는 적고 굵게, 결과는 Agent가 다음 행동을 정하기 쉬운 모양으로 만든다. |
| OpenAI, *A practical guide to building agents* (2025) | 지시는 규칙 목록이 아니라 목표와 판단 기준으로 쓴다. 한 Agent가 감당 못 할 만큼 분기가 늘면 Agent를 나눈다. 가드레일은 코드로 둔다. |
| *12-Factor Agents* (HumanLayer, 오픈 문서) | 제어 흐름은 내가 가진다(Own your control flow). 작고 한 가지 일만 하는 Agent. 오류는 압축해서 컨텍스트에 넣는다. 결정적인 일은 LLM에 맡기지 않는다. |
| Eval-driven development (위 자료 모두 공통) | 실제 실패 사례로 평가 세트를 만들고, 무엇을 바꾸든 그 세트를 다시 돌려 확인한다. |

## 공통 원칙 4가지

**1. 실패를 고치기 전에 "어디에 속한 문제인가"부터 분류한다**

이 원칙이 가장 중요합니다. 실패의 상당수는 프롬프트 문제가 아닙니다.

| 실패 종류 | 고칠 곳 | 오늘 사례 |
|---|---|---|
| 기계적으로 보장할 수 있는 것(저장, 횟수, 재시도) | **엔진(코드)** | 한도 안에 저장, 재시도할 때 지난 답 넘기기 |
| Agent가 필요한 정보를 못 받음 | **컨텍스트(입력)** | 문서를 TOOL step이 읽어서 넘기는 것(이미 하고 있음) |
| 도구가 쓰기 불편해서 잘게 쪼개 부름 | **도구 설계** | 같은 파일을 키워드마다 검색. 한 번에 여러 키워드나 주변 줄을 돌려주는 검색이면 해결됨 |
| 일의 성격이 다른데 한 Agent가 다 함 | **Workflow 구조** | 처음 설계와 재설계가 한 프롬프트의 1-1~1-4 분기에 같이 있음 |
| 모델 능력 부족 | **모델 선택** | flash 모델이 남은 횟수 안내를 무시함 |
| 정말로 판단 기준이 모호함 | **프롬프트** | 이 경우만 프롬프트를 고친다 |

엔진과 도구에서 고친 것은 **모든 Agent와 모든 업무에 한꺼번에 적용**됩니다. 프롬프트에서 고친 것은 그 Agent의 그 상황에만 적용됩니다. 업무가 조금만 달라져도 다시 덧붙여야 하는 이유가 여기에 있습니다.

**2. 프롬프트에는 규칙 목록 말고 목표와 이유를 쓴다**

`sm-architect-agent.md`에는 지금 "~한다/~하지 않는다" 규칙이 40개 가까이 있습니다. 대부분 지난 실패마다 하나씩 더한 것입니다. 규칙이 많아지면 모델은 그중 일부를 놓치고, 규칙끼리 부딪히기도 합니다. "같은 파일 두 번 열지 마라"가 "잘게 검색하라"로 바뀐 것처럼요. 가이드들이 권하는 것은 다음 세 가지입니다.
- 무엇을 만들어야 하고 **누가 그것을 어떻게 쓰는지**(목표와 독자)
- 좋은 결과와 나쁜 결과의 **예시 1~2개**
- 꼭 지킬 제약 몇 개와 **그 이유**

이유를 알면 모델이 처음 보는 상황에서도 스스로 판단합니다. 규칙만 있으면 규칙이 없는 상황에서 멈추거나 엉뚱하게 움직입니다.

**3. 분기가 늘면 프롬프트를 키우지 말고 Agent나 step을 나눈다**

"처음 설계"와 "지적 반영"은 일 자체가 다릅니다. 처음 설계는 조사하고 작성하는 일이고, 지적 반영은 기존 문서를 고치는 일입니다. 한 프롬프트에 둘을 넣으면 조건문이 생기고, 모델은 어느 모드인지 헷갈립니다. 이번에도 재설계인데 처음 설계처럼 조사했습니다. Workflow에서 `feedback` 유무로 갈라 다른 Agent를 부르게 하면, 각 프롬프트가 짧아지고 서로 간섭하지 않습니다. dstone 엔진은 Java가 흐름을 정하는 구조라서 이미 이 원칙에 맞게 만들어져 있습니다. 그 구조를 더 적극적으로 쓰면 됩니다.

**4. 고칠 때마다 실제 사례로 다시 돌려 본다 (평가 세트)**

두더지 잡기가 되는 진짜 원인은 "고친 뒤 다른 경우가 깨졌는지 모른다"는 데 있습니다. 표준 방법은 이렇습니다.
- 실제 실행 중 실패했거나 대표적인 사례 5~10개를 입력과 기대 결과로 묶어 둡니다. 예: 오늘 `9294e45d`의 step10 재설계 상황, 11-19의 `4c163653`.
- 프롬프트나 모델, 엔진을 바꾸면 그 세트를 실제 LLM으로 다시 돌려 봅니다. 저장했는지, Tool을 몇 번 불렀는지, 지적을 모두 반영했는지를 확인합니다.
- 지금 `WorkflowTestBase`/`ScriptedChatModel` 테스트는 **배관**(값이 제대로 전달되는가)을 확인합니다. **Agent의 행동**을 확인하는 세트는 아직 없습니다. 지금 빠진 것이 이것입니다.

## dstone에 적용한다면

1. **실패 분류를 먼저 하는 습관을 들입니다.** 앞으로 실패를 분석할 때 위 표의 어느 칸인지부터 정하고, 프롬프트는 마지막 수단으로 둡니다. 지난 답에서 제안한 대응책 중 1, 2(엔진)는 이 원칙에 맞습니다. 3(프롬프트 추가)은 오히려 지옥을 키우는 쪽이었습니다. 3 대신 "재설계 전용 Agent로 나누기"가 맞는 방향입니다.
2. **sm Agent 프롬프트를 한 번 정리합니다.** 쌓인 규칙 중 엔진이나 도구가 이미 보장하거나 보장할 수 있는 것은 빼고, 나머지는 목표·이유·예시 중심으로 다시 씁니다. 분량이 절반 이하로 줄 것으로 봅니다.
3. **sm-workflow 평가 세트를 만듭니다.** History와 실행 기록에서 사례를 골라 `-Ddstone.ai.test.llmcall=true`로 돌리는 테스트로 둡니다. 정리 작업(2)이 성능을 떨어뜨리지 않았는지도 이것으로 확인합니다.

이 순서라면 처음 1~2주는 정리 비용이 들지만, 그 뒤로는 새 업무를 붙일 때 "프롬프트 한 줄 더"가 아니라 "평가 사례 하나 더"가 됩니다.

원하시면 이 방향을 `docs/09`나 `.claude/docs/`에 "Agent 개선 원칙"으로 적어 두고, 앞으로의 수정이 그 원칙을 따르도록 CLAUDE.md에 한 줄 넣겠습니다. 그다음 1번(엔진 대응책 2개)부터 진행할 수도 있습니다.

---

## 질문

<pasted_content id="1c9f">
응. 이 방향으로 진행하되 모든 Agent 정의를 재정리 했으면 좋겠어.
그리고 추가적으로 아래의 내용을 진행하고자 해. 네 계획을 말해줘.

1. APPROVER 의 의견을 후속/이전 Agent에 전달하는 부분에 대한 방법 정리.
    - 현재의 방법인 workflow의 state에 저장된 내용을 agent의 input(feedback)항목으로 넘기는 방법보다 합리적이고 편리한 방법이 있나?
    - APPROVER 의 의견이 해당 Agent의 성격에 맞지않는 명령이라면?
2. Skill 의 활용
    - 고객요청사항 분석Agent, 영향도 분석Agent, 아키텍처 설계Agent, 개발Agent 마다 가이드가 될 만한 Skill을 작성한 후 개별 Agent에 적용하면 어떨까?
</pasted_content id="1c9f">

---

## 답변

계획을 세우기 전에 현재 Agent 정의 전체 규모와 엔진의 Skill 기능이 어떻게 동작하는지 확인하겠습니다.

---

## 답변

계획을 정리했습니다. 먼저 바탕이 되는 현황입니다.

- Agent 정의 37개: sm 5, pilot 12, sample 18, test 2. 실제 업무용은 sm과 pilot이고, sm 설계·개발 Agent는 130~140줄입니다.
- Skill: 엔진에 기능은 있지만(`skills.allowed`, `Skill` Tool) 정의된 것은 견본 `sql-review` 하나뿐입니다.
- Skill은 **필요할 때 불러 읽는 방식**입니다. LLM에는 이름과 설명만 보이고, 모델이 `Skill` Tool을 불러야 본문을 읽습니다. 그래서 부를 때마다 Tool 호출을 1번 쓰고, 모델이 안 부르면 그 안내는 적용되지 않습니다.

---

## 1. 승인자 의견 전달 방식

### 지금 방식(state → `with.feedback`)의 문제

1. **연결을 손으로 관리합니다.** route를 새로 이을 때마다 해당 Agent step의 feedback 목록을 같이 고쳐야 합니다. 오늘만 해도 빠진 곳이 3곳이었습니다.
2. **"왜 지금 이 단계에 왔는가"를 알 수 없습니다.** state는 지워지지 않으므로 지난 루프의 결정이 섞여 들어옵니다. 그래서 `[이 단계로 보냄]` 같은 표시와 읽는 법 규칙을 프롬프트에 더해야 했습니다. 바로 프롬프트 지옥의 씨앗입니다.

### 제안: 엔진이 결정 기록을 관리하고, Workflow는 읽기만 합니다

- **결정 기록(decision journal):** 엔진이 실행마다 APPROVAL·SUPERVISOR의 결정을 시간 순서로 남깁니다. 남기는 값은 step, 이름, 결정, 의견, 승인자, **이동한 step**입니다. 엔진은 어떤 route가 어디로 갔는지 이미 알고 있으니, 특정 Workflow를 몰라도 되는 범용 기능입니다.
- **읽는 경로 2개를 새로 둡니다** (YAML에는 여전히 값을 읽는 경로만 쓰고, 로직은 넣지 않습니다).
  - `${arrival}`: **이 step으로 오게 만든 마지막 결정** 하나. 앞으로 진행한 승인이든 되돌린 승인이든 똑같이 이것 하나입니다.
  - `${decisions}`: 이 실행에서 지금까지 내려진 결정 전체(사람이 읽을 수 있는 목록).
- 그러면 각 Agent step은 `feedback` 목록 대신 `instruction: ${arrival}`, `history: ${decisions}` 두 줄만 갖습니다. route를 새로 이어도 고칠 곳이 없고, `[이 단계로 보냄]` 표시와 "결정이 승인이면 무시" 같은 읽는 법 규칙도 필요 없어집니다.
- TOOL step만 거쳐 Agent에 닿는 경우(step08 → step09 TOOL → step10)도 엔진이 "다음 Agent step까지" 넘겨줍니다.

### 의견이 그 Agent의 성격에 맞지 않을 때

예를 들어 설계 승인에서 "개발자는 로그를 꼭 남길 것"이라고 적었거나, 이 단계 권한 밖의 일(DB 직접 수정, 요구사항 변경)을 시키는 경우입니다. 세 겹으로 막겠습니다.

1. **Agent는 반영할 수 없는 의견을 버리지 않고 돌려줍니다.** 출력 계약에 `notApplied`(반영하지 않은 의견과 이유)를 둡니다. 프롬프트에는 사례별 규칙 대신 원칙 한 줄만 둡니다. "이 단계 산출물로 반영할 수 없는 의견은 반영하지 말고 이유와 함께 돌려준다."
2. **다음 승인 화면에 그것을 보여 줍니다.** 사람이 "이 의견은 반영되지 않았다"는 것을 알고 다시 판단할 수 있습니다.
3. **승인 화면에 route마다 받는 Agent를 보여 줍니다.** 예: "재설계 → 아키텍처 설계(sm-architect-agent)". 승인자가 의견을 쓰기 전에 누가 읽을지 알 수 있습니다.

Tool 권한(`tools.allowed`)은 그대로 마지막 안전장치입니다. 의견에 무엇이 적혀 있어도 Agent는 허용된 Tool 밖의 일을 할 수 없습니다.

---

## 2. Skill 활용

**"Agent마다 Skill 하나"는 권하지 않습니다.** 늘 쓰는 지침을 Skill로 빼면 프롬프트에 있을 때와 내용은 같습니다. 그런데 Tool 호출을 1번 더 쓰고, 모델이 안 부르면 빠집니다. 이번 flash 모델처럼 안내를 무시하는 모델에서는 더 위험합니다.

Skill이 진짜 효과를 내는 곳은 **"조금 다른 업무를 만날 때마다 덧붙이는 지식"**입니다. 그래서 다음처럼 나누겠습니다.

| 어디에 | 무엇을 | 예 |
|---|---|---|
| Agent 프롬프트 | 역할, 목표, 산출물 계약, 이유를 붙인 핵심 제약 몇 개 | "설계서는 개발자가 이것만 보고 구현한다" |
| **공통 Skill (여러 Agent가 같이 씀)** | 일하는 방법 | `source-investigation`(knowledge Tool 먼저, 범위 읽기, 한꺼번에 부르기, 멈출 때), `revise-document`(지적마다 바로 고치기) |
| **기술·업무 Skill (필요할 때만)** | 기술별 지식 | `spring-mybatis-crud`, `jsp-kendo-screen`, `file-upload`, `websquare-screen`… |
| 엔진·도구 | 기계적으로 보장할 것 | 저장 보장, 재시도, 횟수 |

- 새 업무를 만나면 프롬프트를 고치는 대신 **Skill을 하나 추가**합니다. 이것이 질문하신 "업무가 조금 달라질 때마다 덧붙이는 문제"에 대한 답입니다.
- 엔진 보강으로 **`skills.preload`**를 추가하겠습니다. 늘 필요한 공통 Skill은 Tool 호출 없이 처음부터 시스템 프롬프트에 넣습니다. 같은 지식을 여러 Agent가 한 파일로 공유하면서 "안 불러서 빠지는" 위험도 없앱니다. 기술 Skill은 지금처럼 필요할 때 부르게 둡니다.

---

## 3. 전체 진행 계획

| 단계 | 내용 | 결과물 |
|---|---|---|
| **0. 원칙 문서** | 실패 분류표, 프롬프트 작성 원칙, Skill·엔진·프롬프트의 경계 | `.claude/docs/ai-engine-agent-principles.md`, CLAUDE.md 한 줄 |
| **1. 평가 세트(기준선)** | 실제 실행 기록(`9294e45d`, `4c163653` 등)에서 sm 단계별 사례 5~8개를 뽑아 실제 LLM으로 돌리는 테스트. 저장 여부, Tool 횟수, 지적 반영, `notApplied`를 확인 | 지금 정의의 점수(기준선) |
| **2. 엔진 보강** | ① 결정 기록 + `${arrival}`/`${decisions}` ② 재시도할 때 지난 답 넘기기 ③ 한도 끝은 `requiredTools`만 허용 ④ `skills.preload` ⑤ `searchInFiles`에 여러 키워드와 앞뒤 줄 지원(잘게 쪼갠 검색 해소) | 범용 기능 + 단위 테스트 |
| **3. Skill 작성** | 공통 Skill 2~3개 + testApp 기술 Skill 몇 개 | `conf/definitions/skills/` |
| **4. Agent 재정리** | sm → pilot → sample/test 순서. 엔진이 보장하는 규칙은 지우고, 목표·이유·예시 중심으로 다시 씀. sm-workflow의 feedback 목록을 `${arrival}`로 교체 | 정의 37개 |
| **5. 다시 평가** | 1단계 세트를 다시 돌려 기준선과 비교. 나빠진 사례가 있으면 4단계로 돌아감 | 비교표(문서에 기록) |

단계마다 커밋을 나누고, 2단계와 4단계 결과는 문서(`docs/09`, `.claude/docs`)에 반영합니다.

## 진행 전에 정해 주실 것

1. **재설계·재분석을 별도 Agent로 나눌지**: 문서를 고치는 일은 문서 종류와 관계없이 방법이 같습니다. 그래서 `document-revise-agent` 하나를 두고 요구사항·영향도·설계의 [재…] route가 모두 이것을 부르게 하는 안을 권합니다. 대신 [재분석]이 "처음부터 다시"를 뜻하는 경우도 있으니, route 이름을 "수정"과 "다시 작성"으로 나누는 것이 좋습니다.
2. **pilot-workflow**: 지금도 쓰시나요? 쓰지 않으면 정리 대상을 sm에 집중하고 pilot은 보관(또는 삭제)하겠습니다.
3. **평가 세트의 실제 LLM 호출 비용**: 1단계와 5단계에서 sm Agent를 수십 번 부릅니다(사례당 수 분). 지금 쓰는 provider/모델로 돌려도 될까요?

이 세 가지를 정해 주시면 0단계부터 시작하겠습니다.

---

## 질문

document-revise-agent 는 문서만 수정하는건가?

---

## 답변

네, 제가 제안한 범위에서는 **이미 만든 문서를 지시대로 고치는 Agent**입니다. 다만 "글자만 고치는 Agent"는 아닙니다. 정확히 하면 이렇습니다.

## 하는 일

- **고치는 대상:** sm-workflow가 만든 문서입니다. `01.customer_requirement.md`, `02.impact.md`, `03-1.design.md`, `03-2.taskitems.md`, `03-3.ddl(데이터베이스).md`가 여기에 해당합니다.
- **조사는 합니다(읽기 전용).** 이번 재설계에서 "별도 썸네일 파일을 만드는 방식으로"라는 의견을 반영하려면 `ImageUtil`과 `FileService`를 실제로 봐야 했습니다. 그래서 소스·DB·knowledge Tool은 읽기 권한으로 쓰고, 쓰기는 `editFileTexts` 같은 문서 편집 Tool만 씁니다. 소스 코드는 건드리지 않습니다.
- **일하는 순서가 정해져 있습니다.** 지적 하나를 보고, 필요한 만큼만 조사한 뒤 바로 반영하고, 다음 지적으로 넘어갑니다. 이번 실패(①, ②)를 막는 방식이 이 Agent의 기본 동작이 됩니다.
- **관련 문서를 함께 맞춥니다.** 설계서를 고치면 그 변경에 맞춰 태스크아이템 목록과 DDL 문서도 같이 고칩니다. 하나만 고쳐서 문서끼리 어긋나는 것을 막기 위해서입니다.
- **반영할 수 없는 의견은 `notApplied`로 돌려줍니다.** 예를 들어 "개발자는 로그를 꼭 남길 것"처럼 문서 수정으로 반영할 수 없는 의견입니다.

## 하지 않는 일

| 경우 | 누가 맡나 | 이유 |
|---|---|---|
| **소스 코드 수정 ([재개발])** | 지금처럼 개발 Agent | 코드는 `dev-resources` 경로 규칙, 삭제 목록, 컴파일 확인 루프가 함께 움직입니다. 이건 개발 Agent의 일입니다. 개발 Agent는 이미 "있는 결과를 고치는" 방식으로 동작합니다. |
| **방향이 통째로 바뀌는 경우** | 원래 작성 Agent ("다시 작성" route) | 예: "요구사항을 처음부터 다시 정리해". 고칠 곳이 문서 대부분이면 고치는 것보다 새로 쓰는 편이 낫습니다. |

## 왜 문서 수정만 따로 떼는가

요구사항, 영향도, 설계 문서는 **내용은 달라도 고치는 방법이 같습니다.** 기존 문서를 읽고, 지적을 찾고, 그 부분만 고치고, 다른 부분은 그대로 둡니다. 지금은 이 방법이 세 Agent 프롬프트의 "1-3. feedback에 지적이 있는 경우" 같은 절에 따로따로 들어 있고, 그 절들이 프롬프트를 길게 만들고 있습니다. 하나로 모으면 세 작성 Agent는 "처음 작성"에만 집중할 수 있고, 고치는 방법은 한 곳에서 다듬으면 됩니다.

코드 수정까지 한 Agent에 넣는 방법도 있습니다. 하지만 그러면 "문서일 때는 이렇게, 코드일 때는 저렇게"라는 분기가 다시 생깁니다. 그래서 둘은 나눠 두고, 공통인 조사 방법은 `source-investigation` Skill로 같이 쓰게 하는 편이 낫다고 봅니다.

---

## 질문

<pasted_content id="1c9f">
진행해.
  1. 재설계·재분석을 별도 Agent로 나눌지: 문서를 고치는 일은 문서 종류와 관계없이 방법이 같습니다. 그래서 document-revise-agent 하나를 두고
     요구사항·영향도·설계의 [재…] route가 모두 이것을 부르게 하는 안을 권합니다. 대신 [재분석]이 "처음부터 다시"를 뜻하는 경우도 있으니,
     route 이름을 "수정"과 "다시 작성"으로 나누는 것이 좋습니다.
    =>  OK.
  2. pilot-workflow: 지금도 쓰시나요? 쓰지 않으면 정리 대상을 sm에 집중하고 pilot은 보관(또는 삭제)하겠습니다.
    => 삭제.
  3. 평가 세트의 실제 LLM 호출 비용: 1단계와 5단계에서 sm Agent를 수십 번 부릅니다(사례당 수 분). 지금 쓰는 provider/모델로 돌려도 될까요?
    =>  OK.
</pasted_content id="1c9f">

---

## 답변

진행하겠습니다. 범위가 커서 단계별로 진행 상황을 알려 드리겠습니다. 먼저 엔진 구조와 테스트 방식, pilot 관련 참조를 파악합니다.

---

## 답변

먼저 pilot 삭제 범위부터 확인합니다.

---

## 답변

pilot 삭제 범위는 파악했습니다. 이제 엔진 보강에 필요한 코드(결정 처리, 표현식, 재시도, Skill, 검색 Tool)를 읽습니다.

---

## 답변

결정 기록을 어디에 끼울지 정했습니다. 다음은 재시도·Skill·Agent 입력 처리가 있는 `AgentExecutor`를 읽습니다.

---

## 답변

엔진 구조를 파악했습니다. 기준선을 먼저 재야 하므로, 실제 LLM 평가가 WSL에서 돌아갈 수 있는지(설정, 키, testApp 경로, knowledge 서버)부터 확인합니다.

---

## 답변

Real LLM 응답도 기록하도록 `ScriptedChatModel`을 고칩니다.

---

## 답변

이제 평가 테스트를 작성합니다. 지금 Agent들의 input 모양부터 확인합니다.

---

## 답변

다음은 엔진 보강입니다. 결정 기록(E1)에 필요한 상수, 각 step 실행기의 결과 모양, 되감기 처리를 확인합니다.

---

## 답변

엔진 코드를 작성합니다. 먼저 결정 기록을 글로 바꾸는 `DecisionText`와 컨텍스트 저장 부분입니다.

---

## 답변

Now the expression reader (`ContextResolver`) — `${arrival}`/`${decisions}` 경로를 엽니다.

---

## 답변

결정 기록 기능을 엔진에 넣는 중입니다(저장·읽기 경로는 끝, 실행기 연결 마무리 중). 기준선 평가는 백그라운드에서 계속 돌고 있습니다.

---

## 답변

다음은 "이 route로 가면 어느 Agent가 받는가"를 계산하는 도우미입니다. 승인 화면 표시와 되감기 둘 다에서 씁니다.

---

## 답변

이제 승인 대기 정보에 "route별 받는 step"을 넣습니다.

---

## 답변

E1(결정 기록)을 마쳤습니다. 이어서 E2(재시도 때 지난 시도의 답을 넘기기)입니다.

---

## 답변

E2 완료. 이어서 E3(한도 끝 몇 번은 `requiredTools`만 허용)입니다.

---

## 답변

E3 완료. 이어서 E4(`skills.preload`: 늘 필요한 Skill을 처음부터 시스템 프롬프트에 넣기)입니다.

---

## 답변

E4 완료. 마지막 E5(`searchInFiles` 개선)입니다. 지금 시그니처를 봅니다.

---

## 답변

기준선 반복은 별도 폴더에서 돌고 있으니, 본 저장소에서는 엔진 변경을 컴파일하겠습니다.

---

## 답변

컴파일이 통과했습니다. 엔진 기능별 단위 테스트를 작성합니다. 기존 테스트 구성을 확인합니다.

---

## 답변

Now the budget-reserve unit test:

---

## 답변

저장소 규칙(람다 금지)에 맞게 `assertThatThrownBy`의 람다를 익명 클래스로 바꿉니다.

---

## 답변

이어서 `searchInFiles`의 여러 키워드·앞뒤 줄 테스트를 기존 `FileToolSearchTest`에 추가합니다.

---

## 답변

결정 기록을 읽는 표현식은 정의 파일과 상관없이 확인할 수 있으니 단위 테스트를 먼저 만듭니다.

---

## 답변

엔진 단위 테스트 13개가 모두 통과했습니다. 다음은 3단계 Skill 작성입니다. 기술 Skill은 testApp의 실제 관례로 써야 하므로 testApp 구조를 확인합니다.

---

## 답변

testApp 관례를 확인했습니다. Skill 4개를 씁니다. 공통 Skill 3개(늘 미리 넣음)와 testApp 기술 Skill 1개(필요할 때 부름)입니다.

---

## 답변

Skill 4개를 만들었습니다. 이제 sm-workflow를 새 구조로 다시 씁니다. 디자이너 배치 파일 형식부터 확인합니다.

---

## 답변

이제 Agent를 다시 씁니다. 먼저 새로 만드는 범용 `document-revise-agent`입니다.

---

## 답변

다음은 sm 작성 Agent들입니다. 지시 읽는 법·저장 방법·조사 방법은 Skill로 빠졌으므로, 본문은 역할·입력·만들 것·이유를 붙인 제약만 남깁니다.

---

## 답변

Next the impact analysis agent:

---

## 답변

Next the architect agent (처음 설계만 맡도록):

---

## 답변

Next the developer agent:

---

## 답변

sm Agent 5개와 새 Agent 1개를 다시 썼습니다. 다음은 pilot 삭제와, 바뀐 정의에 맞춰 기존 sm 테스트를 고치는 일입니다.

---

## 답변

기존 sm 테스트들을 새 구조에 맞게 고쳐야 합니다. `WorkflowStepper`가 제공하는 기능을 확인합니다.

---

## 답변

sm 테스트를 고쳤습니다. 정의 파일 검증(기동 시)까지 포함해 LLM 없이 도는 전체 테스트를 돌립니다.

---

## 답변

The application context failed to start; let me find the boot error.

---

## 답변

평가가 도는 동안 문서를 고칩니다. 먼저 `docs/09`에서 pilot이 나오는 곳을 확인합니다.

---

## 답변

docs/09에서 pilot 설명 블록의 범위를 확인합니다.

---

## 답변

이어서 CLAUDE.md와 `.claude/docs`를 고칩니다. pilot 전용 문서는 지우고, 새 원칙 문서를 연결합니다.

---

## 답변

과거 기록 속 pilot 언급은 그대로 두고, 새 기능 설명을 `.claude/docs`에 덧붙입니다.

---

## 답변

평가가 끝나기를 기다리는 동안, 디자이너 화면이 Agent를 저장할 때 새 `skills.preload`를 지워 버리지 않는지 확인합니다.

---

## 답변

첫 회차에서 요구사항 작성이 끝났고(8초, 성공), 나머지 사례가 도는 중입니다. 완료 알림을 기다리겠습니다.

---

## 답변

아직 3분 정도밖에 지나지 않았고 정상적으로 돌고 있습니다(지금 설계 수정 사례를 실행 중). 완료 알림이 오면 결과를 정리하겠습니다.

---

## 답변

변경은 55개 파일입니다. 평가 완료 알림을 기다립니다.

---

## 답변

원격에서 새 커밋(`054a93c1`)이 들어와 병합되어 있습니다. 제가 고치는 파일과 겹치는지 확인합니다.

---

## 답변

평가 세트 세 회차가 아직 돌고 있습니다(사례마다 수 분). 끝나면 자동으로 이어서 결과를 비교하고 정리하겠습니다.

지금까지 끝난 작업은 다음과 같습니다.
- **원칙 문서**: `.claude/docs/ai-engine-agent-principles.md`를 쓰고 CLAUDE.md에 연결했습니다.
- **평가 세트**: `SmAgentEvalTest`(실제 LLM, 사례 4개)를 만들고 기준선을 쟀습니다.
- **엔진**: 다섯 가지를 넣었고, 단위 테스트와 LLM 없이 도는 전체 테스트가 통과했습니다(기존에도 실패하던 2건 제외).
  - 결정 기록 `${arrival}`/`${decisions}`
  - 재시도 때 지난 답 넘기기
  - 한도 끝은 꼭 해야 하는 Tool 몫으로 남기기
  - `skills.preload`
  - `searchInFiles`에 여러 키워드와 앞뒤 줄
- **Agent·Skill**: sm Agent 5개를 다시 썼고, 범용 `document-revise-agent`와 Skill 4개를 만들었습니다. sample Agent 3개를 정리했고 pilot은 삭제했습니다.
- **sm-workflow 2.0.0**: [~ 수정]과 [~ 다시 작성] 경로로 나눴습니다. 덮어쓰기로 망가져 있던 step16 컴파일 확인도 되살렸습니다.
- **문서**: docs/09와 `.claude/docs`를 갱신했습니다.
