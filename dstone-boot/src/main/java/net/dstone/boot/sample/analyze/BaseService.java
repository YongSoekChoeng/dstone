package net.dstone.boot.sample.analyze;

import org.springframework.beans.factory.annotation.Qualifier;

public abstract class BaseService {

    protected void d(Object msg) {
    	System.out.println(msg);
    }

    @Qualifier("TestDao1")
    protected TestDao1 testDao1;

    @Qualifier("TestDao2")
    protected TestDao2 testDao2;

    @Qualifier("TestBiz1")
    protected TestBiz1 testBiz1;

    @Qualifier("TestBiz2")
    protected TestBiz2 testBiz2;
    
    public abstract void testBaseService(String id); 
    
    protected void parentHellow(String name) {
    	System.out.println(name);
    }
    
    protected TestDao1 getTestDao1() {
    	return testDao1;
    }

    protected TestDao2 getTestDao2() {
    	return testDao2;
    }

    protected TestBiz1 getTestBiz1() {
    	return testBiz1;
    }

    protected TestBiz2 getTestBiz2() {
    	return testBiz2;
    }
    
}
