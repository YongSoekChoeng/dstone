# dstoneboot_를_로컬에서_실행

> 생성일시: 2026-09-04 17:07

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

---

## 질문

<task-notification>
<task-id>boqpu5qkn</task-id>
<tool-use-id>toolu_01P7mZpZ26JGsFUckDww1Jew</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/8022ba40-a6c1-4905-aefb-22a151ea8d85/tasks/boqpu5qkn.output</output-file>
<status>completed</status>
<summary>Background command "docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:pathtest . 2&gt;&amp;1 | tail -40" completed (exit code 0)</summary>
</task-notification>

---

## 답변

Docker 이미지 빌드가 성공했습니다 (exit 0). 새 경로(`/app/dstone/dstone-boot`)로 정상 빌드되는지 컨테이너를 잠깐 띄워서 확인하겠습니다.

---

## 답변

WORKDIR과 war 파일 위치가 `/app/dstone/dstone-boot`로 정확히 반영됐습니다. 테스트용 이미지는 실사용이 아니므로 정리하겠습니다.

---

## 답변

## 검증 완료

`dstone-boot` Docker 이미지가 새 경로(`/app/dstone/dstone-boot`)로 정상 빌드됐고, 컨테이너 안에서 `WORKDIR`과 war 파일 위치도 의도한 대로 반영된 걸 확인했습니다. 테스트 이미지는 삭제했습니다.

이제 `/workshop` 관련 변경은 코드·설정·문서·실제 시스템 권한까지 전부 정리됐고, batch(VM 배포)와 boot(Docker/k8s) 양쪽 다 동작 검증을 마쳤습니다. 실제로 Jenkins에서 각 Job의 **Build Now**를 눌러 파이프라인 전체를 한 번 돌려보시면 최종 확인이 될 것 같습니다.

---

## 질문

지금 jenkins 에 이 모든 작업들이 등록되어 있다는 말인가?

---

## 답변

확인해보겠습니다. Jenkins에 실제로 Job이 등록되어 있는지, 있다면 이번 변경사항이 자동으로 반영되는 구조인지 확인할게요.

---

## 답변

아니요, 등록하지는 않았습니다. 제가 한 건 다음 두 가지뿐입니다:

1. **git 리포지토리 안의 파일들** 수정 (`Jenkinsfile`, `Dockerfile`, `conf/*.properties`, 문서)
2. **호스트 시스템 권한** 변경 (`jenkins` 계정을 `jysn007` 그룹에 추가, 디렉터리 setgid) — 이건 사용자가 직접 `sudo` 명령으로 실행하셨죠

Jenkins Job 자체는 건드리지 않았습니다 (애초에 이 세션에서 만들지도 않았고요).

`docs/cloud-architecture.md`에 따르면 이 WSL 환경엔 이미 `dstone-boot-deploy`/`dstone-batch-deploy`/`dstone-batchadmin-deploy` 3개 Job이 **"Pipeline script from SCM"** 방식으로 구성돼 있다고 적혀 있습니다. 이 방식의 핵심은 Job이 파이프라인 스크립트 자체를 저장하지 않고, 매 빌드마다 리포지토리(`file:///app/dstone`)에서 `Jenkinsfile`을 다시 읽어온다는 점이에요. 그래서 만약 그 Job들이 실제로 이미 존재한다면:

- 제가 고친 `Jenkinsfile` 내용은 **다음 Build Now를 누르는 순간 자동으로 반영**됩니다. 별도 "등록" 작업이 필요 없습니다.
- 다만 저는 지금 Jenkins에 로그인 권한이 없어서 (`curl`로 확인해보니 인증 필요, 401) **Job이 실제로 존재하는지 이 세션에서 직접 확인은 못 했습니다.**

제가 검증한 범위는:
- `dstone-batch`의 `bin/startApp.sh`를 `DSTONE_PROFILE=vm`으로 **직접** 실행해서 새 경로(`/app/dstone/dstone-batch`) 동작 확인
- `dstone-boot`의 `Dockerfile`을 **직접** `docker build`해서 이미지 안 경로 확인

즉 코드 변경 자체는 검증됐지만, **Jenkins 파이프라인을 통한 end-to-end 실행**은 아직 확인 안 된 상태입니다. `http://localhost:8080`에서 Job 목록이 실제로 있는지 먼저 봐주시고, 있다면 각 Job에서 Build Now를 눌러 결과를 알려주시면 좋을 것 같습니다.

---

## 질문

dstone 의 pod 에서 로깅정책이 어떻게 동작하나?

---

## 답변

`dstone-boot` pod의 로깅은 `dstone-boot-conf` ConfigMap에 박혀 있는 `log4j2.xml`이 결정합니다(`dstone-boot/k8s/configmap.yaml`). 이 파일이 `conf` 볼륨으로 `/app/dstone/dstone-boot/conf`에 마운트되고, `DstoneBootApplication.main()`이 `logging.config` 시스템 프로퍼티로 그 경로의 `log4j2.xml`을 직접 가리켜서 로딩합니다.

## 출력 경로 두 곳 (동시에 기록)

- **Console appender** → stdout. 컨테이너 런타임이 캡처하므로 `kubectl logs -n dstone deploy/dstone-boot`로 보는 게 이 경로입니다.
- **RollingFile appender** → 컨테이너 내부 파일: `${APP_HOME}/LOGS/${APP_NAME}/execution/execution.log`. `env-k8s.properties`의 `APP_HOME=/app/dstone`, `APP_NAME=dstone-boot`가 그대로 치환되니 실제 경로는 `/app/dstone/LOGS/dstone-boot/execution/execution.log`입니다.

## 롤링/보관 정책 (RollingFile 쪽)

- `TimeBasedTriggeringPolicy interval="1"` + 파일명 패턴 `%d{yyyy-MM-dd}` → **일 단위**로 롤링.
- `DefaultRolloverStrategy max="7"` → 백업 파일 최대 7개 유지.
- 그 안의 `<Delete>` 정책이 롤링될 때마다 백업 디렉터리를 스캔해서 `IfLastModified age="3d"` — **3일 지난 백업은 즉시 삭제**.
- 즉 max=7과 3일-삭제 둘이 같이 걸려 있어서, 실제로는 최근 3일치 정도만 남고 그 이상은 거의 못 쌓입니다(두 정책 중 먼저 걸리는 쪽이 이깁니다).

## 로그 레벨 정책

- Root/`org.springframework`/`net.dstone.boot` = `INFO`.
- JDBC 계열은 대부분 `OFF`로 죽여놨습니다: `log4jdbc.log4j2`, `jdbc`, `jdbc.connection`, `jdbc.resultset`, `jdbc.audit`, `com.zaxxer.hikari`(커넥션풀). SQL 노이즈 억제 목적.
- 단 `jdbc.sqltiming`, `jdbc.resultsettable`은 `INFO`로 살려둬서 쿼리 실행시간/결과테이블은 로깅됨 — 다만 각 로거에 `ThreadContextMapFilter(SUPPRESS_SQL_LOG=Y → DENY)`가 걸려 있어서, 코드에서 특정 호출을 그 키로 마킹하면 그 순간만 선택적으로 SQL 로그를 죽일 수 있게 되어 있습니다.

## 짚고 넘어갈 부분: 파일 로그는 영속화가 안 됩니다

