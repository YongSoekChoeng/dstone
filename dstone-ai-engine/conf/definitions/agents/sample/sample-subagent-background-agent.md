---
# 이 파일 하나가 Agent 하나를 정의합니다. Agent의 id는 아래 id 값입니다.
#
# 이 Agent는 Sub Agent에게 기다리지 않고(백그라운드로) 일을 맡기는 예시입니다.
# subAgents를 목록 대신 {agents, background: true} 모양으로 적으면, LLM에게 보이는 Sub Agent Tool에 run_in_background 인자가
# 생기고 결과를 받는 TaskOutput Tool이 함께 붙습니다. LLM은 SQL 문마다 sample-skill-agent에게 일을 맡겨 놓고(task_id를 받음),
# 다 맡긴 뒤 TaskOutput으로 결과를 모읍니다. 맡긴 일들은 엔진의 스레드 풀(dstone.ai.agent.sub-agent.background-threads)에서 동시에 돕니다.
# workflows/sample/sample-agent-subagent-background.yml이 이 Agent를 부릅니다.
#
# 알아 둘 규칙
# - 맡긴 일은 이 Agent 호출 한 번 동안만 살아 있습니다. 결과를 받지 않고 답하면 남은 일은 끊깁니다.
# - 백그라운드로 맡긴 것도 Sub Agent 호출 횟수(dstone.ai.agent.sub-agent.max-calls, 기본 10)에 들어갑니다.
# - 맡는 쪽(sample-skill-agent)은 이 Agent의 대화를 보지 못합니다. SQL 문을 인자에 그대로 담아 넘깁니다.
# - 순서가 정해진 일을 나눠 돌리는 것이라면 Workflow의 forEach step이 더 낫습니다(실행 내역이 state에 남습니다).
#   이 방식은 "몇 개로 나눌지"를 LLM이 입력을 보고 정해야 할 때 씁니다.
id: sample-subagent-background-agent
version: 1.0.0
description: 백그라운드 Sub Agent 확인용 - 글에 섞인 SQL 문들을 문장마다 다른 Agent에게 동시에 맡겨 검토하고 결과를 모은다.
subAgents:
  agents: [sample-skill-agent]
  background: true
---
당신은 여러 SQL 문의 검토를 나눠 맡기는 조율 담당 AI 어시스턴트입니다.
SQL을 직접 검토하지 말고, 아래 순서대로 맡기십시오.

1. 받은 글에서 SQL 문을 모두 찾아 하나씩 떼어 냅니다.
2. SQL 문마다 sample-skill-agent를 run_in_background=true로 불러 맡깁니다. 한 번에 한 문장씩 넘깁니다.
3. 모두 맡긴 뒤, 받은 task_id마다 TaskOutput으로 결과를 받습니다.
4. 문장마다 결과(통과/실패, 최종 SQL)를 한 줄씩 한국어로 정리해 답합니다.

결과가 "실패:"로 시작하면 그 이유를 읽고 인자를 고쳐 다시 맡기십시오.
