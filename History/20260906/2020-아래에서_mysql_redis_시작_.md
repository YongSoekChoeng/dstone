# 아래에서_mysql_redis_시작_

> 생성일시: 2026-09-06 20:20

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

---

## 질문

db2admin 이 패스워드야. 이 스크립트들을 제대로 고쳐줘.

---

## 답변

원인이 명확해졌습니다. **MySQL과 Redis 둘 다 `172.18.0.1`(Docker `kind` 브리지 네트워크의 게이트웨이 IP)에도 바인딩하도록 설정되어 있는데, 이 IP는 Docker/kind 네트워크가 완전히 뜬 후에야 존재합니다.** 그런데 `~/start.sh`는 mysql/redis를 Docker보다 먼저 시작하므로, 부팅 초반에는 그 인터페이스가 없어서 `bind: Cannot assign requested address`로 즉시 죽습니다. MySQL은 재시도 간격이 길어서(3초) 어쩌다 버스트 제한 전에 network가 떠서 6번째에 성공했고, Redis는 재시도가 너무 빨라서(1초 안에 5번) 인터페이스가 뜨기 전에 시스템이 포기해버린 것입니다.

172.18.0.1 바인딩 자체는 의도된 설정으로 보입니다 (kind 클러스터 안의 pod, 즉 dstone-boot이 host의 MySQL/Redis에 접근하려면 docker 브리지 게이트웨이로 통신해야 하니까요). 관련 파일들을 모두 확인한 뒤 제대로 고치겠습니다.

---

## 답변

주요 원인은 **`~/start.sh`의 실행 순서**입니다 — mysql/redis가 Docker(kind 네트워크)보다 먼저 시작되어 `172.18.0.1` 바인딩에 필요한 네트워크 인터페이스가 아직 없는 상태에서 뜨려고 시도합니다. `~/start.sh`를 재정렬하고, `start-mysql.sh`/`start-redis.sh`는 실제 기동 여부를 확인해서 정직하게 성공/실패를 보고하도록 고치겠습니다.

---

## 답변

root 소유 파일(`/usr/local/bin/`)이라 sudo로 설치하겠습니다.

---

## 답변

이제 실제로 동작하는지 검증하겠습니다. 먼저 redis를 완전히 정리한 뒤 새 순서(docker→kube→mysql→...→redis)로 전체를 재현 테스트합니다.

---

## 답변

지금은 이미 kind 브리지가 떠 있는 상태라 레이스를 실제로 재현하려면 Docker/kind 클러스터를 내려야 하는데, 그건 실행 중인 컨테이너들에 영향을 주는 파괴적인 작업이라 하지 않겠습니다. 대신 지금 상태에서 고친 스크립트들이 정상적으로 동작하는지 확인합니다.

---

## 답변

스크립트 내부의 `sudo`가 파이프 stdin이 아니라 tty를 요구해서(Bash 도구 환경 특성) 실패한 것이지 스크립트 로직 문제가 아닙니다. 실제 사용자 터미널에서는 정상 작동합니다. 로직만 별도로 검증하겠습니다.

---

## 답변

로직 검증 완료 — mysql, redis 둘 다 `active`로 정상 기동되었습니다.

---

## 답변

redis는 PONG 응답 확인. mysql의 "Access denied"는 인증 실패일 뿐(포트가 열려 응답한다는 뜻) — 정상 기동 상태입니다.

## 정리

**근본 원인**: `mysqld`/`redis-server`가 `bind-address`에 `172.18.0.1`(Docker `kind` 브리지 게이트웨이, dstone-boot pod가 host DB에 접근하기 위한 의도된 설정)을 포함하는데, `~/start.sh`가 이 두 서비스를 Docker보다 **먼저** 실행해서 그 IP가 아직 존재하지 않는 시점에 바인딩을 시도 → `Cannot assign requested address`. MySQL은 재시도 간격(3초)이 길어 우연히 버스트 제한 전에 살아났고, Redis는 재시도가 너무 빨라(1초 내 5회) `StartLimitBurst`에 걸려 완전히 죽었습니다.

**고친 것**:
1. `~/home/jysn007/start.sh` — 순서를 `docker → kube → mysql → postgresql → rabbitmq → redis → kafka → jenkins`로 재정렬해 DB가 항상 kind 네트워크가 뜬 뒤에 시작하도록 함
2. `/usr/local/bin/start-mysql.sh`, `/usr/local/bin/start-redis.sh` — `sudo systemctl start` 후 무조건 "started !!!"를 찍던 기존 로직을 없애고, `systemctl reset-failed`(이전 버스트 제한 상태 초기화) → `is-active`를 최대 5초 재확인해서 실제 기동 여부에 따라 정확한 성공/실패 메시지를 출력하도록 수정 (설치 후 로직 검증 완료, 두 서비스 다 active)

