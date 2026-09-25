package net.dstone.ai.runtime.step;

import java.util.Map;

/**
 * <pre>
 * StepRunner 하나를 실행할 때 넘겨주는 입력값입니다. 
 * step의 input 템플릿은 runtime.workflow.WorkFlowExecutor가 미리 채워서 넘겨주므로, StepRunner는 템플릿을 전혀 몰라도 됩니다.
 *
 * step 종류에 따라 쓰는 필드가 다릅니다.
 * - AGENT / SUPERVISOR / ROUTER / APPROVAL: text를 씁니다(LLM에게 보낼 사용자 메시지, 또는 그대로 넘길 텍스트).
 * - TOOL: arguments를 씁니다(JSON으로 바꿔서 Tool에게 넘길 인자).
 *
 * @param text          채워진 입력 텍스트입니다. TOOL step에서는 null입니다.
 * @param arguments     채워진 Tool 인자입니다. TOOL step이 아니면 null입니다.
 * @param workflowInputs Workflow를 실행할 때 넘긴 값(컨텍스트의 inputs)입니다. Agent의 system prompt 안의 {변수명}을 채우는 데 씁니다.
 * </pre>
 */
public record StepInput(String text, Map<String, Object> arguments, Map<String, Object> workflowInputs) {
}
