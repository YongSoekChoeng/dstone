package net.dstone.boot.analyzer; 
 
import java.util.ArrayList;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.common.utils.RequestUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.XmlUtil;
@Controller
@RequestMapping(value = "/analyzer/configuration/*")
public class ConfigurationController extends net.dstone.boot.common.biz.BaseController { 
    

    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.ConfigurationService configurationService; 
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 시스템정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/listSys.do") 
    public ModelAndView listSys(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
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
   		net.dstone.boot.analyzer.vo.SysVo 					paramVo;
   		//net.dstone.boot.analyzer.vo.SysVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.analyzer.vo.SysVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.SysVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.analyzer.vo.SysVo[])bindMultiValues(requestUtil, "net.dstone.boot.analyzer.vo.SysVo");
   		/*** 페이징파라메터 세팅 시작 ***/
   		if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getParameter("PAGE_NUM"))){
   			paramVo.setPAGE_NUM(Integer.parseInt(requestUtil.getParameter("PAGE_NUM")));
   			paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);
   		}
   		/*** 페이징파라메터 세팅 끝 ***/
   		// 2. 서비스 호출
   		returnObj 				= configurationService.listSys(paramVo);
   		// 3. 결과처리
   		mav.addObject("returnObj", returnObj	);
   		/*** 페이징객체 세팅 시작 ***/
   		mav.addObject("pageHTML", ((net.dstone.common.utils.PageUtil) returnObj.get("pageUtil")).htmlPostPage(request, "MAIN_FORM", "PAGE_NUM", "goPage")	);
   		/*** 페이징객체 세팅 끝 ***/
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 


    /** 
     * 시스템정보 입력 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertSys.do") 
    public ModelAndView insertSys(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 					requestUtil;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.cud.vo.TbSysCudVo 				paramVo;
   		//net.dstone.boot.analyzer.cud.vo.TbSysCudVo[] 			paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE			= requestUtil.getParameter("ACTION_MODE");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.analyzer.cud.vo.TbSysCudVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.cud.vo.TbSysCudVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.analyzer.cud.vo.TbSysCudVo[])bindMultiValues(requestUtil, "net.dstone.boot.analyzer.cud.vo.TbSysCudVo");
   		// 2. 서비스 호출
   		boolean result 			= configurationService.insertSys(paramVo);
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("returnObj", new Boolean(result)	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 


    /** 
     * 시스템정보 상세조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/getSys.do") 
    public ModelAndView getSys(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 					requestUtil;
   		net.dstone.boot.analyzer.vo.SysVo					returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.vo.SysVo 					paramVo;
   		//net.dstone.boot.analyzer.vo.SysVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.analyzer.vo.SysVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.SysVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.analyzer.vo.SysVo[])bindMultiValues(requestUtil, "net.dstone.boot.analyzer.vo.SysVo");
   		// 2. 서비스 호출
   		returnObj 				= configurationService.getSys(paramVo);
   		// 3. 결과처리
   		mav.addObject("returnObj", returnObj	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    



    /** 
     * 시스템정보 삭제 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/deleteSys.do") 
    public ModelAndView deleteSys(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 					requestUtil;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.cud.vo.TbSysCudVo 				paramVo;
   		//net.dstone.boot.analyzer.cud.vo.TbSysCudVo[] 			paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE			= requestUtil.getParameter("ACTION_MODE");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.analyzer.cud.vo.TbSysCudVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.cud.vo.TbSysCudVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.analyzer.cud.vo.TbSysCudVo[])bindMultiValues(requestUtil, "net.dstone.boot.analyzer.cud.vo.TbSysCudVo");
   		// 2. 서비스 호출
   		boolean result 			= configurationService.deleteSys(paramVo);
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("returnObj", new Boolean(result)	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    
    /** 
     * 어플리케이션 옵션 조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/getAppOption.do") 
    public ModelAndView getAppOption(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.vo.SysVo 					paramVo;
   		net.dstone.boot.analyzer.vo.SysVo					returnObj;
   		net.dstone.common.utils.XmlUtil					xmlUtil;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		paramVo 				= (net.dstone.boot.analyzer.vo.SysVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.SysVo());
   		// 2. 서비스 호출
   		returnObj 				= configurationService.getSys(paramVo);
   		
   		if( returnObj != null && !StringUtil.isEmpty(returnObj.getCONF_FILE_PATH()) ) {
   			xmlUtil 			= XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, returnObj.getCONF_FILE_PATH());
   			
   			// 분석패키지루트 목록(분석대상 패키지 루트. 해당 패키지이하의 모듈만 분석한다.)
			if(!StringUtil.isEmpty(xmlUtil.getNode("INCLUDE_PACKAGE_ROOT").getTextContent()) && !xmlUtil.getNode("INCLUDE_PACKAGE_ROOT").getTextContent().trim().equals("") ) {
				ArrayList<String> includePackageRootList = new ArrayList<String>();
				String includePackageRootListStr = xmlUtil.getNode("INCLUDE_PACKAGE_ROOT").getTextContent();
				String[] includePackageRootListArr = StringUtil.toStrArray(includePackageRootListStr, ",", true);
				for(String includePackageRoot : includePackageRootListArr) {
					includePackageRootList.add(includePackageRoot);
				}
				returnObj.setINCLUDE_PACKAGE_ROOT(includePackageRootList);
			}

   			// 분석제외패키지패턴 목록(분석제외대상 패키지 패턴. 해당 패키지명이 속하는 패키지는 분석제외한다.)
			if(!StringUtil.isEmpty(xmlUtil.getNode("EXCLUDE_PACKAGE_PATTERN").getTextContent()) && !xmlUtil.getNode("EXCLUDE_PACKAGE_PATTERN").getTextContent().trim().equals("") ) {
				ArrayList<String> excludePackagePatternList = new ArrayList<String>();
				String excludePackagePatternListStr = xmlUtil.getNode("EXCLUDE_PACKAGE_PATTERN").getTextContent();
				String[] excludePackagePatternListArr = StringUtil.toStrArray(excludePackagePatternListStr, ",", true);
				for(String excludePackagePattern : excludePackagePatternListArr) {
					excludePackagePatternList.add(excludePackagePattern);
				}
				returnObj.setEXCLUDE_PACKAGE_PATTERN(excludePackagePatternList);
			}
			
   		}
   		
   		// 3. 결과처리
   		mav.addObject("returnObj", returnObj	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    
    /** 
     * 어플리케이션 옵션 저장 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/saveAppOption.do") 
    public ModelAndView saveAppOption(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		//파라메터로 사용할 VO
   		net.dstone.boot.analyzer.vo.SysVo 					paramVo;
   		net.dstone.boot.analyzer.vo.SysVo					returnObj;
   		net.dstone.common.utils.XmlUtil					xmlUtil;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		paramVo 				= (net.dstone.boot.analyzer.vo.SysVo)bindSingleValue(requestUtil, new net.dstone.boot.analyzer.vo.SysVo());
   		// 2. 서비스 호출
   		returnObj 				= configurationService.getSys(paramVo);
   		
   		if( returnObj != null && !StringUtil.isEmpty(returnObj.getCONF_FILE_PATH()) ) {
   			xmlUtil 			= XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, returnObj.getCONF_FILE_PATH());
   			// 분석패키지루트 목록(분석대상 패키지 루트. 해당 패키지이하의 모듈만 분석한다.)
   	   		if( requestUtil.getParameterValues("INCLUDE_PACKAGE_ROOT") != null ) {
   	   			StringBuffer includePackageRootBuff = new StringBuffer();
   	   			for(String includePackageRoot : requestUtil.getParameterValues("INCLUDE_PACKAGE_ROOT")) {
   	   				if( includePackageRootBuff.length() > 0 ) {
   	   				includePackageRootBuff.append(",");
   	   				}
   	   				includePackageRootBuff.append(includePackageRoot);
   	   			}
   	   			xmlUtil.getNode("INCLUDE_PACKAGE_ROOT").setTextContent(includePackageRootBuff.toString());
   	   		}
   	   		// 분석제외패키지패턴 목록(분석제외대상 패키지 패턴. 해당 패키지명이 속하는 패키지는 분석제외한다.)
   	   		if( requestUtil.getParameterValues("EXCLUDE_PACKAGE_PATTERN") != null ) {
   	   			StringBuffer excludePackagePatternBuff = new StringBuffer();
   	   			for(String excludePackagePattern : requestUtil.getParameterValues("EXCLUDE_PACKAGE_PATTERN")) {
   	   				if( excludePackagePatternBuff.length() > 0 ) {
   	   					excludePackagePatternBuff.append(",");
   	   				}
   	   				excludePackagePatternBuff.append(excludePackagePattern);
   	   			}
   	   			xmlUtil.getNode("EXCLUDE_PACKAGE_PATTERN").setTextContent(excludePackagePatternBuff.toString());
   	   		}
   	   		xmlUtil.save();
   		}
   		
   		// 3. 결과처리
   		mav.addObject("returnObj", returnObj	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }

} 
