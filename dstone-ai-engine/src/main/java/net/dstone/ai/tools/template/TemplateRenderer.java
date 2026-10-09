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
 * 
 * <샘플 및 상세설명>
 *  * 아래 예시는 모두 이 데이터를 넣는다고 가정.
 *  {
 *    "title": "FAQ 메뉴",
 *    "reference": { "name": "샘플게시판" },
 *    "targets": [
 *      { "path": "/src/Faq.java",  "frs": ["FR-01", "FR-02"], "change": "새로 만든다" },
 *      { "path": "/src/menu.sql",  "frs": [],                 "change": "메뉴 | 추가\n한 줄" }
 *    ],
 *    "questions": []
 *  }
 *
 *  1. {{이름}} / {{a.b.c}} — 값 넣기. 그 자리에 값을 그대로 넣습니다. 점으로 안쪽 필드를 가리킵니다.
 *    제목: {{title}} / 참고: {{reference.name}} / 담당: {{owner}}
 *    제목: FAQ 메뉴 / 참고: 샘플게시판 / 담당:
 *    
 *  2. {{#목록}} ... {{/목록}} — 되풀이. 목록의 항목 수만큼 안쪽을 반복합니다. 안에서는 항목의 필드를 이름만으로 씁니다.
 *    {{#targets}}
 *    - {{path}}: {{change}}
 *    {{/targets}}
 *    - /src/Faq.java: 새로 만든다
 *    - /src/menu.sql: 메뉴 | 추가한 줄    
 *    -> 항목이 글자인 목록(frs)은 필드가 없으므로 항목 자체를 {{.}}으로 씁니다.
 *    -> 안쪽에서 찾지 못한 이름은 바깥에서 찾습니다. 그래서 {{#frs}}{{.}} ({{path}}){{/frs}}처럼 바깥 항목의 path를 함께 쓸 수 있습니다.
 *    -> {{#targets}}처럼 태그만 있는 줄은 결과에 빈 줄을 남기지 않습니다.
 *    -> 목록 안의 null은 없는 항목으로 보고 건너뜁니다. forEach step에서 실패한 자리가 null로 오기 때문에 넣은 규칙입니다.
 *    
 *  3. {{#값}} ... {{/값}} — 있을 때만. 2번과 같은 문법인데, 값이 목록이 아니면 "있으면 한 번 보여 준다"로 동작합니다.
 *    {{#title}}제목이 있습니다: {{.}}{{/title}}
 *    -> 없는 값, false, 빈 글자 "", 빈 목록 []이면 안쪽 전체를 건너뜁니다.
 *    
 *  4. {{^값}} ... {{/값}} — 없을 때만. 3번의 반대입니다. 보통 둘을 짝지어 "있으면 값, 없으면 기본 문구"를 만듭니다.
 *    {{#questions}}
 *    - {{.}}
 *    {{/questions}}
 *    {{^questions}}
 *    없음
 *    {{/questions}}
 *    없음
 *    -> if / else에 해당하는 유일한 방법입니다. 다만 "있다/없다"만 따질 수 있고 "값이 '수정'이면" 같은 비교는 못 합니다.
 *    
 *  5. {{-index}} — 순번. 되풀이 안에서 몇 번째 항목인지를 1부터 넣습니다.
 *    {{#targets}}
 *    ## {{-index}}. {{path}}
 *    {{/targets}}
 *    ## 1. /src/Faq.java
 *    ## 2. /src/menu.sql
 *    
 *  6. {{#-first}} / {{^-last}} — 첫 항목일 때만 / 마지막이 아닐 때만 되풀이 안에서만 쓰는 특별한 이름입니다.
 *    쉼표로 잇기 — 마지막 항목 뒤에는 쉼표를 붙이지 않습니다.
 *    {{#frs}}{{.}}{{^-last}}, {{/-last}}{{/frs}}
 *    FR-01, FR-02
 *    목록이 있을 때만 머리줄 넣기 — 머리줄을 되풀이 밖에 두면 목록이 비어도 표 머리만 덩그러니 나옵니다. 첫 항목일 때만 찍으면 목록이 빌 때 표 전체가 사라집니다.
 *    {{#renames}}
 *    {{#-first}}
 *    | 참고 | 신규 |
 *    |---|---|
 *    {{/-first}}
 *    | {{from}} | {{to}} |
 *    {{/renames}}
 *    
 *  7. {{#cell}} ... {{/cell}} — 표의 한 칸에 넣기. Mustache 표준이 아니라 이 엔진이 넣어 둔 도우미입니다. 마크다운 표는 |로 칸을 나누고 한 줄이 한 행이라서, 
 *    값에 |나 줄바꿈이 있으면 표가 깨집니다. 감싼 부분의 |를 /로, 줄바꿈을 빈칸으로 바꿉니다.
 *    | {{path}} | {{#cell}}{{change}}{{/cell}} |
 *    | /src/menu.sql | 메뉴 / 추가 한 줄 |
 *    
 *  틀로 할 수 없는 것    
 *    - 값 비교나 계산 (예: "유형이 수정인 것만", "개수 세기")
 *    - 두 목록을 서로 맞춰 보기 (예: "FR마다 그 FR을 다루는 파일 찾기")
 *    - 목록 전체에 대한 판단 (예: "어느 항목에도 질문이 없으면 '없음'")
 *    
 *  이런 것이 필요하면 틀에 넣기 전에 데이터를 그 모양으로 만들어야 합니다. filterList로 먼저 거르거나, Agent의 output스키마를 그 모양으로 정하는 식입니다.
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
