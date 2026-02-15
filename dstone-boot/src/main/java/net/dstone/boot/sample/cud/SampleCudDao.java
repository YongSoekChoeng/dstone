package net.dstone.boot.sample.cud; 
 
import java.util.List; 
import java.util.Map; 
 
import org.springframework.stereotype.Repository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.beans.factory.annotation.Qualifier; 
import org.mybatis.spring.SqlSessionTemplate; 
 
@Repository 
public class SampleCudDao extends net.dstone.boot.common.biz.BaseDao { 

    /******************************************* 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] 시작 *******************************************/ 
    /* 
     * 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] NEW키값 조회 
     */ 
    public net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo selectSampleGroupRoleNewKey(net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo sampleGroupRoleCudVo) throws Exception { 
        return (net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo) sqlSessionSample.selectOne("net.dstone.boot.sample.cud.SampleCudDao.selectSampleGroupRoleNewKey", sampleGroupRoleCudVo); 
    } 
    /* 
     * 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] 입력 
     */  
    public int insertSampleGroupRole(net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo sampleGroupRoleCudVo) throws Exception { 
        return sqlSessionSample.insert("net.dstone.boot.sample.cud.SampleCudDao.insertSampleGroupRole", sampleGroupRoleCudVo); 
    } 
    /* 
     * 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] 수정 
     */  
    public int updateSampleGroupRole(net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo sampleGroupRoleCudVo) throws Exception { 
        return sqlSessionSample.update("net.dstone.boot.sample.cud.SampleCudDao.updateSampleGroupRole", sampleGroupRoleCudVo); 
    } 
    /* 
     * 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] 삭제 
     */ 
    public int deleteSampleGroupRole(net.dstone.boot.sample.cud.vo.SampleGroupRoleCudVo sampleGroupRoleCudVo) throws Exception { 
        return sqlSessionSample.delete("net.dstone.boot.sample.cud.SampleCudDao.deleteSampleGroupRole", sampleGroupRoleCudVo); 
    } 
    /******************************************* 샘플그룹별ROLE[SAMPLE_GROUP_ROLE] 끝 *******************************************/ 



    /******************************************* 샘플ROLE[SAMPLE_ROLE] 시작 *******************************************/ 
    /* 
     * 샘플ROLE[SAMPLE_ROLE] NEW키값 조회 
     */ 
    public net.dstone.boot.sample.cud.vo.SampleRoleCudVo selectSampleRoleNewKey(net.dstone.boot.sample.cud.vo.SampleRoleCudVo sampleRoleCudVo) throws Exception { 
        return (net.dstone.boot.sample.cud.vo.SampleRoleCudVo) sqlSessionSample.selectOne("net.dstone.boot.sample.cud.SampleCudDao.selectSampleRoleNewKey", sampleRoleCudVo); 
    } 
    /* 
     * 샘플ROLE[SAMPLE_ROLE] 입력 
     */  
    public int insertSampleRole(net.dstone.boot.sample.cud.vo.SampleRoleCudVo sampleRoleCudVo) throws Exception { 
        return sqlSessionSample.insert("net.dstone.boot.sample.cud.SampleCudDao.insertSampleRole", sampleRoleCudVo); 
    } 
    /* 
     * 샘플ROLE[SAMPLE_ROLE] 수정 
     */  
    public int updateSampleRole(net.dstone.boot.sample.cud.vo.SampleRoleCudVo sampleRoleCudVo) throws Exception { 
        return sqlSessionSample.update("net.dstone.boot.sample.cud.SampleCudDao.updateSampleRole", sampleRoleCudVo); 
    } 
    /* 
     * 샘플ROLE[SAMPLE_ROLE] 삭제 
     */ 
    public int deleteSampleRole(net.dstone.boot.sample.cud.vo.SampleRoleCudVo sampleRoleCudVo) throws Exception { 
        return sqlSessionSample.delete("net.dstone.boot.sample.cud.SampleCudDao.deleteSampleRole", sampleRoleCudVo); 
    } 
    /******************************************* 샘플ROLE[SAMPLE_ROLE] 끝 *******************************************/ 



