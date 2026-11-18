---
# 이 파일 하나가 Agent 하나를 정의합니다. Agent의 id는 아래 id 값이고, 파일 이름도 id와 똑같이
# 맞춥니다(다르면 어느 파일이 어느 Agent인지 헷갈립니다).
# 이 Agent에게 시킬 시스템 프롬프트도 바로 이 파일 안에 함께 적습니다. 즉 Agent 하나에 필요한
# 정보(설명, 프롬프트, 쓸 Tool)가 이 파일 하나로 전부 정리되어 있습니다.
#
# ⚠️ 이 파일은 다른 곳에서 이미 쓰이고 있으므로, 아래 값들은 함부로 바꾸면 안 됩니다.
# 이 모듈의 채팅 화면(static/ai/chat/chat.html)이 처음 열릴 때 고를 Agent로 "sample-general-chat"을
# 적어 두었습니다(defaultAgent). 그래서:
# - id 값을 다른 값으로 바꾸면 채팅 화면이 기본 Agent를 찾지 못합니다. 바꿔야 한다면 chat.html의
#   defaultAgent와 화면 안내 표의 Agent 이름도 함께 고칩니다.
# - 역할 문구는 프롬프트에 고정해 두었습니다. 프롬프트가 읽을 수 있는 값은 Agent가 받은 input뿐인데, 이 Agent는
#   input이 글자 하나(사용자가 친 말)라서 채울 필드가 없습니다. 역할을 바꿔 가며 부르고 싶다면 input을 object로
#   선언하고 프롬프트에 ${input.role}을 쓰는 Agent를 씁니다(예: sample-role-reply-agent.md).
# - input/output을 선언하지 않았으므로 둘 다 string입니다(글자를 받아 글자로 답함).
# - tools는 일부러 적지 않았습니다. 채팅에서는 Agent에 적힌 Tool이 언제나 붙고, 화면에서 고른 Tool(ChatRequest의 tools)은
#   거기에 더해집니다. 여기에 "*"를 적으면 등록된 Tool이 매번 모두 붙어 일반 질문에도 토큰과 시간이 늘고, 화면에서 더 고를 것도 남지 않습니다.
#   그래서 이 Agent는 Tool 없이 답하는 것이 기본이고, 필요한 Tool은 화면의 [+ 찾아서 추가]로 그때그때 붙입니다.
#   올린 문서(개발 가이드 등)를 참고하게 하려면 화면에서 knowledgeListDocs / knowledgeSearchDocs / knowledgeGetChunks를 고릅니다.
#   업무용 Agent는 실제로 쓰는 Tool 이름을 적습니다(예: sample-tool-demo-agent.md). 적은 Tool은 채팅에서도 늘 붙습니다.
id: sample-general-chat
version: 1.0.0
description: 일반 대화용 기본 Agent. 채팅 화면(/ai/chat)이 처음에 고른다.
tools:
  allowed:
    - knowledgeCallees
    - knowledgeCallers
    - knowledgeDatasources
    - knowledgeFindMethods
    - knowledgeImpact
    - knowledgeListProjects
    - knowledgeSearch
    - knowledgeTableUsage
---
당신은 친절한 AI 어시스턴트입니다.
답변은 항상 한국어로, 간결하게 합니다.
