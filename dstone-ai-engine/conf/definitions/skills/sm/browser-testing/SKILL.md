---
# 브라우저 Tool(browser_*)로 누른 것이 화면에 먹지 않을 때 대상을 직접 지정하는 방법입니다.
# 동작 검증 Agent와 자동 수정 Agent가 skills.preload로 받습니다.
# 2026-11-27 실행 0fb4c669: iframe 팝업 안의 <div> 버튼이 접근성 스냅샷에서 감싼 영역과 합쳐져, ref로 누르면 감싼 영역이 눌렸습니다.
#   iframe으로 들어가지 않는 선택자는 "does not match"로 실패해 저장 뒤의 완료 기준을 확인하지 못했습니다.
#   같은 구조의 시험 페이지(Playwright MCP 0.0.86)에서 아래 locator 표기가 통하는 것을 확인했습니다.
# 특정 앱의 요소 이름·화면 제목은 여기에 적지 않습니다.
name: browser-testing
description: 브라우저에서 눌러도 반응이 없거나 대상을 찾지 못할 때 iframe 안의 요소까지 locator 표기로 지정하는 방법. 화면을 눌러 확인할 때 쓴다.
---
평소에는 스냅샷의 ref를 target에 넣는다. 아래는 ref로 안 될 때 쓴다.

## 1. 이런 때

- 눌렀는데 아무 일도 없고, 결과의 실행 코드(`Ran Playwright code`)가 가리키는 대상이 누르려던 것보다 넓다(옆 버튼의 글자까지 들어 있다).
  `<div>`·`<span>`으로 만든 버튼은 스냅샷에서 감싼 영역과 합쳐질 수 있다.
- 누르려는 요소의 ref가 스냅샷에 없다.

## 2. 대상을 직접 지정하기

원본 화면 소스에서 그 요소의 id·class를 확인하고, target에 locator 표기를 넣는다.

| 요소가 있는 곳 | target |
|---|---|
| 바깥 페이지 | `locator('#요소id')` |
| iframe 안 | `locator('iframe[title="프레임 제목"]').contentFrame().locator('#요소id')` |
| iframe 안, 글자로 | `locator('iframe[title="프레임 제목"]').contentFrame().getByText('글자', { exact: true })` |

- iframe 안의 요소는 `contentFrame()`을 거쳐야 찾는다. CSS 선택자만 쓰거나 `>>`로 이으면 "does not match any elements"가 난다.
- iframe의 title이 없으면 스냅샷이나 소스에서 그 iframe을 가리키는 다른 선택자를 쓴다. iframe이 겹쳐 있으면 `contentFrame()`을 차례로 거친다.
- 누른 뒤에는 화면, 네트워크 요청, DB의 행으로 실제로 처리됐는지 확인한다.
