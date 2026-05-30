package net.dstone.common.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

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
				net.dstone.common.utils.LogUtil.sysout("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.initialize() 수행중 예외발생. 상세사항:" + e.toString());
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
			net.dstone.common.utils.LogUtil.sysout("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getBoolProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
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
			net.dstone.common.utils.LogUtil.sysout("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getIntProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
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
			net.dstone.common.utils.LogUtil.sysout("<MESSAGE>[" + DateUtil.getToDate("yyyy'년 'MM'월 'dd'일 'HH'시'mm'분 'ss'초'") + "] " + "SystemUtil.getProperty('" + keyName + "') 수행중 예외발생. 상세사항:" + e.toString());
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
			net.dstone.common.utils.LogUtil.sysout("[ " + name + " ]:" + value);
		}
	}

	/**
	* <code>showMemoryInfo</code> 설정프로퍼티값을 확인하는 메소드
	*/
	public static void showMemoryInfo() {
		Runtime rt = Runtime.getRuntime();
		
		long totalMem = rt.totalMemory();
		long freeMem = rt.freeMemory();
		long usedMem = totalMem-freeMem;
		
		long totalMemMB = new Double(totalMem/(1024*1024)).longValue();
		long freeMemMB = new Double(freeMem/(1024*1024)).longValue();
		long usedMemMB = totalMemMB - freeMemMB;
				
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();

		net.dstone.common.utils.LogUtil.sysout("/********************* Memory Check 시작 **********************/");
		
		net.dstone.common.utils.LogUtil.sysout("Total amount of memory  [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMemMB)), 20, " ") + " Mb]");
		net.dstone.common.utils.LogUtil.sysout("Used amount of memory   [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(usedMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(usedMemMB)), 20, " ") + " Mb]");
		net.dstone.common.utils.LogUtil.sysout("Amount of free memory   [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMemMB)), 20, " ") + " Mb]");
		net.dstone.common.utils.LogUtil.sysout("/********************* Memory Check 끝 ************************/");

	}

	/**
	* <code>getMemoryInfo</code> 설정프로퍼티값을 확인하는 메소드
	*/
	public static String getMemoryInfo(String div) {
		StringBuffer buff = new StringBuffer();
		Runtime rt = Runtime.getRuntime();
		
		long totalMem = rt.totalMemory();
		long freeMem = rt.freeMemory();
		long usedMem = totalMem-freeMem;
		
		long totalMemMB = new Double(totalMem/(1024*1024)).longValue();
		long freeMemMB = new Double(freeMem/(1024*1024)).longValue();
		long usedMemMB = totalMemMB - freeMemMB;
				
		java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
		
		buff.append("Total amount of memory  [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(totalMemMB)), 20, " ") + " Mb]").append(div);
		buff.append("Used amount of memory   [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(usedMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(usedMemMB)), 20, " ") + " Mb]").append(div);
		buff.append("Amount of free memory   [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMem)), 20, " ") + " byte] [ " + net.dstone.common.utils.StringUtil.filler(String.valueOf(nf.format(freeMemMB)), 20, " ") + " Mb]").append(div);

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
	
	/**
	* <code>getSystemProperty</code> 시스템프로퍼티값을 확인하는 메소드
	*/
	public static String getSystemProperty(String name) {
		return System.getProperty(name, "");
	}
	
	/**
	 * 컴맨드라인 시스템명령 실행 메서드<br>
	 * 예)<br>
	 * String[] cli = new String[]{"svn", "log", "http://220.95.212.225:6080/scm/repo/scmadmin/hen/hen", "--search", "dh.shin", "-v", "-r", "BASIC:HEAD"};<br>
	 * String output = net.dstone.common.utils.SystemUtil.executeCli(cli, "EUC-KR");<br>
	 * System.out.println("output:" + output);<br>
	 * 
	 * @param cli
	 * @param charSet
	 * @return
	 */
	public static String executeCli(String[] cli, String charSet) {

		InputStream stdIn = null;
		InputStreamReader isr = null;
		BufferedReader br = null;

		StringBuffer output = new StringBuffer();

		try {

			ProcessBuilder pb = new ProcessBuilder();
			pb.command(cli);
			Process proc = pb.start();

			stdIn = proc.getInputStream();
			isr = new InputStreamReader(stdIn, charSet);
			br = new BufferedReader(isr);

			String line = null;

			while ((line = br.readLine()) != null) {
				output.append(line).append("\n");
			}

			proc.waitFor();

		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			try {
				if (stdIn != null) {
					stdIn.close();
				}
				if (isr != null) {
					isr.close();
				}
				if (br != null) {
					br.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return output.toString();
	}
	
	/**
	 * 컴맨드라인 시스템명령 실행 메서드. <br>
	 * 파라메터에 개행문자등이 들어갈 경우 별도로 처리할 필요 있음.
	 * 예)<br>
	 * List<String> command = Arrays.asList("svn");
	 * String[] args = new String[]{"log", "http://220.95.212.225:6080/scm/repo/scmadmin/hen/hen", "--search", "dh.shin", "-v", "-r", "BASIC:HEAD"};<br>
	 * String output = net.dstone.common.utils.SystemUtil.executeCli(command, args, "EUC-KR");<br>
	 * System.out.println("output:" + output);<br>
	 * 
	 * @param cli
	 * @param charSet
	 * @return
	 */
	public static String executeCli(List<String> command, String[] args, String charSet) {

		InputStream stdIn = null;
		InputStreamReader isr = null;
		BufferedReader br = null;

		StringBuffer output = new StringBuffer();

		try {

			ProcessBuilder pb = new ProcessBuilder(command);
			Process proc = pb.start();
			
	        // 프로세스의 표준 입력 스트림을 가져와 데이터 전송
			if(args != null) {
				try (OutputStream stdin = proc.getOutputStream()) {
					for(String arg : args) {
			            stdin.write(arg.getBytes(charSet));
			        }
				}
			}

			stdIn = proc.getInputStream();
			isr = new InputStreamReader(stdIn, charSet);
			br = new BufferedReader(isr);

			String line = null;

			while ((line = br.readLine()) != null) {
				output.append(line).append("\n");
			}

			proc.waitFor();

		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			try {
				if (stdIn != null) {
					stdIn.close();
				}
				if (isr != null) {
					isr.close();
				}
				if (br != null) {
					br.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return output.toString();
	}
}
