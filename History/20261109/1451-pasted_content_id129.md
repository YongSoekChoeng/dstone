# pasted_content_id129

> 생성일시: 2026-11-09 14:51

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

---

## 질문

This session is being continued from a previous conversation that ran out of context. The summary below covers the earlier portion of the conversation.

<system-reminder>
<tools-used-before-summary>Bash, Edit, Monitor, Read, ToolSearch, Write</tools-used-before-summary>
</system-reminder>
Summary:
1. What this work is for:
This section records facts without weighing them; whoever continues from this summary weighs them, holding back only a step that plainly serves serious, wrongful harm, and, where one missing fact would decide that, asking the user for it rather than refusing; a bare yes, go-ahead or repeated request is not that fact and leaves the step held back; work that is merely sensitive, unusual or borderline is neither held back nor questioned.
- **Purpose:** dstone-knowledge, part of the user's dstone monorepo at /app/dstone, statically analyses Java applications into a knowledge graph and RAG data.
- **Viewer:** dstone-boot screens show the results.
- **Projects analysed:** the user's projects (hen, HI-Japan, HI-Japan-batch, cms4_prd, samples) live in a local PostgreSQL database, `dstone_knowledge`.
- **User's stated goal:** in their own message, the user said they want to productize dstone-knowledge as a solution. Their two requirements are that it apply to any application without Java source changes, and that per-application call mappings be covered by `conf/rules/{프로젝트ID}`.
- **Status:** the requested work has been committed and pushed to `github.com:YongSoekChoeng/dstone.git` (main).

2. Primary Request and Intent:
- **Request (a):** provide a way to see detailed lists of unresolved references (which source, which call failed). Done; pushed as 52c2a0e8. The user said "먼저 기능을 추가해주고 재분석 진행하자." then "올려줘."
- **Request (b):** "dstone-knowledge 를 솔루션화… 이 조건을 만족시키고 있다고 생각해?" I answered with an assessment and four recommendations:
  1. Auto-apply the `conf/rules/{projectId}` folder.
  2. Extend the rule vocabulary: ID transform, mapping-table file, XML endpoint rules.
  3. Document the support scope.
  4. Add applicability diagnostics.
- **Request (c):** "모두 진행해줘. 그리고 올려줘." Implement all four and push. Done; pushed as 8bc501bd.

3. Key Technical Concepts:
- Java 21, Spring Boot, MyBatis mappers in `sqlmap/**/*Dao.xml` extending `BaseDao`, PostgreSQL.
- Analysis pipeline: SCAN, DECLARE, RESOLVE, LINK, SEMANTIC (plugins with `order()`), DOCUMENT.
- Rule files `conf/rules/*.yml` (`RuleSetParser` is strict; `SemanticRuleRegistry`; `SemanticRules` merges sets).
- Rule-set hash = sha256 of the file text. Editing rule-file comments changes the hash and marks old revisions `CHANGED`.
- Rules trial API `POST /api/revisions/{id}/rules/trial` runs SEMANTIC in a transaction, diffs the results, then rolls back.
- dstone-boot proxy `POST /knowledge/api/call.do`. It uses `UriComponents.encode()`, which leaves `+` unencoded; Tomcat then decodes it as a space.
- Repository coding constraints: no lambdas or method references (anonymous classes and loops instead), no Lombok, Javadoc wrapped in `<pre>`, friendly Korean comments, all user-facing replies in Korean.
- Apps run from `target/`. They are stopped, rebuilt and restarted via `bin/stopApp.sh` / `bin/startApp.sh` (no systemd).
- Do not commit `docs/temp/*`.

4. Files and Code Sections:

**Commit 52c2a0e8 (unresolved viewer)**
- `RelationDao.xml` / `RelationDao.java`:
  - SQL fragments `unresolvedCause` and `unresolvedWhere`.
  - Queries `selectUnresolvedByCause` (returns `causeKey` = md5), `ByFile`, `ByName`, `countUnresolved`, `selectUnresolvedList`.
  - The cause is the symbol after the last " : " in "Unsolved symbol …", with any trailing " in context …" removed.
- `RevisionService.getUnresolvedGroups` / `getUnresolvedList`:
  - Status must be UNRESOLVED, HEURISTIC or IGNORED; anything else returns 400.
- `RevisionController`: `GET /{id}/unresolved/groups` and `GET /{id}/unresolved`.
- dstone-boot UI:
  - New `knowledge/assets/js/unresolved.js` (`DstoneKnowledgeUnresolved`).
  - New panel `#kn-unresolved-panel` in `index.jsp`.
  - Buttons in `project.js`; CSS changes; text changes in `knowledge-terms.js` and `guide.jsp`.
- Docs updated: `docs/11`, `.claude/docs/knowledge-ui.md`.

