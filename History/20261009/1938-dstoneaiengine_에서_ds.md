# dstoneaiengine_에서_ds

> 생성일시: 2026-10-09 19:38

## 질문

dstone-ai-engine 에서 
dstone.ai.tools.pilot.* 클래스들을 보면 pilot-workflow 에 종속된 기능들 같은데
그렇다면 workflow를 추가할 때마다 이렇게 java클래스가 늘어나야 하는 구조인가?

---

## 답변

아니요, Workflow를 추가한다고 Java 클래스가 반드시 늘어나는 구조는 아닙니다. 다만 **"경로로 값을 읽는 것" 이상의 결정적 로직이 필요한 Workflow는 Java Tool이 늘어나는 구조**가 맞습니다.

## 현재 상태

- Workflow 18개 중 전용 Java 클래스를 가진 것은 `pilot-workflow` 하나뿐입니다. 나머지 17개는 YAML과 범용 Tool만으로 돌아갑니다.
- `pilot-workflow`의 TOOL 스텝 17개 중 6개는 범용 Tool(`readFile`, `copyFileWithReplace`, `writeFile`, `replaceInFile`, `appendFile`, `editFileLines`)이고, 11개가 `pilotNewdev…` 전용입니다.

## 왜 pilot만 전용 클래스가 생겼나

2026-10-09에 jq를 없애면서 "로직은 YAML에 두지 않고 Java Tool로 만든다"로 정했기 때문입니다. 표현식이 경로 읽기만 하므로, 그 이상은 전부 Tool이 됩니다. 전용 Tool 11개를 성격별로 나누면 이렇습니다.

| 성격 | Tool | 범용화 가능성 |
|---|---|---|
| 파일 여러 개 읽기 (1개) | `ReadSources` | 가능. `readFiles` 같은 범용 Tool로 바꿀 수 있습니다 |
| 문서 만들기 (4개) | `WriteRequirements` / `WriteImpact` / `WriteDesign` / `WriteTasks` | 부분적으로 가능. 템플릿 파일 + 범용 `renderTemplate` Tool |
| 목록 모양 바꾸기 (4개) | `DesignInputs` / `PlanChanges` / `DevelopInputs` / `ApplyInputs` | 어렵습니다. jq가 하던 일이라 범용화하면 표현식 언어가 다시 필요합니다 |
| 검증 규칙 (2개) | `CheckAnalysis` / `CheckDesign` | 불가. 업무 규칙이라 어디엔가 코드로 있어야 합니다 |

즉 "LLM → LLM → 승인"처럼 단순한 Workflow는 YAML만으로 끝납니다. pilot처럼 LLM이 하던 일을 결정적 단계로 빼낼수록 Java가 늘어납니다. 대신 그 로직은 단위 테스트가 됩니다(`PilotNewdevLogicTest`).

## 실제로 걸리는 문제는 위치

클래스가 늘어나는 것보다, **특정 Workflow 전용 코드가 재사용용 엔진 jar 안(`net.dstone.ai.tools.pilot`)에 들어 있다**는 점이 더 문제입니다. 다른 SI 프로젝트가 엔진을 가져가면 pilot 로직 약 1,200줄이 딸려 가고, 그 프로젝트의 전용 Tool도 엔진 소스에 넣어야 합니다.

## 권장

1. **원칙은 유지합니다.** jq로 되돌리면 테스트할 수 없는 로직이 YAML에 다시 쌓입니다.
2. **Workflow 전용 Tool은 엔진 밖으로 분리합니다.** 엔진에는 범용 Tool만 두고, `pilot` 같은 것은 별도 모듈(또는 프로젝트 쪽 jar)에 `@AiTool` 빈으로 둡니다. `ConfigTool`이 `@AiTool` 빈을 스캔해서 등록하므로 엔진 코드는 거의 바꿀 필요가 없을 것으로 보입니다. 컴포넌트 스캔 범위와 정의 파일 로딩 경로는 아직 확인하지 않았습니다.
3. **범용화는 두 번째 사용처가 생길 때 합니다.** `readFiles`와 템플릿 렌더링은 다음 Workflow에서도 필요해지면 그때 뽑아내는 편이 안전합니다. 템플릿에 반복·조건을 넣기 시작하면 로직이 다시 Java 밖으로 나갑니다.

2번을 진행하길 원하시면 스캔 범위와 정의 파일 로딩 경로부터 확인해서 분리안을 잡겠습니다.

---

## 질문

