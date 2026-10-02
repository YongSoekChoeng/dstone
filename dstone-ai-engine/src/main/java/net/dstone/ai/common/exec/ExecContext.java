package net.dstone.ai.common.exec;

import java.util.HashMap;

import net.dstone.common.core.BaseObject;

public class ExecContext extends BaseObject {
	
	static ExecContext ctxt;
	
	private HashMap<String,HashMap<String,Object>> map = new HashMap<String,HashMap<String,Object>>();
	
	private ExecContext() {
		
	}
	public static ExecContext getInstance() {
		if(ctxt == null) {
			ctxt = new ExecContext();
		}
		return ctxt;
	}
	
	private HashMap<String,Object> getCurrentContext() {
		String currentThreadName = Thread.currentThread().getName();
		if( !map.containsKey(currentThreadName) ) {
			HashMap<String,Object> currentThreadContext = new HashMap<String,Object>();
			map.put(currentThreadName, currentThreadContext);
		}
		
		return map.get(currentThreadName);
	}

	public void removeCurrentContext() {
		String currentThreadName = Thread.currentThread().getName();
		if( !map.containsKey(currentThreadName) ) {
			HashMap<String,Object> currentThreadContext = map.get(currentThreadName);
			currentThreadContext.clear();
		}
		map.remove(currentThreadName);
	}

	public boolean existKey(String key) {
		boolean existKey = false;
		String currentThreadName = Thread.currentThread().getName();
		if( map.containsKey(currentThreadName) ) {
			HashMap<String,Object> currentThreadContext = map.get(currentThreadName);
			existKey = currentThreadContext.containsKey(key);
		}
		return existKey;
	}
	
	public void put(String key, Object val) {
		getCurrentContext().put(key, val);
	}
	
	public Object get(String key) {
		return getCurrentContext().get(key);
	}
}
