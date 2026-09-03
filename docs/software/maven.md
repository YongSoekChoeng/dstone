# Apache Maven

## 목차

- [1. 개요](#1-개요)
- [2. 설치 정보](#2-설치-정보)
- [3. 설치 방법](#3-설치-방법)
- [4. 환경 변수](#4-환경-변수)
- [5. 설정](#5-설정)
- [6. dstone 프로젝트에서의 역할 (CLAUDE.md 기준 빌드 명령)](#6-dstone-프로젝트에서의-역할-claudemd-기준-빌드-명령)

## 1. 개요
dstone 전 모듈의 빌드 도구. 루트 및 각 모듈(`dstone-common`, `dstone-boot`, `dstone-batch`, `dstone-batchadmin`)의 `pom.xml` 빌드/패키징에 사용한다.

## 2. 설치 정보
- 버전: Apache Maven 3.9.12
- 설치 방식: Ubuntu 공식 저장소 apt 패키지
- Maven Home: `/usr/share/maven`

## 3. 설치 방법
```bash
sudo apt update
sudo apt install -y maven
```

설치 확인:
```bash
mvn -version
```

## 4. 환경 변수
`~/.bashrc`에 아래와 같이 등록되어 있다.
```bash
export MAVEN_HOME=/usr/share/maven
export PATH=$PATH:$MAVEN_HOME/bin
```

## 5. 설정
별도 `~/.m2/settings.xml` 커스터마이즈 없이 기본 설정(Maven Central) 그대로 사용 중이다. 사내/사설 리포지토리를 추가하게 되면 이 문서에 `settings.xml` 경로와 mirror 설정을 追加 기록한다.

## 6. dstone 프로젝트에서의 역할 (CLAUDE.md 기준 빌드 명령)
```bash
# dstone-common을 가장 먼저 빌드해야 함 (다른 모듈이 의존)
cd dstone-common && mvn clean install

cd dstone-boot && mvn clean package        # WAR
cd dstone-batch && mvn clean package       # JAR
cd dstone-batchadmin && mvn clean package  # WAR

# 루트에서 전체 빌드
mvn clean install

# 테스트 스킵
mvn clean package -DskipTests
```
