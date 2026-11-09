# pasted_content_id129

> 생성일시: 2026-11-09 12:15

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

---

## 질문

<pasted_content id="1290">
dstone-knowledge 를 솔루션화 하려고 해.
먼저 해당 어플리케이션이 솔루션으로 제품화되려면 아래의 조건이 반드시 필요할 것 같아.
1. 어떤 어플리케이션에 적용하건 java 소스변경없이 적용 가능해야함.
2. 만일 어플리케이션마다 호출맵핑이 상이하다면 conf/rules/{프로젝트ID} 로 커버가 가능해야함.

이 조건을 dstone-knowledge이 만족시키고 있다고 생각해?
</pasted_content id="1290">

---

## 답변

규칙 파일 구조와, Java 코드에 직접 박혀 있는 프레임워크 관례가 얼마나 되는지 확인해 보겠습니다.

---

## 답변

hen에서 화면과 서버가 실제로 얼마나 이어졌는지 숫자로 확인해 보겠습니다.

---

## 답변

## 결론

두 조건 모두 **방향은 맞지만, 제품으로 내세울 만큼 완전하지는 않습니다.**

- **조건 1(Java 소스 변경 없이 적용):** 지원하는 기술 범위 안에서는 만족합니다. 범위를 벗어나는 기술을 만나면 Java를 고쳐야 합니다.
- **조건 2(호출 매핑은 `conf/rules/{프로젝트ID}`로 해결):** 장치는 있습니다. 다만 프로젝트 ID로 자동으로 묶이지 않고, 규칙에 적을 수 없는 관례가 남아 있습니다.

## 조건 1: Java 소스 변경 없이 적용

**지금 되는 것 (근거)**
- **hen:** 전용 규칙 파일 없이 분석했습니다. HTTP 진입점 844개, 화면→서버 요청 1,454건, 메소드→SQL 연결 2,477건이 나왔고, 진입점 844개 가운데 709개가 화면과 이어졌습니다.
- **기본 지원:** Spring, 서블릿/`web.xml`, Struts 1·2, MyBatis/iBATIS, JSP, WebSquare/Nexacro를 따로 설정하지 않아도 읽습니다.
- **프로젝트별 설정:** jar 위치(`classpath`)와 사전 CSV는 프로젝트를 등록할 때 넣습니다. 코드를 고칠 일이 아닙니다.

**Java를 고쳐야 하는 경우 (기술 범위 밖)**

| 영역 | 지금 코드에 고정된 것 | 벗어나면 |
|---|---|---|
| 화면 기술 | `.xfdl`/`.xadl` = Nexacro, WebSquare 네임스페이스가 든 `.xml` (`FileClassifier`, `RichClientPlugin`) | MiPlatform, Vue·React(`.vue`/`.ts`) 등은 화면으로 잡히지 않음 |
| SQL 매퍼 형식 | MyBatis, iBATIS, JEF 쿼리 XML (`MapperXmlReader`) | 다른 자체 쿼리 XML이나 쿼리를 properties에 두는 방식은 읽지 못함 |
| 진입점 설정 파일 | `web.xml`, `struts-config.xml`/`struts.xml` | 자체 라우팅 XML(거래 ID→빈·메소드 표)은 읽지 못함 |
| 언어 | Java, JSP | Kotlin, Groovy는 분석하지 않음 |

## 조건 2: 호출 매핑을 규칙 파일로 해결

**되는 것:** `conf/rules/*.yml`로 애노테이션, 상위 타입, "n번째 인자가 SQL 이름/화면 이름", 화면 함수의 거래 ID → 메소드(`perform{id}` 같은 틀)를 적을 수 있습니다. 서버를 다시 띄울 필요도 없습니다. 후보 추천(`/rules/candidates`)과 시험 적용(`/rules/trial`)까지 있어서 실무 흐름도 갖춰져 있습니다.

**조건과 어긋나는 것**

1. **폴더 이름이 프로젝트 ID와 자동으로 묶이지 않습니다.**
   - 폴더는 묶음 이름을 정할 뿐입니다. 어떤 프로젝트에 쓸지는 프로젝트의 `ruleSets`에 따로 적어야 합니다.
   - 게다가 `autoApply`의 기본값이 `true`여서, 하위 폴더 규칙도 **모든 프로젝트에 적용됩니다**. 실제로 `hi-japan/*.yml`이 지금 모든 프로젝트에 걸려 있습니다. 고객사가 여럿이 되면 A사의 규칙이 B사 분석에 끼어들 수 있습니다.
2. **규칙으로 적을 수 없는 매핑이 있습니다.**
   - ID를 메소드 이름으로 바꿀 때 틀에 끼워 넣는 것(`perform{id}`)만 됩니다. 대소문자를 바꾸거나 일부만 잘라 쓰는 변환은 안 됩니다.
   - ID→클래스·메소드 대응표 파일을 참조하는 방식도 적을 수 없습니다.
   - 주소를 상수로 적은 `@RequestMapping(Urls.X)`, 상위 클래스의 `@RequestMapping`, Struts DispatchAction의 파라미터로 메소드를 고르는 방식은 코드에서도 아직 다루지 않습니다.
   - 애노테이션을 단순 이름으로만 구분해서, 이름이 같은 다른 애노테이션을 가려낼 규칙이 없습니다.