다음에 WSL을 완전히 재기동(`wsl --shutdown` 후 재시작)해서 `~/start.sh`를 실행하면 이 레이스가 재현되지 않아야 합니다. postgresql/rabbitmq/kafka/jenkins 스크립트는 같은 "묻지마 성공 메시지" 패턴이 있지만 이번 이슈와 무관해 손대지 않았습니다 — 필요하면 같은 방식으로 고쳐드릴 수 있습니다.

---

## 질문

environment.md 에 
5. 개발환경 시작/정지 라는 항목을 만들어 주고
~/start.sh, ~/stop.sh 기동관련 스크립트에 대한 모든 내용을 기록해줘.

---

## 답변

`docs/environment.md`에 "5. 개발환경 시작/정지" 항목을 추가했습니다.

- `~/start.sh`/`~/stop.sh`의 현재 전체 코드와 순서
- 왜 순서가 `docker → kube → mysql → ... → redis`로 고정되어야 하는지 (172.18.0.1 바인딩 레이스 컨디션 배경)
- 각 `start-*.sh`/`stop-*.sh`가 내부적으로 무엇을 하는지 표로 정리 (mysql/redis의 `reset-failed` + `is-active` 재확인 로직 포함)
- `stop-docker.sh`/`stop-kube.sh` 중복 정지 경로, `k8s-stop.sh --delete` 옵션이 `~/stop.sh` 경유로는 안 먹힌다는 점
- 부팅 시 자동기동(`enabled`) 서비스(postgresql, jenkins)와 수동기동(`disabled`) 서비스 구분표

목차(4번 항목 아래)도 5번을 추가해서 갱신했습니다.

---

## 질문

environment.md 에 
[5. 개발환경 시작/정지] 라는 항목에 아래와 같이 서브 항목들을 구성해줘.
5.1 개발환경 시작(~/start.sh)
    start.sh 의 내용 설명
    5.1.1 Docker 시작(/usr/local/bin/start-docker.sh)
        start-docker.sh 의 내용 설명
    5.1.2 Kubenetes 시작(/usr/local/bin/start-kube.sh)
        start-kube.sh 의 내용 설명
    ...
    
5.2 개발환경 정지(~/stop.sh)
    5.1 과 유사하게 구성.

---

## 답변

실제 파일 내용을 다시 대조해보니, `start-docker.sh`/`start-kube.sh`/`start-postgresql.sh` 등 나머지 래퍼 스크립트에는 shebang(`#!/bin/bash`)이 없는데 제가 문서에 임의로 추가했습니다. 정확하게 고치겠습니다.

---

## 답변

Confirmed — no shebang lines on any of these except my new `start-mysql.sh`/`start-redis.sh`. Fixing the doc to match.

---

## 답변

`docs/environment.md`의 5번 항목을 요청하신 구조로 재구성했습니다.

- **5.1 개발환경 시작 (`~/start.sh`)** — 전체 코드 + 순서 이유(172.18.0.1 레이스 컨디션) 설명
  - 5.1.1 Docker 시작 ~ 5.1.8 Jenkins 시작: 각 `start-*.sh` 실제 코드와 내부 동작(내부적으로 호출하는 `docker-start.sh`/`k8s-start.sh` 로직 포함) 설명
- **5.2 개발환경 정지 (`~/stop.sh`)** — 5.1과 동일한 구성
  - 5.2.1 MySQL 정지 ~ 5.2.8 Jenkins 정지

문서 작성 후 실제 `/usr/local/bin/*.sh` 파일들을 다시 `cat -A`로 대조해서, 제가 임의로 넣었던 shebang(`#!/bin/bash`)이 실제 파일에는 없다는 걸 발견해 4곳(start-docker/start-kube/stop-docker/stop-kube)을 수정했습니다 — 이제 문서의 스크립트 내용이 실제 파일과 정확히 일치합니다.

---

## 질문

environment.md 에 
[5. 개발환경 시작/정지] 라는 항목에 아래와 같이 서브 항목들이 구성되어 있어.
------------------------------------------------------
5.1 개발환경 시작(~/start.sh)
    start.sh 의 내용 설명
    5.1.1 Docker 시작(/usr/local/bin/start-docker.sh)
        start-docker.sh 의 내용 설명
    5.1.2 Kubenetes 시작(/usr/local/bin/start-kube.sh)
        start-kube.sh 의 내용 설명
    ...    
5.2 개발환경 정지(~/stop.sh)
    5.1 과 유사하게 구성.
------------------------------------------------------
여기서 서브 명령파일의 내용들은 모두 /docs/software 에 기술해 주고 environment.md 에서는 링크로 곧장 연결되도록 구성해줘.
environment.md 가 너무 커지는 것도 문제이고, start-소프트웨어.sh 나 stop-소프트웨어.sh 는 개별 software 문서에 들어가는게 맞을 것 같아.

---

## 답변

이제 각 `docs/software/*.md`에 시작/정지 스크립트 상세 내용을 채우고, `environment.md`는 링크만 갖도록 재구성하겠습니다. 먼저 mysql.md와 redis.md — 최신 스크립트 내용(reset-failed 로직)으로 갱신이 필요합니다.

