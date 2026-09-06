# 개발 환경 (WSL) 소프트웨어 목록

## 목차

- [1. 운영 원칙 (중요)](#1-운영-원칙-중요)
- [2. 목록](#2-목록)
- [3. 클라우드 아키텍처 시뮬레이션](#3-클라우드-아키텍처-시뮬레이션)
- [4. dstone 프로젝트와의 연결 관계](#4-dstone-프로젝트와의-연결-관계)
- [5. 개발환경 시작/정지 (`~/start.sh` / `~/stop.sh`)](#5-개발환경-시작정지-startsh--stopsh)

dstone 프로젝트를 개발하기 위해 WSL(Ubuntu) 환경에 수동으로 설치·구성한 소프트웨어 목록이다.
각 항목의 설치 방법 및 상세 설정은 `docs/software/{소프트웨어명}.md` 문서를 참고한다.

- 최초 작성일: 2026-09-03
- 대상 환경: WSL2 Ubuntu 26.04 LTS (Resolute Raccoon)
- 갱신 방식: 새 소프트웨어를 설치하거나 주요 설정을 변경할 때마다 이 문서와 `docs/software/*.md`를 함께 갱신한다.

## 1. 운영 원칙 (중요)

WSL은 기본적으로 systemd 서비스를 부팅 시 자동 기동하지 않도록 운용 중이다. 그래서 DB/메시징/CI 등 대부분의 서비스는
`/usr/local/bin/start-*.sh`, `/usr/local/bin/stop-*.sh` 스크립트로 수동 기동/중지한다 (아래 표의 "시작 스크립트" 참고).
WSL을 재시작했다면 필요한 서비스를 먼저 `start-*.sh`로 올려야 한다.

## 2. 목록

| 구분 | 소프트웨어 | 버전(확인 시점) | 설치 방식 | 주요 포트 | 시작 스크립트 | 상세 문서 |
|---|---|---|---|---|---|---|
| 언어/런타임 | OpenJDK (JDK) | 21.0.12 | Ubuntu 공식 저장소 (`apt install openjdk-21-jdk`) | - | - | [jdk.md](software/jdk.md) |
| 빌드 도구 | Apache Maven | 3.9.12 | Ubuntu 공식 저장소 (`apt install maven`) | - | - | [maven.md](software/maven.md) |
| 형상관리 | Git | 2.53.0 | Ubuntu 공식 저장소 (`apt install git`) | - | - | [git.md](software/git.md) |
| 데이터베이스 | MySQL Server | 8.4.11 | Ubuntu 공식 저장소 (`apt install mysql-server`) | 3306 | `start-mysql.sh` / `stop-mysql.sh` | [mysql.md](software/mysql.md) |
| 데이터베이스 | PostgreSQL | 18.6 | Ubuntu 공식 저장소 (`apt install postgresql`) | 5432 | `start-postgresql.sh` / `stop-postgresql.sh` | [postgresql.md](software/postgresql.md) |
| 캐시/세션 | Redis | 8.0.5 | Ubuntu 공식 저장소 (`apt install redis-server`) | 6379 | `start-redis.sh` / `stop-redis.sh` | [redis.md](software/redis.md) |
| 메시지 큐 | RabbitMQ | 4.0.5 | Ubuntu 공식 저장소 (`apt install rabbitmq-server`) | 5672 (AMQP), 15672 (관리 콘솔) | `start-rabbitmq.sh` / `stop-rabbitmq.sh` | [rabbitmq.md](software/rabbitmq.md) |
| 메시지 큐 | Apache Kafka (KRaft 모드) | 4.2.1 (Scala 2.13) | 수동 설치 (tar.gz, `/opt/kafka`) | 9092 (broker), 9093 (controller) | `/opt/kafka/kafka-start.sh` (`start-kafka.sh`에 포함) | [kafka.md](software/kafka.md) |
| 관리 도구 | Kafbat UI (Kafka 관리 콘솔) | jar 배포판 | 수동 설치 (jar, `/opt/kafka/admin-tools/KafbatUI`) | 9099 | `/opt/kafka/admin-tools/KafbatUI/start.sh` (`start-kafka.sh`에 포함) | [kafbat-ui.md](software/kafbat-ui.md) |
| 컨테이너 | Docker CE + Compose plugin | 29.7.2 / Compose v5.5.0 | Docker 공식 저장소 (`download.docker.com`) | - (unix socket) | `start-docker.sh` / `stop-docker.sh` | [docker.md](software/docker.md) |
| 컨테이너 오케스트레이션 | kubectl + kind (로컬 K8s) | kubectl v1.37.0 / kind v0.27.0 | 수동 설치 (바이너리 다운로드, `/usr/local/bin`) | kind API 서버는 임의 포트 | `start-kube.sh` (`k8s-start.sh`) / `stop-kube.sh` (`k8s-stop.sh`) | [kubernetes.md](software/kubernetes.md) |
| CI/CD | Jenkins | 2.568.3 | Jenkins 공식 저장소 (`pkg.jenkins.io`) | 8080 | `start-jenkins.sh` / `stop-jenkins.sh` | [jenkins.md](software/jenkins.md) |
| 런타임(부가) | Node.js + npm | 20.20.2 / 10.8.2 | NodeSource 저장소 (`deb.nodesource.com`) | - | - | [nodejs.md](software/nodejs.md) |

## 3. 클라우드 아키텍처 시뮬레이션

dstone-boot는 [kind](software/kubernetes.md)에 컨테이너 Pod로, dstone-batch/dstone-batchadmin은 systemd 없이 `bin/*.sh` 쉘 스크립트로 제어되는 VM 스타일 프로세스로 운용한다. MySQL/Redis/RabbitMQ/Kafka는 CSP 매니지드 서비스에 대응시켜 클러스터 바깥에 둔다. 설계 배경과 네트워킹/레지스트리/CI-CD 세부사항은 [cloud-architecture.md](cloud-architecture.md) 참고.

## 4. dstone 프로젝트와의 연결 관계

- **MySQL**: `dstone-common`/`dstone-boot`/`dstone-batch`/`dstone-batchadmin` 공통 메인 데이터 저장소(`sampleDB` 등). `conf/env.properties`의 `DB_HOST`/`DB_PORT`로 접속 정보 주입.
- **Redis**: `dstone-boot`의 분산 세션 저장소(`dstone:session` 네임스페이스).
- **RabbitMQ**: `dstone-boot`의 메시지 큐 연동.
- **Kafka**: 애플리케이션에서 메시징 연동 실험/개발용 (로컬 KRaft 단일 브로커).
- **Jenkins**: `dstone-batch/Jenkinsfile`, `dstone-boot/Jenkinsfile` 파이프라인 실행.
- **Docker / kind**: `dstone-boot`을 컨테이너 이미지로 빌드해 로컬 `kind` 클러스터에 Pod로 배포하는 환경(`dstone-boot/Dockerfile`, `dstone-boot/k8s/`). 상세는 [cloud-architecture.md](cloud-architecture.md) 참고.
- **PostgreSQL / Node.js**: 현재 dstone 서비스 자체 설정(`application.yml`)에서는 사용하지 않는 것으로 보이며, 개발 환경 실습/부가 도구 용도로 설치되어 있음. 실제 프로젝트 연동이 생기면 이 문서와 CLAUDE.md를 갱신할 것.

## 5. 개발환경 시작/정지 (`~/start.sh` / `~/stop.sh`)

WSL을 새로 띄운 뒤 개발환경 전체를 한 번에 올리고 내리기 위한 최상위 스크립트다. 각 소프트웨어별 `start-*.sh`/`stop-*.sh`(`/usr/local/bin`, root 소유, `755`)를 순서대로 호출하기만 하는 얇은 래퍼이며, 개별 소프트웨어의 설치/설정 자체는 [2. 목록](#2-목록) 표의 "상세 문서" 링크를 참고한다.

### 5.1 개발환경 시작 (`~/start.sh`)

```sh
#! /bin/sh

# docker (kind 브리지 네트워크(172.18.0.1)가 떠 있어야 mysql/redis 바인딩이 성공하므로
#         이 두 서비스보다 먼저 기동해야 한다)
start-docker.sh

# kubenetes
start-kube.sh

# mysql
start-mysql.sh

# postgresql
start-postgresql.sh

# rabbitmq
start-rabbitmq.sh

# redis
start-redis.sh

# kafka
start-kafka.sh

# jenkins
start-jenkins.sh
```

`/usr/local/bin`에 있는 개별 `start-*.sh`를 순서대로 실행하기만 하는 쉘 스크립트다. **순서가 이렇게 정해진 이유**: MySQL(`mysqld.cnf`)과 Redis(`redis.conf`)는 `bind-address`/`bind`에 `127.0.0.1`뿐 아니라 `172.18.0.1`(Docker의 `kind` 브리지 네트워크 게이트웨이)도 포함하고 있다. `kind` 클러스터 안에서 도는 `dstone-boot` Pod가 host의 MySQL/Redis에 접근하려면 이 게이트웨이 IP로 접속해야 하기 때문이다. 이 IP는 dockerd가 뜨고 `kind` 네트워크 브리지(`br-*`)가 attach된 뒤에만 존재하므로, **Docker/kind가 뜨기 전에 mysql·redis를 먼저 켜면 `bind: Cannot assign requested address`로 기동이 실패한다.** (2026-09-06 장애 진단 참고 — 과거에는 mysql/postgresql/rabbitmq/redis/kafka → docker → kube 순서였고, MySQL은 systemd 자동 재시작 타이밍이 맞아 우연히 살아났지만 Redis는 `StartLimitBurst`(기본 5회/10초)에 걸려 항상 죽어 있었다.) 이 레이스 컨디션 때문에 현재는 **Docker(및 kind 네트워크)를 가장 먼저 올리고, mysql/redis를 포함한 나머지 서비스는 그 뒤에** 올리는 순서로 고정되어 있다. 순서를 바꿀 때는 이 제약을 반드시 유지할 것.

#### 5.1.1 Docker 시작 (`/usr/local/bin/start-docker.sh`)

```sh
/usr/local/bin/docker-start.sh
echo "Docker started !!!"
```

실제 로직은 `docker-start.sh`에 있다: `pgrep -x dockerd`로 이미 떠 있는지 확인하고, 없으면 `sudo dockerd`를 `nohup`으로 백그라운드 기동한 뒤 `docker info`가 성공할 때까지 최대 30초 동안 1초 간격으로 폴링한다. 30초 안에 뜨지 않으면 실패로 종료하고 dockerd 로그(`~/.docker/dockerd.log`)를 tail로 보여준다. `docker.service`는 `systemctl is-enabled docker` 기준 `disabled`라 systemd가 아니라 이 스크립트가 직접 프로세스를 관리한다.

#### 5.1.2 Kubernetes 시작 (`/usr/local/bin/start-kube.sh`)

```sh
/usr/local/bin/k8s-start.sh
echo "Kubenetes started !!!"
```

실제 로직은 `k8s-start.sh`에 있다: ① 현재 사용자가 `docker` 그룹에 속해 있는지 확인 → ② dockerd가 응답하는지 확인하고 안 되면 `sudo systemctl start docker`로 기동 후 최대 30초 대기 → ③ 로컬 사설 레지스트리 컨테이너(`kind-registry`, `127.0.0.1:5000`, ECR/GCR 대체용)가 없으면 생성 → ④ `kind` 클러스터(`dev`)가 없으면 위 레지스트리를 미러로 등록한 설정과 함께 새로 생성하고, 있으면 그대로 재사용(재시작 시 자동 복원) → ⑤ 레지스트리 컨테이너를 `kind` 도커 네트워크에 연결(이때 `172.18.0.1` 브리지가 확정적으로 존재하게 됨) → ⑥ `kubectl get nodes`로 노드 상태를 확인한다. `KUBECONFIG` 기본값은 `/etc/kind/dev.config`.

#### 5.1.3 MySQL 시작 (`/usr/local/bin/start-mysql.sh`)

```sh
#!/bin/sh

# 이전 실행에서 StartLimitBurst에 걸려 failed 상태로 남아있으면 start가 거부되므로 먼저 리셋
sudo systemctl reset-failed mysql.service >/dev/null 2>&1

sudo systemctl start mysql

for i in 1 2 3 4 5; do
    if systemctl is-active --quiet mysql.service; then
        echo "Mysql started !!!"
        exit 0
    fi
    sleep 1
done

echo "Mysql FAILED to start. Check: sudo systemctl status mysql.service / sudo journalctl -xeu mysql.service" >&2
exit 1
```

`mysql.service`는 `disabled`라 WSL 부팅 시 자동으로 뜨지 않는다. 과거 버전은 `sudo systemctl start mysql`의 종료 코드를 확인하지 않고 무조건 "Mysql started !!!"를 출력해서, 5.1 상단의 레이스 컨디션으로 실제 기동이 실패했을 때도 성공한 것처럼 로그가 찍히는 문제가 있었다. 2026-09-06에 다음과 같이 수정했다: `systemctl reset-failed`로 이전 실행에서 남은 실패 상태를 지우고 → `start` 실행 → 최대 5초간 `systemctl is-active`를 재확인해서 실제 상태에 따라 정확한 성공/실패 메시지를 출력하도록 함.

#### 5.1.4 PostgreSQL 시작 (`/usr/local/bin/start-postgresql.sh`)

```sh
sudo systemctl start postgresql
echo "Postgresql started !!!"
```

`postgresql.service`는 `systemctl is-enabled` 기준 **enabled**라 WSL 부팅 시 이미 떠 있는 경우가 대부분이며, 이 경우 `systemctl start`는 사실상 no-op이다.

#### 5.1.5 RabbitMQ 시작 (`/usr/local/bin/start-rabbitmq.sh`)

```sh
sudo systemctl start rabbitmq-server
echo "Rabbitmq started !!! Admin Console URL : http://localhost:15672"
```

`rabbitmq-server.service`는 `disabled`라 매번 수동 기동이 필요하다. 관리 콘솔은 `http://localhost:15672`.

#### 5.1.6 Redis 시작 (`/usr/local/bin/start-redis.sh`)

```sh
#!/bin/sh

# 이전 실행에서 StartLimitBurst에 걸려 failed 상태로 남아있으면 start가 거부되므로 먼저 리셋
sudo systemctl reset-failed redis-server.service >/dev/null 2>&1

sudo systemctl start redis-server

for i in 1 2 3 4 5; do
    if systemctl is-active --quiet redis-server.service; then
        echo "Redis started !!! For realtime administration, Use Tool /DB/Tools/Redis-Desktop-Manager/Version/Another Redis Desktop Manager.exe"
        exit 0
    fi
    sleep 1
done

echo "Redis FAILED to start. Check: sudo systemctl status redis-server.service / sudo journalctl -xeu redis-server.service" >&2
exit 1
```

`redis-server.service`도 `disabled`. MySQL과 같은 이유(5.1 상단 참고)로 5.1.3과 동일한 패턴 — `reset-failed` → `start` → 최대 5초 `is-active` 재확인 후 정확한 성공/실패 출력 — 으로 2026-09-06에 수정했다. 수정 전에는 실제로 `StartLimitBurst`(기본 5회/10초)에 걸려 기동이 완전히 실패했는데도 "Redis started !!!"가 출력되는 문제가 있었다.

#### 5.1.7 Kafka 시작 (`/usr/local/bin/start-kafka.sh`)

```sh
/opt/kafka/kafka-start.sh
echo "Kafka started !!!"
/opt/kafka/admin-tools/KafbatUI/start.sh
echo "KafbatUI(Kafka관리툴) started !!! Admin Console URL : http://localhost:9099"
```

systemd를 쓰지 않고 자체 쉘 스크립트로 nohup 기동한다. `/opt/kafka/kafka-start.sh`는 KRaft 모드 단일 브로커를 백그라운드로 띄우고(로그: `/opt/kafka/kafka_2.13-4.2.1/logs/kafka-server.out`), 이어서 관리 UI인 Kafbat UI를 기동한다(관리 콘솔: `http://localhost:9099`).

#### 5.1.8 Jenkins 시작 (`/usr/local/bin/start-jenkins.sh`)

```sh
sudo systemctl start jenkins
echo "Jenkins started !!!"
```

`jenkins.service`는 **enabled**라 WSL 부팅 시 이미 떠 있는 경우가 대부분이며, `systemctl start`는 사실상 no-op이다.

### 5.2 개발환경 정지 (`~/stop.sh`)

```sh
#! /bin/sh

# mysql
stop-mysql.sh

# postgresql
stop-postgresql.sh

# rabbitmq
stop-rabbitmq.sh

# redis
stop-redis.sh

# kafka
stop-kafka.sh

# docker
stop-docker.sh

# kubenetes
stop-kube.sh

# jenkins
stop-jenkins.sh
```

`/usr/local/bin`에 있는 개별 `stop-*.sh`를 순서대로 실행하기만 하는 쉘 스크립트다. 정지 순서는 기동 순서(5.1)만큼 엄격하지 않다 — Docker/kind가 살아있는 동안 mysql/redis를 먼저 내려도 재현되는 장애는 없다.

#### 5.2.1 MySQL 정지 (`/usr/local/bin/stop-mysql.sh`)

```sh
sudo systemctl stop mysql
echo "Mysql stopped !!!"
```

#### 5.2.2 PostgreSQL 정지 (`/usr/local/bin/stop-postgresql.sh`)

```sh
sudo systemctl stop postgresql
echo "Postgresql stopped !!!"
```

`postgresql.service`는 **enabled**이지만 정지는 자동으로 다시 일어나지 않는다 — WSL을 재기동하기 전까지는 내린 상태가 유지된다.

#### 5.2.3 RabbitMQ 정지 (`/usr/local/bin/stop-rabbitmq.sh`)

```sh
sudo systemctl stop rabbitmq-server
echo "Rabbitmq stopped !!!"
```

#### 5.2.4 Redis 정지 (`/usr/local/bin/stop-redis.sh`)

```sh
sudo systemctl stop redis-server
echo "Redis stopped !!!"
```

#### 5.2.5 Kafka 정지 (`/usr/local/bin/stop-kafka.sh`)

```sh
/opt/kafka/kafka-stop.sh
echo "Kafka stopped !!!"
/opt/kafka/admin-tools/KafbatUI/stop.sh
echo "KafbatUI(Kafka관리툴) stopped !!!"
```

#### 5.2.6 Docker 정지 (`/usr/local/bin/stop-docker.sh`)

```sh
/usr/local/bin/docker-stop.sh
echo "Docker stopped !!!"
```

실제 로직은 `docker-stop.sh`에 있다: `pgrep -x dockerd`로 PID를 찾아 `sudo kill`로 종료하고, 2초 대기 후에도 프로세스가 남아있으면 실패로 표시한다. `kind` 클러스터/컨테이너 자체는 삭제되지 않고 dockerd 프로세스만 내려간다.

#### 5.2.7 Kubernetes 정지 (`/usr/local/bin/stop-kube.sh`)

```sh
/usr/local/bin/k8s-stop.sh
echo "Kubenetes stopped !!!"
```

실제 로직은 `k8s-stop.sh`에 있다: 옵션 없이 실행하면(=`~/stop.sh` 경유 시 항상 이 경로) `kind` 클러스터는 그대로 두고 `sudo systemctl stop docker`로 dockerd만 정지한다 — 컨테이너들은 `restart-policy`에 의해 다음 `start-kube.sh` 실행 시 자동 복원된다. `k8s-stop.sh --delete`로 직접 실행하면 `kind delete cluster`까지 수행해 클러스터를 완전히 삭제하지만, 이 옵션은 `~/stop.sh`를 통해서는 전달할 수 없으므로 클러스터를 완전히 지우고 싶으면 `k8s-stop.sh --delete`를 직접 호출해야 한다.

**주의**: 5.2.6(`stop-docker.sh`)과 5.2.7(`stop-kube.sh`)이 각각 dockerd를 내리는 경로를 갖고 있어 dockerd 정지 시도가 사실상 중복 실행된다. 5.2.6에서 `kill`로 이미 내려가 있으면 5.2.7의 `docker info` 체크가 "이미 정지됨"으로 바로 종료되어 실질적인 문제는 없다.

#### 5.2.8 Jenkins 정지 (`/usr/local/bin/stop-jenkins.sh`)

```sh
sudo systemctl stop jenkins
echo "Jenkins stopped !!!"
```

`jenkins.service`는 **enabled**이지만 postgresql과 마찬가지로 정지 후 자동으로 다시 뜨지 않는다.

트러블슈팅(예: mysql/redis 기동 실패 시 진단 절차, `bind-address`/`172.18.0.1` 레이스 컨디션 상세)은 이 세션의 대화 기록 및 각 소프트웨어 문서(`mysql.md`, `redis.md`)를 참고.
