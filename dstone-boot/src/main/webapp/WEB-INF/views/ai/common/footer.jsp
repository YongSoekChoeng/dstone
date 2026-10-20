<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String currentLink = requestUtil.getParameter("defaultLink", "");
String linkBase = requestUtil.getStrContextPath() + "/defaultLink.do?defaultLink=";
%>

			<!-- Footer -->
				<div id="footer-wrapper">
					<div class="container">
						<div class="row">
							<div class="col-12">

								<section>
									<h2>Dstone AI</h2>
									<div>
										<div class="row">
											<div class="col-3 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>ai/index" class="<%=(currentLink.equals("ai/index")?"current-page-item":"")%>">메인</a>
													</li>
												</ul>
											</div>
											<div class="col-3 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>ai/chat/chat" class="<%=(currentLink.equals("ai/chat/chat")?"current-page-item":"")%>">채팅</a>
													</li>
												</ul>
											</div>
											<div class="col-3 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>ai/workflow/workflow" class="<%=(currentLink.equals("ai/workflow/workflow")?"current-page-item":"")%>">Workflow 테스트</a>
													</li>
												</ul>
											</div>
											<div class="col-3 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>ai/admin/workflow/workflow" class="<%=(currentLink.equals("ai/admin/workflow/workflow")?"current-page-item":"")%>">Workflow 실행 관리</a>
													</li>
												</ul>
											</div>
											<div class="col-3 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>ai/designer/designer" class="<%=(currentLink.equals("ai/designer/designer")?"current-page-item":"")%>">Workflow 디자이너</a>
													</li>
												</ul>
											</div>
										</div>
									</div>
								</section>

							</div>
						</div>
						<div class="row">
							<div class="col-12">

								<div id="copyright">
									Dstone AI &middot; powered by dstone-ai-engine | Design: <a href="http://html5up.net">HTML5 UP</a>
								</div>

							</div>
						</div>
					</div>
				</div>
