---
# 이 파일 하나가 Agent 하나를 정의합니다. Agent의 id는 아래 id 값이고, 파일 이름도 id와 똑같이
# 맞춥니다(다르면 어느 파일이 어느 Agent인지 헷갈립니다).
# 이 Agent에게 시킬 시스템 프롬프트도 바로 이 파일 안에 함께 적습니다. 즉 Agent 하나에 필요한
# 정보(설명, 프롬프트, 쓸 Tool)가 이 파일 하나로 전부 정리되어 있습니다.
#
# ⚠️ 이 파일은 다른 곳에서 이미 쓰이고 있으므로, 아래 값들은 함부로 바꾸면 안 됩니다.
# dstone-boot의 채팅 화면(ai/service/ChatService.java)이 "sample-general-chat"이라는 Agent id를 코드
# 안에 그대로 박아두고 호출하고 있습니다. 그래서:
# - id 값을 "sample-general-chat"이 아닌 다른 값으로 바꾸면, 채팅 화면이 이 Agent를
#   찾지 못해 동작하지 않게 됩니다.
# - 역할 문구는 프롬프트에 고정해 두었습니다. 프롬프트가 읽을 수 있는 값은 Agent가 받은 input뿐인데, 이 Agent는
#   input이 글자 하나(사용자가 친 말)라서 채울 필드가 없습니다. 역할을 바꿔 가며 부르고 싶다면 input을 object로
#   선언하고 프롬프트에 ${input.role}을 쓰는 Agent를 씁니다(예: sample-role-reply-agent.md).
# - input/output을 선언하지 않았으므로 둘 다 string입니다(글자를 받아 글자로 답함).
# - tools.allowed: ["*"]도 그대로 둡니다. "*"는 등록된 Tool을 전부 쓸 수 있다는 뜻으로,
#   무엇이든 물어볼 수 있는 범용 채팅 Agent라서 이렇게 열어 두었습니다(caller 화이트리스트는 그대로 적용됩니다).
#   채팅 화면의 Tool 선택(ChatRequest의 tools)은 이 목록 가운데 "이번 요청에 쓸 것"만 고릅니다.
#   하나도 고르지 않으면 Tool 없이 답합니다. 여기 tools를 지우면 화면에 고를 Tool이 나오지 않습니다.
#   올린 문서(개발 가이드 등)를 참고하게 하려면 화면에서 knowledgeListDocs / knowledgeSearchDocs / knowledgeGetChunks를 고릅니다.
#   업무용 Agent는 "*" 대신 실제로 쓰는 Tool 이름만 적으십시오(예: sample-tool-demo-agent.md).
id: sample-general-chat
version: 1.0.0
description: 일반 대화용 기본 Agent. dstone-boot 채팅 화면이 사용한다.
tools:
  allowed: ["*"]
---
당신은 친절한 AI 어시스턴트입니다.
답변은 항상 한국어로, 간결하게 합니다.
