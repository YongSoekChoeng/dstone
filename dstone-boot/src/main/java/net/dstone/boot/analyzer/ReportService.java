package net.dstone.boot.analyzer; 
 
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.StringUtil; 
 
@Service 
public class ReportService extends BaseService { 
     
    /********* DAO 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.ReportDao reportDao; 
    /********* DAO 정의부분 끝 *********/
    /** 
     * 종합결과 리스트조회 
     * @param paramVo 
     * @return 
     * @throws Exception 
     */ 
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public Map listOverAll(net.dstone.boot.analyzer.vo.OverAllVo paramVo) throws BizException{ 
        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.
        /************************ 변수 선언 시작 ************************/ 
        HashMap returnMap;                     	// 반환대상 맵 
        List<net.dstone.boot.analyzer.vo.OverAllVo> list;            // 리스트 
        int totalCnt;
        int maxLevel;
        /************************ 변수 선언 끝 **************************/ 
        try { 
            /************************ 변수 정의 시작 ************************/ 
            returnMap = new HashMap(); 
            list = null; 
            totalCnt = 0;
            maxLevel = 0;
            /************************ 변수 정의 끝 **************************/ 
            
            /************************ 비즈니스로직 시작 ************************/ 
            //DAO 호출부분 구현
            list = reportDao.listOverAll(paramVo); 
            if( list != null ) {
            	totalCnt = list.size();
    			for(net.dstone.boot.analyzer.vo.OverAllVo vo : list) {
    				
    				vo.setDISPLAY_ID_1(getDisplayId(vo.getFUNCTION_ID_1()));
    				vo.setDISPLAY_ID_2(getDisplayId(vo.getFUNCTION_ID_2()));
    				vo.setDISPLAY_ID_3(getDisplayId(vo.getFUNCTION_ID_3()));
    				vo.setDISPLAY_ID_4(getDisplayId(vo.getFUNCTION_ID_4()));
    				vo.setDISPLAY_ID_5(getDisplayId(vo.getFUNCTION_ID_5()));
    				vo.setDISPLAY_ID_6(getDisplayId(vo.getFUNCTION_ID_6()));
    				vo.setDISPLAY_ID_7(getDisplayId(vo.getFUNCTION_ID_7()));
    				vo.setDISPLAY_ID_8(getDisplayId(vo.getFUNCTION_ID_8()));
    				vo.setDISPLAY_ID_9(getDisplayId(vo.getFUNCTION_ID_9()));
    				vo.setDISPLAY_ID_10(getDisplayId(vo.getFUNCTION_ID_10()));
    				
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_1()) && maxLevel < 1) { maxLevel = 1; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_2()) && maxLevel < 2) { maxLevel = 2; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_3()) && maxLevel < 3) { maxLevel = 3; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_4()) && maxLevel < 4) { maxLevel = 4; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_5()) && maxLevel < 5) { maxLevel = 5; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_6()) && maxLevel < 6) { maxLevel = 6; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_7()) && maxLevel < 7) { maxLevel = 7; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_8()) && maxLevel < 8) { maxLevel = 8; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_9()) && maxLevel < 9) { maxLevel = 9; }
    				if(!StringUtil.isEmpty(vo.getDISPLAY_ID_10()) && maxLevel < 10) { maxLevel = 10; }
    			}
            }
            returnMap.put("totalCnt", StringUtil.moneyForamt(String.valueOf(totalCnt))); 
            returnMap.put("maxLevel", String.valueOf(maxLevel)); 
            returnMap.put("returnObj", list); 
            
            /************************ 비즈니스로직 끝 **************************/ 
        } catch (Exception e) { 
            String errDetailMsg = this.getClass().getName() + ".listOverAll 수행중 예외발생. 상세사항:" + e.toString(); 
            this.error(errDetailMsg); 
            throw new BizException(ErrCd.SYS_ERR, errDetailMsg);
        } 
        return returnMap; 
    }
    
    private String getDisplayId(String functionId) {
		String tempStr = functionId;
		String clzzId = "";
		String methodId = "";
		String displayId = "";
		if( !StringUtil.isEmpty(tempStr) ) {
			clzzId = "";
			methodId = "";
			if(tempStr.indexOf("(") > -1){
				tempStr = tempStr.substring(0, tempStr.indexOf("("));
			}
			if(tempStr.indexOf(".") > -1){
				methodId = tempStr.substring(tempStr.lastIndexOf(".")+1);
				tempStr = tempStr.substring(0, tempStr.lastIndexOf("."));
			}
			if(tempStr.indexOf(".") > -1){
				clzzId = tempStr.substring(tempStr.lastIndexOf(".")+1);
			}
			displayId = (clzzId + "." + methodId);
		}
    	return  displayId;
    }

} 
