# dstone-boot

## 개요

`dstone-boot`는 Spring Boot 기반의 통합 웹 애플리케이션 프레임워크입니다. 두 가지 주요 기능을 하나의 애플리케이션으로 제공합니다.

1. **웹 애플리케이션 프레임워크**: 다중 데이터베이스, 소셜 로그인, 메시징(RabbitMQ), WebSocket 등 현대적인 웹 애플리케이션 개발에 필요한 공통 기능과 유틸리티를 통합
2. **소스 코드 분석 도구**: Java 웹 애플리케이션의 소스 코드(Java, JSP, MyBatis XML)를 정적 분석하여 아키텍처와 의존성을 시각적으로 매핑

- **Artifact ID:** `dstone-boot`
- **Packaging:** WAR
- **Main Class:** `net.dstone.boot.DstoneBootApplication`
- **기본 포트:** 7081

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| 코어 | Java 21, Spring Boot 3.5.x, dstone-common |
| 웹 | Spring MVC, JSP/JSTL, jQuery |
| 보안 | Spring Security, OAuth2 (Google/Naver/Kakao) |
| 데이터 | MyBatis, HikariCP, MySQL / Oracle / PostgreSQL / H2 |
| 캐시/세션 | Redis (Lettuce), Spring Session |
| 메시징 | RabbitMQ, WebSocket |
| 코드 분석 | javaparser 3.28.1, JSQLParser 4.7, ClassGraph 4.8.165, jsoup 1.11.3 |
| AOP | AspectJ (aspectjrt, aspectjweaver, aspectjtools) |
| API 문서 | Springfox Swagger 2.9.2 |
| 규칙 엔진 | Easy Rules 4.1.0 |
| 외부 연동 | Google Drive API, Google Sheets API |
| 기타 | Weka (머신러닝), Tess4j (OCR), ByteBuddy, Lombok |
| 빌드 | Maven (WAR 패키징) |

---

## 패키지 구조

```
src/main/java/net/dstone/boot/
├── DstoneBootApplication.java          # 메인 진입점 (SpringBootServletInitializer 상속)
├── analyzer/                           # 소스 코드 분석 기능
│   ├── AnalysisController.java         # 분석 요청 처리 컨트롤러
│   ├── ReportController.java           # 분석 보고서 생성
│   ├── ConfigurationService.java       # 분석 설정 관리
│   ├── cud/                            # 분석 결과 CRUD
│   ├── taskitem/                       # 분석 태스크 항목
│   └── vo/                             # 분석 결과 VO
└── common/
    ├── config/                         # 애플리케이션 설정
    │   ├── Config.java                 # 메인 설정
    │   ├── ConfigDatasource.java       # 다중 데이터소스 설정
    │   ├── ConfigTransaction.java      # 트랜잭션 관리
    │   ├── ConfigMapper.java           # MyBatis 매퍼 설정
    │   ├── ConfigRedis.java            # Redis 설정
    │   ├── ConfigAspect.java           # AOP/AspectJ 설정
    │   ├── ConfigEnc.java              # 암호화 설정
    │   ├── ConfigListener.java         # 애플리케이션 리스너
    │   ├── ConfigWebMvc.java           # Spring MVC 설정
    │   └── ConfigWebSocket.java        # WebSocket 설정
    ├── security/
    │   └── ConfigSecurity.java         # Spring Security 설정 (소셜 로그인 포함)
    ├── web/                            # 필터, 인터셉터, 공통 예외 처리
    ├── tools/
    │   └── analyzer/                   # AppAnalyzer - 핵심 분석 엔진
    ├── biz/                            # 공통 비즈니스 로직
    └── sample/                         # 샘플 구현체

src/main/java/net/dstone/boot/sample/  # 기능별 샘플
    ├── analyze/                        # 분석 샘플
    ├── cud/                            # CRUD 샘플
    ├── google/                         # Google API 연동 샘플
    ├── kakao/                          # 카카오 소셜 로그인 샘플
    ├── naver/                          # 네이버 소셜 로그인 샘플
    ├── market/                         # 커머스 샘플
    ├── rule/                           # 비즈니스 규칙 샘플
    └── swagger/                        # Swagger API 문서 샘플

src/main/webapp/                        # 웹 리소스 (JSP, CSS, JS)
```

---

## 설정 (`conf/application.yml`)

### 서버 설정

```yaml
server:
  port: 7081
  tomcat:
    max-threads: 200
    min-spare-threads: 10
    max-connections: 10000
  servlet:
    encoding:
      charset: UTF-8
      force-response: true
  ssl:
    enabled: false    # TLS/SSL 활성화 시 true로 변경
```

### 다중 데이터소스

세 개의 독립적인 데이터소스를 운영합니다:

