# Dstone Framework — 문서 인덱스

이 디렉터리(`docs/`, 리포지토리 루트)가 dstone 프로젝트의 **단일 문서 저장소**다. 앞으로 새로 작성하거나 갱신하는 문서는 모두 여기에 둔다 (`dstone-boot/docs/`, `dstone-batch/docs/` 같은 모듈별 `docs/` 디렉터리는 더 이상 사용하지 않는다).

## 프로젝트 개요

**dstone**은 Java 21 / Spring Boot 3.5 기반의 멀티모듈 엔터프라이즈 프레임워크다. 웹 애플리케이션 개발(`dstone-boot`), 대용량 배치 처리(`dstone-batch`), 배치 잡 운영 관리(`dstone-batchadmin`)에 필요한 공통 기반(`dstone-common`)을 통합 제공한다.

- **Group ID:** `net.dstone`
- **Version:** `1.0.0-SNAPSHOT`
- **Java:** 21
- **Spring Boot:** 3.5.x
- **빌드 도구:** Maven (멀티모듈 POM)

### 모듈 구성

```
dstone/                         (루트 POM)
├── dstone-common/              공통 기반 라이브러리 (JAR)
├── dstone-boot/                웹 애플리케이션 프레임워크 (WAR) — kind(K8s)에 Pod로 배포
├── dstone-batch/                배치 처리 프레임워크 (JAR) — VM 스타일(bin/*.sh)로 운영
└── dstone-batchadmin/           배치 잡 관리 웹 애플리케이션 (WAR) — VM 스타일(bin/*.sh)로 운영
```

### 모듈 의존 관계

```
dstone-boot       ──┐
dstone-batch      ──┼──▶  dstone-common
dstone-batchadmin ──┘
```

`dstone-common`은 독립 라이브러리로 나머지 세 모듈 모두에 포함된다. `dstone-batchadmin`은 `dstone-batch` 서버 인스턴스를 REST로 원격 제어할 뿐, 컴파일 의존성은 없다.

### 빠른 참조

| 모듈 | 포트 | Main Class | Packaging |
|---|---|---|---|
| dstone-boot | 7081 | `net.dstone.boot.DstoneBootApplication` | WAR |
| dstone-batch | 6081 | `net.dstone.batch.common.DstoneBatchApplication` | JAR |
| dstone-batchadmin | 5081 | `net.dstone.batchadmin.DstoneBatchAdminApplication` | WAR |

### 빌드 순서

```bash
# 1. 공통 라이브러리 먼저 설치 (다른 모듈이 의존)
cd dstone-common && mvn clean install

# 2. 각 모듈 빌드
cd dstone-boot && mvn clean package        # WAR
cd dstone-batch && mvn clean package       # JAR
cd dstone-batchadmin && mvn clean package  # WAR

# 또는 루트에서 전체 빌드
mvn clean install
```

## 문서 목록

### 모듈별 문서
| 문서 | 내용 |
|---|---|
| [dstone-common.md](dstone-common.md) | 공통 유틸리티, 설정, 보안, 환경 변수 |
| [dstone-boot.md](dstone-boot.md) | 웹 애플리케이션 프레임워크 및 소스 코드 분석기 |
| [dstone-batch.md](dstone-batch.md) | Spring Batch 기반 배치 잡 개발 프레임워크 |
| [dstone-batchadmin.md](dstone-batchadmin.md) | 배치 잡 관리(모니터링·스케줄링·원격제어) 웹 애플리케이션 |
| [dstone-saga.md](dstone-saga.md) | SAGA + Outbox 패턴 샘플 기능의 전체 실행 흐름 추적 |

### 개발 환경 / 인프라
| 문서 | 내용 |
|---|---|
| [environment.md](environment.md) | WSL 개발 환경에 설치된 전체 소프트웨어 목록 |
| [software/](software/) | 소프트웨어별 설치 방법 상세 문서 (JDK, Maven, Docker, MySQL, Redis, RabbitMQ, Kafka, Kubernetes, Jenkins 등) |
| [cloud-architecture.md](cloud-architecture.md) | dstone을 클라우드 아키텍처와 유사하게 운용하기 위한 설계(쿠버네티스 배포, VM 스타일 운영, CI/CD) |

