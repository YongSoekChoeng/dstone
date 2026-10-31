# dstoneaiengine_에서_sk

> 생성일시: 2026-10-31 15:03

## 질문

dstone-ai-engine 에서 
skill 의 기능적인 부분을 자세하게 알려줘.

---

## 답변

코드 기준으로 정확히 설명하려고 Skill 관련 소스와 샘플을 확인하겠습니다.

---

## 답변

# dstone-ai-engine의 Skill 기능

Skill은 **Agent가 필요할 때만 꺼내 읽는 작업 안내서**(`SKILL.md`)입니다. 프롬프트에는 늘 필요한 규칙만 두고, 특정 일에만 필요한 절차는 Skill로 떼어 냅니다. 같은 Skill을 여러 Agent가 함께 쓸 수도 있습니다.

Anthropic API의 서버 쪽 Skills와는 다른 기능입니다. 평범한 Tool 호출로 동작하므로, Tool 호출을 지원하는 모델이면 provider와 상관없이 쓸 수 있습니다.

아래 내용은 소스와 문서를 읽고 정리한 것이며, 이번에 실행해 확인하지는 않았습니다.

## 1. 동작 흐름

```
Agent 호출
 └ AgentExecutor.buildSpec()가 skills.allowed의 Skill을 Tool 하나(이름: Skill)로 묶어 붙임
     └ LLM은 Tool 설명에서 Skill의 이름과 설명만 봄
         └ 맞는 일이면 Skill(command="sql-review") 호출
             └ Tool 결과로 SKILL.md 본문과 Skill 폴더 경로를 받음
                 └ 안내를 따름 (참고 파일이 필요하면 readFile로 읽음)
```

Skill을 부를지는 LLM이 정합니다. Workflow에서는 평범한 AGENT step 하나로 보이고, Skill 호출을 step으로 따로 적지 않습니다.

## 2. Skill 파일

위치는 `conf/definitions/skills/<폴더>/<Skill 이름>/SKILL.md`이고, 파일 하나가 Skill 하나입니다.

```markdown
---
name: sql-review
description: SQL 문을 검토하거나 고칠 때 쓴다. 문법 검사 순서와 자주 틀리는 항목 목록을 담고 있다.
---
# SQL 검토 순서
1. 검토할 SQL 문을 하나씩 떼어 냅니다.
2. validateSqlSyntax로 문법을 검사합니다.
3. 실패하면 이 Skill 폴더의 checklist.md를 readFile로 읽고 ...
```

| 항목 | 설명 |
|---|---|
| `name` | 필수. 영문 소문자·숫자·하이픈으로 64자 이내이고, **폴더 이름과 같아야 함** |
| `description` | 필수. 1,024자 이내. LLM이 부를지 판단하는 근거이며 Tool 설명에 항상 들어감 |
| 본문 | 필수. 호출할 때만 대화에 들어감 |
| `license`, `compatibility`, `metadata`, `allowed-tools` | 공개 형식과 맞추려고 받기만 하고 쓰지 않음 |
| 그 밖의 키 | 오타일 수 있어 읽을 때 막음 |
| 같은 폴더의 다른 파일 | 참고 자료. 엔진은 읽지 않고 Agent가 `readFile`로 읽음 |

`allowed-tools`를 적어도 효과가 없습니다. 쓸 수 있는 Tool은 Agent의 `tools.allowed`가 정합니다.

## 3. Agent에 연결

```yaml
skills:
  allowed: [sql-review]
tools:
  allowed: [validateSqlSyntax, readFile]   # 참고 파일까지 읽게 하려면 readFile 필요
execution:
  maxToolCalls: 10
```

프롬프트에는 "알맞은 Skill이 있으면 그 안내를 따르십시오" 한 줄이면 됩니다. Skill을 여러 개 적어도 LLM에게는 `Skill` Tool 하나로 보입니다.

| Tool 구성 | 내용 |
|---|---|
| 설명 | 사용 규칙과 `<available_skills>` 목록(이름, 설명) |
| 인자 | `command` = Skill 이름 |
| 정상 응답 | `Skill 'x' 안내입니다.` + 폴더 경로 + 본문 |
| 없는 이름을 부른 경우 | `실패: 'x'라는 Skill은 쓸 수 없습니다. 쓸 수 있는 Skill = [...]` |

## 4. 검사

`SkillRegistry`와 `AgentRegistry`가 기동, 디자이너 [검사], 다시 읽기 때 검사합니다.

| 상황 | 결과 |
|---|---|
| `name` 규칙 위반, 폴더 이름과 불일치 | 실패 |
| `description` 없음 또는 1,024자 초과, 본문 없음 | 실패 |
| Skill 이름 중복, 파일 이름이 `SKILL.md`가 아님 | 실패 |
| `skills.allowed`에 등록되지 않은 이름 | 실패 |
| 같은 이름의 Tool 또는 Sub Agent id가 `Skill` | 실패 |
| 참고 파일이 있는데 `tools.allowed`에 `readFile`이 없음 | **경고만** (본문만 읽힘) |

