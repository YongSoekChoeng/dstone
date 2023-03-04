package net.dstone.common.task;

import java.util.HashMap;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dstone.common.utils.StringUtil;

public abstract class TaskItem implements Callable<TaskItem>{

	private Logger logger = LoggerFactory.getLogger(TaskItem.class);
	
	protected void debug(Object o){
		logger.debug(o.toString());
	}
	
	public abstract TaskItem doTheTask();
	
	@Override
	public TaskItem call() throws Exception {
		return (TaskItem)this.doTheTask();
	}
	
	private String strId = "";
	private HashMap<String, Object> prop = new HashMap<String, Object>();

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
		this.prop.put(strKey, strVal);
	}
	
	/**
	 * @param strKey
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getProperty(String strKey){
		return this.prop.get(strKey).toString();
	}
	
	/**
	 * @param strKey
	 * @param strDefaultVal
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getProperty(String strKey, String strDefaultVal){
		return StringUtil.nullCheck(this.prop.get(strKey), strDefaultVal);
	}
	
	/**
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public HashMap getProp(){
		return this.prop;
	}

	@Override
	public String toString() {
		return "TaskItem [strId=" + strId + ", prop=" + prop + "]";
	}
	
}
