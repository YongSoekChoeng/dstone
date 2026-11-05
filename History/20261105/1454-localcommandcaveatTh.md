# localcommandcaveatTh

> 생성일시: 2026-11-05 14:54

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

---

## 질문

D:/AppHome/testApp/workshop/changes/work001~004에 이전 실행의 산출물이 남아 있습니다.
  ==>> D:/AppHome/testApp/workshop/changes 는 사용하지 않아. 대신 D:/WorkShop/00.SM 를 사용해.

---

## 답변

4081 포트는 WSL이 아니라 Windows 쪽 knowledge 인스턴스입니다. Workflow 설명의 예시 경로부터 실제 쓰는 위치로 고칩니다.

---

## 답변

알려 주셔서 다시 확인해 보니, 3번 원인을 잘못 짚었습니다. 바로잡겠습니다.

## 3번 원인 정정

- `D:/AppHome/testApp/workshop/changes/work001~004`에는 요청서(`00.request.md`)와 `error.log`만 있습니다. 이전 실행의 산출물은 없습니다.
- 헛된 확인의 진짜 원인은 **knowledge의 testApp 분석 결과가 지금 소스보다 오래됐기 때문**입니다.
  - 최신 리비전 58은 2026-11-04 17:49에 분석됐습니다. 이 리비전에는 `src/main/java/kr/co/gnx/sample/imageboard/ImageBoardController.java` 등 이미지게시판 파일이 들어 있습니다.
  - 지금 디스크의 `src/.../sample/` 아래에는 `board`만 있습니다. 분석한 뒤에 이 파일들이 소스에서 지워진 것으로 보입니다.
  - 그래서 knowledge가 이미 없는 파일을 돌려줬고, Agent는 2~4회차에 실제로 있는지 확인하느라 검색 6건을 썼습니다. `workshop` 폴더를 뒤진 것은 Agent가 출처를 `workshop`으로 짐작했기 때문입니다.
- **해결 방법**: testApp을 지금 소스로 다시 분석해 새 리비전을 만들면 됩니다. 소스를 되돌린 뒤에는 매번 다시 분석해야 같은 일이 생기지 않습니다.
- 4081 포트는 WSL이 아니라 **Windows 쪽 knowledge 인스턴스**입니다. 그래서 분석은 제가 시작하지 않았습니다. Windows 쪽 Knowledge 화면에서 실행하시거나, 제가 해도 된다고 하시면 아래 요청을 보내겠습니다.

```
POST http://localhost:4081/api/projects/testApp/analyses
{"revisionLabel": "<새 라벨>"}
```

## 함께 고친 것

`sm-workflow.yml` 설명의 입력 예시를 `D:/WorkShop/00.SM/work004`로 바꿨습니다(커밋 전). `pilot-workflow.yml`의 예시에도 `D:/AppHome/testApp/workshop/changes/work001[...]`이 남아 있는데, 이쪽도 바꿀지 알려 주세요.

## 진행 대기 중인 것

남은 튜닝 포인트는 다음 세 가지입니다.
1. 공통 기능 내부를 따라가지 않게 프롬프트에 구체적인 예 넣기
2. 신규기능이면 참고 파일을 열지 않게 규칙을 분명히 하기
4. `searchInFiles`가 파일 경로도 받게 고치기

이 세 가지를 바로 진행할까요?

---

## 질문

신규기능일 때 참고소스가 없으면 어떻게 구조를 만들지?

---

## 답변

참고 소스를 아예 안 읽자는 뜻이 아니었습니다. **읽는 곳을 architect(step09) 한 곳으로 모으자**는 제안입니다. 다만 질문하신 대로 "비슷한 기능이 아예 없을 때"는 지금 프롬프트에 빈틈이 있습니다.

## 1. 비슷한 기능이 있을 때 (이번 이미지게시판처럼)

지금은 같은 파일을 두 step이 각각 읽습니다.

| step | 지금 하는 일 | 구조를 알아야 하나 |
|---|---|---|
| step06 영향도 | 샘플게시판 6개 파일을 통째로 읽음(16,431자) | 아니요. 어느 파일을 참고할지, 메뉴와 테이블을 어디에 등록할지만 정하면 됩니다 |
| step09 설계 | 같은 파일을 다시 읽음 | 예. 이름 바꿈 표, 빼는 것, 더하는 것을 쓰려면 내용을 봐야 합니다 |

구조는 architect가 읽어서 설계서에 담고, developer는 그 설계서와 `copyFileWithReplace`로 파일을 만듭니다. 그래서 영향도 단계가 읽지 않아도 구조를 잃지 않습니다. 영향도 단계는 이번 산출물처럼 참고 파일 경로와 등록 위치만 넘기면 됩니다. 경로는 검색이나 knowledge로 확인할 수 있습니다.

## 2. 비슷한 기능이 없을 때 (지금 빈틈)

architect 프롬프트에는 "유사한 기능이 있으면 참고 파일로 적는다"만 있고, 없을 때 어떻게 할지가 없습니다. developer 쪽은 참고 파일이 없으면 `writeFile`로 내용 전체를 쓰게 돼 있습니다. 그런데 설계서에 그 내용을 어떤 기준으로 정할지 정해져 있지 않습니다. 그러면 프로젝트 관례와 동떨어진 코드가 나올 수 있습니다.

**이렇게 보완하려고 합니다.**

- **step06 영향도**: 비슷한 기능이 없으면 "참고 기능 없음"이라고 적습니다. 대신 층마다(화면, Controller, Service, DAO, mapper, VO) **관례를 볼 기존 파일 하나씩**의 경로를 적습니다. 여기서도 내용은 읽지 않습니다.
- **step09 설계**: 그 파일들을 한 번씩 읽고 프로젝트 관례를 확인합니다.
  - 확인할 것: 패키지와 이름 규칙, 상속하는 부모 클래스, 어노테이션, 응답 방식(jsonView 등), 페이징과 공통 함수 쓰는 법
  - 새 파일마다 "참고 파일 없음"이라고 밝히고, 위 관례를 따른 **내용 전체를 설계서에 그대로** 적습니다.
- **step13 개발**: 지금 규칙대로, 참고 파일이 없는 새 파일은 설계서 내용 그대로 `writeFile`로 만듭니다. 바꿀 것이 없습니다.

## 정리

