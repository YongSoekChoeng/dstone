# Dstone Framework - 전체 문서 인덱스

## 프로젝트 개요

**dstone**은 Java 21 / Spring Boot 3.5 기반의 멀티모듈 엔터프라이즈 프레임워크입니다.  
웹 애플리케이션 개발(`dstone-boot`)과 대용량 배치 처리(`dstone-batch`)에 필요한 공통 기반(`dstone-common`)을 통합 제공합니다.

- **Group ID:** `net.dstone`
- **Version:** `1.0.0-SNAPSHOT`
- **Java:** 21
- **Spring Boot:** 3.5.x
- **빌드 도구:** Maven (멀티모듈 POM)

---

## 모듈 구성

```
dstone/                         (루트 POM)
├── dstone-common/              공통 기반 라이브러리 (JAR)
├── dstone-boot/                웹 애플리케이션 프레임워크 (WAR)
└── dstone-batch/               배치 처리 프레임워크 (JAR)
```

### 모듈 의존 관계

```
dstone-boot  ──┐
               ├──▶  dstone-common
dstone-batch ──┘
```

`dstone-common`은 독립 라이브러리로, `dstone-boot`와 `dstone-batch` 모두에 포함됩니다.

---

## 문서 목록

| 모듈 | 문서 | 설명 |
|---|---|---|
| 공통 라이브러리 | [dstone-common.md](./dstone-common.md) | 공통 유틸리티, 설정, 보안, 환경 변수 |
| 웹 프레임워크 | [dstone-boot.md](./dstone-boot.md) | 웹 애플리케이션 프레임워크 및 소스 코드 분석기 |
| 배치 프레임워크 | [dstone-batch.md](./dstone-batch.md) | Spring Batch 기반 배치 잡 개발 프레임워크 |

---

## 빠른 참조

### 포트 및 진입점

| 모듈 | 포트 | Main Class | Packaging |
|---|---|---|---|
| dstone-boot | 7081 | `net.dstone.boot.DstoneBootApplication` | WAR |
| dstone-batch | 6081 | `net.dstone.batch.common.DstoneBatchApplication` | JAR |

### 필수 환경 변수

모든 모듈에서 공통으로 사용합니다. `conf/env.properties`에 정의합니다.

| 변수명 | 설명 |
|---|---|
| `APP_HOME` | 애플리케이션 홈 디렉토리 |
| `APP_CONF_DIR` | 설정 파일 디렉토리 |
| `DB_HOST` | DB 서버 호스트 |
| `DB_PORT` | DB 서버 포트 |
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_PORT` | Redis 포트 |
| `RABBITMQ_HOST` | RabbitMQ 호스트 (dstone-boot) |
| `RABBITMQ_PORT` | RabbitMQ 포트 (dstone-boot) |
| `FILE_UPLOAD_ROOT` | 파일 업로드 루트 경로 (dstone-boot) |
| `jasypt.encryptor.password` | Jasypt 암복호화 키 |

### 빌드 순서

```bash
# 1. 공통 라이브러리 먼저 설치
cd dstone-common && mvn clean install

# 2-A. 웹 애플리케이션 빌드
cd dstone-boot && mvn clean package

# 2-B. 배치 빌드
cd dstone-batch && mvn clean package

# 또는 루트에서 전체 빌드
mvn clean install
```

---

## 공통 아키텍처 패턴

### 1. 설정 분리 구조

```
conf/
├── env.properties      → 환경별 민감 설정 (Jasypt 암호화 키, DB 접속 정보)
├── application.yml     → 애플리케이션 설정 (env.properties 변수 참조)
└── log4j2.xml          → 로깅 설정
```

애플리케이션 기동 시 `conf/env.properties`를 먼저 System Properties로 로드한 후, `application.yml`을 읽습니다.

### 2. 보안 (Jasypt 암호화)

`application.yml`의 DB 비밀번호, API 키 등 민감 정보는 `ENC(...)` 형식으로 암호화합니다.

```yaml
# 암호화된 값 예시
username: ENC(ydLjxrknr8dD59e6E+HvxdxRaGiFa9jOCpJJDtb0uak=)
password: ENC(ydLjxrknr8dD59e6E+HvxdxRaGiFa9jOCpJJDtb0uak=)
```

복호화 키는 `env.properties`의 `jasypt.encryptor.password`에 설정합니다.

### 3. 데이터베이스 (HikariCP + MyBatis + log4jdbc)

모든 모듈이 동일한 패턴으로 데이터소스를 구성합니다:

```yaml
spring.datasource.<name>.hikari:
  driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
  jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/<database>
  username: ENC(...)
  password: ENC(...)
```

- **HikariCP**: 고성능 커넥션 풀
- **log4jdbc**: SQL 쿼리 로깅 (실행 쿼리, 파라미터, 실행 시간)
- **MyBatis**: SQL 매퍼 (`src/main/resources/sqlmap/` 경로)

### 4. Spring Security 설정 방식

```yaml
# dstone-boot: Spring Security 활성화
spring.security.enabled: true

# dstone-batch: Spring Security 비활성화
spring.autoconfigure.exclude:
  - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

---

## 인프라 요구 사항

| 인프라 | 용도 | 모듈 |
|---|---|---|
| MySQL | 메인 데이터 저장 | 모든 모듈 |
| Redis | 세션 저장, 캐시 | dstone-boot |
| RabbitMQ | 메시지 큐 | dstone-boot |
| Tomcat (외부) | WAR 배포 (선택) | dstone-boot |

Docker Compose를 이용한 로컬 인프라 구성 스크립트:
- `dstone-boot/docs/docker/` 참조
- `dstone-batch/docs/docker/` 참조

---

## 기존 모듈별 문서

각 모듈의 `docs/` 디렉토리에 한국어 상세 문서가 있습니다:

| 문서 경로 | 내용 |
|---|---|
| [`dstone-boot/docs/01.프로젝트개요(dstone-boot).md`](../dstone-boot/docs/01.프로젝트개요(dstone-boot).md) | dstone-boot 프로젝트 개요 (한국어) |
| [`dstone-boot/docs/02.Docker빌드(dstone-boot).md`](../dstone-boot/docs/02.Docker빌드(dstone-boot).md) | dstone-boot Docker 배포 가이드 |
| [`dstone-batch/docs/01.프로젝트개요(dstone-batch).md`](../dstone-batch/docs/01.프로젝트개요(dstone-batch).md) | dstone-batch 프로젝트 개요 (한국어) |
| [`dstone-batch/docs/02.Docker빌드(dstone-batch).md`](../dstone-batch/docs/02.Docker빌드(dstone-batch).md) | dstone-batch Docker 배포 가이드 |
