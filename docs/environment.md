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

# docker
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

`/usr/local/bin`에 있는 개별 `start-*.sh`를 순서대로 실행하기만 하는 쉘 스크립트다. **(2026-09-07 변경) 순서 제약이 사라졌다.** 예전에는 MySQL(`mysqld.cnf`)과 Redis(`redis.conf`)가 `bind-address`/`bind`에 `127.0.0.1`뿐 아니라 `172.18.0.1`(Docker의 `kind` 브리지 네트워크 게이트웨이)도 명시적으로 나열하고 있었다. `kind` 클러스터 안에서 도는 `dstone-boot` Pod가 host의 MySQL/Redis에 접근하려면 이 게이트웨이 IP로 접속해야 하기 때문인데, 이 IP는 dockerd가 뜨고 `kind` 네트워크 브리지(`br-*`)가 attach된 뒤에만 존재해서 **Docker/kind가 뜨기 전에 mysql·redis를 먼저 켜면 `bind: Cannot assign requested address`로 기동이 실패**했다 (2026-09-06 장애 진단 참고 — 과거에는 mysql/postgresql/rabbitmq/redis/kafka → docker → kube 순서였고, MySQL은 systemd 자동 재시작 타이밍이 맞아 우연히 살아났지만 Redis는 `StartLimitBurst`(기본 5회/10초)에 걸려 항상 죽어 있었다).

이 레이스 컨디션을 근본적으로 없애기 위해 MySQL/Redis의 바인딩을 `0.0.0.0`(모든 인터페이스)으로 바꿨다(상세는 [mysql.md 4절](software/mysql.md#4-서비스-시작중지), [redis.md 4절](software/redis.md#4-서비스-시작중지) 참고). 이러면 mysql/redis 기동 시점에 `172.18.0.1`이 존재하든 안 하든 상관없고, 이후 Docker/kind가 떠서 그 IP가 새로 생겨도 mysql/redis 재시작 없이 바로 접근된다. **따라서 이제 위 목록의 순서는 예시일 뿐이며, 필요에 따라 선택적으로 구성하면 된다:**

- **온프레미스만(Docker/kind 불필요)**: mysql/postgresql/rabbitmq/redis/kafka 등 "일반 소프트웨어 그룹"만 올리고, `dstone-boot`은 `dstone-boot/bin/startApp.sh`(`-Dspring.profiles.active=wsl`, `localhost` 접속)로 WSL에 네이티브 기동한다.
- **클라우드(kind)까지 포함**: 위에 더해 `start-docker.sh`/`start-kube.sh`로 Docker/kind를 올리고, `k8s` 프로파일 기반 컨테이너로 `dstone-boot`을 배포한다([cloud-architecture.md](cloud-architecture.md) 참고).

각 `start-*.sh`의 실제 내용과 상세 설명은 해당 소프트웨어 문서에 있다 (스크립트 원문을 이 문서에 중복 기재하지 않고 링크만 둔다):

#### 5.1.1 Docker 시작 (`/usr/local/bin/start-docker.sh`)
→ [docker.md 4절](software/docker.md#4-서비스-시작중지)

#### 5.1.2 Kubernetes 시작 (`/usr/local/bin/start-kube.sh`)
→ [kubernetes.md 5절](software/kubernetes.md#5-서비스클러스터-시작중지)

#### 5.1.3 MySQL 시작 (`/usr/local/bin/start-mysql.sh`)
→ [mysql.md 4절](software/mysql.md#4-서비스-시작중지)

#### 5.1.4 PostgreSQL 시작 (`/usr/local/bin/start-postgresql.sh`)
→ [postgresql.md 4절](software/postgresql.md#4-서비스-시작중지)

#### 5.1.5 RabbitMQ 시작 (`/usr/local/bin/start-rabbitmq.sh`)
→ [rabbitmq.md 4절](software/rabbitmq.md#4-서비스-시작중지)

#### 5.1.6 Redis 시작 (`/usr/local/bin/start-redis.sh`)
→ [redis.md 4절](software/redis.md#4-서비스-시작중지)

#### 5.1.7 Kafka 시작 (`/usr/local/bin/start-kafka.sh`)
→ [kafka.md 5절](software/kafka.md#5-서비스-시작중지) (+ [kafbat-ui.md 5절](software/kafbat-ui.md#5-서비스-시작중지))

#### 5.1.8 Jenkins 시작 (`/usr/local/bin/start-jenkins.sh`)
→ [jenkins.md 5절](software/jenkins.md#5-서비스-시작중지)

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

`/usr/local/bin`에 있는 개별 `stop-*.sh`를 순서대로 실행하기만 하는 쉘 스크립트다. 정지 순서는 기동 순서(5.1)만큼 엄격하지 않다 — Docker/kind가 살아있는 동안 mysql/redis를 먼저 내려도 재현되는 장애는 없다. 5.1과 마찬가지로 각 `stop-*.sh`의 상세는 해당 소프트웨어 문서를 참고한다.

#### 5.2.1 MySQL 정지 (`/usr/local/bin/stop-mysql.sh`)
→ [mysql.md 4절](software/mysql.md#4-서비스-시작중지)

#### 5.2.2 PostgreSQL 정지 (`/usr/local/bin/stop-postgresql.sh`)
→ [postgresql.md 4절](software/postgresql.md#4-서비스-시작중지)

#### 5.2.3 RabbitMQ 정지 (`/usr/local/bin/stop-rabbitmq.sh`)
→ [rabbitmq.md 4절](software/rabbitmq.md#4-서비스-시작중지)

#### 5.2.4 Redis 정지 (`/usr/local/bin/stop-redis.sh`)
→ [redis.md 4절](software/redis.md#4-서비스-시작중지)

#### 5.2.5 Kafka 정지 (`/usr/local/bin/stop-kafka.sh`)
→ [kafka.md 5절](software/kafka.md#5-서비스-시작중지) (+ [kafbat-ui.md 5절](software/kafbat-ui.md#5-서비스-시작중지))

#### 5.2.6 Docker 정지 (`/usr/local/bin/stop-docker.sh`)
→ [docker.md 4절](software/docker.md#4-서비스-시작중지)

#### 5.2.7 Kubernetes 정지 (`/usr/local/bin/stop-kube.sh`)
→ [kubernetes.md 5절](software/kubernetes.md#5-서비스클러스터-시작중지) — `--delete` 옵션(클러스터 완전 삭제)은 [kubernetes.md 6절](software/kubernetes.md#6-로컬-사설-레지스트리-dstone-boot-이미지-배포용)에 정리되어 있으며, `~/stop.sh` 경유로는 전달할 수 없어 필요하면 `k8s-stop.sh --delete`를 직접 호출해야 한다.

**주의**: 5.2.6(`stop-docker.sh`)과 5.2.7(`stop-kube.sh`)이 각각 dockerd를 내리는 경로를 갖고 있어 dockerd 정지 시도가 사실상 중복 실행된다(문제는 없음 — 상세는 두 문서 참고).

#### 5.2.8 Jenkins 정지 (`/usr/local/bin/stop-jenkins.sh`)
→ [jenkins.md 5절](software/jenkins.md#5-서비스-시작중지)

트러블슈팅(예: mysql/redis 기동 실패 시 진단 절차, 과거의 `bind-address`/`172.18.0.1` 레이스 컨디션 이력)은 각 소프트웨어 문서(`mysql.md`, `redis.md`, `docker.md`, `kubernetes.md`)의 시작/중지 절을 참고.
