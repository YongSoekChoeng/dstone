package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/submit.do 요청 바디. dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit을
 * 그대로 테스트해보기 위한 화면이라, workflowId까지 화면에서 고른다(다른 AI 화면들처럼 workflowId가
 * 코드에 고정돼 있지 않다 - GET /ai/workflow/list.do로 받아온 목록 중 하나를 선택).
 *
 * @param workflowId 호출할 Workflow의 id(예: sample-agent-basic-echo)
 * @param input      Workflow에 넘길 값. 그 Workflow의 input 모양(글자 또는 JSON 객체)이어야 한다 - 화면이
 *                   /ai/workflow/list.do로 받은 input 스키마를 보고 글자로 보낼지 JSON으로 읽어 보낼지 정한다.
 * @param sessionId  대화 세션 ID(비워두면 dstone-ai-engine이 새로 발급한다)
 */
public record WorkFlowTestRequest(String workflowId, Object input, String sessionId) {
}
