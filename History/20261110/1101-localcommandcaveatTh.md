# localcommandcaveatTh

> 생성일시: 2026-11-10 11:01

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="8a5e">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<내용>
**단계별 저장 테이블 한눈에 보기**(`cms4_prd`, 리비전 95)

| 단계              | 새로 쓰는 테이블(행)                                                                                                                          | 고쳐 쓰는 테이블                                                |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| ① SCAN          | `analysis_file`(1,331)                                                                                                                | —                                                        |
| ② CARRY         | 증분 분석일 때만: ③·④의 테이블에 앞 리비전 행을 복사                                                                                                      | `analysis_file.carried_from_file_id`                     |
| ③ DECLARE       | `analysis_symbol`(256), `analysis_method`(6,027), `analysis_field`(2,076), `analysis_annotation`(1,574), `analysis_reference`(39,480) | `analysis_file`(파싱 결과)                                   |
| ④ RESOURCE      | `analysis_mapper`(788), `analysis_config`(5), `analysis_resource`(92)                                                                 | —                                                        |
| ⑤ CARRY_RESOLVE | 증분 분석일 때만: 앞 리비전의 `analysis_relation` 복사                                                                                              | —                                                        |
| ⑥ RESOLVE       | `analysis_relation`(40,065)                                                                                                           | `analysis_reference.status`                              |
| ⑦ LINK          | `analysis_relation`(68), `analysis_metric`(7)                                                                                         | —                                                        |
| ⑧ SEMANTIC      | `analysis_endpoint`(707), `analysis_relation`(3,505), `analysis_resource`(2,415)                                                      | `analysis_symbol.layer`, `analysis_method.proxy_related` |
| ⑨ DOCUMENT      | `rag_document`(3,625), `rag_chunk`(4,000), `rag_embedding`(새 해시만, `PENDING`)                                                          | —                                                        |
| 모든 단계           | `analysis_revision_pass`(9), `analysis_file_pass`(1,421), `analysis_error`(0)                                                         | —                                                        |

<질문>
1. ⑧ SEMANTIC 에서 analysis_resource 에 입력하는 내용들이 뭐지?
</pasted_content id="8a5e">

---

## 답변

Now find the callers and resourceType values.

---

## 답변

⑧ SEMANTIC이 `analysis_resource`에 넣는 2,415행은 **코드 구조가 아닌 부가 정보**입니다. 메소드 기능명, 컬럼과 테이블의 한글명, 화면명, web.xml의 필터·리스너 설정, 분석에 쓴 규칙 파일 목록이 여기에 들어갑니다. 리비전 95를 DB에서 직접 세어 보니 아래 행 수의 합이 정확히 2,415였습니다.

| resource_type | 행 수 | 넣는 곳 | 내용 |
|---|---|---|---|
| `METHOD_TITLE` | 1,818 | `MethodTitlePlugin` | 메소드 기능명. 메소드 바로 위 주석의 첫 줄이나 `@Description` 값입니다. name은 메소드 식별 키, value는 기능명(예: "월별 계약 입력")이고, 노드 맵의 메소드 상세 화면에 나옵니다. |
| `SOURCE_NAME` | 551 | `SourceNamePlugin` | Java 소스에서 찾은 **컬럼 한글명**. JPA 엔티티 필드에서 찾으면 `via=ENTITY`(테이블을 앎)이고, `mbNm`처럼 이름이 컬럼과 같은 VO 필드에서 찾으면 `via=NAME`(테이블을 모름)입니다. DB 코멘트, DDL, SQL 주석 어디에도 한글명이 없을 때 마지막으로 쓰는 근거입니다. |
| `SCREEN_NAME` | 16 | `SemanticDao.insertScreenName` (화면 처리 단계) | 화면 파일의 `title` 같은 태그나 메뉴 목록에서 찾은 화면명. name은 화면 경로, value는 화면명입니다. |
| `FILTER_MAPPING` | 9 | `ServletEntries` | web.xml의 filter-mapping. name은 필터 이름, value는 URL 패턴입니다. |
| `SERVLET_MAPPING` | 7 | `ServletEntries` | web.xml의 servlet-mapping. 같은 정보로 SERVLET 진입점(`analysis_endpoint`)도 따로 만듭니다. |
| `RULE_SET` | 7 | `SemanticPass` | 이 리비전을 분석할 때 적용한 규칙 묶음(java, servlet, spring, mybatis, fallback 등)과 각 묶음의 해시, 순서, 규칙 수. 규칙을 고친 뒤 `rerunFrom=SEMANTIC`으로 다시 분석했는지 확인할 때 씁니다. |
| `LISTENER` | 4 | `ServletEntries` | web.xml의 listener 클래스. |
| `SEMANTIC_STATUS` | 3 | 각 플러그인 | 플러그인별 실행 결과. 이번 리비전은 `METHOD_TITLE=OK`(파일 231개에서 1,818건), `SCREEN_DICTIONARY`와 `TABLE_DICTIONARY`는 사전 파일이 없어 `NOT_CONFIGURED`였습니다. |

이번 리비전에 0건인 타입도 있습니다. 엔티티 클래스의 테이블 한글명(`SOURCE_TABLE_NAME`), 기능명을 읽지 못한 파일과 그 까닭(`METHOD_TITLE_SKIP`), 사전 파일에서 읽은 한글명(`DICT_SCREEN_NAME`, `DICT_TABLE_NAME`, `DICT_COLUMN_NAME`)입니다. 이 프로젝트에는 해당 소스나 사전 파일이 없어서 행이 생기지 않았습니다.

④ RESOURCE가 같은 테이블에 넣는 92행은 `DEPENDENCY`(52), `SPRING_BEAN`(37), `COMPONENT_SCAN`(3)으로, 빌드 파일과 Spring 설정에서 읽은 값입니다. SEMANTIC이 넣는 타입 목록은 `src/main/resources/sqlmap/analysis/SemanticDao.xml:93`에 있습니다. SEMANTIC을 다시 돌리면 이 목록에 든 타입만 지우고 새로 씁니다.

문서 표의 `analysis_resource`(2,415)에 "기능명·한글명·화면명·web.xml 설정·규칙 묶음" 같은 짧은 설명을 붙일까요?
