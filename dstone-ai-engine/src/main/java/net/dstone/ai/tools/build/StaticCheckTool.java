package net.dstone.ai.tools.build;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.tools.build.sandbox.SandboxDdl;
import net.dstone.ai.tools.file.FileTool;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 서버를 띄우지 않고 몇 초 안에 잡을 수 있는 형식 오류를 찾습니다. 사람에게 보이거나 서버를 띄우기 전에 거르려는 것입니다.
 * 사람 승인 한 번이 20~40분이라(실행 daa7c276: 전체 118분 중 76%가 승인 대기), 기계로 잡을 수 있는 것은 그 전에 잡습니다.
 *
 *   checkDocuments  Markdown 문서: 닫히지 않은 코드 블록(```), 칸 수가 머리줄과 다른 표 줄, DDL 파일을 격리 서버와 같은 규칙으로 읽기
 *                   (실행 ff9ec3c7: DDL 문서 끝의 ``` 하나로 기동이 두 번 실패했고, 사람은 승인할 때 보지 못했다)
 *   checkSources    개발 결과 폴더: XML이 올바른지와 MyBatis statement id 중복, JSP의 스크립틀릿(<% %>)과 태그 라이브러리 태그의 짝,
 *                   JavaScript 구문(node --check). Java는 compileJava가 본다.
 *
 * 결과는 {route: ok | problem, problems, checked, skipped, message}입니다. 걸린 것이 있어도 Tool은 성공이고,
 * Workflow가 routes로 고치는 쪽과 넘어가는 쪽을 나눕니다(DataTool.countUp과 같은 모양).
 *
 * 틀렸다고 확신할 수 있는 것만 problem으로 냅니다. 확인할 수 없는 것은 건너뛰고 skipped에 적습니다.
 *   - JSP 안의 <script>는 JSP 코드(<% %>, ${...}, 태그)가 섞이지 않은 블록만 검사한다(섞이면 실행 전에는 JS가 아니다)
 *   - type이 JavaScript가 아닌 <script>(화면 템플릿 등)는 검사하지 않는다
 *   - 태그 짝은 그 JSP가 taglib으로 선언한 접두어의 태그만 본다
 *   - node가 없으면 JavaScript 검사는 건너뛴다(설정 dstone.ai.tool.static-check.node-command, 기본 node)
 * 앱을 모릅니다. 확장자와 문법만 봅니다.
 * </pre>
 */
@AiTool
public class StaticCheckTool extends BaseObject {

	/** 파일 하나에서 알리는 최대 문제 수(나머지는 수만) */
	private static final int MAX_PROBLEMS_PER_FILE = 10;

	/** node --check 한 번의 최대 대기 초 */
	private static final int NODE_TIMEOUT_SECONDS = 20;

