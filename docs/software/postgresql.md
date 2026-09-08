# PostgreSQL

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 접속](#5-접속)
- [6. dstone 프로젝트에서의 역할](#6-dstone-프로젝트에서의-역할)

## 1. 개요
로컬 개발/실습용으로 설치된 관계형 데이터베이스. **(2026-09-08 변경)** `dstone-ai-engine`의 RAG(Phase 2) VectorStore(pgvector)가 사용하기 시작했다 - 그 외 모듈(`dstone-boot`/`dstone-batch`/`dstone-batchadmin`)은 여전히 MySQL만 쓴다.

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

`dstone_ai` 전용 계정으로 접속하려면:
```bash
psql -h 127.0.0.1 -p 5432 -U dstone_ai -d dstone_ai
```

## 6. dstone 프로젝트에서의 역할
`dstone-ai-engine`의 RAG(Phase 2) VectorStore가 이 인스턴스를 쓴다 - `net.dstone.ai.rag.ingest.DocumentIngestService`가 청크로 쪼갠 문서를 [Ollama](ollama.md)로 임베딩한 뒤 pgvector 테이블에 저장하고, `net.dstone.ai.rag.retrieval.RetrievalService`가 유사도 검색을 한다. 전용 롤/DB/확장을 아래처럼 한 번 생성해뒀다(최초 1회, `postgres` 슈퍼유저 권한 필요):

```bash
sudo -u postgres psql <<'SQL'
CREATE ROLE dstone_ai LOGIN PASSWORD '<비밀번호>';
CREATE DATABASE dstone_ai OWNER dstone_ai;
SQL
sudo -u postgres psql -d dstone_ai -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

- 롤/DB: `dstone_ai` / `dstone_ai` (비밀번호는 `dstone-ai-engine/conf/application.yml`의 `spring.datasource.password`에 프로젝트 컨벤션대로 Jasypt `ENC(...)`로 암호화해 저장 - "Sensitive Config Encryption" 참고)
- 접속 정보(`DB_HOST`/`DB_PORT`)는 `dstone-ai-engine/conf/env*.properties`로 주입(다른 모듈의 MySQL과 동일한 패턴)
- 테이블(`vector_store`)은 `spring.ai.vectorstore.pgvector.initialize-schema: true`로 앱이 최초 기동 시 자동 생성한다 - 별도 스키마 SQL 파일 없음
- **k8s(kind) 미배포**: mysql/redis와 달리 아직 kind 브리지 게이트웨이(`172.18.0.1`)로 열어주는 작업은 하지 않았다. `dstone-ai-engine`을 실제로 kind에 배포하기 전에 `listen_addresses`/`pg_hba.conf`를 mysql.md/redis.md와 같은 방식으로 조정해야 한다.