| 데이터소스 | 용도 | 데이터베이스 |
|---|---|---|
| `common` | 메인 애플리케이션 | sampleDB |
| `sample` | 샘플 데이터 | sampleDB |
| `analyzer` | 분석 결과 저장 | analyzeDB |

### 세션 및 캐시

```yaml
spring:
  session:
    store-type: redis
    redis:
      namespace: dstone:session   # Redis 세션 네임스페이스
  data.redis:
    enabled: true
    client-type: lettuce
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
```

### 소셜 로그인

```yaml
# Naver OAuth2
naver:
  client-id: ...
  client-secret: ...

# Kakao OAuth2
kakao:
  client-id: ...

# Google OAuth2
google:
  client-id: ...
  client-secret: ...
```

### 파일 업로드

```yaml
spring.servlet.multipart:
  location: ${FILE_UPLOAD_ROOT}
  max-file-size: 100MB
  max-request-size: 100MB
```

---

## 소스 코드 분석기 (Analyzer)

`dstone-boot`의 핵심 차별화 기능입니다. 지정된 경로의 Java 웹 애플리케이션 소스 코드를 정적 분석하여 구조와 의존성을 파악합니다.

### 분석 흐름

```
사용자 (웹 UI)
    │
    ▼ 분석 경로 지정 & 시작
AnalysisController
    │
    ▼ 비동기 처리 (UI 블로킹 없음)
AppAnalyzer (핵심 엔진)
    │
    ├── Java 파일 파싱    → javaparser    (클래스, 메소드, 어노테이션, 호출 관계)
    ├── JSP 파일 파싱     → jsoup         (화면 구조, URL 링크)
    └── MyBatis XML 파싱  → JSQLParser    (SQL 쿼리, 테이블 CRUD 관계)
    │
    ▼ 분석 결과 저장
analyzer 데이터베이스 (H2 / MySQL)
    │
    ▼ 웹 UI 조회 및 시각화
ReportController
```

### 주요 클래스

| 클래스 | 역할 |
|---|---|
| `net.dstone.boot.analyzer.AnalysisController` | 분석 작업 웹 요청 처리 |
| `net.dstone.boot.common.tools.analyzer.AppAnalyzer` | 실제 소스 코드 분석 엔진 |
| `net.dstone.boot.analyzer.ConfigurationService` | 분석 설정 관리 |
| `net.dstone.boot.analyzer.ReportController` | 분석 결과 리포트 제공 |

### 분석 대상 및 추출 정보

| 파일 유형 | 파싱 라이브러리 | 추출 정보 |
|---|---|---|
| `.java` | javaparser 3.28.1 | 클래스 계층, 메소드 호출 관계, URL 매핑, 어노테이션 |
| `.jsp` | jsoup 1.11.3 | 화면 구조, 스크립트릿, URL 링크 |
| `.xml` (MyBatis) | JSQLParser 4.7 | SQL 쿼리, 파라미터, CRUD 대상 테이블 |

---

## 보안 (Spring Security)

```yaml
spring.security.enabled: true
```

- **자체 인증/인가**: `ConfigSecurity.java`에서 URL별 접근 권한 설정
- **소셜 로그인**: Google, Naver, Kakao OAuth2 지원
- **세션 관리**: Redis 기반 분산 세션 (`dstone:session` 네임스페이스)
- **SSL/TLS**: `application.yml`의 `server.ssl` 설정으로 활성화 가능

---

## AOP (Aspect-Oriented Programming)

`ConfigAspect.java`에서 AspectJ를 활용한 횡단 관심사를 처리합니다:

- 메소드 실행 시간 측정
- 공통 로깅 (요청/응답 로그)
- 예외 공통 처리

---

## 샘플 코드

`src/main/java/net/dstone/boot/sample/` 경로에 다음 기능별 샘플을 제공합니다:

| 패키지 | 설명 |
|---|---|
| `cud/` | MyBatis CRUD 연동 예제 |
| `google/` | Google Drive/Sheets API 연동 |
| `kakao/` | 카카오 소셜 로그인 구현 |
| `naver/` | 네이버 소셜 로그인 구현 |
| `analyze/` | 소스 분석 결과 활용 예제 |
| `rule/` | Easy Rules 규칙 엔진 적용 |
| `swagger/` | Swagger API 문서 자동화 |

---

## 빌드 및 실행

```bash
# 빌드 (WAR)
cd dstone-boot
mvn clean package

# 로컬 실행 (내장 Tomcat)
java -jar target/dstone-boot-1.0.0-SNAPSHOT.war

# 외부 Tomcat 배포 시 WAR 파일을 webapps/에 배포
```

### Docker 실행

```bash
# Docker 빌드 및 실행 참고
# dstone-boot/docs/docker/ 경로의 스크립트 참조
```

> 상세 Docker 배포 절차: [`dstone-boot/docs/02.Docker빌드(dstone-boot).md`](../dstone-boot/docs/02.Docker빌드(dstone-boot).md)
