package net.dstone.common.utils;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

/**
 * Web Client Util
 * 웹호출을 담당한다.
 * @author Default
 */
public class WcUtil {
	
	public static int HTTP_OK 			= 200;

	public static String CONT_TYPE_HTML = "text/html";
	public static String CONT_TYPE_FORM = "application/x-www-form-urlencoded";
	public static String CONT_TYPE_XML 	= "text/xml";
	public static String CONT_TYPE_JSON = "application/json";

	private static LogUtil logger = new LogUtil(WcUtil.class);
	
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
	public String charset = "UTF-8";
	public ResponseReader response;
	public int StatusCd = -1;
	
	public String execute(Bean tBean) {
		return execute(tBean, null);
	}

	public String execute(Bean tBean, String charset) {

		this.bean = tBean;
		this.response = new ResponseReader();
		try {
			if(!StringUtil.isEmpty(charset)){
				this.charset = charset;
			}
			
			if(StringUtil.isEmpty(bean.method)) {
				throw new Exception("Web 전송 메서드(method)를 세팅해 주세요. 현재 세팅된 메서드(method)["+bean.method+"]");
			}
			if(StringUtil.isEmpty(bean.contentType)) {
				throw new Exception("Web 전송 컨텐츠타입(contentType)을 세팅해 주세요. 현재 세팅된 컨텐츠타입(contentType)["+bean.contentType+"]");
			}

			// 작업처리		
			this.StatusCd = this.doExecute();

		} catch (java.io.IOException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this.response.outputStr.toString();
	}
	
	/**
	 * Populating HttpMethod with headers
	 */
	private List<Header> getHeaders() {
		List<Header> headers = new ArrayList<Header>();
		Iterator<String> headerKeys = bean.headers.keySet().iterator();
		if( headerKeys != null ) {
			while(headerKeys.hasNext()) {
				String key = headerKeys.next();
				String value = bean.headers.get(key);
				headers.add(new org.apache.http.message.BasicHeader(key, value));
			}
		}
		if(!bean.headers.containsKey("Content-Type")) {
			headers.add(new org.apache.http.message.BasicHeader("Content-Type", bean.getContentType()));
		}
		return headers;
	}	
	
	/**
	 * Populating Body
	 */
	private HttpRequestBase getBody()throws Exception {
		HttpRequestBase httpRequest 		= null;
		if(this.bean != null){
			
			// Bean.Body 값 세팅
			if(StringUtil.isEmpty(this.bean.body)){
				if( WcUtil.CONT_TYPE_XML.equals(this.bean.getContentType()) ){
					this.bean.body = this.bean.parameters.toXml();
				}else if( WcUtil.CONT_TYPE_JSON.equals(this.bean.getContentType()) ){
					this.bean.body = this.bean.parameters.toJson();
				}
			}
			
			if ("GET".equals(bean.method)) {
				// Body 없이 쿼리 스트링으로만 진행.
				httpRequest = new HttpGet(this.bean.url);
			}else if ("DELETE".equals(bean.method)) {
				// Body 없이 쿼리 스트링으로만 진행.
				httpRequest = new HttpDelete(this.bean.url);
			}else if ("POST".equals(bean.method) || "PUT".equals(bean.method)) {

				// XML/JSON 타입일 경우
				if( WcUtil.CONT_TYPE_XML.equals(this.bean.getContentType()) ||  WcUtil.CONT_TYPE_JSON.equals(this.bean.getContentType()) ){
					if ("POST".equals(bean.method)) {
						httpRequest = new HttpPost(this.bean.url);
						if(StringUtil.isEmpty(charset)){
							((HttpPost)httpRequest).setEntity(new StringEntity(this.bean.body));
						}else {
							((HttpPost)httpRequest).setEntity(new StringEntity(this.bean.body, this.charset));
						}
					}else {
						httpRequest = new HttpPut(this.bean.url);
						if(StringUtil.isEmpty(charset)){
							((HttpPut)httpRequest).setEntity(new StringEntity(this.bean.body));
						}else {
							((HttpPut)httpRequest).setEntity(new StringEntity(this.bean.body, this.charset));
						}
					}					
				// 나머지 HTML FORM 타입일 경우	
				}else {
					List<NameValuePair> paramList = new ArrayList<NameValuePair>();
					String value = null;
					for (String key : bean.parameters.getChildrenKeys()) {
						value = bean.parameters.getDatum(key);
						paramList.add(new BasicNameValuePair(key, value));
					}
					if ("POST".equals(bean.method)) {
						httpRequest = new HttpPost(this.bean.url);
						if(StringUtil.isEmpty(charset)){
							((HttpPost)httpRequest).setEntity(new UrlEncodedFormEntity(paramList));
						}else {
							((HttpPost)httpRequest).setEntity(new UrlEncodedFormEntity(paramList, this.charset));
						}
					}else {
						httpRequest = new HttpPut(this.bean.url);
						if(StringUtil.isEmpty(charset)){
							((HttpPut)httpRequest).setEntity(new UrlEncodedFormEntity(paramList));
						}else {
							((HttpPut)httpRequest).setEntity(new UrlEncodedFormEntity(paramList, this.charset));
						}
					}					
				}				
			}else{ 
				throw new Exception("지원하지 않는 메소드["+bean.method+"] 입니다.");
			}
		}
		return httpRequest;
	}	
	
	/**
	 * Executing HttpMethod.
	 */
	private int doExecute() {
		int statusCd = -1;
		HttpClientBuilder httpClientBuilder = null;
		CloseableHttpClient client			= null;
		HttpRequestBase httpRequest 		= null;
		HttpResponse httpResponse			= null;
		long sTime 							= System.currentTimeMillis();
		long eTime 							= 0;
		boolean debugInOutYn				= true;
		StringBuffer debugStr 				= new StringBuffer();
		
		try {
			
			// 0. HttpClientBuilder 생성.
			httpClientBuilder = HttpClients.custom();
			if( this.bean.url.toLowerCase().startsWith("https") ){
				SSLContext sslcontext = SSLContexts.custom().useProtocol("SSL").loadTrustMaterial(null, new TrustStrategy() {
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						return true;
					}
				}).build();
				httpClientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier()).setSslcontext(sslcontext);
			}
			
			// 1. Header 값 세팅
			httpClientBuilder.setDefaultHeaders(this.getHeaders());
			// 2. HttpClient 생성.
			client = httpClientBuilder.build();
			// 3. Body 값 세팅 및 호출.		
			httpRequest = this.getBody();
			// 4. Http 호출
			httpResponse = client.execute(httpRequest);
			// 5. 응답확인
			this.response.read(httpResponse);
			eTime = System.currentTimeMillis();

			if(debugInOutYn){
				debugStr.append("\n");
				debugStr.append("\n");
				debugStr.append("/************************************* WcUtil 호출 START **************************************************************/").append("\n");
				debugStr.append("URL ["+this.bean.url+"] Elapsed Time["+ Long.toString( ( eTime-sTime) ) +"] StatusCd["+ this.response.StatusCd +"]").append("\n");
				debugStr.append("/************************************* WcUtil 호출 END ****************************************************************/").append("\n");
				debugStr.append("\n");
				debugStr.append("\n");
//				debugStr.append("/************************************* WcUtil 호출결과 START **************************************************************/").append("\n");
//				debugStr.append(responseReader.outputStr.toString()).append("\n");
//				debugStr.append("/************************************* WcUtil 호출결과 END ****************************************************************/").append("\n");
//				debugStr.append("\n");
//				debugStr.append("\n");
				logger.info(debugStr.toString());
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			if( client != null ) {
				try {
					client.close();
				} catch (Exception e2) {
					// TODO: handle exception
				}
			}
			statusCd = this.response.StatusCd;
		}
		return statusCd;
	}
	
	/**
	 * This is a helper class holding HTTP packet data.
	 */
	public static class Bean {
		public String method = "GET";
		public String url = "";
		private String body = "";
		private String contentType = CONT_TYPE_HTML;
		
		private Map<String, String> headers = new HashMap<String, String>();
		private DataSet parameters = new DataSet();
		
		public String getContentType() {
			return contentType;
		}
		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public void addHeader(String key, String value) {
			if( StringUtil.isEmpty(this.contentType)){
				this.contentType = WcUtil.CONT_TYPE_HTML;
			}
			headers.put(key, value);
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
		public StringBuffer outputStr = new StringBuffer();
		public String buffStr = "";
		
		public void read(HttpResponse response) {
			StatusCd = response.getStatusLine().getStatusCode();
			try {
				if (StatusCd == HTTP_OK) {
					ResponseHandler<String> handler = new BasicResponseHandler();
					String body = handler.handleResponse(response);
					if(!StringUtil.isEmpty(body)) {
						outputStr.append(body);
					}
				} else {
					outputStr.append("Response is error : " + response.getStatusLine().getStatusCode());
					outputStr.append("\n").append("상세사항 : " + response);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
			}
		}
	}
}
