<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="/WEB-INF/views/common/header.jsp" flush="true"/>
		<style type="text/css">
			.login-box { max-width: 360px; margin: 80px auto; padding: 30px; border: 1px solid #ddd; border-radius: 6px; }
			.login-box h2 { margin-top: 0; }
			.login-box .field { margin-bottom: 12px; }
			.login-box .field label { display:block; margin-bottom: 4px; }
			.login-box .field input { width: 100%; box-sizing: border-box; }
			.login-box .err { color: #c0392b; margin-bottom: 10px; }
		</style>
	</head>
	<body class="is-preload">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<section>
						<div class="login-box">
							<h2>배치관리 로그인</h2>
							<form name="SUBMIT_FORM" method="post" action="<%=net.dstone.batchadmin.common.config.ConfigSecurity.LOGIN_PROCESS_ACTION%>">
								<div class="field">
									<label>아이디</label>
									<input type="text" name="<%=net.dstone.batchadmin.common.config.ConfigSecurity.USERNAME_PARAMETER%>" autofocus value="batchadmin" >
								</div>
								<div class="field">
									<label>비밀번호</label>
									<input type="password" name="<%=net.dstone.batchadmin.common.config.ConfigSecurity.PASSWORD_PARAMETER%>">
								</div>
								<button type="submit" class="button primary fit">로그인</button>
							</form>
						</div>
					</section>
				</div>
			</div>
		</div>
	</body>
</html>
