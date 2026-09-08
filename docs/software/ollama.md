# Ollama

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법 (실제 수행된 절차)](#3-설치-방법-실제-수행된-절차)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 동작 확인](#5-동작-확인)
- [6. dstone 프로젝트에서의 역할](#6-dstone-프로젝트에서의-역할)

## 1. 개요
`dstone-ai-engine`의 RAG(Phase 2) 임베딩 전용 로컬 모델 런타임. Anthropic(Claude, 채팅용)은 임베딩 생성 API를 제공하지 않고, 이 환경엔 OpenAI API 키도 없어서 로컬에서 무료로 돌릴 수 있는 임베딩 모델을 위해 설치했다 - 채팅(Claude) 자체와는 무관하다. apt 패키지가 아닌 공식 tar.zst 배포판을 `/opt/ollama`에 수동 설치했고, kafka와 동일하게 systemd에 등록하지 않았다.

## 2. 설치 정보
- 버전: v0.33.3 (Linux amd64)
- 설치 방식: 수동 설치 (공식 GitHub Releases tar.zst)
- 설치 경로: `/opt/ollama`
- 모델 데이터 디렉터리: `/opt/ollama/data` (`OLLAMA_MODELS` 환경변수로 지정 - 홈 디렉터리 기본 경로 대신 kafka의 데이터 디렉터리처럼 설치 경로 안에 자체 포함시킴)
- 설치된 모델: `bge-m3` (다국어/한국어 지원 임베딩 모델, 1024차원)

## 3. 설치 방법 (실제 수행된 절차)
최신 릴리스는 `.tgz`가 아니라 `.tar.zst`로 배포되므로 `zstd` 패키지가 먼저 필요하다.
```bash
sudo apt-get update -y
sudo apt-get install -y zstd

sudo mkdir -p /opt/ollama
sudo chown "$USER":"$USER" /opt/ollama
cd /opt/ollama

curl -L -o ollama.tar.zst \
  "https://github.com/ollama/ollama/releases/download/v0.33.3/ollama-linux-amd64.tar.zst"
tar --zstd -xf ollama.tar.zst
rm ollama.tar.zst
# -> /opt/ollama/bin/ollama, /opt/ollama/lib/ollama/* 생성됨

mkdir -p /opt/ollama/data /opt/ollama/logs
```

설치 확인:
```bash
/opt/ollama/bin/ollama --version
```

모델 pull(서버가 떠 있어야 함, [4절](#4-서비스-시작중지) 참고):
```bash
/opt/ollama/bin/ollama pull bge-m3
```

## 4. 서비스 시작/중지
전용 스크립트를 작성해 사용 중이다 (systemd 미등록, 수동 실행 - kafka와 동일한 PID파일 방식). 최상위 래퍼(`~/start.sh`/`~/stop.sh`가 호출):

```sh
# /usr/local/bin/start-ollama.sh
/opt/ollama/ollama-start.sh
echo "Ollama started !!! (dstone-ai-engine RAG 임베딩 전용 로컬 모델 런타임, http://localhost:11434)"
```
```sh
# /usr/local/bin/stop-ollama.sh
/opt/ollama/ollama-stop.sh
echo "Ollama stopped !!!"
```

- `/opt/ollama/ollama-start.sh`: `OLLAMA_MODELS=/opt/ollama/data nohup bin/ollama serve`로 백그라운드 기동, PID를 `ollama-server.pid`에 기록. 이미 실행 중이면 중복 실행 방지. 기본 포트 11434는 `127.0.0.1`에만 바인딩된다(`OLLAMA_HOST` 미지정) - mysql/redis처럼 kind 브리지 게이트웨이(`172.18.0.1`)로 열어주는 건 `dstone-ai-engine`을 실제로 kind에 배포하는 시점에 처리한다.
- `/opt/ollama/ollama-stop.sh`: PID 파일 기준 `kill` (정상 종료 최대 30초 대기 후 `kill -9` 강제 종료).

로그: `/opt/ollama/logs/ollama-server.out`

## 5. 동작 확인
```bash
curl http://localhost:11434/api/tags   # 설치된 모델 목록(JSON)
```
```bash
curl http://localhost:11434/api/embed -d '{"model": "bge-m3", "input": "테스트 문장입니다."}'
```

## 6. dstone 프로젝트에서의 역할
`dstone-ai-engine`의 `spring.ai.model.embedding: ollama`(`conf/application.yml`)가 이 서버를 가리킨다(`spring.ai.ollama.base-url: http://localhost:11434`, `spring.ai.ollama.embedding.model: bge-m3`). `net.dstone.ai.rag.ingest.DocumentIngestService`가 문서를 청크로 쪼갠 뒤 이 임베딩 모델로 벡터화해서 [PostgreSQL(pgvector)](postgresql.md#6-dstone-프로젝트에서의-역할)에 저장하고, `net.dstone.ai.rag.retrieval.RetrievalService`/`ChatController`의 RAG-증강 채팅이 검색 시에도 동일한 모델로 질의를 벡터화한다. `dstone.ai.rag.enabled=false`(또는 미설정)면 이 서버가 없어도 `dstone-ai-engine`은 그대로 기동된다.
