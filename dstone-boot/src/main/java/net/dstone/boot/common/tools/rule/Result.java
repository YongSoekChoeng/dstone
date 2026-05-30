package net.dstone.boot.common.tools.rule;

import java.util.HashMap;

public class Result {
	
	private boolean isValid = false;
	private HashMap<String, Object> resultMap = new HashMap<String, Object>();
	
	
	public boolean isValid() {
		return isValid;
	}
	public void setValid(boolean isValid) {
		this.isValid = isValid;
	}
	public HashMap<String, Object> getResultMap() {
		return resultMap;
	}
	public void setResultMap(HashMap<String, Object> resultMap) {
		this.resultMap = resultMap;
	}
	public Object getResult(String key) {
		return resultMap.get(key);
	}
	public void setResult(String key, Object value) {
		this.resultMap.put(key, value);
	}
	
	@Override
	public String toString() {
		return "Result [isValid=" + isValid + ", resultMap=" + resultMap + "]";
	}
	
}
