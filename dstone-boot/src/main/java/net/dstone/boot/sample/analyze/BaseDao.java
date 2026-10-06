package net.dstone.boot.sample.analyze;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class BaseDao {

    @Autowired 
    @Qualifier("sqlSessionSample") 
    private SqlSessionTemplate sqlSessionSample;

	public SqlSessionTemplate getSqlSessionSample() {
		return sqlSessionSample;
	}

	public void setSqlSessionSample(SqlSessionTemplate sqlSessionSample) {
		this.sqlSessionSample = sqlSessionSample;
	} 
    
    protected void d(Object msg) {
    	System.out.println(msg);
    }
    
    public abstract void testBaseDao(String id);

}
