# localcommandcaveatTh

> 생성일시: 2026-11-05 12:36

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="1d5a">
dstone-ai-engine 에서 아래의 sm-workflow 를 튜닝하고자 해. 문제의 원인을 분석부터 해줘.
<가장 큰 문제점>
- tool calling 이 많음.
- 읽었던 파일을 다시 읽는 부분이 많고 따라서 대화창이 너무 비대해짐.
- 위의 결과로서 너무 느림.
</pasted_content id="1d5a">

---

## 답변

sm-workflow 정의와 Agent 파일부터 보고, 실제 실행 로그와 DB 기록으로 원인을 확인하겠습니다.

---

## 답변

정의는 확인했습니다. 이제 실제 실행 기록(로그의 `dstone-ai-engine agent:` 통계 줄, Tool 호출 줄)을 보겠습니다.

---

## 답변

Windows 쪽 인스턴스 로그(34MB, 오늘 갱신)가 실제 실행으로 보입니다. 거기서 Agent 통계 줄을 뽑겠습니다.

---

## 답변

Agent 한 번 호출에 입력 토큰이 15만~180만 개입니다. 라운드별 줄과 Tool 인자 줄을 보고 중복 읽기를 세겠습니다.

---

## 답변

영향도분석서가 설계 단계에 무엇을 넘기는지 확인하려고 실제 산출물을 찾는 중입니다.

---

## 답변

산출물은 되돌리기(rewind)로 지워진 듯합니다. 로그에 남은 writeFile 내용으로 대신 확인하겠습니다.

---

## 답변

# sm-workflow 원인 분석

오늘 Windows 쪽 인스턴스가 남긴 실행 로그(`D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log`, 07:48~11:42 사이 3회 실행, Agent 호출 25건)에서 라운드별 통계와 Tool 인자를 모두 뽑아 집계했습니다.

## 핵심 결론

느린 이유는 Tool 호출 수 자체보다 **"한 번 호출할 때마다 대화 전체를 다시 보내는 구조"에 "라운드 수"와 "큰 Tool 결과"가 곱해지기 때문**입니다.

| Agent 호출 | 라운드 | 마지막 라운드 입력 | 입력 토큰 합계 | 시간 |
|---|---|---|---|---|
| developer (08:43) | 35 | 94k | **184만** | 314초 |
| architect (11:34) | 29 | 87k | **171만** | — |
| architect (10:31) | 21 | 116k | **151만** | 428초 |
| impact (평균) | 10~12 | 35~60k | 15만~45만 | 50~200초 |

개발 Agent는 첫 라운드가 14k 토큰이었는데 35회차에는 94k가 됐습니다. 그 사이 34번의 호출이 매번 앞의 내용을 전부 다시 보냈습니다.

## 원인 (영향이 큰 순서)

**1. 파일을 범위 없이 통째로 읽음**
- 범위 지정은 프롬프트로만 막고 있어서 지켜지지 않았습니다. "필요한 범위만 읽는다"는 문장이 있지만 Tool은 범위가 없어도 최대 30,000자까지 그대로 돌려줍니다.
- `readFileLines` 200건 중 **146건(73%)이 범위 없이 호출**됐고, 읽은 104만 자 가운데 92만 자가 이 전체 읽기에서 나왔습니다.
- 예: architect가 `BaseVO.java`를 통째로 읽었습니다(32,275자).

**2. step 사이에서 같은 파일을 다시 읽음 (말씀하신 "다시 읽기"의 실체)**
- 한 Agent 호출 안에서 겹쳐 읽은 경우는 의외로 적습니다(대부분 0~3건).
- 대신 step이 바뀔 때마다 처음부터 다시 조사합니다. 10:02 실행에서는 읽은 파일 39개 중 22개를 두 번 이상의 Agent 호출이 따로 읽었습니다. `FileService.java`, `SampleBoardController.java`, `sampleBoard.jsp` 등은 5번씩 읽혔습니다.
- 이유는 영향도분석서가 **파일 경로만 넘기고 줄 범위나 내용은 넘기지 않기** 때문입니다. impact 프롬프트도 신규기능은 "내용은 읽지 않는다"고 정해 두었습니다. 그래서 architect가 다시 열고, developer도 또 엽니다.
- 승인 화면에서 재분석을 고르면 같은 조사를 처음부터 되풀이합니다. 10:02 실행에서 impact가 4번, architect가 2번 돌았습니다.

