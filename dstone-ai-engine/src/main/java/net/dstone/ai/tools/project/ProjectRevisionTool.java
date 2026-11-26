package net.dstone.ai.tools.project;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.project.ProjectBuildService;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * Workflow가 시작할 때 프로젝트의 현재 리비전(프로젝트 관리의 [Build] 결과)을 정하는 Tool입니다(TOOL step 전용).
 * 실행은 여기서 받은 리비전을 state에 두고 끝까지 그것만 씁니다. 그 사이에 [Build]로 새 리비전이 생겨도 영향이 없습니다.
 *
 * 결과: {success, route: ok | none, projectId, revision, sourceRoot, revisionDir, kind, webRoot, classesDir, libDir, mainClass, message}
 *   route=none  빌드한 리비전이 없다. Workflow는 멈추지 않고, 서버 기동 테스트만 하지 못한다.
 *   success=false  프로젝트ID가 없거나 등록되지 않았다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class ProjectRevisionTool extends BaseObject {

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private ProjectBuildService projectBuildService;

	@Tool(description = "프로젝트 관리에 등록된 프로젝트의 현재 리비전(빌드된 소스와 산출물 위치)을 돌려준다. "
		+ "{success, route: ok | none, projectId, revision, sourceRoot, kind, webRoot, classesDir, libDir, message}. 빌드한 리비전이 없으면 route=none.")
	public Map<String, Object> resolveProjectRevision(@ToolParam(description = "프로젝트ID(요청서의 프로젝트ID)") String projectId) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		String id = projectId == null ? "" : projectId.trim();
		if (id.isEmpty()) {
			result.put("success", Boolean.FALSE);
			result.put("message", "요청서에 프로젝트ID가 없습니다. '## 프로젝트ID' 절에 프로젝트 관리에 등록한 ID를 적으십시오.");
			return result;
		}
		if (this.projectStore.settings(id) == null) {
			result.put("success", Boolean.FALSE);
			result.put("message", "프로젝트 관리에 없는 프로젝트ID입니다: " + id);
			return result;
		}
		Map<String, Object> resolved = this.projectBuildService.resolve(id, null);
		result.put("success", Boolean.TRUE);
		if (resolved == null) {
			result.put("route", "none");
			result.put("projectId", id);
			// 분석·컴파일 확인은 소스 디렉토리로 계속한다(그 폴더의 클래스가 최신이어야 컴파일 확인이 맞다)
			Object sourceDir = this.projectStore.settings(id).get("sourceDir");
			result.put("sourceRoot", sourceDir == null ? "" : String.valueOf(sourceDir));
			result.put("message", "빌드된 리비전이 없습니다. 프로젝트 관리에서 [Build]를 누르면 서버 기동 테스트를 할 수 있습니다. 이번 실행은 서버 기동 테스트 없이 진행합니다.");
			return result;
		}
		result.put("route", "ok");
		result.putAll(resolved);
		result.put("message", "리비전 " + resolved.get("revision") + "(" + resolved.get("kind") + ")를 씁니다. 소스: " + resolved.get("sourceRoot"));
		return result;
	}

}
