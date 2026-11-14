package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <pre>
 * 올린 문서의 블록(DocumentTextExtractor)을 검색 단위(청크)로 묶습니다. 문서의 장/절을 살립니다.
 *
 * 0) 다듬기: 검색에 방해만 되는 줄을 뺍니다.
 *    - 목차 줄: "1.2 설정 ........ 32", "1.2 설정(탭)32"처럼 끝이 쪽 번호인 줄. 목차는 모든 제목을 담고 있어서 무엇을 찾든 걸린다.
 *    - PDF의 목차 쪽: 줄의 절반 이상이 북마크 제목이나 목차 줄인 쪽(제목과 쪽 번호가 다른 줄로 나뉘어 위 규칙에 안 걸리는 목차).
 *    - PDF의 머리말/꼬리말: 쪽의 처음과 끝 두 줄 안에 되풀이되는 줄(숫자만 다른 것 포함).
 *      5쪽 이상에 나오고, 처음 나온 쪽부터 마지막 쪽 사이의 절반 이상에 나오면 머리말로 본다(장마다 바뀌는 "장 제목 | 쪽"도 잡힌다).
 * 1) 절 나누기: 블록을 "같은 제목 아래의 글" 단위(절)로 모읍니다.
 *    - Word, HTML: 제목 블록(h1~h6)이 나올 때마다 새 절. 제목 경로는 깊이로 계산한다(h2는 앞 h1의 아래).
 *    - PDF: 제목 표시가 없어서 북마크를 씁니다. 북마크가 시작하는 쪽에서 북마크 제목과 같은 줄을 찾아 거기서 새 절로 넘어갑니다.
 *      그 쪽에서 제목 줄을 못 찾으면 쪽 처음부터 그 북마크의 절로 봅니다.
 *    - 제목도 북마크도 없으면 문서 전체가 제목 없는 절 하나입니다.
 * 2) 묶기: 절의 문단을 차례로 이어 붙이다가 최대 글자 수를 넘기 전에 끊습니다.
 *    - 짧은 절은 다음 절과 한 청크에 같이 들어갑니다. 다만 맨 위 제목이 다른 절(다른 장)끼리는 섞지 않습니다.
 *    - 한 청크에 절이 여럿이면, 공통 제목 경로는 청크의 제목 경로가 되고 그 아래 제목은 본문 안에 한 줄로 남깁니다.
 *    - 긴 절은 여러 청크로 나뉘고, 다음 청크는 앞 청크의 마지막 문단으로 시작합니다(짧을 때만). 경계에 걸친 내용을 양쪽에서 찾게 하려는 것입니다.
 *    - 문단 하나가 최대 글자 수보다 길면 TextChunker로 더 자릅니다. 표의 행은 이어 붙여 한 문단처럼 다룹니다.
 *    - PDF는 줄마다 문단으로 나오는 일이 많아서, 쪽이 있는 문단끼리는 빈 줄 없이 줄바꿈 하나로 잇습니다.
 *
 * DB나 다른 클래스를 보지 않는 순수한 계산이라 단위 테스트로 확인합니다.
 * </pre>
 */
public final class DocumentChunker {

	/** 제목 경로를 이을 때 쓰는 글 */
	public static final String PATH_SEPARATOR = " > ";

	/** 청크 하나 */
	public static class Chunk {
		/** 제목 경로. 예: 2. 엔진 설치 > 2.2 설정. 제목이 없는 문서면 빈 글 */
		public final String headingPath;
		/** 본문(머리말 없이) */
		public final String text;
		/** 이 청크가 걸친 첫 쪽과 끝 쪽(PDF). 모르면 null */
		public final Integer pageStart;
		public final Integer pageEnd;

		Chunk(String headingPath, String text, Integer pageStart, Integer pageEnd) {
			this.headingPath = headingPath;
			this.text = text;
			this.pageStart = pageStart;
			this.pageEnd = pageEnd;
		}

		@Override
		public String toString() {
			return "[" + headingPath + "]" + (pageStart == null ? "" : "@p" + pageStart + "-" + pageEnd) + " " + text;
		}
	}

	/** 같은 제목 아래의 글 */
	private static class Section {
		final List<String> path;
		final List<Piece> pieces = new ArrayList<Piece>();
		/** 마지막 문단이 표의 행을 모은 것인지 */
		boolean lastIsRows;

