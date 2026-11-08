# dstoneknowledge_에서_아

> 생성일시: 2026-11-08 09:20

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

---

## 질문

만일 공통코드를 조회하는 gfn_comnMultiComboLoad 같은 함수패턴을 추가하려면 어떻게 해야 하지?

---

## 답변

hen 프로젝트에서 `gfn_comnMultiComboLoad`가 어디에 정의돼 있고 어떻게 쓰이는지부터 확인하겠습니다.

---

## 답변

Transaction.js는 스캔됐지만 `gfn_comnMultiComboLoad`를 거친 연결은 하나도 없습니다(`bfn_*` 두 개만 연결됨). 공통 함수를 알아보는 코드를 보겠습니다.

---

## 답변

따로 패턴(규칙)을 추가할 필요는 없습니다. `gfn_comnMultiComboLoad` 같은 공통 함수는 이미 **공통 함수(`via = COMMON_SCRIPT`)** 처리 대상입니다. 그런데 hen에서는 **함수 정의 방식 때문에 공통 함수로 인식되지 않아** 연결이 하나도 만들어지지 않았습니다.

## 지금 처리 방식

`RichClientPlugin.loadCommonFunctions`는 설정 없이 다음 순서로 처리합니다.

1. `file_type = SCRIPT` 파일(`.js`, `.xjs`)에서 `ScriptFunctions.findFunctions`로 함수 정의를 모두 찾습니다.
2. 함수 본문에 따옴표로 적힌 주소가 진입점과 같으면, 그 함수가 이 주소를 부른다고 기록합니다.
3. 화면에서 그 함수를 부르는 곳을 찾으면 `화면 ─REQUESTS→ 메소드`(`COMMON_SCRIPT`, `MEDIUM`)로 잇습니다.

`gfn_comnMultiComboLoad`는 2번 조건을 만족합니다. `Transaction.js` 본문에 `var strUrl = "/hsy/sc/selectCommInitList.do";`가 리터럴로 적혀 있고, 이 주소의 진입점도 있습니다.

## 연결되지 않는 이유: 이름이 맞지 않음

```js
// Transaction.js (공통 스크립트)
var pForm = nexacro.Form.prototype;
pForm.gfn_comnMultiComboLoad = function(strDS, strLgcd, ...) { ... }

// 화면(.xfdl), 275개 화면
this.gfn_comnMultiComboLoad("dsCombo1|dsCombo2", ...);
```

- 정의는 `pForm.gfn_comnMultiComboLoad`라는 이름으로 등록됩니다.
- 화면에서는 `this.gfn_comnMultiComboLoad`로 부릅니다.
- `ScriptFunctions.matchName`은 점이 있는 이름이면 호출 이름의 뒤쪽이 정의 이름과 같아야 맞다고 봅니다. `this.gfn_…`는 `pForm.gfn_…`로 끝나지 않으므로 맞지 않습니다.

JavaScript에서는 `pForm`이 `nexacro.Form.prototype`의 별칭이라 화면(Form 인스턴스)의 `this.gfn_x`와 같은 함수입니다. 하지만 분석기는 이 별칭 관계를 모릅니다.

대조해 보면, 연결된 `bfn_refineAddress`는 `hslLib.xjs`에서 `this.bfn_refineAddress = function`으로 정의돼 있어서 이름이 그대로 맞았습니다.

hen 공통 스크립트에서 `pForm.` 방식으로 정의된 함수는 약 250개입니다. `Transaction.js`만 봐도 이 문제 때문에 주소 세 개가 빠져 있습니다.

| 공통 함수 본문의 주소 |
|---|
| `/hsy/sc/selectCommInitList.do` (`gfn_comnMultiComboLoad`) |
| `/hsy/sc/selectCommInitTableList.do` |
| `/prj/com/RetrieveInitOrgCd.do` |

## 고치는 방법(제안)

`ScriptFunctions.findFunctions`에서 파일 맨 바깥에 있는 `var X = <무엇>.prototype;` 별칭을 알아보게 합니다. 그리고 `X.f = function` 정의를 **`this.f`라는 이름으로도** 등록합니다.

- 화면 호출 `this.gfn_comnMultiComboLoad(`가 그대로 맞습니다.
- 공통 함수 안의 `this.gfn_Transaction(...)`처럼 함수끼리 부르는 연결도 같이 풀립니다.
- 규칙 파일이 아니라 코드로 넣는 이유는, "prototype 별칭에 붙인 함수는 인스턴스에서 `this.`로 부른다"가 특정 프레임워크의 관례가 아니라 JavaScript 문법이기 때문입니다. `ScriptFunctions`는 DB를 쓰지 않는 순수 클래스라 단위 테스트를 붙이기도 쉽습니다.
- 같은 이름이 두 파일에 있으면 지금처럼 모호한 것으로 보고 버립니다. 예를 들어 `Excel.js`와 `Excel_back.js`에 같은 이름이 있으면 둘 다 연결하지 않습니다.

이렇게 고치면 `gfn_comnMultiComboLoad`를 부르는 화면 약 275개에 `REQUESTS → selectCommInitList 처리 메소드`(`COMMON_SCRIPT`, `MEDIUM`, 줄 번호는 화면에서 부른 줄)가 생깁니다.

## 알아 둘 점

- **결과가 꽤 달라집니다.** 공통코드 조회 메소드 하나에 화면 수백 개가 연결됩니다. 영향도 분석에서 이 메소드나 공통코드 테이블을 고르면 화면 수백 개가 나옵니다. 사실이긴 하지만 노드 맵에서 이 메소드 주변이 복잡해질 수 있습니다.
- **"어느 공통코드를 조회하는지"(`strLgcd` 인자)는 이번 수정에 들어가지 않습니다.** 이번 수정이 만드는 것은 화면과 메소드 사이의 연결뿐입니다. 이 값까지 남기려면 별도 기능으로 설계해야 합니다.
- **경우에 따라 쓰는 수단이 다릅니다.** 공통 함수가 주소를 고정해 두지 않고 화면이 넘긴 **거래 ID**로 메소드를 찾는 방식이라면, 이번 같은 코드 수정이 아니라 규칙 파일의 `screens.calls`(예: `conf/rules/hi-japan/frontend.yml`)에 함수 이름과 메소드 이름 틀을 적으면 됩니다. 화면을 ID로 여는 함수라면 `screens.opens`에 적습니다.
- **반영하려면 `rerunFrom: SEMANTIC`으로 다시 분석해야 합니다.** 그런데 hen의 `localPath`가 `D:/AppHome/hen`이라 WSL 쪽 4081 인스턴스는 이 경로를 읽지 못합니다. 그래서 다시 분석하는 작업은 Windows 쪽 인스턴스에서 해야 합니다. 원래 리비전 68도 Windows 쪽에서 분석된 것입니다.

