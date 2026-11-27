package net.dstone.ai.tools.knowledge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.annotation.ToolCategory;
import net.dstone.ai.common.exception.KnowledgeCallException;
import net.dstone.ai.common.knowledge.KnowledgeClient;
import net.dstone.ai.tools.file.FileTool;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 본뜰 기존 기능 하나를 화면부터 SQL까지 통째로 모아, 원본 코드 그대로 문서 하나에 적습니다(knowledgeFeatureCode).
 *
 * 왜 두나:
 *   새 기능을 설계하는 Agent가 참고 코드를 스스로 찾으면, 화면은 A 기능에서, 받는 Controller는 B 기능에서 가져와
 *   주고받는 형식이 어긋나거나(실행 0bc967d2의 팝업), 프로젝트의 관례(업로드 부품, 공통 서비스의 사전 조건)를 모른 채
 *   새로 짓습니다(실행 9dc51bb8의 업로드). dstone-knowledge가 저장한 관계(화면 → 주소 → 메소드 → SQL, 메소드 → 팝업 화면)를
 *   따라 모으면 한 기능의 것만 짝으로 들어옵니다. 고르는 판단(어느 기능을 본뜰지)만 Agent가 하고, 모으는 일은 이 Tool이 합니다.
 *
 * 무엇을 쓰나: dstone-knowledge의 GET /api/revisions/{리비전}/feature 가 돌려준 목록의 위치(파일, 줄)를 원본 소스 루트에서 읽습니다.
 *   분석한 경로와 지금 원본의 경로가 달라도 되게 프로젝트 기준 상대경로로 받아 sourceRoot에 붙입니다.
 *   글자 수 한도(maxChars) 안에서 중요한 것부터 넣고, 못 넣은 것은 위치만 적습니다.
 *   넣는 순서: 시작 화면 → 팝업 화면 → 처리 메소드 → 그 바로 아래 메소드 → SQL → 더 깊은 메소드.
 *   화면이 먼저인 까닭: 업로드 부품, 팝업 열기와 값 넘기기 같은 관례는 화면에 있고, 처리 메소드는 주소 표에 이름이 남으며
 *   깊은 메소드(DAO, 공통 유틸)는 짧고 뻔하다. (testApp에서 측정: 메소드와 SQL을 앞에 두면 업로드가 있는 팝업 화면이 한도에 걸려 빠졌다)
 *
 * 앱을 모릅니다. 어떤 프로젝트든 dstone-knowledge에 분석해 두었으면 씁니다.
 * </pre>
 */
@AiTool(category = ToolCategory.CODE_RAG)
public class KnowledgeFeatureTool extends BaseObject {

	/**
	 * maxChars를 주지 않았을 때의 문서 글자 수 한도. 다음 step이 readFile(maxChars 60000)로 통째로 읽으므로 그보다 작게 둔다.
	 * 본뜰 기능 두 개(목록 화면 + 업로드 팝업이 있는 화면)를 담으려면 이 정도가 든다(testApp에서 측정)
	 */
	private static final int DEFAULT_MAX_CHARS = 56000;

	/** 화면 파일을 통째로 넣는 최대 줄 수. 넘으면 앞부분만 넣고 위치를 적는다 */
	private static final int MAX_SCREEN_LINES = 700;

	@Autowired
	private KnowledgeClient knowledgeClient;

	/** 문서에 넣을 코드 조각 하나 */
	static class Piece {
		final int priority;
		final int order;
		final String section;
		final String title;
		final String language;
		final String relativePath;
		final int lineStart;
		final int lineEnd;
		String body;
		boolean included;
		String omitted;

		Piece(int priority, int order, String section, String title, String language, String relativePath, int lineStart, int lineEnd) {
			this.priority = priority;
			this.order = order;
			this.section = section;
			this.title = title;
			this.language = language;
			this.relativePath = relativePath;
			this.lineStart = lineStart;
			this.lineEnd = lineEnd;
		}
	}

