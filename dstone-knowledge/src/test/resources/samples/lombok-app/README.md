# lombok-app (Lombok 검증 표본)

`dstone-knowledge`가 **Lombok이 만드는 멤버를 부르는 호출**을 풀 수 있는지 확인하려고 만든 작은 표본이다.
실제로 돌아가는 프로그램이 아니라 분석기의 입력으로만 쓴다(컴파일하지 않는다. Lombok jar도 넣지 않았다).

소스에는 `getName()`, `setActive()`, `builder()`, `log`가 하나도 없다. 전부 Lombok이 컴파일할 때 만든다.
JavaSymbolSolver는 소스에 없는 멤버를 모르기 때문에, 분석기가 DECLARE 단계에서 만들어 넣은 멤버(`is_synthetic`)로 이어야 한다.

## 기대 값

| 호출 (`MemberService`) | 이어져야 하는 대상 | 근거 |
|---|---|---|
| `Member.builder()` | `Member#builder()` | `@Builder` |
| `.id(id)`, `.name(name)` | `Member.MemberBuilder#id(String)`, `#name(String)` | `builder()`의 반환 타입 `MemberBuilder`를 따라간다 |
| `.build()` | `Member.MemberBuilder#build()` | 위와 같음 |
| `member.setActive(true)` | `Member#setActive(boolean)` | `@Setter`. boolean 필드 `isActive`는 `is`를 뗀다 |
| `member.getName()`, `member.getId()` | `Member#getName()`, `#getId()` | `@Getter` |
| `member.isActive()` | `Member#isActive()` | `@Getter`, boolean |
| `log.info(...)` | 프로젝트 밖 `org.slf4j.Logger.info` | `@Slf4j`가 만드는 `log` 필드의 타입 |
| `memberRepository.save(member)` | `MemberRepository#save(Member)` + 가능한 구현 `MemoryMemberRepository#save(Member)` | 인터페이스 호출 |

| 항목 | 값 |
|---|---|
| `MemberService`의 호출 | 11 (`builder`, `id`, `name`, `build`, `setActive`, `info`, `getName` 2, `save`, `getId`, `isActive`) |
| 그중 Lombok 멤버로 이어지는 것 | 9 |
| `MemoryMemberRepository`의 호출 | 2 (`store.put`, `member.getId`) |
| 못 푼 호출 | 0 |