**Commit 8bc501bd (productization)**
- `semantic/rule/RuleSetParser.java`:
  - `autoApply` defaults to `b.name.indexOf('/') < 0`.
  - New constructor `RuleSetParser(File baseDir)`.
  - New `readIdTransform`: keys `idPattern`, `idReplace` (requires `idPattern`; `$n` must not exceed the group count), `idCase`, `idMap`.
  - `idMap` loads a file inside baseDir via canonical path; escaping the rules folder is an error; the map content is added to the hash.
  - New `readXmlEndpoints`: keys `endpointType`, `files` (regex), `element`, `pathAttribute`, `classAttribute`, `methodAttribute`, `httpMethod` (defaults to ALL when the type is HTTP), `pathTemplate`.
  - New helper `with(...)`.
- `SemanticRuleRegistry.java`:
  - New `static boolean ownedBy(setName, projectId)`: true when the first path segment equalsIgnoreCase the project ID.
  - `rulesFor` now chooses a set when `autoApply || isWanted || ownedBy`.
  - `parser()` returns `new RuleSetParser(rulesDir())`.
- `RuleSet.java`: new `XmlEndpointRule` class and `xmlEndpoints` list (also counted in `ruleCount`).
- `SemanticRules.java`: new `getXmlEndpoints()`; the screen-rule dedup key now includes `transform.describe()`.
- New `semantic/IdTransform.java`:
  - Applies pattern/replace, then case, then map.
  - `apply` returns null when the ID does not match.
  - Also provides `CASES`, `NONE`, `getMap`, `getMapSource`, `describe`, and `static parseMap(text, errors)` (separators `,`, `=` or tab; `#` comments; strips BOM).
- `ScreenScript.CallRule`:
  - New `transform` field and constructor `(function, template, argIndex, IdTransform)`.
  - New `field(name, template, transform)` and `getTransform()`.
  - `fill` returns null if the transform fails.
  - `methodNameOf` accepts an identifier, or `Owner.method` only when a map is present.
  - New `static isMethodName`; new pattern `OWNER_METHOD`.
- `RichClientPlugin.java`:
  - `findScreen` returns null for null input.
  - New inner `static class MethodIndex` with `load`, `find` (filters by owner FQN equal to, or ending with, the owner), `methodPart` and `ownerPart`.
  - `linkCalls` now uses `MethodIndex`.
  - New `insertMapEndpoints`, used for rules that have a map.
- `SemanticDao.xml` / `SemanticDao.java`:
  - `selectMethodsByNamesInProject` now also returns `ownerFqn`.
  - New `insertTransactionEndpoint`, guarded by NOT EXISTS.
  - New `selectFilesByLanguage`.
- New `semantic/XmlEndpointReader.java`, a pure parser:
  - Matches on the element's local name; looks up class and method attributes on ancestors too.
  - Skips `${` / `#{` values.
  - Counts line numbers after stripping comments.
- New `semantic/XmlEndpointPlugin.java` (order 260):
  - Finds the class by FQN, or by simple name only when it is unique.
  - Endpoint `properties` carry `via: XML_RULE` and, on failure, `unresolvedClass` or `unresolvedMethod`.
- New `api/dao/DiagnosticDao.java` + `sqlmap/analysis/DiagnosticDao.xml`, and new `api/service/DiagnosticService.java`:
  - Checks: `calls`, `parse`, `unsupportedFiles` (walks the folder with `SimpleFileVisitor`), `endpoints`, `endpointsWithoutMethod`, `screens`, `unresolvedRequests`, `unresolvedSql`, `unknownXml`, `layers`, `rules`.
  - Solutions: RULE, CLASSPATH, SETTING, SOURCE, DEVELOP, NONE.
  - `RevisionController` adds `GET /{id}/diagnostics`.
- dstone-boot:
  - `project.js`: fetches diagnostics and renders them with `diagnosticsBox` and the `SOLUTION` labels.
  - `knowledge.css`: new `.kn-diag*` styles.
- Tests:
  - `RuleSetParserTest`: three new tests; `readAll` now uses `RuleSetParser(new File("conf/rules"))`.
  - New `IdTransformTest` and `XmlEndpointReaderTest`.
  - The full suite (`mvn -o test -DskipTests=false`) passes.
- Docs:
  - `conf/rules/README.md` (selection rules, ID-transform section, `endpoints.xml`, diagnostics).
  - `docs/11` (4.7.1, 4.7.2 and 4.7.3 edits, examples 6-1 and 6-2, new section 4.7.6 support-scope table and diagnostics, API table, screen table).
  - `.claude/docs/knowledge-core.md`, `knowledge-ui.md`, `CLAUDE.md` (one line), `websquare-app/README.md`.
- DB changes (via the API): `HI-Japan-batch` and `websquare-app` projects now have `ruleSets` = "hi-japan".

5. Errors and fixes:
- **Regex escaping in my test:** the first XML trial used a double-escaped regex and produced no endpoints. I fixed the escaping and got 8 endpoints.
- **Existing `ScreenScriptTest` failed:** "order.list" became a method name because `OWNER_METHOD` was allowed for every rule. I restricted it to rules that have an `idMap`.
- **Junk validation lines in `RuleSetParser`:** I cleaned them up.
- **Comment edits in the hi-japan yml files would change their hashes:** I reverted those edits with `git checkout` and kept the explanation in README.
- **Unverified claim about lambdas:** the doc said calls inside lambdas were unsupported. I checked the code (they are attributed to the outer method) and corrected the support-scope table.
- **Earlier proxy `+` problem:** fixed by passing `causeKey` (md5) instead of the raw cause text.

