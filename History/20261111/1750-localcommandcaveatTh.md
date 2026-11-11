# localcommandcaveatTh

> 생성일시: 2026-11-11 17:50

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="a980">
/docs/11.dstone-knowledge.md 에 아래의 작업을 해줘. 그리고 올려줘.

<작업대상 단락>
처리기별 설명

<작업 내용>
처리기 별로 "저장하는 테이블과 컬럼" 앞에 "샘플 소스" 단락을 만들어줘. 지금 샘플소스가 있는 처리기도 있는 반면에 없는 처리기들도 있어.
또한 종류별로 동작이 다른 처리기라면 샘플소스도 종류별로 케이스를 만들어줘.
</pasted_content id="a980">

---

## 답변

CSV 블록 안의 `#` 설명은 CSV 문법이 아니라서 블록 밖으로 빼고, 8번 `sql:` 규칙 예시 문법과 14번 주석을 찾는 순서가 실제 코드와 맞는지 확인합니다.

---

## 답변

처리기별 설명 첫 문단에도 샘플 소스 이야기를 넣고, 17개 모두 "샘플 소스" 단락 바로 뒤에 "저장하는 테이블과 컬럼"이 오는지 확인합니다.

---

## 답변

17개 모두 맞게 들어갔습니다. 커밋하고 푸시합니다.

---

## 답변

`docs/11.dstone-knowledge.md`의 "처리기별 설명"에서 처리기 17개 모두 "저장하는 테이블과 컬럼" 바로 앞에 **샘플 소스** 단락을 넣었고, 커밋(`6a8a070f`)해서 `origin/main`에 푸시했습니다. 17개 모두 순서가 맞는지 스크립트로 확인했습니다.

- **샘플이 원래 있던 처리기**: 3 LayerResolver, 4 EntryFinder, 5 WiringMarker, 6 JpaQueryReader, 8 SqlLinker, 10 ViewLinker는 기존 샘플을 이 단락으로 옮겼습니다. 그리고 종류별 케이스를 더 채웠습니다.
  - 3번: `BY_ANNOTATION`, `BY_EXTENDS`, `BY_NAME`, 앞 단계 규칙이 이기는 경우, JSP의 `VIEW`
  - 4번: HTTP(VERB·ALL·상수·물려받은 주소), 애노테이션, 설정 XML, `MAIN`·`JSP`, 상속
  - 5번: `inject`, 메소드에 붙은 `proxy`, 클래스에 붙은 `proxy`
  - 6번: 엔티티·naming, `@Query`(JPQL·native), `@NamedQuery`, 메소드 이름 쿼리
  - 8번: 따옴표 글자, 상수 계산, 잇지 않는 경우와 `UNRESOLVED`, 생성자, 타입·`name` 틀 규칙
  - 10번: 잇는 경우와 잇지 않는 경우
- **샘플이 없던 처리기**: 나머지 11개는 새로 썼습니다.
  - 1번: web.xml, 프로젝트 밖 서블릿, 필터·리스너, `@WebServlet`
  - 2번: Struts1, `DispatchAction`, Struts2
  - 7번: 매퍼 인터페이스, `include`, 정규식으로 찾는 경우
  - 9번: 메소드 안 문자열, 다른 클래스의 상수, `createQuery`, JPA statement
  - 11번: include, title, PATH_TEXT, SCRIPT_SRC
  - 12번: WebSquare·Nexacro `call`, `field`, `open`, 공통 스크립트, 메소드 수에 따른 결과, PATH_TEXT
  - 13번: JS 메뉴 목록, XML 메뉴 목록, 제목이 이미 있는 화면
  - 14번: JPA 엔티티, VO 필드
  - 15번: 첫 줄, `@Description`, 버리는 글, 파일이 바뀐 경우
  - 16·17번: CSV 모양별, 설정이 없을 때

**함께 고친 것**
- 14번 SourceNamePlugin의 "호출 순서"에 적힌 주석 찾는 순서가 실제 코드와 달라서 코드에 맞췄습니다. 실제 순서는 설명 애노테이션 → 줄 끝 주석 → 바로 위 JavaDoc·블록 주석이고, 바로 위 `//` 주석은 쓰지 않습니다.
- 처리기별 설명의 첫 문단에 샘플 소스를 적었다는 말을 넣었습니다.
