package net.dstone.common.utils;

import java.io.InputStream;
import java.io.Reader;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

public class DbUtil {
 
	public java.sql.Connection con = null;
	public java.sql.Statement stmt = null;
	public LoggableStatement pstmt = null;
	public java.sql.ResultSet rs = null;
	private DataSource ds = null;
	String sDataSource = "";

	StringBuffer strDbInfo = new StringBuffer();

	public int currentWasKind = net.dstone.common.utils.SystemUtil.getInstance().getIntProperty("CURRENT_WAS_KIND");
	public String currentDbKind = "";

	public static int INSERT = 6;
	public static int SELECT = 7;
	public static int UPDATE = 8;
	public static int DELETE = 9;

	public boolean boolViewConvert = net.dstone.common.utils.SystemUtil.getInstance().getBoolProperty("DBHANDLER_DATA_ENCODE_CONVERT_YN");

	public String[] columnNames = null; 	
	public String[] columnTypes = null; 	
	public int[] 	intColumnTypes = null;	
	public int[] 	intColumnLen = null;	

	public int intColumnCount = 0; 			
	public int intRowCount = 0; 			
	public int intTotalCount = 0; 			

	String strUser, strPasswd, strDriver, strUrl, strPort;
	String strErrCode;
	String strErrMsg;
	String strQuery;
	
	private boolean isLoggableStmtInUse = false;
	
	String strPageQuery;						
	String strPageQueryDiv;						
	String strCntQuery;							
	private boolean isPagingUse = false;		
	int intPageNum = 0;							

	/**
	 */
	public DbUtil(String gubun) {
		net.dstone.common.utils.SystemUtil info = net.dstone.common.utils.SystemUtil.getInstance();
		strDriver = info.getProperty(gubun + ".strDriver");
		strUrl = info.getProperty(gubun + ".strUrl");
		strPort = info.getProperty(gubun + ".strPort");
		strUser = info.getProperty(gubun + ".strUser");
		strPasswd = info.getProperty(gubun + ".strPasswd");
		sDataSource = info.getProperty(gubun + ".DS");
		currentDbKind = info.getProperty(gubun + ".DbKind");
		initialize();
	}
	
	private void printQuery(){
		//log.debug( this.getQuery() );
	}


