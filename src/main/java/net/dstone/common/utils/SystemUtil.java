package net.dstone.common.utils;

public class SystemUtil {
	
	private static SystemUtil systemInfo = null;
	private static java.util.Properties pro = new java.util.Properties();
	public static boolean SYSINFO_ENCODING_CONVERT = false;
	
	
	/**
	 * SystemUtil
	 */
	private SystemUtil() {}
	
	public static SystemUtil getInstance() {
		if (systemInfo == null) {
			systemInfo = new SystemUtil();
			initialize();
		}
		return systemInfo;
	}
	
	private static void initialize() {
		java.io.FileInputStream fin = null;
		try {
			java.util.Properties props = new java.util.Properties();
			String path = SystemUtil.class.getProtectionDomain().getCodeSource().getLocation().toString().substring(6);
			fin = new java.io.FileInputStream(new java.io.File(path + System.getProperty("file.separator") + "SYSTEMINFO.properties"));
			pro.load(fin);
			SystemUtil.SYSINFO_ENCODING_CONVERT = pro.getProperty("SYSINFO_ENCODING_CONVERT", "").trim().toUpperCase().equals("TRUE") ? true : false;
		} catch (Exception fn) {
			String sFileName = System.getProperty("user.home") + System.getProperty("file.separator") + "SYSTEMINFO.properties";
			try {
				fin = new java.io.FileInputStream(new java.io.File(sFileName));
				pro.load(fin);
				SystemUtil.SYSINFO_ENCODING_CONVERT = pro.getProperty("SYSINFO_ENCODING_CONVERT", "").trim().toUpperCase().equals("TRUE") ? true : false;
			} catch (Exception e) {
				System.out.println("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.initialize() 수행중 예외발생. 상세사항:" + e.toString());
			} finally {
				if(fin != null){
					try {
						fin.close();
						fin = null;
					} catch (Exception e) {}
				}
			}
		}  finally {
			if(fin != null){
				try {
					fin.close();
				} catch (Exception e) {}
			}
		}
	}
	
	public boolean getBoolProperty(String keyName) {
		String answer = "";
		try {
			answer = pro.getProperty(keyName, "");
			if (SYSINFO_ENCODING_CONVERT) {
				answer = DbUtil.fromDbToKor(answer);
			}
		} catch (Exception e) {
			System.out.println("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getBoolProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
		} finally {
			if (answer.trim().toUpperCase().equals("TRUE")) {
				return true;
			} else {
				return false;
			}
		}
	}
	
	public int getIntProperty(String keyName) {
		String answer = "";
		int iAnswer = 0;
		try {
			answer = pro.getProperty(keyName, "");
			if (SYSINFO_ENCODING_CONVERT) {
				answer = DbUtil.fromDbToKor(answer);
			}
			iAnswer = Integer.parseInt(answer);
		} catch (Exception e) {
			System.out.println("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getIntProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
		} finally {}
		return iAnswer;
	}
	
	public String getProperty(String keyName) {
		String answer = "";
		try {
			answer = pro.getProperty(keyName, "");
			if (SYSINFO_ENCODING_CONVERT) {
				answer = DbUtil.fromDbToKor(answer);
			}
		} catch (Exception e) {
			System.out.println("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
		} finally {}
		return answer;
	}
	public void reset() {
		initialize();
	}
	public java.util.Properties copy() {
		java.util.Properties copyProp = new java.util.Properties();
		copyProp = (java.util.Properties)this.pro.clone();
		return copyProp;
	}
	
	/**
	* <code>showAllSystemProperties</code> 시스템프로퍼티값을 확인하는 메소드
	*/
	public static void showAllSystemProperties() {
		java.util.Properties prop = java.lang.System.getProperties();
		java.util.Enumeration enum2 = prop.keys();

		String name = "";
		String value = "";
		while (enum2.hasMoreElements()) {
			name = (String) enum2.nextElement();
			value = prop.getProperty(name);
			System.out.println("[ " + name + " ]:" + value);
		}
	}

	/**
	* <code>showMemoryInfo</code> 설정프로퍼티값을 확인하는 메소드
	*/
	public static void showMemoryInfo() {
		Runtime rt = Runtime.getRuntime();
		long totalMem = rt.totalMemory();
		long freeMem = rt.freeMemory();
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();

		System.out.println("/********************* Memory Check 시작 **********************/");
		System.out.println("Total amount of memory 	 [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMem)), 20, " ") + " byte]");
		System.out.println("Amount of free memory 	 [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMem)), 20, " ") + " byte]");
		System.out.println("/********************* Memory Check 끝 ************************/");

	}

	/**
	* <code>getMemoryInfo</code> 설정프로퍼티값을 확인하는 메소드
	*/
	public static String getMemoryInfo(String div) {
		StringBuffer buff = new StringBuffer();
		Runtime rt = Runtime.getRuntime();
		long totalMem = rt.totalMemory();
		long freeMem = rt.freeMemory();
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();

		buff.append("Total amount of memory 	 [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMem)), 20, " ") + " byte]").append(div);
		buff.append("Amount of free memory 	 [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMem)), 20, " ") + " byte]").append(div);

		return buff.toString();

	}
	
	/**
	* <code>getAllSystemProperties</code> 시스템프로퍼티값을 확인하는 메소드
	*/
	public static String getAllSystemProperties(String div) {
		StringBuffer buff = new StringBuffer();
		java.util.Properties prop = java.lang.System.getProperties();
		java.util.Enumeration enum2 = prop.keys();

		String name = "";
		String value = "";
		while (enum2.hasMoreElements()) {
			name = (String) enum2.nextElement();
			value = prop.getProperty(name);
			buff.append("[ " + name + " ]:" + value).append(div);
		}
		return buff.toString();
	}
	
}
