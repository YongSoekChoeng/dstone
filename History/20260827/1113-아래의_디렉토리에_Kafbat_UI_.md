# 아래의_디렉토리에_Kafbat_UI_

> 생성일시: 2026-08-27 11:13

## 질문

아래의 디렉토리에 Kafbat UI 를 깔고 백그라운드기동(start.sh) 및 중지(stop.sh) 스크립트를 만들어줘.
카프카가 깔려있는 디렉토리는 /opt/kafka/kafka_2.13-4.2.1 야.
/opt/kafka/admin-tools/KafbatUI

---

## 답변

Kafbat UI를 `/opt/kafka/admin-tools/KafbatUI`에 설치하고 시작/중지 스크립트를 만들기 전에, 환경을 먼저 확인하겠습니다.

---

## 답변

v1.5.0의 standalone jar(`api-v1.5.0.jar`, ~142MB)를 찾았습니다. 다운로드하고 설정 및 스크립트를 구성하겠습니다.

---

## 답변

JDK 버전 우려는 타당한 지적입니다. Kafbat UI는 Spring Boot 3.x 기반이라 최소 Java 17 이상을 요구하는데, 현재 서버엔 Java 21이 설치되어 있어 (`openjdk 21.0.12`) 요구사항은 충족됩니다. 다만 실제로 확실히 확인하기 위해 다운로드 완료 후 jar 매니페스트의 요구 버전과 실행 테스트로 검증하겠습니다.

---

## 답변

v1.5.0은 Java 25로 컴파일되어 있어 서버의 Java 21과 호환되지 않습니다 (사용자 말씀이 맞았습니다). Java 21을 명시한 최신 버전은 **v1.4.2**이므로 이것으로 교체하겠습니다.

---

## 답변

다운로드가 백그라운드에서 진행 중입니다. 완료되는 동안 설정 파일과 시작/중지 스크립트를 준비하겠습니다.

---

## 답변

Download complete. Jar 매니페스트에서 실제 클래스 파일 버전을 확인해 Java 21 호환성을 검증하고, 실행 테스트까지 해보겠습니다.

---

## 답변

v1.4.2는 클래스 파일 major version 65 = **Java 21** 바이트코드로 확인됐습니다. 서버의 Java 21과 정확히 일치합니다. 이제 실제로 잠깐 실행해서 정상 기동되는지 검증하겠습니다.

---

## 답변

Kafbat UI 자체는 정상 기동됐고(웹서버 HTTP 200 응답), 로그의 연결 타임아웃은 Kafka 브로커가 현재 꺼져 있어서 발생한 것으로 정상입니다(Kafka를 켜면 자동 재연결됩니다). 이제 stop.sh도 검증하고 정리하겠습니다.

---

## 답변

start.sh / stop.sh 모두 정상 동작 확인했습니다.

**요약**
- `/opt/kafka/admin-tools/KafbatUI/`에 Kafbat UI 설치 완료
  - `kafbat-ui.jar` — **v1.4.2** (Java 21 바이트코드, class major version 65로 확인) — 처음엔 최신 v1.5.0을 받았으나 이건 Java 25 컴파일이라 서버의 Java 21과 안 맞아 v1.4.2로 교체했습니다(말씀하신 우려가 맞았습니다).
  - `conf/application-local.yml` — 포트 8080, Kafka 클러스터 `127.0.0.1:9092` (설치된 KRaft 단일 브로커) 연결
  - `start.sh` / `stop.sh` — 기존 `/opt/kafka/kafka-start.sh`/`kafka-stop.sh`와 동일한 스타일(PID 파일, nohup 백그라운드, 정상종료 대기 후 kill -9 fallback)
- 실제로 start.sh 실행 → 웹서버 HTTP 200 확인 → stop.sh로 정상 종료까지 테스트 완료

Kafka가 켜져 있는 상태에서 `start.sh`를 실행하면 브로커에 정상 연결됩니다. `logs/kafbat-ui.out`에서 로그 확인 가능합니다.
