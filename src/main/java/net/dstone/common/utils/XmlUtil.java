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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * XML 핸들링 유틸리티
 * 사용예)
	String xmlPath = "D:/Temp/sql-config.xml";
	net.dstone.common.utils.XmlUtil xmlUtil;
	try {
		System.out.println( "start !!!" );
		
		xmlUtil = net.dstone.common.utils.XmlUtil.getInstance(xmlPath);
		xmlUtil.getDataSet().checkData();
		xmlUtil.getNodeById("dataSource").getAttributes().getNamedItem("destroy-method").setTextContent("close2");
		xmlUtil.save();
		
		System.out.println( "end !!!" );
	} catch (Exception e) {
		e.printStackTrace();
	}
 */
public class XmlUtil {
	public static XmlUtil xml = null;

	public static XmlUtil getInstance(String xmlPath) {
		if (xml == null) {
			xml = new XmlUtil();
		}
		try {
			xml.init(xmlPath);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return xml;
	}

	String xmlPath;
	XPathFactory xpathFactory;
	XPath xPath;
	Document document;
	InputSource xmlSource;
	DataSet dataSet = null;

	private XmlUtil() {

	}

	private void init(String xmlPath) throws Exception {
		this.xpathFactory = XPathFactory.newInstance();
		this.xPath = xpathFactory.newXPath();
		this.xmlPath = xmlPath;
		this.xmlSource = new InputSource(this.xmlPath);
		this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this.xmlSource); 
		this.dataSet = new DataSet();
		parseIntoDataSet(this.document, this.dataSet);
	}
	
	private DataSet parseIntoDataSet(Node node, DataSet pDataSet){
		if(node != null){
			if(isValid(node)){
				pDataSet.setDatum("노드명", node.getNodeName());
				if(node.hasAttributes()){
					NamedNodeMap attArray = node.getAttributes();
					String strKey = null;
					String strVal = null;
					if(attArray != null){
						for(int i=0; i<attArray.getLength();i++){
							strKey = attArray.item(i).getNodeName();
							strVal = attArray.item(i).getNodeValue();
							pDataSet.setDatum(strKey, strVal);
						}
					}
				}
				if(node.hasChildNodes()){
					NodeList nodeList = node.getChildNodes();
					if(nodeList != null){
						for(int i=0; i<nodeList.getLength();i++){
							Node childNode = nodeList.item(i);
							DataSet childDataSet = new DataSet();
							if(isValid(childNode)){
								pDataSet.addRow(childDataSet);
								parseIntoDataSet(childNode, childDataSet);
							}
						}
					}
				}
			}
		}
		return pDataSet;
	}
	
	private boolean isValid(Node node){
		boolean valid = false;
		if( 
			node.getNodeType() == Node.DOCUMENT_NODE 
			|| node.getNodeType() == Node.ELEMENT_NODE
			|| node.getNodeType() == Node.ATTRIBUTE_NODE
		){
			valid = true;
		}
		return valid;
	}

	public DataSet getDataSet(){
		return this.dataSet;
	}

	public DataSet getDataSet(String nodeNm){
		DataSet returnDataSet = null;
		Node node = getNode(nodeNm);
		if(node != null){
			returnDataSet = new DataSet();
			parseIntoDataSet(node, returnDataSet);
		}
		return returnDataSet;
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
		String expression = "//*/" + nodeNm;
		try {
			node = (Node) this.xPath.evaluate(expression, document, XPathConstants.NODE);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
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
	
	public void save() {
		try {
			TransformerFactory transFactory = TransformerFactory.newInstance();
			Transformer transformer = transFactory.newTransformer();
			DOMSource source = new DOMSource(this.getRoot());
			StreamResult result = new StreamResult(new java.io.File(xmlPath));
			transformer.transform(source, result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