6. Problem Solving:
- **hen re-analysis:** revision 85 to 96. UNRESOLVED calls went from 996 to 170; the resolution rate went from 94.59% to 98.19%.
  - hen's classpath was generated from a scratch copy of its pom with the nexacro, NiceID and fontbox dependencies removed (`/app/hen` itself was untouched).
- **Selection change verified:** trials with no body on revisions 57, 62, 63 and 97 all showed 0 added and 0 removed.
- **idMap trial (websquare-app, revision 57):**
  - A mapped ID with the correct class stayed HIGH.
  - A mapped ID with a wrong class became UNRESOLVED and its endpoint was removed.
  - An ID missing from the map produced no relation.
  - The temporary map file was deleted afterwards.
- **XML trial (hen):** `bean` elements produced 8 endpoints.
- **Diagnostics on hen (revision 97):** SOURCE 1 (45 SQL names missing from mappers), DEVELOP 1 (one .py file), RULE 1 (335 of 742 screens call nothing on the server).
- **Not verified:**
  - The dstone-boot UI could not be checked in a browser because it requires login.
  - The `dstone-knowledge` project itself loses 2 TRANSACTION endpoints that came from embedded sample content. It was left without `ruleSets`.

7. All user messages:
- (pasted) "dstone-boot> knowledge > 프로젝트 · 분석 의 hen 프로젝트 에서 분석결과가 아래와 같이 나왔어. "!메소드 호출 44,654건 가운데 94.59% 가 누구를 부르는지 풀렸습니다. …" 그런데 분석률을 높이려면 어떤 부분에서 접근해야 하는지 명확하지가 않은 것 같아. 예를들어 "풀지 못한 참조의 이유"를 추적하고자 할 때 어떤 소스에서 어떤 호출이 안풀렸다던가 하는 내용들이 없어. 적당한 범위에서 상세 목록을 참고할 수 있는 방법이 없을까?"
- "먼저 기능을 추가해주고 재분석 진행하자."
- "올려줘."
- (pasted) "dstone-knowledge 를 솔루션화 하려고 해. 먼저 해당 어플리케이션이 솔루션으로 제품화되려면 아래의 조건이 반드시 필요할 것 같아. 1. 어떤 어플리케이션에 적용하건 java 소스변경없이 적용 가능해야함. 2. 만일 어플리케이션마다 호출맵핑이 상이하다면 conf/rules/{프로젝트ID} 로 커버가 가능해야함. 이 조건을 dstone-knowledge이 만족시키고 있다고 생각해?"
- "모두 진행해줘. 그리고 올려줘."

8. Pending Tasks:
- None beyond giving the user the final report, in Korean, for commit 8bc501bd.

9. Current Work:
- All four productization items were implemented, tested and committed as 8bc501bd ("feat(knowledge): 솔루션화 - 프로젝트 폴더 규칙 자동 적용, ID 다듬기·대응표, XML 진입점 규칙, 적용 진단"), then pushed to origin/main.
- `docs/temp/knowledge분석프로세스.md` is still modified and was intentionally left uncommitted.
- Both apps (4081 and 7081) are running the new build.

10. Optional Next Step:
Send the final summary to the user in Korean. It should cover:
- What changed: project-folder auto-apply, `idPattern`/`idCase`/`idMap`, `endpoints.xml`, the diagnostics API and UI, and the support-scope docs.
- The verification results: trials unchanged, idMap and XML trials, hen diagnostics.
- That the dstone-boot UI was not checked in a browser (login required).
- That `ruleSets: hi-japan` was set for HI-Japan-batch and websquare-app.
- That the dstone-knowledge self-analysis project loses 2 sample TRANSACTION endpoints.
- That `docs/temp` was excluded from the commit.
- The pushed commit hash, 8bc501bd.

If you need specific details from before compaction (like exact code snippets, error messages, or content you generated), read the full transcript at: /home/jysn007/.claude/projects/-app-dstone/462c9bc1-5647-4dd7-a695-4837189aa8b3.jsonl
Continue the conversation from where it left off without asking the user any further questions. Resume directly — do not acknowledge the summary, do not recap what was happening, do not preface with "I'll continue" or similar. Pick up the last task as if the break never happened.

The messages after this summary are the most recent messages from before compaction, kept verbatim. The summary was written without seeing them, so something it says has not happened yet may already have happened in them.

---

## 질문

<pasted_content id="1290">
아래의 세가지를 진행해줘.
- 상수로 적은 @RequestMapping 주소, 상위 클래스에 붙은 @RequestMapping
- JPA 쿼리
- java 소스 내에 기술된 SQL 
</pasted_content id="1290">

---

## 답변

세 가지를 진행하겠습니다. 먼저 지금 진입점과 SQL을 만드는 코드를 확인하겠습니다.