| 경우 | 영향도(step06) | 설계(step09) |
|---|---|---|
| 비슷한 기능 있음 | 참고 파일 경로와 등록 위치만 적음 | 참고 파일을 읽고, 이름 바꿈 표·빼는 것·더하는 것을 적음 |
| 비슷한 기능 없음 | "참고 기능 없음" + 층마다 관례를 볼 파일 경로 | 관례 파일을 읽고, 새 파일 내용 전체를 적음 |

어느 경우든 소스 내용은 architect만 한 번 읽습니다.

앞서 말씀드린 1·4번과 함께 이 내용으로 영향도·설계 Agent 프롬프트를 고칠까요?

---

## 질문

앞서 말한 1번을 제외한 나머지를 적용하고 올려줘.

---

## 답변

1번(공통 기능 내부 따라가기 예시)을 빼고 나머지를 적용합니다. 먼저 영향도·설계 Agent 프롬프트의 현재 내용을 확인합니다.

---

## 답변

이제 4번입니다. `searchInFiles`의 `basePath`에 파일 경로를 주면 그 파일 하나만 검색하게 고칩니다.

---

## 답변

파일 하나만 검색하는 동작을 확인하는 테스트를 추가합니다.

---

## 답변

Tests pass (126/126). Next I'll update the docs and CLAUDE.md, then commit and push.

---

## 답변

1번을 뺀 나머지를 적용하고 `main`에 올렸습니다(`2022be9d`). 실제 LLM 없이 돌린 전체 테스트 126건이 통과했습니다. 실제 LLM으로 다시 돌려 보지는 않았습니다.

## 바뀐 것

**영향도 Agent (1.4.0)**
- 신규기능일 때 참고 파일은 `readFileLines`로 열지 않습니다. 경로는 knowledge나 검색 결과로 확인하고, 줄 범위나 코드도 적지 않습니다.
- 메뉴 등록, 설정, 테이블 생성문 파일처럼 등록할 위치만 범위를 정해 읽습니다.
- 비슷한 기능이 있으면 그 기능의 층별 파일을 '참고 파일'로 적습니다.
- 비슷한 기능이 없으면 "참고 기능 없음"이라고 적고, 층마다 프로젝트 관례를 볼 기존 파일 하나씩을 '관례 파일'로 적습니다.

**설계 Agent (1.4.0)**
- 참고 파일은 이 단계에서만 읽습니다.
- "참고 기능 없음"이면 관례 파일을 한 번씩 읽어 아래 관례를 확인합니다.
  - 이름 규칙, 부모 클래스, 어노테이션, 응답 방식, 페이징
- 그 관례를 따른 새 파일 내용 전체를 설계서에 그대로 적습니다. 개발 단계는 지금 규칙대로 그 내용을 `writeFile`로 만듭니다.

**`searchInFiles`**
- `basePath`에 파일 경로를 주면 그 파일 하나에서만 찾습니다.
- 테스트 3건(`FileToolSearchTest`)을 추가했습니다.

**함께 반영한 것**
- `sm-workflow` 설명의 입력 예시를 `D:/WorkShop/00.SM/work004`로 바꿨습니다.
- 엔진 문서와 CLAUDE.md도 고쳤습니다. 작업 폴더는 `D:/WorkShop/00.SM`이고 `testApp/workshop/changes`는 쓰지 않는다는 점도 CLAUDE.md에 적었습니다.

## Windows 쪽 반영 방법

- **정의 파일**(영향도·설계 Agent, Workflow): `git pull` 후 정의 다시 읽기만 하면 됩니다.
- **`searchInFiles` 변경**: 엔진 코드라서 다시 빌드하고 재시작해야 반영됩니다.

## 아직 남은 것

- **knowledge 재분석**: knowledge의 testApp 리비전 58에 지금 소스에 없는 이미지게시판 파일이 남아 있습니다. 다시 분석하지 않으면 다음 실행에서도 있는지 확인하느라 라운드를 쓰게 됩니다.
- **`pilot-workflow.yml`의 입력 예시**: 아직 `testApp/workshop/changes/...`로 남아 있습니다.

---

## 질문

executionId    212afffd-1781-4704-8de4-d3b07a66f2cc  가 오래걸리는 것 같아.

---

## 답변

# 실행 `212afffd…` 확인 결과

**지금은 실행 중이 아니라 사람의 승인을 기다리는 중입니다.** 12:55:44에 step06(영향도 분석)이 끝났고, 그 뒤로 `step07 영향도 분석 승인`에서 `WAITING_APPROVAL` 상태입니다. Workflow 실행 관리 화면에서 승인하면 step08로 넘어갑니다.

## step00~06 소요 시간

| step | 시간 |
|---|---|
| step00~01 (TOOL) | 1초 미만 |
| step02 요구사항 | 13초 |
| step04 리뷰 | 22초 |
| **step06 영향도** | **166초** |

## step06: 할 일은 절반으로 줄었는데 시간은 2배

| | 직전 실행 `5e47439d` | 이번 실행 |
|---|---|---|
| LLM 라운드 | 12 | **6** |
| Tool 호출 | 27 | **17** |
| 입력 토큰 | 253k | **108k** |
| 참고 파일 전체 읽기 | 6개 | **0개** (seed/schema만 범위를 정해 읽음) |
| 시간 | 63초 | **166초** |

이번 변경은 의도대로 동작했습니다. 그런데도 느려진 이유는 **LLM이 답하는 속도가 느렸기 때문**입니다.

- 166초 가운데 LLM 응답 대기가 약 136초, Tool 실행이 약 30초였습니다.
- 마지막 라운드는 출력 536토큰에 27.5초가 걸렸습니다(초당 약 20토큰). 직전 실행의 마지막 라운드는 비슷한 양(457토큰)에 3.5초였습니다.
- 같은 모델(OpenRouter의 qwen3.8-27b)이고 입력 크기도 비슷하니, 그 시간대에 OpenRouter 쪽 처리 속도가 떨어진 것으로 보입니다. 엔진이나 정의 파일로는 고칠 수 없습니다.
- 반복해서 느리다면 OpenRouter의 provider 고정이나 다른 모델을 검토할 수 있습니다.

## 그래도 줄일 수 있는 부분