위 방식으로 `ScriptFunctions`를 고치고 단위 테스트까지 추가할까요?

---

## 질문

응 진행해줘. 그리고 올려줘.

---

## 답변

`ScriptFunctions` 전체와 기존 테스트부터 보겠습니다.

---

## 답변

이제 테스트를 추가합니다. 기존 `nexacro-app` 표본이 있는지 보겠습니다.

---

## 답변

표본 프로젝트의 기대 값을 바꾸지 않도록, 테스트는 `ScriptFunctionsTest`에 글자를 직접 넣는 방식으로 추가하겠습니다.

---

## 답변

새 테스트를 포함해 `ScriptFunctionsTest` 7건이 통과했습니다. 전체 테스트를 돌리고, 실제 hen 소스로도 확인하겠습니다.

---

## 답변

Unit tests all pass (171 tests, 0 failures). Next I'll run the new logic against the real hen script files and screens to see what changes.

---

## 답변

Now update both docs.

---

## 답변

코드, 테스트, 문서 4개 파일만 커밋해서 올리겠습니다. `docs/temp`는 저장소 규칙에 따라 제외합니다.

---

## 답변

수정을 마치고 `main`에 커밋해 GitHub에 푸시했습니다(커밋 `d4d240a9`). hen의 공통 라이브러리처럼 `var pForm = nexacro.Form.prototype;`로 별칭을 만들고 그 별칭에 붙인 함수를, 이제 화면에서 부르는 이름인 `this.함수`로 인식합니다. 다만 hen 분석 결과에는 아직 반영되지 않았고, 아래 순서로 다시 분석해야 합니다.

## 바뀐 점

`ScriptFunctions`가 공통 스크립트의 함수 정의를 다음처럼 등록합니다.

| 정의 | 등록 이름 |
|---|---|
| `pForm.gfn_x = function` (`pForm`은 파일 맨 바깥의 prototype 별칭) | `this.gfn_x` |
| `nexacro.Form.prototype.gfn_x = function` | `this.gfn_x` |
| `comLib.gfnX = function` | 그대로 |

- 함수 안에서 만든 별칭은 인식하지 않습니다. nexacro 라이브러리 원본은 `var _pGrid = nexacro.Grid.prototype`처럼 대부분 함수 안에서 별칭을 만드는데, 이것까지 인식하면 Grid·Component 메소드가 섞여 들어오기 때문입니다.
- 별칭이 아닌 `Array.prototype.slice.call(...)` 같은 표현도 인식하지 않습니다.
- 정의 이름이 화면 호출(`this.gfn_x(...)`)과 같아졌으므로 `RichClientPlugin`은 고치지 않았습니다. 공통 함수끼리 서로 부르는 연결(`this.gfn_Transaction`)도 같은 이유로 함께 풀립니다.

## 확인한 것

- `ScriptFunctionsTest`에 테스트 1건을 추가했습니다. 전체 단위 테스트 171건이 모두 통과했습니다.
- hen 리비전 68의 실제 소스(SCRIPT 979개, 화면 726개)로 새 로직만 따로 돌려 봤습니다. 주소를 가진 공통 함수가 2개에서 7개로 늘었습니다. 이미 연결돼 있던 `bfn_*`는 DB 결과와 같습니다.

| 새로 연결되는 공통 함수 | 주소 | 화면 수 |
|---|---|---|
| `gfn_comnMultiComboLoad` | `/hsy/sc/selectCommInitList.do` | 266 |
| `gfn_getSvrDate` | `/common/selectSysDatetime.do` | 33 |
| `gfn_comnSingleComboLoad` | `/hsy/sc/selectCommInitTableList.do` | 31 |
| `gfn_singleFileDownload` | `/hsy/sc/downloadAttcFile.do` | 2 |

- 문서 `docs/11.dstone-knowledge.md`와 `.claude/docs/knowledge-screens-rich-client.md`에 이번 규칙과 hen 확인 결과를 적었습니다. `docs/temp`는 커밋하지 않았습니다.

## hen에 반영하려면 해야 할 일

실행 중인 4081 인스턴스는 다시 빌드하거나 재기동하지 않았습니다. hen의 `localPath`가 `D:/AppHome/hen`이므로 Windows 쪽에서 다음 순서로 진행해야 합니다.

1. pull합니다.
2. `dstone-knowledge`를 빌드하고 재기동합니다.
3. hen 리비전 68에 `rerunFrom: SEMANTIC`으로 다시 분석합니다.

SCRIPT 파일은 이미 리비전 목록에 있으므로 새 리비전을 만들 필요는 없습니다.

---

## 질문

올려줘.

---

## 답변

새로 올릴 것은 없습니다. 이번 수정은 직전 답변에서 이미 GitHub에 푸시했습니다.

- 수정 커밋 `d4d240a9`(knowledge: prototype에 붙인 공통 함수를 this.함수 이름으로 인식)가 `origin/main`에 들어 있습니다. 그 뒤에 자동 기록 커밋 `fe0ed117`이 하나 더 올라가 있습니다.
- 로컬 `main`과 `origin/main`은 같은 상태이고, 아직 푸시하지 않은 커밋은 없습니다.
- 커밋되지 않은 변경은 `docs/temp/knowledge분석프로세스.md` 하나뿐입니다. `docs/temp`는 저장소 규칙에 따라 커밋하지 않는 폴더라 그대로 두었습니다. 이 파일도 올려야 한다면 말씀해 주세요.

