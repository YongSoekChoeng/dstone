# RabbitMQ

## 개요
`dstone-boot`에서 사용하는 메시지 큐 브로커(AMQP 0.9.1).

## 설치 정보
- 버전: RabbitMQ Server 4.0.5
- 설치 방식: Ubuntu 공식 저장소 apt 패키지 (내부적으로 Erlang/OTP 런타임 의존성 포함)
- 서비스명: `rabbitmq-server.service` (systemd, 부팅 시 자동시작은 비활성화되어 있어 수동 기동)

## 설치 방법
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

## 서비스 시작/중지
```bash
/usr/local/bin/start-rabbitmq.sh   # sudo systemctl start rabbitmq-server
/usr/local/bin/stop-rabbitmq.sh    # sudo systemctl stop rabbitmq-server
```

## 접속 정보
- AMQP 포트: 5672
- 관리 콘솔: http://localhost:15672 (기본 계정 `guest`/`guest`, localhost에서만 허용됨에 유의)

> `rabbitmqctl`은 Erlang 쿠키(`~/.erlang.cookie`) 권한 문제로 일반 사용자 계정에서 직접 실행하면 오류가 날 수 있다. `sudo rabbitmqctl ...` 형태로 실행할 것.

## dstone 프로젝트에서의 역할
`dstone-boot`의 메시징 연동에 사용된다. 접속 정보는 `conf/env.properties`의 `RABBITMQ_HOST`/`RABBITMQ_PORT`로 주입된다.
