package net.dstone.boot.common.tools.analyzer.svc.query.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.SvcAnalyzer;
import net.dstone.boot.common.tools.analyzer.svc.query.ParseQuery;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.QueryVo;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.SqlUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.XmlUtil;

public class MybatisParseQuery implements ParseQuery {

	private static Map<String, String> MYBATIS_INCLUDE_SQL = new HashMap<String, String>();
	private static boolean IS_MYBATIS_INCLUDE_SQL_DONE = false;
	
	private static List<String> MYBATIS_REMOVE_TAG_LIST = new ArrayList<String>();
	static {
		/*****************************************************
		MYBATIS_REMOVE_TAG_LIST.add("choose");
		MYBATIS_REMOVE_TAG_LIST.add("foreach");
		MYBATIS_REMOVE_TAG_LIST.add("dynamic");
		*****************************************************/
	}

	private static List<String> MYBATIS_DIV_TAG_LIST = new ArrayList<String>();
	static {
		/*****************************************************
		MYBATIS_DIV_TAG_LIST.add("isEqual");
		MYBATIS_DIV_TAG_LIST.add("isNull");
		MYBATIS_DIV_TAG_LIST.add("isNotNull");
		MYBATIS_DIV_TAG_LIST.add("isNotEqual");
		MYBATIS_DIV_TAG_LIST.add("isEmpty");
		MYBATIS_DIV_TAG_LIST.add("isNotEmpty");
		MYBATIS_DIV_TAG_LIST.add("isGreaterThan");
		MYBATIS_DIV_TAG_LIST.add("isGreaterEqual");
		MYBATIS_DIV_TAG_LIST.add("isLessEqual");
		MYBATIS_DIV_TAG_LIST.add("isPropertyAvailable");
		MYBATIS_DIV_TAG_LIST.add("isNotPropertyAvailable");
		MYBATIS_DIV_TAG_LIST.add("isParameterPresent");
		MYBATIS_DIV_TAG_LIST.add("isNotParameterPresent");
		*****************************************************/
	}

