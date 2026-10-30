---
# 이 파일 하나가 Agent 하나를 정의합니다. Agent의 id는 아래 id 값입니다.
#
# 이 Agent는 Skill(skills.allowed) 기능을 보여주는 예시입니다.
# skills.allowed에 적은 sql-review는 skills/sample/sql-review/SKILL.md입니다. LLM에게는 Skill이라는 Tool 하나가 보이고,
# 그 설명에 sql-review의 이름과 설명이 들어 있습니다. LLM이 SQL 검토 일이라고 판단하면 Skill을 불러 본문을 읽고 따릅니다.
# 그래서 이 Agent의 프롬프트에는 검토 순서를 적지 않습니다. 순서는 Skill 본문에 있고, 다른 Agent도 같은 Skill을 쓸 수 있습니다.
#
# 알아 둘 규칙
# - Skill 본문이 같은 폴더의 checklist.md를 readFile로 읽으라고 하므로, tools.allowed에 readFile을 적었습니다.
# - Skill 호출도 Tool 호출 횟수(execution.maxToolCalls)에 들어갑니다.
# - workflows/sample/sample-agent-skill.yml이 이 Agent를 부르고,
#   sample-subagent-background-agent.md가 이 Agent를 Sub Agent로 씁니다(그래서 description이 꼭 있어야 합니다).
id: sample-skill-agent
version: 1.0.0
description: Skill 확인용 - SQL 문 하나를 받아 문법을 검사하고, 틀렸으면 고쳐서 결과를 돌려준다.
tools:
  allowed: [validateSqlSyntax, readFile]
skills:
  allowed: [sql-review]
execution:
  maxToolCalls: 10
---
당신은 SQL 문을 검토하는 AI 어시스턴트입니다.
받은 문장에서 SQL 문을 찾아 검토하고, 그 결과를 한국어로 답하십시오.
SQL 검토 방법은 알맞은 Skill이 있으면 그 안내를 따르십시오.
