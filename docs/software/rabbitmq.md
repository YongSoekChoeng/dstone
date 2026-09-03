# RabbitMQ

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 접속 정보](#5-접속-정보)
- [6. dstone용 가상호스트/사용자 구성 (설치 후 필수)](#6-dstone용-가상호스트사용자-구성-설치-후-필수)
- [7. dstone 프로젝트에서의 역할](#7-dstone-프로젝트에서의-역할)

## 1. 개요
`dstone-boot`에서 사용하는 메시지 큐 브로커(AMQP 0.9.1).

## 2. 설치 정보
- 버전: RabbitMQ Server 4.0.5
- 설치 방식: Ubuntu 공식 저장소 apt 패키지 (내부적으로 Erlang/OTP 런타임 의존성 포함)
- 서비스명: `rabbitmq-server.service` (systemd, 부팅 시 자동시작은 비활성화되어 있어 수동 기동)

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y rabbitmq-server
```

관리 콘솔(웹 UI) 플러그인 활성화:
```bash
sudo rabbitmq-plugins enable rabbitmq_management
```

설치 확인:
```bash
sudo rabbitmqctl status
sudo rabbitmqctl version
```

## 4. 서비스 시작/중지
```bash
/usr/local/bin/start-rabbitmq.sh   # sudo systemctl start rabbitmq-server
/usr/local/bin/stop-rabbitmq.sh    # sudo systemctl stop rabbitmq-server
```

## 5. 접속 정보
- AMQP 포트: 5672
- 관리 콘솔: http://localhost:15672 (기본 계정 `guest`/`guest`, localhost에서만 허용됨에 유의)

> `rabbitmqctl`은 Erlang 쿠키(`~/.erlang.cookie`) 권한 문제로 일반 사용자 계정에서 직접 실행하면 오류가 날 수 있다. `sudo rabbitmqctl ...` 형태로 실행할 것.

## 6. dstone용 가상호스트/사용자 구성 (설치 후 필수)

`dstone-boot/conf/application.yml`의 `spring.rabbitmq.virtual-host`가 기본(`/`)이 아닌 **`/dstone-mq`**로 고정되어 있다(`spring.rabbitmq.host`/`port`/`username`/`password`와 함께 [ConfigMq.java](../../dstone-boot/src/main/java/net/dstone/boot/common/config/ConfigMq.java)가 읽어 `ConnectionFactory`를 구성한다) — RabbitMQ를 apt로 설치한 상태 그대로는 이 vhost가 없어 애플리케이션 기동 시 커넥션 자체가 실패한다. 최초 1회 아래처럼 vhost와 계정을 만들어야 한다.

```bash
sudo rabbitmqctl add_vhost /dstone-mq

# 기본 guest 계정은 localhost 접속만 허용되므로(원격/Pod 환경에서는 사용 불가), 별도 계정을 만드는 것을 권장
sudo rabbitmqctl add_user dstone dstone123          # 사용자명/비밀번호는 예시 — 원하는 값으로 변경 가능
sudo rabbitmqctl set_permissions -p /dstone-mq dstone ".*" ".*" ".*"   # configure/write/read 모두 허용
sudo rabbitmqctl set_user_tags dstone management     # 관리 콘솔(15672) 로그인 허용(선택)
```
- `application.yml`의 `spring.rabbitmq.username`/`password`는 Jasypt `ENC(...)` 값이라 이 문서만으로 원래 평문을 알 수 없다 — 위 예시처럼 **새 계정/비밀번호를 만들었다면 그 평문을 [mysql.md 7절](mysql.md#7-비밀번호jasypt-enc-다루기)의 방법으로 `ENC(...)`로 암호화**해 `spring.rabbitmq.username`/`password`에 채워 넣어야 한다. (RabbitMQ가 로컬호스트에서만 붙는 개발 환경이라면, 대신 `guest` 계정에 `/dstone-mq` 권한만 추가로 부여하고(`sudo rabbitmqctl set_permissions -p /dstone-mq guest ".*" ".*" ".*"`) `application.yml`의 username/password를 `guest`로 암호화해 넣는 방법이 더 간단하다.)
- **큐/익스체인지는 직접 만들 필요가 없다.** `app.notifications.queue`(fanout, `app.fanout.exchange`)와 `app.orders.queue`(direct, `app.direct.exchange`, routing-key `orders.process`)는 `ConfigMq`가 Spring AMQP `Queue`/`FanoutExchange`/`DirectExchange`/`Binding` 빈으로 선언해두어 `dstone-boot` 기동 시 RabbitMQ의 내장 `RabbitAdmin`이 자동으로 선언(declare)한다 — vhost와 계정 권한만 맞으면 나머지는 애플리케이션이 알아서 만든다.
- 확인: 관리 콘솔(http://localhost:15672)에서 좌측 상단 vhost를 `/dstone-mq`로 전환한 뒤 `dstone-boot` 기동 후 **Queues** 탭에 `app.notifications.queue`/`app.orders.queue`가 나타나는지 확인한다. CLI로는 `sudo rabbitmqctl list_queues -p /dstone-mq name messages`.

## 7. dstone 프로젝트에서의 역할
`dstone-boot`의 메시징 연동(알림 fanout 발행/구독, 주문 처리 큐)에 사용된다. 접속 정보는 `conf/env.properties`의 `RABBITMQ_HOST`/`RABBITMQ_PORT`와 `application.yml`의 `spring.rabbitmq.virtual-host`/`username`/`password`(Jasypt `ENC(...)`)로 구성된다 — vhost/계정 최초 구성은 [6절](#6-dstone용-가상호스트사용자-구성-설치-후-필수) 참고.
