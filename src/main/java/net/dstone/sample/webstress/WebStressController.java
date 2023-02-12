package net.dstone.sample.webstress; 
 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.common.utils.SystemUtil;
@Controller
@RequestMapping(value = "/sample/webstress/*")
public class WebStressController extends net.dstone.common.biz.BaseController { 


    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.dept.DeptService deptService; 
    /********* SVC 정의부분 끝 *********/
    
	public static String TEST_FILE_ROOT;
	static {
		if(SystemUtil.getSystemProperty("os.name").toUpperCase().startsWith("WINDOW")) {
			TEST_FILE_ROOT = "D:/Temp/samplewebstress";
		}else {
			TEST_FILE_ROOT = "/tmp/samplewebstress";
		}
	}
	
    /** 
     * 샘플Queue입력 (Syc)
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertWebStressBySync.do") 
    public ModelAndView insertWebStressBySync(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		
    	/************************ 변수 선언 시작 ************************/
		String filePath = TEST_FILE_ROOT;
		net.dstone.common.utils.RequestUtil requestUtil = null;
		net.dstone.sample.dept.cud.vo.SampleDeptCudVo paramVo = new net.dstone.sample.dept.cud.vo.SampleDeptCudVo();
   		/************************ 변수 선언 끝 **************************/
   		try {

   			/************************ 컨트롤러 로직 시작 ************************/
   			requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
   			
   			/* 파일생성 테스트 일경우 시작 */
   			/*
   			net.dstone.common.utils.FileUtil.makeDir(filePath);
			String id = "TEST-" + net.dstone.common.utils.DateUtil.getToDate("HHmmss-") + new net.dstone.common.utils.GuidUtil().getNewGuid();
			String fileName = id + ".log";

			net.dstone.sample.webstress.WebStressFileQueueItem item = new net.dstone.sample.webstress.WebStressFileQueueItem();
			item.setId(id);
			item.setProperty("filePath", filePath);
			item.setProperty("fileName", fileName);
			item.doTheJob();
			*/
   			/* 파일생성 테스트 일경우 끝 */

			/* DB데이터생성 테스트 일경우 시작 */
			paramVo.setDEPT_NAME("부서-"+(new net.dstone.common.utils.GuidUtil()).getNewGuid());
			paramVo.setINPUT_DT(net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd"));
			deptService.insertSampleDept(paramVo);
			/* DB데이터생성 테스트 일경우 끝 */

   			mav.setViewName("/sample/view/webstress/webStress");
   			/************************ 컨트롤러 로직 끝 ************************/
   		
   		} catch (Exception e) {
   			handleException(request, response, e);
   			mav.setViewName(null);
   		}
   		return mav;
    } 
    
    /** 
     * 샘플Queue입력 (Asyc)
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertWebStressByAsync.do") 
    public ModelAndView insertWebStressByAsync(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		
    	/************************ 변수 선언 시작 ************************/
		String filePath = TEST_FILE_ROOT;
		net.dstone.common.utils.RequestUtil requestUtil = null;
		net.dstone.sample.dept.cud.vo.SampleDeptCudVo paramVo = new net.dstone.sample.dept.cud.vo.SampleDeptCudVo();
   		/************************ 변수 선언 끝 **************************/
		try {
   			/************************ 컨트롤러 로직 시작 ************************/
			requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
			
   			/* 파일생성 테스트 일경우 시작 */
   			/*
			net.dstone.common.utils.FileUtil.makeDir(filePath);
			String id = "TEST-" + net.dstone.common.utils.DateUtil.getToDate("HHmmss-") + new net.dstone.common.utils.GuidUtil().getNewGuid();
			String fileName = id + ".log";
			String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
			net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);

			net.dstone.sample.webstress.WebStressFileQueueItem item = new net.dstone.sample.webstress.WebStressFileQueueItem();
			item.setId(id);
			item.setProperty("filePath", filePath);
			item.setProperty("fileName", fileName);
			net.dstone.common.queue.QueueHandler.getInstance().addQueue(item);
			*/
   			/* 파일생성 테스트 일경우 끝 */

			/* DB데이터생성 테스트 일경우 시작 */
			paramVo.setDEPT_NAME("부서-"+(new net.dstone.common.utils.GuidUtil()).getNewGuid());
			paramVo.setINPUT_DT(net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd"));
			
			net.dstone.sample.webstress.WebStressDbQueueItem item = new net.dstone.sample.webstress.WebStressDbQueueItem();
			item.setObj("deptService", deptService);
			item.setObj("paramVo", paramVo);
			net.dstone.common.queue.QueueHandler.getInstance().addQueue(item);
			/* DB데이터생성 테스트 일경우 끝 */
			
			mav.setViewName("/sample/view/webstress/webStress");
			/************************ 컨트롤러 로직 끝 ************************/

		} catch (Exception e) {
   			handleException(request, response, e);
   			mav.setViewName(null);
   		}
   		return mav;
    } 

} 
