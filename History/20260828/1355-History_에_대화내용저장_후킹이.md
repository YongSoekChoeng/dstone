# History_에_대화내용저장_후킹이

> 생성일시: 2026-08-28 13:55

## 질문

History 에 대화내용저장 후킹이 안되는 것 같은데

---

## 답변

I found the root cause. Let me report what I have while confirming the exact fix.

I dug through the session transcripts and git history and found the actual cause.

**What happened:** The `Stop` hook (`.claude/hooks/save-history.sh`) ran successfully 4 times today (08:43, 10:55, 12:47, 13:00 — all committed to `History/20260828/`). Then at **13:33 KST it failed with exit code 127**: `/bin/sh: 1: .claude/hooks/save-history.sh: not found`.

**Why:** The hook command in `.claude/settings.json` is a **relative path**:
```json
"command": ".claude/hooks/save-history.sh"
```
Earlier in that session a Bash tool call had `cd`'d into `dstone-common` (Bash's working directory persists across tool calls within a session), and it never `cd`'d back. When the session ended, the Stop hook fired with `cwd=/app/dstone/dstone-common` instead of `/app/dstone`, so the relative path resolved to a nonexistent file and the save silently failed — that's the conversation where the messaging/outbox/saga code was added, which you ended up committing manually as `"."`.

I'm checking with a Claude Code docs agent to confirm the cleanest fix (there's usually a `$CLAUDE_PROJECT_DIR`-style variable meant exactly for this), and I'll apply it once confirmed.

---

## 질문

<task-notification>
<task-id>a0c07573e0f724769</task-id>
<tool-use-id>toolu_01DvcJ8iFzEvqWtwPmakjs31</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/aff7a028-e89c-40f8-9ce4-bb1d686b6333/tasks/a0c07573e0f724769.output</output-file>
<status>completed</status>
<summary>Agent "Confirm hook cwd and CLAUDE_PROJECT_DIR behavior" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>Yes, your observations are correct. Here are the answers based on the official Claude Code documentation:

## 1. Working Directory Behavior

**Yes**, Claude Code executes hook commands with the **current working directory at the time of the hook invocation**. This is captured in the `cwd` field of the hook's JSON input. If a prior Bash call changed directories, the hook sees that working directory—not the project root. This explains your 127 error.

## 2. Environment Variable for Project Root

**Yes**, Claude Code provides **`$CLAUDE_PROJECT_DIR`**, which is set to the project root where the session started and remains stable regardless of where the shell's `cwd` moves. (For worktrees, `cwd` follows the agent into the worktree, but `CLAUDE_PROJECT_DIR` stays at the original project root.)

## 3. Recommended Path Specification

The officially recommended way is to use **exec form with `${CLAUDE_PROJECT_DIR}`**:

```json
{
  "type": "command",
  "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/save-history.sh",
  "args": []
}
```

If you use shell form (no `args` array), wrap the placeholder in double quotes:

```json
{
  "type": "command",
  "command": "\"${CLAUDE_PROJECT_DIR}\"/.claude/hooks/save-history.sh"
}
```

**Exec form is preferred** because it safely handles the placeholder expansion without shell interpretation.

**Source:** [Claude Code hooks.md — Specifying Hook Command Paths](https://code.claude.com/docs/en/hooks.md)</result>
<usage><subagent_tokens>19517</subagent_tokens><tool_uses>3</tool_uses><duration_ms>31498</duration_ms></usage>
</task-notification>

---

## 답변

Confirmed — Claude Code hooks run with whatever `cwd` the session's Bash calls left behind, and `${CLAUDE_PROJECT_DIR}` is the built-in variable meant to always resolve to the project root regardless of that. Applying the fix.

---

## 답변

Good — invoked with an absolute path from a different cwd, it now runs correctly instead of `not found`.

**Root cause:** `.claude/settings.json` pointed to the hook with a relative path (`.claude/hooks/save-history.sh`). Claude Code runs hooks in whatever directory a prior Bash `cd` left the session in, not the project root. In the 13:00–13:35 session, a `cd dstone-common` was never undone, so at Stop the hook resolved to `dstone-common/.claude/hooks/save-history.sh` → not found (exit 127) → that conversation (the messaging/outbox/saga work) never got saved to `History/`, which is why you committed it manually as `"."`.

**Fix applied:** changed the command to `${CLAUDE_PROJECT_DIR}/.claude/hooks/save-history.sh`, Claude Code's built-in variable that always points at the project root regardless of cwd. Verified it now runs correctly from a subdirectory.

Note: the 13:00–13:35 conversation's history file is permanently missed (the hook only reads the live transcript at Stop time). Let me know if you want me to reconstruct a `History/` entry for it manually from the transcript — otherwise nothing further to do, future Stop events will save correctly.
