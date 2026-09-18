package net.dstone.boot.common.biz;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class BaseDao extends net.dstone.common.biz.BaseDao {

    @Autowired 
    @Qualifier("sqlSessionCommon") 
    protected SqlSessionTemplate sqlSessionCommon; 

    @Autowired 
    @Qualifier("sqlSessionSample") 
    protected SqlSessionTemplate sqlSessionSample; 

    /*
    @Autowired 
    @Qualifier("sqlSessionSampleOracle") 
    protected SqlSessionTemplate sqlSessionSampleOracle; 

    @Autowired 
    @Qualifier("sqlSessionSamplePostgresql") 
    protected SqlSessionTemplate sqlSessionSamplePostgresql; 
    */

    @Autowired 
    @Qualifier("sqlSessionAnalyzer") 
    protected SqlSessionTemplate sqlSessionAnalyzer; 

}
