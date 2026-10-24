package net.dstone.knowledge.common.util;

import java.util.Locale;

/**
 * <pre>
 * 테이블 이름을 한 가지 모양으로 맞춥니다. SQL에서 뽑은 이름과 화면·API로 받은 이름이 모두 이 규칙을 씁니다.
 *
 *   "DPHJSDBA.JZ_EMP_INFO"  → "JZ_EMP_INFO"   (스키마를 뗀다. 스키마를 붙여 쓰든 안 붙여 쓰든 같은 테이블이다)
 *   "\"dbo\".[tb_order]"     → "TB_ORDER"      (따옴표·대괄호를 벗기고 대문자로)
 *
 * DDL(DdlReader), 사전 파일(DictionaryFileReader)도 같은 이유로 스키마를 떼고 읽습니다.
 * </pre>
 */
public final class TableName {

	private TableName() {
	}

	/**
	 * @param written SQL이나 요청에 적힌 이름(스키마.테이블 또는 테이블)
	 * @return 스키마를 뗀 대문자 테이블 이름. 비어 있으면 빈 글자
	 */
	public static String bare(String written) {
		if (written == null) {
			return "";
		}
		String name = written.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim();
		int dot = name.lastIndexOf('.');
		if (dot >= 0) {
			name = name.substring(dot + 1).trim();
		}
		return name.toUpperCase(Locale.ROOT);
	}

}
