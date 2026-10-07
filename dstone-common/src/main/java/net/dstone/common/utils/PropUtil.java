package net.dstone.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class PropUtil {

	static PropUtil propUtil = null;

	java.util.Properties syspro = new java.util.Properties();
	java.util.Properties propDictionay = new java.util.Properties();

	private PropUtil(String rootDirectory) {
		init(rootDirectory);
	}

	public static PropUtil initialize(String rootDirectory) {
		if (propUtil == null) {
			propUtil = new PropUtil(rootDirectory);
		}
		return propUtil;
	}

	private void init(String rootDirectory) {
		String[] propFiles = net.dstone.common.utils.FileUtil.readFileListAll(rootDirectory);
		Properties props = new Properties();
		File propsFile = null;
		String propsName = "";
		net.dstone.common.utils.LogUtil.sysout( "||============================================ SYSTEM.KIND[" + StringUtil.nullCheck(System.getProperty("SYSTEM.KIND"), "local") + "] ============================================||" );

		if (propFiles != null) {
			
			for (int i = 0; i < propFiles.length; i++) {
				if (!propFiles[i].endsWith(".properties")) {
					continue;
				}
				
				propsFile = new File(propFiles[i]);
				propsName = propFiles[i];
				propsName = net.dstone.common.utils.StringUtil.replace(propsName, "\\", "/");
				propsName = propsName.substring(propsName.lastIndexOf("/") + 1, propsName.lastIndexOf("."));

				if ( propsName.equals("app_server") || propsName.equals("app_local")  ) {
					if( StringUtil.nullCheck(System.getProperty("SYSTEM.KIND"), "local").equals("server") && !propFiles[i].endsWith("/app_server.properties") ) {
						continue;
					}else if( StringUtil.nullCheck(System.getProperty("SYSTEM.KIND"), "local").equals("local") && !propFiles[i].endsWith("/app_local.properties") ){
						continue;
					}
				}

				FileInputStream fis = null;
				try {
					propFiles[i] = net.dstone.common.utils.StringUtil.replace(propFiles[i], "\\", "/");
					fis = new FileInputStream(propFiles[i]);
					props.load(fis);
					if ( propsName.equals("app_server") || propsName.equals("app_local")  ) {
						propsName = "app";
					}
					propDictionay.put(propsName, props);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (fis != null) {
						try {
							fis.close();
						} catch (Exception e) {
						}
					}
				}
			}
		}
	}

	public static PropUtil getInstance() {
		return propUtil;
	}

	public String getProp(String propsName, String key) {
		return ((Properties) propDictionay.get(propsName)).getProperty(key);
	}
}
