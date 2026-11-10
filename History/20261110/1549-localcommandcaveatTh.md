# localcommandcaveatTh

> 생성일시: 2026-11-10 15:49

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="d0fe">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<내용>
### 0.16 자주 헷갈리는 것

| 질문                         | 답                                                                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| LLM이 코드를 읽고 분석하는 것 아닌가     | 아니다. 분석은 전부 정해진 규칙(파서와 SQL)으로 한다. 이 모듈에는 채팅 모델이 없다. 같은 소스를 넣으면 언제나 같은 결과가 나온다                                     |
| 벡터 DB에 소스를 넣는 RAG와 무엇이 다른가 | 소스를 그대로 자르지 않는다. 분석으로 알아낸 사실(주소, 부르는 곳, 테이블)을 글로 적어 그것을 넣는다. 그리고 검색 말고도 호출 관계·영향도를 정확히 조회할 수 있다                   |
| 대상 프로젝트를 빌드해야 하나           | 아니다. 소스 폴더만 있으면 된다. 라이브러리 jar가 있으면 더 정확해질 뿐이다                                                                     |
| Spring 프로젝트만 되나            | 아니다. ①~⑦은 프레임워크를 모른다. ⑧의 규칙 파일과 처리기가 Spring, 서블릿, Struts, MyBatis/iBATIS, JSP를 각각 맡고, 맞는 것이 없어도 호출 관계까지는 정상으로 나온다 |
| 분석이 끝났는데 검색이 안 된다          | 임베딩이 아직 돌고 있거나 Ollama가 내려가 있다. `GET /api/revisions/{id}/rag`를 본다. 질문에 클래스·메소드 이름을 넣으면 임베딩 없이도 찾힌다                 |
| 결과에 `LOW`가 붙어 있다           | 이름만 보고 짐작한 것이다. 절반은 틀린다고 보고 참고로만 쓴다. 대상 프로젝트의 jar를 `classpath`로 주면 줄어든다                                           |
| 영향도 결과가 "전부"인가             | 아니다. 저장된 관계만 따라간다. 리플렉션, 문자열로 조립한 주소, Java 코드 안에 문자열로 적은 SQL은 잇지 못한다(4.10절과 4.11절의 "아직 다루지 않는 것")                 |
| 리비전이 계속 쌓이지 않나             | 프로젝트마다 분석이 끝난 리비전을 최근 5개만 남기고 자동으로 지운다(4.16절)                                                                     |
| 분석 도중 서버가 죽으면              | 다시 띄우고 같은 라벨로 분석을 시작하면 끝난 단계와 끝난 파일을 건너뛰고 이어서 한다                                                                  |

<질문>
Java 코드 안에 문자열로 적은 SQL은 잇지 못한다 라고 나오는데 이부분 구현된게 아닌가?
</pasted_content id="d0fe">

---

## 답변

맞습니다. 이미 구현돼 있고, 0.16의 그 문장이 갱신되지 않은 것입니다.

**구현 내용 (2026-11-09, 분석기 1.2)**
- `analysis/pass03/declare/JavaSqlCollector.java`: DECLARE 단계에서 메소드 본문을 파싱하면서 문자열로 적은 SQL을 찾습니다. 다음 형태를 따라갑니다.
  - `"SELECT …" + …`처럼 이어 붙인 문자열
  - `sql += …`
  - `StringBuilder`/`StringBuffer`의 `append`
  - 같은 클래스의 `final` 상수

  찾은 SQL은 `analysis_mapper`에 `mapper_type = JAVA`로 저장합니다.
- `analysis/pass08/semantic/sql/JavaSqlLinker.java`: SEMANTIC 단계에서 그 SQL을 메소드·테이블과 연결합니다(`via = JAVA_STRING` / `JAVA_CONSTANT`).
- 그래서 영향도 분석, 테이블 사용처, 메소드 문서의 "실행하는 SQL"이 MyBatis 매퍼와 똑같이 이어집니다. `legacy-app`의 `OrderDAO` 같은 직접 JDBC 코드도 여기에 포함됩니다.
- 문서의 4.3절(1155행), 4.7절 표(1282행), 4.7.9 지원 범위(2038행), M8 이후 기록(3850행)에는 이미 "된다"고 적혀 있습니다.

**갱신이 안 된 곳 (기능을 넣기 전 상태 그대로)**
| 행 | 내용 |
|---|---|
| 670 | "사슬이 끊기는 곳" 표: "Java 안에 문자열로 적은 SQL → 잇지 않는다" |
| 895 | 0.16 "영향도 결과가 '전부'인가" |
| 2267 | 4.10 "아직 다루지 않는 것"의 SQL 항목: "JDBC 직접 사용" |
| 2295 | 4.11 영향도 분석의 한계 |
| 3895 | "M6에서 남긴 한계": `legacy-app`은 테이블에서 출발할 수 없다 |
| 3915 | "노드 맵에서 남긴 한계": 순수 JDBC는 테이블 노드가 없다 |

**지금도 연결하지 못하는 것**
- 파라미터나 메소드 반환값으로 받은 SQL 조각은 `?`로 남습니다. 그 자리에 테이블 이름이 들어가면 그 테이블은 찾지 못합니다.
- `${tableName}`처럼 실행할 때 테이블 이름이 정해지는 SQL
- Criteria API, QueryDSL처럼 코드로 만드는 쿼리
- MyBatis `@Select` 애노테이션: 2267행에 "다루지 않는다"고 함께 적혀 있는데, 코드를 검색해 보니 처리하는 곳을 찾지 못했습니다. 이 부분은 여전히 한계로 보입니다.

위 6곳을 고칠까요? 0.16은 "리플렉션, 문자열로 조립한 주소, 파라미터로 받은 SQL 조각은 잇지 못한다"로 바꾸고, 나머지 5곳도 현재 상태에 맞게 손보면 됩니다.
