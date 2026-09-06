# rabbitmq_를_시작하는데_Adm

> 생성일시: 2026-09-06 10:10

## 질문

rabbitmq 를 시작하는데 Admin Console URL : http://localhost:15672 이 제대로 안뜨는 것 같아.

---

## 답변

RabbitMQ 상태부터 확인하겠습니다.

---

## 답변

So RabbitMQ is installed natively via apt (not Docker), using systemd service + start/stop scripts. Let's check actual status.

---

## 답변

RabbitMQ 프로세스는 실행 중이고 15672 포트도 열려 있네요. curl로 실제 응답을 확인해보겠습니다.

---

## 답변

서버 쪽에서 직접 확인해보니, RabbitMQ 프로세스는 정상 기동 중이고(`beam.smp`), `15672` 포트도 `0.0.0.0`에 바인딩되어 있고, `curl http://localhost:15672/`가 **200 OK**로 관리 콘솔 HTML을 정상 반환합니다(`/api/overview`는 인증 안 해서 401 — 정상 동작). 즉 WSL 내부에서는 RabbitMQ 관리 콘솔이 완전히 정상입니다.

그래서 문제는 브라우저(Windows) 쪽에서 접속할 때 나타나는 것 같은데, 정확히 어떤 증상인가요?

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

지금은 제대로 떠

---

## 답변

확인 감사합니다. 지금은 정상적으로 뜨는 거면 별문제 없는 것 같습니다 — 아마 처음 접속했을 때 서비스가 완전히 기동되기 전이었거나 일시적인 타이밍 문제였을 수 있습니다. 서버 쪽(WSL 내부에서 curl 테스트)은 이미 정상 응답 확인했으니 추가로 손댈 부분은 없습니다.
