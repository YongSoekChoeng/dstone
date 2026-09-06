# rabbitmq_를_시작하는데_Adm

> 생성일시: 2026-09-06 10:21

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

---

## 질문

jysn007@DESKTOP-DVQ9FB6:~$ sudo systemctl status mysql.service --no-pager -l
● mysql.service - MySQL Community Server
     Loaded: loaded (/usr/lib/systemd/system/mysql.service; disabled; preset: enabled)
     Active: activating (start) since Sun 2026-09-06 10:16:14 KST; 3s ago
        Job: 34488
 Invocation: 1d8997e8f1194411bb1f45db1699fbef
    Process: 43791 ExecStartPre=/usr/share/mysql/mysql-systemd-start pre (code=exited, status=0/SUCCESS)
   Main PID: 43800 (mysqld)
     Status: "Shutdown of plugins in progress"
      Error: code: 98 (주소가 이미 사용 중입니다)
      Tasks: 10 (limit: 9519)
     Memory: 481.3M (peak: 482.1M)
        CPU: 743ms
     CGroup: /system.slice/mysql.service
             └─43800 /usr/sbin/mysqld

 9월 06 10:16:14 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 10:16:14 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 780ms CPU time over 3.741s wall clock time, 482M memory peak.
 9월 06 10:16:14 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 55.
 9월 06 10:16:14 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
jysn007@DESKTOP-DVQ9FB6:~$ sudo journalctl -xeu mysql.service --no-pager | tail -n 60
 9월 06 10:16:21 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 783ms CPU time over 3.715s wall clock time, 480M memory peak.
░░ Subject: Resources consumed by unit runtime
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ The unit mysql.service completed and consumed the indicated resources.
 9월 06 10:16:21 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 57.
░░ Subject: Automatic restarting of a unit has been scheduled
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ Automatic restarting of the unit mysql.service has been scheduled, as the result for
░░ the configured Restart= setting for the unit.
 9월 06 10:16:21 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
░░ Subject: mysql.service 유닛 시작
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ mysql.service 유닛을 시작했습니다.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
░░ Subject: Unit process exited
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ An ExecStart= process belonging to unit mysql.service has exited.
░░
░░ The process' exit code is 'exited' and its exit status is 1.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
░░ Subject: Unit failed
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ The unit mysql.service has entered the 'failed' state with result 'exit-code'.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
░░ Subject: mysql.service 유닛 동작 실패
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ mysql.service 유닛 동작에 실패했습니다.
░░
░░ 결과는 failed 입니다.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 810ms CPU time over 3.780s wall clock time, 479.9M memory peak.
░░ Subject: Resources consumed by unit runtime
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ The unit mysql.service completed and consumed the indicated resources.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 58.
░░ Subject: Automatic restarting of a unit has been scheduled
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ Automatic restarting of the unit mysql.service has been scheduled, as the result for
░░ the configured Restart= setting for the unit.
 9월 06 10:16:25 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
░░ Subject: mysql.service 유닛 시작
░░ Defined-By: systemd
░░ Support: http://www.ubuntu.com/support
░░
░░ mysql.service 유닛을 시작했습니다.
jysn007@DESKTOP-DVQ9FB6:~$

---

## 답변

명령 실행 결과를 붙여넣어 주세요.

---

## 질문

jysn007@DESKTOP-DVQ9FB6:~$ sudo journalctl -u mysql.service -b --no-pager | head -n 100
 9월 06 09:48:20 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 1.056s CPU time over 3.927s wall clock time, 522.3M memory peak.
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 1.
 9월 06 09:48:24 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 774ms CPU time over 3.711s wall clock time, 479.1M memory peak.
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 2.
 9월 06 09:48:27 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 717ms CPU time over 3.704s wall clock time, 478.9M memory peak.
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 3.
 9월 06 09:48:31 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 883ms CPU time over 4.171s wall clock time, 478.9M memory peak.
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 4.
 9월 06 09:48:35 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 949ms CPU time over 4.028s wall clock time, 479M memory peak.
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 5.
 9월 06 09:48:39 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 855ms CPU time over 3.943s wall clock time, 478.9M memory peak.
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 6.
 9월 06 09:48:43 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 837ms CPU time over 3.732s wall clock time, 478.9M memory peak.
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 7.
 9월 06 09:48:47 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 900ms CPU time over 4.014s wall clock time, 479M memory peak.
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 8.
 9월 06 09:48:51 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 1.008s CPU time over 4.043s wall clock time, 478.9M memory peak.
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 9.
 9월 06 09:48:55 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 929ms CPU time over 4.004s wall clock time, 478.8M memory peak.
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 10.
 9월 06 09:48:59 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 781ms CPU time over 4.005s wall clock time, 481M memory peak.
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 11.
 9월 06 09:49:03 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 1.041s CPU time over 4.342s wall clock time, 478.9M memory peak.
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 12.
 9월 06 09:49:07 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 1.065s CPU time over 4.264s wall clock time, 479.2M memory peak.
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 13.
 9월 06 09:49:12 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 1.001s CPU time over 4.130s wall clock time, 479.2M memory peak.
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 14.
 9월 06 09:49:16 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 931ms CPU time over 4.230s wall clock time, 478.9M memory peak.
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 15.
 9월 06 09:49:20 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Consumed 972ms CPU time over 3.956s wall clock time, 478.9M memory peak.
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Scheduled restart job, restart counter is at 16.
 9월 06 09:49:24 DESKTOP-DVQ9FB6 systemd[1]: Starting mysql.service - MySQL Community Server...
 9월 06 09:49:28 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Main process exited, code=exited, status=1/FAILURE
 9월 06 09:49:28 DESKTOP-DVQ9FB6 systemd[1]: mysql.service: Failed with result 'exit-code'.
 9월 06 09:49:28 DESKTOP-DVQ9FB6 systemd[1]: Failed to start mysql.service - MySQL Community Server.
jysn007@DESKTOP-DVQ9FB6:~$ sudo lsof -i :3306
jysn007@DESKTOP-DVQ9FB6:~$ sudo ls -la /var/run/mysqld/
total 4
drwxr-xr-x  2 mysql mysql  80 Sep  6 10:19 .
drwxr-xr-x 30 root  root  820 Sep  6 10:19 ..
srwxrwxrwx  1 mysql mysql   0 Sep  6 10:19 mysqlx.sock
-rw-------  1 mysql mysql   6 Sep  6 10:19 mysqlx.sock.lock
jysn007@DESKTOP-DVQ9FB6:~$

---

## 답변

시스템 부팅 시점(09:48:20)부터 지금까지 30분 넘게 계속 재시작 루프가 돌고 있고, `lsof -i :3306`엔 아무것도 안 잡히는데도 mysqld 자신은 "주소가 이미 사용 중"이라고 하는 걸 보면, 문제는 TCP 3306이 아니라 다른 바인딩 주소(예: `mysqld.cnf`에 설정된 `bind-address = 127.0.0.1,172.18.0.1` 중 `172.18.0.1` — kind 브릿지 네트워크 게이트웨이 IP와 겹치는 값)일 가능성이 있습니다. mysqld 자체 에러 로그를 보면 정확한 원인이 나올 것 같습니다.

---

## 답변

mysqld의 실제 에러 로그를 확인해서 정확한 바인딩 실패 원인(IP:포트)을 찾아야 할 것 같습니다. 아래 명령을 실행해서 결과를 붙여넣어 주세요.

```
sudo tail -n 80 /var/log/mysql/error.log
```