`deployment.yaml`을 보면 volume은 `conf`(ConfigMap) 하나뿐이고, `/app/dstone/LOGS` 쪽엔 아무 볼륨도 안 붙어 있습니다. 즉 RollingFile이 쓰는 `execution.log`는 컨테이너의 writable layer에만 존재하는 **휘발성 파일**입니다 — pod가 재시작/재스케줄되면 통째로 사라집니다. 이 리포지토리엔 Fluent Bit/Filebeat 같은 로그 수집 사이드카나 DaemonSet도 없어서, 실제로 안정적으로 확인 가능한 로그는 **stdout(Console appender) → `kubectl logs`** 경로뿐이고, 파일 로그는 pod가 살아있는 동안 `kubectl exec`로 들어가서 봐야 의미가 있는 정도입니다. 운영 관점에서 로그를 진짜 남기고 싶다면 PVC 마운트나 사이드카 추가가 필요한 지점입니다.

---

## 질문

/app/dstone/LOGS 에 로그가 생기지 않는데 /app/dstone/LOGS 디렉토리까지는 미리 만들어 놓아야 하는건가?

---

## 답변

아니요, 미리 만들어둘 필요 없습니다. Log4j2의 `RollingFileAppender`는 대상 파일 경로에 없는 디렉토리가 있으면 자동으로 만들면서 씁니다. 실제로 방금 `spring.profiles.active=wsl`로 직접 기동해서 확인했는데:

---

## 답변

`/app/dstone/LOGS/dstone-boot/execution/execution.log`가 디렉토리 없이 시작한 상태에서 그냥 잘 생성됐습니다.

그럼 안 생기는 이유는 디렉토리 부재가 아니라 다른 원인일 가능성이 높습니다. 흔한 경우들:

1. **프로파일 미지정 → `local` 기본값으로 떨어짐**: 지난번에 확인했듯 profile을 안 주면 `env.properties`(Windows 경로 `D:/AppHome/framework`)를 읽습니다. 그러면 로그 시스템이 초기화되기 전(config 로딩 단계)에 앱이 죽어서 `execution.log`가 아예 안 생깁니다. `env "spring.profiles.active=wsl" java -jar ...`처럼 profile을 명시했는지 확인해보세요.
2. **쓰기 권한 문제**: `/app/dstone` 소유자가 아닌 계정(예: k8s pod가 다른 UID로 뜨거나, Jenkins가 다른 계정으로 실행)으로 돌리면 `/app/dstone/LOGS` 생성이 조용히 실패할 수 있습니다.
3. **애플리케이션이 로그 초기화 이전 단계에서 죽음**: 지금 방금 뜬 로그처럼 DB/Redis 연결 실패로 컨텍스트가 죽어도 로그 파일 자체는 생성됩니다(Console/RollingFile 둘 다 Spring 컨텍스트보다 먼저 뜨는 로깅 시스템 구성요소라서) — 그러니 이 케이스는 아닙니다.

지금 로그가 안 생긴다고 하신 게 어떤 실행 경로(로컬 WSL 직접 실행 / k8s pod / Jenkins vm 배포)인지랑, 그때 어떤 명령/프로파일로 띄웠는지 알려주시면 정확히 짚어드릴게요.

---

## 질문

