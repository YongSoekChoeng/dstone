# dstoneboot_를_로컬에서_실행

> 생성일시: 2026-09-04 10:16

## 질문

dstone-boot 를 로컬에서 실행했을 때 아래와 같은 에러가 발생해.
이부분 해결해줘.

SLF4J(W): Class path contains multiple SLF4J providers.
SLF4J(W): Found provider [org.apache.logging.slf4j.SLF4JServiceProvider@564fabc8]
SLF4J(W): Found provider [ch.qos.logback.classic.spi.LogbackServiceProvider@16d04d3d]
SLF4J(W): See https://www.slf4j.org/codes.html#multiple_bindings for an explanation.
SLF4J(I): Actual provider is of type [org.apache.logging.slf4j.SLF4JServiceProvider@564fabc8]
Exception in thread "main" java.lang.ExceptionInInitializerError
    at org.springframework.boot.builder.SpringApplicationBuilder.createSpringApplication(SpringApplicationBuilder.java:115)
    at org.springframework.boot.builder.SpringApplicationBuilder.<init>(SpringApplicationBuilder.java:102)
    at org.springframework.boot.builder.SpringApplicationBuilder.<init>(SpringApplicationBuilder.java:98)
    at net.dstone.boot.DstoneBootApplication.main(DstoneBootApplication.java:44)
Caused by: org.apache.logging.log4j.LoggingException: log4j-slf4j2-impl cannot be present with log4j-to-slf4j
    at org.apache.logging.slf4j.Log4jLoggerFactory.validateContext(Log4jLoggerFactory.java:67)
    at org.apache.logging.slf4j.Log4jLoggerFactory.newLogger(Log4jLoggerFactory.java:49)
    at org.apache.logging.slf4j.Log4jLoggerFactory.newLogger(Log4jLoggerFactory.java:32)
    at org.apache.logging.log4j.spi.AbstractLoggerAdapter.getLogger(AbstractLoggerAdapter.java:52)
    at org.apache.logging.slf4j.Log4jLoggerFactory.getLogger(Log4jLoggerFactory.java:32)
    at org.slf4j.LoggerFactory.getLogger(LoggerFactory.java:447)
    at org.apache.commons.logging.LogAdapter$Slf4jAdapter.createLocationAwareLog(LogAdapter.java:121)
    at org.apache.commons.logging.LogAdapter.createLog(LogAdapter.java:95)
    at org.apache.commons.logging.LogFactory.getLog(LogFactory.java:67)
    at org.apache.commons.logging.LogFactory.getLog(LogFactory.java:59)
    at org.springframework.boot.SpringApplication.<clinit>(SpringApplication.java:202)
    ... 4 more

---

## 답변

dstone-boot 클래스패스에 SLF4J 바인딩이 두 개(log4j2 계열 + logback) 들어가 있어서 나는 에러네요. pom.xml을 확인해서 원인을 찾아볼게요.

---

## 답변

spring-boot-starter-web에서만 spring-boot-starter-logging을 제외했고, actuator/websocket/webflux 등 다른 스타터들은 제외 처리가 안 되어 있어서 logback이 다시 딸려 들어오는 것 같습니다. dependency:tree로 확인해볼게요.

---

## 답변

dstone-common엔 문제 없네요. dstone-boot 쪽에서 확인해보겠습니다.

---

## 답변

원인을 확인했습니다. `dstone-boot/pom.xml`의 `spring-boot-starter-actuator`가 `spring-boot-starter-logging`(logback + log4j-to-slf4j)을 끌어오는데, exclusion이 걸려있지 않아서 `dstone-common`이 제공하는 log4j2 구현체와 충돌하는 거였습니다. actuator에 동일한 exclusion을 추가하겠습니다.

---

## 답변

Exclusion을 추가한 후 트리를 다시 확인해서 정말 해결됐는지 검증하겠습니다.

---

## 답변

logback과 log4j-to-slf4j가 사라지고 log4j-slf4j2-impl만 남았습니다. 다른 스타터(webflux, websocket 등)도 같은 방식으로 새는 것이 없는지 dstone-common 전체를 한 번 더 점검하겠습니다.

---

## 답변

dstone-common 전체 트리에도 logback/log4j-to-slf4j가 없습니다. 이제 실제로 빌드하고 로컬에서 기동해서 에러가 사라졌는지 확인하겠습니다.

---

## 답변

Build succeeds. Let's try running it locally to confirm the SLF4J error is gone.

---

## 답변

No SLF4J warnings/errors in the output anymore. Let's peek at what actually happened during the run to make sure it's a normal (e.g., DB/Redis connection) failure and not a new logging issue.

