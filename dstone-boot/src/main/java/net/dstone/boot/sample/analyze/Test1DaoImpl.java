package net.dstone.boot.sample.analyze;

import org.springframework.stereotype.Repository;

@Repository("TestDao1") 
public class Test1DaoImpl extends BaseDao implements TestDao1{

	@Override
	public String doTestDao01(String name) {
		return "";
	}

	@Override
	public void testBaseDao(String id) {
		d(id);
	}

	public void testMyDao1(String id) {
		d(id);
	}

}