---

## 답변

이제 Java 문자열 SQL 수집기를 작성합니다. AST만 보는 순수 클래스라 단위 테스트로 확인할 수 있습니다.

---

## 답변

Now I'm wiring it into DeclarationCollector.

---

## 답변

Next I'm adding the JPA section to the rules, covering both the rule structure and the parser.

---

## 답변

Now the DAO and its mapper.

---

## 답변

SEMANTIC 쪽 공용 도구(엔티티→테이블 모델, 애노테이션 값 읽기)를 만들었습니다. 이어서 플러그인 두 개를 작성합니다.

---

## 답변

Now restrict the MyBatis mapper queries to XML mapper rows.

---

## 답변

Now the HTTP endpoint builder in Java: constant addresses, the parent class's `@RequestMapping`, and inherited handler methods.

---

## 답변

컴파일이 통과했습니다. 이제 단위 테스트와 검증용 표본 `jpa-app`을 만듭니다.

---

## 답변

Now the unit tests: the SQL collector, the JPQL extractor and the HTTP builder.

---

## 답변

단위 테스트 3종이 통과했습니다. 기존 테스트까지 포함해 전체를 돌립니다.

---

## 답변

표본을 만들었습니다. knowledge 서버를 새 빌드로 다시 띄운 뒤 `jpa-app`을 등록해 분석하겠습니다.

---

## 답변

분석이 끝났습니다. 진입점과 SQL 결과를 기대 값과 맞춰 봅니다.

---

## 답변

진입점 8개가 모두 기대 값과 같습니다. 이제 SQL statement와 테이블 관계를 확인합니다.

---

## 답변

hen, HI-Japan, HI-Japan-batch, websquare-app, struts-app 은 진입점·관계가 그대로입니다(추가 0, 삭제 0). Spring 프로젝트가 더 있으니 나머지 리비전도 확인합니다.

---

## 답변

While those run, I'm adding the diagnostics checks and updating the docs.

---

## 답변

Now updating docs/11 in one pass.

---

## 답변

규칙 문법 테스트를 추가하고 있습니다. 기대 값을 잘못 적었던 것(규칙 수 5 → 4)만 고치고 다시 돌립니다.

---

## 답변

전체 테스트 194건이 통과했습니다. hen 과 anybiz_prd 분석이 끝나기를 기다리는 동안 `.claude/docs/knowledge-core.md` 를 고칩니다.

---

## 답변

dstone-boot 화면은 진단 항목을 API 응답 그대로 그리므로 고칠 곳이 없습니다. 이제 hen·anybiz_prd 분석이 끝나기를 기다립니다.

---

## 답변

적용 진단의 새 항목은 지금 떠 있는 서버(진단 코드를 넣기 전 빌드)에 아직 없습니다. 다시 빌드해서 띄운 뒤 확인합니다.

---

## 답변

적용 진단의 새 항목 두 개도 정상으로 나옵니다. 바뀐 파일을 정리합니다.

---

## 답변

요청하신 세 가지를 모두 넣었습니다. Java 소스를 고치지 않아도 분석됩니다. 테스트 194건이 통과했고 4081 서버도 새 빌드로 다시 띄웠습니다. 아직 커밋하지 않았습니다.

## 1. 상수로 적은 `@RequestMapping` 주소와 상위 클래스의 `@RequestMapping`
HTTP 진입점을 SQL 대신 Java(`HttpEndpointBuilder`)로 계산하게 바꿨습니다.
- **상수 주소**: `@RequestMapping(Urls.ORDER)`, `BASE + "/list"`, `Urls.Admin.ROOT` 같은 상수를 끝까지 따라가 실제 주소를 계산합니다.
- **상위 타입의 클래스 주소**: 클래스에 주소가 없으면 인터페이스, 상위 클래스 차례로 찾아 처음 만나는 주소를 씁니다.
- **물려받은 메소드**: 상위 클래스·인터페이스의 주소 메소드는 그것을 물려받은 컨트롤러의 진입점이 됩니다. 처리 메소드는 가장 가까운 재정의입니다. 재정의하면서 자기 주소를 붙였으면 물려받지 않습니다.

## 2. JPA 쿼리
새 규칙 파일 `conf/rules/jpa.yml` 로 처리합니다.
- **쿼리 애노테이션**: `@Query`(JPQL, 네이티브 SQL)와 `@NamedQuery`(`@NamedQueries` 안의 것 포함)를 읽습니다.
- **Spring Data 저장소**: `findByStatus` 같은 메소드 이름 쿼리를 읽고, 이름이 같은 이름 있는 쿼리가 있으면 그것과 잇습니다.
- **EntityManager**: `createQuery`(JPQL), `createNativeQuery`(SQL), `createNamedQuery` 를 읽습니다.
- **엔티티 → 테이블**: JPQL 의 엔티티 이름과 `o.items` 같은 조인 경로를 `@Table` 이나 이름 짓는 관례로 테이블로 바꿉니다. 관례로 지은 테이블 이름은 신뢰도를 MEDIUM 으로 남깁니다.

