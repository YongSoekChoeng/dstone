/*
 * Created on 2005. 10. 13.
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.dstone.common.utils;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.Vector;

/**
 * 데이터 저장객체 클래스. Serializable 을 구현하였으며, 각종 리스트성데이타와 프로퍼티성데이터를<br>
 * 저장하는데 용이하다.
 * 작성 날짜: (2005-08-21 오후 9:06:19)
 * @author: 정용석
 */
public class DataSet implements java.io.Serializable {
	
	/***************************** 제어용변수 시작 *****************************/
	public static final String CARRIER_MASK_BAR_1 = "*******************";
	public static final String CARRIER_MASK_BAR_2 = "-------------------";
	public static int STRING_DEFAULT_LENGTH = 999999999;
	public static int INT_DEFAULT_LENGTH = 9;
	public static int SHORT_DEFAULT_LENGTH = 6;
	public static int BYTE_DEFAULT_LENGTH = 6;
	public static int CHAR_DEFAULT_LENGTH = 999999999;
	public static int FLOAT_DEFAULT_LENGTH = 6;
	public static int BOOLEAN_DEFAULT_LENGTH = 1;
	public static int DOUBLE_DEFAULT_LENGTH = 9;
	public static int LONG_DEFAULT_LENGTH = 9;
	/***************************** 제어용변수 끝 *******************************/
	
	/***************************** 저장용변수 시작 *****************************/
	protected Vector child = null; 	  /* 자식 DataSet을 적제할 Vector 	*/
	public ColumnInfo prop = null; /* 프로퍼티성 데이터 				*/
	/***************************** 저장용변수 끝 *******************************/
	
	/***************************** 컬럼정보 시작 *****************************/
	public class ColumnInfo extends java.util.LinkedHashMap {
		
		public java.util.LinkedHashMap columnList = new java.util.LinkedHashMap();
		
		public void setColInfo(String columnName, int columnType, int columnSize){
			if(!columnList.containsKey(columnName)){
				Column col = new Column();
				col.columnName = columnName;
				col.columnType = columnType;
				col.columnSize = columnSize;
				columnList.put(columnName, col);
			}
		}
		public int getColType(String columnName){
			int columnType = -1;
			Object col = columnList.get(columnName);
			if(col != null){
				columnType = ((Column)col).columnType;				
			}
			return columnType;
		}
		public int getColSize(String columnName){
			int columnSize = -1;
			Object col = columnList.get(columnName);
			if(col != null){
				columnSize = ((Column)col).columnSize;				
			}
			return columnSize;
		}
		public class Column  implements java.io.Serializable {
			public String columnName = null;     /* 컬럼명   */
			public int columnType = -1;          /* 컬럼타입 */
			public int columnSize = -1;          /* 컬럼길이 */
		}
	}
	/***************************** 컬럼정보 끝 *******************************/
	
	private void debug(Object o){
		System.out.println(o);
	}
	
	public DataSet(){
		init();
	}
	public DataSet(ResultSet rs){
		init();
		boolean boolViewConvert = false;
		try {
			// 컬럼명을 구한다.
			java.sql.ResultSetMetaData rsMeta = rs.getMetaData();
			String columnName = "";
			int columnType = -1;
			int columnSize = -1;
			int intColumnCount = rsMeta.getColumnCount();

			int intIndex = 0;
			DataSet row = null;
			while (rs.next()) {
				row = new DataSet();
				for (int j = 0; j < intColumnCount; j++) {
					columnName = rsMeta.getColumnName(j+1);
					if(intIndex == 0){
						columnType = rsMeta.getColumnType(j+1);
						columnSize = rsMeta.getColumnDisplaySize(j+1);
						row.prop.setColInfo(columnName, columnType, columnSize);
					}
					if (boolViewConvert) {
						row.setDatum(columnName, fromDbToKor( rs.getString( columnName ) ) );
					}else{
						row.setDatum(columnName, rs.getString( columnName ) );
					}
				}
				addRow(row);
				intIndex++;
			}
		} catch (Exception e) {
			debug(e);
		}
	}
	private String nullCheck(String input, String defaultVal) {
		if (input == null) {
			return defaultVal;
		} else {
			return input;
		}
	}
	
	/**
	 * <code>putObj</code> 	
	 * Object를 세팅하는 메소드
	 * @param key 
	 * @param obj
	 */
	public void putObj(String key, Object obj)throws Exception {
		init();
		screenObj(obj);
		this.prop.put(key, obj);
	}	
	/**
	 * <code>getObj</code> 	
	 * Object를 가져오는 메소드
	 * @param key 
	 * @return FWObject
	 */
	public Object getObj(String key) {
		return this.prop.get(key);
	}	
		
	
	protected void init(){
		if(child == null){child = new Vector();}
		if(prop == null){prop = new ColumnInfo();}
	}
	
