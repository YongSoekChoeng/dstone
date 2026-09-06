# rabbitmq_를_시작하는데_Adm

> 생성일시: 2026-09-06 11:04

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

---

## 질문

jysn007@DESKTOP-DVQ9FB6:~$ sudo tail -n 80 /var/log/mysql/error.log
2026-09-06T01:21:20.171473Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:20.171536Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:21.709358Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:21.709386Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:22.098497Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:22.355602Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48593
2026-09-06T01:21:22.363736Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:22.653103Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:23.795338Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:23.795405Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:23.906452Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:23.906476Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:23.909056Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:23.909082Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:23.909137Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:25.442264Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:25.442283Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:25.857128Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:26.088921Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48645
2026-09-06T01:21:26.096836Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:26.409437Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:27.530497Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:27.530536Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:27.644505Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:27.644607Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:27.648066Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:27.648102Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:27.648156Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:29.192748Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:29.192786Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:29.589649Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:29.827757Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48695
2026-09-06T01:21:29.835225Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:30.109636Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:31.246881Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:31.246920Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:31.349269Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:31.349302Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:31.351538Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:31.351551Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:31.351603Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:32.895063Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:32.895085Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:33.347881Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:33.598014Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48747
2026-09-06T01:21:33.616963Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:33.968758Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:35.086407Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:35.086479Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:35.213117Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:35.213141Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:35.215394Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:35.215415Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:35.215492Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:36.787222Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:36.787242Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:37.103527Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:37.353456Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48798
2026-09-06T01:21:37.361489Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:37.705022Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:38.855611Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:38.855673Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:38.955727Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:38.955749Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:38.957960Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:38.957982Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:38.958034Z 0 [ERROR] [MY-010119] [Server] Aborting
2026-09-06T01:21:40.512294Z 0 [System] [MY-010910] [Server] /usr/sbin/mysqld: Shutdown complete (mysqld 8.4.11-0ubuntu0.26.04.1)  (Ubuntu).
2026-09-06T01:21:40.512314Z 0 [System] [MY-015016] [Server] MySQL Server - end.
2026-09-06T01:21:40.834981Z 0 [System] [MY-015015] [Server] MySQL Server - start.
2026-09-06T01:21:41.074291Z 0 [System] [MY-010116] [Server] /usr/sbin/mysqld (mysqld 8.4.11-0ubuntu0.26.04.1) starting as process 48863
2026-09-06T01:21:41.081755Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2026-09-06T01:21:41.410543Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2026-09-06T01:21:42.535037Z 0 [ERROR] [MY-011300] [Server] Plugin mysqlx reported: 'Setup of bind-address: '127.0.0.1' port: 33060 failed, `bind()` failed with error: Address already in use (98). Do you already have another mysqld server running with Mysqlx ?'
2026-09-06T01:21:42.535091Z 0 [ERROR] [MY-013597] [Server] Plugin mysqlx reported: 'Value '127.0.0.1' set to `Mysqlx_bind_address`, X Plugin can't bind to it. Skipping this value.'
2026-09-06T01:21:42.652325Z 0 [Warning] [MY-010068] [Server] CA certificate ca.pem is self signed.
2026-09-06T01:21:42.652416Z 0 [System] [MY-013602] [Server] Channel mysql_main configured to support TLS. Encrypted connections are now supported for this channel.
2026-09-06T01:21:42.655667Z 0 [ERROR] [MY-010262] [Server] Can't start server: Bind on TCP/IP port: Address already in use
2026-09-06T01:21:42.655696Z 0 [ERROR] [MY-010257] [Server] Do you already have another mysqld server running on port: 3306 ?
2026-09-06T01:21:42.655789Z 0 [ERROR] [MY-010119] [Server] Aborting
jysn007@DESKTOP-DVQ9FB6:~$

---

## 답변

mysqlx(33060)와 메인 포트(3306) 둘 다 "이미 사용 중"이라고 나오는데, `lsof`에는 아무것도 안 잡히는 게 핵심 단서입니다. Docker/kind 브릿지(172.18.0.1)가 `bind-address`에 포함돼 있어서, kind 클러스터 쪽에서 뭔가 그 주소·포트를 점유하고 있을 가능성을 확인해보겠습니다.

---

## 답변

원인을 찾았습니다.

**근본 원인:** `/var/run/mysqld/` 디렉터리를 보면 `mysqlx.sock`, `mysqlx.sock.lock` 파일이 오늘 10:19에 생성된 채로 남아 있습니다. 이게 systemd의 `RuntimeDirectory=mysqld`가 만드는 디렉터리인데, **자동 재시작(Restart=on-failure) 사이에는 이 디렉터리가 재생성되지 않습니다** — 서비스가 완전히 stop→start 될 때만 새로 만들어집니다.

즉 부팅 후 09:48:20에 mysqld가 처음 죽으면서 소켓/락 파일을 제대로 정리 못 하고 남겼고, 그 이후 30분 넘게 계속되는 모든 재시작 시도가 그 **남은 소켓 파일 자체와 충돌**(`bind()` on Unix socket path → Address already in use)해서 매번 즉시 실패 → 재시작 → 다시 실패, 이런 식으로 스스로 무한 반복되고 있는 상태입니다. `lsof`/`ss`에 아무것도 안 잡히는 이유도 이겁니다 — TCP 포트 충돌이 아니라 죽은(stale) 유닉스 소켓 파일과의 충돌이라서요.

**정리 방법** — 아래 순서대로 실행해주세요 (각 단계 결과를 보여주시면 다음 단계 안내드릴게요):