**1. knowledge가 지운 파일을 계속 돌려줌 (재분석하면 해결)**
- 이번에도 knowledge가 이미 없는 `ImageBoard` 파일을 돌려줬습니다.
- 그래서 2회차에 `imageboard`, `ImageBoard`, `TBTS_IMAGE_BOARD`를 소스 루트 전체에서 다시 찾았습니다.
- 3회차에는 `D:/WorkShop/00.SM/work004`, 즉 **이전 실행들의 산출물 폴더**까지 검색해 3,329자를 받아 왔습니다.
- testApp을 다시 분석하면 이 라운드들은 없어집니다.

**2. 소스 루트 전체 검색이 느리고 결과가 큼**
- `D:/AppHome/testApp` 전체 검색이 7번 있었고, 한 번에 약 2초씩 걸렸습니다. 결과도 `게시판` 4,062자, `upload` 7,085자, `sampleBoard` 8,179자로 컸습니다.
- 소스 루트 아래의 `uploadData`, `workshop`(134MB, 실행 파일 포함)까지 훑기 때문입니다.
- 이 폴더들을 검색 제외 폴더(`exclude-dirs`)에 넣으면 빨라집니다. 다만 `exclude-dirs`는 폴더 이름 기준이라 모든 프로젝트에 똑같이 적용됩니다.

**3. 첫 `knowledgeSearch`가 15초 걸림**
- 두 번째 검색은 1초였습니다. knowledge 쪽에서 임베딩 작업이 쌓여 있어, 검색어를 임베딩하는 데 처음에 기다린 것으로 보입니다.

1번(testApp 재분석)을 Windows 쪽 knowledge(4081)에 제가 요청해도 될까요? 2번 `exclude-dirs`에 `uploadData`, `workshop`을 넣을지도 정해 주세요.

---

## 질문

This session is being continued from a previous conversation that ran out of context. The summary below covers the earlier portion of the conversation.

<system-reminder>
<tools-used-before-summary>Bash, Edit, Read, Write</tools-used-before-summary>
</system-reminder>
Summary:
1. Primary Request and Intent:
   - Tune `sm-workflow` in dstone-ai-engine. The problems are too many tool calls, re-reading files, a bloated conversation, and slowness. Analyze the causes first.
   - Explain why `sm-ready-agent` should become a TOOL step.
   - Proceed without greatly increasing the step count.
   - Apply all proposed fixes, then commit and push ("올려줘").
   - Check step06 of execution `5e47439d-f711-471e-8284-19ca2ca17ffe` for tuning points.
   - User correction: `D:/AppHome/testApp/workshop/changes` is NOT used. The work folder is `D:/WorkShop/00.SM` instead.
   - Explain how a new feature's structure is designed when there is no reference source.
   - Apply everything except point 1 (the common-function-internals prompt example) and push.
   - Latest: "executionId 212afffd-1781-4704-8de4-d3b07a66f2cc 가 오래걸리는 것 같아." (this execution seems to be taking long). Investigate.

2. Key Technical Concepts:
   - Spring AI tool loop. The whole conversation is re-sent every round, so cost is roughly rounds × accumulated tool results.
   - Per-call engine wrappers: `ToolCallBudgetCallback`, `ToolResultCompactionAdvisor`, `AgentCallStatsAdvisor` (logs `dstone-ai-engine agent:` lines), and ConfigTool's `dstone-ai-engine tool: [x] 호출 - 인자 ... / 결과 N자` lines.
   - Tool results arrive as JSON-quoted strings.
   - `FileAccessRule` decides READ/WRITE from a tool name and its args.
   - Workflow expressions: `${input}/${state.now}`.
   - The model is OpenRouter `qwen/qwen3.8-27b`; `reasoning` low/medium has no effect on it.
   - Environment: the Windows instance (D: clone) runs ai-engine on 8081 and knowledge on 4081. WSL has no listener on those ports, so localhost:4081 is the Windows instance.
   - Edit only under /app/dstone. Sync to the Windows clone via git push to main; the user pulls on Windows. Definition changes need only a reload; engine Java changes need a rebuild and restart.
   - User rules: Korean responses; no lambdas; Javadoc `<pre>`; friendly Korean comments; no Lombok; step ids stepNN.

