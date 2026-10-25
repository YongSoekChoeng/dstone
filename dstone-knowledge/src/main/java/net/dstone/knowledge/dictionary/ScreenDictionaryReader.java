package net.dstone.knowledge.dictionary;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.KoreanName;

/**
 * <pre>
 * 프로젝트 담당자가 따로 정리해 둔 화면 목록 파일(CSV)에서 화면 한글명을 읽습니다.
 *
 * 화면 파일의 제목이나 메뉴 이름이 한글이 아닐 때(예: 일본어 메뉴) 화면 상세에 한글 화면명을 보여 주려고 씁니다(GraphService).
 * 있어도 되고 없어도 되는 기능이라, 설정이 없으면 아무 파일도 읽지 않습니다.
 *
 * 설정(conf/application.yml, 프로젝트 ID 마다):
 *   dstone.knowledge.dictionary.screen-file.{프로젝트ID}   CSV 파일 경로. 여러 개면 쉼표로 나누거나 YAML 목록으로 적는다.
 *                                                           상대 경로는 프로젝트의 소스 폴더(localPath) 기준이다.
 *
 * 파일 모양: 첫 줄이 머리글인 CSV(탭으로 나눈 파일도 된다). 머리글 이름으로 열을 찾는다(대소문자, 띄어쓰기, 끝의 괄호 설명은 보지 않는다).
 *   화면 한글명   화면한글명, 화면명, 한글명, 메뉴명, 프로그램명, SCREEN_NAME 중 앞에 있는 것. "화면명(한글)" 도 화면명으로 읽는다
 *   화면 파일     파일, 파일경로, 화면파일, 화면경로, 경로, SRC_PATH, PATH, FILE
 *   화면 ID       화면ID, 화면코드, 프로그램ID, PAGE_ID, SCREEN_ID
 * 한글명 열과, 파일 열이나 화면 ID 열 가운데 하나는 있어야 합니다.
 * 화면은 파일 경로로 먼저 찾고(한쪽 경로가 다른 쪽 경로로 끝나면 같은 화면), 없으면 화면 ID = 화면 파일 이름(확장자 뺀 것)으로 찾습니다.
 * 한글이 없는 한글명은 건너뜁니다. 같은 화면이 여러 번 나오면 먼저 나온 것을 씁니다.
 *
 * 분석 결과(DB)에 저장하지 않고, 화면 상세를 열 때 읽습니다. 파일은 고친 시각과 크기가 같으면 다시 읽지 않습니다.
 * </pre>
 */
@Component
public class ScreenDictionaryReader extends BaseObject {

	private static final String PREFIX = "dstone.knowledge.dictionary.screen-file.";

	private static final String[] NAME_HEADERS = { "화면한글명", "화면명", "한글명", "메뉴명", "프로그램명", "SCREEN_NAME" };
	private static final String[] PATH_HEADERS = { "파일", "파일경로", "화면파일", "화면경로", "경로", "SRC_PATH", "PATH", "FILE" };
	private static final String[] ID_HEADERS = { "화면ID", "화면코드", "프로그램ID", "PAGE_ID", "SCREEN_ID" };

	@Autowired
	private ConfigProperty configProperty;

	/** 파일 경로 → 읽어 둔 사전 */
	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

	/**
	 * <pre>
	 * 화면 사전 파일 하나에서 읽은 것
	 * </pre>
	 */
	public static class Dictionary {

		/** [화면 파일 경로(앞의 / 를 떼고 \ 는 / 로), 한글명, 적힌 곳]. 파일에 적힌 순서다 */
		final List<String[]> byPath = new ArrayList<String[]>();

		/** 화면 ID(대문자) → [한글명, 적힌 곳] */
		final Map<String, String[]> byId = new HashMap<String, String[]>();

		/** 머리글에서 열을 못 찾았을 때 그 까닭. 읽은 것이 있으면 null */
		String problem;
	}

	private static class Cached {
		final long modified;
		final long size;
		final Dictionary dictionary;

		Cached(long modified, long size, Dictionary dictionary) {
			this.modified = modified;
			this.size = size;
			this.dictionary = dictionary;
		}
	}

