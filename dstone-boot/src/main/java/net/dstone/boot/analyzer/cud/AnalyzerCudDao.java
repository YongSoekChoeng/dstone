package net.dstone.boot.analyzer.cud; 
 
import org.springframework.stereotype.Repository; 
 
@Repository 
public class AnalyzerCudDao extends net.dstone.boot.common.biz.BaseDao { 

    /******************************************* 클래스[TB_CLZZ] 시작 *******************************************/ 
    /* 
     * 클래스[TB_CLZZ] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbClzzCudVo selectTbClzz(net.dstone.boot.analyzer.cud.vo.TbClzzCudVo tbClzzCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbClzzCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbClzz", tbClzzCudVo); 
    } 
    /* 
     * 클래스[TB_CLZZ] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbClzzCudVo selectTbClzzNewKey(net.dstone.boot.analyzer.cud.vo.TbClzzCudVo tbClzzCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbClzzCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbClzzNewKey", tbClzzCudVo); 
    } 
    /* 
     * 클래스[TB_CLZZ] 입력 
     */  
    public int insertTbClzz(net.dstone.boot.analyzer.cud.vo.TbClzzCudVo tbClzzCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbClzz", tbClzzCudVo); 
    } 
    /* 
     * 클래스[TB_CLZZ] 수정 
     */  
    public int updateTbClzz(net.dstone.boot.analyzer.cud.vo.TbClzzCudVo tbClzzCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbClzz", tbClzzCudVo); 
    } 
    /* 
     * 클래스[TB_CLZZ] 삭제 
     */ 
    public int deleteTbClzz(net.dstone.boot.analyzer.cud.vo.TbClzzCudVo tbClzzCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbClzz", tbClzzCudVo); 
    } 
    /******************************************* 클래스[TB_CLZZ] 끝 *******************************************/ 



    /******************************************* 테이블맵핑[TB_FUNC_TBL_MAPPING] 시작 *******************************************/ 
    /* 
     * 테이블맵핑[TB_FUNC_TBL_MAPPING] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo selectTbFuncTblMapping(net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo tbFuncTblMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFuncTblMapping", tbFuncTblMappingCudVo); 
    } 
    /* 
     * 테이블맵핑[TB_FUNC_TBL_MAPPING] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo selectTbFuncTblMappingNewKey(net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo tbFuncTblMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFuncTblMappingNewKey", tbFuncTblMappingCudVo); 
    } 
    /* 
     * 테이블맵핑[TB_FUNC_TBL_MAPPING] 입력 
     */  
    public int insertTbFuncTblMapping(net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo tbFuncTblMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbFuncTblMapping", tbFuncTblMappingCudVo); 
    } 
    /* 
     * 테이블맵핑[TB_FUNC_TBL_MAPPING] 수정 
     */  
    public int updateTbFuncTblMapping(net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo tbFuncTblMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbFuncTblMapping", tbFuncTblMappingCudVo); 
    } 
    /* 
     * 테이블맵핑[TB_FUNC_TBL_MAPPING] 삭제 
     */ 
    public int deleteTbFuncTblMapping(net.dstone.boot.analyzer.cud.vo.TbFuncTblMappingCudVo tbFuncTblMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbFuncTblMapping", tbFuncTblMappingCudVo); 
    } 
    /******************************************* 테이블맵핑[TB_FUNC_TBL_MAPPING] 끝 *******************************************/ 



    /******************************************* 테이블[TB_TBL] 시작 *******************************************/ 
    /* 
     * 테이블[TB_TBL] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbTblCudVo selectTbTbl(net.dstone.boot.analyzer.cud.vo.TbTblCudVo tbTblCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbTblCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbTbl", tbTblCudVo); 
    } 
    /* 
     * 테이블[TB_TBL] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbTblCudVo selectTbTblNewKey(net.dstone.boot.analyzer.cud.vo.TbTblCudVo tbTblCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbTblCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbTblNewKey", tbTblCudVo); 
    } 
    /* 
     * 테이블[TB_TBL] 입력 
     */  
    public int insertTbTbl(net.dstone.boot.analyzer.cud.vo.TbTblCudVo tbTblCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbTbl", tbTblCudVo); 
    } 
    /* 
     * 테이블[TB_TBL] 수정 
     */  
    public int updateTbTbl(net.dstone.boot.analyzer.cud.vo.TbTblCudVo tbTblCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbTbl", tbTblCudVo); 
    } 
    /* 
     * 테이블[TB_TBL] 삭제 
     */ 
    public int deleteTbTbl(net.dstone.boot.analyzer.cud.vo.TbTblCudVo tbTblCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbTbl", tbTblCudVo); 
    } 
    /******************************************* 테이블[TB_TBL] 끝 *******************************************/ 



