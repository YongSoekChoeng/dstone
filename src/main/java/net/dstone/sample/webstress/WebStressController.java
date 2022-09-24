package net.dstone.sample.webstress; 
 
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
@Controller
@RequestMapping(value = "/sample/webstress/*")
public class WebStressController extends net.dstone.common.biz.BaseController { 
    
    /** 
     * 샘플Queue입력 (Syc)
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/insertWebStressBySync.do") 
    public ModelAndView insertWebStressBySync(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {
   		
    	/************************ 변수 선언 시작 ************************/
		String filePath = "D:/Temp/samplewebstress";
		net.dstone.common.utils.RequestUtil requestUtil = null;
   		/************************ 변수 선언 끝 **************************/
   		try {
   			/************************ 컨트롤러 로직 시작 ************************/
   			requestUtil = new net.dstone.common.utils.RequestUtil(request, response);		
   			net.dstone.common.utils.FileUtil.makeDir(filePath);

			String id = "TEST-" + net.dstone.common.utils.DateUtil.getToDate("HHmmss-") + new net.dstone.common.utils.GuidUtil().getNewGuid();
			String fileName = id + ".log";
			String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
			String fileDelYn = requestUtil.getParameter("fileDelYn", "Y");

			net.dstone.sample.webstress.WebStressQueueItem item = new net.dstone.sample.webstress.WebStressQueueItem();
			item.setId(id);
			item.setProperty("filePath", filePath);
			item.setProperty("fileName", fileName);
			item.setProperty("fileConts", fileConts);
			item.setProperty("fileDelYn", fileDelYn);
			item.doTheJob();

			//Thread.sleep(5*1000);

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
		String filePath = "D:/Temp/samplewebstress";
		net.dstone.common.utils.RequestUtil requestUtil = null;
   		/************************ 변수 선언 끝 **************************/
		try {
   			/************************ 컨트롤러 로직 시작 ************************/
			requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
			net.dstone.common.utils.FileUtil.makeDir(filePath);
			String id = "TEST-" + net.dstone.common.utils.DateUtil.getToDate("HHmmss-") + new net.dstone.common.utils.GuidUtil().getNewGuid();
			String fileName = id + ".log";
			String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
			net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
			String fileDelYn = requestUtil.getParameter("fileDelYn", "Y");

			net.dstone.sample.webstress.WebStressQueueItem item = new net.dstone.sample.webstress.WebStressQueueItem();
			item.setId(id);
			item.setProperty("filePath", filePath);
			item.setProperty("fileName", fileName);
			item.setProperty("fileConts", fileConts);
			item.setProperty("fileDelYn", fileDelYn);
			
			net.dstone.common.queue.QueueHandler.getInstance().addQueue(item);
			
			//Thread.sleep(5*1000);

			mav.setViewName("/sample/view/webstress/webStress");
			/************************ 컨트롤러 로직 끝 ************************/

		} catch (Exception e) {
   			handleException(request, response, e);
   			mav.setViewName(null);
   		}
   		return mav;
    } 

} 