<pasted_content id="d25b">
2026-10-09에 jq를 없애면서 "로직은 YAML에 두지 않고 Java Tool로 만든다"로 정했기 때문입니다.
==>> Java Tool로 만든다 라는 의미는 범용적인 Tool을 만든다는 뜻이지 특정 workflow만을 위한 Tool을 의미하지 않아.
지금 나는 최대한 쉽고, 범용적이고, 유연한 형태의 어플리케이션을 만드려고 최대한 노력하고 있어.
yml 이 어려워져서 수정을 택했는데 유연하지못한 형태의 어플리케이션이 나온다면 이것은 올바른 수정진행 방향이 아닌 것 같아.
개선 방향에 대해서 고민하고 네 의견을 말해줘.
</pasted_content id="d25b">

---

## 답변

말씀이 맞습니다. 제가 jq 30여 개를 **그 Workflow 전용 Java로 1:1 옮긴 것**은 로직의 위치만 YAML에서 Java로 바꿨을 뿐, "쉽고 범용적이고 유연하게"라는 목적에는 오히려 역행했습니다. 지금은 Workflow를 고치려면 Java를 고치고 빌드해야 하니 jq 때보다 더 굳었습니다.

## 제 의견: 전용 Tool을 없애고 세 군데로 나눠 담는다

`PilotNewdevLogic`의 메서드 목록을 보면 하는 일은 네 종류뿐이고, 각각 범용으로 풀 자리가 다릅니다.

| 지금 (pilot 전용) | 바꿀 곳 | 방법 |
|---|---|---|
| 문서 만들기 4개 (`…Doc`) | **정의 파일** | `definitions/templates/*.md` 템플릿 + 범용 `renderTemplate(template, data, outputPath)` Tool 하나 |
| 파일 여러 개 읽기 1개 | **범용 Tool** | `readFiles(paths)` |
| 목록 모양 바꾸기 4개 | **엔진 + 스키마** | 아래 설명 |
| 검증 2개 | **범용 검증 Tool 몇 개** | 아래 설명 |

**문서 템플릿**은 효과가 가장 크고 위험이 가장 작습니다. 프롬프트와 스키마를 이미 파일로 뺐으니 문서 틀도 같은 방식으로 빼면, 문서 모양은 개발자가 아니어도 고칠 수 있습니다. 템플릿 문법은 Mustache처럼 반복과 "있으면 표시"만 되는 것으로 제한해야 로직이 다시 새어 들어가지 않습니다.

**목록 모양 바꾸기**는 대부분 엔진이 부족해서 생긴 코드입니다.
- forEach 결과가 입력과 따로 저장되어 다시 짝지어야 합니다. 엔진이 `[{input, result, error}]`로 묶어 저장하면 `developInputs`/`applyInputs`류가 필요 없어집니다.
- 항목을 Tool 인자 모양으로 바꾸는 일은 step `input`의 `${item.x}`로 이미 됩니다.
- 남는 것은 "종류별로 나누기"와 "경로로 내용 찾기"뿐입니다. 범용 `groupBy(list, field)`, `lookup(map, key)` 정도의 작은 데이터 Tool로 덮입니다.
- Agent의 출력 스키마를 다음 step이 받는 모양에 맞추면 변환 자체가 줄어듭니다.

**검증**은 규칙을 뜯어보면 일반적인 것들입니다. "파일이 있어야/없어야 한다", "이 글이 파일에 정확히 한 번 나온다", "A 목록의 id가 B 목록에 모두 있다". 이것을 `checkFilesExist`, `checkTextOccurs`, `checkReferences` 같은 범용 Tool로 두고 결과 형식(`{ok, problems, text, feedback}`)만 통일하면 됩니다.

## 감수할 점

- **YAML step 수가 늘어납니다.** 전용 Tool 하나가 하던 일이 범용 step 2~3개가 됩니다. 다만 step 하나가 한 가지 일만 하므로 길어질 뿐 어려워지지는 않습니다. jq와 다른 점은 이것입니다.
- **범용 데이터 Tool이 늘어나면 jq를 step 단위로 다시 만드는 셈이 됩니다.** 그래서 개수를 작게 묶어 두고(5~6개), 그걸로 안 되는 요구는 Tool을 늘리지 말고 Workflow 설계를 단순하게 바꾸는 쪽으로 가야 합니다.
- **그대로 못 옮기는 로직이 있습니다.** 예를 들어 `tasksDoc`은 결과 목록 10개를 받아 파일별 상태를 판정합니다. forEach 결과 묶기가 들어가면 상당 부분 풀리겠지만, 일부는 문서 내용을 단순화해야 할 수 있습니다.
- **아직 메서드 목록과 YAML 일부만 본 상태입니다.** 규칙 하나하나가 위 범용 Tool로 실제 덮이는지는 착수 전에 대응표로 확인해야 합니다.