	@Tool(description = "본뜰 기존 기능 하나를 dstone-knowledge의 분석 결과로 화면부터 SQL까지(팝업 화면 포함) 모아 원본 코드 그대로 outputFile에 Markdown으로 쓴다. "
		+ "entries에는 그 기능의 화면 파일 경로나 주소(/로 시작)를 한 줄에 하나씩 준다. 결과로 저장한 파일, 넣은 것의 수, 찾지 못한 시작점을 돌려준다.")
	public Map<String, Object> knowledgeFeatureCode(
			@ToolParam(description = "프로젝트 ID(dstone-knowledge에 분석해 둔 것)") String projectId,
			@ToolParam(description = "원본 소스 루트의 절대경로. 분석 결과의 상대경로를 여기에 붙여 읽는다") String sourceRoot,
			@ToolParam(description = "본뜰 기능의 화면 파일 경로나 주소. 한 줄에 하나(목록 기호와 백틱은 무시)") String entries,
			@ToolParam(description = "결과를 쓸 Markdown 파일의 절대경로") String outputFile,
			@ToolParam(required = false, description = "문서 글자 수 한도. 비우면 56000") Integer maxChars,
			@ToolParam(required = false, description = "sourceRoot의 형상관리 리비전. 분석한 리비전과 다르면 경고를 남긴다") String sourceRevision) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		List<String> entryList = splitEntries(entries);
		if (entryList.isEmpty()) {
			return fail(result, "본뜰 기능의 시작점(entries)이 비어 있습니다.");
		}
		if (StringUtil.isEmpty(projectId) || StringUtil.isEmpty(sourceRoot) || StringUtil.isEmpty(outputFile)) {
			return fail(result, "projectId, sourceRoot, outputFile을 모두 주십시오.");
		}
		JsonNode feature;
		String warning = null;
		try {
			long revisionId = this.knowledgeClient.latestRevision(projectId);
			Map<String, String> params = new LinkedHashMap<String, String>();
			// 서버는 쉼표로 나눈 값을 목록으로 받는다(주소와 파일 경로에는 쉼표가 없다)
			params.put("entry", String.join(",", entryList));
			feature = this.knowledgeClient.get("/api/revisions/" + revisionId + "/feature", params);
			if (isScmRevision(sourceRevision)) {
				warning = revisionWarning(this.labelOf(projectId, revisionId), sourceRevision.trim());
			}
		} catch (KnowledgeCallException e) {
			return fail(result, e.getMessage());
		}

		List<String> notFound = new ArrayList<String>();
		JsonNode entryRows = feature.path("entries");
		for (int i = 0; i < entryRows.size(); i++) {
			if ("NOT_FOUND".equals(entryRows.get(i).path("kind").asText())) {
				notFound.add(entryRows.get(i).path("entry").asText());
			}
		}
		if (feature.path("endpoints").size() == 0 && feature.path("screens").size() == 0) {
			result.put("notFound", notFound);
			return fail(result, "시작점에 맞는 화면이나 주소를 분석 결과에서 찾지 못했습니다: " + String.join(", ", entryList));
		}

		List<Piece> pieces = piecesOf(feature);
		int limit = maxChars == null || maxChars.intValue() < 1000 ? DEFAULT_MAX_CHARS : maxChars.intValue();
		String markdown = render(feature, pieces, new File(sourceRoot.trim()), limit, notFound);
		if (warning != null) {
			markdown = "> 주의: " + warning + "\n\n" + markdown;
		}
		try {
			File out = new File(outputFile.trim());
			if (out.getParentFile() != null) {
				out.getParentFile().mkdirs();
			}
			Files.write(out.toPath(), markdown.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return fail(result, "결과 파일을 쓰지 못했습니다: " + e.getMessage());
		}

		int included = 0;
		List<String> omitted = new ArrayList<String>();
		for (int i = 0; i < pieces.size(); i++) {
			if (pieces.get(i).included) {
				included++;
			} else {
				omitted.add(pieces.get(i).title + " (" + pieces.get(i).omitted + ")");
			}
		}
		result.put("success", Boolean.TRUE);
		result.put("file", outputFile.trim());
		result.put("chars", Integer.valueOf(markdown.length()));
		result.put("screens", Integer.valueOf(feature.path("screens").size()));
		result.put("endpoints", Integer.valueOf(feature.path("endpoints").size()));
		result.put("included", Integer.valueOf(included));
		result.put("omitted", omitted);
		result.put("notFound", notFound);
		result.put("message", "본뜰 기능의 코드 " + included + "조각을 " + outputFile.trim() + "에 적었습니다"
			+ (omitted.isEmpty() ? "." : ". 한도나 파일 문제로 위치만 적은 것 " + omitted.size() + "개.")
			+ (notFound.isEmpty() ? "" : " 찾지 못한 시작점: " + String.join(", ", notFound))
			+ (warning == null ? "" : " 주의: " + warning));
		if (warning != null) {
			result.put("warning", warning);
		}
		return result;
	}