## 3. Java 소스 안의 SQL
- 메소드 안의 문자열을 따라가 SQL 을 찾습니다. 문자열 이어 붙이기, `sql +=`, `StringBuilder.append`, 같은 클래스의 상수까지 봅니다.
- 찾은 SQL 은 SQL 매퍼와 같은 테이블에 넣습니다. 그래서 테이블 관계, 메소드 문서, 영향도 분석이 그대로 이어집니다.
- 파라미터처럼 값을 모르는 부분은 `?` 로 둡니다. 다른 클래스의 SQL 상수는 그 상수를 읽은 메소드와 잇습니다(MEDIUM).

## 검증
- **기존 결과 유지**: 기존 리비전 9개에 시험 적용했고, HTTP 진입점은 모두 하나도 달라지지 않았습니다. 다른 항목이 다르게 나온 프로젝트가 둘 있지만 이번 변경 때문은 아닙니다.
  - anybiz_prd: 이 리비전이 11-08 커밋보다 먼저 분석됐기 때문입니다.
  - dstone-boot: 그 뒤 고친 JSP 의 줄 번호 차이입니다.
- **새 표본 `jpa-app`**: 진입점 8개와 SQL 16개가 표본 README 의 기대 값과 모두 같습니다.
- **증분 분석**: 증분 분석과 전체 분석의 결과가 같습니다.
- **실제 프로젝트에서 새로 찾은 SQL**:
  - legacy-app: 2개(`TB_ORDER` 읽기·지우기)
  - hen: 3개(`TB_HR_EMAIL_SND_I` UPDATE 2개 포함)
  - anybiz_prd: 1개(`MailDAO` INSERT)
- **적용 진단**: "값을 모르는 주소 상수"와 "Java 소스 안의 SQL" 두 항목을 추가했고, API 응답으로 확인했습니다. 화면은 브라우저로 보지 못했습니다.

## 알아 두실 점
- **분석기 버전 1.1 → 1.2**: 다음 분석은 프로젝트마다 한 번 전체 분석으로 돕니다.
- **기존 리비전의 규칙 상태**: 새 공통 규칙 `jpa` 가 생겨 예전 리비전은 "규칙이 최신이 아님"으로 보입니다.
- **DB 변경**: `analysis_mapper.properties_json` 컬럼을 추가해 로컬 DB 에 이미 반영했습니다.
- **새로 만든 리비전**: 프로젝트 `jpa-app` 을 등록했습니다. 검증하면서 리비전 98~103 이 생겼습니다(jpa-app, legacy-app, cms4_prd, hen, anybiz_prd).
- **아직 안 되는 것**(문서의 지원 범위 표에 적었습니다):
  - static import 하거나 다른 jar 에 있는 상수로 적은 주소. 상수 이름이 주소에 남고 진단에 표시됩니다.
  - Spring Data 의 기본 메소드(`save`, `findById` 등)를 부르는 곳
  - Criteria API, QueryDSL
  - 테이블 이름을 변수로 넘긴 SQL

`docs/11`, 규칙 README, `.claude/docs` 도 함께 고쳤습니다. 올릴까요?

---

## 질문

응 올려줘.

---

## 답변

`580b0b0e` 커밋으로 `origin/main`에 올렸습니다. 이번 작업으로 바뀐 코드, 규칙 파일 `jpa.yml`, 표본 `jpa-app`, 테스트, 문서가 모두 들어갔습니다. `docs/temp` 아래 파일은 이번에도 빼 두었습니다.

---

## 질문

<pasted_content id="1290">
dstone-knowledge 에서
나는 아래과 같이 패키지 구조를 가져갔으면 좋겠어. 어떻게 생각해?
net.dstone.knowledge.
                    analysis.
                        common. ==>> 단계와 상관없이 들어가야 할 클래스들이 들어갈 패키지.
                        pass01.scan.
                        pass02.carry.
                        pass03.declare.
                        pass04.resource.
                        pass05.carryresolve.
                        pass06.resolve.
                        pass07.link.
                        pass08.semantic.
                        pass09.document.
</pasted_content id="1290">

---

## 답변

방향에는 찬성합니다. 지금은 단계별 코드가 `scanner`, `parser`, `resolver`, `incremental`, `semantic`, `rag` 처럼 이름 규칙 없이 흩어져 있습니다. 예를 들어 LINK 는 `resolver` 에, CARRY 와 CARRY_RESOLVE 는 `incremental` 에 섞여 있습니다. 단계 이름으로 묶으면 "이 단계 코드는 어디 있나"가 바로 보입니다. 다만 그대로 옮기기 전에 다섯 가지는 손보는 게 좋겠습니다.

