package net.dstone.common.utils;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class StringUtil {

	/** 
	 * null 체크하는 메소드
	 * 
	 * @param input
	 *            - 체크할 스트링
	 * @param defaultVal
	 *            - 체크할 스트링이 null 일때 반환할 값
	 * @return String
	 */
	public static String nullCheck(Object input, String defaultVal) {
		if (input == null) {
			return defaultVal;
		} else {
			return input.toString();
		}
	}
 
	/**
	 * null 체크하는 메소드
	 * 
	 * @param input
	 *            - 체크할 스트링
	 * @param defaultVal
	 *            - 체크할 스트링이 null 일때 반환할 값
	 * @return String
	 */
	public static Object nullCheck(Object input, Object defaultVal) {
		if (input == null) {
			return defaultVal;
		} else {
			return input.toString();
		}
	}

	/**
	 * null 체크하는 메소드
	 * 
	 * @param input
	 *            - 체크할 스트링
	 * @param defaultVal
	 *            - 체크할 스트링이 null 일때 반환할 값
	 * @return String
	 */
	public static String nullCheck(String input, String defaultVal) {
		if (input == null || "".equals(input) || input.trim().length() == 0) {
			return defaultVal;
		} else {
			return input;
		}
	}
	
	public static String ltrim(String input) {
		StringBuffer outStr = new StringBuffer();
		char[] incharArr = null;
		char[] delchar = {' ', '\t'};
		boolean isTeBeDelChar = false;
		boolean isAppendStarted = false;
		try {
			incharArr = input.toCharArray();
			for(int i=0; i<incharArr.length; i++){
				if(isAppendStarted){
					outStr.append(incharArr[i]);
				}else{
					isTeBeDelChar = false;
					for(int k=0; k<delchar.length; k++){
						if(incharArr[i] == delchar[k]){
							isTeBeDelChar = true;
							break;
						}
					}
					if(!isTeBeDelChar){
						outStr.append(incharArr[i]);
						isAppendStarted = true;
					}
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return outStr.toString();
	}	
	
	public static String rtrim(String input) {
		StringBuffer outStr = new StringBuffer();
		char[] incharArr = null;
		char[] delchar = {' ', '\t'};
		boolean isTeBeDelChar = false;
		boolean isAppendStarted = false;
		try {
			incharArr = reverse(input).toCharArray();
			for(int i=0; i<incharArr.length; i++){
				if(isAppendStarted){
					outStr.append(incharArr[i]);
				}else{
					isTeBeDelChar = false;
					for(int k=0; k<delchar.length; k++){
						if(incharArr[i] == delchar[k]){
							isTeBeDelChar = true;
							break;
						}
					}
					if(!isTeBeDelChar){
						outStr.append(incharArr[i]);
						isAppendStarted = true;
					}
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return outStr.reverse().toString();
	}

	/**
	 * <code>replace</code> 설명:스트링치환 메소드.
	 * 
	 * @param str
	 *            치환할 스트링
	 * @param pattern
	 *            있으면 바꾸고자하는 스트링
	 * @param replace
	 *            바꿀 스트링
	 * @return String
	 */
	public static String replace(String str, String pattern, String replace) {

		if(str == null || "".equals(str)){return "";}
		
		int s = 0; // 찾기 시작할 위치
		int e = 0; // StringBuffer에 append 할 위치
		StringBuffer result = new StringBuffer(); // 잠시 문자열 담궈둘 놈

		while ((e = str.indexOf(pattern, s)) >= 0) {
			result.append(str.substring(s, e));
			result.append(replace);
			s = e + pattern.length();
		}
		result.append(str.substring(s));

		return result.toString();
	}
	
	/**
	 * <code>reverse</code> 설명:스트링역순치환 메소드.
	 * 
	 * @param str 치환할 스트링
	 * @return String
	 */
	public static String reverse(String str) {
		if(str == null || "".equals(str)){return "";}
		StringBuffer buff = new StringBuffer(str);
		return buff.reverse().toString();
	}

	/**
	 * <code>subByteString</code> 설명:바이트절삭 메소드.
	 * 
	 * @param str
	 *            절삭대상 스트링
	 * @param byteSize
	 *            대상길이(이 길이를 넘는 부분은 절삭)
	 * @return String
	 */
	public static String subByteString(String str, int byteSize) {
		int rSize = 0;
		int len = 0;
		if (str.getBytes().length > byteSize) {
			for (; rSize < str.length(); rSize++) {
				if (str.charAt(rSize) > 0x007F)
					len += 2;
				else
					len++;

				if (len > byteSize)
					break;
			}
			str = str.substring(0, rSize);
		}
		return str;
	}

	/**
	 * 오늘 날짜를 지정된 형식으로 반환.
	 * 
	 * @param format
	 *            날짜 형식
	 * @return 변환된 날짜 문자열
	 * @throws Exception
	 */
	public static final String getToDate(String format) throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(format);

		return sdf.format(new java.util.Date());
	}

	/**
	 * <code>byteStrTail</code> 설명:긴 스트링을 줄이는 메소드.<br>
	 * 
	 * @param str
	 *            원래스트링
	 * @param len
	 *            줄이고자하는 길이
	 * @param tail
	 *            len길이 이후에 붙여줄 스트링값
	 * @return String <br>
	 *         예) frame.common.Util.byteStrTail("가가가가가가가가", 10, ".....") ===>>
	 *         "가가가가가....." 반환
	 */
	public static String byteStrTail(String str, int i, String trail) {
		if (str == null)
			return "";
		String tmp = str;
		int slen = 0, blen = 0;
		char c;
		try {
			if (tmp.getBytes("MS949").length > i) {
				while (blen + 1 < i) {
					c = tmp.charAt(slen);
					blen++;
					slen++;
					if (c > 127)
						blen++; // 2-byte character..
				}
				tmp = tmp.substring(0, slen) + trail;
			}
		} catch (java.io.UnsupportedEncodingException e) {
		}
		return tmp;
	}

	public static String escapHTML(String html) {
		return org.apache.commons.lang.StringEscapeUtils.escapeHtml(html);
	}

	public static String unEscapHTML(String html) {
		return org.apache.commons.lang.StringEscapeUtils.unescapeHtml(html);
	}

	/**
	 * 리스트에서 제목부분 표현 메소드.
	 * 
	 * @param strText
	 * @param maxLen
	 * @return 변환된 날짜 문자열
	 * @throws Exception
	 */
	public static String textTail(String strText, int maxLen) {
		return byteStrTail(strText, maxLen, "...");
	}

	/**
	 * 날짜포맷 메소드.
	 * 
	 * @param param_format
	 *            포맷
	 * @param sDate
	 *            날짜
	 * @return 변환된 날짜 문자열
	 */
	public static String dateFormat(String param_format, String sDate) {
		if (sDate.length() != 8) {
			return "";
		} else {
			int year = Integer.parseInt(sDate.substring(0, 4));
			int month = Integer.parseInt(sDate.substring(4, 6));
			int date = Integer.parseInt(sDate.substring(6));
			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.set(java.util.Calendar.YEAR, year);
			cal.set(java.util.Calendar.MONTH, month - 1);
			cal.set(java.util.Calendar.DATE, date);
			java.util.Date day = cal.getTime();

			java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
					param_format);
			String strDay = format.format(day);
			return strDay;
		}
	}

	public static String filler(String input, int len, String filler) {
		StringBuffer Answer1 = new StringBuffer(input);
		StringBuffer Answer2 = new StringBuffer("");
		int intLen = Answer1.length();
		Answer1 = Answer1.reverse();

		if (input.length() > len) {
			return input;
		}
		for (int i = 0, j = 0; i < len; i++, j++) {
			if (j <= (intLen - 1)) {
				Answer2 = Answer2.append(Answer1.charAt(i));
			} else {
				Answer2 = Answer2.append(filler);
			}
		}
		return Answer2.reverse().toString();
	}

	public static String backFiller(String input, int len, String filler) {
		StringBuffer Answer1 = new StringBuffer(input);
		StringBuffer Answer2 = new StringBuffer("");
		int intLen = Answer1.length();

		if (input.length() > len) {
			return input;
		}
		for (int i = 0, j = 0; i < len; i++, j++) {
			if (j <= (intLen - 1)) {
				Answer2 = Answer2.append(Answer1.charAt(i));
			} else {
				Answer2 = Answer2.append(filler);
			}
		}
		return Answer2.toString();
	}

	public static String encodingConv(String str, String fromEnc, String toEnc) {
		String tmp = new String("");
		if (str == null || str.length() == 0)
			return "";

		try {
			tmp = new String(str.getBytes(fromEnc), toEnc);
		} catch (java.io.UnsupportedEncodingException e) {
		} catch (Exception ee) {
		}
		return tmp;
	}

	/**
	 * <code>toStrArray</code> 설명: 구분자를 포함한 스트링을 배열로 바꾸는 메소드
	 * 
	 * @param strInput 구분자를 포함한 전체스트링
	 * @param div 구분자
	 * @param trimYn 트림여부
	 * @return 스트링배열
	 */
	public static String[] toStrArray(String strInput, String div, boolean trimYn) {
		String[] sResults = null;
		String strOutput = "";
		String sTemp = "";
		int index = 0;

		strInput = replace(strInput, (div), (" " + div));
		java.util.StringTokenizer token = new java.util.StringTokenizer( strInput, div);
		sResults = new String[token.countTokens()];
		while (token.hasMoreElements()) {
			sTemp = (String) token.nextElement();
			if(trimYn){
				sResults[index] = sTemp.trim();
			}else{
				sResults[index] = sTemp;
			}
			index++;
		}
		return sResults;
	}
	/**
	 * <code>toStrArray</code> 설명: 구분자를 포함한 스트링을 배열로 바꾸는 메소드
	 * 
	 * @param strInput 구분자를 포함한 전체스트링
	 * @param div 구분자
	 * @return 스트링배열
	 */
	public static String[] toStrArray(String strInput, String div) {
		return toStrArray(strInput, div, true);
	}

	/**
	 * <code>moneyForamt</code> 설명:화폐형식표기 메소드.
	 * 
	 * @param input
	 *            화폐수치
	 * @return 컴마가 들어간 스트링
	 */
	public static String moneyForamt(int input) {
		String str;
		if (input > 0) {
			str = new Integer(input).toString();
			int dot_num = str.indexOf(".");
			if (dot_num > 0) {
				return moneyForamtter(str.substring(0, dot_num))
						+ str.substring(dot_num);
			} else {
				return moneyForamtter(str);
			}
		} else {
			str = "0";
			return str;
		}

	}

	/**
	 * <code>moneyForamt</code> 설명:화폐형식표기 메소드.
	 * 
	 * @param input
	 *            화폐수치
	 * @return 컴마가 들어간 스트링
	 */
	public static String moneyForamt(long input) {
		String str;
		if (input > (long) 0) {
			str = new Long(input).toString();
			int dot_num = str.indexOf(".");
			if (dot_num > 0) {
				return moneyForamtter(str.substring(0, dot_num))
						+ str.substring(dot_num);
			} else {
				return moneyForamtter(str);
			}
		} else {
			str = "0";
			return str;
		}

	}

	/**
	 * <code>moneyForamt</code> 설명:화폐형식표기 메소드.
	 * 
	 * @param input
	 *            화폐수치
	 * @return 컴마가 들어간 스트링
	 */
	public static String moneyForamt(String input) {
		String str;
		if (!"".equals(input)) {
			str = input;
			int dot_num = str.indexOf(".");
			if (dot_num > 0) {
				return moneyForamtter(str.substring(0, dot_num))
						+ str.substring(dot_num);
			} else {
				return moneyForamtter(str);
			}
		} else {
			str = "";
			return str;
		}

	}

	/**
	 * <code>moneyForamt</code> 설명:화폐형식표기 메소드.
	 * 
	 * @param input
	 *            화폐수치
	 * @return 컴마가 들어간 스트링
	 */
	private static String moneyForamtter(String str) {

		String str1 = new String("");
		String str2 = new String("");
		double m = 0.0;
		String output = new String(""), els = new String("");
		int length = 0;
		int pos = 0, i = 0, mod = 0, mod1 = 0, ismin = 0, point = -1;
		str.trim();
		if (str == null || str.length() == 0)
			return "";

		try {
			str1 = String.valueOf(Long.parseLong(str.trim()));
		} catch (NumberFormatException ne) {
			return "";
		}
		point = str1.indexOf('.');

		if (point >= 0) {
			els = str1.substring(point, str1.length());
			str1 = str1.substring(0, point);
		}

		if (str1.substring(0, 1).equals("-")) {
			ismin = 1;
			str1 = str1.substring(1, str1.length());
		} else
			ismin = 0;
		if (str1 == null)
			return "";
		if (str1.length() < 1)
			return "";
		length = str1.length();

		str2 = "";
		mod = length % 3;
		mod1 = length / 3;

		if (mod == 0) {
			mod1 -= 1;
			mod = 3;
		}
		str2 = str2 + str1.substring(0, mod);

		if (mod == 3) {
			for (i = 1; i <= mod1; i++) {
				str2 += ",";
				str2 += str1.substring(mod + (3 * (i - 1)), ((i + 1) * 3));
			}
		} else if (mod == 2) {
			for (i = 1; i <= mod1; i++) {
				str2 += ",";
				str2 += str1.substring(mod + (3 * (i - 1)), (i * 3) + 2);
			}
		} else {
			for (i = 1; i <= mod1; i++) {
				str2 += ",";
				str2 += str1.substring(mod + (3 * (i - 1)), (i * 3) + 1);
			}
		}
		if (ismin == 1)
			return "-" + str2 + els;
		else
			return str2 + els;
	}

	/**
	 * <code>moneyForamt</code> 설명:화폐형식표기 메소드.
	 * 
	 * @param input
	 *            화폐수치
	 * @return 컴마가 들어간 스트링
	 */
	public static String moneyForamt(java.math.BigDecimal input) {

		String str = input.toString();
		int dot_num = str.indexOf(".");
		if (dot_num > 0) {
			return moneyForamtter(str.substring(0, dot_num))
					+ str.substring(dot_num);
		} else {
			return moneyForamtter(str);
		}
	}

	public static String min(String data1, String data2) {
		String min = "";
		try {
			java.math.BigDecimal bdata1 = new java.math.BigDecimal(data1);
			java.math.BigDecimal bdata2 = new java.math.BigDecimal(data2);
			java.math.BigDecimal bmin = bdata1.min(bdata2);
			min = bmin.toString();
		} catch (Exception e) {
		}
		return min;
	}

	public static String max(String data1, String data2) {
		String max = "";
		try {
			java.math.BigDecimal bdata1 = new java.math.BigDecimal(data1);
			java.math.BigDecimal bdata2 = new java.math.BigDecimal(data2);
			java.math.BigDecimal bmax = bdata1.max(bdata2);
			max = bmax.toString();
		} catch (Exception e) {
		}
		return max;
	}

	/**
	 * null/공백 체크하는 메소드
	 * 
	 * @param input
	 *            체크할 객체
	 * @return boolean
	 */
	public static boolean isEmpty(Object input) {
		boolean empty = true; 
		if (input != null) {
			if (input instanceof String) {
				if (!"".equals(input)) {
					empty = false;
				}
			} else if (input instanceof String[]) {
				if (((String[]) input).length > 0) {
					for (int i = 0; i < ((String[]) input).length; i++) {
						if (!"".equals(((String[]) input))) {
							empty = false;
							break;
						}
					}
				}
			}
		}
		return empty;
	}
	
	/**
	 * 문자열이 포함되어 있는지 체크하는 메소드
	 * 
	 * @param input 체크할 전체문자열
	 * @param compStr 포함되어 있는지 체크할 비교문자열
	 * @param searchSaperatedOnly 독립단어만 비교할건지 여부
	 * @return boolean
	 */
	public static boolean isIncluded(String input, String compStr, boolean searchSaperatedOnly) {
		boolean included = false;
		String key = "";
		String[] div = {" ", "\t", "\n", "\r", "(", ")", "{", "}", "[", "]", ".", ",", ";"};
		if (input != null) {
			if(searchSaperatedOnly){
				/* PREFIX */
				for(int l=0; l<div.length; l++){
					/* SUFFIX */
					for(int o=0; o<div.length; o++){
						if(input.startsWith(compStr)){
							key = compStr + div[o];
						}else if(input.endsWith(compStr)){
							key = div[l] + compStr;
						}else{
							key = div[l] + compStr + div[o];
						}
						if(input.indexOf(key) != -1){
							included = true;
						}
						if(included){break;}
					}
					if(included){break;}
				}							
			}else{
				if(input.indexOf(compStr) != -1){
					included = true;
				}
			}
		}
		return included;
	}
	
	/**
	 * 문자열에서 검색시작문자열 이후의 (단어분리 변수배열로 분리되는)단어하나를 반환하는 메소드
	 * 
	 * @param input 체크할 전체문자열
	 * @param startStr 검색시작문자열
	 * @param div 단어분리 변수배열
	 * @return String
	 */
	public static String nextWord(String inputStr, String startStr, String[] div){
		String input = inputStr;
		String nextStr = "";

		int frontIndex = -1;
		int endIndex = -1;
		String frontStr = "";
		String endStr = "";
		if( input.indexOf(startStr) != -1 ){			
			input = input.substring(input.indexOf(startStr)+startStr.length()).trim();
			for(int l=0; l<div.length; l++){
				if(input.indexOf(div[l]) != -1){
					if( nextStr.length() == 0 || nextStr.length() > toStrArray(input, div[l])[0].length() ){
						nextStr = toStrArray(input, div[l])[0];
					}
				}
			}
		}
		return nextStr;
	}
	
	/**
	 * 문자열에서 검색시작문자열 이전의 (단어분리 변수배열로 분리되는)단어하나를 반환하는 메소드
	 * 
	 * @param input 체크할 전체문자열
	 * @param startStr 검색시작문자열
	 * @param div 단어분리 변수배열
	 * @return String
	 */
	public static String beforeWord(String inputStr, String startStr, String[] div){
		String beforeStr = "";
		String[] revDiv = new String[div.length];
		for(int i=0; i<div.length; i++){
			revDiv[i] = net.dstone.common.utils.StringUtil.reverse(div[i]);
		}
		beforeStr = nextWord(net.dstone.common.utils.StringUtil.reverse(inputStr), net.dstone.common.utils.StringUtil.reverse(startStr), div);
		return net.dstone.common.utils.StringUtil.reverse(beforeStr);
	}

	/**
	 * 긴 스트링값을 잘라서 배열로 반환해주는 메소드
	 * 
	 * @param contents
	 * @param intContentSize
	 */
	public static String[] getDividedContents(String raw, int len) {

		if (raw == null)
			return null;

		ArrayList aryList = new ArrayList();
		// String[] ary =null;
		try {
			// raw 의 byte
			byte[] rawBytes = raw.getBytes("MS949");
			int rawLength = rawBytes.length;

			if (rawLength > len) {

				int aryLength = (rawLength / len)
						+ (rawLength % len != 0 ? 1 : 0);

				int endCharIndex = 0; // 문자열이 끝나는 위치
				String tmp;
				for (int i = 0; i < aryLength; i++) {

					if (i == (aryLength - 1)) {

						tmp = raw.substring(endCharIndex);
						// else 부분에서 endCharIndex 가 작아져서 (이를테면 len 10 이고 else
						// 부분에서 잘려지는 길이가 9 일때)
						// 위에서 계산되어진 aryLength 길이보다 길이가 더 길어질 소지가 있습니다.
						// 이에 길이가 더 길 경우에는 for 한번더 돌도록 한다.
						if (tmp.getBytes("MS949").length > len) {
							aryLength++;
							i--;
							continue;
						}

					} else {

						int useByteLength = 0;
						int rSize = 0;
						for (; endCharIndex < raw.length(); endCharIndex++) {

							if (raw.charAt(endCharIndex) > 0x007F) {
								useByteLength += 2;
							} else {
								useByteLength++;
							}
							if (useByteLength > len) {
								break;
							}
							rSize++;
						}
						tmp = raw.substring((endCharIndex - rSize),
								endCharIndex);
					}
					aryList.add(tmp);

				}

			} else {
				aryList.add(raw);
			}

		} catch (java.io.UnsupportedEncodingException e) {
		}

		return (String[]) aryList.toArray(new String[0]);
	}

	/**
	 * strInput스트링내에 objStr의반복횟수를 반환해주는 메소드
	 * 
	 * @param strInput
	 * @param objStr
	 */
	public static int countString(String strInput, String objStr) {
		int iResult = 0;
		int s = 0; 
		int e = 0; 
		StringBuffer result = new StringBuffer();
		while ((e = strInput.indexOf(objStr, s)) >= 0) {
			iResult++;
			s = e + objStr.length();
		}
		return iResult;
	}

	/**
	 * 헝가리언표기법으로 반환해주는 메소드
	 * 
	 */
	public static String getHungarian(String s1, String s2) {
		StringBuffer stringbuffer = new StringBuffer("");
		String s3 = "_";
		java.util.StringTokenizer stringtokenizer = new java.util.StringTokenizer(s1, s3);
		String s4 = "";
		boolean flag = true;
		while (stringtokenizer.hasMoreElements()) {
			s4 = stringtokenizer.nextToken();
			if (flag) {
				stringbuffer.append(s4.toLowerCase());
				flag = false;
			} else if (s4.length() > 0)
				stringbuffer.append(s4.substring(0, 1).toUpperCase()).append(s4.substring(1).toLowerCase());
		}
		s4 = stringbuffer.toString();
		if (!s2.equals("") && s4.length() > 0)
			s4 = s2 + s4.substring(0, 1).toUpperCase() + s4.substring(1);
		return s4;
	}
}
