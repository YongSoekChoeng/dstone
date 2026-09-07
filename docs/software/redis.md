# Redis

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 주요 설정](#5-주요-설정)
- [6. 동작 확인](#6-동작-확인)
- [7. GUI 도구](#7-gui-도구)
- [8. dstone 프로젝트에서의 역할](#8-dstone-프로젝트에서의-역할)

## 1. 개요
`dstone-boot`의 분산 세션 저장소(`dstone:session` 네임스페이스) 및 캐시 용도로 사용하는 인메모리 데이터 저장소.

## 2. 설치 정보
- 버전: Redis 8.0.5
- 설치 방식: Ubuntu 공식 저장소 apt 패키지 (`redis`, `redis-server`, `redis-tools`)
- 서비스명: `redis-server.service` (systemd, 부팅 시 자동시작은 비활성화되어 있어 수동 기동)
- 설정 파일: `/etc/redis/redis.conf`

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y redis
```

설치 확인:
```bash
redis-server --version
redis-cli --version
```

## 4. 서비스 시작/중지
```bash
/usr/local/bin/start-redis.sh
/usr/local/bin/stop-redis.sh
```

`start-redis.sh`:
```sh
#!/bin/sh

# 이전 실행에서 StartLimitBurst에 걸려 failed 상태로 남아있으면 start가 거부되므로 먼저 리셋
sudo systemctl reset-failed redis-server.service >/dev/null 2>&1

sudo systemctl start redis-server

for i in 1 2 3 4 5; do
    if systemctl is-active --quiet redis-server.service; then
        echo "Redis started !!! For realtime administration, Use Tool /DB/Tools/Redis-Desktop-Manager/Version/Another Redis Desktop Manager.exe"
        exit 0
    fi
    sleep 1
done

echo "Redis FAILED to start. Check: sudo systemctl status redis-server.service / sudo journalctl -xeu redis-server.service" >&2
exit 1
```

`redis-server.service`는 `disabled`라 WSL 부팅 시 자동으로 뜨지 않는다. **(2026-09-07 이전 이력)** 한때 `bind`에 `172.18.0.1`([5절](#5-주요-설정) 참고)이 포함되어 있어서, Docker/kind 네트워크가 뜨기 전에 redis를 먼저 켜면 `bind: Cannot assign requested address`로 기동이 실패하는 레이스 컨디션이 있었다 — 그래서 한동안 `~/start.sh`는 반드시 Docker/kind를 먼저 올린 뒤 redis를 올리도록 순서가 고정되어 있었다. 지금은 `bind`를 `0.0.0.0`으로 바꿔 이 제약 자체가 사라졌다(상세: [environment.md 5.1절](../environment.md#51-개발환경-시작-startsh), [5절](#5-주요-설정)) — Docker/kind 기동 여부와 무관하게 redis만 독립적으로 켜고 끌 수 있다. 2026-09-06 이전 버전은 무조건 "Redis started !!!"를 출력했는데, 재시도 간격이 너무 짧아 `StartLimitBurst`(기본 5회/10초)에 걸려 기동이 완전히 실패했는데도 성공한 것처럼 보이는 문제가 있었다. 지금은 mysql([mysql.md 4절](mysql.md#4-서비스-시작중지))과 동일한 패턴 — `reset-failed` → `start` → 최대 5초 `is-active` 재확인 — 으로 정확한 성공/실패를 출력한다.

`stop-redis.sh`:
```sh
sudo systemctl stop redis-server
echo "Redis stopped !!!"
```

## 5. 주요 설정
- 포트: 6379 (기본값)
- `systemd` unit이 `redis-server.service`로 `/etc/redis/redis.conf`를 `--supervised systemd` 모드로 구동한다.
- **(2026-09-07 변경)** `bind` 설정은 원래 기본값(`127.0.0.1 -::1`)에 `172.18.0.1`(kind 도커 브리지 게이트웨이 IP)만 추가한 `127.0.0.1 172.18.0.1 -::1` 형태였다. `kind` 클러스터의 Pod에서 접속하려면 이 게이트웨이 IP가 필요했기 때문인데(— [cloud-architecture.md](../cloud-architecture.md) 참고), 이 IP는 Docker/kind가 떠야만 존재해서 redis를 Docker보다 먼저 켜면 바인딩에 실패하는 순서 제약이 생겼다. 이를 없애기 위해 `bind 0.0.0.0 -::1`(모든 IPv4 인터페이스)로 바꿨다 — Docker/kind 기동 여부와 무관하게 redis가 항상 뜰 수 있고, 나중에 게이트웨이 IP가 새로 생겨도 재시작 없이 바로 접근된다. 대신 Windows 호스트 등 그 외 인터페이스로도 소켓 자체는 열리는데, WSL 네트워크가 Windows NAT 뒤의 사설 환경이라 실질적 노출 차이는 크지 않다고 보고 감수했다.
- **`protected-mode no`로 변경 필요**: 바인딩 범위를 특정 IP로 좁히든(과거) `0.0.0.0`으로 넓히든(현재), loopback이 아닌 곳에서 오는 연결은 Redis의 `protected-mode`(기본값 `yes`)가 "비밀번호(`requirepass`) 없는 상태"라는 이유로 자체적으로 거부한다(`DENIED Redis is running in protected mode...`). 그래서 `/etc/redis/redis.conf`에서 `protected-mode no`로 바꾸고 `sudo systemctl restart redis-server`로 반영해야 한다.
  ```bash
  sudo sed -i 's/^protected-mode yes/protected-mode no/' /etc/redis/redis.conf
  sudo systemctl restart redis-server
  ```
- 인증(`requirepass`)은 로컬 개발환경 특성상 미설정 상태. 외부 노출 시 반드시 설정할 것 — `requirepass`를 설정하면 `dstone-boot`의 `spring.data.redis.password`(현재 `application.yml`에 없음)도 함께 추가해야 접속이 유지된다.

## 6. 동작 확인
```bash
redis-cli ping                       # PONG
redis-cli set foo bar && redis-cli get foo   # bar
```
`dstone-boot`을 기동한 뒤 로그인/세션이 생기는 API를 한 번 호출하고 나서 아래 명령으로 세션이 실제로 Redis에 저장되는지 확인할 수 있다(네임스페이스는 `dstone:session`).
```bash
redis-cli keys "dstone:session:*"
```

## 7. GUI 도구
`start-redis.sh` 실행 시 안내되는 대로, Windows 쪽 `Another Redis Desktop Manager`로 접속해 실시간 확인 가능 (`localhost:6379`).

## 8. dstone 프로젝트에서의 역할
`dstone-boot`의 Spring Session 저장소로 사용되며, Redis 기반 분산 세션 구성(`dstone:session` 네임스페이스)에 필요하다. 접속 정보는 `conf/env.properties`의 `REDIS_HOST`/`REDIS_PORT`로 주입된다.