	public String[] getCols(){
		String[] cols = null;
		if(this.prop != null){
			if(this.prop.columnList != null){
				try{
					java.util.Iterator enumObj = this.prop.columnList.keySet().iterator();
					String sTemp = "";
					cols = new String[this.prop.columnList.size()];
					int intColCnt = 0;
					while (enumObj.hasNext()) {
						cols[intColCnt++] = (String) enumObj.next();
					}
				}catch(Exception e){
					debug(e);
				}
			}
		}
		return cols;
	}
	
	public String[] getRowColumns(){
		String[] cols = null;
		if(this.child != null){
			if(this.child.size()>0){
				try{
					cols = this.getRow(0).getCols();
				}catch(Exception e){
					debug(e);
				}
			}
		}
		return cols;
	}
	public int getColsCnt(){
		int intColsCnt = 0;
		if(this.prop != null){
			if(this.prop.columnList != null){
				intColsCnt = this.prop.columnList.size();
			}
		}
		return intColsCnt;
	}
	
	public int getRowColumnCnt(){
		int intColsCnt = 0;
		if(this.child != null){
			if(this.child.size()>0){
				try{
					intColsCnt = this.getRow(0).getCols().length;
				}catch(Exception e){
					
				}
			}
		}
		return intColsCnt;
	}	
	
	public void addRow(DataSet row){
		init();	
		child.add(row);
	}
	public void removeRow(int intIndex){
		if(this.child != null){
			if(this.getRowCnt() > (intIndex)){
				this.child.remove(intIndex);
			}
		}
	}
	public DataSet getRow(int intIndex){
		DataSet row = null;
		try{
			row = (DataSet)child.get(intIndex);
		}catch(Exception e){
			debug(e);
		}
		return row;
	}
	
	public DataSet[] getRows(){
		DataSet[] rows = null;
		try{
			if(child != null){
				rows = new DataSet[child.size()];
				child.copyInto(rows);
			}
		}catch(Exception e){
			debug(e);
		}
		return rows;
	}	
	
	
	public int getRowCnt(){
		int intRowCnt = 0;
		if(this.child != null){
			intRowCnt = this.child.size();
		}
		return intRowCnt;
	}
	/**
	 * <code>insertRow</code> 	
	 * Row 데이터를 저장하는 메소드
	 * @param index 저장될 데이터가 위치할 인덱스
	 * @param row 저장될 데이터
	 */
	public void insertRow(int index, DataSet row) {
		if (this.child == null) {
			return;
		}
		if (this.child.size() < index) {
			return;
		}
		this.child.insertElementAt(row, index);
	}
	/**
	 * <code>replaceRow</code> 	
	 * Row 데이터를 치환하는 메소드
	 * @param index 치환하고자하는 Row의 인덱스
	 * @param row 치환할 데이터
	 */
	public void replaceRow(int index, DataSet newRow) {
		if (this.child == null) {
			return;
		}
		if (this.child.size() <= index) {
			return;
		}
		this.child.remove(index);
		this.child.insertElementAt(newRow, index);
	}
	
	/**
	 * <code>switchRow</code> 	
	 * Row의 위치를 서로 바꾸는 메소드
	 * @param index1 바꿀 Row 인덱스1
	 * @param index2 바꿀 Row 인덱스2
	 */
	public void switchRow(int index1, int index2) {
		if (this.child == null) {
			return;
		}
		if (this.child.size() <= index1) {
			return;
		}
		if (this.child.size() <= index2) {
			return;
		}
		Object o1 = this.child.get(index1);
		Object o2 = this.child.get(index2);
		this.child.remove(index1);
		this.child.insertElementAt(o2, index1);
		this.child.remove(index2);
		this.child.insertElementAt(o1, index2);
	}	
		
		
	
	public void checkData() {
		StringBuffer buffer = new StringBuffer();
		try {
			debug( this.toString() );
		} catch (Exception e) {
			debug(e.toString());
		}
	}
	
