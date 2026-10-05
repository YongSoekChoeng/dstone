package net.dstone.knowledge.api.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.ResourceService;

/**
 * <pre>
 * Java 밖의 자원 API입니다: SQL statement, 테이블, 설정 값, 그 밖의 항목.
 * </pre>
 */
@RestController
@RequestMapping("/api/revisions/{revisionId}")
public class ResourceController {

	@Autowired
	private ResourceService resourceService;

	/** 테이블 목록. name은 이름에 포함 조건입니다. */
	@GetMapping("/tables")
	public Map<String, Object> getTableList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "name", required = false) String name
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return resourceService.getTableList(revisionId, name, page, size);
	}

	/** 테이블 하나를 건드리는 SQL과 그 SQL을 실행하는 메소드 */
	@GetMapping("/tables/{table}")
	public Map<String, Object> getTableUsage(@PathVariable("revisionId") long revisionId, @PathVariable("table") String table) {
		return resourceService.getTableUsage(revisionId, table);
	}

	/** SQL statement 목록. namespace와 id는 포함 조건입니다. */
	@GetMapping("/mappers")
	public Map<String, Object> getMapperList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "namespace", required = false) String namespace
			, @RequestParam(name = "id", required = false) String statementId
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return resourceService.getMapperList(revisionId, namespace, statementId, page, size);
	}

	/** 설정 값 목록. key는 포함 조건입니다. 비밀번호처럼 보이는 값은 ****로 나옵니다. */
	@GetMapping("/configs")
	public Map<String, Object> getConfigList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "key", required = false) String key
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return resourceService.getConfigList(revisionId, key, page, size);
	}

	/** 그 밖의 항목. type은 일치 조건(SPRING_BEAN / DEPENDENCY / SERVLET_MAPPING ...), name은 포함 조건입니다. */
	@GetMapping("/resources")
	public Map<String, Object> getResourceList(@PathVariable("revisionId") long revisionId
			, @RequestParam(name = "type", required = false) String type
			, @RequestParam(name = "name", required = false) String name
			, @RequestParam(name = "page", defaultValue = "1") int page
			, @RequestParam(name = "size", defaultValue = "50") int size) {
		return resourceService.getResourceList(revisionId, type, name, page, size);
	}

}