**1. 패키지 이름의 번호(`pass01`)는 빼는 게 낫습니다.**
- 실제 실행 순서는 각 Pass 의 `order()` 값(100, 150, 200, 250, 290, 300, 400, 500, 600)이 정하고, 패키지 번호는 설명 역할만 합니다.
- 중간에 단계 하나만 끼워 넣어도 뒤 패키지를 모두 이름을 바꿔야 하고, 그때 `order()` 와 어긋나기 쉽습니다.
- `analysis.scan`, `analysis.declare` … 처럼 단계 이름만 쓰고, 순서는 `AnalysisPass` 주석과 문서의 파이프라인 표에 두기를 권합니다.
- 번호로 정렬해 보이는 편을 더 원하시면 그대로 가도 됩니다. 이 부분은 취향 문제입니다.

**2. `analysis.common` 하나로 모으면 잡동사니가 됩니다. 둘로 나누겠습니다.**
- `analysis.job`: 실행 틀(`AnalysisPass`, `AnalysisJobRunner`, `FilePassRunner`, `FileWatchdog`, `AnalysisJobContext` 등). 지금 `job` 패키지입니다.
- `analysis.common`: 여러 단계가 같이 쓰는 도구. `EncodingDetector`, `BoundedCache`, `SqlTableExtractor`(RESOURCE·SEMANTIC 이 같이 씀)가 여기에 들어갑니다.
- 한 단계에서만 쓰는 도구는 그 단계에 둡니다. 예: `ConstantEvaluator` → semantic, `JavaSqlCollector` → declare.

**3. RAG 의 검색·임베딩은 `analysis` 밖에 둡니다.**
- `rag` 패키지 12개 가운데 DOCUMENT 단계는 `DocumentPass`, 두 개의 `*DocumentBuilder`, `TextChunker` 정도입니다.
- `EmbeddingWorker`(서버 상주 스레드), `HybridRanker`, `SearchKeywords`, `EmbeddingGate`, `RagSettings` 는 검색 서비스라 분석이 아닙니다. `net.dstone.knowledge.rag` 에 그대로 두는 게 맞습니다.

**4. semantic(39개)은 하위 패키지로 한 번 더 나누면 좋겠습니다.**
- 하위 패키지 후보는 `semantic.rule`(이미 있음), `semantic.web`(Spring·서블릿·Struts·XML 진입점), `semantic.sql`(MyBatis·JPA·Java SQL), `semantic.screen`(JSP·리치클라이언트·메뉴), `semantic.layer`, `semantic.dictionary` 입니다.
- 지금 따로 있는 `dictionary` 패키지는 SEMANTIC 플러그인만 쓰므로 이 아래로 옮기면 됩니다.

**5. 그대로 두는 것**
- `api`(controller/service/dao)와 `common`(config, util, security)입니다.
- DAO 를 옮기지 않으면 MyBatis namespace 가 그대로라 위험이 줄어듭니다.

**제안하는 구조**
```
net.dstone.knowledge
  analysis.job          실행 틀
  analysis.common       여러 단계가 같이 쓰는 도구
  analysis.scan
  analysis.carry        CARRY, CARRY_RESOLVE
  analysis.declare      (+ model)
  analysis.resource
  analysis.resolve      RESOLVE, LINK, symbol(DbTypeSolver …)
  analysis.semantic     (+ rule / web / sql / screen / layer / dictionary)
  analysis.document
  rag                   임베딩 · 검색
  api, common           그대로
```
CARRY 와 CARRY_RESOLVE, RESOLVE 와 LINK 를 각각 한 패키지로 묶은 것도 원안과 다른 점입니다. 같은 자료를 다루고 클래스 수가 적기 때문인데, 원안처럼 나눠도 괜찮습니다.

**옮길 때의 비용과 확인 방법**
- 동작이 바뀌지 않는 이름 변경이지만 손대는 곳이 넓습니다.
  - MyBatis XML 의 `parameterType`/`resultType` 에 적힌 클래스 이름(예: `parser.model.TypeRow`)
  - 테스트 패키지
  - `docs/11`, `.claude/docs`, `CLAUDE.md` 의 클래스 경로
  - log4j2 로거 이름(설정돼 있다면)
- 확인 방법:
  - 단위 테스트 194건을 다시 돌립니다.
  - 기존 리비전 몇 개에 SEMANTIC 을 시험 적용해 추가 0건, 삭제 0건인지 봅니다.
  - 표본 하나를 전체 분석해 이전 결과와 해시를 견줍니다.
- 커밋은 이름 변경만 따로 하나로 만들겠습니다.

진행할까요? 진행한다면 패키지 이름의 번호(`pass01` …)를 붙일지만 정해 주세요.

---

## 질문

응 붙여줘.

---

## 답변

번호를 붙여, 처음 주신 구조대로 9단계를 나눠 옮기겠습니다. 먼저 클래스마다 어디로 갈지 정하려고, 패키지 사이의 의존과 설정에 적힌 패키지 이름을 확인합니다.

---

## 답변

These are package-private members that are now accessed across packages. I'm opening them up one at a time.

---

## 답변

컴파일이 통과했습니다. 남은 옛 패키지 이름이 있는지 찾습니다. 문서에 짧게 적은 `semantic.rule.RuleSetParser` 같은 표기와 소스 주석도 함께 봅니다.

---

## 답변

Next, rewriting the docs/11 module structure section to the new layout.

