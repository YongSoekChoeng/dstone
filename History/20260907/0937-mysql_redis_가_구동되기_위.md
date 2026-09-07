# mysql_redis_가_구동되기_위

> 생성일시: 2026-09-07 09:37

## 질문

mysql, redis 가 구동되기 위해 docker 가 기동되어야 한다는게 왜그런지 모르겠어. 이유를 정확하게 알려줘.

---

## 답변

MySQL/Redis 자체는 Docker 컨테이너가 아니라 WSL에 네이티브로 설치된 서비스예요(`apt install mysql-server`/`redis-server`). 그런데 왜 Docker가 먼저 떠 있어야 하는지는 **바인딩 주소** 때문입니다.

**원인**

1. `dstone-boot`는 `kind` 클러스터 안의 Pod로 떠 있습니다. Pod 입장에서 `127.0.0.1`은 호스트가 아니라 Pod 자기 자신이라, 호스트의 MySQL/Redis에 `127.0.0.1`로는 접근할 수 없습니다.
2. 그래서 `mysqld.cnf`의 `bind-address`와 `redis.conf`의 `bind`를 `127.0.0.1` 하나만이 아니라 `172.18.0.1`(= `kind`가 만드는 Docker 브리지 네트워크의 게이트웨이 IP)까지 포함하도록 설정해뒀습니다. Pod가 이 게이트웨이 IP로 호스트의 MySQL/Redis에 접근하는 구조예요.
3. 문제는 `172.18.0.1`이라는 주소 자체가 **dockerd가 뜨고 `kind` 브리지 네트워크(`br-*`)가 실제로 attach된 뒤에만 존재**한다는 점입니다. Docker/kind가 뜨기 전에는 그 IP를 가진 인터페이스가 아예 없습니다.
4. 그 상태에서 MySQL/Redis를 먼저 켜면, 설정 파일에 적힌 `172.18.0.1`에 바인딩을 시도하다가 그 주소가 존재하지 않으니 `bind: Cannot assign requested address` 에러로 기동 자체가 실패합니다.

즉 "Docker가 있어야 mysql/redis가 동작해서"가 아니라, **mysql/redis가 자기 설정에 적어둔 특정 IP(kind 브리지 게이트웨이)가 Docker/kind 없이는 존재하지 않기 때문에 그 주소로 바인딩하는 절차 자체가 실패**하는 겁니다.

**참고 — 과거 장애 이력**: `docs/environment.md` 91번째 줄에 2026-09-06 장애 진단 기록이 있는데, 예전엔 순서가 mysql/redis → docker였고 MySQL은 systemd 자동 재시작 타이밍이 우연히 맞아 살아났지만 Redis는 `StartLimitBurst`(기본 5회/10초)에 걸려 계속 죽어 있었다고 합니다. 그래서 지금은 **Docker(및 kind 네트워크)를 가장 먼저 올리고 그 다음 mysql/redis**를 올리는 순서로 고정돼 있어요(`docs/environment.md` 5.1절).

---

## 질문

<어플리케이션 탑재 관련 소프트웨어 그룹>
docker(dstone-boot 어플리케이션 탑재)
kubenetes

<일반 소프트웨어 그룹>
mysql
postgresql
rabbitmq
redis
kafka
jenkins

나는 docker를 시작하지 않고도 일반 소프트웨어 그룹만 시작시키고 wsl에 있는 dstone-boot 어플리케이션을 
기동하여 서비스를 진행할 수 있었으면 좋겠어.
docker에 올라간 어플리케이션을 기동시킬 수도 있어야 하고.
말하자면 선택적으로 온프레미스/클라우드 서비스를 기동시킬 환경을 원해.

이렇게 하려면 어떤 방법을 취해야 할까?

---

## 답변

지금 상태를 보니 **필요한 뼈대는 이미 상당 부분 갖춰져 있고, 막고 있는 건 딱 하나(mysql/redis의 bind 설정)**예요. 정리하면:

