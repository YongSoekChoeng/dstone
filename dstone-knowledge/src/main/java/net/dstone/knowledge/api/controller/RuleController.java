package net.dstone.knowledge.api.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.RuleService;

/**
 * <pre>
 * 의미 분석 규칙(conf/rules/*.yml) API입니다.
 *
 *   GET  /api/rules                                   폴더에 있는 규칙 묶음
 *   GET  /api/revisions/{revisionId}/rules            그 리비전에 쓴 규칙과 지금 규칙의 비교
 *   POST /api/revisions/{revisionId}/rules/trial      규칙 시험 적용(전후 차이만 알려 주고 저장하지 않음)
 *        본문: {"ruleSet": "규칙 묶음 YAML 글"}  (없으면 지금 파일 그대로 돌려 본다)
 *   GET  /api/revisions/{revisionId}/rules/candidates 규칙이 아직 모르는 관례의 후보
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class RuleController {

	@Autowired
	private RuleService ruleService;

	@GetMapping("/rules")
	public Map<String, Object> getRuleSets() {
		return ruleService.getRuleSets();
	}

	@GetMapping("/revisions/{revisionId}/rules")
	public Map<String, Object> getRevisionRules(@PathVariable("revisionId") long revisionId) {
		return ruleService.getRevisionRules(revisionId);
	}

	@PostMapping("/revisions/{revisionId}/rules/trial")
	public Map<String, Object> trial(@PathVariable("revisionId") long revisionId
			, @RequestBody(required = false) Map<String, Object> body
			, @RequestParam(name = "samples", defaultValue = "30") int samples) {
		Object text = body == null ? null : body.get("ruleSet");
		return ruleService.trial(revisionId, text == null ? null : String.valueOf(text), samples);
	}

	@GetMapping("/revisions/{revisionId}/rules/candidates")
	public Map<String, Object> getCandidates(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "limit", defaultValue = "20") int limit) {
		return ruleService.getCandidates(revisionId, limit);
	}

}
