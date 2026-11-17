package net.dstone.knowledge.analysis.pass08.semantic.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 화면이 서버 주소를 적는 자리(screen: 의 url:)를 찾는 로직을 확인합니다. DB나 Spring 없이 도는 테스트입니다.
 * 표본은 H-Energy(Nexacro), anybiz_prd(JSP + Kendo) 화면에서 실제로 본 모양을 줄인 것입니다.
 * </pre>
 */
public class ScreenUrlsTest {

	private final ScreenUrls screenUrls = new ScreenUrls();

	private static final ScreenUrls.Rule TRANSACTION = ScreenUrls.Rule.call("gfn_Transaction", 1, "this.gfn_Transaction(_, URL)", "projects/h/f.yml:3");

	private List<String> urls(String text, ScreenUrls.Rule... rules) {
		List<String> urls = new ArrayList<String>();
		List<ScreenUrls.Hit> hits = screenUrls.find(text, Arrays.asList(rules));
		for (int i = 0; i < hits.size(); i++) {
			urls.add(hits.get(i).getUrl() + (hits.get(i).getVariable() == null ? "" : " <- " + hits.get(i).getVariable()));
		}
		return urls;
	}

	@Test
	public void 함수_인자에_바로_적은_주소를_읽는다() {
		String text = "this.fn_a = function() {\n  this.gfn_Transaction(\"a\", \"/hhr/a.do\", \"in=in\");\n};";
		assertEquals(Arrays.asList("/hhr/a.do"), urls(text, TRANSACTION));
	}

	@Test
	public void 함수_안에서_한_번만_대입한_변수의_값을_읽는다() {
		// H-Energy 화면 모양: 같은 이름의 변수를 함수마다 따로 만든다
		String text = "this.fn_save = function ()\n{\n\tvar strSvc = \"save\";\n\tvar strUrl      = \"/hhr/ws/insert.do\";\n"
				+ "\tthis.gfn_Transaction(strSvc\n\t\t, strUrl\n\t\t, strInDs);\n};\n"
				+ "this.fn_list = function ()\n{\n\tvar strUrl = \"/hhr/ws/list.do\";\n\tthis.gfn_Transaction(\"list\", strUrl);\n};\n";
		assertEquals(Arrays.asList("/hhr/ws/insert.do <- strUrl", "/hhr/ws/list.do <- strUrl"), urls(text, TRANSACTION));
	}

	@Test
	public void 계산할_수_없는_값은_읽지_않는다() {
		// 글자를 이어 붙였다
		assertEquals(0, urls("this.f = function() { var strUrl = \"/hhr/\" + svcId + \".do\"; this.gfn_Transaction(a, strUrl); };", TRANSACTION).size());
		// 두 번 대입했다
		assertEquals(0, urls("this.f = function() { var u = \"/a.do\"; if (x) { u = \"/b.do\"; } this.gfn_Transaction(a, u); };", TRANSACTION).size());
		// += 로 고쳤다
		assertEquals(0, urls("this.f = function() { var u = \"/a.do\"; u += \"?x=1\"; this.gfn_Transaction(a, u); };", TRANSACTION).size());
		// 매개변수로 받았다(fn_trans 같은 감싸는 함수 안)
		assertEquals(0, urls("this.fn_trans = function (sId, sUrl) { var u = 1; this.gfn_Transaction(sId, sUrl); };", TRANSACTION).size());
		// 주석 안의 호출
		assertEquals(0, urls("// this.gfn_Transaction(\"a\", \"/a.do\");\n/* this.gfn_Transaction(\"a\", \"/b.do\"); */", TRANSACTION).size());
		// 함수 정의
		assertEquals(0, urls("function gfn_Transaction(id, \"/a.do\") {}", TRANSACTION).size());
	}

	@Test
	public void 함수_밖에서_한_번만_대입한_변수는_파일_전체에서_찾는다() {
		String text = "var listUrl = \"/order/list.ajax\";\nfunction load() {\n  $.ajax({ url: listUrl, type: 'post' });\n}\n";
		assertEquals(Arrays.asList("/order/list.ajax <- listUrl"), urls(text, ScreenUrls.Rule.prop("url", "url: URL", null)));
	}

	@Test
	public void 객체_속성의_주소를_다듬어_읽는다() {
		String text = "var ds = {\n  transport: {\n    read : {url: \"/insa/list.ajax?${_csrf.parameterName}=${_csrf.token}\"},\n"
				+ "    \"url\": '${ctx}/insa/save.ajax'\n  }\n};\n";
		assertEquals(Arrays.asList("/insa/list.ajax", "/insa/save.ajax"), urls(text, ScreenUrls.Rule.prop("url", "url: URL", null)));
		// 삼항 연산자의 : 는 속성이 아니다
		assertEquals(0, urls("var x = a ? url : \"/b.ajax\";", ScreenUrls.Rule.prop("url", "url: URL", null)).size());
		// 이어 붙인 값
		assertEquals(0, urls("var o = { url: base + \"/a.ajax\" };", ScreenUrls.Rule.prop("url", "url: URL", null)).size());
	}

	@Test
	public void HTML_태그_속성의_주소를_읽는다() {
		ScreenUrls.Rule form = ScreenUrls.Rule.attr("form", "action", "<form action=\"URL\">", null);
		String text = "<form name=\"downform\" method=\"post\" action=\"/files/getFile.do?${_csrf.parameterName}=${_csrf.token}\">\n"
				+ "<FORM id=x ACTION='/files/upload.do'>\n"
				+ "<script>var h = \"<form id='p' action=\\\"/report/print.do\\\" method='POST'>\";</script>\n"
				+ "<form action='<c:url value=\"/login.do\"/>'>\n"
				+ "<!-- <form action=\"/old.do\"> -->\n";
		assertEquals(Arrays.asList("/files/getFile.do", "/files/upload.do", "/report/print.do"), urls(text, form));
	}