3. Files and Code Sections:
   - `dstone-ai-engine/conf/definitions/workflows/sm/sm-workflow.yml` (version 1.3.0)
     - step00 is now a TOOL step:
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
     - All 9 `${state.ready.base_dir}` became `${input}/${state.now}`.
     - The `state` declaration `ready` became `now`.
     - A header comment was added.
     - The description example is now `D:/WorkShop/00.SM/work004`.
   - `agents/sm/sm-analyze-impact-agent.md` (1.4.0)
     - Ranged reads only; batch independent tool calls in one answer.
     - Table gets a "위치(줄 범위)" column.
     - Modifications get a '현재 코드' section: verbatim excerpt, ±3 lines, ≤40 lines, no "N: " prefix.
     - New features: reference files are NOT opened with readFileLines. Paths come from knowledge/searchInFiles. Only registration spots (menu, config, DDL) are read with a range.
     - If no similar feature exists: write "참고 기능 없음" and list one '관례 파일' (convention file) per layer.
   - `agents/sm/sm-architect-agent.md` (1.4.0)
     - Ranged reads; batch tool calls.
     - Do not follow common util, framework, or common-script internals.
     - readFileListAll only to find reference files missing from the impact doc.
     - Write '변경 전' from the impact doc's '현재 코드'.
     - Reference files are read only in this step.
     - On "참고 기능 없음": read the convention files and write each new file's full content into the design.
   - `agents/sm/sm-developer-agent.md` (1.3.0)
     - No longer reads a copied file whole before editing. If the design gives exact text, call replaceInFile directly; otherwise use searchInFiles plus a ranged readFileLines.
     - New `[Tool 쓰는 법]` section: ranged reads, batch calls.
   - `sm-ready-agent.md`: unused but left on disk.
   - `FileTool.java`
     - `readFileLines` with no `startLine` and no `endLine` calls `readLinesWithoutRange`. It returns the first `max-lines-without-range` lines (default 300, `DEFAULT_MAX_LINES_WITHOUT_RANGE`) plus the notice "(안내: startLine과 endLine 없이 불러서 앞 N줄만 반환했습니다 ... startLine=N+1 ...)".
     - New public method `readNumberedLines(path, start, end)` applies no line cap.
     - `lineNumberOf` is now public.
     - `searchInFiles` accepts a file path as `basePath` and then searches only that file, ignoring the pattern. The error message is now "디렉토리나 파일을 찾을 수 없습니다: ".
   - `FileSetTool.java`: `readItemFiles` now uses `readNumberedLines`.
   - New `runtime/agent/ReadHistory.java`: a per-call ledger.
     - Methods: `record(key, stamp, from, to)`, `covers(...)` (merges ranges), `forgetPath`, `clear`, `keyOf(tool, path)`, constant `END = MAX_VALUE`.
   - New `runtime/agent/RereadGuardCallback.java`
     - Wraps every tool. For readFileLines/readFile, if the requested range is already covered and the file's stamp (lastModified:length) is unchanged, it returns "(안내: 이 파일의 a~b줄은 이번 작업에서 이미 ...로 읽었고 ... )" without running the tool.
     - Records ranges parsed from the result (truncation markers "...(구간이 커서" and "(안내: startLine과 endLine 없이").
     - A WRITE tool on a path calls `forgetPath`.
     - Logs through slf4j.
   - `AgentExecutor.java`
     - Creates `ReadHistory` when `dstone.ai.agent.tool.reread-guard` is true (default true).
     - `budgeted(agent, callbacks, succeeded, readHistory)` puts the guard inside the budget wrapper.
     - Passes `readHistory` to `ToolResultCompactionAdvisor`, whose new 3-argument constructor clears it when compaction happens.
   - `Constants.Agent.TOOL_REREAD_GUARD`.
   - `KnowledgeTool.java`: the hit body cap now comes from `dstone.ai.tool.knowledge.max-content-chars` (default 1500).
   - `conf/application.yml`: `reread-guard: true`, `knowledge.max-content-chars: 800`, `file.max-lines-without-range: 300`, `max-search-matches: 50`.
   - Tests:
     - New `RereadGuardTest` (12).
     - New `FileToolSearchTest` (3).
     - `workflow/sm/WorkFlowTest` rewritten: step00 sets `now` with no LLM; design saved but taskitems missing makes step12 FAILED, using `put("now", ...)` and `jumpTo("step11")`.
     - Full suite: 126 pass with `-Ddstone.ai.test.llmcall=false`.
   - Docs: `docs/09.dstone-ai-engine.md` (re-read guard, new settings, searchInFiles file path) and `CLAUDE.md` (sm read/round pass paragraph and its follow-up).
   - Commits on main, pushed: `7289e473`, `2022be9d`.

4. Errors and fixes:
   - `FileTool.intProperty` treats ≤0 as the default value. I removed the wrong "0은 끔" (0 turns it off) config comment and the dead `maxLines<=0` check.
   - `FileSetTool` called `readFileLines(path,null,null)`, which the new cap would have affected. I switched it to `readNumberedLines`.
   - The old `WorkFlowTest` only passed because the path came out empty. I rewrote it for the new path rule.
   - I wrongly blamed the testApp workshop folder for stale results. The user corrected me: that folder is unused and D:/WorkShop/00.SM is used. The real cause is knowledge revision 58 (2026-11-04 17:49), which still contains `sample/imageboard` files that are gone from the source. Re-analysis is needed. 4081 is the Windows instance, so I did not trigger it.

5. Problem Solving:
   - Execution 5e47439d step06 (after the first change): 12 rounds, 27 tools, 253k tokens, 63 s, 0 re-reads.
   - Execution 212afffd step06 (after the second change, the latest):
     - 6 rounds, 17 tool calls, 108,435 input tokens (much fewer), but 165,859 ms.
     - The slowness is per-round LLM latency and reasoning output:
       - round 3: 24.3 s, 2,749 output tokens
       - round 4: 18.2 s
       - round 5: 47.6 s, 4,869 output tokens, the writeFile call
       - round 6: 27.5 s, 536 output tokens, no tool call
     - Compare the earlier run: rounds of 1–4 s, and 14.7 s for the writeFile round.
     - Tool time: the first knowledgeSearch took about 15 s (12:53:06 → 12:53:21); each root searchInFiles took about 2 s.
     - Wasted searches: it searched for imageboard/ImageBoard/TBTS_IMAGE_BOARD at the root again (stale knowledge), and searched its own work folder `D:/WorkShop/00.SM/work004` for "imageboard" (3,329 chars).
     - The impact Agent did not open reference files (the rule worked).
     - Current state: WAITING_APPROVAL at step07 since 12:55:44. It is not running; it is waiting for human approval.

6. All user messages:
   - (pasted) "dstone-ai-engine 에서 아래의 sm-workflow 를 튜닝하고자 해. 문제의 원인을 분석부터 해줘. <가장 큰 문제점> - tool calling 이 많음. - 읽었던 파일을 다시 읽는 부분이 많고 따라서 대화창이 너무 비대해짐. - 위의 결과로서 너무 느림."
   - "sm-ready-agent를 TOOL step(getToDate)으로 바꿈 => 이렇게 하는 이유는 ?"
   - "workflow 의 step 을 크게 늘리지 않고 진행할 수 있는 방법이 있을까?"
   - "응 모두 진행해줘."
   - "올려줘."
   - (pasted) "dstone-boot>ai 의 Workflow 실행 관리 에서 windows 에서 실행한 아래의 실행건에 대해서 확인해줘. executionId : 5e47439d-f711-471e-8284-19ca2ca17ffe 1. step06 을 진행 했는데 튜닝포인트가 있을까?"
   - "D:/AppHome/testApp/workshop/changes/work001~004에 이전 실행의 산출물이 남아 있습니다. ==>> D:/AppHome/testApp/workshop/changes 는 사용하지 않아. 대신 D:/WorkShop/00.SM 를 사용해."
   - "신규기능일 때 참고소스가 없으면 어떻게 구조를 만들지?"
   - "앞서 말한 1번을 제외한 나머지를 적용하고 올려줘."
   - "executionId 212afffd-1781-4704-8de4-d3b07a66f2cc 가 오래걸리는 것 같아."

7. Pending Tasks:
   - Answer the user about execution 212afffd (analysis done, not yet reported).
   - Open questions not decided by the user:
     - Re-analyze testApp in dstone-knowledge. This is on the Windows instance at 4081; ask before triggering `POST /api/projects/testApp/analyses`.
     - Whether to change the pilot-workflow.yml example path `D:/AppHome/testApp/workshop/changes/work001[...]`.
     - Whether to delete `sm-ready-agent.md`.
   - Point 1 (prompt example against following common-function internals) was explicitly excluded by the user.

