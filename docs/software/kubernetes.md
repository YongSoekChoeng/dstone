# Kubernetes 로컬 개발 환경 (kubectl + kind)

## 1. 개요
[Docker](docker.md) 위에서 동작하는 로컬 쿠버네티스 클러스터. `kind`(Kubernetes IN Docker)로 클러스터를 구성하고 `kubectl`로 제어한다. 쿠버네티스 배포/운영 실습 및 향후 dstone 컴포넌트 컨테이너 오케스트레이션 검토용으로 설치되어 있다.

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

```bash
/usr/local/bin/start-kube.sh   # -> /usr/local/bin/k8s-start.sh
/usr/local/bin/stop-kube.sh    # -> /usr/local/bin/k8s-stop.sh
```

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
`dstone-boot`이 이 클러스터의 `dstone` 네임스페이스에 Deployment/Service/ConfigMap으로 배포된다(매니페스트: `dstone-boot/k8s/`). `dstone-boot/Jenkinsfile`이 이미지를 빌드해 로컬 레지스트리에 푸시한 뒤 `kubectl apply`/`kubectl set image`로 배포한다. 자세한 클라우드 아키텍처 대응 관계는 [cloud-architecture.md](../cloud-architecture.md) 참고.
