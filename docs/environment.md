# 개발 환경 (WSL) 소프트웨어 목록

dstone 프로젝트를 개발하기 위해 WSL(Ubuntu) 환경에 수동으로 설치·구성한 소프트웨어 목록이다.
각 항목의 설치 방법 및 상세 설정은 `docs/software/{소프트웨어명}.md` 문서를 참고한다.

- 최초 작성일: 2026-09-03
- 대상 환경: WSL2 Ubuntu 26.04 LTS (Resolute Raccoon)
- 갱신 방식: 새 소프트웨어를 설치하거나 주요 설정을 변경할 때마다 이 문서와 `docs/software/*.md`를 함께 갱신한다.

## 운영 원칙 (중요)

WSL은 기본적으로 systemd 서비스를 부팅 시 자동 기동하지 않도록 운용 중이다. 그래서 DB/메시징/CI 등 대부분의 서비스는
`/usr/local/bin/start-*.sh`, `/usr/local/bin/stop-*.sh` 스크립트로 수동 기동/중지한다 (아래 표의 "시작 스크립트" 참고).
WSL을 재시작했다면 필요한 서비스를 먼저 `start-*.sh`로 올려야 한다.

## 목록

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

## dstone 프로젝트와의 연결 관계

- **MySQL**: `dstone-common`/`dstone-boot`/`dstone-batch`/`dstone-batchadmin` 공통 메인 데이터 저장소(`sampleDB` 등). `conf/env.properties`의 `DB_HOST`/`DB_PORT`로 접속 정보 주입.
- **Redis**: `dstone-boot`의 분산 세션 저장소(`dstone:session` 네임스페이스).
- **RabbitMQ**: `dstone-boot`의 메시지 큐 연동.
- **Kafka**: 애플리케이션에서 메시징 연동 실험/개발용 (로컬 KRaft 단일 브로커).
- **Jenkins**: `dstone-batch/Jenkinsfile`, `dstone-boot/Jenkinsfile` 파이프라인 실행.
- **Docker / kind**: `dstone-boot/docs/docker/`, `dstone-batch/docs/docker/`의 Docker Compose 배포 스크립트 실행 및 로컬 쿠버네티스 실습 환경.
- **PostgreSQL / Node.js**: 현재 dstone 서비스 자체 설정(`application.yml`)에서는 사용하지 않는 것으로 보이며, 개발 환경 실습/부가 도구 용도로 설치되어 있음. 실제 프로젝트 연동이 생기면 이 문서와 CLAUDE.md를 갱신할 것.
