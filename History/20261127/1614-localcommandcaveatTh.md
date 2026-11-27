# localcommandcaveatTh

> 생성일시: 2026-11-27 16:14

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

resolveProjectRevision 에 대해서 물어볼께. 해당 tool이 호출되면 
형상관리로부터 새로운 리비전을 내려받아서 (프로젝트관리 화면에서 [Build]버튼을 클릭한 것처럼)리비전 갱신을 하고 해당 리비전으로 진행하게 하는 것은 어떨까?

---

## 답변

빌드 흐름은 확인했습니다. 마지막으로 진행 중인 실행의 리비전이 정리 때 보호되는지만 보겠습니다.

---

## 답변

좋은 방향이라고 봅니다. 지금은 [Build]를 깜빡하면 에러 없이 옛 소스로 분석하고 개발합니다. 다만 조건 몇 가지를 같이 넣어야 합니다. 아직 코드는 바꾸지 않았습니다.

## 지금 동작
- `resolveProjectRevision`은 DB에 적힌 현재 리비전을 읽기만 합니다.
- [Build]는 `git pull`/`svn update` → 소스 복사 → 빌드를 백그라운드 스레드에서 합니다. 같은 SCM 리비전이 이미 성공했으면 다시 빌드하지 않습니다. 그래서 바뀐 것이 없을 때는 갱신 비용이 거의 없습니다.

## 권하는 모양
| 항목 | 제안 |
|---|---|
| 켜는 방법 | Tool 인자 `refresh`를 두고 `sm-workflow.yml`에서 `refresh: true`로 지정합니다. 갱신할지는 Workflow가 정합니다. |
| 빌드 호출 | 버튼과 같은 로직을 끝날 때까지 기다리는 메소드로 부릅니다. 이미 빌드 중이면 끝나기를 기다렸다가 그 결과를 씁니다. |
| 형상관리가 없는 프로젝트 | 갱신하지 않고 지금처럼 현재 리비전을 씁니다. 리비전 이름이 빌드 시각이라 실행할 때마다 전체를 다시 빌드하게 됩니다. |
| 갱신·빌드 실패 | 이전 리비전으로 넘어가지 않고 로그와 함께 실패시킵니다. 조용히 옛 소스를 쓰면 이 기능을 넣는 의미가 없습니다. |

## 같이 해결해야 할 것
1. **리비전 삭제 위험 (지금도 있는 문제)**: 오래된 리비전을 지울 때 보호 표시(`runs/{실행ID}/revision.txt`)는 테스트 서버를 띄울 때 생기고 내릴 때 지워집니다. 승인 대기 중인 실행의 리비전은 보호되지 않을 수 있습니다(`keep-revisions` 기본 3). 자동 빌드를 하면 리비전이 자주 늘어나므로, 끝나지 않은 실행의 `state.projectRevision`을 기준으로 보호하도록 먼저 고쳐야 합니다.
2. **코드 분석 결과와의 어긋남**: `knowledgeFeatureCode`는 dstone-knowledge가 분석한 줄 번호로 리비전 소스를 읽습니다. 소스만 새로워지면 엉뚱한 줄을 가져올 수 있습니다. 우선은 분석한 리비전과 다를 때 결과에 경고를 남기고, 재분석까지 자동으로 돌리는 것은 따로 판단하는 편이 낫습니다. 두 리비전을 비교할 값이 있는지는 더 확인해야 합니다.
3. **권한**: [Build]는 관리자만 누를 수 있는데, 이렇게 바꾸면 Workflow를 실행하는 사람 누구나 빌드를 일으킵니다. 사내 도구라 허용해도 된다고 보지만 결정은 필요합니다.
4. **시간과 원본 폴더**: 새 커밋이 있으면 첫 step이 몇 분 걸리고 실행 스레드를 잡고 있습니다. step 시간 제한에 걸리지 않는지 구현할 때 확인해야 합니다. 소스 디렉토리에 로컬 변경이 있으면 `git pull --ff-only`가 실패해 실행이 시작되지 않을 수 있습니다.

실행 중간에는 지금처럼 처음 정한 리비전을 끝까지 씁니다. step59로 되감았을 때만 새로 받게 됩니다.

진행한다면 1번 수정과 `refresh` 추가를 함께 하고, 2번은 경고까지만 넣겠습니다. 이대로 할까요?
