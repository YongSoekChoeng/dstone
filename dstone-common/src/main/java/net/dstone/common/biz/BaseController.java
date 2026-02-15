package net.dstone.common.biz;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.utils.Base64Util;
import net.dstone.common.utils.RequestUtil;
import net.dstone.common.utils.StringUtil;

@Controller
public abstract class BaseController extends net.dstone.common.core.BaseObject {

	public static String RETURN_SUCCESS = "0";
	public static String RETURN_FAIL 	= "1";

	protected static String CALL_DIV_LINE 	= "********************************************************" ;
	protected static String DIV_LINE 		= "===========================" ; 

	protected String nullCheck(Object o) {
		return net.dstone.common.utils.StringUtil.nullCheck(o, "");
	}

	protected void logInput(String url, Object obj) {
		StringBuffer buff = new StringBuffer();
		buff.append("\n").append("\n").append("\n");
		buff.append("|| " +CALL_DIV_LINE+ " API CALL 시작 " +CALL_DIV_LINE+" ||").append("\n");
		buff.append("URL:").append(url).append("\n");
		if ( obj != null && obj.getClass() != byte[].class) {
			buff.append("|| " +DIV_LINE+ " INPUT START " +DIV_LINE+" ||").append("\n");
			buff.append(this.convertToJson(obj)).append("\n");
			buff.append("|| " +DIV_LINE+ " INPUT END " +DIV_LINE+" ||");
			buff.append("\n");
		}
		info(buff.toString());
	}
	
	protected void logInput(String url, Object header, Object body) {
		StringBuffer buff = new StringBuffer();
		buff.append("\n").append("\n").append("\n");
		buff.append("|| " +CALL_DIV_LINE+ " API CALL 시작 " +CALL_DIV_LINE+" ||").append("\n");
		buff.append("URL:").append(url).append("\n");
		buff.append("|| " +DIV_LINE+ " INPUT HEADER START " +DIV_LINE+" ||").append("\n");
		buff.append(this.convertToJson(header)).append("\n");
		buff.append("|| " +DIV_LINE+ " INPUT HEADER END " +DIV_LINE+" ||").append("\n");
		if ( body != null && body.getClass() != byte[].class) {
			buff.append("|| " +DIV_LINE+ " INPUT BODY START " +DIV_LINE+" ||").append("\n");
			buff.append(this.convertToJson(body)).append("\n");
			buff.append("|| " +DIV_LINE+ " INPUT BODY END " +DIV_LINE+" ||");
			buff.append("\n");
		}
		info(buff.toString());
	}
	
	protected void logOutput(Object header, Object body) {
		StringBuffer buff = new StringBuffer();
		buff.append("\n");
		buff.append("|| " +DIV_LINE+ " OUTPUT HEADER START " +DIV_LINE+" ||").append("\n");
		buff.append(this.convertToJson(header)).append("\n");
		buff.append("|| " +DIV_LINE+ " OUTPUT HEADER END " +DIV_LINE+" ||").append("\n");
		if ( body != null && body.getClass() != byte[].class) {
			buff.append("|| " +DIV_LINE+ " OUTPUT BODY START " +DIV_LINE+" ||").append("\n");
			buff.append(this.convertToJson(body)).append("\n");
			buff.append("|| " +DIV_LINE+ " OUTPUT BODY END " +DIV_LINE+" ||");
			buff.append("\n");
		}
		buff.append("|| " +CALL_DIV_LINE+ " API CALL 종료 " +CALL_DIV_LINE+" ||").append("\n");
		buff.append("\n").append("\n");
		info(buff.toString());
	}
	
	protected String convertToJson(Object param) {
		String jsonStr = "{}";
		ObjectMapper mapper = new ObjectMapper();
		try {
			jsonStr = mapper.writeValueAsString(param);
		} catch (Exception e) {
			//e.printStackTrace();
		}
		return jsonStr;
	}
	
