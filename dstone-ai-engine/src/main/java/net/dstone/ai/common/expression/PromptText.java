package net.dstone.ai.common.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dstone.ai.common.exception.ExpressionException;
import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * Agent 프롬프트(Agent 파일의 --- 아래 본문) 안의 "${ ... }" 자리를 다룹니다.
 * 문법은 Workflow YAML의 표현식과 같습니다(ContextResolver). 프롬프트에서 읽을 수 있는 곳은 둘뿐입니다.
 *
 *   ${input}                  Agent가 받은 값 전체(글자 하나짜리 input이면 그 글자, 객체면 JSON 글자)
 *   ${input.inputStr}         받은 값의 필드
 *   ${input.note:없음}        값이 없을 때 쓸 기본값(없어도 되는 필드는 기본값이 없으면 빈 글자)
 *   ${output.outArr}          돌려줄 필드의 "이름"(outArr). 답은 아직 없으니 어디에 담을지 가리킬 때 씁니다
 *
 * 환경 변수 ${APP_HOME}(대문자)는 파일을 읽을 때 이미 바뀌어 있습니다.
 * ${ 를 글자 그대로 쓰려면(JSP EL 예시 등) 앞에 \ 를 붙여 \${ 로 적습니다.
 * ${ 가 없는 중괄호 { } 는 그냥 글자입니다. JSON 예시를 그대로 적어도 됩니다.
 *
 * 검사(check)와 채우기(render)가 같은 규칙을 쓰도록 둘 다 여기 둡니다.
 * </pre>
 */
public final class PromptText {

	/** 프롬프트에서 Agent가 받은 값을 읽는 이름입니다. */
	public static final String INPUT = "input";

	/** 프롬프트에서 돌려줄 필드 이름을 가리키는 이름입니다. */
	public static final String OUTPUT = "output";

	/** 글자 그대로 쓰는 "${" 입니다. */
	private static final String ESCAPED_PREFIX = "\\${";

	/** 채우는 동안 글자 그대로의 "${" 를 잠시 바꿔 둘 표시입니다(표현식으로 읽히지 않게). */
	private static final String HIDDEN_PREFIX = "\u0000{";

	/** 파일을 읽을 때 바뀌지 않고 남은 환경 변수 모양입니다. */
	private static final Pattern ENV_NAME = Pattern.compile("[A-Z0-9_]+");

	/** 예전 문법 {이름} 입니다. 앞에 $ 나 \ 가 붙은 것은 빼고 찾습니다. */
	private static final Pattern OLD_PLACEHOLDER = Pattern.compile("(?<![$\\\\])\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

	private PromptText() {
	}

	/**
	 * <pre>
	 * 프롬프트의 ${ } 자리를 미리 검사합니다. 문제가 없으면 빈 목록을 돌려줍니다.
	 * - 문법이 맞는지
	 * - 읽는 곳이 input / output 인지
	 * - ${input.필드}의 필드가 input 모양에 있는지, ${output.필드}의 필드가 output 모양에 있는지
	 * - 예전 문법 {필드}가 남아 있지 않은지(input/output 필드 이름과 같은 것만 봅니다)
	 * </pre>
	 *
	 * @param prompt       프롬프트 본문
	 * @param inputSchema  Agent input 스키마
	 * @param outputSchema Agent output 스키마
	 */
	public static List<String> check(String prompt, Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
		List<String> problems = new ArrayList<>();
		if (prompt == null) {
			return problems;
		}
		String text = hideEscaped(prompt);
		List<ContextResolver.Reference> references;
		try {
			references = ContextResolver.references(text);
		} catch (ExpressionException e) {
			problems.add(e.getMessage());
			return problems;
		}
		for (ContextResolver.Reference reference : references) {
			String problem = checkReference(reference, inputSchema, outputSchema);
			if (problem != null) {
				problems.add(problem);
			}
		}
		// 예전 문법 {필드}: 이제는 그냥 글자로 남아서 LLM에게 그대로 보이므로, 필드 이름과 같으면 고치라고 알려 줍니다.
		Set<String> inputNames = fieldNames(inputSchema);
		Set<String> outputNames = fieldNames(outputSchema);
		Set<String> reported = new LinkedHashSet<>();
		Matcher matcher = OLD_PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			String name = matcher.group(1);
			if (!reported.add(name)) {
				continue;
			}
			if (inputNames.contains(name)) {
				problems.add("{" + name + "} - 예전 문법입니다. ${input." + name + "} 로 적으십시오.");
			} else if (outputNames.contains(name)) {
				problems.add("{" + name + "} - 예전 문법입니다. 돌려줄 필드 이름은 ${output." + name + "} 로 적으십시오.");
			}
		}
		return problems;
	}

	/**
	 * <pre>
	 * 프롬프트의 ${ } 자리를 채운 글자를 돌려줍니다. 없는 값은 빈 글자가 됩니다.
	 * </pre>
	 *
	 * @param prompt       프롬프트 본문
	 * @param input        Agent가 받은 값
	 * @param outputSchema Agent output 스키마(${output.필드} 를 필드 이름으로 채울 때 씁니다)
	 * @throws ExpressionException 문법이 틀렸을 때(검사를 통과한 프롬프트라면 나지 않습니다)
	 */
	public static String render(String prompt, Object input, Map<String, Object> outputSchema) {
		if (prompt == null || prompt.isEmpty()) {
			return "";
		}
		String text = hideEscaped(prompt);
		if (text.contains(ContextResolver.PREFIX)) {
			Map<String, Object> context = new LinkedHashMap<>();
			context.put(INPUT, input);
			Map<String, Object> variables = new LinkedHashMap<>();
			variables.put(OUTPUT, outputNames(outputSchema));
			Object resolved = ContextResolver.resolve(text, context, variables);
			// 프롬프트 전체가 ${input} 하나뿐이면 값이 타입 그대로 오므로 글자로 바꿉니다.
			text = resolved == null ? "" : (resolved instanceof String s ? s : JsonSchemaUtil.toText(resolved));
		}
		return text.replace(HIDDEN_PREFIX, ContextResolver.PREFIX);
	}

	/**
	 * 표현식 하나를 검사합니다. 문제가 없으면 null입니다.
	 *
	 * @param reference    검사할 표현식
	 * @param inputSchema  Agent input 스키마
	 * @param outputSchema Agent output 스키마
	 */
	private static String checkReference(ContextResolver.Reference reference, Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
		String root = reference.root();
		if (INPUT.equals(root)) {
			String problem = JsonSchemaUtil.checkPath(inputSchema, INPUT, reference.path());
			return problem == null ? null : reference.text() + " - " + problem;
		}
		if (OUTPUT.equals(root)) {
			Set<String> names = fieldNames(outputSchema);
			if (names.isEmpty()) {
				return reference.text() + " - 이 Agent는 output에 필드를 적지 않아서(답을 글자로 받음) 가리킬 필드가 없습니다.";
			}
			if (reference.path().size() != 1) {
				return reference.text() + " - output은 필드 이름 하나까지만 적습니다. 예: ${output." + names.iterator().next() + "}";
			}
			if (!names.contains(reference.path().get(0))) {
				return reference.text() + " - output에는 " + names + "만 있습니다('" + reference.path().get(0) + "' 없음).";
			}
			return null;
		}
		if (ENV_NAME.matcher(root).matches()) {
			return reference.text() + " - 환경 변수 " + root + " 를 찾지 못했습니다(conf/env*.properties에 있는지 확인하십시오).";
		}
		return reference.text() + " - 프롬프트에서는 input과 output만 읽을 수 있습니다."
			+ " Workflow의 state 같은 값이 필요하면 step의 with로 Agent input에 넣어 주고 ${input.이름} 으로 읽으십시오.";
	}

	/** 글자 그대로 쓰는 \${ 를 표현식으로 읽히지 않게 잠시 바꿔 둡니다. */
	private static String hideEscaped(String prompt) {
		return prompt.replace(ESCAPED_PREFIX, HIDDEN_PREFIX);
	}

	/** object 스키마의 맨 위 필드 이름들입니다. object가 아니면 빈 목록입니다. */
	private static Set<String> fieldNames(Map<String, Object> schema) {
		Map<String, Object> properties = schema == null ? null : JsonSchemaUtil.properties(schema);
		return properties == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(properties.keySet());
	}

	/** ${output.필드} 를 채울 값: 필드 이름 → 필드 이름 */
	private static Map<String, Object> outputNames(Map<String, Object> outputSchema) {
		Map<String, Object> names = new LinkedHashMap<>();
		for (String name : fieldNames(outputSchema)) {
			names.put(name, name);
		}
		return names;
	}

}
