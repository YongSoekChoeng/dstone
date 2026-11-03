# localcommandcaveatTh

> 생성일시: 2026-11-03 09:21

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

sm-workflow 에서 호출하는 agent들에 대해 프롬프팅 및 툴 콜링에 대해서 점검해줘.
효율성 관점에서 문제가 있어보여.

---

## 답변

`sm-workflow` 정의와 호출되는 Agent, 실제 실행 로그부터 확인하겠습니다.

---

## 답변

프롬프트만 봐도 파일명 불일치 같은 문제가 보입니다. 추측으로 끝내지 않도록 실제 실행 기록으로 Tool 호출 횟수와 시간, 토큰을 확인하겠습니다.

---

## 답변

실행 이력은 확보했습니다. 지금은 Tool 호출 횟수를 직접 재기 위해 `dstone-knowledge` 쪽 로그와 LLM 로그 설정을 대조하고 있습니다.

---

## 답변

`sm-workflow`를 점검해 보니 비효율이 실제로 있습니다. 가장 큰 낭비는 **요구사항 분석 Agent(step01)의 무관한 문서 검색과 영향도 조사 중복**입니다. 정의 파일은 고치지 않았습니다.

## 측정 결과
11월 2일 실행 7건을 실행 이력과 `dstone-knowledge` 로그로 맞춰 본 값입니다. 그날 정의 파일을 계속 고쳤으므로 같은 조건의 비교는 아닙니다.

| Step | Agent | 소요 시간(초) | knowledge 호출 | 허용 Tool | Tool 정의 / 프롬프트(글자) |
|---|---|---|---|---|---|
| step01 | requirement | 44–387 | **6–9회** | 10개 | 5,567 / 590 |
| step02 | reviewer | 17–79 | **0회** | 17개 | 11,150 / 881 |
| step03 | impact | 81–238 | 2–7회 | 14개 | 8,315 / 684 |
| step05 | architect | 171–700 | **0회** | 16개 | 10,078 / 3,120 |
| step07 | developer | 90, 653 | **0회** | 16개 | 10,941 / 2,099 |

Tool 정의 글자 수는 카탈로그 API 응답 기준의 근사치이고, 이 정의는 LLM을 부를 때마다 함께 전송됩니다. 파일 Tool 호출 수와 라운드별 토큰은 **로그가 없어 재지 못했습니다**(`IS_LLM_LOGGING_YN=false`, LLM이 고른 Tool 호출은 기록되지 않음).

## 효율 문제 (영향이 큰 순서)
1. **step01의 RAG 검색이 쓸모없이 반복됩니다.**
   - `with: ${input}`이라 검색어가 요청서 파일 경로입니다.
   - 한 번의 Agent 호출에서 같은 검색어가 10~14번 검색됐습니다. LLM 라운드마다 도는 것으로 보입니다.
   - 같은 경로로 지금 검색해 보니 무관한 `Java_Application_Knowledge_Graph…설계서` 조각 5개(약 7,800자)가 임계값 0.35를 넘겼습니다.
   - `context` 블록을 지우면 해결됩니다.

2. **step01이 step03의 일을 미리 합니다.**
   - 요구사항 분석인데 양식이 '대상 파일 절대경로'를 요구해서 knowledge 호출이 step03보다 많습니다.
   - '변경 내용(설계서의 어느 변경인지)' 칸은 설계 전이라 채울 수도 없습니다.
   - 양식을 요구사항 중심으로 줄이고 knowledge Tool을 빼면 됩니다.

3. **쓰지 않는 Tool이 매번 전송됩니다.**
   - reviewer, architect, developer는 knowledge를 한 번도 부르지 않았습니다.
   - reviewer에는 `copyFileWithReplace`, `editFileLines`, `writeFile` 같은 수정 Tool까지 있습니다.