**이미 있는 것**
- `dstone-boot`는 이미 두 개의 프로파일을 갖고 있습니다.
  - `wsl` 프로파일(`conf/env-wsl.properties`): DB/Redis/RabbitMQ/Kafka 전부 `localhost` — 도커 전혀 불필요.
  - `k8s` 프로파일(`conf/env-k8s.properties`): 전부 `172.18.0.1`(kind 브리지 게이트웨이) — 컨테이너용.
  - 두 파일 다 `pom.xml`의 `conf/*.properties` 리소스 규칙으로 WAR 클래스패스에 같이 번들되고, `DstoneBootApplication.setSysProperties()`가 `-Dspring.profiles.active` 값에 따라 그중 하나만 골라 읽습니다. 즉 **같은 WAR로 실행 시점 플래그만 바꿔서 온프레미스/클라우드를 선택하는 구조가 이미 구현돼 있어요.**
  - `dstone-boot/bin/startApp.sh`도 이미 `-Dspring.profiles.active=wsl`로 고정돼 있어서, `mvn package` 후 이 스크립트만 실행하면 도커 없이 WSL 네이티브로 뜹니다.
  - 클라우드 쪽은 `docs/cloud-architecture.md`의 kind 빌드/푸시/배포 절차 그대로.

**진짜 막는 지점**
- `mysqld.cnf`의 `bind-address = 127.0.0.1,172.18.0.1`(Redis도 동일 패턴)이 문제입니다. `172.18.0.1`은 dockerd+kind 브리지가 떠야만 존재하는 주소라서, **온프레미스 모드만 쓰고 싶어도** mysql/redis를 켜려면 그 주소가 존재해야 하니 결국 Docker부터 띄워야 하는 강제 결합이 생깁니다. 이게 없어져야 "일반 SW 그룹만 선택적으로 기동"이 가능해집니다.

**해결 방법 두 가지**
1. **(추천) bind를 `0.0.0.0`으로 완화** — mysql/redis가 모든 인터페이스에서 리슨하도록 바꾸면, 그 시점에 kind 브리지가 있든 없든 기동에 아무 영향이 없고, 나중에 Docker/kind를 켜서 `172.18.0.1`이 새로 생겨도 재시작 없이 바로 접근됩니다. 순서 제약이 완전히 사라짐. 단점은 바인딩 범위가 특정 IP 나열보다 넓어진다는 것인데, 어차피 WSL 자체가 Windows NAT 뒤에 있고 Redis는 이미 protected-mode를 꺼둔 상태라 실질적 노출 차이는 크지 않습니다.
2. **특정 IP 바인딩 유지 + 동적 재구성** — start-mysql.sh/start-redis.sh가 시작 시점에 `172.18.0.1` 존재 여부를 보고 bind-address를 껐다 켰다 하고, k8s-start.sh가 도커를 올릴 때 mysql/redis를 재구성+재시작. 바인딩 범위는 좁게 유지되지만 스크립트가 복잡해지고 도커를 켤 때마다 mysql/redis가 잠깐 재시작되는 부작용이 생깁니다.

1번이 훨씬 단순하고 지금 원하시는 "선택적 기동"과 정확히 맞아떨어져서 추천합니다.

이 방향으로 진행하려면 `/etc/mysql/mysql.conf.d/mysqld.cnf`, `/etc/redis/redis.conf` 같은 시스템 설정(sudo 필요, git 관리 밖)을 고치고 서비스 재시작이 필요합니다. 어느 방식으로 갈지 정해주시면 바로 적용하고, `docs/environment.md`/`docs/cloud-architecture.md`에 "dstone-boot는 wsl-native/k8s-Pod 두 모드를 선택적으로 지원한다"는 내용도 맞춰서 업데이트할게요.

---

## 질문

