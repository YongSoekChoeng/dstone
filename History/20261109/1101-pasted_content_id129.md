# pasted_content_id129

> 생성일시: 2026-11-09 11:01

## 질문

<pasted_content id="1290">
dstone-boot> knowledge > 프로젝트 · 분석 의 hen 프로젝트 에서
분석결과가 아래와 같이 나왔어.
"!메소드 호출 44,654건 가운데 94.59% 가 누구를 부르는지 풀렸습니다. 쓸 수는 있지만 빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
그런데 분석률을 높이려면 어떤 부분에서 접근해야 하는지 명확하지가 않은 것 같아.
예를들어 "풀지 못한 참조의 이유"를 추적하고자 할 때 어떤 소스에서 어떤 호출이 안풀렸다던가 하는 내용들이 없어.
적당한 범위에서 상세 목록을 참고할 수 있는 방법이 없을까?
</pasted_content id="1290">

---

## 답변

먼저 해결률을 어떻게 계산하는지, 풀리지 않은 호출이 DB에 어떤 형태로 남는지 확인하겠습니다.

---

## 답변

이미 "못 푼 이유"를 묶어 세는 쿼리가 있네요. 테이블 구조와 API·화면 노출 상태를 보겠습니다.

---

## 답변

현재 요약 화면에는 이유별 건수와 예시 한 건만 나옵니다. 실제 hen 데이터에서 어떤 이유가 많은지 DB로 확인해 보겠습니다.

---

## 답변

해석기 오류 뒤에 "어떤 기호를 못 찾았는지"가 들어 있습니다. 이걸로 묶으면 원인이 바로 보일 것 같습니다.

---

## 답변

## hen 프로젝트 분석 결과(리비전 85)

**원인은 대부분 jar가 없어서였습니다.** hen 프로젝트에는 클래스패스가 등록돼 있지 않습니다. 그래서 `WEB-INF/lib`에 있는 jar 26개만 자동으로 읽혔습니다. Spring, slf4j, HttpClient, jjwt처럼 `pom.xml`로 받는 라이브러리는 그 폴더에 없어서 분석기가 그 타입들을 알지 못했습니다.

못 찾은 기호를 많은 순서로 묶어 보면 이렇습니다.

| 못 찾은 기호 | 건수 | 필요한 jar |
|---|---|---|
| `Model` (`new ModelAndView(...).addObject`) | 378 | spring-webmvc / spring-context |
| `logger` | 168 | slf4j / log4j |
| `HttpEntity`, `GET`/`POST`, `HttpStatus` | 약 130 | spring-web |
| `StringEntity`, `UrlEncodedFormEntity` | 8 | httpclient |
| `Jwts.parserBuilder()` 등 | - | jjwt |

못 푼 호출이 몰린 파일은 `common/integration/*SendBiz.java`(외부 연계 쪽, RestTemplate 사용)였습니다.

**바로 해 볼 수 있는 조치:** `/app/hen`에서 `mvn dependency:build-classpath`를 실행하고, 그 출력을 프로젝트 등록 화면의 라이브러리 칸에 그대로 넣은 다음 다시 분석하면 됩니다. 위 표 기준으로 못 푼 호출 996건 가운데 700건 이상은 풀릴 것으로 보입니다.

덧붙여, 화면의 94.59%에는 짐작으로 이은 호출(HEURISTIC) 1,419건이 빠져 있습니다. 전체는 이렇게 나뉩니다.

- EXTERNAL: 37,761건
- RESOLVED: 4,478건
- HEURISTIC: 1,419건
- UNRESOLVED: 996건

## 상세 목록을 볼 방법

**지금 화면에 없는 것:** 요약의 "풀지 못한 참조의 이유"는 이유의 첫 문장으로 묶은 건수와 예시 한 건만 보여 줍니다. 그런데 925건이 "프로젝트에 같은 이름의 메소드가 없습니다"라는 한 묶음으로 뭉쳐서, 무엇이 문제인지 알 수 없습니다. 실제 단서는 그 뒤 `해석기 오류: Unsolved symbol : Model` 부분에 있습니다.

**쓸 수 있는 데이터:** 파일, 줄, 호출식, 이유가 모두 `analysis_reference`에 건별로 이미 저장돼 있습니다. 그래서 새로 분석할 필요 없이 조회와 화면만 추가하면 됩니다.

### 지금 바로 SQL로 보기

