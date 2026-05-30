# dstone-batch

## 개요

`dstone-batch`는 Spring Boot, Spring Batch, Spring Cloud Task를 기반으로 구축된 엔터프라이즈급 배치 처리 프레임워크입니다. 반복적인 잡(Job) 설정을 표준화하고, 개발자가 비즈니스 로직에 집중할 수 있도록 설계되었습니다.

- **Artifact ID:** `dstone-batch`
- **Packaging:** JAR (독립 실행형)
- **Main Class:** `net.dstone.batch.common.DstoneBatchApplication`
- **기본 포트:** 6081

---

## 핵심 특징

| 특징 | 설명 |
|---|---|
| 표준화된 잡 개발 | `BaseJobConfig` 추상 클래스를 상속하여 일관된 방식으로 잡 정의 |
| 자동 잡 등록 | `@AutoRegJob` 어노테이션으로 잡을 자동 탐지 및 등록 |
| 다중 스레드 처리 | `TaskExecutor` 기반 멀티스레드 청크 처리 |
| 병렬 플로우 실행 | `SplitFlow`를 활용한 여러 Flow의 병렬 실행 |
| 다중 데이터소스 | `common`, `sample` 두 개의 독립적인 데이터소스 지원 |
| 유연한 실행 환경 | 로컬 직접 실행 또는 Spring Cloud Data Flow 연동 |
| 보안 설정 관리 | Jasypt를 이용한 DB 비밀번호 암호화 |

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| 코어 | Java 21, Spring Boot 3.5.x, dstone-common |
| 배치 | Spring Batch (spring-boot-starter-batch) |
| 클라우드 | Spring Cloud Task, Spring Cloud Data Flow |
| 데이터 | MyBatis, HikariCP, MySQL |
| AOP | AspectJ |
| 보안 | Jasypt (설정 암호화) |
| 빌드 | Maven (JAR 패키징) |

---

## 프로젝트 구조

```
dstone-batch/
├── conf/                           # 외부 설정 파일
│   ├── application.yml             # 메인 설정
│   ├── env.properties              # 환경 변수 및 민감 정보
│   └── log4j2.xml                  # 로깅 설정
├── pom.xml
└── src/main/
    ├── java/net/dstone/batch/
    │   ├── common/                 # 프레임워크 핵심
    │   │   ├── DstoneBatchApplication.java   # 메인 진입점
    │   │   ├── annotation/
    │   │   │   └── AutoRegJob.java            # 자동 등록 어노테이션
    │   │   ├── config/
    │   │   │   ├── Config.java               # 메인 설정
    │   │   │   ├── ConfigDatasource.java     # 다중 데이터소스
    │   │   │   ├── ConfigTransaction.java    # 트랜잭션 관리
    │   │   │   ├── ConfigMapper.java         # MyBatis 매퍼
    │   │   │   ├── ConfigJob.java            # Spring Batch 잡 설정
    │   │   │   ├── ConfigAutoReg.java        # 잡 자동 등록 로직
    │   │   │   └── ConfigAspect.java         # AOP 설정
    │   │   ├── core/
    │   │   │   ├── BaseJobConfig.java         # 잡 설정 기반 추상 클래스
    │   │   │   ├── BaseTasklet.java           # Tasklet 기반 추상 클래스
    │   │   │   ├── BaseItem.java              # ItemReader/Processor/Writer 기반
    │   │   │   └── BasePartitioner.java       # Partitioner 기반 추상 클래스
    │   │   └── runner/
    │   │       └── SimpleBatchRunner.java     # 개별 잡 실행기
    │   └── sample/jobs/            # 샘플 배치 잡
    │       ├── job001/             # sampleJob - 종합 예제
    │       ├── job002/             # 테이블 Insert/Delete
    │       ├── job003/             # 테이블 Update
    │       ├── job004/             # 파일 데이터 생성
    │       ├── job005/             # 파일 복사
    │       └── job006/             # Table↔File 변환
    └── resources/
        ├── schema/                 # Spring Batch/Task 테이블 생성 SQL
        └── sqlmap/                 # MyBatis 매퍼 XML
```

---

## 핵심 개념

### 잡(Job) 정의 패턴

모든 배치 잡은 `BaseJobConfig`를 상속하고 `@AutoRegJob` 어노테이션을 선언합니다:

```java
@Component
@AutoRegJob(name = "sampleJob")   // 잡 고유 이름
public class SampleJobConfig extends BaseJobConfig {

    @Override
    public void configJob() throws Exception {
        // 1. Tasklet 기반 스텝
        this.addStep(this.createStep("01.스텝1"));

        // 2. 멀티스레드 청크(Chunk) 스텝
        this.addStep(this.createMultiThreadStep(
            "02.멀티스레드스텝",
            20,                      // 스레드 수
            5,                       // 청크 크기
            new SampleItemReader<>(),
            new SampleItemProcessor(),
            new SampleItemWriter()
        ));

        // 3. 순차 Flow 추가
        this.addFlow(this.createSimpleFlow("03.심플플로우"));

        // 4. 병렬 Split Flow 추가
        this.addFlow(this.createSplitFlow("04.스플릿플로우"));
    }
}
```

