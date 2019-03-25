package net.dstone.common.utils;

import java.text.*;
import java.util.*;

public class DateUtil {

	public static final String DATE_FORMAT_YMDHMS = "yyyyMMddHHmmss";
	public static final String DATE_FORMAT_YMD = "yyyyMMdd";
	public static final String[] WEEK_DAY = { "일", "월", "화", "수", "목", "금", "토" };

	/**
	 * 오늘 날짜를 지정된 형식으로 반환.
	 * 
	 * @param format
	 *            날짜 형식
	 * @return 변환된 날짜 문자열
	 * @throws Exception
	 */
	public static String getToDate(String format) {
		String output = "";
		try { 
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			output = sdf.format(new java.util.Date());
		} catch (Exception e) {
			// TODO: handle exception
		}
		return output;
	}

	/**
	 * 날짜에 해당하는 요일 반환.
	 * 
	 * @param date
	 *            날짜
	 * @return 요일
	 * @throws Exception
	 */
	public static String getDay(String sDate) {
		String day = "";
		try {
			int year = Integer.parseInt(sDate.substring(0, 4));
			int month = Integer.parseInt(sDate.substring(4, 6));
			int date = Integer.parseInt(sDate.substring(6, 8));

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.set(java.util.Calendar.YEAR, year);
			cal.set(java.util.Calendar.MONTH, month - 1);
			cal.set(java.util.Calendar.DATE, date);

			day = WEEK_DAY[cal.get(Calendar.DAY_OF_WEEK) - 1];
		} catch (Exception e) {
			// TODO: handle exception
		}
		return day;
	}

	/**
	 * 날짜를 입력받아 지정된 형식으로 반환
	 * 
	 * @param date
	 *            날짜
	 * @param format
	 *            날짜 형식
	 * @return 변환된 날짜 문자열
	 * @throws Exception
	 */
	public static final String getToDate(java.util.Date date, String format)
			throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(format);