**3. 한 라운드에 Tool을 하나씩만 부름**
- architect(11:34)의 11~21회차는 매 라운드에 `searchInFiles`나 `readFileLines`를 하나씩만 요청했습니다.
- 라운드 하나마다 60k 토큰 이상을 다시 보내므로, 한꺼번에 요청했다면 라운드를 크게 줄일 수 있었습니다.

**4. architect가 조사 범위를 벗어남**
- "공통 유틸·프레임워크 내부는 따라가지 않는다" 규칙이 impact 프롬프트에만 있고 architect에는 없습니다.
- 그 결과 architect가 `genexon.js` 내부(`PopWindowOpen`, `initKendoUI_*`, `PopWindowClose2`)와 kendo 라이브러리까지 추적했습니다. `kendo` 검색 한 번의 결과가 14,894자였습니다.
- 소스 루트 전체를 `imageBoard`로 검색한 결과도 15,444자였습니다.

**5. 검색 결과가 큼**
- 10:02 실행 기준 `searchInFiles` 50건이 115k자, `knowledgeSearch` 12건이 114k자를 돌려줬습니다. `knowledgeSearch`는 한 건에 평균 약 10k자, 많게는 16k자였습니다.
- 이 결과는 모두 이후 라운드마다 다시 전송됩니다.

**6. `reasoning: high`로 라운드 하나가 오래 걸림**
- architect(10:43)는 9회차 한 라운드에만 263초를 썼고, `writeFile` 없이 끝나 `requiredTools` 재시도 대상이 됐습니다.
- 410초 가운데 대부분이 Tool 호출이 아니라 추론 시간이었습니다.

**7. 그 밖의 비효율**
- `sm-ready-agent`가 11:13 실행에서 `getToDate`를 12번 반복했습니다. 날짜 하나 얻는 데 LLM을 13번 불렀습니다.
- developer는 입력으로 받은 `03-2.taskitems.md`를 파일로 다시 읽기도 했습니다.

## 개선 방향 후보

| # | 방안 | 줄어드는 것 | 바꿀 곳 |
|---|---|---|---|
| A | `readFileLines`를 범위 없이 부르면 앞의 일정 줄만 주고 "범위를 지정하라"고 안내 (범용 Tool 규칙) | 원인 1 | 엔진 Tool |
| B | 영향도분석서에 대상마다 `파일:시작-끝 줄`을 적게 하고, 그 범위를 TOOL step(`readItemFiles` 등)이 LLM 없이 한 번 읽어 architect·developer 입력으로 넘김 | 원인 2 | Workflow·프롬프트 |
| C | Agent 호출 안에서 이미 읽은 파일·범위를 다시 요청하면 짧은 안내로 대신 답하는 읽기 캐시 (CLAUDE.md에 "미구현"으로 남아 있는 항목) | 원인 2 일부 | 엔진 |
| D | architect 프롬프트에 "공통 프레임워크 내부를 따라가지 않는다", "필요한 읽기는 한 라운드에 모아서 요청한다" 추가 | 원인 3, 4 | 프롬프트 |
| E | `searchInFiles`·`knowledgeSearch` 결과 한도를 낮춤 | 원인 5 | 설정 |
| F | `sm-ready-agent`를 TOOL step(`getToDate`)으로 바꿈 | 원인 7 | Workflow |
| G | architect·impact의 `reasoning`을 `medium`으로 낮춰 측정 | 원인 6 | 정의 파일 (측정 필요) |

효과는 **B > A > D > C** 순으로 클 것으로 봅니다. B가 들어가면 architect와 developer가 소스를 조사하며 여러 라운드를 도는 일 자체가 크게 줄어듭니다. 어느 방안부터 진행할지 알려 주세요.