	/* ======================= 리비전 맞춰 보기 ======================= */

	private String labelOf(String projectId, long revisionId) throws KnowledgeCallException {
		JsonNode revisions = this.knowledgeClient.get("/api/projects/" + projectId.trim() + "/revisions", null);
		for (int i = 0; i < revisions.size(); i++) {
			if (revisions.get(i).path("revisionId").asLong() == revisionId) {
				return revisions.get(i).path("revisionLabel").asText("");
			}
		}
		return "";
	}

	/** 형상관리 리비전인지 봅니다. 빈 값과 빌드 시각(yyyyMMdd-HHmmss)은 견줄 수 없어 아닙니다. */
	static boolean isScmRevision(String revision) {
		return revision != null && !revision.trim().isEmpty() && !revision.trim().matches("\\d{8}-\\d{6}");
	}

	/** 분석 리비전 이름이 소스 리비전(커밋 해시는 앞부분만 같아도 됨)과 맞지 않으면 경고 글, 맞으면 null. */
	static String revisionWarning(String label, String sourceRevision) {
		boolean hash = label.length() >= 7 && sourceRevision.length() >= 7;
		if (label.equals(sourceRevision) || (hash && (label.startsWith(sourceRevision) || sourceRevision.startsWith(label)))) {
			return null;
		}
		return "분석한 리비전(" + label + ")이 소스 리비전(" + sourceRevision + ")과 같다고 확인되지 않습니다. 그 사이 소스가 바뀌었으면 "
			+ "아래 코드의 줄 범위가 어긋날 수 있으니 원본을 읽어 확인하십시오.";
	}

	/* ======================= 조각 만들기 ======================= */

	/** 서버가 돌려준 목록을 코드 조각으로. 우선순위가 작을수록 먼저 넣는다 */
	static List<Piece> piecesOf(JsonNode feature) {
		List<Piece> pieces = new ArrayList<Piece>();
		int order = 0;
		JsonNode screens = feature.path("screens");
		for (int i = 0; i < screens.size(); i++) {
			JsonNode screen = screens.get(i);
			int hop = screen.path("hop").asInt();
			String path = screen.path("path").asText();
			String reason = screen.path("reason").asText();
			pieces.add(new Piece(screenPriority(hop), order++, "화면", path + " (" + reason + ")", languageOf(path), path, 1, -1));
			JsonNode scripts = screen.path("scripts");
			for (int s = 0; s < scripts.size(); s++) {
				String script = scripts.get(s).asText();
				pieces.add(new Piece(screenPriority(hop), order++, "화면", script + " (" + path + "가 넣는 스크립트)", languageOf(script), script, 1, -1));
			}
		}
		JsonNode methods = feature.path("methods");
		for (int i = 0; i < methods.size(); i++) {
			JsonNode method = methods.get(i);
			int depth = method.path("depth").asInt();
			String owner = method.path("owner").asText();
			String simple = owner.substring(owner.lastIndexOf('.') + 1);
			String path = method.path("path").asText();
			int start = method.path("lineStart").asInt();
			int end = method.path("lineEnd").asInt();
			pieces.add(new Piece(depth <= 1 ? 3 : (depth == 2 ? 4 : 6), order++, "메소드",
				simple + "#" + method.path("signature").asText() + " — " + path + ":" + start + "-" + end + " (" + method.path("layer").asText("") + ")",
				languageOf(path), path, start, end));
		}
		JsonNode statements = feature.path("statements");
		for (int i = 0; i < statements.size(); i++) {
			JsonNode statement = statements.get(i);
			String path = statement.path("path").asText();
			int start = statement.path("lineStart").asInt();
			int end = statement.path("lineEnd").asInt();
			String tables = statement.path("tables").asText("");
			pieces.add(new Piece(5, order++, "SQL",
				statement.path("statement").asText() + " — " + path + ":" + start + "-" + end + (tables.isEmpty() ? "" : " (" + tables + ")"),
				languageOf(path), path, start, end));
		}
		return pieces;
	}