| Agent | 남길 Tool |
|---|---|
| requirement | `readFile`, `writeFile` |
| reviewer | `readFile`, `replaceInFile` |
| developer | `readFile`(또는 `readFileLines`), `replaceInFile`, `appendFile`, `copyFileWithReplace`, `writeFile`, `editFileTexts` |

architect는 처리 방향을 먼저 정해야 합니다. 프롬프트는 `knowledgeImpact`를 부르라고 하는데 6건 모두 부르지 않았으므로, 이 지시를 지우거나 지키게 만들어야 합니다.

4. **같은 파일을 Agent마다 LLM을 거쳐 읽습니다.**
   - `isFileExist`로 확인한 뒤 `readFile`로 읽는 순서라, 프롬프트대로면 architect는 파일 3개에 최대 6번의 호출을 씁니다.
   - `readFile`은 파일이 없으면 어차피 `실패:`로 답하므로 `isFileExist`는 빼도 됩니다.
   - `pilot-workflow`처럼 앞에 TOOL step(`readFiles`)을 두고 `with`로 넘기면 읽기용 호출이 사라집니다.

5. **재시도 설정이 비용만 키울 수 있습니다.**
   - architect와 developer는 `maxAttempts: 5`, `RETRY`, `maxToolCalls: 100`입니다.
   - 답이 글자 형식이라 재시도는 호출 한도 초과나 토큰 소진 때 주로 일어나고, 그때마다 처음부터 다시 합니다.
   - 최악이면 Tool 500회이고, developer는 이미 고친 파일에 다시 작업합니다. `maxAttempts`는 1로 두는 게 맞습니다.

6. **다시 돌릴 때 이유가 전달되지 않습니다.**
   - step04, step06의 승인 코멘트와 step02의 판정 사유를 다음 Agent가 받지 못해 같은 조사를 반복합니다.
   - step02는 불통과면 되돌아가지 않고 바로 `FAIL`로 끝납니다.

7. **토큰 소진과 응답 시간 초과로 일이 끊깁니다.**
   - `59a326de`의 step03은 누적 출력 36,379토큰에서 `finishReason=LENGTH`로 실패했습니다. 설정한 `max-tokens` 60,000보다 낮은 값이라 원인은 더 봐야 합니다.
   - `1d9e6257`의 step07은 LLM 응답 시간 초과로 끊겼습니다.
   - `model.reasoning: low/medium`은 10월 측정에서 이 모델(`qwen3.8-27b`)에 효과가 없었으니 시간 단축을 기대하기 어렵습니다.

## 프롬프트 오류 (헛호출과 재작업의 원인)
| 위치 | 문제 |
|---|---|
| architect 할 일 2 | `01-requirements.md`, `02-impact.md`를 읽으라고 하지만 실제 파일은 `01.customer_requirement.md`, `02.impact.md` |
| developer 산출물 | `03-2-taskitems.md`로 적혀 있지만 실제는 `03-2.taskitems.md` |
| developer Tool 목록 | 프롬프트가 쓰라는 `isFileExist`, `readFile`이 허용 목록에 없음 |
| workflow와 step01 입력 | workflow는 디렉터리, Agent는 파일 경로라고 함. 실제 실행은 파일 경로이고 `00-request.md`와 `00.request.md`도 섞여 있음 |
| reviewer | 엔진 규칙은 판정 대상을 고치지 말라는데 프롬프트는 분석서 수정을 지시함. 충돌 시 엔진 규칙이 우선 |
| reviewer 반환 | `true/false`로 적혀 있지만 엔진이 정한 형식은 `{pass, reason}` |
| reviewer 체크 기준 | '영향도가 빠진 부분'을 보라고 하지만 영향도 분석은 다음 step |
| architect, developer | 없는 `questions` 항목에 적으라고 함(output이 글자 형식). 이 내용은 답변 글에만 남고 승인 화면에는 따로 보이지 않을 수 있음 |
| architect | 없는 `[새 파일의 설계]` 절을 참조하고, '산출물 2개'라면서 3개를 나열함 |