### 기타 자료
| 자료 | 내용 |
|---|---|
| [dstone-batch-postman-collection.json](dstone-batch-postman-collection.json) | dstone-batch 샘플 잡 호출용 Postman 컬렉션 |

## 공통 아키텍처 패턴

### 1. 설정 분리 구조

```
conf/
├── env*.properties      → 환경별(local/dev/wsl/vm/k8s) 민감·호스트 설정. 클래스패스에 포함되어 빌드 시점에 선택됨
├── application.yml      → 애플리케이션 설정 (env*.properties 값을 ${...}로 참조), APP_CONF_DIR 경로에서 외부 로드
└── log4j2.xml           → 로깅 설정, 역시 APP_CONF_DIR에서 외부 로드
```

애플리케이션 기동 시 `setSysProperties()`가 `-Dspring.profiles.active=<profile>`에 대응하는 `env-<profile>.properties`(기본은 `env.properties`)를 클래스패스에서 읽어 System Properties로 먼저 등록하고, 그 안의 `APP_CONF_DIR` 값을 이용해 `application.yml`/`log4j2.xml`을 디스크에서 로드한다. 프로파일별 배포 대상은 [cloud-architecture.md](cloud-architecture.md#dstone-batch--dstone-batchadmin--vm-스타일)에 정리되어 있다.

### 2. 보안 (Jasypt 암호화)

`application.yml`의 DB 비밀번호 등 민감 정보는 `ENC(...)` 형식으로 암호화한다. 복호화 키는 `dstone-common`의 `EncUtil.java`에 고정되어 있다(환경변수로 별도 주입하지 않음).

### 3. 데이터베이스 (HikariCP + MyBatis + log4jdbc)

모든 모듈이 동일한 패턴으로 데이터소스를 구성한다:

```yaml
spring.datasource.<name>.hikari:
  driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
  jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/<database>
  username: ENC(...)
  password: ENC(...)
```

각 모듈의 실제 테이블 생성 스크립트는 `src/main/resources/schema/*.sql`(dstone-boot/dstone-batch/dstone-batchadmin 각각에 있음)에 있다 — Spring Batch/Boot이 자동으로 스키마를 만들지 않으므로(`initialize-schema: NEVER`) 최초 1회 수동 실행해야 한다.

### 4. Spring Security 설정 방식

```yaml
# dstone-boot: Spring Security 활성화
spring.security.enabled: true

# dstone-batch: Spring Security 비활성화
spring.autoconfigure.exclude:
  - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration

# dstone-batchadmin: 로그인은 필요하지만 URL별 권한 체크는 없는 단일 역할 내부 관리 도구
```

## 인프라 요구 사항

| 인프라 | 용도 | 모듈 |
|---|---|---|
| MySQL | 메인 데이터 저장 | 모든 모듈 |
| Redis | 세션 저장, 캐시 | dstone-boot |
| RabbitMQ | 메시지 큐 | dstone-boot |
| Kafka | SAGA/Outbox 샘플 기능의 이벤트 발행/구독 | dstone-boot (샘플 기능 한정) |

WSL 환경 설치 방법은 [environment.md](environment.md)와 [software/](software/) 참고.

## 클라우드 아키텍처 시뮬레이션

`dstone-boot`은 컨테이너화되어 로컬 `kind` 쿠버네티스 클러스터에 Pod로 배포되고, `dstone-batch`/`dstone-batchadmin`은 systemd 없이 `bin/*.sh` 쉘 스크립트로 제어되는 VM 스타일 프로세스로 운영된다. MySQL/Redis/RabbitMQ/Kafka는 클러스터 밖의 CSP 매니지드 서비스에 대응한다. 자세한 설계와 CI/CD 파이프라인 구성은 [cloud-architecture.md](cloud-architecture.md) 참고.
