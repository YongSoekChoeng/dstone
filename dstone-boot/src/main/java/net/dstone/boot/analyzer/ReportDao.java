package net.dstone.boot.analyzer; 
 
import java.util.List;

import org.springframework.stereotype.Repository; 
 
@Repository 
public class ReportDao extends net.dstone.boot.common.biz.BaseDao { 

    /* 
     * 종합결과 리스트조회(카운트) 
     */ 
    public int listOverAllCount(net.dstone.boot.analyzer.vo.OverAllVo overAllVo) throws Exception { 
        Object returnObj = sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.ReportDao.listOverAllCount", overAllVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 종합결과 리스트조회 
     */ 
    public List<net.dstone.boot.analyzer.vo.OverAllVo> listOverAll(net.dstone.boot.analyzer.vo.OverAllVo overAllVo) throws Exception { 
        List<net.dstone.boot.analyzer.vo.OverAllVo> list = sqlSessionAnalyzer.selectList("net.dstone.boot.analyzer.ReportDao.listOverAll", overAllVo); 
        return list; 
    } 

} 