    /******************************************* 종합메트릭스[TB_METRIX] 시작 *******************************************/ 
    /* 
     * 종합메트릭스[TB_METRIX] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo selectTbMetrix(net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo tbMetrixCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbMetrix", tbMetrixCudVo); 
    } 
    /* 
     * 종합메트릭스[TB_METRIX] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo selectTbMetrixNewKey(net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo tbMetrixCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbMetrixNewKey", tbMetrixCudVo); 
    } 
    /* 
     * 종합메트릭스[TB_METRIX] 입력 
     */  
    public int insertTbMetrix(net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo tbMetrixCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbMetrix", tbMetrixCudVo); 
    } 
    /* 
     * 종합메트릭스[TB_METRIX] 수정 
     */  
    public int updateTbMetrix(net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo tbMetrixCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbMetrix", tbMetrixCudVo); 
    } 
    /* 
     * 종합메트릭스[TB_METRIX] 삭제 
     */ 
    public int deleteTbMetrix(net.dstone.boot.analyzer.cud.vo.TbMetrixCudVo tbMetrixCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbMetrix", tbMetrixCudVo); 
    } 
    /******************************************* 종합메트릭스[TB_METRIX] 끝 *******************************************/ 



    /******************************************* 화면[TB_UI] 시작 *******************************************/ 
    /* 
     * 화면[TB_UI] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbUiCudVo selectTbUi(net.dstone.boot.analyzer.cud.vo.TbUiCudVo tbUiCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbUiCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbUi", tbUiCudVo); 
    } 
    /* 
     * 화면[TB_UI] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbUiCudVo selectTbUiNewKey(net.dstone.boot.analyzer.cud.vo.TbUiCudVo tbUiCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbUiCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbUiNewKey", tbUiCudVo); 
    } 
    /* 
     * 화면[TB_UI] 입력 
     */  
    public int insertTbUi(net.dstone.boot.analyzer.cud.vo.TbUiCudVo tbUiCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbUi", tbUiCudVo); 
    } 
    /* 
     * 화면[TB_UI] 수정 
     */  
    public int updateTbUi(net.dstone.boot.analyzer.cud.vo.TbUiCudVo tbUiCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbUi", tbUiCudVo); 
    } 
    /* 
     * 화면[TB_UI] 삭제 
     */ 
    public int deleteTbUi(net.dstone.boot.analyzer.cud.vo.TbUiCudVo tbUiCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbUi", tbUiCudVo); 
    } 
    /******************************************* 화면[TB_UI] 끝 *******************************************/ 



    /******************************************* 화면기능맵핑[TB_UI_FUNC_MAPPING] 시작 *******************************************/ 
    /* 
     * 화면기능맵핑[TB_UI_FUNC_MAPPING] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo selectTbUiFuncMapping(net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo tbUiFuncMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbUiFuncMapping", tbUiFuncMappingCudVo); 
    } 
    /* 
     * 화면기능맵핑[TB_UI_FUNC_MAPPING] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo selectTbUiFuncMappingNewKey(net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo tbUiFuncMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbUiFuncMappingNewKey", tbUiFuncMappingCudVo); 
    } 
    /* 
     * 화면기능맵핑[TB_UI_FUNC_MAPPING] 입력 
     */  
    public int insertTbUiFuncMapping(net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo tbUiFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbUiFuncMapping", tbUiFuncMappingCudVo); 
    } 
    /* 
     * 화면기능맵핑[TB_UI_FUNC_MAPPING] 수정 
     */  
    public int updateTbUiFuncMapping(net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo tbUiFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbUiFuncMapping", tbUiFuncMappingCudVo); 
    } 
    /* 
     * 화면기능맵핑[TB_UI_FUNC_MAPPING] 삭제 
     */ 
    public int deleteTbUiFuncMapping(net.dstone.boot.analyzer.cud.vo.TbUiFuncMappingCudVo tbUiFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbUiFuncMapping", tbUiFuncMappingCudVo); 
    } 
    /******************************************* 화면기능맵핑[TB_UI_FUNC_MAPPING] 끝 *******************************************/ 



