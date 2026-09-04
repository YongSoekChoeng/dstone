# dstoneboot_를_로컬에서_실행

> 생성일시: 2026-09-04 09:50

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
