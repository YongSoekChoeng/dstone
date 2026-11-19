# conf/scripts — 사람이 직접 쓰거나 엔진이 runShellScript로 부르는 스크립트

`conf/definitions`처럼 jar에 들어가지 않고 환경마다 맞춰 쓰는 자산이다. 특정 Workflow를 위한 스크립트는 두지 않는다.
필요한 값(경로, DB 이름)은 모두 인자로 받고, 그 값을 정하는 일은 Workflow 정의나 실행하는 사람이 한다.

| 폴더 | 스크립트 | 하는 일 | 엔진 등록 |
|---|---|---|---|
| `env/` | `reset-target` (`.sh` / `.bat`) | 대상 소스(git stash)와 DB(백업 후 재생성)를 기준 상태로. 기본은 미리 보기 | 등록하지 않음(사람이 직접 실행) |

- 컴파일 확인은 스크립트가 아니라 엔진의 범용 Tool `compileJava`가 한다(바뀐 파일만 원본 클래스를 참조해 컴파일).
- 엔진이 부르게 하려면 `conf/application.yml`의 `dstone.ai.tool.shell.allowed-commands`에 등록한다. 경로는 확장자 없이 적는다
  (`runShellScript`가 Windows면 `.bat`/`.cmd`, 그 밖이면 `.sh`를 고른다).
- 같은 일을 하는 `.sh`와 `.bat`(+`.ps1`)을 함께 둔다. `.ps1`은 ASCII로만 쓴다(Windows PowerShell 5.1이 BOM 없는 UTF-8을 깨뜨린다).
- 줄바꿈은 `.gitattributes`로 고정한다(`.sh` LF, `.bat`/`.ps1` CRLF).
- 위험한 일(파일 삭제, DB 변경)을 하는 스크립트는 엔진에 등록하지 않는다.