## 5. 실행 중 적용되는 규칙

- **호출 횟수:** Skill 호출도 `execution.maxToolCalls`(없으면 `dstone.ai.agent.tool.max-calls`, 기본 100)에 포함되고, 다른 Tool과 횟수를 함께 씁니다. 한도를 넘으면 Skill도 거절됩니다.
- **결과 크기:** 다른 Tool처럼 `dstone.ai.tool.max-result-chars`(기본 60,000자)를 넘으면 잘립니다.
- **오래된 결과 줄이기:** Tool 결과 합계가 `keep-result-chars`(기본 200,000자)를 넘으면 오래된 결과부터 앞부분 300자만 남습니다. 긴 대화에서는 Skill 본문도 잘릴 수 있고, 이때 LLM은 다시 불러야 합니다.
- **`toolsEnabled: false`(채팅 API):** 일반 Tool만 꺼지고 Skill은 남습니다.
- **caller 화이트리스트:** `allowed-by-caller`는 일반 Tool에만 적용되고 Skill에는 적용되지 않습니다.
- **Sub Agent:** `sample-skill-agent`처럼 Skill을 가진 Agent를 Sub Agent로도 쓸 수 있습니다. 샘플은 `sample-subagent-background-agent`입니다.

`toolsEnabled: false`이면 Skill 본문은 읽어도 `readFile`이 없어 참고 파일은 못 엽니다. Skill 본문이 참고 파일을 전제로 한다면 절차가 중간에 막힐 수 있습니다.

## 6. 수정과 반영

| 수정한 것 | 반영 시점 |
|---|---|
| `SKILL.md` | 등록소에 올라간 내용을 쓰므로 `POST /api/ai/definitions/reload`를 호출해야 함 |
| 참고 파일(`checklist.md` 등) | 호출할 때마다 읽으므로 다음 `readFile`부터 바로 반영 |
| Agent의 `skills.allowed` | 다시 읽기 또는 디자이너 저장 후 반영 |

다시 읽기는 Skill → Agent → Workflow 순서로 전부 검사합니다. 모두 통과해야 세 등록소를 함께 교체하고, 하나라도 실패하면 아무것도 바뀌지 않습니다. 디자이너 화면에서는 Skill을 고르기만 할 수 있고, 내용은 파일에서 직접 고칩니다.

## 7. 없는 기능과 한계

- **스크립트 실행 없음:** 의도적으로 뺐습니다. 라이브러리의 `ShellTools`가 셸 화이트리스트를 우회하기 때문입니다. 스크립트가 필요하면 `ShellExecTool` 화이트리스트에 올려 Tool로 부릅니다.
- **호출 보장 없음:** LLM이 Skill을 안 부르거나 안내를 안 따를 수 있고, 본문의 절차도 엔진이 강제하지 않습니다. 반드시 지켜야 하는 절차는 Workflow step이나 Java Tool로 만들어야 합니다.
- **"한 번만 부르십시오"도 안내일 뿐:** 다시 부르는 것을 막는 코드는 없으며, 그만큼 호출 횟수와 대화 길이가 늘어납니다.
- **본문에 입력값 슬롯 없음:** Skill 본문은 `PromptText` 렌더링을 거치지 않아 `${input.x}`가 채워지지 않습니다. 대문자 환경변수(`${APP_HOME}` 등)는 로더가 파일을 읽을 때 채웁니다.
- **Agent 선택지가 많을수록 부정확:** 설명만 보고 고르는 능력은 모델마다 다르므로 `skills.allowed`로 후보를 좁히는 것이 좋습니다.
- **실제 사용 범위:** 지금 Skill을 쓰는 Agent는 `sample-skill-agent` 하나이고, `pilot`이나 `sm` Agent에는 아직 적용되지 않았습니다.

## 8. 직접 확인하는 방법

"Workflow 테스트" 화면에서 다음과 같이 호출합니다.

```
workflowId: sample-agent-skill
input     : SELECT id, name FORM member WHERE id = 1 이 SQL 좀 봐줘
```

`FORM`은 일부러 넣은 오타입니다. Skill 호출 → 검사 실패 → `checklist.md` 읽기 → 수정 → 재검사 순서로 진행되어야 합니다. Skill 호출 여부는 로그에서 확인할 수 있습니다.

```
dstone-ai-engine skill: agent[sample-skill-agent] -> skill[sql-review] ...자
```

LLM 없이 테스트하려면 다음을 실행합니다.

```bash
cd dstone-ai-engine && mvn test -DskipTests=false -Dtest=SampleAgentSkillTest
```

**관련 소스**
- `src/main/java/net/dstone/ai/runtime/agent/SkillTool.java`
- `src/main/java/net/dstone/ai/common/registry/SkillRegistry.java`
- `src/main/java/net/dstone/ai/common/registry/AgentRegistry.java:372`
- `src/main/java/net/dstone/ai/runtime/agent/AgentExecutor.java:470`
- `docs/09.dstone-ai-engine.md` 9장

