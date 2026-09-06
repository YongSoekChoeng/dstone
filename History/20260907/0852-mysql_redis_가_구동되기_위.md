# mysql_redis_가_구동되기_위

> 생성일시: 2026-09-07 08:52

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