8. Current Work:
   I was investigating "executionId 212afffd-1781-4704-8de4-d3b07a66f2cc 가 오래걸리는 것 같아." From the Windows log (extract saved at `/tmp/claude-1000/-app-dstone/34e7502c-ae01-48fe-a521-235d051b584d/scratchpad/run2.log`):
   - Started at 12:52:23. step02 took 13 s and step04 took 22 s.
   - step06 (impact) took 166 s with only 6 rounds and 108k tokens. Token use is lower than before, but per-round latency and reasoning output are high (the round times listed in section 5).
   - The first knowledgeSearch took about 15 s; root searches took about 2 s each.
   - The stale knowledge index (revision 58 with imageboard) caused imageboard re-checks, and it also searched its own work folder.
   - The execution is now WAITING_APPROVAL at step07, meaning it is waiting for the user's approval, not stuck.

9. Optional Next Step:
   Report the 212afffd findings to the user in Korean:
   - It is currently waiting at step07 for approval, not running.
   - step06 used fewer tokens (108k vs 253k) but took longer (166 s) because of per-round LLM latency and reasoning output (round 5: 47.6 s; round 6: 27.5 s for 536 tokens), which looks like provider/model speed rather than tool calls.
   - Smaller items: the first knowledgeSearch took 15 s; the stale knowledge revision 58 caused imageboard re-searches; it searched its own work folder.
   - Suggest re-analyzing testApp in knowledge (ask permission, since it is the Windows instance).
   - Optionally suggest restricting search to the source path. Do not apply changes without the user's confirmation.

If you need specific details from before compaction (like exact code snippets, error messages, or content you generated), read the full transcript at: /home/jysn007/.claude/projects/-app-dstone/34e7502c-ae01-48fe-a521-235d051b584d.jsonl
Continue the conversation from where it left off without asking the user any further questions. Resume directly — do not acknowledge the summary, do not recap what was happening, do not preface with "I'll continue" or similar. Pick up the last task as if the break never happened.

The messages after this summary are the most recent messages from before compaction, kept verbatim. The summary was written without seeing them, so something it says has not happened yet may already have happened in them.

---

## 질문

<pasted_content id="1d5a">
dstone-ai-engine 의 Workflow(sm-workflow)를 튜닝중이야.
방금전에 진행한 두 건은 제대로 동작한 것 같아.
그래도 튜닝포인트가 있으면 더 진행하고 싶어. 아래의 내용을 참고해서 튜닝 포인트를 알려줘.
* sm-workflow : 전산조직에서 SM(시스템 운영)을 하기위해 사용할 AI 에이전트의 Workflow.
1. 현재의 workflow 내에서 step을 많이 늘리지 않아야 함.
2. agent의 프롬프트가 너무 방만하고 길면 안됨.
3. 특정 업무에 너무 의존성이 있는 설정이나 프롬프트는 지양.
</pasted_content id="1d5a">

---

## 답변

# sm-workflow 추가 튜닝 포인트

방금 끝난 두 실행을 기준으로 봤습니다. `5bf95466`(work004, 이미지게시판 신규)과 `b8c5325f`(work003, 로그 메뉴 오류 수정과 FAQ 신규)입니다. step 수는 늘리지 않고, 프롬프트는 짧아지거나 그대로이며, 특정 업무에 묶이지 않는 것만 골랐습니다.

## 실측 요약 (Agent 호출 1회 기준)

| Agent | 5bf95466 | b8c5325f |
|---|---|---|
| 요구분석 | 2라운드, 28초 | 2라운드, 6~14초 |
| 리뷰 | 2라운드, 61초 | **2라운드, 182초 (출력 31k토큰)** / 재실행 30초 |
| 영향도 | 9라운드, 162k토큰, 67초 | 19라운드, 607k토큰, 99초 |
| 설계 | 16라운드, 637k토큰, 170초 | 9라운드, 281k토큰, 282초 |
| 개발 | **32라운드, 1.37M토큰, 253초** | 13라운드, 436k토큰, 121초 |

영향도와 설계 단계는 지난번 변경이 잘 먹혀서 크게 손댈 곳이 없습니다. 남은 낭비는 **개발 단계, 리뷰 단계, 엔진의 추론 글 재전송** 세 군데에 몰려 있습니다.

## 1. [엔진] 이전 라운드의 추론(reasoning) 글이 다음 라운드에 다시 실려 갑니다 — 효과가 가장 클 것으로 봅니다

- **근거:** 리뷰 Agent(b8c5325f)는 1회차에 입력 2,087토큰, 출력 30,916토큰을 썼습니다. 그런데 2회차 입력이 **33,078토큰**이었습니다. 그사이에 오간 Tool 인자는 1,679자뿐이라, 늘어난 약 31k토큰은 1회차의 추론 글로 봐야 맞습니다.
- **코드로 확인한 내용:** Spring AI 2.0.1의 `OpenAiChatModel`은 답에서 받은 `reasoningContent`를 다음 요청의 assistant 메시지에 `reasoning_content`로 다시 넣습니다. 그래서 Tool 루프가 길수록 지난 추론 글이 매 라운드 누적되어 다시 전송됩니다. 개발 단계 32라운드가 1.37M토큰이 된 것도 이 영향이 큽니다.
- **고치는 방법:** 이미 Tool 루프 안에 있는 `ToolResultCompactionAdvisor` 자리에서, 모델에 보내는 사본의 assistant 메시지에서 `reasoningContent`를 빼면 됩니다. 설정 하나(`dstone.ai.agent.keep-reasoning`, 기본 false)로 켜고 끌 수 있게 합니다.
- **장점:** 정의 파일은 하나도 바뀌지 않고, 모든 Workflow에 똑같이 적용됩니다. Qwen 계열은 원래 지난 추론을 대화 이력에 넣지 않는 쪽을 권장합니다.
- **확인할 것:** 실제로 얼마나 줄어드는지는 한 번 돌려서 재야 합니다.

## 2. [개발 Agent] 수정을 한 라운드에 하나씩 하는 것은 프롬프트 문구 때문입니다

