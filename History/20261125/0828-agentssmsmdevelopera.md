# agentssmsmdevelopera

> 생성일시: 2026-11-25 08:28

## 질문

agents/sm/sm-developer-agent.md 에 대해서 자세하게 설명해줘.

---

## 답변

# `sm-developer-agent` (v2.3.0) 설명

파일: `dstone-ai-engine/conf/definitions/agents/sm/sm-developer-agent.md`

## 1. 한마디로

`sm-workflow`의 **step17(18-개발)**을 맡은 Agent입니다. 앞 단계에서 사람이 승인한 상세 설계서(`03-1.design.md`)대로 소스를 만들고 고칩니다. 결과는 원본이 아니라 `dev-resources/`에 둡니다. 그다음 `compileJava`로 컴파일을 확인하고, 항목마다 완료나 보류를 태스크아이템 목록(`03-2.taskitems.md`)에 적습니다.

**설계서대로만 구현한다**는 원칙이 중심에 있습니다. 판단은 설계 단계와 사람이 이미 끝냈습니다. 개발 단계에서 임의로 바꾼 내용은 아무도 검토하지 않은 채 반영되므로, 설계와 다르게 해야 할 이유가 생기면 고치지 않고 **'보류'**로 둡니다.

## 2. Workflow에서의 위치

```
step13 설계 승인 → step14/15 설계서·태스크아이템 읽기 → step16 개발 폴더 초기화
   → step17 개발 (이 Agent) → step18 컴파일 확인(TOOL)
        ├ 성공 → step28~ 자동 검증(격리 기동, 동작 검증 …)
        └ 실패 → step19 개발 승인(PL)
                   ├ 재개발 → step20/21 설계서·목록 다시 읽기 → step17로 돌아옴
                   ├ 전문가 해결 → sm-troubleshooter-agent
                   └ 설계/영향도/요구사항 수정 …
```

Workflow는 `sm-workflow.yml:283` 근처에서 이렇게 값을 넘깁니다.

| 입력 | 값 | 의미 |
|---|---|---|
| `base_dir` | `${input}/${state.now}` | 작업 폴더(실행 시각별) |
| `source_dir` | `${state.sourceDir}` | 원본 소스 루트(요청서의 소스경로) |
| `request`/`design`/`taskitems` | state의 문서 내용 | 세 문서. **파일로 다시 읽지 않는다** |
| `instruction` | `${arrival:}` | 이번 일을 시킨 결정(설계 승인 또는 재개발 지시) |
| `history` | `${decisions:}` | 지금까지 내려진 결정 전체 |
| `compile_error` | `${state.compileError:}` | 지난 컴파일 오류. 있으면 이것부터 고친다 |

재개발 때는 step20/21이 설계서와 목록을 다시 읽어 state를 갱신합니다. 그래서 Agent는 항상 최신 문서를 입력으로 받습니다.

## 3. Front matter 설정

- **model**: `temperature 0.2`, `reasoning medium`. 창의성보다 설계서를 그대로 옮기는 정확성이 중요해서 낮게 둡니다.
- **tools.allowed**: 파일 읽기와 쓰기(`readFileLines`, `isFileExist`, `writeFile`, `editFileTexts`, `appendFile`, `copyFileWithReplace`), 검색(`searchInFiles`), 컴파일(`compileJava`). DB나 knowledge Tool은 없습니다. 조사는 이미 설계 단계에서 끝났다는 전제입니다.
- **skills.preload**: 늘 함께 받는 Skill 세 개입니다.
  - `handling-instructions`: instruction과 history를 읽고 반영할 것과 안 할 것을 가르는 방법
  - `source-investigation`: 선언을 찾아 확인하는 조사 방법
  - `troubleshooting`: "증상 → 먼저 볼 것", "값 대조표". 나중에 생길 동작 오류를 개발 단계에서 미리 막으려고 받습니다.
- **skills.allowed**: `projects/**`. 프로젝트별 관례(예: `projects/testApp/gnx-crud-screen`)는 필요할 때만 불러 씁니다.
- **execution**: `maxToolCalls: 120`. `requiredTools: [editFileTexts]`는 `editFileTexts`를 한 번도 안 부르고 끝내면 실패로 본다는 뜻입니다. 기록을 빼먹고 "다 했다"고 답하는 경우를 막습니다.

## 4. 출력

| 필드 | 내용 |
|---|---|
| `completed` / `pending` | 완료·보류 건수 |
| `pendingReasons` | 보류 항목 번호와 이유 |
| `notApplied` | 반영하지 않은 지시와 이유 |
| `taskitemsRecorded` | `03-2.taskitems.md`를 고쳐 저장했는지 |
| `designSynced` | 재개발로 고친 내용을 설계서에도 옮겨 적었는지 |

