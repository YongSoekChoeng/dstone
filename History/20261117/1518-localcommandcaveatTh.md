# localcommandcaveatTh

> 생성일시: 2026-11-17 15:18

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="50d1">
dstone-knowledge 에서 
/conf/rules 의 내용을 백엔드와 연동하는 부분과 함께 아주 쉽고 직관적이고 자세하게 풀어서 아래의 아웃풋 파일로 작성해줘. 그리고 올려줘.
직관적이고 자세하고 친절하게 만들어줘.

<상세요청>
1. Repomix  를 활용해줘.
2. rule 별로 아래의 루트를 한눈에 볼 수 있었으면 좋겠어.
    - rule적용대상소스(호출형태포함) -> 적용되는rule -> 서버모듈 -> 테이블(저장형태샘플) -> 조회쿼리 -> dstone-boot의 knowledge 에서 어떻게 작용하는지.
3. (가장중요)rule 추가의 경우가 발생했을 때 진행할 작업 길라잡이.

<아웃풋>
/docs/11.dstone-knowledge-rules.md
</pasted_content id="50d1">

---

## 답변

요청 확인했습니다. 먼저 `conf/rules` 구조와 관련 백엔드 코드를 살펴보겠습니다.

---

## 답변

규칙 파일은 다 봤습니다. 이제 규칙을 읽고 처리하는 백엔드 코드를 찾겠습니다.

---

## 답변

Repomix로 규칙 엔진 코드를 한 파일로 묶어서 읽겠습니다.

---

## 답변

전체는 약 20만 토큰이라 구조부터 보고 핵심 파일만 골라 읽겠습니다.

---

## 답변

이제 처리기들이 실제로 어느 테이블에 무엇을 쓰는지 SQL 매퍼에서 확인하겠습니다.

---

## 답변

백엔드 쪽(규칙 → 처리기 → 테이블)은 파악했습니다. 이제 조회 API와 dstone-boot Knowledge 화면 쪽을 확인하겠습니다.

---

## 답변

조회 API(그래프·영향도·진단)가 규칙 결과를 어떻게 쓰는지 마저 확인하고 있습니다.

---

## 답변

표본 행을 실제 DB에서 뽑아 "저장 형태 샘플"을 정확히 채우겠습니다.

---

## 답변

Now I'll pull one sample row per rule.

---

## 답변

마지막으로 boot 화면의 탭 이름과 각 화면이 쓰는 API를 확인하겠습니다.

---

## 답변

문서에 정확한 줄 번호를 쓰려고 규칙 파일 줄 번호를 확인합니다.

---

## 답변

자료 조사는 끝났고, 이제 문서를 쓰겠습니다. 화면 이름 하나만 더 확인합니다.

---

## 답변

문서를 작성합니다. 실제 DB(2026-11-17 기준 리비전)에서 뽑은 행을 저장 형태 샘플로 넣겠습니다.

---

## 답변

문서를 썼습니다. 문서에 적은 API 파라미터와 키 이름이 실제 코드와 맞는지 검증하겠습니다.

---

## 답변

Two fixes: the impact API parameter (`table=`) and the candidates response key (`callArguments`, `screenRules`).

---

## 답변

색인 문서들(`docs/README.md`, `docs/01.index.md`, docs/11 3장, rules README)에 새 문서 링크를 답니다.

---

## 답변

링크 정리는 끝났습니다. 커밋 전에 번역투 표현이 남았는지 확인합니다(한국어 작성 규칙).

---

## 답변

검사 통과했습니다. 이제 커밋하고 원격에 올리겠습니다.

---

## 답변

`docs/11.dstone-knowledge-rules.md`를 새로 쓰고 `main`에 커밋·푸시했습니다(`e5897e92`). 이 저장소가 원래 `main`에 바로 커밋하는 방식이라 그대로 따랐습니다.

