# Redis

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 주요 설정](#5-주요-설정)
- [6. GUI 도구](#6-gui-도구)
- [7. dstone 프로젝트에서의 역할](#7-dstone-프로젝트에서의-역할)

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
/usr/local/bin/start-redis.sh   # sudo systemctl start redis-server
/usr/local/bin/stop-redis.sh    # sudo systemctl stop redis-server
```

## 5. 주요 설정
- 포트: 6379 (기본값)
- `systemd` unit이 `redis-server.service`로 `/etc/redis/redis.conf`를 `--supervised systemd` 모드로 구동한다.
- `bind` 설정을 기본값(`127.0.0.1 -::1`)에서 `127.0.0.1 172.18.0.1 -::1`로 확장해, `kind` 클러스터의 Pod(브리지 게이트웨이 IP `172.18.0.1`)에서도 접속할 수 있도록 했다 — [cloud-architecture.md](../cloud-architecture.md) 참고. `protected-mode yes`는 유지.
- 인증(`requirepass`)은 로컬 개발환경 특성상 미설정 상태. 외부 노출 시 반드시 설정할 것.

## 6. GUI 도구
`start-redis.sh` 실행 시 안내되는 대로, Windows 쪽 `Another Redis Desktop Manager`로 접속해 실시간 확인 가능 (`localhost:6379`).

## 7. dstone 프로젝트에서의 역할
`dstone-boot`의 Spring Session 저장소로 사용되며, Redis 기반 분산 세션 구성(`dstone:session` 네임스페이스)에 필요하다. 접속 정보는 `conf/env.properties`의 `REDIS_HOST`/`REDIS_PORT`로 주입된다.