집계에 쓴 스크립트는 `/tmp/claude-1000/-app-dstone/34e7502c-ae01-48fe-a521-235d051b584d/scratchpad/dup.py`에 있습니다.

---

## 질문

sm-ready-agent를 TOOL step(getToDate)으로 바꿈 => 이렇게 하는 이유는 ?

---

## 답변

`sm-ready-agent`를 TOOL step으로 바꾸자는 이유는 이 step에 **LLM이 판단할 일이 없기** 때문입니다. 시간 절약은 부수 효과이고 크지 않습니다.

## 이유

**1. 하는 일이 정해진 계산뿐입니다**
- 이 step은 현재 시각을 `yyyyMMdd-HHmm`으로 만들어 입력 경로 뒤에 붙입니다. 같은 입력이면 답이 하나뿐입니다.
- 이런 일은 LLM 없이 처리하는 편이 맞습니다. 이 Workflow도 같은 원칙을 이미 따르고 있습니다. 맨 위 주석에 "문서는 LLM이 아니라 TOOL step이 읽어서 넘긴다"고 적혀 있습니다.

**2. 실제 실행에서 불안정했습니다**
- 11:13 실행에서 `getToDate`를 12번 되풀이했고 LLM을 13번 불렀습니다.
- 다른 두 실행에서도 한 번이면 될 호출을 2번 했습니다.
- 지금은 경로를 LLM이 직접 조합합니다. 구분자를 빠뜨리거나 날짜 형식을 바꿀 여지가 있습니다.

**3. 이 값이 틀리면 Workflow 전체가 틀어집니다**
- `state.ready.base_dir`는 step02~step13의 모든 문서 경로와 개발 산출물 경로(`dev-resources/`)의 기준입니다.
- 잘못 만들어지면 이후 step이 모두 엉뚱한 폴더에서 읽고 씁니다. TOOL step으로 바꾸면 이 값이 항상 같은 방식으로 정해집니다.

**4. 시간과 토큰은 조금 줄어듭니다**
- 이 step은 실행마다 4~9초, 입력 토큰 2천~1만 3천 개를 썼습니다.
- 전체 실행 시간에 비하면 작아서, 이것만으로 바꿀 이유는 되지 않습니다.

## 바꾸면 이렇게 됩니다

```yaml
  - id: step00
    name: 사전작업
    type: TOOL
    tool: getToDate
    with:
      format: yyyyMMdd-HHmm
    save: now
    next: step01
    onFailure: FAIL
```

이후 step에서는 `${state.ready.base_dir}` 대신 `${input}/${state.now}`를 씁니다.

## 바꾸기 전에 알아 둘 점

- **참조를 모두 고쳐야 합니다.** 엔진 식은 값을 읽기만 하고 계산 결과를 따로 저장하지 못합니다. 그래서 `${state.ready.base_dir}`를 쓰는 8곳을 `${input}/${state.now}`로 모두 바꿔야 하고, `state:` 선언도 `ready` 대신 `now`로 바꿔야 합니다.
- **Tool 이름 하나가 확인되지 않았습니다.** `getToDate`는 `tools/sample/DateTimeTool`에 있는 일반 Tool입니다(`getToDate(String format)`). TOOL step에서 인자 이름 `format`이 그대로 통하는지는 부팅 검사로 확인해야 합니다.
- **`sm-ready-agent.md`는 더 이상 쓰이지 않습니다.** 지울지 남길지 정해 주시면 됩니다.

진행할까요?

---

## 질문

workflow 의 step 을 크게 늘리지 않고 진행할 수 있는 방법이 있을까?

---

## 답변

네, 가능합니다. 앞서 제안한 방안 가운데 step을 늘리는 것은 **B 하나뿐**입니다. B도 step을 추가하지 않는 방식으로 바꿀 수 있습니다.

## step 수에 주는 영향

