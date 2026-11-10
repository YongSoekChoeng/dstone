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
