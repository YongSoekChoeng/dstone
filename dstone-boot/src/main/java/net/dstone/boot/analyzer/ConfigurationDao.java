package net.dstone.boot.analyzer; 
 
import java.util.List;

import org.springframework.stereotype.Repository; 
 
@Repository 
public class ConfigurationDao extends net.dstone.boot.common.biz.BaseDao { 

    /* 
     * 시스템정보 리스트조회(카운트) 
     */ 
    public int listSysCount(net.dstone.boot.analyzer.vo.SysVo sysVo) throws Exception { 
        Object returnObj = sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.ConfigurationDao.listSysCount", sysVo); 
        if (returnObj == null) {
            return 0;
        } else {
            return ((Integer) returnObj).intValue();
        }
    } 
    /* 
     * 시스템정보 리스트조회 
     */ 
    public List<net.dstone.boot.analyzer.vo.SysVo> listSys(net.dstone.boot.analyzer.vo.SysVo sysVo) throws Exception { 
        List<net.dstone.boot.analyzer.vo.SysVo> list = sqlSessionAnalyzer.selectList("net.dstone.boot.analyzer.ConfigurationDao.listSys", sysVo); 
        return list; 
    } 


    /* 
     * 시스템정보 상세조회 
     */ 
    public net.dstone.boot.analyzer.vo.SysVo getSys(net.dstone.boot.analyzer.vo.SysVo sysVo) throws Exception { 
        return (net.dstone.boot.analyzer.vo.SysVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.ConfigurationDao.getSys", sysVo); 
    } 

} 
