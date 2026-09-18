package net.dstone.common.exception.resolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;

import net.dstone.common.biz.BaseController;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.exception.SecException;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.RequestUtil;

public class DsExceptionResolver extends SimpleMappingExceptionResolver {

	private static final LogUtil logger = new LogUtil(DsExceptionResolver.class);

	@Override
	protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		logger.info(this.getClass().getName() + ".doResolveException() has been called !!!");
		ModelAndView mav = null;
		this.applyErrCdToResponse(request, response, ex);
		String viewName = this.determineViewName(ex, request);
		if( viewName != null ) {
			mav = this.getModelAndView(viewName, ex, request);
			mav.addObject("errCd", response.getHeader("errCd"));
			mav.addObject("errMsg", response.getHeader("errMsg"));
			StringBuffer errMsgDetail = new StringBuffer();
			StackTraceElement[] stackTraceElementList = ex.getStackTrace();
			if(stackTraceElementList != null) {
				for( StackTraceElement item : stackTraceElementList) {
					errMsgDetail.append(item.toString()).append("\n");
				}
			}
			mav.addObject("errMsgDetail", errMsgDetail.toString());
			if(RequestUtil.isAjax(request) || RequestUtil.isJson(request)) {
				Integer statusCode = this.determineStatusCode(request, viewName);
				if( statusCode != null ) {
					this.addStatusCode(viewName, statusCode);
				}
			}
		}
		this.dumpExceptionLog(ex);
		return mav;
	}
	
	private void dumpExceptionLog(Exception ex) {
		ex.printStackTrace();
	}
	@Override
	protected ModelAndView getModelAndView(String viewName, Exception ex, HttpServletRequest request) {
		ModelAndView mav = new ModelAndView(viewName);
		if(RequestUtil.isAjax(request) || RequestUtil.isJson(request)) { 
			mav = new ModelAndView("jsonView"); 
		}
		return mav;
	}

	private void applyErrCdToResponse(HttpServletRequest request, HttpServletResponse response, Exception ex) {
		try {
			if(ex instanceof SecException) {
				BaseController.setErrCd(request, response, ((SecException)ex).getErrCd());
			}else if(ex instanceof BizException) {
				BaseController.setErrCd(request, response, ((BizException)ex).getErrCd());
			}else {
				BaseController.setErrCd(request, response, ErrCd.SYS_ERR);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

}
