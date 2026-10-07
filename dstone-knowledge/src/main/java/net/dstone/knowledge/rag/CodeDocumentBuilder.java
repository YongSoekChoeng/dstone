package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.knowledge.common.util.HashText;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.rag.model.ChunkRow;
import net.dstone.knowledge.rag.model.DocumentRow;

/**
 * <pre>
 * 파일 하나의 분석 결과와 소스로 RAG 문서와 청크를 만듭니다. DB도 파일도 건드리지 않고 받은 재료만으로 글을 짓습니다.
 *
 * 만드는 문서:
 *   FILE    파일 하나에 한 건. 경로, 패키지, 선언된 타입, import.
 *   TYPE    타입 하나에 한 건. 계층, 상속/구현/주입, 진입점, 필드와 메소드 목록. 소스는 넣지 않는다.
 *   METHOD  메소드 하나에 한 건. 분석으로 알아낸 사실 + 소스.
 *
 * 메소드 문서에 소스만 넣지 않고 분석 결과를 글로 풀어 같이 넣는 이유:
 * "주문 취소는 어디서 처리하나" 같은 질문은 소스 글자와 닮지 않았습니다. 하지만 그 메소드가 어느 주소(POST /order/cancel.do)를
 * 처리하는지, 어느 계층인지, 무엇을 부르고 누가 부르는지를 글로 적어 두면 질문과 가까워집니다.
 * 이 사실들은 소스를 아무리 읽어도 그 메소드 안에는 적혀 있지 않은 것들입니다(다른 파일을 봐야 알 수 있습니다).
 *
 * 단순 접근자(get/set/is로 시작하는 한두 줄짜리 메소드)는 메소드 문서를 따로 만들지 않습니다.
 * VO가 많은 프로젝트는 메소드의 대부분이 이런 것이라, 다 만들면 임베딩 양만 몇 배로 늘고 검색 결과는 흐려집니다.
 * 타입 문서의 메소드 목록에는 들어갑니다.
 *
 * 파일 하나에 객체 하나를 만들어 쓰고 버립니다.
 * </pre>
 */
public class CodeDocumentBuilder {

	/** 메소드 문서에 적는 "호출하는 것"의 최대 수 */
	private static final int MAX_CALLEES = 15;

	/** 메소드 문서에 적는 "프로젝트 밖으로 나가는 호출"의 최대 수 */
	private static final int MAX_EXTERNAL_CALLEES = 8;

	/** 설명(주석)으로 가져오는 최대 글자 수 */
	private static final int MAX_DESCRIPTION = 500;

	/** 파일 문서에 적는 import의 최대 수 */
	private static final int MAX_IMPORTS = 40;

	private final String projectId;
	private final long revisionId;
	private final boolean skipAccessors;
	private final int chunkMaxChars;

	/**
	 * <pre>
	 * 문서를 만들 재료. 전부 "이 파일 하나"에 대한 조회 결과입니다(RagDao의 select...ByFile).
	 * </pre>
	 */
	public static class Material {
		public List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> methods = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> annotations = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> typeRelations = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> callees = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> callers = new ArrayList<Map<String, Object>>();
		public List<Map<String, Object>> endpoints = new ArrayList<Map<String, Object>>();
		/** 메소드가 실행하는 SQL과 여는 화면. 키: fromId, kind(SQL / VIEW), name, statementType, tables */
		public List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
	}

	/**
	 * <pre>
	 * 만든 결과
	 * </pre>
	 */
	public static class Result {
		public final List<DocumentRow> documents = new ArrayList<DocumentRow>();
		public final List<ChunkRow> chunks = new ArrayList<ChunkRow>();
	}

	/**
	 * @param skipAccessors 단순 접근자의 메소드 문서를 만들지 않을지
	 * @param chunkMaxChars 청크 하나에 담는 소스의 최대 글자 수
	 */
	public CodeDocumentBuilder(String projectId, long revisionId, boolean skipAccessors, int chunkMaxChars) {
		this.projectId = projectId;
		this.revisionId = revisionId;
		this.skipAccessors = skipAccessors;
		this.chunkMaxChars = chunkMaxChars;
	}