		return sdf.format(date);
	}

	/**
	 * 지정된 포맷의 날짜 문자열을 java.util.Date 타입으로 변환하여 반환.
	 * 
	 * @param format
	 *            날짜 형식
	 * @param source
	 *            원본 문자열
	 * @return 변환된 날짜 타입
	 * @throws Exception
	 */
	public static final java.util.Date parseDateStr(String format, String source)
			throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(format);

		return sdf.parse(source);
	}

	public static String dateFormat(String format, String sDate) {
		if (sDate == null || "".equals(sDate)) {
			return "";
		}
		if (sDate.length() == 4) {
			sDate = sDate.substring(0, 4) + "0101010101";
		} else if (sDate.length() == 6) {
			sDate = sDate.substring(0, 6) + "01010101";
		} else if (sDate.length() == 8) {
			sDate = sDate.substring(0, 8) + "010101";
		} else if (sDate.length() == 10) {
			sDate = sDate.substring(0, 10) + "0101";
		} else if (sDate.length() == 12) {
			sDate = sDate.substring(0, 12) + "01";
		} else if (sDate.length() == 14) {
			sDate = sDate;
		}
		int year = Integer.parseInt(sDate.substring(0, 4));
		int month = Integer.parseInt(sDate.substring(4, 6));
		int date = Integer.parseInt(sDate.substring(6, 8));
		int hour = 0;
		int minute = 0;
		if (sDate.length() > 9) {
			hour = Integer.parseInt(sDate.substring(8, 10));
		}
		if (sDate.length() > 11) {
			minute = Integer.parseInt(sDate.substring(10, 12));
		}
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.set(java.util.Calendar.YEAR, year);
		cal.set(java.util.Calendar.MONTH, month - 1);
		cal.set(java.util.Calendar.DATE, date);
		if (sDate.length() > 9) {
			cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
		}
		if (sDate.length() > 11) {
			cal.set(java.util.Calendar.MINUTE, minute);
		}
		java.util.Date day = cal.getTime();

		java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
				format);
		String strDay = dateFormat.format(day);
		return strDay;

	}

	/**
	 * 해당일이 속한 달의 마지막날짜 반환
	 * 
	 * @param inData
	 *            날짜
	 * @param format
	 *            날짜 형식
	 * @return 변환된 날짜 문자열
	 * @throws Exception
	 */
	public static String getLastDate(String inData, String format)
			throws Exception {
		String lastDate = "";
		lastDate = dayCalForStr(inData, 1, 2, "yyyyMM") + "01";
		lastDate = dayCalForStr(lastDate, -1, 1, format);
		return lastDate;
	}

	/**
	 * <code>dayCalForStr</code> 날짜계산 메소드 예)dayCalForStr("20111217", 10, 1,
	 * "yyyy:MM:dd") ==> "2011:12:27" 반환
	 * 
	 * @param baseDate
	 *            날짜(yyyyMMdd 포맷)
	 * @param margin
	 *            가감하고자 하는 수치
	 * @param mode
	 *            연산모드( 1: 일단위(default, 2:월단위, 3:년단위)
	 * @param dtformat
	 *            반환결과포맷
	 * @return 연산된날짜
	 */
	public static String dayCalForStr(String baseDate, int margin, int mode,
			String dtformat) throws Exception {
		int year;
		int month;
		int date;
		if (baseDate == null || baseDate.length() != 8) {
			throw new Exception("날짜입력형식(yyyyMMdd)이 맞지 않습니다. 실제입력값[" + baseDate
					+ "]");
		}
		try {
			year = Integer.parseInt(baseDate.substring(0, 4));
			month = Integer.parseInt(baseDate.substring(4, 6));
			date = Integer.parseInt(baseDate.substring(6));
		} catch (Exception e) {
			throw new Exception("날짜입력형식(yyyyMMdd)이 맞지 않습니다. 실제입력값[" + baseDate
					+ "]");
		}
		int addMargin = margin;

		java.util.Calendar cal = java.util.Calendar.getInstance();
		java.util.Date day;

		if (mode == 3) { // 년단위
			cal.set(java.util.Calendar.YEAR, year);
			cal.set(java.util.Calendar.MONTH, month - 1);
			cal.set(java.util.Calendar.DATE, date);
			cal.add(java.util.Calendar.YEAR, addMargin);
			day = cal.getTime();
		} else if (mode == 2) { // 월단위
			cal.set(java.util.Calendar.YEAR, year);
			cal.set(java.util.Calendar.MONTH, month - 1);
			cal.set(java.util.Calendar.DATE, date);
			cal.add(java.util.Calendar.MONTH, addMargin);
			day = cal.getTime();
		} else { // 일단위(default)
			cal.set(java.util.Calendar.YEAR, year);
			cal.set(java.util.Calendar.MONTH, month - 1);
			cal.set(java.util.Calendar.DATE, date);
			cal.add(java.util.Calendar.DATE, addMargin);
			day = cal.getTime();
		}

		java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
				dtformat);
		String strDay = format.format(day);
		return strDay;
	}

	/**
	 * <code>dayCalSpan</code>
	 * 
	 * @param startDay
	 *            시작날짜
	 * @param endDay
	 *            종료날짜
	 * @return 시작날짜와 종료날짜사이의 날짜수
	 */
	public static int dayCalSpan(String startDay, String endDay) throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		try {
			return (int) ((sdf.parse(endDay).getTime() - sdf.parse(startDay).getTime()) / 1000 / 60 / 60 / 24);
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * <code>getDateBetweenSpan</code> 두날짜사이의 날짜배열들을 반환하는 메소드
	 * 
	 * @param startDay
	 *            From날짜(yyyyMMdd 포맷)
	 * @param endDay
	 *            To날짜(yyyyMMdd 포맷)
	 * @param time
	 *            시간(HHmmss 포맷)
	 * @param amPm
	 *            AM/PM
	 * @param satuYn
	 *            토요일포함여부
	 * @param sunYn
	 *            일요일포함여부
	 * @param beforeExcludeYn
	 *            현재이전날짜(시간)제외 여부. true 일 경우 결과값중에 현재날짜 이전의 날짜는 제외된다.
	 * @param dtformat
	 *            반환결과포맷
	 * @return 날짜배열
	 */
	public static String[] getDateBetweenSpan(String startDay, String endDay, String time, String amPm, boolean satuYn, boolean sunYn, boolean beforeExcludeYn, String dtformat) throws Exception {

		if (startDay == null || "".equals(startDay) || startDay.length() != 8
				|| endDay == null || "".equals(endDay) || endDay.length() != 8) {
			throw new Exception("날짜입력형식(yyyyMMdd)이 맞지 않습니다. 실제입력값 startDay["
					+ startDay + "] endDay[" + endDay + "]");
		}
		if (time == null || "".equals(time) || time.length() != 6) {
			time = getToDate("HHmmss");
		}

		int yearStart;
		int monthStart;
		int dateStart;

		int hour;
		int minute;
		int second;

		try {
			yearStart = Integer.parseInt(startDay.substring(0, 4));
			monthStart = Integer.parseInt(startDay.substring(4, 6));
			dateStart = Integer.parseInt(startDay.substring(6, 8));

			hour = Integer.parseInt(time.substring(0, 2));
			if ("PM".equalsIgnoreCase(amPm)) {
				hour = hour + 12;
			}
			minute = Integer.parseInt(time.substring(2, 4));
			second = Integer.parseInt(time.substring(4, 6));
		} catch (Exception e) {
			throw new Exception("날짜입력형식(yyyyMMdd)이 맞지 않습니다. 실제입력값 startDay["
					+ startDay + "] endDay[" + endDay + "]");
		}
		int span = dayCalSpan(startDay, endDay);
		if (span < 0) {
			throw new Exception("종료날짜는 (" + endDay + ")가 시작날짜(" + startDay
					+ ")보다 크거나 같아야 합니다.");
		}
		java.util.Calendar rightNow = java.util.Calendar.getInstance();
		java.util.Calendar cal = java.util.Calendar.getInstance();
		java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(dtformat);

		java.util.Vector v = new java.util.Vector();
		String[] days = null;
		for (int i = 0; i <= span; i++) {
			cal.set(yearStart, monthStart - 1, dateStart, hour, minute, second);
			cal.add(java.util.Calendar.DATE, i);
			if (beforeExcludeYn) {
				if (cal.before(rightNow)) {
					continue;
				}
			}
			if (!satuYn) {
				if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
					continue;
				}
			}
			if (!sunYn) {
				if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
					continue;
				}
			}
			String spanDay = format.format(cal.getTime());
			v.add(spanDay);
		}
		days = new String[v.size()];
		v.copyInto(days);
		v.clear();
		v = null;

		return days;

	}

	/**
	 * <code>getYearBetweenSpan</code> 두년도사이의 년도배열들을 반환하는 메소드
	 * 
	 * @param startYear
	 *            From년도
	 * @param endYear
	 *            To년도
	 * @return 날짜배열
	 */
	public static String[] getYearBetweenSpan(String startYear, String endYear)
			throws Exception {
		String[] years = null;
		int yearStart;
		int yearEnd;
		int yearSpan;

		try {
			if (startYear == null || "".equals(startYear)
					|| startYear.length() != 4 || endYear == null
					|| "".equals(endYear) || endYear.length() != 4) {
				throw new Exception("날짜입력형식(yyyy)이 맞지 않습니다. 실제입력값 startYear["
						+ startYear + "] endYear[" + endYear + "]");
			}

			yearStart = Integer.parseInt(startYear);
			yearEnd = Integer.parseInt(endYear);
			yearSpan = yearEnd - yearStart + 1;
			if (yearSpan < 0) {
				throw new Exception("종료날짜는 시작날짜 이후이어야 합니다. 실제입력값 startYear["
						+ startYear + "] endYear[" + endYear + "]");
			}
			years = new String[yearSpan];

			for (int i = 0; i < yearSpan; i++) {
				years[i] = String.valueOf(yearStart + i);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return years;

	}

	/**
	 * 지정된 포맷을 날짜 문자열을 비교
	 * 
	 * @param date1
	 *            날짜 문자열
	 * @param date2
	 *            날짜 문자열
	 * @param format
	 *            날짜 형식
	 * @return int 0 : date1과 date2가 같다, 1 : date1이 date2 이전, 2 : date1이 date2 이후
	 * @throws Exception
	 */
	public static int compareDates(String date1, String date2, String format)
			throws Exception {
		long d1 = Long.parseLong(net.dstone.common.utils.DateUtil.dateFormat(format, date1));
		long d2 = Long.parseLong(net.dstone.common.utils.DateUtil.dateFormat(format, date2));

		if (d1 == d2) {
			return 0;
		} else if (d1 < d2) {
			return 1;
		} else {
			return 2;
		}
	}

	public static boolean isDayInSpan(String compDt, String fromDt, String toDt, String format) throws Exception {
		boolean isDayInSpan = false;

		/*
		if(compDt != null && !"".equals(compDt) && compDt.length()==6 ){compDt = compDt.substring(0, 8);}
		if(fromDt != null && !"".equals(fromDt) && fromDt.length()==6 ){fromDt = fromDt.substring(0, 8);}
		if(toDt != null && !"".equals(toDt) && toDt.length()==6 ){toDt = toDt.substring(0, 8);}
		*/

		if(compDt != null && !"".equals(compDt) && compDt.length()>8 ){compDt = compDt.substring(0, 8);}
		if(fromDt != null && !"".equals(fromDt) && fromDt.length()>8 ){fromDt = fromDt.substring(0, 8);}
		if(toDt != null && !"".equals(toDt) && toDt.length()>8 ){toDt = toDt.substring(0, 8);}
		
		
		if(compDt!=null && fromDt!=null && toDt!=null){
			if (compareDates(compDt, fromDt, format) != 1 && compareDates(compDt, toDt, format) != 2) {
				isDayInSpan = true;
			}
		}

		return isDayInSpan;
	}

	public static String getToDate(String srcFormat, String date1,
			String targetFormat) {
		String out = "";
		try {
			java.util.Date date = parseDateStr(srcFormat, date1);
			out = getToDate(date, targetFormat);
		} catch (Exception e) {
		}
		return out;
	}

	public static String getAgeFromJuminNo(String yyyy, String strJuminNo) {
		String out = "";
		try {
			Calendar cal = Calendar.getInstance();
			int y = cal.get(Calendar.YEAR);
			if (yyyy != null && !"".equals(yyyy)) {
				y = Integer.parseInt(yyyy);
			}
			int m = cal.get(Calendar.MONTH) + 1;
			int d = cal.get(Calendar.DATE);
			int age_y = 1900 + Integer.parseInt(strJuminNo.substring(0, 2));
			int age_m = Integer.parseInt(strJuminNo.substring(2, 4));
			int age_d = Integer.parseInt(strJuminNo.substring(4, 6));
			out = String.valueOf(((y - age_y) + 1));
		} catch (Exception e) {
			out = "";
		}
		return out;
	}

	public static String getWeekToDay(String yyyymm, int week, String pattern) {
		Calendar cal = Calendar.getInstance();
		int new_yy = Integer.parseInt(yyyymm.substring(0, 4));
		int new_mm = Integer.parseInt(yyyymm.substring(4, 6));
		int new_dd = 1;
		cal.set(new_yy, new_mm - 1, new_dd);
//		// 임시 코드
//		if (cal.get(cal.DAY_OF_WEEK) == cal.SUNDAY) {
//			week = week - 1;
//		}
		cal.add(Calendar.DATE, (week - 1) * 7 + (cal.getFirstDayOfWeek() - cal .get(Calendar.DAY_OF_WEEK)));
		SimpleDateFormat formatter = new SimpleDateFormat(pattern);
		return formatter.format(cal.getTime());
	}

	public static int getWeek(String yyyymmdd, int addDay) {
		Calendar cal = Calendar.getInstance();
		int new_yy = Integer.parseInt(yyyymmdd.substring(0, 4));
		int new_mm = Integer.parseInt(yyyymmdd.substring(4, 6));
		int new_dd = Integer.parseInt(yyyymmdd.substring(6, 8));

		cal.set(new_yy, new_mm - 1, new_dd);
		cal.add(Calendar.DATE, addDay);

		int week = cal.get(Calendar.DAY_OF_WEEK);
		return week;
	}

	public static int getWeekOfYear(String yyyymmdd) {
		Locale LOCALE_COUNTRY = Locale.KOREA;
		Calendar cal = Calendar.getInstance();
		int new_yy = Integer.parseInt(yyyymmdd.substring(0, 4));
		int new_mm = Integer.parseInt(yyyymmdd.substring(4, 6));
		int new_dd = Integer.parseInt(yyyymmdd.substring(6, 8));
		cal.set(new_yy, new_mm - 1, new_dd);
		int week_of_year = cal.get(Calendar.WEEK_OF_YEAR);
		return week_of_year;
	}

	public static int getWeekOfMonth(String yyyymmdd) {
		Calendar cal = Calendar.getInstance();
		int new_yy = Integer.parseInt(yyyymmdd.substring(0, 4));
		int new_mm = Integer.parseInt(yyyymmdd.substring(4, 6));
		int new_dd = Integer.parseInt(yyyymmdd.substring(6, 8));
		cal.set(new_yy, new_mm - 1, new_dd);

		int week_of_month = cal.get(Calendar.WEEK_OF_MONTH);
		return week_of_month;
	}

	public static int getWeekCountForMonth(String yyyymm) {
		String lastDate = "";
		try {
			yyyymm = yyyymm.substring(0, 6) + "01";
			lastDate = getLastDate(yyyymm, "yyyyMMdd");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return getWeekOfMonth(lastDate);
	}

	public static int getWeekCountForMonth() {
		return getWeekCountForMonth(getToDate("yyyyMM"));
	}

	public static String[][] getWeekDatesForMonth(String yyyymm) {
		String[][] days = null;
		try {
			yyyymm = yyyymm.substring(0, 6);
			int weekCnt = getWeekCountForMonth(yyyymm);
			days = new String[weekCnt][];
			for (int i = 0; i < weekCnt; i++) {
				String[] startEnd = new String[2];
				startEnd[0] = getWeekToDay(yyyymm, i + 1, "yyyyMMdd");
				startEnd[1] = dayCalForStr(startEnd[0], 6, 1, "yyyyMMdd");
				days[i] = startEnd;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return days;
	}

	public static String[][] getWeekDatesForMonth() {
		return getWeekDatesForMonth(getToDate("yyyyMM"));
	}

	public static String[] getWeekDatesForMonth(String yyyymm, int index) {
		String[] startEnd = new String[2];
		try {
			startEnd[0] = getWeekToDay(yyyymm, index, "yyyyMMdd");
			startEnd[1] = dayCalForStr(startEnd[0], 6, 1, "yyyyMMdd");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return startEnd;
	}

	public static String[] getWeekDatesForMonth(int index) {
		return getWeekDatesForMonth(getToDate("yyyyMM"), index);
	}

	public static String[] getWeekDatesForNow() {
		return getWeekDatesForMonth(getToDate("yyyyMM"), getWeekOfMonth(getToDate("yyyyMMdd")));
	}
	
	public static String[] getWeekDatesForDate(String yyyyMMdd) {
		String[] startEnd = new String[2];
		
		if(yyyyMMdd != null && yyyyMMdd.length()>=8){
			if(yyyyMMdd.length()>8){
				yyyyMMdd = yyyyMMdd.substring(0, 8);
			}
			String[][] monWeeks = getWeekDatesForMonth (yyyyMMdd.substring(0, 6));
			if(monWeeks != null){
				for(int i=0; i<monWeeks.length; i++){
					if( Integer.parseInt(monWeeks[i][0]) <= Integer.parseInt(yyyyMMdd) && Integer.parseInt(yyyyMMdd) <= Integer.parseInt(monWeeks[i][1]) ){
						startEnd = monWeeks[i];
						break;
					}
				}
			}
		}
		return startEnd;
	}

}