1번 방향으로 진행해줘. (패스워드 : db2admin)
그리고 아래의 도큐먼트에도 내용 수정반영해줘.
docs/cloud-architecture.md
docs/environment.md
docs/software/mysql.md
docs/software/redis.md

---

## 답변

Config 반영됨. 이제 서비스 재시작해서 적용 확인.

---

## 답변

두 서비스 모두 `0.0.0.0`으로 정상 리슨 중이고, Docker/kind 없이도 재기동 성공했습니다. 이제 문서 4개를 순서대로 수정할게요.

---

## 답변

Now `cloud-architecture.md` section 2.

---

## 답변

Now `docs/software/mysql.md` and `docs/software/redis.md`.

---

## 답변

Now editing both files.

---

## 답변

That's just an auth error (no credentials passed), not a connectivity issue — the TCP port is reachable and MySQL responds. Both MySQL and Redis are confirmed listening on `0.0.0.0` and working fully independent of Docker/dockerd, which is exactly the goal.

---

## 답변

That's a minor detail, not worth chasing further. The substantive changes are done.

---

## 답변

**완료했습니다.**

**적용한 변경**
- `/etc/mysql/mysql.conf.d/mysqld.cnf`: `bind-address`를 `127.0.0.1,172.18.0.1` → `0.0.0.0`
- `/etc/redis/redis.conf`: `bind`를 `127.0.0.1 172.18.0.1 -::1` → `0.0.0.0 -::1`
- 두 서비스 재시작 후 `0.0.0.0:3306`/`0.0.0.0:6379`로 리슨 확인, **dockerd가 꺼진 상태에서도** 정상 응답하는 것까지 검증했습니다.

**문서 반영**
요청하신 4개(`docs/environment.md`, `docs/cloud-architecture.md`, `docs/software/mysql.md`, `docs/software/redis.md`) 외에, 같은 "Docker를 먼저 켜야 한다"는 낡은 서술이 남아있던 `docs/software/docker.md`, `docs/software/kubernetes.md`도 함께 고쳤습니다(안 고치면 서로 모순되는 문서가 남아서요). 옛 레이스 컨디션 설명은 지우지 않고 "2026-09-07 이전 이력"으로 남겨 왜 지금 이렇게 됐는지 맥락이 보이게 했습니다.

**결과적으로**: 이제 도커/kind를 켜지 않고도 mysql/redis 등 일반 SW 그룹만 올려서 `dstone-boot/bin/startApp.sh`(이미 `-Dspring.profiles.active=wsl`로 `localhost` 접속하도록 되어 있음)로 WSL 네이티브 기동이 가능하고, 필요할 때만 `start-docker.sh`/`start-kube.sh`를 추가로 올려 `k8s` 프로파일 컨테이너 배포로 전환하는 선택적 구조가 완성됐습니다.

git status는 uncommitted 상태로 남겨뒀습니다 — 커밋은 요청하시면 진행하겠습니다.

---

## 질문

음... 지금 로컬 pc에서 kafka로 요청을 보내면 아무 동작이 없어.

---

## 답변

**원인 확인했습니다.** Docker/kind가 지금 꺼져있는 상태(`ip addr`에 `172.18.0.1` 없음)인데, Kafka의 `advertised.listeners`가 그 IP로 하드코딩돼 있어서 그렇습니다.

```
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://172.18.0.1:9092,CONTROLLER://127.0.0.1:9093
```

동작 방식: 클라이언트가 `localhost:9092`(혹은 어디서든)로 **처음 접속**하는 건 성공합니다. 하지만 브로커가 "실제 produce/fetch는 `172.18.0.1:9092`로 해라"라고 메타데이터로 알려주는데, 그 IP가 지금 존재하지 않으니(Docker/kind 꺼짐) 클라이언트는 그 다음 단계에서 영원히 응답을 못 받고 멈춥니다. 방금 직접 재현해봤는데 정확히 이 증상이었습니다(`kafka-console-producer`가 8초 타임아웃까지 아무 반응 없이 걸림).

