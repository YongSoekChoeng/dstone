package net.dstone.boot.ai.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.boot.ai.service.WorkFlowDesignerService;

/**
 * <pre>
 * "Workflow 디자이너" 화면(views/ai/designer/designer.jsp)이 dstone-ai-engine의 정의 API(/api/ai/definitions)를 부를 때 거치는 창구입니다.
 * 화면의 스크립트가 여기로 요청을 보내면 WorkFlowDesignerService가 dstone-ai-engine에 그대로 넘겨줍니다.
 * </pre>
 */
@RestController
@RequestMapping("/ai/designer/*")
public class WorkFlowDesignerController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private WorkFlowDesignerService workFlowDesignerService;

	/**
	 * <pre>
	 * dstone-ai-engine의 정의 API 하나를 부릅니다.
	 * 본문: {method, path, body?}
	 *   - method: GET / POST
	 *   - path: /api/ai/definitions로 시작하는 경로. 예: /api/ai/definitions/workflows/sample-tool-chain-basic
	 *   - body: POST로 보낼 JSON
	 * </pre>
	 */
	@PostMapping("/call.do")
	public ResponseEntity<String> call(@RequestBody Map<String, Object> request) {
		Object method = request.get("method");
		Object path = request.get("path");
		return this.workFlowDesignerService.call(method == null ? null : String.valueOf(method), path == null ? null : String.valueOf(path), request.get("body"));
	}

}