---

## 질문

/docs/11.dstone-knowledge.md 에 아래의 내용을 보강해줘.
- /conf/rules 아래의 룰관련 내용의 가이드 보강.
- 룰이 추가될 때 구체적으로 어떤 부분이 어떻게 추가되어야 하는지 샘플 가이드.

---

## 답변

`docs/11.dstone-knowledge.md`에 지금 있는 규칙 설명과 `conf/rules` 아래 실제 파일부터 확인하겠습니다.

---

## 답변

규칙 파일과 파서의 키를 모두 확인했습니다. 이제 4.7.1절 뒤에 "규칙 키별 동작", "새 규칙 추가 예시", "작업 순서와 확인 방법"을 새로 쓰겠습니다.

---

## 답변

문서를 넣었습니다. 예시에 적은 동작이 실제 코드와 맞는지 몇 가지를 확인하겠습니다(주소 속성, HTTP 메소드 값, 메뉴 키 형식, 규칙 검사 시점).

---

## 답변

Add the `OfwPost` line to the combined example as well, then check that the combined YAML actually passes the real parser.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/humanizer

# humanizer: 한국어 AI 작문 패턴 감지 및 교정기

## 소개

당신은 AI가 생성한 텍스트의 특징적인 패턴을 감지하고 교정하는 한국어 글쓰기 분석 전문가입니다. 당신의 전문성은 과학적 언어학 연구, 특히 KatFishNet 논문(ArXiv 2503.00032v4)에 기반하고 있으며, 이 논문은 인간이 작성한 텍스트와 LLM이 생성한 한국어 텍스트 사이의 측정 가능한 차이를 식별했습니다.

**중요한 컨텍스트**:
- 이 스킬은 **한국어 텍스트에만** 집중하며, 영어 번역이나 다른 언어는 다루지 않습니다
- 검출 패턴은 높은 정확도(쉼표 패턴: 94.88% AUC, 품사 다양성: 82.99% AUC, 띄어쓰기: 79.51% AUC)를 가진 **실증적 언어학 분석**에 기반합니다
- 당신의 목표는 원래의 의미와 의도를 보존하면서 텍스트를 **자연스럽게 인간이 작성한 것처럼** 만드는 것입니다
- 우선순위에 따라 구성된 6개 카테고리의 40가지 고유 패턴을 분석하며, 각 패턴은 S1/S2/S3 심각도로 분류됩니다

## 작업 설명

실행될 때 다음을 수행합니다:

1. **한국어 텍스트 읽기** - 사용자가 제공한 텍스트(인자, 대화 컨텍스트, 또는 파일)
2. **텍스트 분석** - 아래에 설명된 40가지 검출 패턴에 대해 분석
3. **AI 작문 마커 식별** - 각 발견 사항에 대한 과학적 근거와 함께
4. **텍스트 재작성** - 자연스럽고 인간이 작성한 것처럼 들리도록
5. **수정된 버전 제시** - 주요 변경 사항의 요약과 함께(선택적)

**프로세스 가이드라인**:
- 항상 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준(격식체 vs 반말)을 유지하세요
- 완벽함보다 자연스러움에 집중하세요
- 중요한 변경 사항에 대한 근거를 설명하세요
- 텍스트가 AI 마커를 거의 보이지 않으면, 이미 자연스럽다고 인정하세요

## 한국어 AI 작문 특징

LLM이 생성한 한국어 텍스트는 인간의 글쓰기와 측정 가능하게 다른 고유한 언어적 패턴을 보입니다:

- **문장부호**: 과도한 쉼표 사용(61% vs 인간 26%), 영어식 쉼표 배치
- **띄어쓰기**: 의존명사 띄어쓰기의 경직된 일관성(SD=0.02 vs 인간의 변동성)
- **어휘 다양성**: 낮은 품사 다양성, 명사 과다 사용, 동사/형용사 빈곤
- **어휘**: 일반적 서술어 과다 사용(중요하다, 핵심적, 효과적, 지속가능한, 혁신적)
- **구조**: 단조로운 문장 리듬, 3박자 리스트, 과도한 접속사

이러한 패턴은 LLM 학습 데이터 편향과 확률적 텍스트 생성에서 나타나며, 한국어 원어민 독자에게 부자연스럽게 느껴질 수 있습니다.

---

## 검출 패턴 (총 40가지)

### 패턴 분류 체계

모든 패턴은 검증 수준과 심각도에 따라 메타데이터를 포함합니다.

**검증 수준**:
- **✅ 과학적**: KatFishNet 논문(ArXiv 2503.00032v4, 2025) 등 외부 문헌으로 검증된 패턴 — 검증 필드에 인라인 인용 (예: `검증: ✅ 과학적 (KatFishNet, 94.88% AUC)`)
- **📊 경험적**: 실무 관찰을 기반으로 한 커뮤니티 기여 패턴

**심각도(severity)**:
- **S1 (Critical)**: 한 번만 나와도 AI 신호로 결정적. 무조건 수정 권장.
- **S2 (Strong)**: 1~2회 허용, 3회+ 반복 시 AI 신호. 빈도 기반 판정.
- **S3 (Weak)**: 단독으로는 약한 신호이나 다른 패턴과 중첩될 때 결정적.

**자연도 등급(Naturalness Grade)** — v1.6.0 신설:

윤문 후 텍스트의 자연스러움을 한 글자로 요약. 출력 형식의 "자연도 등급" 라인에 표시.

- **A**: S1 0건 + S2 ≤ 2 — 자연스러움 거의 완벽 (인간이 작성한 것처럼 읽힘)
- **B**: S1 1~2건 또는 S2 3~5건 — 자연스럽지만 사소한 AI 흔적
- **C**: S1 3+건 또는 S2 6+건 — AI 흔적 명확, 추가 윤문 권장
- **D**: 다중 카테고리 위반 (S1 5+건 + S2 8+건) 또는 변경률 가드 트리거 발생

자연도 등급은 윤문 **후** 텍스트에 적용 — 윤문 결과의 품질을 한 줄로 평가. 사용자가 결과를 신뢰할 수 있는지 빠르게 판단하도록 돕습니다.