	/** 시작 화면 1, 그 화면이 연 화면(팝업) 2, 더 먼 화면 6 */
	private static int screenPriority(int hop) {
		return hop == 0 ? 1 : (hop == 1 ? 2 : 6);
	}

	/* ======================= 문서 ======================= */

	static String render(JsonNode feature, List<Piece> pieces, File sourceRoot, int limit, List<String> notFound) {
		StringBuilder head = new StringBuilder();
		head.append("# 본뜰 기능의 코드\n\n");
		head.append("dstone-knowledge의 분석 결과에서 관계(화면 → 주소 → 메소드 → SQL, 메소드 → 돌려주는 화면)를 따라 한 기능의 것만 모았다. ")
			.append("코드는 원본을 고치지 않고 그대로 옮겼다. 분석이 잇지 못한 호출(문자열로 만든 주소, 리플렉션) 너머는 들어 있지 않다.\n\n");
		head.append("## 시작점\n\n");
		JsonNode entries = feature.path("entries");
		for (int i = 0; i < entries.size(); i++) {
			JsonNode entry = entries.get(i);
			String kind = entry.path("kind").asText();
			head.append("- `").append(entry.path("entry").asText()).append("` → ")
				.append("NOT_FOUND".equals(kind) ? "분석 결과에 없음" : ("SCREEN".equals(kind) ? "화면 " : "주소 ") + entry.path("matched").asText())
				.append('\n');
		}
		head.append("\n## 주소와 처리 메소드\n\n| 주소 | 처리 메소드 | 요청하는 화면 |\n|---|---|---|\n");
		JsonNode endpoints = feature.path("endpoints");
		for (int i = 0; i < endpoints.size(); i++) {
			JsonNode endpoint = endpoints.get(i);
			head.append("| ").append(joinText(endpoint.path("paths"), "<br>", "(주소 없음)")).append(" | `")
				.append(shortHandler(endpoint.path("handler").asText())).append("` | ")
				.append(joinText(endpoint.path("requestedBy"), "<br>", "")).append(" |\n");
		}
		StringBuilder tail = new StringBuilder();
		JsonNode shared = feature.path("shared");
		JsonNode models = feature.path("models");
		if (shared.size() > 0 || models.size() > 0) {
			tail.append("\n## 따라가지 않은 것\n\n");
			for (int i = 0; i < shared.size(); i++) {
				JsonNode row = shared.get(i);
				tail.append("- 공통 메소드 `").append(row.path("owner").asText()).append('#').append(row.path("signature").asText())
					.append("` — ").append(row.path("path").asText()).append(':').append(row.path("lineStart").asText()).append('\n');
			}
			for (int i = 0; i < models.size(); i++) {
				tail.append("- 쓰는 VO 같은 타입 `").append(models.get(i).asText()).append("`\n");
			}
		}
		List<String> notes = new ArrayList<String>();
		JsonNode serverNotes = feature.path("notes");
		for (int i = 0; i < serverNotes.size(); i++) {
			notes.add(serverNotes.get(i).asText());
		}

		// 코드를 읽어 두고, 우선순위대로 한도 안에서 넣는다.
		// 못 넣은 조각도 제목과 위치 한 줄은 남으므로 그 몫을 먼저 잡아 둔다(문서 전체가 한도를 넘지 않게)
		int used = head.length() + tail.length() + 2000;
		for (int i = 0; i < pieces.size(); i++) {
			used += placeholderSize(pieces.get(i), sourceRoot);
		}
		List<Piece> byPriority = new ArrayList<Piece>(pieces);
		Collections.sort(byPriority, new Comparator<Piece>() {
			@Override
			public int compare(Piece a, Piece b) {
				return a.priority != b.priority ? a.priority - b.priority : a.order - b.order;
			}
		});
		for (int i = 0; i < byPriority.size(); i++) {
			Piece piece = byPriority.get(i);
			String body = readPiece(sourceRoot, piece);
			if (body == null) {
				continue;
			}
			// 코드를 넣으면 위치 줄 대신 코드가 들어가므로, 잡아 둔 위치 줄 몫은 돌려받는다
			int size = body.length() + piece.title.length() + 30 - placeholderSize(piece, sourceRoot);
			if (used + size > limit) {
				piece.omitted = "글자 한도로 위치만 적음";
				continue;
			}
			piece.body = body;
			piece.included = true;
			used += size;
		}

		StringBuilder sb = new StringBuilder(head);
		String[] sections = new String[] { "화면", "메소드", "SQL" };
		String[] headings = new String[] { "## 화면 코드", "## 메소드 코드(처리 메소드부터 부르는 순서로)", "## SQL" };
		for (int s = 0; s < sections.length; s++) {
			boolean any = false;
			List<Piece> left = new ArrayList<Piece>();
			for (int i = 0; i < pieces.size(); i++) {
				Piece piece = pieces.get(i);
				if (!sections[s].equals(piece.section)) {
					continue;
				}
				if (!any) {
					sb.append('\n').append(headings[s]).append("\n\n");
					any = true;
				}
				if (!piece.included) {
					left.add(piece);
					continue;
				}
				sb.append("### ").append(piece.title).append("\n\n");
				sb.append("```").append(piece.language).append('\n').append(piece.body);
				if (!piece.body.endsWith("\n")) {
					sb.append('\n');
				}
				sb.append("```\n\n");
			}
			// 못 넣은 것은 한 줄씩 위치만(경로는 sourceRoot 기준)
			if (!left.isEmpty()) {
				sb.append("위치만 적은 것(").append(sourceRoot.getPath().replace('\\', '/')).append(" 기준, 필요하면 readFileLines로 읽는다):\n");
				for (int i = 0; i < left.size(); i++) {
					sb.append("- ").append(left.get(i).title);
					if (!left.get(i).title.contains(left.get(i).relativePath)) {
						sb.append(" — ").append(left.get(i).relativePath);
					}
					sb.append(" (").append(left.get(i).omitted).append(")\n");
				}
				sb.append('\n');
			}
		}
		sb.append(tail);
		if (feature.path("truncated").asBoolean() || !notes.isEmpty() || !notFound.isEmpty()) {
			sb.append("\n## 알림\n\n");
			for (int i = 0; i < notes.size(); i++) {
				sb.append("- ").append(notes.get(i)).append('\n');
			}
			if (!notFound.isEmpty()) {
				sb.append("- 찾지 못한 시작점: ").append(String.join(", ", notFound)).append('\n');
			}
		}
		return sb.toString();
	}

