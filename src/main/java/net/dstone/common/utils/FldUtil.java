package net.dstone.common.utils;

import java.util.Iterator;

import org.slf4j.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class FldUtil {

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(FldUtil.class);

	/***************************** 제어용상수/변수 시작 ************************/
	public static final String N 									= "\n";
	public static final String T 									= "\t";
	public static String FLD_ROOT_DIR								= "";
	/***************************** 제어용상수/변수 끝   ************************/
	
	private static FldUtil fldUtil = null;
	
	private StructManager structManager = null;
	
	public class Member{
		private String id;
		private String name;
		private String type;
		private int size = 0;
		private int arrayCount = 0;
		private boolean isAtomic = true;
		private boolean isArray = false;
		private int arrayCountSize = 0;
		
		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public int getSize() {
			return size;
		}
		public void setSize(int size) {
			this.size = size;
		}
		public int getArrayCount() {
			return arrayCount;
		}
		public void setArrayCount(int arrayCount) {
			this.arrayCount = arrayCount;
		}
		public boolean isAtomic() {
			return isAtomic;
		}
		public void setAtomic(boolean isAtomic) {
			this.isAtomic = isAtomic;
		}
		public boolean isArray() {
			return isArray;
		}
		public void setArray(boolean isArray) {
			this.isArray = isArray;
		}
		public int getArrayCountSize() {
			return arrayCountSize;
		}
		public void setArrayCountSize(int arrayCountSize) {
			this.arrayCountSize = arrayCountSize;
		}
		@Override
		public String toString() {
			return "Member [id=" + id + ", name=" + name + ", type=" + type + ", size=" + size + ", arrayCount=" + arrayCount + ", isAtomic="
					+ isAtomic + ", isArray=" + isArray + ", arrayCountSize=" + arrayCountSize + "]";
		}
	}

	public class Struct {

		private String structId;
		
		private java.util.LinkedHashMap<String, Member> members = new java.util.LinkedHashMap<String, Member>();
		private java.util.LinkedHashMap<String, Struct> childStructs = new java.util.LinkedHashMap<String, Struct>();
		private java.util.LinkedHashMap<String, Member> orderInfo = new java.util.LinkedHashMap<String, Member>();

		protected Struct(){
		}

		protected Struct(String structId){
			this.structId = structId; 
		}
		
		protected Member newMember(){
			return new Member();
		}

		protected void addMember(String key, Member value){
			this.members.put(key, value);
			this.orderInfo.put(key, value);
		}
		protected boolean containsMember(String key){
			boolean containMember = false;
			
			if(  this.members.containsKey(key) ){
				containMember = true;
			}else{
				Struct childStruct = null;
				String childMemberKey = null;
				Iterator<String> iter = this.childStructs.keySet().iterator();
				while(iter.hasNext()){
					childMemberKey = iter.next();
					if(childMemberKey.equals(key)){
						containMember = true;
					}else{
						childStruct = this.childStructs.get(childMemberKey);
						containMember = childStruct.containsMember(key);
					}
				}
			}
			
			return containMember;
		}
		public Member getMember(String key){
			return (Member)this.members.get(key);
		}
		public Member[] getMemberList(){
			Member[] memberList = null;
			if(this.members != null){
				memberList = new Member[this.members.size()];
				java.util.Iterator<Member> it = this.members.values().iterator();
				int idx = 0;
				while(it.hasNext()){
					memberList[idx++] = (Member)it.next();
				}
			}
			return memberList;
		}
		
		public String[] getMemberIds(){
			String[] memberIds = null;
			Member[] memberList = getMemberList();
			if( memberList != null ){
				memberIds = new String[memberList.length];
				for(int i=0;i<memberList.length; i++){
					memberIds[i] = memberList[i].id;
				}
			}
			return memberIds;
		}
		protected void addChildStruct(Member key, Struct value){
			this.childStructs.put(key.id, value);
		}
		public Struct getChildStruct(Member key) throws Exception{
			Struct childStruct = null;
			if(key != null){
				childStruct = getChildStruct(key.getId());
			}
			return childStruct;
		}
		public Struct getChildStruct(Member key, boolean searchRecursiveYn) throws Exception{
			Struct childStruct = null;
			if(key != null){
				childStruct = getChildStruct(key.getId(), searchRecursiveYn);
			}
			return childStruct;
		}
		
		public Struct getChildStruct(String key) throws Exception{
			return getChildStruct(key, false);
		}
		
		public Struct getChildStruct(String key, boolean searchRecursiveYn) throws Exception{
			Struct childStruct = null;
			if(this.childStructs.containsKey(key)){
				childStruct = this.childStructs.get(key);
			}else{
				if(searchRecursiveYn){
					Iterator<String> childKeys = this.childStructs.keySet().iterator();
					String childKey = "";
					while( childKeys.hasNext() ){
						childKey = childKeys.next();
						childStruct = this.childStructs.get(childKey).getChildStruct(key, searchRecursiveYn);
						if(childStruct != null){
							break;
						}
					}
				}
			}
			if(childStruct == null){
				throw new Exception("["+key+"]은 "+this.structId+" 에 속하지 않은 필드입니다.");
			}
			return childStruct;
		}
		
		public int getOffSet(String id) throws Exception{
			int offSet = 0;
			Member subMember = null;
			Struct subMemberStruct = null;
			int subMemberSize = 0;
			int subMemberCount = 0;
			int subMemberRealSize = 0;
			Iterator<String> orderInfoKeySet = null;
			orderInfoKeySet = this.orderInfo.keySet().iterator();
			if( !this.orderInfo.containsKey(id) ){
				throw new Exception("["+id+"]는 존재하지않는 멤버입니다. 본 메소드는 재귀검색을 하지 않습니다.");
			}
			while(orderInfoKeySet.hasNext()){
				subMember = this.orderInfo.get(orderInfoKeySet.next());

				if(id.equals(subMember.getId())){
					break;
				}else{
					if( subMember.isAtomic() ){
						subMemberSize = subMember.getSize();
						subMemberCount = subMember.getArrayCount();
						subMemberRealSize = subMemberSize*subMemberCount;
						offSet = offSet + subMemberRealSize;
					}else{
						if(subMember.isArray){
							offSet = offSet + subMember.getArrayCountSize();
						}
						subMemberStruct = this.getChildStruct(subMember.getId());
						subMemberSize = subMemberStruct.getStructSize();
						subMemberCount = subMember.getArrayCount();
						subMemberRealSize = subMemberSize*subMemberCount;
						offSet = offSet + subMemberRealSize;
					}
				}
				debug( "id["+id+"] getId["+subMember.getId()+"] subMemberRealSize["+subMemberRealSize+"]  offSet["+offSet+"]" );	
			}
			return offSet;
		}
		
		public int getStructSize(){
			return getStructSize(0);
		}
		
		@SuppressWarnings("unused")
		private int getStructSize(int structSize){
			Member subMember = null;
			Struct subMemberStruct = null;
			String memberStructId = "";
			int subMemberSize = 0;
			int subMemberCount = 0;
			int subMemberRealSize = 0;
			Iterator<String> orderInfoKeySet = null;
			try {
				orderInfoKeySet = this.orderInfo.keySet().iterator();
				while(orderInfoKeySet.hasNext()){
					subMember = this.orderInfo.get(orderInfoKeySet.next());
					if( subMember.isAtomic() ){
						memberStructId = this.structId;
						subMemberSize = subMember.getSize();
						subMemberCount = subMember.getArrayCount();
						subMemberRealSize = subMemberSize*subMemberCount;
						structSize = structSize + subMemberRealSize;
					}else{
						structSize = structSize + subMember.getArrayCountSize();
						subMemberStruct = this.getChildStruct(subMember.getId());
						memberStructId = subMemberStruct.structId;
						subMemberSize = subMemberStruct.getStructSize();
						subMemberCount = subMember.getArrayCount();
						subMemberRealSize = subMemberSize*subMemberCount;
						structSize = structSize + subMemberRealSize;
					}
					//debug("id["+subMember.getId()+"] isAtomic["+subMember.isAtomic()+"] isArray["+subMember.isArray()+"] subMemberSize["+subMemberSize+"] subMemberCount["+subMemberCount+"] "+memberStructId+".subMemberRealSize["+subMemberRealSize+"] structSize["+structSize+"]" );	
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return structSize;
		}
		
		protected String toString(String space) {
			StringBuffer buff = new StringBuffer();
			String newSpace = space + T;
			if(this.members != null){
				java.util.Iterator<Member> it = this.members.values().iterator();
				while(it.hasNext()){
					Member tempMember = (Member)it.next();
					buff.append(space).append( tempMember.toString() ).append(N);
					if(this.childStructs.containsKey(tempMember.getId())){
						buff.append( ((Struct)this.childStructs.get(tempMember.getId())).toString(newSpace) );
					}
				}
			}
			return buff.toString();
		}
		public String toString() {
			return toString("");
		}
		public String getStructId() {
			return structId;
		}
		protected void setStructId(String structId) {
			this.structId = structId;
		}
		
	}
	
	public class StructManager {
		
		protected java.util.HashMap<String, Struct> structInfo = new java.util.HashMap<String, Struct>();

		protected StructManager(){
			init();
		}
		private void init(){
			// 1. 자료구조정보 로딩
			loadConfig();
		}
		private void loadConfig(){
			String layOutId = null;
			String[] fileList = null;
			Struct struct = null;
			fileList = FileUtil.readFileList(FLD_ROOT_DIR);
			if(fileList != null){
				debug( "/******************************* 자료구조정보 XML(총 "+fileList.length+"건) 로딩 시작 *******************************/" );
				for(int i=0; i<fileList.length; i++){
					fileList[i] = StringUtil.replace(fileList[i], "\\", "/");
					if(!fileList[i].endsWith(".xml")){
						continue;
					}
					layOutId = fileList[i].substring(fileList[i].lastIndexOf("/")+1, fileList[i].lastIndexOf("."));
					debug( "레이아웃ID["+layOutId+"] XML 파일[" + fileList[i] + "]" );
					struct = parseXml(XmlUtil.XML_SOURCE_KIND_PATH, fileList[i]);
					structInfo.put(layOutId, struct);
				}
				debug( "/******************************* 자료구조정보 XML(총 "+fileList.length+"건) 로딩 끝 *******************************/" );
			}
		}
		
		private Struct parseXml(int parseXmlKind, String xmlFile){
			XmlUtil xmlUtil = null;
			Struct struct = null;
			Node rootNode = null;
			try {
				xmlUtil = XmlUtil.getInstance(XmlUtil.XML_SOURCE_KIND_PATH, FLD_ROOT_DIR + "/" + xmlFile);
				if( xmlUtil.getDocument().getChildNodes().getLength() >0 ){
					rootNode = xmlUtil.getDocument().getChildNodes().item(0);
					struct = new Struct(StringUtil.replace(xmlFile, ".xml", ""));
					struct = loadStruct(rootNode, struct);
				}
			} catch (Exception e) {
				debug(e);
			}
			return struct;
		}
		
		public Struct parseXmlStr(int parseXmlKind, String layoutId, String xmlSource){
			XmlUtil xmlUtil = null;
			Struct struct = null;
			Node rootNode = null;
			try {
				xmlUtil = XmlUtil.getInstance(XmlUtil.XML_SOURCE_KIND_STRING, xmlSource);
				if( xmlUtil.getDocument().getChildNodes().getLength() >0 ){
					rootNode = xmlUtil.getDocument().getChildNodes().item(0);
					struct = new Struct(layoutId);
					struct = loadStruct(rootNode, struct);
				}
			} catch (Exception e) {
				debug(e);
			}
			return struct;
		}

		private Struct loadStruct(Node node, Struct struct){
			/****************************** 변수선언/정의 시작 ******************************/
			Member member;
			Struct childStruct = null;
			Node childNode = null;
			NamedNodeMap childNodeMap = null;
			
			String id = "";
			String name = "";
			String type = "";
			String size = "";
			String arrayCount = "";
			String arrayCountSize = "";
			
			String debugStr = "";
			/****************************** 변수선언/정의  끝 ******************************/

			/****************************** 세팅 시작 ******************************/
			try {
				for( int i=0; i<node.getChildNodes().getLength(); i++ ){
					childNode = node.getChildNodes().item(i);
					if( childNode.getNodeType() == Node.ELEMENT_NODE ){
						/*** A. 멤버생성 ***/
						member = struct.newMember();
						childNodeMap = childNode.getAttributes();	

						id = "";
						name = "";
						type = "";
						size = "0";
						arrayCount = "0";
						arrayCountSize = "0";
						
						if( childNodeMap.getNamedItem("id") != null ){
							id =  childNodeMap.getNamedItem("id").getNodeValue() ;
						}
						if( childNodeMap.getNamedItem("name") != null ){
							name =  childNodeMap.getNamedItem("name").getNodeValue() ;
						}
						if( childNodeMap.getNamedItem("type") != null ){
							type =  childNodeMap.getNamedItem("type").getNodeValue() ;
						}
						if( childNodeMap.getNamedItem("size") != null ){
							size =  childNodeMap.getNamedItem("size").getNodeValue() ;
						}
						if( childNodeMap.getNamedItem("arrayCount") != null ){
							arrayCount =  childNodeMap.getNamedItem("arrayCount").getNodeValue() ;
						}
						if( childNodeMap.getNamedItem("arrayCountSize") != null ){
							arrayCountSize =  childNodeMap.getNamedItem("arrayCountSize").getNodeValue() ;
						}

						//debugStr = "id["+childNodeMap.getNamedItem("id").getNodeValue()+"]";
						//debug( "getNodeName["+childNode.getNodeName()+"] id["+id+"] " );

						// 1. 컬럼 ID
						member.setId(id);
						// 2. 컬럼 NAME
						member.setName(name);
						// 3. 컬럼 TYPE
						member.setType(type);
						// 4. 컬럼 SIZE
						member.setSize( Integer.valueOf(size) );
						// 5. 컬럼 배열갯수
						member.setArrayCount( Integer.valueOf(arrayCount) );
						// 6. 컬럼 배열갯수 SIZE
						member.setArrayCountSize( Integer.valueOf(arrayCountSize) );
						// 7. 컬럼 Atomic여부
						if( childNode.getNodeName().equals("col") ){
							member.setAtomic(true);
						}else{
							member.setAtomic(false);
						}
						// 8. 배열여부
						if(member.getArrayCount() < 2){
							member.setArray(false);
						}else{
							member.setArray(true);
						}
						// 9. CHILD(재귀 로딩)
						if( !member.isAtomic() ){
							childStruct = new Struct(member.getId());
							childStruct = loadStruct(childNode, childStruct);
							struct.addChildStruct(member, childStruct);
						}
						/*** B. 멤버등록 ***/
						struct.addMember(member.getId(), member);
					}
				}
			} catch (Exception e) {
				debug(debugStr + " 수행중 예외발생.");
				e.printStackTrace();
			}
			/****************************** 세팅 끝 ******************************/
			return struct;
		}
		

		public Struct getStructInfo(String layOutId) throws Exception{
			Struct struct = null;		
			struct = (Struct)this.structInfo.get(layOutId);
			if( struct != null ){
				logger.debug("요청레이아웃ID["+layOutId+"] looked up ! 총사이즈:" + struct.getStructSize());
			}else{
				throw new Exception("요청레이아웃ID["+layOutId+"]에 대한 레이아웃정의정보가 존재하지 않습니다.");
			}
			return struct;
		}
		public String getStructKey(Struct struct){
			String layOutId = null;
			Object key = null;
			if(this.structInfo.containsValue(struct)){
				java.util.Iterator<String> iter = this.structInfo.keySet().iterator();
				while(iter.hasNext()){
					key = iter.next();
					if( struct.equals(this.structInfo.get(key)) ){
						layOutId = (String)key;
						break;
					}
				}
			}
			return layOutId;
		}
		
		public String toString(){
			return toString("");
		}
		
		private String toString(String strTab){
			StringBuffer buffer = new StringBuffer();
			String strNewTab = strTab + T;
			Object key = null;
			Struct val = null;
			try {
				java.util.Iterator<String> iter = this.structInfo.keySet().iterator();
				while(iter.hasNext()){
					key = iter.next();
					val = this.structInfo.get(key);
					buffer.append(strTab).append("<"+key+">").append(N);
					buffer.append(strTab).append(val.toString(strNewTab));
					if( iter.hasNext() ){
						buffer.append(N);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return buffer.toString();
		}
		
		public void checkData(){
			StringBuffer buffer = new StringBuffer();
			buffer.append(N).append("||******************************* ").append(" StructManager Start ").append(" *******************************||").append(N);
			buffer.append(this.toString(""));
			buffer.append("").append("||******************************* ").append(" StructManager End ").append(" *******************************||").append(N);
			debug(buffer.toString());
		}
	}
	
	private FldUtil(){
		init();
	}

	private void debug(Object o){
		if( o != null ){
			logger.info(o.toString());
		}
	}
	
	private void init(){
		try {
			if( StringUtil.isEmpty(FLD_ROOT_DIR) ) {
				FLD_ROOT_DIR = net.dstone.common.utils.PropUtil.getInstance().getProp("app", "FLD_ROOT_DIR");
			}
			if(structManager == null){
				structManager = this.new StructManager();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static StructManager getStructManager() {
		if( fldUtil == null ){
			fldUtil = new FldUtil();
		}
		return fldUtil.structManager;
	}
	
	/**
	 * 고정길이데이터(FLD)의 테스트 샘플을 덤프해주는 메소드
	 * @param layoutId
	 * @return
	 */
	public static String getSampleTestFld(String layoutId) {
		String N = "\n";
		StringBuffer buff = new StringBuffer();
		
		buff.append("* 고정길이데이터(FLD)레이아웃정의 샘플. 파일명 : "+layoutId+".xml").append(N).append(N);

		buff.append("<?xml version=\"1.0\" encoding=\"EUC-KR\"?>").append(N);
		buff.append("<root>").append(N);
		buff.append("	<col id=\"WrkClas\" type=\"char\" size=\"2\" arrayCount=\"1\" name=\"작업구분\"/>").append(N);
		buff.append("	<col id=\"BnkCd\" type=\"char\" size=\"3\" arrayCount=\"1\" name=\"은행코드\"/>").append(N);
		buff.append("	<col id=\"InsurCoCd\" type=\"char\" size=\"3\" arrayCount=\"1\" name=\"보험사코드\"/>").append(N);
		buff.append("	<col id=\"Acno\" type=\"char\" size=\"14\" arrayCount=\"1\" name=\"계좌번호\"/>").append(N);
		buff.append("	<col id=\"SecuNo\" type=\"char\" size=\"20\" arrayCount=\"1\" name=\"보험증권번호\"/>").append(N);
		buff.append("	<col id=\"InqYm\" type=\"char\" size=\"6\" arrayCount=\"1\" name=\"조회년월\"/>").append(N);
		buff.append("	<array id=\"PaymentArray\" arrayCount=\"12\"  arrayCountSize=\"3\" name=\"이체내역배열\">").append(N);
		buff.append("		<col id=\"BilDt\" type=\"char\" size=\"10\" arrayCount=\"1\" name=\"청구년월일\"/>").append(N);
		buff.append("		<col id=\"BilYm\" type=\"char\" size=\"6\" arrayCount=\"1\" name=\"청구년월\"/>").append(N);
		buff.append("		<col id=\"LstPmtSeq\" type=\"numeric\" size=\"5\" arrayCount=\"1\" name=\"최종납입회차\"/>").append(N);
		buff.append("		<col id=\"LstPmtYm\" type=\"char\" size=\"6\" arrayCount=\"1\" name=\"최종납입년월\"/>").append(N);
		buff.append("		<col id=\"TransDmdD\" type=\"char\" size=\"2\" arrayCount=\"1\" name=\"이체요구일\"/>").append(N);
		buff.append("		<col id=\"TotInsurpAmt\" type=\"numeric\" size=\"15\" arrayCount=\"1\" name=\"합계보험료금액\"/>").append(N);
		buff.append("		<col id=\"RlRctsAmt\" type=\"numeric\" size=\"15\" arrayCount=\"1\" name=\"실입금액\"/>").append(N);
		buff.append("		<col id=\"BnkNm\" type=\"char\" size=\"50\" arrayCount=\"1\" name=\"은행명\"/>").append(N);
		buff.append("		<col id=\"TransAcno\" type=\"char\" size=\"16\" arrayCount=\"1\" name=\"이체계좌번호\"/>").append(N);
		buff.append("		<col id=\"TransDepsr\" type=\"char\" size=\"30\" arrayCount=\"1\" name=\"이체예금주명\"/>").append(N);
		buff.append("		<col id=\"TranRtDtl\" type=\"char\" size=\"50\" arrayCount=\"1\" name=\"이체결과내역\"/>").append(N);
		buff.append("		<col id=\"TelNo2\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"전화번호2\"/>").append(N);
		buff.append("		<col id=\"TelNo3\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"전화번호3\"/>").append(N);
		buff.append("		<col id=\"TelNo4\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"전화번호4\"/>").append(N);
		buff.append("		<col id=\"MTelNo2\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"휴대폰전화번호2\"/>").append(N);
		buff.append("		<col id=\"MTelNo3\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"휴대폰전화번호3\"/>").append(N);
		buff.append("		<col id=\"MTelNo4\" type=\"char\" size=\"4\" arrayCount=\"1\" name=\"휴대폰전화번호4\"/>").append(N);
		buff.append("	</array>").append(N);
		buff.append("	<col id=\"Empno\" type=\"char\" size=\"7\" arrayCount=\"1\" name=\"직원개인번호\"/>").append(N);
		buff.append("	<col id=\"ReturnType\" type=\"char\" size=\"1\" arrayCount=\"1\" name=\"리턴타입\"/>").append(N);
		buff.append("	<col id=\"ReturnPmg\" type=\"char\" size=\"10\" arrayCount=\"1\" name=\"리턴PMG\"/>").append(N);
		buff.append("	<col id=\"ReturnEtc\" type=\"char\" size=\"20\" arrayCount=\"1\" name=\"리턴ETC\"/>").append(N);
		buff.append("	<col id=\"ReturnMessage\" type=\"char\" size=\"100\" arrayCount=\"1\" name=\"리턴메시지\"/>").append(N);
		buff.append("</root>").append(N);
		
		return buff.toString();
	}
	
}
