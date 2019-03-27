package net.dstone.common.utils;

import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WebUtil {

	public static String makeComboYear(String strStartYear, String strHowLong,
			String strSelectedYear) {
		int startYear = Integer.parseInt(net.dstone.common.utils.StringUtil.nullCheck(
				strStartYear, net.dstone.common.utils.DateUtil.getToDate("yyyy")));
		int howLong = Integer.parseInt(net.dstone.common.utils.StringUtil.nullCheck(
				strHowLong, "0"));
		int selectedYear = Integer.parseInt(net.dstone.common.utils.StringUtil
				.nullCheck(strSelectedYear, "-1"));
		StringBuffer html = new StringBuffer();
		for (int i = 0; i < howLong; i++) {
			if ((startYear + i) == selectedYear) {
				html.append("<option value='" + (startYear + i)
						+ "' selected >");
			} else {
				html.append("<option value='" + (startYear + i) + "' >");
			}
			html.append((startYear + i));
			html.append("</option>");

		} 
		return html.toString();
	}

	public static String makeComboYearSpan(String strBaseYear,
			int intBetweenHowLong, String strSelectedYear) {
		String thisYear = net.dstone.common.utils.DateUtil.getToDate("yyyy");
		int startYear = Integer.parseInt(net.dstone.common.utils.StringUtil.nullCheck(
				strBaseYear, thisYear)) - intBetweenHowLong;
		int endYear = Integer.parseInt(net.dstone.common.utils.StringUtil.nullCheck(
				strBaseYear, thisYear)) + intBetweenHowLong;
		int selectedYear = Integer.parseInt(net.dstone.common.utils.StringUtil
				.nullCheck(strSelectedYear, thisYear));
		StringBuffer html = new StringBuffer();

		for (int i = startYear; i <= endYear; i++) {
			if ((i) == selectedYear) {
				html.append("<option value='" + (i) + "' selected >");
			} else {
				html.append("<option value='" + (i) + "' >");
			}
			html.append((i));
			html.append("</option>");

		}
		return html.toString();
	}

	/**
	 * 1. HTML SELECT BOX 를 만들어 준다. 
	 * 2. 현재 년월을 기준으로 이전년월을 생성한다
	 * @param year 뒤로 년수, 선택된 년월 , 객체명
	 * @return SELECT BOT TAG
	 * @throws Exception
	 */
	public static String selectOptionBackYYYYMM(int back, int front, String ym) {
		String re = "";
		back = back +1;
		java.util.Calendar c = java.util.Calendar.getInstance();
		int yyyy = c.get(java.util.Calendar.YEAR)+front;
		int mm = c.get(java.util.Calendar.MONTH) + 1;
		int yyyy2 = yyyy;

		int year = 0;
		try {
			year = Integer.parseInt(ym);
		} catch (Exception e) {
		}
		while (yyyy > yyyy2 - (back+front)) {
			if ((yyyy * 100 + mm) == year) {
				re += "<OPTION value='" + (yyyy * 100 + mm) + "' selected>"
						+ DateUtil.dateFormat("yyyy'/'MM", String.valueOf(yyyy * 100 + mm)+"01")  + "</OPTION>\n";
			} else {
				re += "<OPTION value='" + (yyyy * 100 + mm) + "'>"
						+ DateUtil.dateFormat("yyyy'/'MM", String.valueOf(yyyy * 100 + mm)+"01") + "</OPTION>\n";
			}
			mm = mm - 1;
			if (mm == 0) {
				mm = 12;
				yyyy = yyyy - 1;
			}
		}
		return re;
	}

	/**
	 * 1. HTML SELECT BOX 를 만들어 준다. 
	 * 2. 현재 년 기준으로 년을 생성한다
	 * @param year 뒤로 년수, 선택된 년 , 객체명
	 * @return SELECT BOT TAG
	 * @throws Exception
	 */
	public static String selectOptionBackYYYY(int back, int front, String ym) {
		String re = "";
		back = back +1;
		java.util.Calendar c = java.util.Calendar.getInstance();
		int yyyy = c.get(java.util.Calendar.YEAR)+front;
		int yyyy2 = yyyy;

		int year = 0;
		try {
			year = Integer.parseInt(ym);
		} catch (Exception e) {
		}
		while (yyyy > yyyy2 - (back+front)) {
			if (yyyy == year) {
				re += "<OPTION value='" + (yyyy) + "' selected>" + (yyyy)
						+ "</OPTION>";
			} else {
				re += "<OPTION value='" + (yyyy) + "'>" + (yyyy) + "</OPTION>";
			}

			yyyy = yyyy - 1;
		}
		return re;
	}
	
	/**
	 * 1. HTML SELECT BOX 를 만들어 준다. 
	 * 2. 년을 생성한다
	 * @param yyyyMMdd 기준일
	 * @return SELECT BOT TAG
	 * @throws Exception
	 */
	public static String selectOptionYear(String yyyyMMdd,int back, int front) {
		StringBuffer html = new StringBuffer();
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "-", "");
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "/", "");
		try {
			int baseYyyy = Integer.parseInt(yyyyMMdd.substring(0, 4));
			String[] years = DateUtil.getYearBetweenSpan( String.valueOf(baseYyyy - back), String.valueOf(baseYyyy + front));
			for(int i=0; i<years.length; i++){
				html.append("<OPTION value='").append(years[i]).append("' ");
				if( Integer.parseInt(years[i]) == baseYyyy ){
					html.append(" selected ");
				}
				html.append(" >").append( years[i] ).append("년").append("</OPTION>"); 
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return html.toString();
	}
	/**
	 * 1. HTML SELECT BOX 를 만들어 준다. 
	 * 2. 달을 생성한다
	 * @param yyyyMMdd 기준일
	 * @return SELECT BOT TAG
	 * @throws Exception
	 */
	public static String selectOptionMonth(String yyyyMMdd) {
		StringBuffer html = new StringBuffer();
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "-", "");
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "/", "");
		try {
			int baseMm = Integer.parseInt(yyyyMMdd.substring(4, 6));
			for(int i=0; i<12; i++){
				html.append("<OPTION value='").append(StringUtil.filler(String.valueOf(i+1), 2, "0")).append("' ");
				if( (i+1) == baseMm ){
					html.append(" selected ");
				}
				html.append(" >").append(StringUtil.filler(String.valueOf(i+1), 2, "0")).append("월").append("</OPTION>");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return html.toString();
	}
	/**
	 * 1. HTML SELECT BOX 를 만들어 준다. 
	 * 2. 주차를 생성한다
	 * @param yyyyMMdd 기준일
	 * @return SELECT BOT TAG
	 * @throws Exception
	 */
	public static String selectOptionWeek(String yyyyMMdd) {
		StringBuffer html = new StringBuffer();
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "-", "");
		yyyyMMdd = StringUtil.replace(yyyyMMdd, "/", "");
		String[][] weeks = DateUtil.getWeekDatesForMonth(yyyyMMdd);
		String startDt = "";
		String endDt = "";
		boolean isSelected = false;
		
		for(int i=0; i<6; i++){
			if(i < weeks.length){
				startDt = weeks[i][0].substring(0, 8);
				endDt = weeks[i][1].substring(0, 8);
				if( Integer.parseInt(startDt) <= Integer.parseInt(yyyyMMdd.substring(0, 8)) &&  Integer.parseInt(endDt) >= Integer.parseInt(yyyyMMdd.substring(0, 8)) ){
					isSelected = true;
				}else{
					isSelected = false;
				}
			}
			html.append("<OPTION value='").append( (i+1) ).append("'");
			if( isSelected ){
				html.append(" selected ");
			}
			html.append(" >").append( (i+1) ).append("주차").append("</OPTION>");
		}

		return html.toString();
	}
	

	/**
	 * HTML 형식의 문자를 치환해주는 메소드
	 * 
	 * @param strText
	 *            내용
	 */
	public static String escapHTML(String strText) {
		strText = unEscapHTML(strText);
		String strInput;
		StringBuffer strOutput = new StringBuffer("");
		String convert;
		char strTmp;
		int nCount;
		if (strText == null) {
			strText = "";
		}
		strInput = strText;
		nCount = strInput.length();

		for (int i = 0; i < nCount; i++) {
			strTmp = strInput.charAt(i);
			if (strTmp == '<')
				strOutput.append("&lt;");
			else if (strTmp == '>')
				strOutput.append("&gt;");
			else if (strTmp == '&')
				strOutput.append("&amp;");
			else if (strTmp == (char) 37)
				strOutput.append("&#37;");
			else if (strTmp == (char) 34)
				strOutput.append("&quot;");
			else if (strTmp == (char) 39)
				strOutput.append("&#39;");
			else if (strTmp == '#')
				strOutput.append("&#35;");
			else if (strTmp == ' ')
				strOutput.append("&nbsp;");
			else
				strOutput.append(strTmp);
		}
		convert = strOutput.toString();
		return convert;
	}

	/**
	 * HTML 형식으로 치환했던 문자를 원복해주는 메소드
	 * 
	 * @param html
	 *            내용
	 */
	public static String unEscapHTML(String html) {
		String strOutPut = html;
		if (html == null || html.length() == 0) {
			return "";
		}
		strOutPut = StringUtil.replace(strOutPut, "&lt;", "<");
		strOutPut = StringUtil.replace(strOutPut, "&gt;", ">");
		strOutPut = StringUtil.replace(strOutPut, "&amp;", "&");
		strOutPut = StringUtil.replace(strOutPut, "&#37;", "%");
		strOutPut = StringUtil.replace(strOutPut, "&#34;", "\"");
		strOutPut = StringUtil.replace(strOutPut, "&#39;", "'");
		strOutPut = StringUtil.replace(strOutPut, "&#35;", "#");
		strOutPut = StringUtil.replace(strOutPut, "&nbsp;", " ");

		return strOutPut;
	}

	public static void historyBack(HttpServletResponse response, String msg) {
		PrintWriter out = null;
		try {
			out = response.getWriter();
			out.println("<script> alert(\"" + msg.replace('\"', '\'')
					+ "\"); history.back(-1) ; </script>");
		} catch (Exception e) {

		}
	}

	public static void historyBack(HttpServletResponse response, int his) {
		PrintWriter out = null;
		try {
			out = response.getWriter();
			out.println("<script> history.go(" + new Integer(his).toString()
					+ ") ; </script>");
		} catch (Exception e) {
		}
	}

	public static void msgBox(HttpServletResponse response, String msg) {
		PrintWriter out = null;

		try {
			out = response.getWriter();
			out.println("<script> alert(\"" + msg.replace('\"', '\'')
					+ "\");</script>");
		} catch (Exception e) {
		}
	}


	public static void msgBox(HttpServletResponse response, String msg,
			String Url) {
		PrintWriter out = null;
		try {
			out = response.getWriter();
			out.println("<script> alert(\"" + msg.replace('\"', '\'')
					+ "\"); self.location.href=('" + Url + "') ;</script>");
		} catch (Exception e) {
		}
	}

	public static void setCookie(javax.servlet.http.HttpServletResponse resp, String sName, String sValue) {
		try {
			javax.servlet.http.Cookie c = null;
			c = new javax.servlet.http.Cookie(sName, sValue);
			c.setPath("/");
			resp.addCookie(c);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return;
	}
	
	public static void setCookie(javax.servlet.http.HttpServletResponse resp, String sName, String sValue, int activeSpanDays) {
		try {
			javax.servlet.http.Cookie c = null;
			c = new javax.servlet.http.Cookie(sName, sValue);
			c.setPath("/");
			c.setMaxAge(60*60*24*activeSpanDays); 
			resp.addCookie(c);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return;
	}

	public static javax.servlet.http.Cookie getCookie(javax.servlet.http.Cookie[] cookies, String name) {
		if (cookies == null)
			return null;

		for (int i = 0; i < cookies.length; i++) {
			if (name.equals(cookies[i].getName()))
				return cookies[i];
		}
		return null;
	}
	
	
	public static String getCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return "";
		}
		for (int i = 0; i < cookies.length; i++) {
			if (cookies[i].getName().equals(name)) {
				return java.net.URLDecoder.decode(cookies[i].getValue());
			}
		}
		return "";
	}

}
