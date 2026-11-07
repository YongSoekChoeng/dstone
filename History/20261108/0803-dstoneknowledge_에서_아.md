# dstoneknowledge_에서_아

> 생성일시: 2026-11-08 08:03

## 질문

dstone-knowledge 에서 아래와 같은 표현이 나오는데 "풀린 관계"란 무엇인가?
CARRY_RESOLVE (290) 증분 분석일 때만: 풀린 관계 복사

---

## 답변

**"풀린 관계"는 RESOLVE 단계(300)에서 이름만 있던 참조를 실제 대상으로 이어 만든 관계(`analysis_relation` 행)를 말합니다.** 여기서 "풀다"는 참조 해석(resolve)을 뜻합니다.

## 참조가 관계로 바뀌는 과정

1. **DECLARE 단계**에서는 파일 하나만 파싱합니다. 그래서 `orderService.save(dto)`라는 호출이 있으면 "`save`라는 이름을 부른다"는 사실만 `analysis_reference`에 남깁니다. 이때는 **이름만 있고**, 그 `save`가 어느 클래스의 어떤 메소드인지는 모릅니다.
2. **RESOLVE 단계**에서 심볼 색인과 해석기로 그 이름이 가리키는 대상을 찾습니다. 예를 들어 "`OrderServiceImpl.save(OrderDto)` 메소드 ID 1234"를 찾으면 `CALLS` 관계를 만듭니다. 이렇게 만들어진 관계가 "풀린 관계"입니다.

| 참조(이름만 있음) | 풀린 관계 |
|---|---|
| `CALL`, `METHOD_REF` | `CALLS` (대상이 프로젝트 밖이면 `EXTERNAL_METHOD`) |
| `CREATE` | `CREATES` + 생성자 `CALLS` |
| `FIELD_ACCESS` | `ACCESSES_FIELD` |
| `TYPE_USE`, `EXTENDS`, `IMPLEMENTS`, `THROWS` | `USES_TYPE`, `EXTENDS`, `IMPLEMENTS`, `THROWS` |

## CARRY_RESOLVE가 따로 있는 이유

선언과 풀린 관계는 복사해도 되는 조건이 다릅니다.

- **선언**(CARRY 단계)은 그 파일만 보면 정해집니다. 그래서 파일 내용이 같으면 그대로 복사합니다.
- **풀린 관계**는 다른 파일에 따라 달라집니다. `A.java`가 그대로여도 A가 부르는 `B.java`의 메소드가 바뀌었다면 A의 호출을 다시 풀어야 합니다.

그래서 `CarryResolvePass`는 바뀐 파일의 선언이 모두 들어온 뒤, RESOLVE 바로 앞(290)에서 실행됩니다. 아래 두 경우에 해당하는 파일은 관계를 복사하지 않고 RESOLVE가 다시 풉니다.

1. 앞 리비전에서 고쳐졌거나 삭제된 파일의 선언을 가리키던 파일
2. 앞 리비전에서 확실히 풀지 못한 참조 이름이 새로 생겼거나 고쳐진 파일의 선언 이름과 같은 파일. 지난번에는 프로젝트 밖의 것으로 봤지만 이번에는 프로젝트 안에서 풀릴 수 있습니다.

나머지 파일은 앞 리비전의 관계를 그대로 복사하고 "RESOLVE 끝남"으로 표시합니다. RESOLVE는 이 표시가 없는 파일만 처리합니다.

