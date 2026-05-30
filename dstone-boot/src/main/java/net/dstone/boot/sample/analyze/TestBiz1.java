package net.dstone.boot.sample.analyze;

import org.springframework.stereotype.Component;

@Component("TestBiz1") 
public class TestBiz1 extends BaseBiz{
	
	public void testHello(String name) {
		System.out.println("Hello " + name + " ~!!");
	}

	public void testTestBiz1(String name) {
		System.out.println("Hello " + name + " ~!!");
	}

	@Override
	protected String testAbsBaseBiz(String id) {
		// TODO Auto-generated method stub
		return null;
	}
}