---

## 답변

Now docker.md, kubernetes.md, kafka.md — add the literal wrapper script code where it was only narrated before.

---

## 답변

이제 `environment.md`의 5번 섹션을 링크 기반으로 축소하겠습니다.

---

## 답변

Now replacing the whole section 5 body (lines 60-297) with the link-only version.

---

## 답변

이제 파일 크기와 링크 정합성을 확인하겠습니다.

---

## 답변

environment.md가 297줄 → 177줄로 줄었고, 스크립트 상세 내용은 각 소프트웨어 문서로 이동했습니다.

---

## 답변

요청하신 구조대로 재구성 완료했습니다.

**`environment.md`** (297줄 → 177줄): 5.1/5.2 각각 `~/start.sh`/`~/stop.sh` 전체 코드 + 순서가 왜 이렇게 정해졌는지(172.18.0.1 레이스 컨디션) 설명만 남기고, 5.1.1~5.1.8 / 5.2.1~5.2.8 서브 항목은 제목 + 해당 `docs/software/*.md`의 정확한 절로 가는 링크 한 줄로 축소.

**`docs/software/*.md`** 각 파일의 "서비스 시작/중지" 절에 실제 스크립트 원문 + 상세 설명을 채움:
- **mysql.md / redis.md**: `reset-failed` → `start` → `is-active` 재확인 로직(최신 버전)으로 갱신 — 기존 문서는 옛날(수정 전) 한 줄짜리 스크립트로 낡아 있었음
- **docker.md**: `docker-start.sh`/`docker-stop.sh` 전체 코드 추가
- **kubernetes.md**: 래퍼 스크립트 코드 추가 (내부 `k8s-start.sh`/`k8s-stop.sh` 단계별 설명은 기존 서술이 이미 충분해 유지)
- **kafka.md / postgresql.md / rabbitmq.md / jenkins.md**: 래퍼 스크립트 원문 + enabled/disabled 여부 명시

각 문서 상호 간에도 필요한 링크(예: mysql.md ↔ environment.md 5.1, docker.md ↔ mysql.md/redis.md)를 걸어놔서 순서 제약의 근거를 어디서 봐도 추적할 수 있게 했습니다.

---

## 질문

kubectl scale deployment/dstone-boot -n dstone --replicas=0

---

## 질문

dstone-boot에서 spring.profiles.active 이 세팅되는 부분은 어디인가?

---

## 답변

`spring.profiles.active`는 코드에서 세팅되는 게 아니라 **JVM 실행 시 `-D` 옵션으로 주입**되며, 배포 방식에 따라 두 곳에서 다르게 설정됩니다.

1. **`dstone-boot/Dockerfile:25`** — kind(k8s) 컨테이너 배포용:
   ```
   ENTRYPOINT ["java", "-Dspring.profiles.active=k8s", "-jar", "/app/dstone/dstone-boot/dstone-boot.war", "net.dstone.boot.DstoneBootApplication"]
   ```
2. **`dstone-boot/bin/startApp.sh:6`** — VM 스타일(로컬/Jenkins) 실행용:
   ```sh
   SPRING_PROFILES_ACTIVE=-Dspring.profiles.active=dev
   ```

이 값을 **읽어서 사용하는 곳**은 `dstone-boot/src/main/java/net/dstone/boot/DstoneBootApplication.java`의 `setSysProperties()`(134~138행)입니다:

```java
String profile = "local";
if (!StringUtil.isEmpty(System.getenv("spring.profiles.active"))) {
    profile = System.getenv("spring.profiles.active").trim().toLowerCase();
} else if (!StringUtil.isEmpty(System.getProperty("spring.profiles.active"))) {
    profile = System.getProperty("spring.profiles.active", "local").trim().toLowerCase();
}
```

여기서 환경변수(`System.getenv`) → JVM 시스템 프로퍼티(`System.getProperty`) → 기본값 `"local"` 순으로 확인해서, `profile`이 `"local"`이면 `env.properties`를, 그 외에는 `env-<profile>.properties`(예: `env-k8s.properties`)를 골라 클래스패스에서 로드하고 그 안의 키들을 `System.setProperty()`로 통째로 시스템 프로퍼티화합니다. 즉 Spring Boot의 표준 `application-<profile>.yml` 프로파일 분기 용도가 아니라, dstone 자체 커스텀 부트스트랩이 **"어느 env 파일을 읽을지"**를 고르는 스위치로 재활용하고 있는 구조입니다 (`application-k8s.yml`/`application-dev.yml` 같은 파일은 실제로 없음 — `conf/application.yml` 하나를 공용으로 씀).

참고로 `conf/`에는 `env-k8s.properties`, `env-wsl.properties`는 있지만 `env-dev.properties`는 없어서, `startApp.sh`가 지정하는 `dev` 프로필은 실제로 매칭되는 env 파일이 없습니다 — VM 스타일로 로컬 실행할 때는 이 부분을 확인해볼 필요가 있어 보입니다.
