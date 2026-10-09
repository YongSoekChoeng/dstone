# dstoneaiengine_에서_ds

> 생성일시: 2026-10-09 19:58

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

---

## 질문

pilot-workflow.yml 에서 step 들의 id 를 일관성있게 순차적으로 표현해줘. 유관 파일들도 마찬가지로 바꿔주고.

---

## 답변

step id를 `step00_readRequest`, `gen01_…`, `new01_…` 형식으로 일괄 변경했습니다. 이름 규칙 설명을 주석과 문서에 넣고 기동 검증 테스트를 돌립니다.

---

## 답변

`pilot-workflow.yml`의 step id 33개를 `<경로><순번 두 자리>_<하는 일>` 형식으로 바꿨습니다. 정의 파일 기동 검증을 포함한 테스트 64개는 통과했고, 서버를 띄운 실행은 다시 하지 않았습니다. 커밋은 아직 하지 않았습니다.

## 새 id

| 경로 | 예전 | 지금 |
|---|---|---|
| 공통 | `step00`, `classify` | `step00_readRequest`, `step01_classify` |
| 일반 | `step01` ~ `step07` | `gen01_requirements`, `gen02_impact`, `gen03_review`, `gen04_approveAnalysis`, `gen05_design`, `gen06_approveDesign`, `gen07_develop` |
| 신규 개발 | `newRef` ~ `newTasksDoc` | `new01_reference` ~ `new24_tasksDoc` |

신규 개발 경로 24개는 실행 순서대로 번호가 붙습니다.

- 분석: `new01_reference`, `new02_readSources`, `new03_analyze`, `new04_checkAnalysis`, `new05_requirementsDoc`, `new06_impactDoc`, `new07_approveAnalysis`
- 설계: `new08_designPrep`, `new09_design`, `new10_checkDesign`, `new11_editList`, `new12_checkEdits`, `new13_designDoc`, `new14_approveDesign`
- 구현: `new15_copyList`, `new16_copy`, `new17_createList`, `new18_create`, `new19_edit`, `new20_developList`, `new21_developPrep`, `new22_develop`, `new23_apply`, `new24_tasksDoc`

## 함께 바꾼 파일

- `pilot-workflow.yml`: id와 `next`/`onFailure`/`routes`, 주석 전체. 맨 위에 이름 규칙 설명도 넣었습니다.
- `agents/pilot/*.yml` 6개: 주석에 적힌 step id.
- `docs/09.dstone-ai-engine.md`, `CLAUDE.md`: 본문의 id와 이름 규칙 표.

## 바꾸지 않은 것

- **state 이름**: `state.classify`, `state.newApproval` 같은 저장 위치 이름은 그대로 뒀습니다. 이것까지 맞추길 원하시면 말씀해 주세요.
- **다른 Workflow**: `testApp-workflow`의 `step01`/`step02`와 샘플들은 대상이 아니라고 보고 건드리지 않았습니다.
- **테스트 자료**: `src/test/resources/pilot/`의 JSON에 예시 오류 문구로 들어 있는 `newDesign[1]`은 그대로입니다.

## 알아둘 점

- id를 바꾸면서 주석과 문서의 한글 조사가 어색해진 곳이 있습니다(예: "step00_readRequest이"). 뜻은 통해서 일일이 고치지 않았습니다.
- 바꾸기 전에 돈 실행의 이력에는 예전 id가 그대로 보입니다.

---

## 질문

아냐 나는 일률적으로 step00 ~ step99 이렇게 바꾸었으면 좋겠어. 아이디만 보고도 순서를 짐작할 수 있도록.

---

## 답변

step id를 `step00` ~ `step32`로, 파일에 적힌 순서대로 다시 매겼습니다. 정의 파일 기동 검증을 포함한 테스트 64개는 통과했고, 서버를 띄운 실행은 다시 하지 않았습니다. 커밋은 아직 하지 않았습니다.

| id | 경로 | 하는 일 |
|---|---|---|
| `step00`, `step01` | 공통 | 요청서 읽기, 경로 고르기 |
| `step02` ~ `step08` | 일반 | 요구사항, 영향도 분석, 리뷰, 승인, 설계, 승인, 구현 |
| `step09` ~ `step15` | 신규 개발 – 분석 | 참고 구현 고르기, 파일 읽어 두기, 요구사항+구성 정의, 검증, 문서 2종, 승인 |
| `step16` ~ `step22` | 신규 개발 – 설계 | 설계 준비, 파일별 설계, 검증, 고칠 설계 고르기, 미리 고쳐 보기, 설계서, 승인 |
| `step23` ~ `step32` | 신규 개발 – 구현 | 복사, 새로 쓰기, 있는 파일 고치기, 빼고 더하기, 결과 문서 |

