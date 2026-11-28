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
 *   containsValue 목록에 그 값인 항목이 있으면 true, 없으면 false(routes로 길을 가를 때)
 *   countUp      되풀이 횟수를 센다. route가 한도 안이면 within, 다 쓰면 over(자동으로 고쳐 다시 시험하는 고리를 멈출 때)
 *   mergeByKey   지난 목록에 이번 목록을 key로 겹쳐 쌓는다(이번에 다시 보지 않은 항목은 지난 것이 남는다)
 *   checkData    값을 서로 맞춰 본다(중복, 빠진 것, 파일이 있는지). Agent가 낸 데이터가 앞뒤가 맞는지 사람에게 보이기 전에 확인할 때
 *
 * "없음", "한도를 다 씀"은 정상 결과라서 실패로 돌려주지 않습니다. 실패로 나누면 실행 이력에 실패가 남아 진짜 오류와 섞입니다.
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
	 *   with:
	 *     list: ${state.copied}
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
			@ToolParam(required = false, description = "고른 항목에서 이 필드의 값만 돌려준다") String pick,
			@ToolParam(required = false, description = "필드 → 값. 그 필드의 값이 적은 값과 같은 항목은 뺀다") Map<String, Object> whereNot) {
		return DataLogic.filter(list, where, notEmpty, empty, anyNotEmpty, pick, whereNot);
	}

	/**
	 * <pre>
	 * 값(목록이면 그 항목들, 하나면 그 값)에 expected와 같은 것이 있는지 봅니다. 있으면 true, 없으면 false입니다.
	 * 없는 것도 정상 결과라서 실패로 돌려주지 않습니다. TOOL step은 이 값을 routes로 받아 갈 곳을 나눕니다.
	 *
	 * YAML 예: 시나리오 가운데 FAIL이 하나라도 있으면 고치러 간다
	 *   tool: containsValue
	 *   with: { value: "${state.verification.scenarios}", field: result, expected: FAIL }
	 *   routes: { "true": 고치는step, "false": 사람에게묻는step }
	 * 예전에는 없으면 실패로 돌려주고 onFailure로 나눴습니다. 그러면 "FAIL 없음"처럼 좋은 결과도 실행 이력에 실패로 남았습니다(2026-11-24).
	 * </pre>
	 */
	@Tool(description = "value(목록이면 항목마다, 하나면 그 값)에 expected와 같은 것이 있으면 true, 없으면 false를 돌려준다. field를 적으면 항목의 그 필드를 본다.")
	public boolean containsValue(
			@ToolParam(required = false, description = "볼 값이나 목록") Object value,
			@ToolParam(description = "찾을 값") String expected,
			@ToolParam(required = false, description = "항목에서 볼 필드(a.b처럼 안쪽도 됨)") String field) {
		return DataLogic.containsValue(value, expected, field);
	}

	/**
	 * <pre>
	 * 값을 글자로 그대로 돌려줍니다. TOOL step이 그 글자를 routes의 이름으로 받아 갈 곳을 나눕니다.
	 * 여러 값으로 갈리는 분기를 containsValue step 여러 개 대신 step 하나로 적으려는 것입니다.
	 * 값이 비었으면 empty를 돌려줍니다(empty도 비었으면 빈 글자). routes에 없는 값이면 step이 실패해 onFailure로 갑니다.
	 *
	 * YAML 예: 설계 승인에서 고른 검증 수준으로 갈 곳을 나눈다
	 *   tool: routeValue
	 *   with: { value: "${state.designApproval.decision:}" }
	 *   routes: { "승인(개발진행, 서버 검증)": step41, "승인(개발진행, 기본 검증)": step19 }
	 *   onFailure: step41
	 * </pre>
	 */
	@Tool(description = "value를 글자로 그대로 돌려준다(비었으면 empty). TOOL step이 그 값을 routes 이름으로 받아 갈 곳을 나눈다.")
	public String routeValue(
			@ToolParam(required = false, description = "갈 곳을 고를 값") Object value,
			@ToolParam(required = false, description = "값이 비었을 때 돌려줄 글자") String empty) {
		String text = value == null ? "" : String.valueOf(value).trim();
		if (text.isEmpty()) {
			return empty == null ? "" : empty;
		}
		return text;
	}

	/**
	 * <pre>
	 * 되풀이 횟수를 하나 올립니다. 결과({route, count, scope, message})를 save해 두었다가 다음에 previous로 넘깁니다.
	 * route는 한도 안이면 within, max를 다 썼으면 over입니다. 다 쓴 것도 정상 결과라서 실패로 돌려주지 않습니다.
	 * TOOL step은 route 값으로 갈 곳을 나눕니다(routes: { within: 한번더step, over: 멈추는step }).
	 * scope가 지난번과 다르면 처음부터 셉니다. 예를 들어 scope에 사람의 결정 기록을 주면, 사람이 새로 시킬 때마다 자동 재시도 횟수가 새로 생깁니다.
	 * </pre>
	 */
	@Tool(description = "횟수를 하나 올려 {route, count, scope, message}로 돌려준다. route는 max 안이면 within, 다 썼으면 over다. previous에 지난 결과를 넘기고, scope가 달라지면 처음부터 센다.")
	public Map<String, Object> countUp(
			@ToolParam(required = false, description = "지난번 결과(save해 둔 값). 처음이면 비움") Object previous,
			@ToolParam(description = "허용하는 최대 횟수") Integer max,
			@ToolParam(required = false, description = "이 값이 바뀌면 처음부터 센다") String scope) {
		return DataLogic.countUp(previous, max, scope);
	}

	/**
	 * <pre>
	 * 지난 목록에 이번 목록을 key 필드 값으로 겹쳐 돌려줍니다(규칙은 DataLogic.mergeByKey 참고).
	 * Agent가 이번에 확인한 것만 답해도 지난 결과가 사라지지 않게, 저장해 둔 목록에 쌓을 때 씁니다.
	 * 아무것도 넘기지 않으면 빈 목록이라, 쌓아 둔 것을 비울 때도 씁니다.
	 *
	 * YAML 예: 이번 검증 결과를 지난 결과에 쌓는다
	 *   tool: mergeByKey
	 *   with: { previous: "${state.allResults:}", current: "${state.round.items}", key: id, mark: carried }
	 *   save: allResults
	 * </pre>
	 */
	@Tool(description = "previous 목록에 current 목록을 key 필드 값으로 겹쳐 돌려준다. 같은 key는 current 항목으로 바뀌고, current에 없는 항목은 남고, 새 key는 뒤에 붙는다. "
		+ "mark를 적으면 항목마다 이번에 없어서 남긴 것인지 true/false로 적는다. 아무것도 넘기지 않으면 빈 목록이다.")
	public List<Object> mergeByKey(
			@ToolParam(required = false, description = "지난 목록. 처음이면 비움") Object previous,
			@ToolParam(required = false, description = "이번 목록") List<Object> current,
			@ToolParam(description = "같은 항목인지 가리는 필드(a.b처럼 안쪽도 됨)") String key,
			@ToolParam(required = false, description = "지난 것을 남겼는지 적을 필드 이름") String mark) {
		return DataLogic.mergeByKey(previous, current, key, mark);
	}

	/**
	 * <pre>
	 * 규칙들을 확인하고 {ok, problems, text, feedback}을 돌려줍니다(모양은 DataLogic.checkResult 참고).
	 * 걸린 것이 있어도 Tool은 성공입니다(걸린 내용을 문서에 넣거나 Agent에게 되돌려 주는 것은 Workflow가 정합니다).
	 * 규칙을 잘못 적었으면(모르는 rule) 실패입니다({success: false, message}).
	 *
	 * YAML 예:
	 *   tool: checkData
	 *   with:
	 *     rules:
	 *       - rule: unique
	 *         list: ${state.analysis.targets}
	 *         field: path
	 *         message: 같은 파일이 변경 대상에 두 번 들어 있습니다
	 *       - rule: filesExist
	 *         list: ${state.analysis.targets}
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