		Section(List<String> path) {
			this.path = path;
		}
	}

	/** 목차 줄: 점선이나 탭 뒤에 쪽 번호(또는 Word가 쪽 번호 자리에 남긴 #)로 끝난다 */
	private static final Pattern TOC_LINE = Pattern.compile("^(.*?)((\\s*\\.){4,}\\s*(\\d{1,3}(,\\d{3})+|\\d{1,4})?|\\t\\s*(\\d{1,3}(,\\d{3})+|\\d{1,4}|#))\\s*$");

	/** 머리말/꼬리말로 볼 최소 쪽 수와, 처음 나온 쪽부터 마지막 쪽 사이에서 나와야 하는 비율 */
	private static final int RUNNING_MIN_PAGES = 5;
	private static final double RUNNING_MIN_RATIO = 0.5;

	/** 목차 쪽으로 볼 최소 줄 수와, 북마크 제목인 줄의 비율 */
	private static final int TOC_PAGE_MIN_LINES = 5;
	private static final double TOC_PAGE_MIN_RATIO = 0.5;

	/** 쪽의 처음과 끝에서 머리말/꼬리말을 찾아볼 줄 수 */
	private static final int RUNNING_EDGE_LINES = 2;

	/** 문단 하나(또는 표의 행 여럿) */
	private static class Piece {
		final String text;
		final Integer pageStart;
		final Integer pageEnd;

		Piece(String text, Integer pageStart, Integer pageEnd) {
			this.text = text;
			this.pageStart = pageStart;
			this.pageEnd = pageEnd;
		}
	}

	/** 만드는 중인 청크: 들어간 절과 그 절의 문단들 */
	private static class Builder {
		final List<Section> sections = new ArrayList<Section>();
		final List<List<Piece>> pieces = new ArrayList<List<Piece>>();
		int size;

		boolean isEmpty() {
			return sections.isEmpty();
		}

		boolean has(Section section) {
			return !sections.isEmpty() && sections.get(sections.size() - 1) == section;
		}

		/** 이 절을 같은 청크에 넣어도 되는지. 맨 위 제목이 같아야 한다(제목이 없는 절끼리는 된다) */
		boolean canJoin(Section section) {
			if (sections.isEmpty() || has(section)) {
				return true;
			}
			List<String> first = sections.get(0).path;
			if (first.isEmpty() && section.path.isEmpty()) {
				return true;
			}
			return !first.isEmpty() && !section.path.isEmpty() && first.get(0).equals(section.path.get(0));
		}

		void add(Section section, Piece piece) {
			if (!has(section)) {
				sections.add(section);
				pieces.add(new ArrayList<Piece>());
				if (size > 0) {
					size += 2;
				}
				if (!section.path.isEmpty()) {
					size += section.path.get(section.path.size() - 1).length() + 1;
				}
			} else {
				size += 2;
			}
			pieces.get(pieces.size() - 1).add(piece);
			size += piece.text.length();
		}

		Chunk build() {
			List<String> prefix = new ArrayList<String>(sections.get(0).path);
			for (int i = 1; i < sections.size(); i++) {
				prefix = commonPrefix(prefix, sections.get(i).path);
			}
			StringBuilder text = new StringBuilder();
			Integer pageStart = null;
			Integer pageEnd = null;
			for (int i = 0; i < sections.size(); i++) {
				List<String> path = sections.get(i).path;
				if (text.length() > 0) {
					text.append("\n\n");
				}
				if (path.size() > prefix.size()) {
					// 청크의 제목 경로보다 깊은 제목은 본문에 한 줄로 남긴다.
					text.append(join(path.subList(prefix.size(), path.size()))).append('\n');
				}
				List<Piece> list = pieces.get(i);
				for (int p = 0; p < list.size(); p++) {
					Piece piece = list.get(p);
					if (p > 0) {
						// PDF(쪽이 있는 문단)는 줄마다 문단으로 나오는 일이 많아서 줄바꿈 하나로 잇는다.
						text.append(piece.pageStart != null && list.get(p - 1).pageStart != null ? "\n" : "\n\n");
					}
					text.append(piece.text);
					if (piece.pageStart != null) {
						pageStart = pageStart == null ? piece.pageStart : Integer.valueOf(Math.min(pageStart.intValue(), piece.pageStart.intValue()));
						pageEnd = pageEnd == null ? piece.pageEnd : Integer.valueOf(Math.max(pageEnd.intValue(), piece.pageEnd.intValue()));
					}
				}
			}
			return new Chunk(join(prefix), text.toString().trim(), pageStart, pageEnd);
		}
	}

