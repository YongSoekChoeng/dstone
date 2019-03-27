package net.dstone.common.utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.FrameworkServlet;

public class BeanUtil {

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(BeanUtil.class);

	/**
	 * 파라미터 정보를 읽어서 Bean에 저장합니다. <br />
	 * 
	 * @param req
	 *            파라메터정보
	 * @param bean
	 *            파라미터 값을 저장할 Bean
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static void setParamToBean(HttpServletRequest req, Object bean)
			throws Exception {
		
		if(req.getParameterNames() != null) {
			Enumeration names = req.getParameterNames();
			while (names.hasMoreElements()) {
		        String paramName = (String)names.nextElement();
		        BeanUtils.setProperty(bean, paramName, req.getParameter(paramName));
	        }
		}
	}
	/**
	 * 파라미터 정보를 읽어서 Map에 저장합니다. <br />
	 * 
	 * @param req
	 *            파라메터정보
	 * @param bean
	 *            파라미터 값을 저장할 Map
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map setParameterToMap(HttpServletRequest req, Map map)
			throws Exception {
		map.putAll(req.getParameterMap());
		return map;
	}

	/**
	 * Bean객체를 Map타입으로 변환합니다.<br />
	 * @param bean Map타입으로변환하고자하는 Bean객체
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes" })
	public static Map bindBeanToMap(Object bean) throws Exception {
		return BeanUtils.describe(bean);
	}
	

	/**
	 * Map을 Bean객체로 변환합니다.<br />
	 * @param map Bean객체로변환하고자하는 Map
	 * @param clazz Bean
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object bindMapToBean(Map map, Class clazz) throws Exception {
		Iterator<String> iterator = map.keySet().iterator();
		Object bean = clazz.newInstance();
		
		while (iterator.hasNext()) {
			String key = iterator.next();
			if (map.get(key) instanceof String) {
				BeanUtils.setProperty(bean, key, map.get(key));
			} else if (map.get(key) instanceof List) {
				
			}
		}
		return bean;
	}
	/**
	 * Map을 Bean객체로 변환합니다.<br />
	 * @param map Bean객체로변환하고자하는 Map
	 * @param clazz Bean
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object bindMapToBean(Map map, Class clazz, boolean ignoreCase) throws Exception {
		Object returnObj = null;
		Map copiMap = (Map)net.dstone.common.utils.BeanUtil.copyObj(map);
		if(ignoreCase){
			mapKeyIgnoreCase(copiMap);
		}
		returnObj = bindMapToBean(copiMap, clazz);
		return returnObj;
	}
	
	/**
	 * Map을 Bean객체로 변환합니다.<br />
	 * 서브맵이 존재할경우, 그서브맵의 데이터도 Bean으로 변환합니다
	 * @param map
	 * @param clazz
	 * @param subclazz
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object bindMapToBean(Map map, Class clazz, Class subclazz) throws Exception {

		Iterator<String> iterator = map.keySet().iterator();
		Object bean = clazz.newInstance();
		
		while (iterator.hasNext()) {
			String key = iterator.next();
			if(map.get(key) != null) {
					
				if (map.get(key) instanceof List) {
					
					List sublist = (List) map.get(key);
					List list = new ArrayList();
					for(int i = 0; i < sublist.size(); i++) {
						Map submap = (Map) sublist.get(i);
						
						Iterator<String> subiterator = submap.keySet().iterator();
						Object subbean = subclazz.newInstance();
						while (subiterator.hasNext()) {
							String subkey = subiterator.next();
							BeanUtils.setProperty(subbean, subkey, submap.get(subkey));
						}
						
						list.add(subbean);
					}
					
					BeanUtils.setProperty(bean, key, list);
				} else {
					BeanUtils.setProperty(bean, key, map.get(key));
				}
			}
		}

		return bean;
	}
	/**
	 * Map을 Bean객체로 변환합니다.<br />
	 * 서브맵이 존재할경우, 그서브맵의 데이터도 Bean으로 변환합니다
	 * @param map
	 * @param clazz
	 * @param subclazz
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object bindMapToBean(Map map, Class clazz, Class subclazz, boolean ignoreCase) throws Exception {
		Object returnObj = null;
		Map copiMap = (Map)net.dstone.common.utils.BeanUtil.copyObj(map);
		if(ignoreCase){
			mapKeyIgnoreCase(copiMap);
		}
		returnObj = bindMapToBean(copiMap, clazz, subclazz);
		return returnObj;
	}
	
	
	/**
	 * 리스트에있는 Bean객체를 Map으로 변환합니다.<br />
	 * @param list Map으로변환하고자하는 List<Bean>객체
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List bindListBeanToMap(List list) throws Exception {
		List beanToMap = new ArrayList();
		for (int idx = 0; idx < list.size(); idx++) {
			beanToMap.add(bindBeanToMap(list.get(idx)));
		}
		return beanToMap;
	}
	/**
	 * Map의 KEY값이 대소문자 구분없이 조회될 수 있도록 하기 위해 KEY값을 대/소문자로 복제.<br />
	 * @param map 객체
	 * @throws Exception
	 */
	public static Map mapKeyIgnoreCase(Map map) throws Exception {
		Iterator<String> iterator = map.keySet().iterator();
		java.util.Properties prop = new java.util.Properties();
		String key = "";
		Object val = null;
		String upkey = "";
		String lowkey = "";
		try {
			while (iterator.hasNext()) {
				key = iterator.next();
				val = map.get(key);
				if(val != null){
					upkey = key.toUpperCase();
					lowkey = key.toLowerCase();
					prop.put(upkey, val);
					prop.put(lowkey, val);
				}
			}
			java.util.Enumeration en = prop.keys();
			while (en.hasMoreElements()) {
				key = (String)en.nextElement();
				map.put(key, prop.get(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return map;
	}
	
	/**
	 * Bean을 복사합니다. <br /> 
	 * Bean데이터가 공백문자 인경우는 제외합니다.<br />
	 * 
	 * @param targetBean 복사하고자하는 Bean
	 * @param sourceBean 원본 Bean
	 * @return 복사하고자하는 Bean
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object copyBean(Object targetBean, Object sourceBean) throws Exception {
		try {
			java.lang.reflect.Field[] targetFields = targetBean.getClass().getDeclaredFields();
			java.lang.reflect.Field targetField = null;
			java.lang.reflect.Field[] sourceFields = sourceBean.getClass().getDeclaredFields();
			java.lang.reflect.Field sourceField = null;
			boolean isMember = false;
			boolean isArray = false;
			
			if(targetFields != null && sourceFields != null ){
				for(int i=0; i<targetFields.length; i++){
					isMember = false;
					isArray = false;
					targetField = targetFields[i];
					
					for(int k=0; k<sourceFields.length; k++){
						sourceField = sourceFields[k];
						targetField.setAccessible(true);
						sourceField.setAccessible(true);
						if(targetField.getName().equals(sourceField.getName())){
							valueMapper(targetBean, targetField, sourceBean, sourceField);
						}
					}
				}
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		return targetBean;
	}
	
	/**
	 * Bean을 복사합니다. <br /> 
	 * Bean데이터가 공백문자 인경우는 제외합니다.<br />
	 * 
	 * @param targetBean 복사하고자하는 Bean
	 * @param sourceBean 원본 Bean
	 * @param overwriteYn 복사하고자하는 Bean 에 값이 있을경우 덮어쓰기여부
	 * @return 복사하고자하는 Bean
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object copyBean(Object targetBean, Object sourceBean, boolean overwriteYn) throws Exception {
		try {
			if( targetBean != null && sourceBean != null ){

				java.lang.reflect.Field[] targetFields = targetBean.getClass().getDeclaredFields();
				java.lang.reflect.Field targetField = null;
				java.lang.reflect.Field[] sourceFields = sourceBean.getClass().getDeclaredFields();
				java.lang.reflect.Field sourceField = null;
				boolean isMember = false;
				boolean isArray = false;
				if(targetFields != null && sourceFields != null ){
					for(int i=0; i<targetFields.length; i++){
						isMember = false;
						isArray = false;
						targetField = targetFields[i];
						
						for(int k=0; k<sourceFields.length; k++){
							sourceField = sourceFields[k];
							targetField.setAccessible(true);
							sourceField.setAccessible(true);
							if(targetField.getName().equals(sourceField.getName())){
								if(overwriteYn){
									valueMapper(targetBean, targetField, sourceBean, sourceField);
								}else{
									if( net.dstone.common.utils.StringUtil.isEmpty(targetField.get(targetBean)) ){
										valueMapper(targetBean, targetField, sourceBean, sourceField);
									}
								}
							}
						}
					}
				}
				
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		return targetBean;
	}
	
	private static void valueMapper(Object targetBean, java.lang.reflect.Field targetField, Object sourceBean, java.lang.reflect.Field sourceField){
		try {
			
			if(sourceField.get(sourceBean) == null || "NULL".equals(sourceField.get(sourceBean))){
				targetField.set(targetBean, null);
			}else{
				targetField.set(targetBean, sourceField.get(sourceBean) );
			}
			
		} catch (Exception e) {
			//e.printStackTrace();
		}

	}
	

	/**
	 * Object를 복사하는 메소드
	 * @param obj
	 * @return FWObject
	 */
	public static Object copyObj(Object obj){
		java.io.ByteArrayInputStream bais = null;
		java.io.ObjectInputStream ois = null;
		java.io.ByteArrayOutputStream baos = null;
		java.io.ObjectOutputStream oos = null;
		byte[] buff = null;
		Object returnObj = null;
		String strErrMsg = "";

		try {
			baos = new java.io.ByteArrayOutputStream(100);
			oos = new java.io.ObjectOutputStream(baos);

			oos.writeObject(obj);
			buff = baos.toByteArray();

			bais = new java.io.ByteArrayInputStream(buff);
			ois = new java.io.ObjectInputStream(bais);

			returnObj = ois.readObject();
		} catch (java.io.NotSerializableException e) {
			strErrMsg = "copyObj ( " + obj.getClass().getName() + " ) 에서 객체복제중 예외발생. 복제될수 없는 객체를 복제하려고 시도하였습니다. 세부사항:" + e.toString();
			logger.debug(strErrMsg);
		} catch (Exception e) {
			strErrMsg = "copyObj ( " + obj.getClass().getName() + " ) 에서 객체복제중 예외발생.세부사항:" + e.toString();
			logger.debug(strErrMsg);
		} finally {
			if (baos != null)
				try {
					baos.close();
				} catch (Exception e) {}
			if (oos != null)
				try {
					oos.close();
				} catch (Exception e) {}
			if (bais != null)
				try {
					bais.close();
				} catch (Exception e) {}
			if (ois != null)
				try {
					ois.close();
				} catch (Exception e) {}
		}
		return returnObj;
	}
	
	public static Object getBean(ServletContext ctx, String servletName, String beanName){
		Object bean = null;
		try {
			WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext( ctx, FrameworkServlet.SERVLET_CONTEXT_PREFIX + servletName ); 
			bean = context.getBean(beanName);
		} catch (Exception e) {
			// TODO: handle exception
		}
		return bean;
	}
	
	/**
	 * bean객체에 값을 세팅합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param Object value
	 * @return 변환된 Map
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object setProperty(Object bean, String property, Object value ) throws Exception {
		BeanUtils.setProperty(bean, property, value);
		return bean;
	}
	
	/**
	 * clazz에 alias에 해당하는 멤버변수가 있는지 확인합니다.<br />
	 * @param String clazz
	 * @param String alias
	 * @return 멤버변수가 있는지 여부
	 * @throws Exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean isBeanMemberName(String clazz, String alias) {
		boolean isMember = false;
		try {
			java.lang.reflect.Field member = Class.forName(clazz).getDeclaredField(alias);
			isMember = true;
		} catch (Exception e) {
			// TODO: handle exception
		}
		return isMember;
	}
	
	/**
	 * bean객체를  XML로 변환합니다.<br />
	 * 
	 * @param Object Bean
	 * @return XML로 변환된 스트링
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static String toXml(Object bean){
		String xml = "";
		try {
			javax.xml.bind.JAXBContext context = javax.xml.bind.JAXBContext.newInstance(new Class[]{bean.getClass()});
			javax.xml.bind.Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_ENCODING, "UTF-8");  
			marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); 
			
			StringWriter sw = new StringWriter();
			
			marshaller.marshal(bean, sw);
			sw.flush();
			xml = sw.getBuffer().toString(); 
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return xml;
	}
	/**
	 * XML을  bean객체로 변환합니다.<br />
	 * 
	 * @param XML스트링
	 * @return Object Bean
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromXml(String xml, Class clzz){
		Object bean = null;
		java.io.StringReader reader = null;
		try {
			javax.xml.bind.JAXBContext context = javax.xml.bind.JAXBContext.newInstance(new Class[]{clzz});
			javax.xml.bind.Unmarshaller unmarshaller = context.createUnmarshaller();
			reader = new java.io.StringReader(xml);
			
			bean = unmarshaller.unmarshal(reader);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	/**
	 * bean객체를  JSON 스트링으로 변환합니다.<br />
	 * 
	 * @param Object Bean
	 * @return JSON 스트링으로 변환된 스트링
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static String toJson(Object bean){
		String json = "";
		try {
			org.codehaus.jackson.map.ObjectMapper mapper = new org.codehaus.jackson.map.ObjectMapper();
			json = mapper.writeValueAsString(bean);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return json;
	}
	
	/**
	 * JSON 스트링을  bean객체로 변환합니다.<br />
	 * 
	 * @param JSON 스트링
	 * @return Object Bean
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromJson(String json, Class clzz){
		Object bean = null;
		try {
			org.codehaus.jackson.map.ObjectMapper mapper = new org.codehaus.jackson.map.ObjectMapper();
			bean = mapper.readValue(json, clzz);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}

	
	
}
