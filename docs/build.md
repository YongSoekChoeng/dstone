# 빌드 가이드

## 목차

- [1. 전제 조건](#1-전제-조건)
- [2. 모듈 구성과 산출물](#2-모듈-구성과-산출물)
- [3. 로컬 빌드 명령](#3-로컬-빌드-명령)
- [4. 실행](#4-실행)
- [5. 설정 프로파일 (`-Dspring.profiles.active=<profile>`)](#5-설정-프로파일--dspringprofilesactiveprofile)
- [6. dstone-boot — 컨테이너 빌드 & kind 배포](#6-dstone-boot--컨테이너-빌드--kind-배포)
- [7. dstone-batch / dstone-batchadmin — VM 스타일 배포 (`bin/*.sh`)](#7-dstone-batch--dstone-batchadmin--vm-스타일-배포-binsh)
- [8. CI/CD (Jenkins)](#8-cicd-jenkins)
- [9. 빌드 관련 트러블슈팅](#9-빌드-관련-트러블슈팅)

dstone 멀티모듈 프로젝트의 빌드 명령, 모듈별 산출물, 배포 방식(VM 스타일 / 컨테이너·쿠버네티스)과 CI/CD 파이프라인을 한 곳에 모은 문서다. 각 모듈의 기능/설정 상세는 모듈별 문서([dstone-common.md](dstone-common.md), [dstone-boot.md](dstone-boot.md), [dstone-batch.md](dstone-batch.md), [dstone-batchadmin.md](dstone-batchadmin.md))를, 배포 아키텍처 설계 배경은 [cloud-architecture.md](cloud-architecture.md)를 참고한다.

## 1. 전제 조건

- JDK 21, Maven 3.9.x — 설치 방법은 [software/jdk.md](software/jdk.md), [software/maven.md](software/maven.md)
- 빌드 도구 버전은 로컬에 설치된 것을 그대로 쓴다(별도 wrapper 없음): `java -version`, `mvn -version`으로 확인
- `dstone-common`은 나머지 세 모듈이 참조하는 라이브러리이므로 **항상 먼저 빌드**돼야 한다 (`mvn install`로 로컬 저장소에 설치되어야 다른 모듈이 참조 가능)

## 2. 모듈 구성과 산출물

```
dstone/                         (루트 aggregator POM, groupId: net.dstone, version: 1.0.0-SNAPSHOT)
├── dstone-common/              JAR (라이브러리) — 다른 모든 모듈이 의존
├── dstone-boot/                WAR (executable) — dstone-boot.war
├── dstone-batch/                JAR (executable) — dstone-batch-1.0.0-SNAPSHOT.jar
└── dstone-batchadmin/           WAR (executable) — dstone-batchadmin.war
```

| 모듈 | Packaging | 산출물 경로 | Main Class | 포트 |
|---|---|---|---|---|
| dstone-common | JAR | `dstone-common/target/dstone-common-1.0.0-SNAPSHOT.jar` | - | - |
| dstone-boot | WAR | `dstone-boot/target/dstone-boot.war` | `net.dstone.boot.DstoneBootApplication` | 7081 |
| dstone-batch | JAR | `dstone-batch/target/dstone-batch-1.0.0-SNAPSHOT.jar` | `net.dstone.batch.common.DstoneBatchApplication` | 6081 |
| dstone-batchadmin | WAR | `dstone-batchadmin/target/dstone-batchadmin.war` | `net.dstone.batchadmin.DstoneBatchAdminApplication` | 5081 |

dstone-boot/dstone-batchadmin의 WAR는 `<finalName>${artifactId}</finalName>`로 고정되어 항상 `dstone-boot.war`/`dstone-batchadmin.war`로 나온다(버전 접미사 없음). dstone-batch JAR는 `${artifactId}-${version}` 형식이라 버전이 파일명에 포함된다.

## 3. 로컬 빌드 명령

```bash
# 1. 공통 라이브러리 먼저 설치 (다른 모듈이 로컬 저장소에서 참조)
cd dstone-common && mvn clean install

# 2. 개별 모듈 빌드
cd dstone-boot && mvn clean package        # WAR
cd dstone-batch && mvn clean package       # JAR
cd dstone-batchadmin && mvn clean package  # WAR

# 또는 루트에서 전체 리액터 빌드 (순서 자동 해결)
mvn clean install

# 테스트 스킵
mvn clean package -DskipTests

# 리액터 부분 빌드 (특정 모듈 + 의존 모듈만) — Jenkinsfile/Dockerfile이 실제 사용하는 방식
mvn -pl dstone-common,dstone-boot -am -DskipTests clean package
```

빌드 시 각 모듈의 `conf/*.properties`, `application.yml`, `log4j2.xml`이 `target/classes`로 복사된다(클래스패스 포함). 서버 배포용으로는 `conf/` 디렉터리를 애플리케이션 홈 밖으로 분리해 외부 설정으로 쓰므로, 배포 빌드 시 `src/main/resources`의 `application.yml`/`log4j2.xml`은 주석 처리해 `conf/` 버전이 우선하도록 한다(모듈별 상세는 각 모듈 문서 참고).

## 4. 실행

```bash
# dstone-boot (내장 Tomcat, 포트 7081)
java -jar dstone-boot/target/dstone-boot.war

# dstone-batch — 특정 잡 실행 (포트 6081)
java -jar -Dspring.batch.job.names=sampleJob dstone-batch/target/dstone-batch-1.0.0-SNAPSHOT.jar

# dstone-batchadmin (포트 5081)
java -jar dstone-batchadmin/target/dstone-batchadmin.war
```

Spring Batch(`dstone-batch`)/애플리케이션 스키마(`dstone-boot`, `dstone-batchadmin`) 테이블은 각 모듈 `src/main/resources/schema/*.sql`을 최초 1회 수동 실행해야 한다(`initialize-schema: NEVER` — 자동 생성 안 됨).

## 5. 설정 프로파일 (`-Dspring.profiles.active=<profile>`)

각 모듈은 배포 대상 환경별로 별도의 `conf/env-<profile>.properties`를 두고, 기동 시 `net.dstone.*.DstoneXxxApplication.setSysProperties()`가 이를 읽어 System Properties로 주입한다. 그 안의 `APP_CONF_DIR` 값으로 실제 `application.yml`/`log4j2.xml` 위치를 찾는다.

| 프로파일 | 대상 | 적용 모듈 |
|---|---|---|
| (기본, `env.properties`) | Windows 개발자 PC | 전체 |
| `dev` | 기존 Docker Compose 배포(레거시) | 전체 |
| `wsl` | WSL git 체크아웃 그대로 수동 테스트 (bin 스크립트 기본값) | dstone-batch, dstone-batchadmin |
| `vm` | Jenkins CI/CD가 배포하는 VM 스타일 실행 경로 | dstone-batch, dstone-batchadmin |
| `k8s` | kind 클러스터 Pod (컨테이너) | dstone-boot |

## 6. dstone-boot — 컨테이너 빌드 & kind 배포

`dstone-boot`은 VM 스타일 `bin/*.sh`로 운영하지 않고 컨테이너 이미지로 빌드해 로컬 `kind` 쿠버네티스 클러스터에 Pod로 배포한다.

```bash
# 빌드 컨텍스트는 반드시 리포지토리 루트 (dstone-common 소스가 함께 필요)
docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:latest .
docker push localhost:5000/dstone-boot:latest

kubectl apply -f dstone-boot/k8s/namespace.yaml
kubectl apply -f dstone-boot/k8s/configmap.yaml
kubectl apply -f dstone-boot/k8s/deployment.yaml
kubectl apply -f dstone-boot/k8s/service.yaml
kubectl rollout status deployment/dstone-boot -n dstone --timeout=120s
```

- `dstone-boot/Dockerfile`은 멀티스테이지 빌드: 1단계(`maven:3.9-eclipse-temurin-21`)에서 루트 `pom.xml` + `dstone-common` + `dstone-boot`(+ 나머지 두 모듈은 리액터 구성을 위해 `pom.xml`만) 복사 후 `mvn -pl dstone-common,dstone-boot -am -DskipTests clean package`, 2단계(`eclipse-temurin:21-jre`)에서 `dstone-boot.war`만 담아 `-Dspring.profiles.active=k8s`로 기동.
- 이미지 태그는 로컬 사설 레지스트리(`localhost:5000`, kind 클러스터의 containerd에 미러로 등록됨)를 거친다. 레지스트리/클러스터 구성은 [software/kubernetes.md](software/kubernetes.md) 참고.
- 배포 후 헬스체크: `kubectl get pods -n dstone -l app=dstone-boot`, `kubectl logs -n dstone deploy/dstone-boot --tail=30`. Readiness/Liveness 프로브는 각각 `/actuator/health/readiness`, `/actuator/health/liveness`.

## 7. dstone-batch / dstone-batchadmin — VM 스타일 배포 (`bin/*.sh`)

systemd에 등록하지 않고 순수 쉘 스크립트로만 기동/중지한다(설계 배경: [cloud-architecture.md](cloud-architecture.md#4-dstone-batch--dstone-batchadmin--vm-스타일)).

```bash
cd dstone-batch/bin        # 또는 dstone-batchadmin/bin
./startApp.sh               # 기본 프로파일 wsl, target/의 jar/war를 찾아 nohup 백그라운드 기동, PID를 application.pid에 기록
DSTONE_PROFILE=vm ./startApp.sh   # Jenkins CI/CD 배포 경로용 프로파일
./statusApp.sh               # application.pid로 실행 여부 확인 (RUNNING/STOPPED)
./stopApp.sh
```

- 두 모듈 모두 같은 패턴: `target/`에서 아티팩트를 glob으로 탐색(없으면 먼저 `mvn clean package` 필요 — 에러 메시지로 안내), 이미 실행 중이면 재기동 거부, 기동 성공 여부를 PID로 재확인.
- 로그는 `logs/dstone-batch.out` / `logs/dstone-batchadmin.out`.
- `dstone-boot`에도 레거시 `bin/startApp.sh`/`stopApp.sh`가 남아 있으나 위 신규 프로파일 체계(`env-wsl`/`env-vm`) 이전 방식이라 현재는 사용하지 않는다 — dstone-boot은 위 "컨테이너 빌드 & kind 배포" 경로가 표준이다.

## 8. CI/CD (Jenkins)

전제: 세 파이프라인 모두 Jenkins Job의 SCM 체크아웃 범위가 **모노레포 루트 전체**여야 한다(리액터 빌드 `-am` 옵션과 Docker 빌드 컨텍스트가 `dstone-common`을 함께 요구하기 때문).

| 파이프라인 | 빌드 | 배포 단계 |
|---|---|---|
| `dstone-boot/Jenkinsfile` | `mvn -pl dstone-common,dstone-boot -am -DskipTests clean package` | `docker build/push`(로컬 레지스트리) → `kubectl apply` + `kubectl set image` + `rollout status`(kind `dstone` 네임스페이스) → 헬스체크(`kubectl get pods`/`logs`) |
| `dstone-batch/Jenkinsfile` | `mvn -pl dstone-common,dstone-batch -am -DskipTests clean package` | 아티팩트+`conf/`+`bin/`을 `/workshop/dstone/dstone-batch`로 복사 → `bin/stopApp.sh` → `bin/startApp.sh`(`DSTONE_PROFILE=vm`) → `bin/statusApp.sh`로 `RUNNING` 확인 |
| `dstone-batchadmin/Jenkinsfile` | `mvn -pl dstone-common,dstone-batchadmin -am -DskipTests clean package` | 위 dstone-batch와 동일 패턴, 배포 경로 `/workshop/dstone/dstone-batchadmin` |

- 세 파이프라인 모두 실패 시 `post.failure`에서 로그 tail(`kubectl logs` 또는 `logs/*.out`)을 출력하고, `post.always`에서 워크스페이스를 정리(`deleteDir()`)한다.
- Jenkins Controller는 WSL 호스트에 상주하며, `jenkins` 시스템 계정이 `docker` 그룹에 속해 있어 별도 인프라 없이 `docker`/`kubectl`을 실행한다. 설치/권한 설정은 [software/jenkins.md](software/jenkins.md) 참고.

## 9. 빌드 관련 트러블슈팅

- **`dstone-boot`/`dstone-batch` 등 개별 모듈만 `mvn clean package`했는데 컴파일 에러**: `dstone-common`이 로컬 Maven 저장소에 먼저 `install`되어 있는지 확인(`~/.m2/repository/net/dstone/dstone-common`).
- **`bin/startApp.sh` 실행 시 "실행할 jar/war 파일을 찾을 수 없습니다"**: 해당 모듈 디렉터리에서 `mvn clean package`를 먼저 실행해 `target/`에 아티팩트를 생성해야 한다.
- **`docker build -f dstone-boot/Dockerfile ...`을 `dstone-boot/` 안에서 실행하면 실패**: 빌드 컨텍스트가 리포지토리 루트여야 한다(`dstone-common` 소스를 함께 COPY하므로) — 반드시 루트에서 `-f dstone-boot/Dockerfile ... .` 형태로 실행.
- **Jenkins 파이프라인이 스파스 체크아웃(모듈 디렉터리만)으로 설정된 경우**: 리액터 빌드(`-am`)와 Docker 빌드 컨텍스트가 실패한다 — Job의 SCM 설정을 모노레포 루트 전체 체크아웃으로 변경해야 한다.
