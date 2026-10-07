---
name: cloud-architecture-wsl
description: WSL 개발 환경을 클라우드 배포 형태로 흉내 낸 dstone 구조와, 그 과정에서 얻은 운영상 함정 모음. 배포·Dockerfile·k8s 매니페스트·kind·Jenkinsfile·`env-*.properties` 프로파일·MySQL/Redis/Kafka 접속 문제를 다룰 때, Pod에 설정 변경이 반영되지 않거나 Kafka 클라이언트가 멈출 때 사용한다.
---

# WSL 위의 클라우드 아키텍처 시뮬레이션

WSL 개발 환경을 클라우드 배포 모양 그대로 만들어 직접 다뤄 보는 것이 목적이다. **전체 설계와 현재 상태는 `docs/04.cloud-architecture.md`가 기준**이다. 작업 전에 먼저 읽는다. 이 문서는 거기서 놓치기 쉬운 점만 추린 것이다.

## 구조

| 대상 | 역할 | 실행 방식 |
|---|---|---|
| `dstone-boot`, `dstone-ai-engine` | 컨테이너 워크로드 | `kind` 클러스터의 Pod (namespace `dstone`, 로컬 레지스트리 `localhost:5000`) |
| `dstone-batch`, `dstone-batchadmin` | VM형 워크로드 | WSL 호스트에서 `bin/*.sh` 스크립트로 제어 (포트 6081/5081) |
| MySQL / Redis / RabbitMQ / Kafka | CSP 관리형 서비스 역할 | 클러스터 밖, WSL 호스트 |
| Jenkins | VM/온프레미스 역할 | WSL 호스트 |

- systemd는 쓰지 않는다 (`no-systemd-app-scripts` skill).
- 별도 배포 디렉터리(`/workshop`)는 없다. 모든 앱 홈은 `/app/dstone` 아래다.
- 문서는 저장소 루트 `docs/` 한 곳에만 둔다. 모듈 안이나 `dstone/docs/` 같은 하위 폴더를 제안하지 않는다.

## 헷갈리기 쉬운 점

### `APP_HOME` 기준이 모듈마다 다르다 — 둘 다 맞다
- `dstone-boot`: 저장소 루트 `/app/dstone`
- `dstone-batch`/`dstone-batchadmin`의 `vm` 프로파일: 모듈 디렉터리 `/app/dstone/<module>`

한쪽에 맞춰 "고치지" 않는다.

### `env-k8s.properties`는 빌드할 때 WAR 안에 들어간다
`application.yml`/`log4j2.xml`처럼 ConfigMap으로 마운트되는 것이 아니다. 고친 뒤 Pod만 재시작하면 아무 변화가 없다. **이미지 빌드 → push → 재배포**까지 해야 반영된다.

### 이미지 태그는 빌드마다 새로 붙인다
`imagePullPolicy: IfNotPresent`에서 같은 태그(`:latest` 등)를 다시 쓰면 kind 노드가 예전 캐시 이미지를 계속 쓴다. 빌드마다 고유 태그를 붙이고 `kubectl set image`로 바꾼다 (Jenkinsfile이 `${BUILD_NUMBER}`로 하는 방식).

### `dstone-boot`는 실행 방식이 두 가지다
- `bin/startApp.sh` + `wsl` 프로파일: WSL에서 바로 실행, 전부 `localhost`, Docker 불필요
- `k8s` 프로파일: Pod로 실행, kind 게이트웨이 IP 사용

### kind NodePort
클러스터에 `extraPortMappings`가 없어서 `kubectl port-forward`가 필요하다.

## 인프라 접속 문제: 먼저 의심할 것

kind Pod가 docker 브리지 너머의 호스트 인프라에 닿아야 하는 지점에서 같은 종류의 문제가 반복된다. **"지금 존재하지 않는 주소"에 묶여 있지 않은지**부터 본다.

- **MySQL / Redis**: `0.0.0.0`으로 바인드한다. kind 게이트웨이 IP(`172.18.0.1`)를 직접 적으면 Docker/kind가 먼저 떠 있어야만 기동되는 순서 의존이 생긴다.
- **Kafka**: 리스너를 둘로 나눈다. `advertised.listeners`는 바인드 범위가 아니라 클라이언트에게 알려 주는 주소라서 `0.0.0.0` 같은 해법이 없다.
  - `9092` (PLAINTEXT, `127.0.0.1` 광고): 로컬 PC, WSL 네이티브 클라이언트, Kafbat UI, `wsl` 프로파일
  - `9094` (DOCKER, `172.18.0.1` 광고): kind Pod 전용 (`env-k8s.properties`의 `KAFKA_PORT`)
  - 증상: 처음 연결은 되는데 produce/fetch가 끝없이 멈춘다 → `server.properties`의 `advertised.listeners`를 먼저 확인한다.

## 문서

인프라나 배포 구조를 바꾸면 `docs/04.cloud-architecture.md`, `docs/02.environment.md`, 해당 `docs/software/*.md`를 함께 고친다. 예전 설정을 바꾼 이유는 지우지 말고 날짜를 붙인 "이전 이력"으로 남긴다. 남은 한계는 `docs/04.cloud-architecture.md`의 "알려진 한계" 절에 있다.