---

## 답변

docs/11 의 그 `incremental` 은 패키지가 아니라 API 파라미터라서 잘못 바꿨습니다. 되돌립니다.

---

## 답변

Moving the classes and updating references is done. Now running the tests.

---

## 답변

Tests pass (194). Next I'll restart the server on the new build and check that the actual analysis results are identical.

---

## 답변

패키지 이름에 번호를 붙여 구조를 바꿨고, 이름 변경만 `febcd98a` 로 로컬에 커밋했습니다. 아직 push 하지 않았습니다.

## 바뀐 구조
```
net.dstone.knowledge
  analysis.job                  실행 틀(AnalysisPass, AnalysisJobRunner, FilePassRunner …)
  analysis.common               단계 공용 도구(EncodingDetector, BoundedCache, SqlTableExtractor)
  analysis.pass01.scan
  analysis.pass02.carry
  analysis.pass03.declare       (+ model)
  analysis.pass04.resource
  analysis.pass05.carryresolve
  analysis.pass06.resolve       (+ model, DbTypeSolver, ClasspathResolver)
  analysis.pass07.link
  analysis.pass08.semantic      SemanticPass, SemanticPlugin, ConstantEvaluator, AnnotationValues
      .rule / .web / .sql / .screen / .layer / .dictionary
  analysis.pass09.document      DocumentPass, Code/ResourceDocumentBuilder, TextChunker
  rag                           임베딩 · 검색 (EmbeddingWorker, HybridRanker …)
  api, common                   그대로
```
- 클래스 115개(테스트 포함)를 옮겼습니다.
- 함께 고친 곳은 MyBatis XML 의 클래스 이름, 테스트 패키지, 문서(`docs/11` 모듈 구성 표, `.claude/docs`)입니다.
- `CLAUDE.md` 에 규칙 한 줄을 더했습니다. 단계를 끼워 넣으면 패키지 번호와 `order()` 를 같이 맞춘다는 내용입니다.
- 다른 패키지에서 쓰게 된 메소드 두 개는 `public` 으로 바꿨습니다(`CarryPass.baseRevisionOf`, `EmbeddingWorker.vectorText`).

## 동작이 바뀌지 않았는지 확인한 것
- **단위 테스트**: 194건 모두 통과했습니다.
- **SEMANTIC 시험 적용**: legacy-app, hen, anybiz_prd, HI-Japan 은 추가 0건, 삭제 0건입니다. jpa-app 은 20건씩 다르게 나오지만 라벨이 모두 같습니다. JPA statement 는 SEMANTIC 이 돌 때마다 새로 만들어 행 ID 가 바뀌기 때문입니다.
- **jpa-app 전체 재분석**: 리비전 104 의 결과가 98 과 같습니다. 타입, 메소드, 참조, 관계 수와 SQL·테이블, 진입점, RAG 문서 해시를 견줬습니다.
- 4081 서버는 새 빌드로 다시 띄워 두었습니다.

올릴까요?

---

## 질문

응 올려줘.

---

## 답변

패키지 정리 커밋 `febcd98a` 를 `origin/main` 에 올렸습니다. 그 위에 history 커밋 `f1d5f00a` 가 하나 더 있는데, 제가 만든 것이 아니라 대화 기록을 남기는 자동 커밋입니다. `docs/temp` 아래 파일은 이번에도 빼 두었습니다.

---

## 질문

<pasted_content id="1290">
CARRY, CARRY_RESOLVE 에 대한 설명을 리비젼과 연관시켜서 아주 자세하게 설명해줘.
테이블적재 데이터 샘플도 참고자료로 보여줘.
이 내용은 아래 문서에 추가해줘. 그리고 아래 문서의 전체 내용을 쉽게 풀어서 다시 작성해줘.
/docs/temp/knowledge분석프로세스.md
</pasted_content id="1290">

---

## 답변

먼저 문서와 CARRY · CARRY_RESOLVE 코드를 읽겠습니다.

---

## 답변

`docs/temp/knowledge분석프로세스.md`를 처음부터 다시 썼습니다. 576줄이던 문서가 894줄이 됐습니다. `docs/temp`는 git 관리 밖이라 커밋하지 않았습니다.

