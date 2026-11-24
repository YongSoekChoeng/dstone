package net.dstone.ai.api.dto;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 실행 기록으로 평가 사례를 만들 때(미리 채점, 저장) 화면이 보내는 본문입니다.
 * POST /api/ai/eval/drafts/preview, POST /api/ai/eval/drafts/save
 * </pre>
 *
 * @param executionId 실행 id
 * @param snapshot    Agent 사례면 step 스냅숏 이름(예: step30-1). Workflow 사례면 비웁니다
 * @param id          사례 id(영문 소문자·숫자·하이픈)
 * @param projectId   사례를 둘 프로젝트(conf/eval/{projectId}/)
 * @param description 무엇을 재는 사례인지
 * @param note        사례 파일 머리 주석에 남길 "그때 무슨 일이 있었나"(선택)
 * @param workKey     작업 폴더로 쓸 입력 이름(Agent 사례). 비우면 작업 폴더 없이 만듭니다
 * @param approvals   Workflow 사례의 승인 step → 고를 결정(비우면 실행 기록의 결정대로)
 * @param checks      채점 기준 [{name, kind, value}]
 */
public record EvalCaseDraftRequest(String executionId, String snapshot, String id, String projectId, String description, String note,
	String workKey, Map<String, String> approvals, List<Map<String, Object>> checks) {
}
