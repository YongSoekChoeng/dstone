# pasted_content_idff7

> 생성일시: 2026-09-22 20:51

## 질문

<pasted_content id="ff7e">
현재 구현되어 있는 내용을 토대로 아래의 문서 최신화 해줘.
/dstone/docs/09.dstone-ai-engine.md
다이어그램 처럼 비주얼이 풍부하고, 설명이 쉽고, 직관적고도 자세한 내용이 되도록 최대한 노력해줘.
특히 Workflow -> Step -> (Agent/Approval/Tool) -> LLM 간의 IN/OUT 형태와 항목들에 대해서 자세한 설명이 꼭 들어갔으면 해.
</pasted_content id="ff7e">

---

## 답변

I'll start by reviewing the current doc and the actual `dstone-ai-engine` source to make sure the rewrite matches what's really implemented.

---

## 답변

Now I have everything needed. I'll write the fully updated documentation, including the new centerpiece section on Step IN/OUT contracts with diagrams.

---

## 답변

All 15 sections now number correctly and every `[N절]` cross-reference points at a section that actually exists at that number. The rewrite is complete.

Summary of what changed in `docs/09.dstone-ai-engine.md`:

- **New centerpiece — §6 "Step IN/OUT 완전 참조"**: a dedicated, diagram-heavy section tracing exactly how data moves Workflow → Step → Agent/Approval/Tool → LLM, with Mermaid sequence diagrams for each `StepType` (AGENT plain/structured, SUPERVISOR, ROUTER, TOOL, APPROVAL), the `StepInput`/`StepOutput` field tables, a 7-step breakdown of what `AgentExecutor` actually sends the LLM (and what it gets back), a `ToolExecutor` breakdown, a token-scope table for `{previous}`/`{변수명}`/`{stepId.키}`, and a full worked example (`testApp-sdlc`, the real 8-step approval SDLC workflow) traced step-by-step.
- **Corrected drift found by reading the actual code**:
  - The logging section described a `WF_STEP phase=... logfmt` one-liner that no longer exists — replaced with the real multi-line `ConfigCallLog` block format (`[StepRunner - ...] Start/End`, etc.).
  - Added the previously-undocumented `tools.jenkins.JenkinsTriggerBuildTool` and its config keys.
  - Updated the YAML resource references — files were reorganized into `agents/sample/`, `agents/testApp/`, `workflows/sample/`, `workflows/testApp/` with new prefixes, and a new `testApp-sdlc.yml` exists.
  - Fixed `maxIterations` default (10, not 5) and other config defaults per current code/`Constants.java`.
  - Clarified a subtle but important fact: `inputTemplate` is only ever read by `ToolStepRunner` — AGENT/SUPERVISOR/ROUTER steps ignore it entirely and always send the raw previous-step text to the LLM.
- Renumbered sections 6–14 to 7–15 to make room, and updated every internal cross-reference and the table of contents.