    /******************************************* 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] 시작 *******************************************/ 
    /* 
     * 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] NEW키값 조회 
     */ 
    public net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo selectSampleRoleProgNewKey(net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo sampleRoleProgCudVo) throws Exception { 
        return (net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo) sqlSessionSample.selectOne("net.dstone.boot.sample.cud.SampleCudDao.selectSampleRoleProgNewKey", sampleRoleProgCudVo); 
    } 
    /* 
     * 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] 입력 
     */  
    public int insertSampleRoleProg(net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo sampleRoleProgCudVo) throws Exception { 
        return sqlSessionSample.insert("net.dstone.boot.sample.cud.SampleCudDao.insertSampleRoleProg", sampleRoleProgCudVo); 
    } 
    /* 
     * 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] 수정 
     */  
    public int updateSampleRoleProg(net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo sampleRoleProgCudVo) throws Exception { 
        return sqlSessionSample.update("net.dstone.boot.sample.cud.SampleCudDao.updateSampleRoleProg", sampleRoleProgCudVo); 
    } 
    /* 
     * 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] 삭제 
     */ 
    public int deleteSampleRoleProg(net.dstone.boot.sample.cud.vo.SampleRoleProgCudVo sampleRoleProgCudVo) throws Exception { 
        return sqlSessionSample.delete("net.dstone.boot.sample.cud.SampleCudDao.deleteSampleRoleProg", sampleRoleProgCudVo); 
    } 
    /******************************************* 샘플ROLE별프로그램[SAMPLE_ROLE_PROG] 끝 *******************************************/ 



    /******************************************* 샘플맴버[SAMPLE_MEMBER] 시작 *******************************************/ 
    /* 
     * 샘플맴버[SAMPLE_MEMBER] NEW키값 조회 
     */ 
    public net.dstone.boot.sample.cud.vo.SampleMemberCudVo selectSampleMemberNewKey(net.dstone.boot.sample.cud.vo.SampleMemberCudVo sampleMemberCudVo) throws Exception { 
        return (net.dstone.boot.sample.cud.vo.SampleMemberCudVo) sqlSessionSample.selectOne("net.dstone.boot.sample.cud.SampleCudDao.selectSampleMemberNewKey", sampleMemberCudVo); 
    } 
    /* 
     * 샘플맴버[SAMPLE_MEMBER] 입력 
     */  
    public int insertSampleMember(net.dstone.boot.sample.cud.vo.SampleMemberCudVo sampleMemberCudVo) throws Exception { 
        return sqlSessionSample.insert("net.dstone.boot.sample.cud.SampleCudDao.insertSampleMember", sampleMemberCudVo); 
    } 
    /* 
     * 샘플맴버[SAMPLE_MEMBER] 수정 
     */  
    public int updateSampleMember(net.dstone.boot.sample.cud.vo.SampleMemberCudVo sampleMemberCudVo) throws Exception { 
        return sqlSessionSample.update("net.dstone.boot.sample.cud.SampleCudDao.updateSampleMember", sampleMemberCudVo); 
    } 
    /* 
     * 샘플맴버[SAMPLE_MEMBER] 삭제 
     */ 
    public int deleteSampleMember(net.dstone.boot.sample.cud.vo.SampleMemberCudVo sampleMemberCudVo) throws Exception { 
        return sqlSessionSample.delete("net.dstone.boot.sample.cud.SampleCudDao.deleteSampleMember", sampleMemberCudVo); 
    } 
    /******************************************* 샘플맴버[SAMPLE_MEMBER] 끝 *******************************************/ 



    /******************************************* 샘플그룹[SAMPLE_GROUP] 시작 *******************************************/ 
    /* 
     * 샘플그룹[SAMPLE_GROUP] NEW키값 조회 
     */ 
    public net.dstone.boot.sample.cud.vo.SampleGroupCudVo selectSampleGroupNewKey(net.dstone.boot.sample.cud.vo.SampleGroupCudVo sampleGroupCudVo) throws Exception { 
        return (net.dstone.boot.sample.cud.vo.SampleGroupCudVo) sqlSessionSample.selectOne("net.dstone.boot.sample.cud.SampleCudDao.selectSampleGroupNewKey", sampleGroupCudVo); 
    } 
    /* 
     * 샘플그룹[SAMPLE_GROUP] 입력 
     */  
    public int insertSampleGroup(net.dstone.boot.sample.cud.vo.SampleGroupCudVo sampleGroupCudVo) throws Exception { 
        return sqlSessionSample.insert("net.dstone.boot.sample.cud.SampleCudDao.insertSampleGroup", sampleGroupCudVo); 
    } 
    /* 
     * 샘플그룹[SAMPLE_GROUP] 수정 
     */  
    public int updateSampleGroup(net.dstone.boot.sample.cud.vo.SampleGroupCudVo sampleGroupCudVo) throws Exception { 
        return sqlSessionSample.update("net.dstone.boot.sample.cud.SampleCudDao.updateSampleGroup", sampleGroupCudVo); 
    } 
    /* 
     * 샘플그룹[SAMPLE_GROUP] 삭제 
     */ 
    public int deleteSampleGroup(net.dstone.boot.sample.cud.vo.SampleGroupCudVo sampleGroupCudVo) throws Exception { 
        return sqlSessionSample.delete("net.dstone.boot.sample.cud.SampleCudDao.deleteSampleGroup", sampleGroupCudVo); 
    } 
    /******************************************* 샘플그룹[SAMPLE_GROUP] 끝 *******************************************/ 


} 
