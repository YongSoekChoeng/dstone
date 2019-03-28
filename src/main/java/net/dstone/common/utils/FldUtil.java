package net.dstone.common.utils;

public class FldUtil {

	public class Member{
		private String name;
		private String type;
		private int length = 0;
		private String desc;
		private String format;
		private boolean isRoot = false;
		private boolean isArray = false;
		private boolean isFixedArray = false;
		private int arrayCount = 0;
		public int getArrayCount() {
			return arrayCount;
		}
		public void setArrayCount(int arrayCount) {
			this.arrayCount = arrayCount;
		}
		public boolean isFixedArray() {
			return isFixedArray;
		}
		public void setFixedArray(boolean isFixedArray) {
			this.isFixedArray = isFixedArray;
		}
		private String arrayCountMemberName = "";
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
		public int getLength() {
			return length;
		}
		public void setLength(int length) {
			this.length = length;
		}
		public boolean isArray() {
			return isArray;
		}
		public void setArray(boolean isArray) {
			this.isArray = isArray;
		}
		public String getArrayCountMemberName() {
			return arrayCountMemberName;
		}
		public void setArrayCountMemberName(String arrayCountMemberName) {
			this.arrayCountMemberName = arrayCountMemberName;
		}
		public String getDesc() {
			return desc;
		}
		public void setDesc(String desc) {
			this.desc = desc;
		}
		public String getFormat() {
			return format;
		}
		public void setFormat(String format) {
			this.format = format;
		}
		public boolean isRoot() {
			return isRoot;
		}
		public void setRoot(boolean isRoot) {
			this.isRoot = isRoot;
		}
		public String toString() {
			return "Member [name=" + name + ", desc=" + desc + ", type=" + type + ", format=" + format + ", length="
					+ length + ", isArray=" + isArray + ", isFixedArray=" + isFixedArray + ", isRoot=" + isRoot
					+ ", arrayCountMemberName=" + arrayCountMemberName + "]";
		}
	}
	
	public class Struct {
		private String structName;

		private java.util.LinkedHashMap<String, Member> members = new java.util.LinkedHashMap<String, Member>();
		private java.util.LinkedHashMap<Member, Struct> childStructs = new java.util.LinkedHashMap<Member, Struct>();
		
		private Struct(){}
		public Struct(String structName){
			this.structName = structName; 
		}

		public Member newMember(){
			return new Member();
		}

		public void addMember(String key, Member value){
			this.members.put(key, value);
		}
		public boolean containsMember(String key){
			return this.members.containsKey(key);
		}
		public Member getMember(String key){
			return (Member)this.members.get(key);
		}
		public Member[] getMemberList(){
			Member[] memberList = null;
			if(this.members != null){
				memberList = new Member[this.members.size()];
				java.util.Collection<Member> c = this.members.values();
				java.util.Iterator<Member> it = c.iterator();
				int idx = 0;
				while(it.hasNext()){
					memberList[idx++] = (Member)it.next();
				}
			}
			return memberList;
		}
		public void addChildStruct(Member key, Struct value){
			this.childStructs.put(key, value);
		}
		public Struct getChildStruct(Member key){
			return (Struct)this.childStructs.get(key);
		}
		
		public Struct getChildStruct(String key){
			Struct childStruct = null;
			java.util.Set<Member> keySet = this.childStructs.keySet();
			java.util.Iterator<Member> keyIter = keySet.iterator();
			while(keyIter.hasNext()){
				Member m = (Member)keyIter.next();
				if(m.getName().equals(key)){
					childStruct = (Struct)this.childStructs.get(m);
					break;
				}
			}
			if(childStruct == null){
				childStruct = new Struct();
			}
			return childStruct;
		}
		
		public String toString(String space) {
			StringBuffer buff = new StringBuffer();
			String newSpace = space + "\t";
			if(this.members != null){
				java.util.Collection<Member> c = this.members.values();
				java.util.Iterator<Member> it = c.iterator();
				while(it.hasNext()){
					Member tempMember = (Member)it.next();
					buff.append(space).append( tempMember.toString() ).append("\n");
					if(this.childStructs.containsKey(tempMember)){
						buff.append( ((Struct)this.childStructs.get(tempMember)).toString(newSpace) );
					}
				}
			}
			return buff.toString();
		}
		public String toString() {
			return toString("");
		}
		public String getStructName() {
			return structName;
		}
		public void setStructName(String structName) {
			this.structName = structName;
		}
	}

	private static FldUtil fldUtil = null;
	private java.util.HashMap structInfo = new java.util.HashMap();

	private FldUtil(){
		init();
	}
	private void debug(Object o){
		System.out.println(o);
	}
	public static FldUtil getInstance(){
		if(fldUtil == null){
			fldUtil = new FldUtil();
		}
		return fldUtil;
	}
	
