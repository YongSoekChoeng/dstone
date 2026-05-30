<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%                                                                                                              
/******************************************* 변수 선언 시작 *******************************************/        	  
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
/******************************************* 변수 선언 끝 *********************************************/           
                                                                                                                
/******************************************* 변수 정의 시작 *******************************************/           
                                                                                                          
/******************************************* 변수 정의 끝 *********************************************/   
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
	
		<!-- Header 영역 -->
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
		
		<script type="text/javascript">

			function openGoogleMap(){
				var url = "<%=request.getContextPath()%>/google/map/initPage.do";
				var target = "";
				var width = "700";
				var height = "500";
				var option = "";
				openWin(url, target, width, height, option);
			}  
		
		</script>  
		
	</head>
	<body class="is-preload">

		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top 영역 -->
					<%@ include file="/WEB-INF/views/common/top.jsp" %>
					
					<section>
						<!-- =============================================== Content 영역 Start =============================================== -->
						  
						<table border=1 class="table-wrapper" >   						                                                                                        
							<tbody>                                                                                             
								<tr>                                                                                                      
									<td>구글맵</td>
									<td><input type="button" value="Open" onclick="openGoogleMap();"></td>
								</tr>   
							</tbody> 
						</table>
								       
						<!-- =============================================== Content 영역 End =============================================== -->
					</section>

				</div>
			
			</div>

			<!-- Menu 영역 -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>
