# History_에_대화내용저장_후킹이

> 생성일시: 2026-08-28 13:54

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
