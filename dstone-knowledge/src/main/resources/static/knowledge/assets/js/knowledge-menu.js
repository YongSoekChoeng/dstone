/**
 * 코드 분석(Knowledge) 화면의 왼쪽 메뉴를 그립니다. 그리는 것은 console.js(DstoneConsole.sidebar)가 합니다.
 * 메뉴 목록은 서버가 정합니다(관리자 > 메뉴관리·권한관리, knowledge_management_menu). 여기에는 상단 표시만 둡니다.
 */
document.addEventListener("DOMContentLoaded", function () {
	DstoneConsole.sidebar({
		brand: "Knowledge",
		mark: "K",
		home: "/knowledge/dashboard/dashboard.html"
	});
});