	/**
	 * <pre>
	 * 화면 하나의 한글명을 찾습니다. 설정이 없거나 사전에 없으면 null 입니다.
	 * </pre>
	 *
	 * @param projectId 분석 프로젝트 ID. 설정 키를 찾는 데 쓴다
	 * @param baseDir 상대 경로의 기준 폴더(프로젝트의 localPath). null 이면 현재 폴더
	 * @param screenPath 분석 결과의 화면 파일 경로. 예: ui/jc/JCIQ0700G.xml
	 * @return {한글명, 적힌 곳(파일이름:줄)}. 없으면 null
	 */
	public String[] find(String projectId, String baseDir, String screenPath) {
		List<String> paths = DictionaryFileReader.configuredFiles(configProperty, PREFIX + projectId);
		String path = normalize(screenPath);
		String id = idOf(path);
		String[] byId = null;
		for (int i = 0; i < paths.size(); i++) {
			Dictionary dictionary;
			try {
				dictionary = load(DictionaryFileReader.resolve(baseDir, paths.get(i)));
			} catch (Exception e) {
				warn("화면 사전 파일을 읽지 못했습니다. projectId=" + projectId + ", file=" + paths.get(i) + ", 까닭=" + ErrorText.summaryOf(e));
				continue;
			}
			if (dictionary.problem != null) {
				warn("화면 사전 파일을 쓸 수 없습니다. projectId=" + projectId + ", file=" + paths.get(i) + ", 까닭=" + dictionary.problem);
				continue;
			}
			String[] found = findByPath(dictionary, path);
			if (found != null) {
				return found;
			}
			if (byId == null) {
				byId = dictionary.byId.get(id);
			}
		}
		return byId;
	}

	private String[] findByPath(Dictionary dictionary, String path) {
		for (int i = 0; i < dictionary.byPath.size(); i++) {
			String[] entry = dictionary.byPath.get(i);
			if (samePath(entry[0], path)) {
				return new String[] { entry[1], entry[2] };
			}
		}
		return null;
	}

	/** 한쪽 경로가 다른 쪽 경로로 끝나면 같은 화면. was/ui/jc/A.xml 과 ui/jc/A.xml */
	static boolean samePath(String a, String b) {
		if (a.length() == 0 || b.length() == 0) {
			return false;
		}
		String x = a.toLowerCase(Locale.ROOT);
		String y = b.toLowerCase(Locale.ROOT);
		return x.equals(y) || x.endsWith("/" + y) || y.endsWith("/" + x);
	}

	private Dictionary load(File file) throws Exception {
		if (!file.isFile()) {
			throw new IllegalArgumentException("파일이 없습니다: " + file.getPath());
		}
		String key = file.getCanonicalPath();
		long modified = file.lastModified();
		long size = file.length();
		Cached cached = cache.get(key);
		if (cached != null && cached.modified == modified && cached.size == size) {
			return cached.dictionary;
		}
		Dictionary dictionary = parse(DictionaryFileReader.decode(Files.readAllBytes(file.toPath())), file.getName());
		cache.put(key, new Cached(modified, size, dictionary));
		return dictionary;
	}

	/**
	 * <pre>
	 * 화면 사전 파일의 글을 읽습니다.
	 * </pre>
	 * @param text 파일 내용
	 * @param fileName 한글명이 어디서 왔는지 적을 때 쓰는 파일 이름
	 */
	static Dictionary parse(String text, String fileName) {
		Dictionary dictionary = new Dictionary();
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		List<List<String>> rows = DictionaryFileReader.rowsOf(text, DictionaryFileReader.delimiterOf(text, fileName));
		if (rows.isEmpty()) {
			dictionary.problem = "내용이 없습니다";
			return dictionary;
		}
		List<String> header = new ArrayList<String>();
		for (int i = 0; i < rows.get(0).size(); i++) {
			header.add(DictionaryFileReader.headerKey(rows.get(0).get(i)));
		}
		int name = DictionaryFileReader.indexOf(header, NAME_HEADERS);
		int path = DictionaryFileReader.indexOf(header, PATH_HEADERS, name);
		int id = DictionaryFileReader.indexOf(header, ID_HEADERS, name, path);
		if (name < 0 || (path < 0 && id < 0)) {
			dictionary.problem = "머리글에서 화면명 열과 화면 파일(또는 화면 ID) 열을 찾지 못했습니다";
			return dictionary;
		}
		for (int r = 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			String koreanName = KoreanName.clean(DictionaryFileReader.cell(row, name));
			if (koreanName == null) {
				continue;
			}
			String where = fileName + ":" + (r + 1);
			String filePath = normalize(DictionaryFileReader.cell(row, path));
			if (filePath.length() > 0) {
				dictionary.byPath.add(new String[] { filePath, koreanName, where });
			}
			String screenId = DictionaryFileReader.cell(row, id);
			if (screenId != null && screenId.trim().length() > 0) {
				String key = screenId.trim().toUpperCase(Locale.ROOT);
				if (!dictionary.byId.containsKey(key)) {
					dictionary.byId.put(key, new String[] { koreanName, where });
				}
			}
		}
		return dictionary;
	}

	/** 경로 비교용: \ 는 /, 앞의 / 와 ./ 는 뗀다. null 이면 빈 글자 */
	static String normalize(String path) {
		if (path == null) {
			return "";
		}
		String text = path.trim().replace('\\', '/');
		while (text.startsWith("/") || text.startsWith("./")) {
			text = text.substring(text.indexOf('/') + 1);
		}
		return text;
	}

	/** 화면 ID 로 쓰는 파일 이름(확장자 뺀 것, 대문자). ui/jc/JCIQ0700G.xml → JCIQ0700G */
	static String idOf(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name).toUpperCase(Locale.ROOT);
	}
}
