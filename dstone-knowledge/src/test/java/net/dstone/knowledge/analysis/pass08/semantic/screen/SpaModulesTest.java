package net.dstone.knowledge.analysis.pass08.semantic.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * Vue / React 파일의 import · 정의 읽기(SpaModules)와 주소 맞추기(SpaEndpoints), 경로 도구(SpaLinker)를 확인합니다.
 * DB나 Spring 없이 도는 테스트입니다. 표본은 /app/testVueApp, /app/testReactApp 의 화면과 API 모듈을 줄인 것입니다.
 * </pre>
 */
public class SpaModulesTest {

	private final SpaModules spaModules = new SpaModules();

	/** "local=imported<-spec" 모양으로 */
	private List<String> imports(SpaModules.Module module) {
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < module.getImports().size(); i++) {
			SpaModules.Import imp = module.getImports().get(i);
			list.add(imp.getLocal() + "=" + imp.getImported() + "<-" + imp.getSpec() + (imp.isDynamic() ? " (dynamic)" : ""));
		}
		return list;
	}

	@Test
	public void import_모양을_모두_읽는다() {
		String text = "import axios from 'axios'\n"
				+ "import { fetchBoards, deleteBoard as remove } from '@/api/board'\n"
				+ "import type { Board } from '@/types'\n"
				+ "import DataGrid, { type Column } from '@/components/DataGrid'\n"
				+ "import * as codeApi from \"./code\"\n"
				+ "import {\n  useState,\n  useEffect\n} from 'react'\n"
				+ "import './main.css'\n"
				+ "// import { old } from './old'\n"
				+ "const lazy = () => import('@/views/sys/CodeManage.vue')\n"
				+ "const util = require('./util')\n";
		List<String> found = imports(spaModules.parse(text));
		assertEquals(Arrays.asList("axios=default<-axios", "fetchBoards=fetchBoards<-@/api/board", "remove=deleteBoard<-@/api/board"
				, "DataGrid=default<-@/components/DataGrid", "codeApi=*<-./code", "useState=useState<-react", "useEffect=useEffect<-react"
				, "null=null<-./main.css", "null=null<-@/views/sys/CodeManage.vue (dynamic)", "util=default<-./util"), found);
	}

	@Test
	public void 맨_바깥_정의의_범위는_다음_맨_바깥_문장_앞까지() {
		String text = "import http from './http'\n\n"
				+ "// 샘플게시판 API\n"
				+ "export async function fetchBoards(title) {\n  const res = await http.get('/sample/boards', { params: { title } })\n  return res.data\n}\n\n"
				+ "export const fetchBoard = (seq: string) => apiClient.get<Board>(`/sample/boards/${seq}`)\n\n"
				+ "function local() {\n  if (a) {\n    const inner = 1\n  }\n}\n"
				+ "export default http\n";
		SpaModules.Module module = spaModules.parse(text);
		assertEquals(Arrays.asList("fetchBoards", "fetchBoard", "local"), new ArrayList<String>(module.getDefinitions().keySet()));
		int[] fetchBoards = module.getDefinitions().get("fetchBoards");
		assertTrue(text.substring(fetchBoards[0], fetchBoards[1]).contains("/sample/boards'"));
		assertFalse(text.substring(fetchBoards[0], fetchBoards[1]).contains("fetchBoard ="));
		assertEquals("http", module.localNameOf("default"));
	}

	@Test
	public void 변수_정의는_그_문장이_끝나는_곳까지() {
		String text = "import router from '@/router'\n"
				+ "const http = axios.create({\n  baseURL: '/api'\n})\n\n"
				+ "http.interceptors.response.use(\n  (r) => r,\n  (e) => { router.push('/login') }\n)\n\n"
				+ "export const list = (t) =>\n  http\n    .get('/a', { t })\n"
				+ "export default http\n";
		SpaModules.Module module = spaModules.parse(text);
		int[] http = module.getDefinitions().get("http");
		assertFalse(text.substring(http[0], http[1]).contains("interceptors"));
		int[] list = module.getDefinitions().get("list");
		assertTrue(text.substring(list[0], list[1]).contains(".get('/a'"));
	}

	@Test
	public void 객체로_묶어_내보낸_함수는_멤버마다_범위를_둔다() {
		String text = "export const codeService = {\n"
				+ "  listGroups(keyword?: string) {\n    return apiClient.get<CodeGroup[]>('/sys/code-groups', { keyword })\n  },\n"
				+ "  async commonCodes(grp: string) {\n    return apiClient.get(`/common/codes/${grp}`)\n  },\n"
				+ "  remove: (id) => apiClient.delete(`/sys/code-groups/${id}`)\n}\n";
		SpaModules.Module module = spaModules.parse(text);
		assertTrue(module.getDefinitions().containsKey("codeService"));
		int[] list = module.getDefinitions().get("codeService.listGroups");
		int[] codes = module.getDefinitions().get("codeService.commonCodes");
		int[] remove = module.getDefinitions().get("codeService.remove");
		assertTrue(text.substring(list[0], list[1]).contains("/sys/code-groups'"));
		assertFalse(text.substring(list[0], list[1]).contains("/common/codes"));
		assertTrue(text.substring(codes[0], codes[1]).contains("/common/codes"));
		assertTrue(text.substring(remove[0], remove[1]).contains("delete"));
	}

	@Test
	public void 기본_내보내기와_다시_내보내기() {
		String text = "export default defineStore('auth', {\n  actions: { async login() { await loginApi() } }\n})\n";
		SpaModules.Module store = spaModules.parse(text);
		assertEquals("default", store.localNameOf("default"));
		assertTrue(store.getDefinitions().containsKey("default"));

		String index = "export { fetchBoards as list, fetchBoard } from './board'\nexport * from './code'\nexport { a as b }\nconst a = 1\n";
		SpaModules.Module barrel = spaModules.parse(index);
		assertEquals(3, barrel.getReExports().size());
		assertEquals("list", barrel.getReExports().get(0).getExported());
		assertEquals("fetchBoards", barrel.getReExports().get(0).getImported());
		assertEquals("*", barrel.getReExports().get(2).getExported());
		assertEquals("a", barrel.localNameOf("b"));
	}

	@Test
	public void vue_파일은_script_안에서만_정의를_찾는다() {
		String text = "<template>\n<div>{{ title }}</div>\n<button @click=\"search\">조회</button>\n</template>\n\n"
				+ "<script setup>\nimport { fetchBoards } from '@/api/board'\nconst boards = ref([])\n"
				+ "async function search() {\n  boards.value = await fetchBoards(title.value)\n}\n</script>\n";
		SpaModules.Module module = spaModules.parse(text);
		assertEquals(Arrays.asList("boards", "search"), new ArrayList<String>(module.getDefinitions().keySet()));
		List<SpaModules.Use> uses = spaModules.uses(text, module, 0, text.length(), Arrays.asList("fetchBoards"));
		assertEquals(1, uses.size());
	}

	@Test
	public void 이름이_쓰인_곳과_점_뒤의_이름() {
		String text = "import * as api from './api'\nimport { fetchBoards } from './board'\n"
				+ "api.fetchCodes(); api?.saveCode(x); obj.fetchBoards(); fetchBoards(); const fetchBoardsX = 1;\n";
		SpaModules.Module module = spaModules.parse(text);
		List<SpaModules.Use> uses = spaModules.uses(text, module, 0, text.length(), Arrays.asList("api", "fetchBoards"));
		List<String> found = new ArrayList<String>();
		for (int i = 0; i < uses.size(); i++) {
			found.add(uses.get(i).getName() + (uses.get(i).getMember() == null ? "" : "." + uses.get(i).getMember()));
		}
		assertEquals(Arrays.asList("api.fetchCodes", "api.saveCode", "fetchBoards"), found);
	}

	/* ---------------- SpaEndpoints ---------------- */

	private SpaEndpoints endpoints(String... rows) {
		List<java.util.Map<String, Object>> list = new ArrayList<java.util.Map<String, Object>>();
		for (int i = 0; i < rows.length; i++) {
			String[] parts = rows[i].split(" ");
			java.util.Map<String, Object> row = new java.util.HashMap<String, Object>();
			row.put("httpMethod", parts[0]);
			row.put("path", parts[1]);
			row.put("methodId", parts[2]);
			list.add(row);
		}
		return new SpaEndpoints(list);
	}

	@Test
	public void 경로_변수와_HTTP_메소드로_진입점을_고른다() {
		SpaEndpoints e = endpoints("GET /api/sample/boards list", "POST /api/sample/boards create", "GET /api/sample/boards/{seq} get"
				, "PUT /api/sample/boards/{seq} update", "GET /api/sample/boards/search search", "ALL /api/any any", "GET /sys/** spa");
		assertEquals(Arrays.asList("list"), e.match("/api/sample/boards", "GET"));
		assertEquals(Arrays.asList("create"), e.match("/api/sample/boards", "POST"));
		assertEquals(Arrays.asList("list", "create"), e.match("/api/sample/boards", null));
		assertEquals(Arrays.asList("get"), e.match("/api/sample/boards/{}", "GET"));
		assertEquals(Arrays.asList("update"), e.match("/api/sample/boards/{}", "PUT"));
		// 글자끼리 똑같은 진입점이 경로 변수 진입점보다 먼저다
		assertEquals(Arrays.asList("search"), e.match("/api/sample/boards/search", "GET"));
		assertEquals(Arrays.asList("get"), e.match("/api/sample/boards/7", "GET"));
		// 메소드를 가리지 않는 진입점(ALL)은 어느 메소드든 받는다
		assertEquals(Arrays.asList("any"), e.match("/api/any", "DELETE"));
		// * 가 든 진입점(화면 주소를 돌려주는 컨트롤러)은 맞추지 않는다
		assertTrue(e.match("/sys/codes", "GET").isEmpty());
		assertTrue(e.match("/api/sample/boards", "DELETE").isEmpty());
	}

	@Test
	public void 맞춰_볼_만한_주소인지() {
		assertTrue(SpaEndpoints.usable("/sample/boards/{}"));
		assertFalse(SpaEndpoints.usable("/{}"));
		assertFalse(SpaEndpoints.usable("/api{}{}"));
	}

	/* ---------------- 경로 도구 ---------------- */

	@Test
	public void 경로_다듬기와_tsconfig_주석_빼기() {
		assertEquals("frontend/src/api/board", SpaLinker.normalize("frontend/src/views/../api/./board"));
		assertNull(SpaLinker.normalize("../outside"));
		assertEquals("frontend/src", SpaLinker.dirOf("frontend/src/main.js"));
		String json = "{\n  // 별칭\n  \"compilerOptions\": { /* 기본 */ \"paths\": { \"@/*\": [\"src/*\"] } }\n}";
		String clean = SpaLinker.withoutComments(json);
		assertTrue(clean.contains("\"@/*\": [\"src/*\"]"));
		assertFalse(clean.contains("별칭"));
	}

}
