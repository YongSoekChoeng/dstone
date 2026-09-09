# Kafbat UI

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법 (재현 절차)](#3-설치-방법-재현-절차)
- [4. 설정](#4-설정)
- [5. 서비스 시작/중지](#5-서비스-시작중지)
- [6. 접속](#6-접속)
- [7. dstone 프로젝트에서의 역할](#7-dstone-프로젝트에서의-역할)

## 1. 개요
[Kafka](kafka.md) 클러스터를 웹에서 조회/관리하기 위한 관리 콘솔(Kafbat UI, 구 kafka-ui 커뮤니티 포크). Spring Boot 기반 실행 가능 jar로 배포된다.

## 2. 설치 정보
- 배포 형태: 실행 가능 jar (`kafbat-ui.jar`)
- 설치 방식: 수동 설치 (릴리즈 jar 다운로드/배치)
- 설치 경로: `/opt/kafka/admin-tools/KafbatUI`

## 3. 설치 방법 (재현 절차)
```bash
mkdir -p /opt/kafka/admin-tools/KafbatUI
cd /opt/kafka/admin-tools/KafbatUI

# GitHub Releases API로 최신 릴리즈의 실행 가능 jar(assets 중 api-*.jar) 다운로드 URL을 조회해서 받는다
JAR_URL="$(curl -s https://api.github.com/repos/kafbat/kafka-ui/releases/latest \
  | grep -oE '"browser_download_url":\s*"[^"]*api[^"]*\.jar"' \
  | grep -oE 'https://[^"]+' | head -1)"
wget -O kafbat-ui.jar "$JAR_URL"
```
- `https://github.com/kafbat/kafka-ui/releases`에서 직접 최신 버전을 확인하고 특정 버전을 고정해 받고 싶다면 `.../releases/latest`를 `.../releases/tags/<tag>`로 바꾼다.
- 재설치/업그레이드 시에는 위 명령을 다시 실행해 `kafbat-ui.jar`를 덮어쓰면 된다(서비스 재시작 필요: [5절](#5-서비스-시작중지) 참고).

## 4. 설정
설정 파일: `/opt/kafka/admin-tools/KafbatUI/conf/application-local.yml`
```yaml
server:
  port: 9099

kafka:
  clusters:
    - name: local
      bootstrapServers: 127.0.0.1:9092
```

## 5. 서비스 시작/중지
```bash
/opt/kafka/admin-tools/KafbatUI/start.sh
/opt/kafka/admin-tools/KafbatUI/stop.sh
```
- `start.sh`는 `setsid nohup java -jar kafbat-ui.jar --spring.config.additional-location=conf/application-local.yml`로 tty와 완전히 분리되어 백그라운드 기동되며, PID를 `kafbat-ui.pid`에 기록한다.
- `/usr/local/bin/start-kafka.sh` / `stop-kafka.sh`가 Kafka 브로커 기동/중지와 함께 이 스크립트도 같이 호출한다.

로그: `/opt/kafka/admin-tools/KafbatUI/logs/kafbat-ui.out`

## 6. 접속
http://localhost:9099

## 7. dstone 프로젝트에서의 역할
[Kafka](kafka.md) 로컬 브로커 운영/확인용 부가 도구. dstone 애플리케이션 자체와 직접적인 런타임 의존관계는 없다.