---

## 답변

SLF4J 충돌 에러는 해결됐습니다 (로그에 더 이상 SLF4J 경고/에러가 없음). 이어서 뜨는 에러는 별개의 문제입니다.

---

## 답변

## 원인 및 조치

`dstone-boot/pom.xml`의 `spring-boot-starter-actuator`가 전이 의존성으로 `spring-boot-starter-logging`(logback-classic + log4j-to-slf4j)을 끌어오고 있었습니다. `dstone-common`은 `spring-boot-starter-web`에만 이 exclusion을 걸어뒀는데, actuator에는 걸려있지 않아서 `dstone-boot`에서만 logback과 log4j2가 동시에 classpath에 올라가 `log4j-slf4j2-impl cannot be present with log4j-to-slf4j` 에러가 발생한 것이었습니다.

`spring-boot-starter-actuator`에도 동일하게 `spring-boot-starter-logging` exclusion을 추가해서 (`dstone-boot/pom.xml:53-60`) 해결했습니다. `dependency:tree`로 확인한 결과 이제 `log4j-slf4j2-impl`만 남고 logback/log4j-to-slf4j는 사라졌고, 실제로 실행해봐도 SLF4J 관련 에러는 더 이상 나지 않습니다.

## 별개로 발견한 문제

수정 후 실행해보니 다음 에러가 새로 나옵니다 (SLF4J 문제와는 무관):

```
Config data resource 'file [D:/AppHome/framework/dstone/dstone-boot/conf/application.yml]' does not exist
```

`conf/env.properties`의 `APP_HOME`/`APP_CONF_DIR`/`FILE_UPLOAD_ROOT`가 Windows 경로(`D:/AppHome/framework`)로 되어 있어서입니다. 같은 디렉토리에 WSL용 값(`/app/dstone/...`)이 담긴 `conf/env-wsl.properties`가 이미 준비되어 있으니, 로컬(WSL)에서 돌리시려면 이 파일 내용으로 `env.properties`를 교체(또는 심볼릭 링크)하면 될 것 같습니다. 이건 별개 사안이라 원하시면 바로 처리해 드릴게요.

---

## 질문

env.properties 는 net.dstone.boot.DstoneBootApplication.setSysProperties() 에 의해서 동적으로 로딩되는게 아닌가 ?

---

## 답변

맞습니다. `setSysProperties()`가 `spring.profiles.active` 값에 따라 로딩할 파일명을 동적으로 결정합니다 (`DstoneBootApplication.java:128-177`):

- `spring.profiles.active`가 비어있거나 `local`이면 → `env.properties`
- 그 외 값이면 → `env-{profile}.properties` (예: `wsl` → `env-wsl.properties`, `dev` → `env-dev.properties`, `k8s` → `env-k8s.properties`)

제가 아까 실행할 때 프로파일을 지정하지 않아서 기본값 `local`로 떨어졌고, 그래서 Windows 경로가 든 `env.properties`가 로딩된 거였습니다. `env.properties` 파일 자체를 바꿀 필요 없이, WSL에서 돌릴 땐 profile을 `wsl`로 지정해주면 됩니다.

한 가지 주의할 점: 코드가 `System.getenv("spring.profiles.active")` 를 그대로 조회합니다 (표준 스프링의 `SPRING_PROFILES_ACTIVE` 환경변수가 아니라 점(.)이 포함된 이름 그대로). 그래서 환경변수로 주려면 이렇게 넣어야 합니다.

---

## 답변

`env-wsl.properties`가 정상적으로 로딩되어 `application.yml` 경로도 올바르게 잡혔습니다(EXIT:124는 타임아웃으로 종료시킨 것 — 이후 DB/Redis 연결 단계까지 진행됨). `env.properties`를 건드릴 필요 없이, 로컬(WSL) 실행 시엔 다음처럼 profile만 지정하면 됩니다:

```bash
env "spring.profiles.active=wsl" java -jar target/dstone-boot.war
```

(환경변수 이름이 표준 `SPRING_PROFILES_ACTIVE`가 아니라 점(`.`)이 포함된 `spring.profiles.active` 그대로라는 점 주의하세요.)

---

## 질문

/dstone-boot/conf/env-k8s.properties 를 사용하는 부분이 어디일까?

---

## 답변

`dstone-boot/Dockerfile:25`에서 사용합니다.

```dockerfile
ENTRYPOINT ["java", "-Dspring.profiles.active=k8s", "-jar", "/workshop/dstone-boot.war", "net.dstone.boot.DstoneBootApplication"]
```

