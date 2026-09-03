# JDK (OpenJDK 21)

## 1. 개요
dstone 전 모듈(dstone-common/boot/batch/batchadmin)의 실행/빌드에 사용하는 Java 런타임 및 개발 키트. Spring Boot 3.5는 Java 17 이상이 필요하며, 본 환경은 Java 21(LTS)을 사용한다.

## 2. 설치 정보
- 버전: OpenJDK 21.0.12 (build 21.0.12+8-1-26.04-Ubuntu)
- 설치 방식: Ubuntu 공식 저장소 apt 패키지
- 설치 경로: `/usr/lib/jvm/java-21-openjdk-amd64`

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
```

설치 확인:
```bash
java -version
javac -version
update-alternatives --list java
```

## 4. 환경 변수
별도로 `JAVA_HOME`을 `.bashrc`에 지정하지 않고, `apt`가 등록한 `update-alternatives` 심볼릭 링크(`/usr/bin/java`)를 그대로 사용 중이다. 필요 시 아래처럼 명시적으로 지정할 수 있다.
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

## 5. dstone 프로젝트에서의 역할
모든 모듈(`pom.xml`)의 컴파일/실행 대상 JDK. `dstone-boot`(WAR, 7081), `dstone-batch`(JAR, 6081), `dstone-batchadmin`(WAR, 7082) 실행 시 `java -jar` 명령의 런타임으로 사용된다.
