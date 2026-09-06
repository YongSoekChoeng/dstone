# rabbitmq_를_시작하는데_Adm

> 생성일시: 2026-09-06 10:16

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

---

## 질문

그런데 start.sh 실행 시 자꾸 아래와 같은 메세지가 보이는데... 문제없는거야?
jysn007@DESKTOP-DVQ9FB6:~$ ./start.sh
Job for mysql.service failed because the control process exited with error code.
See "systemctl status mysql.service" and "journalctl -xeu mysql.service" for details.
Mysql started !!!
Postgresql started !!!
...

---

## 답변

지금 상태를 확인해보니 상황이 좀 애매합니다.

**확인된 사실:**
- `mysqld` 프로세스는 떠 있습니다(PID 41668, 방금 이 `start.sh` 실행 시점과 거의 일치하는 시각에 기동됨).
- TCP `127.0.0.1:3306`으로는 실제로 연결되고 인증 단계까지 진행됩니다(`mysqladmin ping` 결과가 "Access denied for user..." — 이건 연결 자체는 성공했다는 뜻).
- 반면 유닉스 소켓 `/var/run/mysqld/mysqld.sock`은 **없습니다**. `/var/run/mysqld/` 디렉터리 자체가 존재하지 않습니다.

즉 mysqld가 TCP로는 응답하지만, 정상적으로 systemd가 관리하는 상태는 아닌 것으로 보입니다 — `systemctl start` 시 control process(소켓 디렉터리 생성 등을 담당하는 사전 스크립트)가 실패했다는 메시지와 일치합니다. `start-mysql.sh`가 `sudo systemctl start mysql` 성공 여부를 확인하지 않고 무조건 `"Mysql started !!!"`를 출력하는 스크립트라서, 지금 이 메시지만으로는 "괜찮다"고 단정하기 어렵습니다.

제가 가진 sudo 권한으로는 비밀번호 입력이 필요해서 (터미널이 없어 이 세션에서 직접 인증 불가) 아래 두 명령을 직접 실행해서 결과를 보여주실 수 있을까요? (`!` 접두사로 입력하면 이 세션에 결과가 그대로 반영됩니다)

---

## 답변

명령 결과를 붙여넣어 주시면 원인을 확인하겠습니다.
