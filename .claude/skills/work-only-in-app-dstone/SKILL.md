---
name: work-only-in-app-dstone
description: 파일 수정은 WSL 저장소 `/app/dstone` 아래에서만 하고 Windows 쪽 클론(`D:/AppHome/framework/dstone`, `/mnt/d/AppHome/framework/dstone`)은 건드리지 않는 규칙. 파일을 만들거나 고치기 전에, 특히 로그·실행 경로·오류 메시지가 D: 드라이브나 `/mnt/d`를 가리킬 때 사용한다.
---

# 작업은 /app/dstone 에서만

모든 수정은 `/app/dstone` 아래에서 한다. Windows 쪽 클론 `D:/AppHome/framework/dstone` (`/mnt/d/AppHome/framework/dstone`)은 고치지 않는다. 사용자가 돌린 엔진이나 로그가 그쪽을 가리켜도 마찬가지다.

## 왜

2026-09-30에 사용자의 실행이 D: 클론을 쓰고 있다는 이유로 그쪽의 workflow/agent YAML을 직접 고쳤고, 사용자가 "앞으로 너는 작업을 /app/dstone 아래에서 작업해"라고 바로잡았다. 두 클론은 GitHub 원격을 통해 git으로 맞춘다.

## 이렇게 한다

| 상황 | 할 일 |
|---|---|
| 고칠 파일이 D:에만 있거나 내용이 다르다 | `/app/dstone`에서 `git pull` (`pull.rebase=true` 설정됨). 그래도 없으면 아직 push되지 않았다고 사용자에게 알린다 |
| D: 쪽 로그·출력물을 봐야 한다 | 읽기는 괜찮다. 진단용으로만 본다 |
| D: 쪽 실행에 수정을 반영해야 한다 | `/app/dstone`에서 고치고, git으로 넘어가야 한다고 안내한다 |

`/mnt/d` 아래에 쓰기(생성·수정·삭제)는 하지 않는다.
