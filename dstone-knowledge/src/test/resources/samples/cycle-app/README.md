# cycle-app (순환 / 깊은 중첩 검증 표본)

`dstone-knowledge`가 **순환이 있는 코드에서 멈추거나 죽지 않는지** 확인하려고 만든 작은 표본이다.
분석기의 입력으로만 쓴다. `CycleA` / `CycleB`, `CycleI` / `CycleJ`는 일부러 컴파일되지 않게 만들었다.

## 들어 있는 함정

| 함정 | 어디에 | 기대하는 동작 |
|---|---|---|
| 자기 자신을 부르는 메소드 | `Recursion.self(int)` | `self → self` 관계 1건. 호출 그래프 조회가 바로 끝난다 |
| 서로를 부르는 메소드 | `Recursion.a() → b() → c() → a()` | 관계 3건. `a()`의 호출자를 depth 5로 조회해도 같은 호출이 한 번씩만 나온다(가장 얕은 깊이로) |
| 순환 상속(클래스) | `CycleA extends CycleB`, `CycleB extends CycleA` | 분석이 끝난다. `EXTENDS` 2건. 메소드가 자기 자신을 재정의한다는 관계는 생기지 않는다 |
| 순환 상속(인터페이스) | `CycleI extends CycleJ`, `CycleJ extends CycleI` | 위와 같음 |
| 순환 상속 안의 호출 | `CycleA.run()`의 `helper()`, `missing()` | 해석기가 스택 넘침으로 실패한다. 그 호출만 짐작하거나 못 푼 것으로 남기고 계속한다 |
| 지나치게 깊게 중첩된 식 | `Deep.big()`: 문자열 6,000개를 `+`로 이음 | 이 파일 하나만 `TOO_DEEP` 오류로 실패하고, Job은 `DONE_WITH_WARNING`으로 끝난다 |

## 기대 값

| 항목 | 값 |
|---|---|
| Job 상태 | `DONE_WITH_WARNING` (오류 1건: `Deep.java`, `TOO_DEEP`) |
| Java 파일 | 7 (`parse_status` OK 6, FAILED 1) |
| `Recursion` 안의 `CALLS` | 6 (`a→b`, `b→c`, `c→a`, `entry→a`, `entry→self`, `self→self`). 전체 `CALLS`는 10 |
| `EXTENDS` | 4 |
| `OVERRIDES` | 4 (`CycleA#run ↔ CycleB#run`, `CycleI#go ↔ CycleJ#go`). 자기 자신으로 가는 것은 0 |
