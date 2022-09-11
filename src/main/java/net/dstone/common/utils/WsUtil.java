package net.dstone.common.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.http.ssl.SSLContexts;

public class WsUtil {
	
	public static int HTTP_OK 			= 200;
	public static int HTTP_NOT_FOUND 	= 404;

	public static String CONT_TYPE_HTML = "text/html";
	public static String CONT_TYPE_FORM = "application/x-www-form-urlencoded";
	public static String CONT_TYPE_XML 	= "text/xml";
	public static String CONT_TYPE_JSON = "application/json";

	private static LogUtil logger = new LogUtil(WsUtil.class);
	
	public static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[]{
		new X509TrustManager(){
			@Override
			public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
			}
			@Override
			public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
			}
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		}
	};
	
	public Bean bean;
	public ResponseReader response;
	
	public HttpMethod httpMethod = null;
	
	public String execute(Bean tBean) {
		return execute(tBean, null);
	}

	public String execute(Bean tBean, String charset) {

		this.bean = tBean;
		this.response = new ResponseReader();
		try {
			if(!StringUtil.isEmpty(charset)){
				this.response.charset = charset;
			}
			
			if(StringUtil.isEmpty(bean.method)) {
				throw new Exception("Web 전송 메서드(method)를 세팅해 주세요. 현재 세팅된 메서드(method)["+bean.method+"]");
			}
			if(StringUtil.isEmpty(bean.contentType)) {
				throw new Exception("Web 전송 컨텐츠타입(contentType)을 세팅해 주세요. 현재 세팅된 컨텐츠타입(contentType)["+bean.contentType+"]");
			}
			
			// 01.STEP1 - Http 메소드세팅
			this.getHttpMethod();
			
			// 02.STEP2 - 헤더세팅
			this.setHeaders();
			
			// 03.STEP3 - 본문세팅
			this.setBody(charset);	
			
			// 04.STEP4 - 작업처리		
			this.doExecute(this.httpMethod, this.response);

		} catch (java.io.IOException e) {
			abort();
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			abort();
			e.printStackTrace();
		} catch (Exception e) {
			abort();
			e.printStackTrace();
		}
		return this.response.outputStr.toString();
	}
	
	private HttpMethod getHttpMethod() {
		try {
			if ("GET".equals(bean.method)) {
				this.httpMethod = new GetMethod(bean.url);
			} else if ("POST".equals(bean.method)) {
				this.httpMethod = new PostMethod(bean.url);
			} else if ("PUT".equals(bean.method)) {
				this.httpMethod = new PutMethod(bean.url);
			} else if ("DELETE".equals(bean.method)) {
				this.httpMethod = new DeleteMethod(bean.url);
			} else {
				throw new RuntimeException("Method '" + bean.method + "' not implemented.");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}

		return this.httpMethod;
	}
	/**
	 * Populating HttpMethod with headers
	 */
	private void setHeaders() {
		for (String key : bean.headers.keySet()) {
			Collection<String> values = (Collection<String>) bean.headers.get(key);
			StringBuilder sb = new StringBuilder();
			int cnt = 0;
			for (String val : values) {
				if (cnt != 0) {
					sb.append(",");
				}
				sb.append(val);
				cnt++;
			}
			this.httpMethod.addRequestHeader(key, sb.toString());
		}
		this.httpMethod.addRequestHeader("Content-Type", bean.getContentType());
	}		
	
	/**
	 * Populating Body
	 */
	private void setBody(String charset)throws Exception {
		
		if(this.bean != null){
			
			/*** 1.파라메터 수집 ***/
			
			NameValuePair[] nameValPairArr = new NameValuePair[bean.parameters.getChildrenCount()];
			int paramsIndex = 0;
			String[] values = null;
			for (String key : bean.parameters.getChildrenKeys()) {
				values = bean.parameters.getDatumArray(key);
				StringBuilder sb = new StringBuilder();
				int cnt = 0;
				for (String val : values) {
					if (cnt != 0) {
						sb.append(",");
					}
					if(StringUtil.isEmpty(charset)){
						sb.append(val);
					}else{
						sb.append(StringUtil.encodingConv(val, charset));
					}
					cnt++;
				}
				nameValPairArr[paramsIndex++] = new NameValuePair(key, sb.toString());
			}		
			
			/*** 2.컨텐츠타입별 파라메터 세팅 ***/
			StringRequestEntity requestEntity = null;

			// HTML 전송일 경우
			if( WsUtil.CONT_TYPE_HTML.equals(this.bean.getContentType()) ){
				if ("GET".equals(bean.method)) {
					GetMethod getMethod = (GetMethod)this.getHttpMethod();
					getMethod.setQueryString(nameValPairArr);
				// PUT : 존재하는 자원을 변경 혹은 새로운 자원을 추가. PUT은 단일 자원에만 수행
				}else if ("PUT".equals(bean.method)) {
					PutMethod putMethod = (PutMethod)this.getHttpMethod();
					putMethod.setQueryString(nameValPairArr);
					String queryString = putMethod.getQueryString(); 
					putMethod.setQueryString("");
					requestEntity = new StringRequestEntity( queryString, this.bean.getContentType(), charset);
					putMethod.setRequestEntity(requestEntity);
				// DELETE : 자원을 삭제	
				}else if ("DELETE".equals(bean.method)) { 
					DeleteMethod deleteMethod = (DeleteMethod)this.getHttpMethod();
					deleteMethod.setQueryString(nameValPairArr);
				}else {
					throw new Exception("컨텐츠타입["+this.bean.getContentType()+"]에서 지원하지 않는 메소드["+bean.method+"] 입니다.");
				}
			// FORM 전송일 경우
			}else if( WsUtil.CONT_TYPE_FORM.equals(this.bean.getContentType()) ){
				// GET
				if ("GET".equals(bean.method)) {
					GetMethod getMethod = (GetMethod)this.getHttpMethod();
					getMethod.setQueryString(nameValPairArr);		
				// PUT : 존재하는 자원을 변경 혹은 새로운 자원을 추가. PUT은 단일 자원에만 수행
				}else if ("PUT".equals(bean.method)) {
					PutMethod putMethod = (PutMethod)this.getHttpMethod();
					putMethod.setQueryString(nameValPairArr);
					String queryString = putMethod.getQueryString(); 
					putMethod.setQueryString("");
					requestEntity = new StringRequestEntity( queryString, this.bean.getContentType(), charset);
					putMethod.setRequestEntity(requestEntity);
				// POST : 새로운 자원을 추가. POST는 여러개의 자원에 수행	
				}else if ("POST".equals(bean.method)) { 
					PostMethod postMethod = (PostMethod)this.getHttpMethod();
					postMethod.setRequestBody(nameValPairArr);
				// DELETE : 자원을 삭제	
				}else if ("DELETE".equals(bean.method)) { 
					DeleteMethod deleteMethod = (DeleteMethod)this.getHttpMethod();
					deleteMethod.setQueryString(nameValPairArr);
				}else {
					throw new Exception("컨텐츠타입["+this.bean.getContentType()+"]에서 지원하지 않는 메소드["+bean.method+"] 입니다.");
				}
			// JSON 전송일 경우
			}else if( WsUtil.CONT_TYPE_JSON.equals(this.bean.getContentType()) ){
				if(StringUtil.isEmpty(charset)){
					this.bean.body = this.bean.parameters.toJson();
				}else{
					this.bean.body = StringUtil.encodingConv(this.bean.parameters.toJson(), charset);
				}
				requestEntity = new StringRequestEntity( this.bean.body, this.bean.getContentType(), charset);
				// GET
				if ("GET".equals(bean.method)) {
					GetMethod getMethod = (GetMethod)this.getHttpMethod();
					getMethod.setQueryString(nameValPairArr);				
				// PUT : 존재하는 자원을 변경 혹은 새로운 자원을 추가. PUT은 단일 자원에만 수행
				}else if ("PUT".equals(bean.method)) {
					PutMethod putMethod = (PutMethod)this.getHttpMethod();
					putMethod.setRequestEntity(requestEntity);
				// POST : 새로운 자원을 추가. POST는 여러개의 자원에 수행	
				}else if ("POST".equals(bean.method)) { 
					PostMethod postMethod = (PostMethod)this.getHttpMethod();
					postMethod.setRequestEntity(requestEntity);
				// DELETE : 자원을 삭제	
				}else if ("DELETE".equals(bean.method)) { 
					DeleteMethod deleteMethod = (DeleteMethod)this.getHttpMethod();
					deleteMethod.setQueryString(nameValPairArr);
				}else {
					throw new Exception("컨텐츠타입["+this.bean.getContentType()+"]에서 지원하지 않는 메소드["+bean.method+"] 입니다.");
				}
			// XML 전송일 경우
			}else if( WsUtil.CONT_TYPE_XML.equals(this.bean.getContentType()) ){
				if(StringUtil.isEmpty(charset)){
					this.bean.body = this.bean.parameters.toXml();
				}else{
					this.bean.body = StringUtil.encodingConv(this.bean.parameters.toXml(), charset);
				}
				requestEntity = new StringRequestEntity( this.bean.body, this.bean.getContentType(), charset);
				// GET
				if ("GET".equals(bean.method)) {
					GetMethod getMethod = (GetMethod)this.getHttpMethod();
					getMethod.setQueryString(nameValPairArr);			
				// PUT : 존재하는 자원을 변경 혹은 새로운 자원을 추가. PUT은 단일 자원에만 수행
				}else if ("PUT".equals(bean.method)) {
					PutMethod putMethod = (PutMethod)this.getHttpMethod();
					putMethod.setRequestEntity(requestEntity);
				// POST : 새로운 자원을 추가. POST는 여러개의 자원에 수행	
				}else if ("POST".equals(bean.method)) { 
					PostMethod postMethod = (PostMethod)this.getHttpMethod();
					postMethod.setRequestEntity(requestEntity);
				// DELETE : 자원을 삭제	
				}else if ("DELETE".equals(bean.method)) { 
					DeleteMethod deleteMethod = (DeleteMethod)this.getHttpMethod();
					deleteMethod.setQueryString(nameValPairArr);
				}else {
					throw new Exception("컨텐츠타입["+this.bean.getContentType()+"]에서 지원하지 않는 메소드["+bean.method+"] 입니다.");
				}
			}
		}
	}	
	
	/**
	 * Executing HttpMethod.
	 */
	private void doExecute(HttpMethod httpMethod, ResponseReader responseReader) {
		if( this.bean.url.toLowerCase().startsWith("https") ){
			Protocol.registerProtocol("https", new Protocol("https", new TrustAllSsLSocketFactory(), 433));
		}
		
		HttpClient client 		= null;
		long sTime 				= System.currentTimeMillis();
		long eTime 				= 0;
		boolean debugInOutYn	= true;
		StringBuffer debugStr 	= new StringBuffer();
		try {

			client 						= new HttpClient();
			responseReader.outputStr 	= new StringBuffer();
			client.executeMethod(httpMethod);	
			responseReader.read(httpMethod);
			
			eTime = System.currentTimeMillis();

			if(debugInOutYn){
				debugStr.append("\n");
				debugStr.append("\n");
				debugStr.append("/************************************* WsUtil 호출 START **************************************************************/").append("\n");
				debugStr.append("URL ["+this.bean.url+"] Elapsed Time["+ Long.toString( ( eTime-sTime) ) +"] StatusCd["+ responseReader.StatusCd +"]").append("\n");
				debugStr.append("/************************************* WsUtil 호출 END ****************************************************************/").append("\n");
				debugStr.append("\n");
				debugStr.append("\n");
//				debugStr.append("/************************************* WsUtil 호출결과 START **************************************************************/").append("\n");
//				debugStr.append(responseReader.outputStr.toString()).append("\n");
//				debugStr.append("/************************************* WsUtil 호출결과 END ****************************************************************/").append("\n");
//				debugStr.append("\n");
//				debugStr.append("\n");
				logger.info(debugStr.toString());
			}


		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);

		} finally {
			if (httpMethod != null){
				httpMethod.releaseConnection();
			}		
		}
	}
	
	/**
	 * Canceling HttpMethod execution.
	 */
	public void abort() {
		if (httpMethod == null) {
			return;
		}
		try {
			httpMethod.abort();
			httpMethod = null;
		} catch (Exception giveup) {
			giveup.printStackTrace();
		}
	}

	/**
	 * This is a helper class holding HTTP packet data.
	 */
	public static class Bean {
		public String method = "GET";
		public String url = "";
		private String body = "";
		private String contentType = CONT_TYPE_HTML;
		
		private Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
		private DataSet parameters = new DataSet();
		
		public String getContentType() {
			return contentType;
		}
		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public void addHeader(String key, String value) {
			if( StringUtil.isEmpty(this.contentType)){
				this.contentType = WsUtil.CONT_TYPE_HTML;
			}
			List<String> valuesList = (List<String>) headers.get(key);
			if (valuesList == null) {
				valuesList = new ArrayList<String>();
			}
			valuesList.add(value);
			headers.put(key, valuesList);
		}

		public void addParam(String key, String value) {
			parameters.addDatum(key, value);
		}

		public String getBody() {
			return body;
		}
		public void setBody(String body) {
			this.body = body;
		}

		public DataSet getParameters() {
			return parameters;
		}
		public void setParameters(DataSet parameters) {
			this.parameters = parameters;
		}

		public String toString() {
			return "{method=" + method + ",url=" + url + ",headers=" + headers + ",parameters=" + parameters + "}";
		}
	}

	/**
	 * This interface is being hooked to the execution template method and it is
	 * being invoked on response read.
	 */
	public class ResponseReader {
		public int StatusCd = -1;
		public StatusLine Status = null;
		public StringBuffer outputStr = null;
		public String buffStr = "";
		public String charset = "";
		
		public void read(HttpMethod httpMethod) {
			outputStr = new StringBuffer();
			java.io.BufferedInputStream br = null;
			java.io.ByteArrayOutputStream sink = null;
			int buffLen = 1024;
			byte[] buff = null;
			int readLen = 0;
			try {
				StatusCd = httpMethod.getStatusCode();
				Status = httpMethod.getStatusLine();
				
				//logger.info("httpMethod.getResponseBodyAsString()["+httpMethod.getResponseBodyAsString()+"]");
				
				br = new java.io.BufferedInputStream(httpMethod.getResponseBodyAsStream(), buffLen);
				sink = new java.io.ByteArrayOutputStream();
				buff = new byte[buffLen];
				
				while ( (readLen = br.read(buff)) != -1 ) {
					sink.write(buff, 0, readLen);
				}
				
				if(StringUtil.isEmpty(charset)){
					buffStr = new String(sink.toByteArray());
				}else{
					buffStr = new String(sink.toByteArray(), charset);
				}
				//outputStr = new StringBuffer(buffStr);
				outputStr.append(buffStr);
				
			} catch (java.io.IOException e) {
				e.printStackTrace();
			} finally {
				if(br != null){
					try {
						br.close();
					} catch (Exception e2) {
					}
				}
				if(sink != null){
					try {
						sink.close();
					} catch (Exception e2) {
					}
				}
			}
		}
	}
	
	public class TrustAllSsLSocketFactory implements ProtocolSocketFactory{

		private TrustManager[] getTrustManager(){
			return TRUST_ALL_CERTS;
		}
		
		private SocketFactory getSocketFactory() throws UnknownHostException {
			TrustManager[] trustAllCerts = getTrustManager();
			try {
				SSLContext sslContext = SSLContexts.custom().build();
				sslContext.init(null,  trustAllCerts, new SecureRandom());
				
				final SSLSocketFactory socketFactory = sslContext.getSocketFactory();
				HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
				return socketFactory;
			} catch (Exception e) {
				throw new UnknownHostException(e.getMessage());
			}
		}
		
		@Override
		public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
			return getSocketFactory().createSocket(host, port);
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException, UnknownHostException {
			return getSocketFactory().createSocket(host, port, clientHost, clientPort);
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return getSocketFactory().createSocket(host, port);
		}
		
	}

	private static void getHttpClient(){
		
	}
}
