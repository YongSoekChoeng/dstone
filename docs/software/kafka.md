# Apache Kafka (KRaft 모드)

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법 (실제 수행된 절차)](#3-설치-방법-실제-수행된-절차)
- [4. 주요 설정](#4-주요-설정)
- [5. 서비스 시작/중지](#5-서비스-시작중지)
- [6. 동작 확인](#6-동작-확인)
- [7. 관리 콘솔](#7-관리-콘솔)
- [8. dstone 프로젝트에서의 역할](#8-dstone-프로젝트에서의-역할)

## 1. 개요
로컬 메시징 실습/개발용 단일 브로커 Kafka. Zookeeper 없이 KRaft(Kafka Raft) 모드로 broker+controller 역할을 한 프로세스에서 수행한다. apt 패키지가 아닌 공식 tar.gz 배포판을 `/opt/kafka`에 수동 설치했다.

## 2. 설치 정보
- 버전: Kafka 4.2.1 (Scala 2.13 빌드, `kafka_2.13-4.2.1`)
- 설치 방식: 수동 설치 (Apache 공식 배포 tar.gz)
- 설치 경로: `/opt/kafka/kafka_2.13-4.2.1`
- 데이터 디렉터리: `/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs`

## 3. 설치 방법 (실제 수행된 절차)
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

## 4. 주요 설정
설정 파일: `/opt/kafka/kafka_2.13-4.2.1/config/server.properties`
```properties
process.roles=broker,controller
node.id=1
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://127.0.0.1:9092,CONTROLLER://127.0.0.1:9093
log.dirs=/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs
```

## 5. 서비스 시작/중지
전용 스크립트를 작성해 사용 중이다 (systemd 미등록, 수동 실행).

- `/opt/kafka/kafka-start.sh`: `nohup bin/kafka-server-start.sh config/server.properties`로 백그라운드 기동, PID를 `kafka-server.pid`에 기록. 이미 실행 중이면 중복 실행 방지.
- `/opt/kafka/kafka-stop.sh`: PID 파일 기준 `kill` (정상 종료 최대 30초 대기 후 `kill -9` 강제 종료), 없으면 `kafka-server-stop.sh` 사용.
- `/usr/local/bin/start-kafka.sh` / `stop-kafka.sh`: 위 두 스크립트 + [Kafbat UI](kafbat-ui.md) 시작/중지까지 한 번에 처리하는 최상위 스크립트.

```bash
/usr/local/bin/start-kafka.sh
/usr/local/bin/stop-kafka.sh
```

로그: `/opt/kafka/kafka_2.13-4.2.1/logs/kafka-server.out`

## 6. 동작 확인
```bash
cd /opt/kafka/kafka_2.13-4.2.1/bin
./kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092
./kafka-topics.sh --list --bootstrap-server localhost:9092
./kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092
./kafka-console-consumer.sh --topic test-topic --bootstrap-server localhost:9092 --from-beginning
```

### dstone-boot가 실제로 사용하는 토픽
`server.properties`에 `auto.create.topics.enable`을 별도로 끄지 않았으므로(KRaft 기본값 `true`) 아래 토픽들은 `dstone-boot`가 처음 발행/구독할 때 자동 생성된다 — 미리 만들어두지 않아도 동작하지만, 헬스체크/디버깅 시 아래 이름을 알고 있으면 편하다.

| 토픽 | 발행(Producer) | 구독(Consumer) |
|---|---|---|
| `order-events` | `net.dstone.boot.sample.kafka.service.KafkaService.publish()` | 같은 클래스의 `@KafkaListener(topics = "order-events")` |
| `step01-inventoryReserve-reply` | `OutboxRelay`(SAGA 1단계 처리 결과) | `OrderSagaReplyListener` |
| `step02-payment-reply` | `OutboxRelay`(SAGA 2단계 처리 결과) | `OrderSagaReplyListener` |
| `step03-orderConfirm-reply` | `OutboxRelay`(SAGA 3단계 처리 결과) | `OrderSagaReplyListener` |

SAGA/Outbox 흐름 전체는 [dstone-saga.md](../dstone-saga.md) 참고. 수동으로 미리 만들어두고 싶다면:
```bash
for t in order-events step01-inventoryReserve-reply step02-payment-reply step03-orderConfirm-reply; do
  ./kafka-topics.sh --create --topic "$t" --bootstrap-server localhost:9092 --if-not-exists
done
```

## 7. 관리 콘솔
[Kafbat UI](kafbat-ui.md)를 통해 웹에서 토픽/컨슈머 등을 확인한다 (http://localhost:9099).

## 8. dstone 프로젝트에서의 역할
`dstone-boot`의 SAGA + Outbox 패턴 샘플 기능(`net.dstone.common.messaging.saga`, `net.dstone.boot.sample.saga`)이 `OutboxRelay`→`KafkaTemplate`로 이벤트를 발행하고 `OrderSagaReplyListener`가 구독하는 방식으로 사용한다(자세한 흐름: [dstone-saga.md](../dstone-saga.md)). 다만 `dstone-boot/conf/application.yml`의 `spring.kafka.bootstrap-servers`가 `localhost:9092`로 하드코딩돼 있어, kind Pod처럼 로컬호스트가 아닌 환경에서는 연결되지 않는다 — [cloud-architecture.md](../cloud-architecture.md)의 "알려진 한계"에 기록되어 있다.
