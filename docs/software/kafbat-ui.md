# Kafbat UI

## 개요
[Kafka](kafka.md) 클러스터를 웹에서 조회/관리하기 위한 관리 콘솔(Kafbat UI, 구 kafka-ui 커뮤니티 포크). Spring Boot 기반 실행 가능 jar로 배포된다.

## 설치 정보
- 배포 형태: 실행 가능 jar (`kafbat-ui.jar`)
- 설치 방식: 수동 설치 (릴리즈 jar 다운로드/배치)
- 설치 경로: `/opt/kafka/admin-tools/KafbatUI`

## 설치 방법 (재현 절차)
```bash
mkdir -p /opt/kafka/admin-tools/KafbatUI
cd /opt/kafka/admin-tools/KafbatUI

# Kafbat UI GitHub 릴리즈에서 실행 가능 jar 다운로드
# https://github.com/kafbat/kafka-ui/releases 에서 최신 kafbat-ui-api-*.jar를 받아
# kafbat-ui.jar 이름으로 배치한다.
```
> 참고: 최초 설치 시 사용한 정확한 다운로드 URL은 셸 히스토리에 남아있지 않다. 재설치/버전 업그레이드 시에는 위 릴리즈 페이지에서 최신 jar를 받아 동일 경로에 교체하면 된다.

## 설정
설정 파일: `/opt/kafka/admin-tools/KafbatUI/conf/application-local.yml`
```yaml
server:
  port: 9099

kafka:
  clusters:
    - name: local
      bootstrapServers: 127.0.0.1:9092
```

## 서비스 시작/중지
```bash
/opt/kafka/admin-tools/KafbatUI/start.sh
/opt/kafka/admin-tools/KafbatUI/stop.sh
```
- `start.sh`는 `setsid nohup java -jar kafbat-ui.jar --spring.config.additional-location=conf/application-local.yml`로 tty와 완전히 분리되어 백그라운드 기동되며, PID를 `kafbat-ui.pid`에 기록한다.
- `/usr/local/bin/start-kafka.sh` / `stop-kafka.sh`가 Kafka 브로커 기동/중지와 함께 이 스크립트도 같이 호출한다.

로그: `/opt/kafka/admin-tools/KafbatUI/logs/kafbat-ui.out`

## 접속
http://localhost:9099

## dstone 프로젝트에서의 역할
[Kafka](kafka.md) 로컬 브로커 운영/확인용 부가 도구. dstone 애플리케이션 자체와 직접적인 런타임 의존관계는 없다.