	/**
	 * 리퀘스트의 값을 빈객체에 세팅하여 반환 .(단건용)
	 * @param request
	 * @param bean
	 * @return
	 */
	protected Object bindSingleValue(RequestUtil request, Object bean) {
		Class<?> clz = null;
		java.lang.reflect.Field[] fields = null;
		java.lang.reflect.Field field = null;
		String fieldName = "";
		Object fieldValue = null;
		boolean isArray = false;
		try {
			clz = bean.getClass(); 
			fields = clz.getDeclaredFields();
			if(fields != null){
				for(int i=0; i<fields.length; i++ ){
					field = fields[i];
					fieldName = field.getName();					
					isArray = bean.getClass().getDeclaredField(fieldName).getType().isArray();
					if(isArray){
						fieldValue = request.getParameterValues(fieldName);
						if( fieldValue != null ) {
							BeanUtils.setProperty(bean, fieldName, fieldValue);
						}
					}else{
						fieldValue = request.getParameter(fieldName);
						if(!StringUtil.isEmpty(fieldValue)) {
							BeanUtils.setProperty(bean, fieldName, fieldValue);
						}
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return bean;
	}
	
	/**
	 * 리퀘스트의 값을 빈객체에 세팅하여 반환 .(다건용)
	 * @param request
	 * @param beanName
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Object bindMultiValues(net.dstone.common.utils.RequestUtil request, String beanName) {
		Object[] beanArray = null;
		Class<?> clz = null;
		Object bean = null;
		java.lang.reflect.Field[] fields = null;
		java.lang.reflect.Field field = null;
		java.util.Properties fieldProp = new java.util.Properties();
		int maxArrayNum = 0;
		String paramName = "";
		String paramValue = "";
		String[] paramValues = null;
		java.util.Vector vec = new java.util.Vector(); 

		try {
			clz = Class.forName(beanName); 
			fields = clz.getDeclaredFields();

			if(fields != null){
				for(int i=0; i<fields.length; i++ ){
					field = fields[i];
					paramName = field.getName();
					paramValues = request.getParameterValues(paramName);
					if(paramValues != null){
						fieldProp.put(paramName, paramValues);
						if(paramValues.length>maxArrayNum){
							maxArrayNum = paramValues.length;
						}
					}
				}
				for(int i=0; i<maxArrayNum; i++ ){
					bean = clz.newInstance();
					for(int k=0; k<fields.length; k++ ){
						field = fields[k];
						paramName = field.getName();
						paramValues = (String[])fieldProp.get(paramName);
						if(paramValues != null && paramValues.length > i ){
							paramValue = paramValues[i];
							try {            
								clz.getMethod("set" + paramName, new Class[]{ paramValue.getClass()}).invoke(bean, paramValue);               
							} catch (Exception ex) {
								//ex.printStackTrace();
							}
						}else{
							paramValue = null;
						}
					}
					vec.add(bean);
				}	
				beanArray = (Object[])java.lang.reflect.Array.newInstance(clz, vec.size());
				vec.copyInto(beanArray);
				vec.clear(); 
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return beanArray;
	}
	
    /**
     * Proxy 에서 허용할 Header 값.
     */
    private static HashMap<String, String> PROXY_ALLOWED_HEADER_KEY_MAP = new HashMap<String, String>();
	static {
		/*
		PROXY_ALLOWED_HEADER_KEY_MAP.put("accept-encoding"			, "Accept-Encoding"			);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("eformsign_signature"		, "eformsign_signature"		);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("authorization"			, "Authorization"			);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("x-naver-client-id"		, "X-Naver-Client-Id"		);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("x-naver-client-secret"	, "X-Naver-Client-Secret"	);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("x-ncp-apigw-api-key"		, "x-ncp-apigw-api-key"		);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("x-ncp-apigw-api-key-id"	, "x-ncp-apigw-api-key-id"	);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("client-id"				, "client-id"				);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("client-secret"			, "client-secret"			);
		PROXY_ALLOWED_HEADER_KEY_MAP.put("productid"				, "productID"				);
		*/
	}

	/**
	 * RestTemplate 을 이용한 Proxy 기능 메소드
	 * @param request
	 * @param response
	 * @param exception
	 */
    @SuppressWarnings({ "rawtypes", "deprecation" })
	@RequestMapping(value = "/proxy.do") 
	protected ResponseEntity proxy(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws Exception{

    	info( this.getClass().getName() + ".proxy has been called !!!" );

    	RestTemplate restTemplate = null;
    	HttpEntity<?> requestEntity = null;
    	ResponseEntity<?> responseEntity = null;
       
        try {
        	String realUrl = request.getParameter("realUrl");
        	info( this.getClass().getName() + ".proxy realUrl["+realUrl+"]" );

			/* 1. Header 생성 */
			HttpHeaders httpHeaders = new HttpHeaders();
			java.util.Enumeration<String> reqHeaderKeys = request.getHeaderNames();
			while( reqHeaderKeys.hasMoreElements() ) {
				String hKey = reqHeaderKeys.nextElement();
				if( PROXY_ALLOWED_HEADER_KEY_MAP.size() == 0 ) {
					httpHeaders.add(hKey, request.getHeader(hKey));
				}else if( PROXY_ALLOWED_HEADER_KEY_MAP.containsKey(hKey) ) {
					httpHeaders.add(PROXY_ALLOWED_HEADER_KEY_MAP.get(hKey), request.getHeader(hKey));
				}
			}
			httpHeaders.setContentType(MediaType.valueOf(request.getContentType()));

			/* 2. Body 및 HttpEntity 생성 */
			// METHOD - POST, PUT 방식
			if( request.getMethod().toUpperCase().equals("POST") || request.getMethod().toUpperCase().equals("PUT") ) {
				// Multi Part 방식
				if( httpHeaders.getContentType().equals(MediaType.MULTIPART_FORM_DATA) ) {
					requestEntity = this.createMultipartHttpEntity((MultipartHttpServletRequest)request);
				// Json 방식	
				}else if( httpHeaders.getContentType().equals(MediaType.APPLICATION_JSON) ||  httpHeaders.getContentType().equals(MediaType.APPLICATION_JSON_UTF8) ) {
					String encoding = request.getCharacterEncoding();
					Charset charset = (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
					requestEntity = this.createJsonHttpEntity(request, charset);
				// Form	방식
				}else {
					String encoding = request.getCharacterEncoding();
					Charset charset = (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
					requestEntity = this.createHttpEntityFromRequest(request, charset);
				}
			// METHOD - 나머지 방식
			}else{
				requestEntity = new HttpEntity<String>("", httpHeaders);
			}
			
			/* 3. RestTemplate 호출 */
	    	restTemplate = net.dstone.common.utils.RestFulUtil.getInstance().getRestTemplate();

			this.logInput(realUrl, requestEntity.getHeaders(), requestEntity.getBody());
			
			responseEntity = restTemplate.exchange(
				realUrl
				, HttpMethod.valueOf(request.getMethod())
				, requestEntity
				, String.class
            );
			
			this.logOutput(responseEntity.getHeaders(), responseEntity.getBody());
			
        } catch (Exception e) {
            e.printStackTrace();
        }
 
		return responseEntity;
	}
    
    private HttpEntity<String> createHttpEntityFromRequest(HttpServletRequest request, Charset charset) throws IOException {
        // 1. 헤더 복사
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }

        // 2. Body 읽기
        String body = StreamUtils.copyToString(request.getInputStream(), charset);

        // 3. HttpEntity 생성
        return new HttpEntity<>(body, headers);
    }
    
    private HttpEntity<String> createJsonHttpEntity(HttpServletRequest request, Charset charset) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }

        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = StreamUtils.copyToString(request.getInputStream(), charset);
        return new HttpEntity<>(body, headers);
    }
    
    private HttpEntity<MultiValueMap<String, Object>> createMultipartHttpEntity(MultipartHttpServletRequest request) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 일반 파라미터
        request.getParameterMap().forEach((key, values) -> {
            for (String value : values) {
                body.add(key, value);
            }
        });

        // 파일 파라미터
        for (Map.Entry<String, MultipartFile> entry : request.getFileMap().entrySet()) {
            MultipartFile multipartFile = entry.getValue();
            body.add(entry.getKey(), new ByteArrayResource(multipartFile.getBytes()) {
                @Override
                public String getFilename() {
                    return multipartFile.getOriginalFilename();
                }
            });
        }
        return new HttpEntity<>(body, headers);
    }

	protected boolean isAjax(HttpServletRequest request) {
		boolean isAjax = false;
		if(RequestUtil.isAjax(request)) {
			isAjax = true;
		}
		return isAjax;
	}
	protected boolean isJson(HttpServletRequest request) {
		boolean isAjax = false;
		if(RequestUtil.isAjax(request)) {
			isAjax = true;
		}
		return isAjax;
	}

	protected void setForcedToUrl(HttpServletResponse response, String forcedToUrl) {
		response.setHeader("FORCED_TO_URL", forcedToUrl);
	}
	
	@SuppressWarnings("deprecation")
	public static void setErrCd(HttpServletRequest request, HttpServletResponse response, ErrCd errCd) {
		response.setHeader("successYn", "N");
		response.setHeader("errCd", errCd.getErrCd());
		try {
			if( RequestUtil.isAjax(request) ) {
				String encodedStr = errCd.getErrMsg();
				encodedStr = URLEncoder.encode(encodedStr, StandardCharsets.UTF_8.name()); 
				encodedStr = encodedStr.replaceAll("\\+", "%20");
				response.setHeader("errMsg", encodedStr);
			}else {			
				String encodedStr = errCd.getErrMsg();
				encodedStr = Base64Util.encodeString(encodedStr);
				response.setHeader("errMsg", encodedStr);
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}

	}
	
	@SuppressWarnings("deprecation")
	public static void setErrCd(HttpServletRequest request, HttpServletResponse response, String strErrCd) {
		response.setHeader("successYn", "N");
		ErrCd errCd = ErrCd.getErrCdByCd(strErrCd);
		if( errCd != null ) {
			response.setHeader("errCd", errCd.getErrCd());
			try {
				if( RequestUtil.isAjax(request) ) {
					String encodedStr = errCd.getErrMsg();
					encodedStr = URLEncoder.encode(encodedStr, StandardCharsets.UTF_8.name()); 
					encodedStr = encodedStr.replaceAll("\\+", "%20");
					response.setHeader("errMsg", encodedStr);
				}else {			
					String encodedStr = errCd.getErrMsg();
					encodedStr = Base64Util.encodeString(encodedStr);
					response.setHeader("errMsg", encodedStr);
				}
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
	}


	/**
	 * 기본링크 메소드(defaultLink)
	 * @param request
	 * @param response
	 * @param exception
	 */
    @RequestMapping(value = "/defaultLink.do") 
	protected ModelAndView defaultLink(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception{
    	/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		/************************ 변수 선언 끝 **************************/

		/************************ 변수 정의 시작 ************************/
		requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
		/************************ 변수 정의 끝 ************************/
		
		/************************ 컨트롤러 로직 시작 ************************/
		mav.setViewName(requestUtil.getParameter("defaultLink", ""));
		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
	}
	/**
	 * 기본링크 메소드(페이지경로)
	 * @param request
	 * @param response
	 * @param exception
	 */
	@RequestMapping("/views/{viewlevel1}")
	public String view1(@PathVariable String viewlevel1) {
		return viewlevel1;
	}
	@RequestMapping("/views/{viewlevel1}/{viewlevel2}")
	public String view2(@PathVariable String viewlevel1, @PathVariable String viewlevel2) {
		return viewlevel1 + "/" + viewlevel2;
	}
	@RequestMapping("/views/{viewlevel1}/{viewlevel2}/{viewlevel3}")
	public String view3(@PathVariable String viewlevel1, @PathVariable String viewlevel2, @PathVariable String viewlevel3) {
		return viewlevel1 + "/" + viewlevel2 + "/" + viewlevel3;
	}
	@RequestMapping("/views/{viewlevel1}/{viewlevel2}/{viewlevel3}/{viewlevel4}")
	public String view4(@PathVariable String viewlevel1, @PathVariable String viewlevel2, @PathVariable String viewlevel3, @PathVariable String viewlevel4) {
		return viewlevel1 + "/" + viewlevel2 + "/" + viewlevel3 + "/" + viewlevel4;
	}

	/**
	 * 브라우져 더미요청 처리
	 * 크롬, 사파리, 엣지 등 대부분의 브라우저는 사이트를 방문하면 자동으로 /favicon.ico 요청을 보냅니다.(특별히 설정하지 않아도 무조건 요청합니다.)
	 * 예전에는 단순히 404 Not Found 로 내려갔는데, Spring Boot 3.x부터는 NoResourceFoundException 을 발생시켜 로그에 찍히도록 바뀌었습니다.
	 */
    @RequestMapping("favicon.ico")
    @ResponseBody
    void favicon() {
        // 아무것도 반환하지 않음 -> 에러로그 발생 안 함
    }

}