	private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(```+|~~~+)(.*)$");
	private static final Pattern TAGLIB = Pattern.compile("<%@\\s*taglib\\b[^%]*?prefix\\s*=\\s*[\"']([A-Za-z0-9_-]+)[\"']");
	private static final Pattern SCRIPT_OPEN = Pattern.compile("(?is)<script\\b([^>]*)>");
	private static final Pattern SCRIPT_TYPE = Pattern.compile("(?is)\\btype\\s*=\\s*[\"']([^\"']*)[\"']");
	private static final Pattern SCRIPT_SRC = Pattern.compile("(?is)\\bsrc\\s*=");
	private static final Pattern MODULE_SYNTAX = Pattern.compile("(?m)^\\s*(import|export)\\s");
	private static final Pattern NODE_ERROR_LINE = Pattern.compile(":(\\d+)\\s*$");

	@Autowired
	private ConfigProperty configProperty;

	/** node를 쓸 수 있는지(처음 한 번만 확인). null이면 아직 모름 */
	private volatile Boolean nodeAvailable;

	/** 문제 목록과 건너뛴 것 */
	static class Report {
		final List<String> problems = new ArrayList<String>();
		final Set<String> skipped = new LinkedHashSet<String>();
		int checked;

		void add(String file, int line, String message) {
			problems.add(file + (line > 0 ? ":" + line : "") + " " + message);
		}

		Map<String, Object> toResult(String what) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			result.put("route", problems.isEmpty() ? "ok" : "problem");
			result.put("problems", problems);
			result.put("checked", Integer.valueOf(checked));
			result.put("skipped", new ArrayList<String>(skipped));
			StringBuilder message = new StringBuilder();
			if (problems.isEmpty()) {
				message.append(what).append(" ").append(checked).append("개에서 형식 오류를 찾지 못했습니다.");
			} else {
				message.append(what).append(" ").append(checked).append("개에서 형식 오류 ").append(problems.size()).append("건을 찾았습니다. 고칠 것:\n");
				for (int i = 0; i < problems.size(); i++) {
					message.append("- ").append(problems.get(i)).append('\n');
				}
			}
			if (!skipped.isEmpty()) {
				message.append(problems.isEmpty() ? " " : "").append("검사하지 않은 것: ").append(String.join("; ", skipped));
			}
			result.put("message", message.toString().trim());
			return result;
		}
	}

	/* ============================== 문서 ============================== */

	@Tool(description = "Markdown 문서들의 형식 오류를 찾는다: 닫히지 않은 코드 블록(```), 칸 수가 머리줄과 다른 표 줄. ddlFile을 주면 격리 시험 서버와 같은 규칙으로 DDL을 읽어 본다. "
		+ "{route: ok | problem, problems(파일:줄 내용), checked, skipped, message}를 돌려준다. 없는 문서는 건너뛴다.")
	public Map<String, Object> checkDocuments(
			@ToolParam(description = "검사할 Markdown 파일의 절대경로 목록") List<String> files,
			@ToolParam(required = false, description = "DDL 파일(.sql 또는 ```sql 블록이 든 .md)의 절대경로. 없으면 비운다") String ddlFile) {
		Report report = new Report();
		if (files != null) {
			for (int i = 0; i < files.size(); i++) {
				String path = files.get(i);
				if (StringUtil.isEmpty(path)) {
					continue;
				}
				File file = new File(path.trim());
				if (!file.isFile()) {
					report.skipped.add(file.getName() + "(없음)");
					continue;
				}
				String text = FileTool.readText(file);
				if (text == null) {
					report.skipped.add(file.getName() + "(글자 파일이 아님)");
					continue;
				}
				report.checked++;
				checkMarkdown(file.getName(), text, report);
			}
		}
		if (!StringUtil.isEmpty(ddlFile)) {
			File file = new File(ddlFile.trim());
			if (file.isFile()) {
				String text = FileTool.readText(file);
				if (text != null) {
					String problem = SandboxDdl.problemOf(file.getName(), text);
					if (problem != null) {
						report.add(file.getName(), 0, "DDL을 읽을 수 없음: " + problem);
					}
				}
			}
		}
		return report.toResult("문서");
	}

	/** 코드 블록 짝과 표 칸 수 */
	static void checkMarkdown(String name, String text, Report report) {
		String[] lines = text.replace("\r\n", "\n").split("\n", -1);
		int before = report.problems.size();
		String fence = null;
		int fenceLine = 0;
		int headerCells = -1;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Matcher m = FENCE.matcher(line);
			if (fence == null) {
				if (m.matches()) {
					fence = m.group(1);
					fenceLine = i + 1;
					headerCells = -1;
					continue;
				}
			} else {
				// 같은 글자로 같은 길이 이상, 뒤에 아무것도 없으면 닫힘
				if (m.matches() && m.group(1).charAt(0) == fence.charAt(0) && m.group(1).length() >= fence.length() && m.group(2).trim().isEmpty()) {
					fence = null;
				}
				continue;
			}
			String trimmed = line.trim();
			if (!trimmed.startsWith("|")) {
				headerCells = -1;
				continue;
			}
			int cells = cellsOf(trimmed);
			boolean separator = trimmed.matches("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$");
			if (headerCells < 0) {
				headerCells = cells;
			} else if (!separator && cells != headerCells && report.problems.size() - before < MAX_PROBLEMS_PER_FILE) {
				report.add(name, i + 1, "표의 칸 수가 머리줄(" + headerCells + "칸)과 다름(" + cells + "칸). 칸 안의 |는 \\|로 쓰거나 `코드`로 감싼다");
			}
		}
		if (fence != null) {
			report.add(name, fenceLine, "코드 블록(" + fence + ")이 닫히지 않음. 블록이 문서 끝까지 이어져 그 뒤 내용이 코드로 읽힌다");
		}
	}

	/** 표 한 줄의 칸 수. \| 와 `코드` 안의 |는 칸을 나누지 않는다 */
	static int cellsOf(String row) {
		String value = row.trim();
		if (value.startsWith("|")) {
			value = value.substring(1);
		}
		if (value.endsWith("|") && !value.endsWith("\\|")) {
			value = value.substring(0, value.length() - 1);
		}
		int cells = 1;
		boolean code = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '`') {
				code = !code;
			} else if (c == '|' && !code) {
				cells++;
			}
		}
		return cells;
	}

	/* ============================== 소스 ============================== */

	@Tool(description = "개발 결과 폴더의 형식 오류를 서버를 띄우지 않고 찾는다: XML이 올바른지와 MyBatis statement id 중복, JSP의 <% %>와 태그 라이브러리 태그의 짝, "
		+ "JavaScript 구문(.js 파일과 JSP 코드가 섞이지 않은 <script> 블록, node가 있을 때). Java는 compileJava로 본다. "
		+ "{route: ok | problem, problems(파일:줄 내용), checked, skipped, message}를 돌려준다.")
	public Map<String, Object> checkSources(
			@ToolParam(description = "개발 결과 폴더의 절대경로(원본 루트 기준 같은 경로로 파일이 들어 있다)") String overlayDir) {
		Report report = new Report();
		File root = StringUtil.isEmpty(overlayDir) ? null : new File(overlayDir.trim());
		if (root == null || !root.isDirectory()) {
			report.skipped.add("개발 결과 폴더가 없음: " + overlayDir);
			return report.toResult("파일");
		}
		List<File> files = new ArrayList<File>();
		collect(root, files);
		for (int i = 0; i < files.size(); i++) {
			File file = files.get(i);
			String name = relative(root, file);
			String lower = file.getName().toLowerCase(Locale.ROOT);
			String text;
			if (lower.endsWith(".xml") || lower.endsWith(".jsp") || lower.endsWith(".jspf") || lower.endsWith(".tag")
					|| lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".js")) {
				text = FileTool.readText(file);
				if (text == null) {
					continue;
				}
			} else {
				continue;
			}
			report.checked++;
			if (lower.endsWith(".xml")) {
				checkXml(name, text, report);
			} else if (lower.endsWith(".js")) {
				this.checkJavaScript(name, text, 0, MODULE_SYNTAX.matcher(text).find(), report);
			} else {
				if (lower.endsWith(".jsp") || lower.endsWith(".jspf") || lower.endsWith(".tag")) {
					checkJsp(name, text, report);
				}
				this.checkScripts(name, text, report);
			}
		}
		return report.toResult("파일");
	}

	/** XML이 올바른지(외부 DTD는 읽지 않음)와 MyBatis mapper의 statement id 중복 */
	static void checkXml(String name, String text, Report report) {
		Document document;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setValidating(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(null);
			builder.setEntityResolver(new EntityResolver() {
				@Override
				public InputSource resolveEntity(String publicId, String systemId) {
					return new InputSource(new ByteArrayInputStream(new byte[0]));
				}
			});
			// 글자로 넘긴다. 파일 첫 줄의 encoding 선언(EUC-KR 등)은 이미 FileTool.readText가 읽을 때 반영했다
			String xml = text.startsWith("\uFEFF") ? text.substring(1) : text;
			document = builder.parse(new InputSource(new StringReader(xml)));
		} catch (SAXParseException e) {
			report.add(name, e.getLineNumber(), "XML 형식 오류: " + e.getMessage());
			return;
		} catch (Exception e) {
			report.add(name, 0, "XML을 읽지 못함: " + e.getMessage());
			return;
		}
		Element root = document.getDocumentElement();
		if (root == null || !"mapper".equals(root.getTagName())) {
			return;
		}
		Set<String> ids = new HashSet<String>();
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			String id = ((Element) node).getAttribute("id");
			if (!StringUtil.isEmpty(id) && !ids.add(id)) {
				report.add(name, 0, "MyBatis statement id가 두 번 나옴: " + id);
			}
		}
	}

	/** <% %> 짝과, taglib으로 선언한 접두어 태그의 여닫기 짝 */
	static void checkJsp(String name, String text, Report report) {
		String body = blankComments(text.replace("\r\n", "\n"));
		int before = report.problems.size();
		// 1) 스크립틀릿
		int open = -1;
		for (int i = 0; i + 1 < body.length(); i++) {
			if (body.charAt(i) == '<' && body.charAt(i + 1) == '%') {
				if (open >= 0) {
					report.add(name, lineOf(body, i), "'<%'가 앞의 '<%'(" + lineOf(body, open) + "줄)를 닫기 전에 다시 열림");
					return;
				}
				open = i;
				i++;
			} else if (body.charAt(i) == '%' && body.charAt(i + 1) == '>' && open >= 0) {
				open = -1;
				i++;
			}
		}
		if (open >= 0) {
			report.add(name, lineOf(body, open), "'<%'를 닫는 '%>'가 없음");
			return;
		}
		// 2) 태그 라이브러리 태그
		Set<String> prefixes = new HashSet<String>();
		Matcher taglib = TAGLIB.matcher(body);
		while (taglib.find()) {
			prefixes.add(taglib.group(1));
		}
		if (prefixes.isEmpty()) {
			return;
		}
		String code = blankScriptlets(body);
		Deque<String[]> stack = new ArrayDeque<String[]>();
		int i = 0;
		while (i < code.length() && report.problems.size() - before < MAX_PROBLEMS_PER_FILE) {
			int lt = code.indexOf('<', i);
			if (lt < 0) {
				break;
			}
			boolean closing = lt + 1 < code.length() && code.charAt(lt + 1) == '/';
			int nameStart = closing ? lt + 2 : lt + 1;
			int colon = code.indexOf(':', nameStart);
			if (colon < 0 || !prefixes.contains(code.substring(nameStart, colon))) {
				i = lt + 1;
				continue;
			}
			int nameEnd = colon + 1;
			while (nameEnd < code.length() && (Character.isLetterOrDigit(code.charAt(nameEnd)) || code.charAt(nameEnd) == '-' || code.charAt(nameEnd) == '_')) {
				nameEnd++;
			}
			String tag = code.substring(nameStart, nameEnd);
			int end = endOfTag(code, nameEnd);
			if (end < 0) {
				report.add(name, lineOf(code, lt), "<" + tag + " 태그가 '>'로 끝나지 않음");
				return;
			}
			boolean selfClosing = code.charAt(end - 1) == '/';
			if (closing) {
				if (stack.isEmpty() || !stack.peek()[0].equals(tag)) {
					report.add(name, lineOf(code, lt), "</" + tag + ">의 짝이 맞지 않음" + (stack.isEmpty() ? "(연 태그 없음)" : "(열린 태그: <" + stack.peek()[0] + "> " + stack.peek()[1] + "줄)"));
					return;
				}
				stack.pop();
			} else if (!selfClosing) {
				stack.push(new String[] { tag, String.valueOf(lineOf(code, lt)) });
			}
			i = end + 1;
		}
		if (!stack.isEmpty()) {
			report.add(name, Integer.parseInt(stack.peek()[1]), "<" + stack.peek()[0] + ">를 닫는 태그가 없음");
		}
	}

	/** 태그의 끝 '>' 위치. 따옴표 안의 '>'는 건너뛴다 */
	private static int endOfTag(String code, int from) {
		char quote = 0;
		for (int i = from; i < code.length(); i++) {
			char c = code.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '>') {
				return i;
			}
		}
		return -1;
	}

	/** JSP·HTML 안의 <script> 블록 가운데 JSP 코드가 섞이지 않은 JavaScript만 검사한다 */
	private void checkScripts(String name, String text, Report report) {
		String body = text.replace("\r\n", "\n");
		Matcher open = SCRIPT_OPEN.matcher(body);
		int from = 0;
		while (open.find(from)) {
			String attrs = open.group(1);
			int start = open.end();
			int close = body.toLowerCase(Locale.ROOT).indexOf("</script", start);
			if (close < 0) {
				report.add(name, lineOf(body, open.start()), "<script>를 닫는 </script>가 없음");
				return;
			}
			from = close + 1;
			if (SCRIPT_SRC.matcher(attrs).find()) {
				continue;
			}
			Matcher type = SCRIPT_TYPE.matcher(attrs);
			String typeValue = type.find() ? type.group(1).trim().toLowerCase(Locale.ROOT) : "";
			boolean module = "module".equals(typeValue);
			if (!typeValue.isEmpty() && !module && !typeValue.contains("javascript") && !typeValue.contains("ecmascript")) {
				continue;
			}
			String script = body.substring(start, close);
			if (script.trim().isEmpty()) {
				continue;
			}
			if (script.contains("<%") || script.contains("${") || script.contains("#{") || containsTag(script)) {
				report.skipped.add("JSP 코드가 섞인 <script>(서버에서 만들어진 뒤에야 JavaScript가 된다)");
				continue;
			}
			this.checkJavaScript(name, script, lineOf(body, start) - 1, module, report);
		}
	}

	/** <접두어:이름 모양의 태그가 들어 있는지 */
	private static boolean containsTag(String script) {
		return Pattern.compile("</?[A-Za-z][A-Za-z0-9_-]*:[A-Za-z]").matcher(script).find();
	}

	/**
	 * <pre>
	 * node --check로 구문만 봅니다(실행하지 않음). offset은 원본 파일에서 이 코드가 시작하는 줄의 앞 줄 수입니다.
	 * node를 쓸 수 없으면 건너뜁니다.
	 * </pre>
	 */
	private void checkJavaScript(String name, String code, int offset, boolean module, Report report) {
		if (!this.nodeAvailable()) {
			report.skipped.add("JavaScript 구문(node를 실행할 수 없음. 설정 " + Constants.Tool.StaticCheck.PREFIX + ".node-command)");
			return;
		}
		File temp = null;
		try {
			temp = File.createTempFile("dstone-static-check-", module ? ".mjs" : ".js");
			Files.write(temp.toPath(), code.getBytes(StandardCharsets.UTF_8));
			List<String> command = new ArrayList<String>(this.nodeCommand());
			command.add("--check");
			command.add(temp.getAbsolutePath());
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			if (!process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				report.skipped.add(name + "의 JavaScript 구문(node가 " + NODE_TIMEOUT_SECONDS + "초 안에 끝나지 않음)");
				return;
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (process.exitValue() == 0) {
				return;
			}
			report.add(name, offset + nodeErrorLine(output, temp.getName()), "JavaScript 구문 오류: " + nodeErrorMessage(output));
		} catch (Exception e) {
			report.skipped.add(name + "의 JavaScript 구문(" + e.getMessage() + ")");
		} finally {
			if (temp != null) {
				temp.delete();
			}
		}
	}

	/** node 출력 첫 줄 "...파일:12"의 줄 번호 */
	static int nodeErrorLine(String output, String tempName) {
		String[] lines = output.replace("\r\n", "\n").split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].contains(tempName)) {
				Matcher m = NODE_ERROR_LINE.matcher(lines[i].trim());
				if (m.find()) {
					return Integer.parseInt(m.group(1));
				}
			}
		}
		return 0;
	}

	/** node 출력에서 "SyntaxError: ..." 줄 */
	static String nodeErrorMessage(String output) {
		String[] lines = output.replace("\r\n", "\n").split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].contains("Error:")) {
				return lines[i].trim();
			}
		}
		return output.trim().length() > 300 ? output.trim().substring(0, 300) : output.trim();
	}

	private boolean nodeAvailable() {
		Boolean known = this.nodeAvailable;
		if (known != null) {
			return known.booleanValue();
		}
		boolean ok;
		try {
			List<String> command = new ArrayList<String>(this.nodeCommand());
			command.add("--version");
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			ok = process.waitFor(NODE_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
			if (!ok) {
				process.destroyForcibly();
			}
		} catch (Exception e) {
			ok = false;
		}
		this.nodeAvailable = Boolean.valueOf(ok);
		return ok;
	}

	/** 설정의 node 명령(빈칸으로 나눈 토큰). 기본 node */
	private List<String> nodeCommand() {
		String value = this.configProperty == null ? null : this.configProperty.getProperty(Constants.Tool.StaticCheck.PREFIX + ".node-command");
		List<String> tokens = new ArrayList<String>();
		String[] parts = (StringUtil.isEmpty(value) ? "node" : value.trim()).split("\\s+");
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				tokens.add(parts[i]);
			}
		}
		return tokens;
	}

	/* ============================== 작은 도구 ============================== */

	/** JSP 주석(<%-- --%>)을 같은 길이의 빈칸으로 바꾼다(줄 번호를 지키려고 줄바꿈은 남김) */
	static String blankComments(String text) {
		return blank(text, "<%--", "--%>");
	}

	/** 스크립틀릿(<% %>)을 빈칸으로 바꾼다. 그 안의 '<'를 태그로 읽지 않으려고 */
	static String blankScriptlets(String text) {
		return blank(text, "<%", "%>");
	}

	private static String blank(String text, String open, String close) {
		StringBuilder sb = new StringBuilder(text);
		int from = 0;
		while (true) {
			int start = sb.indexOf(open, from);
			if (start < 0) {
				break;
			}
			int end = sb.indexOf(close, start + open.length());
			int stop = end < 0 ? sb.length() : end + close.length();
			for (int i = start; i < stop; i++) {
				if (sb.charAt(i) != '\n') {
					sb.setCharAt(i, ' ');
				}
			}
			from = stop;
		}
		return sb.toString();
	}

	private static int lineOf(String text, int index) {
		int line = 1;
		for (int i = 0; i < index && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	private static void collect(File dir, List<File> files) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			if (children[i].isDirectory()) {
				collect(children[i], files);
			} else {
				files.add(children[i]);
			}
		}
	}

	private static String relative(File root, File file) {
		String rootPath = root.getAbsolutePath().replace('\\', '/');
		String path = file.getAbsolutePath().replace('\\', '/');
		return path.startsWith(rootPath + "/") ? path.substring(rootPath.length() + 1) : path;
	}

}
