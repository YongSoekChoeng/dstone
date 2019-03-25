package net.dstone.sample.member; 
 
import java.util.List; 
import java.util.Map; 
 
import org.springframework.stereotype.Repository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.beans.factory.annotation.Qualifier; 
import org.mybatis.spring.SqlSessionTemplate; 
 
@Repository 
public class GroupDao extends net.dstone.common.biz.BaseDao { 

    @Autowired 
    @Qualifier("sqlSession1") 
    private SqlSessionTemplate sqlSession1; 
     
    /* 
     * 샘플그룹정보 리스트조회(카운트) 
     */ 
    public int listSampleGroupCount(net.dstone.sample.member.vo.SampleGroupVo sampleGroupVo) throws Exception { 
        Object returnObj = sqlSession1.selectOne("NET_DSTONE_SAMPLE_MEMBER_GROUPDAO.listSampleGroupCount", sampleGroupVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 샘플그룹정보 리스트조회 
     */ 
    public List<net.dstone.sample.member.vo.SampleGroupVo> listSampleGroup(net.dstone.sample.member.vo.SampleGroupVo sampleGroupVo) throws Exception { 
        List<net.dstone.sample.member.vo.SampleGroupVo> list = sqlSession1.selectList("NET_DSTONE_SAMPLE_MEMBER_GROUPDAO.listSampleGroup", sampleGroupVo); 
        return list; 
    } 


    /* 
     * 샘플그룹정보 상세조회 
     */ 
    public net.dstone.sample.member.vo.SampleGroupVo getSampleGroup(net.dstone.sample.member.vo.SampleGroupVo sampleGroupVo) throws Exception { 
        return (net.dstone.sample.member.vo.SampleGroupVo) sqlSession1.selectOne("NET_DSTONE_SAMPLE_MEMBER_GROUPDAO.getSampleGroup", sampleGroupVo); 
    } 

} 
