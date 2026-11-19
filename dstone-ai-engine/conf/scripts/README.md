# conf/scripts — 엔진이 runShellScript로 부르거나 사람이 직접 쓰는 스크립트

`conf/definitions`처럼 jar에 들어가지 않고 환경마다 맞춰 쓰는 자산이다. 특정 Workflow를 위한 스크립트는 두지 않는다.
필요한 값(경로, 빌드 명령, DB 이름)은 모두 인자로 받고, 그 값을 정하는 일은 Workflow 정의가 한다.

| 폴더 | 스크립트 | 하는 일 | 엔진 등록 이름 |
|---|---|---|---|
| `build/` | `overlay-compile` (`.sh` / `.bat`+`.ps1`) | 원본 사본에 변경 폴더를 덮어씌우고 삭제 목록을 지운 뒤 컴파일 | `overlay-compile` |
| `env/` | `reset-target` (`.sh` / `.bat`) | 대상 소스(git stash)와 DB(백업 후 재생성)를 기준 상태로. 기본은 미리 보기 | 등록하지 않음(사람이 직접 실행) |

- 엔진 등록: `conf/application.yml`의 `dstone.ai.tool.shell.allowed-commands`. 경로는 확장자 없이 적는다
  (`runShellScript`가 Windows면 `.bat`/`.cmd`, 그 밖이면 `.sh`를 고른다).
- 같은 일을 하는 `.sh`와 `.bat`(+`.ps1`)을 함께 둔다. `.ps1`은 ASCII로만 쓴다(Windows PowerShell 5.1이 BOM 없는 UTF-8을 깨뜨린다).
- 줄바꿈은 `.gitattributes`로 고정한다(`.sh` LF, `.bat`/`.ps1` CRLF).
- 위험한 일(파일 삭제, DB 변경)을 하는 스크립트는 엔진에 등록하지 않는다.
