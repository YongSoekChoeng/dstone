package net.dstone.common.queue;

import java.util.Properties;

public abstract class QueueItem {

	
	private String strId = "";
	private Properties prop = new Properties();
	
	public QueueItem(){
		strId = String.valueOf(System.currentTimeMillis());
	}
	/**
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getId() {
		return strId;
	}

	/**
	 * @param string
	 * 2007. 12. 23.
	 * Comment :
	 */
	public void setId(String string) {
		strId = string;
	}
	
	/**
	 * @param strKey
	 * @param obj
	 * 2007. 12. 23.
	 * Comment :
	 */
	public void setObj(String strKey, Object obj){
		this.prop.put(strKey, obj);
	}
	
	/**
	 * @param strKey
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public Object getObj(String strKey){
		return this.prop.get(strKey);
	}
	
	/**
	 * @param strKey
	 * @param strVal
	 * 2007. 12. 23.
	 * Comment :
	 */
	public void setProperty(String strKey, String strVal){
		this.prop.setProperty(strKey, strVal);
	}
	
	/**
	 * @param strKey
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getProperty(String strKey){
		return this.prop.getProperty(strKey);
	}
	
	/**
	 * @param strKey
	 * @param strDefaultVal
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getProperty(String strKey, String strDefaultVal){
		return this.prop.getProperty(strKey, strDefaultVal);
	}
	
	/**
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public Properties getProp(){
		return this.prop;
	}
	
	public abstract void doTheJob();

}
