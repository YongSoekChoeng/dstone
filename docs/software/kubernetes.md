# Kubernetes 로컬 개발 환경 (kubectl + kind)

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법 (실제 수행된 절차)](#3-설치-방법-실제-수행된-절차)
- [4. 클러스터 구성](#4-클러스터-구성)
- [5. 서비스(클러스터) 시작/중지](#5-서비스클러스터-시작중지)
- [6. 로컬 사설 레지스트리 (dstone-boot 이미지 배포용)](#6-로컬-사설-레지스트리-dstone-boot-이미지-배포용)
- [7. 동작 확인](#7-동작-확인)
- [8. dstone 프로젝트에서의 역할](#8-dstone-프로젝트에서의-역할)
- [9. 호스트 포트로 직접 노출하고 싶다면 (`extraPortMappings`)](#9-호스트-포트로-직접-노출하고-싶다면-extraportmappings)
- [10. 트러블슈팅](#10-트러블슈팅)

## 1. 개요
[Docker](docker.md) 위에서 동작하는 로컬 쿠버네티스 클러스터. `kind`(Kubernetes IN Docker)로 클러스터를 구성하고 `kubectl`로 제어한다. 쿠버네티스 배포/운영 실습 및 향후 dstone 컴포넌트 컨테이너 오케스트레이션 검토용으로 설치되어 있다.

> 전제 조건: [Docker](docker.md)가 먼저 설치되어 있고 `docker` 그룹 권한이 적용된 상태여야 한다(`kind`는 노드를 Docker 컨테이너로 띄운다). WSL2는 최소 4GB 이상의 메모리 할당(`.wslconfig`)을 권장 — 리소스가 부족하면 `kind create cluster`가 노드 컨테이너의 kubelet/컨트롤플레인 기동 단계에서 타임아웃으로 실패할 수 있다.

## 2. 설치 정보
| 도구 | 버전 | 설치 경로 |
|---|---|---|
| kubectl | v1.37.0 (client) | `/usr/local/bin/kubectl` |
| kind | v0.27.0 | `/usr/local/bin/kind` |

둘 다 apt 패키지가 아닌 공식 릴리즈 바이너리를 직접 다운로드해 설치했다.

## 3. 설치 방법 (실제 수행된 절차)
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

## 4. 클러스터 구성
- 클러스터명: `dev` (환경변수 `KIND_CLUSTER_NAME`으로 오버라이드 가능, 기본값 `dev`)
- kubeconfig 경로: `/etc/kind/dev.config` (환경변수 `KUBECONFIG`으로 오버라이드 가능)
- `~/.bashrc`에 `export KUBECONFIG=/etc/kind/dev.config` 등록되어 있어 별도 `--kubeconfig` 옵션 없이 `kubectl` 사용 가능.
- `/etc/kind` 디렉터리는 `docker` 그룹 소유로 group-readable (`chmod g+r`) 되어 있어 `docker` 그룹 사용자면 kubeconfig를 읽을 수 있다.

## 5. 서비스(클러스터) 시작/중지
전용 스크립트로 docker 데몬 기동 확인 → kind 클러스터 생성/재사용까지 자동화되어 있다.

최상위 래퍼(`~/start.sh`/`~/stop.sh`가 호출):
```sh
# /usr/local/bin/start-kube.sh
/usr/local/bin/k8s-start.sh
echo "Kubenetes started !!!"
```
```sh
# /usr/local/bin/stop-kube.sh
/usr/local/bin/k8s-stop.sh
echo "Kubenetes stopped !!!"
```

**(2026-09-07 이전 이력)** 이 클러스터([Docker](docker.md) 위에서 동작)가 뜨는 시점에 `kind` 브리지 네트워크(`172.18.0.1`)가 생기는데, 한때는 이 IP가 있어야만 MySQL/Redis가 바인딩에 성공해서 `~/start.sh`가 이 스크립트를 [MySQL](mysql.md#4-서비스-시작중지)/[Redis](redis.md#4-서비스-시작중지)보다 반드시 먼저 실행해야 했다. MySQL/Redis의 바인딩을 `0.0.0.0`으로 바꾼 뒤로는 이 순서 제약이 사라져, kind/Docker 없이도 MySQL/Redis만 독립적으로 기동할 수 있다 — 상세: [environment.md 5.1절](../environment.md#51-개발환경-시작-startsh).

`k8s-start.sh` 동작:
1. 현재 사용자가 `docker` 그룹인지 확인 (아니면 에러 종료)
2. `docker info` 실패 시 `sudo systemctl start docker` 후 최대 30초 대기
3. 로컬 사설 레지스트리 컨테이너(`kind-registry`, `registry:2`, 호스트 포트 5000)가 없으면 생성
4. `kind get clusters`에 `dev`가 없으면, 위 레지스트리를 `containerdConfigPatches`로 미러 등록하는 kind config와 함께 `kind create cluster --name dev --kubeconfig /etc/kind/dev.config`로 신규 생성 (있으면 기존 컨테이너 재사용 — 기존 클러스터는 이미 이 설정을 포함하고 있으므로 별도 조치 불필요)
5. `kind-registry` 컨테이너를 `kind` 도커 네트워크에 연결(아직 연결 안 돼 있으면)
6. `kubectl get nodes`로 노드 준비 상태 확인

## 6. 로컬 사설 레지스트리 (dstone-boot 이미지 배포용)
- kind 공식 "local registry" 레시피 적용: `docker build` → `docker push localhost:5000/<image>` → Pod가 `localhost:5000/<image>`를 그대로 `image:`에 지정하면 클러스터 노드의 containerd가 `kind-registry:5000`으로 라우팅해서 pull한다.
- 클러스터를 수동으로 재생성해야 한다면(`kind delete cluster --name dev` 후) 반드시 `k8s-start.sh`를 통해 재생성하거나, 그 안의 `containerdConfigPatches` kind config를 그대로 사용해야 레지스트리 미러가 다시 인식된다 (`kind create cluster`만 단독으로 실행하면 미러 설정이 빠진다).
- 확인: `docker network inspect kind`에 `kind-registry` 컨테이너가 보여야 하고, `kubectl get pods -A`에서 이미지 pull 이벤트가 `localhost:5000/...`을 정상적으로 가져오는지 `kubectl describe pod`로 확인.

`k8s-stop.sh` 동작:
- 기본: docker 데몬만 정지 (`sudo systemctl stop docker`), kind 클러스터 컨테이너는 유지되어 다음 `start-kube.sh` 실행 시 자동 복원됨
- `k8s-stop.sh --delete`: kind 클러스터까지 완전 삭제 후 docker 데몬 정지

## 7. 동작 확인
```bash
kubectl get nodes
kind get clusters
```

## 8. dstone 프로젝트에서의 역할
`dstone-boot`이 이 클러스터의 `dstone` 네임스페이스에 Deployment/Service/ConfigMap으로 배포된다(매니페스트: `dstone-boot/k8s/`, Service는 `NodePort`로 `nodePort: 30081` ↔ `port: 7081` 매핑). `dstone-boot/Jenkinsfile`이 이미지를 빌드해 로컬 레지스트리에 푸시한 뒤 `kubectl apply`/`kubectl set image`로 배포한다. 실제 배포/조회/재시작/롤백 등 운영 명령 전체 목록은 [cloud-architecture.md 6절](../cloud-architecture.md#6-kubectl-운영-명령-dstone-boot) 참고.

## 9. 호스트 포트로 직접 노출하고 싶다면 (`extraPortMappings`)
현재 `k8s-start.sh`가 만드는 클러스터는 `extraPortMappings` 없이 생성되어 있어, 호스트에서 `http://localhost:30081`처럼 NodePort로 바로 접속할 수 없다 — [cloud-architecture.md 6.7절](../cloud-architecture.md#67-애플리케이션-접속-호스트--pod)에 정리된 대로 `kubectl port-forward`가 유일한 접근 경로다. `kubectl port-forward` 없이 `localhost:30081`로 바로 붙고 싶다면, 클러스터를 아래 설정을 포함해 재생성하면 된다(**주의: 클러스터 재생성은 그 안의 모든 리소스를 지운다** — 재생성 후 [cloud-architecture.md 6.3절](../cloud-architecture.md#63-최초-배포--전체-재적용)의 매니페스트를 다시 `apply`해야 함).

```bash
/usr/local/bin/stop-kube.sh --delete   # 기존 클러스터 완전 삭제

cat > /tmp/kind-config-with-ports.yaml <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5000"]
      endpoint = ["http://kind-registry:5000"]
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30081
        hostPort: 30081
        protocol: TCP
EOF

kind create cluster --name dev --kubeconfig /etc/kind/dev.config --config /tmp/kind-config-with-ports.yaml
docker network connect kind kind-registry   # 이미 연결돼 있다면 에러 무시
```
- `containerdConfigPatches`는 [3절](#3-설치-방법-실제-수행된-절차) `k8s-start.sh`가 자동으로 넣어주던 것과 동일한 내용이다 — 수동 재생성 시 반드시 함께 넣어야 로컬 레지스트리 미러가 계속 동작한다.
- 이후 `k8s-start.sh`를 다시 실행하면 "이미 `dev` 클러스터가 있음"으로 인식해 이 설정을 그대로 재사용한다(컨테이너를 지우지 않는 한 유지됨).

## 10. 트러블슈팅
| 증상 | 원인 | 대처 |
|---|---|---|
| `kind create cluster`가 오래 걸리다 타임아웃 | Docker 데몬이 아직 준비 안 됨 / WSL2 메모리 부족 | `docker info`로 데몬 상태 먼저 확인, `.wslconfig`에서 `memory` 값 상향 후 WSL 재시작 |
| `kubectl` 실행 시 `error loading config file "/etc/kind/dev.config": permission denied` | 현재 사용자가 `docker` 그룹이 아니거나 그룹 적용 전 | `groups $USER`로 `docker` 포함 여부 확인, 안 되어 있으면 [docker.md 5절](docker.md#5-권한) 참고 |
| `kubectl get pods`에서 `ImagePullBackOff`/`ErrImagePull` (`localhost:5000/...`) | `kind-registry`가 `kind` 네트워크에 연결 안 됨, 또는 이미지를 push하지 않음 | `docker network inspect kind`로 `kind-registry` 존재 확인, `docker push localhost:5000/<image>:<tag>` 재실행 |
| `kind get clusters`엔 `dev`가 있는데 `kubectl`이 아무 응답 없음 | 컨트롤플레인 컨테이너가 멈춰있거나 크래시 | `docker ps -a`로 `dev-control-plane` 컨테이너 상태 확인, 필요 시 `/usr/local/bin/stop-kube.sh --delete` 후 `start-kube.sh`로 재생성 |
