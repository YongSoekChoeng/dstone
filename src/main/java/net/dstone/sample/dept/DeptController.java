package net.dstone.sample.dept; 
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.annotation.RequestMapping;

import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.PropUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.BeanUtil;

@Controller
@RequestMapping(value = "/dept/*")
public class DeptController extends net.dstone.common.biz.BaseController { 
    

    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.dept.DeptService deptService; 
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 샘플그룹정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/listSampleDept.do") 
    public ModelAndView listSampleDept(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 세션체크 시작 ************************/
   		loginCheck(request, response);
   		/************************ 세션체크 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		Map 											returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.sample.dept.vo.SampleDeptVo 					paramVo;
   		//net.dstone.sample.dept.vo.SampleDeptVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		try {
   			/************************ 변수 정의 시작 ************************/
   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
   			paramVo					= null;
   			//paramList				= null;
   			returnObj				= null;
   			/************************ 변수 정의 끝 ************************/
   			
   			/************************ 컨트롤러 로직 시작 ************************/
   			// 1. 파라메터 바인딩
   			// 일반 파라메터 받는경우
   			//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   			// 싱글 VALUE 맵핑일 경우
   			paramVo 				= (net.dstone.sample.dept.vo.SampleDeptVo)bindSingleValue(requestUtil, new net.dstone.sample.dept.vo.SampleDeptVo());
   			// 멀티 VALUE 맵핑일 경우
   			//paramList 			= (net.dstone.sample.dept.vo.SampleDeptVo[])bindMultiValues(requestUtil, "net.dstone.sample.dept.vo.SampleDeptVo");
   			/*** 페이징파라메터 세팅 시작 ***/
   			if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getParameter("PAGE_NUM", ""))){
   				paramVo.setPAGE_NUM(requestUtil.getIntParameter("PAGE_NUM"));
   				paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);
   			}
   			/*** 페이징파라메터 세팅 끝 ***/
   			// 2. 서비스 호출
   			returnObj 				= deptService.listSampleDept(paramVo);
   			// 3. 결과처리
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			request.setAttribute("RETURN_MSG"	, "" );
   			//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   			request.setAttribute("returnObj"	, returnObj	);
   			mav.setViewName("/sample/view/dept/listSampleDept");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_FAIL );
   			request.setAttribute("RETURN_MSG"	, e.toString() );
   			mav.setViewName("/sample/view/dept/listSampleDept");
   		}
   		return mav;
    } 


    /** 
     * 샘플그룹정보 상세조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/getSampleDept.do") 
    public ModelAndView getSampleDept(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 세션체크 시작 ************************/
   		loginCheck(request, response);
   		/************************ 세션체크 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		net.dstone.sample.dept.vo.SampleDeptVo					returnObj;
   		//파라메터
   		//String										ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.sample.dept.vo.SampleDeptVo 					paramVo;
   		//net.dstone.sample.dept.vo.SampleDeptVo[] 				paramList;
   		/************************ 변수 선언 끝 **************************/
   		try {
   			/************************ 변수 정의 시작 ************************/
   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
   			paramVo					= null;
   			//paramList				= null;
   			returnObj				= null;
   			/************************ 변수 정의 끝 ************************/
   			
   			/************************ 컨트롤러 로직 시작 ************************/
   			// 1. 파라메터 바인딩
   			// 일반 파라메터 받는경우
   			//ACTION_MODE				= requestUtil.getParameter("ACTION_MODE", "LIST_PLAIN");
   			// 싱글 VALUE 맵핑일 경우
   			paramVo 				= (net.dstone.sample.dept.vo.SampleDeptVo)bindSingleValue(requestUtil, new net.dstone.sample.dept.vo.SampleDeptVo());
   			// 멀티 VALUE 맵핑일 경우
   			//paramList 			= (net.dstone.sample.dept.vo.SampleDeptVo[])bindMultiValues(requestUtil, "net.dstone.sample.dept.vo.SampleDeptVo");
   			// 2. 서비스 호출
   			returnObj 				= deptService.getSampleDept(paramVo);
   			// 3. 결과처리
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			request.setAttribute("RETURN_MSG"	, "" );
   			//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   			request.setAttribute("returnObj"	, returnObj	);
   			mav.setViewName("/sample/view/dept/getSampleDept");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_FAIL );
   			request.setAttribute("RETURN_MSG"	, e.toString() );
   			mav.setViewName("/sample/view/dept/getSampleDept");
   		}
   		return mav;
    } 


    /** 
     * 샘플그룹정보 입력 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertSampleDept.do") 
    public ModelAndView insertSampleDept(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 세션체크 시작 ************************/
   		loginCheck(request, response);
   		/************************ 세션체크 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.sample.dept.cud.vo.SampleDeptCudVo 							paramVo;
   		//net.dstone.sample.dept.cud.vo.SampleDeptCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		try {
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
   			paramVo 				= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo)bindSingleValue(requestUtil, new net.dstone.sample.dept.cud.vo.SampleDeptCudVo());
   			// 멀티 VALUE 맵핑일 경우
   			//paramList 			= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo[])bindMultiValues(requestUtil, "net.dstone.sample.dept.cud.vo.SampleDeptCudVo");
   			// 2. 서비스 호출
   			boolean result 			= deptService.insertSampleDept(paramVo);
   			// 3. 결과처리
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			request.setAttribute("RETURN_MSG"	, "" );
   			//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   			request.setAttribute("returnObj"	, new Boolean(result) );
   			mav.setViewName("/sample/view/dept/insertSampleDept");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_FAIL );
   			request.setAttribute("RETURN_MSG"	, e.toString() );
   			mav.setViewName("/sample/view/dept/insertSampleDept");
   		}
   		return mav;
    } 


    /** 
     * 샘플그룹정보 수정 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/updateSampleDept.do") 
    public ModelAndView updateSampleDept(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 세션체크 시작 ************************/
   		loginCheck(request, response);
   		/************************ 세션체크 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.sample.dept.cud.vo.SampleDeptCudVo 							paramVo;
   		//net.dstone.sample.dept.cud.vo.SampleDeptCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		try {
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
   			paramVo 				= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo)bindSingleValue(requestUtil, new net.dstone.sample.dept.cud.vo.SampleDeptCudVo());
   			// 멀티 VALUE 맵핑일 경우
   			//paramList 			= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo[])bindMultiValues(requestUtil, "net.dstone.sample.dept.cud.vo.SampleDeptCudVo");
   			// 2. 서비스 호출
   			boolean result 			= deptService.updateSampleDept(paramVo);
   			// 3. 결과처리
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			request.setAttribute("RETURN_MSG"	, "" );
   			//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   			request.setAttribute("returnObj"	, new Boolean(result) );
   			mav.setViewName("/sample/view/dept/updateSampleDept");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_FAIL );
   			request.setAttribute("RETURN_MSG"	, e.toString() );
   			mav.setViewName("/sample/view/dept/updateSampleDept");
   		}
   		return mav;
    } 


    /** 
     * 샘플그룹정보 삭제 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/deleteSampleDept.do") 
    public ModelAndView deleteSampleDept(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 세션체크 시작 ************************/
   		loginCheck(request, response);
   		/************************ 세션체크 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		//파라메터
   		//String											ACTION_MODE;
   		//파라메터로 사용할 VO
   		net.dstone.sample.dept.cud.vo.SampleDeptCudVo 							paramVo;
   		//net.dstone.sample.dept.cud.vo.SampleDeptCudVo[] 							paramList;
   		/************************ 변수 선언 끝 **************************/
   		try {
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
   			paramVo 				= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo)bindSingleValue(requestUtil, new net.dstone.sample.dept.cud.vo.SampleDeptCudVo());
   			// 멀티 VALUE 맵핑일 경우
   			//paramList 			= (net.dstone.sample.dept.cud.vo.SampleDeptCudVo[])bindMultiValues(requestUtil, "net.dstone.sample.dept.cud.vo.SampleDeptCudVo");
   			// 2. 서비스 호출
   			boolean result 			= deptService.deleteSampleDept(paramVo);
   			// 3. 결과처리
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			request.setAttribute("RETURN_MSG"	, "" );
   			//request.setAttribute("ACTION_MODE"	, ACTION_MODE	);
   			request.setAttribute("returnObj"	, new Boolean(result) );
   			mav.setViewName("/sample/view/dept/deleteSampleDept");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			request.setAttribute("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_FAIL );
   			request.setAttribute("RETURN_MSG"	, e.toString() );
   			mav.setViewName("/sample/view/dept/deleteSampleDept");
   		}
   		return mav;
    } 

} 
