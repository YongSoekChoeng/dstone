# localcommandcaveatTh

> 생성일시: 2026-11-10 09:23

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

너는 넥사크로나 웹스퀘어를 이해하고있어?

---

## 답변

네, 개념과 구조는 잘 알고 있습니다. 다만 두 제품 모두 상용 솔루션이라 공개 자료가 적습니다. 그래서 버전별 세부 API나 사내에서 따로 만든 공통 라이브러리 동작은 실제 코드를 보고 확인해야 정확합니다.

**넥사크로 (Nexacro, 투비소프트)**
- 화면은 `.xfdl`(XML) 파일에 Form, 컴포넌트, Dataset을 선언하고, 스크립트는 그 안의 `<Script>` 블록이나 `.xjs`에 둡니다.
- 서버와는 `this.transaction(svcId, url, inDatasets, outDatasets, args, callback)`으로 통신합니다. 이때 Dataset을 SSV나 XML 형식으로 주고받습니다.
- 화면은 `this.go()`, `popupdiv`, `ChildFrame`/`showModal`, `gfn_*` 같은 공통 함수로 엽니다. 서비스 URL은 `TypeDefinition.xml`의 prefix(예: `svc::`)로 풀어냅니다.
- 컴포넌트는 `Grid`·`Combo`·`Edit` 등을 쓰고, `BindItem`으로 Dataset 컬럼과 연결합니다.

**웹스퀘어 (WebSquare, 인스웨이브)**
- 화면은 XHTML 기반 `.xml` 파일입니다. `w2:` 네임스페이스 컴포넌트를 쓰고, 데이터는 `w2:dataCollection` 안의 `dataList`/`dataMap`에 둡니다.
- 서버 호출은 `xf:submission`(`ref`, `target`, `action`)을 선언한 뒤 `$p.executeSubmission()`이나 `com.sbm.execute` 같은 공통 래퍼로 실행합니다.
- 화면은 `$p.openPopup`, `wfm_*`(WFrame), `$p.url`, 공통 `com.win.openPopup` 등으로 엽니다. 스크립트는 `<script>` 블록이나 `scwin.*` 함수에 있습니다.
- 바인딩은 `ref="data:dlt_xxx.col"` 형식을 씁니다.

**이 저장소에서는**
`dstone-knowledge`가 이미 두 형식의 화면을 분석합니다. 화면, 서버 호출(TRANSACTION 엔드포인트), 화면 오픈 관계, 필드 바인딩, 공통 스크립트를 추출해서 Knowledge Graph에 연결하는 방식입니다. 관련 규칙과 설계는 `.claude/docs/knowledge-screens-rich-client.md`와 `dstone-knowledge/conf/rules/*.yml`에 있습니다. 프로젝트마다 쓰는 공통 함수 이름이 달라서, 그런 차이는 규칙 파일로 맞추도록 되어 있습니다.

넥사크로나 웹스퀘어로 하실 작업(분석 규칙 추가, 특정 화면 해석, 코드 작성 등)이 있으면 말씀해 주세요. 관련 파일을 열어 보고 진행하겠습니다.

---

## 질문

그렇다면 클로드코드를 이용해서 개발을 진행한다고 할 때 넥사크로나 웹스퀘어로 구성된 어플도 진행할 수 있다는 말인가?

---

## 답변

네, 할 수 있습니다. 다만 잘하는 일과 한계가 분명히 나뉩니다.

## 가능한 이유

두 제품 모두 화면 소스가 텍스트 파일입니다.

- 넥사크로: `.xfdl`(XML), `.xjs`(스크립트), `TypeDefinition.xml`, `.xadl`
- 웹스퀘어: 화면 `.xml`(XHTML), 공통 `.js`

Claude Code는 이 파일들을 읽고 고치고 새로 만들 수 있습니다. 화면 뒤의 서버 쪽(Spring Controller, Service, MyBatis SQL)은 원래 잘하는 영역입니다. 그래서 화면에서 DB까지 한 기능을 통째로 개발할 수 있습니다.

## 잘하는 일

- 기존 화면을 본보기로 비슷한 화면 만들기 (조회, 저장, 팝업)
- Dataset/dataList 정의, `transaction`/`submission` 호출, 콜백 처리
- 그리드 컬럼 추가, 바인딩 수정, 유효성 검사 스크립트 작성
- 화면 ID와 서비스 URL, 서버 Controller, SQL의 연결 관계를 따라가며 수정
- 공통 함수(`gfn_*`, `com.*`) 사용법을 기존 코드에서 익혀서 똑같이 쓰기
- 대량 일괄 수정 (예: 특정 공통 함수 호출 방식을 전체 화면에서 바꾸기)

## 한계

1. **눈으로 확인하기 어렵습니다.** 넥사크로 화면 배치는 `left/top/width/height` 좌표라서 파일은 고칠 수 있지만, 실제로 보기 좋게 놓였는지는 제가 직접 볼 수 없습니다. 레이아웃은 Nexacro Studio나 WebSquare Studio에서 사람이 마무리하는 편이 낫습니다.
2. **실행 확인에 제약이 있습니다.** 웹스퀘어는 엔진이 배포된 서버만 있으면 브라우저로 띄워 볼 수 있습니다. 넥사크로는 런타임이나 생성된 JS, 라이선스가 필요해서 제 쪽에서 실행까지 확인하기는 어렵습니다.
3. **공개 자료가 적습니다.** 버전별 세부 API나 컴포넌트 속성은 틀릴 수 있습니다. 프로젝트의 기존 코드, 공통 라이브러리, 개발 가이드 문서를 주시면 정확도가 크게 올라갑니다.
4. **Studio와 함께 쓸 때 조심할 점이 있습니다.** Studio가 파일을 다시 저장하면서 서식을 바꿀 수 있습니다. 넥사크로의 generate 결과물은 직접 고치지 말고 원본(`.xfdl`)을 고친 뒤 다시 생성해야 합니다.

## 실무에서 권하는 방식

1. 프로젝트 `CLAUDE.md`에 화면 규칙을 적습니다. 파일 위치, 명명 규칙, 공통 함수, 서비스 URL 규칙 같은 것들입니다.
2. 표준 화면 2~3개를 "본보기"로 정해 두고, 새 화면은 그것을 따라 만들게 합니다.
3. 스크립트, 데이터, 서버 연동은 Claude Code가 맡고, 화면 배치와 실행 확인은 개발자가 Studio에서 합니다.
4. 영향도 분석에는 이 저장소의 `dstone-knowledge`를 쓸 수 있습니다. 이 화면을 고치면 어느 서버 로직과 테이블이 영향을 받는지 미리 볼 수 있습니다.

정리하면, 코드와 로직 개발은 Claude Code로 충분히 진행할 수 있고, 화면 디자인과 실행 검증은 Studio와 사람이 맡는 분업이 현실적입니다.