	private DocumentChunker() {
	}

	/**
	 * <pre>
	 * 블록을 청크로 묶습니다.
	 * </pre>
	 *
	 * @param blocks 문서에서 뽑은 블록(차례대로)
	 * @param bookmarks PDF의 북마크. 없으면 빈 목록. 있으면 제목 블록 대신 이것으로 절을 나눈다
	 * @param maxChars 청크 본문의 최대 글자 수(제목 경로 머리말은 빼고)
	 * @param overlapChars 앞 청크의 마지막 문단을 다음 청크에 다시 넣을 때, 그 문단의 최대 글자 수. 0이면 겹치지 않는다
	 */
	public static List<Chunk> chunk(List<DocumentBlock> blocks, List<PdfOutline.Bookmark> bookmarks, int maxChars, int overlapChars) {
		List<DocumentBlock> cleaned = clean(blocks, bookmarks);
		List<Section> sections = bookmarks == null || bookmarks.isEmpty() ? sectionsByHeading(cleaned, maxChars) : sectionsByBookmark(cleaned, bookmarks, maxChars);
		return pack(sections, maxChars, overlapChars);
	}

	/* ---------- 0) 다듬기 ---------- */

	/** 목차 줄, PDF 목차 쪽, PDF 머리말/꼬리말을 뺀 블록. 다 빠진 블록은 버린다 */
	static List<DocumentBlock> clean(List<DocumentBlock> blocks, List<PdfOutline.Bookmark> bookmarks) {
		Set<String> running = runningLines(blocks);
		Set<String> titles = new HashSet<String>();
		if (bookmarks != null) {
			for (int i = 0; i < bookmarks.size(); i++) {
				titles.add(normalize(bookmarks.get(i).title()));
			}
		}
		List<DocumentBlock> result = new ArrayList<DocumentBlock>();
		int i = 0;
		while (i < blocks.size()) {
			// 같은 쪽의 블록을 모은다(쪽이 없는 파일은 블록 하나씩).
			Integer page = blocks.get(i).page;
			int end = i + 1;
			if (page != null) {
				while (end < blocks.size() && page.equals(blocks.get(end).page)) {
					end++;
				}
			}
			List<DocumentBlock> pageBlocks = blocks.subList(i, end);
			i = end;
			int total = 0;
			int titleLines = 0;
			for (int b = 0; b < pageBlocks.size(); b++) {
				String[] lines = pageBlocks.get(b).text.split("\n");
				total += lines.length;
				for (int l = 0; l < lines.length; l++) {
					// 북마크 제목인 줄과 목차 줄(점선 + 쪽 번호)을 같이 센다. 목차는 제목과 점선이 다른 줄로 나뉘기도 한다.
					if (titles.contains(normalize(lines[l])) || TOC_LINE.matcher(lines[l]).matches()) {
						titleLines++;
					}
				}
			}
			if (page != null && total >= TOC_PAGE_MIN_LINES && titleLines >= total * TOC_PAGE_MIN_RATIO) {
				// 목차 쪽
				continue;
			}
			int lineNo = 0;
			for (int b = 0; b < pageBlocks.size(); b++) {
				DocumentBlock block = pageBlocks.get(b);
				if (DocumentBlock.HEADING.equals(block.kind)) {
					result.add(block);
					lineNo++;
					continue;
				}
				String[] lines = block.text.split("\n");
				StringBuilder kept = new StringBuilder();
				for (int l = 0; l < lines.length; l++, lineNo++) {
					String line = lines[l];
					if (TOC_LINE.matcher(line).matches()) {
						continue;
					}
					boolean edge = page != null && (lineNo < RUNNING_EDGE_LINES || lineNo >= total - RUNNING_EDGE_LINES);
					if (edge && running.contains(runningKey(line))) {
						continue;
					}
					if (kept.length() > 0) {
						kept.append('\n');
					}
					kept.append(line);
				}
				String text = kept.toString().trim();
				if (text.length() > 0) {
					result.add(text.equals(block.text) ? block : new DocumentBlock(block.kind, block.level, text, block.page));
				}
			}
		}
		return result;
	}