각 패턴에는 다음 3필드 메타데이터가 포함됩니다:
- **검증**: 검증 수준 (✅ 과학적 / 📊 경험적), 외부 문헌은 인라인 인용
- **심각도**: S1 / S2 / S3 + 정의
- **버전**: 패턴이 추가된 스킬 버전

### 빠른 참조

**카테고리별 우선순위와 대표 패턴**:

1. **문장부호** (7가지, 최고: 94.88% AUC) - 쉼표 과다, 영어식 배치, 연결어미 뒤 쉼표, 콜론 과다
2. **띄어쓰기** (3가지, 높음: 79.51% AUC) - 경직된 의존명사, 보조용언, 숫자 띄어쓰기
3. **품사 다양성** (3가지, 높음: 82.99% AUC) - 명사 과다, 동사/형용사 빈곤
4. **어휘** (10가지, 중간) - AI 유행어, 불필요한 한자어, 영어 직역, 복수형 과다, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 표현, hype 어휘 군집, ~적 N 추상 체인
5. **구조** (4가지, 중간) - 문장 리듬, 3박자, 접속사, 경어체
6. **번역투** (13가지, 높음: 경험적) - 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발)

전체 40가지 패턴 목록과 상세 내용은 각 카테고리 참조 문서 참조.

### 상세 패턴 설명

각 패턴의 완전한 검출 기준, 과학적 근거, 예시, 교정 전략은 다음을 참조하세요:

- **문장부호 패턴 (1-7)**: [references/punctuation-patterns.md](references/punctuation-patterns.md) 참조
- **띄어쓰기 패턴 (8-10)**: [references/spacing-patterns.md](references/spacing-patterns.md) 참조
- **품사 다양성 패턴 (11-13)**: [references/pos-patterns.md](references/pos-patterns.md) 참조
- **어휘 패턴 (14-20, 37-39)**: [references/vocabulary-patterns.md](references/vocabulary-patterns.md) 참조
- **구조 패턴 (21-24)**: [references/structure-patterns.md](references/structure-patterns.md) 참조
- **번역투 패턴 (25-36, 40)**: [references/translation-ese-patterns.md](references/translation-ese-patterns.md) 참조

**상세 패턴을 로드해야 할 때**:
- 해당 카테고리의 패턴을 감지했을 때 특정 카테고리 파일을 로드하세요
- 포괄적인 분석이 필요한 경우 모든 파일을 로드하세요
- 텍스트가 최소한의 AI 마커만 보이면 로드를 건너뛰세요

---

## 작업 흐름

### 1단계: 텍스트 받기

- 사용자가 인자, 대화, 또는 파일 경로를 통해 한국어 텍스트를 제공합니다
- 파일 경로가 주어지면 Read 도구를 사용하여 내용을 로드합니다
- 텍스트가 대화 컨텍스트에 있으면 추출합니다

### 2단계: 모든 패턴에 대해 분석

다음을 체계적으로 확인합니다:
- **문장부호 패턴 (1-7)**: 쉼표 개수, 위치 확인, 불필요한 사용 식별, 영어식 콜론 사용 확인
- **띄어쓰기 패턴 (8-10)**: 의존명사 띄어쓰기 일관성, 보조용언 띄어쓰기, 숫자 띄어쓰기 확인
- **품사 패턴 (11-13)**: 명사/동사/형용사 비율 추정, 명사화 구문 식별
- **어휘 패턴 (14-20, 37-39)**: AI 유행어, 한자어 과다 사용, 영어 직역, 복수형 '-들' 과다 사용, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 관용구·결말 공식·결말 단언, hype 어휘 군집, ~적 N 추상 체인 확인
- **구조 패턴 (21-24)**: 문장 길이 변동, 목록 패턴, 접속사, 경어체 균일성 확인
- **번역투 패턴 (25-36, 40)**: 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발) 확인

감지한 패턴에 필요한 **상세 참조 파일을 로드**하세요.

### 3단계: 발견 사항 식별

감지된 각 패턴에 대해:
- 텍스트의 구체적인 인스턴스를 기록하세요
- 과학적 근거를 설명하세요(해당되는 경우 KatFishNet 참조)
- 이 패턴이 왜 AI 작문을 나타내는지 설명하세요

### 4단계: 텍스트 재작성

- 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준을 유지하세요
- 감지된 모든 패턴에 대한 교정을 적용하세요
- 자연스러움과 인간다운 흐름에 집중하세요
- 한국어 원어민이 작성한 것처럼 들리게 만드세요

### 4.5단계: 의미 보존 자체검증 (필수)

자연스러운 버전을 출력하기 전에 다음 6항을 자체 점검합니다. **위반 사항이 발견되면 해당 부분을 수정한 뒤 재점검합니다.**

- [ ] **수치·날짜·고유명사**: 원본의 숫자, 날짜, 인명, 기관명, 제품명이 모두 보존되었는가?
- [ ] **인과관계 방향**: "X 때문에 Y" → "Y 때문에 X"로 뒤바뀌지 않았는가?
- [ ] **부정 표현**: "있다/없다", "가능/불가능", "찬성/반대"가 반전되지 않았는가?
- [ ] **직접 인용**: 큰따옴표 안 직접 인용은 그대로인가?
- [ ] **핵심 주장·결론**: 원본의 메시지·결론이 변경되지 않았는가?
- [ ] **격식 수준**: 원본의 격식체/반말이 일관되게 유지되었는가?

위반 사항이 없으면 출력 형식의 "주요 변경 사항 요약" 끝에 "✓ 의미 보존 검증: 6항 모두 통과"를 명시합니다. 위반이 있었다면 수정 사항을 함께 기록합니다.

### 5단계: 결과 제시

**형식**:

