# Jenkins

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 서비스 설정](#4-서비스-설정)
- [5. 서비스 시작/중지](#5-서비스-시작중지)
- [6. 초기 설정](#6-초기-설정)
- [7. docker/kubectl 실행 권한](#7-dockerkubectl-실행-권한)
- [8. dstone 프로젝트에서의 역할](#8-dstone-프로젝트에서의-역할)
- [9. Job 생성](#9-job-생성)

## 1. 개요
dstone-boot, dstone-batch의 CI/CD 파이프라인(`Jenkinsfile`)을 실행하는 자동화 서버.

## 2. 설치 정보
- 버전: 2.568.3
- 설치 방식: Jenkins 공식 apt 저장소 (`pkg.jenkins.io`)
- 저장소 등록 파일: `/etc/apt/sources.list.d/jenkins.list`
```
deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/
```
- JENKINS_HOME: `/var/lib/jenkins`
- 실행 WAR: `/usr/share/java/jenkins.war`

## 3. 설치 방법
```bash
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | \
  sudo tee /etc/apt/keyrings/jenkins-keyring.asc > /dev/null

echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" | \
  sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update
sudo apt install -y jenkins
```
Jenkins는 JDK가 필요하므로 [JDK](jdk.md) 설치가 선행되어야 한다.

## 4. 서비스 설정
설정 파일: `/etc/default/jenkins`
```
JENKINS_USER=jenkins
JENKINS_GROUP=jenkins
JENKINS_WAR=/usr/share/java/jenkins.war
JENKINS_HOME=/var/lib/jenkins
HTTP_PORT=8080
JENKINS_ARGS="--webroot=/var/cache/jenkins/war --httpPort=8080"
```
- 포트: 8080
- `jenkins.service`는 systemd에 `enabled` 상태로 등록되어 있다 (다른 서비스와 달리 부팅 시 자동 기동 대상).

## 5. 서비스 시작/중지
```bash
/usr/local/bin/start-jenkins.sh   # sudo systemctl start jenkins
/usr/local/bin/stop-jenkins.sh    # sudo systemctl stop jenkins
```

## 6. 초기 설정
```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword   # 최초 관리자 비밀번호
```
브라우저에서 http://localhost:8080 접속 → 위 비밀번호 입력 → 플러그인 설치 화면에서 **"Install suggested plugins"(추천 플러그인 설치)**를 선택하면 충분하다. Git, GitHub, Pipeline(`workflow-aggregator`), SSH Credentials 등 기본 플러그인이 여기에 포함된다. **Docker/Kubernetes 전용 Jenkins 플러그인은 설치할 필요가 없다** — 세 `Jenkinsfile` 모두 `docker`/`kubectl` CLI를 `sh` 스텝으로 직접 호출하는 방식이라 플러그인이 CLI를 대신할 필요가 없기 때문이다. 이후 관리자 계정 생성 마법사를 완료하면 대시보드로 진입한다.

## 7. docker/kubectl 실행 권한
`dstone-boot` 파이프라인이 Jenkins 에이전트(로컬 실행)에서 직접 `docker build/push`, `kubectl apply`를 수행하므로 `jenkins` 시스템 계정이 `docker` 그룹에 속해 있어야 한다.
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins   # 그룹 변경은 프로세스 재시작 후에 적용됨
groups jenkins                    # "jenkins docker" 확인
```
`/etc/kind/dev.config`(KUBECONFIG)도 `docker` 그룹에 read 권한이 열려 있어(`chmod g+r`) 별도 설정 없이 읽을 수 있다. `dstone-batch`/`dstone-batchadmin` 파이프라인은 배포 파일을 별도 디렉터리가 아니라 리포지토리 자기 자신(`/app/dstone/dstone-batch`, `/app/dstone/dstone-batchadmin`)에 직접 복사하므로, `jenkins` 계정이 `jysn007` 그룹(리포지토리 소유 그룹)에도 속해 있어야 하고 두 디렉터리(및 하위 `target`/`conf`/`bin`)에 그룹 쓰기 권한(setgid)이 필요하다 — 없다면 `sudo usermod -aG jysn007 jenkins && sudo systemctl restart jenkins`, `sudo chmod g+ws /app/dstone/dstone-batch /app/dstone/dstone-batchadmin`.

## 8. dstone 프로젝트에서의 역할
- `dstone-boot/Jenkinsfile`: Maven 리액터 빌드 → Docker 이미지 빌드/푸시(로컬 레지스트리) → kind 클러스터에 `kubectl`로 배포
- `dstone-batch/Jenkinsfile`, `dstone-batchadmin/Jenkinsfile`: Maven 리액터 빌드 → 리포지토리 자기 자신의 모듈 디렉터리(`/app/dstone/<module>`)로 복사 → 해당 모듈의 `bin/stopApp.sh`+`bin/startApp.sh`로 재기동(VM 스타일, systemd 미사용)
- 파이프라인 실행에는 [Maven](maven.md), [JDK](jdk.md), [Docker](docker.md), [kubernetes](kubernetes.md)(dstone-boot에 한함)가 함께 필요하다. 설계 배경은 [cloud-architecture.md](../cloud-architecture.md) 참고.

## 9. Job 생성
Jenkins 설치·플러그인 설치까지 끝났다면 실제 파이프라인 Job(`dstone-boot-deploy`, `dstone-batch-deploy`, `dstone-batchadmin-deploy`) 3개를 만들어야 실제 CI/CD가 동작한다. New Item → Pipeline 선택 → SCM(`Pipeline script from SCM`, Git, Repository URL, Script Path) 설정까지 화면 단위로 그대로 따라 할 수 있는 절차는 [cloud-architecture.md 5절](../cloud-architecture.md#5-cicd-파이프라인)에 정리되어 있다 — 이 문서는 소프트웨어 설치/권한까지만 다루고, Job 생성 이후 절차는 그쪽을 참고한다.