오전에 로그 생성 디렉토리 위치를 바꾸는 작업을 했거든 (/dstone-boot/conf/*.properties 의 APP_HOME 수정)
그 이후에 안생겼던 것 같은데... 혹시 properties 작업을 하게 되면 뭔가 반드시 해야 하는 사전작업이 있는건가? 나는 그저 재시작 했어.
kubectl scale deployment/dstone-boot -n dstone --replicas=0
kubectl scale deployment/dstone-boot -n dstone --replicas=1

---

## 답변

범인을 찾았습니다. **`kubectl scale`만으로는 이미지가 다시 빌드되지도, 다시 pull되지도 않습니다.** 지금 떠 있는 pod가 쓰는 이미지 자체가 오늘 작업 이전 것이었어요.

---

## 답변

## 원인

1. **`env-k8s.properties`는 ConfigMap이 아니라 이미지에 빌드타임에 구워 넣는 파일**입니다. `application.yml`/`log4j2.xml`은 `configmap.yaml` → 볼륨 마운트라서 `kubectl apply -f configmap.yaml`만 해도 반영되지만, `dstone-boot/conf/*.properties`는 `pom.xml`의 리소스 설정으로 Maven 빌드 시점에 WAR 안(`WEB-INF/classes/`)에 그대로 패키징됩니다. 즉 **git에서 파일을 고쳐도, 그건 이미지를 새로 빌드하기 전까지 실행 중인 컨테이너엔 전혀 반영되지 않습니다.**
2. `kubectl scale --replicas=0` → `1`은 **같은 Deployment 스펙**으로 pod를 다시 만드는 것뿐이라, 이미지 자체를 다시 빌드/pull하지 않습니다(`imagePullPolicy: IfNotPresent`라 태그가 같으면 로컬 캐시 이미지를 그대로 재사용).
3. 확인해보니 지금 떠 있는 pod의 실제 이미지 태그는 `localhost:5000/dstone-boot:kafka-fix`이고, 그 안에 들어있는 WAR 경로는 `/workshop/dstone-boot.war`입니다 — 즉 **오늘 저희가 `/workshop → /app/dstone`로 바꾼 작업 이전에 빌드된, 완전히 옛날 이미지**입니다. `dstone-boot/k8s/deployment.yaml` 파일엔 `:latest`로 적혀 있지만 실제 클러스터의 Deployment는 예전에 `kubectl set image`로 `:kafka-fix`를 수동 지정해놓은 상태라 매니페스트와도 어긋나 있고요.

## 결론 — properties 수정 후 필요한 절차

`conf/*.properties`처럼 이미지에 빌드타임 포함되는 파일을 고쳤다면, **재시작이 아니라 재빌드+재배포**가 필요합니다:

```bash
docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:<새태그> .
docker push localhost:5000/dstone-boot:<새태그>
kubectl set image deployment/dstone-boot dstone-boot=localhost:5000/dstone-boot:<새태그> -n dstone
kubectl rollout status deployment/dstone-boot -n dstone
```

(같은 태그(`latest`)로 다시 빌드해도 `imagePullPolicy: IfNotPresent`면 노드가 캐시를 쓸 수 있으니, 새 태그를 쓰거나 `kubectl rollout restart`가 아니라 `set image`로 태그를 바꿔줘야 확실합니다. 이게 정확히 Jenkinsfile이 매 빌드마다 `${BUILD_NUMBER}` 태그를 새로 쓰는 이유입니다.)

지금 바로 새 이미지 빌드해서 배포까지 해드릴까요? (이미 로컬에 빌드 테스트는 해봤던 `Dockerfile`이라 바로 가능합니다.)

---

## 질문

새 이미지로 배포하는 명령이 cloud-architecture.md [6.2 빠른 참조표] 에 기술되어 있는데 <TAG>는 내가 임으로 붙이면 되는건가? 그렇다면 기존(최근) TAG를 확인하는 방법은 무엇인가?
[6.2 빠른 참조표]에 대해서 조금 더 상세하고 친절하게 설명을 추가해줘.

---

## 답변

좋은 질문입니다. 먼저 답부터: **네, `<TAG>`는 임의로 정하시면 됩니다.** Jenkins 파이프라인은 자동으로 `${BUILD_NUMBER}`(빌드 번호)를 태그로 씁니다만, 수동으로 할 땐 날짜(`20260904-1`), 목적을 담은 이름(`kafka-fix`처럼) 등 원하는 문자열이면 됩니다 — Docker 태그는 그냥 이미지 버전을 가리키는 문자열 레이블일 뿐, 특별한 규칙은 없습니다. 다만 지금 겪으신 것처럼 태그를 매번 새로 만들지 않으면(`latest` 고정) `imagePullPolicy: IfNotPresent` 때문에 새 이미지가 안 당겨질 수 있으니, 매번 다른 태그를 쓰는 습관이 안전합니다.

**기존/최근 태그 확인 방법**은 세 갈래입니다 (뭘 보고 싶은지에 따라 다름):

---

## 답변

확인해보니 레지스트리에도 딱 `latest`, `kafka-fix` 두 태그뿐이고 둘 다 같은 이미지 ID(`ae3f180644c6`)입니다 — 즉 `latest`도 이미 그 옛날 이미지로 덮어써진 상태였네요. 이제 문서를 보강하겠습니다.

---

## 답변

`docs/cloud-architecture.md`의 6.2 절을 보강했습니다. 요약하면:

- **`<TAG>`는 임의로 정하시면 됩니다** — 형식 제약 없음, Jenkins는 `${BUILD_NUMBER}`를 자동으로 씁니다.
- **왜 매번 새 태그를 써야 하는지**: `imagePullPolicy: IfNotPresent` 때문에 같은 태그를 재사용하면 kind 노드가 캐시된 옛날 이미지를 그대로 씁니다 — 지금 겪으신 문제의 원인이 바로 이거였죠(레지스트리엔 `latest`/`kafka-fix` 두 태그뿐이고 둘 다 같은 옛날 이미지 ID였음).
- **현재 뜬 태그 확인법 3가지**를 명령과 함께 문서에 추가:
  1. `kubectl get deployment dstone-boot -n dstone -o jsonpath='{.spec.template.spec.containers[0].image}'` — 클러스터에 실제로 떠 있는 것 (가장 신뢰할 수 있는 정답)
  2. `curl -s http://localhost:5000/v2/dstone-boot/tags/list` — 레지스트리에 올라간 전체 태그 목록
  3. `docker images localhost:5000/dstone-boot` — 이 호스트에 로컬 캐시된 태그들
- `deployment.yaml` 파일의 `image: ...:latest`는 최초 적용 시점 기본값일 뿐, `set image`로 바뀐 뒤엔 실제 클러스터 상태와 어긋날 수 있다는("매니페스트 드리프트") 점도 명시했습니다.
- 표 전체에 각 명령의 동작 방식과 주의사항(비고 컬럼)을 추가하고, 관련 상세 절로 가는 링크도 달았습니다.

이제 새 이미지로 실제 배포까지 진행해드릴까요? (새 태그로 빌드 → push → `set image` → rollout 확인)

---

## 질문

cloud-architecture.md [5. CI/CD 파이프라인] 부분은 build.md 으로 옮겨가는게 맞지 않겠나?

---

## 답변

지금 구조를 보면 `build.md` 8절은 이미 표 하나로 압축된 요약이고, `cloud-architecture.md` 5절(5.1~5.7)은 Jenkins Job을 화면에서 처음부터 만드는 상세 절차(스크린샷, SCM 설정값, 트러블슈팅 표)입니다 — 이 관계는 kubectl 쪽(6절)과 완전히 같은 패턴이에요: `build.md`엔 "컨테이너 빌드 & kind 배포"가 몇 줄로 요약돼 있고, 상세 운영 절차(6.1~6.15)는 지금처럼 `cloud-architecture.md`에만 있습니다.

그래서 5절만 옮기면 오히려 일관성이 깨질 것 같습니다: `build.md`는 "빌드/실행 방법을 빠르게 찾는 곳", `cloud-architecture.md`는 "이 시뮬레이션 환경을 왜 이렇게 만들었고 실제로 어떻게 운영하는지"를 다루는 곳으로 역할이 나뉘어 있고, 5절의 내용(로컬 `file://` SCM을 쓰는 이유, `docker`/`jysn007` 그룹 권한 설정, 스크린샷 곁들인 Job 생성)은 build 자체보다 "이 가짜-클라우드 환경을 어떻게 조작하느냐"에 훨씬 가깝거든요.

옮기기보다는 지금 상태(요약은 build.md, 상세는 cloud-architecture.md + 상호링크)를 유지하는 걸 추천합니다. 그래도 옮기고 싶으신 특별한 이유가 있으신가요?

---

## 질문

아냐 그대로 가자.

---

## 답변

네, 지금 구조 그대로 유지하겠습니다.

---

## 질문

cloud-architecture.md 의 6.2절이 보강이 안되어 있는데 ?

---

## 답변

리포지토리엔 정상적으로 반영·커밋까지 되어 있습니다(`b9a06f4` 커밋에 diff로 다 들어가 있어요). 지금 `git status`도 clean이고, 파일을 직접 읽어봐도 "이미지 태그, 왜 매번 새로 붙여야 하나" 소단락과 비고 컬럼이 그대로 있습니다.

혹시 다음 중 하나가 원인일 수 있을까요?

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

상기 질문 "새 이미지로 배포하는 명령이 cloud-architecture.md [6.2 빠른 참조표] 에 기술되어 있는데 <TAG>는 내가 임으로 붙이면 되는건가? 그렇다면 기존(최근) TAG를 확인하는 방법은 무엇인가?
[6.2 빠른 참조표]에 대해서 조금 더 상세하고 친절하게 설명을 추가해줘." 라는 대답으로 수정된 내용이 cloud-architecture.md 에 반영이 안되어 있어. 아마도 이후 질의/응답중에 나온 "아냐 이대로 가자." 라는 말때문에 그냥 무시된게 아닌가 싶어.
내 말은 "cloud-architecture.md [5. CI/CD 파이프라인] 부분은 build.md 으로 옮겨가는게 어떻겠냐"는 건에 대해서만 이대로 두자 라는 의미이고 상기 질문은 보강된 내용으로 반영해 줘.

---

## 답변

확인해보니 6.2절 보강 내용은 이미 파일에 들어가 있고, 커밋(`b9a06f4`)까지 된 상태입니다 — `그대로 가자`는 답변으로 인해 되돌아가거나 무시된 적이 없습니다. 방금 다시 파일을 열어서 확인한 결과입니다:

---

## 답변

실제 파일(`/app/dstone/docs/cloud-architecture.md`, 269~298줄)에 보강된 내용이 그대로 들어 있고, `git status`도 clean이며, 커밋 `b9a06f4`에도 이 diff가 정확히 포함돼 있습니다. 즉 아무것도 유실되거나 되돌려지지 않았습니다.

혹시 보고 계신 화면이 다음 중 하나 아닐까요?
1. **에디터/IDE에 파일을 이미 열어둔 상태**라 저장 이후 자동으로 새로고침되지 않은 경우 — 탭을 닫았다 다시 열거나 리로드해보세요.
2. **GitHub 웹에서 보고 계신 경우** — 이 커밋들은 로컬 저장소에만 있고 아직 `origin/main`으로 push된 적이 없어서, GitHub 화면엔 반영이 안 되어 있을 수 있습니다.

어느 쪽인지, 혹은 다른 방식으로 보고 계신지 알려주시면 정확히 짚어드리겠습니다.

---

## 질문

"kafka-fix" 라는 태그는 지금 돌아가고 있는 이미지인가?

---

## 답변

네, `kafka-fix`가 지금 실제로 돌아가고 있는 이미지 맞습니다.

```
Deployment 스펙:  localhost:5000/dstone-boot:kafka-fix
실행 중인 Pod:    dstone-boot-6b87d645bd-m98vc (Running, 57분째)
실제 컨테이너 이미지: localhost:5000/dstone-boot@sha256:ae3f18...  (= kafka-fix, latest와 동일 digest)
```

다만 지난번에 확인했듯 이 이미지는 **오늘 저희가 `/workshop → /app/dstone` 경로 변경 작업을 하기 이전에 빌드된 옛날 이미지**입니다(내부 WAR 경로가 아직 `/workshop/dstone-boot.war`). 즉 지금 pod는 오늘 고친 `Dockerfile`, `deployment.yaml`, `env-k8s.properties` 내용을 전혀 반영하지 않은 채 돌아가고 있는 상태입니다.

새로 빌드해서 배포해드릴까요, 아니면 원하시는 시점에 직접 하시겠어요?

---

## 질문

직접하고자 해 가이드를 해줘 모든걸 삭제하고 처음부터 끝까지 직접 진행해보고 싶어.

---

## 답변

완전 클린 슬레이트로 처음부터 끝까지 가시는 거 좋습니다. 지금 상태 기준(namespace `dstone`에 25시간 된 리소스들, 로컬엔 `kafka-fix`/`latest` 두 태그, 둘 다 같은 옛날 이미지)으로 단계별 가이드 드릴게요. 명령은 전부 리포지토리 루트(`/app/dstone`)에서 실행하시면 됩니다.

## 0단계 — 사전 확인

```bash
kind get clusters                      # dev 나오는지
kubectl config current-context         # kind-dev 인지
docker ps --filter name=kind-registry  # kind-registry Up 상태인지
```
지금 확인해보니 셋 다 정상입니다 (`dev`, `kind-dev`, `kind-registry Up 26 hours`). 클러스터/레지스트리 자체는 안 건드리셔도 됩니다.

## 1단계 — 기존 것 전부 삭제

**k8s 리소스 삭제** (namespace를 지우면 그 안의 deployment/service/configmap/pod가 다 같이 사라짐):
```bash
kubectl delete -f dstone-boot/k8s/
kubectl get all -n dstone              # "No resources found" 나올 때까지 기다리기 (namespace 삭제는 몇 초 걸림)
```

**로컬에 캐시된 옛날 이미지 삭제**:
```bash
docker rmi localhost:5000/dstone-boot:kafka-fix localhost:5000/dstone-boot:latest
```

> 레지스트리(`localhost:5000`) 안에 저장된 이미지 데이터 자체까지 지우는 건 별도 GC 절차가 필요해서 번거롭습니다(`REGISTRY_STORAGE_DELETE_ENABLED` 설정 + exec 후 garbage-collect). 어차피 새 태그로 push하면 덮어써지는 개념이 아니라 새 항목이 생기는 거라, 레지스트리 내부 정리는 건너뛰셔도 실습엔 지장 없습니다. 완전히 지우고 싶으시면 말씀해주세요, 그 절차도 알려드릴게요.

## 2단계 — 새 이미지 빌드

Docker 빌드 컨텍스트는 반드시 리포지토리 루트여야 합니다(`dstone-common` 소스가 같이 필요해서).

```bash
docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:<원하는태그> .
```
`<원하는태그>`는 임의 문자열입니다 — 예: `docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:20260904-1 .`

**확인 포인트**: 빌드 로그 마지막이 `naming to localhost:5000/dstone-boot:<태그>` 같은 줄로 끝나면 성공.

## 3단계 — 레지스트리에 push

```bash
docker push localhost:5000/dstone-boot:<원하는태그>
curl -s http://localhost:5000/v2/dstone-boot/tags/list   # 방금 push한 태그가 목록에 보이는지 확인
```

## 4단계 — 매니페스트 반영

`dstone-boot/k8s/deployment.yaml`을 열어서 `image: localhost:5000/dstone-boot:latest` 줄을 방금 만든 태그로 바꿔주세요 (예: `image: localhost:5000/dstone-boot:20260904-1`) — 이렇게 하면 이번에 겪었던 "태그가 그대로라 캐시된 옛 이미지가 계속 뜨는" 문제를 애초에 피할 수 있습니다.

그다음 4개 매니페스트를 순서대로 적용 (namespace 먼저):
```bash
kubectl apply -f dstone-boot/k8s/namespace.yaml
kubectl apply -f dstone-boot/k8s/configmap.yaml
kubectl apply -f dstone-boot/k8s/deployment.yaml
kubectl apply -f dstone-boot/k8s/service.yaml
kubectl rollout status deployment/dstone-boot -n dstone --timeout=120s
```
`rollout status`가 `deployment "dstone-boot" successfully rolled out`을 출력하면 성공.

## 5단계 — 검증

```bash
# 실제로 새 태그가 떴는지
kubectl get deployment dstone-boot -n dstone -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

# pod 상태
kubectl get pods -n dstone -l app=dstone-boot

# 로그
kubectl logs -n dstone deploy/dstone-boot --tail=50

# 오늘 고친 경로가 실제로 반영됐는지 (WAR 위치 + LOGS 디렉토리 자동 생성 확인)
POD=$(kubectl get pods -n dstone -l app=dstone-boot -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n dstone $POD -- sh -c "pwd && ls -la /app/dstone/LOGS 2>&1"
```
이번엔 `pwd`가 `/app/dstone/dstone-boot`로 나오고, WAR도 `/workshop`이 아니라 그 경로에 있어야 정상입니다.

## 6단계 — 접속 확인

```bash
kubectl get svc dstone-boot -n dstone   # NodePort 확인 (지금은 30081)
# 방법 A: port-forward
kubectl port-forward -n dstone svc/dstone-boot 7081:7081
# 방법 B: NodePort로 바로 (kind 노드 IP 확인 필요시 kubectl get nodes -o wide)
curl -I http://localhost:30081/actuator/health/readiness
```

---

각 단계 실행하시면서 막히는 부분이나 에러 메시지 나오면 붙여넣어 주세요, 같이 짚어드릴게요.

---

## 질문

jysn007@DESKTOP-DVQ9FB6:/app/dstone$ docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:20260904-1
ERROR: docker: 'docker buildx build' requires 1 argument

Usage:  docker buildx build [OPTIONS] PATH | URL | -

Run 'docker buildx build --help' for more information
jysn007@DESKTOP-DVQ9FB6:/app/dstone$

---

## 답변

빌드 컨텍스트 경로(`.`)가 빠졌습니다. 명령 마지막에 `.`을 붙여주세요:

```bash
docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:20260904-1 .
```

(제가 가이드에 적었던 명령엔 있었는데, 실행하실 때 끝의 `.`이 빠진 것 같습니다 — `buildx build`는 `PATH | URL | -` 인자가 필수라 저 에러가 난 거예요.)

---

## 질문

4단계 — 매니페스트 반영 이 반드시 필요한 단계인가 ?

---

## 답변

아니요, 필수는 아닙니다 — 파일을 안 고치고 넘어가도 됩니다. 대신 `kubectl apply -f dstone-boot/k8s/`로 (파일에 적힌 대로 `:latest`) 일단 적용한 뒤, 곧바로 `kubectl set image deployment/dstone-boot dstone-boot=localhost:5000/dstone-boot:<방금 빌드한 태그> -n dstone`로 실제 태그를 지정해주면 동일한 결과를 얻습니다. 이게 사실 문서 6.4절과 Jenkins 파이프라인이 실제로 하는 방식이기도 하고요 — 매번 빌드할 때마다 git에 커밋된 `deployment.yaml`을 태그 변경으로 건드리지 않아도 되는 장점이 있습니다.

한 가지 주의할 점: namespace를 지웠어도 kind 노드(`dev-control-plane`)의 containerd 이미지 캐시는 별개로 남아있을 수 있어서, `:latest`로 다시 apply해도 예전에 캐시된 `:latest`가 그대로 뜰 위험이 여전히 있습니다. 그래서 파일 편집 여부와 상관없이 **`set image`로 이번에 실제 빌드한 태그를 명시적으로 지정하는 것**만큼은 꼭 해주셔야 새 이미지가 확실히 반영됩니다.

---

## 질문

내말은 latest 와 20260904-1 이라는 태그가 동일한 태그가 아닌가 하는거지?

---

## 답변

좋은 질문입니다 — 태그는 이미지 자체가 아니라 **특정 이미지(digest)를 가리키는 이름표**일 뿐이라서, 여러 태그가 같은 이미지를 가리킬 수도 있고 서로 다른 이미지를 가리킬 수도 있습니다. 지금 레지스트리를 직접 확인해보니:

```
latest       -> sha256:d160077e8c17...   (예전 kafka-fix와 동일한 digest — 옛날 이미지)
20260904-1   -> sha256:7f80b9d1bec0...   (방금 빌드한 새 이미지)
kafka-fix    -> sha256:d160077e8c17...   (latest와 동일)
```

**지금은 서로 다른 이미지입니다.** 이유는 간단합니다 — 아까 2단계에서 드린 명령이:
```bash
docker build -f dstone-boot/Dockerfile -t localhost:5000/dstone-boot:20260904-1 .
docker push localhost:5000/dstone-boot:20260904-1
```
이렇게 **`20260904-1` 태그만** 새로 빌드/push했지, `latest`는 다시 빌드하지도 push하지도 않았기 때문입니다. 그래서 `latest`는 여전히 옛날(`kafka-fix`) 시절 이미지를 그대로 가리키고 있고, 새로 만든 건 `20260904-1`뿐입니다.

만약 문서 6.4절처럼 `docker build ... -t <TAG> -t localhost:5000/dstone-boot:latest .`처럼 **두 태그를 한 번에** 걸어서 빌드했다면, 그땐 `latest`와 `<TAG>`가 완전히 같은 이미지를 가리켰을 겁니다(동일 digest). 그 방식도 나쁘지 않은데, 대신 매번 `latest`도 같이 덮어써지니 "예전 latest가 뭐였는지" 추적이 안 된다는 단점이 있어서, 지금처럼 매번 고유 태그만 쓰고 `deployment`엔 `kubectl set image`로 명시하는 방식이 더 안전합니다.

**결론**: 지금 `deployment.yaml`이 가리키는 `:latest`는 새로 만드신 이미지가 아니라 옛날 이미지이므로, `kubectl set image deployment/dstone-boot dstone-boot=localhost:5000/dstone-boot:20260904-1 -n dstone`로 명시적으로 `20260904-1`을 지정해주셔야 새 이미지가 반영됩니다.

---

## 질문

실무에서는 어떤 방법을 선호하는가?

---

## 답변

실무에서는 (b) 방식 — **`latest`는 아예 배포에 안 쓰고, 빌드마다 고유한 불변(immutable) 태그**(git 커밋 해시나 CI 빌드 번호, 예: `v1.2.3`, `a1b2c3d`, `build-482`)만 만들어서 `Deployment`가 그 태그를 정확히 가리키게 하는 쪽을 압도적으로 선호합니다. `latest`를 배포 참조로 쓰면 "지금 뭐가 떠 있는지"를 태그만 보고 알 수 없고, `kubectl rollout undo`도 무력화됩니다(직전 리비전도 똑같이 `latest`라고 적혀 있으니 롤백해봐야 최신 걸 다시 가리킬 뿐). 지금 겪으신 "태그 재사용 → 캐시된 옛 이미지가 계속 뜨는" 혼란 자체가 `latest`를 프로덕션 배포에 쓰지 말아야 하는 이유를 정확히 보여주는 사례입니다.

주요 트레이드오프는 **관리 부담**입니다 — 매 배포마다 매니페스트의 이미지 필드를 그 커밋/빌드에 맞는 새 태그로 갱신해야 하니, `kubectl set image`로 수동 지정하거나(지금 방식) 아예 GitOps(ArgoCD/Flux 등)로 그 갱신 자체를 커밋으로 남겨서 "클러스터 상태 = git 커밋" 원칙을 지키는 게 정석입니다. `latest`도 관행상 같이 push는 하되(사람이 수동으로 `docker pull`할 때 편의용), Kubernetes 매니페스트가 실제로 참조하는 값으로는 절대 쓰지 않는 게 일반적인 룰입니다.

---

## 질문

kubectl scale deployment/dstone-boot -n dstone --replicas=2
로 기동 할 경우 pod가 2개가 되는건가?

---

## 답변

네, 됩니다. `kubectl scale`은 Deployment의 `spec.replicas` 값을 바꾸는 것뿐이고, 컨트롤러가 그 차이를 감지해서 ReplicaSet을 통해 pod를 하나 더 만듭니다(같은 이미지·같은 스펙으로). 확인은:

```bash
kubectl get pods -n dstone -l app=dstone-boot -w
```

## 다만 이 앱을 2 replica로 돌리면 짚어볼 점들이 있습니다

- **파일 업로드**: `FILE_UPLOAD_ROOT`(`/app/dstone/dstone-boot/fileUp`)가 컨테이너 로컬 경로라 PVC 공유가 안 되어 있습니다. pod A가 받은 업로드 파일을 pod B는 못 봅니다 — 이 기능을 쓰는 상태로 2개 이상 띄우면 요청이 로드밸런싱될 때마다 파일 유무가 들쭉날쭉해집니다.
- **WebSocket/STOMP**: 로그에 보이는 `SimpleBrokerMessageHandler`는 인메모리 브로커라 pod마다 따로 놉니다. 클라이언트 A가 pod 1에 붙어 구독 중인데 pod 2가 메시지를 브로드캐스트하면 A는 못 받습니다 — 세션(Redis)과 달리 이건 외부 공유 브로커(RabbitMQ STOMP relay 등)로 안 바꾸면 다중 pod에서 정상 동작 안 함.
- **`kubectl logs deploy/dstone-boot`**: pod가 여러 개면 이 명령이 어떤 pod 로그인지 애매해집니다(2개 이상이면 에러 나거나 하나만 임의로 골라줌) — `-l app=dstone-boot --all-containers --prefix` 또는 pod 이름을 직접 지정해야 전체를 볼 수 있습니다.
- 반대로 **DB(HikariCP)/Redis 세션/Kafka consumer-group**은 원래 다중 인스턴스를 염두에 둔 구조라 이 부분들은 그대로 잘 동작합니다.

지금 이 환경은 학습/시뮬레이션 목적이라 실제로 2개를 띄워서 저 파일업로드/웹소켓 이슈가 재현되는 걸 직접 보는 것도 좋은 실습이 될 것 같습니다. 해보시겠어요?

---

## 질문

jysn007@DESKTOP-DVQ9FB6:/app/dstone$ kubectl scale deployment/dstone-boot -n dstone --replicas=0
deployment.apps/dstone-boot scaled
jysn007@DESKTOP-DVQ9FB6:/app/dstone$ kubectl scale deployment/dstone-boot -n dstone --replicas=2
deployment.apps/dstone-boot scaled
jysn007@DESKTOP-DVQ9FB6:/app/dstone$ nohup kubectl port-forward -n dstone svc/dstone-boot 7081:7081 > /tmp/port-forward.log 2>&1 & disown
[1] 55618
jysn007@DESKTOP-DVQ9FB6:/app/dstone$

이렇게 구동하고 http://localhost:7081 로 접속했을 때 접속이 안되는 것 같은데?

---

## 답변

로그를 보니 원인이 명확합니다 — **타이밍 문제**입니다. Pod는 지금 둘 다 정상(`READY 1/1`, restart 없음)인데, `port-forward` 명령을 pod가 뜬 지 **19초 만에** 실행하셨어요(로그 타임스탬프 15:09:00, pod 시작 15:08:41). Spring Boot 앱이 Tomcat 포트(7081)를 실제로 열기까지 로컬 테스트 때도 20~30초 이상 걸렸었죠 — 그 사이에 접속을 시도해서 pod 안에서 "connection refused"가 난 겁니다. readinessProbe의 `initialDelaySeconds: 30`도 이걸 감안해서 걸려있는 값이고요.

지금은 pod가 뜬 지 충분히 지나서 준비됐을 테니 다시 시도해보겠습니다.

---

## 답변

지금은 정상적으로 붙습니다 — 방금 새로 띄운 `port-forward`로 `http://localhost:7081/actuator/health/readiness`가 `200 OK`를 반환했습니다. 브라우저에서 `http://localhost:7081`로 다시 접속해보세요, 이제 될 겁니다.

정리하면: 아까 겪으신 "접속 안 됨"은 설정 문제가 아니라 **pod가 완전히 뜨기 전에(약 19초 시점) port-forward를 시도한 타이밍 이슈**였습니다. 앞으로는 `kubectl get pods -n dstone -l app=dstone-boot`로 `READY 1/1`이 된 걸 확인한 뒤 port-forward를 여시면 이런 일이 안 생깁니다.

---

## 질문

pod 를 구분해서 접속할 방법이 있나 ?

---

## 답변

네, Service를 거치지 않고 **특정 pod에 직접** `port-forward`하면 됩니다. Service는 두 pod 사이에서 로드밸런싱하지만, pod를 지정하면 그 pod로만 붙습니다.

---

## 답변

실제로 각각 다른 로컬 포트로 띄워서 두 pod 모두 독립적으로 응답하는 걸 확인했습니다.

## 방법 정리

**1. 특정 pod에 직접 port-forward** (가장 확실한 방법)
```bash
kubectl port-forward -n dstone pod/dstone-boot-595889556d-2gqts 7091:7081   # pod 1만
kubectl port-forward -n dstone pod/dstone-boot-595889556d-qs7xj 7092:7081   # pod 2만
```
`svc/dstone-boot` 대신 `pod/<pod이름>`을 지정하면 Service의 로드밸런싱을 완전히 우회하고 그 pod로만 연결됩니다. 지금 `localhost:7091`, `localhost:7092`로 각각 접속해서 열어보시면 서로 다른 pod와 통신하고 있는 걸 확인하실 수 있습니다.

**2. pod 안에서 직접 확인** (네트워크 왕복 없이)
```bash
kubectl exec -n dstone dstone-boot-595889556d-2gqts -- curl -s localhost:7081/actuator/health/readiness
```

**3. 어느 pod가 요청을 처리했는지 로그로 구분**
```bash
kubectl logs -n dstone dstone-boot-595889556d-2gqts --tail=20 -f
kubectl logs -n dstone dstone-boot-595889556d-qs7xj --tail=20 -f
```
두 터미널(또는 두 백그라운드 로그)을 동시에 띄워두고 `svc/dstone-boot`(NodePort 30081 또는 port-forward)로 요청을 여러 번 보내보면, 어느 쪽 로그에 찍히는지로 라운드로빈이 실제로 도는 걸 볼 수 있습니다.

참고로 이 앱 자체는 응답 안에 "어느 pod가 처리했는지" 알려주는 정보(호스트명 등)를 내려주진 않습니다 — 그건 `/actuator/info`에 `HOSTNAME` 환경변수를 노출하도록 코드/설정을 추가해야 하는 부분이라, 필요하시면 그것도 만들어드릴 수 있어요.

---

## 질문

jenkins 등록 도와줘

---

## 답변

`/var/lib/jenkins/jobs`를 보니 이미 `dstone-boot-deploy` Job 하나는 등록되어 있고(SCM URL `file:///app/dstone`, Script Path `dstone-boot/Jenkinsfile` — 문서와 일치), `dstone-batch-deploy`/`dstone-batchadmin-deploy` 두 개가 아직 없습니다. 이 둘을 만들어드리면 될까요?

Jenkins Job 생성은 브라우저 UI(New Item)로 하는 거라 제가 화면을 직접 조작할 순 없지만, 이미 등록된 `dstone-boot-deploy`와 완전히 같은 패턴이라 금방 하실 수 있게 정확한 값으로 안내해드릴게요.

---

## 답변

현재 상태를 보니 두 가지를 해드려야 합니다.

## 발견한 문제: 기존 `dstone-boot-deploy` Job이 브랜치 설정 오류로 실패해 있음

```
hudson.plugins.git.GitException: Command "git fetch ... +refs/heads/master:refs/remotes/origin/master" returned status code 128:
stderr: fatal: couldn't find remote ref refs/heads/master
Finished: FAILURE
```

Job의 **Branches to build**가 `*/master`로 등록돼 있는데, 이 리포지토리의 실제 브랜치는 `main`입니다(`git branch` 확인함). 그래서 1번 빌드가 시작하자마자 바로 실패했습니다. 문서(`cloud-architecture.md` 5.2)엔 `*/main`으로 적으라고 되어 있는데 실제 설정은 `*/master`로 잘못 들어가 있던 상태예요.

**고치는 법**: Jenkins(`http://localhost:8080`) → `dstone-boot-deploy` Job → 왼쪽 **Configure** → Pipeline 섹션 → **Branches to build** 값을 `*/master` → `*/main`으로 수정 → **Save**.

## 남은 2개 Job 만들기

`dstone-batch-deploy`, `dstone-batchadmin-deploy`는 아직 없습니다. `dstone-boot-deploy`와 완전히 같은 방식으로 만들면 됩니다 — **New Item** → 이름 입력 → **Pipeline** 선택 → **OK** → 아래로 스크롤해서 **Pipeline** 섹션:

| 항목 | dstone-batch-deploy | dstone-batchadmin-deploy |
|---|---|---|
| Definition | `Pipeline script from SCM` | 동일 |
| SCM | `Git` | 동일 |
| Repository URL | `file:///app/dstone` | 동일 |
| Credentials | `- none -` | 동일 |
| **Branches to build** | `*/main` | 동일 |
| Script Path | `dstone-batch/Jenkinsfile` | `dstone-batchadmin/Jenkinsfile` |

**Save** → **Build Now**로 확인.

두 개 다 만드시고, `dstone-boot-deploy`도 브랜치 값 고치신 뒤 세 Job 모두 **Build Now** 한 번씩 눌러서 결과 알려주시면, 실패하는 게 있을 때 로그 보고 같이 짚어드릴게요.

---

## 질문

일단 dstone-boot 를 진행했고 다음과 같은 에러가 났어. Started by user jysn007
hudson.plugins.git.GitException: Command "git fetch --tags --force --progress --prune -- origin +refs/heads/master:refs/remotes/origin/master" returned status code 128:
stdout: 
stderr: fatal: couldn't find remote ref refs/heads/master

    at PluginClassLoader for git-client//org.jenkinsci.plugins.gitclient.CliGitAPIImpl.launchCommandIn(CliGitAPIImpl.java:2852)
    at PluginClassLoader for git-client//org.jenkinsci.plugins.gitclient.CliGitAPIImpl.launchCommandWithCredentials(CliGitAPIImpl.java:2193)
    at PluginClassLoader for git-client//org.jenkinsci.plugins.gitclient.CliGitAPIImpl$1.execute(CliGitAPIImpl.java:638)
    at PluginClassLoader for git//jenkins.plugins.git.GitSCMFileSystem$BuilderImpl.build(GitSCMFileSystem.java:404)
Caused: java.io.IOException
    at PluginClassLoader for git//jenkins.plugins.git.GitSCMFileSystem$BuilderImpl.build(GitSCMFileSystem.java:409)
    at PluginClassLoader for scm-api//jenkins.scm.api.SCMFileSystem.of(SCMFileSystem.java:219)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition.create(CpsScmFlowDefinition.java:123)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition.create(CpsScmFlowDefinition.java:70)
    at PluginClassLoader for workflow-job//org.jenkinsci.plugins.workflow.job.WorkflowRun.run(WorkflowRun.java:342)
    at hudson.model.ResourceController.execute(ResourceController.java:97)
    at hudson.model.Executor.run(Executor.java:456)
Finished: FAILURE

---

## 답변

확인해보니 아직 설정이 안 바뀌었습니다 — Job의 `config.xml`을 보면 여전히 `*/master`로 되어 있고, 새 빌드 기록도 안 생겼어요(방금 보내주신 로그가 지난번 1번 빌드와 완전히 동일한 내용입니다). 즉 **Branches to build 수정 후 Save를 누르는 단계가 아직 반영이 안 된 상태**로 보입니다.

다시 한번 정확히 짚어드릴게요:

1. `http://localhost:8080` → 대시보드에서 **dstone-boot-deploy** Job 클릭
2. 왼쪽 메뉴에서 **Configure** 클릭 (Build Now 말고 Configure입니다)
3. 아래로 스크롤 → **Pipeline** 섹션 안에 Git 설정이 있고, 그 안에 **Branches to build** 입력란이 `*/master`로 되어 있을 겁니다
4. 그 값을 `*/main`으로 고침
5. 페이지 맨 아래 **Save** 버튼 클릭 (반드시 눌러야 반영됩니다 — 그냥 다른 메뉴로 이동하면 저장 안 됨)
6. 저장 후 화면이 Job 상세 페이지로 돌아오면, 왼쪽 메뉴에서 **Build Now** 클릭

Save를 누르신 뒤 다시 Build Now 해보시고 결과 알려주세요 — 제가 파일로 바로 확인해서 반영됐는지 봐드릴 수 있습니다.

---

## 질문

Started by user jysn007
Obtained dstone-boot/Jenkinsfile from git file:///app/dstone
[Pipeline] Start of Pipeline
[Pipeline] node
Running on Jenkins in /var/lib/jenkins/workspace/dstone-boot-deploy
[Pipeline] {
[Pipeline] stage
[Pipeline] { (Declarative: Checkout SCM)
[Pipeline] checkout
ERROR: Checkout of Git remote 'file:///app/dstone' aborted because it references a local directory, which may be insecure. You can allow local checkouts anyway by setting the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.
ERROR: Maximum checkout retry attempts reached, aborting
[Pipeline] }
[Pipeline] // stage
[Pipeline] }
[Pipeline] // node
[Pipeline] stage
[Pipeline] { (Declarative: Post Actions)
[Pipeline] echo
====== Cleanup ======
[Pipeline] deleteDir
Error when executing always post condition:
Also:   org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId: 4960e4b4-beeb-4281-b545-5f14728036b9
org.jenkinsci.plugins.workflow.steps.MissingContextVariableException: Required context class hudson.FilePath is missing
Perhaps you forgot to surround the deleteDir step with a step that provides this, such as: node
    at PluginClassLoader for workflow-step-api//org.jenkinsci.plugins.workflow.steps.StepDescriptor.checkContextAvailability(StepDescriptor.java:264)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.DSL.invokeStep(DSL.java:312)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.DSL.invokeMethod(DSL.java:199)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsScript.invokeMethod(CpsScript.java:124)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:98)
    at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:325)
    at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1225)
    at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1034)
    at org.codehaus.groovy.runtime.callsite.PogoMetaClassSite.call(PogoMetaClassSite.java:41)
    at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall(CallSiteArray.java:47)
    at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call(AbstractCallSite.java:116)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:180)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.GroovyInterceptor.onMethodCall(GroovyInterceptor.java:23)
    at PluginClassLoader for script-security//org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SandboxInterceptor.onMethodCall(SandboxInterceptor.java:163)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:178)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:182)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:152)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.sandbox.SandboxInvoker.methodCall(SandboxInvoker.java:17)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.LoggingInvoker.methodCall(LoggingInvoker.java:124)
    at WorkflowScript.run(WorkflowScript:75)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.delegateAndExecute(ModelInterpreter.groovy:139)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:728)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.catchRequiredContextForNode(ModelInterpreter.groovy:390)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:727)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2125)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2110)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2151)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:717)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.executePostBuild(ModelInterpreter.groovy:695)
    at ___cps.transform___(Native Method)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationGroup.methodCall(ContinuationGroup.java:107)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.dispatchOrArg(FunctionCallBlock.java:118)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.fixName(FunctionCallBlock.java:82)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationPtr$ContinuationImpl.receive(ContinuationPtr.java:71)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ConstantBlock.eval(ConstantBlock.java:21)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Next.step(Next.java:84)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Continuable.run0(Continuable.java:142)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.access$001(SandboxContinuable.java:17)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.run0(SandboxContinuable.java:48)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThread.runNextChunk(CpsThread.java:188)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup.run(CpsThreadGroup.java:464)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:372)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:302)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$wrap$4(CpsVmExecutorService.java:143)
    at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)
    at hudson.remoting.SingleLaneExecutorService$1.run(SingleLaneExecutorService.java:139)
    at jenkins.util.ContextResettingExecutorService.lambda$wrap$0(ContextResettingExecutorService.java:26)
    at jenkins.security.ImpersonatingExecutorService.lambda$wrap$0(ImpersonatingExecutorService.java:66)
    at jenkins.util.ErrorLoggingExecutorService.lambda$wrap$0(ErrorLoggingExecutorService.java:51)
    at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
    at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)
    at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
    at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:53)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:50)
    at org.codehaus.groovy.runtime.GroovyCategorySupport$ThreadCategoryInfo.use(GroovyCategorySupport.java:136)
    at org.codehaus.groovy.runtime.GroovyCategorySupport.use(GroovyCategorySupport.java:275)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$categoryThreadFactory$0(CpsVmExecutorService.java:50)
    at java.base/java.lang.Thread.run(Thread.java:1583)