```
## 분석 결과

**감지된 패턴 요약**: S1 N건, S2 M건, S3 K건
**자연도 등급**: A | B | C | D — [등급 부여 근거 한 줄]

다음 AI 작문 패턴을 감지했습니다(심각도 높은 순):

1. **패턴 X [S1]**: [발견 사항에 대한 간단한 설명]
   - [텍스트의 구체적인 예시]
   - [과학적/경험적 근거]
   - [S1/S2/S3 부여 이유]

2. **패턴 Y [S2]**: [...]

[감지된 모든 패턴에 대해 계속, S1 → S2 → S3 순으로 정렬]

---

## 자연스러운 버전

[자연스러운 한국어 글쓰기로 재작성된 텍스트]

---

## 주요 변경 사항 요약 (선택적)

- [근거와 함께 변경 사항 1]
- [근거와 함께 변경 사항 2]
- [기타]
```

**텍스트가 이미 자연스러운 경우**:

```
## 분석 결과

이 텍스트는 AI 작문 마커를 거의 보이지 않으며 상당히 자연스럽게 읽힙니다. 사소한 문제만 발견했습니다:

- [사소한 문제 1 (있는 경우)]
- [사소한 문제 2 (있는 경우)]

이 글은 이미 인간이 작성한 것처럼 들립니다. [원본 또는 약간 수정된 버전을 제공]
```

---

## 중요 사항

### 의미 보존
- 사실적 내용이나 핵심 메시지를 절대 변경하지 마세요
- 모든 주요 정보를 그대로 유지하세요
- 작성자의 의도된 톤과 목적을 유지하세요

### 변경률 가드 (Over-rewriting Guard)

자연스러운 버전을 작성한 후 **변경률**을 자체 추정해 과도한 교정을 방지하세요.

**변경률 계산**:
- 원본과 자연스러운 버전을 어절 단위로 비교
- 변경된 어절 / 전체 어절 비율 (대략적, 0~100%)
- 단순 띄어쓰기·문장부호 변경은 제외하고 의미 단위 변경만 카운트

**임계값**:
- **<30% (정상)**: 그대로 출력
- **30~50% (경고)**: 출력 끝에 "변경률 N%로 다소 큼. 의미 보존 재확인 권장" 한 줄 추가
- **>50% (중단)**: 출력하지 말고 사용자에게 확인 요청
  - 메시지: "변경률이 N%로 과도합니다. 원본 의미가 변경됐을 가능성이 있습니다. 더 보수적으로 재작성해드릴까요?"
  - 사용자 동의 시 보수적으로 재작성 후 30% 이내로 다시 시도

**근거**: 과도한 교정은 원본의 의미·뉘앙스·작성자 톤을 잃을 위험이 있음. 정량 임계로 자체 검증해 의미 보존 원칙을 강화.

### 격식 유지
- 원래의 격식 수준(격식체 vs 반말)을 존중하세요
- 텍스트가 격식체(비즈니스, 학술)라면 격식체를 유지하되 자연스럽게 하세요
- 텍스트가 반말(블로그, 소셜 미디어)이라면 더 대화적으로 만드세요
- 리듬을 위해 같은 격식 범위 내에서 어투를 다양화할 수 있습니다

### 자연스러움 집중
- 목표는 인간이 작성한 것처럼 들리게 하는 것이지, 완벽하게 만드는 것이 아닙니다
- 자연스러운 한국어 글쓰기는 약간의 불규칙성과 개인적 스타일을 가지고 있습니다
- 작성자의 목소리를 바꿀 정도로 과도하게 교정하지 마세요

### 근거 설명
- 주요 변경 사항에 대해서는 간단히 이유를 설명하세요
- 40가지 검출 기준의 특정 패턴을 참조하세요
- 사용자가 무엇이 텍스트를 AI 생성처럼 vs 인간처럼 들리게 하는지 이해하도록 도와주세요

### 문화적 감수성
- 한국 문화적 맥락과 기대를 인식하세요
- 한국어 독자는 직접성과 효율성을 중시합니다
- 원래의 톤과 일치하지 않는 한 지나치게 화려하거나 극적인 언어를 피하세요
- 영어와 다른 한국어 글쓰기 관습을 존중하세요

### 과학적 근거
- 당신의 교정은 실증적 언어학 연구에 기반합니다
- KatFishNet 논문은 대부분의 패턴에 대한 과학적 검증을 제공합니다
- 주관적인 스타일 선호가 아니라 측정 가능한 언어적 차이를 적용하는 것입니다

---

## 특수 상황

### 매우 짧은 텍스트
텍스트가 1-2문장만 있는 경우:
- 모든 패턴을 감지하기에 충분한 내용이 없을 수 있습니다
- 명백한 마커(쉼표, 어휘, 띄어쓰기)에 집중하세요
- 한계를 인정하세요: "이 텍스트는 매우 짧아서 패턴 분석이 제한적입니다."

### 이미 자연스러운 텍스트
텍스트가 AI 마커를 거의 또는 전혀 보이지 않는 경우:
- 텍스트가 잘 작성되었음을 인정하세요
- 있다면 사소한 제안을 제공하세요
- 불필요한 경우 교정을 강요하지 마세요

### 기술적이거나 전문 분야별 텍스트
텍스트에 전문 용어가 포함된 경우:
- 한자어라도 기술 용어는 보존하세요
- 문장 구조와 문장부호 패턴에 집중하세요
- 기술 글쓰기는 다른 관습을 가지고 있음을 인정하세요

### 언어 혼합
텍스트에 영어 단어나 구문이 포함된 경우:
- 영어 내용은 그대로 보존하세요
- 한국어 부분에 집중하세요
- 부자연스러운 코드 전환 패턴을 확인하세요

### 사용자가 특정 격식 요청
사용자가 격식체 또는 반말 출력을 요청하는 경우:
- 요청대로 격식 수준을 조정하세요
- 여전히 40가지 패턴 교정을 모두 적용하세요
- 요청에 따라 격식을 조정했음을 설명하세요

---

## 분석 예시

실제 적용 사례는 [examples/](examples/) 디렉토리 참조:
- [before-1.md](examples/before-1.md) / [after-1.md](examples/after-1.md): 격식체 교육 분야 텍스트
- [before-2.md](examples/before-2.md) / [after-2.md](examples/after-2.md): 다양한 패턴 예시
- [before-3.md](examples/before-3.md) / [after-3.md](examples/after-3.md): 번역투 풍부 비즈니스 보고서 (v1.4.0 신규 카테고리 시연 + S1/S2/S3 심각도 출력 형식)
- [before-4.md](examples/before-4.md) / [after-4.md](examples/after-4.md): AI 결말 관용구·~적 N·~것이다 풍부 텍스트 (v1.5.0/v1.6.0 신규 패턴 시연 + 자연도 등급 출력 형식)

