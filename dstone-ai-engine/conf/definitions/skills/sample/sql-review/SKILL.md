---
# 이 파일 하나가 Skill 하나를 정의합니다. Skill 이름은 아래 name 값이고, 폴더 이름과 같아야 합니다.
#
# Skill은 "이런 일을 할 때는 이렇게 하라"는 작업 안내서입니다. Agent는 평소에 name과 description만 보고 있다가,
# 맞는 일이 오면 Skill Tool로 이 파일의 본문(아래 --- 다음부터)을 불러 읽고 그대로 따릅니다.
# 이 Skill을 쓰는 Agent: agents/sample/sample-skill-agent.md (skills.allowed: [sql-review])
#
# 같은 폴더의 checklist.md는 참고 파일입니다. 엔진은 읽지 않고, Agent가 본문의 안내를 보고 readFile로 읽습니다.
# 그래서 이 Skill을 쓰는 Agent는 tools.allowed에 readFile이 있어야 합니다(없으면 기동할 때 경고가 남습니다).
name: sql-review
description: SQL 문을 검토하거나 고칠 때 쓴다. 문법 검사 순서와 자주 틀리는 항목 목록을 담고 있다.
---
# SQL 검토 순서

1. 검토할 SQL 문을 하나씩 떼어 냅니다. 한 번에 한 문장만 검토합니다.
2. validateSqlSyntax로 문법을 검사합니다.
3. 검사가 실패하면 이 Skill 폴더의 checklist.md를 readFile로 읽고, 목록에 있는 항목부터 의심해 고칩니다.
   checklist.md는 한 번만 읽습니다.
4. 고친 SQL을 다시 validateSqlSyntax로 검사합니다. 고치기는 두 번까지만 합니다.

# 답하는 모양

- 첫 줄: `결과: 통과` 또는 `결과: 실패`
- 둘째 줄부터: 최종 SQL, 그리고 무엇을 왜 고쳤는지 한두 문장. 고친 것이 없으면 "고친 곳 없음".