즉 지금 mysql/redis에서 고친 것과 **똑같은 패턴의 버그**입니다 — kind Pod 전용 IP를 일반 클라이언트 경로에도 강제한 것.

**차이점**: mysql/redis는 `bind`를 `0.0.0.0`으로 넓히면 끝났지만, Kafka의 `advertised.listeners`는 "모든 인터페이스"라는 개념이 없고 클라이언트에게 알려주는 값 하나뿐이라 그렇게는 못 고칩니다. 대신 Kafka 표준 방식대로 **리스너를 용도별로 분리**하면 됩니다:

```
listeners=PLAINTEXT://:9092,DOCKER://:9094,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://127.0.0.1:9092,DOCKER://172.18.0.1:9094,CONTROLLER://127.0.0.1:9093
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,DOCKER:PLAINTEXT,...
```

- `9092`(PLAINTEXT) → 로컬 PC/WSL 네이티브 클라이언트, Kafbat UI, `dstone-boot`의 `wsl` 프로파일용. Docker/kind 상태와 무관하게 항상 동작.
- `9094`(DOCKER) → kind Pod 전용. `dstone-boot`의 `env-k8s.properties`/`k8s/configmap.yaml`에서 `KAFKA_PORT`를 9092→9094로 바꿔줘야 합니다.

이 방향으로 진행할까요? Kafka 재시작이 필요하고(`kafka-stop.sh`→`kafka-start.sh`, sudo 불필요), `dstone-boot`의 k8s 프로파일 설정 파일과 `docs/cloud-architecture.md`/`docs/software/kafka.md` 문서도 같이 손봐야 합니다.

---

## 질문

응 수정해줘

---

## 답변

Kafka came back up with all three listener ports live. Now let's verify the fix actually resolves the produce hang.

---

## 답변

Produce/consume now works instantly on `localhost:9092` — confirmed fixed. Now update `dstone-boot`'s k8s-side config and the docs.

---

## 답변

Now let's fix `docs/software/kafka.md`, which is already stale in more places than just this issue.

---

## 답변

Now update the port table in `docs/environment.md`.

---

## 답변

**완료했습니다.**

**Kafka 설정 변경**
- `/opt/kafka/kafka_2.13-4.2.1/config/server.properties`: 리스너를 `PLAINTEXT`(9092, 로컬 PC/WSL 전용, `127.0.0.1` 광고)와 `DOCKER`(9094, kind Pod 전용, `172.18.0.1` 광고)로 분리
- Kafka 재시작 후 `localhost:9092`로 produce/consume 테스트 → 즉시 성공 확인 (이전엔 타임아웃까지 멈춰있었음)
- `dstone-boot/conf/env-k8s.properties`의 `KAFKA_PORT`를 9092→9094로 변경 (`wsl` 프로파일은 그대로 9092)

**문서 반영**
`docs/cloud-architecture.md`, `docs/software/kafka.md`, `docs/environment.md`를 새 리스너 구성에 맞게 수정했고, 겸사겸사 이미 해결된 사안인데 낡은 채로 남아있던 "알려진 한계" 서술(kafka.md §8, cloud-architecture.md §7)도 같이 바로잡았습니다.

**주의할 점**: `env-k8s.properties`는 Maven 빌드 시점에 WAR 클래스패스에 baked-in 되는 파일이라(ConfigMap 마운트 아님), 이번 변경은 **dstone-boot 이미지를 재빌드/재푸시/재배포해야** 실제 kind Pod에 반영됩니다. 지금은 dockerd 자체가 꺼져있어서 rebuild는 진행하지 않았습니다 — 나중에 kind 쪽 Kafka 연결을 테스트하실 때 이 재배포 단계를 잊지 마세요.

git status는 uncommitted 상태로 남겨뒀습니다 (mysql/redis 변경분 포함 6개 파일 + 이번 4개 파일).
