# 아래에서_mysql_redis_시작_

> 생성일시: 2026-09-06 17:51

## 질문

아래에서 mysql, redis 시작 시 보이는 에러메세지는 무엇을 의미하는가? 그리고 해결책은?


jysn007@DESKTOP-DVQ9FB6:/app/dstone$ ~/start.sh
[sudo: authenticate] 비밀번호:
Job for mysql.service failed because the control process exited with error code.
See "systemctl status mysql.service" and "journalctl -xeu mysql.service" for details.
Mysql started !!!
Postgresql started !!!
Rabbitmq started !!! Admin Console URL : http://localhost:15672
Job for redis-server.service failed because the control process exited with error code.
See "systemctl status redis-server.service" and "journalctl -xeu redis-server.service" for details.
Redis started !!! For realtime administration, Use Tool /DB/Tools/Redis-Desktop-Manager/Version/Another Redis Desktop Manager.exe
Kafka를 백그라운드로 시작합니다...
Kafka가 시작되었습니다. (PID: 994)
로그: /opt/kafka/kafka_2.13-4.2.1/logs/kafka-server.out
Kafka started !!!
Kafbat UI를 백그라운드로 시작합니다...
Kafbat UI가 시작되었습니다. (PID: 1444)
로그: /opt/kafka/admin-tools/KafbatUI/logs/kafbat-ui.out
KafbatUI(Kafka관리툴) started !!! Admin Console URL : http://localhost:9099
Starting Docker...
Waiting for Docker
Docker is ready.
Docker started !!!
[k8s-start] docker 데몬이 이미 실행 중입니다.
[k8s-start] 로컬 레지스트리 컨테이너가 이미 실행 중입니다.
[k8s-start] kind 클러스터 'dev' 는 이미 존재합니다. (재시작 시 자동 복원됨)
[k8s-start] 노드 상태 확인 중...
[k8s-start] WARNING: kubectl로 노드 조회에 실패했습니다. kind 컨테이너 상태를 확인하세요.
CONTAINER ID   IMAGE                  COMMAND                   CREATED      STATUS         PORTS                       NAMES
41768dcdfa0f   kindest/node:v1.32.2   "/usr/local/bin/entr…"   3 days ago   Up 7 seconds   127.0.0.1:45429->6443/tcp   dev-control-plane
Kubenetes started !!!
Jenkins started !!!
jysn007@DESKTOP-DVQ9FB6:/app/dstone$

---

## 답변

dstone 환경의 mysql/redis systemd 에러를 진단하기 위해 실제 서비스 상태와 start.sh 스크립트를 확인하겠습니다.

---

## 답변

원인을 좁혀보니 두 서비스가 서로 다른 상황입니다.

## 확인된 사실

**MySQL** — journal을 보면 17:47:33~17:47:48 사이 systemd가 `mysqld`를 5번 연속 실패시킨 뒤 6번째 시도에서 성공했습니다 (`Started mysql.service`). 지금 `systemctl is-active mysql.service` → **active**, 즉 현재는 정상 기동 중입니다. `start-mysql.sh`가 실행한 `sudo systemctl start mysql` 명령 자체는 (systemd가 아직 재시작 중일 때 리턴해서) 실패로 끝났지만, 스크립트가 종료 코드를 검사하지 않고 무조건 `echo "Mysql started !!!"`를 찍기 때문에 로그에 실패 메시지 + 성공 메시지가 같이 보인 것입니다. 결과적으로 지금은 떠 있습니다.

**Redis** — journal을 보면 17:47:42~43 사이 **1초도 안 되는 간격**으로 5번 연속 즉시 실패했고, systemd의 `StartLimitBurst`(기본 5회/10초)에 걸려 `Start request repeated too quickly`로 아예 포기했습니다. 지금 `systemctl is-active redis-server.service` → **failed**. MySQL과 달리 재시도로 살아나지 않았고, `start-redis.sh`도 종료 코드를 안 보고 "Redis started !!!"를 찍은 것뿐 — **실제로는 redis가 떠 있지 않습니다.**

거의 즉시(0~1초) 반복 실패하는 패턴은 리소스 경합(디스크/CPU 느림)보다는 **설정 오류나 포트 충돌** 쪽에 훨씬 가깝습니다 (리소스 경합이면 MySQL처럼 몇 초씩 걸리다 실패했을 것). mysql/redis 로그 파일과 프로세스 상태는 `sudo`가 터미널 인증을 요구해서 제가 직접 읽을 수 없었습니다. 아래 명령을 `!` 접두사로 직접 실행해서 결과를 알려주시면 정확한 원인을 짚어드릴 수 있습니다.

---

## 답변

포트 3306은 지금 mysqld(pid 1765)가 정상적으로 점유 중이고, 6379는 아무도 듣고 있지 않습니다 — redis는 현재 완전히 죽어있는 상태가 맞습니다. 다만 sudo가 인터랙티브 인증을 요구해서 실제 에러 로그(원인)는 제가 직접 못 읽습니다. 아래 명령들을 터미널에서 직접 실행해 주시면 (또는 `!` 접두사로) 정확한 원인을 알려드리겠습니다:

```bash
sudo tail -50 /var/log/redis/redis-server.log
sudo -u redis redis-server /etc/redis/redis.conf --daemonize no   # 포그라운드로 직접 실행해서 즉시 나오는 에러 확인
sudo systemctl reset-failed redis-server.service                  # StartLimitBurst 리셋 (재시도 가능하게)
sudo systemctl start redis-server.service
sudo systemctl status redis-server.service --no-pager -l
```

## 지금까지 결론과 조치

**MySQL**: 지금 정상 작동 중이니 당장 조치 불필요합니다. 다만 매번 부팅 시 5번 실패 후 겨우 살아나는 건 비정상이니, 근본 원인(아래)을 잡아두는 게 좋습니다.

**Redis**: 죽어있으므로 위 명령으로 재시작해야 합니다. `reset-failed`로 systemd의 실패 카운터를 초기화한 뒤 다시 `start`하면 즉시 원인 에러가 `status` 출력에 그대로 나타날 겁니다.

**두 서비스 모두 즉시/반복 실패하는 흔한 WSL 원인들** (로그 확인 전 참고용):
1. **부팅 시 `/run/mysqld`, `/run/redis` 같은 tmpfs 런타임 디렉터리가 아직 안 만들어진 상태에서 여러 서비스(mysql/postgresql/rabbitmq/redis/kafka)가 `~/start.sh`로 거의 동시에 기동**되면서 디스크·CPU 경합 → 소켓 바인딩/초기화 타임아웃. (MySQL은 재시도 간격이 3초라 결국 버텨서 성공, Redis는 버스트 제한에 먼저 걸려 포기)
2. **WSL을 완전히 종료(`wsl --shutdown`)하지 않고 재기동**해서 이전 세션의 `dump.rdb`/`ib_logfile` 잠금 파일이나 좀비 프로세스가 남아있는 경우
3. redis.conf의 `bind`/`unixsocket` 경로 문제 (권한 없는 디렉터리를 가리키는 경우 즉시 exit 1)

로그 출력 주시면 바로 근본 원인과 확정 해결책(예: `start.sh`에 각 서비스 사이 `sleep`이나 systemd `Wants=`/`After=` 순서 조정 추가) 드리겠습니다.
