# localcommandcaveatTh

> 생성일시: 2026-11-05 12:00

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