## 진행 순서 제안

`pilot-workflow`는 형식 변경 후 LLM으로 한 번도 돌려보지 않았으므로 한 번에 갈아엎지 않고 단계마다 기존 테스트 기대값(`src/test/resources/pilot/`)과 비교하며 가는 것이 안전합니다.

1. 템플릿 + `renderTemplate` — 전용 Tool 4개 제거, 출력이 기존과 글자 단위로 같은지 비교 가능
2. `readFiles` — 1개 제거
3. 엔진의 forEach 결과 묶기 + `groupBy`/`lookup` — 4개 제거
4. 범용 검증 Tool — 2개 제거, `tools.pilot` 패키지 삭제

이 방향이 맞다면 먼저 규칙별 대응표(현재 로직 → 어느 범용 Tool/엔진 기능/템플릿으로 가는지, 못 옮기는 것은 무엇인지)를 만들어 보여드리고, 확인받은 뒤 1번부터 시작하겠습니다.

---

## 질문

응 시작해

---

## 답변

먼저 대응표를 만들기 위해 엔진 쪽 관련 코드(파일 Tool, 정의 파일 로더, TOOL step 실행, forEach 저장 방식)를 확인합니다.

---

## 답변

Mustache 의존성부터 넣고, 범용 Tool 두 개(`readFiles`, `renderTemplate`)를 만듭니다.

---

## 답변

전체 테스트 68개는 통과했습니다. 지금은 실제 부팅 검증(TOOL step 인자 이름 검사)이 가능한지 실행 중인 프로세스와 인프라를 확인하고 있습니다.

---

## 답변

전용 Tool 11개 중 3개를 범용 Tool과 틀 파일로 바꿨고, 남은 8개는 결정이 필요한 지점이 있어 멈췄습니다. 테스트 68개 통과, 사본 jar를 다른 포트로 띄운 부팅에서도 `pilot-workflow` 경고는 없었습니다. LLM을 붙인 실행은 하지 않았습니다.

## 이번에 바꾼 것

| 전용 Tool (삭제) | 대체 |
|---|---|
| `pilotNewdevReadSources` | 범용 `readFiles` — 결과 모양 `{files, missing, text}` 그대로 |
| `pilotNewdevWriteRequirements` | 범용 `renderTemplate` + `templates/pilot/newdev-requirements.md.mustache` |
| `pilotNewdevWriteDesign` | 범용 `renderTemplate` + `templates/pilot/newdev-design.md.mustache` |

- **문서 틀**은 `definitions/templates/` 아래 Mustache 파일입니다. 문법은 값 넣기, 되풀이, 있으면/없으면, 순번, 표의 칸 변환뿐입니다.
- **동일성**: 두 문서는 기존 기대값(jq 실행 결과)과 글자 하나까지 같습니다. 다만 설계서에서 질문이 하나도 없을 때 "없음" 대신 빈 칸이 됩니다.
- **의존성 추가**: `jmustache` 1.16 (Spring Boot가 버전 관리).
- **문서**: `docs/09.dstone-ai-engine.md`와 `CLAUDE.md`에 반영했습니다.

## 남은 8개 대응표

코드를 전부 읽어 보니 지난번에 말씀드린 것보다 까다롭습니다. 범용 Tool만으로는 안 되고, 문서 내용이나 Agent 출력 모양을 바꿔야 풀리는 것이 있습니다.

| 전용 Tool | 범용으로 푸는 방법 | 걸리는 점 |
|---|---|---|
| `WriteImpact` | 틀 파일 | "FR별 대응" 표의 '변경 대상 파일' 칸은 FR마다 대상을 찾아 맞추는 로직이라 틀로 안 됩니다 |
| `WriteTasks` | 틀 파일 | 결과 목록 10개를 경로로 맞춰 '상태'를 판정하는 로직이 핵심이라 틀로 안 됩니다 |
| `CheckAnalysis` (규칙 9개) | 2개는 Agent 출력 스키마(`minItems`), 7개는 범용 `checkData` Tool | 규칙을 YAML에 적는 형식이 새로 생깁니다 |
| `CheckDesign` (규칙 5개) | 3개는 스키마(`if/then`), 1개는 `checkData`, "변경 전이 정확히 한 번"은 `replaceInFile`의 미리 해보기 옵션 | 위와 같음 |
| `DesignInputs` | forEach의 step `input`에서 직접 조립 | 경로로 파일 내용을 붙여 주는 범용 Tool 하나가 필요합니다 |
| `PlanChanges` | 범용 `filterList` + forEach | 설계 Agent의 출력 필드를 파일 Tool 인자 모양에 맞춰 바꿔야 합니다 (`before/after` → `oldText/newText` 등) |
| `DevelopInputs`, `ApplyInputs` | 엔진이 forEach 결과를 `[{item, result, error}]`로 묶어 저장 + `filterList` | 엔진 저장 형식이 바뀝니다 |