	public String nvl(String strInput){
		if(strInput == null){
			return "";
		}else{
			return strInput;
		}
	}
	public void setDatum(String strKey, String strVal){
		this.prop.put(strKey, nvl(strVal));
		this.prop.setColInfo(strKey, Types.VARCHAR, STRING_DEFAULT_LENGTH);
	}
	public void setDatum(String strKey, int intVal){
		this.prop.put(strKey, String.valueOf(intVal));
		this.prop.setColInfo(strKey, Types.INTEGER, INT_DEFAULT_LENGTH);
	}
	public void setDatum(String strKey, short sVal){
		this.prop.put(strKey, String.valueOf(sVal));
		this.prop.setColInfo(strKey, Types.INTEGER, SHORT_DEFAULT_LENGTH);
	}		
	public void setDatum(String strKey, byte bVal){
		this.prop.put(strKey, String.valueOf(bVal));
		this.prop.setColInfo(strKey, Types.BIT, BYTE_DEFAULT_LENGTH);
	}		
	public void setDatum(String strKey, char cVal){
		this.prop.put(strKey, String.valueOf(cVal));
		this.prop.setColInfo(strKey, Types.CHAR, CHAR_DEFAULT_LENGTH);
	}
	public void setDatum(String strKey, float fVal){
		this.prop.put(strKey, String.valueOf(fVal));
		this.prop.setColInfo(strKey, Types.FLOAT, FLOAT_DEFAULT_LENGTH);
	}
	public void setDatum(String strKey, boolean boolVal){
		this.prop.put(strKey, String.valueOf(boolVal));
		this.prop.setColInfo(strKey, Types.BOOLEAN, BOOLEAN_DEFAULT_LENGTH);
	}	
	public void setDatum(String strKey, double dVal){
		this.prop.put(strKey, String.valueOf(dVal));
		this.prop.setColInfo(strKey, Types.DOUBLE, DOUBLE_DEFAULT_LENGTH);
	}
	public void setDatum(String strKey, long lVal){
		this.prop.put(strKey, String.valueOf(lVal));
		this.prop.setColInfo(strKey, Types.LONGVARCHAR, LONG_DEFAULT_LENGTH);
	}
	public String getDatum(String strKey){
		String strOutput = null;
		try {
			if (prop != null && prop.containsKey(strKey) ) {
				strOutput = (String)prop.get(strKey);
			}
		} catch (java.lang.Exception e) {
			debug("[" + strKey + "] 값을 찾는중 오류발생. 세부사항:" + e.toString());
		} 
		return strOutput;
	}
	
	public String getDatum(int index, String strKey){
		return getRow(index).getDatum(strKey);
	}	
	public String getDatum(String strKey, String strDefaultVal){
		String strOutput = null;
		try {
			if (prop != null && prop.containsKey(strKey) ) {
				strOutput = (String)prop.get(strKey);
			}
			if(strOutput == null || strOutput.equals("")){
				strOutput  = strDefaultVal;
			}
		} catch (java.lang.Exception e) {
			debug("[" + strKey + "] 값을 찾는중 오류발생. 세부사항:" + e.toString());
		} 
		return strOutput;
	}
	
	public String getDatum(int index, String strKey, String strDefaultVal){
		return getRow(index).getDatum(strKey, strDefaultVal);
	}	
		
	public int getIntDatum(String strKey){
		int intOutput = 0;
		try {
			intOutput = Integer.parseInt( getDatum(strKey) );
		} catch (java.lang.Exception e) {
			debug("[" + strKey + "] 값을 찾는중 오류발생. 세부사항:" + e.toString());
		} 
		return intOutput;
	}
	

	public int getIntDatum(int index, String strKey){
		return getRow(index).getIntDatum(strKey);
	}		
		
	public int getIntDatum(String strKey, int intDefaultVal){
		int intOutput = 0;
		String strOutput = null;
		try {
			strOutput = getDatum(strKey);
			if(strOutput == null || strOutput.equals("")){
				intOutput  = intDefaultVal;
			}else{
				intOutput = Integer.parseInt( getDatum(strKey) );
			}
		} catch (java.lang.Exception e) {
			debug("[" + strKey + "] 값을 찾는중 오류발생. 세부사항:" + e.toString());
		} 
		return intOutput;
	}
	
	public int getIntDatum(int index, String strKey, int intDefaultVal){
		return getRow(index).getIntDatum(strKey, intDefaultVal);
	}	
						
	
	
