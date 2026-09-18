package net.dstone.boot.sample.analyze;

import org.springframework.stereotype.Repository;

@Repository("TestDao2") 
public class Test2DaoImpl extends BaseDao implements TestDao2{

	@Override
	public void doTestDao02(String name) {
		d(name);
	}

	@Override
	public void testBaseDao(String id) {
		d(id);
	}

	public void testMyDao2(String id) {
		d(id);
	}

}
