package net.dstone.boot.analyzer; 
 
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.XmlUtil; 
 
@Service 
public class ConfigurationService extends BaseService { 
     
    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.cud.AnalyzerCudDao analyzerCudDao; 
    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/
    /********* DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.ConfigurationDao configurationDao; 
    /********* DAO 정의부분 끝 *********/
    /** 
     * 시스템정보 리스트조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    public Map listSys(net.dstone.boot.analyzer.vo.SysVo paramVo) throws BizException{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        HashMap returnMap;                                        // 반환대상 맵 
        List<net.dstone.boot.analyzer.vo.SysVo> list;            // 리스트 
        /*** 페이징파라메터 세팅 시작 ***/
        net.dstone.common.utils.PageUtil pageUtil; 						// 페이징 유틸 
        int INT_TOTAL_CNT = 0;
        int INT_FROM = 0;
        int INT_TO = 0;
        /*** 페이징파라메터 세팅 끝 ***/
        /************************ 변수 선언 끝 **************************/ 
        try { 
            /************************ 변수 정의 시작 ************************/ 
            returnMap = new HashMap(); 
            list = null; 
            /************************ 변수 정의 끝 **************************/ 
            
            /************************ 비즈니스로직 시작 ************************/ 
            /*** 페이징파라메터 세팅 시작 ***/
            INT_TOTAL_CNT = configurationDao.listSysCount(paramVo);
            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } 
            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } 
            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); 
            INT_TO = paramVo.getPAGE_SIZE(); 
            paramVo.setINT_FROM(INT_FROM);
            paramVo.setINT_TO(INT_TO);

            /*** 페이징파라메터 세팅 끝 ***/
            
            //DAO 호출부분 구현
            list = configurationDao.listSys(paramVo); 
            returnMap.put("returnObj", list); 
            
            /*** 페이징유틸 생성 시작 ***/ 
            pageUtil = new net.dstone.common.utils.PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), INT_TOTAL_CNT);
            returnMap.put("pageUtil", pageUtil);
            /*** 페이징유틸 생성 끝 ***/ 
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".listSys 수행중 예외발생. 상세사항:" + e.toString(); 
            this.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        } 
        return returnMap; 
    } 


    /**  
     * 시스템정보 입력 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean insertSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo paramVo) throws BizException{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        net.dstone.boot.analyzer.cud.vo.TbSysCudVo newKeyVo; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            newKeyVo = new net.dstone.boot.analyzer.cud.vo.TbSysCudVo();
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            if(StringUtil.isEmpty(paramVo.getWORKER_ID())) {
            	paramVo.setWORKER_ID("SYSTEM");
            }
            
            if(!StringUtil.isEmpty(paramVo.getAPP_CLASSPATH())) {
            	paramVo.setAPP_CLASSPATH(StringUtil.replace(paramVo.getAPP_CLASSPATH(), "\t", ""));
            	paramVo.setAPP_CLASSPATH(StringUtil.replace(paramVo.getAPP_CLASSPATH(), " ", ""));
            }
            
            if( analyzerCudDao.selectTbSys(paramVo) == null) {
            	analyzerCudDao.insertTbSys(paramVo);  
            }else {
            	analyzerCudDao.updateTbSys(paramVo);
            }
            this.saveXmlCongig(paramVo);
            
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".insertSys("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString(); 
            this.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        }  
        return isSuccess;  
    }
    
    private void saveXmlCongig(net.dstone.boot.analyzer.cud.vo.TbSysCudVo paramVo) {
    	XmlUtil xmlConfig = null;
    	try {
    		if( paramVo != null && !StringUtil.isEmpty(paramVo.getCONF_FILE_PATH()) ) {
    			xmlConfig = XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, paramVo.getCONF_FILE_PATH());

    			// 0. 시스템명
    			if(!StringUtil.isEmpty(paramVo.getSYS_NM())) {
    				xmlConfig.getNode("SYS_NM").setTextContent(paramVo.getSYS_NM());
    			}

    			// 1. 어플리케이션루트
    			if(!StringUtil.isEmpty(paramVo.getAPP_ROOT_PATH())) {
    				xmlConfig.getNode("APP_ROOT_PATH").setTextContent(paramVo.getAPP_ROOT_PATH());
    			}
    			
    			// 2. 어플리케이션서버소스경로
    			if(!StringUtil.isEmpty(paramVo.getAPP_SRC_PATH())) {
    				xmlConfig.getNode("APP_SRC_PATH").setTextContent(paramVo.getAPP_SRC_PATH());
    			}
    			
    			// 3. 어플리케이션웹소스경로
    			if(!StringUtil.isEmpty(paramVo.getAPP_WEB_PATH())) {
    				xmlConfig.getNode("APP_WEB_PATH").setTextContent(paramVo.getAPP_WEB_PATH());
    			}
    			
    			// 4. 어플리케이션쿼리소스루트
    			if(!StringUtil.isEmpty(paramVo.getAPP_SQL_PATH())) {
    				xmlConfig.getNode("APP_SQL_PATH").setTextContent(paramVo.getAPP_SQL_PATH());
    			}
    			
    			// 5. 분석결과생성경로
    			if(!StringUtil.isEmpty(paramVo.getWRITE_PATH())) {
    				xmlConfig.getNode("WRITE_PATH").setTextContent(paramVo.getWRITE_PATH());
    			}
    			
    			// 6.분석결과저장파일명
    			if(!StringUtil.isEmpty(paramVo.getSAVE_FILE_NAME())) {
    				xmlConfig.getNode("SAVE_FILE_NAME").setTextContent(paramVo.getSAVE_FILE_NAME());
    			}
    			
    			// 7.DB저장여부
    			if(!StringUtil.isEmpty(paramVo.getIS_SAVE_TO_DB())) {
    				xmlConfig.getNode("IS_SAVE_TO_DB").setTextContent(paramVo.getIS_SAVE_TO_DB());
    			}
    			
    			// 8.DB아이디
    			if(!StringUtil.isEmpty(paramVo.getDBID())) {
    				xmlConfig.getNode("DBID").setTextContent(paramVo.getDBID());
    			}
    			
    			// 9.테이블명DB추출여부
    			if(!StringUtil.isEmpty(paramVo.getIS_TABLE_LIST_FROM_DB())) {
    				xmlConfig.getNode("IS_TABLE_LIST_FROM_DB").setTextContent(paramVo.getIS_TABLE_LIST_FROM_DB());
    			}	
    			
    			// 10.테이블명 조회프리픽스
    			if(!StringUtil.isEmpty(paramVo.getTABLE_NAME_LIKE_STR())) {
    				xmlConfig.getNode("TABLE_NAME_LIKE_STR").setTextContent(paramVo.getTABLE_NAME_LIKE_STR());
    			}
    			
    			// 11.테이블명 정보파일명
    			if(!StringUtil.isEmpty(paramVo.getTABLE_LIST_FILE_NAME())) {
    				xmlConfig.getNode("TABLE_LIST_FILE_NAME").setTextContent(paramVo.getTABLE_LIST_FILE_NAME());
    			}
    			
    			// 12.JDK 홈
    			if(!StringUtil.isEmpty(paramVo.getAPP_JDK_HOME())) {
    				xmlConfig.getNode("APP_JDK_HOME").setTextContent(paramVo.getAPP_JDK_HOME());
    			}
    			
    			// 13.어플리케이션 클래스패스
    			if(!StringUtil.isEmpty(paramVo.getAPP_CLASSPATH())) {
    				xmlConfig.getNode("APP_CLASSPATH").setTextContent(paramVo.getAPP_CLASSPATH().trim());
    			}
    			
    			// 14.쓰레드 핸들러 종류(분석작업을 진행 할 쓰레드핸들러 종류. 1:싱글 쓰레드풀, 2:Fixed 쓰레드풀, 3:Cached 쓰레드풀)
    			if(!StringUtil.isEmpty(paramVo.getWORKER_THREAD_KIND())) {
    				xmlConfig.getNode("WORKER_THREAD_KIND").setTextContent(paramVo.getWORKER_THREAD_KIND());
    			}
    			
    			// 15.쓰레드 갯수(분석작업을 진행 할 쓰레드 갯수. 쓰레드 핸들러 종류가 Fixed 쓰레드 핸들러 일 경우에만 유효)
    			if(!StringUtil.isEmpty(paramVo.getWORKER_THREAD_NUM())) {
    				xmlConfig.getNode("WORKER_THREAD_NUM").setTextContent(paramVo.getWORKER_THREAD_NUM());
    			}

    			// 16.분석제외패키지패턴 목록(분석제외대상 패키지 패턴. 해당 패키지명이 속하는 패키지는 분석제외한다.)
    			if(!StringUtil.isEmpty(paramVo.getINCLUDE_PACKAGE_ROOT())) {
    				xmlConfig.getNode("INCLUDE_PACKAGE_ROOT").setTextContent(paramVo.getINCLUDE_PACKAGE_ROOT());
    			}

    			// 17.분석패키지루트 목록(분석대상 패키지 루트. 해당 패키지이하의 모듈만 분석한다.)
    			if(!StringUtil.isEmpty(paramVo.getEXCLUDE_PACKAGE_PATTERN())) {
    				xmlConfig.getNode("EXCLUDE_PACKAGE_PATTERN").setTextContent(paramVo.getEXCLUDE_PACKAGE_PATTERN());
    			}
    			
    			xmlConfig.save();
    			
    			if( AppAnalyzer.CONF != null ) {
    				if( AppAnalyzer.CONF.getNode("SYS_ID").getTextContent().equals(xmlConfig.getNode("SYS_ID").getTextContent()) ) {
    	    			AppAnalyzer.CONF_CHANGED = true;
    				}
    			}
    			
    		}
		} catch (Exception e) {
			this.error(e); 
		}
    }


    /** 
     * 시스템정보 상세조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    public net.dstone.boot.analyzer.vo.SysVo getSys(net.dstone.boot.analyzer.vo.SysVo paramVo) throws BizException{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        net.dstone.boot.analyzer.vo.SysVo returnObj;            // 반환객체 
        /************************ 변수 선언 끝 **************************/ 
        try { 
            /************************ 변수 정의 시작 ************************/ 
            returnObj = null;
            /************************ 변수 정의 끝 **************************/ 
            
            /************************ 비즈니스로직 시작 ************************/ 
            
            //DAO 호출부분 구현
            returnObj = configurationDao.getSys(paramVo); 
            
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".getSys 수행중 예외발생. 상세사항:" + e.toString(); 
            this.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        } 
        return returnObj; 
    } 


    /**  
     * 시스템정보 삭제 
     * @param paramVo  
     * @return boolean 
     * @throws Exception  
    */  
    public boolean deleteSys(net.dstone.boot.analyzer.cud.vo.TbSysCudVo paramVo) throws BizException{  
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. 
        /************************ 변수 선언 시작 ************************/  
        boolean isSuccess = false; 
        /************************ 변수 선언 끝 **************************/  
        try {  
            /************************ 변수 정의 시작 ************************/  
            
            /************************ 변수 정의 끝 **************************/  
             
            /************************ 비즈니스로직 시작 ************************/  
            //DAO 호출부분 구현 
            analyzerCudDao.deleteTbSys(paramVo);  
            isSuccess = true; 
            /************************ 비즈니스로직 끝 **************************/  
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".deleteSys("+paramVo+") 수행중 예외발생. 상세사항:" + e.toString(); 
            this.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        }  
        return isSuccess;  
    } 

} 
