---
name: no-systemd-app-scripts
description: dstone 앱 프로세스의 기동/중지를 systemd가 아닌 `bin/startApp.sh`·`stopApp.sh`·`statusApp.sh` 셸 스크립트로 관리하는 규칙. 모듈이나 서비스의 시작/중지/상태 확인 수단을 추가·수정할 때, 부팅 시 자동 시작이나 `systemctl`/unit 파일을 떠올릴 때 사용한다.
---

# systemd 대신 수동 셸 스크립트

WSL에서 돌리는 앱 프로세스는 systemd 서비스로 등록하지 않는다. unit 파일도, `systemctl enable`도, 부팅 시 자동 시작도 만들지 않는다.

## 왜

클라우드 VM 워크로드를 흉내 내는 방식을 정할 때 사용자가 systemd 안을 명시적으로 거절했다. 이 환경의 모든 것은 사람이 직접, 의도적으로 켜고 끈다. Kafka·Docker 등 인프라도 `/usr/local/bin/start-*.sh` / `stop-*.sh`로 같은 방식을 쓴다.

## 이렇게 한다

- 각 모듈의 `bin/` 아래 일반 셸 스크립트를 쓴다: `startApp.sh` / `stopApp.sh` / `statusApp.sh` (nohup + PID 파일).
- 현재 `dstone-batch`, `dstone-batchadmin`, `dstone-ai-engine`에 세 스크립트가 있고, `dstone-boot`에는 `startApp.sh`/`stopApp.sh`가 있다.
- 새 모듈이나 서비스에는 **기존 스크립트를 본떠서** 만든다. 새로운 방식을 설계하지 않는다. 기존 스크립트에 문제가 있으면 그것을 고친다.
- 대상 환경은 `DSTONE_PROFILE` 환경 변수로 고른다 (`conf/env-<profile>.properties`).
- Jenkins 배포도 같은 스크립트를 부른다 (`stopApp.sh` → `startApp.sh`, `DSTONE_PROFILE=vm`).

## 하지 않는 것

- systemd를 다시 제안하지 않는다.
- 인프라용 스크립트를 새로 추가하면 문서도 맞춘다 (`software-inventory-docs` skill).
