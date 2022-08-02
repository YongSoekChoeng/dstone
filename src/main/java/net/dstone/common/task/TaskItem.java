package net.dstone.common.task;

import java.util.Properties;
import java.util.concurrent.Callable;

public abstract class TaskItem implements Callable<TaskItem>{
	
	protected void debug(Object o){
		net.dstone.common.utils.LogUtil.sysout(o);
	}
	
	public abstract TaskItem doTheTask();
	
	@Override
	public TaskItem call() throws Exception {
		return (TaskItem)this.doTheTask();
	}
	
	private String strId = "";
	private Properties prop = new Properties();

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

	@Override
	public String toString() {
		return "TaskItem [strId=" + strId + ", prop=" + prop + "]";
	}
	
}