품질 문제도 하나 있습니다. `1d9e6257`에서는 태스크 목록에 FAQ가 빠져 있어 developer가 FAQ를 구현하지 않고 653초 만에 끝냈습니다. 설계서와 태스크 목록이 일치하는지 확인하는 단계가 없어서 생긴 일입니다.

## 고치는 순서
1. **프롬프트 오류와 RAG 제거:** 위 표의 항목을 고치고 `context` 블록을 지웁니다. 동작을 바꾸지 않는 안전한 수정이라 가장 먼저 합니다.
2. **Tool 정리:** Agent별 Tool을 줄이고 `maxAttempts`를 1로 낮춥니다.
3. **step01 축소:** 양식을 줄이고, 파일 읽기는 앞단 TOOL step으로 옮깁니다.
4. **재실행 사유 전달:** 승인 코멘트와 판정 사유를 다음 Agent 입력으로 넘깁니다.
5. **Tool 호출 로그 추가:** Agent 호출마다 라운드 수, Tool별 횟수, 토큰을 한 줄로 남겨 개선 효과를 재도록 합니다. 이 항목은 엔진 코드를 고쳐야 합니다.

위 판단은 실행 로그와 프롬프트 대조로 내린 것이고, 고친 뒤 얼마나 빨라지는지는 아직 재지 않았습니다.

---

## 질문

이 수정내용을 모두 적용해줘.

---

## 답변

전부 적용하겠습니다. 먼저 재사용할 Tool과 엔진 코드, 테스트, 문서의 참조를 확인합니다.

---

## 답변

정의 파일 수정과 로그 Advisor 추가를 끝냈습니다. 이제 테스트를 새 step 구조에 맞게 고쳐 돌립니다.

---

## 답변

실행을 기다리는 동안 문서를 맞춥니다.

---

## 답변

실제 LLM 실행이 진행 중입니다. 끝나면 결과를 확인해서 보고하겠습니다.

---

## 질문

<task-notification>
<task-id>bwbe0heg3</task-id>
<summary>Monitor event: "sm-workflow 실제 실행의 승인 진행과 종료 상태"</summary>
<event>ec7fb964-0f9c-4a21-818f-d287ebdc37c9</event>
</task-notification>

---

## 답변

The monitor reported only the execution id, so the run is still in progress; I'll wait for the next event.

---

## 질문

작업끝나면 올려줘.

---

## 답변

알겠습니다. 실제 LLM 실행 결과까지 확인한 뒤 커밋하고 푸시하겠습니다. 아직 실행 중입니다.

---

## 질문

아직도 진행중이야? 무슨 문제있는거 아니야?

---

## 답변

상태를 바로 확인하겠습니다.

---

## 답변

전체 실행이 `DONE`으로 끝났습니다. 복사본 환경을 정리하고 결과물을 확인한 뒤, 마지막 코드로 테스트를 다시 돌리고 올리겠습니다.

---

## 답변

수정을 모두 적용해 `sm-workflow-efficiency` 브랜치에 올렸습니다(`4cb94585`). `main`에는 아직 병합하지 않았고, 병합하기 전까지 Windows 쪽 인스턴스는 예전 정의로 동작합니다. 테스트는 84건 모두 통과했고, 실제 LLM으로 끝까지 돌린 1회도 `DONE`으로 끝났습니다.

## 실제 실행 결과
`testApp` 복사본에서 work003 요청을 자동 승인으로 돌린 결과입니다.

| 단계 | 이전(11/2) | 이번 | 이번 LLM·Tool 호출 수 |
|---|---|---|---|
| 요구사항 분석 | 44–387초 | **14초** | LLM 2회, Tool 1회 |
| 리뷰 | 17–79초 | 48초 | LLM 2회, Tool 1회 |
| 영향도 분석 | 81–238초 | 170초 | LLM 16회, Tool 28회 |
| 설계 | 171–700초 | 163초 | LLM 9회, Tool 15회 |
| 개발 | 90, 653초 | 85초 | LLM 8회, Tool 23회 |

