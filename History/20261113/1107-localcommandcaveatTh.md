# localcommandcaveatTh

> 생성일시: 2026-11-13 11:07

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="01a2">
아래의 단계별로 테이블(CRUD) 와 간략한 설명을 기술해줘.
| 단계              | 테이블(CRUD)  | 기능
| --------------- | 
| ① SCAN          | ...
| ② CARRY         |
| ③ DECLARE       |
| ④ RESOURCE      |
| ⑤ CARRY_RESOLVE |
| ⑥ RESOLVE       |
| ⑦ LINK          |
| ⑧ SEMANTIC      |
| ⑨ DOCUMENT      |
</pasted_content id="01a2">

---

## 답변

There's already a section 2.10. Let me read it and the per-pass sections briefly.

---

## 답변

`docs/11.dstone-knowledge.md`의 0.4절과 2.10절 표, 그리고 각 Pass 코드의 DAO 호출을 대조해서 정리했습니다. 표기는 **C** = 행 추가, **R** = 조회, **U** = 컬럼 수정, **D** = 행 삭제, **복사** = 앞 리비전에서 행 복사입니다. 관리 테이블(`analysis_job`, `analysis_revision_pass`, `analysis_file_pass`, `analysis_error`)은 표에서 뺐습니다.

| 단계 | 테이블(CRUD) | 기능 |
|---|---|---|
| ① SCAN | `analysis_file` **C** | 소스 폴더의 파일을 모두 훑어 목록을 만들고 종류(Java, JSP, XML, properties 등)를 나눕니다. 이후 모든 단계가 이 목록을 기준으로 돕니다. |
| ② CARRY<br/>(증분 분석만) | `analysis_revision` **R**<br/>`analysis_file` **U** (`carried_from_file_id`, 파싱 결과)<br/>`analysis_symbol` / `_method` / `_field` / `_annotation` / `_reference` **복사**<br/>`analysis_mapper` / `_config` / `_resource` **복사** | 앞 리비전과 비교해 바뀌지 않은 파일의 선언과 자원 행을 그대로 옮겨 옵니다. 이 파일들은 다시 파싱하지 않습니다. |
| ③ DECLARE | `analysis_file` **R/U** (`parse_status`, `package_name` 등)<br/>`analysis_symbol` / `_method` / `_field` / `_annotation` **D·C**<br/>`analysis_reference` **D·C**<br/>`analysis_mapper` **D·C** (`JAVA`: Java 문자열 SQL) | Java 파일과 JSP 안의 Java를 한 파일씩 파싱해서 클래스, 메소드, 필드, 애노테이션을 기록합니다. 호출이나 타입 참조는 대상을 풀지 않고 이름만 적어 둡니다. |
| ④ RESOURCE | `analysis_file` **R**<br/>`analysis_mapper` **D·C** (`MYBATIS`, `IBATIS`, `QUERY_XML`)<br/>`analysis_config` **D·C**<br/>`analysis_resource` **D·C** (빈, 의존성, DDL) | Java가 아닌 파일을 읽습니다. 대상은 MyBatis/iBATIS 매퍼 XML의 SQL, properties·YAML 설정, 빌드 파일 의존성, DDL입니다. |
| ⑤ CARRY_RESOLVE<br/>(증분 분석만) | `analysis_relation` **복사**<br/>`analysis_reference` **U** (`status` → `PENDING`) | 앞 리비전에서 풀어 둔 관계 가운데 옮겨도 안전한 것만 복사합니다. 바뀐 파일의 영향을 받는 참조는 다시 풀도록 `PENDING`으로 되돌립니다. |
| ⑥ RESOLVE | `analysis_symbol` / `_method` / `_field` / `_reference` **R**<br/>`analysis_reference` **U** (`status`, `fail_reason`)<br/>`analysis_relation` **D·C** (`CALLS` 등 7종) | Java 파일을 다시 파싱하고 DB의 선언 정보를 조회해서 ③에서 이름만 적어 둔 참조의 실제 대상을 찾습니다. 그 결과가 "누가 누구를 부르나" 관계가 됩니다. |
| ⑦ LINK | `analysis_symbol` / `_method` **R**<br/>`analysis_relation` **D·C** (`OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION`)<br/>`analysis_metric` **D·C** | 파싱 없이 SQL만으로 재정의 관계와 인터페이스→구현 관계를 잇고, 품질 지표를 계산합니다. |
| ⑧ SEMANTIC | `analysis_*` 전반 **R** (+ 규칙 파일 `conf/rules`, `web.xml`, Struts 설정)<br/>`analysis_symbol` **U** (`layer`, `layer_confidence`, `properties_json`)<br/>`analysis_method` **U** (`proxy_related`)<br/>`analysis_endpoint` **D·C/U** (`method_id`)<br/>`analysis_relation` **D·C** (`INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`, `REQUESTS`, `INCLUDES`)<br/>`analysis_mapper` **D·C** (`JPA`), **U** (`method_id`)<br/>`analysis_resource` **D·C** (매핑, 화면명, 기능명, 이름 사전, 규칙 기록) | 규칙과 처리기(`LayerResolver`, `EntryFinder`, `SqlLinker` 등)로 의미를 붙입니다. 계층을 정하고, 진입점(URL·서블릿·JSP·거래)을 찾고, 주입 관계와 SQL·테이블·화면 연결을 만듭니다. |
| ⑨ DOCUMENT | `analysis_*` 전반 **R** (+ 소스의 해당 줄)<br/>`rag_document`, `rag_chunk` **D·C**<br/>`rag_embedding` **C** (새 `content_hash`만, 대기열) | 분석 결과를 파일 단위 설명 문서로 풀어 쓰고 청크로 자릅니다. 임베딩 대기열에도 올리며, 실제 임베딩 계산은 Job과 별도로 도는 `EmbeddingWorker`가 맡습니다. |

- 테이블 쓰기(C/U/D/복사)는 문서 2.10절 표와 같습니다. 조회(R)는 2.10절에 없어서 각 Pass가 부르는 DAO 메소드를 보고 덧붙였습니다.
- ⑧과 ⑨의 "`analysis_*` 전반 R"은 모든 테이블을 뜻하지 않고, 이 두 단계가 앞 단계의 결과 테이블을 두루 조회한다는 뜻입니다.

원하시면 이 R 열을 2.10절 표에 추가해 두겠습니다.

---

## 질문

/docs/11.dstone-knowledge.md > 0.4 분석 공정 9단계 단락의 표를 이 내용으로 교체해줘.

---

## 답변

`docs/11.dstone-knowledge.md`의 0.4절 표를 단계, 테이블(CRUD), 기능 세 열짜리 새 표로 바꿨습니다.

- **범례 추가:** 표 위에 C·R·U·D·복사 기호 설명과 관리 테이블을 뺐다는 안내, 그리고 2.10절 표로 가는 안내를 한 줄 넣었습니다.
- **각주 추가:** 표 아래에 ⑧·⑨의 "`analysis_*` 전반 R"이 앞 단계 결과 테이블을 두루 읽는다는 뜻이라는 설명을 붙였습니다.
- **문체:** 이 문서 본문과 맞추려고 문장 끝을 "~한다"체로 고쳤습니다.
- **빠진 열:** 원래 표에 있던 "비유하면"과 "담당 클래스" 열은 없어졌습니다. 담당 클래스는 2.0절 표에도 있어서 정보가 사라지지는 않습니다. 비유가 아쉬우시면 기능 열 앞에 다시 넣어 드리겠습니다.

커밋은 하지 않았습니다.
