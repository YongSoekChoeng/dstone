package net.dstone.ai.api.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 정의 파일 글자에서 Workflow, Agent, Skill의 이름만 바꿔 주는 순수 함수 모음입니다.
 *
 * YAML로 읽었다가 다시 쓰면 주석이 사라지므로, 그 이름이 적힌 자리만 글자 그대로 바꿉니다.
 * 주석과 프롬프트 본문은 건드리지 않습니다.
 * 여기서 놓친 참조(예: 한 줄 { } 모양의 step)가 있어도 저장 전 전체 검사에서 "없는 Agent"로 걸리므로 틀린 파일이 쓰이지 않습니다.
 * </pre>
 */
final class DefinitionRenamer {

	/** 이름에 쓰는 글자입니다. 이름이 다른 이름의 일부일 때(a-agent와 a-agent-2) 잘못 바꾸지 않으려고 앞뒤를 봅니다. */
	private static final String NAME_CHARS = "A-Za-z0-9_\\-";

	private DefinitionRenamer() {
	}

	/**
	 * 파일 맨 왼쪽에 적힌 자기 이름(id: 또는 name:)을 바꿉니다. 그런 줄이 없으면 글자를 그대로 돌려줍니다.
	 *
	 * @param text    파일 내용
	 * @param key     id 또는 name
	 * @param oldName 옛 이름
	 * @param newName 새 이름
	 */
	static String renameOwnName(String text, String key, String oldName, String newName) {
		Pattern pattern = Pattern.compile("^(" + key + ":[ \\t]*[\"']?)" + Pattern.quote(oldName) + "(?=[\"']?[ \\t]*(?:#.*)?$)", Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return text;
		}
		return text.substring(0, matcher.start()) + matcher.group(1) + newName + text.substring(matcher.end());
	}

	/**
	 * Workflow 파일에서 step의 agent 값을 바꿉니다.
	 *
	 * @param text    Workflow 파일 내용
	 * @param oldName 옛 Agent id
	 * @param newName 새 Agent id
	 */
	static String renameStepAgent(String text, String oldName, String newName) {
		Pattern pattern = Pattern.compile("^([ \\t]*(?:-[ \\t]+)?agent:[ \\t]*[\"']?)" + Pattern.quote(oldName) + "(?=[\"']?[ \\t]*(?:#.*)?$)", Pattern.MULTILINE);
		return pattern.matcher(text).replaceAll("$1" + Matcher.quoteReplacement(newName));
	}

	/**
	 * <pre>
	 * Agent 파일의 머리말(--- 사이)에서 블록 하나(subAgents 또는 skills) 안의 이름을 바꿉니다.
	 * 블록은 맨 왼쪽에 적힌 key: 줄부터 다음 맨 왼쪽 키 앞까지입니다. 주석 부분과 키 이름은 바꾸지 않습니다.
	 * </pre>
	 *
	 * @param text    Agent 파일 내용
	 * @param block   subAgents 또는 skills
	 * @param oldName 옛 이름
	 * @param newName 새 이름
	 */
	static String renameInFrontBlock(String text, String block, String oldName, String newName) {
		String[] lines = text.split("(?<=\n)", -1);
		StringBuilder result = new StringBuilder(text.length());
		boolean inFront = false;
		boolean frontDone = false;
		boolean inBlock = false;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String bare = line.trim();
			if (!frontDone && "---".equals(bare)) {
				frontDone = inFront;
				inFront = !inFront;
				inBlock = false;
			} else if (inFront) {
				boolean startsAtLeft = !line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && line.charAt(0) != '#' && line.charAt(0) != '-';
				if (startsAtLeft) {
					inBlock = line.startsWith(block + ":");
				}
				if (inBlock) {
					line = renameOutsideComment(line, oldName, newName);
				}
			}
			result.append(line);
		}
		return result.toString();
	}

	/**
	 * 이름이 글자 어딘가에 낱말로 들어 있는지 봅니다. 주석과 본문까지 다 봅니다(직접 고칠 곳을 알려 주는 데 씁니다).
	 *
	 * @param text 파일 내용
	 * @param name 찾을 이름
	 */
	static boolean mentions(String text, String name) {
		return wordPattern(name).matcher(text).find();
	}

	/** 한 줄의 주석 앞부분에서만 이름을 바꿉니다. 키 이름(뒤에 : 가 붙는 낱말)은 바꾸지 않습니다. */
	private static String renameOutsideComment(String line, String oldName, String newName) {
		int comment = line.startsWith("#") ? 0 : line.indexOf(" #");
		String code = comment < 0 ? line : line.substring(0, comment);
		String rest = comment < 0 ? "" : line.substring(comment);
		Pattern pattern = Pattern.compile(wordPattern(oldName).pattern() + "(?![ \\t]*:)");
		return pattern.matcher(code).replaceAll(Matcher.quoteReplacement(newName)) + rest;
	}

	/** 앞뒤에 이름 글자가 붙어 있지 않은 낱말 하나를 찾는 패턴입니다. */
	private static Pattern wordPattern(String name) {
		return Pattern.compile("(?<![" + NAME_CHARS + "])" + Pattern.quote(name) + "(?![" + NAME_CHARS + "])");
	}

}