```sql
-- 못 찾은 기호별 건수 (어떤 jar가 빠졌는지)
SELECT substring(fail_reason FROM 'Unsolved symbol[^:]*: ([^ ]+)') AS sym, count(*)
  FROM analysis_reference
 WHERE revision_id = 85 AND ref_kind = 'CALL' AND status = 'UNRESOLVED'
 GROUP BY 1 ORDER BY 2 DESC;

-- 건별 상세 (파일·줄·호출식)
SELECT f.path, r.line_start, r.scope_text || '.' || r.name AS call, r.fail_reason
  FROM analysis_reference r JOIN analysis_file f ON f.file_id = r.file_id
 WHERE r.revision_id = 85 AND r.ref_kind = 'CALL' AND r.status = 'UNRESOLVED'
 ORDER BY f.path, r.line_start;
```

### 화면 기능으로 추가하는 안

1. **dstone-knowledge API**: `GET /api/revisions/{id}/unresolved`를 만듭니다.
   - 세 가지 기준으로 묶어 셉니다: 못 찾은 기호별, 파일별, 이유별.
   - 건별 목록은 묶음·파일·상태(UNRESOLVED/HEURISTIC)로 걸러 쪽 단위로 보여 줍니다.
2. **dstone-boot 화면**: 요약의 "풀지 못한 참조의 이유" 표에 상세 버튼을 붙입니다.
   - 왼쪽에는 기호별·파일별 상위 목록을 둡니다.
   - 하나를 누르면 오른쪽에 `파일:줄`, 호출식, 이유가 나옵니다.
   - 진단 문구도 "jar가 없어 보입니다: Model, HttpEntity…"처럼 구체적으로 바꿉니다.

분석 단계(Pass)는 손대지 않으므로 다시 분석할 필요가 없습니다.

이 안대로 구현할까요? 아니면 먼저 classpath를 넣고 다시 분석해서 얼마나 오르는지부터 확인할까요?

---

## 질문

먼저 기능을 추가해주고 재분석 진행하자.

---

## 답변

먼저 API 쪽 구조(컨트롤러·서비스·DAO)와 boot 화면의 프록시·요약 렌더링을 확인하겠습니다.

---

## 답변

이제 boot 쪽 프록시 방식과 요약 섹션 렌더링을 봅니다.

---

## 답변

API부터 만들겠습니다. 구현 전에 서비스 작성 규칙(검증 예외, 페이징)을 한 번 더 확인합니다.

---

## 답변

SQL을 먼저 psql에서 시험해 봅니다.

---

## 답변

빠르게 동작합니다. 매퍼에 쿼리를 추가합니다.

---

## 답변

API 코드를 넣었습니다. 빌드하기 전에 4081 인스턴스가 어떤 jar로 떠 있는지 확인합니다.

---

## 답변

이제 boot 화면입니다. CSS와 화면 구조를 확인합니다.

---

## 답변

Export에 REF_KIND/REFERENCE_STATUS를 추가하고, 화면 패널·스크립트·CSS를 작성합니다.

---

## 답변

Now the JSP panel.

---

## 답변

화면 동작은 별도 스크립트 `unresolved.js`로 만듭니다.

---

## 답변

프록시는 빈 값을 보내지 않아서 "모든 종류"가 그대로 동작합니다. 스크립트를 등록하고 요약 화면에 버튼을 연결합니다.

---

## 답변

코드는 다 넣었습니다. 이제 knowledge·boot를 내리고 빌드한 다음 다시 띄웁니다. 스크립트의 기본 프로파일을 먼저 확인합니다.

---

## 답변

분석 Job이 돌고 있지 않은지 확인한 뒤 내립니다.

---

## 답변

Knowledge가 올라왔습니다. 새 API를 확인합니다.

---

## 답변

API는 정상입니다. boot 프록시를 거쳐서도 되는지 확인합니다.

---

## 답변

원인 값이 예상과 다르게 나왔습니다. 실제로 어떤 값인지 봅니다.

---

## 답변

고친 코드를 다시 빌드하기 전에 hen의 클래스패스부터 뽑아 둡니다(재시작을 한 번으로 끝내려고요).

---

## 답변

오프라인 저장소에 없는 것이 있어서 온라인으로 다시 받아 봅니다.

---

## 답변

jar 137개의 클래스패스를 얻었습니다. 이제 수정한 코드를 다시 빌드하고 재시작합니다.