- **산출물:** 설계 문서 3개를 만들었고, 태스크 12건이 모두 완료로 기록됐습니다. 소스 파일은 9개가 새로 생기거나 바뀌었습니다.
- **비교의 한계:** 한 번만 돌린 결과입니다. 또한 복사본 경로 때문에 프로젝트ID를 빼서 knowledge Tool 없이 파일 Tool로만 조사했으므로, 이전 실행과 조건이 다릅니다.

## 테스트 중 발견해 추가로 고친 것
- **영향도 Agent 과잉 조사:** 첫 실행에서 38회차, Tool 93회(중복 24회), 입력 83,000토큰까지 늘어나 중단했습니다. Tool 한도를 30회로 두고, 파일은 필요한 범위만 읽고 호출 흐름을 한 번 따라가면 멈추도록 프롬프트를 고쳤습니다.
- **로그 유실:** 새 기록이 stdout으로 나가 `startApp.sh` 환경에서는 사라지는 문제였습니다. Agent 기록과 Tool 호출 기록을 `execution.log`로 옮겼습니다. 다른 `sysout` 로그는 그대로라 여전히 stdout으로만 나갑니다.

## 제안과 달라진 점
- **Tool 구성:**
  - requirement는 `writeFile`, `appendFile`만 씁니다.
  - reviewer는 `editFileTexts`만 씁니다. 행마다 호출하지 않고 한 번에 고치게 하려는 것입니다.
  - architect는 knowledge Tool을 뺐습니다.
  - developer에는 덮어쓰기 방지용 `isFileExist`를 남겼습니다.
- **step 번호:** 읽기 TOOL step이 늘어 `step00`~`step12`로 다시 매겼습니다. 리뷰가 불통과면 `step04`에서 사람이 재분석, 그대로 진행, 중지 중에서 고릅니다.
- **설계와 태스크 일치 확인:** 별도 검사 단계는 만들지 않고 architect의 완료조건에 넣었습니다. LLM이 지키지 않으면 FAQ 누락 같은 일이 다시 생길 수 있습니다.

## 사용하실 때 주의할 점
- **입력 형식이 바뀌었습니다.** 파일 경로가 아니라 `.../work003` 같은 **폴더 경로**를 넣어야 합니다. 그동안처럼 파일 경로를 넣으면 `step00`에서 바로 실패합니다.
- **진행 중인 실행은 이어갈 수 없습니다.** step 번호가 바뀌어 예전 실행은 재개되지 않을 수 있으니, 새로 시작해 주세요.

## 확인하지 못한 것
- **생성 코드:** 복사본의 코드는 빌드도 실행도 하지 않았습니다. 확인한 건 FAQ 매퍼 XML의 형식이 맞다는 점과, Java 파일에 샘플게시판 이름이 남아 있지 않다는 점뿐입니다.
- **신규 파일 생성 방식:** 프롬프트는 참고 파일을 복사해 고치라고 하지만, 이번 실행의 developer는 `copyFileWithReplace` 없이 `writeFile`로 파일을 새로 썼습니다. 설계서의 참고 파일 표기가 지시한 형식과 달랐던 것이 원인으로 보이며, 더 확인이 필요합니다.
- **미실행 경로:** knowledge Tool을 쓰는 영향도 분석과 재분석·재설계 경로는 실제 LLM으로 돌리지 않았고, 재분석 사유 전달은 가짜 LLM 테스트로만 확인했습니다.

## 병합 방법
`main`에 바로 올리지 않고 브랜치로 푸시했습니다. 아래 명령으로 병합하면 됩니다.
```bash
git checkout main && git merge sm-workflow-efficiency && git push
```