각 예시는 감지된 패턴, 분석, 자연스러운 버전, 주요 변경 사항을 포함합니다.

---

## 최종 알림

- **철저하게**: 40가지 패턴을 모두 체계적으로 확인하세요
- **과학적으로**: 가능한 경우 언어학 연구에 근거하여 교정하세요
- **도움이 되게**: 사용자가 배울 수 있도록 근거를 설명하세요
- **존중하며**: 작성자의 의미와 의도를 보존하세요
- **자연스럽게**: 목표는 로봇 같은 완벽함이 아니라 인간다운 한국어입니다

당신의 전문성은 한국어 작성자가 의도된 메시지와 스타일을 유지하면서 진정으로 인간이 작성한 것처럼 들리는 텍스트를 만들도록 돕습니다. 한국어 언어학에 대한 지식과 AI 텍스트 감지 뒤의 과학적 연구를 사용하여 가치 있고 정확한 교정을 제공하세요.


ARGUMENTS: docs/11.dstone-knowledge.md 의 4.7.2~4.7.5절(새로 쓴 부분)만 검토. 코드·키·식별자는 바꾸지 않는다.

---

## 답변

새로 쓴 부분은 번역투와 AI식 표현이 거의 없습니다. 한 문장만 둘로 나눕니다(자연도 A, 변경률 5% 미만).

---

## 답변

`docs/11.dstone-knowledge.md` 4.7절에 규칙 가이드와 규칙 추가 샘플을 보강해 커밋하고 GitHub에 푸시했습니다(`6065404e`). 4.7.1절 첫머리에는 새 절들로 가는 안내 문장을 넣었고, `docs/temp`는 이번에도 커밋하지 않았습니다.

## 새로 넣은 내용

| 절 | 내용 |
|---|---|
| **4.7.2 규칙 키별 동작** | 규칙 키(`endpoints.*`, `injects`, `proxies`, `layers.*`, `callArguments`, `screens.*`)마다 무엇을 보고, 무엇이 생기고, 어느 플러그인이 적용하고, 결과가 어느 테이블에 남는지를 한 표로 정리했습니다. 계층은 먼저 정한 것이 유지된다는 점, 상위 타입은 바로 위 하나만 본다는 점, 애노테이션은 단순 이름으로 쓴다는 점도 적었습니다. |
| **4.7.3 규칙을 쓸까, 코드를 고칠까** | 규칙 파일, 새 `SemanticPlugin`, 코드 수정, 잇지 않음 가운데 어느 쪽으로 풀지 고르는 기준입니다. 어제 고친 Nexacro prototype 별칭을 코드 수정 사례로 들었습니다. |
| **4.7.4 규칙 추가 예시** | 가상 프로젝트 `order-system`으로 묶음 파일 머리말(`name`, `order`, `autoApply`)과 `ruleSets` 등록 방법을 보였습니다. 이어서 관례 7가지를 **소스 모양 → 규칙 → 생기는 결과 → 확인 SQL** 순서로 설명하고, 예시를 합친 완성 파일도 넣었습니다. |
| **4.7.5 작업 순서와 확인** | 후보 API → 소스 확인 → 시험 적용(`curl` 예시) → 저장 위치(`${APP_CONF_DIR}/rules`) → `rerunFrom: SEMANTIC` → `upToDate` 확인 → 커밋과 README 기록 순서입니다. 자주 하는 실수 7가지와 원인도 표로 넣었습니다. |

4.7.4절의 관례 7가지는 다음과 같습니다.

1. 사내 애노테이션이 붙은 메소드를 배치 진입점으로
2. 자체 주소 애노테이션을 HTTP 진입점으로
3. 상위 클래스로 계층 정하기
4. SQL 이름을 둘째 인자로 받는 DAO 메소드(`argIndex`, `receiverTypes`, `template`)
5. 화면 이름을 돌려주는 생성자(`target: VIEW`, `exclude`)
6. 화면이 거래 ID로 서버를 부르는 공통 함수(`screens.calls`, `fields`)
7. 화면 ID로 다른 화면을 여는 함수와 메뉴 목록(`screens.opens`, `menus`)

## 확인한 것

- 예시 묶음 두 개(`order-system/backend`, `order-system/frontend`)를 실제 규칙 파서 `RuleSetParser`로 읽어 봤고, 둘 다 오류 없이 읽혔습니다.
- 예시 2의 HTTP 메소드는 코드와 맞지 않아 쓰는 도중에 고쳤습니다. 처음에는 `method = "GET"` 문자열로 적었는데, 코드는 `RequestMethod.GET`처럼 enum으로 적은 값의 마지막 마디를 읽습니다. 그래서 `OfwMethod.GET` 형태로 바꿨습니다.
- `TRANSACTION` 진입점이 생기는 조건도 코드와 맞췄습니다. 그 함수를 프로젝트의 화면이 실제로 쓰고, 틀의 고정 글자가 3자 이상일 때만 생깁니다.
- 새로 쓴 한국어 문장은 `humanizer` 지침으로 점검했고, 한 문장만 둘로 나눴습니다.

---

## 질문

올려줘.

---

## 답변

이번에도 새로 올릴 것은 없습니다. 규칙 가이드를 보강한 커밋 `6065404e`는 이미 GitHub `origin/main`에 올라가 있습니다. 그 뒤로 자동 기록 커밋 `a4afc3d5`가 하나 더 쌓였습니다.

- 로컬 `main`과 `origin/main`은 같은 상태이고, 아직 푸시하지 않은 커밋은 없습니다.
- 커밋하지 않은 변경은 `docs/temp/knowledge분석프로세스.md` 하나뿐입니다. `docs/temp`는 커밋하지 않는 폴더라서 그대로 두었습니다. 이 파일도 올려야 한다면 말씀해 주세요.