## 제품화 전에 하면 좋은 것

1. **`conf/rules/{프로젝트ID}/` 자동 적용:** 프로젝트 ID와 이름이 같은 폴더는 `ruleSets`에 적지 않아도 그 프로젝트에만 쓰게 합니다. 하위 폴더 묶음의 기본값은 `autoApply: false`로 바꿉니다. 작은 변경으로 조건 2의 핵심이 해결됩니다.
2. **규칙 어휘 넓히기:** ID 변환(대소문자, 정규식 치환)과 대응표 파일 참조를 넣습니다. 진입점 정의 XML도 "XPath → 주소/클래스/메소드" 규칙으로 적을 수 있게 합니다.
3. **"지원 범위" 문서화:** 위 표를 제품 문서에 명시합니다. 그러면 "범위 안에서는 소스 변경 없음"이 약속으로 성립합니다.
4. **처음 적용할 때의 진단:** 오늘 만든 "풀지 못한 참조 살펴보기"에 더해, 진입점이 안 잡힌 화면과 SQL이 안 이어진 호출 같은 진단을 고객사 현장에서 볼 수 있게 합니다. 그러면 규칙만으로 해결할지, 개발이 필요한지 바로 판단할 수 있습니다.

원하시면 1번(프로젝트 ID 폴더 자동 적용)부터 진행하겠습니다. 기존 `hi-japan` 규칙을 쓰는 프로젝트와 표본(`websquare-app`)의 `ruleSets` 설정도 함께 맞춰야 합니다.

---

## 질문

모두 진행해줘. 그리고 올려줘.

---

## 답변

범위가 넓어서 먼저 규칙 로딩·적용 코드를 읽고 나서 순서대로 진행하겠습니다.

---

## 답변

코드 구조를 파악했습니다. A(규칙 선택)부터 고칩니다.

---

## 답변

A는 끝났습니다. B로 넘어가 ID 변환 클래스를 만듭니다.

---

## 답변

이제 `ScreenScript.CallRule`에 변환을 연결합니다.

---

## 답변

이제 `linkCalls`가 "클래스.메소드" 모양도 처리하도록 고칩니다.

---

## 답변

대응표 규칙은 대응표의 항목을 그대로 거래 진입점으로 올리도록 하겠습니다. `insertEndpoint` SQL의 JSON 처리 방식을 확인합니다.

---

## 답변

파서에 `idPattern`/`idReplace`/`idCase`/`idMap` 키를 추가합니다. 대응표 파일은 규칙 폴더 안에서만 읽습니다.

---

## 답변

검증 코드에 쓸데없는 줄이 섞여 들어가서 정리합니다.

---

## 답변

B까지 컴파일이 됩니다. 이어서 C(XML 진입점 규칙)를 만듭니다. `SemanticRules`가 묶음을 어떻게 합치는지 확인합니다.

---

## 답변

화면 규칙의 중복 판정 키에 변환 내용을 넣고, XML 진입점 규칙을 규칙 모델에 추가합니다.

---

## 답변

이제 규칙대로 설정 XML을 읽어 진입점을 만드는 `XmlEndpointPlugin`을 작성합니다. 필요한 DAO 메소드가 있는지 먼저 확인합니다.

---

## 답변

파싱 부분은 DB 없이 테스트할 수 있도록 `XmlEndpointReader`로 따로 떼어 만듭니다.

---

## 답변

C도 컴파일됩니다. 이제 D(적용 진단)입니다. 화면→서버 관계가 어떤 모양으로 저장되는지 먼저 확인합니다.

---

## 답변

진단용 SQL을 매퍼로 만듭니다.

---

## 답변

D까지 컴파일됩니다. 이제 테스트를 추가합니다. 기존 테스트가 실제 `conf/rules`를 어떻게 검사하는지 먼저 봅니다.

---

## 답변

기존 테스트에 이어 새 규칙(폴더 기본값, 프로젝트 폴더 자동 적용, ID 다듬기, 대응표, XML 진입점)의 테스트를 더합니다.

---

## 답변

백엔드는 끝났습니다. 이제 boot 화면의 리비전 요약에 "적용 진단" 상자를 넣습니다.

---

## 답변

화면 코드도 넣었습니다. 분석이 돌고 있지 않은지 확인하고, 두 앱을 내려 빌드한 뒤 다시 띄웁니다.

---

## 답변

정규식의 역슬래시가 한 번 더 들어갔습니다. 고쳐서 다시 시험합니다.

---

## 답변

XML 규칙으로 진입점 8개가 생겼습니다(id가 없는 bean 1개는 건너뜀). 이번에는 websquare-app에 대응표 규칙을 시험합니다.

---

