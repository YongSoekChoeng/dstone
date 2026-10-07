# nexacro-app (Nexacro / X-Platform 화면 검증 표본)

`dstone-knowledge`가 **Nexacro / X-Platform 화면(`.xfdl`)을 서버 쪽 메소드와 잇는지** 확인하려고 만든 작은 표본이다.
분석기의 입력으로만 쓴다. Nexacro 엔진도 Spring jar도 없다.

**실제 Nexacro 프로젝트 소스로는 아직 확인하지 못했다.** 손으로 만든 이 표본만 통과한 상태다. 실무 프로젝트는 `transaction`을 공통 함수(`gfn_transaction` 등)로 감싸고 주소를 변수로 넘기는 경우가 많은데, 그 모양은 실제 소스를 보고 규칙을 정해야 한다.

## 들어 있는 것

| 무엇 | 어디에 | 기대하는 동작 |
|---|---|---|
| 화면 알아보기 | `nxui/order/*.xfdl` (맨 위 요소 `<FDL>`) | 언어 `XML`, 파일 종류 `NEXACRO` |
| 애플리케이션 정의 | `nxui/App.xadl` (맨 위 요소 `<ADL>`) | `NEXACRO_APP`. 화면이 아니다 |
| 서비스 접두어가 붙은 주소 | `OrderList.xfdl`: `this.transaction("search", "svc::order/list.do", …)` | 접두어를 떼고 `/order/list.do`로 맞춘다 → `REQUESTS` (`MEDIUM`) |
| 접두어 뒤에 `/`가 있는 주소 | `OrderDetail.xfdl`: `"svc::/order/save.do"` | `REQUESTS` (`MEDIUM`) |
| 지워 둔 옛 호출 | `OrderList.xfdl`: `// this.transaction("old", "svc::order/save.do", …)` | 주석 안이므로 잇지 않는다 |
| 화면 안의 화면 | `OrderList.xfdl`: `<Div url="order::OrderDetail.xfdl">` | `INCLUDES` 1건 |

## 기대 값

| 항목 | 값 |
|---|---|
| Job 상태 | `DONE` (오류 0) |
| 파일 | 4: Java 1, Nexacro 화면 2, 애플리케이션 정의 1 |
| 진입점 | `HTTP` 2 (`/order/list.do`, `/order/save.do`) |
| `REQUESTS` | 2: `OrderList.xfdl` → `OrderController#list`, `OrderDetail.xfdl` → `OrderController#save` |
| `INCLUDES` | 1: `OrderList.xfdl` → `OrderDetail.xfdl` |
| 검색 문서 | 6: `METHOD` 2, `TYPE` 1, `FILE` 1, `VIEW` 2 |

## 아직 다루지 않는 것

- 공통 스크립트(`.xjs`) 안의 호출. `.xjs`는 분석 대상 파일이 아니다.
- 서비스 접두어(`svc::`)가 실제로 가리키는 주소(`typedefinition`의 `<Service>`). 접두어는 떼기만 한다.
- MiPlatform(화면이 일반 `.xml`).
