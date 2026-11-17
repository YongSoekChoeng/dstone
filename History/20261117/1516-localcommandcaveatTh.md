# localcommandcaveatTh

> 생성일시: 2026-11-17 15:16

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
