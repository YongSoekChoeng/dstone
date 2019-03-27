package net.dstone.common.biz;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Controller;



@Controller
public abstract class BaseController extends net.dstone.common.spring.mvc.BaseObject {

	public static String RETURN_SUCCESS = "0";
	public static String RETURN_FAIL 	= "1"; 
	
	private static boolean IS_JSON_CHARSET_CONVERT = false; 
	
	protected void debug(Object o) {
		super.debug(o);
	}

	protected String nullCheck(Object o) {
		return net.dstone.common.utils.StringUtil.nullCheck(o, "");
	}

	/**
	 * 리퀘스트의 값을 빈객체에 세팅하여 반환 .(단건용)
	 * @param request
	 * @param bean
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Object bindSingleValue(net.dstone.common.utils.RequestUtil request, Object bean) {
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
					}else{
						fieldValue = request.getParameter(fieldName);
					}
					BeanUtils.setProperty(bean, fieldName, fieldValue);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return bean;
	}
	
	/**
	 * 리퀘스트의 Json값을 빈객체에 세팅하여 반환 .(단건용)
	 * @param request
	 * @param bean
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Object bindSingleJsonObj(net.dstone.common.utils.RequestUtil request, Object bean) {
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
						fieldValue = request.getJsonParameterValues(fieldName);
					}else{
						fieldValue = request.getJsonParameterValue(fieldName);
					}
					BeanUtils.setProperty(bean, fieldName, fieldValue);
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
	@SuppressWarnings("unchecked")
	protected Object bindMultiValues(net.dstone.common.utils.RequestUtil request, String beanName) {
		Object[] beanArray = null;
		Class<?> clz = null;
		Object bean = null;
		java.lang.reflect.Field[] fields = null;
		java.lang.reflect.Field field = null;
		java.util.Properties fieldProp = new java.util.Properties();
		java.util.HashMap<String, Object> map = null;
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
					map = new java.util.HashMap<String, Object>();
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
	 * 리퀘스트의 Json값을 빈객체에 세팅하여 반환 .(다건용)
	 * @param request
	 * @param beanName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Object bindMultiJsonObjs(net.dstone.common.utils.RequestUtil request, String beanName) {
		Object[] beanArray = null;
		Class<?> clz = null;
		Object bean = null;
		java.lang.reflect.Field[] fields = null;
		java.lang.reflect.Field field = null;
		java.util.Properties fieldProp = new java.util.Properties();
		java.util.HashMap<String, Object> map = null;
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
					paramValues = request.getJsonParameterValues(paramName);
					if(paramValues != null){
						fieldProp.put(paramName, paramValues);
						if(paramValues.length>maxArrayNum){
							maxArrayNum = paramValues.length;
						}
					}
				}
				for(int i=0; i<maxArrayNum; i++ ){
					map = new java.util.HashMap<String, Object>();
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
	 * 에러페이지 포워딩 메소드
	 * @param request
	 * @param response
	 * @param exception
	 */
	protected void handleException(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, Object exception){
		
	}

	/**
	 * 세션체크 메소드
	 * @param request
	 * @param response
	 * @param exception
	 */
	protected void loginCheck(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response){
		
	}
	
}
