package net.dstone.boot.sample.analyze;

import jakarta.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("TestService") 
public class TestServiceImpl extends BaseService implements TestService {

    /********* DAO 정의부분 시작 *********/
    @Autowired
    private TestDao1 myTestDao1;
    
//    @Qualifier("TestDao1")
//    private TestDao1 TestDao1;
//    
//    @Qualifier(value ="TestDao2")
//    private TestDao2 testDao2; 
//    
//    @Resource(name = "TestDao2")
//    private TestDao2 test2Dao;
//
//    @Autowired
//    private TestBiz2 testBiz2;
    /********* DAO 정의부분 끝 *********/
    
	@Override
	public void doTestService01(String name) {
		
/*

net.dstone.boot.sample.analyze.Test1DaoImpl.doTestDao01(java.lang.String)↕
net.dstone.boot.sample.analyze.Test2DaoImpl.doTestDao02(java.lang.String)↕
net.dstone.boot.sample.analyze.BaseService.getTestDao2()↕
net.dstone.boot.sample.analyze.Test1DaoImpl.testMyDao1(java.lang.String)↕
net.dstone.boot.sample.analyze.TestBiz1.testTestBiz1(java.lang.String)↕
net.dstone.boot.sample.analyze.TestBiz2.testTestBiz2(java.lang.String)↕
net.dstone.boot.sample.analyze.TestBiz1.testAbsBaseBiz(java.lang.String)↕
net.dstone.boot.sample.analyze.TestBiz2.testAbsBaseBiz(java.lang.String)

*/
		
		
//		net.dstone.boot.sample.analyze.BaseBiz.testAbsBaseBiz(java.lang.String)
		
		// 부모 변수로 메소드 호출
		testDao1.doTestDao01(name); // 부모 변수 직접사용
		getTestDao2().doTestDao02(name); // 부모 변수 getter 로 사용

		// 자신의 변수로 메소드 호출
		((Test1DaoImpl)myTestDao1).testMyDao1(name);
		
		// 메소드로컬변수로 메소드 호출
		BaseBiz baseBiz = null;
		if(true) {
			baseBiz = new TestBiz1();
			((TestBiz1)baseBiz).testTestBiz1(name);
		}else {
			baseBiz = new TestBiz2();
			((TestBiz2)baseBiz).testTestBiz2(name);
		}
		baseBiz.testAbsBaseBiz(name);
		

//		testDao2.doTestDao02(name);
//
//		testBiz2.testBiz1ForAutowired(name); 
//		 
//		testBaseService(name);
//		parentHellow(name);
	}

	public void doTestService02(TestBiz1 testBiz1) {
		testBiz1.testHello("하하");
	}

	@Override
	public void testBaseService(String id) {
		StringBuffer buff = new StringBuffer();
		buff.append(getTestDao1().doTestDao01(id));
		/*
		testDao1.doTestDao01(id); 
		buff.append(getTestDao1().doTestDao01(id));
		getTestDao2().doTestDao02(id);
		getTestBiz1().testBaseBiz01(id);
		buff.append(getTestBiz1().testBaseBiz01(id));
		*/
	}


}
