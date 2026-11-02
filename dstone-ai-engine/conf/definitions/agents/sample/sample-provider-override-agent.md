---
# 이 Agent만 기본 provider(spring.ai.model.chat)가 아닌 다른 provider를 쓰는 예시입니다.
# 접속 정보와 기본 모델은 conf/application.yml의 spring.ai.ollama에서 읽습니다.
# 다른 provider로 바꾸면 그쪽 api-key가 있어야 기동됩니다.
id: sample-provider-override-agent
version: 1.0.0
description: per-Agent provider 확인용 Agent. 기본 provider와 달리 ollama로 답한다.
model:
  provider: ollama
tools:
  allowed: [getCurrentDateTime]
---
당신은 AI 어시스턴트입니다. 지금 시각을 물으면 getCurrentDateTime Tool로 확인해서 한국어로 짧게 답하십시오.
