package net.dstone.boot.ai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.boot.ai.service.SqlConvertService;
import net.dstone.boot.ai.vo.SqlConvertRequest;
import net.dstone.boot.ai.vo.SqlConvertVo;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.SessionListener;
import net.dstone.common.utils.StringUtil;

/**
 * "오라클 SQL을 PostgreSQL SQL로 바꿔줘" 화면 전용 컨트롤러. 전부 AJAX라 ChatController/
 * DocumentController와 같은 이유로 @RestController로 둔다.
 */
@RestController
@RequestMapping("/ai/sqlconvert/*")
public class SqlConvertController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private SqlConvertService sqlConvertService;

	@PostMapping(value = "/convert.do")
	public SqlConvertVo convert(@RequestBody SqlConvertRequest request, HttpServletRequest servletRequest) {
		if (StringUtil.isEmpty(request.originalSql())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "originalSql은 필수입니다.");
		}
		return this.sqlConvertService.convert(request.originalSql(), this.getRequesterId(servletRequest));
	}

	@PostMapping(value = "/listHistory.do")
	public List<SqlConvertVo> listHistory() {
		return this.sqlConvertService.listHistory();
	}

	private String getRequesterId(HttpServletRequest request) {
		CustomUserDetails userDetails = (CustomUserDetails) request.getSession(true).getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
		return userDetails.getUsername();
	}

}
