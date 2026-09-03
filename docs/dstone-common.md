# dstone-common

## 목차

- [1. 개요](#1-개요)
- [2. 기술 스택](#2-기술-스택)
- [3. 패키지 구조](#3-패키지-구조)
- [4. 주요 유틸리티 (`net.dstone.common.utils`)](#4-주요-유틸리티-netdstonecommonutils)
- [5. 공통 설정 패턴](#5-공통-설정-패턴)
- [6. 환경 변수](#6-환경-변수)
- [7. 보안 아키텍처](#7-보안-아키텍처)
- [8. 애플리케이션 기동 순서](#8-애플리케이션-기동-순서)
- [9. 빌드](#9-빌드)

## 1. 개요

`dstone-common`은 dstone 멀티모듈 프로젝트의 핵심 공통 라이브러리입니다. `dstone-boot`와 `dstone-batch` 두 모듈이 공통으로 사용하는 유틸리티, 설정, 기반 클래스를 제공합니다.

- **Artifact ID:** `dstone-common`
- **Packaging:** JAR (라이브러리)
- **역할:** 공통 기반 라이브러리 (다른 모듈에 의존성으로 포함)

---

## 2. 기술 스택

| 영역 | 기술 |
|---|---|
| 코어 | Java 21, Spring Boot 3.5.x |
| 웹 | Spring Web, Spring WebFlux (WebClient) |
| 보안 | Spring Security, Jasypt 3.0.4, BouncyCastle 1.81, JJWT 0.11.5 |
| 데이터 | MyBatis 3.5.19, HikariCP, MySQL, PostgreSQL, Oracle(ojdbc8), H2, HSQLDB |
| 캐시/세션 | Spring Data Redis (Lettuce), Spring Session Data Redis |
| 메시징 | Spring AMQP (RabbitMQ), Spring WebSocket |
| 로깅 | Log4j2 2.24.3, log4jdbc-log4j2 |
| 파일 | Apache Commons IO 2.15.1, Commons VFS2 2.9.0, Apache PDFBox 2.0.27, JSCH 0.1.55 (SFTP) |
| HTTP | Apache HttpClient 4.5.14, HttpClient5, OkHttp 4.9.3 |
| 유틸 | Apache Commons Lang3 3.18.0, Commons BeanUtils 1.11.0, juniversalchardet 1.0.3 |
| SQL 파싱 | JSQLParser 4.7 |
| 이메일 | Jakarta Mail 2.1.4 |
| JSON | Jackson, json-simple 1.1.1 |

---

## 3. 패키지 구조

```
src/main/java/net/dstone/common/
├── biz/            # 공통 비즈니스 로직 컴포넌트
├── config/         # 핵심 프레임워크 설정 (Redis, RabbitMQ, WebSocket 등)
├── consts/         # 상수 및 Enum 정의
├── core/           # 기반 추상 클래스 및 인터페이스
├── exception/      # 공통 예외 클래스
│   └── resolver/   # 커스텀 예외 처리기
├── queue/          # 메시지 큐 추상화 계층
├── socket/         # WebSocket 통신 지원
├── task/           # 비동기 태스크 및 스케줄링 관리
├── utils/          # 범용 유틸리티 클래스
└── websocket/      # WebSocket 핸들러 및 컨트롤러
```

---

## 4. 주요 유틸리티 (`net.dstone.common.utils`)

| 클래스 | 설명 |
|---|---|
| `LogUtil` | 구조화된 로깅 (Log4j2 기반) |
| `StringUtil` | 문자열 처리 (공백, 인코딩, 포맷 등) |
| `ConvertUtil` | 타입 변환 (JSON ↔ Object, Map ↔ VO 등) |
| `EncryptUtil` | 암복호화 (AES, Jasypt, BouncyCastle) |
| `DateUtil` | 날짜/시간 계산 및 포맷 변환 |
| `FileUtil` | 파일 I/O (읽기, 쓰기, 복사, SFTP 전송) |

---

## 5. 공통 설정 패턴

모든 모듈의 `conf/application.yml`에서 공통으로 참조하는 설정 구조:

```yaml
# Redis (Lettuce 클라이언트)
spring.data.redis:
  host: ${REDIS_HOST}
  port: ${REDIS_PORT}
  client-type: lettuce
  lettuce.pool:
    max-active: 5
    max-idle: 5
    min-idle: 2

# RabbitMQ (Fanout + Direct Exchange)
spring.rabbitmq:
  host: ${RABBITMQ_HOST}
  port: ${RABBITMQ_PORT}

# 데이터소스 (HikariCP + log4jdbc)
spring.datasource.<name>.hikari:
  driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
  jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/<database>
  username: ENC(...)   # Jasypt 암호화
  password: ENC(...)
```

---

## 6. 환경 변수

| 변수명 | 설명 |
|---|---|
| `APP_HOME` | 애플리케이션 홈 디렉토리 |
| `APP_CONF_DIR` | 설정 파일 디렉토리 경로 |
| `DB_HOST` | 데이터베이스 서버 호스트 |
| `DB_PORT` | 데이터베이스 서버 포트 |
| `REDIS_HOST` | Redis 서버 호스트 |
| `REDIS_PORT` | Redis 서버 포트 |
| `RABBITMQ_HOST` | RabbitMQ 서버 호스트 |
| `RABBITMQ_PORT` | RabbitMQ 서버 포트 |
| `FILE_UPLOAD_ROOT` | 파일 업로드 루트 디렉토리 |

환경 변수는 `conf/env.properties`에 정의하며, 애플리케이션 기동 시 `setSysProperties()` 호출로 System Properties에 자동 등록됩니다.

---

## 7. 보안 아키텍처

- **Jasypt**: `application.yml` 내 민감 정보(DB 비밀번호 등)를 `ENC(...)` 형식으로 암호화 저장. 복호화 키는 `env.properties`의 `jasypt.encryptor.password`에 설정
- **BouncyCastle**: JDK 17+ 환경에서의 중첩 JAR 보안 문제 해결 및 암호화 알고리즘 확장
- **JJWT 0.11.5**: JWT 토큰 생성 및 검증

---

## 8. 애플리케이션 기동 순서

공통 모듈이 정의한 기동 순서를 모든 모듈이 따릅니다:

1. `setSysProperties()` 호출 → `conf/env.properties` 내용을 System Properties로 등록
2. `APP_CONF_DIR` 경로의 `application.yml` 로드
3. `log4j2.xml` 로깅 설정 로드
4. Spring Boot 자동 설정 활성화

---

## 9. 빌드

```bash
# 공통 라이브러리 빌드 (다른 모듈 빌드 전 선행 필요)
cd dstone-common
mvn clean install
```

빌드 시 `conf/` 디렉토리의 `.properties` 파일이 `target/classes`로 복사됩니다.

> 전체 모듈 빌드 명령, 배포 방식, CI/CD 파이프라인 종합 정리는 [build.md](build.md) 참고.
