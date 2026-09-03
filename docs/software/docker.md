# Docker

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법 (실제 수행된 절차)](#3-설치-방법-실제-수행된-절차)
- [4. 서비스 시작/중지](#4-서비스-시작중지)
- [5. 권한](#5-권한)
- [6. dstone 프로젝트에서의 역할](#6-dstone-프로젝트에서의-역할)
- [7. 트러블슈팅](#7-트러블슈팅)

## 1. 개요
컨테이너 런타임. `dstone-boot` 이미지 빌드/로컬 사설 레지스트리 운영 및 [kind](kubernetes.md) 기반 로컬 쿠버네티스 클러스터 구동에 사용한다.

## 2. 설치 정보
- 버전: Docker CE 29.7.2, Docker Compose plugin v5.5.0
- 설치 방식: Docker 공식 apt 저장소 (`download.docker.com`)
- 저장소 등록 파일: `/etc/apt/sources.list.d/docker.sources`
```
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: resolute
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
```

## 3. 설치 방법 (실제 수행된 절차)
```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 현재 사용자를 docker 그룹에 추가 (재로그인 필요)
sudo usermod -aG docker "$USER"
```

설치 확인:
```bash
docker --version
docker compose version
```

## 4. 서비스 시작/중지
WSL 환경 특성상 `dockerd`를 systemd로 자동 기동하지 않고, 전용 스크립트로 백그라운드에서 직접 기동한다.

```bash
/usr/local/bin/docker-start.sh    # nohup dockerd, /home/<user>/.docker/dockerd.log 에 로그, 최대 30초 대기 후 준비 확인
/usr/local/bin/docker-stop.sh     # pgrep dockerd 후 kill
/usr/local/bin/docker-status.sh   # docker info로 실행 여부 및 서버 버전 출력
```
최상위 래퍼: `/usr/local/bin/start-docker.sh` / `stop-docker.sh`.

## 5. 권한
현재 사용자(`jysn007`)는 `docker` 그룹에 속해 있어 `sudo` 없이 `docker` 명령을 사용할 수 있다. 그룹 변경 후에는 재로그인(WSL 재시작)이 필요하다.

## 6. dstone 프로젝트에서의 역할
- `dstone-boot` 이미지 빌드/실행 환경(`dstone-boot/Dockerfile`) — `docker-buildx-plugin`이 있어야 하는 멀티스테이지 빌드(`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`)이며, 로컬 사설 레지스트리(`localhost:5000`)를 거쳐 kind에 배포한다. 빌드 컨텍스트는 반드시 리포지토리 루트(`dstone-common` 소스를 함께 COPY하므로). 상세는 [cloud-architecture.md](../cloud-architecture.md), [../build.md](../build.md#6-dstone-boot--컨테이너-빌드--kind-배포) 참고.
- `dstone-batch`/`dstone-batchadmin`은 Docker 이미지가 아니라 VM 스타일 `bin/*.sh`로 운영한다 — 이 두 모듈에는 Docker가 직접 관여하지 않는다.
- [kind](kubernetes.md) 클러스터의 컨테이너 런타임으로도 사용된다(모든 클러스터 노드/레지스트리가 결국 Docker 컨테이너로 뜬다).

## 7. 트러블슈팅
| 증상 | 원인 | 대처 |
|---|---|---|
| `docker: permission denied while trying to connect to the Docker daemon socket` | 현재 세션이 `docker` 그룹 적용 전에 로그인됨 | `newgrp docker`(현재 셸에만 즉시 적용) 또는 WSL 재시작(`wsl --shutdown` 후 재접속)으로 그룹 재적용 |
| `Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?` | `dockerd`가 떠 있지 않음(WSL은 systemd 자동기동 없음) | `/usr/local/bin/docker-start.sh` 실행 후 `docker info`로 확인 |
| `docker build`가 `buildx`를 못 찾는다는 에러 | `docker-buildx-plugin` 미설치 | `sudo apt install -y docker-buildx-plugin` |
| WSL2에서 `iptables`/네트워크 관련 dockerd 기동 실패 | WSL2 커널의 legacy/nft iptables 백엔드 불일치 | `sudo update-alternatives --config iptables`로 `iptables-legacy` 선택 후 `docker-start.sh` 재시도 |
| `docker build -f dstone-boot/Dockerfile ...`에서 `dstone-common`을 못 찾음 | 빌드 컨텍스트를 `dstone-boot/` 안에서 실행함 | 반드시 리포지토리 루트에서 `-f dstone-boot/Dockerfile ... .` 형태로 실행 |