	public String toString(String strTab, int intIndex){
		StringBuffer buffer = new StringBuffer();
		Object o = null;
		String strNewTab = "";
		String strIndex = "";
		String strTemp = "";
		try {
			//buffer.append("\n");
			if(intIndex > -1){
				//strIndex = String.valueOf(intIndex);
				//strIndex = "ROW INDEX [" + strIndex + "]";
				//buffer.append(strTab).append(strIndex).append("\t");
			}else{
				buffer.append(strTab).append("/").append(CARRIER_MASK_BAR_2).append(strIndex).append(" DATA IN DATASET START ").append(CARRIER_MASK_BAR_2).append("/");
			}

			if (this.prop != null && this.prop.size()>0) {
				buffer.append("\n");
				strTemp = propToString(strTab);
				buffer.append( strTemp );
			}
			if (this.child != null) {
				if (getRowCnt() > 0) {
					buffer.append("\n");
				}
				strTemp = childToString(strTab);
				buffer.append( strTemp );
			}			
			if(intIndex > -1){
			}else{
				buffer.append("\n");
				buffer.append(strTab).append("/").append(CARRIER_MASK_BAR_2).append(strIndex).append(" DATA IN DATASET END  ").append(CARRIER_MASK_BAR_2).append("/");
			}
			
		} catch (Exception e) {
			debug(e);
		}
		return buffer.toString();
	}
	
	protected String childToString(String strTab){
		StringBuffer buffer = new StringBuffer();
		Object o = null;
		String strNewTab = "";
		try {
			String[] tempCols = getCols();
			String[] cols = null;
			DataSet row = null;
			for (int i = 0; i < this.getRowCnt(); i++) {
				row = this.getRow(i);	
				cols = row.getCols();	
				if(i==0){
					buffer.append(strTab).append("[Child의 갯수]:" + this.getRowCnt()).append("\t").append("[Column의 갯수]:" + row.getColsCnt());
				}
				//Arrays.sort(cols);
				strNewTab = strTab + "\t\t";
				buffer.append( row.toString(strNewTab, i) );
			}
		} catch (Exception e) {
			debug(e);
		}
		return buffer.toString();
	}
	
	protected String propToString(String strTab){
		StringBuffer buffer = new StringBuffer();
		Object o = null;
		String strNewTab = "";
		try {
			if (this.prop != null) {
				java.util.Iterator enumObj = this.prop.keySet().iterator();
				String sTemp = "";
				buffer.append(strTab);
				while (enumObj.hasNext()) {
					sTemp = (String) enumObj.next();
					//buffer.append(strTab);
					o = this.prop.get(sTemp);
					if(o instanceof DataSet){
						strNewTab = strTab + "\t";
						buffer.append("[" + sTemp + "]:[").append("\n").append( ((DataSet)o).toString(strNewTab, -1) ).append(strTab).append("]").append("\n");
					}else if(o instanceof Byte){
						buffer.append("[" + sTemp + "]:[" + ((Byte)o).toString() + "]");
					}else if(o instanceof Character){
						buffer.append("[" + sTemp + "]:[" + ((Character)o).toString() + "]");
					}else if(o instanceof String){
						buffer.append("[" + sTemp + "]:[" + ((String)o).toString() + "]");
					}else if(o instanceof Integer){
						buffer.append("[" + sTemp + "]:[" + ((Integer)o).toString() + "]");
					}else if(o instanceof Float){
						buffer.append("[" + sTemp + "]:[" + ((Float)o).toString() + "]");
					}else if(o instanceof Double){
						buffer.append("[" + sTemp + "]:[" + ((Double)o).toString() + "]");
					}else if(o instanceof Short){
						buffer.append("[" + sTemp + "]:[" + ((Short)o).toString() + "]");
					}else if(o instanceof Long){
						buffer.append("[" + sTemp + "]:[" + ((Long)o).toString() + "]");
					}else if(o instanceof Boolean){
						buffer.append("[" + sTemp + "]:[" + ((Boolean)o).toString() + "]");
					}else{
					}
				}
			}
		} catch (Exception e) {
			debug(e);
		}
		return buffer.toString();
	}	
		
	
	
	public String toString(){
		return toString("", -1);
	}
	
	protected void screenObj(Object o) throws Exception {
		try {
			if(o instanceof DataSet){
			}else if(o instanceof Byte){
			}else if(o instanceof Character){
			}else if(o instanceof String){
			}else if(o instanceof Integer){
			}else if(o instanceof Float){
			}else if(o instanceof Double){
			}else if(o instanceof Short){
			}else if(o instanceof Long){
			}else if(o instanceof Boolean){
			}else{
				throw new Exception("지원되지 않는 데이터유형입니다.");
			}
		} catch (Exception e) {
			debug(e);
			throw e;
		}
	}	
	
	public void clear(){
		if(this.child != null){
			for(int i=0; i<getRowCnt();i++){
				((DataSet)this.child.get(i)).clear();
			}
			this.child.clear();
			this.child = null;
		}
		if(this.prop != null){
			this.prop.clear();
			this.prop = null;
		}
	}
	
	// 디비로 부터 읽어온 스트링을 한글캐릭터셋에 맞게 변환.
	private static String fromDbToKor(Object obj) {
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
	
}
