package net.dstone.common.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.util.Base64Utils;

public class StringUtil {

	/**
	 * 깨진문자(surrogate characters)를 필터링하기위한 정규식
	 */
	public static String BAD_CHAR_REXP = "([\\ud800-\\udbff\\udc00-\\udfff])";
	
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
	 * @param input - 체크할 스트링
	 * @param defaultVal - 체크할 스트링이 null 일때 반환할 값
	 * @return String
	 */
	public static String nullCheck(String input, String defaultVal) {
		if (input == null || "".equals(input) || input.trim().length() == 0) {
			return defaultVal;
		} else {
			return input;
		}
	}
	
	/**
	 * null/zero 체크하는 메소드
	 * @param input - 체크할 스트링
	 * @param defaultVal - 체크할 스트링이 null 이거나 "0" 일때 반환할 값
	 * @return String
	 */
	public static String nullZeroCheck(String input, String defaultVal) {
		if (input == null || "".equals(input) || input.trim().length() == 0 || input.trim().equals("0") ) {
			return defaultVal;
		} else {
			return input;
		}
	}

	/**
	 * 연속공백 치환하는 메소드
	 * @param input - 공백이 존재하는 스트링
	 * @param replacement - 연속공백을 치환값
	 * @return String
	 */
	public static String replacMultiSpace(String input, String replacement) {
		String replaced = "";
		if( !isEmpty(input) ) {
			replaced = input.replaceAll("\\s{2,}", replacement);
		}
		return replaced;
	}