---

## 답변

분석을 시작했습니다(리비전 #96). 끝날 때까지 기다립니다.

---

## 답변

분석이 도는 동안 문서를 고칩니다. API 목록과 화면 설명이 있는 곳을 찾습니다.

---

## 답변

Docs edits (API table, screen table, guide).

---

## 답변

문서를 고쳤습니다. 분석이 끝나기를 기다립니다.

---

## 답변

그동안 guide 문구가 들어가도록 boot만 다시 빌드해 띄웁니다(분석 중인 knowledge는 그대로 둡니다).

---

## 답변

재분석(리비전 #96)이 끝나면 결과를 비교해 알려 드리겠습니다.

---

## 질문

<task-notification>
<task-id>b1t4dwdsx</task-id>
<summary>Monitor event: "hen 재분석(A20261109001) 단계 진행"</summary>
<event>RUNNING | SCAN:DONE CARRY:DONE DECLARE:DONE RESOURCE:RUNNING</event>
</task-notification>

---

## 답변

RESOURCE 단계를 진행하고 있습니다. 계속 기다립니다.

---

## 질문

<task-notification>
<task-id>b1t4dwdsx</task-id>
<summary>Monitor event: "hen 재분석(A20261109001) 단계 진행"</summary>
<event>RUNNING | SCAN:DONE CARRY:DONE DECLARE:DONE RESOURCE:DONE CARRY_RESOLVE:DONE RESOLVE:RUNNING</event>
</task-notification>

---

## 답변

RESOLVE 단계(호출 해석)에 들어갔습니다.

---

## 질문

<task-notification>
<task-id>b1t4dwdsx</task-id>
<summary>Monitor event: "hen 재분석(A20261109001) 단계 진행"</summary>
<event>RUNNING | SCAN:DONE CARRY:DONE DECLARE:DONE RESOURCE:DONE CARRY_RESOLVE:DONE RESOLVE:DONE LINK:DONE SEMANTIC:RUNNING</event>
</task-notification>

---

## 답변

RESOLVE까지 끝났으니 호출 해석 결과는 이미 나왔습니다. 먼저 비교해 봅니다.

---

## 답변

못 푼 호출이 996건에서 170건으로 줄었습니다. 남은 원인 중 상위 몇 개가 무엇인지 확인합니다.

---

## 답변

남은 원인도 확인했습니다. 분석 마지막 단계(SEMANTIC 이후)가 끝나면 정리해서 알려 드리겠습니다.

---

## 질문

<task-notification>
<task-id>b1t4dwdsx</task-id>
<summary>Monitor event: "hen 재분석(A20261109001) 단계 진행"</summary>
<event>RUNNING | SCAN:DONE CARRY:DONE DECLARE:DONE RESOURCE:DONE CARRY_RESOLVE:DONE RESOLVE:DONE LINK:DONE SEMANTIC:DONE DOCUMENT:RUNNING</event>
</task-notification>

---

## 답변

DOCUMENT 단계(RAG 청크 생성)만 남았습니다.

---

## 질문

<task-notification>
<task-id>b1t4dwdsx</task-id>
<tool-use-id>toolu_019ofSQmWJWWsJjA5SLeWLNb</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/462c9bc1-5647-4dd7-a695-4837189aa8b3/tasks/b1t4dwdsx.output</output-file>
<status>completed</status>
<summary>Monitor "hen 재분석(A20261109001) 단계 진행" stream ended</summary>
<event>DONE | SCAN:DONE CARRY:DONE DECLARE:DONE RESOURCE:DONE CARRY_RESOLVE:DONE RESOLVE:DONE LINK:DONE SEMANTIC:DONE DOCUMENT:DONE</event>
</task-notification>

---

## 답변

기능을 추가했고, hen 재분석까지 마쳤습니다. 호출 해석률이 **94.59%에서 98.19%**로 올라 화면 판정이 "!"에서 "✔"로 바뀝니다.

## 재분석 결과 (리비전 #85 → #96)

| 지표 | 이전 | 이후 |
|---|---|---|
| 호출 해석률 (`callResolutionRate`) | 94.59% | **98.19%** |
| 못 푼 호출 (UNRESOLVED) | 996건 | **170건** |
| 짐작으로 이은 호출 (HEURISTIC) | 1,419건 | **640건** |

hen은 Maven으로 받는 jar(Spring, slf4j, HttpClient, jjwt 등)가 등록돼 있지 않았습니다. 그래서 `mvn dependency:build-classpath`로 뽑은 jar 137개를 프로젝트의 라이브러리 칸에 넣고, 증분이 아닌 전체 분석을 돌렸습니다.

- **pom.xml 문제:** 원래 `pom.xml` 그대로는 실패했습니다. 투비소프트 저장소가 http라서 Maven이 막았기 때문입니다. 그 저장소에서 받는 nexacro·NiceID·fontbox는 이미 `WEB-INF/lib`에 있어 자동으로 잡힙니다. 그래서 `/app/hen`은 손대지 않고, 스크래치 폴더의 pom 사본에서 그 의존성만 빼고 뽑았습니다.
- **남은 170건:** 대부분 jar가 아니라 **소스 자체가 빠진 경우**입니다. `com.frameq.common.util.StringUtil`, `com.frameq.system.vo.SessionVO`, `com.frameq.system.exception.BizException`처럼 import는 하는데 `/app/hen` 소스에도 jar에도 없습니다. 공통 프레임워크 모듈의 소스나 jar를 구해 넣으면 더 올라갑니다. 나머지는 `oz_scheduler_export.jsp` 같은 JSP 안의 스크립틀릿입니다.

## 추가한 기능

**API (dstone-knowledge)**
- `GET /api/revisions/{id}/unresolved/groups`: 원인·파일·부르려던 이름별로 묶어 셉니다.
- `GET /api/revisions/{id}/unresolved`: 한 건씩 보여 줍니다. 파일:줄, 호출식, 그 호출이 들어 있는 메소드, 원인, 이유가 나오고 쪽 단위로 넘깁니다.
- **원인 추출:** 해석기 메시지 `Unsolved symbol … : Model`에서 마지막 기호를 꺼내 원인으로 씁니다. 예전 요약은 이유의 첫 문장으로 묶어서 925건이 한 줄로 뭉쳤는데, 이제는 `Model` 378건, `logger` 168건처럼 나뉩니다.

**화면 (dstone-boot, 프로젝트 · 분석)**
- 리비전 요약에 "풀지 못한 참조 살펴보기" 단추와 "짐작으로 이은 참조 살펴보기" 단추를 넣었습니다.
- 단추를 누르면 원인·파일·이름별 표가 나옵니다. 표의 줄을 누르면 그 조건으로 좁혀지고, 아래 목록에 해당 파일과 줄이 나옵니다. 걸린 조건은 단추로 표시되고, 누르면 풀립니다.
- 원인으로 거를 때는 원인 글자 대신 md5 키를 보냅니다. 원인에 `+`가 들어 있으면 프록시를 지나면서 빈칸으로 바뀌기 때문입니다.

문서는 `docs/11.dstone-knowledge.md`(API 표, 화면 표), `.claude/docs/knowledge-ui.md`, 사용 안내 화면(`guide.jsp`)을 고쳤습니다.

## 확인한 것과 못 한 것

- **API:** 4081에서 직접 확인했습니다. 묶음, 목록, 잘못된 status를 넣었을 때의 400, `+`가 든 원인 거르기 모두 정상입니다.
- **화면:** 로그인이 필요해서 브라우저로는 확인하지 못했습니다. JS 문법 검사만 통과했습니다. 7081에서 리비전 #96의 요약을 열고 단추가 제대로 동작하는지 한 번 봐 주세요.
- **재시작:** 이 작업을 하면서 4081(knowledge)과 7081(boot)을 새 빌드로 다시 띄웠습니다.
- **커밋:** 아직 하지 않았습니다. 원하시면 커밋하겠습니다.

---

## 질문

올려줘.

---

## 답변

변경 내용을 `52c2a0e8` 커밋으로 `origin/main`에 올렸습니다.

- **포함한 파일:** 이번 작업에서 고친 12개(dstone-knowledge의 API, dstone-boot 화면, 문서)입니다.
- **뺀 파일:** `docs/temp/knowledge분석프로세스.md`는 원래 git에서 다루지 않는 파일이라 커밋하지 않았습니다. 지금도 수정된 상태로 남아 있습니다.

hen 프로젝트에 넣은 라이브러리 경로는 저장소 파일이 아니라 dstone-knowledge DB에 저장돼 있어서 커밋 대상이 아닙니다.
