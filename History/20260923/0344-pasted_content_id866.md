# pasted_content_id866

> 생성일시: 2026-09-23 03:44

## 질문

<pasted_content id="866b">
dstone-ai-engine 에서 agent 의 name 이라는 항목이 있는데 이걸 id로 수정해줘. agent.name => agent.id 로. java 파일과 yml 통털어서 모두.
</pasted_content id="866b">

---

## 답변

I'll rename `agent.name` to `agent.id` across dstone-ai-engine — let me find every occurrence first.

---

## 답변

Now let's check the other AgentDefinition-style records (WorkFlowDefinition) for the `id` doc-comment convention to match wording.

---

## 답변

Now I'll apply the renames. Starting with the core Java files.

---

## 답변

Now `AgentRegistry.java`:

---

## 답변

Now let's verify no other Java files reference `.name()` on AgentDefinition (e.g., in step runners, workflow executor referencing agent.name for logging).

---

## 답변

Good — no other `.name()` calls on `AgentDefinition` remain. Now let's update all 12 YAML files.

---

## 답변

All 12 renamed. Now let's check the top-of-file comments in each YAML that say "파일 이름이 곧 이 Agent의 이름(ID)이 됩니다" for any other `name` wording that should shift.

---

## 답변

Now fix `sample-general-chat.yml`'s specific `agent.name` references and `sample-router-classifier-agent.yml`'s comment context.

---

## 답변

That's about route labels, unrelated — no change needed. Now let's check `dstone-boot`'s ChatService.java for any `agent.name`-style reference, and update docs.

---

## 답변

That's the `ChatRequest.agent` DTO field (unrelated — it's the request field name "agent", not the `AgentDefinition.name`), calling into `POST /api/ai/chat`, so no change needed there. Now update the docs file.

---

## 답변

Let's check for any other `agent.*name` mentions across the whole doc (table entries, architecture package table, etc.) and confirm nothing else references `AgentDefinition`'s `name` field specifically.

---

## 답변

None of these reference the specific `name` field — they're fine as-is. Now let's build the module to make sure everything compiles.

---

## 답변

dstone-common built fine. Now build dstone-ai-engine.

---

## 답변

Compiles cleanly. Let's do a final sanity sweep for any lingering `agent.name`/`.name()`-on-`AgentDefinition` references anywhere in the repo (including tests, k8s configs, other modules).

---

## 답변

No remaining references. Summary of changes:

- **`AgentDefinition.java`**: record field `name` → `id` (javadoc updated).
- **`YamlDefinitionLoader.java`**, **`AgentRegistry.java`** (also renamed internal `byName` map → `byId`, param `agentName` → `agentId`), **`ConfigCallLog.java`**: updated all `.name()` calls to `.id()` and related log/error text.
- **12 agent YAML files** under `resources/agents/{sample,testApp}/`: `name:` key → `id:`, plus header-comment wording ("Agent의 이름(ID)" → "Agent의 id").
- **`docs/09.dstone-ai-engine.md`**: updated the YAML authoring example and prose to say `id:`/"agent id".

Verified: `mvn compile` succeeds for both `dstone-common` and `dstone-ai-engine`, and no remaining references to the old `agent.name`/`AgentDefinition.name()` exist anywhere in the repo. `ChatRequest`/`ChatResponse`'s `agent` field (the request payload key naming which Agent to call) was intentionally left untouched since it's a different, unrelated field.
