package net.dstone.boot.sample.analyze;

public abstract class BaseBiz {
	protected String testBaseBiz(String id) {
		System.out.println(id);
		return id;
	}
	
	protected abstract String testAbsBaseBiz(String id);
}