	public void commit() {
		if (this.con != null) {
			try {
				if (!this.con.getAutoCommit()) {
					if (this.strErrMsg.equals("") && this.strErrCode.equals("0")) {
						this.con.commit();
					} else {
						this.con.rollback();
						throw new Exception( "DbUtil::strErrMsg[" + strErrMsg + "], strErrCode[" + strErrCode + "] commit 수행중 예외발생.");
					}
				}
			} catch (Exception e) {}
		}
	}
	public int delete() throws Exception {
		int intDeletedRow = 0;
		if (this.pstmt == null) {
			strErrCode = "5";
			strErrMsg = "delete . Query[" + getQuery() + "]. \n수행중예외발생. : PreparedStatement is null.";
			intDeletedRow = 0;
			throw new Exception(strErrMsg);
		}
		this.strQuery = pstmt.getQueryString();
		this.isLoggableStmtInUse = true;

		try {
			intDeletedRow = pstmt.executeUpdate();
		} catch (Exception se) {
			strErrCode = "5";
			strErrMsg = "delete 수행중예외발생.. Query[" + getQuery() + "]. \n수행중예외발생. : " + se.toString();
			intDeletedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intDeletedRow;
	}
	public int delete(String sql) throws Exception {
		int intDeletedRow = 0;
		this.isLoggableStmtInUse = false;
		try {
			strQuery = sql;
			release(this.stmt);
			release(this.rs);
			stmt = con.createStatement();
			intDeletedRow = stmt.executeUpdate(strQuery);
		} catch (Exception se) {
			strErrCode = "5";
			strErrMsg = "delete 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			intDeletedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intDeletedRow;
	}

	public static String fromDbQueryToKorQuery(String ss) {

		StringBuffer buff = new StringBuffer();
		java.util.StringTokenizer token = null;
		boolean isEng = true;

		char[] cc = new char[ss.length()];
		int st = 0;
		int end = 0;
		cc = ss.toCharArray();

		for (int j = 0; j < cc.length; j++) {
			if (cc[j] >= '\uAC00' && cc[j] <= '\uD7A3') {
				if (isEng) {
					buff.append(";").append(cc[j]);
				} else {
					buff.append(cc[j]);
				}
				isEng = false;
			} else {
				if (!isEng) {
					buff.append(";").append(cc[j]);
				} else {
					buff.append(cc[j]);
				}
				isEng = true;
			}
		}

		token = new java.util.StringTokenizer(buff.toString(), ";");
		buff = new StringBuffer();
		int i = 0;

		while (token.hasMoreElements()) {
			if ((i % 2) != 0) {
				buff.append(token.nextToken());
			} else {
				buff.append(DbUtil.fromDbToKor(token.nextToken()));
			}
		}
		return buff.toString();
	}

	public static String fromDbToKor(Object obj) {
		String result = "";
		if (obj != null) {
			try {
				result = new String(((String) obj).getBytes("8859_1"), "KSC5601");
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		} else
			return null;
	}

	public static String fromKorQueryToDbQuery(String ss) {

		StringBuffer buff = new StringBuffer();
		java.util.StringTokenizer token = null;
		boolean isEng = true;

		char[] cc = new char[ss.length()];
		int st = 0;
		int end = 0;
		cc = ss.toCharArray();

		for (int j = 0; j < cc.length; j++) {
			if (cc[j] >= '\uAC00' && cc[j] <= '\uD7A3') {
				if (isEng) {
					buff.append(";").append(cc[j]);
				} else {
					buff.append(cc[j]);
				}
				isEng = false;
			} else {
				if (!isEng) {
					buff.append(";").append(cc[j]);
				} else {
					buff.append(cc[j]);
				}
				isEng = true;
			}
		}

		token = new java.util.StringTokenizer(buff.toString(), ";");
		buff = new StringBuffer();
		int i = 0;

		while (token.hasMoreElements()) {
			if ((i % 2) != 0) {
				buff.append(token.nextToken());
			} else {
				buff.append(DbUtil.fromKorToDb(token.nextToken()));
			}
		}
		return buff.toString();
	}
	public static String fromKorToDb(Object obj) {
		String result = "";
		if (obj != null) {
			try {
				result = new String(((String) obj).getBytes("KSC5601"), "8859_1");
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		} else
			return null;
	}
	// Unicode 1.2 -> Unicode 2.0
	public static String fromUni12(String uni12) {
		int len = uni12.length();
		char[] out = new char[len];
		byte[] ksc = new byte[2];
		for (int i = 0; i < len; i++) {
			char c = uni12.charAt(i);
			if (c < 0x3400 && 0x4dff < c) {
				out[i] = c;
			} else if (0x3d2e <= c) 
				{
				System.err.println("Warning: Some of Unicode 1.2 hangul character was ignored.");
				out[i] = '\ufffd';
			} else 
				{
				try {
					ksc[0] = (byte) ((c - 0x3400) / 94 + 0xb0);
					ksc[1] = (byte) ((c - 0x3400) % 94 + 0xa1);
					out[i] = new String(ksc, "KSC5601").charAt(0);
				} catch (java.io.UnsupportedEncodingException ex) {
					throw new InternalError("Fatal Error: KSC5601 encoding is not supported.");
				}
			}
		}
		return new String(out);
	}
	
	public java.math.BigDecimal getBigDecimal(int index) throws Exception {

		java.math.BigDecimal output = new java.math.BigDecimal("0.00");
		try {
			if (rs == null) {
				return null;
			} else {
				output = rs.getBigDecimal(index);
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getBigDecimal(int index) 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return output;
	}

	public java.math.BigDecimal getBigDecimal(String name) throws Exception {

		java.math.BigDecimal output = new java.math.BigDecimal("0.0");
		try {
			if (rs == null) {
				return null;
			} else {
				output = rs.getBigDecimal(name);
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getBigDecimal(String name) 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return output;
	}
	public int getColumnCount() {
		return this.intColumnCount;
	}
	/**
	 */
	public String[] getColumnNames() {
		return this.columnNames;
	}
	
	public boolean getConnection() throws SQLException, Exception {

		boolean boolflag = true;

		if (this.sDataSource.trim().equals("")) {
			try {
				Class.forName(strDriver);
				con = java.sql.DriverManager.getConnection(strUrl, strUser, strPasswd);
				//getDataBaseInfo(con, net.dstone.common.utils.SystemUtil.getInstance().getIntProperty("DEBUGGER_DEBUG"));
			} catch (java.sql.SQLException se) {
				strErrCode = "2";
				strErrMsg = "getConnection() 수행중 SQLException 발생. strUrl[" + strUrl + "], strUser[" + strUser + "], strPasswd[" + strPasswd + "] \n상세사항. :" + se.getMessage();
				boolflag = false;
				throw new Exception(strErrMsg);
			} catch (Exception e) {
				strErrCode = "1";
				strErrMsg = "getConnection() 수행중 Exception 발생. strUrl[" + strUrl + "], strUser[" + strUser + "], strPasswd[" + strPasswd + "]\n상세사항. :" + e.getMessage();
				boolflag = false;
				throw new Exception(strErrMsg);
			}
			return boolflag;
		} else {
			if (ds == null) {

				java.util.Hashtable props = new java.util.Hashtable();
				InitialContext ctx = null;
				try {
					if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBSPHERE").trim())) {
						props.put(Context.PROVIDER_URL, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL"));
						props.put(Context.INITIAL_CONTEXT_FACTORY, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY"));
						ctx = new InitialContext(props);
					} else if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBLOGIC").trim())) {
						props.put(Context.PROVIDER_URL, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL"));
						props.put(Context.INITIAL_CONTEXT_FACTORY, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY"));
						ctx = new InitialContext(props);
					} else {
						ctx = new InitialContext();
					}
					ds = (DataSource) ctx.lookup(sDataSource);
					if (ds == null) {
						throw new Exception("데이터소스가 null 입니다.  DataSource["+sDataSource+"] lookup 실패.");
					}
					if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBSPHERE").trim())) {
						con = ds.getConnection(strUser, strPasswd);
					} else if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBLOGIC").trim())) {
						con = ds.getConnection();
					} else {
						con = ds.getConnection();
					}
					if (con == null) {
						throw new Exception("DataSource [" + sDataSource + "] 에서 커넥션 생성 실패.");
					}
				} catch (java.sql.SQLException se) {
					boolflag = false;
					strErrCode = "2";
					strErrMsg = "DataSource lookup실패[" + sDataSource + "] (SQLException) \n";
					strErrMsg = "PROVIDER_URL[" + net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL") + "] \n";
					strErrMsg = "INITIAL_CONTEXT_FACTORY[" + net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY") + "] \n";
					strErrMsg = strErrMsg + "user=[" + strUser + "], passwd=[" + strPasswd + "], url=[" + strUrl + "]\n";
					strErrMsg = strErrMsg + "수행중예외발생. :" + se.toString();
				} catch (Exception e) {
					strErrCode = "2";
					strErrMsg = "DataSource lookup실패[" + sDataSource + "] (Exception) \n";
					strErrMsg = "PROVIDER_URL[" + net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL") + "] \n";
					strErrMsg = "INITIAL_CONTEXT_FACTORY[" + net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY") + "] \n";
					strErrMsg = strErrMsg + "user=[" + strUser + "], passwd=[" + strPasswd + "], url=[" + strUrl + "]\n";
					strErrMsg = strErrMsg + "수행중예외발생. :" + e.toString();
				} finally {
					if (ctx != null) {
						try {
							ctx.close();
						} catch (Exception e) {}
					}
					if (!strErrCode.equals("0")) {
						throw new Exception(strErrMsg);
					}
				}
			}

			return boolflag;
		}
	}
	public boolean getConnection(boolean bAutoCommit) throws SQLException, Exception {

		boolean boolflag = true;

		if (this.sDataSource.trim().equals("")) {
			try {
				Class.forName(strDriver);
				con = java.sql.DriverManager.getConnection(strUrl, strUser, strPasswd);
				con.setAutoCommit(bAutoCommit);
			} catch (java.sql.SQLException se) {
				strErrCode = "2";
				strErrMsg = "getConnection("+bAutoCommit+") 수행중 SQLException 발생. strUrl[" + strUrl + "], strUser[" + strUser + "], strPasswd[" + strPasswd + "] \n상세사항. :" + se.getMessage();
				boolflag = false;
				throw new Exception(strErrMsg);
			} catch (Exception e) {
				strErrCode = "1";
				strErrMsg = "getConnection("+bAutoCommit+") 수행중 Exception 발생. strUrl[" + strUrl + "], strUser[" + strUser + "], strPasswd[" + strPasswd + "] \n상세사항. :" + e.getMessage();
				boolflag = false;
				throw new Exception(strErrMsg);
			}
			return boolflag;
		} else {
			if (ds == null) {

				java.util.Hashtable props = new java.util.Hashtable();
				InitialContext ctx = null;
				try {
					if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBSPHERE").trim())) {
						props.put(Context.PROVIDER_URL, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL"));
						props.put(Context.INITIAL_CONTEXT_FACTORY, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY"));
						ctx = new InitialContext(props);
					} else if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBLOGIC").trim())) {
						props.put(Context.PROVIDER_URL, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_PROVIDER_URL"));
						props.put(Context.INITIAL_CONTEXT_FACTORY, net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_INITIAL_CONTEXT_FACTORY"));
						ctx = new InitialContext(props);
					} else {
						ctx = new InitialContext();
					}
					ds = (DataSource) ctx.lookup(sDataSource);

					if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBSPHERE").trim())) {
						con = ds.getConnection(strUser, strPasswd);
					} else if (net.dstone.common.utils.SystemUtil.getInstance().getProperty("CURRENT_WAS_KIND").trim().equals(net.dstone.common.utils.SystemUtil.getInstance().getProperty("WEBLOGIC").trim())) {
						con = ds.getConnection();
					} else {
						con = ds.getConnection();
					}
					con.setAutoCommit(bAutoCommit);
				} catch (java.sql.SQLException se) {
					boolflag = false;
					strErrCode = "2";
					strErrMsg = "DataSource lookup실패[" + sDataSource + "] (SQLException) \n";
					strErrMsg = strErrMsg + "user=[" + strUser + "], passwd=[" + strPasswd + "], url=[" + strUrl + "]\n";
					strErrMsg = strErrMsg + "수행중예외발생. :" + se.toString();
				} catch (Exception e) {
					strErrCode = "2";
					strErrMsg = "DataSource lookup실패[" + sDataSource + "] (Exception) \n";
					strErrMsg = strErrMsg + "user=[" + strUser + "], passwd=[" + strPasswd + "], url=[" + strUrl + "]\n";
					strErrMsg = strErrMsg + "수행중예외발생. :" + e.toString();
				} finally {
					if (ctx != null) {
						try {
							ctx.close();
						} catch (Exception e) {}
					}
					if (!strErrCode.equals("0")) {
						throw new Exception(strErrMsg);
					}
				}
			}

			return boolflag;
		}
	}

	public String getDate(int index, String format) throws Exception {

		String strOutput = "";
		java.sql.Date date = null;
		try {
			if (rs == null) {
				return null;
			} else {
				if (rs.getDate(index) != null) {
					date = rs.getDate(index);
					java.text.SimpleDateFormat formt = new java.text.SimpleDateFormat(format);
					strOutput = formt.format(date);
				}
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getDate(" + index + ",  '" + format + "')수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return strOutput;
	}

	public String getDate(String name, String format) throws Exception {

		String strOutput = "";
		java.sql.Date date = null;
		try {
			if (rs == null) {
				return null;
			} else {
				if (rs.getDate(name) != null) {
					date = rs.getDate(name);
					java.text.SimpleDateFormat formt = new java.text.SimpleDateFormat(format);
					strOutput = formt.format(date);
				}
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getDate('" + name + "', '" + format + "') 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return strOutput;
	}

	public String getDatum(int index) throws Exception {

		String strOutput = "";
		try {
			if (rs == null) {
				return null;
			} else {
				strOutput = (rs.getString(index) == null ? "" : rs.getString(index));
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_6";
			strErrMsg = "getDatum(" + index + ") 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {
			if (boolViewConvert) {
				strOutput = DbUtil.fromDbToKor(strOutput);  
			}
		}
		return strOutput;
	}
	
	public int getType(String strColumnName) throws Exception {
		int intType = Integer.MIN_VALUE;
		try {
			if (rs != null) {
				for(int i=0; i<this.columnNames.length;i++){
					if(columnNames[i].equalsIgnoreCase(strColumnName)){
						intType = intColumnTypes[i];
						break;
					}
				}
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_6";
			strErrMsg = "getType(" + strColumnName + ") 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		}
		return intType;
	}	

	public String getDatum(String name) throws Exception {
		String strOutput = "";
		try {
			if (rs == null) {
				return null;
			} else {
				strOutput = (rs.getString(name) == null ? "" : rs.getString(name));
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_5";
			strOutput = "getDatum('" + name + "') 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {
			if (boolViewConvert) {
				strOutput = DbUtil.fromDbToKor(strOutput); 
			}
		}
		return strOutput;
	}
	public String getErrCode() {
		return this.strErrCode;
	}
	public String getErrMsg() {
		return this.strErrMsg;
	}
	public void getDataBaseInfo(Connection conn, int debugLevel) {
		if (net.dstone.common.utils.SystemUtil.getInstance().getBoolProperty("DBHANDLER_GET_DBINFO_YN")) {
			try {
				java.sql.DatabaseMetaData dbMetaData = conn.getMetaData();
				strDbInfo = new StringBuffer();
				String strTemp = "";
				int intTemp = 0;
				strDbInfo.append("/*********************** DATA BASE INFOMATION **********************/").append("\r\n");
				try {
					strTemp = dbMetaData.getDatabaseProductName();
					strDbInfo.append("Database Product Name \r\n<").append(strTemp).append(">\r\n");
				} catch (Throwable e) {}
				try {
					strTemp = dbMetaData.getDatabaseProductVersion();
					strDbInfo.append("Database Product Version \r\n<").append(strTemp).append("> \r\n");
				} catch (Throwable e) {}
				try {
					intTemp = dbMetaData.getDefaultTransactionIsolation();
					strDbInfo.append("Database Default Transaction Isolation [").append(getStrIsolationLevel(intTemp)).append("] \r\n");
				} catch (Throwable e) {}
				try {
					intTemp = dbMetaData.getDriverMajorVersion();
					strDbInfo.append("Driver   Major   Version [").append(intTemp).append("] \r\n");
				} catch (Throwable e) {}
				try {
					intTemp = dbMetaData.getDriverMinorVersion();
					strDbInfo.append("Driver   Minor   Version [").append(intTemp).append("] \r\n");
				} catch (Throwable e) {}
				try {
					strTemp = dbMetaData.getDriverName();
					strDbInfo.append("Driver   Name [").append(strTemp).append("] \r\n");
				} catch (Throwable e) {}
				try {
					strTemp = dbMetaData.getDriverVersion();
					strDbInfo.append("Driver   Version [").append(strTemp).append("] \r\n");
				} catch (Throwable e) {}
				try {
					strTemp = dbMetaData.getURL();
					strDbInfo.append("URL [").append(strTemp).append("] \r\n");
				} catch ( Throwable e) {}
				try {
					strTemp = dbMetaData.getUserName();
					strDbInfo.append("User     Name [").append(strTemp).append("] \r\n");
				} catch (Throwable e) {}
				strDbInfo.append("/*******************************************************************/").append("\r\n");
			} catch (Exception e) {
				System.out.println("getDataBaseInfo 수행중예외발생. 상세사항:" + e.toString());
			}
		}
	}

	public int getInt(int index) throws Exception {

		int intOutput = 0;
		try {
			intOutput = rs.getInt(index);
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getInt(" + index + ") 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return intOutput;
	}

	public int getInt(String name) throws Exception {

		int intOutput = 0;
		try {
			intOutput = rs.getInt(name);
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getInt('" + name + "') 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {
			return intOutput;
		}
	}
	public String getQuery() {
		if (isLoggableStmtInUse) {
			return this.pstmt.getQueryString();
		} else {
			return this.strQuery;
		}
	}
	public int getRowCount() {
		return this.intRowCount;
	}

	public String getString(int index) throws Exception {

		String strOutput = "";
		try {
			if (rs == null) {
				return null;
			} else {
				strOutput = (rs.getString(index) == null ? "" : rs.getString(index));
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getString(" + index + ") 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {
			if (boolViewConvert) {
				strOutput = DbUtil.fromDbToKor(strOutput);  
			}
		}
		return strOutput;
	}

	public String getString(String name) throws Exception {

		String strOutput = "";
		try {
			if (rs == null) {
				return null;
			} else {
				strOutput = (rs.getString(name) == null ? "" : rs.getString(name));
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getString('" + name + "') 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {
			if (boolViewConvert) {
				strOutput = DbUtil.fromDbToKor(strOutput); 
			}
		}
		return strOutput;
	}
	public String getTimestamp(int index) throws Exception {

		String strOutput = "";
		java.sql.Timestamp time = null;
		try {
			if (rs == null) {
				return null;
			} else {
				if (rs.getTimestamp(index) != null) {
					time = rs.getTimestamp(index);
					strOutput = time.toString();
				}
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getTimestamp(" + index + ") 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return strOutput;
	}

	public String getTimestamp(String name) throws java.lang.Exception {

		String strOutput = "";
		java.sql.Timestamp time = null;
		try {
			if (rs == null) {
				return null;
			} else {
				if (rs.getTimestamp(name) != null) {
					time = rs.getTimestamp(name);
					strOutput = time.toString();
				}
			}
		} catch (java.lang.Exception e) {
			strErrCode = "6_4";
			strErrMsg = "getTimestamp(" + name + " ) 수행중예외발생. \n상세사항. : " + e.toString();
			throw new Exception(strErrMsg);
		} finally {}
		return strOutput;
	}
	private void initialize() {
		strErrCode = "0";  
		strErrMsg = "";  
		strQuery = "";
		strPageQuery = "";
		strPageQueryDiv = "";
		strCntQuery = "";
	}
	public int insert() throws Exception {
		int intInsertedRow = 0;
		if (this.pstmt == null) {
			strErrCode = "5";
			strErrMsg = "insert 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : PreparedStatement is null.";
			intInsertedRow = 0;
			throw new Exception(strErrMsg);
		}
		this.strQuery = pstmt.getQueryString();
		this.isLoggableStmtInUse = true;

		try {
			intInsertedRow = pstmt.executeUpdate();
		} catch (java.sql.SQLException se) {
			strErrCode = "3";
			strErrMsg = "insert 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			intInsertedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intInsertedRow;
	}
	public int insert(String sql) throws Exception {
		int intInsertedRow = 0;
		this.isLoggableStmtInUse = false;
		try {
			strQuery = sql;
			release(this.stmt);
			release(this.rs);
			stmt = con.createStatement();
			intInsertedRow = stmt.executeUpdate(strQuery);
		} catch (java.sql.SQLException se) {
			strErrCode = "3";
			strErrMsg = "insert 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			intInsertedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intInsertedRow;
	}
	public void release() {
		try {

			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
			if (pstmt != null) {
				pstmt.close();
			}
			if (con != null) {
				con.close();
			}

		} catch (java.sql.SQLException se) {
			strErrCode = "7";
			strErrMsg = "closing 수행중예외발생." ;
		} catch (java.lang.Exception se) {
			strErrCode = "7_1";
			strErrMsg = "closing 수행중예외발생." ;
		}
	}

	public void release(Object obj) {
		try {
			if (obj != null) {
				if (obj instanceof Connection) {
					try {
						((Connection) obj).close();
						obj = null;
					} catch (Exception e) {}
				} else if (obj instanceof Statement) {
					try {
						((Statement) obj).close();
						obj = null;
					} catch (Exception e) {}
				} else if (obj instanceof PreparedStatement) {
					try {
						((PreparedStatement) obj).close();
						obj = null;
					} catch (Exception e) {}
				} else if (obj instanceof LoggableStatement) {
					try {
						((LoggableStatement) obj).close();
						obj = null;
					} catch (Exception e) {}
				}
			}
		} catch (java.lang.Exception se) {
			strErrCode = "7_1";
			strErrMsg = "closing 수행중예외발생." ;
		}
	}
	public static String replace(String str, String pattern, String replace) {

		int s = 0; 
		int e = 0; 
		StringBuffer result = new StringBuffer();  

		while ((e = str.indexOf(pattern, s)) >= 0) {
			result.append(str.substring(s, e));
			result.append(replace);
			s = e + pattern.length();
		}
		result.append(str.substring(s));

		return result.toString();
	}
	public void rollBack() {
		if (this.con != null) {
			try {
				if (!this.con.getAutoCommit()) {
					this.con.rollback();
				}
			} catch (Exception e) {}
		}
	}
	public java.sql.ResultSet select() throws Exception {
		if (this.pstmt == null) {
			strErrCode = "6";
			strErrMsg = "select 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : PreparedStatement is null .";
			throw new Exception(strErrMsg);
		}

		try {
			if(this.isPagingUse){
				setParamForPaging(this.pstmt);
			}
			this.strQuery = pstmt.getQueryString();
			this.isLoggableStmtInUse = true;
			rs = pstmt.executeQuery();

			java.sql.ResultSetMetaData rsMeta = rs.getMetaData();
			columnNames = new String[rsMeta.getColumnCount()];
			columnTypes = new String[rsMeta.getColumnCount()];
			intColumnTypes = new int[rsMeta.getColumnCount()];
			intColumnLen = new int[rsMeta.getColumnCount()];
			this.intColumnCount = rsMeta.getColumnCount();
			for (int i = 0; i < columnNames.length; i++) {
				columnNames[i] = rsMeta.getColumnName(i + 1).toUpperCase();
				columnTypes[i] = getType(rsMeta.getColumnType(i + 1));
				intColumnTypes[i] = rsMeta.getColumnType(i + 1);
				intColumnLen[i] = rsMeta.getColumnDisplaySize(i + 1);
			}

		} catch (java.sql.SQLException se) {
			strErrCode = "6";
			strErrMsg = "select 수행중예외발생. Query[" + getQuery() + "]. \n상세사항. : " + se.toString();
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return rs;
	}
	

	public java.sql.ResultSet select(String sql) throws Exception {
		this.isLoggableStmtInUse = false;
		try {
			strQuery = sql;
			
			stmt = con.createStatement();
			rs = stmt.executeQuery(this.strQuery);

			java.sql.ResultSetMetaData rsMeta = rs.getMetaData();
			columnNames = new String[rsMeta.getColumnCount()];
			columnTypes = new String[rsMeta.getColumnCount()];
			intColumnTypes = new int[rsMeta.getColumnCount()];
			intColumnLen = new int[rsMeta.getColumnCount()];
			this.intColumnCount = rsMeta.getColumnCount();
			for (int i = 0; i < columnNames.length; i++) {
				columnNames[i] = rsMeta.getColumnName(i + 1).toUpperCase();
				columnTypes[i] = getType(rsMeta.getColumnType(i + 1));
				intColumnTypes[i] = rsMeta.getColumnType(i + 1);
				intColumnLen[i] = rsMeta.getColumnDisplaySize(i + 1);
			}

		} catch (java.sql.SQLException se) {
			strErrCode = "6";
			strErrMsg = "select 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return rs;
	}
	public void setAutoCommit(boolean bAutoCommit) {
		try {
			if (bAutoCommit) {
				if (!this.con.getAutoCommit()) {
					this.con.setAutoCommit(bAutoCommit);
				}
			} else {
				if (this.con.getAutoCommit()) {
					this.con.setAutoCommit(bAutoCommit);
				}
			}
		} catch (Exception e) {}
	}
	public PreparedStatement setQuery(String sql) throws Exception {
		if (this.con == null) {
			strErrCode = "6_4";
			strErrMsg = "커넥션이 null 입니다.";
			throw new Exception(strErrMsg);
		}

		release(this.pstmt);
		release(this.rs);

		this.pstmt = new LoggableStatement(this.con, sql.toUpperCase());
		this.isPagingUse = false;
		this.intPageNum = 0;
		return this.pstmt;
	}
	
	public PreparedStatement setPagingQuery(String sql, int intPageNum) throws Exception {
		if (this.con == null) {
			strErrCode = "6_4";
			strErrMsg = "커넥션이 null 입니다.";
			throw new Exception(strErrMsg);
		}

		release(this.pstmt);
		release(this.rs);

		if (currentDbKind.toUpperCase().startsWith("DBAPP")) {
		} else if (currentDbKind.toUpperCase().startsWith("DB2NET")) {
		} else if (currentDbKind.toUpperCase().startsWith("ORACLE")) {
			this.strPageQuery = getPagingSqlForOracle(sql);
		} else if (currentDbKind.toUpperCase().startsWith("MSSQL")) {
		} else if (currentDbKind.toUpperCase().startsWith("MYSQL")) {

		}

		this.pstmt = new LoggableStatement(this.con, this.strPageQuery.toUpperCase());
		this.isPagingUse = true;
		this.intPageNum = intPageNum;
		return this.pstmt;
	}
	
	private String getPagingSqlForOracle(String sql) throws Exception {
		StringBuffer newSql = new StringBuffer();
		try {
			strPageQueryDiv = "--@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@--";
			//newSql.append(" SELECT /*+ FIRST_ROWS */ *            	").append("\r\n");
			newSql.append(" SELECT  *            					").append("\r\n");
			newSql.append(" FROM (                                	").append("\r\n");
			newSql.append("        SELECT ROWNUM RNUM, P1.*       	").append("\r\n");
			newSql.append("        FROM (                         	").append("\r\n\r\n");
			
			newSql.append("").append(strPageQueryDiv).append(" ").append("\r\n");
			newSql.append("").append(sql).append("  	").append("\r\n");
			newSql.append("").append(strPageQueryDiv).append(" ").append("\r\n\r\n");
			
			newSql.append("              ) P1                     	").append("\r\n");
			newSql.append("       )                               	").append("\r\n");
			newSql.append(" WHERE RNUM >= ?         				").append("\r\n");
			newSql.append("       AND RNUM <= ?       				").append("\r\n");
			newSql.append("       AND ROWNUM <= ?   				").append("\r\n");
					
		} catch (Exception e) {
			throw new Exception(strErrMsg);
		}
		return newSql.toString();
	}
	
	private void setParamForPaging(LoggableStatement pstmt) throws Exception {
		String cntSql = new String(pstmt.getQueryString());
		try {
			cntSql = pstmt.getQueryString().substring( pstmt.getQueryString().indexOf(strPageQueryDiv)+strPageQueryDiv.length(),  cntSql.lastIndexOf(strPageQueryDiv));
			this.intTotalCount = getTotalCount(cntSql);
			if (intPageNum > 0 && intTotalCount > 0 && intPageNum > ((intTotalCount - 1) / PageUtil.DEFAULT_PAGE_SIZE) + 1) {
				intPageNum = ((intTotalCount - 1) / PageUtil.DEFAULT_PAGE_SIZE) + 1;
			}
			int intFrom = (intPageNum * PageUtil.DEFAULT_PAGE_SIZE) - PageUtil.DEFAULT_PAGE_SIZE + 1;
			int intTo = intPageNum * PageUtil.DEFAULT_PAGE_SIZE;
			
			int intQuestMarkCnt = StringUtil.countString( pstmt.sqlTemplate, "?" );
			
			pstmt.setInt(intQuestMarkCnt-2, intFrom);
			pstmt.setInt(intQuestMarkCnt-1, intTo);
			pstmt.setInt(intQuestMarkCnt, PageUtil.DEFAULT_PAGE_SIZE);
					
		} catch (Exception e) {
			throw new Exception(strErrMsg);
		}
	}	
	
	

	/**
	 */
	private int getTotalCount(String sqlQuery) throws Exception {
		intTotalCount = 0;
		String sql = sqlQuery;
		if (sql == null) {
			throw new NullPointerException();
		}

		try {
			String upperQuery = sql.toUpperCase();
			if (upperQuery.lastIndexOf("ORDER BY") > 0) {
				int intLastIndexOfOrderBy = upperQuery.lastIndexOf("ORDER BY");
				if(upperQuery.substring(intLastIndexOfOrderBy).indexOf(")") == -1) {
					sql = sql.substring(0, upperQuery.lastIndexOf("ORDER BY"));
				}
			}
			StringBuffer newSql = new StringBuffer();
			if (upperQuery.indexOf("GROUP BY") > 0) {
				newSql.append(" SELECT /*+ RULE */ COUNT(*)   \r\n").append("   FROM (  \r\n").append(sql).append("  )  \r\n");
			} else {
				sql = sql.substring(upperQuery.indexOf("FROM"), sql.length());
				newSql.append(" SELECT /*+ RULE */ COUNT(*)   \r\n").append("   " + sql );
			}
			this.stmt = con.createStatement();
			this.rs = stmt.executeQuery(newSql.toString());

			if (rs != null && rs.next()) {
				if (rs.getInt(1) > 0){
					intTotalCount = rs.getInt(1);
				}
			}
		} catch (Exception ex) {
			strErrMsg = ex.toString();
			throw new Exception(strErrMsg);
		} finally{
			release(this.stmt);
		}
		return intTotalCount;
	}
	
	public int update() throws Exception {

		int intUpdatedRow = 0;
		if (this.pstmt == null) {
			strErrCode = "4";
			strErrMsg = "update 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : PreparedStatement is null.";
			intUpdatedRow = 0;
			throw new Exception(strErrMsg);
		}
		this.strQuery = pstmt.getQueryString();
		this.isLoggableStmtInUse = true;

		try {
			intUpdatedRow = pstmt.executeUpdate();
		} catch (java.sql.SQLException se) {
			strErrCode = "4";
			strErrMsg = "update 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			intUpdatedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intUpdatedRow;
	}
	public int update(String sql) throws Exception {
		int intUpdatedRow = 0;
		this.isLoggableStmtInUse = false;
		try {
			strQuery = sql;
			release(this.stmt);
			release(this.rs);
			stmt = con.createStatement();
			intUpdatedRow = stmt.executeUpdate(strQuery);
		} catch (java.sql.SQLException se) {
			strErrCode = "4";
			strErrMsg = "update 수행중예외발생. Query[" + getQuery() + "]. \n상세사항 : " + se.toString();
			intUpdatedRow = 0;
			throw new Exception(strErrMsg);
		} finally {
			printQuery();
		}
		return intUpdatedRow;
	}

	public static String getType(int i) throws SQLException {
		String s = "";
		switch (i) {
			case 2003 :
				s = "String";
				break;

			case -5 :
				s = "int ";
				break;

			case -2 :
				s = "java.sql.Date";
				break;

			case -7 :
				s = "int";
				break;

			case 2004 :
				s = "String";
				break;

			case 1 : // '\001'
				s = "String";
				break;

			case 2005 :
				s = "String";
				break;

			case 91 : // '['
				s = "java.sql.Date";
				break;

			case 3 : // '\003'
				s = "double";
				break;

			case 8 : // '\b'
				s = "double";
				break;

			case 6 : // '\006'
				s = "float";
				break;

			case 4 : // '\004'
				s = "int";
				break;

			case -4 :
				s = "int";
				break;

			case -1 :
				s = "String";
				break;

			case 0 : // '\0'
				s = "String";
				break;

			case 2 : // '\002'
				s = "int";
				break;

			case 1111 :
				s = "String";
				break;

			case 5 : // '\005'
				s = "int";
				break;

			case 92 : // '\\'
				s = "java.sql.Time";
				break;

			case 93 : // ']'
				s = "java.sql.Timestamp";
				break;

			case -6 :
				s = "int";
				break;

			case -3 :
				s = "int";
				break;

			case 12 : // '\f'
				s = "String";
				break;

			default :
				s = "String";
				break;
		}
		if (s.equals("")) {
			throw new SQLException("[" + i + "] getType 수행중예외발생. ["+i+"]은 관리대상 외의 타입입니다.");
		} else {
			return s;
		}
	}
	
	public static String getTypeStr(int i) throws SQLException {
		String s = "";
		switch (i) {
			case 2003:s="java.sql.Types.ARRAY";break;
			case -5:s="java.sql.Types.BIGINT";break;
			case -2:s="java.sql.Types.BINARY";break;
			case -7:s="java.sql.Types.BIT";break;
			case 2004:s="java.sql.Types.BLOB";break;
			case 16:s="java.sql.Types.BOOLEAN";break;
			case 1:s="java.sql.Types.CHAR";break;
			case 2005:s="java.sql.Types.CLOB";break;
			case 70:s="java.sql.Types.DATALINK";break;
			case 91:s="java.sql.Types.DATE";break;
			case 3:s="java.sql.Types.DECIMAL";break;
			case 2001:s="java.sql.Types.DISTINCT";break;
			case 8:s="java.sql.Types.DOUBLE";break;
			case 6:s="java.sql.Types.FLOAT";break;
			case 4:s="java.sql.Types.INTEGER";break;
			case 2000:s="java.sql.Types.JAVA_OBJECT";break;
			case -4:s="java.sql.Types.LONGVARBINARY";break;
			case -1:s="java.sql.Types.LONGVARCHAR";break;
			case 0:s="java.sql.Types.NULL";break;
			case 2:s="java.sql.Types.NUMERIC";break;
			case 1111:s="java.sql.Types.OTHER";break;
			case 7:s="java.sql.Types.REAL";break;
			case 2006:s="java.sql.Types.REF";break;
			case 5:s="java.sql.Types.SMALLINT";break;
			case 2002:s="java.sql.Types.STRUCT";break;
			case 92:s="java.sql.Types.TIME";break;
			case 93:s="java.sql.Types.TIMESTAMP";break;
			case -6:s="java.sql.Types.TINYINT";break;
			case -3:s="java.sql.Types.VARBINARY";break;
			case 12:s="java.sql.Types.VARCHAR";break;
			default : s = "String "; break;
		}
		if (s.equals("")) {
			throw new SQLException("[" + i + "] getTypeStr 수행중예외발생. ["+i+"]은 관리대상 외의 타입입니다.");
		} else {
			return s;
		}
	}

	public String getDbInfo() {
		return this.strDbInfo.toString();
	}

	private static String getStrIsolationLevel(int isolationLevel) {
		String strOut = "";
		if (java.sql.Connection.TRANSACTION_NONE == isolationLevel) {
			strOut = "TRANSACTION_NONE";
		} else if (java.sql.Connection.TRANSACTION_READ_COMMITTED == isolationLevel) {
			strOut = "TRANSACTION_READ_COMMITTED";
		} else if (java.sql.Connection.TRANSACTION_READ_UNCOMMITTED == isolationLevel) {
			strOut = "TRANSACTION_READ_UNCOMMITTED";
		} else if (java.sql.Connection.TRANSACTION_REPEATABLE_READ == isolationLevel) {
			strOut = "TRANSACTION_REPEATABLE_READ";
		} else if (java.sql.Connection.TRANSACTION_SERIALIZABLE == isolationLevel) {
			strOut = "TRANSACTION_SERIALIZABLE";
		}
		return strOut;
	}
	
	public PageUtil getPager(int intPageNum) throws Exception {
		PageUtil pager = null;
		if(this.isPagingUse){
			try{
				pager = new PageUtil(intPageNum, PageUtil.DEFAULT_PAGE_SIZE, this.intTotalCount);
				//if(pager == null){
				//	pager = new Pager(1, Pager.PAGE_SIZE, 0);
				//}
			}catch(Exception e){
				strErrMsg = e.toString();
				throw new Exception(strErrMsg);
			}
		}
		return pager;
	}	

	public class LoggableStatement implements PreparedStatement {

	    /**
	     * used for storing parameter values needed for producing log
	     */
	    private ArrayList parameterValues;

	    /**
	     *the query string with question marks as parameter placeholders
	     */
	    public String sqlTemplate;

	    /**
	     *  a statement created from a real database connection
	     */
	    private PreparedStatement wrappedStatement;

	    /**
	    	* Constructs a LoggableStatement.
	    	*
	    	* Creates {@link java.sql.PreparedStatement PreparedStatement} with the query string <code>sql</code> using
	    	* the specified <code>connection</code> by calling {@link java.sql.Connection#prepareStatement(String)}.
	    	* <p>
	    	* Whenever a call is made to this <code>LoggableStatement</code> it is forwarded to the prepared statment created from
	    	* <code>connection</code> after first saving relevant parameters for use in logging output.
	    	*
	    	* @param Connection java.sql.Connection a JDBC-connection to be used for obtaining a "real statement"
	    	* @param sql java.lang.String thw sql to exectute
	    	* @exception java.sql.SQLException if a <code>PreparedStatement</code> cannot be created
	    	* using the supplied <code>connection</code> and <code>sql</code>
	    	*/

	    public LoggableStatement(Connection connection, String sql) throws java.sql.SQLException {
	       	wrappedStatement = connection.prepareStatement(sql);          
	       	sqlTemplate = sql;
	       	parameterValues = new ArrayList();
	    }
	    public LoggableStatement(Connection connection, String sql, int resultSetType, int resutlSetConcurrentcy) throws java.sql.SQLException {
	        wrappedStatement = connection.prepareStatement(sql, resultSetType, resutlSetConcurrentcy);
	        sqlTemplate = sql;
	        parameterValues = new ArrayList();        
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Adds a set of parameters to the batch.
	     *
	     * @exception SQLException if a database access error occurs
	     * @see Statement#addBatch
	     */
	    public void addBatch() throws java.sql.SQLException {
	        wrappedStatement.addBatch();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Adds a SQL command to the current batch of commmands for the statement.
	     * This method is optional.
	     *
	     * @param sql typically this is a static SQL INSERT or UPDATE statement
	     * @exception SQLException if a database access error occurs, or the
	     * driver does not support batch statements
	     */
	    public void addBatch(String sql) throws java.sql.SQLException {
	        wrappedStatement.addBatch(sql);
	    }
	    /**
	     * Cancels this <code>Statement</code> object if both the DBMS and
	     * driver support aborting an SQL statement.
	     * This method can be used by one thread to cancel a statement that
	     * is being executed by another thread.
	     *
	     * @exception SQLException if a database access error occurs
	     */
	    public void cancel() throws SQLException {
	        wrappedStatement.cancel();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Makes the set of commands in the current batch empty.
	     * This method is optional.
	     *
	     * @exception SQLException if a database access error occurs or the
	     * driver does not support batch statements
	     */
	    public void clearBatch() throws java.sql.SQLException {
	        wrappedStatement.clearBatch();
	    }
	    /**
	     * Clears the current parameter values immediately.
	     * <P>In general, parameter values remain in force for repeated use of a
	     * Statement. Setting a parameter value automatically clears its
	     * previous value.  However, in some cases it is useful to immediately
	     * release the resources used by the current parameter values; this can
	     * be done by calling clearParameters.
	     *
	     * @exception SQLException if a database access error occurs
	     */
	    public void clearParameters() throws java.sql.SQLException {
	        wrappedStatement.clearParameters();
	    }
	    /**
	     * Clears all the warnings reported on this <code>Statement</code>
	     * object. After a call to this method,
	     * the method <code>getWarnings</code> will return
	     * null until a new warning is reported for this Statement.
	     *
	     * @exception SQLException if a database access error occurs
	     */
	    public void clearWarnings() throws java.sql.SQLException {
	        wrappedStatement.clearWarnings();
	    }
	    /**
	     * Releases this <code>Statement</code> object's database
	     * and JDBC resources immediately instead of waiting for
	     * this to happen when it is automatically closed.
	     * It is generally good practice to release resources as soon as
	     * you are finished with them to avoid tying up database
	     * resources.
	     * <P><B>Note:</B> A Statement is automatically closed when it is
	     * garbage collected. When a Statement is closed, its current
	     * ResultSet, if one exists, is also closed.
	     *
	     * @exception SQLException if a database access error occurs
	     */
	    public void close() throws java.sql.SQLException {
	        wrappedStatement.close();
	    }
	    /**
	     * Executes any kind of SQL statement.
	     * Some prepared statements return multiple results; the execute
	     * method handles these complex statements as well as the simpler
	     * form of statements handled by executeQuery and executeUpdate.
	     *
	     * @exception SQLException if a database access error occurs
	     * @see Statement#execute
	     */
	    public boolean execute() throws java.sql.SQLException {
	        return wrappedStatement.execute();
	    }
	    /**
	     * Executes a SQL statement that may return multiple results.
	     * Under some (uncommon) situations a single SQL statement may return
	     * multiple result sets and/or update counts.  Normally you can ignore
	     * this unless you are (1) executing a stored procedure that you know may
	     * return multiple results or (2) you are dynamically executing an
	     * unknown SQL string.  The  methods <code>execute</code>,
	     * <code>getMoreResults</code>, <code>getResultSet</code>,
	     * and <code>getUpdateCount</code> let you navigate through multiple results.
	     *
	     * The <code>execute</code> method executes a SQL statement and indicates the
	     * form of the first result.  You can then use getResultSet or
	     * getUpdateCount to retrieve the result, and getMoreResults to
	     * move to any subsequent result(s).
	     *
	     * @param sql any SQL statement
	     * @return true if the next result is a ResultSet; false if it is
	     * an update count or there are no more results
	     * @exception SQLException if a database access error occurs
	     * @see #getResultSet
	     * @see #getUpdateCount
	     * @see #getMoreResults
	     */
	    public boolean execute(String sql) throws java.sql.SQLException {
	        return wrappedStatement.execute(sql);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Submits a batch of commands to the database for execution.
	     * This method is optional.
	     *
	     * @return an array of update counts containing one element for each
	     * command in the batch.  The array is ordered according
	     * to the order in which commands were inserted into the batch.
	     * @exception SQLException if a database access error occurs or the
	     * driver does not support batch statements
	     */
	    public int[] executeBatch() throws java.sql.SQLException {
	        return wrappedStatement.executeBatch();
	    }
	    /**
	     * Executes the SQL query in this <code>PreparedStatement</code> object
	     * and returns the result set generated by the query.
	     *
	     * @return a ResultSet that contains the data produced by the
	     * query; never null
	     * @exception SQLException if a database access error occurs
	     */
	    public java.sql.ResultSet executeQuery() throws java.sql.SQLException {
	        return wrappedStatement.executeQuery();
	    }
	    /**
	     * Executes a SQL statement that returns a single ResultSet.
	     *
	     * @param sql typically this is a static SQL SELECT statement
	     * @return a ResultSet that contains the data produced by the
	     * query; never null
	     * @exception SQLException if a database access error occurs
	     */
	    public java.sql.ResultSet executeQuery(String sql) throws java.sql.SQLException {
	        return wrappedStatement.executeQuery(sql);
	    }
	    /**
	     * Executes the SQL INSERT, UPDATE or DELETE statement
	     * in this <code>PreparedStatement</code> object.
	     * In addition,
	     * SQL statements that return nothing, such as SQL DDL statements,
	     * can be executed.
	     *
	     * @return either the row count for INSERT, UPDATE or DELETE statements;
	     * or 0 for SQL statements that return nothing
	     * @exception SQLException if a database access error occurs
	     */
	    public int executeUpdate() throws java.sql.SQLException {
	        return wrappedStatement.executeUpdate();
	    }
	    /**
	     * Executes an SQL INSERT, UPDATE or DELETE statement. In addition,
	     * SQL statements that return nothing, such as SQL DDL statements,
	     * can be executed.
	     *
	     * @param sql a SQL INSERT, UPDATE or DELETE statement or a SQL
	     * statement that returns nothing
	     * @return either the row count for INSERT, UPDATE or DELETE or 0
	     * for SQL statements that return nothing
	     * @exception SQLException if a database access error occurs
	     */
	    public int executeUpdate(String sql) throws java.sql.SQLException {
	        return wrappedStatement.executeUpdate(sql);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Returns the <code>Connection</code> object
	     * that produced this <code>Statement</code> object.
	     * @return the connection that produced this statement
	     * @exception SQLException if a database access error occurs
	     */
	    public java.sql.Connection getConnection() throws java.sql.SQLException {
	        return wrappedStatement.getConnection();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Retrieves the direction for fetching rows from
	     * database tables that is the default for result sets
	     * generated from this <code>Statement</code> object.
	     * If this <code>Statement</code> object has not set
	     * a fetch direction by calling the method <code>setFetchDirection</code>,
	     * the return value is implementation-specific.
	     *
	     * @return the default fetch direction for result sets generated
	     *          from this <code>Statement</code> object
	     * @exception SQLException if a database access error occurs
	     */
	    public int getFetchDirection() throws java.sql.SQLException {
	        return wrappedStatement.getFetchDirection();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Retrieves the number of result set rows that is the default
	     * fetch size for result sets
	     * generated from this <code>Statement</code> object.
	     * If this <code>Statement</code> object has not set
	     * a fetch size by calling the method <code>setFetchSize</code>,
	     * the return value is implementation-specific.
	     * @return the default fetch size for result sets generated
	     *          from this <code>Statement</code> object
	     * @exception SQLException if a database access error occurs
	     */
	    public int getFetchSize() throws java.sql.SQLException {
	        return wrappedStatement.getFetchSize();
	    }
	    /**
	     * Returns the maximum number of bytes allowed
	     * for any column value.
	     * This limit is the maximum number of bytes that can be
	     * returned for any column value.
	     * The limit applies only to BINARY,
	     * VARBINARY, LONGVARBINARY, CHAR, VARCHAR, and LONGVARCHAR
	     * columns.  If the limit is exceeded, the excess data is silently
	     * discarded.
	     *
	     * @return the current max column size limit; zero means unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public int getMaxFieldSize() throws java.sql.SQLException {
	        return wrappedStatement.getMaxFieldSize();
	    }
	    /**
	     * Retrieves the maximum number of rows that a
	     * ResultSet can contain.  If the limit is exceeded, the excess
	     * rows are silently dropped.
	     *
	     * @return the current max row limit; zero means unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public int getMaxRows() throws java.sql.SQLException {
	        return wrappedStatement.getMaxRows();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Gets the number, types and properties of a ResultSet's columns.
	     *
	     * @return the description of a ResultSet's columns
	     * @exception SQLException if a database access error occurs
	     */
	    public java.sql.ResultSetMetaData getMetaData() throws java.sql.SQLException {
	        return wrappedStatement.getMetaData();
	    }
	    /**
	     * Moves to a Statement's next result.  It returns true if
	     * this result is a ResultSet.  This method also implicitly
	     * closes any current ResultSet obtained with getResultSet.
	     *
	     * There are no more results when (!getMoreResults() &&
	     * (getUpdateCount() == -1)
	     *
	     * @return true if the next result is a ResultSet; false if it is
	     * an update count or there are no more results
	     * @exception SQLException if a database access error occurs
	     * @see #execute
	     */
	    public boolean getMoreResults() throws java.sql.SQLException {
	        return wrappedStatement.getMoreResults();
	    }
	    /**
	     * Returns the sql statement string (question marks replaced with set parameter values)
	     * that will be (or has been) executed by the {@link java.sql.PreparedStatement PreparedStatement} that this
	     * <code>LoggableStatement</code> is a wrapper for.
	     * <p>
	     * @return java.lang.String the statemant represented by this <code>LoggableStatement</code>
	     */
	    public String getQueryString() {

	        StringBuffer buf = new StringBuffer();
	        int qMarkCount = 0;
	        ArrayList chunks = new ArrayList();
	        StringTokenizer tok = new StringTokenizer(sqlTemplate + " ", "?");
	        while (tok.hasMoreTokens()) {
	            String oneChunk = tok.nextToken();
	            buf.append(oneChunk);

	            try {
	                Object value;
	                if (parameterValues.size() > 1 + qMarkCount) {
	                    value = parameterValues.get(1 + qMarkCount++);
	                } else {
	                    if (tok.hasMoreTokens()) {
	                        value = null;
	                    } else {
	                        value = "";
	                    }
	                }
	                buf.append("" + value);
	            } catch (Throwable e) {
	                buf.append("ERROR WHEN PRODUCING QUERY STRING FOR LOG." + e.toString());
	                // catch this without whining, if this fails the only thing wrong is probably this class
	            }
	        }
	        return buf.toString().trim();
	    }
	    /**
	     * Retrieves the number of seconds the driver will
	     * wait for a Statement to execute. If the limit is exceeded, a
	     * SQLException is thrown.
	     *
	     * @return the current query timeout limit in seconds; zero means unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public int getQueryTimeout() throws java.sql.SQLException {
	        return wrappedStatement.getQueryTimeout();
	    }
	    /**
	     *  Returns the current result as a <code>ResultSet</code> object.
	     *  This method should be called only once per result.
	     *
	     * @return the current result as a ResultSet; null if the result
	     * is an update count or there are no more results
	     * @exception SQLException if a database access error occurs
	     * @see #execute
	     */
	    public java.sql.ResultSet getResultSet() throws java.sql.SQLException {
	        return wrappedStatement.getResultSet();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Retrieves the result set concurrency.
	     */
	    public int getResultSetConcurrency() throws java.sql.SQLException {
	        return wrappedStatement.getResultSetConcurrency();
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Determine the result set type.
	     */
	    public int getResultSetType() throws java.sql.SQLException {
	        return wrappedStatement.getResultSetType();
	    }
	    /**
	     *  Returns the current result as an update count;
	     *  if the result is a ResultSet or there are no more results, -1
	     *  is returned.
	     *  This method should be called only once per result.
	     *
	     * @return the current result as an update count; -1 if it is a
	     * ResultSet or there are no more results
	     * @exception SQLException if a database access error occurs
	     * @see #execute
	     */
	    public int getUpdateCount() throws java.sql.SQLException {
	        return wrappedStatement.getUpdateCount();
	    }
	    /**
	     * Retrieves the first warning reported by calls on this Statement.
	     * Subsequent Statement warnings will be chained to this
	     * SQLWarning.
	     *
	     * <p>The warning chain is automatically cleared each time
	     * a statement is (re)executed.
	     *
	     * <P><B>Note:</B> If you are processing a ResultSet, any
	     * warnings associated with ResultSet reads will be chained on the
	     * ResultSet object.
	     *
	     * @return the first SQLWarning or null
	     * @exception SQLException if a database access error occurs
	     */
	    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
	        return wrappedStatement.getWarnings();
	    }
	    /**
	     * Saves the parameter value <code>obj</code> for the specified <code>position</code> for use in logging output
	     *
	     * @param position position (starting at 1) of the parameter to save
	     * @param obj java.lang.FWObject the parameter value to save
	     */
	    private void saveQueryParamValue(int position, Object obj) {
	        String strValue;
	        if (obj instanceof String || obj instanceof java.sql.Date) {
	            // if we have a String or Date , include '' in the saved value
	            strValue = "'" + obj + "'";
	        } else {

	            if (obj == null) {
	                // convert null to the string null
	                strValue = "null";
	            } else {
	                // unknown object (includes all Numbers), just call toString
	                strValue = obj.toString();
	            }
	        }

	        // if we are setting a position larger than current size of parameterValues, first make it larger
	        while (position >= parameterValues.size()) {
	            parameterValues.add(null);
	        }
	        // save the parameter
	        parameterValues.set(position, strValue);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets an Array parameter.
	     *
	     * @param i the first parameter is 1, the second is 2, ...
	     * @param x an object representing an SQL array
	     * @exception SQLException if a database access error occurs
	     */
	    public void setArray(int i, java.sql.Array x) throws java.sql.SQLException {

	        wrappedStatement.setArray(i, x);
	        saveQueryParamValue(i, x);

	    }
	    /**
	     * Sets the designated parameter to the given input stream, which will have
	     * the specified number of bytes.
	     * When a very large ASCII value is input to a LONGVARCHAR
	     * parameter, it may be more practical to send it via a
	     * java.io.InputStream. JDBC will read the data from the stream
	     * as needed, until it reaches end-of-file.  The JDBC driver will
	     * do any necessary conversion from ASCII to the database char format.
	     *
	     * <P><B>Note:</B> This stream object can either be a standard
	     * Java stream object or your own subclass that implements the
	     * standard interface.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the Java input stream that contains the ASCII parameter value
	     * @param length the number of bytes in the stream
	     * @exception SQLException if a database access error occurs
	     */
	    public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws java.sql.SQLException {

	        wrappedStatement.setAsciiStream(parameterIndex, x, length);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * Sets the designated parameter to a java.lang.BigDecimal value.
	     * The driver converts this to an SQL NUMERIC value when
	     * it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws java.sql.SQLException {
	        wrappedStatement.setBigDecimal(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);

	    }
	    /**
	     * Sets the designated parameter to the given input stream, which will have
	     * the specified number of bytes.
	     * When a very large binary value is input to a LONGVARBINARY
	     * parameter, it may be more practical to send it via a
	     * java.io.InputStream. JDBC will read the data from the stream
	     * as needed, until it reaches end-of-file.
	     *
	     * <P><B>Note:</B> This stream object can either be a standard
	     * Java stream object or your own subclass that implements the
	     * standard interface.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the java input stream which contains the binary parameter value
	     * @param length the number of bytes in the stream
	     * @exception SQLException if a database access error occurs
	     */
	    public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws java.sql.SQLException {
	        wrappedStatement.setBinaryStream(parameterIndex, x, length);
	        saveQueryParamValue(parameterIndex, x);

	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets a BLOB parameter.
	     *
	     * @param i the first parameter is 1, the second is 2, ...
	     * @param x an object representing a BLOB
	     * @exception SQLException if a database access error occurs
	     */
	    public void setBlob(int i, java.sql.Blob x) throws java.sql.SQLException {
	        wrappedStatement.setBlob(i, x);
	        saveQueryParamValue(i, x);
	    }
	    /**
	     * Sets the designated parameter to a Java boolean value.  The driver converts this
	     * to an SQL BIT value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setBoolean(int parameterIndex, boolean x) throws java.sql.SQLException {
	        wrappedStatement.setBoolean(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Boolean(x));

	    }
	    /**
	     * Sets the designated parameter to a Java byte value.  The driver converts this
	     * to an SQL TINYINT value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setByte(int parameterIndex, byte x) throws java.sql.SQLException {
	        wrappedStatement.setByte(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Integer(x));
	    }
	    /**
	     * Sets the designated parameter to a Java array of bytes.  The driver converts
	     * this to an SQL VARBINARY or LONGVARBINARY (depending on the
	     * argument's size relative to the driver's limits on VARBINARYs)
	     * when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setBytes(int parameterIndex, byte[] x) throws java.sql.SQLException {
	        wrappedStatement.setBytes(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets the designated parameter to the given <code>Reader</code>
	     * object, which is the given number of characters long.
	     * When a very large UNICODE value is input to a LONGVARCHAR
	     * parameter, it may be more practical to send it via a
	     * java.io.Reader. JDBC will read the data from the stream
	     * as needed, until it reaches end-of-file.  The JDBC driver will
	     * do any necessary conversion from UNICODE to the database char format.
	     *
	     * <P><B>Note:</B> This stream object can either be a standard
	     * Java stream object or your own subclass that implements the
	     * standard interface.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the java reader which contains the UNICODE data
	     * @param length the number of characters in the stream
	     * @exception SQLException if a database access error occurs
	     */
	    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws java.sql.SQLException {
	        wrappedStatement.setCharacterStream(parameterIndex, reader, length);
	        saveQueryParamValue(parameterIndex, reader);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets a CLOB parameter.
	     *
	     * @param i the first parameter is 1, the second is 2, ...
	     * @param x an object representing a CLOB
	     * @exception SQLException if a database access error occurs
	     */
	    public void setClob(int i, java.sql.Clob x) throws java.sql.SQLException {
	        wrappedStatement.setClob(i, x);
	        saveQueryParamValue(i, x);

	    }
	    /**
	     * Defines the SQL cursor name that will be used by
	     * subsequent Statement <code>execute</code> methods. This name can then be
	     * used in SQL positioned update/delete statements to identify the
	     * current row in the ResultSet generated by this statement.  If
	     * the database doesn't support positioned update/delete, this
	     * method is a noop.  To insure that a cursor has the proper isolation
	     * level to support updates, the cursor's SELECT statement should be
	     * of the form 'select for update ...'. If the 'for update' phrase is
	     * omitted, positioned updates may fail.
	     *
	     * <P><B>Note:</B> By definition, positioned update/delete
	     * execution must be done by a different Statement than the one
	     * which generated the ResultSet being used for positioning. Also,
	     * cursor names must be unique within a connection.
	     *
	     * @param name the new cursor name, which must be unique within
	     *             a connection
	     * @exception SQLException if a database access error occurs
	     */
	    public void setCursorName(String name) throws java.sql.SQLException {
	        wrappedStatement.setCursorName(name);

	    }
	    /**
	     * Sets the designated parameter to a java.sql.Date value.  The driver converts this
	     * to an SQL DATE value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setDate(int parameterIndex, java.sql.Date x) throws java.sql.SQLException {

	        wrappedStatement.setDate(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets the designated parameter to a java.sql.Date value,
	     * using the given <code>Calendar</code> object.  The driver uses
	     * the <code>Calendar</code> object to construct an SQL DATE,
	     * which the driver then sends to the database.  With a
	     * a <code>Calendar</code> object, the driver can calculate the date
	     * taking into account a custom timezone and locale.  If no
	     * <code>Calendar</code> object is specified, the driver uses the default
	     * timezone and locale.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @param cal the <code>Calendar</code> object the driver will use
	     *            to construct the date
	     * @exception SQLException if a database access error occurs
	     */
	    public void setDate(int parameterIndex, java.sql.Date x, java.util.Calendar cal) throws java.sql.SQLException {
	        wrappedStatement.setDate(parameterIndex, x, cal);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * Sets the designated parameter to a Java double value.  The driver converts this
	     * to an SQL DOUBLE value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setDouble(int parameterIndex, double x) throws java.sql.SQLException {
	        wrappedStatement.setDouble(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Double(x));
	    }
	    /**
	     * Sets escape processing on or off.
	     * If escape scanning is on (the default), the driver will do
	     * escape substitution before sending the SQL to the database.
	     *
	     * Note: Since prepared statements have usually been parsed prior
	     * to making this call, disabling escape processing for prepared
	     * statements will have no effect.
	     *
	     * @param enable true to enable; false to disable
	     * @exception SQLException if a database access error occurs
	     */
	    public void setEscapeProcessing(boolean enable) throws java.sql.SQLException {
	        wrappedStatement.setEscapeProcessing(enable);

	    }
	    /**
	     * JDBC 2.0
	     *
	     * Gives the driver a hint as to the direction in which
	     * the rows in a result set
	     * will be processed. The hint applies only to result sets created
	     * using this Statement object.  The default value is
	     * ResultSet.FETCH_FORWARD.
	     * <p>Note that this method sets the default fetch direction for
	     * result sets generated by this <code>Statement</code> object.
	     * Each result set has its own methods for getting and setting
	     * its own fetch direction.
	     * @param direction the initial direction for processing rows
	     * @exception SQLException if a database access error occurs
	     * or the given direction
	     * is not one of ResultSet.FETCH_FORWARD, ResultSet.FETCH_REVERSE, or
	     * ResultSet.FETCH_UNKNOWN
	     */
	    public void setFetchDirection(int direction) throws java.sql.SQLException {
	        wrappedStatement.setFetchDirection(direction);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Gives the JDBC driver a hint as to the number of rows that should
	     * be fetched from the database when more rows are needed.  The number
	     * of rows specified affects only result sets created using this
	     * statement. If the value specified is zero, then the hint is ignored.
	     * The default value is zero.
	     *
	     * @param rows the number of rows to fetch
	     * @exception SQLException if a database access error occurs, or the
	     * condition 0 <= rows <= this.getMaxRows() is not satisfied.
	     */
	    public void setFetchSize(int rows) throws java.sql.SQLException {
	        wrappedStatement.setFetchSize(rows);
	    }
	    /**
	     * Sets the designated parameter to a Java float value.  The driver converts this
	     * to an SQL FLOAT value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setFloat(int parameterIndex, float x) throws java.sql.SQLException {
	        wrappedStatement.setFloat(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Float(x));

	    }
	    /**
	     * Sets the designated parameter to a Java int value.  The driver converts this
	     * to an SQL INTEGER value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setInt(int parameterIndex, int x) throws java.sql.SQLException {
	        wrappedStatement.setInt(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Integer(x));
	    }
	    /**
	     * Sets the designated parameter to a Java long value.  The driver converts this
	     * to an SQL BIGINT value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setLong(int parameterIndex, long x) throws java.sql.SQLException {
	        wrappedStatement.setLong(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Long(x));

	    }
	    /**
	     * Sets the limit for the maximum number of bytes in a column to
	     * the given number of bytes.  This is the maximum number of bytes
	     * that can be returned for any column value.  This limit applies
	     * only to BINARY, VARBINARY, LONGVARBINARY, CHAR, VARCHAR, and
	     * LONGVARCHAR fields.  If the limit is exceeded, the excess data
	     * is silently discarded. For maximum portability, use values
	     * greater than 256.
	     *
	     * @param max the new max column size limit; zero means unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public void setMaxFieldSize(int max) throws java.sql.SQLException {
	        wrappedStatement.setMaxFieldSize(max);

	    }
	    /**
	     * Sets the limit for the maximum number of rows that any
	     * ResultSet can contain to the given number.
	     * If the limit is exceeded, the excess
	     * rows are silently dropped.
	     *
	     * @param max the new max rows limit; zero means unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public void setMaxRows(int max) throws java.sql.SQLException {
	        wrappedStatement.setMaxRows(max);
	    }
	    /**
	     * Sets the designated parameter to SQL NULL.
	     *
	     * <P><B>Note:</B> You must specify the parameter's SQL type.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param sqlType the SQL type code defined in java.sql.Types
	     * @exception SQLException if a database access error occurs
	     */
	    public void setNull(int parameterIndex, int sqlType) throws java.sql.SQLException {
	        wrappedStatement.setNull(parameterIndex, sqlType);
	        saveQueryParamValue(parameterIndex, null);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets the designated parameter to SQL NULL.  This version of setNull should
	     * be used for user-named types and REF type parameters.  Examples
	     * of user-named types include: STRUCT, DISTINCT, JAVA_OBJECT, and
	     * named array types.
	     *
	     * <P><B>Note:</B> To be portable, applications must give the
	     * SQL type code and the fully-qualified SQL type name when specifying
	     * a NULL user-defined or REF parameter.  In the case of a user-named type
	     * the name is the type name of the parameter itself.  For a REF
	     * parameter the name is the type name of the referenced type.  If
	     * a JDBC driver does not need the type code or type name information,
	     * it may ignore it.
	     *
	     * Although it is intended for user-named and Ref parameters,
	     * this method may be used to set a null parameter of any JDBC type.
	     * If the parameter does not have a user-named or REF type, the given
	     * typeName is ignored.
	     *
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param sqlType a value from java.sql.Types
	     * @param typeName the fully-qualified name of an SQL user-named type,
	     *  ignored if the parameter is not a user-named type or REF
	     * @exception SQLException if a database access error occurs
	     */
	    public void setNull(int paramIndex, int sqlType, String typeName) throws java.sql.SQLException {
	        wrappedStatement.setNull(paramIndex, sqlType, typeName);
	        saveQueryParamValue(paramIndex, null);

	    }
	    /**
	     * <p>Sets the value of a parameter using an object; use the
	     * java.lang equivalent objects for integral values.
	     *
	     * <p>The JDBC specification specifies a standard mapping from
	     * Java FWObject types to SQL types.  The given argument java object
	     * will be converted to the corresponding SQL type before being
	     * sent to the database.
	     *
	     * <p>Note that this method may be used to pass datatabase-
	     * specific abstract data types, by using a Driver-specific Java
	     * type.
	     *
	     * If the object is of a class implementing SQLData,
	     * the JDBC driver should call its method <code>writeSQL</code> to write it
	     * to the SQL data stream.
	     * If, on the other hand, the object is of a class implementing
	     * Ref, Blob, Clob, Struct,
	     * or Array, then the driver should pass it to the database as a value of the
	     * corresponding SQL type.
	     *
	     * This method throws an exception if there is an ambiguity, for example, if the
	     * object is of a class implementing more than one of those interfaces.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the object containing the input parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setObject(int parameterIndex, Object x) throws java.sql.SQLException {
	        wrappedStatement.setObject(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	    	 * Sets the value of the designated parameter with the given object.
	    	 * This method is like setObject above, except that it assumes a scale of zero.
	    	 *
	    	 * @param parameterIndex the first parameter is 1, the second is 2, ...
	    	 * @param x the object containing the input parameter value
	    	 * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
	    	 *                      sent to the database
	    	 * @exception SQLException if a database access error occurs
	    	 */
	    public void setObject(int parameterIndex, Object x, int targetSqlType) throws java.sql.SQLException {
	        wrappedStatement.setObject(parameterIndex, x, targetSqlType);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * <p>Sets the value of a parameter using an object. The second
	     * argument must be an object type; for integral values, the
	     * java.lang equivalent objects should be used.
	     *
	     * <p>The given Java object will be converted to the targetSqlType
	     * before being sent to the database.
	     *
	     * If the object has a custom mapping (is of a class implementing SQLData),
	     * the JDBC driver should call its method <code>writeSQL</code> to write it
	     * to the SQL data stream.
	     * If, on the other hand, the object is of a class implementing
	     * Ref, Blob, Clob, Struct,
	     * or Array, the driver should pass it to the database as a value of the
	     * corresponding SQL type.
	     *
	     * <p>Note that this method may be used to pass datatabase-
	     * specific abstract data types.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the object containing the input parameter value
	     * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
	     * sent to the database. The scale argument may further qualify this type.
	     * @param scale for java.sql.Types.DECIMAL or java.sql.Types.NUMERIC types,
	     *          this is the number of digits after the decimal point.  For all other
	     *          types, this value will be ignored.
	     * @exception SQLException if a database access error occurs
	     * @see Types
	     */
	    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws java.sql.SQLException {

	        wrappedStatement.setObject(parameterIndex, x, targetSqlType, scale);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * Sets the number of seconds the driver will
	     * wait for a Statement to execute to the given number of seconds.
	     * If the limit is exceeded, a SQLException is thrown.
	     *
	     * @param seconds the new query timeout limit in seconds; zero means
	     * unlimited
	     * @exception SQLException if a database access error occurs
	     */
	    public void setQueryTimeout(int seconds) throws java.sql.SQLException {
	        wrappedStatement.setQueryTimeout(seconds);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets a REF(&lt;structured-type&gt;) parameter.
	     *
	     * @param i the first parameter is 1, the second is 2, ...
	     * @param x an object representing data of an SQL REF Type
	     * @exception SQLException if a database access error occurs
	     */
	    public void setRef(int i, java.sql.Ref x) throws java.sql.SQLException {
	        wrappedStatement.setRef(i, x);
	        saveQueryParamValue(i, x);

	    }
	    /**
	     * Sets the designated parameter to a Java short value.  The driver converts this
	     * to an SQL SMALLINT value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setShort(int parameterIndex, short x) throws java.sql.SQLException {
	        wrappedStatement.setShort(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, new Integer(x));
	    }
	    /**
	     * Sets the designated parameter to a Java String value.  The driver converts this
	     * to an SQL VARCHAR or LONGVARCHAR value (depending on the argument's
	     * size relative to the driver's limits on VARCHARs) when it sends
	     * it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setString(int parameterIndex, String x) throws java.sql.SQLException {

	        wrappedStatement.setString(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * Sets the designated parameter to a java.sql.Time value.  The driver converts this
	     * to an SQL TIME value when it sends it to the database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setTime(int parameterIndex, java.sql.Time x) throws java.sql.SQLException {
	        wrappedStatement.setTime(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets the designated parameter to a java.sql.Time value,
	     * using the given <code>Calendar</code> object.  The driver uses
	     * the <code>Calendar</code> object to construct an SQL TIME,
	     * which the driver then sends to the database.  With a
	     * a <code>Calendar</code> object, the driver can calculate the time
	     * taking into account a custom timezone and locale.  If no
	     * <code>Calendar</code> object is specified, the driver uses the default
	     * timezone and locale.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @param cal the <code>Calendar</code> object the driver will use
	     *            to construct the time
	     * @exception SQLException if a database access error occurs
	     */
	    public void setTime(int parameterIndex, java.sql.Time x, java.util.Calendar cal) throws java.sql.SQLException {
	        wrappedStatement.setTime(parameterIndex, x, cal);
	        saveQueryParamValue(parameterIndex, x);

	    }
	    /**
	     * Sets the designated parameter to a java.sql.Timestamp value.  The driver
	     * converts this to an SQL TIMESTAMP value when it sends it to the
	     * database.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @exception SQLException if a database access error occurs
	     */
	    public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws java.sql.SQLException {
	        wrappedStatement.setTimestamp(parameterIndex, x);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * JDBC 2.0
	     *
	     * Sets the designated parameter to a java.sql.Timestamp value,
	     * using the given <code>Calendar</code> object.  The driver uses
	     * the <code>Calendar</code> object to construct an SQL TIMESTAMP,
	     * which the driver then sends to the database.  With a
	     * a <code>Calendar</code> object, the driver can calculate the timestamp
	     * taking into account a custom timezone and locale.  If no
	     * <code>Calendar</code> object is specified, the driver uses the default
	     * timezone and locale.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the parameter value
	     * @param cal the <code>Calendar</code> object the driver will use
	     *            to construct the timestamp
	     * @exception SQLException if a database access error occurs
	     */
	    public void setTimestamp(int parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) throws java.sql.SQLException {
	        wrappedStatement.setTimestamp(parameterIndex, x, cal);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    /**
	     * Sets the designated parameter to the given input stream, which will have
	     * the specified number of bytes.
	     * When a very large UNICODE value is input to a LONGVARCHAR
	     * parameter, it may be more practical to send it via a
	     * java.io.InputStream. JDBC will read the data from the stream
	     * as needed, until it reaches end-of-file.  The JDBC driver will
	     * do any necessary conversion from UNICODE to the database char format.
	     * The byte format of the Unicode stream must be Java UTF-8, as
	     * defined in the Java Virtual Machine Specification.
	     *
	     * <P><B>Note:</B> This stream object can either be a standard
	     * Java stream object or your own subclass that implements the
	     * standard interface.
	     *
	     * @param parameterIndex the first parameter is 1, the second is 2, ...
	     * @param x the java input stream which contains the
	     * UNICODE parameter value
	     * @param length the number of bytes in the stream
	     * @exception SQLException if a database access error occurs
	     * @deprecated
	     */
	    public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws java.sql.SQLException {
	        wrappedStatement.setUnicodeStream(parameterIndex, x, length);
	        saveQueryParamValue(parameterIndex, x);
	    }
	    
	    /* This Method is not applicable to IBM JDK 1.3 */
	    /*******************************************************************************/
	    public java.sql.ParameterMetaData getParameterMetaData() throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [getParameterMetaData()].");	
	    }
	    /*******************************************************************************/
	    
		public void setURL(int parameterIndex, java.net.URL x) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [setURL(int parameterIndex, java.net.URL x)].");	
		}
		public boolean execute(String sql, int autoGeneratedKeys) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [execute(String sql, int autoGeneratedKeys)].");
		}
		public boolean execute(String sql, int columnIndexes[]) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [String sql, int columnIndexes[])].");	
		}	
		public boolean execute(String sql, String columnNames[]) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [execute(String sql, String columnNames[])].");	
		}	
		public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [executeUpdate(String sql, int autoGeneratedKeys)].");	
		}
		public int executeUpdate(String sql, int columnIndexes[]) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [executeUpdate(String sql, int columnIndexes[])].");	
		}	
		public int executeUpdate(String sql, String columnNames[]) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [executeUpdate(String sql, String columnNames[])].");
		}
		public java.sql.ResultSet getGeneratedKeys() throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [getGeneratedKeys()].");
		}
		public boolean getMoreResults(int current) throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [getMoreResults(int current)].");
		}
		public int getResultSetHoldability() throws SQLException{
			throw new SQLException("NOT IMPLEMENTED METHOD [getResultSetHoldability()].");
		}
		public boolean isClosed() throws SQLException {
			// TODO Auto-generated method stub
			return false;
		}
		public void setPoolable(boolean poolable) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public boolean isPoolable() throws SQLException {
			// TODO Auto-generated method stub
			return false;
		}
//		public <T> T unwrap(Class<T> iface) throws SQLException {
//			// TODO Auto-generated method stub
//			return null;
//		}
//		public boolean isWrapperFor(Class<?> iface) throws SQLException {
//			// TODO Auto-generated method stub
//			return false;
//		}
//		public void setRowId(int parameterIndex, RowId x) throws SQLException {
//			// TODO Auto-generated method stub
//			
//		}
		public void setNString(int parameterIndex, String value)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setNCharacterStream(int parameterIndex, Reader value,
				long length) throws SQLException {
			// TODO Auto-generated method stub
			
		}
//		public void setNClob(int parameterIndex, NClob value) throws SQLException {
//			// TODO Auto-generated method stub
//			
//		}
		public void setClob(int parameterIndex, Reader reader, long length)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setBlob(int parameterIndex, InputStream inputStream, long length)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setNClob(int parameterIndex, Reader reader, long length)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
//		public void setSQLXML(int parameterIndex, SQLXML xmlObject)
//				throws SQLException {
//			// TODO Auto-generated method stub
//			
//		}
		public void setAsciiStream(int parameterIndex, InputStream x, long length)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setBinaryStream(int parameterIndex, InputStream x, long length)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setCharacterStream(int parameterIndex, Reader reader,
				long length) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setAsciiStream(int parameterIndex, InputStream x)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setBinaryStream(int parameterIndex, InputStream x)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setCharacterStream(int parameterIndex, Reader reader)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setNCharacterStream(int parameterIndex, Reader value)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setClob(int parameterIndex, Reader reader) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setBlob(int parameterIndex, InputStream inputStream)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		public void setNClob(int parameterIndex, Reader reader) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public boolean isWrapperFor(Class<?> iface) throws SQLException {
			// TODO Auto-generated method stub
			return false;
		}
		@Override
		public void setRowId(int parameterIndex, RowId x) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setNClob(int parameterIndex, NClob value) throws SQLException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void setSQLXML(int parameterIndex, SQLXML xmlObject)
				throws SQLException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void closeOnCompletion() throws SQLException {
			// TODO Auto-generated method stub
			
		}
		@Override
		public boolean isCloseOnCompletion() throws SQLException {
			// TODO Auto-generated method stub
			return false;
		}
	}

}
