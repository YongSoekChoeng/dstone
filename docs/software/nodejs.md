# Node.js

## 1. 개요
프런트엔드 빌드 도구/스크립트 실행 등 보조 용도로 설치된 JavaScript 런타임. 현재 dstone 각 모듈 빌드(Maven 기반)에는 직접 포함되어 있지 않다.

## 2. 설치 정보
- 버전: Node.js v20.20.2 (LTS), npm 10.8.2
- 설치 방식: NodeSource 공식 apt 저장소 (`deb.nodesource.com`, Node 20.x 라인)
- 저장소 등록 파일: `/etc/apt/sources.list.d/nodesource.sources`
```
Types: deb
URIs: https://deb.nodesource.com/node_20.x
Suites: nodistro
Components: main
Architectures: amd64
Signed-By: /usr/share/keyrings/nodesource.gpg
```

## 3. 설치 방법
```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

설치 확인:
```bash
node -v
npm -v
```

## 4. dstone 프로젝트에서의 역할
현재 직접적인 런타임 의존관계는 없음. 프런트엔드 도구 체인(lint/번들러 등)이 추가되면 이 문서와 `docs/environment.md`를 갱신한다.
