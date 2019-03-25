package net.dstone.sample.member; 
 
import java.util.List; 
import java.util.Map; 
 
import org.springframework.stereotype.Repository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.beans.factory.annotation.Qualifier; 
import org.mybatis.spring.SqlSessionTemplate; 
 
@Repository 
public class MemberDao extends net.dstone.common.biz.BaseDao { 

    @Autowired 
    @Qualifier("sqlSession1") 
    private SqlSessionTemplate sqlSession1; 
     
    /* 
     * 샘플멤버정보 리스트조회(카운트) 
     */ 
    public int listSampleMemberCount(net.dstone.sample.member.vo.SampleMemberVo sampleMemberVo) throws Exception { 
        Object returnObj = sqlSession1.selectOne("NET_DSTONE_SAMPLE_MEMBER_MEMBERDAO.listSampleMemberCount", sampleMemberVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 샘플멤버정보 리스트조회 
     */ 
    public List<net.dstone.sample.member.vo.SampleMemberVo> listSampleMember(net.dstone.sample.member.vo.SampleMemberVo sampleMemberVo) throws Exception { 
        List<net.dstone.sample.member.vo.SampleMemberVo> list = sqlSession1.selectList("NET_DSTONE_SAMPLE_MEMBER_MEMBERDAO.listSampleMember", sampleMemberVo); 
        return list; 
    } 


    /* 
     * 샘플멤버정보 상세조회 
     */ 
    public net.dstone.sample.member.vo.SampleMemberVo getSampleMember(net.dstone.sample.member.vo.SampleMemberVo sampleMemberVo) throws Exception { 
        return (net.dstone.sample.member.vo.SampleMemberVo) sqlSession1.selectOne("NET_DSTONE_SAMPLE_MEMBER_MEMBERDAO.getSampleMember", sampleMemberVo); 
    } 

} 
