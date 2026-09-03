# dstone 클라우드 아키텍처 시뮬레이션 (WSL)

dstone 프레임워크를 실제 클라우드 배포와 최대한 유사한 구조로 WSL 환경에서 운용하기 위한 설계 기록이다. 소프트웨어 설치 자체는 `docs/environment.md`/`docs/software/*.md`를 참고하고, 이 문서는 "무엇을 무엇에 대응시켰고 왜 그렇게 했는지"를 정리한다.

## 아키텍처 매핑

| dstone 구성요소 | 클라우드 대응 개념 | WSL 구현 |
|---|---|---|
| MySQL / Redis / RabbitMQ / Kafka | RDS / ElastiCache / Amazon MQ / MSK (매니지드 서비스) | 기존 WSL 설치 그대로, k8s 클러스터 **바깥**에서 네트워크로 접근 |
| dstone-boot | EKS/GKE 위 Deployment (stateless 웹 티어) | 컨테이너화(`dstone-boot/Dockerfile`) 후 로컬 `kind` 클러스터에 Pod로 배포(`dstone-boot/k8s/`) |
| dstone-batch | EC2 (배치 워커 VM) | systemd **미사용**. `dstone-batch/bin/*.sh`로 기동/중지하는 순수 프로세스, 포트 6081 |
| dstone-batchadmin | EC2 (배치 관제/스케줄러 VM) | 동일하게 `dstone-batchadmin/bin/*.sh`, 포트 5081 |
| Jenkins | 자체 관리형 CI 서버(VM 상주) | WSL 호스트에 Controller 상주 유지. 에이전트도 로컬 실행(향후 Kubernetes Plugin으로 에이전트만 kind Pod화하는 것을 다음 단계로 고려) |

MySQL/Redis/RabbitMQ/Kafka를 k8s 클러스터 밖에 두는 이유: 실제 클라우드에서도 RDS/ElastiCache 등은 EKS 클러스터 내부가 아니라 VPC의 별도 관리형 엔드포인트로 존재한다. 이 구조를 그대로 흉내내, "클러스터 안에서 뭘 만들지"와 "클러스터 밖 관리형 서비스에 어떻게 접근할지"를 구분해서 학습할 수 있게 했다.

## dstone-boot ↔ kind 네트워킹

kind 클러스터의 Pod는 `kind` 도커 브리지 네트워크(예: `172.18.0.0/16`) 안에 있고, 호스트의 MySQL/Redis는 원래 `127.0.0.1`에만 바인딩되어 있어 Pod에서 접근할 수 없었다. 이를 해결하기 위해:

1. `docker network inspect kind`로 브리지 게이트웨이 IP(예: `172.18.0.1`, 호스트 인터페이스 `br-xxxx`)를 확인.
2. MySQL(`bind-address`)과 Redis(`bind`)를 `127.0.0.1`에 이 게이트웨이 IP를 추가해 바인딩(`127.0.0.1,172.18.0.1` 형태) — 즉 kind 네트워크에서만 추가로 열어주고 그 외 인터페이스로는 노출하지 않는다. 이 서브넷 자체가 WSL 내부에서만 존재하므로, 실사용 VPC 보안그룹처럼 접근 범위를 좁히는 효과를 낸다(호스트에 `ufw` 등 방화벽 데몬 자체가 없어 별도 규칙은 두지 않았다).
3. RabbitMQ는 기본이 전체 인터페이스 리슨이라 별도 조치 불필요.
4. dstone-boot 컨테이너 이미지에는 k8s 전용 프로파일(`env-k8s.properties`, `-Dspring.profiles.active=k8s`)을 포함시켜 `DB_HOST`/`REDIS_HOST`/`RABBITMQ_HOST`를 이 게이트웨이 IP로 지정했다.

## 로컬 사설 레지스트리

CSP의 ECR/GCR을 흉내내기 위해 `registry:2` 컨테이너(`kind-registry`, `localhost:5000`)를 kind 공식 "local registry" 레시피로 구성했다: kind 클러스터를 `containerdConfigPatches`로 이 레지스트리를 미러로 인식하도록 생성하고, `kind-registry` 컨테이너를 `kind` 도커 네트워크에 연결한다. `docker build → docker push localhost:5000/... → kubectl apply(image: localhost:5000/...)` 흐름이 실제 클라우드의 "빌드 → 레지스트리 푸시 → 클러스터 배포" 흐름과 동일하다. 설정은 `/usr/local/bin/k8s-start.sh`에 멱등적으로 포함되어 있다(자세한 내용은 [kubernetes.md](software/kubernetes.md)).

## dstone-batch / dstone-batchadmin — VM 스타일

