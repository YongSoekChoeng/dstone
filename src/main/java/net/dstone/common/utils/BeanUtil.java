package net.dstone.common.utils;

import java.io.StringWriter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.ClassUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;

public class BeanUtil {
	
	private static LogUtil logger = new LogUtil(BeanUtil.class);
	
	public static int NAME_POLICY_NOTHING 		= 0;
	public static int NAME_POLICY_HUNGARIAN 	= 1;
	public static int NAME_POLICY_STATIC 		= 2;
	
	public static final Set<Class<?>> PRIMITIVE_NUMS;
	static{
		Set<Class<?>> numClazz = new HashSet<Class<?>>();
		numClazz.add(byte.class);
		numClazz.add(short.class);
		numClazz.add(int.class);
		numClazz.add(long.class);
		numClazz.add(double.class);
		numClazz.add(float.class);
		PRIMITIVE_NUMS = numClazz;
	}
	
	private static String getNameFromPolicy(String propertyName, int policy, String prefix){
		String memberName = propertyName;
		if( !StringUtil.isEmpty(propertyName) ){
			if( policy ==  NAME_POLICY_HUNGARIAN){
				memberName = StringUtil.getHungarianName(propertyName, prefix);
			}else if( policy ==  NAME_POLICY_HUNGARIAN){
				memberName = StringUtil.getStaticName(propertyName, prefix);
			}
		}
		return memberName;
	}
	
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
	public static void setParamToBean(HttpServletRequest req, Object bean) throws Exception {

		if (req.getParameterNames() != null) {
			Enumeration names = req.getParameterNames();
			while (names.hasMoreElements()) {
				String paramName = (String) names.nextElement();
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
	public static Map setParameterToMap(HttpServletRequest req, Map map) throws Exception {
		map.putAll(req.getParameterMap());
		return map;
	}

	/**
	 * Bean객체를 Map타입으로 변환합니다.<br />
	 * 
	 * @param bean
	 *            Map타입으로변환하고자하는 Bean객체
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes" })
	public static Map bindBeanToMap(Object bean) throws Exception {
		return BeanUtils.describe(bean);
	}

	/**
	 * Map을 Bean객체로 변환합니다.<br />
	 * 
	 * @param map Bean객체로변환하고자하는 Map
	 * @param clazz Bean
	 * @return 변환된 Map
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes" })
	public static Object bindMapToBean(Map map, Class clazz) throws Exception {
		Object bean = clazz.newInstance();
		BeanUtils.populate(bean, map);
		return bean;
	}
	
	/**
	 * 리스트에있는 Bean객체를 Map으로 변환합니다.<br />
	 * 
	 * @param list
	 *            Map으로변환하고자하는 List<Bean>객체
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
	 * 
	 * @param map
	 *            객체
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
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
				if (val != null) {
					upkey = key.toUpperCase();
					lowkey = key.toLowerCase();
					prop.put(upkey, val);
					prop.put(lowkey, val);
				}
			}
			java.util.Enumeration en = prop.keys();
			while (en.hasMoreElements()) {
				key = (String) en.nextElement();
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
	 * @param targetBean
	 *            복사하고자하는 Bean
	 * @param sourceBean
	 *            원본 Bean
	 * @return 복사하고자하는 Bean
	 * @throws Exception
	 */
	public static Object copyBean(Object targetBean, Object sourceBean) throws Exception {
		try {
			java.lang.reflect.Field[] targetFields = targetBean.getClass().getDeclaredFields();
			java.lang.reflect.Field targetField = null;
			java.lang.reflect.Field[] sourceFields = sourceBean.getClass().getDeclaredFields();
			java.lang.reflect.Field sourceField = null;

			if (targetFields != null && sourceFields != null) {
				for (int i = 0; i < targetFields.length; i++) {
					targetField = targetFields[i];

					for (int k = 0; k < sourceFields.length; k++) {
						sourceField = sourceFields[k];
						targetField.setAccessible(true);
						sourceField.setAccessible(true);
						if (targetField.getName().equals(sourceField.getName())) {
							valueMapper(targetBean, targetField, sourceBean, sourceField);
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return targetBean;
	}

	/**
	 * Bean을 복사합니다. <br />
	 * Bean데이터가 공백문자 인경우는 제외합니다.<br />
	 * 
	 * @param targetBean
	 *            복사하고자하는 Bean
	 * @param sourceBean
	 *            원본 Bean
	 * @param overwriteYn
	 *            복사하고자하는 Bean 에 값이 있을경우 덮어쓰기여부
	 * @return 복사하고자하는 Bean
	 * @throws Exception
	 */
	public static Object copyBean(Object targetBean, Object sourceBean, boolean overwriteYn) throws Exception {
		try {
			if (targetBean != null && sourceBean != null) {

				java.lang.reflect.Field[] targetFields = targetBean.getClass().getDeclaredFields();
				java.lang.reflect.Field targetField = null;
				java.lang.reflect.Field[] sourceFields = sourceBean.getClass().getDeclaredFields();
				java.lang.reflect.Field sourceField = null;
				if (targetFields != null && sourceFields != null) {
					for (int i = 0; i < targetFields.length; i++) {
						targetField = targetFields[i];

						for (int k = 0; k < sourceFields.length; k++) {
							sourceField = sourceFields[k];
							targetField.setAccessible(true);
							sourceField.setAccessible(true);
							if (targetField.getName().equals(sourceField.getName())) {
								if (overwriteYn) {
									valueMapper(targetBean, targetField, sourceBean, sourceField);
								} else {
									if (StringUtil.isEmpty(targetField.get(targetBean))) {
										valueMapper(targetBean, targetField, sourceBean, sourceField);
									}
								}
							}
						}
					}
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return targetBean;
	}

	private static void valueMapper(Object targetBean, java.lang.reflect.Field targetField, Object sourceBean, java.lang.reflect.Field sourceField) {
		try {

			if (sourceField.get(sourceBean) == null || "NULL".equals(sourceField.get(sourceBean))) {
				targetField.set(targetBean, null);
			} else {
				targetField.set(targetBean, sourceField.get(sourceBean));
			}

		} catch (Exception e) {
			//e.printStackTrace();
		}

	}

	/**
	 * Object를 복사하는 메소드
	 * 
	 * @param obj
	 * @return FWObject
	 */
	public static Object copyObj(Object obj) {
		java.io.ByteArrayInputStream bais = null;
		java.io.ObjectInputStream ois = null;
		java.io.ByteArrayOutputStream baos = null;
		java.io.ObjectOutputStream oos = null;
		byte[] buff = null;
		Object returnObj = null;
		String strErrMsg = "";

		try {
			if(obj != null){
				baos = new java.io.ByteArrayOutputStream(100);
				oos = new java.io.ObjectOutputStream(baos);

				oos.writeObject(obj);
				buff = baos.toByteArray();

				bais = new java.io.ByteArrayInputStream(buff);
				ois = new java.io.ObjectInputStream(bais);

				returnObj = ois.readObject();
			}
		} catch (java.io.NotSerializableException e) {
			strErrMsg = "copyObj ( " + obj.getClass().getName() + " ) 에서 객체복제중 예외발생. 복제될수 없는 객체를 복제하려고 시도하였습니다. 세부사항:" + e.toString();
			logger.error(strErrMsg);
		} catch (Exception e) {
			strErrMsg = "copyObj ( " + obj.getClass().getName() + " ) 에서 객체복제중 예외발생.세부사항:" + e.toString();
			logger.error(strErrMsg);
		} finally {
			if (baos != null)
				try {
					baos.close();
				} catch (Exception e) {
				}
			if (oos != null)
				try {
					oos.close();
				} catch (Exception e) {
				}
			if (bais != null)
				try {
					bais.close();
				} catch (Exception e) {
				}
			if (ois != null)
				try {
					ois.close();
				} catch (Exception e) {
				}
		}
		return returnObj;
	}

	/**
	 * bean객체에 값을 세팅합니다.<br />
	 * 
	 * @param clazz
	 *            Bean
	 * @param String
	 *            property
	 * @param Object
	 *            value
	 * @return 변환된 Map
	 * 
	 * @throws Exception
	 */
	public static Object setProperty(Object bean, String property, Object value) throws Exception {
		
//		BeanUtils.setProperty(bean, property, value);
		
		try {
			java.lang.reflect.Field field = bean.getClass().getDeclaredField(property);
			field.setAccessible(true);
			
			if(PRIMITIVE_NUMS.contains(field.getType()) && (value !=null && isAtomic(value.getClass())) ){
				if( field.getType() == byte.class ){
					field.set(bean, Byte.valueOf( (String)value).byteValue());
				}else if( field.getType() == short.class ){
					field.set(bean, Short.valueOf( (String)value).shortValue());
				}else if( field.getType() == int.class ){
					field.set(bean, Integer.valueOf( (String)value).intValue());
				}else if( field.getType() == long.class ){
					field.set(bean, Long.valueOf( (String)value).longValue());
				}else if( field.getType() == double.class ){
					field.set(bean, Double.valueOf( (String)value).doubleValue());
				}else if( field.getType() == float.class ){
					field.set(bean, Float.valueOf( (String)value).floatValue());
				}
			}else{
				field.set(bean, value);
			}
		} catch (Exception e) {
			BeanUtils.setProperty(bean, property, value);
		}
		
		return bean;
	}
	
	/**
	 * bean객체에 값을 반환합니다.<br />
	 * 
	 * @param clazz
	 *            Bean
	 * @param String
	 *            property
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	public static Object setObject(Object bean, String property, Object value) throws Exception {
		try {
			java.lang.reflect.Field member = bean.getClass().getDeclaredField(property);
			member.setAccessible(true);
			member.set(bean, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	/**
	 * bean객체의 멤버명리스트를 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int namePolicy
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	public static String[] getPropertyList(Object bean, int namePolicy, String prefix) throws Exception {
		String[] propertyList = null;
		//java.lang.reflect.Field[] targetFields = bean.getClass().getDeclaredFields();
		java.lang.reflect.Field[] targetFields = bean.getClass().getFields();
		java.lang.reflect.Field targetField = null;
		try {
			targetFields = bean.getClass().getDeclaredFields();
			if(targetFields != null){
				propertyList = new String[targetFields.length];
				for(int i=0; i<targetFields.length; i++){
					targetField = targetFields[i];
					propertyList[i] = getNameFromPolicy(targetField.getName(), namePolicy, prefix);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return propertyList;
	}
	
	/**
	 * bean객체의 정보리스트를 반환합니다.(static제외)<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int namePolicy
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	public static LinkedList<HashMap<String, String>> getPropertyInfoList(Object bean, boolean isStaticInclude) throws Exception {
		
		LinkedList<HashMap<String, String>> propertyInfoList = new LinkedList<HashMap<String, String>>();
		java.lang.reflect.Field[] targetFields = null;
		java.lang.reflect.Field targetField = null;
		try {
			targetFields = bean.getClass().getDeclaredFields();
			if(targetFields != null){
				for(int i=0; i<targetFields.length; i++){
					targetField = targetFields[i];
					if( !isStaticInclude && java.lang.reflect.Modifier.isStatic(targetField.getModifiers())){
						continue;
					}
					propertyInfoList.add(getPropertyInfo(bean, targetField.getName()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return propertyInfoList;		
	}
	
	/**
	 * bean객체의 정보리스트를 반환합니다.(static제외)<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int namePolicy
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	public static LinkedList<HashMap<String, String>> getPropertyInfoList(Object bean, boolean isStaticInclude, int namePolicy, String prefix) throws Exception {
		
		LinkedList<HashMap<String, String>> propertyInfoList = new LinkedList<HashMap<String, String>>();
		java.lang.reflect.Field[] targetFields = null;
		java.lang.reflect.Field targetField = null;
		try {
			targetFields = bean.getClass().getDeclaredFields();
			if(targetFields != null){
				for(int i=0; i<targetFields.length; i++){
					targetField = targetFields[i];
					if( !isStaticInclude && java.lang.reflect.Modifier.isStatic(targetField.getModifiers())){
						continue;
					}
					propertyInfoList.add(getPropertyInfo(bean, targetField.getName(), namePolicy, prefix));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return propertyInfoList;		
	}

	/**
	 * bean객체의 정보를 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int namePolicy
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	public static HashMap<String, String> getPropertyInfo(Object bean, String name) throws Exception {
		return getPropertyInfo(bean, name, -1, ""); 
	}
	
	/**
	 * bean객체의 정보를 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int namePolicy
	 * @return 반환값
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static HashMap<String, String> getPropertyInfo(Object bean, String name, int namePolicy, String prefix) throws Exception {
		HashMap<String, String>propertyInfo = null;
		java.lang.reflect.Field targetField = null;
		
		try {
			propertyInfo = new HashMap<String, String>();
			targetField = bean.getClass().getDeclaredField(name);
			if( targetField != null ){
				if(namePolicy > -1){
					propertyInfo.put("NAME", getNameFromPolicy(targetField.getName(), namePolicy, prefix));
				}else{
					propertyInfo.put("NAME", targetField.getName());
				}
				propertyInfo.put("IS_ARRAY", String.valueOf(BeanUtil.isArray(targetField)));
				if(BeanUtil.isArray(targetField)){
					propertyInfo.put("CLASS", targetField.getType().getComponentType().getName());
					propertyInfo.put("IS_NUMBER", String.valueOf(isNumber(targetField.getType().getComponentType())) );
					propertyInfo.put("IS_ARRAY_ATOMIC", String.valueOf(BeanUtil.isAtomic(targetField.getType().getComponentType())));
				}else{
					propertyInfo.put("CLASS", targetField.getType().getName());
					propertyInfo.put("IS_NUMBER", String.valueOf(isNumber(targetField.getType())) );
					propertyInfo.put("IS_ARRAY_ATOMIC", String.valueOf(BeanUtil.isAtomic(targetField.getType())));
				}
				propertyInfo.put("IS_ATOMIC", String.valueOf(BeanUtil.isAtomic(targetField.getType())));
				if( List.class.isAssignableFrom(targetField.getType()) ){
					propertyInfo.put("IS_LIST", "true");
					Class clzz;
					try {
						clzz = ((Class)((ParameterizedType)targetField.getGenericType()).getActualTypeArguments()[0]);
						propertyInfo.put("IS_LIST_ATOMIC", String.valueOf(BeanUtil.isAtomic(clzz)));
						propertyInfo.put("LIST_COMP_CLASS", clzz.getName());
					} catch (Exception e) {
						propertyInfo.put("IS_LIST", "false");
						propertyInfo.put("IS_LIST_ATOMIC", "false");
						propertyInfo.put("LIST_COMP_CLASS", "");
					}
				}else{
					propertyInfo.put("IS_LIST", "false");
					propertyInfo.put("IS_LIST_ATOMIC", "false");
					propertyInfo.put("LIST_COMP_CLASS", "");
				}
				
			}
		} catch (Exception e) {
			logger.info( bean.getClass()+ "의 ["+name+"]필드정보 수행중 예외발생.");
			e.printStackTrace();
		}
		return propertyInfo;
	}
	
	
	/**
	 * bean객체에 값을 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @return 반환값
	 * @throws Exception
	 */
	public static Object getProperty(Object bean, String property) {
		Object returnObj = null;
		try {
			returnObj = BeanUtils.getProperty(bean, property);
		} catch (Exception e) {
			try {
				java.lang.reflect.Field field = bean.getClass().getDeclaredField(property);
				field.setAccessible(true);
				returnObj = field.get(bean);
			} catch (Exception e1) {
				//e1.printStackTrace();
			}
		}
		return returnObj;
	}
	
	/**
	 * 클래스의 정보를 덤프합니다.(멤버가 Non-Atomic일 경우 재귀적으로 보여줌) <br />
	 * 
	 * @param className 클래스명
	 * @param orderBy 정렬순서. 0:정렬없음, 1:NAME(필드명), 2:VIRTUAL_GP(가상그룹)+ NAME(필드명)
	 * @throws Exception
	 */
	public static String checkBeanMember(String className, int orderBy) throws Exception {
		StringBuffer buff = new StringBuffer();
		ArrayList<String[]> lineList = getBeanMemberInfo(className, "", true);
		ArrayList<String> list = new ArrayList<String>();

		ArrayList<String> orderList = new ArrayList<String>();
		HashMap<String, String[]> lineMap = new HashMap<String, String[]>();
		
		String line = null;
		String[] words = null;

		String KEY = null;
		String TAB = null;
		String VIRTUAL_GP = null;
		String NAME = null;
		String CLASS = null;
		String KIND = null;
		String LOCAL_NAME = null;
		String PARENT_NAME = "";
		
		if(lineList != null){

			for(int i=0; i<lineList.size(); i++){
				words = lineList.get(i);
				if(words != null){
					/* LEVEL(레벨)|VIRTUAL_GP(가상그룹)|NAME(필드명)|CLASS(타입)|KIND(구조:단건,배열,리스트)|LOCAL_NAME(필드한글명)|PARENT_NAME(부모필드명) */
					VIRTUAL_GP 		= words[1].trim();
					NAME 				= words[2].trim();
					PARENT_NAME	= words[6].trim();
					if(orderBy == 1){
						KEY = "[" + NAME + "]";
					}else if(orderBy == 2){
						if(StringUtil.isEmpty(VIRTUAL_GP)){
							KEY = "[" + NAME + "]";
						}else{
							KEY = "[" + VIRTUAL_GP+"." + NAME + "]";
						}
					}else{
						if(StringUtil.isEmpty(PARENT_NAME)){
							KEY = "[" + NAME + "]";
						}else{
							KEY = "[" + PARENT_NAME+"." + NAME + "]";
						}
					}
					orderList.add(KEY);
					lineMap.put(KEY, words);
				}
			}

			if(orderBy != 0){
				java.util.Collections.sort(orderList);
			}
			
			for(int i=0; i<orderList.size(); i++){
				words = lineMap.get(orderList.get(i));
				if(words != null){
					
					KEY = null;
					TAB = null;
					VIRTUAL_GP = null;
					NAME = null;
					CLASS = null;
					KIND = null;
					LOCAL_NAME = null;
					PARENT_NAME = "";
					
					/* LEVEL(레벨)|VIRTUAL_GP(가상그룹)|NAME(필드명)|CLASS(타입)|KIND(구조:단건,배열,리스트)|LOCAL_NAME(필드한글명) */
					TAB 					= StringUtil.filler("", Integer.parseInt(words[0]), "\t") ;
					VIRTUAL_GP 		= words[1].trim();
					NAME 				= words[2].trim();
					CLASS 				= words[3].trim();
					KIND 				= words[4].trim();
					LOCAL_NAME	= words[5].trim();
					PARENT_NAME	= words[6].trim();
					
					//net.dstone.common.utils.LogUtil.sysout( "NAME["+NAME+"] LEVEL["+words[0]+"] PARENT_NAME["+PARENT_NAME+"] VIRTUAL_GP["+VIRTUAL_GP+"]" );					
					
					line = TAB;
					if(orderBy == 1){
						line = line +  NAME + " - 필드명["+LOCAL_NAME+"],  타입["+CLASS+"], 구조["+KIND+"], 가상그룹["+VIRTUAL_GP+"]" ;
					}else if(orderBy == 2){
						if( StringUtil.isEmpty(VIRTUAL_GP) &&  !StringUtil.isEmpty(PARENT_NAME) ){
							VIRTUAL_GP = PARENT_NAME;
						}
						if(StringUtil.isEmpty(VIRTUAL_GP)){
							line = line + NAME + " - 필드명["+LOCAL_NAME+"],  타입["+CLASS+"], 구조["+KIND+"]" ;
						}else{
							line = line + VIRTUAL_GP+"." + NAME + " - 필드명["+LOCAL_NAME+"],  타입["+CLASS+"], 구조["+KIND+"]" ;
						}
					}else{
						if( !StringUtil.isEmpty(VIRTUAL_GP) ){
							PARENT_NAME = VIRTUAL_GP;
						}
						if(StringUtil.isEmpty(PARENT_NAME)){
							line = line + NAME + " - 필드명["+LOCAL_NAME+"],  타입["+CLASS+"], 구조["+KIND+"]" ;
						}else{
							line = line + PARENT_NAME+"." + NAME + " - 필드명["+LOCAL_NAME+"],  타입["+CLASS+"], 구조["+KIND+"]" ;
						}
					}
					line = line + "\n";
					buff.append(line);
				}
			}
			
		}
		
		return buff.toString();
		
	}
	
	/**
	 * 클래스에 해당하는 멤버정보를 확인한다. 멤버정보내용은 다음과 같다.
	 * String[0] = 레벨
	 * String[1] = 가상그룹
	 * String[2] = 필드명
	 * String[3] = 타입
	 * String[4] = 구조(단건,배열,리스트)
	 * String[5] = 필드한글명
	 * @param className
	 * @param tab
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<String[]> getBeanMemberInfo(String className, String tab) throws Exception {
		return getBeanMemberInfo(className, tab, false);
	}
	
	/**
	 * 클래스에 해당하는 멤버정보를 확인한다. 멤버정보내용은 다음과 같다.
	 * String[0] = 레벨
	 * String[1] = 가상그룹
	 * String[2] = 필드명
	 * String[3] = 타입
	 * String[4] = 구조(단건,배열,리스트)
	 * String[5] = 필드한글명
	 * @param className
	 * @param tab
	 * @param recursiveYn
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<String[]> getBeanMemberInfo(String className, String tab, boolean recursiveYn) throws Exception {
		return getBeanMemberInfo(className, tab, "", recursiveYn);
	}
	
	/**
	 * 클래스에 해당하는 멤버정보를 확인한다. 멤버정보내용은 다음과 같다.
	 * String[0] = 레벨
	 * String[1] = 가상그룹
	 * String[2] = 필드명
	 * String[3] = 타입
	 * String[4] = 구조(단건,배열,리스트)
	 * String[5] = 필드한글명
	 * @param className
	 * @param tab
	 * @param parentName
	 * @param recursiveYn
	 * @return
	 * @throws Exception
	 */
	private static ArrayList<String[]> getBeanMemberInfo(String className, String tab, String parentName, boolean recursiveYn) throws Exception {	
		ArrayList<String[]> lineList = new ArrayList<String[]>();
		ArrayList<String[]> subLineList = null;
		String[] lineArray = new String[7];	/* LEVEL(레벨)|VIRTUAL_GP(가상그룹)|NAME(필드명)|CLASS(타입)|KIND(구조:단건,배열,리스트)|LOCAL_NAME(필드한글명)|PARENT_NAME(부모필드명) */
		String newTab = "\t" + tab;
		Object bean = null;
		String[] propertyList = null;
		HashMap<String, String> propertyInfo = null;
		boolean isRecursiveNeeded = false;
		String subClassName = "";
		String[] childLineArray = null;

		String LEVEL = null;
		String VIRTUAL_GP = null;
		String NAME = null;
		String CLASS = null;
		String KIND = null;
		String LOCAL_NAME = null;
		
		try {
			bean = Class.forName(className).newInstance();
			propertyList = getPropertyList(bean, NAME_POLICY_NOTHING, "");
			
			for(int i=0; i<propertyList.length; i++){
				propertyInfo = getPropertyInfo(bean, propertyList[i]);
				lineArray = new String[7];
				
				isRecursiveNeeded = false;
				subClassName = "";
				
				LEVEL = String.valueOf(tab.length());
				VIRTUAL_GP = propertyInfo.get("VIRTUAL_GP");
				NAME = propertyInfo.get("NAME");
				CLASS = "";
				KIND = "";
				LOCAL_NAME = propertyInfo.get("LOCAL_NAME");
				// ARRAY
				if(propertyInfo.get("IS_ARRAY").equals("true")){
					// ATOMIC
					if(propertyInfo.get("IS_ARRAY_ATOMIC").equals("true")){
						CLASS = propertyInfo.get("CLASS");
						KIND = "배열";
					// COMPLEX
					}else{
						CLASS = propertyInfo.get("CLASS");
						KIND = "배열";
						isRecursiveNeeded = true;
						subClassName = propertyInfo.get("CLASS");
					}
				// LIST
				}else if(propertyInfo.get("IS_LIST").equals("true")){
					// ATOMIC
					if(propertyInfo.get("IS_LIST_ATOMIC").equals("true")){
						CLASS = propertyInfo.get("LIST_COMP_CLASS");
						KIND = "리스트";
					// COMPLEX
					}else{
						CLASS = propertyInfo.get("LIST_COMP_CLASS");
						KIND = "리스트";
						isRecursiveNeeded = true;
						subClassName = propertyInfo.get("LIST_COMP_CLASS");
					}
				// NON - ARRAY
				}else{
					// ATOMIC
					if(propertyInfo.get("IS_ATOMIC").equals("true")){
						CLASS = propertyInfo.get("CLASS");
						KIND = "단건";
					// COMPLEX
					}else{
						CLASS = propertyInfo.get("CLASS");
						KIND = "단건";
						isRecursiveNeeded = true;
						subClassName = propertyInfo.get("CLASS");
					}
				}
				
				if( !StringUtil.isEmpty(VIRTUAL_GP) ){
					LEVEL = String.valueOf(tab.length()+1);
				}
				
				lineArray[0] = LEVEL;
				lineArray[1] = VIRTUAL_GP;
				lineArray[2] = NAME;
				lineArray[3] = CLASS;
				lineArray[4] = KIND;
				lineArray[5] = LOCAL_NAME;
				lineArray[6] = parentName; // PARENT_NAME 은 부모에서 세팅. 
				
				lineList.add(lineArray);
				
				if(recursiveYn){
					if(isRecursiveNeeded){
						subLineList = getBeanMemberInfo(subClassName, newTab, NAME, recursiveYn);
						if(subLineList != null){
							for(int k=0; k<subLineList.size(); k++){
								childLineArray = subLineList.get(k);
								lineList.add(childLineArray);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return lineList;
	}
	
	/**
	 * bean객체의 배열값을 String배열형식으로 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @return 반환값
	 * @throws Exception
	 */
	public static String[] getArrayProperty(Object bean, String property) throws Exception {
		String[] returnObj = null;
		try {
			returnObj = BeanUtils.getArrayProperty(bean, property);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnObj;
	}
	/**
	 * bean객체에 값을 반환합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @return 반환값
	 * @throws Exception
	 */
	public static Object getObject(Object bean, String property) throws Exception {
		Object retVal = null;
		try {
			java.lang.reflect.Field member = bean.getClass().getDeclaredField(property);
			member.setAccessible(true);
			retVal = member.get(bean );
		} catch (Exception e) {
			e.printStackTrace();
		}
		return retVal;
	}
	
	/**
	 * bean객체에에서 property의 getter/setter명을 반환합니다. delCharArray의 문자열이 있을경우 이를 무시하고 비교진행합니다.<br />
	 * 
	 * @param clazz Bean
	 * @param String property
	 * @param int gubun (1:getter, 2:setter )
	 * @param boolean isCasSensitive
	 * @param String[] delCharArray
	 * @return 반환값
	 * @throws Exception
	 */
	public static String getGetterSetterName(Object bean, String property, int gubun, boolean isCasSensitive, String[] delCharArray) throws Exception {
		String feildName = property;
		String setterName = null;
		java.lang.reflect.Method[] methods = null;
		java.lang.reflect.Method method = null;
		String kind = "";
		boolean isFound = false;
		try {
			if(gubun == 1){
				kind = "get";
			}else if(gubun == 2){
				kind = "set";
			}else{
				throw new Exception("구분값을 확인하세요.");
			}
			methods = bean.getClass().getDeclaredMethods();
			if( methods != null ){
				for(int i=0; i<methods.length; i++){
					method = methods[i];
					setterName = method.getName();
					setterName = StringUtil.replace(setterName, kind, "");	
					if(delCharArray != null){
						for(int k=0; k<delCharArray.length; k++){
							setterName = StringUtil.replace(setterName, delCharArray[k], "");	
							feildName = StringUtil.replace(feildName, delCharArray[k], "");	
						}
					}
					if(isCasSensitive){
						if( setterName.equals(feildName) ){
							setterName = method.getName();
							isFound = true;
						}
					}else{
						feildName = feildName.toUpperCase();
						setterName = setterName.toUpperCase();
						if( setterName.equals(feildName.toUpperCase()) ){
							setterName = method.getName();
							isFound = true;
						}
					}
					if(isFound){
						break;
					}
				}
			}
			if( !isFound ){
				setterName = "";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return setterName;
	}
	

	/**
	 * clazz에 alias에 해당하는 멤버변수가 있는지 확인합니다.<br />
	 * 
	 * @param String
	 *            clazz
	 * @param String
	 *            alias
	 * @return 멤버변수가 있는지 여부
	 * @throws Exception
	 */
	public static boolean isBeanMemberName(String clazz, String alias) {
		boolean isMember = false;
		try {
			Class.forName(clazz).getDeclaredField(alias);
			isMember = true;
		} catch (Exception e) {
			logger.info("["+clazz+"].["+alias+"]는 해당Bean 멤버가 아니라서 SKIP합니다.");
		}
		return isMember;
	}
	/**
	 * clazz에 property에 해당하는 멤버가 배열인지 확인합니다.<br />
	 * @param bean
	 * @param property
	 * @return
	 */
	public static boolean isBeanMemberArray(Object bean, String property){
		boolean isArray = false;
		try {
			java.lang.reflect.Field member = bean.getClass().getDeclaredField(property);
			isArray = member.getType().isArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isArray;
	}

	/**
	 * clazz에 property에 해당하는 멤버가 단순타입인지(내부멤버를 가지는 Complex 클래스이거나 사용자제작 클래스가 아닌지) 확인합니다.<br />
	 * @param bean
	 * @param property
	 * @return
	 */
	public static boolean isBeanMemberAtomic(Object bean, String property){
		boolean isAtomic = false;
		try {
			java.lang.reflect.Field member = bean.getClass().getDeclaredField(property);
			if( member.getType().isArray() ){
				isAtomic = isAtomic(member.getType().getComponentType());
			}else {
				isAtomic = isAtomic(member.getType());
			}			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isAtomic;
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean isAtomic(Class clzz){
		boolean isAtomic = false;
		if( clzz.isPrimitive() ){
			isAtomic = true;
		}else if( clzz == Character.class ){
			isAtomic = true;
		}else if( clzz == Byte.class ){
			isAtomic = true;
		}else if( clzz == String.class ){
			isAtomic = true;
		}else if( clzz == Float.class ){
			isAtomic = true;
		}else if( clzz == Integer.class ){
			isAtomic = true;
		}else if( clzz == Long.class ){
			isAtomic = true;
		}else if( clzz == Double.class ){
			isAtomic = true;
		}else if( clzz == java.math.BigDecimal.class ){
			isAtomic = true;
		}else if( clzz == java.util.Date.class ){
			isAtomic = true;
		}else if( clzz.getName().startsWith("java.lang.") ){
			isAtomic = true;
		}
		return isAtomic;
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean isAtomicArray(Class clzz){
		boolean isAtomicArray = false;
		if(isArray(clzz)){
			if( isArray(clzz.getComponentType())){
				isAtomicArray = true;
			}
		}
		return isAtomicArray;
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean isArray(Class clzz){
		return clzz.isArray();
	}
	public static boolean isArray(java.lang.reflect.Field member){
		boolean isArray = false;
		if(member != null){
			isArray = member.getType().isArray();
		}
		return isArray;
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean isNumber(Class clzz){ 
		boolean isNumber = false;
		if(Number.class.isAssignableFrom(clzz) || PRIMITIVE_NUMS.contains(clzz)){
			isNumber = true;
		}
		return isNumber;
	}
	

	public static boolean isNumber(String clzzNm)throws Exception{
		boolean isNumber = false;
		try {
			isNumber = isNumber(ClassUtils.getClass(clzzNm));
		} catch (Exception e) {
			
		}
		return isNumber;
	}
	
	/**
	 * clazz에 property에 해당하는 멤버가 단순타입인지(내부멤버를 가지는 Complex 클래스이거나 사용자제작 클래스가 아닌지) 확인합니다.<br />
	 * @param bean
	 * @param property
	 * @return
	 */
	public static String getBeanMemberStrType(Object bean, String property){
		String strType = "";
		try {
			java.lang.reflect.Field member = bean.getClass().getDeclaredField(property);
			if( member.getType().isArray() ){
				strType = "Array";
			}else if( member.getType().isPrimitive() ){
				// strType - byte, short, int, long, char, float, double, boolean, void 
				strType = member.getType().getName();
			}else if( member.getType() == Byte.class ){
				strType = "Byte";
			}else if( member.getType() == String.class ){
				strType = "String";
			}else if( member.getType() == Float.class ){
				strType = "Float";
			}else if( member.getType() == Integer.class ){
				strType = "Integer";
			}else if( member.getType() == Long.class ){
				strType = "Long";
			}else if( member.getType() == Double.class ){
				strType = "Double";
			}else if( member.getType() == java.math.BigDecimal.class ){
				strType = "BigDecimal";
			}else if( member.getType() == java.util.Date.class ){
				strType = "Date";
			}else{
				strType = member.getType().getName();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return strType;
	}
	
	/**
	 * 객체가 단순타입인지(내부멤버를 가지는 Complex 클래스이거나 사용자제작 클래스가 아닌지) 확인합니다.<br />
	 * @param bean
	 * @param property
	 * @return
	 */
	public static boolean isObjectAtomic(Object property){
		boolean isAtomic = false;
		try {
			if(property != null){
				if( property.getClass().isArray() ){
					isAtomic = false;
				}else if( property.getClass().isPrimitive() ){
					isAtomic = true;
				}else if( property.getClass() == Byte.class ){
					isAtomic = true;
				}else if( property.getClass() == Character.class ){
					isAtomic = true;
				}else if( property.getClass() == String.class ){
					isAtomic = true;
				}else if( property.getClass() == Float.class ){
					isAtomic = true;
				}else if( property.getClass() == Integer.class ){
					isAtomic = true;
				}else if( property.getClass() == Long.class ){
					isAtomic = true;
				}else if( property.getClass() == Double.class ){
					isAtomic = true;
				}else if( property.getClass() == java.math.BigInteger.class ){
					isAtomic = true;
				}else if( property.getClass() == java.math.BigDecimal.class ){
					isAtomic = true;
				}else if( property.getClass() == java.util.Date.class ){
					isAtomic = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isAtomic;
	}

	/**
	 * bean객체를 XML로 변환합니다.<br />
	 * 
	 * @param bean
	 * @return XML로 변환된 스트링
	 */
	public static String toXml(Object bean) throws Exception {
		String xml = "";
		StringWriter sw = new StringWriter();

		try {
			javax.xml.bind.JAXBContext context = javax.xml.bind.JAXBContext.newInstance(new Class[] { bean.getClass() });
			javax.xml.bind.Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_SCHEMA_LOCATION, "" );
			
			marshaller.marshal( bean , sw);
			sw.flush();
			xml = sw.getBuffer().toString();
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return xml;
	}

	/**
	 * XML을 bean객체로 변환합니다.<br />
	 * 
	 * @param XML스트링
	 * @return Object Bean
	 */
	@SuppressWarnings("rawtypes")
	public static Object fromXml(String xml, Class clzz, String rootTagName) throws Exception {
		java.io.StringReader reader = null;
		Object bean = null;
		
		try {		
			javax.xml.bind.JAXBContext context = javax.xml.bind.JAXBContext.newInstance(new Class[] { clzz });
			javax.xml.bind.Unmarshaller unmarshaller = context.createUnmarshaller();
			reader = new java.io.StringReader(xml);
			
			bean = unmarshaller.unmarshal(reader);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	
	/**
	 * bean객체를 JSON 스트링으로 변환합니다.<br />
	 * 
	 * @param Object Bean
	 * @return JSON 스트링으로 변환된 스트링
	 */
	public static String toJson(Object bean) {
		String json = "";
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			
			mapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
			mapper.configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true);
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			json = mapper.writeValueAsString(bean);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return json;
	}
	
	/**
	 * bean객체를 JSON 스트링으로 변환합니다.<br />
	 * 
	 * @param Object Bean
	 * @param String property JSON 스트링으로 변환하고자 하는 필드명
	 * @return JSON 스트링으로 변환된 스트링
	 */
	public static String toJson(Object bean, String property) {
		Object beanProperty = null;
		String json = "";
		try {
			beanProperty = BeanUtil.getObject(bean, property);
			if(beanProperty != null){
				json = toJson(beanProperty);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return json;
	}

	/**
	 * JSON 스트링을 bean객체로 변환합니다.<br />
	 * 
	 * @param JSON객체
	 * @return  Object Bean(JSON객체를 맵핑할 클래스)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object fromJson(String json, Class clzz) {
		Object bean = null;

		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			mapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
			bean = mapper.readValue(json, clzz);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	

	
	/**
	 * JSON 스트링을 bean객체로 변환합니다.<br />
	 * 
	 * @param JSON객체
	 * @param JSON객체의 하위 노드명
	 * @return  Object Bean(JSON객체의 하위 노드를 맵핑할 클래스)
	 */
	@SuppressWarnings({ "rawtypes" })
	public static Object fromJson(String json, String nodeName, Class clzz) {
		Object bean = null;
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			com.fasterxml.jackson.databind.JsonNode treeNode = mapper.readTree(json);
			com.fasterxml.jackson.databind.JsonNode childNode = treeNode.get(nodeName);
			if( childNode != null ){
				if( childNode.isArray() ){
					mapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
					mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
					mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
					bean =  mapper.readValue(childNode.toString(), mapper.getTypeFactory().constructArrayType(clzz));
				}else{
					bean = fromJson(childNode.toString(), clzz);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	/**
	 * JSON 스트링을 Map객체로 변환합니다.<br />
	 * 
	 * @param JSON객체
	 * @return  Object Bean(JSON객체를 맵핑할 클래스)
	 */
	@SuppressWarnings("unchecked")
	public static java.util.Map<String, Object> fromJsonToMap(String json) {
		java.util.Map<String, Object> bean = null;
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			mapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
			bean = (java.util.Map<String, Object>)mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}
	/**
	 * JSON 스트링을 Map객체로 변환합니다.<br />
	 * 
	 * @param JSON객체
	 * @param JSON객체의 하위 노드명
	 * @return  Object Bean(JSON객체의 하위 노드를 맵핑할 클래스)
	 */
	@SuppressWarnings("unchecked")
	public static java.util.Map<String, Object> fromJsonToMap(String json, String nodeName) {
		java.util.Map<String, Object> bean = null;
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			com.fasterxml.jackson.databind.JsonNode treeNode = mapper.readTree(json);
			com.fasterxml.jackson.databind.JsonNode childNode = treeNode.get(nodeName);
			if( childNode != null ){
				if( childNode.isArray() ){
					mapper.setPropertyNamingStrategy(new JsonPropertyNamingStrategy());
					mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
					mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
					bean = (java.util.Map<String, Object>)mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){});
				}else{
					bean = fromJsonToMap(childNode.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bean;
	}

	public static String getRootDir() {
		String rootDirectory =  java.nio.file.Paths.get("").toAbsolutePath().toString();
		rootDirectory = StringUtil.replace(rootDirectory, "\\", "/");
		return rootDirectory;
	}
	
	public static java.util.HashMap<String, String> getCommentMap(String className, String projRoot){
		java.util.HashMap<String, String> commentMap = new java.util.HashMap<String, String>();
		try {
			String[] lines = FileUtil.readFileByLines(projRoot + "/WebContent/WEB-INF/src/" + StringUtil.replace(className, ".", "/")+ ".java" );
			String line = null;
			String[] words = null;
			String member = "";
			String comment = "";
			if( lines != null ){
				for(int i=0; i<lines.length; i++){
					line = lines[i];
					member = "";
					//comment = "";
					if( line.trim().startsWith("//") || line.trim().startsWith("/*") ){
						line = line.trim();
						line = StringUtil.replace(line, "/", "");
						line = StringUtil.replace(line, "*", "");
						comment = line;
					}else if( line.indexOf("private") != -1 && line.indexOf(";") != -1  && line.indexOf("(") == -1 ){
						line = line.trim();
						line = StringUtil.replace(line, "\t", " ");
						line = StringUtil.replace(line, ";", "");
						words = StringUtil.toStrArray(line, " ");
						if(words.length > 2){
							member = words[2];
							commentMap.put(member, comment);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return commentMap;
	}

	@SuppressWarnings("serial")
	static class JsonPropertyNamingStrategy extends PropertyNamingStrategy{

		@Override
		public String nameForConstructorParameter(MapperConfig<?> arg0, AnnotatedParameter arg1, String arg2) {
			return super.nameForConstructorParameter(arg0, arg1, arg2);
		}
		
		@Override
		public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
			return field.getName();
		}

		@Override
		public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
			return convert(method, defaultName);
		}

		@Override
		public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
			return convert(method, defaultName);
		}
		
		public String convert(AnnotatedMethod method, String defaultName){
			Class<?> clazz = method.getDeclaringClass();
			java.lang.reflect.Field[] targetFields = null;
			java.lang.reflect.Field targetField = null;
			try {
				targetFields = clazz.getDeclaredFields();
				if(targetFields != null){
					for(int i=0; i<targetFields.length; i++){
						targetField = targetFields[i];
						if(targetField.getName().equalsIgnoreCase(defaultName)){
							defaultName = targetField.getName();
							break;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return defaultName;
		}

	}
}
