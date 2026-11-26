# localcommandcaveatTh

> 생성일시: 2026-11-26 18:40

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 에서 실행ID 0fb4c669-a4aa-4759-8bda-4024ca168246 개발 실패 했는데 테스트하다가 난 것 같아. 원인 추적해줘.

---

## 답변

Windows 쪽에서 돌고 있는 엔진의 실행이네요(로그에 1,236줄). 파일은 읽기만 하겠습니다.

---

## 답변

테스트 서버의 앱이 DB에 비밀번호 없이 접속하는 이유를 코드에서 확인하고 있습니다.

---

## 답변

Linux Tomcat에서 같은 오류가 재현됐습니다. 이제 테스트 드라이버가 받는 접속 값을 계측해 원인을 좁히겠습니다.

---

## 답변

## 결론

개발 코드 문제가 아니라 **엔진 버그 2건**이 겹쳤습니다. 실행은 `FAILED`가 아니고, 지금 `24-개발 승인`(step19)에서 승인 대기 중입니다.

| 시각 | 일어난 일 |
|---|---|
| 18:25 | 1차 동작 검증: 10개 시나리오 확인 결과 앱 DB 접속 실패로 BLOCKED(2차 기록에 "지난 회차와 동일") |
| 18:31 | `admin`이 화면 수정 의견과 함께 `재개발` 선택 |
| 18:34 | 2차 동작 검증: 10개 모두 BLOCKED |
| 18:34 | `43-실패한 완료 기준이 있는지`(step36)가 실패로 기록되고 승인 단계로 이동 |

## 원인 1: 테스트 서버의 앱이 DB에 접속하지 못함

오류는 `Access denied for user 'testapp'@'localhost' (using password: NO)`입니다. 설정에는 비밀번호가 있고 테스트 드라이버까지 정상 전달됩니다.

1. testApp은 `com.mysql.cj.jdbc.Driver`를 쓰도록 설정돼 있습니다.
2. 테스트 서버는 이 이름을 테스트 드라이버로 바꿉니다.
3. 테스트 드라이버의 `Holder.connectReal()`은 **원래 드라이버가 무엇이었는지 모른 채**, `ServiceLoader`에서 URL을 받겠다는 첫 드라이버를 씁니다.
4. testApp의 `WEB-INF/lib`에는 `mariadb-java-client-2.2.5`도 있고, 이 드라이버가 `jdbc:mysql:` URL을 가로챕니다.
5. 이 구버전 드라이버로는 인증에 실패합니다. 예외가 나면 다음 드라이버도 시도하지 않습니다.

엔진의 `dbQuery`는 엔진 쪽 드라이버를 써서 정상이었습니다.

**재현:** WSL의 Tomcat 9에 같은 리비전을 띄워 같은 오류를 확인했습니다. 접속에 `org.mariadb.jdbc.Driver`가 선택됐고, 이를 건너뛰게 하자 `com.mysql.cj`로 접속해 오류가 사라졌습니다. 개발 DB에는 커밋하지 않는 테스트 드라이버로만 접속했고, 임시 Tomcat은 종료했습니다.

## 원인 2: step36의 비밀값 오탐

```
실패: 비밀값 자리표시자(${secret:...})는 [browser_type, browser_fill_form] Tool에만 쓸 수 있습니다.
```

- 검증 Agent가 결과의 `steps`에 `${secret:testApp.testPassword} 입력`이라고 글자 그대로 적었습니다.
- step36의 `containsValue`는 이 결과를 인자로 받습니다.
- `ToolCallGate`가 자리표시자 글자만 보고 호출을 거부했습니다.

이번에는 `onFailure`와 `false`가 둘 다 step19라 흐름은 같았습니다. 하지만 결과에 `FAIL`이 있었다면 **자동 수정(step31)으로 가지 못하고 승인으로 빠졌을 것**입니다.

## 수정 제안

둘 다 특정 Workflow나 앱에 묶이지 않는 엔진 수정입니다.

1. **드라이버 선택:** 바꾸기 전의 드라이버 클래스 이름(`RunLayer`가 치환할 때 알 수 있음)을 테스트 드라이버에 넘겨 그것을 먼저 씁니다. 이름을 모를 때만 URL 종류에 맞는 기본 드라이버, `ServiceLoader` 순으로 찾습니다. JNDI 연결에도 같은 기준이 필요합니다.
2. **비밀값 검사:** TOOL step이 부른 호출은 자리표시자를 치환하지 않은 글자 그대로 통과시킵니다. 실제 값은 들어가지 않습니다. Agent의 Tool 호출은 지금처럼 거부합니다.

고친 뒤 이 실행은 승인 화면에서 `재개발`을 선택하면 다시 검증됩니다. 이때 개발 Agent도 다시 돕니다. 지금 상태로 `승인`하면 **화면을 한 번도 확인하지 못한 결과**를 승인하게 됩니다.

엔진은 Windows 쪽 클론에서 돌고 있으니, `/app/dstone`에서 고친 뒤 git으로 동기화하고 다시 빌드해야 합니다. 두 건을 바로 수정할까요?
