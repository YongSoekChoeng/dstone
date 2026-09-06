# PostgreSQL

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 접속](#5-접속)
- [6. dstone 프로젝트에서의 역할](#6-dstone-프로젝트에서의-역할)

## 1. 개요
로컬 개발/실습용으로 설치된 관계형 데이터베이스. 현재 dstone 각 모듈의 `application.yml`에서는 사용하지 않으며, 필요 시 대체 DB 실습이나 향후 연동을 위해 준비된 상태다.

## 2. 설치 정보
- 버전: PostgreSQL 18.6 (Ubuntu 26.04 공식 패키지)
- 설치 방식: Ubuntu 공식 저장소 apt 패키지
- 서비스명: `postgresql.service` (systemd, 활성화되어 있음)
- 클러스터: `18/main`, 데이터 디렉터리 `/var/lib/postgresql/18/main`, 로그 `/var/log/postgresql/postgresql-18-main.log`

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y postgresql
```

설치 확인:
```bash
psql --version
pg_lsclusters
```

## 4. 서비스 시작/중지
```sh
# /usr/local/bin/start-postgresql.sh
sudo systemctl start postgresql
echo "Postgresql started !!!"
```
```sh
# /usr/local/bin/stop-postgresql.sh
sudo systemctl stop postgresql
echo "Postgresql stopped !!!"
```
`postgresql.service`는 `systemctl is-enabled` 기준 **enabled**라 WSL 부팅 시 이미 떠 있는 경우가 대부분이며, 이 경우 `start-postgresql.sh`는 사실상 no-op이다. 정지는 자동으로 다시 일어나지 않으므로 `stop-postgresql.sh`로 내리면 WSL을 재기동하기 전까지 유지된다.

## 5. 접속
```bash
sudo -u postgres psql
```
- 포트: 5432 (기본값)

## 6. dstone 프로젝트에서의 역할
현재는 직접 연동된 모듈 없음. 실제로 특정 모듈이 PostgreSQL을 사용하게 되면 `docs/environment.md`와 이 문서에 데이터소스/스키마 정보를 추가한다.
