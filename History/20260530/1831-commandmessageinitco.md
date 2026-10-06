# commandmessageinitco

> 생성일시: 2026-05-30 18:31

## 질문

<command-message>init</command-message>
<command-name>/init</command-name>

---

## 질문

Please analyze this codebase and create a CLAUDE.md file, which will be given to future instances of Claude Code to operate in this repository.

What to add:
1. Commands that will be commonly used, such as how to build, lint, and run tests. Include the necessary commands to develop in this codebase, such as how to run a single test.
2. High-level code architecture and structure so that future instances can be productive more quickly. Focus on the "big picture" architecture that requires reading multiple files to understand.

Usage notes:
- If there's already a CLAUDE.md, suggest improvements to it.
- When you make the initial CLAUDE.md, do not repeat yourself and do not include obvious instructions like "Provide helpful error messages to users", "Write unit tests for all new utilities", "Never include sensitive information (API keys, tokens) in code or commits".
- Avoid listing every component or file structure that can be easily discovered.
- Don't include generic development practices.
- If there are Cursor rules (in .cursor/rules/ or .cursorrules) or Copilot rules (in .github/copilot-instructions.md), make sure to include the important parts.
- If there is a README.md, make sure to include the important parts.
- Do not make up information such as "Common Development Tasks", "Tips for Development", "Support and Documentation" unless this is expressly included in other files that you read.
- Be sure to prefix the file with the following text:

```
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
```

---

## 답변

I'll analyze the codebase structure to create an accurate CLAUDE.md file.

---

## 답변

Now I have enough information to create a comprehensive CLAUDE.md file.

---

## 답변

`CLAUDE.md` has been created at `/app/dstone/CLAUDE.md`. It covers:

- **Build commands** in the correct order (dstone-common must be built first)
- **Run commands** for both boot and batch modules
- **Configuration pattern** — the `conf/` directory structure and startup sequence
- **Database setup** — HikariCP + MyBatis + log4jdbc pattern, Jasypt encryption for credentials
- **dstone-boot specifics** — multi-datasource setup, Spring Security with social login, the source code analyzer feature
- **dstone-batch specifics** — `BaseJobConfig`/`@AutoRegJob` job development pattern, NEVER auto-init of batch schema tables
- **Infrastructure requirements** and environment variables
- **CI/CD** — Jenkins pipeline locations