	/** 못 넣은 조각이 남기는 제목과 위치 줄의 크기(넉넉히) */
	private static int placeholderSize(Piece piece, File sourceRoot) {
		return piece.title.length() + piece.relativePath.length() + 40;
	}

	/**
	 * <pre>
	 * 조각의 코드를 원본에서 읽습니다. 화면(lineEnd = -1)은 통째로(너무 길면 앞부분만), 메소드와 SQL은 그 줄 범위만.
	 * 파일이 없으면 null을 돌려주고 omitted에 이유를 적습니다(분석한 뒤 지워졌거나 sourceRoot가 다른 경우).
	 * </pre>
	 */
	static String readPiece(File sourceRoot, Piece piece) {
		File file = new File(sourceRoot, piece.relativePath);
		if (!file.isFile()) {
			piece.omitted = "원본에 파일이 없음";
			return null;
		}
		String text = FileTool.readText(file);
		if (text == null) {
			piece.omitted = "글자 파일이 아님";
			return null;
		}
		String[] lines = text.replace("\r\n", "\n").split("\n", -1);
		int start = Math.max(1, piece.lineStart);
		int end = piece.lineEnd < 0 ? lines.length : Math.min(lines.length, piece.lineEnd);
		boolean cut = false;
		if (piece.lineEnd < 0 && end - start + 1 > MAX_SCREEN_LINES) {
			end = start + MAX_SCREEN_LINES - 1;
			cut = true;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = start; i <= end && i <= lines.length; i++) {
			sb.append(lines[i - 1]).append('\n');
		}
		if (cut) {
			sb.append("... (").append(MAX_SCREEN_LINES).append("줄까지만 옮김. 나머지는 ").append(MAX_SCREEN_LINES + 1).append("줄부터 readFileLines로 읽는다)\n");
		}
		return sb.toString();
	}

