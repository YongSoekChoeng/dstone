package net.dstone.boot.analyzer; 
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.common.utils.RequestUtil;
import net.dstone.common.utils.StringUtil;
@Controller
@RequestMapping(value = "/analyzer/report/*")
public class ReportController extends net.dstone.boot.common.biz.BaseController { 
    

    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.ReportService reportService; 
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 종합결과 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/listOverAll.do") 
    public ModelAndView listOverAll(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		Map 											returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.vo.OverAllVo 					paramVo;
   		//net.dstone.boot.analyzer.vo.OverAllVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		paramVo 				= (net.dstone.boot.analyzer.vo.OverAllVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.OverAllVo());

   		// 2. 서비스 호출
   		returnObj 				= reportService.listOverAll(paramVo);
   		
   		// 3. 결과처리
   		mav.addObject("returnObj", returnObj);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    

    /** 
     * 종합결과 상세조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/selectOverAll.do") 
    public ModelAndView selectOverAll(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		Map 											returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.vo.OverAllVo 					paramVo;
   		//net.dstone.boot.analyzer.vo.OverAllVo[] 				paramList;
   		List<net.dstone.boot.analyzer.vo.OverAllVo> 				list;
   		List<HashMap<String, String>> 						tblList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		returnObj				= null;
   		tblList					= new ArrayList<HashMap<String, String>>();
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		paramVo 				= (net.dstone.boot.analyzer.vo.OverAllVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.OverAllVo());

   		// 2. 서비스 호출
   		paramVo.setLIMIT(1);
   		returnObj 				= reportService.listOverAll(paramVo);
   		list					= (List<net.dstone.boot.analyzer.vo.OverAllVo>)returnObj.get("returnObj");
   		if( list.size() > 0 ) {
   			paramVo = list.get(0);
   			String tblStr = paramVo.getCALL_TBL();
   			if( !StringUtil.isEmpty(tblStr) ){
   				ArrayList<String> uniqueKeyList = new ArrayList<String>();
   				String[] tblInfoArr = StringUtil.toStrArray(tblStr, ",", true);
   				for(String tblInfo : tblInfoArr){
   					String tblIdNm = "";
   					String tblCrud = "";
   					if( tblInfo.indexOf("-") > -1 ){
   						tblIdNm = tblInfo.substring(0, tblInfo.lastIndexOf("-"));
   						tblCrud = tblInfo.substring(tblInfo.lastIndexOf("-")+1);
   					}else{
   						tblIdNm = tblInfo;
   						tblCrud = "";
   					}
   					String tblId = "";
   					String tblNm = "";
   					if( tblIdNm.indexOf("(") > -1 && tblIdNm.indexOf(")") > -1 ){
   						tblId = tblIdNm.substring(0, tblIdNm.indexOf("("));
   						tblNm = tblIdNm.substring(tblIdNm.indexOf("(")+1, tblIdNm.lastIndexOf(")"));
   					}else {
   						tblId = tblIdNm;
   						tblNm = "";
   					}
   					
   					if( !uniqueKeyList.contains((tblId+tblCrud)) ) {
   	   					HashMap<String, String> tblMap = new HashMap<String, String>();
   	   					tblMap.put("TBL_ID", tblId);
   	   					tblMap.put("TBL_NM", tblNm);
   	   					tblMap.put("TBL_CRUD", tblCrud);
   	   					tblList.add(tblMap);
   	   					uniqueKeyList.add((tblId+tblCrud));
   					}
   					//System.out.println("tblId["+tblId+"] tblNm["+tblNm+"] tblCrud["+tblCrud+"]");	
   					
   				}
   			}
   		}
   		
   		// 3. 결과처리
   		mav.addObject("maxLevel", returnObj.get("maxLevel"));
   		mav.addObject("detailVo", paramVo);
   		mav.addObject("tblList", tblList);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }

} 