---

## 질문

dstone-knowledge 에서 
hen 프로젝트의 
OnlineBatchService#asyncCall 가 sso_application_jsp#decrypt 를 호출한다는 근거는 뭔가?

---

## 답변

DB에서 두 메소드 사이에 어떤 관계가 저장돼 있는지부터 찾겠습니다.

---

## 답변

hen은 리비전 70으로 다시 분석돼 있습니다(68은 없어졌습니다). 리비전 70에서 두 메소드 사이의 관계를 보겠습니다.

---

## 답변

두 메소드 사이에 직접 저장된 관계는 없습니다. `decrypt`로 들어오는 관계를 모두 보겠습니다.

---

## 답변

원인은 `EncUtil.java:41`의 `EncUtil.getEncryptor().decrypt(encStr)`입니다. 이제 `asyncCall`에서 이 줄까지 이어지는 호출 경로를 찾겠습니다.

---

## 답변

경로를 찾았습니다. 앞의 세 단계는 `HIGH`이고 마지막 한 단계만 `LOW`(이름으로 짐작)입니다. 각 줄의 원본과 이름 짐작 코드를 확인하겠습니다.

---

## 답변

정리하면, 이 관계는 실제 호출이 아니라 **이름이 같은 메소드를 찾아 짐작으로 이은 결과(`LOW` / `HEURISTIC`)**입니다. 앞의 세 단계는 해석기가 확실하게 푼 호출이고, 문제가 되는 것은 마지막 한 단계뿐입니다.

## 노드 맵의 경로(hen 리비전 70)

hen은 오늘 아침 다시 분석돼 리비전 68은 없어졌고, 지금은 70입니다.

| # | 호출 | 근거 줄 | 신뢰도 |
|---|---|---|---|
| 1 | `OnlineBatchService#asyncCall(Batch)` → `PropUtil#getProperty(String)` | `OnlineBatchService.java:36` `propUtil.getProperty("frameq.online.base.url")` | `HIGH` / `RESOLVED` |
| 2 | `PropUtil#getProperty(String)` → `PropUtil#getProperty(String,String)` | `PropUtil.java:14` | `HIGH` / `RESOLVED` |
| 3 | `PropUtil#getProperty(String,String)` → `EncUtil#decrypt(String)` | `PropUtil.java:22` `ENC(...)` 값이면 `EncUtil.decrypt(val)` | `HIGH` / `RESOLVED` |
| 4 | `EncUtil#decrypt(String)` → `sso_application_jsp#decrypt(String)` | `EncUtil.java:41` | **`LOW` / `HEURISTIC`, `via = NAME`, `candidates = 3`** |

## 4번이 생긴 과정

`EncUtil.java:41`:

```java
public static String decrypt(String encStr) {
    return EncUtil.getEncryptor().decrypt(encStr);   // jasypt StandardPBEStringEncryptor.decrypt
}
```

실제로 호출되는 것은 외부 라이브러리 jasypt의 `StandardPBEStringEncryptor.decrypt`입니다. 그런데 RESOLVE 단계(`ReferenceResolver.resolveCall`)는 이렇게 처리했습니다.

1. **해석기가 풀지 못했습니다.** hen 프로젝트에 `classpath`가 등록돼 있지 않아 jasypt jar를 알 수 없기 때문입니다.
2. **대상 타입도 알아내지 못했습니다.** 대비책 `typeOfExpression` → `returnTypeOf`는 `getEncryptor()`의 반환 타입(`StandardPBEStringEncryptor`)을 DB에서 읽기는 합니다. 하지만 **프로젝트 안의 타입일 때만** 그 타입을 돌려주고, 외부 타입이면 `null`을 돌려줍니다(`ReferenceResolver.java:913`).
3. **이름으로 짐작했습니다.** 대상 타입을 모르면 `guessByName`이 프로젝트 전체에서 "이름이 `decrypt`이고 인자가 1개인 메소드"를 찾습니다. 후보가 5개 이하라 모두 `LOW`로 이었습니다.

| 후보 | 공개 범위 |
|---|---|
| `com.frameq.common.util.Crypto#decrypt(String)` | public static |
| `com.frameq.common.util.EncUtil#decrypt(String)` | public static (자기 자신) |
| `sso_application_jsp#decrypt(String)` | **private** (`sso_application.jsp:5`) |

JSP의 `decrypt`는 private이라 `EncUtil`에서 부를 수 없습니다. 따라서 이 선은 **잘못 짐작한 것**입니다. 같은 이유로 `EncUtil#decrypt`가 자기 자신이나 `Crypto#decrypt`를 부르는 선도 틀린 선입니다.

## 영향 범위와 고치는 방법

리비전 70에는 이렇게 이름으로 짐작한 관계(`via = NAME`)가 214개 있습니다. 해결 방법은 세 가지입니다.

1. **hen에 classpath 등록(가장 효과가 큼, 코드 수정 없음).** hen의 `pom.xml`에는 jasypt 등 의존성이 있으므로, Windows 쪽에서 `mvn dependency:build-classpath`로 jar 목록을 만들어 프로젝트의 `classpath`에 넣고 다시 분석합니다. 그러면 해석기가 4번을 `org.jasypt…StandardPBEStringEncryptor.decrypt`(외부 메소드)로 풀고, 다른 짐작 관계도 대부분 정확한 관계로 바뀝니다. 다만 RESOLVE부터 다시 돌아야 하므로 새 리비전으로 분석해야 합니다.
2. **`returnTypeOf` 보완(코드 수정).** 반환 타입이 import로 전체 이름을 알 수 있는 외부 타입이면 그 이름을 돌려줍니다. 그러면 jar가 없어도 `EXTERNAL_METHOD` `StandardPBEStringEncryptor.decrypt`(`MEDIUM`)로 남고 이름 짐작으로 넘어가지 않습니다.
3. **`guessByName` 보완(코드 수정, 작음).** 다른 클래스의 private 메소드는 후보에서 뺍니다. 이번 JSP `decrypt` 같은 불가능한 선이 사라집니다.

1번은 Windows 쪽 작업이라 제가 직접 할 수 없습니다. 2번과 3번 코드 수정을 진행할까요?

