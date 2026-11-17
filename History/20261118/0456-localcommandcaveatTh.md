# localcommandcaveatTh

> 생성일시: 2026-11-18 04:56

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

지금 Repomix 가 skill로 깔려있는 상태인가?

---

## 답변

아니요, Repomix는 지금 skill로도 플러그인으로도 쓸 수 없는 상태입니다.

- **설정에는 켜져 있습니다.** `/app/dstone/.claude/settings.json`에 Repomix 마켓플레이스(`yamadashy/repomix`)와 플러그인 3개(`repomix-mcp`, `repomix-commands`, `repomix-explorer`)가 `true`로 적혀 있습니다.
- **설치는 되어 있지 않습니다.** `~/.claude/plugins/installed_plugins.json`과 `known_marketplaces.json` 어디에도 repomix가 없습니다. 이번 세션에도 Repomix 관련 skill, 명령, MCP 도구가 하나도 올라오지 않았습니다.
- **skill 폴더에도 없습니다.** `~/.claude/skills`나 `/app/dstone/.claude/skills`에 repomix 폴더가 없고, `repomix` CLI도 전역으로 설치되어 있지 않습니다.

설정만 해 두고 실제 설치는 아직 안 한 상태입니다. 쓰려면 Claude Code 안에서 다음 순서로 설치하고 세션을 다시 시작하세요.

```
/plugin marketplace add yamadashy/repomix
/plugin install repomix-mcp@repomix
/plugin install repomix-commands@repomix
/plugin install repomix-explorer@repomix
```

`repomix-mcp`는 내부에서 `npx repomix`를 실행합니다. 그래서 Node/npx가 있으면 CLI를 따로 설치하지 않아도 됩니다.