- **5bf95466:** `replaceInFile`을 18번 불렀는데, 매번 한 라운드에 하나씩이었습니다. 원인은 프롬프트 1-3의 **"replaceInFile로 하나씩 적용한다"**입니다.
- **b8c5325f:** 우연히 `editFileTexts`로 6개 파일을 한 라운드에 고쳤고, 그래서 13라운드에 끝났습니다.
- **제안:** "한 파일의 변경은 `editFileTexts` 한 번에 모으고, 여러 파일은 한 답에서 함께 부른다"로 바꿉니다. 도구 목록에서 `replaceInFile`을 빼서 고치는 방법을 하나로 정합니다. `editFileTexts`도 공백이 달라도 맞춰 주는 기능이 있습니다.
- 개발 단계는 매 라운드 설계서 전체(이번에는 34k자, 약 17k토큰)를 다시 싣습니다. 그래서 라운드 수를 줄이는 효과가 그대로 곱해집니다.

## 3. [개발 Agent] 결과 기록과 불필요한 확인 호출을 줄입니다

| 지금 하는 일 | 낭비 | 제안 |
|---|---|---|
| `03-2.taskitems.md`의 행을 고쳐 상태를 기록 | 입력으로 받은 파일을 다시 읽고 `editFileTexts`를 부름. 2~3라운드, 한 번은 인자만 2,271토큰 | `appendFile`로 파일 끝에 '개발 결과' 표(태스크아이템, 상태, 요약)를 한 번만 덧붙임. 읽을 필요가 없음 |
| 모든 대상 파일에 `isFileExist`를 부름 (매번 9번) | 1라운드 | `copyFileWithReplace`는 이미 있는 파일을 덮어쓰지 않으므로 바로 부름. 확인은 참고 파일 없이 `writeFile`할 때만 |
| 복사하기 전에 참고 원본을 통째로 읽음 | b8c5325f에서 같은 내용을 원본, 복사본, 고친 뒤 복사본으로 **3번** 읽음 | 원본은 읽지 않고, 성공한 `editFileTexts` 뒤에 다시 읽어 확인하지 않음 |

이 변경은 프롬프트 문장을 바꾸거나 지우는 것이라 프롬프트가 오히려 짧아집니다.

## 4. [리뷰 Agent] 판정만 하게 하고 파일 기록은 뺍니다

- 리뷰 Agent는 SUPERVISOR인데 `editFileTexts`로 요구분석서의 '상태' 칸을 고치느라 라운드가 2개입니다.
- b8c5325f에서는 1회차에 172초가 걸렸고, 1번 문제 때문에 2회차에 33k토큰이 다시 실렸습니다.
- 판정 결과는 이미 `state.review`와 실행 이력에 남습니다. Tool을 빼면 LLM을 한 번만 부르고 끝납니다.
- 이렇게 하면 요구분석서의 '상태' 칸은 쓸 곳이 없어지므로 요구분석 Agent의 표에서도 뺍니다.

## 5. [작은 것] `knowledgeSearch` 결과 크기

- 영향도 1회차에 `topK: 10`으로 두 번 검색해 9,479자와 8,732자를 받았고, 이 내용이 이후 18라운드 내내 실렸습니다.
- 엔진 설정으로 `topK` 상한(예: 5)을 두면 모든 Agent에 똑같이 적용됩니다.

## 손대지 않는 것

- **영향도와 설계 단계의 공통 클래스 전체 읽기** (`BaseVO` 13k자, `InterceptorAdapter` 11k자): 지난번에 빼기로 한 1번 항목(공통 기능 내부를 따라가지 않게 하는 예시)과 같은 이야기라 제외했습니다.
- **설계 Agent의 문서 작성 라운드** (118초, 222초): `reasoning: high`로 문서를 생성하는 시간입니다. 이 모델에서는 단계를 나누지 않고는 줄일 방법이 마땅치 않습니다.

**추천 순서:** 1번(엔진) → 2·3번(개발 프롬프트) → 4번(리뷰). 1번만 Java를 바꾸고, 나머지는 정의 파일만 바꾸면 됩니다. 어디까지 적용할지 알려 주시면 진행하겠습니다.

---

## 질문

4. [리뷰 Agent] 판정만 하게 하고 파일 기록은 뺍니다 ==>> 이게 정확하게 무슨 뜻인가?

---

## 답변

# 4번이 뜻하는 것

## 지금 리뷰 Agent가 하는 일 (step04)

리뷰 Agent는 지금 두 가지 일을 합니다.

1. **판정:** 요청서와 고객요구사항분석서를 비교해 통과인지 불통과인지 정하고 `{pass, reason}`으로 답합니다. 엔진은 이 답을 `state.review`에 저장합니다. 불통과면 step05(사람 결정)로 보내고, 재분석할 때는 `reason`을 요구분석 Agent에게 `feedback`으로 넘깁니다.
2. **파일 기록:** `editFileTexts`로 `01.customer_requirement.md`를 직접 고칩니다.
   - TASK 표의 '상태' 칸을 `대기`에서 `통과` 또는 `점검요청`으로 바꿉니다.
   - 점검요청이 있으면 파일 끝에 '점검 사유'를 덧붙입니다.

## 2번 때문에 LLM 호출이 두 번이 됩니다

```
1회차: LLM이 판정을 생각한 뒤 "editFileTexts를 불러 줘"라고 답함
       → 엔진이 파일을 고침
2회차: 지금까지의 대화 전체를 다시 보내고, LLM이 {pass, reason}으로 답함
```

b8c5325f에서 1회차는 172초, 출력 31k토큰이었습니다. 2회차는 1번 항목의 문제 때문에 그 31k토큰을 다시 받아 33k토큰 입력이 됐습니다.

## 제안: 1번만 하게 하기

리뷰 Agent에서 `editFileTexts` Tool과 프롬프트의 '파일에 기록' 부분(할 일 2번)을 뺍니다. 그러면 LLM을 한 번 불러 `{pass, reason}`을 받고 끝납니다.

## 바뀌는 점

