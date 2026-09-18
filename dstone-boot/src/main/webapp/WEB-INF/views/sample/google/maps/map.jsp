<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%    
	String mapsPlatformKey = request.getAttribute("mapsPlatformKey").toString();
System.out.println("mapsPlatformKey["+mapsPlatformKey+"]");
%>
<!DOCTYPE html>
<html>
<head>
	<title>마커 주변 건물 조회</title>
	<meta charset="utf-8" />
	<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
	<script src="https://maps.googleapis.com/maps/api/js?key=<%=mapsPlatformKey%>&libraries=places&callback=initMap" async defer> </script>
	<script>
	
		let map;

		function initMap() {
        	const location = { lat: 37.5665, lng: 126.9780 }; // 서울 시청
	        	map = new google.maps.Map(document.getElementById("map"), {
	          	center: location,
	          	zoom: 16,
	        });
	
	        // PlacesService 인스턴스 생성
	        const service = new google.maps.places.PlacesService(map);
	        
	        map.addListener("click", function (e) {
	            new google.maps.Marker({
		            position: e.latLng,
		            map: map,
		            title: "사용자 추가 마커",
	            });
	          
				// 반경 200m 이내 건물/장소 검색
				service.nearbySearch(
					{
		            	location: e.latLng,
		            	radius: 200,
		            	type: "establishment", // 또는 'restaurant', 'cafe', 'store' 등
		          	},
		          	(results, status) => {
		           		if (status === google.maps.places.PlacesServiceStatus.OK) {
		              		var html = "<table border=1 >";
		              		html = html + "<td width='20%' >건물명</td>";
		              		html = html + "<td  >주소</td>";
		              		html = html + "<td width='30%' >위/경도</td>";
		                
		              		for (var i = 1; i < results.length; i++) {
		                		//const place = results[i];
		                		var place = results[i];
		                		html = html + "<tr onclick='javascript:setMarker("+ place.geometry.location.lat() +", "+ place.geometry.location.lng() +");'>"
		                		html = html + "<td>"+place.name+"</td>";
		                		html = html + "<td>"+place.vicinity+"</td>";
		                		html = html + "<td>"+place.geometry.location +"</td>";
							 	html = html + "</tr>"
		              		}
		              		html = html + "</table>"
					  		$("#establishment").html(html); 
		           		}
		          	}
		        );
		        /* service.nearbySearch End */
	        });
		    /* map.addListener End */
		}
		/* initMap End */
      
		function setMarker(latParam, lngParam){
			var location = { lat: latParam, lng: lngParam }; // 위도/경도 객체
			var marker = new google.maps.Marker({
			  position: location,
			  map: map,
			  title: "사용자 선택 마커"
			});
		}
        
    </script>
	<style>
		#map {
			height: 90vh;
			width: 100%;
		}
		#establishment {
			
		}
	</style>
</head>
<body>
	<h2>📍 마커 주변 건물(장소) 목록 조회</h2>
	<div id="map"></div>
	
	<div id="establishment"></div>
	
</body>
</html>