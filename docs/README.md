# Dstone Framework — 문서 인덱스

## 목차

- [1. 프로젝트 개요](#1-프로젝트-개요)
  - [1.1 모듈 구성](#11-모듈-구성)
  - [1.2 모듈 의존 관계](#12-모듈-의존-관계)
  - [1.3 빠른 참조](#13-빠른-참조)
  - [1.4 빌드 순서](#14-빌드-순서)
- [2. 문서 목록](#2-문서-목록)
  - [2.1 모듈별 문서](#21-모듈별-문서)
  - [2.2 개발 환경 / 인프라](#22-개발-환경--인프라)
  - [2.3 다이어그램 (`images/`)](#23-다이어그램-images)
  - [2.4 데이터 파일 (`data/`)](#24-데이터-파일-data)
- [3. 공통 아키텍처 패턴](#3-공통-아키텍처-패턴)
  - [3.1 설정 분리 구조](#31-설정-분리-구조)
  - [3.2 보안 (Jasypt 암호화)](#32-보안-jasypt-암호화)
  - [3.3 데이터베이스 (HikariCP + MyBatis + log4jdbc)](#33-데이터베이스-hikaricp--mybatis--log4jdbc)
  - [3.4 Spring Security 설정 방식](#34-spring-security-설정-방식)
- [4. 인프라 요구 사항](#4-인프라-요구-사항)
- [5. 클라우드 아키텍처 시뮬레이션](#5-클라우드-아키텍처-시뮬레이션)

이 디렉터리(`docs/`, 리포지토리 루트)가 dstone 프로젝트의 **단일 문서 저장소**다. 앞으로 새로 작성하거나 갱신하는 문서는 모두 여기에 둔다 (`dstone-boot/docs/`, `dstone-batch/docs/` 같은 모듈별 `docs/` 디렉터리는 더 이상 사용하지 않는다).

## 1. 프로젝트 개요

**dstone**은 Java 21 / Spring Boot 4.1(Spring Framework 7) 기반의 멀티모듈 엔터프라이즈 프레임워크다. 웹 애플리케이션 개발(`dstone-boot`), 대용량 배치 처리(`dstone-batch`), 배치 잡 운영 관리(`dstone-batchadmin`), Spring AI 기반 AI/MLOps 서빙(`dstone-ai-engine`)에 필요한 공통 기반(`dstone-common`)을 통합 제공한다.

- **Group ID:** `net.dstone`
- **Version:** `1.0.0-SNAPSHOT`
- **Java:** 21
- **Spring Boot:** 4.1.x (Spring Framework 7)
- **빌드 도구:** Maven (멀티모듈 POM)

### 1.1 모듈 구성

```
dstone/                         (루트 POM)
├── dstone-common/              공통 기반 라이브러리 (JAR)
├── dstone-boot/                웹 애플리케이션 프레임워크 (WAR) — kind(K8s)에 Pod로 배포
├── dstone-batch/                배치 처리 프레임워크 (JAR) — VM 스타일(bin/*.sh)로 운영
├── dstone-batchadmin/           배치 잡 관리 웹 애플리케이션 (WAR) — VM 스타일(bin/*.sh)로 운영
└── dstone-ai-engine/            Spring AI 기반 AI/MLOps 코어 엔진 (JAR) — kind(K8s)에 Pod로 배포(아직 미배포)
```

### 1.2 모듈 의존 관계

```
dstone-boot       ──┐
dstone-batch      ──┼──▶  dstone-common
dstone-batchadmin ──┤
dstone-ai-engine  ──┘
```

`dstone-common`은 독립 라이브러리로 나머지 네 모듈 모두에 포함된다. `dstone-batchadmin`은 `dstone-batch` 서버 인스턴스를 REST로 원격 제어할 뿐, 컴파일 의존성은 없다.

### 1.3 빠른 참조

| 모듈 | 포트 | Main Class | Packaging |
|---|---|---|---|
| dstone-boot | 7081 | `net.dstone.boot.DstoneBootApplication` | WAR |
| dstone-batch | 6081 | `net.dstone.batch.common.DstoneBatchApplication` | JAR |
| dstone-batchadmin | 5081 | `net.dstone.batchadmin.DstoneBatchAdminApplication` | WAR |
| dstone-ai-engine | 8081 | `net.dstone.ai.DstoneAiEngineApplication` | JAR |

### 1.4 빌드 순서

```bash
# 1. 공통 라이브러리 먼저 설치 (다른 모듈이 의존)
cd dstone-common && mvn clean install

# 2. 각 모듈 빌드
cd dstone-boot && mvn clean package        # WAR
cd dstone-batch && mvn clean package       # JAR
cd dstone-batchadmin && mvn clean package  # WAR
cd dstone-ai-engine && mvn clean package   # JAR

# 또는 루트에서 전체 빌드
mvn clean install
```

빌드 명령/산출물, VM 스타일(`bin/*.sh`) 및 컨테이너(kind) 배포, Jenkins CI/CD 파이프라인을 종합 정리한 문서는 [03.build.md](03.build.md) 참고.

## 2. 문서 목록

> 📚 표 없이 한눈에 훑어보고 싶다면 [01.index.md](01.index.md) — `docs/` 전체(및 `software/` 하위) md 문서를 번호순 인덱스로 정리해둔 문서다.

### 2.1 모듈별 문서
| 문서 | 내용 |
|---|---|
| [05.dstone-common.md](05.dstone-common.md) | 공통 유틸리티, 설정, 보안, 환경 변수 |
| [06.dstone-boot.md](06.dstone-boot.md) | 웹 애플리케이션 프레임워크 및 소스 코드 분석기 |
| [07.dstone-batch.md](07.dstone-batch.md) | Spring Batch 기반 배치 잡 개발 프레임워크 |
| [08.dstone-batchadmin.md](08.dstone-batchadmin.md) | 배치 잡 관리(모니터링·스케줄링·원격제어) 웹 애플리케이션 |
| [09.dstone-ai-engine.md](09.dstone-ai-engine.md) | Spring AI 기반 provider-agnostic AI/MLOps 엔진 — Chat/Gateway/Session/Prompt(Phase 0~1), RAG(Phase 2), Agent/Tool·Function Calling(Phase 3), Governance·API Key 인증(Phase 4, 진행 중) |
| [10.dstone-saga.md](10.dstone-saga.md) | SAGA + Outbox 패턴 샘플 기능의 전체 실행 흐름 추적 |
| [03.build.md](03.build.md) | 빌드 명령, 산출물, VM 스타일/컨테이너 배포, CI/CD 파이프라인 종합 |

### 2.2 개발 환경 / 인프라
| 문서 | 내용 |
|---|---|
| [02.environment.md](02.environment.md) | WSL 개발 환경에 설치된 전체 소프트웨어 목록, 시작/정지 스크립트 운용, WSL export/import로 개발 환경을 다른 PC로 이전하는 절차 |
| [04.cloud-architecture.md](04.cloud-architecture.md) | dstone을 클라우드 아키텍처와 유사하게 운용하기 위한 설계(쿠버네티스 배포, VM 스타일 운영, CI/CD) |

`software/` 디렉터리에는 [02.environment.md 2절](02.environment.md#2-목록) 표에 나열된 소프트웨어별 설치·설정 상세 문서가 있다:

| 문서 | 내용 |
|---|---|
| [software/01.jdk.md](software/01.jdk.md) | OpenJDK 21 설치 |
| [software/02.maven.md](software/02.maven.md) | Apache Maven 설치 |
| [software/03.git.md](software/03.git.md) | Git 설치 |
| [software/04.mysql.md](software/04.mysql.md) | MySQL Server 설치·계정/스키마 구성·서비스 시작/정지 |
| [software/05.postgresql.md](software/05.postgresql.md) | PostgreSQL 설치 |
| [software/06.redis.md](software/06.redis.md) | Redis 설치·서비스 시작/정지 |
| [software/07.rabbitmq.md](software/07.rabbitmq.md) | RabbitMQ 설치·dstone용 vhost/사용자/큐/익스체인지 구성·서비스 시작/정지 |
| [software/08.kafka.md](software/08.kafka.md) | Apache Kafka(KRaft 모드) 설치·리스너 구성·서비스 시작/정지 |
| [software/09.kafbat-ui.md](software/09.kafbat-ui.md) | Kafka 관리 콘솔(Kafbat UI) 설치 |
| [software/10.docker.md](software/10.docker.md) | Docker CE + Compose plugin 설치·서비스 시작/정지 |
| [software/11.kubernetes.md](software/11.kubernetes.md) | kubectl + kind(로컬 K8s) 설치·클러스터/로컬 레지스트리 시작·정지 |
| [software/12.jenkins.md](software/12.jenkins.md) | Jenkins 설치·서비스 시작/정지 |
| [software/13.nodejs.md](software/13.nodejs.md) | Node.js + npm 설치 |
| [software/14.ollama.md](software/14.ollama.md) | Ollama(로컬 LLM/임베딩 모델 런타임) 설치·`dstone-ai-engine` RAG 임베딩(`bge-m3`) 연동·서비스 시작/정지 |
| [software/15.ollama-webui-lite.md](software/15.ollama-webui-lite.md) | Ollama 관리 콘솔(Ollama Web UI Lite) 설치·CORS/IPv6 연동 이슈 해결 |

### 2.3 다이어그램 (`images/`)

각 문서 안에 인라인으로 삽입되는 SVG 다이어그램이다. 별도로 열람하기보다, 아래 문서 본문에서 문맥과 함께 보는 것을 권장한다.

| 파일 | 삽입 위치 | 내용 |
|---|---|---|
| [images/jenkins-job-setup-flow.svg](images/jenkins-job-setup-flow.svg) | [04.cloud-architecture.md](04.cloud-architecture.md) | Jenkins Pipeline Job 생성 절차 |
| [images/jenkins-boot-pipeline-flow.svg](images/jenkins-boot-pipeline-flow.svg) | [04.cloud-architecture.md](04.cloud-architecture.md) | `dstone-boot/Jenkinsfile` 파이프라인 스테이지(Docker 빌드/푸시 → kind 배포) |
| [images/jenkins-batch-pipeline-flow.svg](images/jenkins-batch-pipeline-flow.svg) | [04.cloud-architecture.md](04.cloud-architecture.md) | `dstone-batch`/`dstone-batchadmin` Jenkinsfile 파이프라인 스테이지(VM 스타일 재배포) |
| [images/saga-00-overview.svg](images/saga-00-overview.svg) ~ [saga-06-compensate.svg](images/saga-06-compensate.svg) (7개) | [10.dstone-saga.md](10.dstone-saga.md) | SAGA + Outbox 샘플 기능의 단계별 실행 흐름(기동 초기화 → 사가 시작 → Outbox 릴레이 → Kafka 컨슈머 → 종결/보상) |

### 2.4 데이터 파일 (`data/`)

| 파일 | 내용 |
|---|---|
| [data/dstone-batch-postman-collection.json](data/dstone-batch-postman-collection.json) | dstone-batch 샘플 잡 호출용 Postman 컬렉션 |
| [data/dstone-ai-engine-postman-collection.json](data/dstone-ai-engine-postman-collection.json) | dstone-ai-engine 채팅(일반/RAG-증강)·RAG 문서 업로드/삭제/검색 API 호출용 Postman 컬렉션 |
| [data/rabbitmq-basic-config.json](data/rabbitmq-basic-config.json) | RabbitMQ Definitions 파일 — [software/07.rabbitmq.md](software/07.rabbitmq.md)에서 만드는 dstone용 vhost/사용자/큐/익스체인지/바인딩 구성을 `rabbitmqctl import_definitions` 한 번으로 반영하기 위한 내보내기 파일 |

## 3. 공통 아키텍처 패턴

### 3.1 설정 분리 구조

```
conf/
├── env*.properties      → 환경별(local/dev/wsl/vm/k8s) 민감·호스트 설정. 클래스패스에 포함되어 빌드 시점에 선택됨
├── application.yml      → 애플리케이션 설정 (env*.properties 값을 ${...}로 참조), APP_CONF_DIR 경로에서 외부 로드
└── log4j2.xml           → 로깅 설정, 역시 APP_CONF_DIR에서 외부 로드
```

애플리케이션 기동 시 `setSysProperties()`가 `-Dspring.profiles.active=<profile>`에 대응하는 `env-<profile>.properties`(기본은 `env.properties`)를 클래스패스에서 읽어 System Properties로 먼저 등록하고, 그 안의 `APP_CONF_DIR` 값을 이용해 `application.yml`/`log4j2.xml`을 디스크에서 로드한다. 프로파일별 배포 대상은 [04.cloud-architecture.md](04.cloud-architecture.md#4-dstone-batch--dstone-batchadmin--vm-스타일)에 정리되어 있다.

### 3.2 보안 (Jasypt 암호화)

`application.yml`의 DB 비밀번호 등 민감 정보는 `ENC(...)` 형식으로 암호화한다. 복호화 키는 `dstone-common`의 `EncUtil.java`에 고정되어 있다(환경변수로 별도 주입하지 않음). 복호화 자체는 jasypt-spring-boot-starter(Boot 4 미지원으로 제거) 대신 `ConfigProperty`의 `EncPropertyEnvironmentPostProcessor`가 담당하며, `dstone-common`을 의존하는 모든 모듈에 자동 적용된다(모듈별 `ConfigEnc` 빈 불필요).

### 3.3 데이터베이스 (HikariCP + MyBatis + log4jdbc)

모든 모듈이 동일한 패턴으로 데이터소스를 구성한다:

```yaml
spring.datasource.<name>.hikari:
  driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
  jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/<database>
  username: ENC(...)
  password: ENC(...)
```

각 모듈의 실제 테이블 생성 스크립트는 `src/main/resources/schema/*.sql`(dstone-boot/dstone-batch/dstone-batchadmin 각각에 있음)에 있다 — Spring Batch/Boot이 자동으로 스키마를 만들지 않으므로(`initialize-schema: NEVER`) 최초 1회 수동 실행해야 한다.

> `dstone-ai-engine`은 예외다: datasource가 하나뿐이라(`spring.datasource.*`, PostgreSQL+pgvector) `<name>` 세그먼트 없이 표준 Spring Boot 단일 datasource 자동설정을 그대로 쓰고, 벡터 테이블(`vector_store`)도 `spring.ai.vectorstore.pgvector.initialize-schema: true`로 앱이 직접 생성한다(수동 스키마 SQL 없음). 상세: [09.dstone-ai-engine.md 6.2절](09.dstone-ai-engine.md#62-문서-적재-ingest).

### 3.4 Spring Security 설정 방식

```yaml
# dstone-boot: Spring Security 활성화
spring.security.enabled: true

# dstone-batch: Spring Security 비활성화 (Boot 4부터 org.springframework.boot.security.autoconfigure.* 로 패키지 이동)
spring.autoconfigure.exclude:
  - org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
  - org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
  - org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
  - org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration

# dstone-batchadmin: 로그인은 필요하지만 URL별 권한 체크는 없는 단일 역할 내부 관리 도구
```

## 4. 인프라 요구 사항

| 인프라 | 용도 | 모듈 |
|---|---|---|
| MySQL | 메인 데이터 저장 | dstone-boot, dstone-batch, dstone-batchadmin |
| Redis | 세션 저장, 캐시 | dstone-boot, dstone-ai-engine(Phase 1부터, 대화 히스토리) |
| RabbitMQ | 메시지 큐 | dstone-boot |
| Kafka | SAGA/Outbox 샘플 기능의 이벤트 발행/구독 | dstone-boot (샘플 기능 한정) |
| Anthropic API (또는 다른 LLM provider) | Chat 모델 추론 | dstone-ai-engine |
| PostgreSQL + pgvector | RAG 벡터 저장소 | dstone-ai-engine (Phase 2, `dstone.ai.rag.enabled=true`일 때만) |
| Ollama (또는 OpenAI) | RAG 임베딩 모델 추론 | dstone-ai-engine (Phase 2, `dstone.ai.rag.enabled=true`일 때만) |

WSL 환경 설치 방법은 [02.environment.md](02.environment.md)와 [software/](software/) 참고. `dstone-ai-engine`의 상세 아키텍처/설정/API는 [09.dstone-ai-engine.md](09.dstone-ai-engine.md) 참고.

## 5. 클라우드 아키텍처 시뮬레이션

`dstone-boot`과 `dstone-ai-engine`은 컨테이너화되어 로컬 `kind` 쿠버네티스 클러스터에 Pod로 배포되고(단, `dstone-ai-engine`은 매니페스트만 준비된 상태로 아직 실제 배포 전), `dstone-batch`/`dstone-batchadmin`은 systemd 없이 `bin/*.sh` 쉘 스크립트로 제어되는 VM 스타일 프로세스로 운영된다. MySQL/Redis/RabbitMQ/Kafka/PostgreSQL/Ollama는 클러스터 밖의 CSP 매니지드 서비스에 대응한다. 자세한 설계와 CI/CD 파이프라인 구성은 [04.cloud-architecture.md](04.cloud-architecture.md) 참고.
