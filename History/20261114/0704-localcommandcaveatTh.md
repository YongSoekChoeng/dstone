# localcommandcaveatTh

> 생성일시: 2026-11-14 07:04

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="0555">
dstone-boot>knowledge 의 영향도 · 호출 관계 에서 아래의 내용을 확인해줘.

<조회조건>
프로젝트: HI-Japan
대상 찾기: 종류-타입, JADC1021M

<질문>
1. 결과를 보면 부르는쪽, 불리는쪽 이 비활성화 되어있음. 이유는?
2. 진입점, 화면, 메소드 가 갖는 정확한 의미를 정리해줘.
</pasted_content id="0555">

---

## 답변

영향도 화면 코드와 실제 데이터를 같이 확인하겠습니다.

---

## 답변

코드 확인은 끝났고, 이제 실행 중인 4081 API로 HI-Japan의 JADC1021M 실제 결과를 조회합니다.

---

## 답변

## 1. 부르는 쪽·불리는 쪽 탭이 비활성화된 이유

버그가 아니고 그렇게 만들어 둔 동작입니다. 두 탭은 **대상이 메소드일 때만** 켜집니다.

- `dstone-boot/src/main/webapp/knowledge/assets/js/impact.js:459`
  ```js
  function tabEnabled(name) {
      return name === "impact" || (target && target.kind === "method");
  }
  ```
  비활성 탭에 마우스를 올리면 "메소드를 대상으로 골랐을 때만 볼 수 있습니다."가 뜹니다.

이렇게 만든 이유는 API에 있습니다. 호출 관계 API(`/methods/{methodId}/callers`, `/callees`)는 메소드 ID 하나만 받습니다. 타입·테이블·SQL에는 "누가 부르는지"를 한 줄로 정할 기준점이 없습니다. 반면 영향도 API(`/impact?type=…`)는 네 종류를 모두 받습니다.

**호출 관계를 보는 방법:** 영향도 탭 아래 "메소드" 표에서 `performJADC1021M01S`나 `performJADC1021M01I`를 누르세요. 그 메소드가 새 대상이 되고 두 탭이 켜집니다. 이전 대상(타입)은 "지나온 대상"에 남으니 눌러서 돌아오면 됩니다.

## 2. 진입점·화면·메소드의 의미

리비전 195에서 `hi.ja.closing.service.JADC1021M`의 실제 영향도 결과를 기준으로 정리했습니다.

영향도는 **대상에서 시작해 "부르는 쪽" 방향으로만** 거슬러 올라갑니다. 따라가는 관계는 `CALLS`, `REQUESTS`, `CALLS_POSSIBLE_IMPLEMENTATION`, `ENCLOSES_ANONYMOUS`이고 기본 깊이는 5입니다. 그래서 JADC1021M이 **부르는** `JADC1021S`나 SQL은 결과에 나오지 않습니다. 그쪽은 불리는 쪽 탭에서 봅니다.

### 메소드 (3건)
대상에서 시작해 거슬러 올라가며 닿은 **Java 메소드** 전부입니다.
- **거리 0**은 출발점입니다. 대상이 타입이면 **그 타입에 선언된 메소드 전부**가 들어갑니다(`selectImpactMethods`, `RelationDao.xml:565`). 그래서 생성자 `<init>(ServiceContext)`까지 포함해 3건입니다.
- 거리 1 이상은 이 메소드들을 부르는 다른 Java 메소드입니다. JADC1021M을 부르는 Java 코드가 없어서 이번 결과에는 없습니다. 화면에서 거래 ID로 불리는 클래스라서 그렇습니다.
- 익명 클래스 안의 메소드는 빼고, 그것을 감싸는 바깥 메소드를 같은 거리로 보여 줍니다.

### 진입점 (2건)
닿은 메소드 가운데 **시스템 바깥에서 들어오는 입구**로 등록된 것입니다(`analysis_endpoint`). 사용자가 변경을 처음 겪는 곳이라는 뜻입니다.

| 주소 | 종류 | 처리 메소드 |
|---|---|---|
| `JADC1021M01S` | TRANSACTION | `performJADC1021M01S` |
| `JADC1021M01I` | TRANSACTION | `performJADC1021M01I` |

- 종류는 HTTP 주소, `TRANSACTION`(거래 ID), 스케줄, `main`, `THREAD` 같은 것들입니다. HI-Japan은 `perform{거래ID}` 규칙으로 거래 ID를 진입점으로 잡습니다.
- 메소드 없이 타입에만 붙은 진입점도 있습니다(Struts DispatchAction처럼 어느 메소드가 불릴지 모르는 경우). 그 타입의 메소드가 하나라도 닿으면 결과에 넣고, 짐작이라서 `LOW`로 표시합니다.

### 화면 (1건)
변경의 영향을 받는 **화면 파일**(JSP, WebSquare/Nexacro XML)입니다. 화면에 닿는 길은 세 가지이고, "이어진 방식" 칸에 적힙니다.

| 방식 | 의미 |
|---|---|
| 화면 요청 (`REQUESTS`) | 화면이 닿은 메소드의 주소나 거래 ID를 요청한다 |
| 호출 (`CALLS`) | JSP 안의 Java 코드가 메소드를 직접 부른다 |
| 화면 열기 (`RENDERS`) | 닿은 메소드가 끝난 뒤 이 화면을 연다 |

이번 결과는 `was/ui/ja/JADC1021G.xml` 하나이고, 거리 1의 **화면 요청**입니다. 소스에서 확인한 근거는 다음과 같습니다.
- `JADC1021G.xml:400`: `HiJS.svr.doRequestAjax("JADC1021M01S", …)`
- `JADC1021G.xml:478`: `HiJS.svr.doRequestAjax("JADC1021M01I", …)`

### 정리
- **메소드**: 영향이 퍼지는 Java 코드 전체이고, 거리 0이 출발점입니다.
- **진입점**: 그중 바깥에서 들어오는 입구(주소, 거래 ID, 배치 등)입니다.
- **화면**: 그 입구를 요청하거나 그 메소드가 여는 UI 파일입니다.