[Pipeline] echo
====== Deployment Failed ======
[Pipeline] sh
Error when executing failure post condition:
Also:   org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId: 5253b829-fefa-4940-ab64-632d43301066
org.jenkinsci.plugins.workflow.steps.MissingContextVariableException: Required context class hudson.FilePath is missing
Perhaps you forgot to surround the sh step with a step that provides this, such as: node
    at PluginClassLoader for workflow-step-api//org.jenkinsci.plugins.workflow.steps.StepDescriptor.checkContextAvailability(StepDescriptor.java:264)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.DSL.invokeStep(DSL.java:312)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.DSL.invokeMethod(DSL.java:199)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsScript.invokeMethod(CpsScript.java:124)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:98)
    at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:325)
    at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1225)
    at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1034)
    at org.codehaus.groovy.runtime.callsite.PogoMetaClassSite.call(PogoMetaClassSite.java:41)
    at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall(CallSiteArray.java:47)
    at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call(AbstractCallSite.java:116)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:180)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.GroovyInterceptor.onMethodCall(GroovyInterceptor.java:23)
    at PluginClassLoader for script-security//org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SandboxInterceptor.onMethodCall(SandboxInterceptor.java:163)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker$1.call(Checker.java:178)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:182)
    at PluginClassLoader for script-security//org.kohsuke.groovy.sandbox.impl.Checker.checkedCall(Checker.java:152)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.sandbox.SandboxInvoker.methodCall(SandboxInvoker.java:17)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.LoggingInvoker.methodCall(LoggingInvoker.java:124)
    at WorkflowScript.run(WorkflowScript:71)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.delegateAndExecute(ModelInterpreter.groovy:139)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:728)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.catchRequiredContextForNode(ModelInterpreter.groovy:390)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:727)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2125)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2110)
    at com.cloudbees.groovy.cps.CpsDefaultGroovyMethods.each(CpsDefaultGroovyMethods:2151)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy:717)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.runPostConditions(ModelInterpreter.groovy)
    at org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter.executePostBuild(ModelInterpreter.groovy:695)
    at ___cps.transform___(Native Method)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationGroup.methodCall(ContinuationGroup.java:107)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.dispatchOrArg(FunctionCallBlock.java:118)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.FunctionCallBlock$ContinuationImpl.fixArg(FunctionCallBlock.java:87)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ContinuationPtr$ContinuationImpl.receive(ContinuationPtr.java:71)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.impl.ConstantBlock.eval(ConstantBlock.java:21)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Next.step(Next.java:84)
    at PluginClassLoader for workflow-cps//com.cloudbees.groovy.cps.Continuable.run0(Continuable.java:142)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.access$001(SandboxContinuable.java:17)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.SandboxContinuable.run0(SandboxContinuable.java:48)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThread.runNextChunk(CpsThread.java:188)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup.run(CpsThreadGroup.java:464)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:372)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsThreadGroup$2.call(CpsThreadGroup.java:302)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$wrap$4(CpsVmExecutorService.java:143)
    at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)
    at hudson.remoting.SingleLaneExecutorService$1.run(SingleLaneExecutorService.java:139)
    at jenkins.util.ContextResettingExecutorService.lambda$wrap$0(ContextResettingExecutorService.java:26)
    at jenkins.security.ImpersonatingExecutorService.lambda$wrap$0(ImpersonatingExecutorService.java:66)
    at jenkins.util.ErrorLoggingExecutorService.lambda$wrap$0(ErrorLoggingExecutorService.java:51)
    at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572)
    at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)
    at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
    at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:53)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService$1.call(CpsVmExecutorService.java:50)
    at org.codehaus.groovy.runtime.GroovyCategorySupport$ThreadCategoryInfo.use(GroovyCategorySupport.java:136)
    at org.codehaus.groovy.runtime.GroovyCategorySupport.use(GroovyCategorySupport.java:275)
    at PluginClassLoader for workflow-cps//org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.lambda$categoryThreadFactory$0(CpsVmExecutorService.java:50)
    at java.base/java.lang.Thread.run(Thread.java:1583)

