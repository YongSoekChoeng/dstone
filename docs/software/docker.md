# Docker

## 개요
컨테이너 런타임. dstone-boot/dstone-batch의 Docker Compose 배포 스크립트(`docs/docker/`) 실행 및 [kind](kubernetes.md) 기반 로컬 쿠버네티스 클러스터 구동에 사용한다.

## 설치 정보
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

## 설치 방법 (실제 수행된 절차)
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

## 서비스 시작/중지
WSL 환경 특성상 `dockerd`를 systemd로 자동 기동하지 않고, 전용 스크립트로 백그라운드에서 직접 기동한다.

```bash
/usr/local/bin/docker-start.sh    # nohup dockerd, /home/<user>/.docker/dockerd.log 에 로그, 최대 30초 대기 후 준비 확인
/usr/local/bin/docker-stop.sh     # pgrep dockerd 후 kill
/usr/local/bin/docker-status.sh   # docker info로 실행 여부 및 서버 버전 출력
```
최상위 래퍼: `/usr/local/bin/start-docker.sh` / `stop-docker.sh`.

## 권한
현재 사용자(`jysn007`)는 `docker` 그룹에 속해 있어 `sudo` 없이 `docker` 명령을 사용할 수 있다. 그룹 변경 후에는 재로그인(WSL 재시작)이 필요하다.

## dstone 프로젝트에서의 역할
- `dstone-boot/docs/docker/`, `dstone-batch/docs/docker/`의 Docker Compose 배포 스크립트 실행 환경.
- `dstone-batchadmin`은 아직 Docker 배포 스크립트가 추가되지 않은 상태 (CLAUDE.md 참고).
- [kind](kubernetes.md) 클러스터의 컨테이너 런타임으로도 사용된다.
