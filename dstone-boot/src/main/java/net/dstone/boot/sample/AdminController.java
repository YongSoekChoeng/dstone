package net.dstone.boot.sample; 
 
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.RequestUtil;
@Controller
@RequestMapping(value = "/sample/admin/*")
public class AdminController extends net.dstone.boot.common.biz.BaseController { 
    
    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.sample.AdminService adminService; 
    /********* SVC 정의부분 끝 *********/

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    /** 
     * 샘플사용자정보 입력 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertUser.do") 
    public ModelAndView insertUser(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { 
   			mav = new ModelAndView("jsonView"); 
   		}else{
   			mav = new ModelAndView("sample/admin/manageUser"); 
   		}
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.sample.cud.vo.SampleMemberCudVo 							paramVo;
   		//net.dstone.boot.sample.cud.vo.SampleMemberCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo)bindSingleValue(requestUtil, new net.dstone.boot.sample.cud.vo.SampleMemberCudVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo[])bindMultiValues(requestUtil, "net.dstone.boot.sample.cud.vo.SampleMemberCudVo");
   		// 2. 서비스 호출
   		boolean result 			= adminService.insertUser(paramVo);
   		// 3. 결과처리
   		//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   		//request.setAttribute("returnObj"	, new Boolean(result) );
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    

    /** 
     * 샘플사용자정보 수정 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/updateUser.do") 
    public ModelAndView updateUser(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { 
   			mav = new ModelAndView("jsonView"); 
   		}else{
   			mav = new ModelAndView("sample/admin/manageUser"); 
   		}
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.sample.cud.vo.SampleMemberCudVo 							paramVo;
   		//net.dstone.boot.sample.cud.vo.SampleMemberCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo)bindSingleValue(requestUtil, new net.dstone.boot.sample.cud.vo.SampleMemberCudVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo[])bindMultiValues(requestUtil, "net.dstone.boot.sample.cud.vo.SampleMemberCudVo");
   		// 2. 서비스 호출
   		boolean result 			= adminService.updateUser(paramVo);
   		// 3. 결과처리
   		//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   		//request.setAttribute("returnObj"	, new Boolean(result) );
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 

    /** 
     * 샘플사용자정보 삭제 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/deleteUser.do") 
    public ModelAndView deleteUser(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { 
   			mav = new ModelAndView("jsonView"); 
   		}else{
   			mav = new ModelAndView("sample/admin/manageUser"); 
   		}
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.sample.cud.vo.SampleMemberCudVo 							paramVo;
   		//net.dstone.boot.sample.cud.vo.SampleMemberCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 바인딩
   		// 일반 파라메터 받는경우
   		//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   		// 싱글 VALUE 맵핑일 경우
   		paramVo 				= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo)bindSingleValue(requestUtil, new net.dstone.boot.sample.cud.vo.SampleMemberCudVo());
   		// 멀티 VALUE 맵핑일 경우
   		//paramList 			= (net.dstone.boot.sample.cud.vo.SampleMemberCudVo[])bindMultiValues(requestUtil, "net.dstone.boot.sample.cud.vo.SampleMemberCudVo");
   		// 2. 서비스 호출
   		boolean result 			= adminService.deleteUser(paramVo);
   		// 3. 결과처리
   		//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   		//request.setAttribute("returnObj"	, new Boolean(result) );
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 




    /** 
     * 샘플사용자정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/listUser.do") 
    public ModelAndView listUser(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { 
   			mav = new ModelAndView("jsonView"); 
   		}else{
   			mav = new ModelAndView("sample/admin/manageUser"); 
   		}
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 									requestUtil;
   		Map 											returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.boot.sample.vo.UserVo 					paramVo;
   		//net.dstone.boot.sample.vo.UserVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		paramVo					= null;
   		//paramList				= null;
   		returnObj				= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 폼서밋의 경우
   		if( !isAjax(request) ) {
   			// 1. 파라메터 바인딩
   	   		// 일반 파라메터 받는경우
   	   		//ACTION_MODE			= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   			// 싱글 VALUE 맵핑일 경우
   	   		paramVo 				= (net.dstone.boot.sample.vo.UserVo)bindSingleValue(requestUtil, new net.dstone.boot.sample.vo.UserVo());
   	   		// 멀티 VALUE 맵핑일 경우
   	   		//paramList 			= (net.dstone.boot.sample.vo.UserVo[])bindMultiValues(requestUtil, "net.dstone.boot.sample.vo.UserVo");
   	   		/*** 페이징파라메터 세팅 시작 ***/
   	   		if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getParameter("PAGE_NUM", ""))){
   	   			paramVo.setPAGE_NUM(requestUtil.getIntParameter("PAGE_NUM"));
   	   		}else {
   	   			paramVo.setPAGE_NUM(1);
   	   		}
   	   		paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);
   	   		/*** 페이징파라메터 세팅 끝 ***/
   	   		// 2. 서비스 호출
   	   		returnObj 				= adminService.listUser(paramVo);
   	   		// 3. 결과처리
   	   		request.setAttribute("returnObj", returnObj	);
   	   	// Ajax일 경우	
   		}else {
   			// 1. 파라메터 바인딩
   	   		// 일반 파라메터 받는경우
   	   		//ACTION_MODE			= requestUtil.getJsonParameterValue("ACTION_MODE");
   			// 싱글 VALUE 맵핑일 경우
   			paramVo 				= (net.dstone.boot.sample.vo.UserVo)bindSingleValue(requestUtil, new net.dstone.boot.sample.vo.UserVo());
   	   		// 멀티 VALUE 맵핑일 경우
   	   		//paramList 			= (net.dstone.boot.sample.vo.UserVo[])bindMultiValues(requestUtil, "net.dstone.boot.sample.vo.UserVo");
   	   		/*** 페이징파라메터 세팅 시작 ***/
   	   		if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getParameter("PAGE_NUM", ""))){
   	   			paramVo.setPAGE_NUM(requestUtil.getIntParameter("PAGE_NUM"));
   	   		}else {
   	   			paramVo.setPAGE_NUM(1);
   	   		}
   	   		paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);
   	   		/*** 페이징파라메터 세팅 끝 ***/
   	   		// 2. 서비스 호출
   	   		returnObj 				= adminService.listUser(paramVo);
   	   		// 3. 결과처리
   	   		mav.addObject("returnObj", returnObj);
   	   		/*** 페이징객체 세팅 시작 ***/
   	   		mav.addObject("pageHTML", ((net.dstone.common.utils.PageUtil) returnObj.get("pageUtil")).htmlPostPage(request, "AJAX_SELECT_FORM", "PAGE_NUM", "goPageAjax")	);
   	   		/*** 페이징객체 세팅 끝 ***/
   		}
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    } 

} 