| | 지금 | 바꾼 뒤 |
|---|---|---|
| LLM 호출 | 2회 | 1회 |
| 통과/불통과에 따른 흐름(step05, 재분석) | 그대로 | 그대로 |
| 재분석 때 요구분석 Agent가 받는 지적 사항 | `reason` | `reason` (같음) |
| 사람이 불통과 사유를 보는 곳 | md 파일의 '점검 사유' + 실행 이력의 step 결과 | **실행 이력의 step 결과에만 남음** |
| md 파일의 '상태' 칸 | 통과/점검요청으로 바뀜 | 늘 '대기' → 요구분석 표에서 칸을 빼는 것이 맞음 |

## 잃는 것

`01.customer_requirement.md`를 열었을 때 TASK별 통과/점검요청 표시와 '점검 사유'가 더 이상 보이지 않습니다. 불통과 사유는 Workflow 실행 관리 화면의 step04 결과(`reason`)에서 확인해야 합니다.

md 파일 안에 리뷰 표시가 꼭 남아야 한다면 이 항목은 적용하지 않는 것이 낫습니다. step을 늘리지 않으면서 파일에 남길 방법은 Agent가 직접 쓰는 지금 방식뿐입니다.

---

## 질문

2. [개발 Agent] 수정을 한 라운드에 하나씩 하는 것은 프롬프트 문구 때문입니다 ==>> 이게 정확하게 무슨 뜻인가? 그리고 어떻게 하라는 것인가?

---

## 답변

# 2번이 뜻하는 것

## 무슨 일이 있었나

개발 Agent는 파일을 고칠 때마다 LLM을 한 번씩 더 부릅니다. 그런데 LLM을 부를 때마다 지금까지의 대화 전체가 다시 전송됩니다. 여기에는 설계서 전문(이번에는 34k자, 약 17k토큰)과 앞서 받은 Tool 결과가 모두 들어 있습니다.

5bf95466의 개발 단계 32라운드 중 18라운드가 이런 모양이었습니다.

```
4회차  replaceInFile 1개 (ImageBoardVO)
5회차  replaceInFile 1개 (ImageBoardVO)
7회차  replaceInFile 1개 (mapper)
8회차  replaceInFile 1개 (mapper)
9회차  replaceInFile 1개 (mapper)
10회차 replaceInFile 1개 (mapper)
...
16~23회차 ImageBoardController 하나에 replaceInFile 6번, 라운드도 6번
```

한 라운드에 수정 하나만 하니, 같은 파일을 6군데 고치면 대화 전체를 6번 다시 보냅니다. 이 실행은 입력 1.37M토큰, 253초가 걸렸습니다.

반면 b8c5325f는 7회차에 `editFileTexts` 6개(파일마다 여러 군데 수정)를 한 번에 불렀습니다. 그래서 13라운드, 436k토큰으로 끝났습니다.

## 왜 그렇게 하나: 프롬프트 문구와 Tool의 모양

지금 `sm-developer-agent.md`에 이렇게 적혀 있습니다.

- 1-2: "그 밖의 변경은 **replaceInFile**(파일, 설계서의 '변경 전', '변경 후')로 그 부분만 바꾼다."
- 1-3: "설계서의 '빼는 것'과 '더하는 것'을 **replaceInFile로 하나씩 적용한다**."

`replaceInFile`은 한 번에 한 군데(`oldText` → `newText`)만 바꿀 수 있습니다. 여기에 "하나씩"이라는 말이 더해지니, 모델이 "하나 고치고 결과 보고, 다음 거 고치고"로 받아들였습니다.

## 어떻게 바꾸자는 것인가

수정 수단을 `editFileTexts`로 바꿉니다. 이 Tool은 한 파일의 여러 군데를 한 번에 고칩니다.

```
editFileTexts(filePath, edits: [{before, after}, {before, after}, ...])
```

- 한 파일의 변경은 모두 한 번의 호출에 담습니다.
- 다른 파일들은 한 번의 답에서 함께 부릅니다.
- 공백이 조금 달라도 맞춰 주는 기능은 `replaceInFile`과 같습니다.

**프롬프트 변경안** (1-2, 1-3의 해당 줄만 바꿉니다)

```
1-2. 이미 있는 파일을 고치는 경우
  - "파일 끝에 덧붙임" 변경은 appendFile로 덧붙인다.
  - 그 밖의 변경은 editFileTexts로 고친다. 한 파일의 변경은 모두 edits에 모아
    한 번에 부르고(before='변경 전', after='변경 후'), 다른 파일은 같은 답에서 함께 부른다.
  - "실패:"로 답하면 그 파일은 하나도 바뀌지 않은 것이다. 안내가 가리킨 곳의 앞뒤만
    readFileLines로 읽어 before를 파일 글자와 맞춘 뒤 그 파일만 한 번 더 부른다.
    그래도 실패하면 고치지 말고 '보류'로 둔다.
1-3. 새 파일을 만드는 경우
  - copyFileWithReplace로 만든 뒤, '빼는 것'과 '더하는 것'을 1-2와 같이
    파일마다 editFileTexts 한 번으로 적용한다.
```

**도구 목록 변경:** `tools.allowed`에서 `replaceInFile`을 뺍니다. 고치는 방법이 둘이면 모델이 습관대로 하나씩 고치는 쪽을 고를 수 있어서입니다. Tool 정의 하나가 빠지면 매 라운드 보내는 양도 조금 줄어듭니다.

## 기대 효과와 주의점

- **기대 효과 (추정):** 5bf95466 기준으로 수정 18라운드가 파일 수(5~6)만큼 줄어들어, 전체가 32라운드에서 15라운드 안팎이 됩니다. 입력 토큰은 절반 가까이 줄어들 것으로 봅니다. 실제 수치는 다시 돌려 봐야 압니다.
- **주의점:** `editFileTexts`는 파일 단위로 전부 아니면 전무입니다. `before` 하나가 안 맞으면 그 파일의 다른 수정도 반영되지 않습니다. 그래서 변경안에 "그 파일만 맞춰서 다시 부른다"는 문장을 넣었습니다. 반대로 보면 반쯤 고쳐진 파일이 남지 않는다는 장점이기도 합니다.

---

## 질문

3. [개발 Agent] 결과 기록과 불필요한 확인 호출을 줄입니다 ==>> 이건 정확히 어떤 말인가? 그리고 어떻게 바꾼다는 말인가?

---

## 답변

# 3번이 뜻하는 것

