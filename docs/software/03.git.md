# Git

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 설정](#4-설정)
- [5. dstone 소스 코드 받기](#5-dstone-소스-코드-받기)

## 1. 개요
dstone 소스 코드 형상관리 도구.

## 2. 설치 정보
- 버전: 2.53.0
- 설치 방식: Ubuntu 공식 저장소 apt 패키지

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y git
```

설치 확인:
```bash
git --version
```

## 4. 설정
사용자별 `user.name`/`user.email` 등은 `git config --global`로 각자 환경에서 설정한다.
```bash
git config --global user.name "본인 이름"
git config --global user.email "본인 이메일"
```
별도의 사내 Git 서버/인증 설정이 추가되면 이 문서에 기록한다.

## 5. dstone 소스 코드 받기
Git 설치 후 가장 먼저 할 일은 리포지토리를 받는 것이다. 원격 저장소는 `git@github.com:YongSoekChoeng/dstone.git`(SSH)이다.

```bash
# SSH 키가 GitHub에 등록되어 있는 경우 (권장)
git clone git@github.com:YongSoekChoeng/dstone.git /app/dstone

# 또는 HTTPS (읽기 전용/최초 확인용, push 시 자격 증명 별도 필요)
git clone https://github.com/YongSoekChoeng/dstone.git /app/dstone
```
- 모노레포 구조라 서브모듈이나 별도 sparse-checkout 없이 루트 전체를 그대로 클론하면 된다 — `dstone-boot`의 Docker 빌드 컨텍스트와 Maven 리액터 빌드(`-am`)가 `dstone-common`을 포함한 루트 전체를 요구하기 때문(자세한 이유는 [../build.md](../build.md), [../cloud-architecture.md](../cloud-architecture.md) 참고).
- SSH 접속을 처음 설정하는 경우: `ssh-keygen -t ed25519 -C "본인 이메일"`로 키를 생성하고 `~/.ssh/id_ed25519.pub` 내용을 GitHub 계정의 SSH Keys에 등록한 뒤 `ssh -T git@github.com`으로 인증을 확인한다.
- Jenkins가 같은 호스트에서 이 저장소를 로컬 경로(`file:///app/dstone`)로 직접 체크아웃하는 방식도 쓰인다 — [jenkins.md](jenkins.md), [../cloud-architecture.md](../cloud-architecture.md#52-git-저장소-연결-방법-scm-설정값) 참고.
- 클론 후 다음 단계는 [JDK](jdk.md) → [Maven](maven.md) 설치 → [MySQL](mysql.md#6-dstone-데이터베이스사용자스키마-초기화-설치-후-필수)에서 데이터베이스 초기화 → `mvn clean install` 빌드 순서다. 전체 흐름은 [../build.md](../build.md) 참고.