**작성 방법**
- **Repomix**로 규칙 파일, SEMANTIC 처리기 전체, 규칙 API, `SemanticDao.xml`·`RuleDao.xml`(68개 파일, 약 20만 토큰)을 한 파일로 묶어 읽었습니다. 문서 첫머리에 같은 명령을 넣어 두어 코드가 바뀌면 다시 묶어 맞출 수 있습니다.
- 저장 형태 샘플은 지어낸 값이 아닙니다. 로컬 `dstone_knowledge` DB의 실제 리비전(testApp, testReactApp, H-Energy, anybiz_prd, HI-Japan, dstone-knowledge)에서 뽑은 행입니다.

**문서 구성**
1. **한 장으로 보기**: 소스 → 규칙 → `SemanticPass` → `analysis_*` → API → boot 화면 흐름도
2. **우선순위**: 폴더별 우선순위와 각 규칙 파일이 하는 일
3. **모든 규칙이 함께 지나는 길**: 기동 검사부터 규칙 기록까지의 공통 흐름, 처리기 실행 순서와 그 순서인 까닭, boot 프록시 경로
4. **규칙별 루트 요약표**: 규칙 15종을 한 줄씩, 요청하신 6단계로 정리
5. **규칙별 상세 루트**: 규칙마다 ①소스 ②규칙(파일:줄) ③처리기 호출 흐름 ④테이블과 실제 행 ⑤조회 SQL·API ⑥Knowledge 화면에서 보이는 곳
6. **규칙 추가 길잡이**(가장 중요)
   - 먼저 "규칙 한 줄로 되는지, 문법 확장인지, 새 처리기인지" 가리는 결정 흐름도
   - 규칙 한 줄 추가 10단계와 각 단계의 `curl` 예시
   - 상황별 레시피 15가지("이럴 땐 이 절에 이렇게")와 자주 틀리는 곳
   - 문법을 넓힐 때 고칠 파일 목록: 파서, `Rule`, `ProjectRules`, 처리기, `SemanticPass`, `clear()`, 조회 서비스, boot 화면 문구, 테스트
   - 결과 확인 SQL 모음과 마무리 체크리스트

**문서를 읽을 때 알아 둘 점**
- **JPA(5.9)**: 지금 DB에 JPA를 쓰는 분석 프로젝트가 없어 실제 행을 넣지 못했습니다. 문서에는 "구조 설명용 예시"라고 표시했습니다.
- **`proxy:` 규칙**: 결과(`proxy_related`)가 DB에는 남지만 지금 boot 화면에서는 보여 주는 곳이 없습니다. 문서에도 그대로 적었습니다.
- **링크 추가**: 새 문서를 `docs/README.md`, `docs/01.index.md`, `docs/11` 3장 머리, `conf/rules/README.md`에서 찾아갈 수 있게 링크를 달았습니다.

---

## 질문

testReactApp, testVueApp 의 분석률이 왜이리 낮은가?

---

## 답변

먼저 화면에서 말하는 "분석률"이 어떤 값인지와, 두 프로젝트의 실제 수치를 확인하겠습니다.

---

## 답변

"분석률"은 대시보드의 **호출 해석률**로 보입니다(testReactApp 75.0%). 풀지 못한 호출이 왜 생겼는지 이유별로 보겠습니다.

---

## 답변

Small project (200 calls); 46 heuristic + 4 unresolved bring it down to 75%. Let's see what the 46 "짐작" calls are.

---

## 답변

거의 모두 컨트롤러가 서비스를 부르는 평범한 호출입니다. 이런 호출이 왜 짐작으로 처리됐는지 소스와 프로젝트 설정을 확인합니다.

---

## 답변

원인이 보입니다. testApp에는 `WEB-INF/lib`에 jar가 있고 React 쪽은 없습니다. 분석기가 jar를 어디서 찾는지 확인합니다.

---

## 답변