---

## 질문

dstone-ai-engine 에서 
skill, tool, subagent 의 기능적인 차이점을 알려줘.

---

## 답변

세 가지 모두 LLM에게는 **호출할 수 있는 Tool**로 보이지만, 호출했을 때 벌어지는 일이 다릅니다.

- **Tool:** 코드를 실행해서 결과를 받습니다.
- **Skill:** 작업 안내문을 읽어 와서 Agent가 직접 따릅니다.
- **Sub Agent:** 다른 Agent에게 일을 맡기고 결과만 받습니다.

## 핵심 차이

| 구분 | Tool | Skill | Sub Agent |
|---|---|---|---|
| **실체** | Java `@AiTool` 메서드 또는 MCP Tool | `SKILL.md` 문서 | 다른 Agent 정의(`.md`) |
| **호출하면** | 코드 실행 | 본문을 텍스트로 반환 | 별도 LLM 호출 실행 |
| **일하는 주체** | 코드 | 호출한 Agent 자신 | 자식 Agent |
| **Agent 설정** | `tools.allowed` | `skills.allowed` | `subAgents` |
| **LLM에게 보이는 모양** | Tool마다 하나 | 전체를 묶은 `Skill` 하나 | Sub Agent마다 하나 |
| **입력** | Tool의 인자 스키마 | `command`(Skill 이름) | 자식의 `input` 스키마 |
| **결과** | Tool 응답 | 안내문과 폴더 경로 | 자식의 답(`output` 계약 검증) |
| **추가 LLM 비용** | 없음 | 없음(대화가 길어져 이후 호출에 영향) | 있음 |
| **부모 대화에 쌓이는 것** | Tool 결과 | Skill 본문 | 자식의 최종 답만 |

## 동작이 갈리는 부분

| 항목 | Tool | Skill | Sub Agent |
|---|---|---|---|
| **Workflow step에서 직접 호출** | 가능(TOOL step, LLM 없음) | 불가 | 불가(그 Agent를 AGENT step으로 부르는 것은 가능) |
| **대화 내용 공유** | 해당 없음 | 부모 대화에 그대로 들어감 | 빈 대화로 시작하고 인자만 봄 |
| **호출 한도** | `maxToolCalls` | Tool과 같은 한도를 함께 사용 | 별도 한도 `sub-agent.max-calls`(기본 10) |
| **결과 크기 상한** | 적용 | 적용 | 적용 |
| **채팅 `toolsEnabled: false`** | 꺼짐 | 유지 | 유지 |
| **caller 제한** | `allowed-by-caller` | 없음 | 자식의 `allowedCallers` |
| **중첩** | 해당 없음 | 없음 | 한 단계까지만(자식은 `subAgents` 불가) |
| **백그라운드 실행** | 없음 | 없음 | `background: true`로 가능, `TaskOutput`으로 결과 수거 |

## 무엇을 쓸지 고르는 기준

| 상황 | 선택 | 이유 |
|---|---|---|
| 파일 읽기, SQL 검사, API 호출처럼 **실제 동작이나 정해진 계산**이 필요함 | Tool | 코드가 처리하므로 결과가 일정함 |
| **LLM 판단 없이** 항상 같은 처리를 함 | Tool + TOOL step | LLM을 거치지 않음 |
| **특정 일에만 필요한 절차**가 프롬프트를 길게 만듦 | Skill | 필요할 때만 읽어 들임 |
| 여러 Agent가 **같은 절차를 공유**함 | Skill | 문서 하나만 고치면 됨 |
| **조사량이 많아** 부모 대화가 커짐 | Sub Agent | 읽은 내용은 자식에 남고 답만 돌아옴 |
| 일부 작업에 **다른 역할, 도구, 모델**이 필요함 | Sub Agent | 자식이 자기 프롬프트, Tool, 모델을 가짐 |
| **비슷한 일 여러 개를 동시에** 처리함 | Sub Agent(background) | 병렬로 맡기고 결과를 모음 |
| **순서나 실행이 반드시 지켜져야** 함 | 셋 다 아닌 Workflow step | 셋 모두 LLM이 부를 때만 실행됨 |

Skill과 Sub Agent는 LLM 판단에 맡기는 방식이라 호출이 보장되지 않습니다. LLM이 Tool로 부르는 경우도 마찬가지이고, 호출을 보장하려면 TOOL step을 씁니다.

## 함께 쓰는 예

저장소의 샘플이 세 가지를 모두 연결합니다.

```
sample-subagent-background-agent              ← 부모 Agent
 └ Sub Agent: sample-skill-agent              ← SQL 문마다 맡김, TaskOutput으로 수거
     ├ Skill: sql-review                      ← 검토 절차를 읽음
     └ Tool: validateSqlSyntax, readFile      ← 실제 검사, 참고 파일 읽기
```

이번 답변은 앞서 읽은 소스와 `CLAUDE.md`를 근거로 정리했으며, 새로 실행해 확인하지는 않았습니다.