[Pipeline] }
[Pipeline] // stage
[Pipeline] End of Pipeline
ERROR: Checkout of Git remote 'file:///app/dstone' aborted because it references a local directory, which may be insecure. You can allow local checkouts anyway by setting the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.
Finished: FAILURE

---

## 답변

새로운 진전입니다 —브랜치 문제는 해결됐고(`Obtained dstone-boot/Jenkinsfile from git file:///app/dstone`까지 성공), 이번엔 다른 문제입니다.

Jenkins Git 플러그인이 보안상 `file://` 로컬 경로 체크아웃을 기본으로 막아놓습니다(과거 로컬 파일시스템 접근 관련 취약점 대응). 에러 메시지에 해결법이 그대로 나와 있네요: `hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT` 시스템 프로퍼티를 `true`로 켜야 합니다. Jenkins가 어떻게 구동되고 있는지 확인해서 정확한 설정 위치를 찾아드릴게요.

---

## 답변

Git 플러그인의 로컬 체크아웃 차단은 JVM 시스템 프로퍼티(`-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`)로 풀어야 합니다. 확인해보니 이 Jenkins는 systemd로 관리되고, JVM 옵션은 유닛 파일에 `JAVA_OPTS`로 직접 박혀 있습니다(`/etc/default/jenkins`는 이 설치에선 안 쓰임). 벤더 유닛 파일을 직접 고치지 않고 `systemctl edit`로 드롭인 오버라이드를 추가하는 게 정석입니다.

아래 명령을 실행해 주세요:

```bash
sudo mkdir -p /etc/systemd/system/jenkins.service.d
sudo tee /etc/systemd/system/jenkins.service.d/override.conf > /dev/null <<'EOF'
[Service]
Environment="JAVA_OPTS=-Djava.awt.headless=true -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true"
EOF
sudo systemctl daemon-reload
sudo systemctl restart jenkins
```

재시작 후 Jenkins 웹 UI가 다시 뜨는 데 10~30초 정도 걸릴 수 있습니다(`http://localhost:8080` 새로고침). 뜬 뒤 `dstone-boot-deploy` Job에서 **Build Now**를 다시 눌러보시고 결과 알려주세요 — 반영 여부는 제가 유닛 상태로 바로 확인해드릴 수 있습니다.
