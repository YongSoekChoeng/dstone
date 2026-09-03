# Jenkins

## 개요
dstone-boot, dstone-batch의 CI/CD 파이프라인(`Jenkinsfile`)을 실행하는 자동화 서버.

## 설치 정보
- 버전: 2.568.3
- 설치 방식: Jenkins 공식 apt 저장소 (`pkg.jenkins.io`)
- 저장소 등록 파일: `/etc/apt/sources.list.d/jenkins.list`
```
deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/
```
- JENKINS_HOME: `/var/lib/jenkins`
- 실행 WAR: `/usr/share/java/jenkins.war`

## 설치 방법
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

## 서비스 설정
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

## 서비스 시작/중지
```bash
/usr/local/bin/start-jenkins.sh   # sudo systemctl start jenkins
/usr/local/bin/stop-jenkins.sh    # sudo systemctl stop jenkins
```

## 초기 설정
```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword   # 최초 관리자 비밀번호
```
브라우저에서 http://localhost:8080 접속 후 플러그인 설치 마법사를 진행한다. Git, Pipeline, Docker 관련 플러그인이 필요하다.

## dstone 프로젝트에서의 역할 (CLAUDE.md 기준)
- `dstone-batch/Jenkinsfile`: `mvn clean package -DskipTests` 빌드 후 Docker Compose로 배포
- `dstone-boot/Jenkinsfile`: 동일 패턴
- 파이프라인 실행에는 [Maven](maven.md), [JDK](jdk.md), [Docker](docker.md)가 함께 필요하다.
