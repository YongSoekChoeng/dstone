package net.dstone.ai.common.definition.workflow;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.definition.SchemaDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;

/**
 * <pre>
 * Workflow 하나 전체를 표현하는 클래스입니다. resources/definitions/workflows/**.yml 파일 하나하나가 이 클래스의
 * 값으로 채워지며, 이 작업은 net.dstone.ai.common.loader.YamlDefinitionLoader 가 담당합니다.
 * 사용자가 API로 어떤 Workflow를 실행해 달라고 요청하면, api.controller.WorkFlowController가 그
 * Workflow의 id로 이 정의를 찾아서 실제로 실행을 담당하는 runtime.workflow.WorkFlowExecutor에게 넘겨줍니다.
 *
 * ## 파일 모양
 *   id: pilot-modernization
 *   version: 1.0.0
 *   description: 요구사항 분석부터 구현까지의 AI 개발 Workflow
 *   input: string, 작업 폴더 경로                  # 실행 요청의 input 모양(비워두면 글자 하나)
 *   settings:
 *     checkpoint: true
 *     maxIterations: 20
 *     onError: STOP
 *   state:                                       # step이 저장하는 이름 전부(이름: 설명)
 *     request: 요청서 내용
 *     analysis: 분석 결과
 *   return: ${state.analysis}                    # (옵셔널) 최종 결과. 비워두면 state 전체
 *   steps:
 *     - id: step01
 *       ...
 *
 * ## 입력(input)
 * 실행 요청의 input에 담아야 하는 값의 모양입니다(common.definition.SchemaDefinition, 짧은 타입 표기 Picoschema).
 * 요청이 이 모양이 아니면 실행하기 전에 바로 거절합니다(HTTP 400). 비워두면 string입니다. step은 ${input}(또는 object면
 * ${input.필드})으로 이 값을 꺼내 씁니다.
 *
 * ## 상태(state)
 * step들이 save로 저장한 값이 모이는 곳입니다. 여기에 이 Workflow가 쓰는 이름을 전부 적습니다(이름: 설명).
 * 값의 모양은 적지 않습니다. 그 이름에 저장하는 step이 부르는 Agent의 output(또는 엔진이 정한 결과 모양)이 곧 그 모양이라,
 * 엔진이 거기서 알아냅니다. 선언하지 않은 이름에 저장하거나, 선언했는데 아무 step도 저장하지 않으면 엔진이 켜지지 않습니다.
 *
 * ## 설정(settings)
 * - maxIterations: 이 Workflow가 전체적으로 몇 번까지 step을 실행할 수 있는가를 정하는 상한선입니다.
 *   onFailure나 routes로 앞의 step으로 되돌아가는 루프가 끝없이 반복되지 않도록 막아줍니다.
 *   비워두면 Constants.WorkFlow.DEFAULT_MAX_ITERATIONS가 쓰입니다.
 * - checkpoint: step 하나가 끝날 때마다 실행 상태를 DB에 저장할지입니다. 엔진은 항상 저장하므로 true만 적을 수 있습니다.
 * - onError: step 실행기가 예외(외부 연결 실패 같은 시스템 오류)를 던졌을 때의 동작입니다. STOP(그 자리에서 실패로 끝냄)만 적을 수 있습니다.
 *
 * ## 최종 결과(return)
 * Workflow가 성공으로 끝났을 때 돌려줄 값입니다. step의 with와 같은 표현식이고, 맵/리스트로 여러 값을 모아도 됩니다.
 * 값 전체가 표현식 하나면 읽어 온 값을 타입 그대로(객체, 리스트 등) 돌려줍니다. 비워두면 state 전체를 돌려줍니다.
 *
 * allowedCallers를 비워두면 누구나 이 Workflow를 실행할 수 있습니다. 값을 채워두면 그 목록에 있는
 * caller(ApiKeyAuthFilter가 요청에서 알아낸 호출 주체)만 실행할 수 있습니다.
 * </pre>
 *
 * @param id             (필수)Workflow를 가리키는 이름입니다.
 * @param version        (필수)이 Workflow 정의의 버전입니다(예: 1.0.0). 실행 기록에 함께 남습니다.
 * @param description    (옵셔널)Workflow가 무엇을 하는지 사람이 읽기 위한 설명입니다(예: 화면의 안내 문구로 쓰입니다).
 * @param allowedCallers (옵셔널)Workflow를 실행할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다.
 * @param input          (옵셔널)실행 요청의 input 모양입니다. 비워두면 string입니다.
 * @param settings       (옵셔널)실행 설정입니다.
 * @param state          (필수)step이 저장하는 state 이름 → 설명입니다(저장하는 step이 없는 Workflow면 비워둡니다).
 * @param returnValue    (옵셔널)Workflow가 성공했을 때 돌려줄 최종 결과입니다. YAML 키는 return입니다. 비워두면 state 전체입니다.
 * @param steps          (필수)Workflow가 실행할 step들의 목록입니다. 각 항목은 type 값에 따라
 *                       common.definition.workflow.step 패키지의 record 하나로 읽힙니다.
 */
public record WorkFlowDefinition(
	String id
	, String version
	, String description
	, List<String> allowedCallers
	, SchemaDefinition input
	, Settings settings
	, Map<String, String> state
	, @JsonProperty("return") Object returnValue
	, List<StepDefinition> steps
	) {

	/**
	 * workflow.settings 입니다.
	 *
	 * @param checkpoint    step마다 실행 상태를 저장할지입니다. 엔진은 항상 저장하므로 true만 적을 수 있습니다.
	 * @param maxIterations Workflow가 전체적으로 실행할 수 있는 step의 최대 횟수입니다.
	 * @param onError       시스템 오류가 났을 때의 동작입니다. STOP만 적을 수 있습니다.
	 */
	public record Settings(Boolean checkpoint, Integer maxIterations, String onError) {
	}

	/**
	 * 실행 요청의 input 모양(JSON Schema)입니다. input을 비워뒀으면 {type: string}입니다.
	 */
	public Map<String, Object> inputSchema() {
		return (this.input == null ? SchemaDefinition.string() : this.input).schema();
	}

	/**
	 * state에 선언한 이름 → 설명입니다. 비워뒀으면 빈 맵입니다.
	 */
	public Map<String, String> stateNames() {
		return this.state == null ? Map.of() : this.state;
	}

	/**
	 * Workflow가 전체적으로 실행할 수 있는 step의 최대 횟수입니다. 적지 않았으면 null입니다.
	 */
	public Integer maxIterations() {
		return this.settings == null ? null : this.settings.maxIterations();
	}

}
