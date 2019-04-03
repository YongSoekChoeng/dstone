/*
 * Created on 2005. 10. 13.
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.dstone.common.utils;

import java.lang.reflect.Array;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import net.dstone.common.utils.BeanUtil.JsonPropertyNamingStrategy;
import net.dstone.common.utils.FldUtil.Member;
import net.dstone.common.utils.FldUtil.Struct;

/**
 * BNC(방카)온라인용 자료구조 클래스.
 * FLD, XML, JSON, VO 여러가지 형태의 데이터로 컨버젼 가능하고 자체적인 데이터저장 기능을 구현했음.
 */
@SuppressWarnings("serial")
public class DataSet implements java.io.Serializable {

	private static LogUtil logger = new LogUtil(DataSet.class);
	
	/**
	 * 데이터의 가장 말단(Leaf) 구현체
	 *
	 */
	protected class Datum  implements java.io.Serializable {
		
		private ArrayList<String> val;
		
		@SuppressWarnings("unused")
		private Datum(){
			
		}
		protected Datum(String val){
			if(this.val == null){
				this.val = new ArrayList<String>();
			}
			this.val.add(val);
		}
		protected boolean isArray(){
			boolean isArray = false;
			if(this.val != null && this.val.size() > 1){
				isArray = true;
			}
			return isArray;
		}
		protected String getVal(){
			String val = null;
			if(this.val.size() > 0){
				val = this.val.get(0);
			}
			return val;
		}
		protected String[] getArrayVal(){
			String[] valArr = null;
			if(this.val.size() > 0){
				valArr = new String[this.val.size()];				
				for(int i=0; i<this.val.size(); i++){
					valArr[i] = this.val.get(i);
				}
			}
			return valArr;
		}
		public void setVal(String val){
			if(this.val != null){
				this.val.clear();
			}else{
				this.val = new ArrayList<String>();
			}
			if(DATA_TRIM_YN){
				this.val.add((!StringUtil.isEmpty(val)?val.trim():val));
			}else{
				this.val.add(val);
			}
		}
		protected void addVal(String val){
			if(this.val == null){
				this.val = new ArrayList<String>();
			}
			this.val.add(val);
		}
		protected boolean isEmpty(){
			boolean isEmpty = true;
			if(this.val.size() > 0){
				for(int i=0; i<this.val.size(); i++){	
					if( !StringUtil.isEmpty(this.val.get(i)) ){
						isEmpty = false;
						break;
					}		
					if( !StringUtil.isEmpty(this.val.get(i)) && !this.val.get(i).equals(JN) ){
						isEmpty = false;
						break;
					}
				}
			}
			return isEmpty;
		}
		
		public String toString(String strTab, String keyName){
			StringBuffer buff = new StringBuffer();
			String valItem = "";
			buff.append(strTab).append(keyName).append( ( !StringUtil.isEmpty(getComment(keyName) ) ? "-("+getComment(keyName) +")" : "" )  ).append(":");
			for(int i=0; i<this.val.size(); i++){
				valItem = (String)this.val.get(i);
				if( !StringUtil.isEmpty(valItem) ){
					buff.append(valItem);
				}
				if(i<(this.val.size()-1)){
					buff.append(", ");
				}
			}
			return buff.toString();
		}

		public String toXml(String strTab, String keyName){
			StringBuffer buff = new StringBuffer();
			String valItem = "";
			String attributeTag = "";
			for(int i=0; i<this.val.size(); i++){
				valItem = (String)this.val.get(i);
				attributeTag = "";
				if( StringUtil.isEmpty(valItem) ){
					continue;
					//buff.append(strTab).append("<"+keyName+"/>");
				}else{
					attributeTag = getDataSetExtraInfo(EXTRA_INFO_XML_ATTR_FOR_VAL, keyName);
					if( StringUtil.isEmpty(attributeTag) ){
						buff.append(strTab).append("<" + keyName+""+ ( !StringUtil.isEmpty(getComment(keyName) ) ? " name='"+getComment(keyName) +"'" : "" )  +">").append(valItem).append("</" + keyName+">");
					}else{
						buff.append(strTab).append("<" + keyName+" "+attributeTag+"='"+valItem+"' "+ ( !StringUtil.isEmpty(getComment(keyName) ) ? " name='"+getComment(keyName) +"'" : "" )  +"/>");
					}
				}
				if(i<(this.val.size()-1)){
					buff.append(N);
				}
			}
			return buff.toString();
		}
		public String toJson(String strTab, String keyName){
			StringBuffer buff = new StringBuffer();
			String valItem = "";
			
			if( this.val.size() == 0 ){
				buff.append(strTab).append(Q+keyName+Q +" : " + JN );
			}else if( this.val.size() == 1  ){
				valItem = (String)this.val.get(0);
				//valItem = StringUtil.replace(valItem, "\"", "`");
				//valItem = StringUtil.replace(valItem, "'", "`");
				buff.append(strTab).append(Q+keyName+Q +" : " +  (StringUtil.isEmpty(valItem)?JN:Q+valItem+Q) );
			}else{
				buff.append(strTab).append(Q+keyName+Q +" : [" );
				for(int i=0; i<this.val.size(); i++){
					valItem = (String)this.val.get(i);
					//valItem = StringUtil.replace(valItem, "\"", "`");
					//valItem = StringUtil.replace(valItem, "'", "`");
					buff.append(Q+valItem+Q);
					if(i<this.val.size()-1){
						buff.append(", ");
					}
				}
				buff.append( "]" );
			}
			return buff.toString();
		}
		
