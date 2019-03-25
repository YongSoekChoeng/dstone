package net.dstone.common.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class WsUtil {

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
				addParams((PostMethod) httpMethod);
				RequestEntity re = new StringRequestEntity(bean.body, null, "UTF8");
				((PostMethod) httpMethod).setRequestEntity(re);

			} else if ("HEAD".equals(bean.method)) {
				httpMethod = new HeadMethod(bean.url);
				addHeaders(httpMethod);

			} else if ("PUT".equals(bean.method)) {
				httpMethod = new PutMethod(bean.url);
				addHeaders(httpMethod);

				RequestEntity re = new StringRequestEntity(bean.body, null, "UTF8");
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
	private void addParams(PostMethod postMethod) {
		for (String key : bean.parameters.keySet()) {
			Collection<String> values = (Collection<String>) bean.parameters.get(key);
			StringBuilder sb = new StringBuilder();
			int cnt = 0;
			for (String val : values) {
				if (cnt != 0) {
					sb.append(",");
				}
				sb.append(val);
				cnt++;
			}
			postMethod.setParameter(key, sb.toString());
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
	 * Executing HttpMethod.
	 */
	private void doExecute(HttpMethod httpMethod, ResponseReader responseReader) {
		HttpClient client = new HttpClient();
		try {
			client.executeMethod(httpMethod);

			responseReader.read(httpMethod);

		} catch (Exception e) {
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
		private Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
		private Map<String, Collection<String>> parameters = new HashMap<String, Collection<String>>();

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

		public String toString() {
			return "{method=" + method + ",url=" + url + ",headers=" + headers + ",parameters=" + parameters + "}";
		}
	}

	/**
	 * This interface is being hooked to the execution template method and it is
	 * being invoked on response read.
	 */
	public class ResponseReader {
		public StatusLine Status = null;
		public StringBuilder outputStr = new StringBuilder();
		public String charset = "";
		
		public void read(HttpMethod httpMethod) {
			outputStr = new StringBuilder();
			java.io.BufferedInputStream br = null;
			int buffLen = 1024;
			byte[] buff = new byte[buffLen];
			try {
				Status = httpMethod.getStatusLine();
				System.out.println(Status.getStatusCode());
				
				br = new java.io.BufferedInputStream(httpMethod.getResponseBodyAsStream(), buffLen);
				while (br.read(buff) != -1) {
					if(StringUtil.isEmpty(charset)){
						outputStr.append(new String(buff));
					}else{
						outputStr.append(new String(buff, charset));
					}
				}
			} catch (java.io.IOException e) {
				e.printStackTrace();
			} finally {
				if(br != null){
					try {
						br.close();
					} catch (Exception e2) {
					}
				}
			}
		}
		
	}

}