    /******************************************* 기능간맵핑[TB_FUNC_FUNC_MAPPING] 시작 *******************************************/ 
    /* 
     * 기능간맵핑[TB_FUNC_FUNC_MAPPING] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo selectTbFuncFuncMapping(net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo tbFuncFuncMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFuncFuncMapping", tbFuncFuncMappingCudVo); 
    } 
    /* 
     * 기능간맵핑[TB_FUNC_FUNC_MAPPING] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo selectTbFuncFuncMappingNewKey(net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo tbFuncFuncMappingCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFuncFuncMappingNewKey", tbFuncFuncMappingCudVo); 
    } 
    /* 
     * 기능간맵핑[TB_FUNC_FUNC_MAPPING] 입력 
     */  
    public int insertTbFuncFuncMapping(net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo tbFuncFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbFuncFuncMapping", tbFuncFuncMappingCudVo); 
    } 
    /* 
     * 기능간맵핑[TB_FUNC_FUNC_MAPPING] 수정 
     */  
    public int updateTbFuncFuncMapping(net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo tbFuncFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbFuncFuncMapping", tbFuncFuncMappingCudVo); 
    } 
    /* 
     * 기능간맵핑[TB_FUNC_FUNC_MAPPING] 삭제 
     */ 
    public int deleteTbFuncFuncMapping(net.dstone.boot.analyzer.cud.vo.TbFuncFuncMappingCudVo tbFuncFuncMappingCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbFuncFuncMapping", tbFuncFuncMappingCudVo); 
    } 
    /******************************************* 기능간맵핑[TB_FUNC_FUNC_MAPPING] 끝 *******************************************/ 



    /******************************************* 기능메서드[TB_FUNC] 시작 *******************************************/ 
    /* 
     * 기능메서드[TB_FUNC] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncCudVo selectTbFunc(net.dstone.boot.analyzer.cud.vo.TbFuncCudVo tbFuncCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFunc", tbFuncCudVo); 
    } 
    /* 
     * 기능메서드[TB_FUNC] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbFuncCudVo selectTbFuncNewKey(net.dstone.boot.analyzer.cud.vo.TbFuncCudVo tbFuncCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbFuncCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbFuncNewKey", tbFuncCudVo); 
    } 
    /* 
     * 기능메서드[TB_FUNC] 입력 
     */  
    public int insertTbFunc(net.dstone.boot.analyzer.cud.vo.TbFuncCudVo tbFuncCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbFunc", tbFuncCudVo); 
    } 
    /* 
     * 기능메서드[TB_FUNC] 수정 
     */  
    public int updateTbFunc(net.dstone.boot.analyzer.cud.vo.TbFuncCudVo tbFuncCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbFunc", tbFuncCudVo); 
    } 
    /* 
     * 기능메서드[TB_FUNC] 삭제 
     */ 
    public int deleteTbFunc(net.dstone.boot.analyzer.cud.vo.TbFuncCudVo tbFuncCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbFunc", tbFuncCudVo); 
    } 
    /******************************************* 기능메서드[TB_FUNC] 끝 *******************************************/ 



    /******************************************* 시스템[TB_SYS] 시작 *******************************************/ 
    /* 
     * 시스템[TB_SYS] 상세 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbSysCudVo selectTbSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo tbSysCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbSysCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbSys", tbSysCudVo); 
    } 
    /* 
     * 시스템[TB_SYS] NEW키값 조회 
     */ 
    public net.dstone.boot.analyzer.cud.vo.TbSysCudVo selectTbSysNewKey(net.dstone.boot.analyzer.cud.vo.TbSysCudVo tbSysCudVo) throws Exception { 
        return (net.dstone.boot.analyzer.cud.vo.TbSysCudVo) sqlSessionAnalyzer.selectOne("net.dstone.boot.analyzer.cud.AnalyzerCudDao.selectTbSysNewKey", tbSysCudVo); 
    } 
    /* 
     * 시스템[TB_SYS] 입력 
     */  
    public int insertTbSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo tbSysCudVo) throws Exception { 
        return sqlSessionAnalyzer.insert("net.dstone.boot.analyzer.cud.AnalyzerCudDao.insertTbSys", tbSysCudVo); 
    } 
    /* 
     * 시스템[TB_SYS] 수정 
     */  
    public int updateTbSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo tbSysCudVo) throws Exception { 
        return sqlSessionAnalyzer.update("net.dstone.boot.analyzer.cud.AnalyzerCudDao.updateTbSys", tbSysCudVo); 
    } 
    /* 
     * 시스템[TB_SYS] 삭제 
     */ 
    public int deleteTbSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo tbSysCudVo) throws Exception { 
        return sqlSessionAnalyzer.delete("net.dstone.boot.analyzer.cud.AnalyzerCudDao.deleteTbSys", tbSysCudVo); 
    } 
    /******************************************* 시스템[TB_SYS] 끝 *******************************************/ 


} 