systemd로 자동 등록하지 않고, EC2에 SSH로 접속해 배포 스크립트를 수동 실행하는 감각을 재현하기 위해 순수 쉘 스크립트(`bin/startApp.sh`/`stopApp.sh`/`statusApp.sh`)로만 제어한다. 두 앱 모두 같은 WSL 호스트에서 포트로만 분리되어 있다(6081/5081) — 실제 EC2 수준의 네트워크·방화벽 격리는 없지만, "무엇을 언제 어떻게 배포하는지"의 운영 감각(수동 기동/정지, 배포 디렉터리 분리, 롤링 없는 단일 프로세스 재시작)에 집중하기 위한 절충이다.

각 모듈은 배포 대상 환경별로 별도의 `env-*.properties` 프로파일을 classpath에 포함시켜 빌드하고, 실행 시 `-Dspring.profiles.active=<profile>`로 선택한다(`net.dstone.*.DstoneXxxApplication.setSysProperties()`가 이 파일을 읽어 `System.setProperty`로 주입하고, 그중 `APP_CONF_DIR` 값으로 실제 `application.yml`/`log4j2.xml` 위치를 찾는다):

| 프로파일 | 대상 | APP_CONF_DIR |
|---|---|---|
| (기본/local) | Windows 개발자 PC | `D:/AppHome/...` |
| dev | 기존 Docker Compose 배포 | `/workshop/dstone/<module>/conf` (컨테이너 내부) |
| wsl | WSL에서 git 체크아웃 그대로 수동 테스트 | `/app/dstone/<module>/conf` |
| vm | Jenkins CI/CD가 배포하는 안정적 실행 경로 | `/workshop/dstone/<module>/conf` (호스트 디렉터리, `docker`그룹 공유) |

`bin/startApp.sh`는 `DSTONE_PROFILE` 환경변수로 프로파일을 오버라이드할 수 있다(기본값 `wsl`). Jenkinsfile은 `DSTONE_PROFILE=vm`으로 실행한다.

## CI/CD 파이프라인

- `dstone-boot/Jenkinsfile`: Checkout → Maven 리액터 빌드(`dstone-common` 먼저) → `docker build`/`push`(로컬 레지스트리) → `kubectl apply` + `kubectl set image` + `rollout status`(kind 배포) → 헬스체크.
- `dstone-batch/Jenkinsfile`, `dstone-batchadmin/Jenkinsfile`: Checkout → Maven 리액터 빌드 → 아티팩트+conf+bin을 `/workshop/dstone/<module>`로 복사 → 기존 프로세스 정지(`bin/stopApp.sh`) → 재기동(`bin/startApp.sh`, `DSTONE_PROFILE=vm`) → `bin/statusApp.sh`로 헬스체크.
- 기존에는 세 Jenkinsfile 모두 `docker-compose up/down`으로 배포했고, 이미지 배포는 Jenkinsfile 밖의 `docs/docker/*/02.*-docker-reg.sh`가 `docker commit` + Docker Hub 하드코딩 비밀번호로 처리했다 — 이번에 위 구조로 전면 교체했다. `docs/docker/` 아래의 기존 Docker Compose 자산은 참고용으로 남겨뒀지만 더 이상 Jenkins가 사용하지 않는다.
- Jenkins Controller는 계속 WSL 호스트(VM 역할)에 상주한다. `jenkins` 시스템 계정을 `docker` 그룹에 포함시켜 별도 인프라 추가 없이 `docker`/`kubectl`을 실행한다.
- 전제: 각 Jenkins Job의 SCM 체크아웃 범위는 모노레포 루트 전체여야 한다(멀티모듈 리액터 빌드 및 `dstone-boot`의 Docker 빌드 컨텍스트가 루트를 요구하기 때문).

## 알려진 한계 / 후속 과제

- `dstone-boot`은 `spring.kafka.enabled: true`이지만 `bootstrap-servers`가 `localhost:9092`로 하드코딩되어 있어 컨테이너에서는 연결되지 않는다(백그라운드에서 재시도만 계속함). `/actuator/health`(전체) 는 이 때문에 `DOWN`으로 보일 수 있으나, k8s 프로브가 실제로 사용하는 `/actuator/health/readiness`·`/actuator/health/liveness`는 정상적으로 `UP`을 반환하므로 배포 자체에는 영향 없다. Kafka를 dstone-boot에 실제로 연동하게 되면 `bootstrap-servers`를 환경변수화해야 한다.
- `dstone-boot/conf/application.yml`의 `sftp.password`가 평문으로 하드코딩되어 있음 — 이번 작업 범위 밖이라 손대지 않았지만 별도로 정리가 필요하다.
- `dstone-boot` NodePort 서비스는 kind 클러스터가 `extraPortMappings` 없이 생성되어 있어 호스트에서 바로 접속하려면 `kubectl port-forward`가 필요하다. 호스트 포트로 직접 노출하려면 kind 클러스터를 `extraPortMappings` 설정과 함께 재생성해야 한다.
