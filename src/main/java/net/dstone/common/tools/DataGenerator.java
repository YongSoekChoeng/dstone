package net.dstone.common.tools;

import io.github.benas.jpopulator.api.Populator;
import io.github.benas.jpopulator.impl.PopulatorImpl;




public class DataGenerator {
	
	public static Populator populator = new PopulatorImpl();
	
	private static void debug(Object msg){
		System.out.println(msg);
	}
	
	public static Object genRandomData(String className){
		int size = (new java.util.Random().nextInt())%10;
		if(size == 0){
			size = 5;
		}else if(size < 0){
			size = size*-1;
		}
		return genRandomData(className, size);
	}
	
	public static Object[] genRandomArrayData(String className){
		int size = (new java.util.Random().nextInt())%10;
		if(size == 0){
			size = 5;
		}else if(size < 0){
			size = size*-1;
		}
		Object[] objList = new Object[size];
		for(int i=0; i<objList.length; i++){
			objList[i] = genRandomData(className, (new java.util.Random().nextInt())%10);
		}
		return objList;
	}
	
	public static Object genRandomData(String className, int arrayNum){
		Class cl = null;
		Object obj = null;
		try {
			cl = Class.forName(className);
			obj = populator.populateBean(cl);
			
			java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
			java.lang.reflect.Field field = null;
			if(fields != null){
				Class childCl = null; 
				for(int i=0; i<fields.length; i++){
					field = fields[i];
					field.setAccessible(true);
					if(field.getType().isArray()){
						fillArrayTypeData(obj, field, arrayNum);
					}else if( field.getType().isAssignableFrom( java.util.List.class ) ){
						fillArrayTypeData(obj, field, arrayNum);
					}
					field.setAccessible(false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	private static Object fillArrayTypeData(Object parentObj, java.lang.reflect.Field field, int arrayNum){
		Class childCl = null;
		try {
			field.setAccessible(true);
			//debug( "Name["+ field.getType().getName() +"]");
			
			// ARRAY 일 경우
			if(field.getType().isArray()){
				childCl = field.getType().getComponentType();
				java.util.Vector v = new java.util.Vector();
				for(int k=0; k<arrayNum; k++){
					v.add(populator.populateBean(childCl));
				}
				Object[] objList = (Object[])java.lang.reflect.Array.newInstance(childCl, arrayNum);
				v.copyInto(objList);
				field.set( parentObj, objList );
				v.clear(); v=null;
			// 	java.util.List 일 경우
			}else if( field.getType().isAssignableFrom( java.util.List.class ) ){
				if ( field.getGenericType() instanceof java.lang.reflect.ParameterizedType ) { 
					java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType)field.getGenericType();
					if( pt.getActualTypeArguments() != null && pt.getActualTypeArguments().length > 0){
						childCl = (Class)(pt.getActualTypeArguments()[0]);
						java.util.List objList = new java.util.ArrayList();
						for(int k=0; k<arrayNum; k++){
							objList.add(populator.populateBean(childCl));
						}
						field.set( parentObj, objList );
					}
				}						
			}
			field.setAccessible(false);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return parentObj;
	}
	
}
