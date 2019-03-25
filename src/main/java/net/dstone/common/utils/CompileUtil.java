package net.dstone.common.utils;

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;

public class CompileUtil {

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(CompileUtil.class);

	/**
	 * 인풋값으로 들어온 자바파일을 동적으로 컴파일 합니다. <br />
	 * @param srcFileName 소스파일
	 * @param classRoot 클래스가 저장될 루트
	 * @throws Exception
	 */
	public static void compile(String srcFileName, String classRoot) throws Exception {

		try {
			javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
			javax.tools.StandardJavaFileManager fileManager  = compiler.getStandardFileManager(null, null, null);
			
			fileManager.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, java.util.Arrays.asList(new java.io.File(classRoot)) );
			
			java.util.ArrayList<java.io.File> classPathList = new java.util.ArrayList<java.io.File>();
	        ClassLoader cl = ClassLoader.getSystemClassLoader();
	        java.net.URL[] urls = ((java.net.URLClassLoader)cl).getURLs();
	        
	        for(int i=0; i<urls.length; i++){
	        	classPathList.add(new java.io.File(urls[i].getFile()));
	        	//System.out.println("urls[i].getFile() ==>>>" + urls[i].getFile());
	        }
	        
			fileManager.setLocation(javax.tools.StandardLocation.CLASS_PATH, classPathList );
			Iterable options = java.util.Arrays.asList("-d", classRoot);
			
			compiler.getTask(
					null,
					fileManager,
					null,
					options,
					null,
					fileManager.getJavaFileObjectsFromFiles(java.util.Arrays.asList(new java.io.File(srcFileName)))
			).call();
		    fileManager.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
