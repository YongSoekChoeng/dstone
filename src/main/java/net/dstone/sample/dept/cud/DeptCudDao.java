package net.dstone.sample.dept.cud; 
 
import java.util.List; 
import java.util.Map; 
 
import org.springframework.stereotype.Repository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.beans.factory.annotation.Qualifier; 
import org.mybatis.spring.SqlSessionTemplate; 
 
@Repository 
public class DeptCudDao extends net.dstone.common.biz.BaseDao { 
	
    @Autowired 
    @Qualifier("sqlSession1") 
    private SqlSessionTemplate sqlSession1; 
     
    /******************************************* 샘플부서[SAMPLE_DEPT] 시작 *******************************************/ 
    /* 
     * 샘플부서[SAMPLE_DEPT] NEW키값 조회 
     */ 
    public net.dstone.sample.dept.cud.vo.SampleDeptCudVo selectSampleDeptNewKey(net.dstone.sample.dept.cud.vo.SampleDeptCudVo sampleDeptCudVo) throws Exception { 
        return (net.dstone.sample.dept.cud.vo.SampleDeptCudVo) sqlSession1.selectOne("NET_DSTONE_SAMPLE_DEPT_CUD_DEPTCUDDAO.selectSampleDeptNewKey", sampleDeptCudVo); 
    } 
    /* 
     * 샘플부서[SAMPLE_DEPT] 입력 
     */  
    public void insertSampleDept(net.dstone.sample.dept.cud.vo.SampleDeptCudVo sampleDeptCudVo) throws Exception { 
        sqlSession1.insert("NET_DSTONE_SAMPLE_DEPT_CUD_DEPTCUDDAO.insertSampleDept", sampleDeptCudVo); 
    } 
    /* 
     * 샘플부서[SAMPLE_DEPT] 수정 
     */  
    public void updateSampleDept(net.dstone.sample.dept.cud.vo.SampleDeptCudVo sampleDeptCudVo) throws Exception { 
        sqlSession1.update("NET_DSTONE_SAMPLE_DEPT_CUD_DEPTCUDDAO.updateSampleDept", sampleDeptCudVo); 
    } 
    /* 
     * 샘플부서[SAMPLE_DEPT] 삭제 
     */ 
    public void deleteSampleDept(net.dstone.sample.dept.cud.vo.SampleDeptCudVo sampleDeptCudVo) throws Exception { 
        sqlSession1.delete("NET_DSTONE_SAMPLE_DEPT_CUD_DEPTCUDDAO.deleteSampleDept", sampleDeptCudVo); 
    } 
    /******************************************* 샘플부서[SAMPLE_DEPT] 끝 *******************************************/ 

} 