| 방안 | 바꿀 곳 | step 수 |
|---|---|---|
| A. 범위 없는 `readFileLines`는 앞부분만 주고 범위를 지정하라고 안내 | 엔진 Tool | 그대로 |
| C. 한 Agent 호출 안에서 이미 읽은 범위를 다시 읽으면 짧은 안내로 대신 답함 | 엔진 | 그대로 |
| D. architect 프롬프트에 조사 범위 제한, 읽기는 한 라운드에 모아서 요청 | 정의 파일 | 그대로 |
| E. 검색 결과 한도 낮춤 | 설정 | 그대로 |
| F. step00을 TOOL step으로 바꿈 | Workflow | 그대로 (바꿔치기) |
| G. `reasoning` 조정 | 정의 파일 | 그대로 |
| **B. 대상 코드를 TOOL step이 읽어서 넘김** | Workflow | **늘어남** |

## B를 step 없이 바꾸는 방법: 앞 step이 이미 읽은 코드를 문서에 남긴다

step 사이의 중복 읽기는 앞 Agent가 읽은 내용이 다음 Agent에 전달되지 않아서 생깁니다. 그런데 문서는 이미 TOOL step(step08, step11, step12)을 거쳐 다음 Agent의 입력으로 들어가고 있습니다. 그러니 **문서에 필요한 코드 조각을 담기만 하면** step을 늘리지 않아도 됩니다.

**영향도분석서(02.impact.md)**
- 수정 대상마다 `파일:시작-끝 줄`과 고칠 부분의 원문을 앞뒤 몇 줄과 함께 적습니다.
- 그러면 architect는 수정사항 파일을 다시 열지 않고, 입력으로 받은 원문으로 '변경 전'을 씁니다.

**설계서(03-1.design.md)**
- '변경 전'이 이미 원문 그대로 들어 있으니, developer는 그것으로 바로 `replaceInFile`을 부릅니다.
- 지금 프롬프트는 신규 파일을 만든 뒤 "readFileLines로 한 번 읽는다"고 시킵니다. 이 규칙을 빼고, 실패했을 때만 읽게 합니다.
- 로그를 보면 developer는 한 번 호출에 `readFileLines`를 19~21번 불렀습니다.

**대가**
- 영향도분석서가 길어지고, 그만큼 impact Agent가 출력할 글자도 늘어납니다.
- 그래서 파일 전체가 아니라 **바꿀 부분 앞뒤 몇 줄까지만** 담도록 제한합니다.
- 신규기능의 참고 파일은 지금처럼 architect가 한 번 읽습니다. 구조를 알아야 이름 바꿈 표를 쓸 수 있어서, 이 읽기는 줄이기 어렵습니다.

## 재분석 반복에 대해서

10:02 실행에서 파일을 5번씩 읽은 데에는 승인 화면에서 영향도 재분석을 고른 탓도 있습니다. 이 실행에서 impact가 4번 돌았습니다. 재분석 때 이전 영향도분석서를 입력으로 넘기면 지적된 부분만 고칠 수 있습니다. 다만 재분석이 step08 이전에서 돌아오면 `state.impact`가 아직 없어 넘길 수 없습니다. 이 부분은 실제로 적용해 보며 판단하는 편이 낫습니다.

## 추천 진행 순서 (모두 step 추가 없음)

1. **정의 파일만 수정**: F, D, B'(문서에 코드 조각 담기), developer의 불필요한 읽기 제거
2. **엔진 수정**: A, C (범용 동작이라 다른 Workflow에도 효과)
3. **측정 후 조정**: E, G

1번은 코드 빌드 없이 디자이너나 파일 저장만으로 반영되므로, 1번 적용 → 실제 요청 1회 실행 → 로그 비교 순서로 가면 효과를 바로 확인할 수 있습니다. 이 순서로 진행할까요?

---

## 질문

응 모두 진행해줘.

---

## 답변

진행하겠습니다. 먼저 손댈 엔진 코드(파일 Tool, Tool 예산 콜백, 결과 압축)와 sm 관련 테스트를 확인합니다.

---

## 답변

엔진 쪽 손볼 곳(파일 읽기 Tool, Agent 호출마다 붙는 Tool 포장지, 결과 압축)을 읽었습니다. 이제 Tool을 붙이는 `AgentExecutor.buildSpec()`을 봅니다.

---

## 답변

Now I'll wire it into `AgentExecutor` and the compaction advisor.

---

## 답변

