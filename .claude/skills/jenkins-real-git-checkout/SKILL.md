---
name: jenkins-real-git-checkout
description: dstone의 Jenkins/CI 체크아웃을 로컬 `file://` 경로나 보안 우회 플래그가 아닌 실제 GitHub 원격에서 받도록 하는 규칙. Jenkins Job의 SCM 설정, Jenkinsfile의 체크아웃 단계, CI 체크아웃 오류를 설계하거나 고칠 때 사용한다.
---

# Jenkins는 실제 git 원격에서 체크아웃

CI는 진짜 GitHub 원격에서 소스를 내려받아 빌드한다.

- 원격: `git@github.com:YongSoekChoeng/dstone.git` (`origin`), 브랜치 `main`
- 인증: `jenkins` 계정의 SSH 키 → GitHub Deploy Key → Jenkins "SSH Username with private key" Credential

## 왜

Jenkins Git 플러그인이 `file:///app/dstone` 체크아웃을 막았을 때, `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`로 우회하자는 제안을 사용자가 받아들이지 않고 "git으로부터 내려받아서 이후 빌드 진행"하는 쪽을 골랐다. 기술적으로는 둘 다 `git fetch`지만, 사용자가 신경 쓰는 것은 **어느 소스를 쓰느냐**다.

## 이렇게 한다

- Job의 Repository URL은 GitHub 원격 + SSH Credential로 잡는다.
- `file://` 경로와 보안 우회 플래그는 잠깐 원인을 확인하는 용도로만 쓴다. 최종 상태로 남기지 않는다.
- 체크아웃은 **모노레포 루트 전체**로 한다. `mvn -pl <module> -am` 리액터 빌드와 Docker 빌드 컨텍스트가 `dstone-common`을 함께 필요로 한다.
- Jenkins는 원격에 올라간 커밋만 본다. 로컬에만 있는 커밋이 있으면 push가 먼저 필요하다고 알린다. **push는 사용자 확인을 받고 한다.**

## 적용 대상

`dstone-boot`, `dstone-batch`, `dstone-batchadmin`, `dstone-ai-engine`의 Jenkins Job과 앞으로 만드는 모든 CI 설정. 자세한 구성은 `docs/software/12.jenkins.md`, `docs/04.cloud-architecture.md`를 본다.