이 값과 `03-2`의 기록이 다음 **개발 승인 화면**에서 PL이 보는 결과입니다.

## 5. 프롬프트 본문 절별 해설

**[원본을 지키는 법]**
- 원본은 읽기만 합니다. 결과는 `dev-resources/` 아래에 **원본 루트 기준 같은 상대경로**로 둡니다.
- 기존 파일을 고칠 때는 먼저 `copyFileWithReplace(원본, 저장경로, [])`로 복사하고 복사본을 고칩니다.
- 파일 삭제는 직접 하지 않습니다. `_delete-files.txt`에 상대경로를 적어 두면 `compileJava`의 `deleteList`가 반영합니다.
- 이렇게 원본 + overlay(`dev-resources`) + 삭제 목록 구조로 두어서, 원본을 건드리지 않고도 컴파일과 격리 기동을 시험할 수 있습니다.

**[구현하는 법]**
1. 태스크아이템을 선행 순서대로 처리합니다. 파일에 '변경 후'가 이미 있으면 건너뛰므로, 재실행해도 결과가 같습니다(멱등).
   - 기존 파일은 `editFileTexts` 한 번에 모아 고칩니다(before = '변경 전', after = '변경 후'). "실패:"가 나면 안내가 가리킨 곳 앞뒤만 읽고 **한 번만** 다시 시도합니다. 그래도 안 되면 보류합니다.
   - 새 파일은 설계서에 '참고 파일'이 있으면 복사하면서 이름을 바꾸고 차이만 편집합니다. 참고 파일이 없을 때만 `writeFile`로 전체를 씁니다. 쓰기 전에 파일이 있는지 확인하고, 있으면 보류합니다.
   - 설계서에 글자 그대로 없는 클래스, 메서드, import는 선언을 찾아 확인합니다. 못 찾으면 **지어내지 않고** 보류합니다.
   - DB에만 적용하는 항목(DDL, INSERT)은 SQL이 `03-3`에 있으면 '완료'로 두고 "DB 반영 필요(03-3)"라고 적습니다. **2.3.0에서 바뀐 부분**입니다. 이전 보류 6건이 모두 "DB 반영은 사람이 해야 함"이어서 승인 화면의 보류 수가 잡음이 됐기 때문입니다.
2. `compileJava`는 최대 3번 부릅니다. 실패하면 오류 줄을 보고 고친 뒤 다시 부르고, 세 번째도 실패하면 남은 오류를 보류 이유로 적습니다. 컴파일러가 없는 것처럼 코드와 무관한 실패면 다시 부르지 않습니다. step18이 한 번 더 확인하기 때문입니다.
3. `03-2.taskitems.md`에 결과를 `editFileTexts` 한 번으로 기록합니다.

**[재개발로 고친 것을 설계서에 남기는 법]**
- 설계를 다시 하면 step16이 개발 폴더를 비우고 설계서만 보고 처음부터 만듭니다. 설계서에 없는 수정은 그때 사라집니다.
- 그래서 재개발로 고친 내용은 설계서에도 같은 before/after로 반영합니다. 설계서에 없던 곳이면 '변경 전/후' 쌍을 덧붙이고, 새 파일이나 삭제가 생기면 설계서와 목록에 항목을 더합니다. 끝으로 `## 수정 이력`에 한 줄을 남깁니다.
- 재개발이 아니면 설계서는 건드리지 않습니다.
- 재개발에서는 **사람의 지시가 설계서보다 앞섭니다.** 지시 때문에 설계와 달라지는 것은 보류가 아니라 수정 대상입니다.

**[제약]**
- 태스크아이템에 적힌 파일만 다룹니다. 리팩터링, 이름 변경, 정리 같은 개선은 하지 않습니다.
- 성공한 편집은 다시 읽어 확인하지 않습니다. Tool 호출 예산을 아끼기 위해서입니다.

## 6. 설계상 포인트 요약

- **책임 분리**: 판단은 설계와 승인 단계, 구현은 이 Agent, 동작 문제는 `sm-troubleshooter-agent`가 맡습니다.
- **보류는 실패가 아니라 신호**입니다. 사람이 승인 화면에서 판단할 거리를 남깁니다.
- **원본 불변 + overlay** 구조라 컴파일, 격리 기동, 자동 검증을 안전하게 할 수 있습니다.
- **설계서가 단일 원본**입니다. 재개발 수정을 설계서에 되돌려 적어서, 다시 설계한 뒤에도 같은 결과가 나오게 합니다.
- 공통 방법은 Skill(`preload`)에, 프로젝트 관례는 `projects/**`에 두어 Agent 프롬프트에는 이 단계 고유의 규칙만 남깁니다.

특정 절이나 실제 실행 기록(보류 사례, 평가 결과)을 더 자세히 보고 싶으시면 말씀해 주세요.