	/**
	 * @param file analysis_file 한 행. 키: fileId, path, module, packageName, languageLevel
	 * @param text 파일 내용(올바른 인코딩으로 읽은 글)
	 */
	public Result build(Map<String, Object> file, String text, Material material) {
		long fileId = ((Number) file.get("fileId")).longValue();
		String path = (String) file.get("path");
		String[] lines = text.split("\r?\n", -1);
		Result result = new Result();

		Map<String, Map<String, Object>> typesById = new HashMap<String, Map<String, Object>>();
		for (int i = 0; i < material.types.size(); i++) {
			typesById.put((String) material.types.get(i).get("symbolId"), material.types.get(i));
		}
		Map<String, List<String>> annotations = annotationsByTarget(material.annotations);
		Map<String, List<Map<String, Object>>> endpointsByMethod = groupBy(material.endpoints, "methodId");
		Map<String, List<Map<String, Object>>> calleesByMethod = groupBy(material.callees, "fromId");
		Map<String, List<Map<String, Object>>> callersByMethod = groupBy(material.callers, "toId");
		Map<String, List<Map<String, Object>>> linksByMethod = groupBy(material.links, "fromId");
		Map<String, List<Map<String, Object>>> relationsByType = groupBy(material.typeRelations, "fromId");
		Map<String, List<Map<String, Object>>> fieldsByType = groupBy(material.fields, "ownerSymbolId");
		Map<String, List<Map<String, Object>>> methodsByType = groupBy(material.methods, "ownerSymbolId");

		addFileDocument(result, fileId, path, file, lines, material.types);

		for (int i = 0; i < material.types.size(); i++) {
			Map<String, Object> type = material.types.get(i);
			// 익명 클래스와 만들어 넣은 타입(Lombok 빌더)은 타입 문서를 만들지 않는다. 설명할 선언이 소스에 없다.
			if ("ANONYMOUS".equals(type.get("kind")) || type.get("synthetic") != null) {
				continue;
			}
			String symbolId = (String) type.get("symbolId");
			addTypeDocument(result, fileId, path, lines, type, annotations.get(symbolId), relationsByType.get(symbolId)
					, fieldsByType.get(symbolId), methodsByType.get(symbolId), endpointsByMethod);
		}

		Map<String, List<Map<String, Object>>> fieldsByOwner = groupBy(material.fields, "ownerSymbolId");
		for (int i = 0; i < material.methods.size(); i++) {
			Map<String, Object> method = material.methods.get(i);
			if (Boolean.TRUE.equals(method.get("isSynthetic")) || (skipAccessors && isAccessor(method, fieldsByOwner.get((String) method.get("ownerSymbolId"))))) {
				continue;
			}
			String methodId = (String) method.get("methodId");
			addMethodDocument(result, fileId, path, lines, method, typesById.get((String) method.get("ownerSymbolId")), annotations.get(methodId)
					, endpointsByMethod.get(methodId), calleesByMethod.get(methodId), callersByMethod.get(methodId), linksByMethod.get(methodId));
		}
		return result;
	}


	/* ============================== 파일 문서 ============================== */

