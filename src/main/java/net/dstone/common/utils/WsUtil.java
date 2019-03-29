package net.dstone.common.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.TraceMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;

public class WsUtil {
	
	public static int HTTP_OK = 200;
	public static int HTTP_NOT_FOUND = 400;

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(WsUtil.class);
	
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
			
			if ("GET".equals(bean.method)) {
				httpMethod = new GetMethod(bean.url);
				addHeaders(httpMethod);
			} else if ("POST".equals(bean.method)) {
				httpMethod = new PostMethod(bean.url);
				addHeaders(httpMethod);
				addParams((PostMethod) httpMethod, charset);
				addTextBody(charset);
				addJsonBody(charset);
				RequestEntity re = new StringRequestEntity(bean.body, bean.contentType, charset);
				((PostMethod) httpMethod).setRequestEntity(re);
			} else if ("HEAD".equals(bean.method)) {
				httpMethod = new HeadMethod(bean.url);
				addHeaders(httpMethod);
			} else if ("PUT".equals(bean.method)) {
				httpMethod = new PutMethod(bean.url);
				addHeaders(httpMethod);
				addTextBody(charset);
				addJsonBody(charset);
				RequestEntity re = new StringRequestEntity(bean.body, bean.contentType, charset);
				((PutMethod) httpMethod).setRequestEntity(re);
			} else if ("DELETE".equals(bean.method)) {
				httpMethod = new DeleteMethod(bean.url);
				addHeaders(httpMethod);

			} else if ("TRACE".equals(bean.method)) {
				httpMethod = new TraceMethod(bean.url);
				addHeaders(httpMethod);

			} else if ("OPTIONS".equals(bean.method)) {
				httpMethod = new OptionsMethod(bean.url);
				addHeaders(httpMethod);

			} else {
				throw new RuntimeException("Method '" + bean.method + "' not implemented.");
			}
			
			doExecute(httpMethod, this.response);

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
	
	/**
	 * Populating PostMethod with parameters
	 */
	private void addParams(PostMethod postMethod, String charset) {
		for (String key : bean.parameters.keySet()) {
			Collection<String> values = (Collection<String>) bean.parameters.get(key);
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
			postMethod.setParameter(key, sb.toString());
			//postMethod.addParameter(key, sb.toString());
		}
	}

	/**
	 * Populating HttpMethod with headers
	 */
	private void addHeaders(HttpMethod httpMethod) {
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
			httpMethod.addRequestHeader(key, sb.toString());
		}
	}
	
	/**
	 * Populating Body
	 */
	private void addTextBody(String charset) {
		if( bean.bodyBuff.length() > 0 ){	
			if(StringUtil.isEmpty(charset)){
				bean.body = bean.body + bean.bodyBuff.toString();
			}else{
				bean.body = bean.body + StringUtil.encodingConv(bean.bodyBuff.toString(), charset);
			}
		}
	}

	/**
	 * Populating Body
	 */
	private void addJsonBody(String charset) {
		if( bean.bodyJsonMap.size() > 0 ){
			if(StringUtil.isEmpty(charset)){
				bean.body = bean.body + BeanUtil.toJson( bean.bodyJsonMap );
			}else{
				bean.body = bean.body + StringUtil.encodingConv(BeanUtil.toJson( bean.bodyJsonMap ), charset);
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
		HttpClient client = new HttpClient();
		long sTime = System.currentTimeMillis();
		long eTime = 0;
		boolean debugInOutYn					= true;
		StringBuffer debugStr = new StringBuffer();
		try {
			responseReader.outputStr = new StringBuffer();

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
		}
	}

	/**
	 * This is a helper class holding HTTP packet data.
	 */
	public static class Bean {
		public String method = "GET";
		public String url = "";
		public String body = "";
		public String contentType = null;
		
		private Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
		private Map<String, Collection<String>> parameters = new HashMap<String, Collection<String>>();
		private StringBuffer bodyBuff = new StringBuffer();
		private Map<String, Map<String, Object>> bodyJsonMap = new LinkedHashMap<String, Map<String, Object>>();

		public void addHeader(String key, String value) {
			List<String> valuesList = (List<String>) headers.get(key);
			if (valuesList == null) {
				valuesList = new ArrayList<String>();
			}
			valuesList.add(value);
			headers.put(key, valuesList);
		}

		public void addParam(String key, String value) {
			Collection<String> valuesList = (Collection<String>) parameters.get(key);
			if (valuesList == null) {
				valuesList = new ArrayList<String>();
			}
			valuesList.add(value);
			parameters.put(key, valuesList);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public void addJsonBody(String key, Object bean) {
			if( bean != null ){
				this.contentType = "application/json";
				Map<String, Object> valuesList = null;
				Map<String, Object> tempValuesList = null;
				if( bodyJsonMap.containsKey(key) ){
					valuesList = bodyJsonMap.get(key);
				}else{
					valuesList = new LinkedHashMap<String, Object>();
				}
				try {
					if( java.util.Map.class.isAssignableFrom(bean.getClass()) ){
						tempValuesList = (java.util.Map)bean;
					}else{
						tempValuesList = BeanUtil.bindBeanToMap(bean);
					}				
					if( tempValuesList != null ){
						valuesList.putAll(tempValuesList);
					}
					bodyJsonMap.put(key, valuesList);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		public void addTextBody(String text) {
			bodyBuff.append(text);
		}

		public String toString() {
			return "{method=" + method + ",url=" + url + ",headers=" + headers + ",parameters=" + parameters + ",bodyJsonMap=" + bodyJsonMap + "}";
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
				outputStr = new StringBuffer(buffStr);
				
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
}
