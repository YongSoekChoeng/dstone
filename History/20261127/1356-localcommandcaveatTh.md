# localcommandcaveatTh

> 생성일시: 2026-11-27 13:56

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/model</command-name>
            <command-message>model</command-message>
            <command-args></command-args>

---

## 질문

<local-command-stdout>Set model to `Sonnet 5.5` and saved as your default for new sessions</local-command-stdout>

---

## 질문

dstone-knowledge 의 검색 · 문서 메뉴의 문서올리기를 통해서 dstone-ai-engine 에서 해당 문서를 참고하게 하려면 어떻게 해야 하지?

---

## 답변

문서 올리기부터 ai-engine이 읽는 경로까지 코드에서 확인하겠습니다.

---

## 답변

문서를 올리면 ai-engine 쪽에 따로 넣을 것은 없습니다. 대신 **Agent가 문서 Tool을 쓸 수 있어야** 읽습니다. 자동 RAG는 없어서 LLM이 필요할 때 Tool로 찾습니다.

## 1. 문서 올리기 (dstone-knowledge)
- **검색 · 문서 > 문서 올리기**에서 파일을 올립니다. `묶음`(예: `dev-guide`)을 붙여 두면 검색 범위를 좁히기 쉽습니다.
- 같은 문서 이름으로 다시 올리면 교체됩니다.
- 영문 이름(함수, 컴포넌트 등)은 바로 검색됩니다. 뜻으로 찾기는 임베딩이 끝난 뒤에 되므로 Ollama가 떠 있어야 합니다.

## 2. 연결 확인 (ai-engine)
- `conf/env*.properties`의 `KNOWLEDGE_HOST`/`KNOWLEDGE_PORT`가 `dstone.ai.tool.knowledge.base-url`이 됩니다. WSL은 `localhost:4081`로 이미 설정돼 있습니다.
- 지금 설정은 knowledge의 API 인증이 꺼져 있어(`security.auth.enabled: false`) 키 없이 보입니다.
- **인증을 켜면** 문서가 호출자별로 나뉩니다. 화면에서 올린 문서의 주인은 `dstone-knowledge-screen`이므로, ai-engine에 줄 키의 `caller`도 이 이름이어야 합니다. 다른 이름이면 올린 문서가 보이지 않습니다. 키는 ai-engine의 `dstone.ai.tool.knowledge.api-key`에 `ENC(...)`로 넣습니다.

## 3. Agent에 Tool 붙이기

| Tool | 하는 일 |
|---|---|
| `knowledgeListDocs` | 올린 문서와 묶음 목록 |
| `knowledgeSearchDocs` | 문서 검색 (묶음·문서로 범위 지정) |
| `knowledgeGetChunks` | 찾은 조각의 앞뒤 이어 읽기 |

**채팅에서 쓸 때**: `sample-general-chat`에는 문서 Tool이 없습니다. 채팅 화면의 **[+ 찾아서 추가]**에서 세 개를 고르고 "dev-guide 묶음에서 ○○ 방법을 찾아 줘"처럼 물으면 됩니다.

**Agent·Workflow에서 쓸 때**: Agent 정의의 `tools.allowed`에 추가합니다. Workflow 디자이너에서 고치면 재시작 없이 반영됩니다.

```yaml
tools:
  allowed: [knowledgeListDocs, knowledgeSearchDocs, knowledgeGetChunks]
```

반드시 검색하게 하려면 다음을 더합니다. 검색에 성공하지 못하면 step이 실패 처리됩니다.

```yaml
execution:
  requiredTools: [knowledgeSearchDocs]
```

## 4. 언제, 어느 문서를 볼지 알려 주기
지금 SM 쪽에서 문서 Tool을 가진 Agent는 `sm-architect-agent`뿐입니다. 그런데 이 Agent의 프롬프트나 `skills/sm` 어디에도 "올린 문서를 언제 보라"는 안내가 없습니다. 그래서 문서를 올려도 **참고한다는 보장이 없습니다.**

내용에 따라 두는 곳을 나눕니다.

| 내용 | 두는 곳 |
|---|---|
| "작업 전에 관련 가이드가 올라와 있는지 확인한다" 같은 범용 방법 | `skills/sm`의 Skill (예: `source-investigation`) |
| "이 프로젝트의 화면을 만들 때는 `dev-guide` 묶음을 본다" 같은 프로젝트 지식 | `skills/projects/{프로젝트ID}/`의 Skill |
| 개발·자동 수정 단계에서도 봐야 하는 경우 | 해당 Agent의 `tools.allowed`에도 Tool 추가 |