	private void init(){
		// 1. 자료구조정보 로딩
		loadConfig();
	}
	private void loadConfig(){
		String interfaceId = null;
		String[] fileList = null;
		Struct struct = null;
		String xmlPath = net.dstone.common.utils.SystemUtil.getInstance().getProperty("XML_PATH");
xmlPath = "D:/Temp/xml";
		fileList = FileUtil.readFileList(xmlPath);
		if(fileList != null){
			debug( "/******************************* 자료구조정보 XML 로딩 시작 *******************************/" );
			for(int i=0; i<fileList.length; i++){
				fileList[i] = StringUtil.replace(fileList[i], "\\", "/");
				if(!fileList[i].endsWith(".xml")){
					continue;
				}
				interfaceId = fileList[i].substring(fileList[i].lastIndexOf("/")+1, fileList[i].lastIndexOf("."));
				debug( "인터페이스ID["+interfaceId+"] XML 파일[" + fileList[i] + "]" );
				struct = parseXml( xmlPath + "/" + fileList[i]);
				structInfo.put(interfaceId, struct);
			}
			debug( "/******************************* 자료구조정보 XML 로딩 끝 *******************************/" );
		}

	}
	private Struct parseXml(String strFileName){
		XmlUtil xmlUtil = null;
		org.w3c.dom.Node root;
		Struct struct = null;
		String structName = "root";
		try {
			struct = new Struct(structName);
			xmlUtil = net.dstone.common.utils.XmlUtil.getInstance(strFileName);
			//xmlUtil.dataSet.checkData();
			
			
//			el = xmlUtil.getElement(structName, 0);
//			struct = new Struct(structName);
//			struct = loadStruct(el, struct);
		} catch (Exception e) {
			debug(e);
		}
		return struct;
	}
	
	private Struct loadStruct(DataSet node, Struct struct){
		/****************************** 변수선언/정의 시작 ******************************/
		java.util.List list = null;
		Member member;
		Member childMember;
		Struct childStruct = null;
		org.w3c.dom.NodeList childNodeList = null;
		org.w3c.dom.Node childNode = null;
		/****************************** 변수선언/정의  끝 ******************************/

		/****************************** 세팅 시작 ******************************/
		try {
			
//			if(node != null){
//				if( node.hasChildNodes() ){
//					childNodeList = node.getChildNodes();
//					for(int k = 0; k < childNodeList.getLength(); k++){
//						childNode = (org.w3c.dom.Node)childNodeList.item(k);
//						// 1. 멤버생성
//						member = struct.newMember();
//						childNode.
//						// 2. NAME
//						if( "DataIn".equals(childNode.getName()) || "DataOut".equals(childNode.getName()) ){
//							member.setRoot(true);
//							member.setName(childNode.getName());
//						}else{
//							member.setRoot(false);
//							member.setName(childNode.getAttribute("id").getValue());
//						}
//						// 3. TYPE
//						if(childNode.getAttribute("type") != null){
//							member.setType(childNode.getAttribute("type").getValue());
//						}
//						// 4. LENGTH
//						if(childNode.getAttribute("size") != null){
//							member.setLength(childNode.getAttribute("size").getIntValue());
//						}
//						// 5. DESC
//						if(childNode.getAttribute("name") != null){
//							member.setDesc(childNode.getAttribute("name").getValue());
//						}
//						// 6. FORMAT
//						if(childNode.getAttribute("format") != null){
//							member.setFormat(childNode.getAttribute("format").getValue());
//						}
//						// 7. CHILD
//						if(childNode.getChildren() != null && childNode.getChildren().size()>0){
//							// 7-1. Array 세팅
//							member.setArray(true);
//							if(!member.isRoot()){
//								if(childNode.getAttribute("arrayCount") == null){
//									member.setArrayCountMemberName(((org.jdom.Element)list.get(k-1)).getAttribute("id").getValue());
//								}else if( ! xutil.util.CommonUtils.isNumber(childNode.getAttribute("arrayCount").getValue()) ){
//									member.setArrayCountMemberName(childNode.getAttribute("arrayCount").getValue());
//								}else{
//									member.setFixedArray(true);
//									member.setArrayCount(Integer.parseInt(childNode.getAttribute("arrayCount").getValue()));
//								}
//							}
//							// 7-2. 재귀 로딩
//							childStruct = new Struct(member.getName());
//							childStruct = loadStruct(childNode, childStruct);
//							struct.addChildStruct(member, childStruct);
//						}
//						// 6. 멤버등록
//						struct.addMember(member.getName(), member);
//					}
//				}
//			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		/****************************** 세팅 끝 ******************************/
		return struct;
	}
	
	public Struct getStructInfo(String interfaceId){
		return (Struct)this.structInfo.get(interfaceId);
	}
	public String getStructKey(Struct struct){
		String interfaceId = null;
		Object key = null;
		if(this.structInfo.containsValue(struct)){
			java.util.Set keyset =  this.structInfo.keySet();
			java.util.Iterator iter = keyset.iterator();
			while(iter.hasNext()){
				key = iter.next();
				if( struct.equals(this.structInfo.get(key)) ){
					interfaceId = (String)key;
					break;
				}
			}
		}
		return interfaceId;
	}
	public String[] getInterfaceIdList(){
		String[] interfaceIdList = null;
        java.util.Set set = this.structInfo.keySet();
        interfaceIdList = (String[])set.toArray();
		return interfaceIdList;
	}
	
	public void refresh(){
		this.init();
	}
}
