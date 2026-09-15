package net.dstone.boot.ai.vo;

import java.util.Map;

/**
 * /ai/workflow/submit.do 요청 바디. dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit을
 * 그대로 테스트해보기 위한 화면이라, workflowId까지 화면에서 직접 입력받는다(다른 AI 화면들처럼 workflowId가
 * 코드에 고정돼 있지 않다).
 *
 * @param workflowId 호출할 Workflow의 id(예: oracle-to-postgresql)
 * @param message Workflow 첫 step의 입력 메시지
 * @param sessionId 대화 세션 ID(비워두면 dstone-ai-engine이 새로 발급한다)
 * @param variables 각 step의 프롬프트/Tool 입력 템플릿을 채우는 값
 */
public record WorkflowTestRequest(String workflowId, String message, String sessionId, Map<String, Object> variables) {
}