## 결정해 주실 것

**1. 문서 내용을 단순하게 바꿔도 되는지** (권장: 바꾼다)
- `02-impact.md`: "FR별 대응" 표에서 '변경 대상 파일' 칸을 뺍니다. 바로 아래 "변경 대상" 표에 FR 칸이 이미 있습니다.
- `04-tasks.md`: 판정한 '상태' 한 칸 대신 복사·편집·적용 결과를 단계별로 그대로 보여줍니다.
- 바꾸지 않으면 이 두 개는 전용 Java로 남습니다.

**2. 검증 규칙을 YAML에 이런 형식으로 적어도 되는지** (권장: 허용하되 규칙 종류를 5개 안팎으로 고정)
```yaml
tool: checkData
input:
  data: { analysis: "${state.analysis}", sources: "${state.sources}" }
  rules:
    - { rule: unique,     values: "analysis.targets[].path", message: "같은 파일이 변경 대상에 두 번 들어 있습니다" }
    - { rule: allIn,      values: "analysis.requirements[].id", in: "analysis.targets[].frs[]", message: "FR에 대응하는 변경 대상이 없습니다" }
    - { rule: filesExist, values: "analysis.targets[changeType=수정].path", message: "수정이라고 했는데 없는 파일입니다" }
```
한 줄에 규칙 하나라 읽기는 쉽지만 `[]`, `[키=값]` 같은 경로 표기가 생깁니다. jq 때처럼 번질 위험이 가장 큰 자리입니다. 싫으시면 검증 2개는 전용 Java로 남습니다.

**3. 설계 Agent의 출력 스키마와 프롬프트를 바꿔도 되는지** (권장: 1·2를 먼저 끝내고 LLM 실행이 가능할 때 진행)
- 측정으로 맞춰 둔 프롬프트를 건드리게 되고, `pilot-workflow`는 형식 변경 뒤 LLM으로 돌려본 적이 없어서 지금 바꾸면 검증 없이 쌓입니다.

---

## 질문

진행중에 생성되는 md 파일은 내가 의도한 강제성은 없어. llm이 중간중간에 생성했을 뿐인거지. 내 관심사는 최종결과만 의도에서 벗어나지 않게 수행하는거야.  현재 작성된 yml파일들을 변경/수정 하는데에 대해서는 두려움 없이 진행해. 다만'용이성, 범용성'이라는 내 의도에서 벗어나지 않으면 되.

---

## 답변

방향 확인했습니다. 그러면 중간 문서는 단순하게 가져가고, `tools.pilot` 패키지를 완전히 없애는 데까지 진행하겠습니다. 먼저 엔진의 표현식·forEach 저장 코드와 pilot 스키마를 읽습니다.

---

## 답변

Now the generic Tools.

---

## 답변

실제 실행에서 결함을 하나 찾았습니다. 비동기 실행 스레드에서는 틀 파일을 못 찾습니다(클래스로더 문제). 고치고 다시 돌립니다.

---

## 답변

분석 단계는 통과했습니다(자동 검증 "문제 없음", 문서 2종 정상 생성). 승인을 넣고 설계 단계로 넘깁니다.

---

## 답변

문서 갱신 중입니다. `docs/09`의 본문은 대부분 고쳤고, 남은 옛 이름 두 군데와 `CLAUDE.md`, YAML 머리말 주석을 정리한 뒤 마지막 전체 테스트를 돌립니다.

---

## 답변

You've hit your session limit · resets 7:10pm (Asia/Seoul)

---

## 질문

Your claude.ai usage limit has reset. Continue the task you were working on when the limit was reached; do not repeat work that is already complete.

---

## 답변

`tools.pilot` 패키지를 완전히 없앴고, `pilot-workflow`는 이제 YAML, 틀 파일, 범용 Tool만으로 돕니다. LLM을 붙여 한 번 끝까지 실행해 새 파일 6개 생성과 기존 파일 2개 수정이 모두 적용되는 것을 확인했습니다. 다만 만들어진 소스를 빌드하거나 기동해 보지는 않았습니다.

## 추가된 범용 기능

