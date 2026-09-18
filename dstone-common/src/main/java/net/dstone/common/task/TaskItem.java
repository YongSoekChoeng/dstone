package net.dstone.common.task;

import java.util.HashMap;
import java.util.concurrent.Callable;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

public abstract class TaskItem extends BaseObject implements Callable<TaskItem>, Runnable{

	public abstract TaskItem doTheTask();
	
	@Override
	public TaskItem call() throws Exception {
		try {
			
			this.doTheTask();

		} catch (Exception e) {
			throw e;
		}
		return (TaskItem)this;
	}

	@Override
	public void run() {
		try {
			
			this.doTheTask();

		} catch (Exception e) {
			throw e;
		}
	}

	private String executorServiceId = "";
	private String taskItemId = "";
	private HashMap<String, Object> prop = new HashMap<String, Object>();

	/**
	 * @return
	 * 2007. 12. 23.
	 * Comment :
	 */
	public String getId() {
		return taskItemId;
	}

	/**
	 * @param string
	 * 2007. 12. 23.
	 * Comment :
	 */
	public void setId(String string) {
		taskItemId = string;
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

	public String getExecutorServiceId() {
		return executorServiceId;
	}

	public void setExecutorServiceId(String executorServiceId) {
		this.executorServiceId = executorServiceId;
	}
	
	public String getExecutorTheadId() {
        String executorTheadId = String.valueOf(Thread.currentThread().getId());
		return executorTheadId;
	}

	@Override
	public String toString() {
		return "TaskItem [executorServiceId=" + executorServiceId + ", taskItemId=" + taskItemId + ", prop=" + prop + "]";
	}

}