	/** PDF에서 쪽의 처음과 끝 두 줄 안에 되풀이되는 줄(숫자는 같은 것으로 본다). 기준은 RUNNING_MIN_PAGES, RUNNING_MIN_RATIO */
	private static Set<String> runningLines(List<DocumentBlock> blocks) {
		Map<Integer, List<String>> linesByPage = new LinkedHashMap<Integer, List<String>>();
		for (int i = 0; i < blocks.size(); i++) {
			DocumentBlock block = blocks.get(i);
			if (block.page == null) {
				continue;
			}
			List<String> lines = linesByPage.get(block.page);
			if (lines == null) {
				lines = new ArrayList<String>();
				linesByPage.put(block.page, lines);
			}
			lines.addAll(Arrays.asList(block.text.split("\n")));
		}
		Set<String> running = new HashSet<String>();
		if (linesByPage.size() < RUNNING_MIN_PAGES) {
			return running;
		}
		// 줄마다 [나온 쪽 수, 처음 쪽, 마지막 쪽]
		Map<String, int[]> stats = new HashMap<String, int[]>();
		Iterator<Map.Entry<Integer, List<String>>> pages = linesByPage.entrySet().iterator();
		while (pages.hasNext()) {
			Map.Entry<Integer, List<String>> entry = pages.next();
			int pageNo = entry.getKey().intValue();
			List<String> lines = entry.getValue();
			Set<String> seen = new HashSet<String>();
			for (int l = 0; l < lines.size(); l++) {
				if (l < RUNNING_EDGE_LINES || l >= lines.size() - RUNNING_EDGE_LINES) {
					String key = runningKey(lines.get(l));
					if (key.length() > 0 && seen.add(key)) {
						int[] stat = stats.get(key);
						if (stat == null) {
							stats.put(key, new int[] { 1, pageNo, pageNo });
						} else {
							stat[0]++;
							stat[2] = pageNo;
						}
					}
				}
			}
		}
		Iterator<Map.Entry<String, int[]>> entries = stats.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<String, int[]> entry = entries.next();
			int[] stat = entry.getValue();
			int span = stat[2] - stat[1] + 1;
			if (stat[0] >= RUNNING_MIN_PAGES && stat[0] >= span * RUNNING_MIN_RATIO) {
				running.add(entry.getKey());
			}
		}
		return running;
	}

	/** 머리말/꼬리말을 비교하는 열쇠: 빈칸 없애고, 숫자는 모두 # 하나로 */
	private static String runningKey(String line) {
		return normalize(line).replaceAll("\\d+", "#");
	}

	/** 나누지 않고 글 그대로 청크 하나(JSONL의 한 줄처럼). 제목 경로와 쪽은 없다 */
	public static Chunk plain(String text) {
		return new Chunk("", text, null, null);
	}

	/* ---------- 1) 절 나누기 ---------- */

	/** 제목 블록으로 절을 나눈다(Word, HTML) */
	private static List<Section> sectionsByHeading(List<DocumentBlock> blocks, int maxChars) {
		List<Section> sections = new ArrayList<Section>();
		Section current = new Section(new ArrayList<String>());
		sections.add(current);
		for (int i = 0; i < blocks.size(); i++) {
			DocumentBlock block = blocks.get(i);
			if (DocumentBlock.HEADING.equals(block.kind)) {
				List<String> path = new ArrayList<String>(current.path.subList(0, Math.min(block.level - 1, current.path.size())));
				path.add(block.text);
				current = new Section(path);
				sections.add(current);
			} else {
				addBlock(current, block, maxChars);
			}
		}
		return sections;
	}

	/** PDF의 북마크로 절을 나눈다 */
	private static List<Section> sectionsByBookmark(List<DocumentBlock> blocks, List<PdfOutline.Bookmark> bookmarks, int maxChars) {
		Map<Integer, List<PdfOutline.Bookmark>> byPage = new HashMap<Integer, List<PdfOutline.Bookmark>>();
		for (int i = 0; i < bookmarks.size(); i++) {
			PdfOutline.Bookmark bookmark = bookmarks.get(i);
			List<PdfOutline.Bookmark> list = byPage.get(Integer.valueOf(bookmark.page));
			if (list == null) {
				list = new ArrayList<PdfOutline.Bookmark>();
				byPage.put(Integer.valueOf(bookmark.page), list);
			}
			list.add(bookmark);
		}

		List<Section> sections = new ArrayList<Section>();
		Section current = new Section(new ArrayList<String>());
		sections.add(current);
		int lastPage = 0;
		int i = 0;
		while (i < blocks.size()) {
			// 같은 쪽의 블록을 모은다.
			Integer page = blocks.get(i).page;
			int end = i;
			while (end < blocks.size() && same(blocks.get(end).page, page)) {
				end++;
			}
			List<DocumentBlock> pageBlocks = blocks.subList(i, end);
			i = end;
			int pageNo = page == null ? lastPage : page.intValue();

			// 글이 없어 건너뛴 쪽에서 시작한 북마크가 있으면 그 마지막 것으로 넘어간다.
			PdfOutline.Bookmark skipped = null;
			for (int p = lastPage + 1; p < pageNo; p++) {
				List<PdfOutline.Bookmark> list = byPage.get(Integer.valueOf(p));
				if (list != null) {
					skipped = list.get(list.size() - 1);
				}
			}
			if (skipped != null) {
				current = startSection(sections, skipped);
			}
			lastPage = Math.max(lastPage, pageNo);

			List<PdfOutline.Bookmark> pending = page == null ? null : byPage.get(page);
			if (pending == null || pending.isEmpty()) {
				for (int b = 0; b < pageBlocks.size(); b++) {
					addBlock(current, pageBlocks.get(b), maxChars);
				}
				continue;
			}

			// 이 쪽에서 북마크 제목과 같은 줄을 찾는다(북마크 순서대로, 앞으로만).
			List<int[]> matches = matchHeadings(pageBlocks, pending);
			int firstMatched = matches.isEmpty() ? -1 : matches.get(0)[2];
			if (firstMatched != 0) {
				// 제목 줄을 못 찾은 북마크는 쪽 처음부터 시작한 것으로 본다. 찾은 것보다 앞의 북마크 가운데 마지막 것.
				PdfOutline.Bookmark atStart = firstMatched < 0 ? pending.get(pending.size() - 1) : pending.get(firstMatched - 1);
				current = startSection(sections, atStart);
			}
			int m = 0;
			for (int b = 0; b < pageBlocks.size(); b++) {
				DocumentBlock block = pageBlocks.get(b);
				String[] lines = block.text.split("\n");
				StringBuilder buffer = new StringBuilder();
				for (int l = 0; l < lines.length; l++) {
					if (m < matches.size() && matches.get(m)[0] == b && matches.get(m)[1] == l) {
						flushLines(current, buffer, block, maxChars);
						current = startSection(sections, pending.get(matches.get(m)[2]));
						m++;
						// 제목 줄 자체는 제목 경로에 들어가므로 본문에 넣지 않는다.
						continue;
					}
					if (buffer.length() > 0) {
						buffer.append('\n');
					}
					buffer.append(lines[l]);
				}
				flushLines(current, buffer, block, maxChars);
			}
		}
		return sections;
	}

	/**
	 * <pre>
	 * 쪽의 줄 가운데 북마크 제목과 같은 줄을 찾습니다. 북마크 순서대로 앞으로만 찾습니다.
	 * 돌려주는 값: [블록 번호, 줄 번호, 북마크 번호]의 목록
	 * </pre>
	 */
	private static List<int[]> matchHeadings(List<DocumentBlock> pageBlocks, List<PdfOutline.Bookmark> pending) {
		List<int[]> matches = new ArrayList<int[]>();
		int next = 0;
		for (int b = 0; b < pageBlocks.size() && next < pending.size(); b++) {
			String[] lines = pageBlocks.get(b).text.split("\n");
			for (int l = 0; l < lines.length && next < pending.size(); l++) {
				String line = normalize(lines[l]);
				for (int k = next; k < pending.size(); k++) {
					if (isHeadingLine(line, normalize(pending.get(k).title()))) {
						matches.add(new int[] { b, l, k });
						next = k + 1;
						break;
					}
				}
			}
		}
		return matches;
	}

	/** 줄이 그 제목인지. 빈칸과 대소문자는 보지 않고, 뒤에 쪽 번호 같은 짧은 글이 붙은 것도 인정한다 */
	private static boolean isHeadingLine(String line, String title) {
		if (title.length() < 2 || line.length() < title.length()) {
			return false;
		}
		return line.equals(title) || (line.startsWith(title) && line.length() - title.length() <= 4);
	}

	private static String normalize(String text) {
		return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private static Section startSection(List<Section> sections, PdfOutline.Bookmark bookmark) {
		Section section = new Section(new ArrayList<String>(bookmark.path));
		sections.add(section);
		return section;
	}

	private static void flushLines(Section section, StringBuilder buffer, DocumentBlock block, int maxChars) {
		String text = buffer.toString().trim();
		buffer.setLength(0);
		if (text.length() > 0) {
			addBlock(section, new DocumentBlock(block.kind, block.level, text, block.page), maxChars);
		}
	}

	private static boolean same(Integer a, Integer b) {
		return a == null ? b == null : a.equals(b);
	}

	/** 절에 블록을 넣는다. 표의 행은 앞의 행에 이어 붙이고(최대 글자 수까지), 너무 긴 문단은 자른다 */
	private static void addBlock(Section section, DocumentBlock block, int maxChars) {
		if (DocumentBlock.ROW.equals(block.kind) && section.lastIsRows) {
			Piece last = section.pieces.get(section.pieces.size() - 1);
			if (last.text.length() + 1 + block.text.length() <= maxChars) {
				section.pieces.set(section.pieces.size() - 1, new Piece(last.text + "\n" + block.text, last.pageStart, block.page == null ? last.pageEnd : block.page));
				return;
			}
		}
		if (block.text.length() <= maxChars) {
			section.pieces.add(new Piece(block.text, block.page, block.page));
		} else {
			List<String> parts = TextChunker.split(block.text, maxChars, 0);
			for (int i = 0; i < parts.size(); i++) {
				section.pieces.add(new Piece(parts.get(i), block.page, block.page));
			}
		}
		section.lastIsRows = DocumentBlock.ROW.equals(block.kind);
	}

	/* ---------- 2) 묶기 ---------- */

	private static List<Chunk> pack(List<Section> sections, int maxChars, int overlapChars) {
		List<Chunk> chunks = new ArrayList<Chunk>();
		Builder current = new Builder();
		Piece lastPiece = null;
		Section lastSection = null;
		for (int s = 0; s < sections.size(); s++) {
			Section section = sections.get(s);
			for (int p = 0; p < section.pieces.size(); p++) {
				Piece piece = section.pieces.get(p);
				int cost = piece.text.length() + 2;
				if (!current.has(section) && !section.path.isEmpty()) {
					cost += section.path.get(section.path.size() - 1).length() + 1;
				}
				if (!current.isEmpty() && (current.size + cost > maxChars || !current.canJoin(section))) {
					chunks.add(current.build());
					current = new Builder();
					// 겹치기: 같은 절이 이어질 때, 앞 청크의 마지막 문단이 짧고 넣어도 이번 문단이 들어갈 자리가 남을 때만
					if (lastSection == section && lastPiece != null && lastPiece.text.length() <= overlapChars
						&& lastPiece.text.length() + 2 + piece.text.length() <= maxChars) {
						current.add(section, lastPiece);
					}
				}
				current.add(section, piece);
				lastPiece = piece;
				lastSection = section;
			}
		}
		if (!current.isEmpty()) {
			chunks.add(current.build());
		}
		return chunks;
	}

	private static List<String> commonPrefix(List<String> a, List<String> b) {
		int n = 0;
		while (n < a.size() && n < b.size() && a.get(n).equals(b.get(n))) {
			n++;
		}
		return new ArrayList<String>(a.subList(0, n));
	}

	private static String join(List<String> path) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < path.size(); i++) {
			if (i > 0) {
				sb.append(PATH_SEPARATOR);
			}
			sb.append(path.get(i));
		}
		return sb.toString();
	}

}
