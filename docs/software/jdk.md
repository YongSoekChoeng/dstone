# JDK (OpenJDK 21)

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 환경 변수](#4-환경-변수)
- [5. dstone 프로젝트에서의 역할](#5-dstone-프로젝트에서의-역할)
- [6. 트러블슈팅](#6-트러블슈팅)

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
모든 모듈(`pom.xml`)의 컴파일/실행 대상 JDK. `dstone-boot`(WAR, 7081), `dstone-batch`(JAR, 6081), `dstone-batchadmin`(WAR, 5081) 실행 시 `java -jar` 명령의 런타임으로 사용된다.

## 6. 트러블슈팅
- **여러 JDK가 공존하는 환경**: `update-alternatives --list java`로 후보를 확인하고 `sudo update-alternatives --config java`로 21을 기본으로 선택한다. IDE(Eclipse/IntelliJ)에서 별도 프로젝트 JDK를 지정하는 경우 IDE 설정도 `/usr/lib/jvm/java-21-openjdk-amd64`로 맞춰야 한다.
- **`mvn clean package`가 "invalid target release: 21" 등으로 실패**: `mvn -version`의 "Java version" 값이 21 미만이면 발생 — [maven.md 5절](maven.md#5-설정)의 JDK 버전 일치 항목 참고.
