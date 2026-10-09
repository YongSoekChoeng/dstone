package net.dstone.ai.tools.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * state에 있는 값을 LLM 없이 다루는 Tool입니다. 어느 Workflow에서나 TOOL step으로 부릅니다.
 *
 *   filterList   목록에서 조건에 맞는 항목만 고른다(forEach로 돌 목록을 만들 때)
 *   checkData    값을 서로 맞춰 본다(중복, 빠진 것, 파일이 있는지). Agent가 낸 데이터가 앞뒤가 맞는지 사람에게 보이기 전에 확인할 때
 *
 * 조건과 규칙은 정해진 몇 가지뿐입니다(DataLogic의 설명 참고). 계산식이나 조건문은 쓸 수 없습니다.
 * stepOnly = true라서 LLM에게는 보이지 않고, 결과 길이 상한도 걸리지 않습니다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class DataTool {

	@Autowired
	private FileTool fileTool;

	/**
	 * <pre>
	 * 목록에서 조건에 맞는 항목만 골라 돌려줍니다. 조건을 여러 개 적으면 모두 맞아야 합니다.
	 *
	 * YAML 예: 복사에 성공했고(removes나 adds가 있는) 항목의 item만 고른다
	 *   tool: filterList
	 *   input:
	 *     list: "${state.copied}"
	 *     where: { success: true }
	 *     anyNotEmpty: [ item.removes, item.adds ]
	 *     pick: item
	 * </pre>
	 */
	@Tool(description = "목록에서 조건에 맞는 항목만 골라 돌려준다. where(필드: 값 - 같아야 함), notEmpty(모두 비어 있지 않아야 하는 필드들), empty(모두 비어 있어야 하는 필드들), "
		+ "anyNotEmpty(하나라도 비어 있지 않아야 하는 필드들)를 함께 적으면 모두 맞아야 한다. pick을 적으면 항목의 그 필드 값만 돌려준다. 필드는 a.b처럼 안쪽을 가리킬 수 있다.")
	public List<Object> filterList(
			@ToolParam(description = "거를 목록") List<Object> list,
			@ToolParam(required = false, description = "필드 → 값. 그 필드의 값이 적은 값과 같은 항목만") Map<String, Object> where,
			@ToolParam(required = false, description = "이 필드들이 모두 비어 있지 않은 항목만") List<String> notEmpty,
			@ToolParam(required = false, description = "이 필드들이 모두 비어 있는 항목만") List<String> empty,
			@ToolParam(required = false, description = "이 필드들 가운데 하나라도 비어 있지 않은 항목만") List<String> anyNotEmpty,
			@ToolParam(required = false, description = "고른 항목에서 이 필드의 값만 돌려준다") String pick) {
		return DataLogic.filter(list, where, notEmpty, empty, anyNotEmpty, pick);
	}

	/**
	 * <pre>
	 * 규칙들을 확인하고 {ok, problems, text, feedback}을 돌려줍니다(모양은 DataLogic.checkResult 참고).
	 * 걸린 것이 있어도 Tool은 성공입니다(걸린 내용을 문서에 넣거나 Agent에게 되돌려 주는 것은 Workflow가 정합니다).
	 * 규칙을 잘못 적었으면(모르는 rule) 실패입니다({success: false, message}).
	 *
	 * YAML 예:
	 *   tool: checkData
	 *   input:
	 *     rules:
	 *       - rule: unique
	 *         list: "${state.analysis.targets}"
	 *         field: path
	 *         message: 같은 파일이 변경 대상에 두 번 들어 있습니다
	 *       - rule: filesExist
	 *         list: "${state.analysis.targets}"
	 *         where: { changeType: 수정 }
	 *         field: path
	 *         message: 수정이라고 했는데 없는 파일입니다
	 * </pre>
	 */
	@Tool(description = "규칙들로 값을 맞춰 보고 {ok, problems, text, feedback}을 돌려준다. 규칙마다 rule(unique: 값이 서로 달라야 함, allIn: 값이 모두 inList 안에 있어야 함, "
		+ "filesExist: 그 경로에 파일이 있어야 함, filesAbsent: 없어야 함), list(볼 목록), where(볼 항목의 조건), field(값으로 볼 필드), inList/inField(allIn에서 견줄 쪽), message(걸렸을 때 보여 줄 말)를 적는다.")
	public Map<String, Object> checkData(@ToolParam(description = "규칙 목록") List<Map<String, Object>> rules) {
		try {
			return DataLogic.checkResult(DataLogic.check(rules, new DataLogic.FileCheck() {
				@Override
				public boolean exists(String path) {
					return DataTool.this.fileTool.isFileExist(path);
				}
			}));
		} catch (IllegalArgumentException e) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", false);
			result.put("message", e.getMessage());
			return result;
		}
	}

}
