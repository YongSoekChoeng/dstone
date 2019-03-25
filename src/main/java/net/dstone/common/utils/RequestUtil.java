package net.dstone.common.utils;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class RequestUtil {

	/** 로그출력 */
	private static Logger logger = org.slf4j.LoggerFactory.getLogger(RequestUtil.class);
	private boolean boolRequestParseYn = true;
	private boolean boolRequestDetailParseYn = false;

	private static final String MAST_BAR_1 = "*****************************************";
	private static final String MAST_BAR_2 = "========================================";
	private static final String MAST_BAR_3 = "#########################################";

	private javax.servlet.http.HttpServletRequest request;
	private javax.servlet.http.HttpServletResponse response;
	private javax.servlet.http.HttpSession session;
	private net.dstone.common.utils.FileUpUtil fileup;

	private String scheme = "";
	private String serverName = "";
	private int serverPort = 80;
	
	private String strClientIP = "";
	private String strURI = "";
	private String strReferer = "";
	private String strContentsType = "";
	private String strContextPath = "";
	private String strRealPath = "";
	private String strCharacterEncoding = "";
	
	private boolean boolUploadYn = false;
	
	java.util.ArrayList<java.util.Properties> uploadList = new java.util.ArrayList<java.util.Properties>();
	java.util.HashMap<String, String[]> jsonMap = new java.util.HashMap<String, String[]>();
	private boolean jsonMapPopulatedYn = false;

	public RequestUtil(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws Exception {

		this.request = request;
		this.response = response;
		this.session = this.request.getSession(true);
		
		this.scheme = this.request.getScheme();
		this.serverName = this.request.getServerName();
		this.serverPort = this.request.getServerPort();

		this.strClientIP = this.request.getRemoteAddr();
		this.strURI = this.request.getRequestURI();
		this.strContextPath = this.request.getContextPath();
		this.strRealPath = this.request.getSession().getServletContext().getRealPath("");

		this.strReferer = this.request.getHeader("referer");
		this.strContentsType = this.request.getHeader("Content-Type");
		this.strCharacterEncoding = this.request.getCharacterEncoding();
		
		if ((this.strContentsType != null) && (this.strContentsType.toUpperCase().startsWith("MULTIPART"))) {
			try {
				boolUploadYn = true;
				this.fileup = new net.dstone.common.utils.FileUpUtil(request, response);
				this.uploadList = (java.util.ArrayList<java.util.Properties>) this.fileup.getUploadInfo().get("UPLOAD_LIST");
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		} else {
			this.fileup = null;
		}
		if (boolRequestParseYn) {
			parseRequest(this);
		}
	}

	public void parseRequest(RequestUtil requestUtil) {

		java.util.Enumeration enumObj = null;
		StringBuffer buff = new StringBuffer();
		String strTempParamName = "";
		String strT = "";

		buff.append("\r\n").append(strT).append("/" + MAST_BAR_2).append("  " + requestUtil.getClass().getName() + ".parseRequest ").append(MAST_BAR_2).append("/").append("\r\n");
		buff.append(strT).append("< 기초정보 >").append("\r\n");
		buff.append(strT).append("Referer(from 경로) [" + requestUtil.strReferer + "]").append("\r\n");
		buff.append(strT).append("URI    (to   경로) [http://" + requestUtil.request.getServerName() + ":" + requestUtil.request.getServerPort() + requestUtil.strURI + "]").append("\r\n");
		if(boolRequestDetailParseYn){
			buff.append(strT).append("boolUploadYn(파일업로드여부) [" + requestUtil.boolUploadYn + "]").append("\r\n");
			buff.append(strT).append("scheme [" + requestUtil.scheme + "]").append("\r\n");
			buff.append(strT).append("serverName [" + requestUtil.serverName + "]").append("\r\n");
			buff.append(strT).append("serverPort [" + requestUtil.serverPort + "]").append("\r\n");
			buff.append(strT).append("strCharacterEncoding [" + requestUtil.strCharacterEncoding + "]").append("\r\n");
			buff.append(strT).append("strClientIP [" + requestUtil.strClientIP + "]").append("\r\n");
			buff.append(strT).append("strContentsType [" + requestUtil.strContentsType + "]").append("\r\n");
			buff.append(strT).append("strRealPath [" + requestUtil.strRealPath + "]").append("\r\n");
		}
		// buff.append("< 메모리정보 >").append("\r\n");
		buff.append(strT).append("< 파라메터 정보 >").append("\r\n");
		// 일반 request 일때
		if (!requestUtil.boolUploadYn) {
			enumObj = requestUtil.request.getParameterNames();
			while (enumObj.hasMoreElements()) {
				strTempParamName = (String) enumObj.nextElement();
				if (requestUtil.request.getParameterValues(strTempParamName).length == 1) {
					buff.append(strT).append(strTempParamName + " [" + requestUtil.request.getParameter(strTempParamName) + "]").append("\r\n");
				} else {
					String[] strParams = requestUtil.request.getParameterValues(strTempParamName);
					buff.append(strT).append(strTempParamName).append("\r\n");
					for (int i = 0; i < strParams.length; i++) {
						buff.append(strT).append("\t" + i + " [" + strParams[i] + "]").append("\r\n");
					}
					buff.append(strT).append(strTempParamName + " [" + requestUtil.request.getParameter(strTempParamName) + "]").append("\r\n");
				}
			}
		// multipart 일때
		} else {
			enumObj = this.fileup.getParameterNames();
			while (enumObj.hasMoreElements()) {
				strTempParamName = (String) enumObj.nextElement();
				//buff.append(strTempParamName + " [" + this.fileup.getParameter(strTempParamName) + "]").append("\r\n");
				if(this.fileup.getParameterValues(strTempParamName).length == 1){
					buff.append(strT).append(strTempParamName + " [" + this.fileup.getParameter(strTempParamName) + "]").append("\r\n");
				}else{
					String[] strParams = fileup.getParameterValues(strTempParamName);
					buff.append(strT).append(strTempParamName ).append("\r\n");
					for(int i=0; i < strParams.length; i++){
						buff.append(strT).append("\t" + i +" [" + strParams[i] + "]").append("\r\n");
					}						
					buff.append(strT).append(strTempParamName + " [" + this.fileup.getParameter(strTempParamName) + "]").append("\r\n");
				}            	                	   
			}	
			if(getFileCount() > 0){
				buff.append(strT).append("<<< Attached File Start >>>\r\n");
				for(int i=0; i<getFileCount(); i++){
					String originalFileName = getOriginalFileName(i);
					String savedFileName = getSavedFileName(i);
					int fileSize = getFileSize(i);
					buff.append(strT).append("originalFileName [" + originalFileName + "] savedFileName [" + savedFileName + "] fileSize [" + fileSize + "]").append("\r\n");
				}buff.append(strT).append("<<< Attached File End >>>\r\n");
			}
		}

		buff.append(strT).append("/").append(MAST_BAR_2).append(" " + requestUtil.getClass().getName() + ".parseRequest ").append(MAST_BAR_2).append("/\r\n");
		System.out.println(buff.toString());
	}
	
	private boolean populateJson(){
		if(!jsonMapPopulatedYn){
			try {
				java.util.Enumeration params = getParameterNames();
				if(this.boolUploadYn){
					
				}else{
					
				}
				String name = "";
				String value = null;
				String[] values = null;
				boolean isArray = false;
				if(params != null){
					if ( params.hasMoreElements() ) {
						String jsonStr = (String) params.nextElement();
						if(jsonStr.startsWith("{") && jsonStr.endsWith("}")){
							JSONParser parser = new JSONParser();
							JSONObject jObj    = (JSONObject)parser.parse(jsonStr);
							java.util.Iterator iter = jObj.keySet().iterator();
							org.json.simple.JSONArray jArray = null;
							while(iter.hasNext()){
								name = (String)iter.next();
								if( jObj.get(name) instanceof org.json.simple.JSONArray ){
									jArray = (org.json.simple.JSONArray)jObj.get(name);
									for(int i=0; i<jArray.size(); i++){
										jArray.set(i, java.net.URLDecoder.decode(jArray.get(i).toString(), getStrCharacterEncoding()));
									}
									values = new String[jArray.size()];
									jArray.toArray(values);jArray.clear();

								}else{
									value = java.net.URLDecoder.decode((String)jObj.get(name), getStrCharacterEncoding()) ;
									values = new String[1];
									values[0] = value;
								}
								this.jsonMap.put(name, values);
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		jsonMapPopulatedYn = true;
		return jsonMapPopulatedYn;
	}
	
	public String getJsonParameterValue(String name) {
		populateJson();
		String paramVal = "";
		if( this.jsonMap.size() > 0 && this.jsonMap.containsKey(name) ){
			if(this.jsonMap.get(name).length > 0){
				paramVal = this.jsonMap.get(name)[0];
			}			
		}
		return paramVal;
	}

	public String[] getJsonParameterValues(String name) {
		populateJson();
		String[] paramVals = null;
		if( this.jsonMap.size() > 0 && this.jsonMap.containsKey(name) ){
			if(this.jsonMap.get(name).length > 0){
				paramVals = this.jsonMap.get(name);
			}			
		}
		return paramVals;
	}

	public String nullCheck(String input, String defaultVal) {
		return net.dstone.common.utils.StringUtil.nullCheck(input, defaultVal);
	}

	public String getSavedPath() {
		String savedPath = "";
		if (this.fileup != null && uploadList.size() > 0) {
			savedPath = this.fileup.getUploadInfo().getProperty("SAVE_DIRECTORY", "");
		}
		return savedPath;
	}

	public int getFileCount() {
		int iFileCount = 0;
		if (this.fileup != null && uploadList.size() > 0) {
			iFileCount = uploadList.size();
			return iFileCount;
		} else {
			return iFileCount;
		}
	}

	public int getFileSize(int index) {
		int iFileSize = 0;
		if (this.fileup != null && uploadList.size() > 0) {
			iFileSize = Integer.parseInt(uploadList.get(index).getProperty("FILE_SIZE", "0"));
			return iFileSize;
		} else {
			return iFileSize;
		}
	}

	public int[] getFileSizes() {
		int[] iFileSize = null;
		if (this.fileup != null && uploadList.size() > 0) {
			iFileSize = new int[uploadList.size()];
			for (int i = 0; i < iFileSize.length; i++) {
				iFileSize[i] = Integer.parseInt(uploadList.get(i).getProperty("FILE_SIZE", "0"));
			}
		}
		return iFileSize;
	}

	public String getOriginalFileName(int index) {
		String originalFileName = "";
		if (this.fileup != null && uploadList.size() > 0) {
			originalFileName = uploadList.get(index).getProperty("ORIGINAL_FILE_NAME", "");
		}
		return originalFileName;
	}

	public String[] getOriginalFileNames() {
		String[] originalFileNames = null;
		if (this.fileup != null && uploadList.size() > 0) {
			originalFileNames = new String[uploadList.size()];
			for (int i = 0; i < originalFileNames.length; i++) {
				originalFileNames[i] = uploadList.get(i).getProperty("ORIGINAL_FILE_NAME", "");
			}
		}
		return originalFileNames;
	}

	public String getParameter(String name) {
		if (this.fileup == null) {
			return nullCheck(request.getParameter(name), "");
		} else {
			return nullCheck(fileup.getParameter(name), "");
		}
	}

	public String getParameter(String name, String defaultVal) {
		if (this.fileup == null) {
			return nullCheck(request.getParameter(name), defaultVal);
		} else {
			return nullCheck(fileup.getParameter(name), defaultVal);
		}
	}

	public java.util.Enumeration getParameterNames() {
		if (this.fileup == null) {
			return request.getParameterNames();
		} else {
			return fileup.getParameterNames();
		}
	}

	public String[] getParameterValues(String name) {
		if (this.fileup == null) {
			return request.getParameterValues(name);
		} else {
			return fileup.getParameterValues(name);
		}
	}

	public int getIntParameter(String name) {
		if (this.fileup == null) {
			return Integer.parseInt(request.getParameter(name));
		} else {
			return Integer.parseInt(fileup.getParameter(name));
		}
	}

	public String getSavedFileName(int index) {
		String savedFileName = "";
		if (this.fileup != null && uploadList.size() > 0) {
			savedFileName = uploadList.get(index).getProperty("SAVED_FILE_NAME", "");
		}
		return savedFileName;
	}

	public String[] getSavedFileNames() {
		String[] savedFileNames = null;
		if (this.fileup != null && uploadList.size() > 0) {
			savedFileNames = new String[uploadList.size()];
			for (int i = 0; i < savedFileNames.length; i++) {
				savedFileNames[i] = uploadList.get(i).getProperty("SAVED_FILE_NAME", "");
			}
		}
		return savedFileNames;
	}

	public String getSessStrVal(String name) {
		return (String) (this.request.getSession(true).getAttribute(name));
	}

	public Object getSessVal(String name) {
		return (this.request.getSession(true).getAttribute(name));
	}

	public void removeSessVal(String name) {
		this.request.getSession(true).removeAttribute(name);
	}

	public void setSessVal(String name, Object obj) {
		this.request.getSession(true).setAttribute(name, obj);
	}

	/**
	 * Object를 리퀘스트에 담는다.
	 */
	public void setAttribute(String name, Object obj) {
		this.request.setAttribute(name, obj);
	}

	/**
	 * 리퀘스트에 담긴 Object를 꺼내온다.
	 */
	public Object getAttribute(String name) {
		return (this.request.getAttribute(name));
	}

	public String getStrClientIP() {
		return strClientIP;
	}

	public void setStrClientIP(String strClientIP) {
		this.strClientIP = strClientIP;
	}

	public String getStrURI() {
		return strURI;
	}

	public void setStrURI(String strURI) {
		this.strURI = strURI;
	}

	public String getStrReferer() {
		return strReferer;
	}

	public void setStrReferer(String strReferer) {
		this.strReferer = strReferer;
	}

	public String getStrContentsType() {
		return strContentsType;
	}

	public void setStrContentsType(String strContentsType) {
		this.strContentsType = strContentsType;
	}

	public String getStrContextPath() {
		return strContextPath;
	}

	public void setStrContextPath(String strContextPath) {
		this.strContextPath = strContextPath;
	}

	public String getScheme() {
		return scheme;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	public String getStrRealPath() {
		return strRealPath;
	}

	public void setStrRealPath(String strRealPath) {
		this.strRealPath = strRealPath;
	}

	public javax.servlet.http.HttpServletRequest getRequest() {
		return request;
	}

	public void setRequest(javax.servlet.http.HttpServletRequest request) {
		this.request = request;
	}

	public javax.servlet.http.HttpServletResponse getResponse() {
		return response;
	}

	public void setResponse(javax.servlet.http.HttpServletResponse response) {
		this.response = response;
	}

	public javax.servlet.http.HttpSession getSession() {
		return session;
	}

	public void setSession(javax.servlet.http.HttpSession session) {
		this.session = session;
	}

	public String getStrCharacterEncoding() {
		return strCharacterEncoding;
	}

	public void setStrCharacterEncoding(String strCharacterEncoding) {
		this.strCharacterEncoding = strCharacterEncoding;
	}
}