3번은 개발 Agent가 하는 세 가지 일을 줄이자는 이야기입니다. 셋 다 결과물에는 도움이 안 되는데 LLM 라운드나 대화 크기만 늘립니다. 각각 실제로 있었던 일, 원인이 된 프롬프트 문구, 바꿀 문장 순서로 적었습니다.

## 3-1. 결과 기록: 행을 고치는 대신 끝에 덧붙이기

**실제로 있었던 일**

| 실행 | 라운드 | 한 일 |
|---|---|---|
| 5bf95466 | 30회차 | 입력으로 이미 받은 `03-2.taskitems.md`를 다시 읽음 (2,802자) |
| | 31회차 | `editFileTexts`로 표의 행들을 고침. 행마다 before/after를 통째로 써서 출력 2,271토큰, 21초 |
| | 32회차 | 마무리 답 |
| b8c5325f | 9~11회차 | 읽기 → 고치기 → 확인한다고 또 읽기 |

**원인이 된 문구:** 할 일 2번 "editFileTexts를 한 번 불러 해당 행만 고친다. 상태를 '완료'로 바꾸고 변경 요약을 덧붙인다."
- 행을 고치려면 `before`에 그 행의 글자를 정확히 적어야 합니다. 그래서 모델이 파일을 다시 읽습니다.
- 행 전체를 before와 after로 두 번 쓰니 출력도 길어집니다.

**바꾸는 방법:** 표는 건드리지 않고, 파일 끝에 결과 표를 `appendFile`로 한 번만 덧붙입니다. 덧붙이기에는 `before`가 필요 없어서 읽을 일이 없습니다.

```
2. 결과를 ${input.base_dir}/03-2.taskitems.md 끝에 appendFile로 한 번 덧붙인다.
   파일을 읽거나 기존 행을 고치지 않는다.
   ## 개발 결과
   | 번호 | 상태(완료/보류) | 바꾼 파일과 위치 | 요약 또는 보류 이유 |
   다시 개발할 때는 '## 개발 결과 (재개발)'로 한 번 더 덧붙인다.
```

**같이 바꿀 곳**
- 설계 Agent가 만드는 태스크아이템 표의 '상태(대기)' 칸은 쓸 곳이 없어지므로 뺍니다.
- 개발 Agent의 feedback 문장 "목록 파일에 '완료'로 적힌 항목"을 "'개발 결과'에 '완료'로 적힌 항목"으로 고칩니다.

**효과:** 2~3라운드가 0~1라운드로 줄어듭니다(마지막 답과 합쳐질 수 있음).

## 3-2. `isFileExist` 확인 호출 없애기

**실제로 있었던 일:** 두 실행 모두 첫 라운드에서 대상 파일 9개마다 `isFileExist`를 불렀습니다.

**원인이 된 문구**
- 1-1 "저장 경로에 그 파일이 **아직 없으면** 먼저 copyFileWithReplace로 복사한다"
- 1-3 "쓰기 전에 **isFileExist로 확인**하고…"

"없으면"이라는 조건이 있으니 모델이 먼저 하나하나 확인합니다. 그런데 `copyFileWithReplace`는 원래 이미 있는 파일을 덮어쓰지 않고 실패로 답합니다. 확인은 Tool이 이미 하고 있습니다.

**바꾸는 방법**

```
- 확인하지 말고 바로 copyFileWithReplace(원본, 저장 경로, 빈 목록)를 부른다.
  이미 있다고 답하면 그 파일을 그대로 고친다.
```

`isFileExist`는 덮어쓸 위험이 있는 `writeFile`(참고 파일 없는 새 파일) 앞에서만 쓰게 남겨 둡니다.

**효과:** 크지 않습니다. 1라운드 정도, 또는 그 라운드의 호출 9개가 줄어듭니다.

## 3-3. 같은 내용을 여러 번 읽지 않기

**실제로 있었던 일 (b8c5325f, 샘플게시판 파일 6개)**

| 라운드 | 읽은 것 | 크기 |
|---|---|---|
| 2회차 | 참고 **원본** 6개 | 약 16k자 |
| 6회차 | 그것을 이름만 바꿔 복사한 **복사본** 6개 | 약 15k자 |
| 8회차 | 고친 뒤 **복사본** 6개를 확인한다고 다시 | 약 7.5k자 |

5bf95466도 2회차에 원본 7개를 읽었습니다. `memberRegi.jsp` 하나만 12.5k자였습니다.

**왜 엔진이 막지 못했나:** 다시 읽기를 막는 장치(`RereadGuardCallback`)는 같은 경로이고 그사이 바뀌지 않은 파일만 막습니다.
- 원본과 복사본은 경로가 달라서 막지 못합니다.
- 고친 뒤에는 파일이 바뀌었으니 막지 않는 것이 맞습니다.

그래서 프롬프트에 적어야 합니다. 지금 프롬프트에는 "만든 파일을 미리 통째로 읽지 않는다"만 있습니다. 원본을 읽지 말라는 말과, 고친 뒤 확인하지 말라는 말이 없습니다.

**바꾸는 방법:** [Tool 쓰는 법]에 두 줄을 넣습니다.

```
- 참고 파일 원본은 읽지 않는다. 고칠 글자는 설계서에 있다. 위치를 찾아야 하면
  복사본에서 searchInFiles로 찾고 그 앞뒤만 readFileLines로 읽는다.
- editFileTexts, appendFile이 성공으로 답하면 다시 읽어 확인하지 않는다.
```

**효과:** 이 Tool 결과들은 한 번 읽히면 이후 모든 라운드에 다시 실립니다. 그래서 읽은 양보다 훨씬 많이 줄어듭니다. b8c5325f 기준으로 약 25k자(약 1만 토큰)가 남은 5~7라운드마다 빠집니다.

## 정리

| 항목 | 바꾸는 문장 | 줄어드는 것 |
|---|---|---|
| 3-1 결과 기록 | 할 일 2번 (+ 설계 Agent 표의 '상태' 칸) | 2~3라운드 |
| 3-2 확인 호출 | 1-1, 1-3의 "없으면/확인" | 0~1라운드 |
| 3-3 다시 읽기 | [Tool 쓰는 법]에 두 줄 | 매 라운드의 대화 크기 |

세 항목 모두 기존 문장을 바꾸거나 한두 줄을 더하는 정도라서 프롬프트 길이는 지금과 비슷합니다. 특정 업무에 묶인 내용도 없습니다.