### BaseJobConfig 제공 메소드

| 메소드 | 설명 |
|---|---|
| `addStep(step)` | 순차 스텝 추가 |
| `addFlow(flow)` | 순차 플로우 추가 |
| `createStep(name)` | 단순 Tasklet 스텝 생성 |
| `createMultiThreadStep(name, threads, chunk, reader, processor, writer)` | 멀티스레드 청크 스텝 생성 |
| `createSimpleFlow(name)` | 여러 스텝을 묶은 순차 Flow 생성 |
| `createSplitFlow(name)` | 여러 Flow를 병렬로 실행하는 Split Flow 생성 |

### @AutoRegJob 동작 방식

`ConfigAutoReg.java`가 스캔하여 어노테이션이 붙은 클래스를 Spring Batch `JobRegistry`에 등록합니다.

- `auto-register-jobs: true` → 애플리케이션 기동 시 모든 잡 일괄 등록 (REST API 서비스용)
- `auto-register-jobs: false` → 잡 실행 시점에 개별 등록 (`SimpleBatchRunner` / SCDF 사용 시)

---

## 설정 (`conf/application.yml`)

### 다중 데이터소스

| 데이터소스 | 용도 | 데이터베이스 | 풀 크기 |
|---|---|---|---|
| `common` | 배치 운영 DB (Spring Batch 메타데이터) | dataflow | max 50 |
| `sample` | 샘플/비즈니스 데이터 | sampleDB | max 100 |

```yaml
spring.datasource.common.hikari:
  driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
  jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/dataflow
  username: ENC(...)
  password: ENC(...)
  minimum-idle: 2
  maximum-pool-size: 50
  connection-timeout: 30000
  max-lifetime: 1700000
```

### Spring Batch 설정

```yaml
spring.batch:
  initialize-schema: NEVER    # 배치 테이블 자동 생성 비활성화 (수동 SQL 실행 필요)
  job.enabled: false          # 기동 시 자동 잡 실행 비활성화 (명시적 실행만 허용)
```

> 배치 테이블 초기 생성: `src/main/resources/schema/*.sql` 스크립트를 수동 실행

### Spring Cloud Task / Data Flow

```yaml
spring.cloud:
  task.initialize-enabled: false
  dataflow.client.server-uri: http://<dataflow-server>:9393
```

---

## 잡 실행 방법

### 방법 1 - 로컬 직접 실행 (SimpleBatchRunner)

```bash
# 1. 빌드
mvn clean package

# 2. 특정 잡 실행
java -jar -Dspring.batch.job.names=sampleJob target/dstone-batch-1.0.0-SNAPSHOT.jar

# 3. 다른 잡 실행 예시
java -jar -Dspring.batch.job.names=tableDataGenType01Job target/dstone-batch-1.0.0-SNAPSHOT.jar
```

**실행 순서:**
1. `DstoneBatchApplication.main()` 시작
2. `setSysProperties()` → `conf/env.properties`를 System Properties로 로드
3. `SimpleBatchRunner.launchJob()` → `JobLauncher`로 지정 잡 실행

### 방법 2 - Spring Cloud Data Flow 연동

1. 빌드된 JAR을 Data Flow에 `task` 애플리케이션으로 등록
2. Data Flow UI 또는 Shell에서 태스크 정의 후 실행
3. 잡 스케줄링, 모니터링, 중앙 관리 활용

---

## 샘플 잡 목록

| 잡 이름 | 디렉토리 | 설명 |
|---|---|---|
| `sampleJob` | `job001/` | Tasklet, Chunk, Flow, Split 종합 예제 |
| `tableDataGenType01Job` 외 | `job002/` | 테이블 데이터 Insert/Delete Tasklet 예제 |
| (update 관련 잡들) | `job003/` | 테이블 데이터 Update 예제 |
| (file gen 관련 잡들) | `job004/` | 파일 데이터 생성 예제 |
| (file copy 관련 잡들) | `job005/` | 파일 복사 예제 |
| (table-file 관련 잡들) | `job006/` | Table→File, File→Table 변환 예제 |

---

## Spring Batch 아키텍처

```
Job
├── Step 1 (Tasklet)
│     └── 단순 작업 단위
├── Step 2 (Chunk-Oriented)
│     ├── ItemReader   (데이터 읽기)
│     ├── ItemProcessor (데이터 변환/필터링)
│     └── ItemWriter   (데이터 저장)
└── Flow (여러 Step 묶음)
      └── SplitFlow (병렬 Flow)
```

**트랜잭션**: 각 청크(Chunk) 단위로 트랜잭션을 커밋하여 대용량 데이터 처리 시 안정성 확보

---

## 빌드

```bash
cd dstone-batch
mvn clean package

# 실행 가능한 JAR 생성 위치
# target/dstone-batch-1.0.0-SNAPSHOT.jar
```

> 상세 Docker 배포 절차: [`dstone-batch/docs/02.Docker빌드(dstone-batch).md`](../dstone-batch/docs/02.Docker빌드(dstone-batch).md)