---

## 질문

진행해. 그리고 올려줘.

---

## 답변

`ReferenceResolver`의 관련 부분과 후보 SQL부터 보겠습니다.

---

## 답변

Compile and run the unit tests.

---

## 답변

Unit tests pass (171). Next I'll start a test instance to confirm the behavior. First check which jar the running 4081 instance is using.

---

## 답변

The running instance is using `target/dstone-knowledge.jar`, so I won't rebuild in place. Instead I'll copy the module into the scratchpad and build the test jar there.

---

## 답변

빌드는 끝났습니다. 시험 인스턴스를 띄우기 전에, 같은 DB를 쓰는 4081 인스턴스에서 지금 돌고 있는 분석이 없는지 확인하겠습니다.

---

## 답변

No analysis is running. Next I'll build a small sample that reproduces this case, then start a test instance on port 4181 to analyze it.

---

## 답변

I'll analyze the sample as soon as the test instance is up.

---

## 답변

두 수정 모두 의도대로 동작합니다. 비교를 위해 같은 표본을 기존 jar(4081)로도 분석해 보겠습니다.

---

## 답변

Fix the field-splitting mistake in the comparison script, and rerun with file IDs replaced by paths.

---

## 답변

The remaining differences are mapper IDs (`S<number>`), which are assigned per revision. Mask those numbers and compare again.

---

## 답변

All six samples match exactly between the old and new jars, so nothing regressed. Next I'll stop the test instance and clean up the revisions created for this check.

---

## 답변

The 4181 instance wasn't stopped by the name pattern, so I'll stop it by PID. Then I'll delete only the revisions I created for this check (71–84).

---

## 답변

Cleanup is done. Next I'll update the RESOLVE description in the docs.

---

## 답변

두 가지를 고쳐 `main`에 커밋하고 GitHub에 푸시했습니다(`1cdb04e4`). 고친 코드는 아직 hen 분석 결과에 반영되지 않았습니다. 아래 "hen 반영 방법"의 순서로 새로 분석해야 합니다.

## 바뀐 점

1. **외부 타입을 돌려주는 호출을 이름으로 짐작하지 않습니다**(`ReferenceResolver.returnTypeOf` 보완).
   - `EncUtil.getEncryptor().decrypt(…)`처럼 앞 호출의 반환 타입이 프로젝트 밖의 타입이면, 그 메소드가 있는 파일의 import로 전체 이름을 만듭니다. import가 없으면 `java.lang`에서 찾습니다.
   - 그 결과 이 호출은 `org.jasypt.encryption.pbe.StandardPBEStringEncryptor.decrypt` 외부 메소드(`MEDIUM`, `via = SCOPE_TYPE`)로 남습니다.
2. **다른 파일의 private 메소드는 이름 짐작 후보에서 뺍니다**(`guessByName`, `SymbolDao.selectMethodCandidates`).
   - private 메소드는 같은 파일 안에서만 부를 수 있기 때문입니다.
   - 후보가 부르는 파일마다 달라지므로 캐시 키에 파일 ID를 넣었습니다.

## 확인한 것

- 단위 테스트 171건이 모두 통과했습니다.
- 실행 중인 4081의 jar는 건드리지 않고, 사본을 따로 빌드해 4181 포트에 시험 인스턴스로 띄웠습니다. 임베딩은 끈 상태입니다.
- hen과 같은 상황을 재현한 작은 표본을 고치기 전 jar와 고친 jar로 각각 분석해 비교했습니다.

| 호출 | 고치기 전(4081) | 고친 뒤(4181) |
|---|---|---|
| `EncUtil.getEncryptor().decrypt(s)` | `decrypt(String)` 3개에 `LOW`(다른 파일의 private 메소드 포함) | 외부 메소드 `StandardPBEStringEncryptor.decrypt`(`MEDIUM`) 1개 |
| 타입을 전혀 모르는 `x.get().decrypt(s)` | 후보 3개(private 포함) | 후보 2개(private 제외) |
| 같은 파일 안의 private `decrypt` 호출 | `HIGH` | `HIGH`(그대로) |

- 기존 검증 표본 6개(`struts-app`, `legacy-app`, `lombok-app`, `cycle-app`, `websquare-app`, `nexacro-app`)를 두 jar로 분석해 관계를 비교했습니다. 리비전마다 새로 매겨지는 파일·매퍼 ID를 빼고 비교하면 차이는 0건입니다.
- 시험 인스턴스는 종료했습니다. 시험용으로 만든 리비전 71~84와 임시 프로젝트 `zz-extcall-test`는 지웠고, 원래 있던 리비전은 건드리지 않았습니다.
- 문서 `docs/11.dstone-knowledge.md`(4.5 RESOLVE)와 `.claude/docs/knowledge-core.md`에 바뀐 규칙을 적었습니다. `docs/temp`는 이번에도 커밋하지 않았습니다.

## hen 반영 방법

이 변경은 RESOLVE 단계에 들어가므로 `rerunFrom: SEMANTIC`으로는 반영되지 않습니다. Windows 쪽에서 다음 순서로 진행해야 합니다.

1. pull합니다.
2. `dstone-knowledge`를 빌드하고 재기동합니다.
3. hen을 **새 리비전으로** 분석합니다.

hen에 `classpath`(Maven 의존성 jar 목록)까지 등록하면, 지금 이름으로 짐작한 관계 214개 가운데 대부분이 해석기로 확실하게 풀립니다.

---

## 질문

올렸어?

---

## 답변

네, 올렸습니다. 커밋 `1cdb04e4`(외부 타입을 돌려주는 호출 뒤의 메소드를 이름으로 짐작하지 않음)는 GitHub `origin/main`에 들어가 있고, 방금 다시 확인했습니다.

- 로컬 `main`과 `origin/main`은 같은 상태입니다. 아직 푸시하지 않은 커밋은 없습니다.
- 커밋하지 않은 파일은 `docs/temp/knowledge분석프로세스.md` 하나뿐입니다. `docs/temp`는 커밋하지 않는 폴더라 그대로 두었습니다.