엔진 연결을 마쳤습니다. E를 위해 `KnowledgeTool`의 1,500자 고정값을 봅니다.

---

## 답변

이제 `conf/application.yml`에 새 설정을 넣습니다.

---

## 답변

엔진 코드를 컴파일해 보겠습니다.

---

## 답변

컴파일이 통과했습니다. 이제 정의 파일을 고칩니다. 먼저 Workflow의 step00을 TOOL step으로 바꿉니다(F).

---

## 답변

Now the architect Agent (D + B').

---

## 답변

이제 새 동작을 확인하는 단위 테스트를 씁니다. 기존 `FileToolLooseMatchTest`가 `FileTool`을 어떻게 만드는지 봅니다.

---

## 답변

123건 모두 통과했습니다. `sm-workflow` 부팅 검사에서 경고가 없었는지도 확인합니다.

---

## 답변

경고도 없습니다. 이제 문서를 맞춥니다. `docs/09.dstone-ai-engine.md`에서 관련 부분을 찾습니다.

---

## 답변

문서에 반영했습니다. CLAUDE.md의 `sm-workflow` 설명과 "읽기 캐시 미구현" 문구도 고칩니다.

---

## 답변

아홉 가지 변경을 모두 반영했고, step 수는 늘리지 않았습니다. 단위 테스트를 포함한 전체 테스트 123건(실제 LLM 없이)이 통과했고, 바뀐 정의로 엔진이 경고 없이 뜹니다. 다만 **실제 LLM으로 sm-workflow를 끝까지 돌려 보지는 않았기 때문에, 시간이 얼마나 줄었는지는 아직 모릅니다.**

## 바꾼 것

| # | 내용 | 파일 |
|---|---|---|
| F | step00을 `getToDate` TOOL step으로 바꿨습니다. 시각은 `state.now`에 저장되고, 뒤의 9곳은 `${input}/${state.now}`로 경로를 만듭니다 | `sm-workflow.yml` (1.3.0) |
| B' | 영향도 Agent가 수정 대상마다 줄 범위를 적고, '현재 코드' 절에 고칠 곳의 원문(앞뒤 3줄, 한 곳에 40줄 이내)을 옮겨 둡니다. architect는 이 원문으로 '변경 전'을 쓰고 파일을 다시 열지 않습니다 | impact·architect Agent |
| D | architect는 공통 프레임워크·스크립트 내부를 따라가지 않고, 범위를 정해 읽고, 서로 상관없는 Tool은 한 번에 모아서 부릅니다 | architect Agent |
| — | developer는 복사한 파일을 미리 통째로 읽던 절차를 없앴고, 범위 읽기와 Tool 모아 부르기 규칙을 넣었습니다 | developer Agent |
| A | `readFileLines`를 범위 없이 부르면 앞 300줄까지만 주고, 범위를 정해 다시 부르라고 안내합니다. 엔진이 파일을 읽어 넘기는 `readItemFiles`는 줄 수로 자르지 않습니다 | `FileTool`, `FileSetTool` |
| C | 한 Agent 호출 안에서 이미 받은 범위를 또 읽으면 Tool을 실행하지 않고 "앞의 결과를 보라"는 안내만 돌려줍니다. 아래 경우에는 다시 읽게 둡니다: 파일이 바뀌었을 때, Tool로 그 파일을 고쳤을 때, 일부만 겹칠 때, 오래된 결과를 줄인 뒤 | `RereadGuardCallback`, `ReadHistory` (새 파일) |
| E | `knowledgeSearch` 한 건의 본문을 1,500자에서 800자로, `searchInFiles` 최대 건수를 100에서 50으로 줄였습니다 | `KnowledgeTool`, `application.yml` |

함께 고친 것:
- `docs/09.dstone-ai-engine.md`의 Tool 한도 설명과 설정 표, `CLAUDE.md`에 반영했습니다.
- 기존 `WorkFlowTest`를 새 작업 폴더 규칙에 맞게 고쳤습니다. 이 테스트는 `state.ready`가 없어 경로가 틀어진 덕에 우연히 통과하던 구조였습니다.
- 새 동작을 확인하는 `RereadGuardTest`(12건)를 추가했습니다.

## 하지 않은 것

- **G(`reasoning` 낮추기)는 하지 않았습니다.** 예전 측정 기록에 지금 모델(qwen3.8-27b)에서는 `low`/`medium`이 효과가 없다고 남아 있습니다. `none`은 조사하는 Agent가 같은 조회를 되풀이하게 만든 적이 있어 쓰지 않았습니다.
- **`sm-ready-agent.md`는 지우지 않았습니다.** 이제 어디서도 쓰이지 않습니다. 지울지는 정해 주세요.
- **커밋하지 않았습니다.**

## 적용하려면

실제로 돌고 있는 것은 Windows 쪽 인스턴스(`D:` 클론)라서 git으로 옮겨야 합니다.
- **정의 파일(F, B', D)**: 끌어온 뒤 정의 다시 읽기(`/api/ai/definitions/reload`)만 하면 바로 반영됩니다.
- **엔진 코드(A, C, E)**: 다시 빌드하고 재시작해야 반영됩니다.

같은 요청으로 한 번 돌린 뒤, 아래 스크립트를 로그에 다시 돌리면 오늘 오전 실행과 Agent별 라운드·토큰·중복 읽기를 비교할 수 있습니다.

`python3 -I /tmp/claude-1000/-app-dstone/34e7502c-ae01-48fe-a521-235d051b584d/scratchpad/dup.py <execution.log>`

커밋할까요?

---

## 질문

올려줘.

---

## 답변

커밋해서 `main`에 올렸습니다(`7289e473`). 로컬과 원격이 같은 상태입니다. 그동안 커밋해 온 방식대로 브랜치 없이 `main`에 바로 올렸습니다.

Windows 쪽 인스턴스에 반영하는 방법:

1. `D:/AppHome/framework/dstone`에서 `git pull`을 합니다.
2. 엔진 코드가 바뀌었으므로 `dstone-ai-engine`을 다시 빌드하고 재시작합니다. 정의 파일만 바뀌었다면 정의 다시 읽기만으로 됐겠지만, 이번에는 엔진 코드도 바뀌었습니다.
3. 같은 요청으로 sm-workflow를 한 번 돌린 뒤, 분석 스크립트로 오늘 오전 실행과 비교합니다.

```
python3 -I /tmp/claude-1000/-app-dstone/34e7502c-ae01-48fe-a521-235d051b584d/scratchpad/dup.py <execution.log>
```

이 스크립트는 세션용 임시 폴더에 있어 나중에 지워질 수 있습니다. 계속 쓰려면 저장소 안으로 옮겨 두겠습니다.

---

## 질문

<pasted_content id="1d5a">
dstone-boot>ai 의 Workflow 실행 관리 에서
windows 에서 실행한 아래의 실행건에 대해서 확인해줘.
executionId : 5e47439d-f711-471e-8284-19ca2ca17ffe
1. step06 을 진행 했는데 튜닝포인트가 있을까?
</pasted_content id="1d5a">

---

## 답변

Windows 쪽 실행 로그에서 이 실행의 step06 기록을 찾겠습니다.

---

## 답변

# step06(영향도 분석) 점검 결과

실행 `5e47439d…`의 step06은 LLM 12회, Tool 27회, 입력 토큰 253,225개, 63초가 걸렸습니다. 이번 실행에는 오늘 올린 변경이 반영돼 있습니다(Workflow 1.3.0, `knowledgeSearch` 한 건 약 750자).

## 좋아진 점

- **오전의 같은 step과 비교**: 입력 토큰은 15만~45만 개 범위의 아래쪽(25만), 시간은 32~195초에 비해 63초입니다. 다만 요청 내용이 달라서 정확한 비교는 아닙니다.
- **같은 범위를 다시 읽은 일은 0건**이었습니다.
- 큰 파일(`FileUtility`, `FileService`, `seed.sql`, `schema.sql`)은 범위를 정해서 읽었습니다.
- 1~6회차는 한 번에 Tool을 3~4개씩 모아서 불렀습니다.

## 튜닝 포인트 (효과가 큰 순서)

**1. 공통 기능 내부를 하나씩 따라감: 입력 토큰의 41%**

7~10회차는 라운드마다 Tool을 하나씩만 불러, 파일 업로드가 어떻게 동작하는지 따라 들어갔습니다(`MultipartHttpServletRequest` → `FileUtility.uploadFile` → `uploadFile` → `imageSave` → `FileService` 420~470줄). 이 4라운드가 매번 26k 토큰을 다시 보내 **105k 토큰**을 썼습니다.

필요한 답은 이미 7회차의 `MemberController`(169~201줄)에 있었습니다. 업로드를 실제로 쓰는 기존 코드입니다.

→ 프롬프트에 구체적인 예를 넣겠습니다. "업로드 같은 공통 기능은 그것을 쓰는 기존 Controller 한 곳만 찾아 적고, 공통 클래스는 열지 않는다."

**2. 신규기능인데 참고 파일 내용을 통째로 읽음**

요청은 신규기능 하나뿐인데, 샘플게시판의 Controller·Service·DAO·VO·mapper·JSP 6개 파일을 통째로 읽었습니다(16,431자). 프롬프트의 "신규기능은 참고 파일 목록과 등록 위치만 찾고 내용은 읽지 않는다"를 어긴 것입니다.

이 내용은 이후 7라운드 동안 계속 다시 실려 갔고, 다음 step인 architect가 같은 파일을 또 읽습니다. 문서에 적은 결과도 `(1-135)` 같은 줄 범위 정도였습니다.

→ 참고 파일은 검색으로 경로만 확인하고 `readFileLines`로 열지 않도록 규칙을 더 분명히 하겠습니다.

**3. 소스 루트 안에 이전 작업 산출물이 섞여 있음**

`D:/AppHome/testApp/workshop/changes/work001~004`에 이전 실행의 산출물이 남아 있습니다.
- knowledge 분석 결과에 이 산출물이 들어가 있어, 실제 소스에 없는 `ImageBoardVO`, `imageboard-mapper.xml`, `TBTS_IMAGE_BOARD`를 돌려줬습니다. 산출물 문서에도 "과거 workshop의 잔존 인덱스"라고 적혀 있습니다.
- 그래서 2~4회차에 진짜 있는지 확인하느라 검색 6건을 썼고, `workshop/changes/work004/20261104-1538`까지 뒤졌습니다.
- 1회차의 소스 루트 전체 검색(`게시판` 4,621자, `upload` 7,085자)에도 이 문서들이 섞였을 가능성이 큽니다.

→ 고칠 방법은 두 가지입니다.
- (a) dstone-knowledge의 `testApp` 분석에서 `workshop`을 빼고 새 리비전으로 다시 분석합니다.
- (b) 파일 Tool의 `exclude-dirs`에 `workshop`을 넣습니다.
- `testApp/workshop` 폴더를 옮기는 방법도 있지만, 사용자 데이터라 직접 정해 주셔야 합니다.

**4. `searchInFiles`에 파일 경로를 주면 실패함 (엔진, 범용)**

8회차에 `basePath`로 `FileService.java` 파일을 줬는데, 이 Tool은 폴더만 받아서 "디렉토리를 찾을 수 없습니다"로 끝났습니다. 그래서 9회차에 폴더로 다시 불렀고, 26k 토큰짜리 라운드 하나를 낭비했습니다.

→ 파일 경로를 주면 그 파일 하나만 검색하도록 고치겠습니다. 다른 Workflow에도 똑같이 도움이 됩니다.

## 예상 효과

1, 2, 4를 적용하면 7~10회차와 3~5회차의 전체 읽기가 사라집니다. 라운드는 12회에서 7~8회 정도, 입력 토큰은 대략 절반으로 줄어들 것으로 봅니다. 3은 이번 요청처럼 과거에 비슷한 작업을 한 적이 있을 때 헛된 확인 라운드를 막아 줍니다.

1·2(프롬프트)와 4(엔진)는 바로 진행할 수 있습니다. 3은 (a) knowledge 재분석과 (b) `exclude-dirs` 추가 중 어느 쪽으로 할지 정해 주세요.