두 프로젝트의 "분석률"(대시보드의 **호출 해석률**)이 낮은 원인은 **분석기가 라이브러리 jar를 하나도 찾지 못한 것**입니다. 소스나 규칙의 문제는 아닙니다. 프론트(React/Vue)와도 관계가 없습니다. 두 프로젝트는 백엔드 Java 코드가 사실상 같아서 수치도 똑같이 나옵니다.

**숫자로 보면** (리비전 235 testReactApp, 234 testVueApp 모두 같음)

| 상태 | 건수 | |
|---|---|---|
| 풀림(RESOLVED) | 107 | |
| 프로젝트 밖으로 풀림(EXTERNAL) | 43 | |
| **짐작으로 이음(HEURISTIC)** | **46** | 해석률을 깎는 주원인 |
| 못 풂(UNRESOLVED) | 4 | |

(107+43)/200 = **75.0%**입니다. 전체 호출이 200개뿐이라 50개만 못 풀어도 25%포인트가 떨어집니다.

**왜 짐작으로 이었나**

짐작 46건은 거의 다 컨트롤러가 서비스를 부르는 평범한 호출입니다.

```java
public List<Board> list(@RequestParam String title, HttpSession session) {
    return boardService.getBoards(SessionUtil.getLoginUser(session), title);
}
```

1. `HttpSession`은 `jakarta.servlet` jar에 있습니다. 분석기에 jar가 없으면 그 타입을 모릅니다.
2. 그래서 인자 `SessionUtil.getLoginUser(session)`의 타입을 정하지 못합니다.
3. 인자 타입을 모르니 `boardService.getBoards(...)`를 타입으로 확정하지 못하고, 이름만 보고 이었습니다(HEURISTIC).

컨트롤러가 모두 `HttpSession`을 받기 때문에 이 패턴이 반복됩니다. `ResponseEntity.status(...).body(...)` 같은 Spring 호출도 같은 이유로 짐작 처리됐습니다.

**testApp은 왜 99%대인가**

testApp도 등록 정보의 classpath는 비어 있습니다. 대신 `src/main/webapp/WEB-INF/lib`에 jar가 들어 있고, 분석기(`ClasspathResolver`)가 프로젝트 폴더 안의 jar를 자동으로 찾아 씁니다.

React/Vue 쪽은 Spring Boot 프로젝트라 그런 폴더가 없습니다. jar는 `target/testReact.jar` 하나뿐인데, 라이브러리 43개가 `BOOT-INF/lib/` 안에 다시 jar로 들어 있습니다. 분석기는 jar 안의 jar를 읽지 못합니다. 그래서 실제로는 쓸 수 있는 jar가 0개인 상태로 분석됐습니다.

**고치는 방법**

1. **바로 할 수 있는 것 (Java 수정 없음)**: 의존 jar를 프로젝트 폴더 안에 꺼내 두고 **RESOLVE부터** 다시 분석합니다.
   ```bash
   cd /app/testReactApp && mvn dependency:copy-dependencies   # target/dependency/*.jar
   cd /app/testVueApp   && mvn dependency:copy-dependencies
   ```
   폴더 안의 jar는 자동으로 잡힙니다. 프로젝트 등록 정보의 classpath에 `mvn dependency:build-classpath` 결과를 넣어도 됩니다. 다만 지금은 오프라인 모드에서 `maven-dependency-plugin`이 없어서 실패했으므로, Maven Central에 한 번 접속해야 합니다.
2. **근본적인 해결 (분석기 개발)**: `ClasspathResolver`가 Spring Boot fat jar의 `BOOT-INF/lib/*.jar`도 꺼내 읽게 합니다. 그러면 Boot 프로젝트는 빌드 결과물만 있어도 jar 설정이 필요 없습니다.

참고로 적용 진단에 같이 나온 "지원하지 않는 소스 24개"는 이 해석률과는 별개 문제입니다.

1번은 Maven Central에서 플러그인을 내려받아야 해서 아직 실행하지 않았습니다. 1번을 실행하고 재분석해서 해석률을 다시 확인할까요, 아니면 2번(fat jar 지원)을 개발할까요?