	/**
	 * 공통 include 쿼리 정보를 추출하여 맵(MYBATIS_INCLUDE_SQL)에 저장하는 메소드
	 */
	private static void fillMybatisIncludeSql() {
		
		if(!IS_MYBATIS_INCLUDE_SQL_DONE) {
			
			String[] queryFileList = FileUtil.readFileListAll(AppAnalyzer.QUERY_ROOT_PATH);
			XmlUtil xml = null;
			String rootKeyword = "";
			String nodeExp = "";
			String namespace = "";
			NodeList nodeList = null;
			Node item = null;
			String sqlKey = "";
			String sqlId = "";
			StringBuffer sqlBody = new StringBuffer();
			
			// 1. MYBATIS_INCLUDE_SQL 맵에 공통 SQL를 세팅. (include태그 가 있는 SQL은 include태그 그대로 저장)
			for(String queryFile : queryFileList) {
				if( !SvcAnalyzer.isValidQueryFile(queryFile) ) {
					continue;
				}
				xml = XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, queryFile);
				// Mibatis
				if(xml.hasNode("mapper")) {
					rootKeyword = "mapper";
				}else if(xml.hasNode("Mapper")) {
					rootKeyword = "Mapper";
				// Ibatis
				}else if(xml.hasNode("sqlMap")) {
					rootKeyword = "sqlMap";
				}else if(xml.hasNode("SqlMap")) {
					rootKeyword = "SqlMap";
				}
				if( xml.hasNode(rootKeyword) && xml.getNode(rootKeyword).getAttributes() != null ) {
					namespace = xml.getNode(rootKeyword).getAttributes().getNamedItem("namespace").getTextContent();
					nodeExp = "/"+rootKeyword+"/sql";
					nodeList = xml.getNodeListByExp(nodeExp);
					if( nodeList != null ){
						for(int i=0; i<nodeList.getLength(); i++){
							item =	nodeList.item(i);
							sqlId = item.getAttributes().getNamedItem("id").getTextContent();
							sqlBody = new StringBuffer();
							
							NodeList cNodeList = item.getChildNodes();
							if( cNodeList != null ) {
								for(int k=0; k<cNodeList.getLength(); k++){
									Node cNode = cNodeList.item(k);
									if( cNode.getNodeType() == Node.ELEMENT_NODE && "include".equals(cNode.getNodeName())) {
										sqlBody.append("<include refid=\""+cNode.getAttributes().getNamedItem("refid").getTextContent()+"\"/>");
									}else {
										sqlBody.append(cNode.getTextContent());
									}
								}
							}else {
								sqlBody.append(item.getTextContent());
							}
							sqlKey = namespace + "." + sqlId;
							MYBATIS_INCLUDE_SQL.put(sqlKey, sqlBody.toString());
						}
					}
				}
			}
			
			// 2. MYBATIS_INCLUDE_SQL 맵에 include태그가 있는 SQL들의 include를 해당 쿼리로 치환.(재귀적처리)
			Iterator<String> iter = MYBATIS_INCLUDE_SQL.keySet().iterator();
			String sqlConts = "";
			String refid = "";
			String[] lines = null;
			String[] div = {"\""};
			while(iter.hasNext()) {
				sqlKey = iter.next();
				sqlConts = MYBATIS_INCLUDE_SQL.get(sqlKey);
				if(sqlConts.indexOf("<include")>-1) {
					sqlBody = new StringBuffer();
					lines = StringUtil.toStrArray(sqlConts, "\n");
					if(lines != null) {
						for(String line : lines) {
							if(line.indexOf("<include")>-1) {
								refid = StringUtil.nextWord(line, "<include refid=\"", div);
								sqlBody.append(MybatisParseQuery.getMybatisIncludeSql(refid)).append("\n");
							}else {
								sqlBody.append(line).append("\n");
							}
						}
					}
					MYBATIS_INCLUDE_SQL.put(sqlKey, sqlBody.toString());
				}
			}
			
			// 3. IS_MYBATIS_INCLUDE_SQL_DONE 을 true 로 세팅.
			IS_MYBATIS_INCLUDE_SQL_DONE = true;
			
		}
	}
	
	/**
	 * refid에 해당하는 공통 include 쿼리를 추출하는 메소드. (재귀적으로 동작)
	 * @param refid
	 * @return
	 */
	private static String getMybatisIncludeSql(String refid) {
		StringBuffer sqlBody = new StringBuffer();
		if(MYBATIS_INCLUDE_SQL.containsKey(refid)) {
			String sqlConts = MYBATIS_INCLUDE_SQL.get(refid);
			if(sqlConts.indexOf("<include")>-1) {
				String[] lines = StringUtil.toStrArray(sqlConts, "\n");
				String[] div = {"\""};
				if(lines != null) {
					for(String line : lines) {
						if(line.indexOf("<include")>-1) {
							String subRefid = StringUtil.nextWord(line, "<include refid=\"", div);
							sqlBody.append(MybatisParseQuery.getMybatisIncludeSql(subRefid)).append("\n");
						}else {
							sqlBody.append(line).append("\n");
						}
					}
				}
			}else {
				sqlBody.append(sqlConts).append("\n");
			}
		}
		return sqlBody.toString();
	}
	
	/**
	 * Mybatis 내부 태그 제거. 쿼리분석을 위해서 Mybatis 관련태그를 제거하는 메소드.
	 * @param xml
	 * @param nodeExp
	 * @param recursivelyYn
	 * @return
	 */
	private static String removeMybatisTagFromSql(XmlUtil xml, String nodeExp, boolean recursivelyYn) {
		StringBuffer outBuff = new StringBuffer();
		Node node = xml.getNodeByExp(nodeExp);
		if(node != null) {
			XmlUtil innerXml = null;
			NodeList nodeList = node.getChildNodes();
			if(nodeList != null) {
				Node cNode = null;
				String refid = "";
				String cNodeExp = "";
				for(int i=0; i<nodeList.getLength(); i++) {
					cNode = nodeList.item(i);
					
					/*******************************************************
					노드타입이름							정수값	노드종류
					Node.ELEMENT_NODE					1		Element
					Node.ATTRIBUTE_NODE					2		Attr
					Node.TEXT_NODE						3		Text
					Node.CDATA_SECTION_NODE				4		CDATASection
					Node.ENTITY_REFERENCE_NODE			5		EntityReference
					Node.ENTITY_NODE					6		Entity
					Node.PROCESSING_INSTRUCTION_NODE	7		ProcessingInstruction
					Node.COMMENT_NODE					8		Comment
					Node.DOCUMENT_NODE					9		Document
					Node.DOCUMENT_TYPE_NODE				10		DocumentType
					Node.DOCUMENT_FRAGMENT_NODE			11		DocumentFragment
					Node.NOTATION_NODE					12		Notation
					*******************************************************/
					// 노드타입이 Element 의 경우
					if( cNode.getNodeType() == Node.ELEMENT_NODE) {
						// 노드명이 include 의 경우
						if("include".equals(cNode.getNodeName())) {
							// 재귀조회일 경우
							if( recursivelyYn) {
								// refid 를 구한다.
								refid = cNode.getAttributes().getNamedItem("refid").getTextContent();
								// 자체 XML에 존재하는 쿼리 라면
								if( xml.hasNodeById(refid)) {
									 cNodeExp = "//*[@id='"+refid+"']";
									 outBuff.append(MybatisParseQuery.removeMybatisTagFromSql(xml, cNodeExp, recursivelyYn));
								// 별도 XML에 존재하는 쿼리 라면(공통 include 쿼리 라면)
								}else {
									if( MYBATIS_INCLUDE_SQL.containsKey(refid) ) {
										outBuff.append(MYBATIS_INCLUDE_SQL.get(refid));
									}
								}
							}else {
								outBuff.append(cNode.getTextContent());
							}
						// 노드명이 where 의 경우 WHERE 1=1 AND ~ 로 대체해준다.
						}else if("where".equals(cNode.getNodeName())) {
							outBuff.append( " WHERE ");
							if( cNode.getTextContent().trim().toUpperCase().startsWith("AND") ) {
								outBuff.append( " 1=1 ");
							}
							outBuff.append( cNode.getTextContent());
						// 노드명이 trim 의 경우 prefix 를 제거해주고  WHERE 1=1 AND ~ 로 대체해준다.
						}else if("trim".equals(cNode.getNodeName())) {
							if( cNode.getAttributes().getNamedItem("prefix") != null && "WHERE".equalsIgnoreCase(cNode.getAttributes().getNamedItem("prefix").getTextContent())) {
								outBuff.append( " WHERE ");
								if( cNode.getTextContent().trim().toUpperCase().startsWith("AND") ) {
									outBuff.append( " 1=1 ");
								}
							}
						// 노드명이 MYBATIS_DIV_TAG_LIST(분기태그)의 경우
						}else if(MYBATIS_DIV_TAG_LIST.contains(cNode.getNodeName())) {
							String ifElseConts = cNode.getTextContent().trim().toUpperCase();
							ifElseConts = StringUtil.trimTextForParse(ifElseConts);
							if(ifElseConts.startsWith("(SELECT") || ifElseConts.startsWith("( SELECT")) {
								innerXml = XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_STRING, "<sqlMap><select>" + ifElseConts + "</select></sqlMap>");
								String ifElseSql = MybatisParseQuery.removeMybatisTagFromSql(innerXml, "/sqlMap/select", recursivelyYn);
								outBuff.append(ifElseSql);
							}
						// 노드명이 MYBATIS_REMOVE_TAG_LIST(무시태그)의 경우
						}else if(MYBATIS_REMOVE_TAG_LIST.contains(cNode.getNodeName())) {
							continue;
						}else {
							outBuff.append(cNode.getTextContent());
						}
					// 노드타입이 텍스트 의 경우
					}else if (cNode.getNodeType() == Node.TEXT_NODE) {
						outBuff.append(cNode.getNodeValue());
					// 노드타입이 코멘트 의 경우
					}else if(cNode.getNodeType() == Node.COMMENT_NODE) {
						outBuff.append("");
					}else {
						outBuff.append(cNode.getTextContent());
					}
				}
			}
		}
		return outBuff.toString();
	}
	
	/**
	 * 파일로부터 쿼리KEY(아이디)를 추출. 쿼리KEY는 파일명으로 사용됨.
	 * @param queryInfo(쿼리KEY를 추출할 수 있는 각종 정보를 담은 맵)
	 * @return
	 */
	@Override
	public String getQueryKey(Map<String, String> queryInfo) throws Exception {
		String queryKey = "";
		if( queryInfo != null ) {
			// KEY
			if(StringUtil.isEmpty(queryInfo.get("SQL_NAMESPACE"))) {
				queryKey = "NO_NAMESPACE" + "_" + queryInfo.get("SQL_ID");
			}else {
				queryKey = queryInfo.get("SQL_NAMESPACE") + "_" + queryInfo.get("SQL_ID");
			}
		}
		return queryKey;
	}
	
	/**
	 * 파일로부터 쿼리정보목록 추출. 쿼리정보는 아래와 같은 항목을 추출해야 한다.
	 * SQL_NAMESPACE - 네임스페이스
	 * SQL_ID - SQL아이디
	 * SQL_KIND - SQL종류(SELECT/INSERT/UPDATE/DELETE)
	 * SQL_BODY - SQL구문
	 * @param queryFile
	 * @return
	 */
	@Override
	public List<Map<String, String>> getQueryInfoList(String queryFile) {
		
		/*** 공통 include 쿼리 정보를 추출하여 맵(MYBATIS_INCLUDE_SQL)에 저장 시작 ***/
		MybatisParseQuery.fillMybatisIncludeSql();
		/*** 공통 include 쿼리 정보를 추출하여 맵(MYBATIS_INCLUDE_SQL)에 저장 끝 ***/
		
		List<Map<String, String>> qList = new ArrayList<Map<String, String>>();
		if(FileUtil.isFileExist(queryFile)) {
			XmlUtil xml = XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, queryFile);
			String rootKeyword = "";
			List<String> queryKinds = new ArrayList<String>();
			queryKinds.add("select");
			queryKinds.add("insert");
			queryKinds.add("update");
			queryKinds.add("delete");
			queryKinds.add("sql");
			
			/*** Mibatis/Ibatis 구분 ***/
			if(xml.hasNode("mapper")) {			// Mibatis
				rootKeyword = "mapper";
			}else if(xml.hasNode("Mapper")) {
				rootKeyword = "Mapper";
			}else if(xml.hasNode("sqlMap")) {	// Ibatis
				rootKeyword = "sqlMap";
			}else if(xml.hasNode("SqlMap")) {
				rootKeyword = "SqlMap";
			}
			
			if( xml.hasNode(rootKeyword) && xml.getNode(rootKeyword).getAttributes() != null ) {
				/*** 네임스페이스 ***/
				String namespace = xml.getNode(rootKeyword).getAttributes().getNamedItem("namespace").getTextContent();
				String nodeExp = "/"+rootKeyword+"/*";
				NodeList nodeList = xml.getNodeListByExp(nodeExp);
				if( nodeList != null ){
					Map<String, String> row = new HashMap<String, String>();
					String sqlBody = "";
					for(int i=0; i<nodeList.getLength(); i++){
						Node item =	nodeList.item(i);
						if( !queryKinds.contains(item.getNodeName()) ) {continue;}
						
						row = new HashMap<String, String>();
						row.put("SQL_NAMESPACE", namespace);
						/*** SQL아이디 ***/
						row.put("SQL_ID", item.getAttributes().getNamedItem("id").getTextContent());
						/*** SQL종류(SELECT/INSERT/UPDATE/DELETE) ***/
						row.put("SQL_KIND", item.getNodeName().toUpperCase());
						
						// Mybatis/Ibatis 쿼리의 내부 태그 제거.
						nodeExp = "/"+rootKeyword+"/" + item.getNodeName() + "[@id='" + item.getAttributes().getNamedItem("id").getTextContent() + "']";
						sqlBody = MybatisParseQuery.removeMybatisTagFromSql(xml, nodeExp, true);
						// 테이블명을 파싱하기 좋게 SQL을 간소화.
						sqlBody = ParseUtil.removeBasicTagFromSql(sqlBody, row.get("SQL_KIND"));
						
						/*** SQL구문 ***/
						row.put("SQL_BODY", sqlBody.toUpperCase());

						if(!StringUtil.isEmpty(row.get("SQL_BODY"))) {
							qList.add(row);
						}
					}
				}
			}
		}
		return qList;
	}

	/**
	 * 쿼리정보파일로부터 테이블ID정보목록 추출
	 * @param queryInfoFile
	 * @param allTblList
	 * @return
	 */
	@Override
	public List<String> getTblInfoList(String queryInfoFile, List<String> allTblList) throws Exception {
		List<String> tblNameList = new ArrayList<String>();
		QueryVo queryVo = ParseUtil.readQueryVo(FileUtil.getFileName(queryInfoFile, false), AppAnalyzer.WRITE_PATH + "/query");
		if(queryVo != null) {
			if(!StringUtil.isEmpty(queryVo.getQueryBody())) {
				tblNameList = SqlUtil.getTableNames(queryVo.getQueryBody(), allTblList);
			}
			if(tblNameList.isEmpty()) {
				LogUtil.sysout("DefaultQuery.getTblInfoList :: 파일["+queryInfoFile+"] 을 분석하였으나 테이블명 조회 하지 못함.");
			}
		}
		return tblNameList;
	}

}