`dstone-boot`가 K8s Pod로 컨테이너 기동될 때 `-Dspring.profiles.active=k8s`가 시스템 프로퍼티로 넘어가고, `setSysProperties()`(`DstoneBootApplication.java:136-137`)가 이를 읽어서 `env-k8s.properties`를 로딩하는 구조입니다.

참고로 다른 실행 경로들도 같은 방식입니다:
- `bin/startApp.sh` → `-Dspring.profiles.active=dev` → `env-dev.properties` (VM/서버 배포용)
- `bin/startApp.bat` → `-Dspring.profiles.active=local` → `env.properties` (윈도우 로컬)
- WSL에서 직접 돌릴 땐 지난번처럼 `-Dspring.profiles.active=wsl` (또는 `env "spring.profiles.active=wsl"`)을 넘기면 `env-wsl.properties`가 쓰입니다.

---

## 질문

원하는 것은 /workshop 가 아닌 /app/dstone 을 모든 어플리케이션의 홈디렉토리로 하고 싶어.
공연히 workshop 라는 디렉토리를 만들고 이중관리 하게 되는 상황을 피하고 싶은거야.

---

## 답변

이건 코드 몇 줄 바꾸는 걸 넘어서 이미 문서화되고(`docs/cloud-architecture.md`) WSL Jenkins에 실제로 구성된(설명대로라면 이미 `/workshop/dstone/` 쓰기 권한까지 부여됨) CI/CD 아키텍처를 바꾸는 일이라, 범위를 먼저 확인하고 싶습니다.

`/workshop`이 쓰이는 곳은 두 갈래입니다:
1. **VM 배포(dstone-batch, dstone-batchadmin)**: Jenkinsfile이 빌드산출물+conf+bin을 `/workshop/dstone/<module>`로 복사한 뒤 그 경로에서 실행 — 이게 말씀하신 "이중관리"의 핵심입니다.
2. **컨테이너 내부(dstone-boot)**: Docker 이미지 WORKDIR/ENTRYPOINT, k8s volumeMount, `env-k8s.properties` — 이건 컨테이너 파일시스템 내부 경로라 호스트의 `/app/dstone`과 실제로 충돌하진 않지만, 일관성 차원에서 바꿀 수도 있습니다.
3. 그 외 각 모듈의 `env-dev.properties`는 문서상 "기존 Docker Compose 배포(레거시, 이미 폐기)"용으로 표시돼 있어 지금 안 쓰이는 죽은 설정으로 보입니다.

---

## 답변

No PID files, so nothing running there currently. Let's use each module's own `stopApp.sh` on that deployed copy just to be safe (idempotent, no-op if not running), then remove the directory.

---

## 답변

Confirmed nothing is running. Removing the `/workshop` deployment tree now.

---

## 답변

빈 `/workshop` 디렉토리 자체(`/` 밑에 root가 만든 것)는 권한상 제가 지울 수 없습니다 — 필요하면 `sudo rmdir /workshop`으로 지워주시면 됩니다. 이제 코드/설정을 고치겠습니다.

---

## 답변

코드/설정 변경 후 한 가지 실제 이슈가 남습니다: Jenkins가 이제 `/app/dstone/dstone-batch`, `/app/dstone/dstone-batchadmin` 경로에 직접 빌드산출물을 복사해야 하는데, 이 디렉토리는 `jysn007:jysn007 755` 소유라 `jenkins` 계정(그룹: `jenkins`, `docker`)은 쓰기 권한이 없습니다. 기존엔 `/workshop/dstone`을 `docker` 그룹 setgid로 별도 프로비저닝했었는데, 이번엔 대상이 실제 개발 리포지토리라 권한 부여 방식을 확인하고 싶습니다.

---

## 답변

`sudo`가 이 세션에선 인증할 터미널이 없어서 직접 실행이 안 됩니다. 아래 명령을 `!` 접두사로 직접 실행해 주세요 (예: `! sudo usermod -aG jysn007 jenkins`):

---

## 답변

아래 명령들을 순서대로 실행해 주세요:

```bash
sudo usermod -aG jysn007 jenkins
sudo systemctl restart jenkins
sudo chmod g+ws /app/dstone/dstone-batch /app/dstone/dstone-batchadmin
sudo chmod -R g+w /app/dstone/dstone-batch/target /app/dstone/dstone-batch/conf /app/dstone/dstone-batch/bin /app/dstone/dstone-batchadmin/target /app/dstone/dstone-batchadmin/conf /app/dstone/dstone-batchadmin/bin
sudo chgrp -R jysn007 /app/dstone/dstone-batch/target /app/dstone/dstone-batch/conf /app/dstone/dstone-batch/bin /app/dstone/dstone-batchadmin/target /app/dstone/dstone-batchadmin/conf /app/dstone/dstone-batchadmin/bin
groups jenkins
```