## 답변

이제 `docs/11.dstone-knowledge.md`를 고칩니다. 4.7절의 끝 위치를 확인합니다.

---

## 답변

Now the docs/11 edits.

---

## 답변

문서의 "지원하지 않는 것" 표에 확인하지 않은 내용이 섞이면 안 되므로, JPA와 람다 관련 주장을 코드에서 확인합니다.

---

## 답변

문서까지 고쳤습니다. 커밋 전에 새로 쓴 Java에 람다나 메소드 참조가 없는지, 그리고 Lombok 사용이 없는지 확인합니다.

---

## 답변

람다·메소드 참조·Lombok 모두 없습니다. `docs/temp`를 빼고 커밋한 뒤 올립니다.

---

## 답변

제안했던 네 가지를 모두 구현해서 `8bc501bd` 커밋으로 `origin/main`에 올렸습니다. 4081(knowledge)과 7081(boot)도 새 빌드로 다시 띄워 두었습니다.

## 바뀐 것

**1. 프로젝트 폴더 규칙 자동 적용 (조건 2)**
- `conf/rules/{프로젝트ID}/` 아래 규칙은 이제 그 프로젝트에만 자동으로 적용됩니다. 폴더 이름과 프로젝트 ID는 대소문자를 구분하지 않습니다. 그래서 `hi-japan/` 폴더가 프로젝트 `HI-Japan`에 적용됩니다.
- 하위 폴더 규칙은 기본적으로 다른 프로젝트에 적용되지 않습니다. 지금까지는 hi-japan 규칙이 모든 프로젝트에 걸려 있었습니다.
- HI-Japan-batch와 표본 websquare-app은 같은 프레임워크를 쓰므로, 프로젝트 설정에 `ruleSets: hi-japan`을 적어 계속 쓰게 했습니다.

**2. 규칙으로 표현할 수 있는 범위 확장**
- **ID 변환:** 화면 규칙에 `idPattern`/`idReplace`(정규식 치환), `idCase`(대소문자 변환), `idMap`(대응표 파일)을 추가했습니다.
  - 대응표 값이 "클래스.메소드" 형태면 그 클래스의 메소드에만 연결합니다.
  - 대응표에 적힌 ID마다 거래 진입점을 만듭니다.
- **XML 진입점:** `endpoints.xml` 규칙을 추가했습니다. 자체 프레임워크가 설정 XML에 적어 둔 "주소(거래 ID) → 클래스·메소드"를 진입점으로 만듭니다.

**3. 지원 범위 문서화**
- `docs/11` 4.7.6절에 영역별로 세 가지를 표로 정리했습니다: 설정 없이 지원하는 것, 규칙 파일로 맞추는 것, 개발이 필요한 것.
- 규칙 README, `.claude/docs`, `CLAUDE.md`에도 반영했습니다.

**4. 적용 진단**
- `GET /api/revisions/{id}/diagnostics` API와, 리비전 요약 화면의 "적용 진단" 상자를 추가했습니다.
- 항목마다 해결 방법이 붙습니다: 규칙 파일 / jar 위치 / 프로젝트 설정 / 소스 보충 / 분석기 개발 필요.
- 예: hen에서는 매퍼에 없는 SQL 45건(소스 보충), `.py` 파일 1개(개발 필요), 서버를 부르지 않는 화면 335/742개(규칙 후보)가 나옵니다.

## 검증

- **테스트:** 전체 단위 테스트가 통과합니다. 새 규칙 문법, ID 변환, XML 진입점 읽기 테스트를 추가했습니다.
- **기존 결과 유지:** HI-Japan, HI-Japan-batch, websquare-app, hen의 리비전을 바뀐 규칙 선택 방식으로 시험 적용해 봤습니다. 네 개 모두 결과가 그대로였습니다(추가 0건, 삭제 0건).
- **새 기능 시험:** 실제 데이터로 시험 적용했습니다(DB에는 남지 않음).
  - 대응표: websquare-app에서 맞는 ID는 그대로 연결됐고, 없는 클래스를 적은 ID는 UNRESOLVED로 남았고, 대응표에 없는 ID는 연결되지 않았습니다.
  - XML 진입점: hen의 Spring XML에서 진입점 8개가 생겼습니다.
- **확인하지 못한 것:** "적용 진단" 화면은 로그인이 필요해서 브라우저로 확인하지 못했습니다. API 응답과 JS 문법만 확인했습니다. 7081에서 리비전 요약을 한 번 열어 봐 주세요.

## 아직 남은 것 (개발 필요로 분류)

다음은 규칙으로는 해결할 수 없어 지원 범위 문서에 "개발 필요"로 적어 두었습니다.
- 상수로 적은 `@RequestMapping` 주소, 상위 클래스에 붙은 `@RequestMapping`
- Struts DispatchAction
- JPA 쿼리
- Kotlin, Vue/React 등 지원하지 않는 언어와 화면 기술

이 가운데 우선순위를 정해 주시면 이어서 진행하겠습니다.
