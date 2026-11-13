# methodref-app (메소드 참조 검증 표본)

RESOLVE가 메소드 참조(`::`)를 제대로 푸는지 확인하려고 만든 작은 표본이다. 분석기 입력으로만 쓴다.
`io.jsonwebtoken.Claims`는 jar 없이 import만 있어서 컴파일되지 않는다(일부러 그렇게 했다).

## 기대 값 (`MethodRefs.java`)

| 줄 | 코드 | 참조 `status` | 관계(`CALLS`) |
|---|---|---|---|
| 19 | `Claims::getSubject` | `EXTERNAL` | `EXTERNAL_METHOD` `io.jsonwebtoken.Claims.getSubject` (`MEDIUM`, 타입은 import로 앎) |
| 24 | `String::trim` | `EXTERNAL` | `EXTERNAL_METHOD` `java.lang.String.trim()` (`HIGH`) |
| 29 | `MemberVO::getName` | `RESOLVED` | `MemberVO#getName()` |
| 34 | `this::upper` | `RESOLVED` | `MethodRefs#upper(String)` |
| 39 | `formatter::format` | `RESOLVED` | `Formatter#format(String)` (오버로드 2개 가운데 해석기가 고름) |
| 44 | `names::add` | `EXTERNAL` | `EXTERNAL_METHOD` `java.util.List.add(E)` |
| 49 | `Formatter::new` | `RESOLVED` | `Formatter#<init>(String)` |
| 54 | `String[]::new` | `IGNORED` | 없음 |

메소드 참조는 모두 8건이고 `UNRESOLVED`는 0건이다.

## 증분 분석 확인

`MethodRefs.java`는 그대로 두고 `Formatter.format(String)`을 지우고 `MemberVO.getName`을 `getFullName`으로 바꾼 뒤 증분 분석하면,
`MethodRefs.java`도 RESOLVE를 다시 돌아 29행은 `UNRESOLVED`, 39행은 남은 `format(String, int)`로 `HEURISTIC`이 된다.
같은 소스를 전체 분석한 결과와 같아야 한다.
