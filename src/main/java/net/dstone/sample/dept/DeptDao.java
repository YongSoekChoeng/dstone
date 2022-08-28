package net.dstone.sample.dept; 
 
import java.util.List; 
import java.util.Map; 
 
import org.springframework.stereotype.Repository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.beans.factory.annotation.Qualifier; 
import org.mybatis.spring.SqlSessionTemplate; 
 
@Repository 
public class DeptDao extends net.dstone.common.biz.BaseDao { 

    @Autowired 
    @Qualifier("sqlSession1") 
    private SqlSessionTemplate sqlSession1; 

    @Autowired 
    @Qualifier("sqlSession2") 
    private SqlSessionTemplate sqlSession2; 
     
    /* 
     * 샘플부서정보 리스트조회(카운트) 
     */ 
    public int listSampleDeptCount(net.dstone.sample.dept.vo.SampleDeptVo sampleDeptVo) throws Exception { 
        Object returnObj = sqlSession1.selectOne("NET_DSTONE_SAMPLE_DEPT_DEPTDAO.listSampleDeptCount", sampleDeptVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 샘플부서정보 리스트조회 
     */ 
    public List<net.dstone.sample.dept.vo.SampleDeptVo> listSampleDept(net.dstone.sample.dept.vo.SampleDeptVo sampleDeptVo) throws Exception { 
        List<net.dstone.sample.dept.vo.SampleDeptVo> list = sqlSession1.selectList("NET_DSTONE_SAMPLE_DEPT_DEPTDAO.listSampleDept", sampleDeptVo); 
        return list; 
    } 


    /* 
     * 샘플부서정보 상세조회 
     */ 
    public net.dstone.sample.dept.vo.SampleDeptVo getSampleDept(net.dstone.sample.dept.vo.SampleDeptVo sampleDeptVo) throws Exception { 
        return (net.dstone.sample.dept.vo.SampleDeptVo) sqlSession1.selectOne("NET_DSTONE_SAMPLE_DEPT_DEPTDAO.getSampleDept", sampleDeptVo); 
    } 

} 