	/* ======================= 작은 도구 ======================= */

	/** 한 줄에 하나(쉼표로 이어도 됨). 목록 기호, 백틱, 따옴표, 빈 줄과 제목 줄은 뺀다 */
	static List<String> splitEntries(String entries) {
		List<String> list = new ArrayList<String>();
		if (entries == null) {
			return list;
		}
		String[] lines = entries.replace("\r\n", "\n").split("[\n,]");
		for (int i = 0; i < lines.length; i++) {
			String value = lines[i].trim();
			if (value.startsWith("#")) {
				continue;
			}
			while (value.startsWith("-") || value.startsWith("*") || value.startsWith("`") || value.startsWith("\"") || value.startsWith("'")) {
				value = value.substring(1).trim();
			}
			// "주소 — 설명"처럼 뒤에 설명을 붙였으면 앞부분만
			int space = value.indexOf(' ');
			if (space > 0) {
				value = value.substring(0, space);
			}
			while (value.endsWith("`") || value.endsWith("\"") || value.endsWith("'")) {
				value = value.substring(0, value.length() - 1);
			}
			// 경로나 주소 모양(/ 또는 확장자가 있는 것)만. 절 안의 설명 문장("(목록·등록 …)")은 시작점이 아니다
			boolean pathLike = value.contains("/") || value.matches("[^\\s()]+\\.[A-Za-z0-9]+");
			if (!value.isEmpty() && pathLike && !value.startsWith("(") && !list.contains(value)) {
				list.add(value);
			}
		}
		return list;
	}

	private static String languageOf(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		int dot = lower.lastIndexOf('.');
		String ext = dot < 0 ? "" : lower.substring(dot + 1);
		if ("xfdl".equals(ext)) {
			return "xml";
		}
		if ("xjs".equals(ext) || "js".equals(ext) || "ts".equals(ext) || "jsx".equals(ext) || "tsx".equals(ext)) {
			return "javascript";
		}
		return ext;
	}

	private static String shortHandler(String handler) {
		int hash = handler.indexOf('#');
		String owner = hash < 0 ? handler : handler.substring(0, hash);
		return owner.substring(owner.lastIndexOf('.') + 1) + (hash < 0 ? "" : handler.substring(hash));
	}

	private static String joinText(JsonNode array, String separator, String empty) {
		if (array == null || array.size() == 0) {
			return empty;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.size(); i++) {
			if (i > 0) {
				sb.append(separator);
			}
			sb.append(array.get(i).asText());
		}
		return sb.toString();
	}

	private static Map<String, Object> fail(Map<String, Object> result, String message) {
		result.put("success", Boolean.FALSE);
		result.put("message", message.startsWith("실패") ? message : "실패: " + message);
		return result;
	}

}
