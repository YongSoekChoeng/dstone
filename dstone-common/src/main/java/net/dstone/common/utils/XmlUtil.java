package net.dstone.common.utils;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class XmlUtil {

	@SuppressWarnings("unused")
	private static LogUtil logger = new LogUtil(XmlUtil.class);

	public static int XML_SOURCE_KIND_PATH 	= 1;
	public static int XML_SOURCE_KIND_STRING = 2;
	
	public static XmlUtil xml = null;

	public static XmlUtil getInstance(int xmlSourceKind, String xmlSource) {
		if (xml == null) {
			xml = new XmlUtil();
		}
		try {
			xml.init(xmlSourceKind, xmlSource);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return xml;
	}

	public static XmlUtil getNonSingletonInstance(int xmlSourceKind, String xmlSource) {
		XmlUtil xmlInstance = new XmlUtil();
		try {
			xmlInstance.init(xmlSourceKind, xmlSource);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return xmlInstance;
	}

	String xmlPath;
	XPathFactory xpathFactory;
	XPath xPath;
	Document document;
	InputSource xmlSource;
	
	private XmlUtil() {

	}

	private void init(int xmlSourceKind, String xmlSource) throws Exception {
		this.xpathFactory = XPathFactory.newInstance();
		this.xPath = xpathFactory.newXPath();
		java.io.FileInputStream fin = null;
		
		try {
			if( xmlSourceKind ==  XML_SOURCE_KIND_PATH){
				this.xmlPath = xmlSource;
				fin =  new java.io.FileInputStream(this.xmlPath);
				this.xmlSource = new InputSource(fin);
				this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this.xmlSource); 
			}else{
				this.xmlPath = null;
				this.xmlSource = new InputSource(new StringReader(xmlSource));
				this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this.xmlSource); 
			}
		} catch (Exception e) {
			logger.error("net.dstone.common.utils.XmlUtil.init(xmlSourceKind["+xmlSourceKind+"]" + " xmlSource["+xmlSource+"]) 수행중 예외발생.");
			e.printStackTrace();
		} finally {
			if( fin != null ){try{ fin.close(); }catch(Exception e){}}
		}

	}
	
	public boolean hasChildElement(Node node){
		boolean hasChild = false;
		if( node != null ){
			if( node.getNodeType() == Node.ELEMENT_NODE ){
				NodeList nodeList = node.getChildNodes();
				if( nodeList != null ){
					Node child = null;
					for(int i=0; i<nodeList.getLength(); i++){
						child = nodeList.item(i);
						if( child.getNodeType() == Node.ELEMENT_NODE ){
							hasChild = true;
							break;
						}
					}
				}
			}
		}
		return hasChild;
	}

	public Node getRoot() {
		Node node = null;
		String expression = "/" ;
		try {
			node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
	}
	
	public Node getNode(String nodeNm) {
		Node node = null;
		String expression = "/" + nodeNm;
		try {
			node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
			if(node == null){
				expression = "//*/" + nodeNm;
				node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
	}

	public Node getNodeByExp(String expression) {
		Node node = null;
		try {
			node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
	}
	
	public int getNodeCountByExp(String expression) {
		int cnt = 0;
		try {
			Number number = (Number) this.xPath.evaluate( "count(" + expression + ")", document, XPathConstants.NUMBER);
			cnt = number.intValue();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cnt;
	}
	
	public Node getNodeById(String id) {
		Node node = null;
		String expression = "//*[@id='" +id+ "']";
		try {
			node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
	}
	public NodeList getNodeList(String nodeNm) {
		NodeList nodeList = null;
		String expression = "//*/" + nodeNm;
		try {
			nodeList = (NodeList) this.xPath.evaluate(expression, document, XPathConstants.NODESET);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nodeList;
	}
	public NodeList getNodeListByExp(String expression) {
		NodeList nodeList = null;
		try {
			nodeList = (NodeList) this.xPath.evaluate(expression, document, XPathConstants.NODESET);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nodeList;
	}
	

	
	public boolean hasNode(String nodeNm) {
		boolean isHasNode = false;
		if(getNode(nodeNm) != null){
			isHasNode = true;
		}
		return isHasNode;
	}
	public boolean hasNodeById(String id) {
		boolean isHasNode = false;
		if(getNodeById(id) != null){
			isHasNode = true;
		}
		return isHasNode;
	}
	
	public boolean hasChildNode(String parentNodeNm, String childNodeNm) {
		boolean isHasNode = false;
		Node parentNode = getNode(parentNodeNm);
		if(parentNode != null){
			NodeList childNodeList = parentNode.getChildNodes();
			if(childNodeList != null){
				for(int i=0; i<childNodeList.getLength(); i++){
					if(childNodeNm.equals(childNodeList.item(i).getNodeName())){
						isHasNode = true;
						break;
					}
				}
			}
		}
		return isHasNode;
	}

	/**
	 * XML내의 주석을 제거한다.
	 * @param sql
	 * @return
	 */
	public static String removeCommentsFromXml(String xml){
		String returnXml = xml; 
		String rexp = "";
		try {
			if(!StringUtil.isEmpty(xml)) {
				rexp = "<!--(?!<!)[^\\[>][\\s\\S]*?-->";
				returnXml = returnXml.replaceAll(rexp, "");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnXml;
	}
	
	public void save() {
		try {
			if( this.xmlPath != null ){
				TransformerFactory transFactory = TransformerFactory.newInstance();
				Transformer transformer = transFactory.newTransformer();
				DOMSource source = new DOMSource(this.getRoot());
				StreamResult result = new StreamResult(new java.io.File(this.xmlPath));
				transformer.transform(source, result);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	/**
	 * @return the xPath
	 */
	public XPath getxPath() {
		return xPath;
	}

	/**
	 * @param xPath the xPath to set
	 */
	public void setxPath(XPath xPath) {
		this.xPath = xPath;
	}
	
}