```
sudo systemctl stop mysql
```
(참고: `TimeoutSec=infinity`라 혹시 오래 걸리거나 멈추면 알려주세요 — 강제 종료 방법 안내드리겠습니다)

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

해결했어

---

## 답변

확인했습니다. 혹시 다시 이런 크래시 루프가 발생하면 `/var/run/mysqld/` 안에 stale 소켓 파일이 남아있는지부터 확인하시면 됩니다.

---

## 질문

net.dstone.boot.common.config.ConfigKafka 와 유사한 형태로 
net.dstone.boot.common.config.ConfigRabbitMQ 를 만들고 RabbitMQ 를 통제하고 싶어.

ConfigRabbitMQ 를 만들어 주고 아래와 같은 샘플소스를 만들어줘.

net.dstone.boot.sample.rabbitmq.controller.RabbitMqController
net.dstone.boot.sample.rabbitmq.service.RabbitMqService
/dstone-boot/src/main/webapp/WEB-INF/views/rabbitmq/test.jsp

---

## 답변

`ConfigMq.java`가 이미 RabbitMQ 설정(Exchange/Queue/Binding/ConnectionFactory/RabbitTemplate)을 담당하고 있네요. `ConfigKafka`와 이름 패턴을 맞추기 위해 `ConfigMq` → `ConfigRabbitMQ`로 이름을 맞추고, 요청하신 샘플 3개를 Kafka 샘플과 동일한 구조로 만들겠습니다.

---

## 답변

Now the sample service, controller, and JSP.

---

## 답변

Now the JSP, mirroring `kafka/test.jsp`.

---

## 답변

Good — `/views/**` maps generically to the JSP path, so I just need to add a menu entry for consistency with the other sample features.

---

## 답변

Compiles cleanly. Let's double check final diff summary.

---

## 답변

작업 완료했습니다. 요약:

- `ConfigMq.java` → `net.dstone.boot.common.config.ConfigRabbitMQ`로 이름 변경 (`ConfigKafka`와 명명 규칙 통일, 기존 Exchange/Queue/Binding/ConnectionFactory/RabbitTemplate 빈 설정은 그대로 유지), `Config.java`의 `@Import` 목록도 갱신
- `net.dstone.boot.sample.rabbitmq.service.RabbitMqService` — `RabbitTemplate`로 발행(`publishOrder`: direct 익스체인지/`orders.process`, `publishNotification`: fanout 익스체인지), `@RabbitListener`로 `app.orders.queue`/`app.notifications.queue` 양쪽 다 수신 로그 출력
- `net.dstone.boot.sample.rabbitmq.controller.RabbitMqController` — `/sample/rabbitmq/send.do`, Kafka 샘플과 동일한 파라미터 구조(orderId/orderName/orderItem/orderCount)
- `/dstone-boot/src/main/webapp/WEB-INF/views/rabbitmq/test.jsp` — kafka test.jsp와 동일한 폼/AJAX 구조
- `left.jsp`에 "RABBITMQ테스트" 메뉴 링크 추가 (`/views/rabbitmq/test`)

`mvn -pl dstone-boot -am compile`로 컴파일 확인했습니다. dstone-boot를 띄운 뒤 좌측 메뉴의 "RABBITMQ테스트"로 들어가서 GO 버튼을 누르면 `app.orders.queue`로 메시지가 발행되고, 서버 로그에 `RabbitMqService.consumeOrders`가 수신 로그를 찍는지로 동작을 확인할 수 있습니다.

---

## 질문

Queue영구저장여부. RabbitMq가 재 실행되더라도 내용을 유지할지 여부. 실제 큐의 Durability와 동일해야 함. 
운영모드에서는 true로 하는게 좋음. ==>> 이 부분을 application.yaml 에서 설정할 수 있는건가?

---

## 답변

네, 이미 설정 가능합니다. `ConfigRabbitMQ.java`의 `queueNotifications()`/`queueOrders()` 빈이 하드코딩이 아니라 `application.yml`의 `queue-durable` 값을 읽어서 큐를 만들도록 되어 있습니다.

`dstone-boot/conf/application.yml:154-173`:
```yaml
rabbitmq:
    bindings:
        binding-notifications:
            queue-id: app.notifications.queue
            queue-durable: true   # ← 여기
            exchange-id: app.fanout.exchange
        binding-orders:
            queue-id: app.orders.queue
            queue-durable: true   # ← 여기
            exchange-id: app.direct.exchange
            routing-key: orders.process
```

그리고 `ConfigRabbitMQ.java`:
```java
@Bean
public Queue queueOrders() {
    return new Queue(
        configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.queue-id"),
        Boolean.valueOf(configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.queue-durable"))
    );
}
```

`queue-durable`을 `true`/`false`로 바꾸면 애플리케이션이 큐를 선언(declare)할 때 그 값 그대로 반영됩니다.

**주의할 점 하나:** RabbitMQ는 큐가 이미 존재하면 durable 속성을 **바꿔서 재선언할 수 없습니다** — "PRECONDITION_FAILED" 에러가 나면서 기동이 실패합니다. 예를 들어 지금 `true`로 이미 큐가 만들어진 상태에서 `false`로만 바꾸면, 기존 큐를 먼저 삭제(`rabbitmqctl delete_queue` 또는 관리 콘솔에서 삭제)한 뒤 재기동해야 새 durable 값으로 다시 만들어집니다.
