package net.dstone.common.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.dstone.common.core.BaseObject;
import net.sf.jsqlparser.util.TablesNamesFinder;

public class SqlUtil extends BaseObject {
	/**
	 * 쿼리내의 테이블명 목록을 반환한다.
	 * 1. 전체테이블목록 을 참조하여 쿼리문에서 테이블명을 추출한다.(allTblList 전체테이블목록이 존재할 경우)
	 * 2. 라이브러리로  테이블명을 추출(실패시 자체분석)한다.
	 * @param paramSql
	 * @param allTblList 전체테이블목록
	 * @return
	 * @throws Exception
	 */
	public static List<String> getTableNames(String paramSql, List<String> allTblList) throws Exception{
		List<String> tableNameList = new ArrayList<String>();
		List<String> tempList = new ArrayList<String>();
		
		// 1. 전체테이블목록 을 참조하여 쿼리문에서 테이블명을 추출한다.
		if( allTblList != null && allTblList.size()>0 ) {
			tempList = getTableNamesWithTblList(paramSql, allTblList);
			if( tempList != null ) {
				for(String tableName : tempList) {
					if( !tableNameList.contains(tableName) ) {
						tableNameList.add(tableName);
					}
				}
			}
		}
		
		// 2. 쿼리문을 라이브러리로  테이블명 추출한다(실패시 자체분석).
		tempList = getTableNames(paramSql);
		if( tempList != null ) {
			for(String tableName : tempList) {
				if( !tableNameList.contains(tableName) ) {
					tableNameList.add(tableName);
				}
			}
		}
		
		return tableNameList;
	}
	