- **함께 바꾼 파일**: `pilot-workflow.yml`(id, `next`/`onFailure`/`routes`, 주석), `agents/pilot/*.yml` 6개의 주석, `docs/09.dstone-ai-engine.md`, `CLAUDE.md`.
- **일반 경로 번호가 하나씩 밀렸습니다**: 예전 `step01`~`step07`이 지금은 `step02`~`step08`입니다. 문서의 과거 측정 기록도 새 번호로 바꿔 적었습니다.
- **step을 중간에 넣으면** 그 뒤 번호를 전부 다시 매겨야 합니다. 이 규칙은 YAML 맨 위 주석에 적어 뒀습니다.
- **state 이름**(`state.classify`, `state.newApproval` 등)은 step id가 아니라서 그대로 뒀습니다.

---

## 질문

응 git에 올려줘

---

## 답변

GitHub `origin/main`에 올렸습니다 (커밋 `f40f088`).

지난번과 같이 `docs/temp` 아래 두 건(`Agent_Workflow_안정화_방안.docx` 삭제, `dstone-workflow-design.md` 신규)은 이번 작업 전부터 있던 변경이라 커밋에 넣지 않았습니다.

---

## 질문

응 알았어.

---

## 답변

네, 필요하실 때 말씀해 주세요.

---

## 질문

