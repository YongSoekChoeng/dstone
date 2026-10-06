---
name: software-inventory-docs
description: WSL 개발 환경에 설치된 소프트웨어 목록 문서(`docs/02.environment.md`, `docs/software/*.md`)의 위치와 갱신 규칙. 소프트웨어를 새로 설치하거나 버전·포트·설정·기동 스크립트를 바꿀 때, 이미 설치된 것의 버전·설치 방법·포트를 알아야 할 때, 프로젝트 문서를 새로 만들 때 사용한다.
---

# 설치 소프트웨어 문서

## 어디에 있나

- `docs/02.environment.md` — 전체 목록: 버전, 설치 방법, 포트, 기동 스크립트
- `docs/software/NN.<이름>.md` — 소프트웨어별 설치 절차와 설정 상세
  (jdk, maven, git, mysql, postgresql, redis, rabbitmq, kafka, kafbat-ui, docker, kubernetes, jenkins, nodejs, ollama, ollama-webui-lite)
- `docs/README.md`, `docs/01.index.md` — 문서 전체 안내

## 찾아볼 때

이미 설치된 것의 버전이나 설치 절차를 다시 추측하거나 알아내지 않는다. 이 문서들을 먼저 읽는다. 문서와 실제가 다르면 실제를 확인하고 문서를 고친다.

## 갱신할 때

새 소프트웨어를 설치하거나 설정을 크게 바꾸면 **요청이 없어도** 문서를 갱신한다. 사용자가 2026-09-03에 상시 요청으로 못 박았다.

1. `docs/software/`에 다음 번호로 파일을 추가하거나, 기존 파일을 고친다.
2. `docs/02.environment.md`의 목록(버전·포트·스크립트)을 맞춘다.
3. 같은 내용을 언급한 다른 문서도 찾아서 맞춘다. 예전에 `docker`·`kubernetes` 문서만 낡은 내용으로 남은 적이 있다.
4. 기동/중지 스크립트는 `/usr/local/bin/start-*.sh` / `stop-*.sh` 방식을 따른다 (`no-systemd-app-scripts` skill).

## 문서 위치 규칙

- 프로젝트 문서는 모두 저장소 루트 `docs/`에 둔다. 모듈 안에 `docs/` 폴더를 만들지 않는다.
- 같은 내용을 여러 파일에 중복해서 쓰지 않는다. 한 곳에 쓰고 나머지는 링크한다.