다만 LINK나 SEMANTIC이 만드는 관계(`OVERRIDES`, `INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `RENDERS` 등)는 풀린 관계가 아니므로 복사하지 않습니다. 이 관계들은 리비전 전체를 봐야 정해지기 때문에 매번 새로 만듭니다.

근거 자료는 `dstone-knowledge/src/main/java/net/dstone/knowledge/incremental/CarryResolvePass.java`의 클래스 주석과 `docs/11.dstone-knowledge.md`의 0.12절(증분 분석 한 장)과 4.5절입니다.

---

## 질문

analysis_file.checksum 컬럼은 언제 바뀌나?

---

## 답변

`checksum`에 값을 쓰는 곳을 찾았습니다. SCAN 단계 하나뿐입니다. 이미 분석한 리비전에 SCAN을 다시 돌릴 때 어떻게 되는지만 마저 확인하겠습니다.

---

## 답변

`analysis_file.checksum`을 쓰는 곳은 **SCAN 단계 하나**(`ScanPass`)뿐입니다. 다른 단계는 이 값을 읽기만 합니다.

## 값이 정해지는 때

SCAN 단계가 디스크의 파일 내용을 읽어 SHA-256을 구하고(`ScanPass.java:188`, `:197`), `AnalysisFileDao.xml`의 `upsertFile`로 행에 넣습니다. 분석 대상에서 빠지는 너무 큰 파일도 내용을 끝까지 읽어 해시를 구합니다.

## 실제로 값이 달라지는 경우

`analysis_file`은 리비전마다 따로 행을 둡니다(`UNIQUE (revision_id, path)`). 그래서 "바뀐다"는 말은 경우를 나눠 봐야 합니다.

| 경우 | checksum |
|---|---|
| 새 리비전을 분석함(전체·증분 모두) | 그 리비전의 행이 새로 생기고, 그때 디스크 내용으로 계산합니다. 앞 리비전의 행은 그대로 남습니다. |
| 같은 리비전에서 SCAN이 중간에 죽은 뒤 이어서 분석함 | SCAN을 처음부터 다시 하고, `ON CONFLICT ... DO UPDATE`가 지금 디스크 내용으로 **덮어씁니다**. |
| 같은 리비전에 `rerunFrom: "SCAN"`을 줌 | 위와 같이 다시 스캔하고 덮어씁니다. |
| 그 밖의 단계(CARRY, DECLARE, RESOLVE 등) | 바꾸지 않습니다. DECLARE의 `updateFileParsed`와 CARRY의 `carryFileParsed`는 파싱 결과, 패키지, 소스 루트만 고칩니다. |

정리하면, **한 행의 checksum이 실제로 바뀌는 경우는 같은 리비전에서 SCAN을 다시 돌렸는데 그사이 디스크의 파일이 달라졌을 때뿐입니다.**

## checksum을 읽는 곳

- **CARRY**(`CarryDao.xml`의 `markCarriedFiles`): 경로, checksum, 분류(언어·종류·모듈·인코딩)가 앞 리비전과 모두 같으면 "같은 파일"로 보고 결과를 옮겨 옵니다.
- **리비전 비교**(`DiffDao.xml:21`): checksum이 다르면 바뀐 파일로 셉니다.
- **SEMANTIC의 `MethodTitlePlugin`**: 소스를 다시 읽기 전에 checksum을 맞춰 봅니다. SCAN 뒤에 파일이 바뀌었으면 줄 번호가 어긋나므로 그 파일은 읽지 않고 이유를 남깁니다.

참고로 이 코드의 증분 분석은 앞 리비전 행의 checksum을 고치는 방식이 아닙니다. 새 리비전 행의 checksum과 비교해서 바뀐 파일을 찾습니다.

---

## 질문

<pasted_content id="e6de">
dstone-boot> knowledge > 노드 맵 메뉴에서 hen 프로젝트를 선택했을 때 너무 느려.


</pasted_content id="e6de">

---

## 답변

노드 맵 화면이 어떤 API를 부르는지부터 확인하겠습니다.

---

## 답변

노드 맵은 `GET /api/revisions/{id}/graph`를 부릅니다. 이 API가 hen 프로젝트에서 얼마나 걸리는지 직접 재 보겠습니다.

---

## 답변

서버 API 하나가 13초 걸립니다(anybiz_prd는 0.7초). 화면이 아니라 서버 쿼리 문제입니다. 쿼리를 보겠습니다.

---

## 답변

두 쿼리 중 어느 쪽이 느린지 DB에서 직접 실행 계획을 보겠습니다.

---

## 답변

## 원인과 조치

노드 맵이 느렸던 원인은 화면이 아니라 **DB 통계가 오래된 것**이었습니다.

- 노드 맵이 부르는 `GET /api/revisions/68/graph`가 **13초** 걸렸습니다. 같은 API가 노드가 더 많은 anybiz_prd에서는 0.7초였습니다.
- 실행 계획을 보면 플래너가 `analysis_symbol`에서 리비전 68(hen)의 행 수를 **1행**으로 추정했습니다. 실제로는 600행입니다. 그래서 600×600 = 36만 번 도는 중첩 루프를 골랐고, `selectGraphNodes`와 `selectGraphEdges`가 각각 약 11초씩 걸렸습니다.
- 통계가 틀린 이유는 이렇습니다. `analysis_symbol`의 통계는 11-06에 마지막으로 갱신됐고, hen은 11-07에 분석됐습니다. 그 뒤 바뀐 행은 1,122개라 PostgreSQL 자동 ANALYZE 기준인 약 1,433행에 못 미쳐 통계가 갱신되지 않았습니다.

`analysis_*` 테이블 6개에 `ANALYZE`를 실행해 통계만 갱신했습니다. 데이터는 바꾸지 않았습니다. 그 뒤 같은 API는 **0.27초**로 줄었습니다. 화면을 새로 고치면 바로 빨라진 것을 확인할 수 있습니다.

## 다시 생길 수 있음

코드에는 분석 직후 통계를 갱신하는 처리(`CarryDao.analyzeTable`)가 이미 있습니다. 그런데 이 처리는 **증분 분석의 CARRY 단계에서만** 돕니다. hen은 전체 분석이었기 때문에(`parentRevisionId = null`) 이 처리를 거치지 않았습니다.

앞으로도 기존 프로젝트보다 작은 프로젝트를 새로 전체 분석하면 같은 일이 생길 수 있습니다. 노드 맵뿐 아니라 그 리비전을 읽는 다른 화면과, 분석 중 LINK·SEMANTIC 단계의 SQL도 느려질 수 있습니다.

전체 분석에서도 DECLARE 뒤와 RESOLVE 뒤에 행이 많이 늘어나는 테이블에 `ANALYZE`를 돌리도록 고치면 됩니다. 기존 `analyzeTable`을 재사용하면 되므로 변경은 작습니다. 다만 코드를 고친 뒤 4081 인스턴스를 다시 띄워야 반영됩니다. 고칠까요?

---

## 질문

테이블 컬럼정보(한글명)은 소스에서만 뽑아오나?

---

## 답변

아닙니다. 소스 코드는 여섯 군데 출처 가운데 하나일 뿐입니다. 노드 맵에서 테이블 노드를 열면 `TableDictionaryService`가 아래 순서로 한글명을 찾고, 앞에서 찾은 값을 씁니다. 이 순서는 이전에 사용자가 정한 것입니다.

| 순서 | 출처(`koreanNameSource`) | 어디서 읽나 | 언제 읽나 |
|---|---|---|---|
| 1 | **DB** | 실제 대상 DB의 테이블·컬럼 코멘트(JDBC 메타데이터 `REMARKS`, `DbCommentReader`) | 상세를 열 때마다 직접 조회합니다(10분 캐시). 저장하지 않습니다. |
| 2 | **DDL** | `.sql`/`.ddl` 파일의 `COMMENT`(MySQL 인라인 `COMMENT`, Oracle/PG `COMMENT ON`) | 분석 시 RESOURCE 단계(`DdlReader`)에서 |
| 3 | **DDL_NOTE** | DDL 파일의 일반 주석(`CREATE TABLE` 줄이나 바로 위 줄, 컬럼 줄 끝 주석) | 분석 시 RESOURCE 단계에서 |
| 4 | **SQL** | MyBatis 등 매퍼 SQL 안의 주석(예: `MB_NM /* 회원명 */`) | 분석 시 SEMANTIC 단계(`SqlTableExtractor`)에서 |
| 5 | **SOURCE** | Java 소스: JPA `@Table`/`@Column`, `@ApiModelProperty`/`@Schema`/`@Comment`, 필드 선언 줄 끝 주석, 필드 위 Javadoc | 분석 시 SEMANTIC 단계(`SourceNamePlugin`)에서 |
| 6 | **DICT** | 프로젝트의 테이블 목록·컬럼 사전 CSV | 분석 시 SEMANTIC 단계(`TableDictionaryPlugin`)에서 |

## 출처별로 알아 둘 점

- **DB 코멘트는 설정해야만 씁니다.** `dstone.knowledge.dictionary.db.{projectId}.{url,username,password,schema}`를 넣은 프로젝트만 읽고, 비밀번호는 `ENC(...)`로 넣습니다. 설정이 없으면 이 출처는 건너뜁니다.
- **사전 파일도 설정해야만 씁니다.** `dstone.knowledge.dictionary.file.{projectId}`에 프로젝트 `localPath` 기준 상대 경로를 쉼표로 구분해 넣습니다. 약어로 짐작한 이름이 섞여 있을 수 있어서 순위를 맨 뒤에 두었습니다.
- **DDL, SQL 주석, 소스는 설정 없이 분석할 때 자동으로 뽑습니다.**
- 모든 주석 텍스트는 `KoreanName.clean`을 거칩니다. 그래서 한글이 들어 있어야 인정되고, 뒤에 붙은 `VARCHAR(20)` 같은 타입 표기는 떼어 냅니다.
- 화면에는 어느 출처에서 왔는지 태그로 표시되고, `koreanNameWhere`에 파일과 줄 같은 위치가 함께 나옵니다.

참고로 HI-Japan에서는 테이블 231개 가운데 SQL 주석에서 58개, 사전 파일에서 167개를 찾았고 6개는 한글명이 없었습니다.

출처는 `.claude/docs/knowledge-ui.md`의 노드 맵 문단입니다. hen 프로젝트에 DB 코멘트나 사전 파일 설정이 들어 있는지도 확인해 드릴까요?

---

## 질문

dstone-boot> knowledge 의 hen 프로젝트 에서
/hen/hca/bs/AltLsInqrPop.xfdl 가 AltLsInqrController#insertTbCmWorkAltI 를 호출한다는 것을 분석한 로직을 알려줘.

---

## 답변

먼저 DB에서 이 화면과 메소드 사이에 어떤 관계가 저장돼 있는지 찾아보겠습니다.

---

## 답변

관계는 `REQUESTS`, `via = PATH_TEXT`, `HEURISTIC`/`MEDIUM`으로 저장돼 있고 근거는 화면 273번째 줄입니다. 원본 소스와 이 관계를 만든 코드를 보겠습니다.

---

## 답변

화면 쪽 로직은 확인했습니다. 이제 화면에서 주소를 찾는 방식(`ScreenScript.findPaths`)과 주소에 대응하는 진입점이 어떻게 만들어졌는지 보겠습니다.

---

## 답변

이 연결은 **SEMANTIC 단계에서 두 플러그인이 순서대로 만든 결과**입니다. 먼저 컨트롤러 쪽에서 주소 목록(진입점)을 만들고, 그다음 화면 파일에서 찾은 주소 글자를 그 목록과 맞춰 봅니다.

## 저장된 관계

```
analysis_relation (revision 68)
  from  F223199 (src/main/hen/hca/bs/AltLsInqrPop.xfdl, file_type = NEXACRO)
  ─REQUESTS→
  to    METHOD 122d8da5… (com.frameq.hca.bs.web.AltLsInqrController#insertTbCmWorkAltI)
  confidence = MEDIUM, resolution_status = HEURISTIC
  properties_json = {"via": "PATH_TEXT", "path": "/hca/bs/insertTbCmWorkAltI.do"}
  line_start = 273
```

## 1단계: 컨트롤러 → 진입점 (`SpringPlugin`, order 100)

`AltLsInqrController.java:80`:

```java
@RequestMapping(value = "/hca/bs/insertTbCmWorkAltI.do")
public ModelAndView insertTbCmWorkAltI(NexacroPlatformMapDTO nxDto, ...)
```

`conf/rules/spring.yml`의 `endpoints.http` 규칙에 `@RequestMapping`이 있어서 `SemanticDao.insertHttpEndpoints`가 SQL만으로 진입점 행을 만듭니다. 클래스에 주소가 있으면 메소드 주소 앞에 붙이는데, 이 클래스에는 없습니다.

```
analysis_endpoint: HTTP  ALL  /hca/bs/insertTbCmWorkAltI.do  → method 122d8da5…
```

## 2단계: 화면 → 메소드 (`RichClientPlugin`, order 510)

이 플러그인은 진입점이 모두 만들어진 뒤에 돌아야 하므로 order가 510입니다.

1. **주소 사전 만들기**(`loadHandlers`): `selectEndpointHandlers`로 HTTP/SERVLET 진입점을 모두 읽어 `주소 → 메소드 ID 목록` 맵을 만듭니다.
2. **화면 파일 읽기**: `file_type`이 `NEXACRO`나 `WEBSQUARE`인 파일을 하나씩 디스크에서 읽습니다(`localPath` + `path`).
3. **주소처럼 생긴 글자 모두 찾기**(`ScreenScript.findPaths`):
   - 정규식 `/[A-Za-z0-9_\-]+(?:/[A-Za-z0-9_\-]+)*(?:\.[A-Za-z0-9]+)?`로 찾습니다.
   - `svc::order/list.do`처럼 서비스 접두어가 붙은 주소는 접두어를 떼고 앞에 `/`를 붙입니다.
   - 주석 안에 있는 글자는 `commentMask`로 제외합니다.
4. **사전과 맞춰 보기**(`linkPath`):
   - 주소가 사전에 그대로 있으면 `MEDIUM`으로 잇습니다. 이번 경우가 이것입니다.
   - 없으면 첫 마디를 컨텍스트 경로로 보고 떼어 낸 뒤 다시 찾습니다. 찾으면 `LOW`입니다.
   - 같은 화면에서 같은 메소드는 한 번만 잇습니다. 줄 번호는 그 글자가 처음 나온 위치입니다.

hen 화면의 근거 줄(`AltLsInqrPop.xfdl:273`)은 다음과 같습니다.

```js
this.fn_Save = function () {
    ...
    var strUrl = "/hca/bs/insertTbCmWorkAltI.do";   // ← 273번째 줄, 이 글자가 잡혔다
    ...
    this.gfn_Transaction(strSvc, strUrl, strInDs, ...);
};
```

## 이 방식에서 알아 둘 점

- **`gfn_Transaction` 호출을 해석한 결과가 아닙니다.** 파일 어디에 있든 진입점 주소와 같은 글자가 있으면 잇습니다. 이번 경우에는 주소를 변수에 담았다가 `gfn_Transaction`에 넘기는데도 잡힌 이유가 이것입니다.
- 그래서 **`HEURISTIC`/`MEDIUM`**으로 저장됩니다. 주소 글자가 있다는 것만 확인했고, 실제로 호출되는지(조건문 안인지, 쓰이지 않는 변수인지)는 확인하지 않습니다.
- 주소가 문자열 조립(`"/hca/" + x + ".do"`)으로 만들어지면 잡지 못합니다. 이름은 계산하지 않고 따옴표 리터럴만 인정한다는 규칙 때문입니다.
- 같은 주소를 쓰는 다른 화면(`F223459`, 1334번째 줄)도 같은 방식으로 이 메소드와 이어져 있습니다.
- 주소 대신 거래 ID로 서버를 부르는 프로젝트(HI-Japan 등)는 다른 길(`via = CALL_RULE`, 규칙 파일의 `screens.calls`)을 탑니다. hen은 주소를 그대로 적기 때문에 별도 규칙 없이 `PATH_TEXT`로 연결됐습니다.

관련 코드:
- `dstone-knowledge/src/main/java/net/dstone/knowledge/semantic/RichClientPlugin.java` (`run`, `loadHandlers`, `linkPaths`, `linkPath`)
- `dstone-knowledge/src/main/java/net/dstone/knowledge/semantic/ScreenScript.java:26` (`PATH` 정규식), `:277` (`findPaths`)
- `dstone-knowledge/src/main/resources/sqlmap/analysis/SemanticDao.xml:454` (`selectEndpointHandlers`)