적용한 뒤에는 실행 진행 기록에서 `knowledgeSearchDocs`가 실제로 불렸는지 확인하세요.

참고할 Agent나 묶음 이름을 알려 주시면 Tool과 Skill 수정까지 해 드리겠습니다.

---

## 질문

만일 ai-engine 에서 프로젝트ID 가 testApp 인 sm-workflow 를 진행한다고 하고 지금 knowledge 의 검색.문서 메뉴에서 등록하는 자료를 참고할 수 있게 하려고 한다면 knowledge 에서 문서올리기 를 할 때 구체적으로 어떻게 해야 하나?

---

## 답변

핵심은 **`묶음`에 `testApp`을 넣는 것**, 그리고 **그 묶음을 보라고 Agent에게 알려 주는 것**입니다. 올린 문서는 프로젝트ID와 자동으로 연결되지 않습니다. 묶음 이름은 자유 입력이고, `testApp`으로 맞추는 것은 우리가 정하는 약속입니다.

## 1. 문서 올리기 (검색 · 문서 > 문서 올리기)

| 항목 | 입력 예 | 설명 |
|---|---|---|
| 파일 | `화면개발가이드.docx` | PDF, Word, PowerPoint, Excel, 텍스트 |
| 문서 이름 | `testApp-screen-guide` | 같은 이름은 교체됩니다. 모든 문서에서 겹치지 않게 프로젝트ID를 앞에 붙입니다 |
| 제목 | `testApp 화면 개발 가이드` | Agent가 문서를 고를 때 보므로 내용이 드러나게 씁니다 |
| 묶음 | `testApp` | 여러 문서를 올려도 같은 값으로 둡니다 |

- Word는 **제목 스타일**, PDF는 **북마크**가 있어야 장/절 단위로 잘 나뉩니다.
- `.md`의 `#` 제목은 장/절로 인식되지 않을 수 있으니 Word나 HTML이 낫습니다.
- 올린 뒤 목록에서 임베딩 수가 조각 수와 같아지면 뜻으로도 검색됩니다.

## 2. sm-workflow에서 보게 하기

지금은 `sm-architect-agent`(17-아키텍처 설계)만 문서 Tool을 가지고 있습니다. 영향도 분석과 개발 단계는 올린 문서를 직접 읽지 못합니다.

### 방법 A: 요청서에 적기 (수정 없이 시험)
설계 Agent는 요청서 전문을 받으므로 `00.request.md`에 다음처럼 적습니다.

```markdown
## 프로젝트ID
testApp

## 참고 문서
설계하기 전에 knowledgeSearchDocs를 collections=testApp 으로 불러 관련 가이드를 확인하고,
따른 규칙은 문서 제목과 함께 설계서에 적는다.
```

### 방법 B: 프로젝트 Skill에 적기 (매번 적지 않으려면)
`skills/projects/testApp/gnx-crud-screen/SKILL.md`에 절을 하나 추가합니다.

```markdown
## 올린 문서

이 프로젝트의 가이드는 묶음 `testApp`에 있다. 문서 Tool이 있으면 설계나 구현을 정하기 전에
knowledgeSearchDocs(collections=testApp)로 관련 규칙을 찾는다.
문서와 기존 소스가 다르면 어느 쪽을 따랐는지 산출물에 적는다.
```

이 Skill은 Agent가 필요하다고 판단해 불러야 읽힙니다. 따라서 실행 후 호출 여부를 확인해야 합니다.

### 개발 단계에도 필요하면
`sm-developer-agent`의 `tools.allowed`에 `knowledgeListDocs`, `knowledgeSearchDocs`, `knowledgeGetChunks`를 추가합니다. 다만 설계서에 규칙이 반영되면 개발은 그대로 따르므로, 먼저 설계 단계만으로 시험하는 편이 낫습니다. 영향도 분석(`sm-analyze-impact-agent`)도 같은 방식으로 추가할 수 있습니다.

`requiredTools: [knowledgeSearchDocs]`는 Agent에 걸리는 설정이라, 문서가 없는 다른 프로젝트에도 검색을 강제합니다. 처음에는 넣지 않는 것을 권합니다.

## 3. 확인
- 실행 진행 기록에서 17-아키텍처 설계의 `knowledgeSearchDocs` 호출과 범위가 `testApp`인지 봅니다.
- `03-1.design.md`에 문서의 규칙이 실제로 반영됐는지 봅니다.

원하시면 방법 B의 Skill 수정을 바로 반영하겠습니다.