		public Object toVo(){
			Object propVal = null;
			try {
				if( this.val.size() == 0 ){
					propVal = null;
				}else if( this.val.size() == 1 ){
					propVal =  this.val.get(0);
				}else{
					propVal = Array.newInstance(Object.class, this.val.size());
					for(int k=0; k<this.val.size(); k++){
						Array.set(propVal, k, this.val.get(k));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return propVal;
		}
		
	}

	/***************************** 제어용상수/변수 시작 ************************/
	public static final String N 											= "\n";
	public static final String T 											= "\t";
	public static final String Q 											= "\"";
	public static final String JN 											= "null";
	public static final String NGS_JSON_HEADER_ROOT							= "header";
	public static final String NGS_JSON_BIZ_ROOT							= "payload";
	public static final String EXTRA_INFO_USE_JSON_MAP_WHEN_SINGLE_ARRAY 	= "USE_JSON_MAP_WHEN_SINGLE_ARRAY";		/* JSON 으로 전환 시 동일KEY값에 대해 단건일 경우 Map형태로 표기할지여부 종목key. 기본은 'N' - 단건이라도 Array([])로 표현. */
	public static final String EXTRA_INFO_XML_ATTR_FOR_VAL 					= "EXTRA_INFO_XML_ATTR_FOR_VAL";		/* XML로 전환 시 Text Content가 아닌 Attribute에 Value를 세팅할 경우 사용할  종목key.  */
	public static final String REAL_COUNT									= "_REAL_COUNT";
	public static boolean FIXED_LENG_STRING_DEBUG_YN 						= false;								/* 고정길이스트링으로 변환될 때(toFld 메소드 사용 시) 디버깅여부 */
	public static boolean DATA_TRIM_YN				 						= true;									/* 데이터를 세팅할 때 Trim 여부 */
	private boolean isNullDataSetHide										= true; 								/* NULL값일 때 보여줄지여부. toString 메소드에만 적용. */
	private boolean isCommentShow											= false; 								/* 코멘트를 보여줄지여부. */
	/***************************** 제어용상수/변수 끝   ************************/
	
	/***************************** 저장용변수 시작 *****************************/
	private HashMap<String, Datum> 						datumMap 			= null;	/* 원자성(ATOMIC)정보(Datum)를 저장할 변수 	*/
	private HashMap<String, List<DataSet>> 				dataSetMap			= null;	/* 복합(COMPLEX)정보(Data)를 저장할 변수 	*/
	private LinkedList<String> 							dataOrderInfo		= null;	/* 순서정보를 저장할 변수 					*/
	private HashMap<String, String> 					dataComment			= null;	/* 주석정보를 저장할 변수 	*/
	private HashMap<String, HashMap<String, String>> 	dataSetExtraInfo	= null;	/* 복합(COMPLEX)정보(Data)의 부가정보를 저장할 변수 	*/
	/***************************** 저장용변수 끝 *******************************/

	/***************************** 유틸용변수 시작 *****************************/
	XmlUtil xmlUtil;
	ObjectMapper jsonMapper;
	Struct struct;
	/***************************** 유틸용변수 끝 *******************************/

	/***************************** 생성자 시작 *********************************/
	public DataSet(){
		init();
	}
	/***************************** 생성자 끝 ***********************************/
	
	/************************* 유틸기능메소드 시작 *****************************/
	private void debug(Object o){
		StringBuffer logBuff = new StringBuffer();
		logBuff.append(N);
		logBuff.append(o);
		logBuff.append(N);
		logger.info(logBuff.toString());
	}
	
	private void init(){
		this.datumMap 			= new HashMap<String, Datum>();
		this.dataSetMap 		= new HashMap<String, List<DataSet>>();
		this.dataOrderInfo		= new LinkedList<String>();
		this.dataComment		= new HashMap<String, String>();
		this.dataSetExtraInfo	= new HashMap<String, HashMap<String, String>>();
	}
	
	public void clear(){ 
		this.datumMap.clear();
		this.dataSetMap.clear();
		this.dataOrderInfo.clear();
		this.dataComment.clear();
		this.dataSetExtraInfo.clear();
	}
	public void clearValue(){ 
		String key = "";
		List<DataSet> list = null;
		for( int i=0; i<this.dataOrderInfo.size(); i++ ){
			key = this.dataOrderInfo.get(i);
			if( this.datumMap.containsKey(key) ){
				this.datumMap.put(key, new Datum(""));
			}
			if( this.dataSetMap.containsKey(key) ){
				list = this.dataSetMap.get(key);
				if( list != null ){
					for(int k=0; k<list.size(); k++){
						list.get(k).clearValue();
					}
				}
			}
		}
	}
	
	/**
	 * 현재 DataSet 의 내용을 복사한 새로운 DataSet을 반환한다.( Deep Copy )
	 * @return DataSet
	 * @throws Exception
	 */
	public DataSet copy() throws Exception{ 
		DataSet copyDs = new DataSet();
		//copyDs = (DataSet)org.apache.commons.lang.SerializationUtils.clone(this);
		copyDs = (DataSet)BeanUtil.copyObj(this);
		return copyDs;
	}
	public boolean isEmpty(){ 
		boolean isEmpty = true;
		String key = "";
		for(int k=0; k<this.dataOrderInfo.size(); k++){
			key = this.dataOrderInfo.get(k);
			isEmpty = this.isDataEmpty(key);
			if( !isEmpty){
				break;
			} 
		}
		return isEmpty;
	}
	
	private boolean isDataEmpty(String key){
		boolean isEmpty = true;
		if(this.datumMap.containsKey(key)){
			isEmpty = this.datumMap.get(key).isEmpty();
		}else if(this.dataSetMap.containsKey(key)){
			List<DataSet> dsList = this.dataSetMap.get(key);
			for(int i=0; i<dsList.size(); i++){
				isEmpty = dsList.get(i).isEmpty();
				if( !isEmpty){
					break;
				}
			}
		}
		return isEmpty;		
	}

	private String getValFromFld(String key, byte[] str, int offSet, int length){
		String val;
		int size = length-offSet;
		val = new String( StringUtil.subByte(str, offSet, length) );
		if( val.getBytes().length > size ){
			val = val.replaceAll(StringUtil.BAD_CHAR_REXP, "");
			val = StringUtil.filler(val, size, " "); 
		}
		if(DATA_TRIM_YN){
			val = val.trim();
		}
//System.out.println( "getValFromFld :: key["+key+"] val["+val+"] length["+size+"] realSize["+val.getBytes().length+"]" );		
		return val;
	}

	private byte[] setValToFld(String key, String pStr, int length){		
		String str = StringUtil.nullCheck(pStr, "");
		byte[] strByteArray = new byte[0];
		try {
			strByteArray = str.getBytes();
			if( strByteArray.length >  length ){
				//strByteArray = StringUtil.subByte(str.getBytes(), 0, length);
				strByteArray = StringUtil.subByteFilterBadChar(str.getBytes(), 0, length, true);
			}else{
				//strByteArray = StringUtil.filler(str, length, " ").getBytes();
				strByteArray = StringUtil.backFiller(str, length, " ").getBytes();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.println( "setValToFld :: key["+key+"] length["+length+"] realSize["+strByteArray.length+"] val["+new String(strByteArray)+"]" );		
		return strByteArray;
	}
	
	private byte[] appendByte(String tabSpace, String key, String keyNm, byte[] srcByteArr, byte[] tgtByteArr) {
		byte[] message = StringUtil.appendByte(srcByteArr, tgtByteArr);
		if( FIXED_LENG_STRING_DEBUG_YN ){
			logger.info( tabSpace + (StringUtil.isEmpty(keyNm)?"":"(" + keyNm+ ")") + "["+key+"] 항목길이["+tgtByteArr.length+"] 누적길이["+message.length+"] 항목값["+new String(tgtByteArr)+"]" );
		}
		return message;
	}

	/**
	 * DataSet 의 정보내용을 확인하기위한 메소드
	 * @return void
	 */
	public void checkData(){
		StringBuffer buffer = new StringBuffer();
		buffer.append(N);
		buffer.append("").append("||******************************* ").append(" DataSet" + (struct !=null ? "["+struct.getStructId()+"]" : "" ) + " Start ").append(" *******************************||").append(N);
		buffer.append(this.toString("")).append(N);
		buffer.append("").append("||******************************* ").append(" DataSet" + (struct !=null ? "["+struct.getStructId()+"]" : "" ) + " End ").append(" *******************************||").append(N);
		//debug(buffer.toString());
		System.out.println(buffer.toString());
	}
	
	private ObjectMapper getJsonMapper(){
		try {
			this.jsonMapper = new ObjectMapper();
			this.jsonMapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
			this.jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			this.jsonMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
			this.jsonMapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
			this.jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			this.jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this.jsonMapper;
	}

	/**
	 * FLD 스트링으로부터 DataSet을 빌드한다.
	 * @param fixedBytes(FLD 스트링)
	 * @param layOutId
	 * @return DataSet
	 */
	public DataSet buildFromFld(byte[] fixedBytes, String layOutId) {
		Struct struct = null;
		int offSet = 0;
		try {
			this.clear();
			struct = FldUtil.getStructManager().getStructInfo(layOutId);
			if( struct != null ){
				this.struct = struct;
				buildFromFld(fixedBytes, offSet, true);
			}else{
				throw new Exception("레이아웃ID["+layOutId+"]에 해당하는 정보가 존재하지 않습니다.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	
	private int buildFromFld(byte[] fixedBytes, int idx, boolean isSetValue ) {
		Member[] childMemberList = null;
		Member childMember = null;
		int arrayCount = 0;
		int realRrrayCount = 0;
		int arrayCountSize = 0;
		DataSet childDs = null;
		String val = "";
		boolean isSetSubValue = true;
		
		String debugStr = "";
		try {
			childMemberList = this.struct.getMemberList();
			if( childMemberList != null ){
				for(int i=0; i<childMemberList.length; i++){
					childMember = childMemberList[i];
					isSetSubValue = true;
					debugStr = "id["+childMember.getId()+"]";
					if( childMember.isAtomic() ){
						if( childMember.isArray() ){
							for(int k=0; k<childMember.getArrayCount(); k++ ){
								/************************** 바이트절삭 처리 시작 **************************/
								val = getValFromFld(childMember.getId(),  fixedBytes, idx, idx+childMember.getSize() );
								idx = idx + childMember.getSize();
								/************************** 바이트절삭 처리 끝 ****************************/
								if(!StringUtil.isEmpty(val)){
									if(isSetValue)this.addDatum(childMember.getId(), val, childMember.getName());
								}
							}
						}else{
							/************************** 바이트절삭 처리 시작 **************************/
							val = getValFromFld(childMember.getId(), fixedBytes, idx, idx+childMember.getSize() );
							idx = idx + childMember.getSize();
							/************************** 바이트절삭 처리 끝 ****************************/
							if(!StringUtil.isEmpty(val)){
								if(isSetValue)this.addDatum(childMember.getId(), val, childMember.getName()); 
							}
						}
					}else{
						arrayCountSize = childMember.getArrayCountSize();
						/************************** 바이트절삭 처리 시작 **************************/
						val = getValFromFld(childMember.getId()+"카운트", fixedBytes, idx, idx+arrayCountSize );
						idx = idx + arrayCountSize;
						realRrrayCount = Integer.parseInt(StringUtil.nullCheck(val, "0"));
						/************************** 바이트절삭 처리 끝 ****************************/
						
						if( childMember.isArray() ){
							arrayCount = childMember.getArrayCount();				
							for(int k=0; k<arrayCount; k++ ){
								childDs = this.addDataSet(childMember.getId(), childMember.getName());
								childDs.struct = this.struct.getChildStruct(childMember.getId());
//								if(isSetValue){
//									childDs = this.newDataSet(childMember.getId());
//									childDs.struct = this.struct.getChildStruct(childMember.getId());
//								}
								if( k >= realRrrayCount ){
									isSetSubValue = false;
								}
								idx = childDs.buildFromFld(fixedBytes, idx, isSetSubValue);
							}
						}else{
							childDs = this.addDataSet(childMember.getId(), childMember.getName());
							childDs.struct = this.struct.getChildStruct(childMember.getId());
							idx = childDs.buildFromFld(fixedBytes, idx, isSetValue);
						}
					}
				}
			}
		} catch (Exception e) {
			debug(debugStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
		return idx;
	}

	/**
	 * XML 스트링으로부터 DataSet을 빌드한다.
	 * @param xmlStr(XML 스트링)
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 DataSet을 빌드한다. 전체를 빌드하고자 할 경우 빈스트링[""] 세팅.
	 * @return DataSet
	 */
	public DataSet buildFromXml(String xmlStr, String rootNodeNm) {
		Node pNode = null;
		try {
			this.clear();
			this.xmlUtil = XmlUtil.getInstance(XmlUtil.XML_SOURCE_KIND_STRING, xmlStr);
			pNode = this.xmlUtil.getNode(rootNodeNm);
			if(pNode != null){
				if(pNode.getNodeType() == Node.ELEMENT_NODE ){
					buildFromXml(pNode);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	private void buildFromXml(Node pNode) {
		NodeList nodeList;
		Node cNode = null;		
		DataSet childDs = null;
		String debufStr = "";
		try {
			nodeList = pNode.getChildNodes();
			if( nodeList != null ){
				for(int i=0; i<nodeList.getLength(); i++){
					cNode = nodeList.item(i);
					debufStr = cNode.getNodeName();
					if(cNode.getNodeType() == Node.ELEMENT_NODE ){
						// LEAF 일 경우
						if( !xmlUtil.hasChildElement(cNode) ){
							this.addDatum(cNode.getNodeName(), cNode.getTextContent());
						// BRANCH 일 경우	
						}else{
							childDs = this.addDataSet(cNode.getNodeName());
							childDs.xmlUtil = this.xmlUtil;
							childDs.buildFromXml(cNode);
						}
					}
				}
			}	
		} catch (Exception e) {
			debug(debufStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
	}
	
	/**
	 * JSON 스트링으로부터 DataSet을 빌드한다.
	 * @param jsonStr(JSON 스트링)
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 DataSet을 빌드한다. 전체를 빌드하고자 할 경우 빈스트링[""] 세팅.
	 * @return DataSet
	 */
	public DataSet buildFromJson(String jsonStr, String rootNodeNm) {
		JsonNode rootNode;
		JsonNode pNode;
		try {
			this.clear();
			if(!StringUtil.isEmpty(jsonStr)){
				jsonStr = jsonStr.replaceAll("[\\x00-\\x09\\x11\\x12\\x14-\\x1F\\x7F]", "");
				this.jsonMapper = getJsonMapper();
				rootNode = this.jsonMapper.readTree(jsonStr);
				pNode = rootNode.get(rootNodeNm);
				if(pNode == null){
					pNode = rootNode;
				}
				buildFromJson(pNode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	
	private void buildFromJson(JsonNode pNode) {
		Iterator<String> cNodeKeys;
		String cNodeKey;
		JsonNode cNode = null;		
		ArrayNode cNodeArray = null;	
		DataSet childDs = null;
		String debugStr = "";
		try {
			cNodeKeys = pNode.fieldNames();
			while( cNodeKeys.hasNext() ){
				cNodeKey = cNodeKeys.next();
				cNode = pNode.get(cNodeKey);
				// 배열일 경우
				if( cNode.isArray() ){
					cNodeArray = (ArrayNode)cNode;
					if(cNodeArray != null){
						for(int i=0; i<cNodeArray.size(); i++){
							JsonNode cNodeArrayItem = cNodeArray.get(i);
							// COMPLEX 타입일 경우
							if( cNodeArrayItem.isContainerNode() ){
								childDs = this.addDataSet(cNodeKey);
								childDs.buildFromJson(cNodeArrayItem);
							// ATOMIC 타입일 경우	
							}else{
								this.addDatum(cNodeKey, cNodeArrayItem.isNumber()?cNodeArrayItem.asText():cNodeArrayItem.textValue());
							}
						}
					}
				// 단일건일 경우	
				}else{
					// COMPLEX 타입일 경우
					if( cNode.isContainerNode() ){
						childDs = this.addDataSet(cNodeKey);
						childDs.buildFromJson(cNode);
					// ATOMIC 타입일 경우	
					}else{
						this.addDatum(cNodeKey, cNode.isNumber()?cNode.asText():cNode.textValue());
					}
				}
				//debugStr = cNodeKey + " : isArray["+cNode.isArray()+"] isObject["+cNode.isObject()+"] isContainerNode["+cNode.isContainerNode()+"] cNode.get(0).isContainerNode["+(cNode.get(0)==null?false:cNode.get(0).isContainerNode())+"]" ;
				//debug(debugStr);			
			}
		} catch (Exception e) {
			debug(debugStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
	}

	/**
	 * VO 객체로부터 DataSet을 빌드한다.
	 * @param bean(VO 객체)
	 * @param property 값이 존재할 경우 해당 property를 Root로 DataSet을 빌드한다. 전체를 빌드하고자 할 경우 빈스트링[""] 세팅.
	 * @return DataSet
	 */
	public DataSet buildFromVo(Object bean, String property) {
		Object pNode = null;
		try {
			this.clear();
			if(!StringUtil.isEmpty(property)){
				pNode = BeanUtil.getProperty(bean, property);
			}else{
				pNode = bean;
			}
			if(pNode != null){
				buildFromVo(pNode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	
	@SuppressWarnings({ "rawtypes", "unused" })
	private void buildFromVo(Object bean) {
		LinkedList<HashMap<String, String>> propertyInfoList = null;
		HashMap<String, String> propertyInfo = null;
		Object propertyVal = null;
		
		String propertyName = "";
		String className = "";
		boolean isNumber = false;
		boolean isArray = false;
		boolean isAtomic = false;
		boolean isArrayAtomic = false;
		boolean isList = false;
		boolean isListAtomic = false;
		String classListName = "";
		
		DataSet childDs = null;
		
		String debugStr = "";
		
		try {
			if(bean != null){

				propertyInfoList = BeanUtil.getPropertyInfoList(bean, false);
				if( propertyInfoList != null ){
					for(int i=0; i<propertyInfoList.size(); i++){
						propertyInfo = propertyInfoList.get(i);
						
						propertyName = propertyInfo.get("NAME");
						className = propertyInfo.get("CLASS");
						isNumber = Boolean.valueOf(propertyInfo.get("IS_NUMBER")).booleanValue();
						isArray = Boolean.valueOf(propertyInfo.get("IS_ARRAY")).booleanValue();
						isAtomic = Boolean.valueOf(propertyInfo.get("IS_ATOMIC")).booleanValue();
						isArrayAtomic = Boolean.valueOf(propertyInfo.get("IS_ARRAY_ATOMIC")).booleanValue();
						isList = Boolean.valueOf(propertyInfo.get("IS_LIST")).booleanValue();
						isListAtomic = Boolean.valueOf(propertyInfo.get("IS_LIST_ATOMIC")).booleanValue();
						classListName = propertyInfo.get("LIST_COMP_CLASS");
						
						//debugStr = bean.getClass().getName()  + "." + propertyName+ ": CLASS[" + className+ "] IS_ARRAY[" + isArray+ "] IS_ATOMIC[" + isAtomic+ "] IS_ARRAY_ATOMIC[" + isArrayAtomic+ "] IS_LIST[" + isList+ "] IS_LIST_ATOMIC[" + isListAtomic+ "] LIST_COMP_CLASS[" + classListName+ "]";
						//debug(debugStr);
						
						propertyVal = BeanUtil.getObject(bean, propertyName);
						
						if(propertyVal == null){
							this.addDatum(propertyName, null );
						}else{
							// 배열일 경우
							if( isArray ){
								int arrLen = Array.getLength(propertyVal);
								for(int k=0; k<arrLen; k++){
									// COMPLEX 타입일 경우
									if( !isArrayAtomic ){
										childDs = this.addDataSet(propertyName);
										childDs.buildFromVo(Array.get(propertyVal, k));
									// ATOMIC 타입일 경우	
									}else{
										this.addDatum(propertyName, Array.get(propertyVal, k).toString());
									}
								}
							// LIST일 경우
							}else if( isList ){
								List list = (List)propertyVal;
								for(int k=0; k<list.size(); k++){
									// COMPLEX 타입일 경우
									if( !isListAtomic ){
										childDs = this.addDataSet(propertyName);
										childDs.buildFromVo(list.get(k));
									// ATOMIC 타입일 경우
									}else{
										this.addDatum(propertyName, list.get(k).toString());
									}
								}
							// 단일건일 경우	
							}else{
								if( propertyVal != null ){
									// COMPLEX 타입일 경우
									if( !isAtomic ){
										childDs = this.addDataSet(propertyName);
										childDs.buildFromVo(propertyVal);
									// ATOMIC 타입일 경우	
									}else{
										this.addDatum(propertyName, propertyVal.toString());
									}
								}
							}
						}

					}
				}	
			}
		} catch (Exception e) {
			debug(debugStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
	}
	
	/**
	 * ResultSet으로부터 DataSet을 빌드한다.
	 * @param rs(DB조회결과 ResultSet)
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 DataSet을 빌드한다.
	 * @return DataSet
	 */
	public DataSet buildFromResultSet(ResultSet rs, String rootNodeNm) {
		return buildFromResultSet(rs, rootNodeNm, false);
	}
	
	/**
	 * ResultSet으로부터 DataSet을 빌드한다.
	 * @param rs(DB조회결과 ResultSet)
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 DataSet을 빌드한다.
	 * @param trimYn(트림여부)
	 * @return DataSet
	 */
	public DataSet buildFromResultSet(ResultSet rs, String rootNodeNm, boolean trimYn) {
		try {
			this.clear();
			java.sql.ResultSetMetaData rsMeta = rs.getMetaData();
			String columnName = "";
			String columnValue = "";
			int intColumnCount = rsMeta.getColumnCount();
			DataSet childDs = null;
			while (rs.next()) {
				childDs = this.addDataSet(rootNodeNm);
				for (int j = 0; j < intColumnCount; j++) {
					columnName = rsMeta.getColumnName(j+1);
					columnValue = rs.getString( columnName );
					if( trimYn ){
						if(columnValue != null){
							columnValue = columnValue.trim();
						}
					}
					childDs.setDatum(columnName, columnValue );
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	
	public void setDefaultVal(String defaultVal){
		String strKey = "";
		Datum datum = null;
		List<DataSet> dsList = null;
		DataSet ds = null;
		try {
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				strKey = this.dataOrderInfo.get(i);
				if(this.datumMap.containsKey(strKey)){
					datum = this.datumMap.get(strKey);
					datum.setVal(defaultVal);
				}else if(this.dataSetMap.containsKey(strKey)){
					dsList = this.dataSetMap.get(strKey);
					for(int k=0; k<dsList.size(); k++){
						ds = dsList.get(k);
						ds.setDefaultVal(defaultVal);
					}
				}
			}
		} catch (Exception e) {
			debug(e);
			e.printStackTrace();
		}
	}
	

	
	public void removeEmptyVal(){
		String strKey = "";
		try {
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				strKey = this.dataOrderInfo.get(i);
				if(this.isDataEmpty(strKey)){
					this.removeChild(strKey);
				}
			}
		} catch (Exception e) {
			debug(e);
			e.printStackTrace();
		}
	}
	
	/**
	 * DataSet 의 정보내용을 String 으로 직렬화하는 메소드
	 * @return String
	 */
	public String toString(){
		return toString("");
	}
	
	/**
	 * DataSet 의 정보내용을 String 으로 직렬화하는 메소드
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 Wrapping하여 직렬화한다.
	 * @return String
	 */
	public String toString(String rootNodeNm){
		String dsStr = "";
		String strTab = "";
		StringBuffer buffer = new StringBuffer();
		if( !StringUtil.isEmpty(rootNodeNm) ){
			buffer.append("["+rootNodeNm + "]").append(N);
			strTab = T;
		}
		buffer.append(toString(strTab, rootNodeNm));
		dsStr = buffer.toString();
		if(dsStr.endsWith(N)){
			dsStr = dsStr.substring(0, dsStr.length()-1);
		}
		return dsStr;
	}
	
	private String toString(String strTab, String strKey){
		StringBuffer buffer = new StringBuffer();
		String strDs = "";
		String strChildTab = strTab + T;
		Datum datum = null;
		List<DataSet> dsList = null;
		DataSet ds = null;
		
		try {
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				strKey = this.dataOrderInfo.get(i);
				if(this.datumMap.containsKey(strKey)){
					datum = this.datumMap.get(strKey);
					if( !this.isNullDataSetHide || !datum.isEmpty() ){
						buffer.append(datum.toString(strChildTab, strKey)).append(N);
					}
				}else if(this.dataSetMap.containsKey(strKey)){
					dsList = this.dataSetMap.get(strKey);
					for(int k=0; k<dsList.size(); k++){
						ds = dsList.get(k);
						if( !this.isNullDataSetHide || !ds.isEmpty() ){
							buffer.append(strChildTab).append(strKey).append( dsList.size()>1?"["+k+"]":"" ).append(N);
							buffer.append(ds.toString(strChildTab, strKey));
						}
					}
				}
			}
			strDs = buffer.toString();
		} catch (Exception e) {
			debug(e);
			e.printStackTrace();
		}
		return strDs;
	}

	/**
	 * DataSet 의 정보내용을 FLD 로 직렬화하는 메소드
	 * @param layOutId 직렬화기준이 될 레이아웃ID한다.
	 * @return String
	 */
	public String toFld(String layOutId) throws Exception{
		this.struct = FldUtil.getStructManager().getStructInfo(layOutId);
		if( FIXED_LENG_STRING_DEBUG_YN ){
			logger.info("||========================================= DataSet 레이아웃["+layOutId+"] Start =========================================||");
		}
		String strOut = new String( toFld("", new byte[]{} ) );
		if( FIXED_LENG_STRING_DEBUG_YN ){
			logger.info("||========================================= DataSet 레이아웃["+layOutId+"] End =========================================||");
		}
		return strOut;
	}
	
	private byte[] toFld(String tabSpace, byte[] strByteArray){
		
		Member[] childMemberList = null;
		Member childMember = null;
		DataSet childDs = null;
		String dsVal = "";
		int arrayCount = 0;
		String arrayCountStr = "";
		String childId = "";
		String childNm = "";
		
		String debugStr = "";
		String subTabSpace = tabSpace + T;
		
		try {
			childMemberList = this.struct.getMemberList();
			if( childMemberList != null ){
				for(int i=0; i<childMemberList.length; i++){
					childMember = childMemberList[i];
					childId = childMember.getId();
					childNm = childMember.getName();
					debugStr = "id["+childId+"]";

					// ATOMIC 타입일 경우	
					if( childMember.isAtomic() ){
						// 배열일 경우
						if( childMember.isArray() ){
							for(int k=0; k<childMember.getArrayCount(); k++ ){
								dsVal = "";
								childId = childMember.getId()+"-배열("+k+")";
								if( this.getDatumArray(childMember.getId()) != null && k < this.getDatumArray(childMember.getId()).length ){
									dsVal = this.getDatumArray(childMember.getId())[k];
								}
								/************************** 고정길이세팅처리 시작 **************************/
								//strByteArray = StringUtil.appendByte(strByteArray, setValToFld(childMember.getId(), dsVal, childMember.getSize() ));
								strByteArray = appendByte(subTabSpace, childId, childNm, strByteArray, setValToFld(childMember.getId(), dsVal, childMember.getSize() ) );
								/************************** 고정길이세팅처리 끝 ****************************/
							}
						// 단건일 경우
						}else{
							dsVal = "";
							if( !StringUtil.isEmpty(this.getDatum(childMember.getId())) ){
								dsVal = this.getDatum(childMember.getId());
							}
							/************************** 고정길이세팅처리 시작 **************************/
							//strByteArray = StringUtil.appendByte(strByteArray, setValToFld(childMember.getId(), dsVal, childMember.getSize() ));
							strByteArray = appendByte(subTabSpace, childId, childNm, strByteArray, setValToFld(childMember.getId(), dsVal, childMember.getSize() ) );
							/************************** 고정길이세팅처리 끝 ****************************/
						}
					// COMPLEX 타입일 경우	
					}else{
						// 사이즈세팅
						if( childMember.getArrayCountSize() > 0){
							//arrayCount = childMember.getArrayCount();
							arrayCount = 0;
							childId = childMember.getId()+"-배열사이즈";
							if( this.getDataSetList(childMember.getId()) != null && this.getDataSetList(childMember.getId()).size()>0 ){
								arrayCount = this.getDataSetList(childMember.getId()).size();
							}
							arrayCountStr = StringUtil.filler(String.valueOf(arrayCount), childMember.getArrayCountSize(), "0");			
							/************************** 고정길이세팅처리 시작 **************************/		
							//strByteArray = StringUtil.appendByte(strByteArray, setValToFld(childMember.getId(), arrayCountStr, childMember.getArrayCountSize() ));
							strByteArray = appendByte(subTabSpace, childId, childNm, strByteArray, setValToFld(childMember.getId(), arrayCountStr, childMember.getArrayCountSize() ) );
							/************************** 고정길이세팅처리 끝 ****************************/
						}
						// 배열일 경우
						if( childMember.isArray() ){
							dsVal = "";
							for(int k=0; k<childMember.getArrayCount(); k++ ){
								childId = childMember.getId()+"-배열("+k+")";
								if( this.getDataSetList(childMember.getId()) != null && k < this.getDataSetList(childMember.getId()).size() ){
									childDs = this.getDataSetList(childMember.getId()).get(k);
									childDs.struct = this.struct.getChildStruct(childMember.getId());
								}else{
									childDs = this.addDataSet(childMember.getId());
									childDs.struct = this.struct.getChildStruct(childMember.getId());
								}
								/************************** 고정길이세팅처리 시작 **************************/		
								//strByteArray = StringUtil.appendByte(strByteArray, childDs.toFld( new byte[]{} ));
								strByteArray = appendByte(subTabSpace, childId, childNm, strByteArray, childDs.toFld( (subTabSpace+T), new byte[]{} ) );
								/************************** 고정길이세팅처리 끝 ****************************/
							}
						// 단건일 경우
						}else{
							if( this.getDataSet(childMember.getId(), 0) != null ){
								childDs = this.getDataSet(childMember.getId(), 0);
								childDs.struct = this.struct.getChildStruct(childMember.getId());
							}else{
								childDs = this.addDataSet(childMember.getId());
								childDs.struct = this.struct.getChildStruct(childMember.getId());
							}
							/************************** 고정길이세팅처리 시작 **************************/
							//strByteArray = StringUtil.appendByte(strByteArray, childDs.toFld( new byte[]{} ));
							strByteArray = appendByte(subTabSpace, childId, childNm, strByteArray, childDs.toFld( (subTabSpace+T), new byte[]{} ) );
							/************************** 고정길이세팅처리 끝 ****************************/
						}
					}
				}
			}
		} catch (Exception e) {
			debug(debugStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
		return strByteArray;
	}


	/**
	 * DataSet 의 정보내용을 XML 로 직렬화하는 메소드
	 * @return String
	 */
	public String toXml(){
		return toXml("");		
	}
	
	/**
	 * DataSet 의 정보내용을 XML 로 직렬화하는 메소드
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 Wrapping하여 직렬화한다.
	 * @return String
	 */
	public String toXml(String rootNodeNm){
		StringBuffer buffer = new StringBuffer();
		String strTab = "";
		if( !StringUtil.isEmpty(rootNodeNm) ){
			strTab = strTab + T;
			buffer.append("<"+rootNodeNm+">").append(N);
			buffer.append(toXml(strTab, rootNodeNm));
			buffer.append("</"+rootNodeNm+">");
		}else{
			buffer.append(toXml(strTab, rootNodeNm));
		}
		return buffer.toString();		
	}
	private String toXml(String strTab, String strKey){
		StringBuffer buffer = new StringBuffer();
		
		String strChildTab = strTab + T;
		Datum datum = null;
		List<DataSet> dsList = null;
		DataSet ds = null;
		String xmlStr = "";
		try {
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				strKey = this.dataOrderInfo.get(i);
				if(this.datumMap.containsKey(strKey)){
					datum = this.datumMap.get(strKey);
					if(datum.isEmpty()){
						continue;
					}else{
						buffer.append(datum.toXml(strTab, strKey)).append(N);
					}
				}else if(this.dataSetMap.containsKey(strKey)){
					dsList = this.dataSetMap.get(strKey);
					for(int k=0; k<dsList.size(); k++){
						ds = dsList.get(k);		
						if(ds.isEmpty()){
							continue;
							//buffer.append(strChildTab).append("<"+strKey+"/>").append(N);
						}else{		
							xmlStr = ds.toXml(strChildTab, strKey);
							if(!StringUtil.isEmpty(xmlStr)){
								buffer.append(strTab).append("<"+ strKey +">").append(N);
								buffer.append(ds.toXml(strChildTab, strKey));
								buffer.append(strTab).append("</"+ strKey +">").append(N);
							}else{
								continue;
								//buffer.append(strChildTab).append("<"+strKey+"/>").append(N);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			debug(e);
			e.printStackTrace();
		}
		return buffer.toString();
	}

	/**
	 * DataSet 의 정보내용을 JSON 으로 직렬화하는 메소드
	 * @return String
	 */
	public String toJson(){
		return toJson("");
	}
	
	/**
	 * DataSet 의 정보내용을 JSON 으로 직렬화하는 메소드
	 * @param rootNodeNm 값이 존재할 경우 해당 Node를 Root로 Wrapping하여 직렬화한다.
	 * @return String
	 */
	public String toJson(String rootNodeNm){
		StringBuffer buffer = new StringBuffer();
		if(!StringUtil.isEmpty(rootNodeNm)){
			buffer.append("{").append(N);
			buffer.append(T).append(Q + rootNodeNm + Q +" : ").append(N);
			buffer.append(toJson(T, rootNodeNm)).append(N);
			buffer.append("}");
		}else{
			buffer.append(toJson("", rootNodeNm));
		}
		return buffer.toString();
	}
	
	private String toJson(String strTab, String strKey){
		StringBuffer buffer = new StringBuffer();
		
		String strChildTab = strTab + T;
		Datum datum = null;
		List<DataSet> dsList = null;
		DataSet ds = null;
		List<DataSet> notNullDsList = new ArrayList<DataSet>();
		
		try {
			buffer.append(strTab).append("{" ).append(N);
			
			if( !this.isEmpty() ){
				for(int i=0; i<this.dataOrderInfo.size(); i++){
					strKey = this.dataOrderInfo.get(i);
					// ATOMIC 타입일 경우	
					if(this.datumMap.containsKey(strKey)){
						datum = this.datumMap.get(strKey);
						if(datum.isEmpty()){
							buffer.append(strChildTab).append(Q+strKey+Q +" : " + JN );
						}else{
							buffer.append(datum.toJson(strChildTab, strKey));
						}
					// COMPLEX 타입일 경우	
					}else if(this.dataSetMap.containsKey(strKey)){
						dsList = this.dataSetMap.get(strKey);
						notNullDsList.clear();
						for(int k=0; k<dsList.size(); k++){
							if( !dsList.get(k).isEmpty() ){
								notNullDsList.add(dsList.get(k));
							}
						}
						if(notNullDsList.size() == 0){
							buffer.append(strChildTab).append(Q+strKey+Q +" : " + JN );
						}else if( notNullDsList.size() == 1){
							if( strKey.equals(NGS_JSON_HEADER_ROOT) || strKey.equals(NGS_JSON_BIZ_ROOT) || this.getDataSetExtraInfo(strKey, EXTRA_INFO_USE_JSON_MAP_WHEN_SINGLE_ARRAY, "N").equals("Y") ){
								ds = notNullDsList.get(0);
								buffer.append(strChildTab).append(Q+strKey+Q +" : " ).append(N);
								buffer.append(ds.toJson(strChildTab, strKey));
							}else{
								buffer.append(strChildTab).append(Q+strKey+Q +" : [" ).append(N);
								for(int k=0; k<notNullDsList.size(); k++){
									ds = notNullDsList.get(k);
									buffer.append(ds.toJson(strChildTab+T, strKey));
									if(k<notNullDsList.size()-1){
										buffer.append(", ");
									}
									buffer.append(N);
								}
								buffer.append(strChildTab).append("]" );
							}
						}else{
							buffer.append(strChildTab).append(Q+strKey+Q +" : [" ).append(N);
							for(int k=0; k<notNullDsList.size(); k++){
								ds = notNullDsList.get(k);
								buffer.append(ds.toJson(strChildTab+T, strKey));
								if(k<notNullDsList.size()-1){
									buffer.append(", ");
								}
								buffer.append(N);
							}
							buffer.append(strChildTab).append("]" );
						}
					}
					if(i<this.dataOrderInfo.size()-1){
						buffer.append(", ");
					}
					buffer.append(N);
				}
			}
			

			buffer.append(strTab).append("}" );
		} catch (Exception e) {
			debug(e);
			e.printStackTrace();
		}
		return buffer.toString();
	}
	
	/**
	 * DataSet을 Bean객체로 전환한다.
	 * @param className 전환대상객체의 클래스
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public Object toVo(Class clazz){
		Object bean = null;
		String property = "";
		Object propertyVal = null;
		try {		
			bean = clazz.newInstance();
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				property = this.dataOrderInfo.get(i);
				if( BeanUtil.isBeanMemberName(clazz.getName(), property) ){
					propertyVal = toVo(bean, property);
					if(propertyVal != null){			
						BeanUtil.setProperty(bean, property, propertyVal);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return bean;
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object toVo(Object bean, String property){
		Object propertyObj = null;
		List<DataSet> dsList = null;
		List<DataSet> notNullDsList = new ArrayList<DataSet>();
		HashMap<String, String> propertyInfo = null;

		String className = "";
		boolean isArray = false;
		boolean isAtomic = false;
		boolean isArrayAtomic = false;
		boolean isList = false;
		boolean isListAtomic = false;
		String classListName = "";

		String debugStr = "";
		
		try {
			
			propertyInfo = BeanUtil.getPropertyInfo(bean, property);
			
			if(propertyInfo != null && propertyInfo.size() > 0){

				className = propertyInfo.get("CLASS");
				isArray = Boolean.valueOf(propertyInfo.get("IS_ARRAY")).booleanValue();
				isAtomic = Boolean.valueOf(propertyInfo.get("IS_ATOMIC")).booleanValue();
				isArrayAtomic = Boolean.valueOf(propertyInfo.get("IS_ARRAY_ATOMIC")).booleanValue();
				isList = Boolean.valueOf(propertyInfo.get("IS_LIST")).booleanValue();
				isListAtomic = Boolean.valueOf(propertyInfo.get("IS_LIST_ATOMIC")).booleanValue();
				classListName = propertyInfo.get("LIST_COMP_CLASS");
				
				//debugStr = bean.getClass().getName()  + "." + propertyInfo.get("NAME")+ ": CLASS[" + className+ "] IS_ARRAY[" + isArray+ "] IS_ATOMIC[" + isAtomic+ "] IS_ARRAY_ATOMIC[" + isArrayAtomic+ "] IS_LIST[" + isList+ "] IS_LIST_ATOMIC[" + isListAtomic+ "] LIST_COMP_CLASS[" + classListName+ "]";
				//debug(debugStr);
				
				// 배열일 경우
				if( isArray ){
					// COMPLEX 타입일 경우
					if( !isArrayAtomic ){
						dsList = this.getDataSetList(property);
						if( dsList != null ){
							notNullDsList.clear();
							for(int k=0; k<dsList.size(); k++){
								if( !dsList.get(k).isEmpty() ){
									notNullDsList.add(dsList.get(k));
								}
							}
							propertyObj = Array.newInstance(Class.forName(className), notNullDsList.size());
							for(int i=0; i<notNullDsList.size(); i++){
								Array.set(propertyObj, i, notNullDsList.get(i).toVo(Class.forName(className)) );
							}
						}
					// ATOMIC 타입일 경우	
					}else{
						propertyObj = this.datumMap.get(property).toVo();
					}
				// LIST일 경우
				}else if( isList ){
					// COMPLEX 타입일 경우
					if( !isListAtomic ){
						dsList = this.getDataSetList(property);			
						if( dsList != null ){
							List aList = new ArrayList();
							for(int i=0; i<dsList.size(); i++){
								if(!dsList.get(i).isEmpty()){
									aList.add(i, dsList.get(i).toVo(Class.forName(classListName)));
								}
							}
							propertyObj = (Object)aList;
						}
					// ATOMIC 타입일 경우
					}else{
						List aList = new ArrayList();
						for(int i=0; i<this.datumMap.get(property).val.size(); i++){
							
							if(!this.datumMap.get(property).isEmpty()){
								aList.add(this.datumMap.get(property).val.get(i));
							}
						}
						propertyObj = (Object)aList;
					}
					
				// 단일건일 경우	
				}else{
					// COMPLEX 타입일 경우
					if( !isAtomic ){
						dsList = this.getDataSetList(property);
						if(dsList!=null){
							if(!dsList.get(0).isEmpty()){
								propertyObj = dsList.get(0).toVo(Class.forName(className));
							}
						}
					// ATOMIC 타입일 경우	
					}else{
						if(!this.datumMap.get(property).isEmpty()){
							propertyObj = this.datumMap.get(property).toVo();
						}
					}
				}
			}

		} catch (Exception e) {
			debug(debugStr + " 수행중 예외발생 !!!");
			e.printStackTrace();
		}
		return propertyObj;
	}
	
	/**
	 * FLD 구조일 때 구조(Struct)정보를 반환한다.
	 * @return Struct
	 */
	public Struct getStruct() {
		return struct;
	}
	
	/**
	 * FLD 구조일 때 구조(Struct)정보를 세팅한다.
	 * @param Struct
	 */
	public void setStruct(Struct struct) {
		this.struct = struct;
	}

	/**
	 * 코멘트를 보여줄지 여부 조회
	 */
	public boolean isCommentShow() {
		return this.isCommentShow;
	}

	/**
	 * 코멘트를 보여줄지 여부 세팅
	 * @param isCommentShow the isCommentShow to set
	 */
	public void setCommentShow(boolean isCommentShow) {
		this.isCommentShow = isCommentShow;
	}

	/**
	 * 코멘트를 반환하는 메소드
	 * @param key(코멘트를보고자 하는 정보 key값)
	 */
	private String getComment( String key ){
		String comment = "";
		if( this.isCommentShow() ){
			if(this.dataComment.containsKey(key)){
				comment = this.dataComment.get(key);
			}
		}
		return comment;
	}

	/**
	 * NULL값일 때 보여줄지 여부 조회
	 */
	public boolean isNullDataSetHide() {
		return this.isNullDataSetHide;
	}

	/**
	 * NULL값일 때 보여줄지 여부 세팅
	 * @param isNullDataSetHide the isNullDataSetHide to set
	 */
	public void setNullDataSetHide(boolean isNullDataSetHide) {
		this.isNullDataSetHide = isNullDataSetHide;
	}

	/************************* 유틸기능메소드 끝   *****************************/
	
	/*********************** 데이터입출력관련 메소드 시작 **************************/
	/**
	 * 하위정보(Datum/DataSet)의 key값 배열을 반환하는 메소드.
	 * @return String[] key값 배열
	 */
	public String[] getChildrenKeys(){
		String[] dataKeys = null;
		try {
			dataKeys = new String[this.dataOrderInfo.size()];
			for(int i=0; i<this.dataOrderInfo.size(); i++){
				dataKeys[i] = this.dataOrderInfo.get(i);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dataKeys;
	}
	/**
	 * 하위정보(Datum/DataSet)의 사이즈를 반환하는 메소드.
	 * @return int key값 배열사이즈
	 */
	public int getChildrenCount(){
		return this.dataOrderInfo.size();
	}
	/**
	 * key로 저장된 하위정보(Datum/DataSet)가 원자성정보(Datum)인지 복합정보(DataSet)인지 확인하기 위한 메소드.
	 * @param key(하위정보(Datum/DataSet) key값)
	 * @return boolean 원자성정보(Datum)일 경우 false, 복합정보(DataSet)일 경우 true
	 */
	public boolean isDataSet(String key){
		boolean isDataSet = false;
		if(this.dataOrderInfo.contains(key)){
			if(this.dataSetMap.containsKey(key)){
				isDataSet = true;
			}
		}
		return isDataSet;
	}
	/**
	 * index로 저장된 하위정보(Datum/DataSet)가 원자성정보(Datum)인지 복합정보(DataSet)인지 확인하기 위한 메소드.
	 * @param index(하위정보(Datum/DataSet) 인덱스값)
	 * @return boolean 원자성정보(Datum)일 경우 false, 복합정보(DataSet)일 경우 true
	 */
	public boolean isDataSet(int index){
		boolean isDataSet = false;
		String key = "";
		if( index < this.dataOrderInfo.size()){
			key = this.dataOrderInfo.get(index);
			isDataSet = isDataSet(key);
		}
		return isDataSet;
	}
	
	/**
	 * key로 저장된 하위정보(Datum/DataSet)가 존재하는지 확인하기 위한 메소드.
	 * @param key(하위정보(Datum/DataSet) key)
	 * @return boolean 존재할 경우 true, 존재하지 않을 경우 false
	 */
	public boolean isChildExists(String key){
		boolean isChildExists = false;
		if( this.dataOrderInfo.contains(key) ){
			isChildExists = true;
		}
		return isChildExists;
	}
	/**
	 * key로 저장된 하위정보(Datum/DataSet)를 반환하는 메소드.
	 * @param key(하위정보(Datum/DataSet) key값)
	 * @return Object 원자성정보(Datum)일 경우 Datum, 복합정보(DataSet)일 경우 DataSet
	 */
	public Object getChild(String key){
		Object val = null;
		try {
			if(this.dataOrderInfo.contains(key)){
				if(this.datumMap.containsKey(key)){
					val = this.datumMap.get(key);
				}else if(this.dataSetMap.containsKey(key)){
					val = this.dataSetMap.get(key);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return val;
	}
	/**
	 * index로 저장된 하위정보(Datum/DataSet)를 반환하는 메소드.
	 * @param index(하위정보(Datum/DataSet) 인덱스값)
	 * @return Object 원자성정보(Datum)일 경우 Datum, 복합정보(DataSet)일 경우 DataSet
	 */
	public Object getChild(int index){
		String key = "";
		Object val = null;
		try {
			if( index < this.dataOrderInfo.size()){
				key = this.dataOrderInfo.get(index);
				val = getChild(key);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return val;
	}
	
	/**
	 * index로 저장된 하위정보(Datum/DataSet)의 KEY를 반환하는 메소드.
	 * @param index(하위정보(Datum/DataSet) 인덱스값)
	 * @return 저장된 하위정보(Datum/DataSet)의 KEY
	 */
	public String getChildKey(int index){
		String key = "";
		try {
			if( index < this.dataOrderInfo.size()){
				key = this.dataOrderInfo.get(index);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return key;
	}
	/**
	 * key로 저장된 하위정보(Datum/DataSet)를 삭제
	 * @param key(하위정보(Datum/DataSet) key값)
	 */	
	public void removeChild(String key){
		if( !isDataSet(key) ){
			removeDatum(key);
		}else{
			removeDataSet(key);
		}
	}
	/**
	 * key로 저장된 하위정보(Datum/DataSet)를 삭제
	 * @param index(하위정보(Datum/DataSet) 인덱스값)
	 */	
	public void removeChild(int index){
		String key = "";
		if( index < this.dataOrderInfo.size()){
			key = this.dataOrderInfo.get(index);
			removeChild(key);
		}
	}
	
	/*** Datum-원자성(ATOMIC)정보 관련 ***/	
	/**
	 * key로 저장된 원자성(ATOMIC)정보값을 반환
	 * @param key(조회하고자 하는 정보 key값)
	 * @return String(key로 저장된 정보값)
	 */
	public String getDatum(String key){
		return getDatum(key, "");
	}
	/**
	 * key로 저장된 원자성(ATOMIC)정보값을 반환
	 * @param key(조회하고자 하는 정보 key값)
	 * @param defaultVal(조회하고자 하는 정보 key값이 없을경우 반환할 디폴트값)
	 * @return String(key로 저장된 정보값)
	 */
	public String getDatum(String key, String defaultVal){
		String val = "";
		if( datumMap.containsKey(key) ){
			val = StringUtil.nullCheck(datumMap.get(key).getVal(), defaultVal); 
		}else{
			val = defaultVal;
		}
		return val;
	}
	
	/**
	 * key로 저장된 원자성(ATOMIC)정보값(배열)을 반환
	 * @param key(조회하고자 하는 정보의 key값)
	 * @return String[](key로 저장된 정보값)
	 */	
	public String[] getDatumArray(String key){
		String[] valArr = null;
		if( datumMap.containsKey(key) ){
			valArr = datumMap.get(key).getArrayVal();
		}
		return valArr;
	}
	
	/**
	 * key로 원자성(ATOMIC)정보값을 저장
	 * @param key(저장하고자 하는 정보 key값)
	 * @param val(key로 저장할 정보값)
	 */	
	public void setDatum(String key, String val){
		setDatum(key, val, "");
	}
	/**
	 * key로 원자성(ATOMIC)정보값을 저장
	 * @param key(저장하고자 하는 정보 key값)
	 * @param val(key로 저장할 정보값)
	 * @param comment(key로 저장할 코멘트)
	 */	
	public void setDatum(String key, String val, String comment){
		if( !datumMap.containsKey(key) ){
			datumMap.put(key, new Datum(val));
			dataOrderInfo.add(key);
			dataComment.put(key, StringUtil.nullCheck(comment, ""));
		}else{
			datumMap.get(key).setVal(val);
			dataComment.put(key, StringUtil.nullCheck(comment, StringUtil.nullCheck(dataComment.get(key), "")));
		}
	}
	/**
	 * key로 원자성(ATOMIC)정보값을 저장. key에 해당하는 값이 이미 존재할 경우 배열로 추가저장한다.
	 * @param key(저장하고자 하는 정보 key값)
	 * @param val(key로 저장할 정보값)
	 */
	public void addDatum(String key, String val){
		addDatum(key, val, "");
	}
	/**
	 * key로 원자성(ATOMIC)정보값을 저장. key에 해당하는 값이 이미 존재할 경우 배열로 추가저장한다.
	 * @param key(저장하고자 하는 정보 key값)
	 * @param val(key로 저장할 정보값)
	 * @param comment(key로 저장할 코멘트)
	 */
	public void addDatum(String key, String val, String comment){
		if( !datumMap.containsKey(key) ){
			datumMap.put(key, new Datum(val));
			dataOrderInfo.add(key);
			dataComment.put(key, StringUtil.nullCheck(comment, ""));
		}else{
			datumMap.get(key).addVal(val);
			dataComment.put(key, StringUtil.nullCheck(comment, StringUtil.nullCheck(dataComment.get(key), "")));
		}
	}
	
	/**
	 * key로 원자성(ATOMIC)정보값을 삭제
	 * @param key(삭제하고자 하는 정보 key값)
	 */
	public void removeDatum(String key){
		if( datumMap.containsKey(key) ){
			datumMap.remove(key);
			dataOrderInfo.remove(key);
			dataComment.remove(key);
		}
	}

	/*** DataSet-복합(COMPLEX)정보 관련 ***/	
	/**
	 * 해당key로 하위(Child)DataSet을 생성하는 메소드
	 * @param key(생성하고자 하는 DataSet의 key값)
	 * @return DataSet
	 */
	public DataSet addDataSet(String key){
		return addDataSet( key, "" );
	}
	/**
	 * 해당key로 하위(Child)DataSet을 생성하는 메소드
	 * @param key(생성하고자 하는 DataSet의 key값)
	 * @param comment(key로 저장할 코멘트)
	 * @return DataSet
	 */
	public DataSet addDataSet(String key, String comment){
		DataSet ds = new DataSet();
		/*** 자식에 전달할 속성정보 시작 ***/
		ds.isNullDataSetHide = this.isNullDataSetHide;
		ds.isCommentShow = this.isCommentShow;
		/*** 자식에 전달할 속성정보 끝 ***/
		
		if( !dataSetMap.containsKey(key) ){
			dataSetMap.put(key, new ArrayList<DataSet>());
			if( !dataSetExtraInfo.containsKey(key) ){
				dataSetExtraInfo.put(key, new HashMap<String, String>());
			}
			if( !dataOrderInfo.contains(key) ){
				dataOrderInfo.add(key);
			}
			if( !dataComment.containsKey(key) ){
				dataComment.put(key, StringUtil.nullCheck(comment, ""));
			}
		}
		dataSetMap.get(key).add(ds);		
		return ds;
	}
	/**
	 * 해당key로 저장된 하위(Children)DataSet중 rowIndex번째 DataSet을반환하는 메소드.
	 * @param key(반환하고자 하는 하위(Child)DataSet의 key값)
	 * @param rowIndex(반환하고자 하는 하위(Child)DataSet의 인덱스)
	 * @return
	 */		
	public DataSet getDataSet(String key, int rowIndex){
		DataSet ds = null;
		if( dataSetMap.containsKey(key) ){
			if( dataSetMap.get(key).size() > rowIndex ){
				ds = dataSetMap.get(key).get(rowIndex);
			}
		}
		if(ds == null){
			//logger.info("DataSet("+key+")은 존재하지 않는 하위항목입니다.");
		}
		return ds;
	}
	
	/**
	 * 해당key로 하위(Child)DataSet을 저장하는 메소드.
	 * @param key(저장하고자 하는 하위(Child)DataSet의 key값)
	 * @param rowIndex(저장하고자 하는 하위(Child)DataSet의 인덱스)
	 * @param ds(저장하고자 하는 하위(Child)DataSet)
	 * @param comment(key로 저장할 코멘트)
	 */		
	public void setDataSet(String key, int rowIndex, DataSet ds, String comment) throws Exception{
		if( this.getDataSet(key, rowIndex) == null ){
			throw new Exception("하위 DataSet["+key+"]의 rowIndex["+rowIndex+"]는 존재하지 않는 항목입니다.");
		}else {
			dataSetMap.get(key).set(rowIndex, ds);
			if( !dataSetExtraInfo.containsKey(key) ){
				dataSetExtraInfo.put(key, new HashMap<String, String>());
			}
			if( !dataOrderInfo.contains(key) ){
				dataOrderInfo.add(key);
			}
			if( !dataComment.containsKey(key) ){
				dataComment.put(key, StringUtil.nullCheck(comment, ""));
			}
		}
	}	
	
	
	/**
	 * 해당key로 저장된 하위(Children)DataSet리스트를 반환하는 메소드.
	 * @param key(반환하고자 하는 하위(Children)DataSet의 key값)
	 * @return ArrayList<DataSet> 하위(Children)DataSet리스트
	 */	
	public ArrayList<DataSet> getDataSetList(String key){
		ArrayList<DataSet> dsList = null;
		if( dataSetMap.containsKey(key) ){
			dsList = (ArrayList<DataSet>)dataSetMap.get(key);
		}
		return dsList;
	}
	
	
	/**
	 * 해당key로 하위(Children)DataSet리스트를 저장하는 메소드.
	 * @param key(반환하고자 하는 하위(Children)DataSet의 key값)
	 * @return ArrayList<DataSet> 저장하고자하는 하위(Children)DataSet리스트
	 */	
	public void setDataSetList(String key, ArrayList<DataSet>dsList) throws Exception{
		setDataSetList(key, dsList, "");
	}
	/**
	 * 해당key로 하위(Children)DataSet리스트를 저장하는 메소드.
	 * @param key(반환하고자 하는 하위(Children)DataSet의 key값)
	 * @return ArrayList<DataSet> 저장하고자하는 하위(Children)DataSet리스트
	 * @param comment(key로 저장할 코멘트)
	 */	
	public void setDataSetList(String key, ArrayList<DataSet>dsList, String comment) throws Exception{
		removeDataSet(key);
		if(dsList!=null){
			for(int i=0; i<dsList.size();i++){
				setDataSet(key, i, dsList.get(i), comment);
			}
		}
	}
	

	
	/**
	 * key로 저장된 하위DataSet를 삭제
	 * @param key(삭제하고자 하는 DataSet key값)
	 */	
	public void removeDataSet(String key){
		if( dataSetMap.containsKey(key) ){
			dataSetMap.remove(key);
		}
		if( dataSetExtraInfo.containsKey(key) ){
			dataSetExtraInfo.remove(key);
		}
		if( dataOrderInfo.contains(key) ){
			dataOrderInfo.remove(key);
		}
		if( dataComment.containsKey(key) ){
			dataComment.remove(key);
		}
	}
	/**
	 * key로 저장된 하위DataSet를 삭제
	 * @param key(삭제하고자 하는 DataSet key값)
	 * @param index(삭제하고자 하는 List<DataSet>의 인덱스)
	 */	
	public void removeDataSet(String key, int index){
		if( dataSetMap.containsKey(key) ){
			if( index > -1 && dataSetMap.get(key).size() > index ){
				dataSetMap.get(key).remove(index);
			}
			if( dataSetMap.get(key).size() == 0 ){
				dataSetMap.remove(key);
				if( dataSetExtraInfo.containsKey(key) ){
					dataSetExtraInfo.remove(key);
				}
				if( dataOrderInfo.contains(key) ){
					dataOrderInfo.remove(key);
				}
				if( dataComment.containsKey(key) ){
					dataComment.remove(key);
				}
			}
		}
	}
	/**
	 * key로 저장된 하위DataSet의 갯수 반환.
	 * @param key(하위DataSet갯수를 조회하고자 하는 DataSet key값)
	 */	
	public int getDataSetRowCount(String key){
		int dsRowCount = 0;
		if( dataSetMap.containsKey(key) ){
			dsRowCount = dataSetMap.get(key).size();
		}
		return dsRowCount;
	}
	
	/**
	 * 해당종목key로 DataSet의 extraInfoKey에 해당하는 부가정보 를 세팅하는 메소드.
	 * @param key(부가정보를 세팅하고자 하는 종목key값)
	 * @param extraInfoKey(부가정보  항목key값)
	 * @param extraInfoVal(부가정보  항목val값)
	 */		
	public void setDataSetExtraInfo(String key, String extraInfoKey, String extraInfoVal){
		HashMap<String, String> keyValSet = null; 
		if( !dataSetExtraInfo.containsKey(key) ){
			keyValSet = new HashMap<String, String>();
			dataSetExtraInfo.put(key, keyValSet);
		}
		keyValSet = dataSetExtraInfo.get(key);
		keyValSet.put(extraInfoKey, extraInfoVal);
	}		
	
	/**
	 * 해당종목key로 DataSet의 extraInfoKey에 해당하는 부가정보 를 반환하는 메소드.
	 * @param key(부가정보를 얻고자 하는 종목key값)
	 * @param extraInfoKey(부가정보  항목key값)
	 */		
	public String getDataSetExtraInfo(String key, String extraInfoKey){
		return getDataSetExtraInfo(key, extraInfoKey, "");
	}
	
	/**
	 * 해당종목key로 DataSet의 extraInfoKey에 해당하는 부가정보 를 반환하는 메소드.
	 * @param key(부가정보를 얻고자 하는 종목key값)
	 * @param extraInfoKey(부가정보  항목key값)
	 * @param defaultVal(디폴트 값)
	 */		
	public String getDataSetExtraInfo(String key, String extraInfoKey, String defaultVal){
		String extraInfoVal = "";
		if(  dataSetExtraInfo.containsKey(key)  ){
			extraInfoVal = dataSetExtraInfo.get(key).get(extraInfoKey);
		}
		if(StringUtil.isEmpty(extraInfoVal)){
			extraInfoVal = defaultVal;
		}
		return extraInfoVal;
	}

	/*********************** 데이터입출력관련 메소드 끝   **************************/
	
}