	private void addFileDocument(Result result, long fileId, String path, Map<String, Object> file, String[] lines, List<Map<String, Object>> types) {
		StringBuilder sb = new StringBuilder();
		sb.append("[파일] ").append(path).append('\n');
		line(sb, "패키지", (String) file.get("packageName"));
		line(sb, "모듈", (String) file.get("module"));
		sb.append("줄 수: ").append(lines.length).append('\n');

		List<String> declared = new ArrayList<String>();
		for (int i = 0; i < types.size(); i++) {
			Map<String, Object> type = types.get(i);
			if (!"ANONYMOUS".equals(type.get("kind")) && type.get("synthetic") == null) {
				declared.add(type.get("fqn") + " (" + kindLabel((String) type.get("kind")) + layerSuffix(type) + ")");
			}
		}
		line(sb, "선언된 타입", join(declared, ", "));

		List<String> imports = new ArrayList<String>();
		for (int i = 0; i < lines.length && imports.size() < MAX_IMPORTS; i++) {
			String trimmed = lines[i].trim();
			if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
				imports.add(trimmed.substring("import ".length(), trimmed.length() - 1).trim());
			}
		}
		line(sb, "import", join(imports, ", "));

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "FILE");
		metadata.put("path", path);
		// 경로는 길 수 있어서(1000자까지) 문서 ID에는 해시를 쓴다.
		String documentId = "FILE:" + HashText.sha256(projectId + "|" + path);
		addDocument(result, documentId, "FILE", "FILE", String.valueOf(fileId), path, path, metadata, fileId
				, splitByLines(sb.toString(), chunkMaxChars * 2), null, 1, lines.length);
	}


	/* ============================== 타입 문서 ============================== */

	private void addTypeDocument(Result result, long fileId, String path, String[] lines, Map<String, Object> type, List<String> annotations
			, List<Map<String, Object>> relations, List<Map<String, Object>> fields, List<Map<String, Object>> methods
			, Map<String, List<Map<String, Object>>> endpointsByMethod) {
		String fqn = (String) type.get("fqn");
		Integer lineStart = intOf(type.get("lineStart"));
		Integer lineEnd = intOf(type.get("lineEnd"));

		StringBuilder sb = new StringBuilder();
		sb.append("[타입] ").append(fqn).append(" (").append(kindLabel((String) type.get("kind"))).append(")\n");
		line(sb, "계층", layerText(type));
		sb.append("파일: ").append(path).append(rangeText(lineStart, lineEnd)).append('\n');
		line(sb, "설명", descriptionAbove(lines, lineStart));

		List<String> extended = new ArrayList<String>();
		List<String> implemented = new ArrayList<String>();
		List<String> injected = new ArrayList<String>();
		if (relations != null) {
			for (int i = 0; i < relations.size(); i++) {
				String target = (String) relations.get(i).get("target");
				String relationType = (String) relations.get(i).get("relationType");
				if ("EXTENDS".equals(relationType)) {
					extended.add(target);
				} else if ("IMPLEMENTS".equals(relationType)) {
					implemented.add(target);
				} else {
					injected.add(target);
				}
			}
		}
		line(sb, "상속", join(extended, ", "));
		line(sb, "구현", join(implemented, ", "));
		line(sb, "주입받는 것", join(injected, ", "));
		line(sb, "애노테이션", annotations == null ? null : join(annotations, " "));

		List<String> endpointTexts = new ArrayList<String>();
		List<String> methodTexts = new ArrayList<String>();
		if (methods != null) {
			for (int i = 0; i < methods.size(); i++) {
				Map<String, Object> method = methods.get(i);
				String text = String.valueOf(method.get("signature"));
				if (method.get("returnType") != null) {
					text += " → " + method.get("returnType");
				}
				if (Boolean.TRUE.equals(method.get("isSynthetic"))) {
					// 소스에 없는 멤버라는 것을 적어 둔다(Lombok이 만든 것, 기본 생성자 등).
					text += " (자동 생성)";
				}
				methodTexts.add(text);
				List<Map<String, Object>> endpoints = endpointsByMethod.get((String) method.get("methodId"));
				if (endpoints != null) {
					for (int e = 0; e < endpoints.size() && endpointTexts.size() < 30; e++) {
						endpointTexts.add(endpointText(endpoints.get(e)));
					}
				}
			}
		}
		line(sb, "진입점", join(endpointTexts, ", "));

		List<String> fieldTexts = new ArrayList<String>();
		if (fields != null) {
			for (int i = 0; i < fields.size(); i++) {
				fieldTexts.add(fields.get(i).get("name") + ": " + fields.get(i).get("type"));
			}
		}
		line(sb, "필드", join(fieldTexts, ", "));
		line(sb, "메소드", join(methodTexts, "; "));

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "TYPE");
		metadata.put("fqn", fqn);
		metadata.put("layer", type.get("layer"));
		addDocument(result, "TYPE:" + type.get("symbolId"), "TYPE", "TYPE", (String) type.get("symbolId"), fqn, path, metadata, fileId
				, splitByLines(sb.toString(), chunkMaxChars * 2), null, lineStart, lineEnd);
	}


	/* ============================== 메소드 문서 ============================== */

	private void addMethodDocument(Result result, long fileId, String path, String[] lines, Map<String, Object> method, Map<String, Object> ownerType
			, List<String> annotations, List<Map<String, Object>> endpoints, List<Map<String, Object>> callees, List<Map<String, Object>> callers
			, List<Map<String, Object>> links) {
		String ownerFqn = ownerType == null ? "?" : (String) ownerType.get("fqn");
		String title = ownerFqn + "#" + method.get("signature");
		Integer lineStart = intOf(method.get("lineStart"));
		Integer lineEnd = intOf(method.get("lineEnd"));

		// 머리말: 분석으로 알아낸 사실들
		StringBuilder head = new StringBuilder();
		head.append(Boolean.TRUE.equals(method.get("isConstructor")) ? "[생성자] " : "[메소드] ").append(title).append('\n');
		if (method.get("returnType") != null) {
			head.append("반환: ").append(method.get("returnType")).append('\n');
		}
		line(head, "계층", ownerType == null ? null : layerText(ownerType));
		head.append("파일: ").append(path).append(rangeText(lineStart, lineEnd)).append('\n');
		line(head, "설명", descriptionAbove(lines, lineStart));

		List<String> endpointTexts = new ArrayList<String>();
		if (endpoints != null) {
			for (int i = 0; i < endpoints.size(); i++) {
				endpointTexts.add(endpointText(endpoints.get(i)));
			}
		}
		line(head, "진입점", join(endpointTexts, ", "));
		line(head, "애노테이션", annotations == null ? null : join(annotations, " "));

		List<String> inProject = new ArrayList<String>();
		List<String> possible = new ArrayList<String>();
		List<String> external = new ArrayList<String>();
		if (callees != null) {
			for (int i = 0; i < callees.size(); i++) {
				Map<String, Object> callee = callees.get(i);
				String target = (String) callee.get("target");
				if (target == null) {
					continue;
				}
				if ("CALLS_POSSIBLE_IMPLEMENTATION".equals(callee.get("relationType"))) {
					addLimited(possible, shortName(target), MAX_CALLEES);
				} else if (Boolean.TRUE.equals(callee.get("inProject"))) {
					// 짐작으로 이은 것(LOW)은 틀릴 수 있다는 표시를 붙인다.
					addLimited(inProject, shortName(target) + ("LOW".equals(callee.get("confidence")) ? "(추정)" : ""), MAX_CALLEES);
				} else {
					addLimited(external, target, MAX_EXTERNAL_CALLEES);
				}
			}
		}
		line(head, "호출하는 것", join(inProject, ", "));
		line(head, "실행될 수 있는 구현", join(possible, ", "));
		line(head, "밖으로 나가는 호출", join(external, ", "));

		// 실행하는 SQL과 여는 화면. "이 기능은 어느 테이블을 고치나", "이 화면은 어디서 여나" 같은 질문에 걸리게 한다.
		List<String> sqls = new ArrayList<String>();
		List<String> views = new ArrayList<String>();
		if (links != null) {
			for (int i = 0; i < links.size(); i++) {
				Map<String, Object> link = links.get(i);
				if ("SQL".equals(link.get("kind"))) {
					String tables = (String) link.get("tables");
					addLimited(sqls, link.get("name") + " [" + link.get("statementType") + (tables == null ? "" : ", 테이블 " + tables) + "]", MAX_CALLEES);
				} else {
					addLimited(views, (String) link.get("name"), MAX_CALLEES);
				}
			}
		}
		line(head, "실행하는 SQL", join(sqls, ", "));
		line(head, "여는 화면", join(views, ", "));

		if (callers != null && !callers.isEmpty()) {
			List<String> callerTexts = new ArrayList<String>();
			for (int i = 0; i < callers.size(); i++) {
				callerTexts.add(shortName((String) callers.get(i).get("caller")));
			}
			long total = ((Number) callers.get(0).get("total")).longValue();
			String text = join(callerTexts, ", ");
			if (total > callerTexts.size()) {
				text += " 외 " + (total - callerTexts.size()) + "곳";
			}
			line(head, "호출받는 곳", text);
		}

		// 소스: 길면 줄 단위로 나눈다. 첫 조각에만 머리말 전체를 붙이고, 뒤 조각에는 "누구의 몇 번째 조각인지"만 붙인다.
		List<int[]> parts = sourceParts(lines, lineStart, lineEnd);
		List<String> contents = new ArrayList<String>();
		List<int[]> ranges = new ArrayList<int[]>();
		if (parts.isEmpty()) {
			contents.add(head.toString());
			ranges.add(null);
		}
		for (int p = 0; p < parts.size(); p++) {
			StringBuilder sb = new StringBuilder();
			if (p == 0) {
				sb.append(head);
			} else {
				sb.append("[메소드] ").append(title).append(" (이어서 ").append(p + 1).append('/').append(parts.size()).append(")\n");
				sb.append("파일: ").append(path).append(rangeText(Integer.valueOf(parts.get(p)[0]), Integer.valueOf(parts.get(p)[1]))).append('\n');
			}
			sb.append("소스:\n");
			for (int l = parts.get(p)[0]; l <= parts.get(p)[1]; l++) {
				sb.append(lines[l - 1]).append('\n');
			}
			contents.add(sb.toString());
			ranges.add(parts.get(p));
		}

		Map<String, Object> metadata = new LinkedHashMap<String, Object>();
		metadata.put("docType", "METHOD");
		metadata.put("fqn", ownerFqn);
		metadata.put("signature", method.get("signature"));
		metadata.put("layer", ownerType == null ? null : ownerType.get("layer"));
		if (!endpointTexts.isEmpty()) {
			metadata.put("endpoints", endpointTexts);
		}
		addDocument(result, "METHOD:" + method.get("methodId"), "METHOD", "METHOD", (String) method.get("methodId"), title, path, metadata, fileId
				, contents, ranges, lineStart, lineEnd);
	}

	/**
	 * <pre>
	 * 필드 값을 그대로 주고받는 메소드인지 봅니다. 세 가지가 다 맞아야 합니다.
	 *   - 이름이 get / set / is로 시작한다
	 *   - 세 줄을 넘지 않는다
	 *   - 같은 타입에 그 이름의 필드가 있다 (getName → name)
	 *
	 * 필드까지 보는 이유: 이름만 보면 DBUtil.getConnection()처럼 짧지만 일을 하는 메소드까지 빠진다.
	 * 그런 메소드는 "DB 커넥션을 얻는 코드"로 찾는 대상이라 문서가 있어야 한다.
	 * </pre>
	 */
	private boolean isAccessor(Map<String, Object> method, List<Map<String, Object>> ownerFields) {
		String name = (String) method.get("name");
		Integer lineStart = intOf(method.get("lineStart"));
		Integer lineEnd = intOf(method.get("lineEnd"));
		if (name == null || lineStart == null || lineEnd == null || ownerFields == null || Boolean.TRUE.equals(method.get("isConstructor"))) {
			return false;
		}
		String property;
		if ((name.startsWith("get") || name.startsWith("set")) && name.length() > 3) {
			property = name.substring(3);
		} else if (name.startsWith("is") && name.length() > 2) {
			property = name.substring(2);
		} else {
			return false;
		}
		if (((Number) method.get("paramCount")).intValue() > 1 || lineEnd.intValue() - lineStart.intValue() > 2) {
			return false;
		}
		for (int i = 0; i < ownerFields.size(); i++) {
			String field = (String) ownerFields.get(i).get("name");
			// isActive()의 필드는 active일 수도, isActive일 수도 있다. 필드 이름의 대소문자 규칙(mb_id, URL)은 프로젝트마다 달라서 가리지 않는다.
			if (field != null && (field.equalsIgnoreCase(property) || field.equalsIgnoreCase(name))) {
				return true;
			}
		}
		return false;
	}

	/** 소스를 청크 크기에 맞춰 줄 단위로 나눕니다. [시작 줄, 끝 줄]의 목록(1부터 세는 줄 번호)입니다. */
	private List<int[]> sourceParts(String[] lines, Integer lineStart, Integer lineEnd) {
		List<int[]> parts = new ArrayList<int[]>();
		if (lineStart == null || lineEnd == null || lineStart.intValue() < 1) {
			return parts;
		}
		int end = Math.min(lineEnd.intValue(), lines.length);
		int partStart = lineStart.intValue();
		int size = 0;
		for (int l = lineStart.intValue(); l <= end; l++) {
			int length = lines[l - 1].length() + 1;
			if (size > 0 && size + length > chunkMaxChars) {
				parts.add(new int[] { partStart, l - 1 });
				partStart = l;
				size = 0;
			}
			size += length;
		}
		if (partStart <= end) {
			parts.add(new int[] { partStart, end });
		}
		return parts;
	}


	/* ============================== 문서와 청크 만들기 ============================== */

	/**
	 * @param contents 청크로 들어갈 글들(순서대로)
	 * @param ranges 청크마다의 줄 범위. null이면 모든 청크에 문서의 줄 범위를 쓴다.
	 */
	void addDocument(Result result, String documentId, String docType, String refKind, String refId, String title, String path
			, Map<String, Object> metadata, long fileId, List<String> contents, List<int[]> ranges, Integer lineStart, Integer lineEnd) {
		String metadataJson = JsonText.of(metadata);
		StringBuilder whole = new StringBuilder();
		for (int i = 0; i < contents.size(); i++) {
			String content = contents.get(i);
			whole.append(content);

			ChunkRow chunk = new ChunkRow();
			chunk.setDocumentId(documentId);
			chunk.setRevisionId(revisionId);
			chunk.setChunkNo(i);
			chunk.setChunkType(docType);
			chunk.setContent(content);
			chunk.setContentHash(HashText.sha256(content));
			chunk.setCharCount(content.length());
			chunk.setMetadataJson(metadataJson);
			chunk.setFileId(fileId);
			int[] range = ranges == null ? null : ranges.get(i);
			chunk.setLineStart(range == null ? lineStart : Integer.valueOf(range[0]));
			chunk.setLineEnd(range == null ? lineEnd : Integer.valueOf(range[1]));
			result.chunks.add(chunk);
		}

		DocumentRow document = new DocumentRow();
		document.setDocumentId(documentId);
		document.setProjectId(projectId);
		document.setRevisionId(revisionId);
		document.setDocType(docType);
		document.setRefKind(refKind);
		document.setRefId(refId);
		document.setTitle(title.length() > 1000 ? title.substring(0, 1000) : title);
		document.setSourcePath(path);
		document.setMetadataJson(metadataJson);
		document.setContentHash(HashText.sha256(whole.toString()));
		result.documents.add(document);
	}

	/** 긴 글을 줄 경계에서 나눕니다. */
	List<String> splitByLines(String text, int maxChars) {
		List<String> parts = new ArrayList<String>();
		if (text.length() <= maxChars) {
			parts.add(text);
			return parts;
		}
		String firstLine = text.substring(0, text.indexOf('\n') + 1);
		StringBuilder current = new StringBuilder();
		String[] lines = text.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			// 한 줄이 너무 길면(메소드가 수백 개인 타입의 메소드 목록 등) 그 줄을 글자 수로 자른다.
			while (line.length() > maxChars) {
				if (current.length() > 0) {
					parts.add(current.toString());
					current = new StringBuilder(firstLine.trim()).append(" (이어서)\n");
				}
				parts.add(current + line.substring(0, maxChars) + "\n");
				current = new StringBuilder(firstLine.trim()).append(" (이어서)\n");
				line = line.substring(maxChars);
			}
			if (current.length() + line.length() + 1 > maxChars && current.length() > 0) {
				parts.add(current.toString());
				// 뒤 조각에도 "누구의 조각인지"를 알 수 있게 첫 줄을 다시 붙인다.
				current = new StringBuilder(firstLine.trim()).append(" (이어서)\n");
			}
			current.append(line).append('\n');
		}
		if (current.length() > 0) {
			parts.add(current.toString());
		}
		return parts;
	}


	/* ============================== 글 조각을 만드는 도구들 ============================== */

	/**
	 * <pre>
	 * 선언 바로 위의 주석에서 설명을 가져옵니다. 없으면 null입니다.
	 * 파싱하지 않고 줄을 거슬러 올라가며 읽습니다: 빈 줄을 건너뛰고, 주석 블록이나 // 줄들이 나오면 그것이 설명입니다.
	 * javadoc의 @param 같은 태그 줄과 HTML 태그는 뺍니다.
	 * </pre>
	 */
	String descriptionAbove(String[] lines, Integer lineStart) {
		if (lineStart == null || lineStart.intValue() < 2 || lineStart.intValue() > lines.length) {
			return null;
		}
		int i = lineStart.intValue() - 2;
		while (i >= 0 && lines[i].trim().length() == 0) {
			i--;
		}
		if (i < 0) {
			return null;
		}
		List<String> collected = new ArrayList<String>();
		String last = lines[i].trim();
		if (last.endsWith("*/")) {
			// 블록 주석: 여는 줄이 나올 때까지 올라간다.
			while (i >= 0) {
				String current = lines[i].trim();
				collected.add(0, current);
				if (current.startsWith("/*")) {
					break;
				}
				i--;
			}
		} else if (last.startsWith("//")) {
			while (i >= 0 && lines[i].trim().startsWith("//")) {
				collected.add(0, lines[i].trim());
				i--;
			}
		} else {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (int c = 0; c < collected.size(); c++) {
			String text = collected.get(c).replace("/**", "").replace("/*", "").replace("*/", "").trim();
			while (text.startsWith("*") || text.startsWith("/")) {
				text = text.substring(1).trim();
			}
			if (text.startsWith("@")) {
				// @param, @return ... 여기부터는 설명이 아니다.
				break;
			}
			text = text.replaceAll("<[^>]+>", " ").trim();
			if (text.length() > 0) {
				sb.append(sb.length() > 0 ? " " : "").append(text);
			}
		}
		String description = sb.toString().replaceAll("\\s+", " ").trim();
		if (description.length() == 0) {
			return null;
		}
		return description.length() > MAX_DESCRIPTION ? description.substring(0, MAX_DESCRIPTION) : description;
	}

	/** 대상 ID → ["@Service", "@RequestMapping(/order/list.do)"] */
	private Map<String, List<String>> annotationsByTarget(List<Map<String, Object>> annotations) {
		Map<String, List<String>> grouped = new HashMap<String, List<String>>();
		for (int i = 0; i < annotations.size(); i++) {
			Map<String, Object> annotation = annotations.get(i);
			String targetId = (String) annotation.get("targetId");
			List<String> list = grouped.get(targetId);
			if (list == null) {
				list = new ArrayList<String>();
				grouped.put(targetId, list);
			}
			String attributes = (String) annotation.get("attributes");
			String text = "@" + annotation.get("name");
			if (attributes != null && !"{}".equals(attributes) && attributes.length() <= 200) {
				text += attributes;
			}
			list.add(text);
		}
		return grouped;
	}

	private Map<String, List<Map<String, Object>>> groupBy(List<Map<String, Object>> rows, String key) {
		Map<String, List<Map<String, Object>>> grouped = new HashMap<String, List<Map<String, Object>>>();
		for (int i = 0; i < rows.size(); i++) {
			String value = (String) rows.get(i).get(key);
			List<Map<String, Object>> list = grouped.get(value);
			if (list == null) {
				list = new ArrayList<Map<String, Object>>();
				grouped.put(value, list);
			}
			list.add(rows.get(i));
		}
		return grouped;
	}

	/** "POST /order/cancel.do", "거래 ID JCST0200M01S", "main()", "스케줄 0 0 3 * * *" */
	private String endpointText(Map<String, Object> endpoint) {
		String type = (String) endpoint.get("endpointType");
		String path = (String) endpoint.get("path");
		if ("HTTP".equals(type) || "SERVLET".equals(type)) {
			String httpMethod = (String) endpoint.get("httpMethod");
			return ("ALL".equals(httpMethod) || httpMethod == null ? "" : httpMethod + " ") + path;
		}
		if ("TRANSACTION".equals(type)) {
			return "거래 ID " + (path == null ? "" : path);
		}
		if ("MAIN".equals(type)) {
			return "프로그램 시작점(main)";
		}
		if ("THREAD".equals(type)) {
			return "스레드 실행";
		}
		if ("SCHEDULED".equals(type)) {
			return "스케줄 " + (path == null ? "" : path);
		}
		if ("LISTENER".equals(type)) {
			return "리스너 " + (path == null ? "" : path);
		}
		return type + (path == null ? "" : " " + path);
	}

	/** com.legacy.order.dao.OrderDAO#delete(String) → OrderDAO#delete(String). 글이 패키지 이름으로 가득 차지 않게 줄인다. */
	private String shortName(String name) {
		if (name == null) {
			return "";
		}
		int hash = name.indexOf('#');
		String owner = hash < 0 ? name : name.substring(0, hash);
		int dot = owner.lastIndexOf('.');
		return (dot < 0 ? owner : owner.substring(dot + 1)) + (hash < 0 ? "" : name.substring(hash));
	}

	private String kindLabel(String kind) {
		if ("INTERFACE".equals(kind)) {
			return "인터페이스";
		}
		if ("ENUM".equals(kind)) {
			return "enum";
		}
		if ("RECORD".equals(kind)) {
			return "record";
		}
		if ("ANNOTATION".equals(kind)) {
			return "애노테이션";
		}
		return "클래스";
	}

	private String layerText(Map<String, Object> type) {
		if (type.get("layer") == null) {
			return null;
		}
		// 이름으로 짐작한 계층은 틀릴 수 있다는 표시를 붙인다.
		return type.get("layer") + ("LOW".equals(type.get("layerConfidence")) ? " (이름으로 추정)" : "");
	}

	private String layerSuffix(Map<String, Object> type) {
		return type.get("layer") == null ? "" : ", " + type.get("layer");
	}

	private String rangeText(Integer lineStart, Integer lineEnd) {
		if (lineStart == null) {
			return "";
		}
		return " (줄 " + lineStart + (lineEnd == null || lineEnd.equals(lineStart) ? "" : "-" + lineEnd) + ")";
	}

	/** 값이 있을 때만 "이름: 값" 한 줄을 붙입니다. */
	private void line(StringBuilder sb, String label, String value) {
		if (value != null && value.length() > 0) {
			sb.append(label).append(": ").append(value).append('\n');
		}
	}

	private void addLimited(List<String> list, String value, int max) {
		if (list.size() < max && !list.contains(value)) {
			list.add(value);
		}
	}

	private String join(List<String> values, String separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(separator);
			}
			sb.append(values.get(i));
		}
		return sb.toString();
	}

	private Integer intOf(Object value) {
		return value == null ? null : Integer.valueOf(((Number) value).intValue());
	}

}