	@Test
	public void 규칙에_적은_컨텍스트_경로만_뗀다() {
		ScreenUrls.Rule form = ScreenUrls.Rule.attr("form", "action", "<form action=\"URL\">", null).withContext("/hi-japan");
		String text = "var h = \"<form id='pform' action='/hi-japan/reporting.do' method='POST'>\";\n"
				+ "var k = \"<form action='/other/reporting.do'>\";\n<form action=\"/print.do\">";
		assertEquals(Arrays.asList("/reporting.do", "/other/reporting.do", "/print.do"), urls(text, form));
	}

	@Test
	public void 주소_다듬기() {
		assertEquals("/order/list.do", ScreenUrls.normalize("${pageContext.request.contextPath}/order/list.do?x=1#top"));
		assertEquals("/order/list.do", ScreenUrls.normalize("<%=ctx%>/order/list.do"));
		assertEquals("/order/list.do", ScreenUrls.normalize("svc::order/list.do"));
		assertEquals("/a.do", ScreenUrls.normalize("/a.do;jsessionid=1"));
		assertNull(ScreenUrls.normalize("list.do"));
		assertNull(ScreenUrls.normalize("/a/${id}.do"));
	}

	@Test
	public void 진입점과_똑같은_주소만_잇고_메소드가_하나면_HIGH() {
		Map<String, List<String>> handlers = new HashMap<String, List<String>>();
		handlers.put("/a.do", Arrays.asList("m1"));
		handlers.put("/b.do", Arrays.asList("m2", "m3"));
		String text = "this.gfn_Transaction(x, \"/a.do\"); this.gfn_Transaction(x, \"/b.do\"); this.gfn_Transaction(x, \"/ctx/a.do\");"
				+ " this.gfn_Transaction(y, \"/a.do\");";
		Set<String> linked = new HashSet<String>();
		List<ScreenUrls.Link> links = ScreenUrls.links(screenUrls.find(text, Arrays.asList(TRANSACTION)), handlers, linked);
		assertEquals(3, links.size());
		assertEquals("m1", links.get(0).getMethodId());
		assertEquals("HIGH", links.get(0).getConfidence());
		assertEquals("MEDIUM", links.get(1).getConfidence());
		assertEquals("MEDIUM", links.get(2).getConfidence());
		assertEquals("URL_RULE", links.get(0).properties().get("via"));
		assertEquals("projects/h/f.yml:3", links.get(0).properties().get("ruleAt"));
		assertTrue(linked.contains("m3"));
	}

	/* ---------------- Vue / React (conf/rules/common/spa.yml) ---------------- */

	private static final ScreenUrls.Rule GET = ScreenUrls.Rule.call("get", 0, "axios.get(URL)", "common/spa.yml:9").withVerb("GET");

	private static final ScreenUrls.Rule FETCH = ScreenUrls.Rule.call("fetch", 0, "fetch(URL)", "common/spa.yml:15");

	@Test
	public void 템플릿_글자와_이어_붙인_주소의_값_자리는_중괄호로_남긴다() {
		String text = "export const fetchBoard = (seq: string) => apiClient.get<Board>(`/sample/boards/${seq}`)\n"
				+ "export function list() { return http.get('/sample/boards', { params: { title } }) }\n"
				+ "export function codes(grp) { return axios.get('/common/codes/' + grp) }\n"
				+ "export function bad(id) { return axios.get('/a/' + id + '.do') }\n"
				+ "const v = map.get('key')\n";
		assertEquals(Arrays.asList("/sample/boards/{}", "/sample/boards", "/common/codes/{}"), urls(text, GET));
		assertEquals("GET", screenUrls.find(text, Arrays.asList(GET)).get(0).getVerb());
	}

	@Test
	public void fetch_는_둘째_인자의_method_를_읽는다() {
		String text = "fetch('/api/members', { method: 'POST', body: JSON.stringify(m) })\nfetch(`/api/members/${id}`)\n";
		List<ScreenUrls.Hit> hits = screenUrls.find(text, Arrays.asList(FETCH));
		assertEquals("/api/members", hits.get(0).getUrl());
		assertEquals("POST", hits.get(0).getVerb());
		assertEquals("/api/members/{}", hits.get(1).getUrl());
		assertNull(hits.get(1).getVerb());
	}

	@Test
	public void baseURL_은_속성_값이나_이어_붙인_주소의_맨_앞_글자() {
		ScreenUrls.Rule baseProp = ScreenUrls.Rule.prop("baseURL", "baseURL: URL", "common/spa.yml:17");
		String vue = "const http = axios.create({\n  baseURL: '/api',\n  timeout: 30000\n})\n";
		assertEquals("/api", screenUrls.findBases(vue, Arrays.asList(baseProp)).get(0).getUrl());
		assertEquals("/api", screenUrls.findBases("axios.create({ baseURL: 'http://localhost:8080/api/' })", Arrays.asList(baseProp)).get(0).getUrl());

		String react = "const API_BASE = '/api'\nasync function request(method, path) {\n"
				+ "  const res = await fetch(API_BASE + path + toQuery(params), { method })\n}\nfetch('/api/members')\n";
		List<ScreenUrls.Hit> bases = screenUrls.findBases(react, Arrays.asList(FETCH));
		assertEquals(1, bases.size());
		assertEquals("/api", bases.get(0).getUrl());
		// 래퍼 안의 fetch(API_BASE + path) 는 주소 전체가 값이라 주소로는 남지 않는다
		assertEquals(Arrays.asList("/api/members"), urls(react, FETCH));
	}
}
