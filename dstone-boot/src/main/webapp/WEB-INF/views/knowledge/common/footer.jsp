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
									<h2>Dstone Knowledge</h2>
									<div>
										<div class="row">
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/index" class="<%=(currentLink.equals("knowledge/index")?"current-page-item":"")%>">프로젝트 · 분석</a>
													</li>
												</ul>
											</div>
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/search/search" class="<%=(currentLink.equals("knowledge/search/search")?"current-page-item":"")%>">검색 · 문서</a>
													</li>
												</ul>
											</div>
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/impact/impact" class="<%=(currentLink.equals("knowledge/impact/impact")?"current-page-item":"")%>">영향도 · 호출 관계</a>
													</li>
												</ul>
											</div>
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/graph/graph" class="<%=(currentLink.equals("knowledge/graph/graph")?"current-page-item":"")%>">노드 맵</a>
													</li>
												</ul>
											</div>
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/diff/diff" class="<%=(currentLink.equals("knowledge/diff/diff")?"current-page-item":"")%>">리비전 비교</a>
													</li>
												</ul>
											</div>
											<div class="col-2 col-6-medium col-12-small">
												<ul class="link-list">
													<li>
														<a href="<%=linkBase%>knowledge/guide/guide" class="<%=(currentLink.equals("knowledge/guide/guide")?"current-page-item":"")%>">사용 안내</a>
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
									Dstone Knowledge &middot; powered by dstone-knowledge | Design: <a href="http://html5up.net">HTML5 UP</a>
								</div>

							</div>
						</div>
					</div>
				</div>
