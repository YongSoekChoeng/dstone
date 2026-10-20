package net.dstone.ai.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.ai.api.dto.DefinitionCatalog;
import net.dstone.ai.api.dto.DefinitionChangeRequest;
import net.dstone.ai.api.dto.DefinitionChangeResult;
import net.dstone.ai.api.dto.DefinitionSource;
import net.dstone.ai.api.service.DefinitionService;
import net.dstone.common.biz.BaseController;

/**
 * <pre>
 * 정의 파일(Workflow, Agent)을 보고, 검사하고, 저장하는 API입니다. dstone-boot의 "Workflow 디자이너" 화면이 씁니다.
 *
 *   GET  /api/ai/definitions                    등록된 Workflow/Agent/Tool 목록(디자이너의 선택 목록)
 *   GET  /api/ai/definitions/{kind}/{id}        파일 내용 그대로(+ Workflow는 노드 위치)
 *   POST /api/ai/definitions/{kind}/check       저장하지 않고 검사만
 *   POST /api/ai/definitions/{kind}/save        검사 → 통과하면 파일 쓰기 → 바로 등록
 *   POST /api/ai/definitions/{kind}/delete      파일 지우기 → 바로 등록
 *   POST /api/ai/definitions/reload             정의 폴더를 다시 읽기(손으로 고친 파일 반영)
 *   kind = workflows | agents
 *
 * 검사에 걸리면 HTTP 200에 ok: false와 이유를 돌려줍니다. 이때 파일과 등록된 정의는 바뀌지 않습니다.
 * 경로가 틀렸으면 400, 저장이 꺼져 있으면(dstone.ai.definition.edit-enabled) 403입니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/ai/definitions")
public class DefinitionController extends BaseController {

	@Autowired
	private DefinitionService definitionService;

	/** 등록된 Workflow, Agent, Tool 목록과 저장할 수 있는지를 돌려줍니다. */
	@GetMapping
	public DefinitionCatalog catalog() {
		return this.definitionService.catalog();
	}

	/**
	 * 정의 파일 하나의 내용을 글자 그대로 돌려줍니다. 없으면 404입니다.
	 *
	 * @param kind workflows 또는 agents
	 * @param id   정의 id
	 */
	@GetMapping("/{kind}/{id}")
	public ResponseEntity<Object> source(@PathVariable("kind") String kind, @PathVariable("id") String id) {
		try {
			DefinitionSource source = this.definitionService.source(kind, id);
			if (source == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(DefinitionChangeResult.fail("등록되지 않은 " + kind + " id입니다: " + id));
			}
			return ResponseEntity.ok(source);
		} catch (DefinitionService.RejectedException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 고친 내용을 저장하지 않고 검사만 합니다.
	 *
	 * @param kind    workflows 또는 agents
	 * @param request 경로, 원래 경로, 내용
	 */
	@PostMapping("/{kind}/check")
	public ResponseEntity<Object> check(@PathVariable("kind") String kind, @RequestBody DefinitionChangeRequest request) {
		try {
			return ResponseEntity.ok(this.definitionService.check(kind, request));
		} catch (DefinitionService.RejectedException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 고친 내용을 검사하고, 통과하면 저장하고 바로 등록합니다.
	 *
	 * @param kind    workflows 또는 agents
	 * @param request 경로, 원래 경로, 내용, 노드 위치
	 */
	@PostMapping("/{kind}/save")
	public ResponseEntity<Object> save(@PathVariable("kind") String kind, @RequestBody DefinitionChangeRequest request) {
		try {
			return ResponseEntity.ok(this.definitionService.save(kind, request));
		} catch (DefinitionService.RejectedException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 정의 파일 하나를 지우고 다시 읽습니다.
	 *
	 * @param kind    workflows 또는 agents
	 * @param request 지울 파일 경로(path)
	 */
	@PostMapping("/{kind}/delete")
	public ResponseEntity<Object> delete(@PathVariable("kind") String kind, @RequestBody DefinitionChangeRequest request) {
		try {
			return ResponseEntity.ok(this.definitionService.delete(kind, request));
		} catch (DefinitionService.RejectedException e) {
			return this.rejected(e);
		}
	}

	/** 정의 폴더의 파일을 다시 읽습니다. 틀린 파일이 있으면 ok: false이고 등록된 정의는 그대로입니다. */
	@PostMapping("/reload")
	public DefinitionChangeResult reload() {
		return this.definitionService.reload();
	}

	/** 거절한 요청을 {ok: false, message} 모양으로 돌려줍니다(저장 꺼짐 403, 그 밖에 400). */
	private ResponseEntity<Object> rejected(DefinitionService.RejectedException e) {
		return ResponseEntity.status(e.isForbidden() ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST).body(DefinitionChangeResult.fail(e.getMessage()));
	}

}
