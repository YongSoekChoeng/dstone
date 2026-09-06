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

WSL을 새로 띄운 뒤 개발환경 전체를 한 번에 올리고 내리기 위한 최상위 스크립트다. 각 소프트웨어별 `start-*.sh`/`stop-*.sh`(`/usr/local/bin`, root 소유, `755`)를 순서대로 호출하기만 하는 얇은 래퍼이며, 개별 스크립트의 상세는 [2. 목록](#2-목록) 표의 "상세 문서" 링크를 참고한다.

### 5.1 `~/start.sh` — 실행 순서와 이유

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

**순서가 이렇게 정해진 이유**: MySQL(`mysqld.cnf`)과 Redis(`redis.conf`)는 `bind-address`/`bind`에 `127.0.0.1`뿐 아니라 `172.18.0.1`(Docker의 `kind` 브리지 네트워크 게이트웨이)도 포함하고 있다. `kind` 클러스터 안에서 도는 `dstone-boot` Pod가 host의 MySQL/Redis에 접근하려면 이 게이트웨이 IP로 접속해야 하기 때문이다. 이 IP는 dockerd가 뜨고 `kind` 네트워크 브리지(`br-*`)가 attach된 뒤에만 존재하므로, **Docker/kind가 뜨기 전에 mysql·redis를 먼저 켜면 `bind: Cannot assign requested address`로 기동이 실패한다.** (2026-09-06 장애 진단 참고 — 과거에는 mysql/postgresql/rabbitmq/redis/kafka → docker → kube 순서였고, MySQL은 systemd 자동 재시작 타이밍이 맞아 우연히 살아났지만 Redis는 `StartLimitBurst`(기본 5회/10초)에 걸려 항상 죽어 있었다.)

이 레이스 컨디션 때문에 현재는 **Docker(및 kind 네트워크)를 가장 먼저 올리고, mysql/redis를 포함한 나머지 서비스는 그 뒤에** 올리는 순서로 고정되어 있다. 순서를 바꿀 때는 이 제약을 반드시 유지할 것.

### 5.2 시작 스크립트 상세

| 순서 | 스크립트 | 내부 동작 | 비고 |
|---|---|---|---|
| 1 | `start-docker.sh` → `docker-start.sh` | `pgrep dockerd`로 이미 떠 있는지 확인 → 없으면 `sudo dockerd`를 백그라운드(`nohup`)로 기동 → `docker info`가 성공할 때까지 최대 30초 폴링 | dockerd는 systemd가 아니라 직접 프로세스로 기동됨 (`systemctl is-enabled docker` → `disabled`) |
| 2 | `start-kube.sh` → `k8s-start.sh` | dockerd 기동 확인 → 로컬 레지스트리 컨테이너(`kind-registry`, `127.0.0.1:5000`) 기동/생성 → `kind` 클러스터(`dev`)가 없으면 레지스트리 미러 설정과 함께 생성, 있으면 재사용 → 레지스트리를 `kind` 네트워크에 연결 → `kubectl get nodes`로 노드 상태 확인 | `KUBECONFIG` 기본값 `/etc/kind/dev.config`. 클러스터/레지스트리는 재시작해도 유지됨 |
| 3 | `start-mysql.sh` | `systemctl reset-failed mysql.service` → `sudo systemctl start mysql` → 최대 5초간 `systemctl is-active` 재확인 후 성공/실패를 정확히 출력 | `mysql.service`는 `disabled`(부팅 자동시작 아님). 2026-09-06 수정: 예전엔 종료 코드를 안 보고 무조건 "started" 출력 |
| 4 | `start-postgresql.sh` | `sudo systemctl start postgresql` | `postgresql.service`는 `enabled`라 WSL 부팅 시 이미 떠 있는 경우가 많음(사실상 no-op) |
| 5 | `start-rabbitmq.sh` | `sudo systemctl start rabbitmq-server` | `disabled` — 수동 기동 필요 |
| 6 | `start-redis.sh` | `systemctl reset-failed redis-server.service` → `sudo systemctl start redis-server` → 최대 5초간 `systemctl is-active` 재확인 후 성공/실패를 정확히 출력 | `redis-server.service`는 `disabled`. 2026-09-06 수정: mysql과 동일한 이유로 정확한 성공/실패 보고 로직 추가 |
| 7 | `start-kafka.sh` | `/opt/kafka/kafka-start.sh` (Kafka 브로커, KRaft 모드) → `/opt/kafka/admin-tools/KafbatUI/start.sh` (관리 UI) | systemd 미사용, 자체 쉘 스크립트로 nohup 기동 |
| 8 | `start-jenkins.sh` | `sudo systemctl start jenkins` | `jenkins.service`는 `enabled` — 부팅 시 이미 떠 있는 경우가 많음(사실상 no-op) |

### 5.3 `~/stop.sh` — 정지 스크립트 상세

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

| 순서 | 스크립트 | 내부 동작 | 비고 |
|---|---|---|---|
| 1 | `stop-mysql.sh` | `sudo systemctl stop mysql` | |
| 2 | `stop-postgresql.sh` | `sudo systemctl stop postgresql` | `enabled` 서비스라 WSL 자체를 끄기 전엔 다시 자동으로 뜨지 않음(수동 정지 필요) |
| 3 | `stop-rabbitmq.sh` | `sudo systemctl stop rabbitmq-server` | |
| 4 | `stop-redis.sh` | `sudo systemctl stop redis-server` | |
| 5 | `stop-kafka.sh` | `/opt/kafka/kafka-stop.sh` → `/opt/kafka/admin-tools/KafbatUI/stop.sh` | |
| 6 | `stop-docker.sh` → `docker-stop.sh` | `pgrep dockerd`로 PID 확인 후 `sudo kill` → 2초 대기 후 여전히 떠 있으면 실패로 표시 | kind 클러스터/컨테이너는 삭제되지 않고 dockerd 프로세스만 내려감 |
| 7 | `stop-kube.sh` → `k8s-stop.sh` | 기본: `sudo systemctl stop docker`로 dockerd 정지(클러스터·컨테이너는 유지, 다음 `start-kube.sh`에서 자동 복원). `k8s-stop.sh --delete`로 직접 실행 시 `kind delete cluster`까지 수행 (`~/stop.sh`에서는 옵션 없이 호출되므로 클러스터는 항상 유지됨) | `--delete` 옵션은 `~/stop.sh` 경유로는 쓸 수 없음 — 클러스터를 완전히 지우려면 `k8s-stop.sh --delete`를 직접 실행 |
| 8 | `stop-jenkins.sh` | `sudo systemctl stop jenkins` | |

**주의**: `stop-docker.sh`(6번)와 `stop-kube.sh`(7번)가 각각 dockerd를 내리는 경로를 갖고 있어 dockerd 정지 시도가 사실상 중복 실행된다(6번에서 `kill`로 이미 내려가 있으면 7번의 `docker info` 체크가 "이미 정지됨"으로 바로 종료되어 실질적인 문제는 없음). 정지 순서 자체는 기동 순서(5.1)만큼 엄격하지 않다 — Docker/kind가 살아있는 동안 mysql/redis를 먼저 내려도 재현되는 장애는 없다.

### 5.4 부팅 시 자동 기동 여부 (`systemctl is-enabled`)

| 서비스 | 상태 | 의미 |
|---|---|---|
| `docker` | disabled | `~/start.sh`/`~/stop.sh`가 아니라 `dockerd` 프로세스를 직접 기동/종료 (systemd 유닛 자체를 쓰지 않음) |
| `mysql` | disabled | `~/start.sh` 실행 전에는 내려가 있음 |
| `redis-server` | disabled | 〃 |
| `rabbitmq-server` | disabled | 〃 |
| `postgresql` | **enabled** | WSL 부팅 시 자동으로 떠 있어, `start-postgresql.sh`는 대부분 no-op |
| `jenkins` | **enabled** | 〃 |

트러블슈팅(예: mysql/redis 기동 실패 시 진단 절차, `bind-address`/`172.18.0.1` 레이스 컨디션 상세)은 이 세션의 대화 기록 및 각 소프트웨어 문서(`mysql.md`, `redis.md`)를 참고.