| 종류 | 이름 | 하는 일 |
|---|---|---|
| Tool | `readFiles` / `readItemFiles` | 파일 여러 개 읽어 두기 / 목록의 항목이 가리키는 파일을 읽어 붙이기 |
| Tool | `renderTemplate` | 틀 파일(`definitions/templates/*.mustache`)에 데이터를 끼워 문서 만들기 |
| Tool | `filterList` | 목록에서 조건에 맞는 항목만 고르기 |
| Tool | `checkData` | 값 맞춰 보기 (규칙 4종: `unique`, `allIn`, `filesExist`, `filesAbsent`) |
| Tool | `editFileTexts` | 파일 하나의 여러 곳을 한 번에 고치기, `dryRun`으로 미리 확인 |
| 엔진 | forEach step의 `output: {items: state.x}` | 반복마다 `{item, input, result, error, success}`를 한 건씩 저장 |

- 검증 규칙은 별도 경로 표기 없이 "어느 목록의 어느 필드를 어떻게 볼지"만 YAML에 적습니다. 목록은 기존 `${state…}` 표현식으로 넘깁니다.
- LLM 없이 이 기능들만으로 엮은 샘플 `sample-tool-edit-report.yml`을 추가했습니다. 새 Workflow를 만들 때 본보기로 쓸 수 있습니다.

## 확인한 것

- **단위 테스트**: 64개 통과.
- **부팅**: 패키징한 jar의 사본을 다른 포트로 띄워 Workflow 19개가 경고 없이 등록됐습니다.
- **샘플 실행**: `sample-tool-edit-report`를 일부 실패 입력과 전부 성공 입력으로 각각 실행해 정상 종료했습니다.
- **pilot 실행**: `testApp` 사본에 "샘플게시판 같은 FAQ" 요청을 넣어 제출부터 종료까지 약 3분 40초(승인은 즉시 입력)였습니다.
  - 새 Java 파일은 `javac` 문법 오류가 없고 매퍼 XML은 well-formed입니다.
  - JSP 하나에 "괄호 짝이 달라졌다"는 주의가 붙었습니다. 사람이 봐야 합니다.
  - `FaqDAO`에 옛 변수명 `sampleBoardMapper`가 남았습니다. 동작에는 영향이 없습니다.

실제 실행에서 결함 두 개를 찾아 고쳤습니다.
- 비동기 실행 스레드에서 틀 파일을 찾지 못하던 문제(클래스로더).
- Tool이 돌려준 빈 목록 `[]`이 빈 글자로 바뀌어 다음 forEach가 실패하던 기존 엔진 버그.

## 달라진 점

- **중간 문서**: 검증 결과 파일 두 개(`02-impact-check.md`, `04-design-check.md`)는 따로 만들지 않고 문서의 '자동 검증 결과'에만 넣습니다. `02-impact.md`의 FR별 대응 표는 뺐고, `04-tasks.md`는 파일별 '상태' 판정 대신 단계별 Tool 결과를 그대로 보여줍니다.
- **기존 파일 수정**: 건별이 아니라 파일 단위로, 모두 맞을 때만 적용됩니다.
- **빠진 검증 4개**: 변경 대상이 0개인 경우, FR 없는 범위 항목, 참고 파일도 내용도 없는 신규 파일, 바꿀 곳 없는 수정 파일은 더 이상 따로 잡지 않습니다. 문서의 표가 비어 보이는 것으로만 드러납니다.

## 아직 확인하지 못한 것

- 재분석·재설계·참고구현재조사 경로는 새 구조로 돌려보지 않았습니다.
- 참고 파일 없이 새로 만드는 파일 단계(`newCreate`)는 이번 실행에 해당 파일이 없어 실제로 타지 않았습니다.
- 일반 경로(`step01`~`step07`)는 이번에 건드리지 않았고 LLM 실행도 하지 않았습니다.
- `dstone-knowledge`가 내려가 있어서 참고 구현 조사는 파일 Tool로만 했습니다.

`docs/09.dstone-ai-engine.md`와 `CLAUDE.md`는 현재 구조로 고쳤습니다. 커밋은 하지 않았습니다.

---

## 질문

git 에 올려줘

---

## 답변

GitHub `origin/main`에 올렸습니다 (커밋 `ff94d56`).

- **브랜치**: 기존 이력이 모두 `main`에 직접 쌓여 있어서 같은 방식으로 `main`에 바로 올렸습니다.
- **제외한 것**: `docs/temp` 아래 두 건은 이번 작업 전부터 있던 변경이라 커밋에 넣지 않았습니다.
  - `Agent_Workflow_안정화_방안.docx` 삭제
  - `dstone-workflow-design.md` 신규(미추적)

이 두 건도 함께 올리길 원하시면 말씀해 주세요.