	/**
	 * 쿼리내의 테이블명 목록을 반환한다.(라이브러리로분석)
	 * 라이브러리분석이 실해할 경우 텍스트자체분석.
	 * @param sql
	 * @return
	 * @throws Exception
	 */
	public static List<String> getTableNames(String paramSql) throws Exception{
		List<String> tableNameList = new ArrayList<String>();
		String keyword = "";
		String tableName = "";
		String[] div = {" "};
		try {
			if(!StringUtil.isEmpty(paramSql)) {
				
				String sql = paramSql; 
				sql = SqlUtil.removeCommentsFromSql(sql);
				sql = StringUtil.trimTextForParse(sql);
				sql = SqlUtil.getReadyForTableSearch(sql);
				
				/*** INSERT/UPDATE/DELETE 일 경우 테이블갯수, 테이블명위치 가 고정되어 있으므로 NEXT WORD 를 발췌해서 테이블명 추출. ***/
				// INSERT
				if(sql.startsWith("INSERT")) {
					keyword = " INTO ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						if(!tableNameList.contains(tableName)) {
							tableNameList.add(tableName);
						}
					}
				// UPDATE	
				}else if(sql.startsWith("UPDATE")) {
					keyword = "UPDATE ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						if(!tableNameList.contains(tableName)) {
							tableNameList.add(tableName);
						}
					}
				// MERGE	
				}else if(sql.startsWith("MERGE")) {
					keyword = " INTO ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						if(!tableNameList.contains(tableName)) {
							tableNameList.add(tableName);
						}
					}
				// DELETE	
				}else if(sql.startsWith("DELETE")) {
					keyword = " FROM ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						if(!tableNameList.contains(tableName)) {
							tableNameList.add(tableName);
						}
					}
				// SELECT	
				}else{
					Iterator<String> tableNames = TablesNamesFinder.findTables(sql).iterator();
					while(tableNames.hasNext()) {
						tableName = tableNames.next();
						if(!tableNameList.contains(tableName)) {
							tableNameList.add(tableName);
						}
					}
				}

			}
		} catch (Exception e) {
			//LogUtil.sysout("net.dstone.common.utils.SqlUtil.getTableNames() 수행중 예외발생. 쿼리:\n" +paramSql);
			//e.printStackTrace();
			getTableNamesByText(paramSql);
		}
		return tableNameList;
	}
	/**
	 * 쿼리내의 테이블명 목록을 반환한다.(텍스트자체분석)
	 * @param sql
	 * @return
	 */
	public static List<String> getTableNamesByText(String paramSql) throws Exception{
		List<String> tableNameList = new ArrayList<String>();
		String keyword = "";
		String tableName = "";
		String[] div = {" "};
		String sql = "";
		try {
			if(!StringUtil.isEmpty(paramSql)) {
				sql = paramSql; 
				sql = StringUtil.trimTextForParse(sql);
				sql = SqlUtil.getReadyForTableSearch(sql);
				if(sql.startsWith("INSERT")) {
					keyword = " INTO ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						tableNameList.add(tableName);
					}
				}else if(sql.startsWith("UPDATE")) {
					keyword = "UPDATE ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						tableNameList.add(tableName);
					}
				}else if(sql.startsWith("MERGE")) {
					keyword = " INTO ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						tableNameList.add(tableName);
					}
				}else if(sql.startsWith("DELETE")) {
					keyword = " FROM ";
					if(sql.indexOf(keyword)>-1) {
						tableName = StringUtil.nextWord(sql, keyword, div);
						tableNameList.add(tableName);
					}
				}else if(sql.startsWith("SELECT")) {
					String selectSql = markBrakets(paramSql);
					tableNameList.addAll(getSelectTableNamesByText(selectSql));
				}
			}
		} catch (Exception e) {
			//LogUtil.sysout("net.dstone.common.utils.SqlUtil.getTableNamesByText() 수행중 예외발생. 쿼리:\n" + paramSql);
			//e.printStackTrace();
			throw e;
		}
		return tableNameList;
	}
	
	private static String getReadyForTableSearch(String paramSql) {
		String sql = "";
		if(!StringUtil.isEmpty(paramSql)) {
			sql = paramSql; 
			sql = StringUtil.replace(sql, "\r\n", " ");
			sql = StringUtil.replace(sql, "\n", " ");
			sql = StringUtil.replace(sql, "\t", " ");
			sql = StringUtil.replace(sql, "(", "( ");
			sql = StringUtil.replace(sql, ")", " )");
			sql = StringUtil.replace(sql, ".", ". ");
			sql = StringUtil.replace(sql, "   ", " ");
			sql = StringUtil.replace(sql, "  ", " ");
			sql = sql.toUpperCase().trim();
		}
		return sql;
	}
	
	/**
	 * 쿼리내의 괄호에 마킹을 한다.
	 * @param paramSql
	 * @return
	 */
	private static String markBrakets(String paramSql) {
		StringBuffer sql = new StringBuffer();
		String div = "^";
		String divOpen = "[";
		String divClose = "]";
		String divStr = "";
		
		if( !StringUtil.isEmpty(paramSql) ) {
			char[] charArr = paramSql.toCharArray();
			int num = 0;
			for( char chr : charArr) {
				if(chr == '(') {
					num++;
					divStr = divOpen + StringUtil.repeatStr(div, num) + divClose;
					sql.append( divStr + " " );
				}else if(chr == ')') {
					divStr = divOpen + StringUtil.repeatStr(div, num) + divClose;
					sql.append(divStr + " " );
					num--;
				}else {
					sql.append(chr);
				}
			}
		}
		
		return sql.toString();
	}
	
	/**
	 * SELECT 쿼리내의 테이블명 목록을 반환한다.(텍스트자체분석)
	 * @param paramSql
	 * @return
	 */
	private static ArrayList<String> getSelectTableNamesByText(String paramSql) throws Exception {
		//LogUtil.sysout( "paramSql:"+paramSql);
		ArrayList<String> tblList = new ArrayList<String>();
		try {
			String sql = "";
			String fromPhaseStr = "";
			String beforeSql = "";
			String afterSql = "";
			String divOpen = "[";
			String divClose = "]";
			String[] div = {" "};
			
			if( !StringUtil.isEmpty(paramSql) ) {
				
				sql = paramSql; 
				sql = StringUtil.replace(sql, "UNION ALL", "UNION");
				sql = StringUtil.replace(sql, "\r\n", " ");
				sql = StringUtil.replace(sql, "\n", " ");
				sql = StringUtil.trimTextForParse(sql);
				sql = sql.toUpperCase().trim();
				
				java.util.ArrayList<String> fromEndKeywordList = new java.util.ArrayList<String>();
				fromEndKeywordList.add("WHERE");
				fromEndKeywordList.add("HAVING");
				fromEndKeywordList.add("ORDER BY");
				fromEndKeywordList.add("CONNECT BY");
				fromEndKeywordList.add("UNION");
				
				String fromKeyword = " FROM ";
				String divStr = "";
				int startIndex = -1;
				int endIndex = -1;
				
				boolean fromStarted = false;
				boolean fromEnded = false;

				int tableNum = 0;
				String nextKeyword = "";
				String nextNextKeyword = "";
				String nextNextNextKeyword = "";
				
				if( sql.startsWith("SELECT ") ) {
					fromStarted = false;
					fromEnded = false;
				}

				/*** FROM 시작(FROM ~ 이후의 쿼리문 발췌) **/
				while( sql.indexOf(fromKeyword)>-1 ) {

					fromStarted = true;
					tableNum = 0; // FROM 이후에 나열된 테이블 순서. 첫번째는 콤마 없이 시작, 이후에는 콤마로 시작. JOIN 으로 연결될 때는 콤마 없이 연결. UNION 으로 연결될 때는 콤마 없이 연결.
					sql = StringUtil.subStringAfter(sql, fromKeyword);

					/*** 1. FROM 안에 괄호()로 묶여있는부분에 대한 분리 START **/
					// FROM 안에 괄호()로 묶여있는부분에 대해 분리하는 작업을 우선적으로 해준다. 분리하여 독립적으로 재귀적인 처리.
					while(sql.indexOf(divOpen)>-1 && sql.indexOf(divClose)>-1) {
						// 구분자 추출
						startIndex = sql.indexOf(divOpen);
						endIndex = sql.indexOf(divClose)+divClose.length();
						divStr = sql.substring( startIndex,  endIndex );

						// 하위쿼리 추출
						fromPhaseStr = StringUtil.subStringAfter(sql, divStr);
						if(fromPhaseStr.indexOf(divStr)>-1) {
							fromPhaseStr = StringUtil.subStringBefore(fromPhaseStr, divStr);
							beforeSql = StringUtil.subStringBefore(sql, divStr+fromPhaseStr).trim();
							afterSql = StringUtil.subStringAfter(sql, fromPhaseStr+divStr).trim();
							if(beforeSql.endsWith(",")) {
								beforeSql = beforeSql.substring(0, beforeSql.length()-1);
							}
							sql = beforeSql + " " + afterSql;
							// 재귀호출
							ArrayList<String> subTblList = getSelectTableNamesByText(fromPhaseStr);
							for(String tbl : subTblList) {
								if(!tblList.contains(tbl)) {
									tblList.add(tbl);
								}
							}
						}else {
							throw new Exception("괄호의 Pair가 맞지 않습니다.");
						}
						sql = StringUtil.trimTextForParse(sql);
						sql = StringUtil.replace(sql, ", ,", ",");
					}
					/*** 1. FROM 안에 괄호()로 묶여있는부분에 대한 분리 END **/

					/*** 2. 테이블 발췌 START **/
					while( fromStarted && !fromEnded ) {
						nextKeyword = StringUtil.nextWord(sql, "", 0, div); 							// 첫번째 다음단어
						nextNextKeyword = StringUtil.nextWord(sql, "", 1, div);							// 두번째 다음단어
						nextNextNextKeyword = StringUtil.nextWord(sql, "", 2, div);						// 세번째 다음단어

						/*** 2-1. FROM 이후 첫번째 테이블 **/
						// 첫번째는 테이블구분 단어가 없으므로 처음단어가 테이블명.
						if( tableNum == 0 ) {
							if(!StringUtil.isEmpty(nextKeyword)) {
								tableNum++;
								if(!tblList.contains(nextKeyword)) {
									tblList.add(nextKeyword);
								}
							}
							sql = StringUtil.subStringAfter(sql, nextKeyword).trim();
						/*** 2-2. FROM 이후 두번째 이후 테이블 **/
						}else {
							/*** 2-2-1. UNION - FROM 테이블 이후 UNION 으로 이어질 경우. 알리아스가 존재할 수 있으므로 다음단어, 다다음단어까지 비교. **/
							if(nextKeyword.equals("UNION") || nextNextKeyword.equals("UNION")) {
								sql = StringUtil.subStringAfter(sql, fromKeyword).trim();				// 첫번째 다음단어 => UNION
								tableNum = 0;
								continue;
							/*** 2-2-2. COMMA - 알리아스가 존재할 수 있으므로 다음단어, 다다음단어까지 비교. **/	
							// 다음단어에 콤마가 올 경우
							}else if(nextKeyword.equals(",")) {
								if(!StringUtil.isEmpty(nextNextKeyword)) {
									tableNum++;
									if(!tblList.contains(nextNextKeyword)) {
										tblList.add(nextNextKeyword); 									// 두번째 다음단어 가 테이블
									}
									sql = StringUtil.subStringAfter(sql, nextKeyword).trim();			// 첫번째 다음단어 => 구분자(콤마)
									sql = StringUtil.subStringAfter(sql, nextNextKeyword).trim();
								}
							// 다다음단어에 콤마가 올 경우
							}else if(nextNextKeyword.equals(",")) {	
								if(!StringUtil.isEmpty(nextNextNextKeyword)) {
									tableNum++;
									if(!tblList.contains(nextNextNextKeyword)) {
										tblList.add(nextNextNextKeyword);								// 세번째 다음단어 가 테이블
									}
									sql = StringUtil.subStringAfter(sql, nextKeyword).trim();			// 첫번째 다음단어 => 앞 테이블의 알리아스
									sql = StringUtil.subStringAfter(sql, nextNextKeyword).trim(); 		// 두번째 다음단어 => 구분자(콤마)
									sql = StringUtil.subStringAfter(sql, nextNextNextKeyword).trim();
								}
							/*** 2-2-3. JOIN - (JOIN/INNER JOIN/LEFT OUTER JOIN/RIGHT OUTER JOIN/FULL OUTER JOIN)으로 이어질 경우. **/	
							}else{	
								sql = StringUtil.subStringAfter(sql, "JOIN").trim();
								String nextKeywordAfterJoin = StringUtil.nextWord(sql, "", 0, div); 	// JOIN 이후의 첫번째 다음단어
								sql = StringUtil.subStringAfter(sql, nextKeywordAfterJoin).trim();
								
								if(!StringUtil.isEmpty(nextKeywordAfterJoin)) {
									tableNum++;
									if(!tblList.contains(nextKeywordAfterJoin)) {
										tblList.add(nextKeywordAfterJoin);								// JOIN 이후의 첫번째 다음단어 가 테이블
									}
									
								}
							}
						}
						/*** 2-3. FROM 종료 처리 **/
						// FROM 이 종료 되는 문구를 만나는 경우
						//if( fromEndKeywordList.contains(nextKeyword) || (StringUtil.isEmpty(nextKeyword)&&StringUtil.isEmpty(nextNextKeyword)&&StringUtil.isEmpty(nextNextNextKeyword)) ) {
						if( fromEndKeywordList.contains(nextKeyword) ) {
							fromEnded = true;
							break;
						}
						// 쿼리가 전부 다 처리되었을 경우
						if(StringUtil.isEmpty(sql)) {
							fromEnded = true;
							break;
						}
					}
					/*** 2. 테이블 발췌 END **/
					
					if(fromEnded || StringUtil.isEmpty(sql)) {
						break;
					}
				}
			}
			
		} catch (Exception e) {
			//LogUtil.sysout("net.dstone.common.utils.SqlUtil.getSelectTableNamesByText() 수행중 예외발생. 상세사항:" + e.toString());
			//e.printStackTrace();
			//throw e;
		} 
		
		return tblList;
	}
	
	/**
	 * 전체테이블리스트로 SELECT 쿼리내의 테이블명 목록을 반환한다.(텍스트자체분석)
	 * @param paramSql 쿼리
	 * @param allTblList 전체테이블목록
	 * @return
	 */
	public static List<String> getTableNamesWithTblList(String paramSql, List<String> allTblList) throws Exception {
		//LogUtil.sysout( "paramSql:"+paramSql);
		List<String> tblList = new ArrayList<String>();
		try {

			String sql = "";
			if( !StringUtil.isEmpty(paramSql) && allTblList != null && allTblList.size()>0 ) {
				sql = SqlUtil.getReadyForTableSearch(paramSql);
				for(String tbl : allTblList) {
					tbl = tbl.toUpperCase();
					if( sql.indexOf(tbl + " ")>-1 ||  sql.indexOf(tbl + "(")>-1 ) {
						tblList.add(tbl);
					}
				}
			}
		} catch (Exception e) {
			LogUtil.sysout("net.dstone.common.utils.SqlUtil.getTableNamesWithTblList() 수행중 예외발생. 상세사항:" + e.toString());
			//e.printStackTrace();
			throw e;
		}
		
		return tblList;
	}
	
	/**
	 * 쿼리내의 주석을 제거한다.
	 * @param sql
	 * @return
	 */
	public static String removeCommentsFromSql(String sql){
		String returnSql = sql; 
		String rexp = "";
		try {
			if(!StringUtil.isEmpty(sql)) {

//				rexp = "(/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/)|'(?:[^']|'')*'|(--.*)";
//				rexp = "(?:/\\\\*[^;]*?\\\\*/)|(?:--[^;]*?$)";
				rexp = "(?s)(?:\\/\\*.*?\\*\\/|--[^\\n]*)";
				returnSql = returnSql.replaceAll(rexp, "");
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnSql;
	}

	/**
	 * 컬럼타입에 따른 파라메터 세팅 스트링을 반환.
	 * @param DATA_TYPE
	 * @param COLUMN_NAME
	 * @param dbKind(DBMS 종류. ORACLE/MSSQL/MYSQL)
	 * @return
	 */
	public static String getParamByType(String DATA_TYPE, String COLUMN_NAME, String dbKind){
		return getParamByType(DATA_TYPE, COLUMN_NAME, dbKind, "MYBATIS");
	}
	
	/**
	 * 컬럼타입에 따른 파라메터 세팅 스트링을 반환.
	 * @param DATA_TYPE
	 * @param COLUMN_NAME
	 * @param dbKind(DBMS 종류. ORACLE/MSSQL/MYSQL)
	 * @param queryKind(쿼리기술종류. MYBATIS/BASIC)
	 * @return
	 */
	public static String getParamByType(String DATA_TYPE, String COLUMN_NAME, String dbKind, String queryKind){
		String outStr = "";
		String colType = DATA_TYPE.toUpperCase();
		if(colType.indexOf(".") > -1){
			colType = colType.substring(colType.lastIndexOf("."));
		}
		if ("ORACLE".equals(dbKind)) {
			if (colType.equals("FLOAT") || colType.equals("INT") || colType.equals("DOUBLE") || colType.equals("NUMBER") || colType.equals("NUMERIC")) {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}else if (colType.equals("DATE") || colType.equals("TIME") ||  colType.startsWith("TIMESTAMP")) {
				if ( colType.equals("DATE") ) {
					if("MYBATIS".equals(queryKind)) {
						outStr = "TO_DATE( #{" + COLUMN_NAME + "}, 'YYYYMMDDHH24MISS')";
					}else {
						outStr = "TO_DATE( ?, 'YYYYMMDDHH24MISS')";
					}
				}else if ( colType.equals("Time") ) {
					if("MYBATIS".equals(queryKind)) {
						outStr = "TO_DATE( #{" + COLUMN_NAME + "}, 'YYYYMMDDHH24MISS')";
					}else {
						outStr = "TO_DATE( ?, 'YYYYMMDDHH24MISS')";
					}
				}else if ( colType.startsWith("TIMESTAMP") ) {
					if("MYBATIS".equals(queryKind)) {
						outStr = "TO_TIMESTAMP( #{" + COLUMN_NAME + "}, 'YYYYMMDDHH24MISSFF3')";
					}else {
						outStr = "TO_TIMESTAMP( ?, 'YYYYMMDDHH24MISSFF3')";
					}
				}
			} else {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}
		} else if ("MSSQL".equals(dbKind)) {
			if (colType.equals("FLOAT") || colType.equals("INT") || colType.equals("DOUBLE") || colType.equals("NUMBER") || colType.equals("NUMERIC")) {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}else if (colType.equals("DATE") || colType.equals("TIME") ||  colType.equals("TIMESTAMP")) {
				if("MYBATIS".equals(queryKind)) {
					outStr = "CONVERT(DATETIME, #{" + COLUMN_NAME + "} )";
				}else {
					outStr = "CONVERT(DATETIME, ? )";
				}
			} else {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}
		} else if ("MYSQL".equals(dbKind)) {
			if (colType.equals("FLOAT") || colType.equals("INT") || colType.equals("DOUBLE") || colType.equals("NUMBER") || colType.equals("NUMERIC")) {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}else if (colType.equals("DATE") || colType.equals("TIME") ||  colType.equals("TIMESTAMP")) {
				if("MYBATIS".equals(queryKind)) {
					outStr = "STR_TO_DATE( #{" + COLUMN_NAME + "}, '%Y%m%d%H%i%s' )";
				}else {
					outStr = "STR_TO_DATE( ?, '%Y%m%d%H%i%s' )";
				}
			} else {
				if("MYBATIS".equals(queryKind)) {
					outStr = "#{" + COLUMN_NAME + "}";
				}else {
					outStr = "?";
				}
			}
		}
		return outStr;
	}
	/**
	 * 페이징 쿼리(상단/하단) 반환 메소드.
	 * @param dbKind(DBMS 종류. ORACLE/MSSQL/MYSQL)
	 * @param upOrDown(0:상단, 1:하단)
	 * @return
	 */
	public static String getPagingQuery(String dbKind, int upOrDown){
		String pagingQuery = "";
		StringBuffer pagingUpConts = new StringBuffer();
		StringBuffer pagingLowConts = new StringBuffer();
		
		if ("ORACLE".equals(dbKind)) {
			// 상단 쿼리
			if(upOrDown == 0){
				pagingUpConts.append("		SELECT  ").append("\n");
				pagingUpConts.append("			RNUM ").append("\n");
				pagingUpConts.append("			,P.* ").append("\n");
				pagingUpConts.append("		FROM  ").append("\n");
				pagingUpConts.append("		    (  ").append("\n");
				pagingUpConts.append("		    SELECT  ").append("\n");
				pagingUpConts.append("		        ROWNUM RNUM, P1.*  ").append("\n");
				pagingUpConts.append("		    FROM  ").append("\n");
				pagingUpConts.append("		        (  ").append("\n");
				pagingUpConts.append("                 /**********************************************************************************************************************************/ ").append("\n");
			// 하단 쿼리
			}else if(upOrDown == 1){
				pagingLowConts.append("                /**********************************************************************************************************************************/ ").append("\n");
				pagingLowConts.append("		        ) P1 ").append("\n");
				pagingLowConts.append("		    )   P ").append("\n");
				pagingLowConts.append("		WHERE 2>1 ").append("\n");
				pagingLowConts.append("		    AND RNUM <![CDATA[>=]]> #{INT_FROM} ").append("\n");
				pagingLowConts.append("		    AND RNUM <![CDATA[<=]]> #{INT_TO} ").append("\n");
				pagingLowConts.append("		    AND ROWNUM <![CDATA[<=]]> #{PAGE_SIZE} ");
			}
		} else if ("MSSQL".equals(dbKind)) {
			// 상단 쿼리
			if(upOrDown == 0){
				pagingUpConts.append("		SELECT  ").append("\n");
				pagingUpConts.append("			RNUM ").append("\n");
				pagingUpConts.append("			,P.* ").append("\n");
				pagingUpConts.append("		FROM  ").append("\n");
				pagingUpConts.append("		    (  ").append("\n");
				pagingUpConts.append("		    SELECT  ").append("\n");
				pagingUpConts.append("		        /* 페이징의 기준이 되는 KEY값을 반드시 넣어주시기 바랍니다. */  ").append("\n");
				pagingUpConts.append("		        ROW_NUMBER() OVER(ORDER BY @KEY값@ DESC) AS ROWNUM RNUM, P1.*  ").append("\n");
				pagingUpConts.append("		    FROM  ").append("\n");
				pagingUpConts.append("		        (  ").append("\n");
				pagingUpConts.append("                /**********************************************************************************************************************************/ ").append("\n");
			// 하단 쿼리
			}else if(upOrDown == 1){
				pagingLowConts.append("                /**********************************************************************************************************************************/ ").append("\n");
				pagingLowConts.append("		        ) P1 ").append("\n");
				pagingLowConts.append("		    )   P ").append("\n");
				pagingLowConts.append("		WHERE 2>1 ").append("\n");
				pagingLowConts.append("		    AND RNUM <![CDATA[>=]]> #{INT_FROM} ").append("\n");
				pagingLowConts.append("		    AND RNUM <![CDATA[<=]]> #{INT_TO} ").append("\n");
				pagingLowConts.append("		    AND ROWNUM <![CDATA[<=]]> #{PAGE_SIZE} ");
			}
		} else if ("MYSQL".equals(dbKind)) {
			// 상단 쿼리
			if(upOrDown == 0){
				pagingUpConts.append("		SELECT  ").append("\n");
				pagingUpConts.append("			P.* ").append("\n");
				pagingUpConts.append("		FROM  ").append("\n");
				pagingUpConts.append("		    (  ").append("\n");
				pagingUpConts.append("            /**********************************************************************************************************************************/ ").append("\n");
			// 하단 쿼리
			}else if(upOrDown == 1){
				pagingLowConts.append("            /**********************************************************************************************************************************/ ").append("\n");
				pagingLowConts.append("		    )   P ").append("\n");
				pagingLowConts.append("		WHERE 2>1 ").append("\n");
				pagingLowConts.append("		    /* 페이징의 기준이 되는 KEY값을 반드시 넣어주시기 바랍니다. */  ").append("\n");
				pagingLowConts.append("		ORDER BY @KEY값@ LIMIT #{INT_FROM}, #{INT_TO} ").append("\n");
			}
		}
		
		// 상단 쿼리
		if(upOrDown == 0){
			pagingQuery = pagingUpConts.toString();
		// 하단 쿼리
		}else if(upOrDown == 1){
			pagingQuery = pagingLowConts.toString();
		}

		return pagingQuery;
	}
	
	/**
	 * 테이블명으로 SELECT 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getSelectSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		StringBuffer sql = new StringBuffer();
		net.dstone.common.utils.DataSet dsCols = null;
		net.dstone.common.utils.DataSet dsKeys = null;
		try {
			dsCols = net.dstone.common.utils.DbUtil.getCols(DBID, TABLE_NAME);
			dsKeys = net.dstone.common.utils.DbUtil.getKeys(DBID, TABLE_NAME);
			
			if(dsCols != null){
				String COLUMN_NAME = "";
				String COLUMN_COMMENT = "";
				String DATA_TYPE = "";
				int DATA_LENGTH = 0;
				sql.append("SELECT ").append("\n");
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						if(!StringUtil.isEmpty(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
							DATA_LENGTH = Integer.parseInt(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
						}else {
							DATA_LENGTH = 0;
						}
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append("FROM").append("\n");
				sql.append("\t").append(TABLE_NAME).append( (StringUtil.isEmpty(tableAlias)?"":" " + tableAlias) ).append("\n");
				sql.append("WHERE").append("\n");
				if (dsKeys.getDataSetRowCount("KEY_LIST") > 0) {
					for (int i = 0; i < dsKeys.getDataSetRowCount("KEY_LIST"); i++) {
						COLUMN_NAME = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsKeys.getDataSet("KEY_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append("AND ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsKeys.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append("\n");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sql.toString();
	}
	
	
	
	/**
	 * 테이블명으로 MERGE 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getMergeSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		StringBuffer sql = new StringBuffer();
		net.dstone.common.utils.DataSet dsCols = null;
		net.dstone.common.utils.DataSet dsKeys = null;
		try {
			dsCols = net.dstone.common.utils.DbUtil.getCols(DBID, TABLE_NAME);
			dsKeys = net.dstone.common.utils.DbUtil.getKeys(DBID, TABLE_NAME);
			
			if(dsCols != null){
				String COLUMN_NAME = "";
				String COLUMN_COMMENT = "";
				String DATA_TYPE = "";
				int DATA_LENGTH = 0;
				
				String div = "\n";
				sql.append("MERGE INTO ").append(TABLE_NAME).append( (StringUtil.isEmpty(tableAlias)?"":" " + tableAlias) ).append(div);
				sql.append("USING DUAL").append(div);
				sql.append("ON (").append(div);
				if (dsKeys.getDataSetRowCount("KEY_LIST") > 0) {
					for (int i = 0; i < dsKeys.getDataSetRowCount("KEY_LIST"); i++) {
						COLUMN_NAME = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsKeys.getDataSet("KEY_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append("AND ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsKeys.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append(")").append(div);
				sql.append("WHEN MATCH THEN ").append(div);
				sql.append("UPDATE SET ").append(div);
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						if(!StringUtil.isEmpty(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
							DATA_LENGTH = Integer.parseInt(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
						}else {
							DATA_LENGTH = 0;
						}
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsCols.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}

				sql.append("WHEN NOT MATCH THEN ").append(div);
				sql.append("INSERT (").append(div);
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						if(!StringUtil.isEmpty(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
							DATA_LENGTH = Integer.parseInt(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
						}else {
							DATA_LENGTH = 0;
						}
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append(") VALUES (").append(div);
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(getParamByType(DATA_TYPE, COLUMN_NAME, dsCols.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append(");").append(div);
				sql.append(div);
			
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sql.toString();
	}
	
	/**
	 * 테이블명으로 INSERT 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getInsertSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		StringBuffer sql = new StringBuffer();
		net.dstone.common.utils.DataSet dsCols = null;
		net.dstone.common.utils.DataSet dsKeys = null;
		try {
			dsCols = net.dstone.common.utils.DbUtil.getCols(DBID, TABLE_NAME);
			dsKeys = net.dstone.common.utils.DbUtil.getKeys(DBID, TABLE_NAME);
			
			if(dsCols != null){
				String COLUMN_NAME = "";
				String COLUMN_COMMENT = "";
				String DATA_TYPE = "";
				int DATA_LENGTH = 0;
				sql.append("INSERT INTO "+TABLE_NAME + (StringUtil.isEmpty(tableAlias)?"":" " + tableAlias) +" (").append("\n");
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						if(!StringUtil.isEmpty(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
							DATA_LENGTH = Integer.parseInt(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
						}else {
							DATA_LENGTH = 0;
						}
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append(") VALUES (").append("\n");
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(getParamByType(DATA_TYPE, COLUMN_NAME, dsCols.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append(");").append("\n");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sql.toString();
	}
	
	/**
	 * 테이블명으로 UPDATE 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getUpdateSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		StringBuffer sql = new StringBuffer();
		net.dstone.common.utils.DataSet dsCols = null;
		net.dstone.common.utils.DataSet dsKeys = null;
		try {
			dsCols = net.dstone.common.utils.DbUtil.getCols(DBID, TABLE_NAME);
			dsKeys = net.dstone.common.utils.DbUtil.getKeys(DBID, TABLE_NAME);
			
			if(dsCols != null){
				String COLUMN_NAME = "";
				String COLUMN_COMMENT = "";
				String DATA_TYPE = "";
				int DATA_LENGTH = 0;
				sql.append("UPDATE "+TABLE_NAME).append( (StringUtil.isEmpty(tableAlias)?"":" " + tableAlias) ).append("\n");
				sql.append("SET ").append("\n");
				if (dsCols.getDataSetRowCount("COL_LIST") > 0) {
					for (int i = 0; i < dsCols.getDataSetRowCount("COL_LIST"); i++) {
						COLUMN_NAME = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsCols.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsCols.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
						if(!StringUtil.isEmpty(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
							DATA_LENGTH = Integer.parseInt(dsCols.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
						}else {
							DATA_LENGTH = 0;
						}
						sql.append("\t");
						if(i > 0 ) {
							sql.append(", ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsCols.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append("WHERE").append("\n");
				if (dsKeys.getDataSetRowCount("KEY_LIST") > 0) {
					for (int i = 0; i < dsKeys.getDataSetRowCount("KEY_LIST"); i++) {
						COLUMN_NAME = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsKeys.getDataSet("KEY_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append("AND ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsKeys.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append("\n");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sql.toString();
	}
	/**
	 * 테이블명으로 DELETE 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getDeleteSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		StringBuffer sql = new StringBuffer();
		net.dstone.common.utils.DataSet dsCols = null;
		net.dstone.common.utils.DataSet dsKeys = null;
		try {
			
			dsCols = net.dstone.common.utils.DbUtil.getCols(DBID, TABLE_NAME);
			dsKeys = net.dstone.common.utils.DbUtil.getKeys(DBID, TABLE_NAME);
			
			if(dsCols != null){
				String COLUMN_NAME = "";
				String COLUMN_COMMENT = "";
				String DATA_TYPE = "";
				int DATA_LENGTH = 0;
				sql.append("DELETE FROM "+TABLE_NAME).append( (StringUtil.isEmpty(tableAlias)?"":" " + tableAlias) ).append("\n");
				sql.append("WHERE").append("\n");
				if (dsKeys.getDataSetRowCount("KEY_LIST") > 0) {
					for (int i = 0; i < dsKeys.getDataSetRowCount("KEY_LIST"); i++) {
						COLUMN_NAME = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_NAME");
						COLUMN_COMMENT = dsKeys.getDataSet("KEY_LIST", i).getDatum("COLUMN_COMMENT");
						DATA_TYPE = dsKeys.getDataSet("KEY_LIST", i).getDatum("DATA_TYPE");
						sql.append("\t");
						if(i > 0 ) {
							sql.append("AND ");
						}
						if(!StringUtil.isEmpty(tableAlias)) {
							sql.append(tableAlias+".");
						}
						sql.append(COLUMN_NAME).append(" = ").append(getParamByType(DATA_TYPE, COLUMN_NAME, dsKeys.getDatum("DB_KIND"), queryKind)).append(" /* "+COLUMN_COMMENT+" */").append("\n");
					}
				}
				sql.append("\n");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sql.toString();
	}
	

	/**
	 * 테이블명으로 CRUD 쿼리를 생성하는 메소드.
	 * @param DBID
	 * @param TABLE_NAME
	 * @param queryKind (BASIC/MYBATIS)
	 * @param tableAlias
	 * @return
	 */
	public static String getCrudSql(String DBID, String TABLE_NAME, String queryKind, String tableAlias) {
		
		StringBuffer sql = new StringBuffer();

		try {
			
			String div = "\n";

			sql.append("/********* 1. SELECT *********/").append(div);
			sql.append(getSelectSql(DBID, TABLE_NAME, queryKind, tableAlias)).append(div);

			sql.append("/********* 2. MERGE *********/").append(div);
			sql.append(getMergeSql(DBID, TABLE_NAME, queryKind, tableAlias)).append(div);
		
			sql.append("/********* 3. INSERT *********/").append(div);
			sql.append(getInsertSql(DBID, TABLE_NAME, queryKind, tableAlias)).append(div);
		
			sql.append("/********* 4. UPDATE *********/").append(div);
			sql.append(getUpdateSql(DBID, TABLE_NAME, queryKind, tableAlias)).append(div);
		
			sql.append("/********* 5. DELETE *********/").append(div);
			sql.append(getDeleteSql(DBID, TABLE_NAME, queryKind, tableAlias)).append(div);
		
		}catch(Exception e){
			e.printStackTrace();
		}
		return sql.toString();

	}



}
