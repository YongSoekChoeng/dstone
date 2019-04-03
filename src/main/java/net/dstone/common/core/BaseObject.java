package net.dstone.common.core;

import net.dstone.common.utils.LogUtil;

public class BaseObject {

	protected LogUtil getLogger() {
		return new LogUtil(this);
	}
	
}
