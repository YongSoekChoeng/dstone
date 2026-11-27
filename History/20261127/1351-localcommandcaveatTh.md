# localcommandcaveatTh

> 생성일시: 2026-11-27 13:51

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
