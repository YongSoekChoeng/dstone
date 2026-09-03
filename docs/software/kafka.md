# Apache Kafka (KRaft 모드)

## 개요
로컬 메시징 실습/개발용 단일 브로커 Kafka. Zookeeper 없이 KRaft(Kafka Raft) 모드로 broker+controller 역할을 한 프로세스에서 수행한다. apt 패키지가 아닌 공식 tar.gz 배포판을 `/opt/kafka`에 수동 설치했다.

## 설치 정보
- 버전: Kafka 4.2.1 (Scala 2.13 빌드, `kafka_2.13-4.2.1`)
- 설치 방식: 수동 설치 (Apache 공식 배포 tar.gz)
- 설치 경로: `/opt/kafka/kafka_2.13-4.2.1`
- 데이터 디렉터리: `/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs`

## 설치 방법 (실제 수행된 절차)
```bash
sudo mkdir -p /opt/kafka
sudo chown "$USER":"$USER" /opt/kafka
cd /opt/kafka

wget https://downloads.apache.org/kafka/4.2.1/kafka_2.13-4.2.1.tgz
tar -xzf kafka_2.13-4.2.1.tgz
rm kafka_2.13-4.2.1.tgz
cd kafka_2.13-4.2.1

# KRaft 스토리지 초기화 (최초 1회)
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
bin/kafka-storage.sh format --standalone -t "$KAFKA_CLUSTER_ID" -c config/server.properties
```

## 주요 설정
설정 파일: `/opt/kafka/kafka_2.13-4.2.1/config/server.properties`
```properties
process.roles=broker,controller
node.id=1
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://127.0.0.1:9092,CONTROLLER://127.0.0.1:9093
log.dirs=/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs
```

## 서비스 시작/중지
전용 스크립트를 작성해 사용 중이다 (systemd 미등록, 수동 실행).

- `/opt/kafka/kafka-start.sh`: `nohup bin/kafka-server-start.sh config/server.properties`로 백그라운드 기동, PID를 `kafka-server.pid`에 기록. 이미 실행 중이면 중복 실행 방지.
- `/opt/kafka/kafka-stop.sh`: PID 파일 기준 `kill` (정상 종료 최대 30초 대기 후 `kill -9` 강제 종료), 없으면 `kafka-server-stop.sh` 사용.
- `/usr/local/bin/start-kafka.sh` / `stop-kafka.sh`: 위 두 스크립트 + [Kafbat UI](kafbat-ui.md) 시작/중지까지 한 번에 처리하는 최상위 스크립트.

```bash
/usr/local/bin/start-kafka.sh
/usr/local/bin/stop-kafka.sh
```

로그: `/opt/kafka/kafka_2.13-4.2.1/logs/kafka-server.out`

## 동작 확인
```bash
cd /opt/kafka/kafka_2.13-4.2.1/bin
./kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092
./kafka-topics.sh --list --bootstrap-server localhost:9092
./kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092
./kafka-console-consumer.sh --topic test-topic --bootstrap-server localhost:9092 --from-beginning
```

## 관리 콘솔
[Kafbat UI](kafbat-ui.md)를 통해 웹에서 토픽/컨슈머 등을 확인한다 (http://localhost:9099).

## dstone 프로젝트에서의 역할
현재 dstone 각 모듈의 `application.yml`에서 직접 연동되어 있지는 않으며, 메시징 기능 실습/향후 연동을 위한 로컬 브로커로 준비되어 있다. 실제 프로젝트 연동 시 이 문서와 `docs/environment.md`를 갱신한다.
