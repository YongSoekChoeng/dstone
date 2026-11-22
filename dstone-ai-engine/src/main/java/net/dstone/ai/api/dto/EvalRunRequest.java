package net.dstone.ai.api.dto;

import java.util.Map;

/**
 * <pre>
 * 평가 시작 요청(POST /api/ai/eval/runs)의 본문입니다.
 * </pre>
 *
 * @param caseId 평가 사례 id(conf/eval의 *.eval.yml)
 * @param params 사례 params에서 바꿀 값. 비우면 사례의 기본값
 * @param model  이번에만 쓸 모델 이름. 비우면 Agent·provider 기본 모델
 * @param repeat 몇 번 돌릴지(1~5). 비우면 1
 */
public record EvalRunRequest(String caseId, Map<String, String> params, String model, Integer repeat) {
}
