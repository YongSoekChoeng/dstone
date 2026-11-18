/**
 * 코드 분석(Knowledge) 화면의 왼쪽 메뉴 목록입니다. 그리는 것은 dstone-common 의 console.js(DstoneConsole.sidebar)가 합니다.
 * 메뉴 한 줄: [화면 주소, 이름, 아이콘 이름]. 첫 칸이 "#group" 이면 메뉴가 아니라 묶음 제목 줄입니다.
 * 쓰는 목적으로 묶습니다: 현행 파악(차세대 전환의 ASIS 분석), 변경 영향(SM 단계의 수정 영향도), 관리(분석을 돌리고 점검하는 사람용)
 */
document.addEventListener("DOMContentLoaded", function () {
	DstoneConsole.sidebar({
		brand: "Knowledge",
		mark: "K",
		home: "/knowledge/dashboard/dashboard.html",
		menus: [
			["/knowledge/dashboard/dashboard.html", "대시보드", "dashboard"],
			["#group", "현행 파악", ""],
			["/knowledge/project/project.html", "프로젝트", "folder"],
			["/knowledge/graph/graph.html", "노드 맵", "graph"],
			["/knowledge/search/search.html", "검색 · 문서", "search"],
			["#group", "변경 영향", ""],
			["/knowledge/impact/impact.html", "영향도", "impact"],
			["/knowledge/diff/diff.html", "리비전 비교", "diff"],
			["#group", "관리", ""],
			["/knowledge/index.html", "분석 실행 · 진단", "tool"],
			["/knowledge/guide/guide.html", "사용 안내", "book"]
		]
	});
});
