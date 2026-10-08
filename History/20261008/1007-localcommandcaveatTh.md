# localcommandcaveatTh

> 생성일시: 2026-10-08 10:07

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 의 yaml들을 아래의 내용을 참고해서 수정해줘.
/app/dstone/docs/temp/Agent_Workflow_안정화_방안.docx

---

## 답변

먼저 문서 내용과 현재 YAML 목록을 확인하겠습니다.

---

## 답변

신규 개발 경로용 Agent 5개와 Workflow step들을 다 썼고, 지금은 Workflow에 넣은 jq 표현식(검증·문서 만들기)을 엔진의 실제 평가기로 돌려 보는 중입니다.

---

## 답변

표현식은 모두 의도대로 나옵니다. 이제 일반 경로 쪽(개발·리뷰 Agent 지적 사항, 조회 한도)을 고치고 엔진을 띄워 기동 검증을 받겠습니다.

---

## 답변

실행이 도는 동안(6분쯤) 문서 쪽을 맞춰 두겠습니다.

---

## 답변

문서의 제안 중 YAML만으로 옮길 수 있는 것을 `pilot-workflow`와 `pilot` Agent에 반영했고, 신규 개발 경로는 실제 요청으로 설계 승인 단계까지 돌려 확인했습니다. 실제 소스를 고치는 구현 단계(`newDevelop`)와 재분석/재설계로 되돌아가는 흐름은 돌려 보지 않았습니다. 커밋은 하지 않았습니다.

## 바뀐 것

**경로 분기 (문서의 4순위)**
- `step00` 뒤에 `classify`(ROUTER)를 넣어 요청서의 말만 보고 `신규개발` / `일반`을 고릅니다.
- 유형이 섞였거나 분류에 실패하면 `일반`으로 갑니다.
- 일반 경로(`step01`~`step07`)의 흐름은 그대로입니다.

**신규 개발 경로 (새 step 15개, 새 Agent 4개)**

| 문서의 제안 | 반영한 방식 |
|---|---|
| 컨텍스트 팩 | `newRef`가 참고 구현의 경로만 모으고, `newPack`이 LLM 없이 한 번 읽습니다. 뒤 Agent는 내용을 input으로 받습니다. |
| 문서가 아니라 데이터 | Agent는 JSON만 내고, `01-requirements.md` / `02-impact.md` / `04-design.md` / `04-tasks.md`는 `writeFile` step이 jq로 만듭니다. |
| 코드 검증기 | `newCheck` / `newDesignCheck`가 jq로 확인합니다(FR↔변경 대상, 파일 존재 여부, '변경 전'이 파일에 정확히 한 번 나오는지). |
| 파일별 반복 | 설계와 구현을 forEach로 파일마다 새 대화에서 합니다. |
| 리뷰 생략 | 신규 개발에서는 리뷰 Agent 대신 위 검증을 씁니다. |

**일반 경로 (문서의 "지금 yml에서 보이는 것")**
- 개발 Agent: `readFileLines`와 `maxToolCalls: 60`을 추가했습니다.
- 개발 Agent의 knowledge Tool: 주석과 `tools`가 어긋나 있던 것은 `tools` 쪽을 따랐습니다. 대신 prompt에 "설계서에 적힌 위치를 못 찾았을 때만 쓴다"를 적었습니다.
- 요구사항/영향도/리뷰/설계 Agent: prompt의 `[조회 한도]`를 지우고 `maxToolCalls` 40 → 32로 옮겼습니다.
- 리뷰 Agent: `reasoning: low`를 넣었습니다. 다만 지금 모델(`qwen/qwen3.8-27b`)은 `low`가 듣지 않아 당장은 달라지는 것이 없습니다.
- 이 항목들은 기동 검사만 통과시켰고 실제 요청으로 재 보지 않았습니다.

## 실제로 돌려 본 결과

`testApp`의 FAQ 신규 요청(work002)을 임시 폴더에 복사해 한 번 돌렸습니다.

| step | 결과 |
|---|---|
| `classify` | 4초, `신규개발` |
| `newRef` | 5분 4초, Tool 7번, 파일 9개 |
| `newAnalyze` | 42초, 자동 검증 "문제 없음" |
| `newDesign` | 파일 8개 동시에 27초 |
| `newDesignCheck` | `schema.sql`의 '변경 전'이 파일에 6번 나오는 것 1건을 잡음 |

돌리면서 고친 것이 세 가지 있습니다.
- **분석 Agent**: 기본 추론으로는 응답 제한 5분을 넘겨 실패해서 `reasoning: none`으로 했습니다.
- **참고 구현 Agent**: 추론을 끄니 같은 조회를 되풀이하다 한도 15번을 다 쓰고 실패해서 기본 추론으로 되돌렸습니다.
- **파일별 설계**: 단독으로 불러 보니 다른 파일과 다른 컬럼 이름을 지어내서, 분석 Agent가 `sharedNames`를 정하고 설계가 그것을 따르게 했습니다. 컬럼을 하나씩 담으라는 마지막 규칙은 추가한 뒤 다시 돌려 보지 않았습니다.

## 알아 둘 점

- **1순위(치환형 편집 Tool)는 넣지 못했습니다.** `replaceInFile`과 복사+이름 치환 Tool은 Java가 필요합니다. 그래서 개발 Agent는 여전히 파일 전체를 `writeFile`/`appendFile`로 씁니다. 설계 output의 `renames` / `edits`는 그 Tool이 생기면 바로 쓸 수 있는 모양으로 받아 두었습니다.
- Tool 규칙을 `description`으로 옮기기, 읽기 결과 캐시, 회귀 테스트도 같은 이유로 남았습니다.
- 검증에 걸려도 자동으로 다시 시키지 않습니다. 결과를 문서에 적고, 승인 단계에서 사람이 재분석/재설계를 고릅니다.
- 신규 개발 경로의 md 문서는 직접 고쳐도 다음 단계에 반영되지 않습니다. 고칠 것은 승인 의견(`comment`)에 적어야 합니다.
- 시험하면서 `dstone_ai` 실행 이력에 세션 `yamltest-1`~`3`이 남았습니다(실패 2건, 승인 대기 1건).
- `target/dstone-ai-engine.jar`는 새 YAML로 다시 빌드된 상태입니다.
- `docs/09.dstone-ai-engine.md`와 `CLAUDE.md`도 맞춰 고쳤습니다.

`replaceInFile`과 복사+치환 Tool을 `tools/utils/FileUtil.java`에 추가하고 개발 Agent를 그쪽으로 바꾸는 작업을 이어서 할까요? 그게 들어가야 `newDevelop`를 테스트용 소스 복사본에서 끝까지 돌려 볼 만합니다.
