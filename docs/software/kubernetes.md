# Kubernetes 로컬 개발 환경 (kubectl + kind)

## 개요
[Docker](docker.md) 위에서 동작하는 로컬 쿠버네티스 클러스터. `kind`(Kubernetes IN Docker)로 클러스터를 구성하고 `kubectl`로 제어한다. 쿠버네티스 배포/운영 실습 및 향후 dstone 컴포넌트 컨테이너 오케스트레이션 검토용으로 설치되어 있다.

## 설치 정보
| 도구 | 버전 | 설치 경로 |
|---|---|---|
| kubectl | v1.37.0 (client) | `/usr/local/bin/kubectl` |
| kind | v0.27.0 | `/usr/local/bin/kind` |

둘 다 apt 패키지가 아닌 공식 릴리즈 바이너리를 직접 다운로드해 설치했다.

## 설치 방법 (실제 수행된 절차)
```bash
# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/kubectl

# kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-linux-amd64
chmod +x kind
sudo mv kind /usr/local/bin/kind
```

설치 확인:
```bash
kubectl version --client
kind version
```

## 클러스터 구성
- 클러스터명: `dev` (환경변수 `KIND_CLUSTER_NAME`으로 오버라이드 가능, 기본값 `dev`)
- kubeconfig 경로: `/etc/kind/dev.config` (환경변수 `KUBECONFIG`으로 오버라이드 가능)
- `~/.bashrc`에 `export KUBECONFIG=/etc/kind/dev.config` 등록되어 있어 별도 `--kubeconfig` 옵션 없이 `kubectl` 사용 가능.
- `/etc/kind` 디렉터리는 `docker` 그룹 소유로 group-readable (`chmod g+r`) 되어 있어 `docker` 그룹 사용자면 kubeconfig를 읽을 수 있다.

## 서비스(클러스터) 시작/중지
전용 스크립트로 docker 데몬 기동 확인 → kind 클러스터 생성/재사용까지 자동화되어 있다.

```bash
/usr/local/bin/start-kube.sh   # -> /usr/local/bin/k8s-start.sh
/usr/local/bin/stop-kube.sh    # -> /usr/local/bin/k8s-stop.sh
```

`k8s-start.sh` 동작:
1. 현재 사용자가 `docker` 그룹인지 확인 (아니면 에러 종료)
2. `docker info` 실패 시 `sudo systemctl start docker` 후 최대 30초 대기
3. `kind get clusters`에 `dev`가 없으면 `kind create cluster --name dev --kubeconfig /etc/kind/dev.config`로 신규 생성 (있으면 기존 컨테이너 재사용)
4. `kubectl get nodes`로 노드 준비 상태 확인

`k8s-stop.sh` 동작:
- 기본: docker 데몬만 정지 (`sudo systemctl stop docker`), kind 클러스터 컨테이너는 유지되어 다음 `start-kube.sh` 실행 시 자동 복원됨
- `k8s-stop.sh --delete`: kind 클러스터까지 완전 삭제 후 docker 데몬 정지

## 동작 확인
```bash
kubectl get nodes
kind get clusters
```

## dstone 프로젝트에서의 역할
현재 dstone 배포 파이프라인(Jenkinsfile)은 Docker Compose 기반이며 쿠버네티스 매니페스트는 아직 없다. 이 환경은 향후 쿠버네티스 전환 검토/실습용 로컬 클러스터다.