	public static String ltrim(String input) {
		StringBuffer outStr = new StringBuffer();
		char[] incharArr = null;
		char[] delchar = { ' ', '\t', '\r', '\n' };
		boolean isTeBeDelChar = false;
		boolean isAppendStarted = false;
		try {
			incharArr = input.toCharArray();
			for (int i = 0; i < incharArr.length; i++) {
				if (isAppendStarted) {
					outStr.append(incharArr[i]);
				} else {
					isTeBeDelChar = false;
					for (int k = 0; k < delchar.length; k++) {
						if (incharArr[i] == delchar[k]) {
							isTeBeDelChar = true;
							break;
						}
					}
					if (!isTeBeDelChar) {
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
		char[] delchar = { ' ', '\t', '\r', '\n' };
		boolean isTeBeDelChar = false;
		boolean isAppendStarted = false;
		try {
			incharArr = reverse(input).toCharArray();
			for (int i = 0; i < incharArr.length; i++) {
				if (isAppendStarted) {
					outStr.append(incharArr[i]);
				} else {
					isTeBeDelChar = false;
					for (int k = 0; k < delchar.length; k++) {
						if (incharArr[i] == delchar[k]) {
							isTeBeDelChar = true;
							break;
						}
					}
					if (!isTeBeDelChar) {
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
	 * 한글포함여부를 체크하는 메소드
	 * 
	 * @param input
	 *            - 체크할 스트링
	 * @return boolean
	 */
	public static String trim(String input) {
		String trimVal = input;
		if (!isEmpty(input)) {
			trimVal = input.trim();
		}

		return trimVal;
	}

	/**
	 * <code>replace</code> 설명:스트링치환 메소드.
	 * 
	 * @param str 치환할 스트링
	 * @param pattern 있으면 바꾸고자하는 스트링
	 * @param replace 바꿀 스트링
	 * @return String
	 */
	public static String replace(String str, String pattern, String replace) {

		if (str == null || "".equals(str)) {
			return "";
		}

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
	 * <code>replace</code> 설명:스트링치환 메소드.
	 * 
	 * @param str 치환할 스트링
	 * @param map HashMap<있으면 바꾸고자하는 스트링, 바꿀 스트링>
	 * @return String
	 */
	public static String replace(String str, HashMap<String, String> map) {
		if (str == null || "".equals(str)) {
			return "";
		}
		java.util.Iterator<String> keys = map.keySet().iterator();
		String key = null;
		String val = null;
		String outStr = str;
		while(keys.hasNext()){
			key = keys.next();
			val = map.get(key);
			outStr = replace(outStr, key, val);
		}
		return outStr;
	}

	/**
	 * <code>reverse</code> 설명:스트링역순치환 메소드.
	 * 
	 * @param str
	 *            치환할 스트링
	 * @return String
	 */
	public static String reverse(String str) {
		if (str == null || "".equals(str)) {
			return "";
		}
		StringBuffer buff = new StringBuffer(str);
		return buff.reverse().toString();
	}

	/**
	 * <code>subStringAfter</code> 설명:특정문자열 이후의 문자열 반환.
	 * @param str 절삭대상 스트링
	 * @param separator 특정문자열
	 * @return String
	 */
	public static String subStringAfter(String str, String separator) {
		return org.apache.commons.lang3.StringUtils.substringAfter(str, separator);
	}
	
	/**
	 * <code>subStringAfter</code> 설명:특정문자열 이전의 문자열 반환.
	 * @param str 절삭대상 스트링
	 * @param separator 특정문자열
	 * @return String
	 */
	public static String subStringBefore(String str, String separator) {
		return org.apache.commons.lang3.StringUtils.substringBefore(str, separator);
	}
	
	/**
	 * <code>subByteString</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @param length 대상길이
	 * @return String
	 */
	public static String subByteString(String str, int offSet, int length) {
		return new String(org.apache.commons.lang3.ArrayUtils.subarray(str.getBytes(), offSet, length));
	}
	/**
	 * <code>subByteString</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @param length 대상길이
	 * @return String
	 */
	public static String subByteString(String str, int offSet, int length, String encoding) {
		String data = "";
		try {
			data = new String(org.apache.commons.lang3.ArrayUtils.subarray(str.getBytes(encoding), offSet, length), encoding);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}

	/**
	 * <code>subByteString</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param idx 시작인덱스
	 * @return String
	 */
	public static String subByteString(String str, int offSet) {
		return new String(org.apache.commons.lang3.ArrayUtils.subarray(str.getBytes(), offSet, str.getBytes().length));
	}
	
	/**
	 * <code>subByteString</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param idx 시작인덱스
	 * @return String
	 */
	public static String subByteString(String str, int offSet, String encoding) {
		String data = "";
		try {
			data = new String(org.apache.commons.lang3.ArrayUtils.subarray(str.getBytes(encoding), offSet, str.getBytes().length), encoding);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}
	
	/**
	 * <code>subByte</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @param length 대상길이
	 * @return String
	 */
	public static byte[] subByte(byte[] str, int offSet, int length) {
		byte[] data = null;
		try {
			data = org.apache.commons.lang3.ArrayUtils.subarray(str, offSet, length);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}
	/**
	 * <code>subByte</code> 설명:바이트절삭 메소드.
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @return String
	 */
	public static byte[] subByte(byte[] str, int offSet) {
		byte[] data = null;
		try {
			data = org.apache.commons.lang3.ArrayUtils.subarray(str, offSet, str.length);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}
	
	/**
	 * <code>subByteFilterBadChar</code> 설명:바이트절삭 메소드.(깨진글자는 필터링)
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @param length 대상길이
	 * @param isLenghhHold str의 길이를 유지할지 여부(true 이면 str에서 깨진글자를 제거한 후 줄어든 길이를 원래str의 길이만큼 스페이스로 채워서 반환)
	 * @return byte[]
	 */
	public static byte[] subByteFilterBadChar(byte[] str, int offSet, int length, boolean isLenghhHold) {
		byte[] data = null;
		try {
			data = org.apache.commons.lang3.ArrayUtils.subarray(str, offSet, length);
			data = removeBadChar(data, isLenghhHold);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}
	
	/**
	 * <code>subByteFilterBadChar</code> 설명:바이트절삭 메소드.(깨진글자는 필터링)
	 * @param str 절삭대상 스트링
	 * @param offSet 시작인덱스
	 * @param isLenghhHold str의 길이를 유지할지 여부(true 이면 str에서 깨진글자를 제거한 후 줄어든 길이를 원래str의 길이만큼 스페이스로 채워서 반환)
	 * @return byte[]
	 */
	public static byte[] subByteFilterBadChar(byte[] str, int offSet, boolean isLenghhHold) {
		byte[] data = null;
		try {
			data = org.apache.commons.lang3.ArrayUtils.subarray(str, offSet, str.length);
			data = removeBadChar(data, isLenghhHold);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return data;
	}
	
	/**
	 * <code>removeBadChar</code> 설명:깨진글자 제거 메소드.
	 * @param data 깨진글자 제거 대상 바이트배열
	 * @param isLenghhHold data의 길이를 유지할지 여부(true 이면 data에서 깨진글자를 제거한 후 줄어든 길이를 원래data의 길이만큼 스페이스로 채워서 반환)
	 * @return byte[]
	 */
	public static byte[] removeBadChar(byte[] data, boolean isLenghhHold) {
		int dataLen = 0;
		byte[] newData = null;
		int newDataLen = 0;
		StringBuffer filler = null;
		try {
			if( data != null ){
				dataLen = data.length;
				newData = new String( data ).replaceAll(BAD_CHAR_REXP, "").getBytes();
				if(isLenghhHold){
					if( newData != null ){
						newDataLen = newData.length;
						if( dataLen > newDataLen ){
							filler = new StringBuffer();
							for( int i=0; i<(dataLen-newDataLen); i++ ){
								filler.append(" ");
							}
							newData = appendByte(newData, filler.toString().getBytes());
						}
					}
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return newData;
	}
	
	/**
	 * <code>appendByte</code> 설명:바이트를 더하는 메소드.
	 * @param srcByteArr 대상바이트배열1
	 * @param tgtByteArr 대상바이트배열2
	 * @return byte[] 대상바이트배열1+대상바이트배열2 의 순서로 합쳐진 바이트배열
	 */
	public static byte[] appendByte(byte[] srcByteArr, byte[] tgtByteArr) {
		byte[] message = new byte[srcByteArr.length + tgtByteArr.length];
		System.arraycopy(srcByteArr, 0, message, 0, srcByteArr.length);
		System.arraycopy(tgtByteArr, 0, message, srcByteArr.length, tgtByteArr.length);
		return message;
	}
	
	/**
	 * <code>appendFld</code> 설명:스트링을 바이트단위 고정길이로 더하는 메소드.
	 * @param base 소스스트링
	 * @param append 더해질스트링
	 * @param fixedByteLength 길이
	 * @return 소스스트링+더해질스트링(바이트길이만큼 고정하여) 의 순서로 합쳐진  스트링
	 */
	public static String appendFld(String base, String append, int fixedByteLength) throws Exception {
        return appendFld(base, append, fixedByteLength, java.nio.charset.Charset.defaultCharset().name());
	}
	
	/**
	 * <code>appendFld</code> 설명:스트링을 바이트단위 고정길이로 더하는 메소드.
	 * @param base 소스스트링
	 * @param append 더해질스트링
	 * @param fixedByteLength 길이
	 * @param charSet 캐릭터셋
	 * @return 소스스트링+더해질스트링(바이트길이만큼 고정하여) 의 순서로 합쳐진  스트링
	 */
	public static String appendFld(String base, String append, int fixedByteLength, String charSet) throws Exception {
        int currentBytes = 0;
        int cutIndex = 0;
        if(StringUtil.isEmpty(base)){ base = ""; }
        if(StringUtil.isEmpty(append)){ append = ""; }
        
        StringBuilder result = new StringBuilder(base);
        try {
            // 바이트 길이 맞춰서 절삭
            for (int i = 0; i < append.length(); i++) {
                String ch = append.substring(i, i + 1);
                int chBytes = ch.getBytes(charSet).length;
                if (currentBytes + chBytes > fixedByteLength) break;
                currentBytes += chBytes;
                cutIndex = i + 1;
            }
            String trimmed = append.substring(0, cutIndex);
            int paddingBytes = fixedByteLength - trimmed.getBytes(charSet).length;

            // 부족하면 공백 채움
            result.append(trimmed);
            for (int i = 0; i < paddingBytes; i++) {
                result.append(" ");
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
        return result.toString();
	}
	

	/**
	 * <code>substrFld</code> 설명:스트링을 바이트단위로 자르는 메소드.
	 * @param base 소스스트링
	 * @param offSet 시작위치
	 * @param length 길이
	 * @return 바이트단위로 절삭된  스트링
	 */
	public static String substrFld(String base, int offSet, int length) throws Exception {
        return substrFld(base, offSet, length, java.nio.charset.Charset.defaultCharset().name());
	}
	
	/**
	 * <code>substrFld</code> 설명:스트링을 바이트단위로 자르는 메소드.
	 * @param base 소스스트링
	 * @param offSet 시작위치
	 * @param length 길이
	 * @param charSet 캐릭터셋
	 * @return 바이트단위로 절삭된  스트링
	 */
	public static String substrFld(String base, int offSet, int length, String charSet) throws Exception {
        int currentByte = 0;
        int startIdx = -1;
        int endIdx = -1;

        if(StringUtil.isEmpty(base)){ base = ""; }
        
        for (int i = 0; i < base.length(); i++) {
            String ch = base.substring(i, i + 1);
            int chByteLen = ch.getBytes(charSet).length;
            if (startIdx == -1 && currentByte + chByteLen > offSet) {
                startIdx = i;
            }
            if (startIdx != -1 && currentByte >= offSet + length) {
                endIdx = i;
                break;
            }
            currentByte += chByteLen;
        }
        // 마지막까지 잘리지 않은 경우
        if (startIdx != -1 && endIdx == -1) {
            endIdx = base.length();
        }
        if (startIdx == -1 || endIdx == -1) return "";

        return base.substring(startIdx, endIdx);
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
		return org.apache.commons.lang3.StringEscapeUtils.escapeHtml3(html);
	}

	public static String unEscapHTML(String html) {
		return org.apache.commons.lang3.StringEscapeUtils.unescapeHtml3(html);
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

			java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(param_format);
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
		return new StringBuffer(subByteString(Answer2.toString(), 0, len)).reverse().toString();
	}

	public static String filler(String input, int len, String filler, String encoding) {
		StringBuffer Answer1 = new StringBuffer(input);
		StringBuffer Answer2 = new StringBuffer("");
		int intLen = Answer1.length();
		Answer1 = Answer1.reverse();

		if (input.getBytes().length > len) {
			return input;
		}
		for (int i = 0, j = 0; i < len; i++, j++) {
			if (j <= (intLen - 1)) {
				Answer2 = Answer2.append(Answer1.charAt(i));
			} else {
				Answer2 = Answer2.append(filler);
			}
		}
		if(encoding == null || "".equals(encoding)){
			return new StringBuffer(subByteString(Answer2.toString(), 0, len)).reverse().toString();
		}else{
			return new StringBuffer(subByteString(Answer2.toString(), 0, len, encoding)).reverse().toString();
		}
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
		return new StringBuffer(subByteString(Answer2.toString(), 0, len)).toString();
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
	
	public static String encodingConv(String str, String toEnc) {
		String tmp = new String("");
		if (str == null || str.length() == 0)
			return "";

		try {
			tmp = new String(str.getBytes(), toEnc);
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
	 * @return 스트링배열
	 */
	public static String[] toStrArray(String strInput, String div) {
		return toStrArray(strInput, div, true);
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
		return toStrArray(strInput, div, trimYn, false);
	}
	
	/**
	 * <code>toStrArray</code> 설명: 구분자를 포함한 스트링을 배열로 바꾸는 메소드
	 * 
	 * @param strInput 구분자를 포함한 전체스트링
	 * @param div 구분자
	 * @param trimYn 트림여부
	 * @param delBlankLineYn 비어있는라인제거여부
	 * @return 스트링배열
	 */
	public static String[] toStrArray(String strInput, String div, boolean trimYn, boolean delBlankLineYn) {
		String[] sResults = null;
		String sTemp = "";

		strInput = replace(strInput, (div), (" " + div));
		java.util.StringTokenizer token = new java.util.StringTokenizer(strInput, div);
		java.util.ArrayList<String> strList = new java.util.ArrayList<String>();
		
		while (token.hasMoreElements()) {
			sTemp = (String) token.nextElement();
			if(delBlankLineYn) {
				if(sTemp.trim().equals("")) {
					continue;
				}
			}
			if (trimYn) {
				strList.add(sTemp.trim());
			} else {
				strList.add(sTemp);
			}
		}
		sResults = new String[strList.size()];
		strList.toArray(sResults);
		strList.clear();
		strList = null;
		return sResults;
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
				return moneyForamtter(str.substring(0, dot_num)) + str.substring(dot_num);
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
				return moneyForamtter(str.substring(0, dot_num)) + str.substring(dot_num);
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
				return moneyForamtter(str.substring(0, dot_num)) + str.substring(dot_num);
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
		String els = new String("");
		int length = 0;
		int i = 0, mod = 0, mod1 = 0, ismin = 0, point = -1;
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
			return moneyForamtter(str.substring(0, dot_num)) + str.substring(dot_num);
		} else {
			return moneyForamtter(str);
		}
	}

	/**
	 * <code>moneyScale</code> 설명:화폐단위표기 메소드.
	 * 
	 * @param input 화폐수치
	 * @param scale 화폐단위(화폐수치를 나눌 화폐단위)
	 * @return 화폐단위로 나눈 화폐수치 스트링
	 */
	public static String moneyScale(String input, int scale) {
		String str = input;
		if( isEmpty(str) ){
			str = "";
		}else{
			str = replace(str, ",", "");
			if( isNumber(str) ){
				java.math.BigDecimal dec = new java.math.BigDecimal(str);
				str = dec.divide(new java.math.BigDecimal(scale), 0, java.math.BigDecimal.ROUND_HALF_UP).toString();
			}
		}
		return str;
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
						if ( ((String[]) input)[i] != null && !"".equals(((String[]) input)[i]) ) {
							empty = false;
							break;
						}
					}
				}
			}
		}
		return empty;
	}
	
	public static String ifEmpty(Object input, String defaultVal) {
		String val = "";
		if( isEmpty(input) ) {
			val = defaultVal;
		}else {
			val = input.toString();
		}
		return val;
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
		String[] div = { " ", "\t", "\n", "\r", "(", ")", "{", "}", "[", "]", ".", ",", ";", "\"", "'" };
		if (input != null) {
			if (searchSaperatedOnly) {
				/* PREFIX */
				for (int l = 0; l < div.length; l++) {
					/* SUFFIX */
					for (int o = 0; o < div.length; o++) {
						if (input.startsWith(compStr)) {
							key = compStr + div[o];
						} else if (input.endsWith(compStr)) {
							key = div[l] + compStr;
						} else {
							key = div[l] + compStr + div[o];
						}
						if (input.indexOf(key) != -1) {
							included = true;
						}
						if (included) {
							break;
						}
					}
					if (included) {
						break;
					}
				}
			} else {
				if (input.indexOf(compStr) != -1) {
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
	public static String nextWord(String inputStr, String startStr, String[] div) {
		return nextWord(inputStr, startStr, 0, div);
	}
	
	/**
	 * 문자열에서 검색시작문자열 이후의 (단어분리 변수배열로 분리되는)단어하나를 반환하는 메소드
	 * 
	 * @param input 체크할 전체문자열
	 * @param startStr 검색시작문자열
	 * @param step 몇번째다음문자열인지 지정(0:다음문자, 1:다다음문자, ...)
	 * @param div 단어분리 변수배열
	 * @return String
	 */
	public static String nextWord(String inputStr, String startStr, int step, String[] div) {

		String input = inputStr;
		String selectedDiv = "";
		int selectedIndex = Integer.MAX_VALUE;
		String remainStr = "";
		String nextStr = "";

		// 검색시작문자열 이 분리변수로 시작할 경우 제거해준다.
		if( div != null && div.length > 0 ) {
			for(int k=0; k<div.length; k++) {
				if(!StringUtil.isEmpty(startStr)) {
					if( startStr.startsWith(div[k])) {
						startStr = startStr.substring(startStr.indexOf(div[k])+div[k].length());
					}
				}
			}
		}
		// 전체문자열  이 분리변수로 끝나지 않을 경우 분리변수를 붙여준다.(전체문자열은 반드시 분리변수로 끝나도록 처리.)
		if( div != null && div.length > 0 ) {
			boolean isEndsWithDiv = false;
			selectedDiv = "";
			selectedIndex = Integer.MAX_VALUE;
			for(int k=0; k<div.length; k++) {
				// 전체문자열에서 최초로 만나는 분리변수를 구한다.
				if(input.indexOf(div[k])>-1 ) {
					selectedIndex = Math.min(input.indexOf(div[k]), selectedIndex);
					if(selectedIndex == input.indexOf(div[k]) ) {
						selectedDiv = div[k];
					}
				}
				if( !isEndsWithDiv && input.endsWith(div[k]) ) {
					isEndsWithDiv = true;
				}
			}
			if( !isEndsWithDiv ) {
				if(StringUtil.isEmpty(selectedDiv)) {
					selectedDiv = div[0];
				}
				input = input + selectedDiv;
			}
		}		
		
		// 전체문자열에서 검색시작문자 이후의 문자열을 잔여문자열로 저장.
		remainStr = org.apache.commons.lang3.StringUtils.substringAfter(input, startStr); 
		
		if((step+1) > 0) {
			selectedDiv = "";
			selectedIndex = Integer.MAX_VALUE;
			for(int i=0; i<(step+1); i++) {
				if( div != null && div.length > 0 ) {
					for(int k=0; k<div.length; k++) {
						// 잔여문자열 이 분리변수로 시작할 경우 제거해준다.
						if( remainStr.startsWith(div[k])) {
							remainStr = remainStr.substring(remainStr.indexOf(div[k])+div[k].length());
						}
						// 잔여문자열에서 최초로 만나는 분리변수를 구한다.
						if(remainStr.indexOf(div[k])>-1 ) {
							selectedIndex = Math.min(remainStr.indexOf(div[k]), selectedIndex);
							if(selectedIndex == remainStr.indexOf(div[k]) ) {
								selectedDiv = div[k];
							}
						}
					}
				}
				// 잔여문자열에서 최초로 만나는 분리변수 이전의 문자열(검색시작문자 이후 분리변수를 만나기 이전의 최초의 문자열)을 구하여 반환값인 다음문자열 에 저장한다.
				nextStr = org.apache.commons.lang3.StringUtils.substringBefore(remainStr, selectedDiv);
				//LogUtil.sysout("input["+ input +"]" + " startStr["+ startStr +"]"  + " selectedDiv["+ selectedDiv +"]" + " nextStr["+ nextStr +"]" + " remainStr["+ remainStr +"]");
				// 잔여문자열에서 최초로 만나는 분리변수 이후의 문자열을 다시 잔여문자열에 저장하여 LOOP를 수행할 수 있도록 한다.
				remainStr = org.apache.commons.lang3.StringUtils.substringAfter(remainStr, selectedDiv);
			}
		}
		
		return nextStr;
	}
	

	/**
	 * 문자열에서 검색시작문자열 이전의 (단어분리 변수배열로 분리되는)단어하나를 반환하는 메소드
	 * @param input 체크할 전체문자열
	 * @param startStr 검색시작문자열
	 * @param div 단어분리 변수배열
	 * @return String
	 */
	public static String beforeWord(String inputStr, String startStr, String[] div) {
		return beforeWord(inputStr, startStr, 0, div);
	}
	
	/**
	 * 문자열에서 검색시작문자열 이전의 (단어분리 변수배열로 분리되는)단어하나를 반환하는 메소드
	 * @param input 체크할 전체문자열
	 * @param startStr 검색시작문자열
	 * @param step 몇번째이전문자열인지 지정
	 * @param div 단어분리 변수배열
	 * @return String
	 */
	public static String beforeWord(String inputStr, String startStr, int step, String[] div) {
		String beforeStr = "";
		String[] revDiv = new String[div.length];
		for (int i = 0; i < div.length; i++) {
			revDiv[i] = StringUtil.reverse(div[i]);
		}
		beforeStr = nextWord(StringUtil.reverse(inputStr), StringUtil.reverse(startStr), step, div);
		return StringUtil.reverse(beforeStr);
	}

	/**
	 * 긴 스트링값을 잘라서 배열로 반환해주는 메소드
	 * 
	 * @param contents
	 * @param intContentSize
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
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

				int aryLength = (rawLength / len) + (rawLength % len != 0 ? 1 : 0);

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
						tmp = raw.substring((endCharIndex - rSize), endCharIndex);
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
		while ((e = strInput.indexOf(objStr, s)) >= 0) {
			iResult++;
			s = e + objStr.length();
		}
		return iResult;
	}

	/**
	 * strInput스트링내에 charArr의반복횟수를 반환해주는 메소드
	 * 
	 * @param strInput
	 * @param charArr
	 */
	public static int countChar(String strInput, char[] charArr) {
		int iResult = 0;
		char[] strInputCharArr;
		strInputCharArr = strInput.toCharArray();
		for (int i = 0; i < strInputCharArr.length; i++) {
			for (int k = 0; k < charArr.length; k++) {
				if (strInputCharArr[i] == charArr[k]) {
					iResult++;
					break;
				}
			}
		}
		return iResult;
	}

	/**
	 * 헝가리언표기법으로 반환해주는 메소드
	 * 
	 */
	//String inputStr, String prefix
	public static String getHungarianName(String inputStr, String prefixTebeRemoved) {
		StringBuffer stringbuffer = new StringBuffer("");
		String deli = "_";
		if( isEmpty(inputStr) ){
			return inputStr;
		}
		if ( !isEmpty(prefixTebeRemoved) ){
			inputStr = replace(inputStr, prefixTebeRemoved, "");
		}
		java.util.StringTokenizer stringtokenizer = new java.util.StringTokenizer(inputStr, deli);
		String returnStr = "";
		boolean flag = true;
		while (stringtokenizer.hasMoreElements()) {
			returnStr = stringtokenizer.nextToken();
			if (flag) {
				stringbuffer.append(returnStr.toLowerCase());
				flag = false;
			} else if (returnStr.length() > 0) {
				stringbuffer.append(returnStr.substring(0, 1).toUpperCase()).append(returnStr.substring(1).toLowerCase());
			}
		}
		returnStr = stringbuffer.toString();
		if (!prefixTebeRemoved.equals("") && returnStr.length() > 0) {
			returnStr = prefixTebeRemoved + returnStr.substring(0, 1).toUpperCase() + returnStr.substring(1);
		}
		return returnStr;
	}
	
	
	
	/**
	 * 헝가리언표기법에서 static표기형식으로 반환해주는 메소드
	 * 
	 */
	public static String getStaticName(String inputStr, String prefix) {
		StringBuffer stringbuffer;
		LinkedList<String> words = new LinkedList<String>();
		char[] charArray = null;
		char chr;
		String editStr;
		String outStr = "";
		try {
			if( !isEmpty(inputStr)  ){
				stringbuffer = new StringBuffer("");
				if( !isEmpty(prefix)  ){
					editStr = replace(inputStr, prefix, "");
				}else{
					editStr = inputStr;
				}
				
				charArray = editStr.toCharArray();
				for(int i=0; i<charArray.length; i++){
					chr = charArray[i];
					if(Character.isUpperCase(chr)){
						words.add(stringbuffer.toString());
						stringbuffer = new StringBuffer("");
						stringbuffer.append(chr);
					}else{
						stringbuffer.append(chr);
					}
				}
				if(stringbuffer.length() > 0){
					words.add(stringbuffer.toString());
				}
				for(int i=0; i<words.size(); i++){
					outStr = outStr + words.get(i).toUpperCase();
					if(i < (words.size()-1) ){
						outStr = outStr + "_";
					}
				}
				if( !isEmpty(prefix)  ){
					outStr = prefix + "_" + outStr;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outStr;
	}
	

	/**
	 * 대문자인지 체크하여 반환해주는 메소드
	 * 
	 */
	public static boolean isUpper(String inputStr) {
		boolean isUpper = false;
		if( inputStr != null ){
			isUpper = inputStr.matches("^[A-Z]*$");
		}
		return isUpper;
	}

	/**
	 * 소문자인지 체크하여 반환해주는 메소드
	 * 
	 */
	public static boolean isLower(String inputStr) {
		boolean isUpper = false;
		if( inputStr != null ){
			isUpper = inputStr.matches("^[a-z]*$");
		}
		return isUpper;
	}

	/**
	 * 한글포함여부를 체크하는 메소드
	 * 
	 * @param input - 체크할 스트링
	 * @return boolean
	 */
	public static boolean isHangle(String input) {
		boolean isHangleInclude = false;
		if (!isEmpty(input)) {
			isHangleInclude = input.matches(".*[ㄱ-ㅎㅏ-ㅣ가-?]+.*");
		}

		return isHangleInclude;
	}
	
	/**
	 * 넘버형식인지 체크하는 메소드
	 * 
	 * @param input - 체크할 스트링
	 * @return boolean
	 */
	public static boolean isNumber(String inputStr) {
		boolean flag = false;
		inputStr = nullCheck(inputStr, "");
		char[] charArray = inputStr.toCharArray();
		for (int j = 0; j < charArray.length; j++) {
			if (charArray[j] >= '0' && charArray[j] <= '9') {
				flag = true;
			} else {
				flag = false;
				break;
			}
		}
		return flag;
	}
	
	/**
	 * 두문자의 유사치를 측정하는 메소드. 비교대상 스트링이 체크대상 스트링을 모두 포함하고 있으나(similarity100%) 동일하지 않을 경우 99%를 반환한다.
	 * @param inputStr - 체크대상 스트링
	 * @param compStr - 비교대상 스트링
	 * @return int - 유사치
	 */
	public static int getSimilarity(String inputStr, String compStr) {
		int similarity = 0;
		
		if( !isEmpty(inputStr) &&  !isEmpty(compStr) ){
			char[] inputCharArray = inputStr.toCharArray();
			char[] compCharArray = compStr.toCharArray();
			int inputStrLen = inputCharArray.length;
			for (int i = 0; i < inputCharArray.length; i++) {
				for (int j = 0; j < compCharArray.length; j++) {
					if(inputCharArray[i] == compCharArray[j]){
						similarity++;
						break;
					}
				}
			}
			BigDecimal result = new BigDecimal(similarity).divide(new BigDecimal(inputStrLen), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
			similarity = result.intValue();
			if(similarity == 100){
				if(!inputStr.equals(compStr)){
					similarity = 99;
				}
			}
		}
		return similarity;
	}
	
	/**
	 * 비교대상 스트링배열에서 체크대상 스트링과 가장 유사한 스트링을 반환하는 메소드.(유사점이 하나도 없을 경우 null반환)
	 * @param inputStr - 체크대상 스트링
	 * @param compStrArray - 비교대상 스트링배열
	 * @return int - 유사한 스트링
	 */
	public static String getMostSimilarWord(String inputStr, String[] compStrArray) {
		String similarStr = null;
		int similarity = 0;
		if( compStrArray != null ){
			int mostSimilarIndex = -1;
			int currentSimilarity = 0;
			for(int i=0; i<compStrArray.length; i++){
				currentSimilarity = getSimilarity(inputStr, compStrArray[i]);
				if( currentSimilarity >  similarity){
					similarity = currentSimilarity;
					mostSimilarIndex = i;
				}
			}
			if(mostSimilarIndex > -1){
				similarStr = compStrArray[mostSimilarIndex];
			}
		}
		return similarStr;
	}
	
	@SuppressWarnings("rawtypes")
	public static String[] getKey(HashMap map){
		String[] keyStrArray = null;
		ArrayList<String> keyList = new ArrayList<String>();
		if( map != null ){
			Iterator iter = map.keySet().iterator();
			while(iter.hasNext()){
				keyList.add( iter.next().toString() );
			}
			keyStrArray = keyList.toArray(new String[]{});
		}
		return keyStrArray;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object[] getValues(HashMap map){
		Object[] valArray = null;
		if( map != null ){
			Collection vals = map.values();
			valArray = vals.toArray(new Object[]{});
		}
		return valArray;
	}
	

	/**
	 * @문자열 압축하기
	 * @param InputStream is
	 * @return 바이트배열
	 * @throws Exception
	 */
	public static byte[] getGzipBytes(String str) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		BufferedOutputStream bos = null;
		try {
			bos = new BufferedOutputStream(new GZIPOutputStream(baos));
			bos.write(str.getBytes());
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (bos != null) {
				try {
					bos.close();
				} catch (IOException e) {
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
				}
			}
		}
		return baos.toByteArray();
	}
	
	/**
	 * @문자열 압축해제하기
	 * @param InputStream is
	 * @return 바이트배열
	 * @throws Exception
	 */
	public static String getUnGzipBytes(byte[] input) throws Exception {
		String outStr = null;
		ByteArrayOutputStream baos = null;
		ByteArrayInputStream bais = null;
		BufferedInputStream bis = null;
		byte[] buffer = new byte[1024];
		int len = -1;
		try {
			bais = new ByteArrayInputStream(input);
			bis = new BufferedInputStream(new GZIPInputStream(bais));
			baos = new ByteArrayOutputStream();
			while( (len=bis.read(buffer))>0 ){
				baos.write(buffer, 0, len);
			}
			outStr = baos.toString();
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (bis != null) {
				try {
					bis.close();
				} catch (IOException e) {
				}
			}
			if (bais != null) {
				try {
					bais.close();
				} catch (IOException e) {
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
				}
			}
		}
		return outStr;
	}
	
	/**
	 * 스트링배열을 원하는 서브배열로 나누는 메소드.
	 * @param sData
	 * @param numMaps
	 * @return
	 */
	public static String[][] splitStringArray(String[] sData, int numMaps) {
		int nbInstances = sData.length;
		int partitionSize = nbInstances / numMaps;
		String[][] splits = new String[numMaps][];
		if(sData != null) {
			for (int partition = 0; partition < numMaps; partition++) {
				int from = partition * partitionSize;
				int to = partition == (numMaps - 1) ? nbInstances : (partition + 1) * partitionSize;
				splits[partition] = Arrays.copyOfRange(sData, from, to);
			}
		}
		return splits;
	}

	/**
	 * 파일내용을 파싱하기 편하게 변환.(탭을 스페이스로 변환, 다중스페이스를 단일스페이스로 변환)
	 * @param conts
	 * @return
	 */
	public static String trimTextForParse(String conts) {
		conts = replace(conts, "\t", " ");
		conts = replace(conts, "   ", " ");
		conts = replace(conts, "  ", " ");
		conts = replace(conts, " ;", ";");
		conts = replace(conts, " (", "(");
		conts = replace(conts, " )", ")");
		conts = replace(conts, " {", "{");
		conts = replace(conts, " }", "}");
		return conts;
	}
	
	/**
	 * 인풋스트링을 반복횟수만큼 복제해서 반환.
	 * @param input
	 * @param repeatNum
	 * @return
	 */
	public static String repeatStr(String input, int repeatNum) {
		StringBuffer output = new StringBuffer();
		if( !isEmpty(input) && repeatNum > -1 ) {
			for(int i=0; i<repeatNum; i++) {
				output.append(input);
			}
		}
		return output.toString();
	}

	/**
	 * 두 문자의 일치비율을 반환.
	 * @param x
	 * @param y
	 * @return
	 */
    public static double reteSimilarity(String x, String y) {
    	double similarity = 0.0;
        if (x == null && y == null) {
            return 1.0;
        }else if (x == null || y == null) {
            return 0.0;
        }else {
        	similarity = StringUtils.getJaroWinklerDistance(x, y);
        }
        return (similarity * 100);
    }
    

	/**
	 * 인코딩체크
	 * @param input
	 * @return
	 */
    public static void encodingCheck(String input) {
    	if(!StringUtil.isEmpty(input)) {
    		UniversalDetector detector = new UniversalDetector(null);
    		byte[] buffer = input.getBytes();
        	detector.handleData(buffer, 0, buffer.length);
        	detector.dataEnd();
        	String charSet = detector.getDetectedCharset();
        	System.out.println("Detected encoding: " + charSet);
    	}
    }

	/**
	 * Base64 인코딩 메소드
	 * @param input
	 * @return
	 */
    public static String base64Encode(String input) {
    	String output = "";
    	try {
    		if( !isEmpty(input) ) {
    			output = new String(Base64Utils.encode(input.getBytes()));
    		}
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return output;
    }

	/**
	 * Base64 디코딩 메소드
	 * @param input
	 * @return
	 */
    public static String base64Decode(String input) {
    	String output = "";
    	try {
    		if( !isEmpty(input) ) {
    			output = new String(Base64Utils.decode(input.getBytes()));
    		}
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return output;
    }
    
    /***
     * 난수로 생성한 문자열을 만든다.
     * @param strLength 난수를 만들 문자열의 길이
     * @return
     */
    public static String getRandomNumber(int strLength) {
        StringBuffer resultStr = new StringBuffer();
        Random random = new Random();
        for(int i = 0; i < strLength ; i++){
        	int result = random.nextInt(10);
        	resultStr.append(Integer.toString(result)); 
        }
    	return resultStr.toString();
    }
    
    /***
     * true가 반환될 확률로 Boolean값을 만든다.
     * @param persentForTrue true가 반환될 확률(%)
     * @return
     */
    public static boolean getRandomBoolean(int persentForTrue) {
    	boolean resultBool = false;
        Random random = new Random();
        int result = random.nextInt(100);
        if(persentForTrue >= result) {
        	resultBool = true;
        }
    	return resultBool;
    }
}
