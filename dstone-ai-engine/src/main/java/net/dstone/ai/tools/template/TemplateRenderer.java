package net.dstone.ai.tools.template;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * 틀(Mustache 템플릿)에 데이터를 끼워 글자를 만듭니다. 파일을 읽거나 쓰지 않아서 단위 테스트로 그대로 확인할 수 있습니다.
 *
 * 틀에서 쓸 수 있는 것은 이것뿐입니다(로직은 넣을 수 없습니다).
 *   {{이름}} / {{a.b.c}}        값을 그 자리에 넣습니다. 없으면 빈 글자입니다.
 *   {{#목록}} ... {{/목록}}      목록의 항목마다 되풀이합니다. 안에서는 항목의 필드를 {{이름}}으로, 항목 자체를 {{.}}으로 씁니다.
 *   {{#값}} ... {{/값}}          값이 있을 때만 보여 줍니다(없음, false, 빈 글자, 빈 목록이면 건너뜁니다).
 *   {{^값}} ... {{/값}}          값이 없을 때만 보여 줍니다.
 *   {{-index}}                  되풀이 안에서 몇 번째인지(1부터)
 *   {{#-first}} / {{^-last}}    되풀이의 첫 항목일 때만 / 마지막 항목이 아닐 때만 (표의 머리줄, 쉼표 넣기에 씁니다)
 *   {{#cell}} ... {{/cell}}     표의 한 칸에 넣을 글자로 바꿉니다(칸을 나누는 | 는 / 로, 줄바꿈은 빈칸으로).
 *
 * 값은 있는 그대로 들어갑니다(HTML 이스케이프를 하지 않습니다). 맵이나 리스트를 그대로 넣으면 JSON 글자가 됩니다.
 * 목록 안의 빈 자리(null)는 없는 항목으로 봅니다. forEach step의 결과에서 실패한 자리가 null로 오기 때문입니다.
 * </pre>
 */
public final class TemplateRenderer {

	/** 틀에서 {{#cell}}...{{/cell}}로 쓰는 이름입니다. */
	private static final String CELL = "cell";

	private TemplateRenderer() {
	}

	/**
	 * 틀에 데이터를 끼운 글자를 돌려줍니다. 틀의 문법이 틀렸으면 예외(MustacheException)가 납니다.
	 *
	 * @param template 틀의 내용(Mustache)
	 * @param data     틀에 넣을 데이터. 없으면 빈 데이터로 봅니다.
	 */
	public static String render(String template, Map<String, Object> data) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put(CELL, new CellLambda());
		if (data != null) {
			for (Map.Entry<String, Object> entry : data.entrySet()) {
				context.put(entry.getKey(), withoutNulls(entry.getValue()));
			}
		}
		Template compiled = Mustache.compiler()
			.escapeHTML(false)
			.defaultValue("")
			.emptyStringIsFalse(true)
			.withFormatter(new ValueFormatter())
			.compile(template);
		return compiled.execute(context);
	}

	/** 리스트 안의 null을 뺀 값을 돌려줍니다(맵과 리스트 안쪽까지). */
	@SuppressWarnings("unchecked")
	private static Object withoutNulls(Object value) {
		if (value instanceof List) {
			List<Object> result = new ArrayList<>();
			for (Object item : (List<Object>) value) {
				if (item != null) {
					result.add(withoutNulls(item));
				}
			}
			return result;
		}
		if (value instanceof Map) {
			Map<String, Object> result = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
				result.put(entry.getKey(), withoutNulls(entry.getValue()));
			}
			return result;
		}
		return value;
	}

	/** 값을 글자로 바꾸는 방법입니다. 글자와 숫자는 그대로, 맵과 리스트는 JSON 글자로 넣습니다. */
	private static final class ValueFormatter implements Mustache.Formatter {
		@Override
		public String format(Object value) {
			if (value instanceof Map || value instanceof List) {
				return JsonSchemaUtil.toText(value);
			}
			return String.valueOf(value);
		}
	}

	/** {{#cell}}...{{/cell}}: 안의 글자를 표의 한 칸에 넣을 수 있게 바꿉니다. */
	private static final class CellLambda implements Mustache.Lambda {
		@Override
		public void execute(Template.Fragment fragment, Writer out) throws IOException {
			out.write(fragment.execute().replace("|", "/").replaceAll("\r?\n", " "));
		}
	}

}