- `usermod` + Jenkins 재시작: jenkins 계정을 `jysn007` 그룹에 넣어서 `/app/dstone` 트리에 그룹 접근권을 갖게 함.
- `chmod g+ws`: 두 모듈 디렉터리에 setgid 비트 — 이후 Jenkins가 새로 만드는 파일/디렉터리도 그룹이 `jysn007`로 유지되어 평소 쓰시는 계정과 충돌 없이 공유됨.
- `chmod -R g+w`: 이미 존재하는 `target/conf/bin`에 그룹 쓰기 권한 부여(로컬에서 이미 `mvn package`로 만들어둔 파일들이라 신규 생성분이 아니라 별도로 열어줘야 함).

실행하시면 알려주세요, 이어서 문서(`CLAUDE.md`, `docs/cloud-architecture.md`, `docs/build.md`, `docs/software/jenkins.md`)의 `/workshop` 언급을 `/app/dstone`로 업데이트하겠습니다.

---

## 질문

실행했어.

---

## 답변

권한 설정 확인됐습니다 (jenkins가 jysn007 그룹에 포함, setgid 2775, Jenkins 서비스 active). 이제 문서를 업데이트하겠습니다.

---

## 답변

Now let's apply all the doc edits for cloud-architecture.md.

---

## 답변

Good — `dstone-boot` container's `/workshop` mention wasn't in prose elsewhere, only in Dockerfile (already fixed). Now updating the remaining doc files.

---

## 답변

Only the intentional historical footnote remains. Now let's verify the modules still build cleanly, and do an end-to-end sanity check of the VM deploy flow locally (simulating what Jenkins will do) using the module's own directories.

---

## 답변

`vm` 프로파일이 `/app/dstone/dstone-batch`를 정확히 가리키는 것을 확인했고, 프로세스도 정상 정지시켰습니다.

---

## 답변

Docker build is running in the background (Maven reactor build inside the image takes a while). I'll check back when it finishes.

---

## 답변

지금까지 진행한 변경사항을 정리해서 알려드리고, `dstone-boot` Docker 이미지 빌드는 백그라운드에서 계속 검증 중입니다(완료되면 알려드릴게요).

## 변경 요약

**VM 배포 경로 (batch/batchadmin)**
- `dstone-batch/Jenkinsfile`, `dstone-batchadmin/Jenkinsfile`: `WORKSPACE_DIR`을 `/workshop/dstone/<module>` → `/app/dstone/<module>`로 변경 (리포지토리 자기 자신의 모듈 디렉터리에 직접 배포)
- `dstone-batch/conf/env-vm.properties`, `dstone-batchadmin/conf/env-vm.properties`: `APP_HOME`/`APP_CONF_DIR`도 동일하게 `/app/dstone/<module>`로 변경
- 실제로 `DSTONE_PROFILE=vm ./bin/startApp.sh`로 로컬 재현 테스트해서 정상 기동/정지 확인함

**dstone-boot 컨테이너 내부 경로**
- `Dockerfile`: `WORKDIR`/`ENTRYPOINT`를 `/workshop` → `/app/dstone/dstone-boot`
- `k8s/deployment.yaml`: volumeMount `/workshop/conf` → `/app/dstone/dstone-boot/conf`
- `conf/env-k8s.properties`: `APP_HOME`/`APP_CONF_DIR`/`FILE_UPLOAD_ROOT` 동일하게 변경

**레거시 정리**
- 4개 모듈의 `conf/env-dev.properties`(폐기된 옛 Docker Compose 배포용, git 추적조차 안 되던 죽은 파일) 삭제

**문서**: `CLAUDE.md`, `docs/cloud-architecture.md`, `docs/build.md`, `docs/software/jenkins.md`, `docs/images/jenkins-batch-pipeline-flow.svg`의 `/workshop` 언급을 전부 갱신 (cloud-architecture.md엔 "예전엔 이랬다"는 히스토리 각주 한 줄만 의도적으로 남김)

**시스템**: 실제 `/workshop/dstone` 배포 디렉터리는 정지 확인 후 삭제 완료, `jenkins` 계정을 `jysn007` 그룹에 추가하고 관련 디렉터리에 setgid 권한 부여 완료 (사용자가 직접 실행)