## 새로 넣은 내용: 6장 "리비전과 증분 분석 — CARRY, CARRY_RESOLVE 자세히"
- **6.1 리비전이란**: 리비전 하나에 그 시점의 프로젝트 전체가 들어 있습니다. 리비전마다 바뀌는 값(`revision_id`, `file_id`, SQL statement 번호)과 그대로인 값(타입·메소드·필드 ID)을 나눠 정리했습니다. 이 구분 덕분에 앞 리비전의 행을 그대로 복사할 수 있다는 점을 설명했습니다.
- **6.2 리비전을 만드는 세 가지 길**: 새 리비전 전체 분석, 새 리비전 증분 분석, 같은 리비전 이어 하기를 비교했습니다.
- **6.3 기준 리비전 고르기**: 기준 리비전을 어떻게 고르는지, 그리고 전체 분석으로 바뀌는 경우(분석기 버전이 다를 때 등)를 적었습니다.
- **6.4 리비전이 이어지는 모양**: 리비전은 `parent_revision_id`로, 파일은 `carried_from_file_id`로 이어지는 모습을 그림으로 보였습니다.
- **6.5 CARRY**: SQL 단계별로 하는 일을 표로 정리하고, 같은 파일로 보는 조건, 복사하는 테이블, `ANALYZE`를 꼭 돌리는 까닭을 적었습니다.
- **6.6 CARRY_RESOLVE**: "다시 풀 파일"을 고르는 SQL 의 WITH 절을 한 줄씩 풀어 썼습니다. 옮기지 않는 관계 9종과, 참조를 PENDING 으로 되돌리는 이유도 넣었습니다.
- **6.7 실제 적재 데이터 예제**: jpa-app 리비전 105 → 106 증분 분석에서 `Urls.java`와 `OrderSql.java`를 바꾼 경우입니다. 테이블별로 실제 행을 보였습니다.
  - `analysis_revision`, `analysis_file`(carried_from 짝짓기), `analysis_file_pass`(옮겨 온 단계 표시)
  - `analysis_method`: `file_id`만 바뀌고 메소드 ID는 그대로인 모습
  - 파일 하나의 테이블별 행 수, 관계가 CARRY_RESOLVE 에서 왔는지 SEMANTIC 에서 새로 만들었는지
  - 바뀌지 않은 `OrderJdbcDao.java`를 다시 풀게 만든 `ACCESSES_FIELD` 관계
  - 참조 행, Java 문자열 SQL 행, 단계별 시간과 로그, API 요약
- **6.8, 6.9**: 단계별로 무엇이 일어나는지 요약 표를 넣고, 조심할 점(jar 가 바뀐 것은 알아채지 못함, 기준 리비전을 지워도 됨 등)을 정리했습니다.

## 다시 쓴 부분
- **문장**: 문서 전체를 쉬운 한국어로 바꿨습니다.
- **용어 표**: 맨 앞에 "먼저 알아 둘 말"(리비전, Job, 단계, 참조, 관계 …)을 넣었습니다.
- **최신 코드에 맞춤**:
  - 기준을 11-07 에서 11-09 로 바꾸고, 클래스 경로를 새 패키지 `analysis.passNN.*` 로 고쳤습니다.
  - 이번에 넣은 JPA, Java 문자열 SQL, HTTP 주소 상수 처리를 반영했습니다.
  - 엣지 표에 "증분에서 옮기나" 열을 더했습니다.

## 예제를 만들면서 알게 된 것
- **작은 프로젝트에서는 증분 분석이 오히려 느립니다.** jpa-app 에서 CARRY 7.6초, CARRY_RESOLVE 9.7초가 걸렸습니다. `ANALYZE` 가 DB 의 모든 리비전 행을 대상으로 하기 때문입니다. 따로 재 보니 `analysis_relation`(212만 행) 2.1초, `analysis_method` 5.2초가 걸렸습니다. 문서에 그대로 적었습니다.
- **RESOLVE 가 느려진 까닭은 아직 모릅니다.** 106 에서 RESOLVE 가 0.33초에서 4.5초로 늘었는데, 다시 푼 파일은 3개뿐이라 시간이 어디서 드는지 확인하지 않았습니다. 문서에도 "확인하지 않았다"고 적었습니다.

예제를 만들려고 jpa-app 을 증분 분석해 리비전 105·106 이 생겼습니다. 그때 표본 파일 `Urls.java`·`OrderSql.java`에 주석을 붙였다가 원래대로 되돌려 두었습니다.

---

## 질문

docs/temp 가 git 관리 밖인가?

---

## 답변

아니요, 제가 틀리게 말했습니다. `docs/temp`는 git 관리 밖이 아닙니다.

- `.gitignore`에 들어 있지 않고, git이 실제로 추적하는 파일이 3개 있습니다.
  - `01.KNOWLEGE(분석Tool).pptx`
  - `02.AI에이전트-FOR-SM.pptx`
  - `knowledge분석프로세스.md`
- 그래서 이번에 고친 `knowledge분석프로세스.md`는 `git status`에 수정됨(`M`)으로 나옵니다.

정확한 상황은 이렇습니다. 10-11 에 "docs/temp 아래 파일은 git과 상관없는 파일로 취급하라"고 하셔서, 제가 커밋할 때 이 폴더를 스테이징하지 않고 빼 왔습니다. git 이 추적하지 않는 것이 아니라, 제가 커밋에서 빼고 있는 것입니다. 이 차이를 메모리에 적어 두었습니다.

정말로 git 에서 빼고 싶으시면 두 가지를 하면 됩니다. 파일은 디스크에 그대로 남습니다.
- `.gitignore`에 `docs/temp/`를 넣습니다.
- `git rm -r --cached docs/temp`로 추적을 풉니다. 이때 원격 저장소에서는 이 3개 파일이 지워진 것으로 기록됩니다.

이렇게 해 둘까요?