<pasted_content id="d25b">
net.dstone.ai.tools.template.TemplateRenderer 의 주석에 있는 아래의 내용에 대해서 자세히 설명해줘.

 틀(Mustache 템플릿)에 데이터를 끼워 글자를 만듭니다. 파일을 읽거나 쓰지 않아서 단위 테스트로 그대로 확인할 수 있습니다.
 
 틀에서 쓸 수 있는 것은 이것뿐입니다(로직은 넣을 수 없습니다).
   {{이름}} / {{a.b.c}}        값을 그 자리에 넣습니다. 없으면 빈 글자입니다.
   {{#목록}} ... {{/목록}}      목록의 항목마다 되풀이합니다. 안에서는 항목의 필드를 {{이름}}으로, 항목 자체를 {{.}}으로 씁니다.
   {{#값}} ... {{/값}}          값이 있을 때만 보여 줍니다(없음, false, 빈 글자, 빈 목록이면 건너뜁니다).
   {{^값}} ... {{/값}}          값이 없을 때만 보여 줍니다.
   {{-index}}                  되풀이 안에서 몇 번째인지(1부터)
   {{#-first}} / {{^-last}}    되풀이의 첫 항목일 때만 / 마지막 항목이 아닐 때만 (표의 머리줄, 쉼표 넣기에 씁니다)
   {{#cell}} ... {{/cell}}     표의 한 칸에 넣을 글자로 바꿉니다(칸을 나누는 | 는 / 로, 줄바꿈은 빈칸으로).
</pasted_content id="d25b">

---

## 답변

`TemplateRenderer`는 "틀 글자 + 데이터 → 완성된 글자" 한 가지만 합니다. 틀 파일을 읽거나 결과를 저장하는 일은 `TemplateTool`이 하므로, 이 클래스는 입력과 출력이 모두 글자라서 단위 테스트(`TemplateRendererTest`)로 바로 확인됩니다.

"로직은 넣을 수 없다"는 말은 틀 안에서 계산, 비교(`==`, `>`), 함수 호출을 쓸 수 없다는 뜻입니다. 쓸 수 있는 것은 아래 7가지뿐입니다.

아래 예시는 모두 이 데이터를 넣는다고 가정합니다.

```json
{
  "title": "FAQ 메뉴",
  "reference": { "name": "샘플게시판" },
  "targets": [
    { "path": "/src/Faq.java",  "frs": ["FR-01", "FR-02"], "change": "새로 만든다" },
    { "path": "/src/menu.sql",  "frs": [],                 "change": "메뉴 | 추가\n한 줄" }
  ],
  "questions": []
}
```

## 1. `{{이름}}` / `{{a.b.c}}` — 값 넣기

그 자리에 값을 그대로 넣습니다. 점으로 안쪽 필드를 가리킵니다.

```
제목: {{title}} / 참고: {{reference.name}} / 담당: {{owner}}
```
```
제목: FAQ 메뉴 / 참고: 샘플게시판 / 담당: 
```

- 없는 값(`owner`)은 오류가 아니라 빈 글자가 됩니다.
- `<`, `&` 같은 글자를 HTML용으로 바꾸지 않고 있는 그대로 넣습니다. 마크다운과 소스 코드를 만들기 때문입니다.
- 맵이나 리스트를 통째로 넣으면 JSON 글자가 됩니다.

## 2. `{{#목록}} ... {{/목록}}` — 되풀이

목록의 항목 수만큼 안쪽을 반복합니다. 안에서는 항목의 필드를 이름만으로 씁니다.

```
{{#targets}}
- {{path}}: {{change}}
{{/targets}}
```
```
- /src/Faq.java: 새로 만든다
- /src/menu.sql: 메뉴 | 추가
한 줄
```

- 항목이 글자인 목록(`frs`)은 필드가 없으므로 항목 자체를 `{{.}}`으로 씁니다.
- 안쪽에서 찾지 못한 이름은 바깥에서 찾습니다. 그래서 `{{#frs}}{{.}} ({{path}}){{/frs}}`처럼 바깥 항목의 `path`를 함께 쓸 수 있습니다.
- `{{#targets}}`처럼 태그만 있는 줄은 결과에 빈 줄을 남기지 않습니다.
- 목록 안의 `null`은 없는 항목으로 보고 건너뜁니다. forEach step에서 실패한 자리가 `null`로 오기 때문에 넣은 규칙입니다.

## 3. `{{#값}} ... {{/값}}` — 있을 때만

2번과 같은 문법인데, 값이 목록이 아니면 "있으면 한 번 보여 준다"로 동작합니다.

```
{{#title}}제목이 있습니다: {{.}}{{/title}}
```

없는 값, `false`, 빈 글자 `""`, 빈 목록 `[]`이면 안쪽 전체를 건너뜁니다.

## 4. `{{^값}} ... {{/값}}` — 없을 때만

3번의 반대입니다. 보통 둘을 짝지어 "있으면 값, 없으면 기본 문구"를 만듭니다.

```
{{#questions}}
- {{.}}
{{/questions}}
{{^questions}}
없음
{{/questions}}
```
```
없음
```

`if / else`에 해당하는 유일한 방법입니다. 다만 "있다/없다"만 따질 수 있고 "값이 '수정'이면" 같은 비교는 못 합니다.

## 5. `{{-index}}` — 순번

되풀이 안에서 몇 번째 항목인지를 1부터 넣습니다.

```
{{#targets}}
## {{-index}}. {{path}}
{{/targets}}
```
```
## 1. /src/Faq.java
## 2. /src/menu.sql
```

## 6. `{{#-first}}` / `{{^-last}}` — 첫 항목일 때만 / 마지막이 아닐 때만

되풀이 안에서만 쓰는 특별한 이름입니다.

**쉼표로 잇기** — 마지막 항목 뒤에는 쉼표를 붙이지 않습니다.
```
{{#frs}}{{.}}{{^-last}}, {{/-last}}{{/frs}}
```
```
FR-01, FR-02
```

**목록이 있을 때만 머리줄 넣기** — 머리줄을 되풀이 밖에 두면 목록이 비어도 표 머리만 덩그러니 나옵니다. 첫 항목일 때만 찍으면 목록이 빌 때 표 전체가 사라집니다.
```
{{#renames}}
{{#-first}}
| 참고 | 신규 |
|---|---|
{{/-first}}
| {{from}} | {{to}} |
{{/renames}}
```

## 7. `{{#cell}} ... {{/cell}}` — 표의 한 칸에 넣기

Mustache 표준이 아니라 이 엔진이 넣어 둔 도우미입니다. 마크다운 표는 `|`로 칸을 나누고 한 줄이 한 행이라서, 값에 `|`나 줄바꿈이 있으면 표가 깨집니다. 감싼 부분의 `|`를 `/`로, 줄바꿈을 빈칸으로 바꿉니다.

```
| {{path}} | {{#cell}}{{change}}{{/cell}} |
```
```
| /src/menu.sql | 메뉴 / 추가 한 줄 |
```

## 틀로 할 수 없는 것

- 값 비교나 계산 (예: "유형이 수정인 것만", "개수 세기")
- 두 목록을 서로 맞춰 보기 (예: "FR마다 그 FR을 다루는 파일 찾기")
- 목록 전체에 대한 판단 (예: "어느 항목에도 질문이 없으면 '없음'")

이런 것이 필요하면 틀에 넣기 전에 데이터를 그 모양으로 만들어야 합니다. `filterList`로 먼저 거르거나, Agent의 output 스키마를 그 모양으로 정하는 식입니다.

실제 사용 예는 `definitions/templates/pilot/newdev-design.md.mustache`에 가장 많이 들어 있습니다.
