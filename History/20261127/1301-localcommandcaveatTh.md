# localcommandcaveatTh

> 생성일시: 2026-11-27 13:01

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

testdriver 는 어떻게 관리/적용되지?

---

## 답변

"테스트 드라이버"는 테스트 서버의 앱 JVM에서 도는 **JDBC 드라이버**입니다. 개발 DB를 쓰되 커밋하지 않고, 끝나면 원복합니다. 사람이 따로 설치하거나 등록할 것은 없고 엔진이 만들어 끼워 넣습니다.

## 관리

| 항목 | 내용 |
|---|---|
| 소스 | `dstone-ai-engine/src/main/resources/testdriver/net/dstone/ai/testdriver/`의 `TestDriver`, `Holder`, `Control` (`.javasrc`) |
| 패키징 | `pom.xml`에서 `${}` 치환 없이 리소스로 복사합니다. 엔진과 함께 컴파일하지 않습니다. |
| 컴파일 | `TestDriverJar.ensure()`가 서버를 처음 띄울 때 엔진의 JDK로 `--release 8` 컴파일합니다. 엔진이 JRE에서 돌면 실패합니다. |
| 보관 | `{work-root}/testdriver/{소스 해시}/dstone-testdriver.jar`. `work-root`가 비어 있으면 `~/.dstone-ai-engine`입니다. |
| 버전 | 소스 내용의 SHA-256이 곧 버전입니다. 같으면 재사용하고, 바뀌면 새 폴더에 만듭니다. |

드라이버를 고쳤다면 **엔진을 다시 빌드해 띄우고, 그 뒤 새로 띄우는 테스트 서버**부터 반영됩니다. `ensure()`에는 옛 해시 폴더를 지우는 코드가 없습니다.

## 적용 (`startTestServer`)

1. **설정 바꾸기** (`RunLayer`): 실행 층에 복사하는 파일에서만 바꾸고, 원본 소스와 리비전은 그대로 둡니다.
   - 프로젝트 관리에 등록한 DB의 URL → `jdbc:dstonetest:{원래 URL}`
   - 알려진 드라이버 클래스 → `net.dstone.ai.testdriver.TestDriver`
   - JNDI 앱은 Tomcat Context에 이 드라이버를 쓰는 `Resource`를 만듭니다(`TomcatBase`).
   - 등록한 URL도 JNDI 이름도 찾지 못하면 서버를 띄우지 않습니다.
2. **classpath에 넣기**: Boot는 `-cp` 끝에 jar를 붙이고, WAR는 실행 전용 `CATALINA_BASE`를 준비할 때 넘깁니다.
3. **JVM 옵션**:
   - `-Ddstone.testdriver.control`: 제어 파일 경로
   - `-Ddstone.testdriver.lockWaitSeconds`: 설정 `db-lock-wait-seconds`, 기본 30
   - `-Ddstone.testdriver.drivers`: 바꾸기 전의 원래 드라이버

## 실행 중 동작

- URL·계정마다 물리 연결 하나만 열고 autocommit을 끕니다.
- 앱의 `commit()`은 savepoint만 풀고, `rollback()`은 마지막 savepoint까지만 되돌립니다.
- DDL은 실행하지 않습니다.
- 엔진은 `TestDriverClient`로 제어 창구(127.0.0.1, 일회용 토큰)에 접속해 원복하고 조회합니다. `test-DB이름` 별칭의 `dbQuery`는 커밋 전 데이터도 봅니다.
- 드라이버를 거치지 않은 DB 연결이 감지되면 서버를 내립니다.

## 원복 시점

| 상황 | 서버 | DB |
|---|---|---|
| step 종료 | 유지 | 유지 |
| 승인 대기·일시정지 | 유지(`idle-minutes` 30분 지나면 내림) | 원복 |
| 실행 종료·실패 | 내림 | 원복 |
| `db-hold-minutes`(60분) 초과 | 내림 | 원복 |
| 엔진 종료 | 내림 | 원복 |

프로시저 안의 COMMIT, 자율 트랜잭션, DB 링크 너머의 쓰기, MyISAM 테이블은 원복되지 않습니다. Java 6·7 앱은 지원하지 않습니다. 자세한 설명은 `docs/09.dstone-ai-engine.md` 11.2절에 있습니다.

상용 DB 드라이버(MySQL, PostgreSQL, Oracle 등)를 어떻게 찾는지 물으신 것이었다면, 앱 설정에 있던 원래 드라이버를 먼저 쓰고, 없으면 URL 종류의 알려진 드라이버, `ServiceLoader` 순으로 찾습니다.
