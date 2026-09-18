package net.dstone.boot.sample.analyze;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("TestBiz2") 
public class TestBiz2 extends BaseBiz {
	
	@Autowired
	private TestBiz1 testBiz1ForAutowired;

	@Qualifier("testBiz1")
	private TestBiz1 testBiz1ForQualifier;
	
	public void testBiz1ForAutowired(String name) {
		testBiz1ForAutowired.testHello(name);
	}

	public void testBiz1ForQualifier(String name) {
		testBiz1ForQualifier.testHello(name);
	}

	public void testTestBiz2(String name) {
		System.out.println("Hello " + name + " ~!!");
	}

	@Override
	protected String testAbsBaseBiz(String id) {
		// TODO Auto-generated method stub
		return null;
	}
}
