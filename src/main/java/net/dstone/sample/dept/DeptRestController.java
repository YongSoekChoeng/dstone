package net.dstone.sample.dept;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeptRestController{

    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.dept.DeptService deptService; 
    /********* SVC 정의부분 끝 *********/

    /** 
     * 샘플부서정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @SuppressWarnings({ "rawtypes", "unchecked", "static-access" })
    @GetMapping(value = "/rest/dept/listSampleDept/{PAGE_NUM}/{PAGE_SIZE}") 
    public ResponseEntity<Map> listSampleDept(@PathVariable int PAGE_NUM, @PathVariable int PAGE_SIZE) {


   		/************************ 변수 선언 시작 ************************/
    	ResponseEntity<Map> responseObj = new ResponseEntity<Map>(new HashMap(), HttpStatus.OK);
   		Map 				returnObj;
   		/************************ 변수 선언 끝 **************************/
   		try {
   			/************************ 변수 정의 시작 ************************/
   			net.dstone.sample.dept.vo.SampleDeptVo paramVo					= new net.dstone.sample.dept.vo.SampleDeptVo();
   			returnObj				= null;
   			/************************ 변수 정의 끝 ************************/
   			
   			/************************ 컨트롤러 로직 시작 ************************/
   			/*** 페이징파라메터 세팅 시작 ***/
   			paramVo.setPAGE_NUM(PAGE_NUM);
   			if(paramVo.getPAGE_SIZE() == 0) {
   				paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);
   			}
   			/*** 페이징파라메터 세팅 끝 ***/
   			// 2. 서비스 호출
   			returnObj 							= deptService.listSampleDept(paramVo);
   			
   			// 3. 결과처리
   			responseObj.getBody().put("RETURN_CD"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   			responseObj.getBody().put("RETURN_MSG"	, "조회성공." );
   			responseObj.getBody().put("returnObj"	, returnObj	);
   			/************************ 컨트롤러 로직 끝 ************************/
   			
   		} catch (Exception e) {
   			responseObj.status(HttpStatus.INTERNAL_SERVER_ERROR);
   			responseObj.getBody().put("RETURN_MSG"	, e.toString() );
   		}
   		return responseObj;
    } 

}
